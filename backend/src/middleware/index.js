import { env } from '../config/env.js';
import { AppError, forbidden, unauthorized } from '../utils/errors.js';
import { verifyToken } from '../services/authService.js';

export async function authenticate(req, _res, next) {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) throw unauthorized();
  req.user = await verifyToken(token);
  next();
}

export const requireRole =
  (...roles) =>
  (req, _res, next) => {
    if (!req.user || !roles.includes(req.user.role)) throw forbidden();
    next();
  };

export function notFoundHandler(_req, _res, next) {
  next(new AppError(404, 'ROUTE_NOT_FOUND', 'The requested resource was not found.'));
}

const DUP_MESSAGES = {
  serialNumber: 'Serial number already exists.',
  assetTag: 'Asset tag already exists.',
  employeeCode: 'Employee code already exists.',
  code: 'Branch code already exists.',
  username: 'Username already exists.',
  assetId: 'Asset already assigned.',
  employeeId: 'Employee already has an active laptop.',
};

/** Central error handler: raw database errors are never leaked to clients. */
// eslint-disable-next-line no-unused-vars
export function errorHandler(err, req, res, _next) {
  if (err instanceof AppError) {
    return res.status(err.status).json({ error: { code: err.code, message: err.message, details: err.details } });
  }
  if (err?.type === 'entity.parse.failed') {
    return res.status(400).json({ error: { code: 'BAD_JSON', message: 'Invalid request body.' } });
  }
  if (err?.code === 11000) {
    const key = Object.keys(err.keyPattern || {})[0];
    return res.status(409).json({
      error: { code: 'DUPLICATE', message: DUP_MESSAGES[key] || 'A record with the same value already exists.' },
    });
  }
  if (!env.isTest) console.error(`[error] ${req.method} ${req.originalUrl}`, err);
  return res
    .status(500)
    .json({ error: { code: 'INTERNAL_ERROR', message: 'Unable to save changes. Please try again.' } });
}
