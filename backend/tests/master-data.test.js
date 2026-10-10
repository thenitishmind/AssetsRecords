import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { fixtures, startTestServer } from './helpers.js';

let t;
let fx;
before(async () => {
  t = await startTestServer();
  fx = fixtures(t.admin);
});
after(async () => t?.stop());

describe('authentication & roles', () => {
  test('requests without a token are rejected', async () => {
    assert.equal((await t.anon.get('/branches')).status, 401);
  });
  test('wrong password gives a generic error', async () => {
    const r = await t.call('POST', '/auth/login', { username: 'admin', password: 'nope' });
    assert.equal(r.status, 401);
    assert.equal(r.data.error.message, 'Invalid username or password.');
  });
  test('operator cannot create branches / employees / assets', async () => {
    assert.equal((await t.op.post('/branches', { name: 'X', code: 'X1', city: 'a', state: 'b' })).status, 403);
    assert.equal((await t.op.post('/employees', {})).status, 403);
    assert.equal((await t.op.post('/assets', {})).status, 403);
    assert.equal((await t.op.get('/audit-logs')).status, 403);
  });
  test('password hash is never returned', async () => {
    const r = await t.admin.get('/users');
    assert.equal(r.status, 200);
    assert.ok(r.data.items.every((u) => u.passwordHash === undefined));
  });
});

describe('branch management', () => {
  test('create, unique code, list with live stats, update', async () => {
    const b = await fx.branch({ name: 'Delhi', code: 'del001' });
    assert.match(b.branchId, /^BRN-\d{4}$/);
    assert.equal(b.code, 'DEL001'); // normalised
    assert.equal(b.totalAssets, 0);

    const dup = await t.admin.post('/branches', { name: 'Delhi 2', code: 'DEL001', city: 'Delhi', state: 'Delhi' });
    assert.equal(dup.status, 409);
    assert.equal(dup.data.error.message, 'Branch code already exists.');

    const upd = await t.admin.put(`/branches/${b.branchId}`, { city: 'New Delhi' });
    assert.equal(upd.status, 200);
    assert.equal(upd.data.city, 'New Delhi');

    const list = await t.admin.get('/branches?q=del');
    assert.ok(list.data.items.some((x) => x.branchId === b.branchId));
  });
  test('validation errors are friendly', async () => {
    const r = await t.admin.post('/branches', { name: '', code: 'A', city: 'x', state: 'y' });
    assert.equal(r.status, 400);
    assert.equal(r.data.error.message, 'Branch name is required.');
  });
  test('a branch holding stock cannot be deactivated', async () => {
    const b = await fx.branch();
    await fx.asset(b.branchId);
    const r = await t.admin.del(`/branches/${b.branchId}`);
    assert.equal(r.status, 409);
    assert.equal(r.data.error.code, 'BRANCH_IN_USE');
  });
  test('an empty branch is soft-deleted (record kept)', async () => {
    const b = await fx.branch();
    const r = await t.admin.del(`/branches/${b.branchId}`);
    assert.equal(r.status, 200);
    assert.equal(r.data.status, 'INACTIVE');
    assert.equal((await t.admin.get(`/branches/${b.branchId}`)).status, 200);
  });
});

describe('employee management', () => {
  test('employee must belong to an existing branch', async () => {
    const r = await t.admin.post('/employees', { employeeCode: 'EZ1', name: 'Ghost', branchId: 'BRN-9999' });
    assert.equal(r.status, 404);
    assert.equal(r.data.error.message, 'Branch does not exist.');
  });
  test('create, populate branch name, unique code', async () => {
    const b = await fx.branch({ name: 'Mumbai' });
    const e = await fx.employee(b.branchId, { name: 'Rahul Kumar', employeeCode: 'emp-100' });
    assert.match(e.employeeId, /^EMP-\d{5}$/);
    assert.equal(e.employeeCode, 'EMP-100');
    assert.equal(e.branchName, 'Mumbai');
    assert.equal(e.currentAssetId, null);
    assert.equal((e.branch ?? undefined), undefined);
    const dup = await t.admin.post('/employees', { employeeCode: 'EMP-100', name: 'Dup', branchId: b.branchId });
    assert.equal(dup.status, 409);
    assert.equal(dup.data.error.message, 'Employee code already exists.');
  });
  test('invalid email rejected', async () => {
    const b = await fx.branch();
    const r = await t.admin.post('/employees', { employeeCode: 'EM9', name: 'A', branchId: b.branchId, email: 'not-an-email' });
    assert.equal(r.status, 400);
  });
});

