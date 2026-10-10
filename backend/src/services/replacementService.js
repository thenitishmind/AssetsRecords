import { nextId, withTransaction } from '../config/db.js';
import {
  ASSET_STATUS,
  DEFAULT_OUTCOME_FOR_REASON,
  EVENT,
} from '../models/constants.js';
import { assetRepo, replacementRepo } from '../repositories/index.js';
import { dateRangeMatch } from '../repositories/assetQueries.js';
import { badRequest, conflict, notFound } from '../utils/errors.js';
import { containsRegex, paginate, parseSort, toDate } from '../utils/helpers.js';
import { replacementSchema } from '../validators/schemas.js';
import { validate } from '../validators/validate.js';
import { performAssign } from './assignmentService.js';
import {
  assertAssignable,
  markAssetDead,
  mustAsset,
  mustBranch,
  mustEmployee,
  recordAudit,
  recordHistory,
  releaseAssignment,
} from './core.js';
import { createRepairRecord } from './repairService.js';

/**
 * Old -> New laptop replacement. Everything below runs in ONE transaction:
 * old assignment closed, old asset moved, new asset assigned, employee updated,
 * replacement document, lifecycle events and audit log.
 */
export async function replaceLaptop(body, user) {
  const data = validate(body, replacementSchema);
  const replacementId = await nextId('replacement', 'RPL-', 6);

  return withTransaction(async (s) => {
    const employee = await mustEmployee(data.employeeId, s);
    if (employee.status !== 'ACTIVE') throw conflict('Employee is inactive.', 'EMPLOYEE_INACTIVE');
    await mustBranch(employee.branchId, s, { activeOnly: true });
    if (!employee.currentAssetId) {
      throw conflict('Employee has no assigned laptop to replace. Use a normal assignment instead.', 'NO_ACTIVE_ASSET');
    }
    if (employee.currentAssetId === data.newAssetId) {
      throw badRequest('The new laptop must be different from the current laptop.');
    }

    const oldAsset = await mustAsset(employee.currentAssetId, s);
    const newAsset = await mustAsset(data.newAssetId, s);
    assertAssignable(newAsset);
    if (newAsset.currentBranchId !== employee.branchId) {
      throw conflict('The new laptop belongs to a different branch than the employee.', 'BRANCH_MISMATCH');
    }

    const outcome = data.oldAssetStatus ?? DEFAULT_OUTCOME_FOR_REASON[data.reason];
    if (!outcome) throw badRequest('Select the status for the old laptop (Available, Repair, Dead or Retired).');
    if (data.reason === 'DEAD' && outcome !== ASSET_STATUS.DEAD) {
      throw badRequest('When the reason is "Dead", the old laptop status must be Dead.');
    }
    if (oldAsset.status === ASSET_STATUS.REPAIR && ![ASSET_STATUS.REPAIR, ASSET_STATUS.DEAD].includes(outcome)) {
      throw conflict(
        'The old laptop is still under repair. It can stay in Repair or be marked Dead.',
        'ASSET_UNDER_REPAIR',
      );
    }

    const date = data.replacementDate ?? new Date();

    // 1. close the old assignment (kept forever) and detach the employee
    await releaseAssignment(s, {
      assetId: oldAsset.assetId,
      employeeId: employee.employeeId,
      reason: 'REPLACED',
      statusAfter: outcome,
      date,
      remarks: data.remarks,
    });

    // 2. move the old laptop according to the chosen outcome
    const detached = { ...oldAsset, currentEmployeeId: null };
    const base = { currentEmployeeId: null, updatedAt: new Date() };
    if ([ASSET_STATUS.AVAILABLE, ASSET_STATUS.RETIRED].includes(outcome)) {
      await assetRepo.update(oldAsset.assetId, { ...base, status: outcome }, s);
    } else if (outcome === ASSET_STATUS.REPAIR) {
      await assetRepo.update(oldAsset.assetId, base, s);
      if (oldAsset.status !== ASSET_STATUS.REPAIR) {
        await createRepairRecord(s, {
          asset: detached,
          employeeId: employee.employeeId,
          user,
          skipHistory: true,
          data: {
            repairDate: date,
            issueType: 'Replaced - hardware issue',
            problemDescription: data.remarks ?? `Replaced by ${newAsset.assetId}`,
          },
        });
      }
    }

    await recordHistory(s, {
      assetId: oldAsset.assetId,
      eventType: EVENT.REPLACED_OUT,
      employeeId: employee.employeeId,
      branchId: employee.branchId,
      previousStatus: oldAsset.status,
      newStatus: outcome === ASSET_STATUS.DEAD ? oldAsset.status : outcome,
      date,
      user,
      remarks: `Replaced by ${newAsset.assetId} (${data.reason})${data.remarks ? ` - ${data.remarks}` : ''}`,
      refs: { replacementId, newAssetId: newAsset.assetId },
    });

    if (outcome === ASSET_STATUS.DEAD) {
      await markAssetDead(s, detached, {
        reason: data.deadReason || 'Replaced - dead',
        remarks: data.remarks,
        deadDate: date,
        user,
        previousEmployeeId: employee.employeeId,
        silentAudit: true,
      });
    }

    // 3. assign the new laptop
    const asg = await performAssign(s, {
      employee,
      asset: newAsset,
      branchId: employee.branchId,
      date,
      remarks: data.remarks,
      user,
      type: 'REPLACEMENT',
      replacementId,
      replacesAssetId: oldAsset.assetId,
    });

    // 4. replacement transaction record
    await replacementRepo.insert(
      {
        replacementId,
        employeeId: employee.employeeId,
        branchId: employee.branchId,
        oldAssetId: oldAsset.assetId,
        newAssetId: newAsset.assetId,
        newAssignmentId: asg.assignmentId,
        replacementDate: date,
        reason: data.reason,
        oldAssetStatus: outcome,
        status: 'COMPLETED',
        handoverReason: data.reason,
        handoverRemarks: data.remarks ?? null,
        remarks: data.remarks ?? null,
        createdBy: user?.username ?? 'system',
        createdAt: new Date(),
      },
      s,
    );

    await recordAudit(s, {
      action: 'LAPTOP_REPLACED',
      entityType: 'REPLACEMENT',
      entityId: replacementId,
      user,
      oldValues: { assetId: oldAsset.assetId, status: oldAsset.status },
      newValues: {
        employeeId: employee.employeeId,
        employeeName: employee.name,
        oldAssetId: oldAsset.assetId,
        oldAssetStatus: outcome,
        newAssetId: newAsset.assetId,
        reason: data.reason,
      },
      remarks: data.remarks,
    });

    return getReplacement(replacementId, s);
  });
}

