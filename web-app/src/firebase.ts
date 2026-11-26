// Firebase configuration from kotlin-app/app/google-services.json
// Syncs Web data to shared `apotek/main/web_{collection}/{remoteId}` (isolated from Android APK `users/{uid}`).

import { initializeApp } from 'firebase/app';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut as fbSignOut,
  onAuthStateChanged,
  User,
} from 'firebase/auth';
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  onSnapshot,
  setDoc,
  deleteDoc,
  deleteField,
  query,
  runTransaction,
  where,
  writeBatch,
} from 'firebase/firestore';
import { computeAtomicStockUpdates } from './syncMerge';
import {
  Medicine,
  Sale,
  SaleItem,
  CashEntry,
  Purchase,
  PurchaseItem,
  Supplier,
} from './types';

const firebaseConfig = {
  apiKey: 'AIzaSyCxL9iEy1pvuNjD33i6YIorMf-10hNwEME',
  authDomain: 'kasbulanan-6171e.firebaseapp.com',
  projectId: 'kasbulanan-6171e',
  storageBucket: 'kasbulanan-6171e.firebasestorage.app',
  messagingSenderId: '49722118171',
  appId: '1:49722118171:android:2bf8528e82172f2e2a68be',
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });

export const DEFAULT_AUTHORITY_EMAILS = [
  'achmadhida66@gmail.com',
  'hanyrahayu19@gmail.com',
];

export type AuthorityRole = 'owner' | 'kasir';

export interface AuthorityConfig {
  emails: string[];
  ownerEmails: string[];
}

export function normalizeEmail(email?: string | null): string {
  return (email || '').trim().toLowerCase();
}

function normalizeEmailList(list: unknown): string[] {
  if (!Array.isArray(list)) return [];
  const seen = new Set<string>();
  for (const item of list) {
    if (typeof item !== 'string') continue;
    const clean = normalizeEmail(item);
    if (clean && clean.includes('@')) seen.add(clean);
  }
  return Array.from(seen);
}

function resolveOwnerEmails(emails: string[], rawOwners: unknown): string[] {
  const parsed = normalizeEmailList(rawOwners).filter((e) => emails.includes(e));
  if (parsed.length > 0) return parsed;
  const defaults = DEFAULT_AUTHORITY_EMAILS.filter((e) => emails.includes(e));
  if (defaults.length > 0) return defaults;
  return emails.length > 0 ? [emails[0]] : [...DEFAULT_AUTHORITY_EMAILS];
}

const AUTHORITY_CACHE_KEY = 'apotek_lestari_authority_cfg_v1';

