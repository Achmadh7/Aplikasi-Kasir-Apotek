import type {
  CashEntry,
  Medicine,
  Purchase,
  PurchaseItem,
  Sale,
  SaleItem,
  Supplier,
} from './types.ts';
import { bulanValid } from './types.ts';

export interface ParsedBackupPayload {
  medicines: Medicine[];
  sales: Sale[];
  saleItems: SaleItem[];
  cashEntries: CashEntry[];
  purchases: Purchase[];
  purchaseItems: PurchaseItem[];
  suppliers: Supplier[];
  closedMonths: string[];
}

function asObject(val: unknown, label: string): Record<string, any> {
  if (!val || typeof val !== 'object' || Array.isArray(val)) {
    throw new Error(`${label} rusak: bukan objek JSON`);
  }
  return val as Record<string, any>;
}

function reqInt(o: Record<string, any>, key: string, label: string): number {
  const v = o[key];
  if (v === undefined || v === null || typeof v !== 'number' || !Number.isInteger(v)) {
    throw new Error(`${label} rusak: field "${key}" hilang atau bukan bilangan bulat`);
  }
  if (v < 0 || v > 2147483647) {
    throw new Error(`${label} rusak: field "${key}" di luar batas (${v})`);
  }
  return v;
}

function optInt(o: Record<string, any>, key: string, label: string, fallback = 0): number {
  const v = o[key];
  if (v === undefined || v === null) return fallback;
  if (typeof v !== 'number' || !Number.isInteger(v)) {
    throw new Error(`${label} rusak: field "${key}" bukan bilangan bulat`);
  }
  if (v < 0 || v > 2147483647) {
    throw new Error(`${label} rusak: field "${key}" di luar batas (${v})`);
  }
  return v;
}

function reqString(o: Record<string, any>, key: string, label: string): string {
  const v = o[key];
  if (typeof v !== 'string') {
    throw new Error(`${label} rusak: field "${key}" harus berupa teks`);
  }
  return v;
}

function checkUniqueIds(rows: Array<{ id: number }>, tableLabel: string) {
  const seen = new Set<number>();
  for (const r of rows) {
    if (seen.has(r.id)) {
      throw new Error(`ID ${tableLabel} duplikat di file backup: ${r.id}`);
    }
    seen.add(r.id);
  }
}

/**
 * Strict backup JSON parser & cross-table validator matching BackupHelper.kt.
 * Bumps `updatedAt` to at least `nowMs` so restored rows overwrite stale cloud entries.
 */