function replacementStages() {
  const asset = (as, field) => ({ $lookup: { from: 'assets', localField: field, foreignField: 'assetId', as } });
  return [
    { $lookup: { from: 'employees', localField: 'employeeId', foreignField: 'employeeId', as: '_e' } },
    { $lookup: { from: 'branches', localField: 'branchId', foreignField: 'branchId', as: '_b' } },
    asset('_o', 'oldAssetId'),
    asset('_n', 'newAssetId'),
    {
      $lookup: {
        from: 'assignments',
        let: { oldAssetId: '$oldAssetId', employeeId: '$employeeId' },
        pipeline: [
          {
            $match: {
              $expr: { $and: [{ $eq: ['$assetId', '$$oldAssetId'] }, { $eq: ['$employeeId', '$$employeeId'] }] },
              status: 'RELEASED',
            },
          },
          { $sort: { assignmentDate: -1, assignmentId: -1 } },
          { $limit: 1 },
        ],
        as: '_oldAsg',
      },
    },
    { $lookup: { from: 'assignments', localField: 'newAssignmentId', foreignField: 'assignmentId', as: '_newAsg' } },
    {
      $addFields: {
        employeeName: { $first: '$_e.name' },
        employeeCode: { $first: '$_e.employeeCode' },
        designation: { $first: '$_e.designation' },
        handoverStatus: { $ifNull: ['$status', 'COMPLETED'] },
        handoverDate: '$replacementDate',
        previousAssignmentDate: { $first: '$_oldAsg.assignmentDate' },
        currentAssignmentDate: { $first: '$_newAsg.assignmentDate' },
        currentAssignmentId: { $first: '$_newAsg.assignmentId' },
        branchName: { $first: '$_b.name' },
        oldSerialNumber: { $first: '$_o.serialNumber' },
        oldModel: { $first: '$_o.model' },
        oldBrand: { $first: '$_o.brand' },
        oldAssetCurrentStatus: { $first: '$_o.status' },
        newSerialNumber: { $first: '$_n.serialNumber' },
        newModel: { $first: '$_n.model' },
        newBrand: { $first: '$_n.brand' },
      },
    },
    { $project: { _e: 0, _b: 0, _o: 0, _n: 0, _oldAsg: 0, _newAsg: 0 } },
  ];
}

export async function getReplacement(replacementId, session) {
  const [r] = await replacementRepo
    .collection()
    .aggregate([{ $match: { replacementId } }, ...replacementStages(), { $project: { _id: 0 } }], { session })
    .toArray();
  if (!r) throw notFound('Replacement record not found.', 'REPLACEMENT_NOT_FOUND');
  return r;
}

export function replacementPipeline(q) {
  const match = {};
  if (q.branchId) match.branchId = q.branchId;
  if (q.employeeId) match.employeeId = q.employeeId;
  if (q.assetId) match.$or = [{ oldAssetId: q.assetId }, { newAssetId: q.assetId }];
  if (q.reason) match.reason = q.reason;
  const dr = dateRangeMatch(toDate(q.dateFrom) || undefined, toDate(q.dateTo) || undefined);
  if (dr) match.replacementDate = dr;
  const stages = [{ $match: match }, ...replacementStages()];
  if (q.q) {
    const rx = containsRegex(q.q);
    stages.push({
      $match: {
        $or: ['employeeName', 'employeeCode', 'oldAssetId', 'newAssetId', 'oldSerialNumber', 'newSerialNumber', 'branchName'].map(
          (k) => ({ [k]: rx }),
        ),
      },
    });
  }
  stages.push({
    $sort: parseSort(q.sort, ['replacementDate', 'employeeName', 'branchName', 'reason'], { replacementDate: -1, _id: 1 }),
  });
  return stages;
}

export async function listReplacements(q, paging) {
  return paginate(replacementRepo.collection(), replacementPipeline(q), paging);
}
