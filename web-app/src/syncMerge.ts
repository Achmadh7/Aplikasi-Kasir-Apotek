import type {
  BaseEntity,
  CashEntry,
  Medicine,
  Purchase,
  PurchaseItem,
  Sale,
  SaleItem,
  Supplier,
} from './types.ts';
import { bulanKeyDariIso, remoteMenang } from './types.ts';

export interface LocalDB {
  medicines: Medicine[];
  sales: Sale[];
  saleItems: SaleItem[];
  cashEntries: CashEntry[];
  purchases: Purchase[];
  purchaseItems: PurchaseItem[];
  suppliers: Supplier[];
  closedMonths: string[];
  lastSyncAt: number;
  pendingCloudReplaceAt?: number;
  lastResetSeenAt?: number;
}

export function applyCloudResetEpoch(prev: LocalDB, resetAt: number): LocalDB {
  if (!resetAt || resetAt <= (prev.lastResetSeenAt || 0)) return prev;
  const keepAfterReset = <T extends BaseEntity>(rows: T[]) =>
    rows.filter((r) => r.updatedAt > resetAt);
  const medicines = keepAfterReset(prev.medicines);
  const sales = keepAfterReset(prev.sales);
  const saleItems = keepAfterReset(prev.saleItems);
  const cashEntries = keepAfterReset(prev.cashEntries);
  const purchases = keepAfterReset(prev.purchases);
  const purchaseItems = keepAfterReset(prev.purchaseItems);
  const suppliers = keepAfterReset(prev.suppliers);
  const noTransactionsLeft =
    sales.length === 0 && purchases.length === 0 && cashEntries.length === 0;
  return {
    medicines,
    sales,
    saleItems,
    cashEntries,
    purchases,
    purchaseItems,
    suppliers,
    closedMonths: noTransactionsLeft ? [] : prev.closedMonths,
    lastSyncAt: prev.lastSyncAt,
    pendingCloudReplaceAt:
      (prev.pendingCloudReplaceAt || 0) > resetAt ? prev.pendingCloudReplaceAt : 0,
    lastResetSeenAt: resetAt,
  };
}

export interface RemoteDocSnap {
  id: string;
  data: Record<string, any>;
}

export interface RemoteCollections {
  obat: RemoteDocSnap[];
  penjualan: RemoteDocSnap[];
  pbf: RemoteDocSnap[];
  kas: RemoteDocSnap[];
  pemasok: RemoteDocSnap[];
  penjualan_item: RemoteDocSnap[];
  pbf_item: RemoteDocSnap[];
}

export interface PushedWatermarks {
  medicines: Map<string, number>;
  sales: Map<string, number>;
  purchases: Map<string, number>;
  cashEntries: Map<string, number>;
  suppliers: Map<string, number>;
  saleItems: Map<string, number>;
  purchaseItems: Map<string, number>;
}

export interface SyncEntityMappers {
  obatFromMap: (m: Record<string, any>, localId: number) => Medicine | null;
  penjualanFromMap: (m: Record<string, any>, localId: number) => Sale | null;
  pbfFromMap: (m: Record<string, any>, localId: number) => Purchase | null;
  kasFromMap: (m: Record<string, any>, localId: number) => CashEntry | null;
  pemasokFromMap: (m: Record<string, any>, localId: number) => Supplier | null;
  itemJualFromMap: (
    m: Record<string, any>,
    localId: number,
    saleId: number,
    medicineId: number
  ) => SaleItem | null;
  itemPbfFromMap: (
    m: Record<string, any>,
    localId: number,
    purchaseId: number
  ) => PurchaseItem | null;
}

/**
 * Clears `kotor` only if `updatedAt` still matches the snapshot pushed in this sync cycle.
 * Rows modified while `syncNow` awaited network I/O keep `kotor: true` and flag `stillDirty`.
 */
function clearPushedDirty<T extends BaseEntity>(
  rows: T[],
  pushed: Map<string, number>
): { rows: T[]; stillDirty: boolean } {
  let stillDirty = false;
  const nextRows = rows.map((row) => {
    if (!row.kotor) return row;
    const pushedAt = pushed.get(row.remoteId);
    if (pushedAt !== undefined && pushedAt === row.updatedAt) {
      return { ...row, kotor: false };
    }
    stillDirty = true;
    return row;
  });
  return { rows: nextRows, stillDirty };
}

