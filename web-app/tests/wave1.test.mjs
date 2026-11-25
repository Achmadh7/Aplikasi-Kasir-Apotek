import assert from 'node:assert/strict';
import test from 'node:test';
import XLSX from 'xlsx-js-style';
import {
  normalizeImportDate,
  parseMedicineSpreadsheet,
  parseStrictMoneyInt,
  readSpreadsheetMatrix,
} from '../src/excel.ts';
import { mergeSyncIntoState } from '../src/syncMerge.ts';

function emptyWatermarks() {
  return {
    medicines: new Map(),
    sales: new Map(),
    purchases: new Map(),
    cashEntries: new Map(),
    suppliers: new Map(),
    saleItems: new Map(),
    purchaseItems: new Map(),
  };
}

function emptyRemote() {
  return {
    obat: [],
    penjualan: [],
    pbf: [],
    kas: [],
    pemasok: [],
    penjualan_item: [],
    pbf_item: [],
  };
}

function baseDb(overrides = {}) {
  return {
    medicines: [],
    sales: [],
    saleItems: [],
    cashEntries: [],
    purchases: [],
    purchaseItems: [],
    suppliers: [],
    closedMonths: [],
    lastSyncAt: 0,
    ...overrides,
  };
}

const testMappers = {
  obatFromMap: (m, localId) => ({
    id: localId,
    remoteId: m.remoteId,
    updatedAt: m.updatedAt,
    terhapus: Boolean(m.terhapus),
    kotor: false,
    name: m.name,
    category: m.category || 'Umum',
    unit: m.unit || 'Strip',
    buyPrice: m.buyPrice || 0,
    sellPrice: m.sellPrice || 0,
    wholesalePrice: m.wholesalePrice || 0,
    stock: m.stock || 0,
    expiryDate: m.expiryDate ?? null,
    barcode: m.barcode ?? null,
    createdAt: m.createdAt || '',
  }),
  penjualanFromMap: (m, localId) => ({
    id: localId,
    remoteId: m.remoteId,
    updatedAt: m.updatedAt,
    terhapus: Boolean(m.terhapus),
    kotor: false,
    invoiceNo: m.invoiceNo || '',
    total: m.total || 0,
    paymentMethod: m.paymentMethod || 'Tunai',
    amountPaid: m.amountPaid || 0,
    changeAmount: m.changeAmount || 0,
    createdAt: m.createdAt || '',
  }),
  pbfFromMap: (m, localId) => ({
    id: localId,
    remoteId: m.remoteId,
    updatedAt: m.updatedAt,
    terhapus: Boolean(m.terhapus),
    kotor: false,
    pbfName: m.pbfName || '',
    invoiceNo: m.invoiceNo || '',
    date: m.date || '',
    total: m.total || 0,
    ppn: m.ppn || 0,
    paymentType: m.paymentType || 'CASH',
    dueDate: m.dueDate || '',
    createdAt: m.createdAt || '',
  }),
  kasFromMap: (m, localId) => ({
    id: localId,
    remoteId: m.remoteId,
    updatedAt: m.updatedAt,
    terhapus: Boolean(m.terhapus),
    kotor: false,
    type: m.type || 'in',
    category: m.category || '',
    description: m.description || '',
    amount: m.amount || 0,
    source: m.source || 'manual',
    createdAt: m.createdAt || '',
  }),
  pemasokFromMap: (m, localId) => ({
    id: localId,
    remoteId: m.remoteId,
    updatedAt: m.updatedAt,
    terhapus: Boolean(m.terhapus),
    kotor: false,
    name: m.name || '',
    contact: m.contact || '',
    createdAt: m.createdAt || '',
  }),
  itemJualFromMap: (m, localId, saleId, medicineId) => ({
    id: localId,
    saleId,
    saleRemoteId: m.saleRemoteId,
    medicineId,
    medicineRemoteId: m.medicineRemoteId || '',
    remoteId: m.remoteId,
    updatedAt: m.updatedAt,
    terhapus: Boolean(m.terhapus),
    kotor: false,
    medicineName: m.medicineName || '',
    category: m.category || '',
    unit: m.unit || '',
    qty: m.qty || 0,
    price: m.price || 0,
    priceType: m.priceType === 'GROSIR' ? 'GROSIR' : 'ECER',
    buyPrice: m.buyPrice || 0,
    subtotal: m.subtotal || 0,
  }),
  itemPbfFromMap: (m, localId, purchaseId) => ({
    id: localId,
    purchaseId,
    purchaseRemoteId: m.purchaseRemoteId,
    remoteId: m.remoteId,
    updatedAt: m.updatedAt,
    terhapus: Boolean(m.terhapus),
    kotor: false,
    medicineName: m.medicineName || '',
    qty: m.qty || 0,
    unit: m.unit || 'Strip',
    buyPrice: m.buyPrice || 0,
    subtotal: m.subtotal || 0,
  }),
};

