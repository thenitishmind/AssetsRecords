import { MongoClient } from 'mongodb';
import { env } from './env.js';
import { COLLECTIONS } from '../models/constants.js';

let client;
let db;
let txSupported = false;

export async function connectDb(dbName = env.dbName) {
  if (db) return db;
  client = new MongoClient(env.mongoUri, {
    serverSelectionTimeoutMS: 20000,
    maxPoolSize: 20,
    appName: 'AssetManagementApi',
  });
  await client.connect();
  db = client.db(dbName);
  const hello = await db.admin().command({ hello: 1 });
  // Transactions need a replica set (Atlas) or sharded cluster.
  txSupported = Boolean(hello.setName || hello.msg === 'isdbgrid');
  return db;
}

export async function closeDb() {
  if (client) await client.close();
  client = undefined;
  db = undefined;
}

export const getDb = () => {
  if (!db) throw new Error('Database not connected');
  return db;
};

export const col = (name) => getDb().collection(name);
export const supportsTransactions = () => txSupported;

/**
 * Runs `fn(session)` atomically. All writes made with the session either all
 * commit or all roll back. Falls back to non-transactional execution when the
 * deployment does not support transactions (e.g. standalone dev mongod).
 */
export async function withTransaction(fn) {
  if (!txSupported) return fn(undefined);
  const session = client.startSession();
  try {
    let result;
    await session.withTransaction(
      async () => {
        result = await fn(session);
      },
      { readConcern: { level: 'snapshot' }, writeConcern: { w: 'majority' } },
    );
    return result;
  } finally {
    await session.endSession();
  }
}

/** Sequential, human-readable business IDs (AST-000001, EMP-00001 ...). */
export async function nextId(counterName, prefix, width = 6) {
  const doc = await col(COLLECTIONS.counters).findOneAndUpdate(
    { _id: counterName },
    { $inc: { seq: 1 } },
    { upsert: true, returnDocument: 'after' },
  );
  return `${prefix}${String(doc.seq).padStart(width, '0')}`;
}

export async function ensureIndexes() {
  const C = COLLECTIONS;
  const idx = async (name, specs) => {
    for (const [keys, opts] of specs) await col(name).createIndex(keys, opts);
  };
  await idx(C.branches, [
    [{ branchId: 1 }, { unique: true }],
    [{ code: 1 }, { unique: true }],
    [{ status: 1 }, {}],
  ]);
  await idx(C.employees, [
    [{ employeeId: 1 }, { unique: true }],
    [{ employeeCode: 1 }, { unique: true }],
    [{ branchId: 1, status: 1 }, {}],
    [{ currentAssetId: 1 }, {}],
  ]);
  await idx(C.assets, [
    [{ assetId: 1 }, { unique: true }],
    [{ serialNumber: 1 }, { unique: true }],
    [{ assetTag: 1 }, { unique: true, partialFilterExpression: { assetTag: { $type: 'string' } } }],
    [{ status: 1, currentBranchId: 1 }, {}],
    [{ currentEmployeeId: 1 }, {}],
  ]);
  await idx(C.assignments, [
    [{ assignmentId: 1 }, { unique: true }],
    // Hard guarantees: one active assignment per asset and per employee.
    [{ assetId: 1 }, { unique: true, partialFilterExpression: { status: 'ACTIVE' }, name: 'one_active_per_asset' }],
    [{ employeeId: 1 }, { unique: true, partialFilterExpression: { status: 'ACTIVE' }, name: 'one_active_per_employee' }],
    [{ assetId: 1, assignmentDate: -1 }, {}],
    [{ employeeId: 1, assignmentDate: -1 }, {}],
  ]);
  await idx(C.replacements, [
    [{ replacementId: 1 }, { unique: true }],
    [{ employeeId: 1, replacementDate: -1 }, {}],
    [{ oldAssetId: 1 }, {}],
    [{ newAssetId: 1 }, {}],
    [{ branchId: 1 }, {}],
  ]);
  await idx(C.repairs, [
    [{ repairId: 1 }, { unique: true }],
    [{ assetId: 1, repairDate: -1 }, {}],
    [{ employeeId: 1 }, {}],
    [{ branchId: 1, status: 1 }, {}],
  ]);
  await idx(C.assetHistory, [
    [{ historyId: 1 }, { unique: true }],
    [{ assetId: 1, date: 1 }, {}],
    [{ employeeId: 1 }, {}],
  ]);
  await idx(C.users, [
    [{ username: 1 }, { unique: true }],
    [{ userId: 1 }, { unique: true }],
  ]);
  await idx(C.auditLogs, [
    [{ auditId: 1 }, { unique: true }],
    [{ date: -1 }, {}],
    [{ entityId: 1 }, {}],
  ]);
}