function readAuthorityCache(): AuthorityConfig | null {
  try {
    const raw = localStorage.getItem(AUTHORITY_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    const emails = normalizeEmailList(parsed?.emails);
    if (emails.length === 0) return null;
    return {
      emails,
      ownerEmails: resolveOwnerEmails(emails, parsed?.ownerEmails),
    };
  } catch {
    return null;
  }
}

function writeAuthorityCache(cfg: AuthorityConfig) {
  try {
    localStorage.setItem(AUTHORITY_CACHE_KEY, JSON.stringify(cfg));
  } catch {
    // ignore storage quota errors
  }
}

export async function fetchAuthorityConfig(seedIfEmpty = false): Promise<AuthorityConfig> {
  const ref = doc(db, 'authority', 'config');
  try {
    const snap = await getDoc(ref);
    if (snap.exists()) {
      const data = snap.data();
      const parsed = normalizeEmailList(data?.emails);
      if (parsed.length > 0) {
        const cfg = {
          emails: parsed,
          ownerEmails: resolveOwnerEmails(parsed, data?.ownerEmails),
        };
        writeAuthorityCache(cfg);
        return cfg;
      }
    }
    if (seedIfEmpty) {
      const defCfg = {
        emails: [...DEFAULT_AUTHORITY_EMAILS],
        ownerEmails: [...DEFAULT_AUTHORITY_EMAILS],
      };
      await setDoc(
        ref,
        {
          emails: defCfg.emails,
          ownerEmails: defCfg.ownerEmails,
          updatedAt: Date.now(),
        },
        { merge: true }
      ).catch(() => {});
      writeAuthorityCache(defCfg);
      return defCfg;
    }
  } catch (err: any) {
    const code = err?.code || '';
    if (code === 'permission-denied' || code === 'unauthenticated') {
      return { emails: [], ownerEmails: [] };
    }
    const cached = readAuthorityCache();
    if (cached) return cached;
  }
  return { emails: [], ownerEmails: [] };
}

export async function saveAuthorityConfig(
  emails: string[],
  ownerEmails: string[]
): Promise<AuthorityConfig> {
  const cleanEmails = normalizeEmailList(emails);
  if (cleanEmails.length === 0) {
    throw new Error('Minimal harus ada 1 email di kolom Authority.');
  }
  const cleanOwners = normalizeEmailList(ownerEmails).filter((e) => cleanEmails.includes(e));
  if (cleanOwners.length === 0) {
    throw new Error('Minimal harus ada 1 akun dengan role Owner.');
  }
  await setDoc(
    doc(db, 'authority', 'config'),
    {
      emails: cleanEmails,
      ownerEmails: cleanOwners,
      updatedAt: Date.now(),
    },
    { merge: true }
  );
  return { emails: cleanEmails, ownerEmails: cleanOwners };
}

export function isEmailAuthorized(email: string | null | undefined, allowed: string[]): boolean {
  const norm = normalizeEmail(email);
  return Boolean(norm) && allowed.includes(norm);
}

export function subscribeAuthorityConfig(onUpdate: (cfg: AuthorityConfig) => void): () => void {
  const ref = doc(db, 'authority', 'config');
  return onSnapshot(
    ref,
    (snap) => {
      if (!snap.exists()) {
        onUpdate({
          emails: [...DEFAULT_AUTHORITY_EMAILS],
          ownerEmails: [...DEFAULT_AUTHORITY_EMAILS],
        });
        return;
      }
      const data = snap.data();
      const parsed = normalizeEmailList(data?.emails);
      const emails = parsed.length > 0 ? parsed : [...DEFAULT_AUTHORITY_EMAILS];
      const cfg = {
        emails,
        ownerEmails: resolveOwnerEmails(emails, data?.ownerEmails),
      };
      writeAuthorityCache(cfg);
      onUpdate(cfg);
    },
    () => {
      // Ignore transient listener errors during sign-out or offline state
    }
  );
}

export async function loginWithGoogle(): Promise<User> {
  const res = await signInWithPopup(auth, googleProvider);
  return res.user;
}

export type PinAlgo = 'pbkdf2-sha256-100k' | 'sha256-v1';

export interface AuthorityPinConfig {
  pinHash: string;
  pinSalt: string;
  algo?: PinAlgo;
}

const PIN_PBKDF2_ITERATIONS = 100_000;

async function hashPinLegacySha256(pin: string, salt: string): Promise<string> {
  if (!globalThis.crypto?.subtle) {
    throw new Error('Browser tidak mendukung enkripsi PIN (butuh HTTPS).');
  }
  const bytes = new TextEncoder().encode(`${salt}:${pin}`);
  const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
}

async function hashPinPbkdf2(pin: string, salt: string): Promise<string> {
  if (!globalThis.crypto?.subtle) {
    throw new Error('Browser tidak mendukung enkripsi PIN (butuh HTTPS).');
  }
  const enc = new TextEncoder();
  const baseKey = await globalThis.crypto.subtle.importKey('raw', enc.encode(pin), 'PBKDF2', false, [
    'deriveBits',
  ]);
  const bits = await globalThis.crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt: enc.encode(salt),
      iterations: PIN_PBKDF2_ITERATIONS,
      hash: 'SHA-256',
    },
    baseKey,
    256
  );
  return Array.from(new Uint8Array(bits), (b) => b.toString(16).padStart(2, '0')).join('');
}

