import { nextId, withTransaction } from '../config/db.js';
import { ASSET_STATUS, EVENT } from '../models/constants.js';
import {
  assetRepo,
  assignmentRepo,
  historyRepo,
  repairRepo,
  replacementRepo,
} from '../repositories/index.js';
import { ASSET_SORT_FIELDS, getEnrichedAsset, listAssets } from '../repositories/assetQueries.js';
import { checkDateOrder, validate } from '../validators/validate.js';
import { assetSchema, markDeadSchema, statusChangeSchema } from '../validators/schemas.js';
import { conflict, notFound } from '../utils/errors.js';
import { parseSort } from '../utils/helpers.js';
import { markAssetDead, mustAsset, mustBranch, recordAudit, recordHistory } from './core.js';

export async function createAsset(body, user) {
  const data = validate(body, assetSchema);
  checkDateOrder(data.warrantyStartDate, data.warrantyEndDate, 'Warranty end date cannot be before the start date.');
  await mustBranch(data.currentBranchId, undefined, { activeOnly: true });
  await assertUnique(data);

  const assetId = await nextId('asset', 'AST-', 6);
  const doc = {
    assetId,
    assetTag: data.assetTag ?? null,
    serialNumber: data.serialNumber,
    brand: data.brand,
    model: data.model,
    processor: data.processor ?? null,
    ram: data.ram ?? null,
    storage: data.storage ?? null,
    operatingSystem: data.operatingSystem ?? null,
    purchaseDate: data.purchaseDate ?? null,
    purchasePrice: data.purchasePrice ?? null,
    warrantyStartDate: data.warrantyStartDate ?? null,
    warrantyEndDate: data.warrantyEndDate ?? null,
    status: ASSET_STATUS.AVAILABLE,
    currentBranchId: data.currentBranchId,
    currentEmployeeId: null,
    deadInfo: null,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await withTransaction(async (s) => {
    await assetRepo.insert(doc, s);
    await recordHistory(s, {
      assetId,
      eventType: EVENT.PURCHASED,
      branchId: doc.currentBranchId,
      previousStatus: null,
      newStatus: ASSET_STATUS.AVAILABLE,
      date: doc.purchaseDate ?? new Date(),
      user,
      remarks: 'Laptop registered and added to stock',
    });
    await recordAudit(s, { action: 'LAPTOP_CREATED', entityType: 'ASSET', entityId: assetId, user, newValues: doc });
  });
  return getEnrichedAsset(assetId);
}

async function assertUnique(data, ignoreAssetId) {
  const notSelf = ignoreAssetId ? { assetId: { $ne: ignoreAssetId } } : {};
  if (data.serialNumber && (await assetRepo.exists({ serialNumber: data.serialNumber, ...notSelf }))) {
    throw conflict('Serial number already exists.', 'DUPLICATE_SERIAL');
  }
  if (data.assetTag && (await assetRepo.exists({ assetTag: data.assetTag, ...notSelf }))) {
    throw conflict('Asset tag already exists.', 'DUPLICATE_ASSET_TAG');
  }
}

export async function updateAsset(assetId, body, user) {
  const data = validate(body, assetSchema, { partial: true });
  return withTransaction(async (s) => {
    const old = await mustAsset(assetId, s);
    const ws = data.warrantyStartDate ?? old.warrantyStartDate;
    const we = data.warrantyEndDate ?? old.warrantyEndDate;
    checkDateOrder(ws, we, 'Warranty end date cannot be before the start date.');
    await assertUnique(
      {
        serialNumber: data.serialNumber !== old.serialNumber ? data.serialNumber : undefined,
        assetTag: data.assetTag !== old.assetTag ? data.assetTag : undefined,
      },
      assetId,
    );
    const moved = data.currentBranchId && data.currentBranchId !== old.currentBranchId;
    if (moved) {
      await mustBranch(data.currentBranchId, s, { activeOnly: true });
      if (old.status !== ASSET_STATUS.AVAILABLE) {
        throw conflict('A laptop can be moved to another branch only while it is available in stock.', 'ASSET_NOT_MOVABLE');
      }
    }
    await assetRepo.update(assetId, { ...data, updatedAt: new Date() }, s);
    if (moved) {
      await recordHistory(s, {
        assetId,
        eventType: EVENT.TRANSFERRED,
        branchId: data.currentBranchId,
        previousStatus: old.status,
        newStatus: old.status,
        user,
        remarks: `Moved from ${old.currentBranchId} to ${data.currentBranchId}`,
        refs: { fromBranchId: old.currentBranchId },
      });
    }
    await recordAudit(s, {
      action: 'LAPTOP_UPDATED',
      entityType: 'ASSET',
      entityId: assetId,
      user,
      oldValues: Object.fromEntries(Object.keys(data).map((k) => [k, old[k] ?? null])),
      newValues: data,
    });
    return getEnrichedAsset(assetId, s);
  });
}

/** Manual status change. Retirement and sale remain explicit terminal actions. */
export async function changeAssetStatus(assetId, body, user) {
  const data = validate(body, statusChangeSchema);
  return withTransaction(async (s) => {
    const asset = await mustAsset(assetId, s);
    if (data.status === ASSET_STATUS.DEAD) {
      throw conflict('Use "Mark as Dead" to mark a laptop as dead.', 'USE_MARK_DEAD');
    }
    if (data.status === ASSET_STATUS.SOLD) {
      if (asset.status !== ASSET_STATUS.DEAD) {
        throw conflict('Only a dead laptop can be marked sold. Mark it dead first.', 'INVALID_SOLD_TRANSITION');
      }
      const soldInfo = {
        soldDate: new Date(),
        remarks: data.remarks ?? null,
        previousEmployeeId: asset.deadInfo?.previousEmployeeId ?? null,
        soldBy: user?.username ?? 'system',
      };
      await assetRepo.update(assetId, { status: ASSET_STATUS.SOLD, soldInfo, updatedAt: new Date() }, s);
      await recordHistory(s, {
        assetId,
        eventType: EVENT.MARKED_SOLD,
        branchId: asset.currentBranchId,
        previousStatus: ASSET_STATUS.DEAD,
        newStatus: ASSET_STATUS.SOLD,
        user,
        remarks: data.remarks,
      });
      await recordAudit(s, {
        action: 'LAPTOP_MARKED_SOLD',
        entityType: 'ASSET',
        entityId: assetId,
        user,
        oldValues: { status: ASSET_STATUS.DEAD },
        newValues: { status: ASSET_STATUS.SOLD },
        remarks: data.remarks,
      });
      return getEnrichedAsset(assetId, s);
    }
    if (data.status !== ASSET_STATUS.RETIRED || ![ASSET_STATUS.AVAILABLE, ASSET_STATUS.DEAD].includes(asset.status)) {
      throw conflict(
        'Only available or dead laptops can be retired. Use the assignment, replacement or repair workflow for other changes.',
        'INVALID_STATUS_CHANGE',
      );
    }
    await assetRepo.update(assetId, { status: ASSET_STATUS.RETIRED, updatedAt: new Date() }, s);
    await recordHistory(s, {
      assetId,
      eventType: EVENT.RETIRED,
      branchId: asset.currentBranchId,
      previousStatus: asset.status,
      newStatus: ASSET_STATUS.RETIRED,
      user,
      remarks: data.remarks,
    });
    await recordAudit(s, {
      action: 'LAPTOP_RETIRED',
      entityType: 'ASSET',
      entityId: assetId,
      user,
      oldValues: { status: asset.status },
      newValues: { status: ASSET_STATUS.RETIRED },
      remarks: data.remarks,
    });
    return getEnrichedAsset(assetId, s);
  });
}

export const retireAsset = (id, user) => changeAssetStatus(id, { status: 'RETIRED', remarks: 'Removed from active inventory' }, user);

export async function markDead(assetId, body, user) {
  const data = validate(body, markDeadSchema);
  return withTransaction(async (s) => {
    const asset = await mustAsset(assetId, s);
    await markAssetDead(s, asset, { ...data, user });
    return getEnrichedAsset(assetId, s);
  });
}

export async function listAssetsService(query, filters, paging) {
  const sort = parseSort(query.sort, ASSET_SORT_FIELDS, { assetId: 1, _id: 1 });
  return listAssets(filters, sort, paging);
}

/** Lifecycle timeline with employee and branch names resolved. */
export async function assetHistory(assetId) {
  await mustAsset(assetId);
  return historyRepo
    .collection()
    .aggregate([
      { $match: { assetId } },
      { $lookup: { from: 'employees', localField: 'employeeId', foreignField: 'employeeId', as: '_e' } },
      { $lookup: { from: 'branches', localField: 'branchId', foreignField: 'branchId', as: '_b' } },
      { $sort: { date: 1, historyId: 1 } },
      {
        $project: {
          _id: 0,
          historyId: 1,
          assetId: 1,
          eventType: 1,
          employeeId: 1,
          employeeName: { $first: '$_e.name' },
          employeeCode: { $first: '$_e.employeeCode' },
          branchId: 1,
          branchName: { $first: '$_b.name' },
          previousStatus: 1,
          newStatus: 1,
          date: 1,
          user: 1,
          remarks: 1,
        },
      },
    ])
    .toArray();
}

/** Everything about a laptop in one traceable response. */
export async function assetDetails(assetId) {
  const asset = await getEnrichedAsset(assetId);
  if (!asset) throw notFound('Laptop does not exist.', 'ASSET_NOT_FOUND');

  const repairs = await repairRepo
    .collection()
    .aggregate([
      { $match: { assetId } },
      { $lookup: { from: 'employees', localField: 'employeeId', foreignField: 'employeeId', as: '_e' } },
      { $sort: { repairDate: -1, repairId: -1 } },
      {
        $addFields: { employeeName: { $first: '$_e.name' }, employeeCode: { $first: '$_e.employeeCode' } },
      },
      { $project: { _id: 0, _e: 0 } },
    ])
    .toArray();

  const replacements = await replacementRepo
    .collection()
    .aggregate([
      { $match: { $or: [{ oldAssetId: assetId }, { newAssetId: assetId }] } },
      { $lookup: { from: 'employees', localField: 'employeeId', foreignField: 'employeeId', as: '_e' } },
      { $sort: { replacementDate: -1, replacementId: -1 } },
      {
        $project: {
          _id: 0,
          replacementId: 1,
          employeeId: 1,
          employeeName: { $first: '$_e.name' },
          oldAssetId: 1,
          newAssetId: 1,
          replacementDate: 1,
          reason: 1,
          oldAssetStatus: 1,
          remarks: 1,
        },
      },
    ])
    .toArray();

  const assignments = await assignmentRepo
    .collection()
    .aggregate([
      { $match: { assetId } },
      { $lookup: { from: 'employees', localField: 'employeeId', foreignField: 'employeeId', as: '_e' } },
      { $sort: { assignmentDate: -1, assignmentId: -1 } },
      {
        $addFields: { employeeName: { $first: '$_e.name' }, employeeCode: { $first: '$_e.employeeCode' } },
      },
      { $project: { _id: 0, _e: 0 } },
    ])
    .toArray();

  const lifecycle = await assetHistory(assetId);
  const asNew = replacements.find((r) => r.newAssetId === assetId);

  return {
    asset,
    currentAssignment: assignments.find((a) => a.status === 'ACTIVE') ?? null,
    repairSummary: {
      repairCount: asset.repairCount,
      totalRepairCost: asset.totalRepairCost,
      lastRepairDate: asset.lastRepairDate,
      latestRepairCost: asset.lastRepairCost,
      averageRepairCost: asset.avgRepairCost,
      highestRepairCost: asset.maxRepairCost,
    },
    replacementSummary: asNew
      ? {
          previousAssetId: asNew.oldAssetId,
          replacementDate: asNew.replacementDate,
          reason: asNew.reason,
          employeeName: asNew.employeeName,
        }
      : null,
    repairs,
    replacements,
    assignments,
    lifecycle,
  };
}
