import { nextId } from '../config/db.js';
import {
  ASSET_STATUS,
  ASSIGNMENT_STATUS,
  EVENT,
  OPEN_REPAIR_STATUSES,
  REPAIR_STATUS,
} from '../models/constants.js';
import {
  assetRepo,
  assignmentRepo,
  auditRepo,
  branchRepo,
  employeeRepo,
  historyRepo,
  repairRepo,
} from '../repositories/index.js';
import { conflict, notFound } from '../utils/errors.js';

export const actorOf = (user) => ({
  userId: user?.userId ?? 'SYSTEM',
  username: user?.username ?? 'system',
  name: user?.name ?? 'System',
  role: user?.role ?? 'SYSTEM',
});

/** Writes an immutable lifecycle event for an asset. */
export async function recordHistory(session, e) {
  const historyId = await nextId('history', 'HST-', 7);
  await historyRepo.insert(
    {
      historyId,
      assetId: e.assetId,
      eventType: e.eventType,
      employeeId: e.employeeId ?? null,
      branchId: e.branchId ?? null,
      previousStatus: e.previousStatus ?? null,
      newStatus: e.newStatus ?? null,
      date: e.date ?? new Date(),
      user: e.user?.username ?? 'system',
      userId: e.user?.userId ?? 'SYSTEM',
      remarks: e.remarks ?? null,
      refs: e.refs ?? {},
      createdAt: new Date(),
    },
    session,
  );
  return historyId;
}

/** Writes an audit log entry. */
export async function recordAudit(session, a) {
  const auditId = await nextId('audit', 'AUD-', 7);
  await auditRepo.insert(
    {
      auditId,
      action: a.action,
      entityType: a.entityType,
      entityId: a.entityId,
      user: actorOf(a.user),
      oldValues: a.oldValues ?? null,
      newValues: a.newValues ?? null,
      remarks: a.remarks ?? null,
      date: new Date(),
    },
    session,
  );
  return auditId;
}

export async function mustBranch(id, session, { activeOnly = false } = {}) {
  const b = await branchRepo.find(id, session);
  if (!b) throw notFound('Branch does not exist.', 'BRANCH_NOT_FOUND');
  if (activeOnly && b.status !== 'ACTIVE') throw conflict('Branch is inactive.', 'BRANCH_INACTIVE');
  return b;
}

export async function mustEmployee(id, session) {
  const e = await employeeRepo.find(id, session);
  if (!e) throw notFound('Employee does not exist.', 'EMPLOYEE_NOT_FOUND');
  return e;
}

export async function mustAsset(id, session) {
  const a = await assetRepo.find(id, session);
  if (!a) throw notFound('Laptop does not exist.', 'ASSET_NOT_FOUND');
  return a;
}

/** Friendly message explaining why an asset in `status` cannot be assigned. */
export function assertAssignable(asset) {
  switch (asset.status) {
    case ASSET_STATUS.AVAILABLE:
      return;
    case ASSET_STATUS.ASSIGNED:
      throw conflict('Asset already assigned.', 'ASSET_ALREADY_ASSIGNED');
    case ASSET_STATUS.REPAIR:
      throw conflict('Laptop is currently under repair.', 'ASSET_UNDER_REPAIR');
    case ASSET_STATUS.DEAD:
      throw conflict('Dead laptop cannot be assigned.', 'ASSET_DEAD');
    case ASSET_STATUS.SOLD:
      throw conflict('Sold laptop cannot be assigned.', 'ASSET_SOLD');
    default:
      throw conflict('Retired laptop cannot be assigned.', 'ASSET_RETIRED');
  }
}

/**
 * Closes an employee's ACTIVE assignment and clears employee.currentAssetId.
 * The assignment document is preserved forever (status RELEASED).
 */
export async function releaseAssignment(session, { assetId, employeeId, reason, statusAfter, date, remarks }) {
  const when = date ?? new Date();
  await assignmentRepo.updateWhere(
    { assetId, employeeId, status: ASSIGNMENT_STATUS.ACTIVE },
    {
      $set: {
        status: ASSIGNMENT_STATUS.RELEASED,
        releaseDate: when,
        releaseReason: reason,
        assetStatusAfter: statusAfter,
        releaseRemarks: remarks ?? null,
        updatedAt: new Date(),
      },
    },
    session,
  );
  await employeeRepo.updateWhere(
    { employeeId, currentAssetId: assetId },
    { $set: { currentAssetId: null, updatedAt: new Date() } },
    session,
  );
}

/**
 * Moves an asset to DEAD. Releases its employee assignment, closes any open
 * repair and writes lifecycle history. The asset record is never deleted.
 */
export async function markAssetDead(session, asset, { reason, remarks, deadDate, user, previousEmployeeId, silentAudit = false }) {
  if (asset.status === ASSET_STATUS.DEAD) throw conflict('Laptop is already marked dead.', 'ASSET_ALREADY_DEAD');
  if (asset.status === ASSET_STATUS.SOLD) throw conflict('A sold laptop cannot be marked dead.', 'ASSET_SOLD');
  if (asset.status === ASSET_STATUS.RETIRED) throw conflict('A retired laptop cannot be marked dead.', 'ASSET_RETIRED');

  const when = deadDate ?? new Date();
  const prevEmp = asset.currentEmployeeId ?? previousEmployeeId ?? null;

  if (asset.currentEmployeeId) {
    await releaseAssignment(session, {
      assetId: asset.assetId,
      employeeId: asset.currentEmployeeId,
      reason: 'DEAD',
      statusAfter: ASSET_STATUS.DEAD,
      date: when,
      remarks,
    });
  }

  const deadInfo = {
    deadDate: when,
    reason,
    remarks: remarks ?? null,
    previousEmployeeId: prevEmp,
    markedBy: user?.username ?? 'system',
  };
  await assetRepo.update(
    asset.assetId,
    { status: ASSET_STATUS.DEAD, currentEmployeeId: null, deadInfo, updatedAt: new Date() },
    session,
  );

  await repairRepo.collection().updateMany(
    { assetId: asset.assetId, status: { $in: OPEN_REPAIR_STATUSES } },
    { $set: { status: REPAIR_STATUS.DEAD, repairCompletionDate: when, updatedAt: new Date() } },
    { session },
  );

  await recordHistory(session, {
    assetId: asset.assetId,
    eventType: EVENT.MARKED_DEAD,
    employeeId: prevEmp,
    branchId: asset.currentBranchId,
    previousStatus: asset.status,
    newStatus: ASSET_STATUS.DEAD,
    date: when,
    user,
    remarks: reason + (remarks ? ` - ${remarks}` : ''),
  });
  if (!silentAudit) {
    await recordAudit(session, {
      action: 'LAPTOP_MARKED_DEAD',
      entityType: 'ASSET',
      entityId: asset.assetId,
      user,
      oldValues: { status: asset.status, employeeId: asset.currentEmployeeId },
      newValues: { status: ASSET_STATUS.DEAD, reason },
      remarks,
    });
  }
}

/** Fields never exposed in API responses. */
export const publicUser = (u) =>
  u && {
    userId: u.userId,
    username: u.username,
    name: u.name,
    role: u.role,
    status: u.status,
    createdAt: u.createdAt,
    updatedAt: u.updatedAt,
    lastLoginAt: u.lastLoginAt ?? null,
  };