function generatePinSalt(): string {
  const buf = new Uint8Array(16);
  globalThis.crypto.getRandomValues(buf);
  return Array.from(buf, (b) => b.toString(16).padStart(2, '0')).join('');
}

export async function fetchAuthorityPin(): Promise<AuthorityPinConfig | null> {
  const pinSnap = await getDoc(doc(db, 'authority', 'pin'));
  if (pinSnap.exists()) {
    const data = pinSnap.data();
    if (typeof data?.pinHash === 'string' && data.pinHash && typeof data?.pinSalt === 'string' && data.pinSalt) {
      return {
        pinHash: data.pinHash,
        pinSalt: data.pinSalt,
        algo: data.algo === 'sha256-v1' ? 'sha256-v1' : 'pbkdf2-sha256-100k',
      };
    }
  }
  // Backward-compatible read for legacy SHA-256 PIN still stored on authority/config
  const legacySnap = await getDoc(doc(db, 'authority', 'config'));
  if (legacySnap.exists()) {
    const data = legacySnap.data();
    if (typeof data?.pinHash === 'string' && data.pinHash && typeof data?.pinSalt === 'string' && data.pinSalt) {
      return { pinHash: data.pinHash, pinSalt: data.pinSalt, algo: 'sha256-v1' };
    }
  }
  return null;
}

export async function saveAuthorityPin(pin: string): Promise<AuthorityPinConfig> {
  if (!/^\d{4,6}$/.test(pin)) {
    throw new Error('PIN harus berupa 4 sampai 6 digit angka.');
  }
  const pinSalt = generatePinSalt();
  const pinHash = await hashPinPbkdf2(pin, pinSalt);
  await setDoc(
    doc(db, 'authority', 'pin'),
    {
      pinHash,
      pinSalt,
      algo: 'pbkdf2-sha256-100k',
      pinUpdatedAt: Date.now(),
    },
    { merge: true }
  );
  // Strip legacy hash fields from readable authority/config document
  await setDoc(
    doc(db, 'authority', 'config'),
    {
      pinHash: deleteField(),
      pinSalt: deleteField(),
      pinUpdatedAt: deleteField(),
    },
    { merge: true }
  ).catch(() => {});
  return { pinHash, pinSalt, algo: 'pbkdf2-sha256-100k' };
}

export async function verifyAuthorityPin(pin: string, config: AuthorityPinConfig): Promise<boolean> {
  if (!/^\d{4,6}$/.test(pin)) return false;
  const candidate =
    config.algo === 'sha256-v1'
      ? await hashPinLegacySha256(pin, config.pinSalt)
      : await hashPinPbkdf2(pin, config.pinSalt);
  return candidate === config.pinHash;
}

export async function logoutFirebase(): Promise<void> {
  await fbSignOut(auth);
}

export { onAuthStateChanged };
export type { User };

// ---- SyncLogic.kt Mappers (Entity <-> Firestore Document Map) ----
const clampInt = (v: unknown): number => {
  const n = typeof v === 'number' ? Math.trunc(v) : Number(v) || 0;
  return Math.max(0, Math.min(2147483647, n));
};

