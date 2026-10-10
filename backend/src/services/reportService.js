import PDFDocument from 'pdfkit';
import { assetRepo, repairRepo, replacementRepo } from '../repositories/index.js';
import { assetListPipeline, dateRangeMatch, repairCountMatch } from '../repositories/assetQueries.js';
import { csvCell, containsRegex, paginate } from '../utils/helpers.js';
import { badRequest } from '../utils/errors.js';
import { replacementPipeline } from './replacementService.js';
import { stockByBranch } from './stockService.js';

const EXPORT_CAP = 20000;

const fmtDate = (d) => (d ? new Date(d).toISOString().slice(0, 10) : '');

/** Column types: text | date | money | int */
const REPORTS = {
  assets: {
    title: 'Current Asset Report',
    columns: [
      { key: 'employeeName', label: 'Employee Name' },
      { key: 'employeeCode', label: 'Employee Code' },
      { key: 'branchName', label: 'Branch' },
      { key: 'employeeDesignation', label: 'Designation' },
      { key: 'assetId', label: 'Asset ID' },
      { key: 'serialNumber', label: 'Serial Number' },
      { key: 'brand', label: 'Brand' },
      { key: 'model', label: 'Model' },
      { key: 'status', label: 'Status' },
      { key: 'assignmentDate', label: 'Assignment Date', type: 'date' },
    ],
    // Current assets only: laptops that are CURRENTLY held by an employee.
    source: () => assetRepo.collection(),
    pipeline: (f) => [
      ...assetListPipeline({
        assigned: true,
        branchId: f.branchId,
        employeeId: f.employeeId,
        status: f.status ? f.status.split(',') : undefined,
        q: f.q,
        dateFrom: f.dateFrom,
        dateTo: f.dateTo,
        repairCount: f.repairCount,
        minCost: f.minCost,
        maxCost: f.maxCost,
      }),
      { $sort: { employeeName: 1, assetId: 1 } },
    ],
  },
  repairs: {
    title: 'Repair Report',
    columns: [
      { key: 'branchName', label: 'Branch' },
      { key: 'employeeName', label: 'Employee' },
      { key: 'employeeDesignation', label: 'Designation' },
      { key: 'assetId', label: 'Asset ID' },
      { key: 'serialNumber', label: 'Serial Number' },
      { key: 'model', label: 'Model' },
      { key: 'repairCount', label: 'Repair Count', type: 'int' },
      { key: 'totalRepairCost', label: 'Total Repair Cost', type: 'money' },
      { key: 'lastRepairDate', label: 'Last Repair Date', type: 'date' },
      { key: 'status', label: 'Current Status' },
    ],
    source: () => repairRepo.collection(),
    pipeline: (f) => {
      const m = {};
      if (f.branchId) m.branchId = f.branchId;
      if (f.employeeId) m.employeeId = f.employeeId;
      const dr = dateRangeMatch(f.dateFrom, f.dateTo);
      if (dr) m.repairDate = dr;
      const post = {};
      const rc = repairCountMatch(f.repairCount);
      if (rc !== null) post.repairCount = rc;
      if (f.minCost !== undefined || f.maxCost !== undefined) {
        post.totalRepairCost = {};
        if (f.minCost !== undefined) post.totalRepairCost.$gte = f.minCost;
        if (f.maxCost !== undefined) post.totalRepairCost.$lte = f.maxCost;
      }
      if (f.status) post.status = { $in: f.status.split(',') };
      if (f.q) {
        const rx = containsRegex(f.q);
        post.$or = ['assetId', 'serialNumber', 'model', 'employeeName', 'employeeDesignation', 'branchName'].map((k) => ({ [k]: rx }));
      }
      return [
        { $match: m },
        { $sort: { repairDate: 1, repairId: 1 } },
        {
          $group: {
            _id: '$assetId',
            repairCount: { $sum: 1 },
            totalRepairCost: { $sum: { $ifNull: ['$finalCost', 0] } },
            lastRepairDate: { $last: '$repairDate' },
            lastEmployeeId: { $last: '$employeeId' },
            lastBranchId: { $last: '$branchId' },
          },
        },
        { $lookup: { from: 'assets', localField: '_id', foreignField: 'assetId', as: '_a' } },
        {
          $addFields: {
            assetId: '$_id',
            serialNumber: { $first: '$_a.serialNumber' },
            model: { $first: '$_a.model' },
            status: { $first: '$_a.status' },
            _empId: { $ifNull: [{ $first: '$_a.currentEmployeeId' }, '$lastEmployeeId'] },
            _brId: { $ifNull: [{ $first: '$_a.currentBranchId' }, '$lastBranchId'] },
          },
        },
        { $lookup: { from: 'employees', localField: '_empId', foreignField: 'employeeId', as: '_e' } },
        { $lookup: { from: 'branches', localField: '_brId', foreignField: 'branchId', as: '_b' } },
        {
          $addFields: {
            employeeName: { $first: '$_e.name' },
            employeeCode: { $first: '$_e.employeeCode' },
            employeeDesignation: { $first: '$_e.designation' },
            branchName: { $first: '$_b.name' },
            branchId: '$_brId',
          },
        },
        { $project: { _a: 0, _e: 0, _b: 0, _id: 0, _empId: 0, _brId: 0, lastEmployeeId: 0, lastBranchId: 0 } },
        ...(Object.keys(post).length ? [{ $match: post }] : []),
        { $sort: { branchName: 1, assetId: 1 } },
      ];
    },
  },
  dead: {
    title: 'Dead Laptop Report',
    columns: [
      { key: 'assetId', label: 'Asset ID' },
      { key: 'serialNumber', label: 'Serial Number' },
      { key: 'brand', label: 'Brand' },
      { key: 'model', label: 'Model' },
      { key: 'branchName', label: 'Branch' },
      { key: 'displayEmployeeName', label: 'Employee' },
      { key: 'deadDate', label: 'Dead Date', type: 'date' },
      { key: 'deadReason', label: 'Reason' },
      { key: 'purchasePrice', label: 'Purchase Price', type: 'money' },
      { key: 'totalRepairCost', label: 'Repair Cost', type: 'money' },
      { key: 'lastRepairDate', label: 'Last Repair Date', type: 'date' },
      { key: 'deadRemarks', label: 'Remarks' },
    ],
    source: () => assetRepo.collection(),
    pipeline: (f) => [
      ...assetListPipeline({
        status: 'DEAD',
        branchId: f.branchId,
        previousEmployeeId: f.employeeId,
        q: f.q,
        dateFrom: f.dateFrom,
        dateTo: f.dateTo,
        dateField: 'deadInfo.deadDate',
        repairCount: f.repairCount,
        minCost: f.minCost,
        maxCost: f.maxCost,
      }),
      {
        $addFields: {
          deadDate: '$deadInfo.deadDate',
          deadReason: '$deadInfo.reason',
          deadRemarks: '$deadInfo.remarks',
        },
      },
      { $sort: { deadDate: -1, assetId: 1 } },
    ],
  },
  sold: {
    title: 'Sold Laptop Report',
    columns: [
      { key: 'assetId', label: 'Asset ID' },
      { key: 'serialNumber', label: 'Serial Number' },
      { key: 'brand', label: 'Brand' },
      { key: 'model', label: 'Model' },
      { key: 'branchName', label: 'Branch' },
      { key: 'soldDate', label: 'Sold Date', type: 'date' },
      { key: 'deadReason', label: 'Previous Dead Reason' },
      { key: 'soldRemarks', label: 'Remarks' },
    ],
    source: () => assetRepo.collection(),
    pipeline: (f) => [
      ...assetListPipeline({
        status: 'SOLD',
        branchId: f.branchId,
        q: f.q,
        dateFrom: f.dateFrom,
        dateTo: f.dateTo,
        dateField: 'soldInfo.soldDate',
      }),
      { $addFields: { soldDate: '$soldInfo.soldDate', soldRemarks: '$soldInfo.remarks', deadReason: '$deadInfo.reason' } },
      { $sort: { soldDate: -1, assetId: 1 } },
    ],
  },
  replacements: {
    title: 'Replacement Report',
    columns: [
      { key: 'employeeName', label: 'Employee' },
      { key: 'employeeCode', label: 'Employee ID' },
      { key: 'branchName', label: 'Branch' },
      { key: 'designation', label: 'Designation' },
      { key: 'oldAssetId', label: 'Old Laptop' },
      { key: 'oldModel', label: 'Old Model' },
      { key: 'oldSerialNumber', label: 'Old Serial Number' },
      { key: 'newAssetId', label: 'New Laptop' },
      { key: 'newModel', label: 'New Model' },
      { key: 'newSerialNumber', label: 'New Serial Number' },
      { key: 'replacementDate', label: 'Replacement Date', type: 'date' },
      { key: 'reason', label: 'Reason' },
      { key: 'oldAssetStatus', label: 'Old Laptop Status' },
    ],
    source: () => replacementRepo.collection(),
    pipeline: (f) =>
      replacementPipeline({
        branchId: f.branchId,
        employeeId: f.employeeId,
        q: f.q,
        reason: f.reason,
        dateFrom: f.dateFrom,
        dateTo: f.dateTo,
        sort: 'replacementDate:desc',
      }),
  },
};

