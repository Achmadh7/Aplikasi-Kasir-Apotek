// Models & Helpers — 100% compatible with Kotlin Android (Models.kt, SyncLogic.kt, TutupBukuLogic.kt, Theme.kt)

export const CATEGORIES = [
  'Obat Bebas',
  'Obat Bebas Terbatas',
  'Obat Keras',
  'Narkotika',
  'Obat Herbal/Tradisional',
];

export const MEDICINE_TYPES = [
  'Analgesik & Antipiretik',
  'Antihistamin/Alergi',
  'Antibiotik',
  'Antivirus',
  'Saluran Cerna',
  'Kardiovaskular',
  'Saluran Pernafasan',
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
  medicineType?: string;
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
const ID_INT_FMT = new Intl.NumberFormat('id-ID');

export function formatIDR(n: number): string {
  const val = Math.round(n || 0);
  return 'Rp ' + ID_INT_FMT.format(val);
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
const WIB_DAY_FMT = new Intl.DateTimeFormat('en-CA', {
  timeZone: WIB_TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const WIB_DATETIME_FMT = new Intl.DateTimeFormat('id-ID', {
  timeZone: WIB_TZ,
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
const WIB_DATE_FMT = new Intl.DateTimeFormat('id-ID', {
  timeZone: WIB_TZ,
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});
const WIB_MONTH_YEAR_FMT = new Intl.DateTimeFormat('id-ID', {
  timeZone: WIB_TZ,
  month: 'long',
  year: 'numeric',
});

let cachedWibTodayStart = 0;
let cachedWibTomorrowStart = 0;

export function nowIso(): string {
  return new Date().toISOString();
}

export function dayKeyID(d: Date = new Date()): string {
  const parts = WIB_DAY_FMT.formatToParts(d);
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
  const now = Date.now();
  if (now < cachedWibTodayStart || now >= cachedWibTomorrowStart) {
    const todayStr = dayKeyID(new Date(now));
    cachedWibTodayStart = new Date(`${todayStr}T00:00:00+07:00`).getTime();
    cachedWibTomorrowStart = cachedWibTodayStart + 86_400_000;
  }
  return cachedWibTodayStart + offsetDays * 86_400_000;
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
  return WIB_DATETIME_FMT.format(d);
}

export function formatDateID(s?: string | null): string {
  const d = parseISODate(s);
  if (!d) return s || '-';
  return WIB_DATE_FMT.format(d);
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
  return WIB_MONTH_YEAR_FMT.format(new Date(r[0]));
}

const ID_NAME_COLLATOR = new Intl.Collator('id-ID', {
  sensitivity: 'base',
  numeric: true,
});

export function compareNameID(a: string, b: string): number {
  return ID_NAME_COLLATOR.compare(a, b);
}

export function nextProgressiveLimit(current: number, step: number, total: number): number {
  if (total <= 0) return 0;
  return Math.min(total, Math.max(0, current) + Math.max(1, step));
}

export function filterAndRankMedicines(
  medicines: readonly Medicine[],
  rawQuery: string,
  opts: { sortAz?: boolean; prioritizeSellable?: boolean } = {}
): Medicine[] {
  const { sortAz = true, prioritizeSellable = false } = opts;
  const q = rawQuery.trim().toLowerCase();
  const tokens = q ? q.split(/\s+/).filter(Boolean) : [];

  const matched =
    tokens.length === 0
      ? [...medicines]
      : medicines.filter((m) => {
          const hay = `${m.name} ${m.category} ${m.medicineType || ''} ${m.barcode || ''}`.toLowerCase();
          return tokens.every((t) => hay.includes(t));
        });

  const scoreOf = (m: Medicine): number => {
    if (!q) return 0;
    const barcode = m.barcode?.toLowerCase() || '';
    if (barcode && barcode === q) return 0;
    const name = m.name.toLowerCase();
    if (name.startsWith(q)) return 1;
    if (tokens.length > 0 && name.startsWith(tokens[0])) return 2;
    if (name.includes(q) || tokens.every((t) => name.includes(t))) return 3;
    return 4;
  };

  return matched.sort((a, b) => {
    if (q) {
      const sa = scoreOf(a);
      const sb = scoreOf(b);
      if (sa !== sb) return sa - sb;
    }
    if (prioritizeSellable) {
      const ra = a.stock > 0 && a.sellPrice > 0 ? 0 : 1;
      const rb = b.stock > 0 && b.sellPrice > 0 ? 0 : 1;
      if (ra !== rb) return ra - rb;
    }
    return sortAz ? compareNameID(a.name, b.name) : compareNameID(b.name, a.name);
  });
}