export const FirestoreMapper = {
  obatToMap: (e: Medicine) => ({
    remoteId: e.remoteId,
    updatedAt: e.updatedAt,
    terhapus: Boolean(e.terhapus),
    name: e.name,
    category: e.category,
    medicineType: (e.medicineType || '').trim(),
    unit: e.unit,
    buyPrice: clampInt(e.buyPrice),
    sellPrice: clampInt(e.sellPrice),
    wholesalePrice: clampInt(e.wholesalePrice),
    stock: clampInt(e.stock),
    expiryDate: e.expiryDate || null,
    barcode: e.barcode || null,
    createdAt: e.createdAt,
  }),
  obatFromMap: (m: Record<string, any>, localId: number): Medicine | null => {
    if (!m.remoteId || typeof m.remoteId !== 'string') return null;
    return {
      id: localId,
      remoteId: m.remoteId,
      updatedAt: Number(m.updatedAt) || 0,
      terhapus: m.terhapus === true,
      name: String(m.name || ''),
      category: String(m.category || 'Umum'),
      medicineType: typeof m.medicineType === 'string' ? m.medicineType.trim() : '',
      unit: String(m.unit || 'Strip'),
      buyPrice: clampInt(m.buyPrice),
      sellPrice: clampInt(m.sellPrice),
      wholesalePrice: clampInt(m.wholesalePrice),
      stock: clampInt(m.stock),
      expiryDate: typeof m.expiryDate === 'string' ? m.expiryDate : null,
      barcode: typeof m.barcode === 'string' ? m.barcode : null,
      createdAt: String(m.createdAt || ''),
      kotor: false,
    };
  },

  penjualanToMap: (e: Sale) => ({
    remoteId: e.remoteId,
    updatedAt: e.updatedAt,
    terhapus: Boolean(e.terhapus),
    invoiceNo: e.invoiceNo,
    total: clampInt(e.total),
    paymentMethod: e.paymentMethod,
    amountPaid: clampInt(e.amountPaid),
    changeAmount: clampInt(e.changeAmount),
    createdAt: e.createdAt,
  }),
  penjualanFromMap: (m: Record<string, any>, localId: number): Sale | null => {
    if (!m.remoteId || typeof m.remoteId !== 'string') return null;
    const pm = m.paymentMethod;
    return {
      id: localId,
      remoteId: m.remoteId,
      updatedAt: Number(m.updatedAt) || 0,
      terhapus: m.terhapus === true,
      invoiceNo: String(m.invoiceNo || ''),
      total: clampInt(m.total),
      paymentMethod: pm === 'QRIS' || pm === 'Transfer' ? pm : 'Tunai',
      amountPaid: clampInt(m.amountPaid),
      changeAmount: clampInt(m.changeAmount),
      createdAt: String(m.createdAt || ''),
      kotor: false,
    };
  },

  itemJualToMap: (e: SaleItem) => ({
    remoteId: e.remoteId,
    updatedAt: e.updatedAt,
    terhapus: Boolean(e.terhapus),
    saleRemoteId: e.saleRemoteId,
    medicineRemoteId: e.medicineRemoteId || '',
    medicineName: e.medicineName,
    category: e.category,
    unit: e.unit,
    qty: clampInt(e.qty),
    price: clampInt(e.price),
    priceType: e.priceType || 'ECER',
    buyPrice: clampInt(e.buyPrice),
    subtotal: clampInt(e.subtotal),
  }),
  itemJualFromMap: (
    m: Record<string, any>,
    localId: number,
    saleId: number,
    medicineId: number
  ): SaleItem | null => {
    if (!m.remoteId || typeof m.remoteId !== 'string') return null;
    if (!m.saleRemoteId || typeof m.saleRemoteId !== 'string') return null;
    return {
      id: localId,
      saleId,
      saleRemoteId: m.saleRemoteId,
      medicineId,
      medicineRemoteId: String(m.medicineRemoteId || ''),
      remoteId: m.remoteId,
      updatedAt: Number(m.updatedAt) || 0,
      terhapus: m.terhapus === true,
      medicineName: String(m.medicineName || ''),
      category: String(m.category || ''),
      unit: String(m.unit || ''),
      qty: clampInt(m.qty),
      price: clampInt(m.price),
      priceType: m.priceType === 'GROSIR' ? 'GROSIR' : 'ECER',
      buyPrice: clampInt(m.buyPrice),
      subtotal: clampInt(m.subtotal),
      kotor: false,
    };
  },

  kasToMap: (e: CashEntry) => ({
    remoteId: e.remoteId,
    updatedAt: e.updatedAt,
    terhapus: Boolean(e.terhapus),
    type: e.type,
    category: e.category,
    description: e.description,
    amount: clampInt(e.amount),
    source: e.source,
    createdAt: e.createdAt,
  }),
  kasFromMap: (m: Record<string, any>, localId: number): CashEntry | null => {
    if (!m.remoteId || typeof m.remoteId !== 'string') return null;
    if (m.type !== 'in' && m.type !== 'out') return null;
    return {
      id: localId,
      remoteId: m.remoteId,
      updatedAt: Number(m.updatedAt) || 0,
      terhapus: m.terhapus === true,
      type: m.type,
      category: String(m.category || ''),
      description: String(m.description || ''),
      amount: clampInt(m.amount),
      source: m.source === 'sale' ? 'sale' : 'manual',
      createdAt: String(m.createdAt || ''),
      kotor: false,
    };
  },

  pbfToMap: (e: Purchase) => ({
    remoteId: e.remoteId,
    updatedAt: e.updatedAt,
    terhapus: Boolean(e.terhapus),
    pbfName: e.pbfName,
    invoiceNo: e.invoiceNo,
    date: e.date,
    total: clampInt(e.total),
    ppn: clampInt(e.ppn),
    paymentType: e.paymentType,
    dueDate: e.dueDate,
    createdAt: e.createdAt,
  }),
  pbfFromMap: (m: Record<string, any>, localId: number): Purchase | null => {
    if (!m.remoteId || typeof m.remoteId !== 'string') return null;
    return {
      id: localId,
      remoteId: m.remoteId,
      updatedAt: Number(m.updatedAt) || 0,
      terhapus: m.terhapus === true,
      pbfName: String(m.pbfName || ''),
      invoiceNo: String(m.invoiceNo || ''),
      date: String(m.date || ''),
      total: clampInt(m.total),
      ppn: clampInt(m.ppn),
      paymentType: m.paymentType === 'KREDIT' ? 'KREDIT' : 'CASH',
      dueDate: String(m.dueDate || ''),
      createdAt: String(m.createdAt || ''),
      kotor: false,
    };
  },

  itemPbfToMap: (e: PurchaseItem) => ({
    remoteId: e.remoteId,
    updatedAt: e.updatedAt,
    terhapus: Boolean(e.terhapus),
    purchaseRemoteId: e.purchaseRemoteId,
    medicineName: e.medicineName,
    qty: clampInt(e.qty),
    unit: e.unit || 'Strip',
    buyPrice: clampInt(e.buyPrice),
    subtotal: clampInt(e.subtotal),
  }),
  itemPbfFromMap: (
    m: Record<string, any>,
    localId: number,
    purchaseId: number
  ): PurchaseItem | null => {
    if (!m.remoteId || typeof m.remoteId !== 'string') return null;
    if (!m.purchaseRemoteId || typeof m.purchaseRemoteId !== 'string') return null;
    return {
      id: localId,
      purchaseId,
      purchaseRemoteId: m.purchaseRemoteId,
      remoteId: m.remoteId,
      updatedAt: Number(m.updatedAt) || 0,
      terhapus: m.terhapus === true,
      medicineName: String(m.medicineName || ''),
      qty: clampInt(m.qty),
      unit: String(m.unit || 'Strip'),
      buyPrice: clampInt(m.buyPrice),
      subtotal: clampInt(m.subtotal),
      kotor: false,
    };
  },

  pemasokToMap: (e: Supplier) => ({
    remoteId: e.remoteId,
    updatedAt: e.updatedAt,
    terhapus: Boolean(e.terhapus),
    name: e.name,
    contact: e.contact,
    createdAt: e.createdAt,
  }),
  pemasokFromMap: (m: Record<string, any>, localId: number): Supplier | null => {
    if (!m.remoteId || typeof m.remoteId !== 'string') return null;
    return {
      id: localId,
      remoteId: m.remoteId,
      updatedAt: Number(m.updatedAt) || 0,
      terhapus: m.terhapus === true,
      name: String(m.name || ''),
      contact: String(m.contact || ''),
      createdAt: String(m.createdAt || ''),
      kotor: false,
    };
  },
};

