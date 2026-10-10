import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { fixtures, startTestServer, supportsTransactions } from './helpers.js';

let t;
let fx;
before(async () => {
  t = await startTestServer();
  fx = fixtures(t.admin);
});
after(async () => t?.stop());

const assign = (api, branch, emp, asset, extra = {}) =>
  api.post('/assignments', { branchId: branch.branchId, employeeId: emp.employeeId, assetId: asset.assetId, ...extra });

const dash = async () => (await t.admin.get('/dashboard')).data;
const getAsset = async (id) => (await t.admin.get(`/assets/${id}`)).data;
const getEmp = async (id) => (await t.admin.get(`/employees/${id}`)).data;

test('transactions are available on this deployment', () => {
  assert.equal(supportsTransactions(), true, 'MongoDB Atlas (replica set) should support transactions');
});

describe('assignment', () => {
  test('assigns an available laptop and keeps every link consistent', async () => {
    const b = await fx.branch();
    const e = await fx.employee(b.branchId, { designation: 'Systems Engineer' });
    const a = await fx.asset(b.branchId);
    const before = await dash();

    const r = await assign(t.op, b, e, a, { remarks: 'New joiner' }); // operators may assign
    assert.equal(r.status, 201);
    assert.match(r.data.assignmentId, /^ASG-\d{6}$/);
    assert.equal(r.data.status, 'ACTIVE');

    const d = await getAsset(a.assetId);
    assert.equal(d.asset.status, 'ASSIGNED');
    assert.equal(d.asset.currentEmployeeId, e.employeeId);
    assert.equal(d.asset.employeeName, e.name);
    assert.equal(d.asset.employeeDesignation, e.designation);
    assert.equal(d.currentAssignment.assignmentId, r.data.assignmentId);
    assert.equal((await getEmp(e.employeeId)).currentAssetId, a.assetId);
    assert.equal((await getEmp(e.employeeId)).assignmentStatus, 'ASSIGNED');
    assert.equal((await t.admin.get(`/assignments/${r.data.assignmentId}`)).data.employeeDesignation, e.designation);

    const after = await dash();
    assert.equal(after.totals.assigned, before.totals.assigned + 1);
    assert.equal(after.totals.available, before.totals.available - 1);
    assert.equal(after.totals.totalAssets, before.totals.totalAssets);

    const br = await t.admin.get(`/branches/${b.branchId}`);
    assert.equal(br.data.assigned, 1);
    assert.equal(br.data.available, 0);
    assert.equal(br.data.employeeCount, 1);
  });

  test('rejects unavailable laptops with clear messages', async () => {
    const b = await fx.branch();
    const [e1, e2, e3, e4] = [await fx.employee(b.branchId), await fx.employee(b.branchId), await fx.employee(b.branchId), await fx.employee(b.branchId)];
    const taken = await fx.asset(b.branchId);
    const fixing = await fx.asset(b.branchId);
    const dead = await fx.asset(b.branchId);
    await assign(t.admin, b, e1, taken);
    await t.admin.post('/repairs', { assetId: fixing.assetId, issueType: 'Screen' });
    await t.admin.post(`/assets/${dead.assetId}/mark-dead`, { reason: 'Water damage' });

    const msg = async (emp, asset) => {
      const r = await assign(t.admin, b, emp, asset);
      assert.equal(r.status, 409);
      return r.data.error.message;
    };
    assert.equal(await msg(e2, taken), 'Asset already assigned.');
    assert.equal(await msg(e3, fixing), 'Laptop is currently under repair.');
    assert.equal(await msg(e4, dead), 'Dead laptop cannot be assigned.');
  });

  test('employee with a laptop gets a replacement-required response', async () => {
    const b = await fx.branch();
    const e = await fx.employee(b.branchId);
    const a = await fx.asset(b.branchId);
    const b2 = await fx.asset(b.branchId);
    await assign(t.admin, b, e, a);
    const r = await assign(t.admin, b, e, b2);
    assert.equal(r.status, 409);
    assert.equal(r.data.error.code, 'EMPLOYEE_HAS_ASSET');
    assert.equal(r.data.error.message, 'Employee already has an assigned laptop.');
    assert.equal(r.data.error.details.replacementRequired, true);
    assert.equal(r.data.error.details.oldAsset.assetId, a.assetId);
    assert.equal((await getAsset(b2.assetId)).asset.status, 'AVAILABLE'); // untouched
  });

  test('branch validation: employee/asset/branch must agree', async () => {
    const b1 = await fx.branch();
    const b2 = await fx.branch();
    const e = await fx.employee(b1.branchId);
    const aOther = await fx.asset(b2.branchId);
    const aSame = await fx.asset(b1.branchId);
    assert.equal((await assign(t.admin, b2, e, aSame)).data.error.message, 'Employee does not belong to the selected branch.');
    assert.equal((await assign(t.admin, b1, e, aOther)).data.error.message, 'This laptop belongs to a different branch than the employee.');
    assert.equal((await assign(t.admin, { branchId: 'BRN-9999' }, e, aSame)).data.error.message, 'Branch does not exist.');
  });

  test('return to stock closes assignment but keeps history', async () => {
    const b = await fx.branch();
    const e = await fx.employee(b.branchId);
    const a = await fx.asset(b.branchId);
    const asg = (await assign(t.admin, b, e, a)).data;
    const r = await t.admin.post(`/assignments/${asg.assignmentId}/return`, { remarks: 'Resigned' });
    assert.equal(r.status, 200);
    assert.equal(r.data.status, 'RELEASED');
    assert.equal((await getAsset(a.assetId)).asset.status, 'AVAILABLE');
    assert.equal((await getEmp(e.employeeId)).currentAssetId, null);
    const list = await t.admin.get(`/assignments?employeeId=${e.employeeId}`);
    assert.equal(list.data.total, 1);
    // can be reassigned afterwards
    const again = await assign(t.admin, b, e, a);
    assert.equal(again.status, 201);
  });
});

