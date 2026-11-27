import type * as XLSX from 'xlsx-js-style';

export interface ExportXlsxOptions {
  title?: string;
  subtitle?: string;
  sumColumns?: string[];
  numberColumns?: string[];
  currencyColumns?: string[];
  percentColumns?: string[];
  totalRatioColumns?: Record<string, [string, string]>;
  totalLabelColumn?: string;
}

const THIN_BORDER = {
  top: { style: 'thin', color: { rgb: 'CBD5E1' } },
  bottom: { style: 'thin', color: { rgb: 'CBD5E1' } },
  left: { style: 'thin', color: { rgb: 'CBD5E1' } },
  right: { style: 'thin', color: { rgb: 'CBD5E1' } },
};

async function loadXlsx(): Promise<typeof XLSX> {
  const mod = (await import('xlsx-js-style')) as unknown as typeof XLSX & { default?: typeof XLSX };
  return mod.default && typeof mod.default.read === 'function' ? mod.default : mod;
}

export function sanitizeExcelCellValue(val: string | number): string | number {
  if (typeof val !== 'string' || !val) return val;
  return /^[=+\-@\t\r]/.test(val) ? `'${val}` : val;
}

export async function exportRowsToXlsx(
  filename: string,
  sheetName: string,
  rows: Record<string, string | number>[],
  options?: ExportXlsxOptions
): Promise<void> {
  const XLSX = await loadXlsx();
  const wb = XLSX.utils.book_new();
  if (rows.length === 0) {
    const ws = XLSX.utils.aoa_to_sheet(
      options?.title ? [[options.title], [options.subtitle ?? '']] : []
    );
    XLSX.utils.book_append_sheet(wb, ws, sheetName);
    XLSX.writeFile(wb, filename);
    return;
  }

  const headers = Object.keys(rows[0]);
  const sumCols = new Set(options?.sumColumns ?? []);
  const currencyCols = new Set(options?.currencyColumns ?? []);
  const percentCols = new Set(options?.percentColumns ?? []);
  const numCols = new Set([
    ...(options?.numberColumns ?? []),
    ...sumCols,
    ...currencyCols,
    ...percentCols,
  ]);
  const ratioCols = options?.totalRatioColumns ?? {};

  const aoa: (string | number)[][] = [];
  const topMetaRows: number[] = [];
  if (options?.title) {
    topMetaRows.push(aoa.length);
    aoa.push([options.title]);
  }
  if (options?.subtitle) {
    topMetaRows.push(aoa.length);
    aoa.push([options.subtitle]);
  }
  if (topMetaRows.length > 0) {
    aoa.push([]); // spacer row
  }

  const headerRowIdx = aoa.length;
  aoa.push(headers);

  const dataStartIdx = aoa.length;
  for (const r of rows) {
    aoa.push(headers.map((h) => sanitizeExcelCellValue(r[h] ?? '')));
  }
  const dataEndIdx = aoa.length - 1;

  const hasTotalRow = sumCols.size > 0 || Object.keys(ratioCols).length > 0;
  const totalRowIdx = hasTotalRow ? aoa.length : -1;
  if (hasTotalRow) {
    aoa.push(headers.map(() => ''));
  }

  const ws = XLSX.utils.aoa_to_sheet(aoa);
  const merges: XLSX.Range[] = [];
  const rowHeights: XLSX.RowInfo[] = [];

  // Title & subtitle styling + merges
  if (options?.title) {
    const r = topMetaRows[0];
    const cell = ws[XLSX.utils.encode_cell({ r, c: 0 })];
    if (cell) {
      cell.s = {
        font: { bold: true, sz: 13, color: { rgb: '0061A4' } },
        alignment: { vertical: 'center', horizontal: 'left' },
      };
    }
    if (headers.length > 1) {
      merges.push({ s: { r, c: 0 }, e: { r, c: headers.length - 1 } });
    }
    rowHeights[r] = { hpt: 24 };
  }
  if (options?.subtitle) {
    const r = options?.title ? topMetaRows[1] : topMetaRows[0];
    const cell = ws[XLSX.utils.encode_cell({ r, c: 0 })];
    if (cell) {
      cell.s = {
        font: { sz: 9, color: { rgb: '64748B' } },
        alignment: { vertical: 'center', horizontal: 'left' },
      };
    }
    if (headers.length > 1) {
      merges.push({ s: { r, c: 0 }, e: { r, c: headers.length - 1 } });
    }
    rowHeights[r] = { hpt: 18 };
  }
  if (merges.length > 0) {
    ws['!merges'] = merges;
  }

  // Header row styling
  rowHeights[headerRowIdx] = { hpt: 22 };
  headers.forEach((h, cIdx) => {
    const cell = ws[XLSX.utils.encode_cell({ r: headerRowIdx, c: cIdx })];
    if (cell) {
      cell.s = {
        fill: { fgColor: { rgb: '0061A4' } },
        font: { bold: true, sz: 10, color: { rgb: 'FFFFFF' } },
        border: {
          top: { style: 'thin', color: { rgb: '004C82' } },
          bottom: { style: 'thin', color: { rgb: '004C82' } },
          left: { style: 'thin', color: { rgb: '004C82' } },
          right: { style: 'thin', color: { rgb: '004C82' } },
        },
        alignment: {
          vertical: 'center',
          horizontal: numCols.has(h) ? 'right' : 'left',
        },
      };
    }
  });

  // Data rows styling + number formats
  for (let rIdx = dataStartIdx; rIdx <= dataEndIdx; rIdx++) {
    const isEven = (rIdx - dataStartIdx) % 2 === 1;
    const fillRgb = isEven ? 'F4F6F9' : 'FFFFFF';
    rowHeights[rIdx] = { hpt: 19 };
    headers.forEach((h, cIdx) => {
      const addr = XLSX.utils.encode_cell({ r: rIdx, c: cIdx });
      const cell = ws[addr] ?? (ws[addr] = { t: 's', v: '' });
      const isNum = numCols.has(h);
      let numFmt: string | undefined;
      if (typeof cell.v === 'number') {
        if (currencyCols.has(h)) numFmt = '"Rp" #,##0';
        else if (percentCols.has(h)) numFmt = '0.0%';
        else if (isNum) numFmt = '#,##0';
      }
      cell.s = {
        fill: { fgColor: { rgb: fillRgb } },
        font: { sz: 10, color: { rgb: '1E293B' } },
        border: THIN_BORDER,
        alignment: {
          vertical: 'center',
          horizontal: isNum ? 'right' : 'left',
        },
        ...(numFmt ? { numFmt } : {}),
      };
    });
  }

  // TOTAL row styling + SUM/ratio formulas
  if (hasTotalRow) {
    rowHeights[totalRowIdx] = { hpt: 22 };
    const labelIdx = options?.totalLabelColumn
      ? Math.max(0, headers.indexOf(options.totalLabelColumn))
      : 0;
    const dataStartExcel = dataStartIdx + 1;
    const dataEndExcel = dataEndIdx + 1;
    const totalExcel = totalRowIdx + 1;

    headers.forEach((h, cIdx) => {
      const addr = XLSX.utils.encode_cell({ r: totalRowIdx, c: cIdx });
      let cell: XLSX.CellObject = { t: 's', v: '' };
      let numFmt: string | undefined;

      if (cIdx === labelIdx) {
        cell = { t: 's', v: 'TOTAL' };
      } else if (sumCols.has(h)) {
        const colLetter = XLSX.utils.encode_col(cIdx);
        const totalVal = rows.reduce((s, r) => s + (Number(r[h]) || 0), 0);
        numFmt = currencyCols.has(h) ? '"Rp" #,##0' : '#,##0';
        cell = {
          t: 'n',
          v: totalVal,
          f: `SUM(${colLetter}${dataStartExcel}:${colLetter}${dataEndExcel})`,
        };
      } else if (ratioCols[h]) {
        const [numColName, denColName] = ratioCols[h];
        const numIdx = headers.indexOf(numColName);
        const denIdx = headers.indexOf(denColName);
        if (numIdx >= 0 && denIdx >= 0) {
          const numSum = rows.reduce((s, r) => s + (Number(r[numColName]) || 0), 0);
          const denSum = rows.reduce((s, r) => s + (Number(r[denColName]) || 0), 0);
          const nCol = XLSX.utils.encode_col(numIdx);
          const dCol = XLSX.utils.encode_col(denIdx);
          numFmt = '0.0%';
          cell = {
            t: 'n',
            v: denSum > 0 ? numSum / denSum : 0,
            f: `IF(${dCol}${totalExcel}>0,${nCol}${totalExcel}/${dCol}${totalExcel},0)`,
          };
        }
      }

      cell.s = {
        fill: { fgColor: { rgb: 'E6EEF8' } },
        font: { bold: true, sz: 10, color: { rgb: '0F172A' } },
        border: {
          top: { style: 'medium', color: { rgb: '0061A4' } },
          bottom: { style: 'thin', color: { rgb: 'CBD5E1' } },
          left: { style: 'thin', color: { rgb: 'CBD5E1' } },
          right: { style: 'thin', color: { rgb: 'CBD5E1' } },
        },
        alignment: {
          vertical: 'center',
          horizontal: numCols.has(h) ? 'right' : 'left',
        },
        ...(numFmt ? { numFmt } : {}),
      };
      ws[addr] = cell;
    });
  }

  ws['!rows'] = rowHeights;
  ws['!cols'] = headers.map((h) => {
    let maxLen = h.length;
    for (const r of rows) {
      const val = r[h];
      let text = String(val ?? '');
      if (typeof val === 'number') {
        if (currencyCols.has(h)) text = 'Rp ' + val.toLocaleString('id-ID');
        else if (percentCols.has(h)) text = (val * 100).toFixed(1) + '%';
        else if (numCols.has(h)) text = val.toLocaleString('id-ID');
      }
      if (text.length > maxLen) maxLen = text.length;
    }
    if (sumCols.has(h)) {
      const totalVal = rows.reduce((s, r) => s + (Number(r[h]) || 0), 0);
      const totalText = currencyCols.has(h)
        ? 'Rp ' + totalVal.toLocaleString('id-ID')
        : totalVal.toLocaleString('id-ID');
      if (totalText.length > maxLen) maxLen = totalText.length;
    }
    return { wch: Math.min(42, Math.max(11, maxLen + 3)) };
  });

  XLSX.utils.book_append_sheet(wb, ws, sheetName);
  XLSX.writeFile(wb, filename);
}