// Prefix with `web_` so web-app data stays isolated from APK, stored under shared `apotek/main` for Owner & Kasir.
const webCol = (colName: string) => `web_${colName}`;
const SHARED_ROOT = ['apotek', 'main'] as const;
const WEB_SYNC_COLS = [
  'obat',
  'penjualan',
  'penjualan_item',
  'kas',
  'pbf',
  'pbf_item',
  'pemasok',
] as const;

export async function pushSingleDoc(
  colName: string,
  remoteId: string,
  data: Record<string, any>
) {
  const ref = doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol(colName), remoteId);
  await setDoc(ref, data);
}

export async function pushBatchDocs(
  colName: string,
  items: Array<{ remoteId: string; data: Record<string, any> }>
) {
  for (let i = 0; i < items.length; i += 400) {
    const batch = writeBatch(db);
    for (const item of items.slice(i, i + 400)) {
      batch.set(doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol(colName), item.remoteId), item.data);
    }
    await batch.commit();
  }
}

export async function fetchCollectionDocs(colName: string, sinceMs: number = 0) {
  const colRef = collection(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol(colName));
  const q = sinceMs > 0 ? query(colRef, where('updatedAt', '>', sinceMs)) : colRef;
  const snap = await getDocs(q);
  return snap.docs.map((d) => ({ id: d.id, data: d.data() }));
}

