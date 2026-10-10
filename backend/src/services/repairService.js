import { nextId, withTransaction } from '../config/db.js';
import {
  ASSET_STATUS,
  EVENT,
  OPEN_REPAIR_STATUSES,
  REPAIR_STATUS,
  REPAIR_TRANSITIONS,
} from '../models/constants.js';
import { assetRepo, repairRepo } from '../repositories/index.js';
import { dateRangeMatch, getEnrichedAsset } from '../repositories/assetQueries.js';
import { badRequest, conflict, notFound } from '../utils/errors.js';
import { containsRegex, paginate, parseSort, toDate } from '../utils/helpers.js';
import { repairCreateSchema, repairUpdateSchema } from '../validators/schemas.js';
import { checkDateOrder, validate } from '../validators/validate.js';
import { markAssetDead, mustAsset, recordAudit, recordHistory } from './core.js';

/** Creates a repair record and moves the asset to REPAIR (inside a transaction). */
export async function createRepairRecord(s, { asset, data, user, status = REPAIR_STATUS.SENT_FOR_REPAIR, employeeId, skipHistory = false }) {
  if (asset.status === ASSET_STATUS.REPAIR) throw conflict('Laptop is currently under repair.', 'ASSET_UNDER_REPAIR');
  if (asset.status === ASSET_STATUS.DEAD) throw conflict('Dead laptop cannot be sent for repair.', 'ASSET_DEAD');
  if (asset.status === ASSET_STATUS.SOLD) throw conflict('Sold laptop cannot be sent for repair.', 'ASSET_SOLD');
  if (asset.status === ASSET_STATUS.RETIRED) throw conflict('Retired laptop cannot be sent for repair.', 'ASSET_RETIRED');

  const lock = await assetRepo.updateWhere(
    { assetId: asset.assetId, status: { $in: [ASSET_STATUS.AVAILABLE, ASSET_STATUS.ASSIGNED] } },
    { $set: { status: ASSET_STATUS.REPAIR, updatedAt: new Date() } },
    s,
  );
  if (lock.modifiedCount !== 1) throw conflict('Laptop is currently under repair.', 'ASSET_UNDER_REPAIR');

  const repairId = await nextId('repair', 'RPR-', 6);
  const doc = {
    repairId,
    assetId: asset.assetId,
    employeeId: employeeId !== undefined ? employeeId : asset.currentEmployeeId ?? null,
    branchId: asset.currentBranchId,
    repairDate: data.repairDate ?? new Date(),
    issueType: data.issueType,
    problemDescription: data.problemDescription ?? null,
    vendor: data.vendor ?? null,
    status,
    estimatedCost: data.estimatedCost ?? null,
    finalCost: null,
    repairStartDate: null,
    repairCompletionDate: null,
    warranty: data.warranty ?? null,
    remarks: data.remarks ?? null,
    previousAssetStatus: asset.status,
    createdBy: user?.username ?? 'system',
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await repairRepo.insert(doc, s);
  if (skipHistory) return doc;
  await recordHistory(s, {
    assetId: asset.assetId,
    eventType: EVENT.SENT_FOR_REPAIR,
    employeeId: doc.employeeId,
    branchId: doc.branchId,
    previousStatus: asset.status,
    newStatus: ASSET_STATUS.REPAIR,
    date: doc.repairDate,
    user,
    remarks: `${doc.issueType}${doc.vendor ? ` (vendor: ${doc.vendor})` : ''}`,
    refs: { repairId },
  });
  return doc;
}

export async function createRepair(body, user) {
  const data = validate(body, repairCreateSchema);
  return withTransaction(async (s) => {
    const asset = await mustAsset(data.assetId, s);
    const doc = await createRepairRecord(s, { asset, data, user });
    await recordAudit(s, {
      action: 'REPAIR_CREATED',
      entityType: 'REPAIR',
      entityId: doc.repairId,
      user,
      newValues: { assetId: doc.assetId, issueType: doc.issueType, vendor: doc.vendor, estimatedCost: doc.estimatedCost },
    });
    return getRepair(doc.repairId, s);
  });
}

export async function updateRepair(repairId, body, user) {
  const data = validate(body, repairUpdateSchema, { partial: true });
  return withTransaction(async (s) => {
    const old = await repairRepo.find(repairId, s);
    if (!old) throw notFound('Repair record not found.', 'REPAIR_NOT_FOUND');
    const asset = await mustAsset(old.assetId, s);

    const newStatus = data.status && data.status !== old.status ? data.status : null;
    if (newStatus && !REPAIR_TRANSITIONS[old.status].includes(newStatus)) {
      throw conflict(`Repair cannot move from ${old.status} to ${newStatus}.`, 'INVALID_REPAIR_TRANSITION');
    }
    const set = { ...data, updatedAt: new Date() };
    delete set.status;

    const start = set.repairStartDate ?? old.repairStartDate;
    const end = set.repairCompletionDate ?? old.repairCompletionDate;
    checkDateOrder(start, end, 'Completion date cannot be before the start date.');

    if (newStatus === REPAIR_STATUS.UNDER_REPAIR && !old.repairStartDate && !set.repairStartDate) {
      set.repairStartDate = new Date();
    }
    if (newStatus === REPAIR_STATUS.REPAIRED) {
      const finalCost = set.finalCost ?? old.finalCost;
      if (finalCost === null || finalCost === undefined) {
        throw badRequest('Final repair cost is required to complete a repair.');
      }
      set.repairCompletionDate = set.repairCompletionDate ?? new Date();
    }
    if (newStatus === REPAIR_STATUS.DEAD) {
      set.repairCompletionDate = set.repairCompletionDate ?? new Date();
    }
    if (newStatus) set.status = newStatus;

    await repairRepo.update(repairId, set, s);

    if (newStatus === REPAIR_STATUS.REPAIRED && asset.status === ASSET_STATUS.REPAIR) {
      const stillOpen = await repairRepo.exists(
        { assetId: asset.assetId, repairId: { $ne: repairId }, status: { $in: OPEN_REPAIR_STATUSES } },
        s,
      );
      if (!stillOpen) {
        const restored = asset.currentEmployeeId ? ASSET_STATUS.ASSIGNED : ASSET_STATUS.AVAILABLE;
        await assetRepo.update(asset.assetId, { status: restored, updatedAt: new Date() }, s);
        await recordHistory(s, {
          assetId: asset.assetId,
          eventType: EVENT.REPAIR_COMPLETED,
          employeeId: asset.currentEmployeeId,
          branchId: asset.currentBranchId,
          previousStatus: ASSET_STATUS.REPAIR,
          newStatus: restored,
          date: set.repairCompletionDate,
          user,
          remarks: `${old.issueType} repaired. Cost: ${set.finalCost ?? old.finalCost}`,
          refs: { repairId },
        });
      }
    } else if (newStatus === REPAIR_STATUS.DEAD) {
      if ([ASSET_STATUS.DEAD, ASSET_STATUS.SOLD, ASSET_STATUS.RETIRED].includes(asset.status)) {
        await repairRepo.update(repairId, { repairCompletionDate: set.repairCompletionDate ?? new Date() }, s);
      } else {
        await markAssetDead(s, asset, {
          reason: data.remarks || old.remarks || 'Beyond economical repair',
          remarks: `Repair ${repairId} ended as dead`,
          user,
          silentAudit: false,
        });
      }
    } else if (newStatus) {
      await recordHistory(s, {
        assetId: asset.assetId,
        eventType: EVENT.REPAIR_UPDATED,
        employeeId: old.employeeId,
        branchId: old.branchId,
        previousStatus: asset.status,
        newStatus: asset.status,
        user,
        remarks: `Repair ${repairId}: ${old.status} -> ${newStatus}`,
        refs: { repairId },
      });
    }

    await recordAudit(s, {
      action: newStatus ? 'REPAIR_STATUS_UPDATED' : 'REPAIR_UPDATED',
      entityType: 'REPAIR',
      entityId: repairId,
      user,
      oldValues: Object.fromEntries(Object.keys(data).map((k) => [k, old[k] ?? null])),
      newValues: data,
    });
    return getRepair(repairId, s);
  });
}

function repairStages() {
  return [
    { $lookup: { from: 'assets', localField: 'assetId', foreignField: 'assetId', as: '_a' } },
    { $lookup: { from: 'employees', localField: 'employeeId', foreignField: 'employeeId', as: '_e' } },
    { $lookup: { from: 'branches', localField: 'branchId', foreignField: 'branchId', as: '_b' } },
    {
      $addFields: {
        serialNumber: { $first: '$_a.serialNumber' },
        brand: { $first: '$_a.brand' },
        model: { $first: '$_a.model' },
        assetStatus: { $first: '$_a.status' },
        employeeName: { $first: '$_e.name' },
        employeeCode: { $first: '$_e.employeeCode' },
        employeeDesignation: { $first: '$_e.designation' },
        branchName: { $first: '$_b.name' },
      },
    },
    { $project: { _a: 0, _e: 0, _b: 0 } },
  ];
}

export async function getRepair(repairId, session) {
  const [r] = await repairRepo
    .collection()
    .aggregate([{ $match: { repairId } }, ...repairStages(), { $project: { _id: 0 } }], { session })
    .toArray();
  if (!r) throw notFound('Repair record not found.', 'REPAIR_NOT_FOUND');
  return r;
}

/** Repair detail + the laptop + all other repairs of the same laptop. */
export async function repairDetails(repairId) {
  const repair = await getRepair(repairId);
  const asset = await getEnrichedAsset(repair.assetId);
  const previousRepairs = await repairRepo
    .collection()
    .aggregate([
      { $match: { assetId: repair.assetId, repairId: { $ne: repairId } } },
      { $sort: { repairDate: -1, repairId: -1 } },
      { $project: { _id: 0 } },
    ])
    .toArray();
  return { repair, asset, previousRepairs };
}

export async function listRepairs(q, paging) {
  const match = {};
  if (q.branchId) match.branchId = q.branchId;
  if (q.employeeId) match.employeeId = q.employeeId;
  if (q.assetId) match.assetId = q.assetId;
  if (q.status) match.status = { $in: String(q.status).split(',') };
  if (q.open === 'true') match.status = { $in: OPEN_REPAIR_STATUSES };
  const dr = dateRangeMatch(toDate(q.dateFrom) || undefined, toDate(q.dateTo) || undefined);
  if (dr) match.repairDate = dr;
  if (q.minCost !== undefined || q.maxCost !== undefined) {
    match.finalCost = {};
    if (q.minCost !== undefined) match.finalCost.$gte = Number(q.minCost);
    if (q.maxCost !== undefined) match.finalCost.$lte = Number(q.maxCost);
  }
  const stages = [{ $match: match }, ...repairStages()];
  if (q.q) {
    const rx = containsRegex(q.q);
    stages.push({
      $match: {
        $or: ['repairId', 'assetId', 'serialNumber', 'model', 'employeeName', 'employeeDesignation', 'vendor', 'issueType', 'branchName'].map(
          (k) => ({ [k]: rx }),
        ),
      },
    });
  }
  stages.push({
    $sort: parseSort(q.sort, ['repairDate', 'finalCost', 'status', 'assetId', 'vendor'], { repairDate: -1, _id: 1 }),
  });
  return paginate(repairRepo.collection(), stages, paging);
}
