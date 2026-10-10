import * as branches from '../services/branchService.js';
import * as employees from '../services/employeeService.js';
import * as assets from '../services/assetService.js';
import * as assignments from '../services/assignmentService.js';
import * as replacements from '../services/replacementService.js';
import * as repairs from '../services/repairService.js';
import * as stock from '../services/stockService.js';
import * as search from '../services/searchService.js';
import * as auth from '../services/authService.js';
import * as audit from '../services/auditService.js';
import { runReport, reportToCsv, reportToPdf, REPORT_NAMES } from '../services/reportService.js';
import { forbidden, badRequest } from '../utils/errors.js';
import { paging, parseFilters } from './common.js';

const created = (res, body) => res.status(201).json(body);

export const authController = {
  login: async (req, res) => res.json(await auth.login(req.body?.username, req.body?.password)),
  me: (req, res) => res.json({ user: req.user }),
  changePassword: async (req, res) => {
    await auth.changeOwnPassword(req.user, req.body);
    res.json({ ok: true });
  },
};

export const userController = {
  list: async (req, res) => res.json(await auth.listUsers(req.query, paging(req))),
  create: async (req, res) => created(res, await auth.createUser(req.body, req.user)),
  update: async (req, res) => res.json(await auth.updateUser(req.params.id, req.body, req.user)),
};

export const auditController = {
  list: async (req, res) => res.json(await audit.listAuditLogs(req.query, paging(req))),
};

export const branchController = {
  list: async (req, res) => res.json(await branches.listBranches(req.query, paging(req))),
  get: async (req, res) => res.json(await branches.getBranch(req.params.id)),
  create: async (req, res) => created(res, await branches.createBranch(req.body, req.user)),
  update: async (req, res) => res.json(await branches.updateBranch(req.params.id, req.body, req.user)),
  remove: async (req, res) => res.json(await branches.deactivateBranch(req.params.id, req.user)),
  assets: async (req, res) => res.json(await branches.branchAssets(req.params.id, req.query, paging(req))),
};

export const employeeController = {
  list: async (req, res) => res.json(await employees.listEmployees(req.query, paging(req))),
  get: async (req, res) => res.json(await employees.getEmployee(req.params.id)),
  create: async (req, res) => created(res, await employees.createEmployee(req.body, req.user)),
  update: async (req, res) => res.json(await employees.updateEmployee(req.params.id, req.body, req.user)),
  remove: async (req, res) => res.json(await employees.deactivateEmployee(req.params.id, req.user)),
  assets: async (req, res) => res.json(await employees.employeeAssets(req.params.id)),
};

export const assetController = {
  list: async (req, res) => {
    const f = parseFilters(req.query);
    res.json(
      await assets.listAssetsService(
        req.query,
        {
          ...f,
          status: f.status ? f.status.split(',') : undefined,
          assigned: req.query.assigned === 'true' ? true : undefined,
        },
        paging(req),
      ),
    );
  },
  get: async (req, res) => res.json(await assets.assetDetails(req.params.id)),
  create: async (req, res) => created(res, await assets.createAsset(req.body, req.user)),
  update: async (req, res) => res.json(await assets.updateAsset(req.params.id, req.body, req.user)),
  status: async (req, res) => res.json(await assets.changeAssetStatus(req.params.id, req.body, req.user)),
  retire: async (req, res) => res.json(await assets.retireAsset(req.params.id, req.user)),
  markDead: async (req, res) => res.json(await assets.markDead(req.params.id, req.body, req.user)),
  history: async (req, res) => res.json({ items: await assets.assetHistory(req.params.id) }),
};

export const assignmentController = {
  list: async (req, res) => res.json(await assignments.listAssignments(req.query, paging(req))),
  get: async (req, res) => res.json(await assignments.getAssignment(req.params.id)),
  create: async (req, res) => created(res, await assignments.assignLaptop(req.body, req.user)),
  update: async (req, res) => res.json(await assignments.updateAssignment(req.params.id, req.body, req.user)),
  returnLaptop: async (req, res) => res.json(await assignments.returnLaptop(req.params.id, req.body, req.user)),
};

export const replacementController = {
  list: async (req, res) => res.json(await replacements.listReplacements(req.query, paging(req))),
  get: async (req, res) => res.json(await replacements.getReplacement(req.params.id)),
  create: async (req, res) => created(res, await replacements.replaceLaptop(req.body, req.user)),
};

export const repairController = {
  list: async (req, res) => res.json(await repairs.listRepairs(req.query, paging(req))),
  get: async (req, res) => res.json(await repairs.repairDetails(req.params.id)),
  create: async (req, res) => created(res, await repairs.createRepair(req.body, req.user)),
  update: async (req, res) => res.json(await repairs.updateRepair(req.params.id, req.body, req.user)),
};

export const stockController = {
  overview: async (_req, res) => res.json(await stock.stockOverview()),
  assets: async (req, res) => res.json(await stock.stockAssets(req.query, paging(req))),
};

export const dashboardController = {
  get: async (_req, res) => res.json(await stock.dashboard()),
};

export const searchController = {
  search: async (req, res) => res.json(await search.globalSearch(req.query.q)),
};

export const reportController = {
  run: (name) => async (req, res) => {
    if (!REPORT_NAMES.includes(name)) throw badRequest('Unknown report.');
    const filters = parseFilters(req.query);
    const format = String(req.query.format || 'json').toLowerCase();

    if (format === 'json') {
      return res.json(await runReport(name, filters, { paging: paging(req, { defaultLimit: 50, maxLimit: 500 }) }));
    }
    // Exports are an ADMIN-only capability.
    if (req.user.role !== 'ADMIN') throw forbidden('Only administrators can export reports.');
    const report = await runReport(name, filters, { all: true });
    const stamp = new Date().toISOString().slice(0, 10);
    const base = `${name}-report-${stamp}`;
    if (format === 'csv') {
      res.setHeader('Content-Type', 'text/csv; charset=utf-8');
      res.setHeader('Content-Disposition', `attachment; filename="${base}.csv"`);
      return res.send(reportToCsv(report));
    }
    if (format === 'pdf') {
      res.setHeader('Content-Type', 'application/pdf');
      res.setHeader('Content-Disposition', `attachment; filename="${base}.pdf"`);
      const summary = Object.entries(req.query)
        .filter(([k, v]) => !['format', 'page', 'limit'].includes(k) && v)
        .map(([k, v]) => `${k}=${v}`)
        .join(', ');
      return reportToPdf(report, summary, res);
    }
    throw badRequest('Unsupported format. Use json, csv or pdf.');
  },
};