export async function deleteCloudDoc(colName: string, remoteId: string) {
  await deleteDoc(doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol(colName), remoteId));
}

// One-time migration from legacy per-account `users/{uid}/web_*` into shared `apotek/main/web_*`.
export async function migrateLegacyUserWebData(
  uid: string,
  existingByCol: Record<string, Array<{ id: string; data: Record<string, any> }>>
): Promise<number> {
  let migrated = 0;
  for (const colName of WEB_SYNC_COLS) {
    const legacySnap = await getDocs(collection(db, 'users', uid, webCol(colName)));
    if (legacySnap.empty) continue;

    const existingList = existingByCol[colName] || [];
    const existingMap = new Map(existingList.map((d) => [d.id, d]));
    const toWrite: Array<{ id: string; data: Record<string, any> }> = [];

    for (const d of legacySnap.docs) {
      const legacyData = d.data();
      const cur = existingMap.get(d.id);
      const legacyUpdated = Number(legacyData.updatedAt) || 0;
      const curUpdated = cur ? Number(cur.data.updatedAt) || 0 : -1;
      if (!cur || legacyUpdated > curUpdated) {
        toWrite.push({ id: d.id, data: legacyData });
        if (cur) {
          cur.data = legacyData;
        } else {
          const entry = { id: d.id, data: legacyData };
          existingList.push(entry);
          existingMap.set(d.id, entry);
        }
      }
    }

    for (let i = 0; i < toWrite.length; i += 400) {
      const batch = writeBatch(db);
      for (const item of toWrite.slice(i, i + 400)) {
        batch.set(doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol(colName), item.id), item.data);
      }
      await batch.commit();
      migrated += Math.min(400, toWrite.length - i);
    }
  }
  return migrated;
}

export async function fetchCloudSyncMeta(): Promise<{ resetAt: number }> {
  try {
    const snap = await getDoc(doc(db, SHARED_ROOT[0], SHARED_ROOT[1], 'web_meta', 'sync'));
    if (!snap.exists()) return { resetAt: 0 };
    return { resetAt: Number(snap.data()?.resetAt) || 0 };
  } catch {
    return { resetAt: 0 };
  }
}

