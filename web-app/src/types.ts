// Models & Helpers — 100% compatible with Kotlin Android (Models.kt, SyncLogic.kt, TutupBukuLogic.kt, Theme.kt)

export const CATEGORIES = [
  'Umum',
  'Obat Bebas',
  'Obat Bebas Terbatas',
  'Obat Keras',
  'Antibiotik',
  'Vitamin',
  'Pereda Nyeri',
  'Obat Batuk & Flu',
  'Pencernaan',
  'Kulit',
  'Mata',
  'Lainnya',
];

export const UNITS = [
  'Strip',
  'Botol',
  'Vial',
  'Ampul',
  'Box',
  'Tablet',
  'Kapsul',
  'Tube',
  'Drop',
  'Sachet',
  'Pcs',
];

export interface BaseEntity {
  id: number;
  remoteId: string;
  updatedAt: number;
  terhapus: boolean; // stored as boolean in Firestore, 0/1 in Room
  kotor?: boolean;
}

export interface Medicine extends BaseEntity {
  name: string;
  category: string;
  unit: string;
  buyPrice: number;
  sellPrice: number;
  wholesalePrice: number;
  stock: number;
  expiryDate: string | null;
  barcode: string | null;
  createdAt: string;
}

export interface SaleItem extends BaseEntity {
  saleId: number;
  saleRemoteId: string;
  medicineId: number;
  medicineRemoteId: string;
  medicineName: string;
  category: string;
  unit: string;
  qty: number;
  price: number;
  priceType: 'ECER' | 'GROSIR';
  buyPrice: number;
  subtotal: number;
}

export interface Sale extends BaseEntity {
  invoiceNo: string;
  total: number;
  paymentMethod: 'Tunai' | 'QRIS' | 'Transfer';
  amountPaid: number;
  changeAmount: number;
  createdAt: string;
}

export interface CashEntry extends BaseEntity {
  type: 'in' | 'out';
  category: string;
  description: string;
  amount: number;
  source: 'sale' | 'manual';
  createdAt: string;
}

export interface PurchaseItem extends BaseEntity {
  purchaseId: number;
  purchaseRemoteId: string;
  medicineName: string;
  qty: number;
  unit: string;
  buyPrice: number;
  subtotal: number;
}

export interface Purchase extends BaseEntity {
  pbfName: string;
  invoiceNo: string;
  date: string; // yyyy-MM-dd
  total: number;
  ppn: number;
  paymentType: 'CASH' | 'KREDIT';
  dueDate: string; // yyyy-MM-dd or ""
  createdAt: string;
}

export interface Supplier extends BaseEntity {
  name: string;
  contact: string;
  createdAt: string;
}

// ---- SyncLogic.kt LWW conflict resolution ----
export function remoteMenang(
  remoteUpdatedAt: number,
  remoteTerhapus: boolean,
  lokalUpdatedAt: number,
  lokalTerhapus: boolean
): boolean {
  if (remoteUpdatedAt !== lokalUpdatedAt) return remoteUpdatedAt > lokalUpdatedAt;
  return remoteTerhapus && !lokalTerhapus;
}

export function genRemoteId(prefix: string): string {
  const uuid =
    typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
          const r = (Math.random() * 16) | 0;
          const v = c === 'x' ? r : (r & 0x3) | 0x8;
          return v.toString(16);
        });
  return `${prefix}-${uuid}`;
}

// ---- Currency & Stock Helpers (Theme.kt) ----
export function formatIDR(n: number): string {
  const val = Math.round(n || 0);
  return 'Rp ' + val.toLocaleString('id-ID');
}

export function formatCompactIDR(n: number): string {
  if (!n) return '0';
  const abs = Math.abs(n);
  const sign = n < 0 ? '-' : '';
  if (abs < 1000) return `${sign}${abs}`;
  if (abs < 1_000_000) {
    const s = (abs / 1000).toFixed(1).replace(/\.0$/, '').replace('.', ',');
    return `${sign}${s}rb`;
  }
  const s = (abs / 1_000_000).toFixed(1).replace(/\.0$/, '').replace('.', ',');
  return `${sign}${s}jt`;
}

export function stockLabel(stock: number): 'Habis' | 'Rendah' | 'Sedang' | 'Aman' {
  if (stock <= 0) return 'Habis';
  if (stock < 10) return 'Rendah';
  if (stock <= 15) return 'Sedang';
  return 'Aman';
}

