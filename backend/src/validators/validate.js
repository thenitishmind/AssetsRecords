import { AppError } from '../utils/errors.js';
import { toDate } from '../utils/helpers.js';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[0-9+()\-\s]{7,20}$/;

/**
 * Tiny schema validator.
 * field spec: { type, required, max, min, values, upper, label }
 * types: string | number | date | enum | email | phone | boolean
 * With `partial: true` only fields present in the body are validated (for PUT).
 */
export function validate(body, schema, { partial = false } = {}) {
  const input = body && typeof body === 'object' ? body : {};
  const out = {};
  const errors = {};

  for (const [field, spec] of Object.entries(schema)) {
    const label = spec.label || field;
    let v = input[field];
    const present = v !== undefined;
    if (partial && !present) continue;

    if (typeof v === 'string') v = v.trim();
    const empty = v === undefined || v === null || v === '';
    if (empty) {
      if (spec.required) errors[field] = `${label} is required.`;
      else if (present) out[field] = null;
      continue;
    }

    switch (spec.type) {
      case 'string':
      case 'email':
      case 'phone':
      case 'enum': {
        if (typeof v !== 'string') {
          errors[field] = `${label} must be text.`;
          break;
        }
        if (spec.upper) v = v.toUpperCase();
        if (spec.max && v.length > spec.max) errors[field] = `${label} must be at most ${spec.max} characters.`;
        else if (spec.min && v.length < spec.min) errors[field] = `${label} must be at least ${spec.min} characters.`;
        else if (spec.type === 'email' && !EMAIL.test(v)) errors[field] = `${label} is not a valid email.`;
        else if (spec.type === 'phone' && !PHONE.test(v)) errors[field] = `${label} is not a valid contact number.`;
        else if (spec.type === 'enum' && !spec.values.includes(v))
          errors[field] = `${label} must be one of: ${spec.values.join(', ')}.`;
        else out[field] = v;
        break;
      }
      case 'number': {
        const n = typeof v === 'number' ? v : Number(v);
        if (!Number.isFinite(n)) errors[field] = `${label} must be a number.`;
        else if (spec.min !== undefined && n < spec.min) errors[field] = `${label} must be at least ${spec.min}.`;
        else if (spec.max !== undefined && n > spec.max) errors[field] = `${label} must be at most ${spec.max}.`;
        else out[field] = n;
        break;
      }
      case 'date': {
        const d = toDate(v);
        if (!d) errors[field] = `${label} is not a valid date.`;
        else out[field] = d;
        break;
      }
      case 'boolean':
        out[field] = v === true || v === 'true';
        break;
      default:
        out[field] = v;
    }
  }

  if (Object.keys(errors).length) {
    throw new AppError(400, 'VALIDATION_ERROR', Object.values(errors)[0], errors);
  }
  return out;
}

export const checkDateOrder = (from, to, message) => {
  if (from && to && to < from) throw new AppError(400, 'VALIDATION_ERROR', message);
};
