import { auditRepo } from '../repositories/index.js';
import { dateRangeMatch } from '../repositories/assetQueries.js';
import { containsRegex, paginate, toDate } from '../utils/helpers.js';

export async function listAuditLogs(q, paging) {
  const match = {};
  if (q.action) match.action = q.action;
  if (q.entityType) match.entityType = q.entityType;
  if (q.entityId) match.entityId = q.entityId;
  if (q.username) match['user.username'] = containsRegex(q.username);
  const dr = dateRangeMatch(toDate(q.dateFrom) || undefined, toDate(q.dateTo) || undefined);
  if (dr) match.date = dr;
  if (q.q) {
    const rx = containsRegex(q.q);
    match.$or = [{ action: rx }, { entityId: rx }, { 'user.username': rx }, { remarks: rx }];
  }
  return paginate(auditRepo.collection(), [{ $match: match }, { $sort: { date: -1, auditId: -1 } }], paging);
}
