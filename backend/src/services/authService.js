import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'node:crypto';
import { env } from '../config/env.js';
import { nextId, withTransaction } from '../config/db.js';
import { userRepo } from '../repositories/index.js';
import { ROLES } from '../models/constants.js';
import { badRequest, conflict, notFound, unauthorized } from '../utils/errors.js';
import { containsRegex, paginate } from '../utils/helpers.js';
import { userCreateSchema, userUpdateSchema } from '../validators/schemas.js';
import { validate } from '../validators/validate.js';
import { publicUser, recordAudit } from './core.js';

const sign = (u) =>
  jwt.sign({ sub: u.userId, username: u.username, role: u.role }, env.jwtSecret, { expiresIn: env.jwtExpiresIn });

export async function login(username, password) {
  const u = await userRepo.findOne({ username: String(username || '').trim().toLowerCase() });
  const ok = u && u.status === 'ACTIVE' && (await bcrypt.compare(String(password || ''), u.passwordHash));
  if (!ok) throw unauthorized('Invalid username or password.', 'INVALID_CREDENTIALS');
  await userRepo.update(u.userId, { lastLoginAt: new Date() });
  return { token: sign(u), user: publicUser(u) };
}

export async function verifyToken(token) {
  let payload;
  try {
    payload = jwt.verify(token, env.jwtSecret);
  } catch {
    throw unauthorized('Your session has expired. Please sign in again.', 'TOKEN_INVALID');
  }
  const u = await userRepo.find(payload.sub);
  if (!u || u.status !== 'ACTIVE') throw unauthorized('Your account is no longer active.', 'TOKEN_INVALID');
  return publicUser(u);
}

/** Creates the first admin from environment variables if no users exist. */
export async function seedAdminIfNeeded(log = console) {
  if ((await userRepo.count({})) > 0) return null;
  let password = env.adminPassword;
  let generated = false;
  if (!password) {
    password = crypto.randomBytes(9).toString('base64url');
    generated = true;
  }
  const userId = await nextId('user', 'USR-', 4);
  const now = new Date();
  await userRepo.insert({
    userId,
    username: env.adminUsername.toLowerCase(),
    name: env.adminName,
    role: ROLES.ADMIN,
    status: 'ACTIVE',
    passwordHash: await bcrypt.hash(password, 12),
    createdAt: now,
    updatedAt: now,
  });
  log.warn(`[seed] Admin user "${env.adminUsername}" created.${generated ? ` Generated password (shown once): ${password}` : ''}`);
  return userId;
}

export async function createUser(body, actor) {
  const data = validate(body, userCreateSchema);
  const username = data.username.toLowerCase();
  if (await userRepo.exists({ username })) throw conflict('Username already exists.', 'DUPLICATE_USERNAME');
  const userId = await nextId('user', 'USR-', 4);
  const now = new Date();
  const doc = {
    userId,
    username,
    name: data.name,
    role: data.role,
    status: 'ACTIVE',
    passwordHash: await bcrypt.hash(data.password, 12),
    createdAt: now,
    updatedAt: now,
  };
  await withTransaction(async (s) => {
    await userRepo.insert(doc, s);
    await recordAudit(s, {
      action: 'USER_CREATED',
      entityType: 'USER',
      entityId: userId,
      user: actor,
      newValues: { username, name: data.name, role: data.role },
    });
  });
  return publicUser(doc);
}

export async function updateUser(userId, body, actor) {
  const data = validate(body, userUpdateSchema, { partial: true });
  return withTransaction(async (s) => {
    const old = await userRepo.find(userId, s);
    if (!old) throw notFound('User not found.', 'USER_NOT_FOUND');
    if (userId === actor.userId && (data.status === 'INACTIVE' || (data.role && data.role !== old.role))) {
      throw badRequest('You cannot deactivate yourself or change your own role.');
    }
    const set = { ...data, updatedAt: new Date() };
    if (data.password) {
      set.passwordHash = await bcrypt.hash(data.password, 12);
      delete set.password;
    }
    await userRepo.update(userId, set, s);
    await recordAudit(s, {
      action: 'USER_UPDATED',
      entityType: 'USER',
      entityId: userId,
      user: actor,
      oldValues: { name: old.name, role: old.role, status: old.status },
      newValues: { ...data, password: data.password ? '***' : undefined },
    });
    return publicUser(await userRepo.find(userId, s));
  });
}

export async function changeOwnPassword(user, body) {
  const { currentPassword, newPassword } = body || {};
  if (!newPassword || String(newPassword).length < 8) throw badRequest('New password must be at least 8 characters.');
  const u = await userRepo.find(user.userId);
  if (!(await bcrypt.compare(String(currentPassword || ''), u.passwordHash))) {
    throw badRequest('Current password is incorrect.', undefined, 'INVALID_CREDENTIALS');
  }
  await userRepo.update(u.userId, { passwordHash: await bcrypt.hash(String(newPassword), 12), updatedAt: new Date() });
  await recordAudit(undefined, { action: 'PASSWORD_CHANGED', entityType: 'USER', entityId: u.userId, user });
}

export async function listUsers(q, paging) {
  const match = {};
  if (q.role) match.role = q.role;
  if (q.status) match.status = q.status;
  if (q.q) match.$or = [{ username: containsRegex(q.q) }, { name: containsRegex(q.q) }];
  return paginate(
    userRepo.collection(),
    [{ $match: match }, { $project: { passwordHash: 0 } }, { $sort: { username: 1 } }],
    paging,
  );
}