// ---- WIB (Asia/Jakarta) Date & Time Helpers ----
const WIB_TZ = 'Asia/Jakarta';

export function nowIso(): string {
  return new Date().toISOString();
}

export function dayKeyID(d: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: WIB_TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(d);
  const y = parts.find((p) => p.type === 'year')?.value ?? '2026';
  const m = parts.find((p) => p.type === 'month')?.value ?? '01';
  const day = parts.find((p) => p.type === 'day')?.value ?? '01';
  return `${y}-${m}-${day}`;
}

export function monthKeyID(d: Date = new Date()): string {
  return dayKeyID(d).slice(0, 7);
}

export function invoiceDateWIB(d: Date = new Date()): string {
  const key = dayKeyID(d); // yyyy-MM-dd
  const [y, m, day] = key.split('-');
  return `INV - ${day}/${m}/${y.slice(2)}`;
}

export function jakartaDayStartMillis(offsetDays = 0): number {
  const todayStr = dayKeyID(new Date());
  // Midnight WIB is UTC-7h
  const baseUtc = new Date(`${todayStr}T00:00:00+07:00`).getTime();
  return baseUtc + offsetDays * 86_400_000;
}

export function jakartaMonthStartMillis(): number {
  const mKey = monthKeyID(new Date());
  return new Date(`${mKey}-01T00:00:00+07:00`).getTime();
}

export function parseISODate(s?: string | null): Date | null {
  if (!s || !s.trim()) return null;
  const v = s.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(v)) {
    const d = new Date(`${v}T00:00:00Z`);
    return isNaN(d.getTime()) ? null : d;
  }
  const d = new Date(v);
  return isNaN(d.getTime()) ? null : d;
}

export function formatDateTimeID(s?: string | null): string {
  const d = parseISODate(s);
  if (!d) return s || '-';
  return new Intl.DateTimeFormat('id-ID', {
    timeZone: WIB_TZ,
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d);
}

export function formatDateID(s?: string | null): string {
  const d = parseISODate(s);
  if (!d) return s || '-';
  return new Intl.DateTimeFormat('id-ID', {
    timeZone: WIB_TZ,
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(d);
}

export function daysUntilExpiry(expiry?: string | null): number | null {
  if (!expiry) return null;
  const expMs = new Date(`${expiry.slice(0, 10)}T00:00:00+07:00`).getTime();
  if (isNaN(expMs)) return null;
  const todayMs = jakartaDayStartMillis(0);
  return Math.floor((expMs - todayMs) / 86_400_000);
}

export function dueDaysLeft(dueDate?: string | null): number | null {
  if (!dueDate) return null;
  return daysUntilExpiry(dueDate);
}

// ---- TutupBukuLogic.kt equivalents ----
export function bulanValid(bulan: string): boolean {
  if (!/^\d{4}-\d{2}$/.test(bulan)) return false;
  const m = parseInt(bulan.slice(5, 7), 10);
  return m >= 1 && m <= 12;
}

export function bulanKeyDariIso(iso?: string | null): string | null {
  if (!iso || !iso.trim()) return null;
  const s = iso.trim();
  if (s.length === 7 && bulanValid(s)) return s;
  const d = parseISODate(s);
  if (!d) return null;
  return monthKeyID(d);
}

export function rentangBulan(bulan: string): [number, number] | null {
  if (!bulanValid(bulan)) return null;
  const [yStr, mStr] = bulan.split('-');
  const y = parseInt(yStr, 10);
  const m = parseInt(mStr, 10);
  const start = new Date(`${bulan}-01T00:00:00+07:00`).getTime();
  const nextY = m === 12 ? y + 1 : y;
  const nextM = m === 12 ? 1 : m + 1;
  const nextBulan = `${nextY}-${String(nextM).padStart(2, '0')}`;
  const end = new Date(`${nextBulan}-01T00:00:00+07:00`).getTime();
  return [start, end];
}

export function bulanSelesai(bulan: string): boolean {
  const r = rentangBulan(bulan);
  if (!r) return false;
  return r[1] <= jakartaMonthStartMillis();
}

export function namaBulanIndonesia(bulan: string): string {
  const r = rentangBulan(bulan);
  if (!r) return bulan;
  return new Intl.DateTimeFormat('id-ID', {
    timeZone: WIB_TZ,
    month: 'long',
    year: 'numeric',
  }).format(new Date(r[0]));
}
