import { assetRepo } from './index.js';
import { OPEN_REPAIR_STATUSES } from '../models/constants.js';
import { containsRegex, paginate } from '../utils/helpers.js';

const first = (path) => ({ $first: path });

/**
 * Joins an asset with its branch, current employee, active assignment and
 * repair statistics. Repair count / cost are ALWAYS computed from the repairs
 * collection, never stored on the asset.
 */
export function enrichAssetStages() {
  return [
    { $lookup: { from: 'branches', localField: 'currentBranchId', foreignField: 'branchId', as: '_branch' } },
    { $lookup: { from: 'employees', localField: 'currentEmployeeId', foreignField: 'employeeId', as: '_emp' } },
    {
      $lookup: {
        from: 'repairs',
        let: { aid: '$assetId' },
        pipeline: [
          { $match: { $expr: { $eq: ['$assetId', '$$aid'] } } },
          { $sort: { repairDate: 1, repairId: 1 } },
          {
            $group: {
              _id: null,
              repairCount: { $sum: 1 },
              totalRepairCost: { $sum: { $ifNull: ['$finalCost', 0] } },
              lastRepairDate: { $last: '$repairDate' },
              lastRepairCost: { $last: { $ifNull: ['$finalCost', 0] } },
              maxRepairCost: { $max: { $ifNull: ['$finalCost', 0] } },
              lastEmployeeId: { $last: '$employeeId' },
              lastRepairStatus: { $last: '$status' },
              openCount: { $sum: { $cond: [{ $in: ['$status', OPEN_REPAIR_STATUSES] }, 1, 0] } },
            },
          },
        ],
        as: '_rs',
      },
    },
    {
      $lookup: {
        from: 'assignments',
        let: { aid: '$assetId' },
        pipeline: [
          { $match: { $expr: { $eq: ['$assetId', '$$aid'] }, status: 'ACTIVE' } },
          { $limit: 1 },
        ],
        as: '_asg',
      },
    },
    { $addFields: { _rsd: first('$_rs') } },
    {
      $addFields: {
        _refEmployeeId: { $ifNull: ['$deadInfo.previousEmployeeId', '$_rsd.lastEmployeeId'] },
      },
    },
    { $lookup: { from: 'employees', localField: '_refEmployeeId', foreignField: 'employeeId', as: '_ref' } },
    {
      $addFields: {
        branchName: first('$_branch.name'),
        branchCode: first('$_branch.code'),
        employeeName: first('$_emp.name'),
        employeeCode: first('$_emp.employeeCode'),
        employeeDesignation: first('$_emp.designation'),
        employeeDepartment: first('$_emp.department'),
        // Person to show in repair/dead lists when no current employee exists.
        displayEmployeeId: { $ifNull: ['$currentEmployeeId', '$_refEmployeeId'] },
        displayEmployeeName: { $ifNull: [first('$_emp.name'), first('$_ref.name')] },
        displayEmployeeCode: { $ifNull: [first('$_emp.employeeCode'), first('$_ref.employeeCode')] },
        assignmentDate: first('$_asg.assignmentDate'),
        assignmentId: first('$_asg.assignmentId'),
        repairCount: { $ifNull: ['$_rsd.repairCount', 0] },
        totalRepairCost: { $ifNull: ['$_rsd.totalRepairCost', 0] },
        lastRepairDate: { $ifNull: ['$_rsd.lastRepairDate', null] },
        lastRepairCost: { $ifNull: ['$_rsd.lastRepairCost', 0] },
        maxRepairCost: { $ifNull: ['$_rsd.maxRepairCost', 0] },
        avgRepairCost: {
          $cond: [
            { $gt: [{ $ifNull: ['$_rsd.repairCount', 0] }, 0] },
            { $round: [{ $divide: ['$_rsd.totalRepairCost', '$_rsd.repairCount'] }, 2] },
            0,
          ],
        },
      },
    },
    { $project: { _branch: 0, _emp: 0, _rs: 0, _asg: 0, _rsd: 0, _ref: 0, _refEmployeeId: 0 } },
  ];
}

/** Parses the "repairCount" filter value: 0 | 1 | 2 | 3+ */
export function repairCountMatch(value) {
  if (value === undefined || value === '') return null;
  const s = String(value);
  if (s.endsWith('+')) return { $gte: parseInt(s, 10) || 0 };
  const n = parseInt(s, 10);
  return Number.isNaN(n) ? null : n;
}

export function dateRangeMatch(from, to) {
  const m = {};
  if (from) m.$gte = from;
  if (to) {
    const end = new Date(to);
    end.setUTCHours(23, 59, 59, 999);
    m.$lte = end;
  }
  return Object.keys(m).length ? m : null;
}

/** Builds the common asset-list pipeline from filters. */
export function assetListPipeline(f = {}) {
  const pre = {};
  if (f.status) pre.status = Array.isArray(f.status) ? { $in: f.status } : f.status;
  if (f.branchId) pre.currentBranchId = f.branchId;
  if (f.employeeId) pre.currentEmployeeId = f.employeeId;
  if (f.assetId) pre.assetId = f.assetId;
  if (f.assigned === true) pre.currentEmployeeId = f.employeeId ? f.employeeId : { $ne: null };
  if (f.previousEmployeeId) pre['deadInfo.previousEmployeeId'] = f.previousEmployeeId;

  const post = {};
  const rc = repairCountMatch(f.repairCount);
  if (rc !== null) post.repairCount = rc;
  if (f.minCost !== undefined || f.maxCost !== undefined) {
    post.totalRepairCost = {};
    if (f.minCost !== undefined) post.totalRepairCost.$gte = f.minCost;
    if (f.maxCost !== undefined) post.totalRepairCost.$lte = f.maxCost;
  }
  const dr = dateRangeMatch(f.dateFrom, f.dateTo);
  if (dr) post[f.dateField || 'assignmentDate'] = dr;
  if (f.q && String(f.q).trim()) {
    const rx = containsRegex(f.q);
    post.$or = [
      'assetId',
      'assetTag',
      'serialNumber',
      'brand',
      'model',
      'employeeName',
      'employeeCode',
      'employeeDesignation',
      'displayEmployeeName',
      'branchName',
    ].map((k) => ({ [k]: rx }));
  }

  const stages = [];
  if (Object.keys(pre).length) stages.push({ $match: pre });
  stages.push(...enrichAssetStages());
  if (Object.keys(post).length) stages.push({ $match: post });
  return stages;
}

export const ASSET_SORT_FIELDS = [
  'assetId',
  'serialNumber',
  'brand',
  'model',
  'status',
  'employeeName',
  'branchName',
  'assignmentDate',
  'repairCount',
  'totalRepairCost',
  'lastRepairDate',
  'purchasePrice',
  'createdAt',
];

export async function listAssets(filters, sort, paging) {
  return paginate(assetRepo.collection(), [...assetListPipeline(filters), { $sort: sort }], paging);
}

export async function allAssets(filters, sort, cap = 20000) {
  return assetRepo
    .collection()
    .aggregate([...assetListPipeline(filters), { $sort: sort }, { $limit: cap }, { $project: { _id: 0 } }])
    .toArray();
}

export async function getEnrichedAsset(assetId, session) {
  const [doc] = await assetRepo
    .collection()
    .aggregate([{ $match: { assetId } }, ...enrichAssetStages(), { $project: { _id: 0 } }], { session })
    .toArray();
  return doc || null;
}
