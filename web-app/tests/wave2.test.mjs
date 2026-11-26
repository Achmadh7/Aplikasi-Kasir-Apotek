import assert from 'node:assert/strict';
import test from 'node:test';
import { parseAndValidateBackupJson } from '../src/backup.ts';
import {
  DELTA_OVERLAP_MS,
  applyCloudResetEpoch,
  computeAtomicStockUpdates,
  computeDeltaSinceMs,
  mergeMedicineDeltaRows,
  mergeSyncIntoState,
} from '../src/syncMerge.ts';
import {
  compareNameID,
  filterAndRankMedicines,
  nextProgressiveLimit,
} from '../src/types.ts';

let seq = 1;
const fakeGenId = (prefix) => `${prefix}-test-${seq++}`;

function sampleBackupObject() {
  return {
    version: 1,
    app: 'apotek-lestari',
    exportedAtMillis: 1700000000000,
    medicines: [
      {
        id: 1,
        name: 'Paracetamol 500mg',
        category: 'Pereda Nyeri',
        unit: 'Strip',
        buyPrice: 3000,
        sellPrice: 5000,
        wholesalePrice: 4500,
        stock: 20,
        expiryDate: '2027-12-31',
        barcode: '899123456',
        createdAt: '2026-11-01T02:00:00.000Z',
        remoteId: 'obat-1',
        updatedAt: 1000,
      },
    ],
    sales: [
      {
        id: 1,
        invoiceNo: 'INV - 01/11/26',
        total: 10000,
        paymentMethod: 'Tunai',
        amountPaid: 15000,
        changeAmount: 5000,
        createdAt: '2026-11-01T03:00:00.000Z',
        remoteId: 'jual-1',
        updatedAt: 1000,
      },
    ],
    saleItems: [
      {
        id: 1,
        saleId: 1,
        medicineId: 1,
        medicineName: 'Paracetamol 500mg',
        category: 'Pereda Nyeri',
        unit: 'Strip',
        qty: 2,
        price: 5000,
        priceType: 'ECER',
        buyPrice: 3000,
        subtotal: 10000,
        remoteId: 'jualitem-1',
        updatedAt: 1000,
      },
    ],
    cashEntries: [
      {
        id: 1,
        type: 'in',
        category: 'Penjualan',
        description: 'INV - 01/11/26',
        amount: 10000,
        source: 'sale',
        createdAt: '2026-11-01T03:00:00.000Z',
        remoteId: 'kas-1',
        updatedAt: 1000,
      },
    ],
    purchases: [
      {
        id: 1,
        pbfName: 'Kimia Farma',
        invoiceNo: 'KF-1',
        date: '2026-11-02',
        total: 33000,
        ppn: 3000,
        paymentType: 'CASH',
        dueDate: '',
        createdAt: '2026-11-02T04:00:00.000Z',
        remoteId: 'pbf-1',
        updatedAt: 1000,
      },
    ],
    purchaseItems: [
      {
        id: 1,
        purchaseId: 1,
        medicineName: 'Paracetamol 500mg',
        qty: 10,
        unit: 'Strip',
        buyPrice: 3000,
        subtotal: 30000,
        remoteId: 'pbfitem-1',
        updatedAt: 1000,
      },
    ],
    suppliers: [
      {
        id: 1,
        name: 'Kimia Farma',
        contact: '081234',
        createdAt: '2026-11-01T01:00:00.000Z',
        remoteId: 'pemasok-1',
        updatedAt: 1000,
      },
    ],
    closedMonths: ['2026-10', 'invalid-month'],
  };
}

test('parseAndValidateBackupJson parses valid backup and bumps updatedAt to restore time', () => {
  const nowMs = 9999999;
  const parsed = parseAndValidateBackupJson(JSON.stringify(sampleBackupObject()), fakeGenId, nowMs);
  assert.equal(parsed.medicines.length, 1);
  assert.equal(parsed.medicines[0].updatedAt, nowMs);
  assert.equal(parsed.sales[0].kotor, true);
  assert.deepEqual(parsed.closedMonths, ['2026-10']);
});