describe('asset master', () => {
  test('auto asset id, unique serial and tag, initial lifecycle', async () => {
    const b = await fx.branch();
    const a = await fx.asset(b.branchId, { serialNumber: 'sn-unique-1', assetTag: 'tag-1' });
    assert.match(a.assetId, /^AST-\d{6}$/);
    assert.equal(a.status, 'AVAILABLE');
    assert.equal(a.serialNumber, 'SN-UNIQUE-1');
    assert.equal(a.repairCount, 0);
    assert.equal(a.branchName, b.name);

    const dup = await t.admin.post('/assets', { serialNumber: 'SN-UNIQUE-1', brand: 'HP', model: 'x', currentBranchId: b.branchId });
    assert.equal(dup.status, 409);
    assert.equal(dup.data.error.message, 'Serial number already exists.');

    const dupTag = await t.admin.post('/assets', { serialNumber: 'other', assetTag: 'TAG-1', brand: 'HP', model: 'x', currentBranchId: b.branchId });
    assert.equal(dupTag.status, 409);

    const hist = await t.admin.get(`/assets/${a.assetId}/history`);
    assert.equal(hist.data.items.length, 1);
    assert.equal(hist.data.items[0].eventType, 'PURCHASED');
    assert.equal(hist.data.items[0].newStatus, 'AVAILABLE');
  });
  test('asset requires an existing branch', async () => {
    const r = await t.admin.post('/assets', { serialNumber: 'ZZZ1', brand: 'HP', model: 'x', currentBranchId: 'BRN-9999' });
    assert.equal(r.status, 404);
  });
  test('editing creates an audit log; status cannot be forced to ASSIGNED', async () => {
    const b = await fx.branch();
    const a = await fx.asset(b.branchId);
    const u = await t.admin.put(`/assets/${a.assetId}`, { ram: '16 GB' });
    assert.equal(u.status, 200);
    assert.equal(u.data.ram, '16 GB');
    const logs = await t.admin.get(`/audit-logs?entityId=${a.assetId}`);
    assert.ok(logs.data.items.some((l) => l.action === 'LAPTOP_UPDATED' && l.oldValues.ram === null && l.newValues.ram === '16 GB'));
    const s = await t.admin.put(`/assets/${a.assetId}/status`, { status: 'ASSIGNED' });
    assert.equal(s.status, 409);
  });
  test('available laptop can be retired and is then unassignable', async () => {
    const b = await fx.branch();
    const e = await fx.employee(b.branchId);
    const a = await fx.asset(b.branchId);
    assert.equal((await t.admin.put(`/assets/${a.assetId}/status`, { status: 'RETIRED' })).status, 200);
    const r = await t.admin.post('/assignments', { branchId: b.branchId, employeeId: e.employeeId, assetId: a.assetId });
    assert.equal(r.status, 409);
    assert.equal(r.data.error.message, 'Retired laptop cannot be assigned.');
  });
});

describe('search & filters', () => {
  test('global search by asset id, serial, employee name/code and branch', async () => {
    const b = await fx.branch({ name: 'Searchville' });
    const e = await fx.employee(b.branchId, { name: 'Zed Searchable', employeeCode: 'SRCH-1' });
    const a = await fx.asset(b.branchId, { serialNumber: 'SEARCH-SN-77', model: 'ThinkPad Z99' });
    await t.admin.post('/assignments', { branchId: b.branchId, employeeId: e.employeeId, assetId: a.assetId });

    const byId = await t.admin.get(`/search?q=${a.assetId}`);
    assert.equal(byId.data.exactAssetId, a.assetId);
    assert.equal(byId.data.assets[0].employeeName, 'Zed Searchable');
    assert.equal(byId.data.assets[0].branchName, 'Searchville');

    assert.equal((await t.admin.get('/search?q=search-sn-77')).data.assets.length, 1);
    assert.equal((await t.admin.get('/search?q=thinkpad%20z99')).data.assets.length, 1);
    assert.equal((await t.admin.get('/search?q=SRCH-1')).data.employees.length, 1);
    assert.ok((await t.admin.get('/search?q=Searchville')).data.branches.length >= 1);
  });

  test('asset filters: branch, status, employee, repair count, repair cost', async () => {
    const b = await fx.branch();
    const e = await fx.employee(b.branchId);
    const a1 = await fx.asset(b.branchId);
    const a2 = await fx.asset(b.branchId);
    const a3 = await fx.asset(b.branchId);
    await t.admin.post('/assignments', { branchId: b.branchId, employeeId: e.employeeId, assetId: a1.assetId });

    // a2: two repairs (1,500 + 3,000); a3: none
    for (const [issue, cost] of [['Keyboard', 1500], ['Battery', 3000]]) {
      const r = await t.admin.post('/repairs', { assetId: a2.assetId, issueType: issue, vendor: 'FixIt' });
      assert.equal(r.status, 201);
      const done = await t.admin.put(`/repairs/${r.data.repairId}`, { status: 'REPAIRED', finalCost: cost });
      assert.equal(done.status, 200);
    }
    const q = (s) => t.admin.get(`/assets?branchId=${b.branchId}&${s}`);
    assert.equal((await q('')).data.total, 3);
    assert.equal((await q('status=ASSIGNED')).data.total, 1);
    assert.equal((await q(`employeeId=${e.employeeId}`)).data.items[0].assetId, a1.assetId);
    assert.equal((await q('repairCount=0')).data.total, 2);
    assert.equal((await q('repairCount=2')).data.items[0].assetId, a2.assetId);
    assert.equal((await q('repairCount=1')).data.total, 0);
    assert.equal((await q('repairCount=2%2B')).data.total, 1);
    assert.equal((await q('minCost=4000')).data.items[0].assetId, a2.assetId);
    assert.equal((await q('minCost=4501')).data.total, 0);
    assert.equal((await q('assigned=true')).data.total, 1);
    assert.equal(a3.status, 'AVAILABLE');
  });
});
