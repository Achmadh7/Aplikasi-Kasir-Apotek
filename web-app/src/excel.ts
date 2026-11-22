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

export async function exportRowsToXlsx(
  filename: string,
  sheetName: string,
  rows: Record<string, string | number>[],
  options?: ExportXlsxOptions
): Promise<void> {
  const XLSX = await import('xlsx-js-style');
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
    aoa.push(headers.map((h) => r[h] ?? ''));
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
  units: string[]
): Promise<void> {
  const XLSX = await import('xlsx-js-style');
  const wb = XLSX.utils.book_new();

  const headers = [
    'Nama Obat *',
    'Kategori',
    'Satuan',
    'Harga Beli *',
    'Harga Jual Ecer *',
    'Harga Grosir',
    'Stok *',
    'Kadaluarsa (YYYY-MM-DD)',
  ];
  const requiredCols = new Set([0, 3, 4, 6]);
  const numericCols = new Set([3, 4, 5, 6]);

  const samples: (string | number)[][] = [
    ['Paracetamol 500mg (Contoh)', 'Obat Bebas', 'Strip', 2500, 4000, 3500, 50, '2027-12-31'],
    ['Amoxicillin 500mg (Contoh)', 'Antibiotik', 'Strip', 4200, 6500, 5800, 30, '2028-06-30'],
  ];

  const aoa: (string | number)[][] = [
    ['APOTEK LESTARI — TEMPLATE IMPOR STOK OBAT'],
    ['Petunjuk: Isi mulai baris 5 (hapus/timpa baris contoh hijau). Kolom (*) wajib diisi. Lihat sheet "Panduan & Pilihan" untuk daftar Kategori & Satuan.'],
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
      alignment: { vertical: 'center', horizontal: numericCols.has(cIdx) ? 'right' : cIdx === 7 ? 'center' : 'left' },
    };
  });

  for (let rIdx = 4; rIdx < aoa.length; rIdx++) {
    const isSample = rIdx < 4 + samples.length;
    headers.forEach((_, cIdx) => {
      const addr = XLSX.utils.encode_cell({ r: rIdx, c: cIdx });
      const cell = ws[addr] ?? (ws[addr] = { t: 's', v: '' });
      const isNum = numericCols.has(cIdx);
      const isDate = cIdx === 7;
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
    { wch: 30 }, { wch: 20 }, { wch: 14 }, { wch: 15 },
    { wch: 17 }, { wch: 15 }, { wch: 11 }, { wch: 24 },
  ];
  XLSX.utils.book_append_sheet(wb, ws, 'Input Stok Obat');

  // Sheet 2: Panduan & Pilihan
  const notes = [
    '1. Nama Obat sama akan otomatis memperbarui data lama.',
    '2. Format Kadaluarsa wajib YYYY-MM-DD (mis. 2027-12-31), atau kosongkan.',
    '3. Harga & Stok isi angka polos tanpa "Rp" atau titik.',
    '4. Kategori/Satuan kosong otomatis memakai default (Umum / Strip).',
    '5. Hapus atau timpa 2 baris contoh hijau sebelum impor.',
  ];
  const guideRows = Math.max(categories.length, units.length, notes.length);
  const guideAoa: string[][] = [
    ['PANDUAN PENGISIAN & DAFTAR PILIHAN VALID'],
    ['Kategori Valid (Default: Umum)', 'Satuan Valid (Default: Strip)', 'Catatan Penting Impor'],
    ...Array.from({ length: guideRows }, (_, i) => [
      categories[i] ?? '',
      units[i] ?? '',
      notes[i] ?? '',
    ]),
  ];
  const wsGuide = XLSX.utils.aoa_to_sheet(guideAoa);
  wsGuide['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: 2 } }];
  wsGuide['!rows'] = [{ hpt: 26 }, { hpt: 22 }, ...Array(guideRows).fill({ hpt: 19 })];
  setBannerStyle(wsGuide['A1'], '064E3B', 'FFFFFF', 12, true);
  [0, 1, 2].forEach((cIdx) => {
    const cell = wsGuide[XLSX.utils.encode_cell({ r: 1, c: cIdx })];
    if (cell) {
      cell.s = {
        fill: { fgColor: { rgb: cIdx < 2 ? '047857' : '334155' } },
        font: { bold: true, sz: 10, color: { rgb: 'FFFFFF' } },
        border: THIN_BORDER,
        alignment: { vertical: 'center', horizontal: 'left' },
      };
    }
  });
  for (let r = 2; r < guideAoa.length; r++) {
    [0, 1, 2].forEach((cIdx) => {
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
  wsGuide['!cols'] = [{ wch: 28 }, { wch: 26 }, { wch: 62 }];
  XLSX.utils.book_append_sheet(wb, wsGuide, 'Panduan & Pilihan');

  XLSX.writeFile(wb, filename);
}

export async function readSpreadsheetMatrix(file: File): Promise<string[][]> {
  const XLSX = await import('xlsx-js-style');
  const buf = await file.arrayBuffer();
  const wb = XLSX.read(buf, { type: 'array' });
  const firstSheet = wb.SheetNames[0];
  if (!firstSheet) return [];
  const ws = wb.Sheets[firstSheet];
  const matrix = XLSX.utils.sheet_to_json<unknown[]>(ws, { header: 1, defval: '' });
  return matrix
    .map((row) => (Array.isArray(row) ? row.map((c) => String(c ?? '').trim()) : []))
    .filter((row) => row.some((c) => c !== ''));
}