describe('replacement (old -> new laptop)', () => {
  async function setup() {
    const b = await fx.branch({ name: 'Branch A' });
    const rahul = await fx.employee(b.branchId, { name: 'Rahul Kumar', designation: 'Systems Engineer' });
    const A = await fx.asset(b.branchId);
    const B = await fx.asset(b.branchId);
    const asg = await assign(t.admin, b, rahul, A);
    assert.equal(asg.status, 201);
    return { b, rahul, A, B };
  }

  test('hardware issue: old laptop -> REPAIR, new -> ASSIGNED, full history preserved', async () => {
    const { b, rahul, A, B } = await setup();
    const before = await dash();

    const op = await t.op.post('/replacements', { employeeId: rahul.employeeId, newAssetId: B.assetId, reason: 'HARDWARE_ISSUE' });
    assert.equal(op.status, 403, 'operators cannot replace');

    const r = await t.admin.post('/replacements', {
      employeeId: rahul.employeeId, newAssetId: B.assetId, reason: 'HARDWARE_ISSUE', remarks: 'Keyboard failed',
    });
    assert.equal(r.status, 201);
    assert.equal(r.data.oldAssetId, A.assetId);
    assert.equal(r.data.newAssetId, B.assetId);
    assert.equal(r.data.oldAssetStatus, 'REPAIR');
    assert.equal(r.data.handoverStatus, 'COMPLETED');
    assert.equal(r.data.handoverDate != null, true);
    assert.equal(r.data.designation, 'Systems Engineer');
    assert.equal(r.data.oldSerialNumber, A.serialNumber);
    assert.equal(r.data.newSerialNumber, B.serialNumber);
    assert.equal(r.data.previousAssignmentDate != null, true);
    assert.equal(r.data.currentAssignmentDate != null, true);
    assert.equal(r.data.employeeName, 'Rahul Kumar');

    // current state
    assert.equal((await getEmp(rahul.employeeId)).currentAssetId, B.assetId);
    const dA = await getAsset(A.assetId);
    const dB = await getAsset(B.assetId);
    assert.equal(dA.asset.status, 'REPAIR');
    assert.equal(dA.asset.currentEmployeeId, null);
    assert.equal(dB.asset.status, 'ASSIGNED');
    assert.equal(dB.asset.currentEmployeeId, rahul.employeeId);
    assert.equal(dB.replacementSummary.previousAssetId, A.assetId);

    // assignment history contains A (released) and B (active)
    const asgs = (await t.admin.get(`/assignments?employeeId=${rahul.employeeId}`)).data.items;
    assert.equal(asgs.length, 2);
    assert.equal(asgs.find((x) => x.assetId === A.assetId).status, 'RELEASED');
    assert.equal(asgs.find((x) => x.assetId === B.assetId).status, 'ACTIVE');

    // employee view: current = B, previous = A, replacement = A -> B, repair auto-created for A
    const ea = (await t.admin.get(`/employees/${rahul.employeeId}/assets`)).data;
    assert.equal(ea.current.assetId, B.assetId);
    assert.deepEqual(ea.previous.map((p) => p.assetId), [A.assetId]);
    assert.equal(ea.previous[0].statusAfterRelease, 'REPAIR');
    assert.equal(ea.previous[0].releaseReason, 'REPLACED');
    assert.equal(ea.replacements.length, 1);
    assert.equal(ea.replacements[0].oldAssetId, A.assetId);
    assert.equal(ea.replacements[0].newAssetId, B.assetId);
    assert.equal(ea.repairs.length, 1);
    assert.equal(ea.repairs[0].assetId, A.assetId);

    // CURRENT asset report shows only B, never A
    const cur = (await t.admin.get(`/reports/assets?employeeId=${rahul.employeeId}`)).data;
    assert.deepEqual(cur.rows.map((x) => x.assetId), [B.assetId]);
    const allCur = (await t.admin.get(`/reports/assets?branchId=${b.branchId}`)).data;
    assert.ok(!allCur.rows.some((x) => x.assetId === A.assetId));
    assert.equal((await t.admin.get(`/assets?assigned=true&branchId=${b.branchId}`)).data.items.some((x) => x.assetId === A.assetId), false);

    // replacement report shows A -> B
    const rep = (await t.admin.get(`/reports/replacements?employeeId=${rahul.employeeId}`)).data;
    assert.equal(rep.rows.length, 1);
    assert.equal(rep.rows[0].oldAssetId, A.assetId);
    assert.equal(rep.rows[0].newAssetId, B.assetId);

    // dashboard: assigned unchanged (one out, one in), available -1, repair +1
    const after = await dash();
    assert.equal(after.totals.assigned, before.totals.assigned);
    assert.equal(after.totals.available, before.totals.available - 1);
    assert.equal(after.totals.repair, before.totals.repair + 1);
    assert.equal(after.totals.totalAssets, before.totals.totalAssets);

    // lifecycle of A keeps everything, in order
    const hA = (await t.admin.get(`/assets/${A.assetId}/history`)).data.items.map((x) => x.eventType);
    assert.deepEqual(hA, ['PURCHASED', 'ASSIGNED', 'REPLACED_OUT']);
    const hB = (await t.admin.get(`/assets/${B.assetId}/history`)).data.items.map((x) => x.eventType);
    assert.deepEqual(hB, ['PURCHASED', 'REPLACEMENT_ASSIGNED']);

    // audit log
    const logs = (await t.admin.get(`/audit-logs?action=LAPTOP_REPLACED`)).data.items;
    const log = logs.find((l) => l.newValues.employeeId === rahul.employeeId);
    assert.ok(log);
    assert.equal(log.newValues.oldAssetId, A.assetId);
    assert.equal(log.newValues.newAssetId, B.assetId);
    assert.equal(log.user.username, 'admin');
  });

  test('upgrade: old laptop returns to AVAILABLE and can be assigned again', async () => {
    const { b, rahul, A, B } = await setup();
    const r = await t.admin.post('/replacements', { employeeId: rahul.employeeId, newAssetId: B.assetId, reason: 'UPGRADE' });
    assert.equal(r.status, 201);
    assert.equal(r.data.oldAssetStatus, 'AVAILABLE');
    assert.equal((await getAsset(A.assetId)).asset.status, 'AVAILABLE');
    const other = await fx.employee(b.branchId);
    assert.equal((await assign(t.admin, b, other, A)).status, 201);
  });

  test('dead: old laptop moves to Dead category with previous employee', async () => {
    const { b, rahul, A, B } = await setup();
    const r = await t.admin.post('/replacements', { employeeId: rahul.employeeId, newAssetId: B.assetId, reason: 'DEAD', deadReason: 'Motherboard failure' });
    assert.equal(r.status, 201);
    const dA = (await getAsset(A.assetId)).asset;
    assert.equal(dA.status, 'DEAD');
    assert.equal(dA.deadInfo.reason, 'Motherboard failure');
    assert.equal(dA.deadInfo.previousEmployeeId, rahul.employeeId);
    const dead = (await t.admin.get(`/reports/dead?branchId=${b.branchId}`)).data.rows;
    assert.equal(dead[0].assetId, A.assetId);
    assert.equal(dead[0].displayEmployeeName, 'Rahul Kumar');
    assert.equal(dead[0].deadReason, 'Motherboard failure');
    // history shows A and B in the employee's trail
    const ea = (await t.admin.get(`/employees/${rahul.employeeId}/assets`)).data;
    assert.equal(ea.previous[0].statusAfterRelease, 'DEAD');
  });

  test('handover marks an old laptop dead before it can be sold', async () => {
    const { b, rahul, A, B } = await setup();
    const invalid = await t.admin.post('/replacements', {
      employeeId: rahul.employeeId,
      newAssetId: B.assetId,
      reason: 'UPGRADE',
      oldAssetStatus: 'SOLD',
    });
    assert.equal(invalid.status, 400);
    assert.equal((await getAsset(A.assetId)).asset.status, 'ASSIGNED');

    const r = await t.admin.post('/replacements', {
      employeeId: rahul.employeeId,
      newAssetId: B.assetId,
      reason: 'DEAD',
      deadReason: 'Beyond economical repair',
      remarks: 'Disposed after replacement',
    });
    assert.equal(r.status, 201);
    assert.equal(r.data.oldAssetStatus, 'DEAD');
    assert.equal((await getAsset(A.assetId)).asset.status, 'DEAD');
    const sold = await t.admin.put(`/assets/${A.assetId}/status`, { status: 'SOLD', remarks: 'Disposed after replacement' });
    assert.equal(sold.status, 200);
    assert.equal(sold.data.status, 'SOLD');
    assert.equal(sold.data.soldInfo.previousEmployeeId, rahul.employeeId);
    assert.equal(sold.data.deadInfo.reason, 'Beyond economical repair');
    assert.equal((await getAsset(B.assetId)).currentAssignment.employeeId, rahul.employeeId);
    assert.equal((await getEmp(rahul.employeeId)).currentAssetId, B.assetId);
    const record = (await t.admin.get(`/replacements?employeeId=${rahul.employeeId}`)).data.items[0];
    assert.equal(record.handoverStatus, 'COMPLETED');
    assert.equal(record.oldAssetCurrentStatus, 'SOLD');
    assert.equal(record.currentAssignmentId, r.data.newAssignmentId);
    const events = (await t.admin.get(`/assets/${A.assetId}/history`)).data.items.map((x) => x.eventType);
    assert.deepEqual(events, ['PURCHASED', 'ASSIGNED', 'REPLACED_OUT', 'MARKED_DEAD', 'MARKED_SOLD']);
  });

  test('invalid replacements change nothing', async () => {
    const { b, rahul, A, B } = await setup();
    const other = await fx.branch();
    const farAsset = await fx.asset(other.branchId);
    await t.admin.post('/repairs', { assetId: B.assetId, issueType: 'Screen' });

    const post = (body) => t.admin.post('/replacements', { employeeId: rahul.employeeId, reason: 'UPGRADE', ...body });
    assert.equal((await post({ newAssetId: B.assetId })).data.error.message, 'Laptop is currently under repair.');
    assert.equal((await post({ newAssetId: farAsset.assetId })).data.error.code, 'BRANCH_MISMATCH');
    assert.equal((await post({ newAssetId: A.assetId })).status, 400);
    assert.equal((await post({ newAssetId: 'AST-999999' })).status, 404);
    const spare = await fx.asset(b.branchId);
    const missingOutcome = await t.admin.post('/replacements', { employeeId: rahul.employeeId, newAssetId: spare.assetId, reason: 'OTHER' });
    assert.equal(missingOutcome.status, 400); // reason OTHER needs an explicit old-laptop status

    assert.equal((await getEmp(rahul.employeeId)).currentAssetId, A.assetId);
    assert.equal((await getAsset(A.assetId)).asset.status, 'ASSIGNED');
    assert.equal((await t.admin.get(`/replacements?employeeId=${rahul.employeeId}`)).data.total, 0);
  });

  test('replacing a laptop that is already in repair keeps its single open repair', async () => {
    const { rahul, A, B } = await setup();
    await t.admin.post('/repairs', { assetId: A.assetId, issueType: 'Battery' });
    const r = await t.admin.post('/replacements', { employeeId: rahul.employeeId, newAssetId: B.assetId, reason: 'HARDWARE_ISSUE' });
    assert.equal(r.status, 201);
    const ea = (await t.admin.get(`/employees/${rahul.employeeId}/assets`)).data;
    assert.equal(ea.repairs.length, 1);
    assert.equal((await getAsset(A.assetId)).asset.status, 'REPAIR');
  });
});

