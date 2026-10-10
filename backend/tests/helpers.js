// Integration-test harness: runs the real Express app against a DEDICATED test
// database on the configured MongoDB deployment (never the production DB).
process.env.NODE_ENV = 'test';

const { env } = await import('../src/config/env.js');
const { connectDb, closeDb, getDb, ensureIndexes, supportsTransactions } = await import('../src/config/db.js');
const { createApp } = await import('../src/app.js');
const { createUser } = await import('../src/services/authService.js');
const bcrypt = (await import('bcryptjs')).default;
const { userRepo } = await import('../src/repositories/index.js');

export { supportsTransactions };

export async function startTestServer() {
  const testDb = process.env.MONGODB_TEST_DB || 'asset_management_test';
  if (testDb === env.dbName) throw new Error('Refusing to run tests against the primary database.');
  await connectDb(testDb);
  await getDb().dropDatabase();
  await ensureIndexes();

  const now = new Date();
  await userRepo.insert({
    userId: 'USR-9001', username: 'admin', name: 'Test Admin', role: 'ADMIN', status: 'ACTIVE',
    passwordHash: await bcrypt.hash('Admin#12345', 4), createdAt: now, updatedAt: now,
  });
  await createUser({ username: 'operator', name: 'Test Operator', password: 'Operator#123', role: 'OPERATOR' }, { userId: 'USR-9001', username: 'admin' });

  const app = createApp({ loginLimit: 1000 });
  const server = await new Promise((resolve) => {
    const s = app.listen(0, '127.0.0.1', () => resolve(s));
  });
  const base = `http://127.0.0.1:${server.address().port}/api`;

  async function call(method, path, body, token) {
    const res = await fetch(base + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const ct = res.headers.get('content-type') || '';
    const data = ct.includes('json') ? await res.json() : Buffer.from(await res.arrayBuffer());
    return { status: res.status, data, headers: res.headers };
  }

  const login = async (username, password) => (await call('POST', '/auth/login', { username, password })).data.token;
  const adminToken = await login('admin', 'Admin#12345');
  const opToken = await login('operator', 'Operator#123');

  const as = (token) => ({
    get: (p) => call('GET', p, undefined, token),
    post: (p, b) => call('POST', p, b ?? {}, token),
    put: (p, b) => call('PUT', p, b ?? {}, token),
    del: (p) => call('DELETE', p, undefined, token),
  });

  return {
    admin: as(adminToken),
    op: as(opToken),
    anon: as(undefined),
    call,
    async stop() {
      await new Promise((r) => server.close(r));
      await getDb().dropDatabase();
      await closeDb();
    },
  };
}

/** Convenience factory for fixtures. */
export function fixtures(api) {
  let n = 0;
  return {
    async branch(over = {}) {
      n += 1;
      const r = await api.post('/branches', { name: `Branch ${n}`, code: `BR${n}${Date.now() % 100000}`, city: 'Delhi', state: 'Delhi', ...over });
      if (r.status !== 201) throw new Error(`branch fixture failed: ${JSON.stringify(r.data)}`);
      return r.data;
    },
    async employee(branchId, over = {}) {
      n += 1;
      const r = await api.post('/employees', { employeeCode: `E${n}${Date.now() % 100000}`, name: `Employee ${n}`, branchId, department: 'IT', ...over });
      if (r.status !== 201) throw new Error(`employee fixture failed: ${JSON.stringify(r.data)}`);
      return r.data;
    },
    async asset(branchId, over = {}) {
      n += 1;
      const r = await api.post('/assets', { serialNumber: `SN${n}${Date.now() % 1000000}`, brand: 'Dell', model: 'Latitude 5440', currentBranchId: branchId, purchasePrice: 60000, ...over });
      if (r.status !== 201) throw new Error(`asset fixture failed: ${JSON.stringify(r.data)}`);
      return r.data;
    },
  };
}
