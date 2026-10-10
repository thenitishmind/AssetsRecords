export const ASSET_STATUS = Object.freeze({
  AVAILABLE: 'AVAILABLE',
  ASSIGNED: 'ASSIGNED',
  REPAIR: 'REPAIR',
  DEAD: 'DEAD',
  SOLD: 'SOLD',
  RETIRED: 'RETIRED',
});
export const ASSET_STATUSES = Object.values(ASSET_STATUS);

export const REPAIR_STATUS = Object.freeze({
  SENT_FOR_REPAIR: 'SENT_FOR_REPAIR',
  UNDER_REPAIR: 'UNDER_REPAIR',
  REPAIRED: 'REPAIRED',
  NOT_REPAIRABLE: 'NOT_REPAIRABLE',
  DEAD: 'DEAD',
  RETURNED: 'RETURNED',
});
export const REPAIR_STATUSES = Object.values(REPAIR_STATUS);
/** A repair in one of these states means the laptop is still with the vendor. */
export const OPEN_REPAIR_STATUSES = ['SENT_FOR_REPAIR', 'UNDER_REPAIR', 'NOT_REPAIRABLE'];

/** Allowed repair status transitions. */
export const REPAIR_TRANSITIONS = Object.freeze({
  SENT_FOR_REPAIR: ['UNDER_REPAIR', 'REPAIRED', 'NOT_REPAIRABLE', 'DEAD'],
  UNDER_REPAIR: ['REPAIRED', 'NOT_REPAIRABLE', 'DEAD'],
  NOT_REPAIRABLE: ['UNDER_REPAIR', 'DEAD'],
  REPAIRED: ['RETURNED'],
  RETURNED: [],
  DEAD: [],
});

export const RECORD_STATUSES = ['ACTIVE', 'INACTIVE'];
export const ROLES = Object.freeze({ ADMIN: 'ADMIN', OPERATOR: 'OPERATOR' });

export const ASSIGNMENT_STATUS = Object.freeze({ ACTIVE: 'ACTIVE', RELEASED: 'RELEASED' });

export const REPLACEMENT_REASONS = ['HARDWARE_ISSUE', 'UPGRADE', 'DEAD', 'OTHER'];
/** Where the OLD laptop goes after a replacement. */
export const OLD_ASSET_OUTCOMES = ['AVAILABLE', 'REPAIR', 'DEAD', 'RETIRED'];
export const DEFAULT_OUTCOME_FOR_REASON = Object.freeze({
  HARDWARE_ISSUE: 'REPAIR',
  UPGRADE: 'AVAILABLE',
  DEAD: 'DEAD',
});

export const DEAD_REASONS = [
  'Motherboard failure',
  'Physical damage',
  'Water damage',
  'Beyond economical repair',
  'Other',
];

export const EVENT = Object.freeze({
  IMPORTED: 'IMPORTED',
  PURCHASED: 'PURCHASED',
  ASSIGNED: 'ASSIGNED',
  RETURNED_TO_STOCK: 'RETURNED_TO_STOCK',
  REPLACED_OUT: 'REPLACED_OUT',
  REPLACEMENT_ASSIGNED: 'REPLACEMENT_ASSIGNED',
  SENT_FOR_REPAIR: 'SENT_FOR_REPAIR',
  REPAIR_UPDATED: 'REPAIR_UPDATED',
  REPAIR_COMPLETED: 'REPAIR_COMPLETED',
  MARKED_DEAD: 'MARKED_DEAD',
  MARKED_SOLD: 'MARKED_SOLD',
  RETIRED: 'RETIRED',
  TRANSFERRED: 'TRANSFERRED',
});

export const COLLECTIONS = Object.freeze({
  branches: 'branches',
  employees: 'employees',
  assets: 'assets',
  assignments: 'assignments',
  replacements: 'replacements',
  repairs: 'repairs',
  assetHistory: 'assetHistory',
  users: 'users',
  auditLogs: 'auditLogs',
  counters: 'counters',
});