test('mergeSyncIntoState preserves mid-sync local sale and dirtiness', () => {
  const pushed = emptyWatermarks();
  pushed.medicines.set('obat-1', 100);

  const liveDb = baseDb({
    medicines: [
      {
        id: 1,
        remoteId: 'obat-1',
        updatedAt: 200,
        terhapus: false,
        kotor: true,
        name: 'Paracetamol',
        category: 'Pereda Nyeri',
        unit: 'Strip',
        buyPrice: 3000,
        sellPrice: 5000,
        wholesalePrice: 4500,
        stock: 9,
        expiryDate: null,
        barcode: null,
        createdAt: '2026-11-25T02:00:00.000Z',
      },
    ],
    sales: [
      {
        id: 1,
        remoteId: 'jual-mid-sync',
        updatedAt: 200,
        terhapus: false,
        kotor: true,
        invoiceNo: 'INV - 25/11/26',
        total: 5000,
        paymentMethod: 'Tunai',
        amountPaid: 5000,
        changeAmount: 0,
        createdAt: '2026-11-25T03:00:00.000Z',
      },
    ],
  });

  const remote = emptyRemote();
  remote.obat.push({
    id: 'obat-2',
    data: {
      remoteId: 'obat-2',
      updatedAt: 150,
      terhapus: false,
      name: 'Amoxicillin',
      category: 'Antibiotik',
      unit: 'Strip',
      buyPrice: 4000,
      sellPrice: 7000,
      wholesalePrice: 0,
      stock: 20,
    },
  });

  const merged = mergeSyncIntoState(liveDb, remote, pushed, testMappers, 999);
  assert.equal(merged.nextDb.sales.length, 1);
  assert.equal(merged.nextDb.sales[0].remoteId, 'jual-mid-sync');
  assert.equal(merged.nextDb.sales[0].kotor, true);
  assert.equal(merged.nextDb.medicines[0].kotor, true);
  assert.equal(merged.nextDb.medicines[0].stock, 9);
  assert.equal(merged.nextDb.medicines.length, 2);
  assert.equal(merged.pulled, 1);
  assert.equal(merged.stillDirty, true);
});

test('mergeSyncIntoState blocks remote pull into closedMonths', () => {
  const db = baseDb({
    closedMonths: ['2026-10'],
    sales: [
      {
        id: 1,
        remoteId: 'jual-oct',
        updatedAt: 100,
        terhapus: false,
        kotor: false,
        invoiceNo: 'INV - 10/10/26',
        total: 10000,
        paymentMethod: 'Tunai',
        amountPaid: 10000,
        changeAmount: 0,
        createdAt: '2026-10-10T05:00:00.000Z',
      },
    ],
  });

  const remote = emptyRemote();
  remote.penjualan.push(
    {
      id: 'jual-oct',
      data: {
        remoteId: 'jual-oct',
        updatedAt: 999999,
        terhapus: true,
        invoiceNo: 'INV - 10/10/26',
        total: 10000,
        paymentMethod: 'Tunai',
        amountPaid: 10000,
        changeAmount: 0,
        createdAt: '2026-10-10T05:00:00.000Z',
      },
    },
    {
      id: 'jual-oct-new',
      data: {
        remoteId: 'jual-oct-new',
        updatedAt: 999999,
        terhapus: false,
        invoiceNo: 'INV - 15/10/26',
        total: 20000,
        paymentMethod: 'Tunai',
        amountPaid: 20000,
        changeAmount: 0,
        createdAt: '2026-10-15T05:00:00.000Z',
      },
    }
  );
  remote.pbf.push({
    id: 'pbf-oct',
    data: {
      remoteId: 'pbf-oct',
      updatedAt: 999999,
      terhapus: false,
      pbfName: 'Kimia Farma',
      date: '2026-10-20',
      total: 50000,
      paymentType: 'CASH',
    },
  });

  const res = mergeSyncIntoState(db, remote, emptyWatermarks(), testMappers, 1000);
  assert.equal(res.nextDb.sales.length, 1);
  assert.equal(res.nextDb.sales[0].terhapus, false);
  assert.equal(res.nextDb.purchases.length, 0);
  assert.equal(res.pulled, 0);
});

