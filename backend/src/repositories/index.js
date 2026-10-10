import { col } from '../config/db.js';
import { COLLECTIONS } from '../models/constants.js';

/**
 * Generic repository bound to one collection and its business-ID field.
 * Every method accepts an optional Mongo `session` so callers can compose
 * several repositories inside one transaction.
 */
function makeRepo(collectionName, idField) {
  const c = () => col(collectionName);
  const strip = { projection: { _id: 0 } };
  return {
    collection: c,
    idField,
    async find(id, session) {
      if (id === undefined || id === null) return null;
      return c().findOne({ [idField]: id }, { ...strip, session });
    },
    async findOne(filter, session) {
      return c().findOne(filter, { ...strip, session });
    },
    async findMany(filter, { sort, limit, session } = {}) {
      let cur = c().find(filter, { ...strip, session });
      if (sort) cur = cur.sort(sort);
      if (limit) cur = cur.limit(limit);
      return cur.toArray();
    },
    async insert(doc, session) {
      await c().insertOne({ ...doc }, { session });
      return doc;
    },
    async update(id, set, session) {
      return c().updateOne({ [idField]: id }, { $set: set }, { session });
    },
    /** Conditional update used for optimistic concurrency checks. */
    async updateWhere(filter, update, session) {
      return c().updateOne(filter, update, { session });
    },
    async count(filter = {}, session) {
      return c().countDocuments(filter, { session });
    },
    async exists(filter, session) {
      return (await c().countDocuments(filter, { limit: 1, session })) > 0;
    },
  };
}

export const branchRepo = makeRepo(COLLECTIONS.branches, 'branchId');
export const employeeRepo = makeRepo(COLLECTIONS.employees, 'employeeId');
export const assetRepo = makeRepo(COLLECTIONS.assets, 'assetId');
export const assignmentRepo = makeRepo(COLLECTIONS.assignments, 'assignmentId');
export const replacementRepo = makeRepo(COLLECTIONS.replacements, 'replacementId');
export const repairRepo = makeRepo(COLLECTIONS.repairs, 'repairId');
export const historyRepo = makeRepo(COLLECTIONS.assetHistory, 'historyId');
export const auditRepo = makeRepo(COLLECTIONS.auditLogs, 'auditId');
export const userRepo = makeRepo(COLLECTIONS.users, 'userId');
