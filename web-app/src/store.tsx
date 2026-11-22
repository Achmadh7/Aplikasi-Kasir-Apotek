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
  remoteMenang,
  bulanKeyDariIso,
  bulanValid,
  bulanSelesai,
} from './types';
import {
  auth,
  onAuthStateChanged,
  logoutFirebase,
  User,
  FirestoreMapper,
  fetchCollectionDocs,
  pushSingleDoc,
  resetAllCloudData,
  DEFAULT_AUTHORITY_EMAILS,
  fetchAuthorityEmails,
  saveAuthorityEmails,
  subscribeAuthorityEmails,
  isEmailAuthorized,
  normalizeEmail,
} from './firebase';
import { idbGet, idbSet } from './idb';

export type SyncStatus = 'NONAKTIF' | 'BELUM_MASUK' | 'PROSES' | 'BERHASIL' | 'GAGAL';

interface LocalDB {
  medicines: Medicine[];
  sales: Sale[];
  saleItems: SaleItem[];
  cashEntries: CashEntry[];
  purchases: Purchase[];
  purchaseItems: PurchaseItem[];
  suppliers: Supplier[];
  closedMonths: string[];
  lastSyncAt: number;
}

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
  addAuthorityEmail: (email: string) => Promise<void>;
  removeAuthorityEmail: (email: string) => Promise<void>;
  syncStatus: SyncStatus;
  lastSyncAt: number;
  syncMessage: string | null;
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
  const [syncStatus, setSyncStatus] = useState<SyncStatus>('BELUM_MASUK');
  const [syncMessage, setSyncMessage] = useState<string | null>(null);
  const dbRef = useRef(dbState);
  dbRef.current = dbState;
  const syncingRef = useRef(false);

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
    };
  }, []);

  useEffect(() => {
    if (!idbReady) return;
    idbSet(STORAGE_KEY, dbState).catch((e) => {
      console.error('Failed saving IndexedDB', e);
    });
  }, [dbState, idbReady]);

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
      const allowed = await fetchAuthorityEmails(true);
      if (!active) return;
      if (!isEmailAuthorized(u.email, allowed)) {
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
      setAuthorityEmails(allowed);
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
    const unsub = subscribeAuthorityEmails((allowed) => {
      if (!active) return;
      setAuthorityEmails(allowed);
      if (!isEmailAuthorized(user.email, allowed)) {
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

  const addAuthorityEmail = async (email: string) => {
    const clean = normalizeEmail(email);
    if (!clean || !clean.includes('@')) {
      throw new Error('Format email tidak valid.');
    }
    if (authorityEmails.includes(clean)) {
      throw new Error('Email sudah terdaftar di kolom Authority.');
    }
    const updated = await saveAuthorityEmails([...authorityEmails, clean]);
    setAuthorityEmails(updated);
  };

  const removeAuthorityEmail = async (email: string) => {
    const clean = normalizeEmail(email);
    const next = authorityEmails.filter((e) => e !== clean);
    if (next.length === 0) {
      throw new Error('Minimal harus tersisa 1 email di kolom Authority.');
    }
    const updated = await saveAuthorityEmails(next);
    setAuthorityEmails(updated);
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
    if (!activeUser || syncingRef.current) return;
    syncingRef.current = true;
    setSyncStatus('PROSES');
    if (!silent) setSyncMessage(null);

    try {
      const uid = activeUser.uid;
      let cur = { ...dbRef.current };
      let pushed = 0;
      let pulled = 0;

      // Fetch remote collections once (like cacheAwan in OnlineSync.kt)
      const [
        remObat,
        remJual,
        remPbf,
        remKas,
        remPemasok,
        remItemJual,
        remItemPbf,
      ] = await Promise.all([
        fetchCollectionDocs(uid, 'obat'),
        fetchCollectionDocs(uid, 'penjualan'),
        fetchCollectionDocs(uid, 'pbf'),
        fetchCollectionDocs(uid, 'kas'),
        fetchCollectionDocs(uid, 'pemasok'),
        fetchCollectionDocs(uid, 'penjualan_item'),
        fetchCollectionDocs(uid, 'pbf_item'),
      ]);

      const remoteUpdatedMap = (docs: Array<{ id: string; data: Record<string, any> }>) => {
        const m = new Map<string, number>();
        docs.forEach((d) => m.set(d.id, Number(d.data.updatedAt) || 0));
        return m;
      };

      const mapObat = remoteUpdatedMap(remObat);
      const mapJual = remoteUpdatedMap(remJual);
      const mapPbf = remoteUpdatedMap(remPbf);
      const mapKas = remoteUpdatedMap(remKas);
      const mapPemasok = remoteUpdatedMap(remPemasok);
      const mapItemJual = remoteUpdatedMap(remItemJual);
      const mapItemPbf = remoteUpdatedMap(remItemPbf);

      // 1) Push dirty local rows where local.updatedAt >= remote.updatedAt
      for (const e of cur.medicines.filter((x) => x.kotor)) {
        if (!e.remoteId) continue;
        if ((mapObat.get(e.remoteId) || 0) <= e.updatedAt) {
          await pushSingleDoc(uid, 'obat', e.remoteId, FirestoreMapper.obatToMap(e));
          pushed++;
        }
        e.kotor = false;
      }

      for (const e of cur.sales.filter((x) => x.kotor)) {
        if (!e.remoteId) continue;
        const b = bulanKeyDariIso(e.createdAt);
        if (b && cur.closedMonths.includes(b)) continue;
        if ((mapJual.get(e.remoteId) || 0) <= e.updatedAt) {
          await pushSingleDoc(uid, 'penjualan', e.remoteId, FirestoreMapper.penjualanToMap(e));
          pushed++;
        }
        e.kotor = false;
      }

      for (const e of cur.purchases.filter((x) => x.kotor)) {
        if (!e.remoteId) continue;
        const b = bulanKeyDariIso(e.date);
        if (b && cur.closedMonths.includes(b)) continue;
        if ((mapPbf.get(e.remoteId) || 0) <= e.updatedAt) {
          await pushSingleDoc(uid, 'pbf', e.remoteId, FirestoreMapper.pbfToMap(e));
          pushed++;
        }
        e.kotor = false;
      }

      for (const e of cur.cashEntries.filter((x) => x.kotor)) {
        if (!e.remoteId) continue;
        const b = bulanKeyDariIso(e.createdAt);
        if (b && cur.closedMonths.includes(b)) continue;
        if ((mapKas.get(e.remoteId) || 0) <= e.updatedAt) {
          await pushSingleDoc(uid, 'kas', e.remoteId, FirestoreMapper.kasToMap(e));
          pushed++;
        }
        e.kotor = false;
      }

      for (const e of cur.suppliers.filter((x) => x.kotor)) {
        if (!e.remoteId) continue;
        if ((mapPemasok.get(e.remoteId) || 0) <= e.updatedAt) {
          await pushSingleDoc(uid, 'pemasok', e.remoteId, FirestoreMapper.pemasokToMap(e));
          pushed++;
        }
        e.kotor = false;
      }

      for (const e of cur.saleItems.filter((x) => x.kotor)) {
        if (!e.remoteId || !e.saleRemoteId) continue;
        if ((mapItemJual.get(e.remoteId) || 0) <= e.updatedAt) {
          await pushSingleDoc(uid, 'penjualan_item', e.remoteId, FirestoreMapper.itemJualToMap(e));
          pushed++;
        }
        e.kotor = false;
      }

      for (const e of cur.purchaseItems.filter((x) => x.kotor)) {
        if (!e.remoteId || !e.purchaseRemoteId) continue;
        if ((mapItemPbf.get(e.remoteId) || 0) <= e.updatedAt) {
          await pushSingleDoc(uid, 'pbf_item', e.remoteId, FirestoreMapper.itemPbfToMap(e));
          pushed++;
        }
        e.kotor = false;
      }

      // 2) Pull & Merge Remote Docs (Last-Write-Wins)
      let nextMedId = cur.medicines.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      const medsByRemote = new Map(cur.medicines.map((x) => [x.remoteId, x]));
      for (const docSnap of remObat) {
        const existing = medsByRemote.get(docSnap.id);
        const mapped = FirestoreMapper.obatFromMap(docSnap.data, existing?.id ?? nextMedId);
        if (!mapped) continue;
        if (!existing) {
          if (!mapped.terhapus) {
            nextMedId++;
            cur.medicines.push(mapped);
            medsByRemote.set(mapped.remoteId, mapped);
            pulled++;
          }
        } else if (
          remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
        ) {
          Object.assign(existing, mapped, { id: existing.id, kotor: false });
          pulled++;
        }
      }

      let nextSaleId = cur.sales.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      const salesByRemote = new Map(cur.sales.map((x) => [x.remoteId, x]));
      for (const docSnap of remJual) {
        const existing = salesByRemote.get(docSnap.id);
        const mapped = FirestoreMapper.penjualanFromMap(docSnap.data, existing?.id ?? nextSaleId);
        if (!mapped) continue;
        if (!existing) {
          if (!mapped.terhapus) {
            nextSaleId++;
            cur.sales.push(mapped);
            salesByRemote.set(mapped.remoteId, mapped);
            pulled++;
          }
        } else if (
          remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
        ) {
          Object.assign(existing, mapped, { id: existing.id, kotor: false });
          pulled++;
        }
      }

      let nextPbfId = cur.purchases.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      const pbfByRemote = new Map(cur.purchases.map((x) => [x.remoteId, x]));
      for (const docSnap of remPbf) {
        const existing = pbfByRemote.get(docSnap.id);
        const mapped = FirestoreMapper.pbfFromMap(docSnap.data, existing?.id ?? nextPbfId);
        if (!mapped) continue;
        if (!existing) {
          if (!mapped.terhapus) {
            nextPbfId++;
            cur.purchases.push(mapped);
            pbfByRemote.set(mapped.remoteId, mapped);
            pulled++;
          }
        } else if (
          remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.updatedAt === -1)
        ) {
          if (
            remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
          ) {
            Object.assign(existing, mapped, { id: existing.id, kotor: false });
            pulled++;
          }
        }
      }

      let nextKasId = cur.cashEntries.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      const kasByRemote = new Map(cur.cashEntries.map((x) => [x.remoteId, x]));
      for (const docSnap of remKas) {
        const existing = kasByRemote.get(docSnap.id);
        const mapped = FirestoreMapper.kasFromMap(docSnap.data, existing?.id ?? nextKasId);
        if (!mapped) continue;
        if (!existing) {
          if (!mapped.terhapus) {
            nextKasId++;
            cur.cashEntries.push(mapped);
            kasByRemote.set(mapped.remoteId, mapped);
            pulled++;
          }
        } else if (
          remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
        ) {
          Object.assign(existing, mapped, { id: existing.id, kotor: false });
          pulled++;
        }
      }

      let nextSupId = cur.suppliers.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      const supByRemote = new Map(cur.suppliers.map((x) => [x.remoteId, x]));
      for (const docSnap of remPemasok) {
        const existing = supByRemote.get(docSnap.id);
        const mapped = FirestoreMapper.pemasokFromMap(docSnap.data, existing?.id ?? nextSupId);
        if (!mapped) continue;
        if (!existing) {
          if (!mapped.terhapus) {
            nextSupId++;
            cur.suppliers.push(mapped);
            supByRemote.set(mapped.remoteId, mapped);
            pulled++;
          }
        } else if (
          remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
        ) {
          Object.assign(existing, mapped, { id: existing.id, kotor: false });
          pulled++;
        }
      }

      let nextSaleItemId = cur.saleItems.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      const saleItemByRemote = new Map(cur.saleItems.map((x) => [x.remoteId, x]));
      for (const docSnap of remItemJual) {
        const d = docSnap.data;
        const parentSale = salesByRemote.get(String(d.saleRemoteId || ''));
        if (!parentSale) continue;
        const med = medsByRemote.get(String(d.medicineRemoteId || ''));
        const existing = saleItemByRemote.get(docSnap.id);
        const mapped = FirestoreMapper.itemJualFromMap(
          d,
          existing?.id ?? nextSaleItemId,
          parentSale.id,
          med?.id ?? 0
        );
        if (!mapped) continue;
        if (!existing) {
          if (!mapped.terhapus) {
            nextSaleItemId++;
            cur.saleItems.push(mapped);
            saleItemByRemote.set(mapped.remoteId, mapped);
            pulled++;
          }
        } else if (
          remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
        ) {
          Object.assign(existing, mapped, { id: existing.id, kotor: false });
          pulled++;
        }
      }

      let nextPbfItemId = cur.purchaseItems.reduce((m, x) => Math.max(m, x.id), 0) + 1;
      const pbfItemByRemote = new Map(cur.purchaseItems.map((x) => [x.remoteId, x]));
      for (const docSnap of remItemPbf) {
        const d = docSnap.data;
        const parentPbf = pbfByRemote.get(String(d.purchaseRemoteId || ''));
        if (!parentPbf) continue;
        const existing = pbfItemByRemote.get(docSnap.id);
        const mapped = FirestoreMapper.itemPbfFromMap(
          d,
          existing?.id ?? nextPbfItemId,
          parentPbf.id
        );
        if (!mapped) continue;
        if (!existing) {
          if (!mapped.terhapus) {
            nextPbfItemId++;
            cur.purchaseItems.push(mapped);
            pbfItemByRemote.set(mapped.remoteId, mapped);
            pulled++;
          }
        } else if (
          remoteMenang(mapped.updatedAt, mapped.terhapus, existing.updatedAt, existing.terhapus)
        ) {
          Object.assign(existing, mapped, { id: existing.id, kotor: false });
          pulled++;
        }
      }

      cur.lastSyncAt = Date.now();
      setDbState({ ...cur });
      setSyncStatus('BERHASIL');
      if (!silent) {
        setSyncMessage(`Tersinkron (↑${pushed} ↓${pulled})`);
      }
    } catch (e: any) {
      console.error('Sync error:', e);
      setSyncStatus('GAGAL');
      setSyncMessage(e?.message || 'Sinkronisasi gagal');
    } finally {
      syncingRef.current = false;
    }
  };

  // Auto-push helper right after local mutation
  const queueAutoSync = () => {
    if (auth.currentUser) {
      setTimeout(() => syncNow(true), 1200);
    }
  };

  // ---- Medicine Mutations ----
  const saveMedicine: StoreContextType['saveMedicine'] = (med, editId) => {
    if (!med.name.trim()) throw new Error('Nama obat tidak boleh kosong');
    if (med.sellPrice <= 0) throw new Error('Harga jual harus lebih dari 0');
    if (med.stock < 0 || med.buyPrice < 0 || med.wholesalePrice < 0)
      throw new Error('Stok / Harga tidak boleh negatif');

    const bc = med.barcode?.trim() || null;
    const now = Date.now();

    setDbState((prev) => {
      if (bc) {
        const dup = prev.medicines.find(
          (m) => !m.terhapus && m.id !== editId && m.barcode?.toLowerCase() === bc.toLowerCase()
        );
        if (dup) throw new Error(`Barcode ${bc} sudah dipakai untuk ${dup.name}`);
      }

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
      const existing = bc
        ? curMeds.find((m) => m.barcode?.toLowerCase() === bc.toLowerCase())
        : curMeds.find(
            (m) =>
              m.name.toLowerCase() === item.name.trim().toLowerCase() &&
              m.category.toLowerCase() === (item.category || 'Umum').toLowerCase() &&
              m.unit.toLowerCase() === (item.unit || 'Strip').toLowerCase()
          );

      if (existing) {
        Object.assign(existing, {
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
        });
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
    if (!clean) throw new Error('Nama pemasok tidak boleh kosong');
    const dup = dbRef.current.suppliers.find(
      (s) => !s.terhapus && s.id !== editId && s.name.toLowerCase() === clean.toLowerCase()
    );
    if (dup) throw new Error(`Pemasok "${clean}" sudah ada di daftar`);
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
    const root = JSON.parse(jsonStr);
    if (root.version !== 1 || root.app !== 'apotek-lestari') {
      throw new Error('File bukan backup Apotek Lestari yang sah (versi 1)');
    }
    if (
      !Array.isArray(root.medicines) ||
      !Array.isArray(root.sales) ||
      !Array.isArray(root.saleItems) ||
      !Array.isArray(root.cashEntries)
    ) {
      throw new Error('Backup tidak lengkap: array wajib tidak ditemukan');
    }

    const meds: Medicine[] = root.medicines.map((m: any) => ({
      id: Number(m.id),
      name: String(m.name || ''),
      category: String(m.category || 'Umum'),
      unit: String(m.unit || 'Strip'),
      buyPrice: Number(m.buyPrice) || 0,
      sellPrice: Number(m.sellPrice) || 0,
      wholesalePrice: Number(m.wholesalePrice) || 0,
      stock: Number(m.stock) || 0,
      expiryDate: m.expiryDate || null,
      barcode: m.barcode || null,
      createdAt: String(m.createdAt || ''),
      remoteId: String(m.remoteId || genRemoteId('obat')),
      updatedAt: Number(m.updatedAt) || Date.now(),
      terhapus: false,
      kotor: true,
    }));

    const sales: Sale[] = root.sales.map((s: any) => ({
      id: Number(s.id),
      invoiceNo: String(s.invoiceNo || ''),
      total: Number(s.total) || 0,
      paymentMethod: s.paymentMethod || 'Tunai',
      amountPaid: Number(s.amountPaid) || 0,
      changeAmount: Number(s.changeAmount) || 0,
      createdAt: String(s.createdAt || ''),
      remoteId: String(s.remoteId || genRemoteId('jual')),
      updatedAt: Number(s.updatedAt) || Date.now(),
      terhapus: false,
      kotor: true,
    }));

    const saleRemoteById = new Map(sales.map((s) => [s.id, s.remoteId]));
    const medRemoteById = new Map(meds.map((m) => [m.id, m.remoteId]));

    const saleItems: SaleItem[] = root.saleItems.map((it: any) => ({
      id: Number(it.id),
      saleId: Number(it.saleId),
      saleRemoteId: saleRemoteById.get(Number(it.saleId)) || '',
      medicineId: Number(it.medicineId) || 0,
      medicineRemoteId: medRemoteById.get(Number(it.medicineId)) || '',
      medicineName: String(it.medicineName || ''),
      category: String(it.category || ''),
      unit: String(it.unit || ''),
      qty: Number(it.qty) || 0,
      price: Number(it.price) || 0,
      priceType: it.priceType === 'GROSIR' ? 'GROSIR' : 'ECER',
      buyPrice: Number(it.buyPrice) || 0,
      subtotal: Number(it.subtotal) || 0,
      remoteId: String(it.remoteId || genRemoteId('jualitem')),
      updatedAt: Number(it.updatedAt) || Date.now(),
      terhapus: false,
      kotor: true,
    }));

    const cashEntries: CashEntry[] = root.cashEntries.map((e: any) => ({
      id: Number(e.id),
      type: e.type === 'out' ? 'out' : 'in',
      category: String(e.category || ''),
      description: String(e.description || ''),
      amount: Number(e.amount) || 0,
      source: e.source === 'sale' ? 'sale' : 'manual',
      createdAt: String(e.createdAt || ''),
      remoteId: String(e.remoteId || genRemoteId('kas')),
      updatedAt: Number(e.updatedAt) || Date.now(),
      terhapus: false,
      kotor: true,
    }));

    const purchases: Purchase[] = (root.purchases || []).map((p: any) => ({
      id: Number(p.id),
      pbfName: String(p.pbfName || ''),
      invoiceNo: String(p.invoiceNo || ''),
      date: String(p.date || ''),
      total: Number(p.total) || 0,
      ppn: Number(p.ppn) || 0,
      paymentType: p.paymentType === 'KREDIT' ? 'KREDIT' : 'CASH',
      dueDate: String(p.dueDate || ''),
      createdAt: String(p.createdAt || ''),
      remoteId: String(p.remoteId || genRemoteId('pbf')),
      updatedAt: Number(p.updatedAt) || Date.now(),
      terhapus: false,
      kotor: true,
    }));

    const pbfRemoteById = new Map(purchases.map((p) => [p.id, p.remoteId]));

    const purchaseItems: PurchaseItem[] = (root.purchaseItems || []).map((pi: any) => ({
      id: Number(pi.id),
      purchaseId: Number(pi.purchaseId),
      purchaseRemoteId: pbfRemoteById.get(Number(pi.purchaseId)) || '',
      medicineName: String(pi.medicineName || ''),
      qty: Number(pi.qty) || 0,
      unit: String(pi.unit || 'Strip'),
      buyPrice: Number(pi.buyPrice) || 0,
      subtotal: Number(pi.subtotal) || 0,
      remoteId: String(pi.remoteId || genRemoteId('pbfitem')),
      updatedAt: Number(pi.updatedAt) || Date.now(),
      terhapus: false,
      kotor: true,
    }));

    const suppliers: Supplier[] = (root.suppliers || []).map((sp: any) => ({
      id: Number(sp.id),
      name: String(sp.name || ''),
      contact: String(sp.contact || ''),
      createdAt: String(sp.createdAt || ''),
      remoteId: String(sp.remoteId || genRemoteId('pemasok')),
      updatedAt: Number(sp.updatedAt) || Date.now(),
      terhapus: false,
      kotor: true,
    }));

    const unionClosed = Array.from(
      new Set([...dbRef.current.closedMonths, ...(root.closedMonths || []).filter(bulanValid)])
    )
      .sort()
      .reverse();

    setDbState({
      medicines: meds,
      sales,
      saleItems,
      cashEntries,
      purchases,
      purchaseItems,
      suppliers,
      closedMonths: unionClosed,
      lastSyncAt: dbRef.current.lastSyncAt,
    });

    queueAutoSync();
    return `Dipulihkan: ${meds.length} obat, ${sales.length} penjualan, ${cashEntries.length} kas, ${purchases.length} faktur PBF.`;
  };

  const resetAllData = async () => {
    if (user) {
      await resetAllCloudData(user.uid);
    }
    setDbState({
      medicines: [],
      sales: [],
      saleItems: [],
      cashEntries: [],
      purchases: [],
      purchaseItems: [],
      suppliers: [],
      closedMonths: [],
      lastSyncAt: 0,
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
        addAuthorityEmail,
        removeAuthorityEmail,
        syncStatus,
        lastSyncAt: dbState.lastSyncAt,
        syncMessage,
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
