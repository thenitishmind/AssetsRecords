import {
  ASSET_STATUSES,
  OLD_ASSET_OUTCOMES,
  RECORD_STATUSES,
  REPAIR_STATUSES,
  REPLACEMENT_REASONS,
  ROLES,
} from '../models/constants.js';

export const branchSchema = {
  name: { type: 'string', required: true, max: 100, label: 'Branch name' },
  code: { type: 'string', required: true, max: 20, upper: true, label: 'Branch code' },
  city: { type: 'string', required: true, max: 80, label: 'City' },
  state: { type: 'string', required: true, max: 80, label: 'State' },
  address: { type: 'string', max: 300, label: 'Address' },
  contactNumber: { type: 'phone', label: 'Contact number' },
  status: { type: 'enum', values: RECORD_STATUSES, label: 'Status' },
};

export const employeeSchema = {
  employeeCode: { type: 'string', required: true, max: 30, upper: true, label: 'Employee code' },
  name: { type: 'string', required: true, max: 100, label: 'Employee name' },
  branchId: { type: 'string', required: true, max: 30, label: 'Branch' },
  department: { type: 'string', max: 80, label: 'Department' },
  designation: { type: 'string', max: 80, label: 'Designation' },
  contactNumber: { type: 'phone', label: 'Contact number' },
  email: { type: 'email', max: 120, label: 'Email' },
  joiningDate: { type: 'date', label: 'Joining date' },
  status: { type: 'enum', values: RECORD_STATUSES, label: 'Status' },
};

export const assetSchema = {
  assetTag: { type: 'string', max: 50, upper: true, label: 'Asset tag' },
  serialNumber: { type: 'string', required: true, max: 60, upper: true, label: 'Serial number' },
  brand: { type: 'string', required: true, max: 60, label: 'Brand' },
  model: { type: 'string', required: true, max: 80, label: 'Model' },
  processor: { type: 'string', max: 80, label: 'Processor' },
  ram: { type: 'string', max: 30, label: 'RAM' },
  storage: { type: 'string', max: 40, label: 'Storage' },
  operatingSystem: { type: 'string', max: 60, label: 'Operating system' },
  purchaseDate: { type: 'date', label: 'Purchase date' },
  purchasePrice: { type: 'number', min: 0, label: 'Purchase price' },
  warrantyStartDate: { type: 'date', label: 'Warranty start date' },
  warrantyEndDate: { type: 'date', label: 'Warranty end date' },
  currentBranchId: { type: 'string', required: true, max: 30, label: 'Branch' },
};

export const assignmentSchema = {
  branchId: { type: 'string', required: true, label: 'Branch' },
  employeeId: { type: 'string', required: true, label: 'Employee' },
  assetId: { type: 'string', required: true, label: 'Laptop' },
  assignmentDate: { type: 'date', label: 'Assignment date' },
  remarks: { type: 'string', max: 500, label: 'Remarks' },
};

export const replacementSchema = {
  employeeId: { type: 'string', required: true, label: 'Employee' },
  newAssetId: { type: 'string', required: true, label: 'New laptop' },
  reason: { type: 'enum', required: true, values: REPLACEMENT_REASONS, label: 'Replacement reason' },
  oldAssetStatus: { type: 'enum', values: OLD_ASSET_OUTCOMES, label: 'Old laptop status' },
  deadReason: { type: 'string', max: 200, label: 'Dead reason' },
  replacementDate: { type: 'date', label: 'Replacement date' },
  remarks: { type: 'string', max: 500, label: 'Remarks' },
};

export const repairCreateSchema = {
  assetId: { type: 'string', required: true, label: 'Laptop' },
  repairDate: { type: 'date', label: 'Repair date' },
  issueType: { type: 'string', required: true, max: 80, label: 'Issue type' },
  problemDescription: { type: 'string', max: 1000, label: 'Problem description' },
  vendor: { type: 'string', max: 120, label: 'Repair vendor' },
  estimatedCost: { type: 'number', min: 0, label: 'Estimated cost' },
  warranty: { type: 'string', max: 120, label: 'Warranty' },
  remarks: { type: 'string', max: 500, label: 'Remarks' },
};

export const repairUpdateSchema = {
  issueType: { type: 'string', max: 80, label: 'Issue type' },
  problemDescription: { type: 'string', max: 1000, label: 'Problem description' },
  vendor: { type: 'string', max: 120, label: 'Repair vendor' },
  status: { type: 'enum', values: REPAIR_STATUSES, label: 'Repair status' },
  estimatedCost: { type: 'number', min: 0, label: 'Estimated cost' },
  finalCost: { type: 'number', min: 0, label: 'Final repair cost' },
  repairStartDate: { type: 'date', label: 'Repair start date' },
  repairCompletionDate: { type: 'date', label: 'Repair completion date' },
  warranty: { type: 'string', max: 120, label: 'Warranty' },
  remarks: { type: 'string', max: 500, label: 'Remarks' },
};

export const markDeadSchema = {
  reason: { type: 'string', required: true, max: 200, label: 'Reason' },
  remarks: { type: 'string', max: 500, label: 'Remarks' },
  deadDate: { type: 'date', label: 'Dead date' },
};

export const statusChangeSchema = {
  status: { type: 'enum', required: true, values: ASSET_STATUSES, label: 'Status' },
  remarks: { type: 'string', max: 500, label: 'Remarks' },
};

export const userCreateSchema = {
  username: { type: 'string', required: true, min: 3, max: 40, label: 'Username' },
  name: { type: 'string', required: true, max: 100, label: 'Name' },
  password: { type: 'string', required: true, min: 8, max: 100, label: 'Password' },
  role: { type: 'enum', required: true, values: Object.values(ROLES), label: 'Role' },
};

export const userUpdateSchema = {
  name: { type: 'string', max: 100, label: 'Name' },
  password: { type: 'string', min: 8, max: 100, label: 'Password' },
  role: { type: 'enum', values: Object.values(ROLES), label: 'Role' },
  status: { type: 'enum', values: RECORD_STATUSES, label: 'Status' },
};
