import { nextId, withTransaction } from '../config/db.js';
import { assetRepo, assignmentRepo, employeeRepo, repairRepo, replacementRepo } from '../repositories/index.js';
import { getEnrichedAsset } from '../repositories/assetQueries.js';
import { conflict, notFound } from '../utils/errors.js';
import { containsRegex, paginate, parseSort } from '../utils/helpers.js';
import { employeeSchema } from '../validators/schemas.js';
import { validate } from '../validators/validate.js';
import { mustBranch, mustEmployee, recordAudit } from './core.js';

function employeeStages() {
  return [
    { $lookup: { from: 'branches', localField: 'branchId', foreignField: 'branchId', as: '_b' } },
    { $lookup: { from: 'assets', localField: 'currentAssetId', foreignField: 'assetId', as: '_a' } },
    {
      $lookup: {
        from: 'assignments',
        let: { employeeId: '$employeeId' },
        pipeline: [
          { $match: { $expr: { $eq: ['$employeeId', '$$employeeId'] }, status: 'ACTIVE' } },
          { $sort: { assignmentDate: -1, assignmentId: -1 } },
          { $limit: 1 },
        ],
        as: '_asg',
      },
    },
    {
      $addFields: {
        branchName: { $first: '$_b.name' },
        branchCode: { $first: '$_b.code' },
        currentAssetSerial: { $first: '$_a.serialNumber' },
        currentAssetModel: { $first: '$_a.model' },
        currentAssetBrand: { $first: '$_a.brand' },
        currentAssetStatus: { $first: '$_a.status' },
        currentAssetTitle: {
          $trim: { input: { $concat: [{ $ifNull: [{ $first: '$_a.brand' }, ''] }, ' ', { $ifNull: [{ $first: '$_a.model' }, ''] }] } },
        },
        assignmentDate: { $first: '$_asg.assignmentDate' },
        assignmentStatus: {
          $cond: [{ $ifNull: ['$currentAssetId', false] }, 'ASSIGNED', 'NO_LAPTOP_ASSIGNED'],
        },
        handoverStatus: {
          $cond: [{ $ifNull: ['$currentAssetId', false] }, 'CURRENT_ASSIGNMENT', 'NO_ACTIVE_ASSIGNMENT'],
        },
      },
    },
    { $project: { _b: 0, _a: 0, _asg: 0 } },
  ];
}

export async function getEmployee(employeeId, session) {
  const [e] = await employeeRepo
    .collection()
    .aggregate([{ $match: { employeeId } }, ...employeeStages(), { $project: { _id: 0 } }], { session })
    .toArray();
  if (!e) throw notFound('Employee does not exist.', 'EMPLOYEE_NOT_FOUND');
  return e;
}

