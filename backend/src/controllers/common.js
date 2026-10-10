import { badRequest } from '../utils/errors.js';
import { parsePaging, toDate } from '../utils/helpers.js';

export const paging = (req, opts) => parsePaging(req.query, opts);

const num = (v, name) => {
  if (v === undefined || v === '') return undefined;
  const n = Number(v);
  if (!Number.isFinite(n)) throw badRequest(`${name} must be a number.`);
  return n;
};

const date = (v, name) => {
  if (v === undefined || v === '') return undefined;
  const d = toDate(v);
  if (!d) throw badRequest(`${name} is not a valid date.`);
  return d;
};

const str = (v) => (v === undefined || v === '' ? undefined : String(v));

/** Parses the common list/report filters from a query string. */
export function parseFilters(q) {
  return {
    branchId: str(q.branchId),
    employeeId: str(q.employeeId),
    status: str(q.status) ? String(q.status).toUpperCase() : undefined,
    q: str(q.q),
    repairCount: str(q.repairCount),
    minCost: num(q.minCost, 'Minimum cost'),
    maxCost: num(q.maxCost, 'Maximum cost'),
    dateFrom: date(q.dateFrom, 'From date'),
    dateTo: date(q.dateTo, 'To date'),
    reason: str(q.reason),
  };
}