test('parseAndValidateBackupJson rejects tampered math, negative numbers, orphans, and duplicates', () => {
  const badSub = sampleBackupObject();
  badSub.saleItems[0].subtotal = 1;
  assert.throws(() => parseAndValidateBackupJson(JSON.stringify(badSub), fakeGenId, 1000), /subtotal/);

  const badTotal = sampleBackupObject();
  badTotal.sales[0].total = 9999;
  assert.throws(() => parseAndValidateBackupJson(JSON.stringify(badTotal), fakeGenId, 1000), /total/);

  const badPaid = sampleBackupObject();
  badPaid.sales[0].amountPaid = 5000;
  badPaid.sales[0].changeAmount = 0;
  assert.throws(() => parseAndValidateBackupJson(JSON.stringify(badPaid), fakeGenId, 1000), /amountPaid/);

  const badNeg = sampleBackupObject();
  badNeg.medicines[0].stock = -5;
  assert.throws(() => parseAndValidateBackupJson(JSON.stringify(badNeg), fakeGenId, 1000), /di luar batas/);

  const badOrphan = sampleBackupObject();
  badOrphan.saleItems[0].saleId = 999;
  assert.throws(() => parseAndValidateBackupJson(JSON.stringify(badOrphan), fakeGenId, 1000), /saleId 999/);

  const badBarcode = sampleBackupObject();
  badBarcode.medicines.push({ ...badBarcode.medicines[0], id: 2, remoteId: 'obat-2' });
  assert.throws(() => parseAndValidateBackupJson(JSON.stringify(badBarcode), fakeGenId, 1000), /Barcode duplikat/);

  const badType = sampleBackupObject();
  badType.cashEntries[0].type = 'transfer';
  assert.throws(() => parseAndValidateBackupJson(JSON.stringify(badType), fakeGenId, 1000), /type harus/);
});

test('applyCloudResetEpoch wipes pre-reset rows and preserves post-reset mutations', () => {
  const db = {
    medicines: [
      { id: 1, remoteId: 'obat-old', updatedAt: 500, terhapus: false, kotor: true, name: 'Lama', category: 'Umum', unit: 'Strip', buyPrice: 1, sellPrice: 2, wholesalePrice: 0, stock: 5, expiryDate: null, barcode: null, createdAt: '' },
      { id: 2, remoteId: 'obat-new', updatedAt: 2000, terhapus: false, kotor: true, name: 'Baru', category: 'Umum', unit: 'Strip', buyPrice: 1, sellPrice: 2, wholesalePrice: 0, stock: 5, expiryDate: null, barcode: null, createdAt: '' },
    ],
    sales: [{ id: 1, remoteId: 'jual-old', updatedAt: 500, terhapus: false, kotor: false, invoiceNo: 'INV', total: 2, paymentMethod: 'Tunai', amountPaid: 2, changeAmount: 0, createdAt: '' }],
    saleItems: [],
    cashEntries: [],
    purchases: [],
    purchaseItems: [],
    suppliers: [],
    closedMonths: ['2026-10'],
    lastSyncAt: 400,
    lastResetSeenAt: 0,
    pendingCloudReplaceAt: 500,
  };

  const next = applyCloudResetEpoch(db, 1000);
  assert.equal(next.medicines.length, 1);
  assert.equal(next.medicines[0].remoteId, 'obat-new');
  assert.equal(next.sales.length, 0);
  assert.deepEqual(next.closedMonths, []);
  assert.equal(next.lastResetSeenAt, 1000);
  assert.equal(next.pendingCloudReplaceAt, 0);
});

