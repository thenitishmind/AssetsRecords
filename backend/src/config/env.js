import dotenv from 'dotenv';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const backendRoot = path.resolve(here, '..', '..');

// Secrets live ONLY on the server side. Prefer backend/.env, fall back to the
// Atlas-generated file kept at the repository root (both are git-ignored).
const candidates = [
  path.join(backendRoot, '.env'),
  path.join(backendRoot, '..', 'atlas-credentials.env'),
];
for (const file of candidates) {
  if (fs.existsSync(file)) dotenv.config({ path: file, override: false, quiet: true });
}

const required = (name) => {
  const v = process.env[name];
  if (!v) throw new Error(`Missing required environment variable ${name}`);
  return v;
};

export const env = {
  get mongoUri() {
    return required('MONGODB_URI');
  },
  get dbName() {
    return process.env.MONGODB_DB || 'asset_management';
  },
  get jwtSecret() {
    return required('JWT_SECRET');
  },
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || '12h',
  port: Number(process.env.PORT || 4000),
  corsOrigins: (process.env.CORS_ORIGINS || '*').split(',').map((s) => s.trim()),
  adminUsername: process.env.ADMIN_USERNAME || 'admin',
  adminName: process.env.ADMIN_NAME || 'System Administrator',
  adminPassword: process.env.ADMIN_PASSWORD || '',
  isTest: process.env.NODE_ENV === 'test',
};