export async function tombstoneCloudDocs(
  colName: string,
  docsToTombstone: Array<{ id: string; data: Record<string, any> }>,
  updatedAt: number
) {
  for (let i = 0; i < docsToTombstone.length; i += 400) {
    const batch = writeBatch(db);
    for (const d of docsToTombstone.slice(i, i + 400)) {
      batch.set(doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol(colName), d.id), {
        ...d.data,
        remoteId: d.id,
        terhapus: true,
        updatedAt,
      });
    }
    await batch.commit();
  }
}

export class StockConflictError extends Error {
  remoteId: string;
  serverStock: number;
  constructor(message: string, remoteId: string, serverStock: number) {
    super(message);
    this.name = 'StockConflictError';
    this.remoteId = remoteId;
    this.serverStock = serverStock;
  }
}

export async function commitAtomicSaleToCloud(params: {
  deductions: Array<{ medicine: Medicine; qty: number }>;
  sale: Sale;
  items: SaleItem[];
  cash: CashEntry;
  nowMs: number;
}): Promise<Map<string, { stock: number; updatedAt: number }>> {
  const ordered = [...params.deductions].sort((a, b) =>
    a.medicine.remoteId.localeCompare(b.medicine.remoteId)
  );
  return runTransaction(db, async (tx) => {
    const obatRefs = ordered.map((d) =>
      doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol('obat'), d.medicine.remoteId)
    );
    const snaps = await Promise.all(obatRefs.map((ref) => tx.get(ref)));
    const plan = computeAtomicStockUpdates(
      ordered,
      snaps.map((s) => ({ exists: s.exists(), data: s.exists() ? s.data() : undefined })),
      params.nowMs,
      FirestoreMapper.obatToMap
    );
    if (!plan.ok) {
      throw new StockConflictError(plan.error, plan.remoteId, plan.serverStock);
    }

    const updatedStocks = new Map<string, { stock: number; updatedAt: number }>();
    plan.updates.forEach((u, i) => {
      tx.set(obatRefs[i], u.docData);
      updatedStocks.set(u.remoteId, { stock: u.stock, updatedAt: u.updatedAt });
    });

    tx.set(
      doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol('penjualan'), params.sale.remoteId),
      FirestoreMapper.penjualanToMap(params.sale)
    );
    for (const it of params.items) {
      tx.set(
        doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol('penjualan_item'), it.remoteId),
        FirestoreMapper.itemJualToMap(it)
      );
    }
    tx.set(
      doc(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol('kas'), params.cash.remoteId),
      FirestoreMapper.kasToMap(params.cash)
    );
    return updatedStocks;
  });
}

export function subscribeMedicineDelta(
  sinceMs: number,
  onDocs: (docs: Array<{ id: string; data: Record<string, any> }>) => void,
  onError?: (err: unknown) => void
): () => void {
  const colRef = collection(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol('obat'));
  const q = query(colRef, where('updatedAt', '>', sinceMs));
  return onSnapshot(
    q,
    (snap) => {
      const changed = snap
        .docChanges()
        .filter((ch) => ch.type === 'added' || ch.type === 'modified')
        .map((ch) => ({ id: ch.doc.id, data: ch.doc.data() }));
      if (changed.length > 0) onDocs(changed);
    },
    (err) => {
      onError?.(err);
    }
  );
}

export async function resetAllCloudData(resetAt: number = Date.now()) {
  for (const colName of WEB_SYNC_COLS) {
    const snap = await getDocs(collection(db, SHARED_ROOT[0], SHARED_ROOT[1], webCol(colName)));
    const docs = snap.docs;
    for (let i = 0; i < docs.length; i += 400) {
      const batch = writeBatch(db);
      docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
      await batch.commit();
    }
  }
  await setDoc(
    doc(db, SHARED_ROOT[0], SHARED_ROOT[1], 'web_meta', 'sync'),
    { resetAt },
    { merge: true }
  );
}