describe('repairs', () => {
  test('repair count, totals and statistics come from repair records', async () => {
    const b = await fx.branch();
    const e = await fx.employee(b.branchId, { designation: 'IT Support Engineer' });
    const a = await fx.asset(b.branchId);
    await assign(t.admin, b, e, a);

    const history = [['Keyboard replacement', 1500], ['Battery replacement', 3000], ['Screen replacement', 7500]];
    for (const [issue, cost] of history) {
      const rep = await t.op.post('/repairs', { assetId: a.assetId, issueType: issue, vendor: 'FixIt', estimatedCost: cost - 100 });
      assert.equal(rep.status, 201);
      assert.equal(rep.data.status, 'SENT_FOR_REPAIR');
      assert.equal(rep.data.employeeId, e.employeeId);
      assert.equal(rep.data.branchId, b.branchId);
      assert.equal(rep.data.employeeDesignation, 'IT Support Engineer');

      // while repairing: laptop is REPAIR, employee keeps link, cannot be assigned/repaired again
      assert.equal((await getAsset(a.assetId)).asset.status, 'REPAIR');
      assert.equal((await getEmp(e.employeeId)).currentAssetId, a.assetId);
      const dup = await t.op.post('/repairs', { assetId: a.assetId, issueType: 'Other' });
      assert.equal(dup.data.error.message, 'Laptop is currently under repair.');

      const id = rep.data.repairId;
      assert.equal((await t.op.put(`/repairs/${id}`, { status: 'UNDER_REPAIR' })).status, 200);
      const noCost = await t.op.put(`/repairs/${id}`, { status: 'REPAIRED' });
      assert.equal(noCost.status, 400);
      const done = await t.op.put(`/repairs/${id}`, { status: 'REPAIRED', finalCost: cost });
      assert.equal(done.status, 200);
      assert.ok(done.data.repairCompletionDate);
      assert.equal((await getAsset(a.assetId)).asset.status, 'ASSIGNED'); // back with the employee
      assert.equal((await t.op.put(`/repairs/${id}`, { status: 'UNDER_REPAIR' })).status, 409); // invalid transition
    }

    const d = await getAsset(a.assetId);
    assert.equal(d.repairSummary.repairCount, 3);
    assert.equal(d.repairSummary.totalRepairCost, 12000);
    assert.equal(d.repairSummary.latestRepairCost, 7500);
    assert.equal(d.repairSummary.averageRepairCost, 4000);
    assert.equal(d.repairSummary.highestRepairCost, 7500);
    assert.ok(d.repairSummary.lastRepairDate);
    assert.equal(d.repairs.length, 3);

    // detail screen shows previous repairs of the same laptop
    const rd = (await t.admin.get(`/repairs/${d.repairs[0].repairId}`)).data;
    assert.equal(rd.previousRepairs.length, 2);
    assert.equal(rd.asset.assetId, a.assetId);

    // repair report and dashboard total
    const rr = (await t.admin.get(`/reports/repairs?branchId=${b.branchId}`)).data;
    assert.equal(rr.rows[0].repairCount, 3);
    assert.equal(rr.rows[0].totalRepairCost, 12000);
    assert.equal(rr.rows[0].employeeName, e.name);
    assert.equal(rr.rows[0].employeeDesignation, 'IT Support Engineer');
    assert.equal((await dash()).repairCostByBranch.find((x) => x.branchId === b.branchId).cost, 12000);

    // lifecycle
    const h = (await t.admin.get(`/assets/${a.assetId}/history`)).data.items.map((x) => x.eventType);
    assert.deepEqual(h, ['PURCHASED', 'ASSIGNED', 'SENT_FOR_REPAIR', 'REPAIR_UPDATED', 'REPAIR_COMPLETED',
      'SENT_FOR_REPAIR', 'REPAIR_UPDATED', 'REPAIR_COMPLETED', 'SENT_FOR_REPAIR', 'REPAIR_UPDATED', 'REPAIR_COMPLETED']);
  });

  test('repair of a non-existing asset is rejected', async () => {
    const r = await t.admin.post('/repairs', { assetId: 'AST-424242', issueType: 'x' });
    assert.equal(r.status, 404);
  });

  test('repair filters: repair count & cost range on the repair report', async () => {
    const b = await fx.branch();
    const x = await fx.asset(b.branchId);
    const y = await fx.asset(b.branchId);
    for (const [asset, cost] of [[x, 500], [y, 2000], [y, 2500]]) {
      const rep = await t.admin.post('/repairs', { assetId: asset.assetId, issueType: 'Fix' });
      await t.admin.put(`/repairs/${rep.data.repairId}`, { status: 'REPAIRED', finalCost: cost });
    }
    const q = async (s) => (await t.admin.get(`/reports/repairs?branchId=${b.branchId}&${s}`)).data.rows.map((r) => r.assetId);
    assert.deepEqual(await q('repairCount=1'), [x.assetId]);
    assert.deepEqual(await q('repairCount=2%2B'), [y.assetId]);
    assert.deepEqual(await q('minCost=1000'), [y.assetId]);
    assert.deepEqual(await q('maxCost=1000'), [x.assetId]);
    assert.deepEqual(await q('dateFrom=2999-01-01'), []);
  });
});