function mergeRemoteParentTable<T extends BaseEntity>(
  baseRows: T[],
  remoteDocs: RemoteDocSnap[],
  mapFn: (data: Record<string, any>, localId: number) => T | null,
  isClosedIso: (iso?: string | null) => boolean,
  getMonthIso?: (row: T) => string | null | undefined
): {
  rows: T[];
  pulled: number;
  stillDirty: boolean;
  tombstoned: Map<number, number>;
} {
  const rows = [...baseRows];
  const idxByRemote = new Map<string, number>();
  let nextId = 1;
  rows.forEach((r, idx) => {
    if (r.id >= nextId) nextId = r.id + 1;
    if (r.remoteId) idxByRemote.set(r.remoteId, idx);
  });

  let pulled = 0;
  let stillDirty = false;
  const tombstoned = new Map<number, number>();

  for (const docSnap of remoteDocs) {
    const existingIdx = idxByRemote.get(docSnap.id);
    const existing = existingIdx !== undefined ? rows[existingIdx] : undefined;
    const mapped = mapFn(docSnap.data, existing?.id ?? nextId);
    if (!mapped) continue;

    if (getMonthIso) {
      if (isClosedIso(getMonthIso(mapped))) continue;
      if (existing && isClosedIso(getMonthIso(existing))) continue;
    }

    if (!existing) {
      if (!mapped.terhapus) {
        nextId++;
        idxByRemote.set(mapped.remoteId, rows.length);
        rows.push({ ...mapped, kotor: false });
        pulled++;
      }
    } else if (
      remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
    ) {
      rows[existingIdx!] = { ...mapped, id: existing.id, kotor: false };
      pulled++;
      if (mapped.terhapus) {
        tombstoned.set(existing.id, mapped.updatedAt);
      }
    } else if (mapped.updatedAt < existing.updatedAt && !existing.kotor) {
      rows[existingIdx!] = { ...existing, kotor: true };
      stillDirty = true;
    }
  }

  return { rows, pulled, stillDirty, tombstoned };
}

function cascadeTombstones<T extends BaseEntity>(
  items: T[],
  parentTombstones: Map<number, number>,
  getParentId: (item: T) => number
): { items: T[]; anyCascaded: boolean } {
  if (parentTombstones.size === 0) return { items, anyCascaded: false };
  let anyCascaded = false;
  const next = items.map((it) => {
    const deletedAt = parentTombstones.get(getParentId(it));
    if (deletedAt === undefined || it.terhapus) return it;
    anyCascaded = true;
    return { ...it, terhapus: true, updatedAt: Math.max(it.updatedAt, deletedAt), kotor: true };
  });
  return { items: next, anyCascaded };
}

function mergeRemoteChildTable<T extends BaseEntity>(
  baseItems: T[],
  remoteDocs: RemoteDocSnap[],
  resolveAndMap: (data: Record<string, any>, fallbackId: number) => T | null
): { items: T[]; pulled: number; stillDirty: boolean } {
  const items = [...baseItems];
  const idxByRemote = new Map<string, number>();
  let nextId = 1;
  items.forEach((it, idx) => {
    if (it.id >= nextId) nextId = it.id + 1;
    if (it.remoteId) idxByRemote.set(it.remoteId, idx);
  });

  let pulled = 0;
  let stillDirty = false;

  for (const docSnap of remoteDocs) {
    const existingIdx = idxByRemote.get(docSnap.id);
    const existing = existingIdx !== undefined ? items[existingIdx] : undefined;
    const mapped = resolveAndMap(docSnap.data, existing?.id ?? nextId);
    if (!mapped) continue;

    if (!existing) {
      if (!mapped.terhapus) {
        nextId++;
        idxByRemote.set(mapped.remoteId, items.length);
        items.push({ ...mapped, kotor: false });
        pulled++;
      }
    } else if (
      remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
    ) {
      items[existingIdx!] = { ...mapped, id: existing.id, kotor: false };
      pulled++;
    } else if (mapped.updatedAt < existing.updatedAt && !existing.kotor) {
      items[existingIdx!] = { ...existing, kotor: true };
      stillDirty = true;
    }
  }

  return { items, pulled, stillDirty };
}

/**
 * Pure LWW merge applied inside React functional state update (`setDbState(prev => ...)`),
 * preserving concurrent local edits and enforcing `closedMonths` locks.
 */
