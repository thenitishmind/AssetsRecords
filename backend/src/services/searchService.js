import { listAssets } from '../repositories/assetQueries.js';
import { listBranches } from './branchService.js';
import { listEmployees } from './employeeService.js';

/**
 * Global search across assets (id, tag, serial, brand, model), employees
 * (name, code) and branches (name, code).
 */
export async function globalSearch(q) {
  const term = String(q || '').trim();
  if (term.length < 2) return { query: term, exactAssetId: null, assets: [], employees: [], branches: [] };
  const paging = (limit) => ({ page: 1, limit, skip: 0 });
  const [assets, employees, branches] = await Promise.all([
    listAssets({ q: term }, { assetId: 1, _id: 1 }, paging(20)),
    listEmployees({ q: term }, paging(20)),
    listBranches({ q: term }, paging(10)),
  ]);
  const exact = assets.items.find(
    (a) =>
      a.assetId.toLowerCase() === term.toLowerCase() ||
      a.serialNumber?.toLowerCase() === term.toLowerCase() ||
      a.assetTag?.toLowerCase() === term.toLowerCase(),
  );
  return {
    query: term,
    exactAssetId: exact?.assetId ?? null,
    assets: assets.items,
    employees: employees.items,
    branches: branches.items,
  };
}