describe('mark dead', () => {
  test('moves the laptop to Dead, frees the employee, keeps the record and history', async () => {
    const b = await fx.branch();
    const e = await fx.employee(b.branchId, { name: 'Dead Owner' });
    const a = await fx.asset(b.branchId, { purchasePrice: 55000 });
    await assign(t.admin, b, e, a);
    const rep = await t.admin.post('/repairs', { assetId: a.assetId, issueType: 'Motherboard' });
    await t.admin.put(`/repairs/${rep.data.repairId}`, { status: 'REPAIRED', finalCost: 9000 });
    const before = await dash();

    assert.equal((await t.op.post(`/assets/${a.assetId}/mark-dead`, { reason: 'x' })).status, 403);
    assert.equal((await t.admin.post(`/assets/${a.assetId}/mark-dead`, {})).status, 400); // reason required
    const r = await t.admin.post(`/assets/${a.assetId}/mark-dead`, { reason: 'Beyond economical repair', remarks: 'Not worth it' });
    assert.equal(r.status, 200);
    assert.equal(r.data.status, 'DEAD');
    assert.equal(r.data.currentEmployeeId, null);
    assert.equal(r.data.totalRepairCost, 9000);

    assert.equal((await getEmp(e.employeeId)).currentAssetId, null);
    const after = await dash();
    assert.equal(after.totals.dead, before.totals.dead + 1);
    assert.equal(after.totals.assigned, before.totals.assigned - 1);
    assert.equal(after.totals.totalAssets, before.totals.totalAssets); // never deleted

    const stockDead = (await t.admin.get('/stock/assets?status=DEAD')).data.items.map((x) => x.assetId);
    assert.ok(stockDead.includes(a.assetId));
    for (const s of ['AVAILABLE', 'ASSIGNED', 'REPAIR']) {
      assert.ok(!(await t.admin.get(`/stock/assets?status=${s}`)).data.items.some((x) => x.assetId === a.assetId));
    }
    const dr = (await t.admin.get(`/reports/dead?branchId=${b.branchId}`)).data.rows[0];
    assert.equal(dr.displayEmployeeName, 'Dead Owner');
    assert.equal(dr.purchasePrice, 55000);
    assert.equal(dr.totalRepairCost, 9000);
    assert.equal(dr.deadReason, 'Beyond economical repair');
    assert.equal(dr.deadRemarks, 'Not worth it');

    const h = (await t.admin.get(`/assets/${a.assetId}/history`)).data.items;
    assert.equal(h.at(-1).eventType, 'MARKED_DEAD');
    assert.equal(h.at(-1).previousStatus, 'ASSIGNED');
    assert.equal(h.at(-1).newStatus, 'DEAD');

    assert.equal((await t.admin.post(`/assets/${a.assetId}/mark-dead`, { reason: 'again' })).status, 409);
    assert.equal((await t.admin.post('/repairs', { assetId: a.assetId, issueType: 'x' })).data.error.message, 'Dead laptop cannot be sent for repair.');
  });

  test('a repair ending as DEAD marks the laptop dead', async () => {
    const b = await fx.branch();
    const a = await fx.asset(b.branchId);
    const rep = await t.admin.post('/repairs', { assetId: a.assetId, issueType: 'Water damage' });
    const u = await t.admin.put(`/repairs/${rep.data.repairId}`, { status: 'NOT_REPAIRABLE' });
    assert.equal(u.status, 200);
    assert.equal((await getAsset(a.assetId)).asset.status, 'REPAIR'); // stays until decided
    const d = await t.admin.put(`/repairs/${rep.data.repairId}`, { status: 'DEAD', remarks: 'Beyond repair' });
    assert.equal(d.status, 200);
    assert.equal(d.data.status, 'DEAD');
    assert.equal((await getAsset(a.assetId)).asset.status, 'DEAD');
  });

  test('retiring a dead laptop keeps it out of every active stock bucket', async () => {
    const b = await fx.branch();
    const a = await fx.asset(b.branchId);
    await t.admin.post(`/assets/${a.assetId}/mark-dead`, { reason: 'Physical damage' });
    assert.equal((await t.admin.put(`/assets/${a.assetId}/status`, { status: 'RETIRED' })).status, 200);
    const s = (await t.admin.get('/stock')).data.summary;
    assert.equal(s.total, s.available + s.assigned + s.repair + s.dead + s.sold + s.retired);
  });

  test('dead laptops can be sold, are counted separately, and cannot be assigned', async () => {
    const b = await fx.branch();
    const e = await fx.employee(b.branchId);
    const a = await fx.asset(b.branchId);
    await assign(t.admin, b, e, a);
    await t.admin.post(`/assets/${a.assetId}/mark-dead`, { reason: 'Beyond economical repair' });
    const soldBefore = (await dash()).totals.sold;

    const sold = await t.admin.put(`/assets/${a.assetId}/status`, { status: 'SOLD', remarks: 'Auctioned' });
    assert.equal(sold.status, 200);
    assert.equal(sold.data.status, 'SOLD');
    assert.equal(sold.data.soldInfo.remarks, 'Auctioned');
    assert.equal((await getEmp(e.employeeId)).assignmentStatus, 'NO_LAPTOP_ASSIGNED');

    const denied = await assign(t.admin, b, e, a);
    assert.equal(denied.status, 409);
    assert.equal(denied.data.error.code, 'ASSET_SOLD');
    assert.equal((await t.admin.post(`/assets/${a.assetId}/mark-dead`, { reason: 'again' })).status, 409);
    assert.equal((await t.admin.post('/repairs', { assetId: a.assetId, issueType: 'Screen' })).status, 409);
    assert.equal((await t.admin.get(`/stock/assets?status=AVAILABLE&branchId=${b.branchId}`)).data.items.some((x) => x.assetId === a.assetId), false);

    const stock = (await t.admin.get('/stock')).data;
    const branch = (await t.admin.get(`/branches/${b.branchId}`)).data;
    const dashboard = await dash();
    assert.equal(stock.summary.sold, soldBefore + 1);
    assert.equal(branch.sold, 1);
    assert.equal(dashboard.totals.sold, soldBefore + 1);
    assert.ok(dashboard.statusChart.some((x) => x.status === 'SOLD' && x.count === soldBefore + 1));
    assert.equal((await t.admin.get(`/reports/sold?branchId=${b.branchId}`)).data.rows[0].assetId, a.assetId);
    const history = (await t.admin.get(`/assets/${a.assetId}/history`)).data.items;
    assert.deepEqual(history.slice(-2).map((x) => x.eventType), ['MARKED_DEAD', 'MARKED_SOLD']);
  });
});