test('mergeSyncIntoState clears matching replaceHandledAt and keeps newer restore', () => {
  const base = {
    medicines: [],
    sales: [],
    saleItems: [],
    cashEntries: [],
    purchases: [],
    purchaseItems: [],
    suppliers: [],
    closedMonths: [],
    lastSyncAt: 0,
    pendingCloudReplaceAt: 5000,
    lastResetSeenAt: 0,
  };
  const emptyRemote = { obat: [], penjualan: [], pbf: [], kas: [], pemasok: [], penjualan_item: [], pbf_item: [] };
  const emptyPush = { medicines: new Map(), sales: new Map(), purchases: new Map(), cashEntries: new Map(), suppliers: new Map(), saleItems: new Map(), purchaseItems: new Map() };
  const nopMappers = {
    obatFromMap: () => null,
    penjualanFromMap: () => null,
    pbfFromMap: () => null,
    kasFromMap: () => null,
    pemasokFromMap: () => null,
    itemJualFromMap: () => null,
    itemPbfFromMap: () => null,
  };

  const cleared = mergeSyncIntoState(base, emptyRemote, emptyPush, nopMappers, 6000, { replaceHandledAt: 5000 });
  assert.equal(cleared.nextDb.pendingCloudReplaceAt, 0);

  const newerRestore = { ...base, pendingCloudReplaceAt: 7000 };
  const kept = mergeSyncIntoState(newerRestore, emptyRemote, emptyPush, nopMappers, 8000, { replaceHandledAt: 5000 });
  assert.equal(kept.nextDb.pendingCloudReplaceAt, 7000);
});

test('computeDeltaSinceMs uses overlap window for auto-sync and falls back to full pull when needed', () => {
  const okBase = {
    lastSyncAt: 1_000_000,
    alreadyMigrated: true,
    pendingCloudReplaceAt: 0,
    remoteResetAt: 500,
    lastResetSeenAt: 500,
    forceFull: false,
  };
  assert.equal(computeDeltaSinceMs(okBase), 1_000_000 - DELTA_OVERLAP_MS);
  assert.equal(computeDeltaSinceMs({ ...okBase, lastSyncAt: 5000 }), 0);
  assert.equal(computeDeltaSinceMs({ ...okBase, lastSyncAt: 0 }), 0);
  assert.equal(computeDeltaSinceMs({ ...okBase, alreadyMigrated: false }), 0);
  assert.equal(computeDeltaSinceMs({ ...okBase, pendingCloudReplaceAt: 999 }), 0);
  assert.equal(computeDeltaSinceMs({ ...okBase, remoteResetAt: 600 }), 0);
  assert.equal(computeDeltaSinceMs({ ...okBase, forceFull: true }), 0);
});

test('nextProgressiveLimit clamps batches cleanly and compareNameID sorts numerically', () => {
  assert.equal(nextProgressiveLimit(0, 60, 0), 0);
  assert.equal(nextProgressiveLimit(60, 60, 1609), 120);
  assert.equal(nextProgressiveLimit(1580, 60, 1609), 1609);
  assert.equal(nextProgressiveLimit(-10, 50, 25), 25);

  const sorted = ['Obat 10', 'Obat 2', 'amoxicillin'].sort(compareNameID);
  assert.deepEqual(sorted, ['amoxicillin', 'Obat 2', 'Obat 10']);
});

test('filterAndRankMedicines matches multi-token queries, ranks prefixes, and prioritizes sellable items', () => {
  const mk = (id, name, stock, sellPrice, barcode = null) => ({
    id,
    name,
    category: 'Obat Bebas',
    medicineType: 'Analgesik',
    unit: 'Strip',
    buyPrice: 0,
    sellPrice,
    wholesalePrice: 0,
    stock,
    expiryDate: null,
    barcode,
    createdAt: '2026-11-01T00:00:00.000Z',
    remoteId: `m-${id}`,
    updatedAt: 1,
    terhapus: false,
    kotor: false,
  });

  const meds = [
    mk(1, 'DEXTRAL 500 PARACETAMOL', 20, 8000),
    mk(2, 'PARACETAMOL 500MG TAB', 0, 0),
    mk(3, 'PARACETAMOL 500MG KAP', 15, 4000),
    mk(4, 'AMOXICILLIN 500MG', 10, 6000, '8990001'),
  ];

  // Multi-token match ranks name prefix ahead of middle match, and sellable first in Kasir
  const kasirRes = filterAndRankMedicines(meds, 'para 500', { prioritizeSellable: true });
  assert.deepEqual(
    kasirRes.map((m) => m.name),
    ['PARACETAMOL 500MG KAP', 'PARACETAMOL 500MG TAB', 'DEXTRAL 500 PARACETAMOL']
  );

  // Empty query in Kasir surfaces ready-to-sell items ahead of unpriced/empty stock
  const kasirDefault = filterAndRankMedicines(meds, '', { prioritizeSellable: true });
  assert.equal(kasirDefault[kasirDefault.length - 1].name, 'PARACETAMOL 500MG TAB');

  // Exact barcode wins top rank
  const byBarcode = filterAndRankMedicines(meds, '8990001');
  assert.deepEqual(byBarcode.map((m) => m.name), ['AMOXICILLIN 500MG']);
});