export async function exportMedicineTemplateXlsx(
  filename: string,
  categories: string[],
  units: string[],
  medicineTypes: string[] = []
): Promise<void> {
  const XLSX = await loadXlsx();
  const wb = XLSX.utils.book_new();

  const headers = [
    'Nama Obat *',
    'Golongan',
    'Jenis Obat',
    'Satuan',
    'Harga Beli *',
    'Harga Jual Ecer *',
    'Harga Grosir',
    'Stok *',
    'Kadaluarsa (YYYY-MM-DD)',
  ];
  const requiredCols = new Set([0, 4, 5, 7]);
  const numericCols = new Set([4, 5, 6, 7]);

  const samples: (string | number)[][] = [
    ['Paracetamol 500mg (Contoh)', 'Obat Bebas', 'Analgesik & Antipiretik', 'Strip', 2500, 4000, 3500, 50, '2027-12-31'],
    ['Amoxicillin 500mg (Contoh)', 'Obat Keras', 'Antibiotik', 'Strip', 4200, 6500, 5800, 30, '2028-06-30'],
  ];

  const aoa: (string | number)[][] = [
    ['APOTEK LESTARI — TEMPLATE IMPOR STOK OBAT'],
    ['Petunjuk: Isi mulai baris 5 (hapus/timpa baris contoh hijau). Kolom (*) wajib diisi. Lihat sheet "Panduan & Pilihan" untuk daftar Golongan, Jenis Obat & Satuan.'],
    [],
    headers,
    ...samples,
    ...Array.from({ length: 20 }, () => Array(headers.length).fill('')),
  ];

  const ws = XLSX.utils.aoa_to_sheet(aoa);
  ws['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: headers.length - 1 } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: headers.length - 1 } },
  ];

  const rowHeights: XLSX.RowInfo[] = [{ hpt: 28 }, { hpt: 22 }, { hpt: 8 }, { hpt: 24 }];
  for (let r = 4; r < aoa.length; r++) rowHeights[r] = { hpt: 20 };
  ws['!rows'] = rowHeights;

  const setBannerStyle = (cell: XLSX.CellObject | undefined, fill: string, color: string, sz: number, bold: boolean, italic = false) => {
    if (!cell) return;
    cell.s = {
      fill: { fgColor: { rgb: fill } },
      font: { bold, italic, sz, color: { rgb: color } },
      alignment: { vertical: 'center', horizontal: 'left', wrapText: true },
    };
  };
  setBannerStyle(ws['A1'], '064E3B', 'FFFFFF', 13, true);
  setBannerStyle(ws['A2'], 'ECFDF5', '065F46', 9.5, false, true);

  headers.forEach((_, cIdx) => {
    const addr = XLSX.utils.encode_cell({ r: 3, c: cIdx });
    const cell = ws[addr];
    if (!cell) return;
    const isReq = requiredCols.has(cIdx);
    cell.s = {
      fill: { fgColor: { rgb: isReq ? '047857' : '334155' } },
      font: { bold: true, sz: 10, color: { rgb: 'FFFFFF' } },
      border: THIN_BORDER,
      alignment: { vertical: 'center', horizontal: numericCols.has(cIdx) ? 'right' : cIdx === 8 ? 'center' : 'left' },
    };
  });

  for (let rIdx = 4; rIdx < aoa.length; rIdx++) {
    const isSample = rIdx < 4 + samples.length;
    headers.forEach((_, cIdx) => {
      const addr = XLSX.utils.encode_cell({ r: rIdx, c: cIdx });
      const cell = ws[addr] ?? (ws[addr] = { t: 's', v: '' });
      const isNum = numericCols.has(cIdx);
      const isDate = cIdx === 8;
      cell.s = {
        fill: { fgColor: { rgb: isSample ? 'F0FDF4' : 'FFFFFF' } },
        font: { sz: 10, color: { rgb: '1E293B' } },
        border: THIN_BORDER,
        alignment: { vertical: 'center', horizontal: isNum ? 'right' : isDate ? 'center' : 'left' },
        ...(isNum ? { numFmt: '#,##0' } : isDate ? { numFmt: '@' } : {}),
      };
    });
  }

  ws['!cols'] = [
    { wch: 30 }, { wch: 20 }, { wch: 16 }, { wch: 14 }, { wch: 15 },
    { wch: 17 }, { wch: 15 }, { wch: 11 }, { wch: 24 },
  ];
  XLSX.utils.book_append_sheet(wb, ws, 'Input Stok Obat');

  // Sheet 2: Panduan & Pilihan
  const notes = [
    '1. Nama Obat sama akan otomatis memperbarui data lama.',
    '2. Format Kadaluarsa wajib YYYY-MM-DD (mis. 2027-12-31), atau kosongkan.',
    '3. Harga & Stok isi angka polos tanpa "Rp" atau titik.',
    '4. Golongan/Satuan kosong otomatis memakai default (Obat Bebas / Strip).',
    '5. Hapus atau timpa 2 baris contoh hijau sebelum impor.',
  ];
  const guideRows = Math.max(categories.length, medicineTypes.length, units.length, notes.length);
  const guideAoa: string[][] = [
    ['PANDUAN PENGISIAN & DAFTAR PILIHAN VALID'],
    ['Golongan Valid', 'Jenis Obat Valid (Opsional)', 'Satuan Valid (Default: Strip)', 'Catatan Penting Impor'],
    ...Array.from({ length: guideRows }, (_, i) => [
      categories[i] ?? '',
      medicineTypes[i] ?? '',
      units[i] ?? '',
      notes[i] ?? '',
    ]),
  ];
  const wsGuide = XLSX.utils.aoa_to_sheet(guideAoa);
  wsGuide['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: 3 } }];
  wsGuide['!rows'] = [{ hpt: 26 }, { hpt: 22 }, ...Array(guideRows).fill({ hpt: 19 })];
  setBannerStyle(wsGuide['A1'], '064E3B', 'FFFFFF', 12, true);
  [0, 1, 2, 3].forEach((cIdx) => {
    const cell = wsGuide[XLSX.utils.encode_cell({ r: 1, c: cIdx })];
    if (cell) {
      cell.s = {
        fill: { fgColor: { rgb: cIdx < 3 ? '047857' : '334155' } },
        font: { bold: true, sz: 10, color: { rgb: 'FFFFFF' } },
        border: THIN_BORDER,
        alignment: { vertical: 'center', horizontal: 'left' },
      };
    }
  });
  for (let r = 2; r < guideAoa.length; r++) {
    [0, 1, 2, 3].forEach((cIdx) => {
      const addr = XLSX.utils.encode_cell({ r, c: cIdx });
      const cell = wsGuide[addr] ?? (wsGuide[addr] = { t: 's', v: '' });
      cell.s = {
        fill: { fgColor: { rgb: r % 2 === 0 ? 'FFFFFF' : 'F8FAFC' } },
        font: { sz: 10, color: { rgb: '1E293B' } },
        border: THIN_BORDER,
        alignment: { vertical: 'center', horizontal: 'left' },
      };
    });
  }
  wsGuide['!cols'] = [{ wch: 26 }, { wch: 26 }, { wch: 24 }, { wch: 60 }];
  XLSX.utils.book_append_sheet(wb, wsGuide, 'Panduan & Pilihan');

  XLSX.writeFile(wb, filename);
}

export async function readSpreadsheetMatrix(file: File): Promise<string[][]> {
  const XLSX = await loadXlsx();
  const buf = await file.arrayBuffer();
  const wb = XLSX.read(buf, { type: 'array', cellNF: true });
  const preferredSheet =
    wb.SheetNames.find((n) => {
      const low = n.trim().toLowerCase();
      return low === 'stok obat' || low === 'input stok obat';
    }) || wb.SheetNames[0];
  if (!preferredSheet) return [];
  const ws = wb.Sheets[preferredSheet];
  const ref = ws['!ref'];
  if (!ref) return [];
  const range = XLSX.utils.decode_range(ref);
  const rows: string[][] = [];
  for (let r = range.s.r; r <= range.e.r; r++) {
    const row: string[] = [];
    let hasValue = false;
    for (let c = range.s.c; c <= range.e.c; c++) {
      const addr = XLSX.utils.encode_cell({ r, c });
      const cell = ws[addr];
      let text = '';
      if (cell) {
        if (cell.t === 'n' && typeof cell.v === 'number') {
          const fmt = typeof cell.z === 'string' ? cell.z : '';
          if (fmt && XLSX.SSF?.is_date?.(fmt)) {
            const dc = XLSX.SSF.parse_date_code(cell.v);
            if (dc && dc.y && dc.m && dc.d) {
              text = `${String(dc.y).padStart(4, '0')}-${String(dc.m).padStart(2, '0')}-${String(dc.d).padStart(2, '0')}`;
            } else {
              text = String(cell.v).trim();
            }
          } else {
            text = String(cell.v).trim();
          }
        } else if (cell.t === 'd' && cell.v instanceof Date && !isNaN(cell.v.getTime())) {
          text = cell.v.toISOString().slice(0, 10);
        } else {
          text = String(cell.v ?? '').trim();
        }
      }
      if (text !== '') hasValue = true;
      row.push(text);
    }
    if (hasValue) rows.push(row);
  }
  return rows;
}

export interface ParsedMedicineImportRow {
  name: string;
  category: string;
  medicineType: string;
  unit: string;
  buyPrice: number;
  sellPrice: number;
  wholesalePrice: number;
  stock: number;
  expiryDate: string | null;
  barcode: string | null;
}

const normalizeHeader = (raw: string): string =>
  raw.trim().replace(/\s*\*$/, '').trim().toLowerCase();

export function parseStrictMoneyInt(raw: string): number | null {
  if (!raw.trim()) return 0;
  const cleaned = raw.trim().replace(/^rp\.?\s*/i, '').replace(/\s+/g, '');
  if (!cleaned) return null;
  if (cleaned.startsWith('-')) return null;
  if (/[^0-9.,]/.test(cleaned)) return null;

  let normalized = cleaned;
  if (/^\d{1,3}(\.\d{3})+(,\d+)?$/.test(cleaned)) {
    normalized = cleaned.replace(/\./g, '').replace(',', '.');
  } else if (/^\d{1,3}(,\d{3})+(\.\d+)?$/.test(cleaned)) {
    normalized = cleaned.replace(/,/g, '');
  } else if (cleaned.includes(',') && !cleaned.includes('.')) {
    normalized = cleaned.replace(',', '.');
  } else if (cleaned.includes(',') && cleaned.includes('.')) {
    normalized = cleaned.replace(/\./g, '').replace(',', '.');
  }

  const num = Number(normalized);
  if (!Number.isFinite(num) || num < 0 || !Number.isInteger(num) || num > 2147483647) {
    return null;
  }
  return num;
}

function strictCalendarDate(y: number, m: number, d: number): string | null {
  if (m < 1 || m > 12 || d < 1 || y < 1900 || y > 2200) return null;
  const dt = new Date(Date.UTC(y, m - 1, d));
  if (
    dt.getUTCFullYear() !== y ||
    dt.getUTCMonth() !== m - 1 ||
    dt.getUTCDate() !== d
  ) {
    return null;
  }
  return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

export function normalizeImportDate(raw: string): { valid: boolean; value: string | null } {
  const s = raw.trim();
  if (!s) return { valid: true, value: null };

  if (/^\d{5}$/.test(s)) {
    const serial = Number(s);
    if (serial >= 20000 && serial <= 100000) {
      const dt = new Date(Date.UTC(1899, 11, 30) + serial * 86_400_000);
      return { valid: true, value: dt.toISOString().slice(0, 10) };
    }
  }

  let match = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (match) {
    const res = strictCalendarDate(Number(match[1]), Number(match[2]), Number(match[3]));
    return res ? { valid: true, value: res } : { valid: false, value: null };
  }
  match = s.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (match) {
    const res = strictCalendarDate(Number(match[3]), Number(match[2]), Number(match[1]));
    return res ? { valid: true, value: res } : { valid: false, value: null };
  }
  match = s.match(/^(\d{4})\/(\d{2})\/(\d{2})$/);
  if (match) {
    const res = strictCalendarDate(Number(match[1]), Number(match[2]), Number(match[3]));
    return res ? { valid: true, value: res } : { valid: false, value: null };
  }
  match = s.match(/^(\d{2})-(\d{2})-(\d{4})$/);
  if (match) {
    const res = strictCalendarDate(Number(match[3]), Number(match[2]), Number(match[1]));
    return res ? { valid: true, value: res } : { valid: false, value: null };
  }

  return { valid: false, value: null };
}

/**
 * Header-aware spreadsheet parser compatible with both Web exports/templates
 * and Android Kotlin ExcelHelper templates/exports.
 */
export function parseMedicineSpreadsheet(matrix: string[][]): {
  rows: ParsedMedicineImportRow[];
  skippedInvalid: number;
} {
  if (matrix.length < 2) throw new Error('File kosong');

  let headerIdx = -1;
  const colIndex = new Map<string, number>();
  const scanLimit = Math.min(matrix.length, 12);
  for (let r = 0; r < scanLimit; r++) {
    const candidate = new Map<string, number>();
    matrix[r].forEach((cell, idx) => {
      const h = normalizeHeader(cell || '');
      if (h && !candidate.has(h)) candidate.set(h, idx);
    });
    if (candidate.has('nama obat')) {
      headerIdx = r;
      candidate.forEach((v, k) => colIndex.set(k, v));
      break;
    }
  }

  if (headerIdx < 0) {
    throw new Error("File bukan template Stok Obat: kolom 'Nama Obat' tidak ditemukan");
  }

  const colOf = (...aliases: string[]): number | undefined => {
    for (const a of aliases) {
      const idx = colIndex.get(a);
      if (idx !== undefined) return idx;
    }
    return undefined;
  };

  const iName = colOf('nama obat')!;
  const iCat = colOf('golongan', 'kategori');
  const iType = colOf('jenis obat', 'jenis');
  const iUnit = colOf('satuan');
  const iBuy = colOf('harga beli');
  const iSell = colOf('harga jual ecer', 'harga jual');
  const iWholesale = colOf('harga grosir', 'harga jual grosir');
  const iStock = colOf('stok');
  const iExpiry = colOf('kadaluarsa (yyyy-mm-dd)', 'tanggal kadaluarsa', 'kadaluarsa');
  const iBarcode = colOf('barcode');

  const cellText = (row: string[], idx: number | undefined): string =>
    idx !== undefined && idx < row.length ? String(row[idx] ?? '').trim() : '';

  const rows: ParsedMedicineImportRow[] = [];
  let skippedInvalid = 0;

  for (const rawRow of matrix.slice(headerIdx + 1)) {
    const name = cellText(rawRow, iName);
    if (!name) {
      const hasAnyOther = rawRow.some((c) => String(c ?? '').trim() !== '');
      if (hasAnyOther) skippedInvalid++;
      continue;
    }
    if (name.toUpperCase() === 'TOTAL') continue;

    const sellParsed = iSell !== undefined ? parseStrictMoneyInt(cellText(rawRow, iSell)) : 0;
    const stockParsed = iStock !== undefined ? parseStrictMoneyInt(cellText(rawRow, iStock)) : 0;
    const buyParsed = iBuy !== undefined ? parseStrictMoneyInt(cellText(rawRow, iBuy)) : 0;

    if (sellParsed === null || stockParsed === null || buyParsed === null) {
      skippedInvalid++;
      continue;
    }

    const wholesaleRaw = iWholesale !== undefined ? parseStrictMoneyInt(cellText(rawRow, iWholesale)) : 0;
    const wholesalePrice = wholesaleRaw ?? 0;

    const expiryCheck = normalizeImportDate(cellText(rawRow, iExpiry));
    if (!expiryCheck.valid) {
      skippedInvalid++;
      continue;
    }

    const barcode = cellText(rawRow, iBarcode) || null;
    rows.push({
      name,
      category: cellText(rawRow, iCat) || 'Umum',
      medicineType: cellText(rawRow, iType),
      unit: cellText(rawRow, iUnit) || 'Strip',
      buyPrice: buyParsed,
      sellPrice: sellParsed,
      wholesalePrice,
      stock: stockParsed,
      expiryDate: expiryCheck.value,
      barcode,
    });
  }

  return { rows, skippedInvalid };
}