describe('stock, dashboard & reports consistency', () => {
  test('numbers always add up and match branch-wise figures', async () => {
    const d = await dash();
    const t0 = d.totals;
    assert.equal(t0.totalAssets, t0.available + t0.assigned + t0.repair + t0.dead + t0.sold + t0.retired);
    const sum = (k) => d.branchWise.reduce((n, r) => n + r[k], 0);
    for (const k of ['total', 'available', 'assigned', 'repair', 'dead', 'sold', 'retired']) {
      assert.equal(sum(k), k === 'total' ? t0.totalAssets : t0[k], `branch-wise ${k}`);
    }
    assert.equal(d.statusChart.reduce((n, s) => n + s.count, 0), t0.totalAssets);
    const stock = (await t.admin.get('/stock')).data;
    assert.equal(stock.summary.total, t0.totalAssets);
    assert.equal(t0.branches, (await t.admin.get('/branches?limit=200')).data.total);
    assert.equal(t0.employeesWithAssets, (await t.admin.get('/employees?hasAsset=true&limit=200')).data.total);
    const costFromRepairs = (await t.admin.get('/repairs?limit=200')).data.items.reduce((n, r) => n + (r.finalCost ?? 0), 0);
    assert.equal(t0.totalRepairCost, costFromRepairs);
    assert.ok(Array.isArray(d.repairCostByMonth) && d.repairCostByMonth.length >= 1);
  });

  test('stock report & exports (CSV/PDF) respect filters and roles', async () => {
    const b = await fx.branch({ name: 'Export Branch' });
    const e = await fx.employee(b.branchId, { name: 'Export Person' });
    const a = await fx.asset(b.branchId);
    await fx.asset(b.branchId);
    await assign(t.admin, b, e, a);

    const s = (await t.admin.get(`/reports/stock?branchId=${b.branchId}`)).data;
    assert.equal(s.rows.length, 1);
    assert.equal(s.rows[0].total, 2);
    assert.equal(s.rows[0].assigned, 1);
    assert.equal(s.rows[0].available, 1);
    assert.equal(s.totals.total, 2);

    const csv = await t.admin.get(`/reports/assets?branchId=${b.branchId}&format=csv`);
    assert.equal(csv.status, 200);
    assert.match(csv.headers.get('content-type'), /text\/csv/);
    const text = csv.data.toString('utf8');
    assert.match(text, /Employee Name,Employee Code,Branch,Designation,Asset ID/);
    assert.match(text, /Export Person/);
    assert.equal(text.trim().split('\r\n').length, 2); // header + 1 current asset only (filtered)

    const pdf = await t.admin.get(`/reports/stock?format=pdf`);
    assert.equal(pdf.status, 200);
    assert.equal(pdf.data.subarray(0, 4).toString(), '%PDF');

    assert.equal((await t.op.get(`/reports/assets?format=csv`)).status, 403); // operators can view, not export
    assert.equal((await t.op.get(`/reports/assets`)).status, 200);
    assert.equal((await t.admin.get('/reports/nope')).status, 404);
  });

  test('asset details connect branch -> employee -> repairs -> replacements -> lifecycle', async () => {
    const b = await fx.branch({ name: 'Delhi' });
    const e = await fx.employee(b.branchId, { name: 'Trace Me' });
    const old = await fx.asset(b.branchId);
    const neu = await fx.asset(b.branchId);
    await assign(t.admin, b, e, old);
    const rep = await t.admin.post('/repairs', { assetId: old.assetId, issueType: 'Fan' });
    await t.admin.put(`/repairs/${rep.data.repairId}`, { status: 'REPAIRED', finalCost: 1200 });
    await t.admin.post('/replacements', { employeeId: e.employeeId, newAssetId: neu.assetId, reason: 'UPGRADE' });

    const dNew = await getAsset(neu.assetId);
    assert.equal(dNew.asset.branchName, 'Delhi');
    assert.equal(dNew.asset.employeeName, 'Trace Me');
    assert.equal(dNew.replacementSummary.previousAssetId, old.assetId);
    const dOld = await getAsset(old.assetId);
    assert.equal(dOld.repairSummary.totalRepairCost, 1200);
    assert.equal(dOld.replacements[0].newAssetId, neu.assetId);
    assert.equal(dOld.asset.status, 'AVAILABLE');
    assert.ok(dOld.lifecycle.length >= 5);
    const brAssets = (await t.admin.get(`/branches/${b.branchId}/assets`)).data;
    assert.equal(brAssets.branch.totalAssets, 2);
    assert.equal(brAssets.assets.total, 2);
  });
});
