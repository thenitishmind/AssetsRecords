import { nextId, withTransaction } from '../config/db.js';
import { ASSET_STATUS, ASSIGNMENT_STATUS, EVENT } from '../models/constants.js';
import { assetRepo, assignmentRepo, employeeRepo } from '../repositories/index.js';
import { dateRangeMatch } from '../repositories/assetQueries.js';
import { conflict, notFound } from '../utils/errors.js';
import { containsRegex, paginate, parseSort, toDate } from '../utils/helpers.js';
import { assignmentSchema } from '../validators/schemas.js';
import { validate } from '../validators/validate.js';
import {
  assertAssignable,
  mustAsset,
  mustBranch,
  mustEmployee,
  recordAudit,
  recordHistory,
  releaseAssignment,
} from './core.js';

/**
 * Atomically assigns an AVAILABLE asset to an employee (must run inside a
 * transaction). Shared by plain assignments and replacements.
 */
export async function performAssign(s, { employee, asset, branchId, date, remarks, user, type = 'NEW', replacementId = null, replacesAssetId = null }) {
  assertAssignable(asset);
  if (asset.currentBranchId !== branchId) {
    throw conflict('This laptop belongs to a different branch than the employee.', 'BRANCH_MISMATCH');
  }

  // Conditional updates give us optimistic concurrency even without transactions.
  const lock = await assetRepo.updateWhere(
    { assetId: asset.assetId, status: ASSET_STATUS.AVAILABLE },
    {
      $set: {
        status: ASSET_STATUS.ASSIGNED,
        currentEmployeeId: employee.employeeId,
        currentBranchId: branchId,
        updatedAt: new Date(),
      },
    },
    s,
  );
  if (lock.modifiedCount !== 1) throw conflict('Asset already assigned.', 'ASSET_ALREADY_ASSIGNED');

  const empLock = await employeeRepo.updateWhere(
    { employeeId: employee.employeeId, currentAssetId: null },
    { $set: { currentAssetId: asset.assetId, updatedAt: new Date() } },
    s,
  );
  if (empLock.modifiedCount !== 1) {
    throw conflict('Employee already has an active laptop.', 'EMPLOYEE_HAS_ASSET');
  }

  const assignmentId = await nextId('assignment', 'ASG-', 6);
  const doc = {
    assignmentId,
    assetId: asset.assetId,
    employeeId: employee.employeeId,
    branchId,
    assignmentDate: date ?? new Date(),
    releaseDate: null,
    assignedBy: user?.username ?? 'system',
    assignedByUserId: user?.userId ?? 'SYSTEM',
    remarks: remarks ?? null,
    status: ASSIGNMENT_STATUS.ACTIVE,
    type,
    replacementId,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await assignmentRepo.insert(doc, s);

  await recordHistory(s, {
    assetId: asset.assetId,
    eventType: type === 'REPLACEMENT' ? EVENT.REPLACEMENT_ASSIGNED : EVENT.ASSIGNED,
    employeeId: employee.employeeId,
    branchId,
    previousStatus: ASSET_STATUS.AVAILABLE,
    newStatus: ASSET_STATUS.ASSIGNED,
    date: doc.assignmentDate,
    user,
    remarks: type === 'REPLACEMENT' ? `Replacement for ${replacesAssetId}${remarks ? ` - ${remarks}` : ''}` : remarks,
    refs: { assignmentId, replacementId, replacesAssetId },
  });
  return doc;
}

export async function assignLaptop(body, user) {
  const data = validate(body, assignmentSchema);
  return withTransaction(async (s) => {
    const branch = await mustBranch(data.branchId, s, { activeOnly: true });
    const employee = await mustEmployee(data.employeeId, s);
    if (employee.status !== 'ACTIVE') throw conflict('Employee is inactive.', 'EMPLOYEE_INACTIVE');
    if (employee.branchId !== branch.branchId) {
      throw conflict('Employee does not belong to the selected branch.', 'BRANCH_MISMATCH');
    }
    if (employee.currentAssetId) {
      const old = await assetRepo.find(employee.currentAssetId, s);
      const asg = await assignmentRepo.findOne({ assetId: employee.currentAssetId, status: 'ACTIVE' }, s);
      throw conflict('Employee already has an assigned laptop.', 'EMPLOYEE_HAS_ASSET', {
        replacementRequired: true,
        oldAsset: old && {
          assetId: old.assetId,
          serialNumber: old.serialNumber,
          brand: old.brand,
          model: old.model,
          status: old.status,
          assignmentDate: asg?.assignmentDate ?? null,
        },
      });
    }
    const asset = await mustAsset(data.assetId, s);
    const doc = await performAssign(s, {
      employee,
      asset,
      branchId: branch.branchId,
      date: data.assignmentDate,
      remarks: data.remarks,
      user,
    });
    await recordAudit(s, {
      action: 'LAPTOP_ASSIGNED',
      entityType: 'ASSIGNMENT',
      entityId: doc.assignmentId,
      user,
      newValues: { assetId: asset.assetId, employeeId: employee.employeeId, branchId: branch.branchId },
      remarks: data.remarks,
    });
    return getAssignment(doc.assignmentId, s);
  });
}

/** Employee returns a laptop to stock (no replacement). */
export async function returnLaptop(assignmentId, body, user) {
  const returnDate = toDate(body?.returnDate) ?? new Date();
  const remarks = body?.remarks ? String(body.remarks).slice(0, 500) : null;
  return withTransaction(async (s) => {
    const asg = await assignmentRepo.find(assignmentId, s);
    if (!asg) throw notFound('Assignment not found.', 'ASSIGNMENT_NOT_FOUND');
    if (asg.status !== ASSIGNMENT_STATUS.ACTIVE) throw conflict('This assignment is already closed.', 'ASSIGNMENT_CLOSED');
    const asset = await mustAsset(asg.assetId, s);
    if (asset.status === ASSET_STATUS.REPAIR) {
      throw conflict('Laptop is currently under repair. Complete the repair before returning it.', 'ASSET_UNDER_REPAIR');
    }
    await releaseAssignment(s, {
      assetId: asset.assetId,
      employeeId: asg.employeeId,
      reason: 'RETURNED',
      statusAfter: ASSET_STATUS.AVAILABLE,
      date: returnDate,
      remarks,
    });
    await assetRepo.update(asset.assetId, { status: ASSET_STATUS.AVAILABLE, currentEmployeeId: null, updatedAt: new Date() }, s);
    await recordHistory(s, {
      assetId: asset.assetId,
      eventType: EVENT.RETURNED_TO_STOCK,
      employeeId: asg.employeeId,
      branchId: asset.currentBranchId,
      previousStatus: asset.status,
      newStatus: ASSET_STATUS.AVAILABLE,
      date: returnDate,
      user,
      remarks,
    });
    await recordAudit(s, {
      action: 'LAPTOP_RETURNED',
      entityType: 'ASSIGNMENT',
      entityId: assignmentId,
      user,
      oldValues: { status: 'ACTIVE' },
      newValues: { status: 'RELEASED', assetStatus: ASSET_STATUS.AVAILABLE },
      remarks,
    });
    return getAssignment(assignmentId, s);
  });
}

/** Only descriptive fields of an assignment may be edited (history stays intact). */
export async function updateAssignment(assignmentId, body, user) {
  const data = validate(body, { assignmentDate: assignmentSchema.assignmentDate, remarks: assignmentSchema.remarks }, { partial: true });
  return withTransaction(async (s) => {
    const old = await assignmentRepo.find(assignmentId, s);
    if (!old) throw notFound('Assignment not found.', 'ASSIGNMENT_NOT_FOUND');
    await assignmentRepo.update(assignmentId, { ...data, updatedAt: new Date() }, s);
    await recordAudit(s, {
      action: 'ASSIGNMENT_UPDATED',
      entityType: 'ASSIGNMENT',
      entityId: assignmentId,
      user,
      oldValues: Object.fromEntries(Object.keys(data).map((k) => [k, old[k] ?? null])),
      newValues: data,
    });
    return getAssignment(assignmentId, s);
  });
}

function assignmentStages() {
  return [
    { $lookup: { from: 'employees', localField: 'employeeId', foreignField: 'employeeId', as: '_e' } },
    { $lookup: { from: 'assets', localField: 'assetId', foreignField: 'assetId', as: '_a' } },
    { $lookup: { from: 'branches', localField: 'branchId', foreignField: 'branchId', as: '_b' } },
    {
      $addFields: {
        employeeName: { $first: '$_e.name' },
        employeeCode: { $first: '$_e.employeeCode' },
        employeeDesignation: { $first: '$_e.designation' },
        employeeDepartment: { $first: '$_e.department' },
        serialNumber: { $first: '$_a.serialNumber' },
        brand: { $first: '$_a.brand' },
        model: { $first: '$_a.model' },
        branchName: { $first: '$_b.name' },
        assetTitle: {
          $trim: { input: { $concat: [{ $ifNull: [{ $first: '$_a.brand' }, ''] }, ' ', { $ifNull: [{ $first: '$_a.model' }, ''] }] } },
        },
      },
    },
    { $project: { _e: 0, _a: 0, _b: 0 } },
  ];
}

export async function getAssignment(assignmentId, session) {
  const [a] = await assignmentRepo
    .collection()
    .aggregate([{ $match: { assignmentId } }, ...assignmentStages(), { $project: { _id: 0 } }], { session })
    .toArray();
  if (!a) throw notFound('Assignment not found.', 'ASSIGNMENT_NOT_FOUND');
  return a;
}

export async function listAssignments(q, paging) {
  const match = {};
  if (q.branchId) match.branchId = q.branchId;
  if (q.employeeId) match.employeeId = q.employeeId;
  if (q.assetId) match.assetId = q.assetId;
  if (q.status) match.status = q.status;
  const dr = dateRangeMatch(toDate(q.dateFrom) || undefined, toDate(q.dateTo) || undefined);
  if (dr) match.assignmentDate = dr;
  const stages = [{ $match: match }, ...assignmentStages()];
  if (q.q) {
    const rx = containsRegex(q.q);
    stages.push({ $match: { $or: ['employeeName', 'employeeCode', 'employeeDesignation', 'branchName', 'assetId', 'serialNumber', 'model'].map((k) => ({ [k]: rx })) } });
  }
  stages.push({
    $sort: parseSort(q.sort, ['assignmentDate', 'employeeName', 'assetId', 'status'], { assignmentDate: -1, _id: 1 }),
  });
  return paginate(assignmentRepo.collection(), stages, paging);
}
