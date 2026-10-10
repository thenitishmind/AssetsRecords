import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { authenticate, requireRole } from '../middleware/index.js';
import {
  assetController,
  assignmentController,
  auditController,
  authController,
  branchController,
  dashboardController,
  employeeController,
  repairController,
  replacementController,
  reportController,
  searchController,
  stockController,
  userController,
} from '../controllers/index.js';

const ADMIN = requireRole('ADMIN');
const ANY = requireRole('ADMIN', 'OPERATOR');

export function buildRouter({ loginLimit = 20 } = {}) {
  const r = Router();

  // --- public ---
  r.post(
    '/auth/login',
    rateLimit({ windowMs: 15 * 60 * 1000, limit: loginLimit, standardHeaders: true, legacyHeaders: false,
      message: { error: { code: 'RATE_LIMITED', message: 'Too many sign-in attempts. Try again later.' } } }),
    authController.login,
  );
  r.get('/health', (_req, res) => res.json({ status: 'ok', time: new Date() }));

  // --- everything below needs a valid token ---
  r.use(authenticate);
  r.get('/auth/me', authController.me);
  r.post('/auth/change-password', authController.changePassword);

  r.get('/dashboard', dashboardController.get);
  r.get('/search', searchController.search);

  // users & audit (admin)
  r.get('/users', ADMIN, userController.list);
  r.post('/users', ADMIN, userController.create);
  r.put('/users/:id', ADMIN, userController.update);
  r.get('/audit-logs', ADMIN, auditController.list);

  // branches
  r.get('/branches', branchController.list);
  r.post('/branches', ADMIN, branchController.create);
  r.get('/branches/:id', branchController.get);
  r.put('/branches/:id', ADMIN, branchController.update);
  r.delete('/branches/:id', ADMIN, branchController.remove);
  r.get('/branches/:id/assets', branchController.assets);

  // employees
  r.get('/employees', employeeController.list);
  r.post('/employees', ADMIN, employeeController.create);
  r.get('/employees/:id', employeeController.get);
  r.put('/employees/:id', ADMIN, employeeController.update);
  r.delete('/employees/:id', ADMIN, employeeController.remove);
  r.get('/employees/:id/assets', employeeController.assets);

  // assets
  r.get('/assets', assetController.list);
  r.post('/assets', ADMIN, assetController.create);
  r.get('/assets/:id', assetController.get);
  r.put('/assets/:id', ADMIN, assetController.update);
  r.delete('/assets/:id', ADMIN, assetController.retire);
  r.put('/assets/:id/status', ADMIN, assetController.status);
  r.post('/assets/:id/mark-dead', ADMIN, assetController.markDead);
  r.get('/assets/:id/history', assetController.history);

  // assignments / replacements
  r.get('/assignments', assignmentController.list);
  r.post('/assignments', ANY, assignmentController.create);
  r.get('/assignments/:id', assignmentController.get);
  r.put('/assignments/:id', ADMIN, assignmentController.update);
  r.post('/assignments/:id/return', ADMIN, assignmentController.returnLaptop);
  r.get('/replacements', replacementController.list);
  r.post('/replacements', ADMIN, replacementController.create);
  r.get('/replacements/:id', replacementController.get);

  // repairs
  r.get('/repairs', repairController.list);
  r.post('/repairs', ANY, repairController.create);
  r.get('/repairs/:id', repairController.get);
  r.put('/repairs/:id', ANY, repairController.update);

  // stock
  r.get('/stock', stockController.overview);
  r.get('/stock/assets', stockController.assets);

  // reports (json for everyone, csv/pdf export for admins)
  for (const name of ['assets', 'repairs', 'stock', 'dead', 'sold', 'replacements']) {
    r.get(`/reports/${name}`, reportController.run(name));
  }
  return r;
}