export const STOCK_COLUMNS = [
  { key: 'branchName', label: 'Branch' },
  { key: 'total', label: 'Total', type: 'int' },
  { key: 'available', label: 'Available', type: 'int' },
  { key: 'assigned', label: 'Assigned', type: 'int' },
  { key: 'repair', label: 'Repair', type: 'int' },
  { key: 'dead', label: 'Dead', type: 'int' },
  { key: 'sold', label: 'Sold', type: 'int' },
  { key: 'retired', label: 'Retired', type: 'int' },
];

export const REPORT_NAMES = [...Object.keys(REPORTS), 'stock'];

/**
 * Returns { title, columns, rows, total, ... }. Rows contain `sno` and raw
 * values; `all: true` fetches every row (export mode).
 */
export async function runReport(name, filters, { paging, all = false } = {}) {
  if (name === 'stock') {
    let rows = await stockByBranch({ branchId: filters.branchId });
    if (filters.q) {
      const rx = containsRegex(filters.q);
      rows = rows.filter((r) => rx.test(r.branchName) || rx.test(r.branchCode));
    }
    const totals = rows.reduce(
      (t, r) => {
        for (const k of ['total', 'available', 'assigned', 'repair', 'dead', 'sold', 'retired']) t[k] += r[k];
        return t;
      },
      { branchName: 'TOTAL', total: 0, available: 0, assigned: 0, repair: 0, dead: 0, sold: 0, retired: 0 },
    );
    const numbered = rows.map((r, i) => ({ sno: i + 1, ...r }));
    return {
      name,
      title: 'Stock Report',
      columns: STOCK_COLUMNS,
      rows: numbered,
      total: numbered.length,
      page: 1,
      limit: numbered.length,
      hasMore: false,
      totals,
    };
  }

  const def = REPORTS[name];
  if (!def) throw badRequest('Unknown report.', undefined, 'UNKNOWN_REPORT');
  const pipeline = def.pipeline(filters);

  if (all) {
    const rows = await def.source().aggregate([...pipeline, { $limit: EXPORT_CAP }, { $project: { _id: 0 } }]).toArray();
    const numbered = rows.map((r, i) => ({ sno: i + 1, ...r }));
    return { name, title: def.title, columns: def.columns, rows: numbered, total: numbered.length, page: 1, limit: numbered.length, hasMore: false };
  }

  const res = await paginate(def.source(), pipeline, paging);
  const rows = res.items.map((r, i) => ({ sno: paging.skip + i + 1, ...r }));
  return { name, title: def.title, columns: def.columns, rows, total: res.total, page: res.page, limit: res.limit, hasMore: res.hasMore };
}