export function mergeSyncIntoState(
  prev: LocalDB,
  remote: RemoteCollections,
  pushed: PushedWatermarks,
  mappers: SyncEntityMappers,
  nowMs: number = Date.now(),
  options?: { replaceHandledAt?: number; remoteResetAt?: number }
): { nextDb: LocalDB; pulled: number; stillDirty: boolean } {
  const basePrev =
    options?.remoteResetAt && options.remoteResetAt > (prev.lastResetSeenAt || 0)
      ? applyCloudResetEpoch(prev, options.remoteResetAt)
      : prev;
  const closedSet = new Set(basePrev.closedMonths);
  const isClosedIso = (iso?: string | null): boolean => {
    const b = bulanKeyDariIso(iso);
    return Boolean(b && closedSet.has(b));
  };

  const cMed = clearPushedDirty(basePrev.medicines, pushed.medicines);
  const cSale = clearPushedDirty(basePrev.sales, pushed.sales);
  const cPbf = clearPushedDirty(basePrev.purchases, pushed.purchases);
  const cKas = clearPushedDirty(basePrev.cashEntries, pushed.cashEntries);
  const cSup = clearPushedDirty(basePrev.suppliers, pushed.suppliers);
  const cSaleItem = clearPushedDirty(basePrev.saleItems, pushed.saleItems);
  const cPbfItem = clearPushedDirty(basePrev.purchaseItems, pushed.purchaseItems);

  const mMed = mergeRemoteParentTable(cMed.rows, remote.obat, mappers.obatFromMap, isClosedIso);
  const mSale = mergeRemoteParentTable(
    cSale.rows,
    remote.penjualan,
    mappers.penjualanFromMap,
    isClosedIso,
    (s) => s.createdAt
  );
  const mPbf = mergeRemoteParentTable(
    cPbf.rows,
    remote.pbf,
    mappers.pbfFromMap,
    isClosedIso,
    (p) => p.date
  );
  const mKas = mergeRemoteParentTable(
    cKas.rows,
    remote.kas,
    mappers.kasFromMap,
    isClosedIso,
    (k) => k.createdAt
  );
  const mSup = mergeRemoteParentTable(
    cSup.rows,
    remote.pemasok,
    mappers.pemasokFromMap,
    isClosedIso
  );

  const medsByRemote = new Map(mMed.rows.map((m) => [m.remoteId, m]));
  const medsByKey = new Map(
    mMed.rows
      .filter((m) => !m.terhapus)
      .map((m) => [
        `${m.name.trim().toLowerCase()}|${m.category.trim().toLowerCase()}|${m.unit.trim().toLowerCase()}`,
        m,
      ])
  );
  const salesByRemote = new Map(mSale.rows.map((s) => [s.remoteId, s]));
  const pbfByRemote = new Map(mPbf.rows.map((p) => [p.remoteId, p]));

  const cascadedSaleItems = cascadeTombstones(cSaleItem.rows, mSale.tombstoned, (it) => it.saleId);
  const cascadedPbfItems = cascadeTombstones(cPbfItem.rows, mPbf.tombstoned, (it) => it.purchaseId);

  const mSaleItems = mergeRemoteChildTable(cascadedSaleItems.items, remote.penjualan_item, (d, id) => {
    const parentSale = salesByRemote.get(String(d.saleRemoteId || ''));
    if (!parentSale || isClosedIso(parentSale.createdAt)) return null;
    const med =
      medsByRemote.get(String(d.medicineRemoteId || '')) ??
      medsByKey.get(
        `${String(d.medicineName || '').trim().toLowerCase()}|${String(d.category || '').trim().toLowerCase()}|${String(d.unit || '').trim().toLowerCase()}`
      );
    return mappers.itemJualFromMap(d, id, parentSale.id, med?.id ?? 0);
  });

  const mPbfItems = mergeRemoteChildTable(cascadedPbfItems.items, remote.pbf_item, (d, id) => {
    const parentPbf = pbfByRemote.get(String(d.purchaseRemoteId || ''));
    if (!parentPbf || isClosedIso(parentPbf.date)) return null;
    return mappers.itemPbfFromMap(d, id, parentPbf.id);
  });

  const pulled =
    mMed.pulled +
    mSale.pulled +
    mPbf.pulled +
    mKas.pulled +
    mSup.pulled +
    mSaleItems.pulled +
    mPbfItems.pulled;

  const stillDirty =
    cMed.stillDirty ||
    cSale.stillDirty ||
    cPbf.stillDirty ||
    cKas.stillDirty ||
    cSup.stillDirty ||
    cSaleItem.stillDirty ||
    cPbfItem.stillDirty ||
    mMed.stillDirty ||
    mSale.stillDirty ||
    mPbf.stillDirty ||
    mKas.stillDirty ||
    mSup.stillDirty ||
    mSaleItems.stillDirty ||
    mPbfItems.stillDirty ||
    cascadedSaleItems.anyCascaded ||
    cascadedPbfItems.anyCascaded;

  return {
    nextDb: {
      medicines: mMed.rows,
      sales: mSale.rows,
      saleItems: mSaleItems.items,
      cashEntries: mKas.rows,
      purchases: mPbf.rows,
      purchaseItems: mPbfItems.items,
      suppliers: mSup.rows,
      closedMonths: basePrev.closedMonths,
      lastSyncAt: nowMs,
      pendingCloudReplaceAt:
        options?.replaceHandledAt && basePrev.pendingCloudReplaceAt === options.replaceHandledAt
          ? 0
          : basePrev.pendingCloudReplaceAt || 0,
      lastResetSeenAt: Math.max(
        basePrev.lastResetSeenAt || 0,
        options?.remoteResetAt || 0
      ),
    },
    pulled,
    stillDirty,
  };
}
