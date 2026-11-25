import assert from 'node:assert/strict';
import test from 'node:test';
import { parseAndValidateBackupJson } from '../src/backup.ts';
import {
  DELTA_OVERLAP_MS,
  applyCloudResetEpoch,
  computeDeltaSinceMs,
  mergeSyncIntoState,
} from '../src/syncMerge.ts';

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