export function parseAndValidateBackupJson(
  jsonStr: string,
  genRemoteIdFn: (prefix: string) => string,
  nowMs: number = Date.now()
): ParsedBackupPayload {
  let root: any;
  try {
    root = JSON.parse(jsonStr);
  } catch {
    throw new Error('File bukan backup Apotek Lestari yang sah (bukan JSON)');
  }
  if (!root || typeof root !== 'object' || root.version !== 1 || root.app !== 'apotek-lestari') {
    throw new Error('File bukan backup Apotek Lestari yang sah (versi 1)');
  }
  for (const reqKey of ['medicines', 'sales', 'saleItems', 'cashEntries'] as const) {
    if (!Array.isArray(root[reqKey])) {
      throw new Error(`Backup tidak lengkap: array "${reqKey}" tidak ditemukan`);
    }
  }

  const medicines: Medicine[] = root.medicines.map((raw: unknown, i: number) => {
    const label = `Baris obat ke-${i + 1}`;
    const o = asObject(raw, label);
    return {
      id: reqInt(o, 'id', label),
      name: reqString(o, 'name', label),
      category: String(o.category ?? 'Umum'),
      unit: String(o.unit ?? 'Strip'),
      buyPrice: optInt(o, 'buyPrice', label, 0),
      sellPrice: reqInt(o, 'sellPrice', label),
      wholesalePrice: optInt(o, 'wholesalePrice', label, 0),
      stock: reqInt(o, 'stock', label),
      expiryDate: o.expiryDate ? String(o.expiryDate) : null,
      barcode: o.barcode ? String(o.barcode) : null,
      createdAt: String(o.createdAt ?? ''),
      remoteId: String(o.remoteId || genRemoteIdFn('obat')),
      updatedAt: Math.max(Number(o.updatedAt) || 0, nowMs),
      terhapus: false,
      kotor: true,
    };
  });

  const sales: Sale[] = root.sales.map((raw: unknown, i: number) => {
    const label = `Baris penjualan ke-${i + 1}`;
    const o = asObject(raw, label);
    return {
      id: reqInt(o, 'id', label),
      invoiceNo: reqString(o, 'invoiceNo', label),
      total: reqInt(o, 'total', label),
      paymentMethod: o.paymentMethod === 'QRIS' || o.paymentMethod === 'Transfer' ? o.paymentMethod : 'Tunai',
      amountPaid: reqInt(o, 'amountPaid', label),
      changeAmount: reqInt(o, 'changeAmount', label),
      createdAt: String(o.createdAt ?? ''),
      remoteId: String(o.remoteId || genRemoteIdFn('jual')),
      updatedAt: Math.max(Number(o.updatedAt) || 0, nowMs),
      terhapus: false,
      kotor: true,
    };
  });

  const saleRemoteById = new Map(sales.map((s) => [s.id, s.remoteId]));
  const medRemoteById = new Map(medicines.map((m) => [m.id, m.remoteId]));

  const saleItems: SaleItem[] = root.saleItems.map((raw: unknown, i: number) => {
    const label = `Baris item penjualan ke-${i + 1}`;
    const o = asObject(raw, label);
    const qty = reqInt(o, 'qty', label);
    const price = reqInt(o, 'price', label);
    const subtotal = reqInt(o, 'subtotal', label);
    if (subtotal !== qty * price) {
      throw new Error(`${label} rusak: subtotal ${subtotal} tidak sama dengan ${qty} x ${price}`);
    }
    const saleId = reqInt(o, 'saleId', label);
    const saleRemoteId = saleRemoteById.get(saleId);
    if (!saleRemoteId) {
      throw new Error(`${label} rusak: saleId ${saleId} tidak ditemukan di daftar sales`);
    }
    const medicineId = reqInt(o, 'medicineId', label);
    return {
      id: reqInt(o, 'id', label),
      saleId,
      saleRemoteId,
      medicineId,
      medicineRemoteId: medRemoteById.get(medicineId) || '',
      medicineName: String(o.medicineName ?? ''),
      category: String(o.category ?? ''),
      unit: String(o.unit ?? ''),
      qty,
      price,
      priceType: o.priceType === 'GROSIR' ? 'GROSIR' : 'ECER',
      buyPrice: optInt(o, 'buyPrice', label, 0),
      subtotal,
      remoteId: String(o.remoteId || genRemoteIdFn('jualitem')),
      updatedAt: Math.max(Number(o.updatedAt) || 0, nowMs),
      terhapus: false,
      kotor: true,
    };
  });

  const itemsBySale = new Map<number, number>();
  for (const it of saleItems) {
    itemsBySale.set(it.saleId, (itemsBySale.get(it.saleId) || 0) + it.subtotal);
  }
  sales.forEach((s, i) => {
    const label = `Baris penjualan ke-${i + 1}`;
    const sumSub = itemsBySale.get(s.id) || 0;
    if (s.total !== sumSub) {
      throw new Error(`${label} rusak: total ${s.total} tidak sama dengan jumlah subtotal item ${sumSub}`);
    }
    if (s.amountPaid < s.total) {
      throw new Error(`${label} rusak: amountPaid ${s.amountPaid} kurang dari total ${s.total}`);
    }
    if (s.changeAmount !== s.amountPaid - s.total) {
      throw new Error(`${label} rusak: changeAmount ${s.changeAmount} tidak sama dengan ${s.amountPaid} - ${s.total}`);
    }
  });

  const cashEntries: CashEntry[] = root.cashEntries.map((raw: unknown, i: number) => {
    const label = `Baris kas ke-${i + 1}`;
    const o = asObject(raw, label);
    if (o.type !== 'in' && o.type !== 'out') {
      throw new Error(`${label} rusak: type harus "in" atau "out"`);
    }
    return {
      id: reqInt(o, 'id', label),
      type: o.type,
      category: String(o.category ?? ''),
      description: String(o.description ?? ''),
      amount: reqInt(o, 'amount', label),
      source: o.source === 'sale' ? 'sale' : 'manual',
      createdAt: String(o.createdAt ?? ''),
      remoteId: String(o.remoteId || genRemoteIdFn('kas')),
      updatedAt: Math.max(Number(o.updatedAt) || 0, nowMs),
      terhapus: false,
      kotor: true,
    };
  });

  if (root.purchases !== undefined && !Array.isArray(root.purchases)) {
    throw new Error('Backup rusak: "purchases" harus berupa array');
  }
  const purchases: Purchase[] = (root.purchases || []).map((raw: unknown, i: number) => {
    const label = `Baris pembelian ke-${i + 1}`;
    const o = asObject(raw, label);
    return {
      id: reqInt(o, 'id', label),
      pbfName: reqString(o, 'pbfName', label),
      invoiceNo: String(o.invoiceNo ?? ''),
      date: String(o.date ?? ''),
      total: reqInt(o, 'total', label),
      ppn: optInt(o, 'ppn', label, 0),
      paymentType: o.paymentType === 'KREDIT' ? 'KREDIT' : 'CASH',
      dueDate: String(o.dueDate ?? ''),
      createdAt: String(o.createdAt ?? ''),
      remoteId: String(o.remoteId || genRemoteIdFn('pbf')),
      updatedAt: Math.max(Number(o.updatedAt) || 0, nowMs),
      terhapus: false,
      kotor: true,
    };
  });

  const pbfRemoteById = new Map(purchases.map((p) => [p.id, p.remoteId]));
  if (root.purchaseItems !== undefined && !Array.isArray(root.purchaseItems)) {
    throw new Error('Backup rusak: "purchaseItems" harus berupa array');
  }
  const purchaseItems: PurchaseItem[] = (root.purchaseItems || []).map((raw: unknown, i: number) => {
    const label = `Baris item pembelian ke-${i + 1}`;
    const o = asObject(raw, label);
    const qty = reqInt(o, 'qty', label);
    const buyPrice = reqInt(o, 'buyPrice', label);
    const subtotal = reqInt(o, 'subtotal', label);
    if (subtotal !== qty * buyPrice) {
      throw new Error(`${label} rusak: subtotal ${subtotal} tidak sama dengan ${qty} x ${buyPrice}`);
    }
    const purchaseId = reqInt(o, 'purchaseId', label);
    const purchaseRemoteId = pbfRemoteById.get(purchaseId);
    if (!purchaseRemoteId) {
      throw new Error(`${label} rusak: purchaseId ${purchaseId} tidak ditemukan di daftar purchases`);
    }
    return {
      id: reqInt(o, 'id', label),
      purchaseId,
      purchaseRemoteId,
      medicineName: String(o.medicineName ?? ''),
      qty,
      unit: String(o.unit ?? 'Strip'),
      buyPrice,
      subtotal,
      remoteId: String(o.remoteId || genRemoteIdFn('pbfitem')),
      updatedAt: Math.max(Number(o.updatedAt) || 0, nowMs),
      terhapus: false,
      kotor: true,
    };
  });

  if (root.suppliers !== undefined && !Array.isArray(root.suppliers)) {
    throw new Error('Backup rusak: "suppliers" harus berupa array');
  }
  const suppliers: Supplier[] = (root.suppliers || []).map((raw: unknown, i: number) => {
    const label = `Baris pemasok ke-${i + 1}`;
    const o = asObject(raw, label);
    return {
      id: reqInt(o, 'id', label),
      name: reqString(o, 'name', label),
      contact: String(o.contact ?? ''),
      createdAt: String(o.createdAt ?? ''),
      remoteId: String(o.remoteId || genRemoteIdFn('pemasok')),
      updatedAt: Math.max(Number(o.updatedAt) || 0, nowMs),
      terhapus: false,
      kotor: true,
    };
  });

  checkUniqueIds(medicines, 'obat');
  checkUniqueIds(sales, 'penjualan');
  checkUniqueIds(saleItems, 'item penjualan');
  checkUniqueIds(cashEntries, 'kas');
  checkUniqueIds(purchases, 'pembelian');
  checkUniqueIds(purchaseItems, 'item pembelian');
  checkUniqueIds(suppliers, 'pemasok');

  const seenBarcodes = new Set<string>();
  for (const m of medicines) {
    const bc = m.barcode?.trim() || '';
    if (bc) {
      if (seenBarcodes.has(bc.toLowerCase())) {
        throw new Error(`Barcode duplikat di file backup: ${bc} (${m.name})`);
      }
      seenBarcodes.add(bc.toLowerCase());
    }
  }

  const seenRemoteIds = new Set<string>();
  for (const list of [medicines, sales, saleItems, cashEntries, purchases, purchaseItems, suppliers]) {
    for (const row of list) {
      if (seenRemoteIds.has(row.remoteId)) {
        throw new Error(`remoteId duplikat di file backup: ${row.remoteId}`);
      }
      seenRemoteIds.add(row.remoteId);
    }
  }

  const closedMonths = Array.isArray(root.closedMonths)
    ? root.closedMonths.filter((b: unknown): b is string => typeof b === 'string' && bulanValid(b))
    : [];

  return {
    medicines,
    sales,
    saleItems,
    cashEntries,
    purchases,
    purchaseItems,
    suppliers,
    closedMonths,
  };
}
