import { assetRepo, branchRepo, employeeRepo, repairRepo } from '../repositories/index.js';
import { listAssets, ASSET_SORT_FIELDS } from '../repositories/assetQueries.js';
import { parseSort } from '../utils/helpers.js';
import { ASSET_STATUSES } from '../models/constants.js';
import { branchStatsStages } from './branchService.js';

/** Counts of assets per status, always derived from the assets collection. */
export async function stockSummary(filters = {}) {
  const match = {};
  if (filters.branchId) match.currentBranchId = filters.branchId;
  const rows = await assetRepo
    .collection()
    .aggregate([{ $match: match }, { $group: { _id: '$status', n: { $sum: 1 } } }])
    .toArray();
  const by = Object.fromEntries(rows.map((r) => [r._id, r.n]));
  const out = Object.fromEntries(ASSET_STATUSES.map((s) => [s.toLowerCase(), by[s] ?? 0]));
  out.total = Object.values(out).reduce((a, b) => a + b, 0);
  return out;
}

export async function stockByBranch(filters = {}) {
  const match = filters.branchId ? { branchId: filters.branchId } : {};
  return branchRepo
    .collection()
    .aggregate([
      { $match: match },
      ...branchStatsStages(),
      {
        $project: {
          _id: 0,
          branchId: 1,
          branchName: '$name',
          branchCode: '$code',
          status: 1,
          total: '$totalAssets',
          available: 1,
          assigned: 1,
          repair: 1,
          dead: 1,
          sold: 1,
          retired: 1,
        },
      },
      { $sort: { branchName: 1 } },
    ])
    .toArray();
}

export async function stockOverview() {
  const [summary, branches] = await Promise.all([stockSummary(), stockByBranch()]);
  return { summary, branches };
}

export async function stockAssets(q, paging) {
  const sort = parseSort(q.sort, ASSET_SORT_FIELDS, { assetId: 1, _id: 1 });
  return listAssets(
    {
      status: q.status ? String(q.status).toUpperCase().split(',') : undefined,
      branchId: q.branchId || undefined,
      q: q.q || undefined,
    },
    sort,
    paging,
  );
}

export async function dashboard() {
  const [summary, branchWise, branches, employeesWithAssets, totalEmployees, cost, byMonth, byBranch] = await Promise.all([
    stockSummary(),
    stockByBranch(),
    branchRepo.count({}),
    employeeRepo.count({ currentAssetId: { $ne: null } }),
    employeeRepo.count({}),
    repairRepo
      .collection()
      .aggregate([{ $group: { _id: null, total: { $sum: { $ifNull: ['$finalCost', 0] } }, count: { $sum: 1 } } }])
      .toArray(),
    repairRepo
      .collection()
      .aggregate([
        {
          $group: {
            _id: { $dateToString: { format: '%Y-%m', date: '$repairDate' } },
            cost: { $sum: { $ifNull: ['$finalCost', 0] } },
            count: { $sum: 1 },
          },
        },
        { $sort: { _id: -1 } },
        { $limit: 12 },
        { $sort: { _id: 1 } },
        { $project: { _id: 0, month: '$_id', cost: 1, count: 1 } },
      ])
      .toArray(),
    repairRepo
      .collection()
      .aggregate([
        { $group: { _id: '$branchId', cost: { $sum: { $ifNull: ['$finalCost', 0] } }, count: { $sum: 1 } } },
        { $lookup: { from: 'branches', localField: '_id', foreignField: 'branchId', as: '_b' } },
        { $project: { _id: 0, branchId: '$_id', branchName: { $first: '$_b.name' }, cost: 1, count: 1 } },
        { $sort: { cost: -1 } },
      ])
      .toArray(),
  ]);

  return {
    totals: {
      totalAssets: summary.total,
      assigned: summary.assigned,
      available: summary.available,
      repair: summary.repair,
      dead: summary.dead,
      sold: summary.sold,
      retired: summary.retired,
      branches,
      employeesWithAssets,
      totalEmployees,
      totalRepairCost: cost[0]?.total ?? 0,
      totalRepairs: cost[0]?.count ?? 0,
    },
    statusChart: ASSET_STATUSES.map((s) => ({ status: s, count: summary[s.toLowerCase()] })),
    branchWise,
    repairCostByMonth: byMonth,
    repairCostByBranch: byBranch,
    generatedAt: new Date(),
  };
}
