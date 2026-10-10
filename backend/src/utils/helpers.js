export const escapeRegex = (s) => String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

export const containsRegex = (s) => new RegExp(escapeRegex(String(s).trim()), 'i');

export function toDate(value) {
  if (value === undefined || value === null || value === '') return null;
  const d = value instanceof Date ? value : new Date(value);
  return Number.isNaN(d.getTime()) ? undefined : d;
}

export const now = () => new Date();

export function parsePaging(query, { defaultLimit = 25, maxLimit = 200 } = {}) {
  const page = Math.max(1, parseInt(query.page, 10) || 1);
  const limit = Math.min(maxLimit, Math.max(1, parseInt(query.limit, 10) || defaultLimit));
  return { page, limit, skip: (page - 1) * limit };
}

/** Parse a sort expression like "name:asc" restricted to an allow-list. */
export function parseSort(sort, allowed, fallback) {
  const [field, dir] = String(sort || '').split(':');
  if (allowed.includes(field)) return { [field]: dir === 'desc' ? -1 : 1, _id: 1 };
  return fallback;
}

/** Run a pipeline with $facet pagination. */
export async function paginate(collection, pipeline, { page, limit, skip }, options = {}) {
  const [res] = await collection
    .aggregate(
      [
        ...pipeline,
        {
          $facet: {
            items: [{ $skip: skip }, { $limit: limit }, { $project: { _id: 0 } }],
            total: [{ $count: 'n' }],
          },
        },
      ],
      options,
    )
    .toArray();
  const total = res?.total?.[0]?.n ?? 0;
  return { items: res?.items ?? [], total, page, limit, hasMore: skip + (res?.items?.length ?? 0) < total };
}

export function csvCell(v) {
  if (v === null || v === undefined) return '';
  let s = v instanceof Date ? v.toISOString().slice(0, 10) : String(v);
  if (/^[=+\-@]/.test(s) && Number.isNaN(Number(s))) s = `'${s}`; // CSV injection guard
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}