test('computeAtomicStockUpdates prevents concurrent oversell and handles clock skew monotonically', () => {
  const med = {
    id: 1,
    name: 'Paracetamol 500mg',
    category: 'Obat Bebas',
    medicineType: 'Analgesik',
    unit: 'Strip',
    buyPrice: 2000,
    sellPrice: 4000,
    wholesalePrice: 3500,
    stock: 10,
    expiryDate: null,
    barcode: null,
    createdAt: '2026-11-01T00:00:00.000Z',
    remoteId: 'obat-1',
    updatedAt: 5000,
    terhapus: false,
    kotor: false,
  };
  const toMap = (m) => ({ ...m });

  // Cashier A sells 2 from server stock 10 -> 8
  const resA = computeAtomicStockUpdates(
    [{ medicine: med, qty: 2 }],
    [{ exists: true, data: { remoteId: 'obat-1', stock: 10, updatedAt: 5000, terhapus: false } }],
    6000,
    toMap
  );
  assert.equal(resA.ok, true);
  assert.equal(resA.updates[0].stock, 8);
  assert.equal(resA.updates[0].updatedAt, 6000);

  // Cashier B (stale local stock 10, slower clock 5500) retries against server stock 8 buying 3 -> 5, updatedAt 6001
  const resB = computeAtomicStockUpdates(
    [{ medicine: med, qty: 3 }],
    [{ exists: true, data: resA.updates[0].docData }],
    5500,
    toMap
  );
  assert.equal(resB.ok, true);
  assert.equal(resB.updates[0].stock, 5);
  assert.equal(resB.updates[0].updatedAt, 6001);

  // Cashier C tries buying 6 against server stock 5 -> rejected with exact remaining stock 5
  const resC = computeAtomicStockUpdates(
    [{ medicine: med, qty: 6 }],
    [{ exists: true, data: resB.updates[0].docData }],
    7000,
    toMap
  );
  assert.equal(resC.ok, false);
  assert.equal(resC.serverStock, 5);
  assert.equal(resC.remoteId, 'obat-1');
});

test('mergeMedicineDeltaRows applies newer remote stock and ignores self-echo or older docs', () => {
  const med = {
    id: 1,
    name: 'Paracetamol 500mg',
    category: 'Obat Bebas',
    medicineType: '',
    unit: 'Strip',
    buyPrice: 2000,
    sellPrice: 4000,
    wholesalePrice: 0,
    stock: 10,
    expiryDate: null,
    barcode: null,
    createdAt: '2026-11-01T00:00:00.000Z',
    remoteId: 'obat-1',
    updatedAt: 5000,
    terhapus: false,
    kotor: false,
  };
  const db = {
    medicines: [med],
    sales: [],
    saleItems: [],
    cashEntries: [],
    purchases: [],
    purchaseItems: [],
    suppliers: [],
    closedMonths: [],
    lastSyncAt: 5000,
  };
  const fromMap = (m, localId) => ({ ...med, ...m, id: localId, kotor: false });

  // Self-echo with same updatedAt returns exact same reference (no spurious re-render)
  const echo = mergeMedicineDeltaRows(db, [{ id: 'obat-1', data: { ...med, updatedAt: 5000 } }], fromMap);
  assert.equal(echo, db);

  // Newer remote stock deduction from another cashier updates immediately
  const updated = mergeMedicineDeltaRows(
    db,
    [{ id: 'obat-1', data: { ...med, stock: 7, updatedAt: 6000 } }],
    fromMap
  );
  assert.notEqual(updated, db);
  assert.equal(updated.medicines[0].stock, 7);
  assert.equal(updated.medicines[0].updatedAt, 6000);
});