export async function createEmployee(body, user) {
  const data = validate(body, employeeSchema);
  await mustBranch(data.branchId, undefined, { activeOnly: true });
  if (await employeeRepo.exists({ employeeCode: data.employeeCode })) {
    throw conflict('Employee code already exists.', 'DUPLICATE_EMPLOYEE_CODE');
  }
  const employeeId = await nextId('employee', 'EMP-', 5);
  const doc = {
    employeeId,
    ...data,
    department: data.department ?? null,
    designation: data.designation ?? null,
    contactNumber: data.contactNumber ?? null,
    email: data.email ?? null,
    joiningDate: data.joiningDate ?? null,
    status: data.status ?? 'ACTIVE',
    currentAssetId: null,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await withTransaction(async (s) => {
    await employeeRepo.insert(doc, s);
    await recordAudit(s, { action: 'EMPLOYEE_CREATED', entityType: 'EMPLOYEE', entityId: employeeId, user, newValues: doc });
  });
  return getEmployee(employeeId);
}

export async function updateEmployee(employeeId, body, user) {
  const data = validate(body, employeeSchema, { partial: true });
  return withTransaction(async (s) => {
    const old = await mustEmployee(employeeId, s);
    if (
      data.employeeCode &&
      data.employeeCode !== old.employeeCode &&
      (await employeeRepo.exists({ employeeCode: data.employeeCode, employeeId: { $ne: employeeId } }, s))
    ) {
      throw conflict('Employee code already exists.', 'DUPLICATE_EMPLOYEE_CODE');
    }
    if (data.branchId && data.branchId !== old.branchId) {
      await mustBranch(data.branchId, s, { activeOnly: true });
      if (old.currentAssetId) {
        throw conflict(
          'Employee has an active laptop. Return or replace it before moving the employee to another branch.',
          'EMPLOYEE_HAS_ASSET',
        );
      }
    }
    if (data.status === 'INACTIVE' && old.currentAssetId) {
      throw conflict('Employee has an active laptop. Return it before deactivating the employee.', 'EMPLOYEE_HAS_ASSET');
    }
    await employeeRepo.update(employeeId, { ...data, updatedAt: new Date() }, s);
    await recordAudit(s, {
      action: 'EMPLOYEE_UPDATED',
      entityType: 'EMPLOYEE',
      entityId: employeeId,
      user,
      oldValues: Object.fromEntries(Object.keys(data).map((k) => [k, old[k] ?? null])),
      newValues: data,
    });
    return getEmployee(employeeId, s);
  });
}

export const deactivateEmployee = (id, user) => updateEmployee(id, { status: 'INACTIVE' }, user);

export async function listEmployees(q, paging) {
  const pre = {};
  if (q.branchId) pre.branchId = q.branchId;
  if (q.status) pre.status = q.status;
  if (q.department) pre.department = containsRegex(q.department);
  if (q.hasAsset === 'true') pre.currentAssetId = { $ne: null };
  if (q.hasAsset === 'false') pre.currentAssetId = null;
  const stages = [];
  if (Object.keys(pre).length) stages.push({ $match: pre });
  stages.push(...employeeStages());
  if (q.q) {
    const rx = containsRegex(q.q);
    stages.push({
      $match: {
        $or: ['name', 'employeeCode', 'employeeId', 'email', 'branchName', 'currentAssetId', 'currentAssetSerial'].map(
          (k) => ({ [k]: rx }),
        ),
      },
    });
  }
  const sort = parseSort(
    q.sort,
    ['name', 'employeeCode', 'branchName', 'department', 'designation', 'joiningDate', 'createdAt'],
    { name: 1, _id: 1 },
  );
  stages.push({ $sort: sort });
  return paginate(employeeRepo.collection(), stages, paging);
}

/**
 * Full employee asset picture used by the employee detail tabs:
 * current asset, previous assets, repair history and replacement history.
 */
export async function employeeAssets(employeeId) {
  const employee = await getEmployee(employeeId);
  const current = employee.currentAssetId ? await getEnrichedAsset(employee.currentAssetId) : null;

  const assetLookup = (as, field) => ({
    $lookup: { from: 'assets', localField: field, foreignField: 'assetId', as },
  });

  const previous = await assignmentRepo
    .collection()
    .aggregate([
      { $match: { employeeId, status: 'RELEASED' } },
      assetLookup('_a', 'assetId'),
      { $sort: { assignmentDate: -1, assignmentId: -1 } },
      {
        $project: {
          _id: 0,
          assignmentId: 1,
          assetId: 1,
          serialNumber: { $first: '$_a.serialNumber' },
          brand: { $first: '$_a.brand' },
          model: { $first: '$_a.model' },
          assignmentDate: 1,
          releaseDate: 1,
          releaseReason: 1,
          statusAfterRelease: '$assetStatusAfter',
          currentAssetStatus: { $first: '$_a.status' },
          remarks: '$releaseRemarks',
        },
      },
    ])
    .toArray();

  const repairs = await repairRepo
    .collection()
    .aggregate([
      { $match: { employeeId } },
      assetLookup('_a', 'assetId'),
      { $sort: { repairDate: -1, repairId: -1 } },
      {
        $project: {
          _id: 0,
          repairId: 1,
          assetId: 1,
          serialNumber: { $first: '$_a.serialNumber' },
          model: { $first: '$_a.model' },
          repairDate: 1,
          issueType: 1,
          problemDescription: 1,
          vendor: 1,
          finalCost: 1,
          estimatedCost: 1,
          status: 1,
        },
      },
    ])
    .toArray();

  const replacements = await replacementRepo
    .collection()
    .aggregate([
      { $match: { employeeId } },
      assetLookup('_o', 'oldAssetId'),
      assetLookup('_n', 'newAssetId'),
      { $sort: { replacementDate: -1, replacementId: -1 } },
      {
        $project: {
          _id: 0,
          replacementId: 1,
          oldAssetId: 1,
          newAssetId: 1,
          oldSerialNumber: { $first: '$_o.serialNumber' },
          newSerialNumber: { $first: '$_n.serialNumber' },
          oldModel: { $first: '$_o.model' },
          newModel: { $first: '$_n.model' },
          replacementDate: 1,
          reason: 1,
          oldAssetStatus: 1,
          remarks: 1,
        },
      },
    ])
    .toArray();

  return { employee, current, previous, repairs, replacements };
}

export async function employeeAssetCount() {
  return assetRepo.count({ currentEmployeeId: { $ne: null } });
}
