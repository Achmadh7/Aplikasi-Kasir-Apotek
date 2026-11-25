import React, { createContext, useContext, useEffect, useState, useRef } from 'react';
import {
  Medicine,
  Sale,
  SaleItem,
  CashEntry,
  Purchase,
  PurchaseItem,
  Supplier,
  genRemoteId,
  nowIso,
  invoiceDateWIB,
  bulanKeyDariIso,
  bulanValid,
  bulanSelesai,
} from './types';
import { LocalDB, PushedWatermarks, applyCloudResetEpoch, mergeSyncIntoState } from './syncMerge';
import { parseAndValidateBackupJson } from './backup';
import {
  auth,
  onAuthStateChanged,
  logoutFirebase,
  User,
  FirestoreMapper,
  fetchCollectionDocs,
  fetchCloudSyncMeta,
  pushBatchDocs,
  tombstoneCloudDocs,
  migrateLegacyUserWebData,
  resetAllCloudData,
  DEFAULT_AUTHORITY_EMAILS,
  AuthorityRole,
  fetchAuthorityConfig,
  saveAuthorityConfig,
  subscribeAuthorityConfig,
  isEmailAuthorized,
  normalizeEmail,
} from './firebase';
import { idbGet, idbSet } from './idb';

export type SyncStatus = 'NONAKTIF' | 'BELUM_MASUK' | 'PROSES' | 'BERHASIL' | 'GAGAL';

const STORAGE_KEY = 'apotek_lestari_web_db_v1';

const emptyDB = (): LocalDB => ({
  medicines: [],
  sales: [],
  saleItems: [],
  cashEntries: [],
  purchases: [],
  purchaseItems: [],
  suppliers: [],
  closedMonths: [],
  lastSyncAt: 0,
  pendingCloudReplaceAt: 0,
  lastResetSeenAt: 0,
});

function normalizeDB(parsed: any): LocalDB {
  if (!parsed || typeof parsed !== 'object') return emptyDB();
  return {
    medicines: Array.isArray(parsed.medicines) ? parsed.medicines : [],
    sales: Array.isArray(parsed.sales) ? parsed.sales : [],
    saleItems: Array.isArray(parsed.saleItems) ? parsed.saleItems : [],
    cashEntries: Array.isArray(parsed.cashEntries) ? parsed.cashEntries : [],
    purchases: Array.isArray(parsed.purchases) ? parsed.purchases : [],
    purchaseItems: Array.isArray(parsed.purchaseItems) ? parsed.purchaseItems : [],
    suppliers: Array.isArray(parsed.suppliers) ? parsed.suppliers : [],
    closedMonths: Array.isArray(parsed.closedMonths) ? parsed.closedMonths : [],
    lastSyncAt: Number(parsed.lastSyncAt) || 0,
    pendingCloudReplaceAt: Number(parsed.pendingCloudReplaceAt) || 0,
    lastResetSeenAt: Number(parsed.lastResetSeenAt) || 0,
  };
}

function loadLocalDB(): LocalDB {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return emptyDB();
    return normalizeDB(JSON.parse(raw));
  } catch {
    return emptyDB();
  }
}

interface StoreContextType {
  // Active (non-deleted) rows
  medicines: Medicine[];
  sales: Sale[];
  saleItems: SaleItem[];
  cashEntries: CashEntry[];
  purchases: Purchase[];
  purchaseItems: PurchaseItem[];
  suppliers: Supplier[];
  closedMonths: string[];
  // Auth & Sync
  user: User | null;
  authLoading: boolean;
  authError: string | null;
  clearAuthError: () => void;
  authorityEmails: string[];
  ownerEmails: string[];
  currentRole: AuthorityRole;
  addAuthorityEmail: (email: string, role?: AuthorityRole) => Promise<void>;
  setAuthorityRole: (email: string, role: AuthorityRole) => Promise<void>;
  removeAuthorityEmail: (email: string) => Promise<void>;
  syncStatus: SyncStatus;
  lastSyncAt: number;
  syncMessage: string | null;
  autoSync: boolean;
  setAutoSync: (enabled: boolean) => void;
  syncNow: (silent?: boolean) => Promise<void>;
  // Medicine actions
  saveMedicine: (
    med: Omit<Medicine, 'id' | 'remoteId' | 'updatedAt' | 'terhapus' | 'createdAt' | 'kotor'>,
    editId?: number
  ) => void;
  deleteMedicine: (id: number) => void;
  bulkImportMedicines: (
    rows: Array<{
      name: string;
      category: string;
      unit: string;
      buyPrice: number;
      sellPrice: number;
      wholesalePrice: number;
      stock: number;
      expiryDate: string | null;
      barcode: string | null;
    }>
  ) => { inserted: number; updated: number; skipped: number };
  // POS Sale actions
  createSale: (
    cart: Array<{ medicineId: number; qty: number; useWholesale: boolean }>,
    paymentMethod: 'Tunai' | 'QRIS' | 'Transfer',
    amountPaid: number
  ) => { sale: Sale; items: SaleItem[] };
  // Cash actions
  createCashEntry: (
    type: 'in' | 'out',
    category: string,
    description: string,
    amount: number,
    createdAt?: string
  ) => void;
  deleteCashEntry: (id: number) => void;
  // PBF & Supplier actions
  createPurchase: (args: {
    pbfName: string;
    invoiceNo: string;
    date: string;
    ppn: number;
    paymentType: 'CASH' | 'KREDIT';
    dueDate: string;
    items: Array<{ medicineName: string; qty: number; unit: string; buyPrice: number }>;
  }) => void;
  markPurchasePaid: (id: number) => void;
  setPurchaseDueDate: (id: number, dueDate: string) => void;
  deletePurchase: (id: number) => void;
  saveSupplier: (name: string, contact: string, editId?: number) => void;
  deleteSupplier: (id: number) => void;
  // Tutup Buku
  tutupBulan: (bulan: string) => void;
  bukaBulan: (bulan: string) => void;
  bulanTerkunci: (bulan: string | null) => boolean;
  // Backup & Reset
  exportBackupJson: () => string;
  importBackupJson: (jsonStr: string) => string;
  resetAllData: () => Promise<void>;
}