test('parseMedicineSpreadsheet imports Kotlin column order without swapping prices', () => {
  const kotlinMatrix = [
    [
      'Nama Obat',
      'Kategori',
      'Satuan',
      'Harga Jual',
      'Harga Jual Grosir',
      'Harga Beli',
      'Stok',
      'Tanggal Kadaluarsa',
      'Barcode',
    ],
    [
      'Paracetamol 500mg',
      'Pereda Nyeri',
      'Strip',
      '12.000',
      '10.500',
      '8.000',
      '50',
      '46752',
      '899123456',
    ],
    ['Obat Rusak', 'Umum', 'Strip', 'delapan ribu', '0', '0', '10', ''],
    ['Obat Tgl Salah', 'Umum', 'Strip', '5000', '0', '3000', '10', '2027-02-30'],
    ['TOTAL', '', '', '', '', '', '50', '', ''],
  ];

  const parsed = parseMedicineSpreadsheet(kotlinMatrix);
  assert.equal(parsed.rows.length, 1);
  assert.equal(parsed.skippedInvalid, 2);
  assert.deepEqual(parsed.rows[0], {
    name: 'Paracetamol 500mg',
    category: 'Pereda Nyeri',
    unit: 'Strip',
    buyPrice: 8000,
    sellPrice: 12000,
    wholesalePrice: 10500,
    stock: 50,
    expiryDate: '2027-12-31',
    barcode: '899123456',
  });
});

test('parseMedicineSpreadsheet imports Web template with banner rows and asterisk headers', () => {
  const webMatrix = [
    ['APOTEK LESTARI — TEMPLATE IMPOR STOK OBAT'],
    ['Petunjuk: ...'],
    [
      'Nama Obat *',
      'Kategori',
      'Satuan',
      'Harga Beli *',
      'Harga Jual Ecer *',
      'Harga Grosir',
      'Stok *',
      'Kadaluarsa (YYYY-MM-DD)',
    ],
    ['Vitamin C', 'Vitamin', 'Botol', 'Rp 35.000', '50,000', '42.000,00', '20', '31/12/2027'],
  ];

  const parsed = parseMedicineSpreadsheet(webMatrix);
  assert.equal(parsed.rows.length, 1);
  assert.equal(parsed.skippedInvalid, 0);
  assert.equal(parsed.rows[0].buyPrice, 35000);
  assert.equal(parsed.rows[0].sellPrice, 50000);
  assert.equal(parsed.rows[0].wholesalePrice, 42000);
  assert.equal(parsed.rows[0].stock, 20);
  assert.equal(parsed.rows[0].expiryDate, '2027-12-31');
});

test('parseStrictMoneyInt and normalizeImportDate reject malformed inputs', () => {
  assert.equal(parseStrictMoneyInt('Rp 12.500'), 12500);
  assert.equal(parseStrictMoneyInt('Rp '), null);
  assert.equal(parseStrictMoneyInt('-500'), null);
  assert.equal(parseStrictMoneyInt('12.5'), null);
  assert.deepEqual(normalizeImportDate('2027-02-30'), { valid: false, value: null });
  assert.deepEqual(normalizeImportDate('2028-02-29'), { valid: true, value: '2028-02-29' });
});

test('readSpreadsheetMatrix extracts Excel date serial and numeric values cleanly', async () => {
  const wb = XLSX.utils.book_new();
  const ws = XLSX.utils.aoa_to_sheet([
    [
      'Nama Obat',
      'Kategori',
      'Satuan',
      'Harga Jual',
      'Harga Jual Grosir',
      'Harga Beli',
      'Stok',
      'Tanggal Kadaluarsa',
    ],
    ['Obat Test', 'Umum', 'Strip', 12000, 10500, 8000, 50, 46752],
  ]);
  ws['D2'].z = '#,##0';
  ws['H2'].z = 'yyyy-mm-dd';
  XLSX.utils.book_append_sheet(wb, ws, 'Stok Obat');
  const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
  const fakeFile = {
    arrayBuffer: async () => buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength),
  };

  const matrix = await readSpreadsheetMatrix(fakeFile);
  const parsed = parseMedicineSpreadsheet(matrix);
  assert.equal(parsed.rows.length, 1);
  assert.equal(parsed.rows[0].sellPrice, 12000);
  assert.equal(parsed.rows[0].buyPrice, 8000);
  assert.equal(parsed.rows[0].expiryDate, '2027-12-31');
});
