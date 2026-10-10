import { nextId, withTransaction } from '../config/db.js';
import { ASSET_STATUS } from '../models/constants.js';
import { assetRepo, branchRepo, employeeRepo } from '../repositories/index.js';
import { allAssets, ASSET_SORT_FIELDS, listAssets } from '../repositories/assetQueries.js';
import { conflict, notFound } from '../utils/errors.js';
import { containsRegex, paginate, parseSort } from '../utils/helpers.js';
import { branchSchema } from '../validators/schemas.js';
import { validate } from '../validators/validate.js';
import { mustBranch, recordAudit } from './core.js';

/** Live per-branch stock / employee / repair-cost statistics. */
export function branchStatsStages() {
  const count = (s) => ({ $ifNull: [`$_so.${s}`, 0] });
  return [
    {
      $lookup: {
        from: 'assets',
        localField: 'branchId',
        foreignField: 'currentBranchId',
        pipeline: [{ $group: { _id: '$status', n: { $sum: 1 } } }],
        as: '_st',
      },
    },
    {
      $lookup: {
        from: 'employees',
        localField: 'branchId',
        foreignField: 'branchId',
        pipeline: [{ $count: 'n' }],
        as: '_ec',
      },
    },
    {
      $lookup: {
        from: 'repairs',
        localField: 'branchId',
        foreignField: 'branchId',
        pipeline: [{ $group: { _id: null, cost: { $sum: { $ifNull: ['$finalCost', 0] } } } }],
        as: '_rc',
      },
    },
    {
      $addFields: {
        _so: {
          $arrayToObject: { $map: { input: '$_st', as: 's', in: { k: '$$s._id', v: '$$s.n' } } },
        },
      },
    },
    {
      $addFields: {
        totalAssets: { $sum: '$_st.n' },
        assigned: count('ASSIGNED'),
        available: count('AVAILABLE'),
        repair: count('REPAIR'),
        dead: count('DEAD'),
        sold: count('SOLD'),
        retired: count('RETIRED'),
        employeeCount: { $ifNull: [{ $first: '$_ec.n' }, 0] },
        repairCost: { $ifNull: [{ $first: '$_rc.cost' }, 0] },
      },
    },
    { $project: { _st: 0, _ec: 0, _rc: 0, _so: 0 } },
  ];
}

export async function createBranch(body, user) {
  const data = validate(body, branchSchema);
  if (await branchRepo.exists({ code: data.code })) {
    throw conflict('Branch code already exists.', 'DUPLICATE_BRANCH_CODE');
  }
  const branchId = await nextId('branch', 'BRN-', 4);
  const doc = {
    branchId,
    ...data,
    address: data.address ?? null,
    contactNumber: data.contactNumber ?? null,
    status: data.status ?? 'ACTIVE',
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await withTransaction(async (s) => {
    await branchRepo.insert(doc, s);
    await recordAudit(s, { action: 'BRANCH_CREATED', entityType: 'BRANCH', entityId: branchId, user, newValues: doc });
  });
  return getBranch(branchId);
}

export async function updateBranch(branchId, body, user) {
  const data = validate(body, branchSchema, { partial: true });
  return withTransaction(async (s) => {
    const old = await mustBranch(branchId, s);
    if (data.code && data.code !== old.code && (await branchRepo.exists({ code: data.code, branchId: { $ne: branchId } }, s))) {
      throw conflict('Branch code already exists.', 'DUPLICATE_BRANCH_CODE');
    }
    if (data.status === 'INACTIVE' && old.status !== 'INACTIVE') await assertBranchDeactivatable(branchId, s);
    const set = { ...data, updatedAt: new Date() };
    await branchRepo.update(branchId, set, s);
    await recordAudit(s, {
      action: 'BRANCH_UPDATED',
      entityType: 'BRANCH',
      entityId: branchId,
      user,
      oldValues: pick(old, Object.keys(data)),
      newValues: data,
    });
    return getBranch(branchId, s);
  });
}

/** Soft delete: branches are never physically removed. */
export async function deactivateBranch(branchId, user) {
  return updateBranch(branchId, { status: 'INACTIVE' }, user);
}

async function assertBranchDeactivatable(branchId, s) {
  const emp = await employeeRepo.count({ branchId, status: 'ACTIVE' }, s);
  const live = await assetRepo.count(
    { currentBranchId: branchId, status: { $in: [ASSET_STATUS.AVAILABLE, ASSET_STATUS.ASSIGNED, ASSET_STATUS.REPAIR] } },
    s,
  );
  if (emp || live) {
    throw conflict(
      'Branch cannot be deactivated while it has active employees or laptops in use or stock.',
      'BRANCH_IN_USE',
      { activeEmployees: emp, liveAssets: live },
    );
  }
}

const pick = (o, keys) => Object.fromEntries(keys.map((k) => [k, o[k] ?? null]));

export async function getBranch(branchId, session) {
  const [b] = await branchRepo
    .collection()
    .aggregate([{ $match: { branchId } }, ...branchStatsStages(), { $project: { _id: 0 } }], { session })
    .toArray();
  if (!b) throw notFound('Branch does not exist.', 'BRANCH_NOT_FOUND');
  return b;
}

export async function listBranches(q, paging) {
  const match = {};
  if (q.status) match.status = q.status;
  if (q.city) match.city = containsRegex(q.city);
  if (q.state) match.state = containsRegex(q.state);
  if (q.q) {
    const rx = containsRegex(q.q);
    match.$or = [{ name: rx }, { code: rx }, { branchId: rx }, { city: rx }];
  }
  const sort = parseSort(
    q.sort,
    ['name', 'code', 'city', 'state', 'createdAt', 'totalAssets', 'employeeCount'],
    { name: 1, _id: 1 },
  );
  return paginate(branchRepo.collection(), [{ $match: match }, ...branchStatsStages(), { $sort: sort }], paging);
}

/** Branch detail: info + stats + (paged) asset list. */
export async function branchAssets(branchId, q, paging) {
  const branch = await getBranch(branchId);
  const sort = parseSort(q.sort, ASSET_SORT_FIELDS, { assetId: 1, _id: 1 });
  const assets = await listAssets(
    { branchId, status: q.status || undefined, q: q.q || undefined, employeeId: q.employeeId || undefined },
    sort,
    paging,
  );
  return { branch, assets };
}

export async function branchAssetsAll(branchId, filters) {
  await mustBranch(branchId);
  return allAssets({ ...filters, branchId }, { assetId: 1, _id: 1 });
}