const StoreContext = createContext<StoreContextType | null>(null);

export const StoreProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [dbState, setDbState] = useState<LocalDB>(() => loadLocalDB());
  const [idbReady, setIdbReady] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authorityEmails, setAuthorityEmails] = useState<string[]>(DEFAULT_AUTHORITY_EMAILS);
  const [ownerEmails, setOwnerEmails] = useState<string[]>(DEFAULT_AUTHORITY_EMAILS);
  const [syncStatus, setSyncStatus] = useState<SyncStatus>('BELUM_MASUK');
  const [syncMessage, setSyncMessage] = useState<string | null>(null);
  const [autoSync, setAutoSyncState] = useState<boolean>(() => {
    try {
      const saved = localStorage.getItem('apotek_lestari_auto_sync');
      return saved === null ? true : saved === 'true';
    } catch {
      return true;
    }
  });
  const dbRef = useRef(dbState);
  dbRef.current = dbState;
  const syncingRef = useRef(false);
  const pendingSyncRef = useRef(false);
  const autoSyncTimerRef = useRef<number | null>(null);
  const resyncTimerRef = useRef<number | null>(null);
  const idbSaveTimerRef = useRef<number | null>(null);
  const dirtyIdbRef = useRef(false);
  const autoSyncRef = useRef(autoSync);
  autoSyncRef.current = autoSync;

  const setAutoSync = (enabled: boolean) => {
    setAutoSyncState(enabled);
    autoSyncRef.current = enabled;
    try {
      localStorage.setItem('apotek_lestari_auto_sync', String(enabled));
    } catch {
      // ignore storage quota/privacy mode errors
    }
    if (enabled && auth.currentUser) {
      void syncNow(true);
    }
  };

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const saved = await idbGet<LocalDB>(STORAGE_KEY);
        if (saved && active) {
          const normalized = normalizeDB(saved);
          dbRef.current = normalized;
          setDbState(normalized);
        } else if (localStorage.getItem(STORAGE_KEY)) {
          // One-time migration from localStorage to IndexedDB
          await idbSet(STORAGE_KEY, dbRef.current);
          localStorage.removeItem(STORAGE_KEY);
        }
      } catch (e) {
        console.error('Failed loading IndexedDB', e);
      } finally {
        if (active) setIdbReady(true);
      }
    })();
    return () => {
      active = false;
      if (autoSyncTimerRef.current) window.clearTimeout(autoSyncTimerRef.current);
      if (resyncTimerRef.current) window.clearTimeout(resyncTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (!idbReady) return;
    dirtyIdbRef.current = true;
    if (idbSaveTimerRef.current) window.clearTimeout(idbSaveTimerRef.current);
    idbSaveTimerRef.current = window.setTimeout(() => {
      idbSaveTimerRef.current = null;
      dirtyIdbRef.current = false;
      idbSet(STORAGE_KEY, dbRef.current).catch((e) => {
        console.error('Failed saving IndexedDB', e);
      });
    }, 200);
  }, [dbState, idbReady]);

  useEffect(() => {
    const flushIdb = () => {
      if (!dirtyIdbRef.current) return;
      dirtyIdbRef.current = false;
      if (idbSaveTimerRef.current) {
        window.clearTimeout(idbSaveTimerRef.current);
        idbSaveTimerRef.current = null;
      }
      void idbSet(STORAGE_KEY, dbRef.current).catch(() => {});
    };
    window.addEventListener('pagehide', flushIdb);
    window.addEventListener('beforeunload', flushIdb);
    return () => {
      window.removeEventListener('pagehide', flushIdb);
      window.removeEventListener('beforeunload', flushIdb);
      if (idbSaveTimerRef.current) window.clearTimeout(idbSaveTimerRef.current);
    };
  }, []);

  useEffect(() => {
    let active = true;
    const unsub = onAuthStateChanged(auth, async (u) => {
      if (!active) return;
      if (!u) {
        setUser(null);
        setAuthLoading(false);
        setSyncStatus('BELUM_MASUK');
        return;
      }
      setAuthLoading(true);
      const cfg = await fetchAuthorityConfig(true);
      if (!active) return;
      if (!isEmailAuthorized(u.email, cfg.emails)) {
        await logoutFirebase().catch(() => {});
        if (!active) return;
        setUser(null);
        setAuthLoading(false);
        setSyncStatus('BELUM_MASUK');
        setAuthError(
          `Akses ditolak: email ${u.email || '(tanpa email)'} tidak terdaftar di kolom Authority.`
        );
        return;
      }
      setAuthError(null);
      setAuthorityEmails(cfg.emails);
      setOwnerEmails(cfg.ownerEmails);
      setUser(u);
      setAuthLoading(false);
      // Trigger initial sync upon sign-in
      setTimeout(() => syncNow(false, u), 200);
    });
    return () => {
      active = false;
      unsub();
    };
  }, []);

  // Auto-logout immediately when the signed-in email is removed from Authority
  useEffect(() => {
    if (!user) return;
    let active = true;
    const unsub = subscribeAuthorityConfig((cfg) => {
      if (!active) return;
      setAuthorityEmails(cfg.emails);
      setOwnerEmails(cfg.ownerEmails);
      if (!isEmailAuthorized(user.email, cfg.emails)) {
        setAuthError(
          `Akses dicabut: email ${user.email || '(tanpa email)'} telah dihapus dari kolom Authority.`
        );
        void logoutFirebase().catch(() => {});
      }
    });
    return () => {
      active = false;
      unsub();
    };
  }, [user]);

  const currentRole: AuthorityRole =
    user && ownerEmails.includes(normalizeEmail(user.email)) ? 'owner' : 'kasir';

  const addAuthorityEmail = async (email: string, role: AuthorityRole = 'kasir') => {
    const clean = normalizeEmail(email);
    if (!clean || !clean.includes('@')) {
      throw new Error('Format email tidak valid.');
    }
    if (authorityEmails.includes(clean)) {
      throw new Error('Email sudah terdaftar di kolom Authority.');
    }
    const nextOwners = role === 'owner' ? [...ownerEmails, clean] : ownerEmails;
    const saved = await saveAuthorityConfig([...authorityEmails, clean], nextOwners);
    setAuthorityEmails(saved.emails);
    setOwnerEmails(saved.ownerEmails);
  };

  const setAuthorityRole = async (email: string, role: AuthorityRole) => {
    const clean = normalizeEmail(email);
    if (!authorityEmails.includes(clean)) {
      throw new Error('Email tidak ditemukan di kolom Authority.');
    }
    const nextOwners =
      role === 'owner'
        ? [...ownerEmails, clean]
        : ownerEmails.filter((e) => e !== clean);
    if (nextOwners.length === 0) {
      throw new Error('Minimal harus tersisa 1 akun dengan role Owner.');
    }
    const saved = await saveAuthorityConfig(authorityEmails, nextOwners);
    setAuthorityEmails(saved.emails);
    setOwnerEmails(saved.ownerEmails);
  };

  const removeAuthorityEmail = async (email: string) => {
    const clean = normalizeEmail(email);
    const nextEmails = authorityEmails.filter((e) => e !== clean);
    if (nextEmails.length === 0) {
      throw new Error('Minimal harus tersisa 1 email di kolom Authority.');
    }
    const nextOwners = ownerEmails.filter((e) => e !== clean);
    if (nextOwners.length === 0) {
      throw new Error('Minimal harus tersisa 1 akun dengan role Owner.');
    }
    const saved = await saveAuthorityConfig(nextEmails, nextOwners);
    setAuthorityEmails(saved.emails);
    setOwnerEmails(saved.ownerEmails);
    if (user && normalizeEmail(user.email) === clean) {
      await logoutFirebase();
    }
  };

  const bulanTerkunci = (bulan: string | null): boolean => {
    if (!bulan) return false;
    return dbRef.current.closedMonths.includes(bulan);
  };

  // Two-way Last-Write-Wins Sync matching OnlineSync.kt
  const syncNow = async (silent = false, overrideUser?: User | null) => {
    const activeUser = overrideUser !== undefined ? overrideUser : user;
    if (!activeUser) return;
    if (syncingRef.current) {
      pendingSyncRef.current = true;
      return;
    }
    syncingRef.current = true;
    pendingSyncRef.current = false;
    setSyncStatus('PROSES');
    if (!silent) setSyncMessage(null);

    try {
      const uid = activeUser.uid;
      const rawSnap = dbRef.current;
      let pushed = 0;

      // Fetch remote collections + sync reset meta from shared apotek/main
      const [
        remObat,
        remJual,
        remPbf,
        remKas,
        remPemasok,
        remItemJual,
        remItemPbf,
        cloudMeta,
      ] = await Promise.all([
        fetchCollectionDocs('obat'),
        fetchCollectionDocs('penjualan'),
        fetchCollectionDocs('pbf'),
        fetchCollectionDocs('kas'),
        fetchCollectionDocs('pemasok'),
        fetchCollectionDocs('penjualan_item'),
        fetchCollectionDocs('pbf_item'),
        fetchCloudSyncMeta(),
      ]);

      const remoteResetAt = cloudMeta.resetAt || 0;
      const snap = applyCloudResetEpoch(rawSnap, remoteResetAt);

      // One-time migration of legacy per-account users/{uid}/web_* data into shared apotek/main/web_*
      const legacyFlagKey = `apotek_lestari_migrated_shared_${uid}`;
      let alreadyMigrated = false;
      try {
        alreadyMigrated = localStorage.getItem(legacyFlagKey) === '1';
      } catch {
        alreadyMigrated = false;
      }
      if (!alreadyMigrated) {
        const migratedCount = await migrateLegacyUserWebData(uid, {
          obat: remObat,
          penjualan: remJual,
          pbf: remPbf,
          kas: remKas,
          pemasok: remPemasok,
          penjualan_item: remItemJual,
          pbf_item: remItemPbf,
        });
        pushed += migratedCount;
        try {
          localStorage.setItem(legacyFlagKey, '1');
        } catch {
          // ignore storage errors
        }
      }

      const remoteUpdatedMap = (docs: Array<{ id: string; data: Record<string, any> }>) => {
        const m = new Map<string, number>();
        docs.forEach((d) => m.set(d.id, Number(d.data.updatedAt) || 0));
        return m;
      };

      const pushedWatermarks: PushedWatermarks = {
        medicines: new Map(),
        sales: new Map(),
        purchases: new Map(),
        cashEntries: new Map(),
        suppliers: new Map(),
        saleItems: new Map(),
        purchaseItems: new Map(),
      };

      const pushTable = async <T extends { remoteId: string; updatedAt: number; kotor?: boolean }>(
        rows: T[],
        colName: string,
        remoteUpdated: Map<string, number>,
        watermark: Map<string, number>,
        toMap: (row: T) => Record<string, any>,
        canPush: (row: T) => boolean = () => true
      ) => {
        const toPush: Array<{ remoteId: string; data: Record<string, any> }> = [];
        const marked: Array<[string, number]> = [];
        for (const e of rows) {
          if (!e.kotor || !e.remoteId) continue;
          if (!canPush(e)) continue;
          if ((remoteUpdated.get(e.remoteId) || 0) <= e.updatedAt) {
            toPush.push({ remoteId: e.remoteId, data: toMap(e) });
          }
          marked.push([e.remoteId, e.updatedAt]);
        }
        if (toPush.length > 0) {
          await pushBatchDocs(colName, toPush);
          pushed += toPush.length;
        }
        for (const [rid, uAt] of marked) {
          watermark.set(rid, uAt);
        }
      };

      const closedSet = new Set(snap.closedMonths);
      const isClosedMonth = (iso?: string | null) => {
        const b = bulanKeyDariIso(iso);
        return Boolean(b && closedSet.has(b));
      };
      const saleMonthByRemote = new Map(snap.sales.map((s) => [s.remoteId, s.createdAt]));
      const pbfMonthByRemote = new Map(snap.purchases.map((p) => [p.remoteId, p.date]));

      // 1) Push dirty local rows from snapshot (parents before children; skip closed months)
      await pushTable(snap.medicines, 'obat', remoteUpdatedMap(remObat), pushedWatermarks.medicines, FirestoreMapper.obatToMap);
      await pushTable(snap.sales, 'penjualan', remoteUpdatedMap(remJual), pushedWatermarks.sales, FirestoreMapper.penjualanToMap, (e) => !isClosedMonth(e.createdAt));
      await pushTable(snap.purchases, 'pbf', remoteUpdatedMap(remPbf), pushedWatermarks.purchases, FirestoreMapper.pbfToMap, (e) => !isClosedMonth(e.date));
      await pushTable(snap.cashEntries, 'kas', remoteUpdatedMap(remKas), pushedWatermarks.cashEntries, FirestoreMapper.kasToMap, (e) => !isClosedMonth(e.createdAt));
      await pushTable(snap.suppliers, 'pemasok', remoteUpdatedMap(remPemasok), pushedWatermarks.suppliers, FirestoreMapper.pemasokToMap);
      await pushTable(
        snap.saleItems,
        'penjualan_item',
        remoteUpdatedMap(remItemJual),
        pushedWatermarks.saleItems,
        FirestoreMapper.itemJualToMap,
        (e) => Boolean(e.saleRemoteId) && !isClosedMonth(saleMonthByRemote.get(e.saleRemoteId))
      );
      await pushTable(
        snap.purchaseItems,
        'pbf_item',
        remoteUpdatedMap(remItemPbf),
        pushedWatermarks.purchaseItems,
        FirestoreMapper.itemPbfToMap,
        (e) => Boolean(e.purchaseRemoteId) && !isClosedMonth(pbfMonthByRemote.get(e.purchaseRemoteId))
      );

      // 1b) After a local backup restore, tombstone older remote docs missing from backup
      const replaceAt = snap.pendingCloudReplaceAt || 0;
      if (replaceAt > 0) {
        const replacePairs: Array<[string, Array<{ id: string; data: Record<string, any> }>, Array<{ remoteId: string; terhapus: boolean }>]> = [
          ['obat', remObat, snap.medicines],
          ['penjualan', remJual, snap.sales],
          ['pbf', remPbf, snap.purchases],
          ['kas', remKas, snap.cashEntries],
          ['pemasok', remPemasok, snap.suppliers],
          ['penjualan_item', remItemJual, snap.saleItems],
          ['pbf_item', remItemPbf, snap.purchaseItems],
        ];
        for (const [colName, remDocs, localRows] of replacePairs) {
          const keepSet = new Set(localRows.filter((x) => !x.terhapus && x.remoteId).map((x) => x.remoteId));
          const staleDocs = remDocs.filter(
            (d) => !keepSet.has(d.id) && d.data?.terhapus !== true && (Number(d.data?.updatedAt) || 0) <= replaceAt
          );
          if (staleDocs.length > 0) {
            await tombstoneCloudDocs(colName, staleDocs, replaceAt);
            pushed += staleDocs.length;
            for (const d of staleDocs) {
              d.data = { ...d.data, terhapus: true, updatedAt: replaceAt };
            }
          }
        }
      }

      // 2) Pull & merge atomically against live React state without clobbering mid-sync mutations
      const remoteBundle = {
        obat: remObat,
        penjualan: remJual,
        pbf: remPbf,
        kas: remKas,
        pemasok: remPemasok,
        penjualan_item: remItemJual,
        pbf_item: remItemPbf,
      };
      const nowMs = Date.now();
      const mergeOpts = { replaceHandledAt: replaceAt, remoteResetAt };
      const preview = mergeSyncIntoState(
        dbRef.current,
        remoteBundle,
        pushedWatermarks,
        FirestoreMapper,
        nowMs,
        mergeOpts
      );
      setDbState((prev) =>
        mergeSyncIntoState(prev, remoteBundle, pushedWatermarks, FirestoreMapper, nowMs, mergeOpts).nextDb
      );
      if (preview.stillDirty) pendingSyncRef.current = true;
      setSyncStatus('BERHASIL');
      if (!silent) {
        setSyncMessage(`Tersinkron (↑${pushed} ↓${preview.pulled})`);
      }
    } catch (e: any) {
      console.error('Sync error:', e);
      setSyncStatus('GAGAL');
      setSyncMessage(e?.message || 'Sinkronisasi gagal');
    } finally {
      syncingRef.current = false;
      if (pendingSyncRef.current && autoSyncRef.current && auth.currentUser) {
        pendingSyncRef.current = false;
        if (resyncTimerRef.current) window.clearTimeout(resyncTimerRef.current);
        resyncTimerRef.current = window.setTimeout(() => {
          resyncTimerRef.current = null;
          void syncNow(true);
        }, 250);
      }
    }
  };

  // Auto-push helper right after local mutation
  const queueAutoSync = () => {
    if (!auth.currentUser || !autoSyncRef.current) return;
    if (syncingRef.current) {
      pendingSyncRef.current = true;
      return;
    }
    if (autoSyncTimerRef.current) window.clearTimeout(autoSyncTimerRef.current);
    autoSyncTimerRef.current = window.setTimeout(() => {
      autoSyncTimerRef.current = null;
      void syncNow(true);
    }, 1200);
  };

  // ---- Medicine Mutations ----
  const saveMedicine: StoreContextType['saveMedicine'] = (med, editId) => {
    if (!med.name.trim()) throw new Error('Nama obat tidak boleh kosong');
    if (med.sellPrice <= 0) throw new Error('Harga jual harus lebih dari 0');
    if (med.stock < 0 || med.buyPrice < 0 || med.wholesalePrice < 0)
      throw new Error('Stok / Harga tidak boleh negatif');

    const bc = med.barcode?.trim() || null;
    if (bc) {
      const dup = dbRef.current.medicines.find(
        (m) => !m.terhapus && m.id !== editId && m.barcode?.toLowerCase() === bc.toLowerCase()
      );
      if (dup) throw new Error(`Barcode ${bc} sudah dipakai untuk ${dup.name}`);
    }
    const now = Date.now();

    setDbState((prev) => {
      if (editId !== undefined) {
        return {
          ...prev,
          medicines: prev.medicines.map((m) =>
            m.id === editId
              ? {
                  ...m,
                  ...med,
                  name: med.name.trim(),
                  barcode: bc,
                  updatedAt: now,
                  kotor: true,
                }
              : m
          ),
        };
      } else {
        const nextId = prev.medicines.reduce((max, x) => Math.max(max, x.id), 0) + 1;
        const newMed: Medicine = {
          id: nextId,
          remoteId: genRemoteId('obat'),
          updatedAt: now,
          terhapus: false,
          kotor: true,
          name: med.name.trim(),
          category: med.category || 'Umum',
          unit: med.unit || 'Strip',
          buyPrice: med.buyPrice || 0,
          sellPrice: med.sellPrice,
          wholesalePrice: med.wholesalePrice || 0,
          stock: med.stock || 0,
          expiryDate: med.expiryDate || null,
          barcode: bc,
          createdAt: nowIso(),
        };
        return { ...prev, medicines: [...prev.medicines, newMed] };
      }
    });
    queueAutoSync();
  };

  const deleteMedicine = (id: number) => {
    const now = Date.now();
    setDbState((prev) => ({
      ...prev,
      medicines: prev.medicines.map((m) =>
        m.id === id ? { ...m, terhapus: true, updatedAt: now, kotor: true } : m
      ),
    }));
    queueAutoSync();
  };

  const bulkImportMedicines: StoreContextType['bulkImportMedicines'] = (rows) => {
    let inserted = 0;
    let updated = 0;
    let skipped = 0;
    const now = Date.now();
    const curMeds = [...dbRef.current.medicines];
    let nextId = curMeds.reduce((m, x) => Math.max(m, x.id), 0) + 1;

    for (const item of rows) {
      if (!item.name?.trim() || item.sellPrice <= 0 || item.stock < 0 || item.buyPrice < 0) {
        skipped++;
        continue;
      }
      const bc = item.barcode?.trim() || null;
      const existingIdx = bc
        ? curMeds.findIndex((m) => m.barcode?.toLowerCase() === bc.toLowerCase())
        : curMeds.findIndex(
            (m) =>
              m.name.toLowerCase() === item.name.trim().toLowerCase() &&
              m.category.toLowerCase() === (item.category || 'Umum').toLowerCase() &&
              m.unit.toLowerCase() === (item.unit || 'Strip').toLowerCase()
          );

      if (existingIdx >= 0) {
        const existing = curMeds[existingIdx];
        curMeds[existingIdx] = {
          ...existing,
          name: item.name.trim(),
          category: item.category || existing.category,
          unit: item.unit || existing.unit,
          sellPrice: item.sellPrice,
          buyPrice: item.buyPrice > 0 ? item.buyPrice : existing.buyPrice,
          wholesalePrice:
            item.wholesalePrice > 0 ? item.wholesalePrice : existing.wholesalePrice,
          stock: item.stock,
          expiryDate: item.expiryDate || existing.expiryDate,
          barcode: bc || existing.barcode,
          terhapus: false,
          updatedAt: now,
          kotor: true,
        };
        updated++;
      } else {
        curMeds.push({
          id: nextId++,
          remoteId: genRemoteId('obat'),
          updatedAt: now,
          terhapus: false,
          kotor: true,
          name: item.name.trim(),
          category: item.category || 'Umum',
          unit: item.unit || 'Strip',
          buyPrice: item.buyPrice || 0,
          sellPrice: item.sellPrice,
          wholesalePrice: item.wholesalePrice || 0,
          stock: item.stock || 0,
          expiryDate: item.expiryDate || null,
          barcode: bc,
          createdAt: nowIso(),
        });
        inserted++;
      }
    }

    setDbState((prev) => ({ ...prev, medicines: curMeds }));
    queueAutoSync();
    return { inserted, updated, skipped };
  };

  // ---- POS Sale Mutation (Repository.createSale) ----
  const createSale: StoreContextType['createSale'] = (cart, paymentMethod, amountPaid) => {
    if (cart.length === 0) throw new Error('Keranjang kosong');
    const cur = dbRef.current;
    const nowMs = Date.now();
    const createdAt = nowIso();
    const bKey = bulanKeyDariIso(createdAt);
    if (bKey && cur.closedMonths.includes(bKey)) {
      throw new Error(`Bulan ${bKey} sudah ditutup, penjualan tidak bisa ditambah`);
    }

    // Aggregate qty per medicineId to verify stock
    const qtyByMed = new Map<number, number>();
    for (const c of cart) {
      if (c.qty <= 0) throw new Error('Jumlah obat harus lebih dari 0');
      qtyByMed.set(c.medicineId, (qtyByMed.get(c.medicineId) || 0) + c.qty);
    }

    const medMap = new Map(cur.medicines.filter((m) => !m.terhapus).map((m) => [m.id, m]));
    for (const [mId, totalQty] of qtyByMed.entries()) {
      const m = medMap.get(mId);
      if (!m) throw new Error('Obat tidak ditemukan');
      if (m.stock < totalQty) throw new Error(`Stok ${m.name} tidak cukup (sisa ${m.stock})`);
    }

    let total = 0;
    const saleId = cur.sales.reduce((max, x) => Math.max(max, x.id), 0) + 1;
    const saleRemoteId = genRemoteId('jual');
    let nextItemId = cur.saleItems.reduce((max, x) => Math.max(max, x.id), 0) + 1;

    const newItems: SaleItem[] = cart.map((c) => {
      const med = medMap.get(c.medicineId)!;
      const wholesale = c.useWholesale && med.wholesalePrice > 0;
      const unitPrice = wholesale ? med.wholesalePrice : med.sellPrice;
      const subtotal = unitPrice * c.qty;
      total += subtotal;
      return {
        id: nextItemId++,
        saleId,
        saleRemoteId,
        medicineId: med.id,
        medicineRemoteId: med.remoteId,
        remoteId: genRemoteId('jualitem'),
        updatedAt: nowMs,
        terhapus: false,
        kotor: true,
        medicineName: med.name,
        category: med.category,
        unit: med.unit,
        qty: c.qty,
        price: unitPrice,
        priceType: wholesale ? 'GROSIR' : 'ECER',
        buyPrice: med.buyPrice,
        subtotal,
      };
    });

    if (amountPaid < total) throw new Error('Uang dibayar kurang dari total');
    const changeAmount = amountPaid - total;
    const invoiceNo = invoiceDateWIB(new Date());

    const newSale: Sale = {
      id: saleId,
      remoteId: saleRemoteId,
      updatedAt: nowMs,
      terhapus: false,
      kotor: true,
      invoiceNo,
      total,
      paymentMethod,
      amountPaid,
      changeAmount,
      createdAt,
    };

    const nextCashId = cur.cashEntries.reduce((max, x) => Math.max(max, x.id), 0) + 1;
    const newCash: CashEntry = {
      id: nextCashId,
      remoteId: genRemoteId('kas'),
      updatedAt: nowMs,
      terhapus: false,
      kotor: true,
      type: 'in',
      category: 'Penjualan',
      description: `Penjualan ${invoiceNo}`,
      amount: total,
      source: 'sale',
      createdAt,
    };

    setDbState((prev) => ({
      ...prev,
      medicines: prev.medicines.map((m) => {
        const deduct = qtyByMed.get(m.id);
        return deduct
          ? { ...m, stock: m.stock - deduct, updatedAt: nowMs, kotor: true }
          : m;
      }),
      sales: [newSale, ...prev.sales],
      saleItems: [...prev.saleItems, ...newItems],
      cashEntries: [newCash, ...prev.cashEntries],
    }));

    queueAutoSync();
    return { sale: newSale, items: newItems };
  };

  // ---- Cash Mutations ----
  const createCashEntry: StoreContextType['createCashEntry'] = (
    type,
    category,
    description,
    amount,
    createdAt
  ) => {
    if (amount <= 0) throw new Error('Jumlah kas harus lebih dari 0');
    const iso = createdAt || nowIso();
    const bKey = bulanKeyDariIso(iso);
    if (bKey && bulanTerkunci(bKey)) {
      throw new Error(`Bulan ${bKey} sudah ditutup, entri kas tidak bisa ditambah`);
    }
    const now = Date.now();
    setDbState((prev) => {
      const nextId = prev.cashEntries.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      const entry: CashEntry = {
        id: nextId,
        remoteId: genRemoteId('kas'),
        updatedAt: now,
        terhapus: false,
        kotor: true,
        type,
        category: category.trim() || (type === 'in' ? 'Kas Masuk' : 'Operasional'),
        description: description.trim(),
        amount,
        source: 'manual',
        createdAt: iso,
      };
      return { ...prev, cashEntries: [entry, ...prev.cashEntries] };
    });
    queueAutoSync();
  };

  const deleteCashEntry = (id: number) => {
    const entry = dbRef.current.cashEntries.find((c) => c.id === id);
    if (!entry) return;
    if (entry.source === 'sale') throw new Error('Kas otomatis dari penjualan tidak bisa dihapus');
    const bKey = bulanKeyDariIso(entry.createdAt);
    if (bKey && bulanTerkunci(bKey)) {
      throw new Error(`Bulan ${bKey} sudah ditutup, entri kas tidak bisa dihapus`);
    }
    const now = Date.now();
    setDbState((prev) => ({
      ...prev,
      cashEntries: prev.cashEntries.map((c) =>
        c.id === id ? { ...c, terhapus: true, updatedAt: now, kotor: true } : c
      ),
    }));
    queueAutoSync();
  };

  // ---- PBF Purchases & Suppliers ----
  const createPurchase: StoreContextType['createPurchase'] = (args) => {
    if (!args.pbfName.trim()) throw new Error('Nama PBF tidak boleh kosong');
    if (args.items.length === 0) throw new Error('Isi minimal satu obat yang dibeli');
    const bKey = bulanKeyDariIso(args.date);
    if (bKey && bulanTerkunci(bKey)) {
      throw new Error(`Bulan ${bKey} sudah ditutup, faktur PBF tidak bisa ditambah`);
    }
    const now = Date.now();
    const cur = dbRef.current;
    const purchaseId = cur.purchases.reduce((m, x) => Math.max(m, x.id), 0) + 1;
    const purchaseRemoteId = genRemoteId('pbf');
    let nextItemId = cur.purchaseItems.reduce((m, x) => Math.max(m, x.id), 0) + 1;

    const pItems: PurchaseItem[] = args.items.map((it) => {
      if (!it.medicineName.trim() || it.qty <= 0 || it.buyPrice <= 0) {
        throw new Error('Nama obat, jumlah, dan harga beli wajib diisi dengan benar');
      }
      return {
        id: nextItemId++,
        purchaseId,
        purchaseRemoteId,
        remoteId: genRemoteId('pbfitem'),
        updatedAt: now,
        terhapus: false,
        kotor: true,
        medicineName: it.medicineName.trim(),
        qty: it.qty,
        unit: it.unit || 'Strip',
        buyPrice: it.buyPrice,
        subtotal: it.qty * it.buyPrice,
      };
    });

    const total = pItems.reduce((acc, x) => acc + x.subtotal, 0) + (args.ppn || 0);
    const newPurchase: Purchase = {
      id: purchaseId,
      remoteId: purchaseRemoteId,
      updatedAt: now,
      terhapus: false,
      kotor: true,
      pbfName: args.pbfName.trim(),
      invoiceNo: args.invoiceNo.trim(),
      date: args.date,
      total,
      ppn: args.ppn || 0,
      paymentType: args.paymentType,
      dueDate: args.paymentType === 'KREDIT' ? args.dueDate.trim() : '',
      createdAt: nowIso(),
    };

    setDbState((prev) => ({
      ...prev,
      purchases: [newPurchase, ...prev.purchases],
      purchaseItems: [...prev.purchaseItems, ...pItems],
    }));
    queueAutoSync();
  };

  const markPurchasePaid = (id: number) => {
    const p = dbRef.current.purchases.find((x) => x.id === id);
    if (!p) return;
    const bKey = bulanKeyDariIso(p.date);
    if (bKey && bulanTerkunci(bKey)) {
      throw new Error(`Bulan ${bKey} sudah ditutup, status faktur tidak bisa diubah`);
    }
    const now = Date.now();
    setDbState((prev) => ({
      ...prev,
      purchases: prev.purchases.map((x) =>
        x.id === id ? { ...x, paymentType: 'CASH', updatedAt: now, kotor: true } : x
      ),
    }));
    queueAutoSync();
  };

  const setPurchaseDueDate = (id: number, dueDate: string) => {
    const p = dbRef.current.purchases.find((x) => x.id === id);
    if (!p) return;
    const bKey = bulanKeyDariIso(p.date);
    if (bKey && bulanTerkunci(bKey)) {
      throw new Error(`Bulan ${bKey} sudah ditutup, jatuh tempo tidak bisa diubah`);
    }
    const now = Date.now();
    setDbState((prev) => ({
      ...prev,
      purchases: prev.purchases.map((x) =>
        x.id === id ? { ...x, dueDate: dueDate.trim(), updatedAt: now, kotor: true } : x
      ),
    }));
    queueAutoSync();
  };

  const deletePurchase = (id: number) => {
    const p = dbRef.current.purchases.find((x) => x.id === id);
    if (!p) return;
    const bKey = bulanKeyDariIso(p.date);
    if (bKey && bulanTerkunci(bKey)) {
      throw new Error(`Bulan ${bKey} sudah ditutup, faktur PBF tidak bisa dihapus`);
    }
    const now = Date.now();
    setDbState((prev) => ({
      ...prev,
      purchases: prev.purchases.map((x) =>
        x.id === id ? { ...x, terhapus: true, updatedAt: now, kotor: true } : x
      ),
      purchaseItems: prev.purchaseItems.map((it) =>
        it.purchaseId === id ? { ...it, terhapus: true, updatedAt: now, kotor: true } : it
      ),
    }));
    queueAutoSync();
  };

  const saveSupplier = (name: string, contact: string, editId?: number) => {
    const clean = name.trim();
    if (!clean) throw new Error('Nama supplier tidak boleh kosong');
    const dup = dbRef.current.suppliers.find(
      (s) => !s.terhapus && s.id !== editId && s.name.toLowerCase() === clean.toLowerCase()
    );
    if (dup) throw new Error(`Supplier "${clean}" sudah ada di daftar`);
    const now = Date.now();

    setDbState((prev) => {
      if (editId !== undefined) {
        return {
          ...prev,
          suppliers: prev.suppliers.map((s) =>
            s.id === editId
              ? { ...s, name: clean, contact: contact.trim(), updatedAt: now, kotor: true }
              : s
          ),
        };
      }
      const nextId = prev.suppliers.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      return {
        ...prev,
        suppliers: [
          ...prev.suppliers,
          {
            id: nextId,
            remoteId: genRemoteId('pemasok'),
            updatedAt: now,
            terhapus: false,
            kotor: true,
            name: clean,
            contact: contact.trim(),
            createdAt: nowIso(),
          },
        ],
      };
    });
    queueAutoSync();
  };

  const deleteSupplier = (id: number) => {
    const now = Date.now();
    setDbState((prev) => ({
      ...prev,
      suppliers: prev.suppliers.map((s) =>
        s.id === id ? { ...s, terhapus: true, updatedAt: now, kotor: true } : s
      ),
    }));
    queueAutoSync();
  };

  // ---- Tutup Buku ----
  const tutupBulan = (bulan: string) => {
    if (!bulanValid(bulan)) throw new Error(`Format bulan tidak valid: ${bulan}`);
    if (!bulanSelesai(bulan)) throw new Error(`Bulan ${bulan} belum selesai`);
    if (bulanTerkunci(bulan)) throw new Error(`Bulan ${bulan} sudah ditutup`);
    setDbState((prev) => ({
      ...prev,
      closedMonths: [bulan, ...prev.closedMonths].sort().reverse(),
    }));
  };

  const bukaBulan = (bulan: string) => {
    setDbState((prev) => ({
      ...prev,
      closedMonths: prev.closedMonths.filter((b) => b !== bulan),
    }));
  };

  // ---- Backup & Restore (100% compatible with BackupHelper.kt) ----
  const exportBackupJson = (): string => {
    const cur = dbRef.current;
    const payload = {
      version: 1,
      app: 'apotek-lestari',
      exportedAtMillis: Date.now(),
      medicines: cur.medicines.filter((x) => !x.terhapus),
      sales: cur.sales.filter((x) => !x.terhapus),
      saleItems: cur.saleItems.filter((x) => !x.terhapus),
      cashEntries: cur.cashEntries.filter((x) => !x.terhapus),
      purchases: cur.purchases.filter((x) => !x.terhapus),
      purchaseItems: cur.purchaseItems.filter((x) => !x.terhapus),
      suppliers: cur.suppliers.filter((x) => !x.terhapus),
      closedMonths: cur.closedMonths,
    };
    return JSON.stringify(payload, null, 2);
  };

  const importBackupJson = (jsonStr: string): string => {
    const nowMs = Date.now();
    const parsed = parseAndValidateBackupJson(jsonStr, genRemoteId, nowMs);
    const cur = dbRef.current;

    const withOrphanTombstones = <
      T extends { remoteId: string; updatedAt: number; terhapus: boolean; kotor?: boolean }
    >(
      oldRows: T[],
      nextRows: T[]
    ): T[] => {
      const nextRemote = new Set(nextRows.map((x) => x.remoteId));
      const orphans = oldRows
        .filter((r) => Boolean(r.remoteId) && !nextRemote.has(r.remoteId))
        .map((r) => ({ ...r, terhapus: true, updatedAt: nowMs, kotor: true }));
      return [...nextRows, ...orphans];
    };

    const unionClosed = Array.from(
      new Set([...cur.closedMonths, ...parsed.closedMonths])
    )
      .sort()
      .reverse();

    setDbState({
      medicines: withOrphanTombstones(cur.medicines, parsed.medicines),
      sales: withOrphanTombstones(cur.sales, parsed.sales),
      saleItems: withOrphanTombstones(cur.saleItems, parsed.saleItems),
      cashEntries: withOrphanTombstones(cur.cashEntries, parsed.cashEntries),
      purchases: withOrphanTombstones(cur.purchases, parsed.purchases),
      purchaseItems: withOrphanTombstones(cur.purchaseItems, parsed.purchaseItems),
      suppliers: withOrphanTombstones(cur.suppliers, parsed.suppliers),
      closedMonths: unionClosed,
      lastSyncAt: cur.lastSyncAt,
      pendingCloudReplaceAt: nowMs,
      lastResetSeenAt: cur.lastResetSeenAt || 0,
    });

    queueAutoSync();
    return `Dipulihkan: ${parsed.medicines.length} obat, ${parsed.sales.length} penjualan, ${parsed.cashEntries.length} kas, ${parsed.purchases.length} faktur PBF.`;
  };

  const resetAllData = async () => {
    const resetAt = Date.now();
    if (user) {
      if (currentRole !== 'owner') {
        throw new Error('Hanya akun Owner yang dapat mereset seluruh data cloud.');
      }
      await resetAllCloudData(resetAt);
    }
    setDbState({
      ...emptyDB(),
      lastResetSeenAt: resetAt,
    });
  };

  return (
    <StoreContext.Provider
      value={{
        medicines: dbState.medicines.filter((x) => !x.terhapus),
        sales: dbState.sales.filter((x) => !x.terhapus),
        saleItems: dbState.saleItems.filter((x) => !x.terhapus),
        cashEntries: dbState.cashEntries.filter((x) => !x.terhapus),
        purchases: dbState.purchases.filter((x) => !x.terhapus),
        purchaseItems: dbState.purchaseItems.filter((x) => !x.terhapus),
        suppliers: dbState.suppliers.filter((x) => !x.terhapus),
        closedMonths: dbState.closedMonths,
        user,
        authLoading,
        authError,
        clearAuthError: () => setAuthError(null),
        authorityEmails,
        ownerEmails,
        currentRole,
        addAuthorityEmail,
        setAuthorityRole,
        removeAuthorityEmail,
        syncStatus,
        lastSyncAt: dbState.lastSyncAt,
        syncMessage,
        autoSync,
        setAutoSync,
        syncNow,
        saveMedicine,
        deleteMedicine,
        bulkImportMedicines,
        createSale,
        createCashEntry,
        deleteCashEntry,
        createPurchase,
        markPurchasePaid,
        setPurchaseDueDate,
        deletePurchase,
        saveSupplier,
        deleteSupplier,
        tutupBulan,
        bukaBulan,
        bulanTerkunci,
        exportBackupJson,
        importBackupJson,
        resetAllData,
      }}
    >
      {children}
    </StoreContext.Provider>
  );
};

export function useStore() {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error('useStore must be used inside StoreProvider');
  return ctx;
}
