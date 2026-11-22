// Firebase configuration from kotlin-app/app/google-services.json
// Syncs directly with users/{uid}/{collection}/{remoteId} matching OnlineSync.kt & SyncLogic.kt

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
  getDocs,
  setDoc,
  deleteDoc,
  writeBatch,
} from 'firebase/firestore';
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

export async function loginWithGoogle(): Promise<User> {
  const res = await signInWithPopup(auth, googleProvider);
  return res.user;
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

export async function pushSingleDoc(
  uid: string,
  colName: string,
  remoteId: string,
  data: Record<string, any>
) {
  const ref = doc(db, 'users', uid, colName, remoteId);
  await setDoc(ref, data);
}

export async function fetchCollectionDocs(uid: string, colName: string) {
  const snap = await getDocs(collection(db, 'users', uid, colName));
  return snap.docs.map((d) => ({ id: d.id, data: d.data() }));
}

export async function deleteCloudDoc(uid: string, colName: string, remoteId: string) {
  await deleteDoc(doc(db, 'users', uid, colName, remoteId));
}

export async function resetAllCloudData(uid: string) {
  const cols = [
    'obat',
    'penjualan',
    'penjualan_item',
    'kas',
    'pbf',
    'pbf_item',
    'pemasok',
  ];
  for (const colName of cols) {
    const snap = await getDocs(collection(db, 'users', uid, colName));
    const docs = snap.docs;
    for (let i = 0; i < docs.length; i += 400) {
      const batch = writeBatch(db);
      docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
      await batch.commit();
    }
  }
}