function cellText(col, row) {
  const v = row[col.key];
  if (v === null || v === undefined) return '';
  if (col.type === 'date') return fmtDate(v);
  if (col.type === 'money') return Number(v).toFixed(2);
  return String(v);
}

export function reportToCsv(report) {
  const cols = [{ key: 'sno', label: 'S.No', type: 'int' }, ...report.columns];
  const lines = [cols.map((c) => csvCell(c.label)).join(',')];
  for (const row of report.rows) lines.push(cols.map((c) => csvCell(cellText(c, row))).join(','));
  if (report.totals) {
    lines.push(cols.map((c) => csvCell(c.key === 'sno' ? '' : cellText(c, report.totals))).join(','));
  }
  return `\uFEFF${lines.join('\r\n')}\r\n`; // BOM so Excel opens UTF-8 (₹, names) correctly
}

export function reportToPdf(report, filterSummary, res) {
  const cols = [{ key: 'sno', label: 'S.No', type: 'int', w: 0.5 }, ...report.columns];
  const doc = new PDFDocument({ size: 'A4', layout: 'landscape', margin: 28, bufferPages: true });
  doc.pipe(res);

  const pageW = doc.page.width - 56;
  const weights = cols.map((c) => c.w ?? (c.key === 'serialNumber' || c.key === 'model' || c.key === 'employeeName' ? 1.4 : 1));
  const total = weights.reduce((a, b) => a + b, 0);
  const widths = weights.map((w) => (w / total) * pageW);

  const header = () => {
    let x = 28;
    const y = doc.y;
    doc.rect(28, y, pageW, 18).fill('#1E3A8A');
    doc.fillColor('#FFFFFF').font('Helvetica-Bold').fontSize(7.5);
    cols.forEach((c, i) => {
      doc.text(c.label, x + 3, y + 5, { width: widths[i] - 6, height: 12, ellipsis: true, lineBreak: false });
      x += widths[i];
    });
    doc.y = y + 20;
    doc.fillColor('#111827');
  };

  doc.font('Helvetica-Bold').fontSize(16).fillColor('#1E3A8A').text(report.title);
  doc.font('Helvetica').fontSize(8).fillColor('#4B5563')
    .text(`Generated: ${new Date().toISOString().replace('T', ' ').slice(0, 16)} UTC   |   Records: ${report.rows.length}${filterSummary ? `   |   Filters: ${filterSummary}` : ''}`);
  doc.moveDown(0.6);
  header();

  const drawRow = (row, i, bold = false) => {
    if (doc.y > doc.page.height - 50) {
      doc.addPage();
      header();
    }
    const y = doc.y;
    if (i % 2 === 0) doc.rect(28, y - 1, pageW, 15).fill('#F3F4F6');
    doc.fillColor('#111827').font(bold ? 'Helvetica-Bold' : 'Helvetica').fontSize(7);
    let x = 28;
    cols.forEach((c, k) => {
      const t = c.key === 'sno' && bold ? '' : cellText(c, row);
      const money = c.type === 'money' && t ? `Rs ${t}` : t;
      doc.text(money, x + 3, y + 3, { width: widths[k] - 6, height: 11, ellipsis: true, lineBreak: false });
      x += widths[k];
    });
    doc.y = y + 15;
  };

  report.rows.forEach((r, i) => drawRow(r, i));
  if (report.totals) drawRow(report.totals, report.rows.length, true);

  const range = doc.bufferedPageRange();
  for (let i = 0; i < range.count; i++) {
    doc.switchToPage(i);
    doc.font('Helvetica').fontSize(7).fillColor('#6B7280')
      .text(`Page ${i + 1} of ${range.count}`, 28, doc.page.height - 22, { width: pageW, align: 'right', lineBreak: false });
  }
  doc.end();
}
