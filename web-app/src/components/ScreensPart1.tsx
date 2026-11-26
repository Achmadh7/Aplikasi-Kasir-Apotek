import React, { useState, useMemo, useEffect, useRef, useDeferredValue } from 'react';
import { createPortal } from 'react-dom';
import { useStore } from '../store';
import { useFeedback } from './Feedback';
import { AnimatedNumber } from './AnimatedNumber';
import { useIsMdUp, useProgressiveList, ListPaginationFooter } from './ProgressiveList';
import {
  exportRowsToXlsx,
  exportMedicineTemplateXlsx,
  readSpreadsheetMatrix,
  parseMedicineSpreadsheet,
} from '../excel';
import {
  CATEGORIES,
  MEDICINE_TYPES,
  UNITS,
  Medicine,
  Sale,
  SaleItem,
  formatIDR,
  formatCompactIDR,
  stockLabel,
  filterAndRankMedicines,
  jakartaDayStartMillis,
  jakartaMonthStartMillis,
  parseISODate,
  dayKeyID,
  formatDateTimeID,
  formatDateID,
  daysUntilExpiry,
} from '../types';
import {
  Search,
  Plus,
  Trash2,
  Edit2,
  Printer,
  Upload,
  Download,
  AlertTriangle,
  TrendingUp,
  Package,
  CheckCircle2,
  X,
} from 'lucide-react';

// ---- Shared Header (Opsi 3: Warm Paper & Forest) ----
export const PageHeader: React.FC<{
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}> = ({ title, subtitle, action }) => (
  <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] shadow-xs p-4 mb-5 flex flex-wrap items-center justify-between gap-3">
    <div className="min-w-0">
      <h1 className="hdr-slideup text-xl font-extrabold tracking-tight text-[#1C1917]">{title}</h1>
      {subtitle && (
        <p className="text-xs text-[#57534E] mt-0.5">{subtitle}</p>
      )}
    </div>
    {action && <div className="flex items-center gap-2 flex-wrap">{action}</div>}
  </div>
);

export const StockPill: React.FC<{ stock: number }> = ({ stock }) => {
  const label = stockLabel(stock);
  const style =
    label === 'Aman'
      ? 'bg-[#15803D] text-white'
      : label === 'Sedang'
      ? 'bg-[#0369A1] text-white'
      : label === 'Rendah'
      ? 'bg-[#E3B341] text-[#1C1917]'
      : 'bg-[#BA1A1A] text-white';
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold ${style}`}
    >
      Stok {stock} • {label}
    </span>
  );
};

// ============================================================================
// 1. BERANDA SCREEN (Matches BerandaScreen.kt + Repository.getDashboard)
// ============================================================================
export const BerandaScreen: React.FC<{ onNavigate: (tab: string) => void }> = ({
  onNavigate,
}) => {
  const { medicines, sales, saleItems, cashEntries, purchases } = useStore();

  const dash = useMemo(() => {
    const startToday = jakartaDayStartMillis(0);
    const endToday = jakartaDayStartMillis(1);
    const startYesterday = jakartaDayStartMillis(-1);

    let todayIn = 0,
      yesterdayIn = 0;

    for (const e of cashEntries) {
      const t = parseISODate(e.createdAt)?.getTime();
      if (!t) continue;
      if (t >= startToday && t < endToday) {
        if (e.type === 'in' && e.source === 'sale') todayIn += e.amount;
      }
      if (t >= startYesterday && t < startToday) {
        if (e.type === 'in' && e.source === 'sale') yesterdayIn += e.amount;
      }
    }

    const lowStockCount = medicines.filter((m) => m.stock < 10).length;
    const expiringCount = medicines.filter((m) => {
      const d = daysUntilExpiry(m.expiryDate);
      return d !== null && d <= 30;
    }).length;
    const creditPbfCount = purchases.filter((p) => p.paymentType === 'KREDIT').length;

    const todaySales = sales.filter((s) => {
      const t = parseISODate(s.createdAt)?.getTime() ?? -1;
      return t >= startToday && t < endToday;
    });

    const recentSales = [...sales]
      .sort(
        (a, b) =>
          (parseISODate(b.createdAt)?.getTime() ?? 0) -
          (parseISODate(a.createdAt)?.getTime() ?? 0)
      )
      .slice(0, 4);

    const itemsCountBySale = new Map<number, number>();
    for (const it of saleItems) {
      itemsCountBySale.set(it.saleId, (itemsCountBySale.get(it.saleId) || 0) + 1);
    }

    const last7Days = Array.from({ length: 7 }, (_, i) => {
      const offset = 6 - i;
      const dStart = jakartaDayStartMillis(-offset);
      const dEnd = dStart + 86_400_000;
      let inc = 0;
      for (const e of cashEntries) {
        const t = parseISODate(e.createdAt)?.getTime();
        if (!t || t < dStart || t >= dEnd) continue;
        if (e.type === 'in' && e.source === 'sale') inc += e.amount;
      }
      const d = new Date(dStart);
      const label = new Intl.DateTimeFormat('id-ID', {
        timeZone: 'Asia/Jakarta',
        weekday: 'short',
      }).format(d);
      return { label, inc, isToday: offset === 0 };
    });

    const total7Days = last7Days.reduce((sum, d) => sum + d.inc, 0);

    return {
      todayIn,
      yesterdayIn,
      lowStockCount,
      expiringCount,
      creditPbfCount,
      totalSalesToday: todaySales.length,
      last7Days,
      total7Days,
      avg7Days: Math.round(total7Days / 7),
      recentSales,
      itemsCountBySale,
    };
  }, [medicines, sales, saleItems, cashEntries, purchases]);

  const maxBar = Math.max(1, ...dash.last7Days.map((d) => d.inc));

  const [chartReady, setChartReady] = useState(false);
  useEffect(() => {
    const id = requestAnimationFrame(() => setChartReady(true));
    return () => cancelAnimationFrame(id);
  }, []);

  const diffYesterday = dash.todayIn - dash.yesterdayIn;
  const totalAlerts = dash.lowStockCount + dash.expiringCount + dash.creditPbfCount;
  const avgTicket = dash.totalSalesToday > 0 ? Math.round(dash.todayIn / dash.totalSalesToday) : 0;

  return (
    <div className="space-y-4">
      {/* Top Bar: Ringkasan */}
      <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] shadow-xs p-4 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <h1 className="hdr-slideup text-lg sm:text-xl font-extrabold tracking-tight text-[#1C1917]">
            Ringkasan Apotek
          </h1>
          <p className="text-xs text-[#57534E] mt-0.5">
            {dash.totalSalesToday} transaksi hari ini • Kemarin {formatIDR(dash.yesterdayIn)}
          </p>
        </div>
      </div>

      {/* 2-Card KPI Strip: Penjualan Hari Ini & Transaksi Hari Ini ONLY */}
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-3.5 sm:p-4 shadow-xs min-w-0">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[11px] font-bold uppercase tracking-wide text-[#1C1917] truncate">
              Penjualan Hari Ini
            </p>
            <span className="hidden sm:inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold bg-[#DCFCE7] text-[#166534]">
              {diffYesterday >= 0 ? 'Naik' : 'Turun'}
            </span>
          </div>
          <p className="text-lg sm:text-2xl font-extrabold mt-1.5 tabular-nums text-[#1C1917] truncate">
            <AnimatedNumber value={dash.todayIn} format={formatIDR} />
          </p>
          <p className="text-[11px] mt-1 text-[#57534E] truncate">
            {diffYesterday >= 0 ? '+' : ''}
            {formatCompactIDR(diffYesterday)} vs kemarin
          </p>
        </div>

        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-3.5 sm:p-4 shadow-xs min-w-0">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[11px] font-bold uppercase tracking-wide text-[#1C1917] truncate">
              Transaksi Hari Ini
            </p>
            <span className="hidden sm:inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold bg-[#E0F2FE] text-[#0369A1]">
              Struk
            </span>
          </div>
          <p className="text-lg sm:text-2xl font-extrabold mt-1.5 tabular-nums text-[#1C1917]">
            <AnimatedNumber value={dash.totalSalesToday} /> Struk
          </p>
          <p className="text-[11px] mt-1 text-[#57534E] truncate">
            Rata-rata {formatCompactIDR(avgTicket)} / struk
          </p>
        </div>
      </div>

      {/* Main Content: 7-col Sales Chart with Numbers + 5-col Transaksi Terbaru & Pengingat Penting */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-start">
        <div className="lg:col-span-7 rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-4 sm:p-5 shadow-xs min-w-0">
          <div className="flex items-center justify-between gap-2 mb-3">
            <div>
              <h3 className="font-bold text-sm sm:text-base text-[#1C1917]">
                Penjualan 7 Hari Terakhir
              </h3>
              <p className="text-[11px] text-[#57534E]">
                Total omzet harian beserta angka nominal
              </p>
            </div>
            <span className="px-2.5 py-1 rounded-full text-[11px] font-semibold bg-[#DCFCE7] text-[#166534] shrink-0">
              7 Hari
            </span>
          </div>

          <div className="pt-4">
            <div className="grid grid-cols-7 gap-1.5 sm:gap-2.5 items-end h-44 sm:h-52 border-b border-[#B7D6C3] pb-2">
              {dash.last7Days.map((d, i) => (
                <div key={i} className="flex flex-col items-center justify-end h-full gap-1.5 min-w-0">
                  <span className="text-[9.5px] sm:text-[11px] font-bold text-[#1C1917] tabular-nums whitespace-nowrap">
                    {d.inc > 0 ? formatCompactIDR(d.inc) : '0'}
                  </span>
                  <div
                    title={`${d.label}: ${formatIDR(d.inc)}`}
                    style={{
                      height: chartReady ? `${Math.max(4, (d.inc / maxBar) * 78)}%` : '0%',
                      transition: 'height 620ms cubic-bezier(0.22, 1, 0.36, 1)',
                      transitionDelay: `${i * 55}ms`,
                    }}
                    className={`w-full max-w-[40px] rounded-t-lg ${
                      d.isToday ? 'bg-[#15803D]' : 'bg-[#86EFAC]'
                    }`}
                  />
                </div>
              ))}
            </div>
            <div className="grid grid-cols-7 gap-1.5 sm:gap-2.5 mt-2 text-center">
              {dash.last7Days.map((d, i) => (
                <span
                  key={i}
                  className={`text-[10px] sm:text-[11.5px] font-semibold ${
                    d.isToday ? 'text-[#166534] font-bold' : 'text-[#57534E]'
                  }`}
                >
                  {d.label}
                </span>
              ))}
            </div>
          </div>

          <div className="mt-3.5 pt-3 border-t border-dashed border-[#B7D6C3] flex flex-wrap justify-between gap-2 text-xs text-[#57534E]">
            <span>
              Total 7 Hari:{' '}
              <strong className="text-[#1C1917] tabular-nums">
                {formatIDR(dash.total7Days)}
              </strong>
            </span>
            <span>
              Rata-rata / Hari:{' '}
              <strong className="text-[#1C1917] tabular-nums">
                {formatIDR(dash.avg7Days)}
              </strong>
            </span>
          </div>
        </div>

        {/* Right Stack: Transaksi Terbaru ABOVE Pengingat Penting */}
        <div className="lg:col-span-5 space-y-4 min-w-0">
          {/* 1. Transaksi Terbaru */}
          <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-4 sm:p-5 shadow-xs">
            <div className="flex items-center justify-between mb-3 gap-2">
              <div>
                <h3 className="font-bold text-sm text-[#1C1917]">
                  Transaksi Terbaru
                </h3>
                <p className="text-[11px] text-[#57534E]">
                  {dash.recentSales.length} penjualan terakhir
                </p>
              </div>
              <button
                type="button"
                onClick={() => onNavigate('riwayat')}
                className="min-h-[44px] px-2 -mr-2 text-xs font-bold text-[#166534] hover:underline shrink-0"
              >
                Lihat Semua
              </button>
            </div>
            {dash.recentSales.length === 0 ? (
              <p className="text-xs text-[#57534E] py-4 text-center">
                Belum ada transaksi.
              </p>
            ) : (
              <div className="space-y-2">
                {dash.recentSales.map((s) => {
                  const itemCount = dash.itemsCountBySale.get(s.id) || 0;
                  const payTone =
                    s.paymentMethod === 'QRIS'
                      ? 'bg-[#E0F2FE] text-[#0369A1]'
                      : s.paymentMethod === 'Transfer'
                      ? 'bg-[#FEF3C7] text-[#92400E]'
                      : 'bg-[#DCFCE7] text-[#166534]';
                  return (
                    <button
                      type="button"
                      key={s.id}
                      onClick={() => onNavigate('riwayat')}
                      className="w-full text-left cursor-pointer rounded-xl bg-[#DCEBE2] border border-[#B7D6C3] px-3 py-2.5 flex items-center justify-between gap-2.5 hover:border-[#15803D] transition"
                    >
                      <div className="min-w-0">
                        <p className="font-bold text-xs text-[#1C1917] truncate">
                          {s.invoiceNo}
                        </p>
                        <div className="flex items-center gap-1.5 mt-0.5 text-[11px] text-[#57534E] flex-wrap">
                          <span>{formatDateTimeID(s.createdAt)}</span>
                          <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${payTone}`}>
                            {s.paymentMethod === 'Tunai' ? 'Cash' : s.paymentMethod}
                          </span>
                          {itemCount > 0 && <span>• {itemCount} item</span>}
                        </div>
                      </div>
                      <span className="font-extrabold text-xs sm:text-sm text-[#1C1917] tabular-nums shrink-0">
                        {formatIDR(s.total)}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* 2. Pengingat Penting */}
          <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-4 sm:p-5 shadow-xs">
            <div className="flex items-center justify-between mb-3 gap-2">
              <div>
                <h3 className="font-bold text-sm text-[#1C1917]">
                  Pengingat Penting
                </h3>
                <p className="text-[11px] text-[#57534E]">
                  Stok menipis, kadaluarsa &amp; tagihan
                </p>
              </div>
              <span
                className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${
                  totalAlerts > 0
                    ? 'bg-[#FEE2E2] text-[#BA1A1A]'
                    : 'bg-[#DCFCE7] text-[#166534]'
                }`}
              >
                {totalAlerts} info
              </span>
            </div>

            {totalAlerts === 0 ? (
              <div className="alert-row-info rounded-xl px-3 py-2.5 flex items-center gap-2 text-xs font-semibold">
                <CheckCircle2 className="w-4 h-4 shrink-0" aria-hidden="true" />
                <span>Semua stok, ED, dan faktur dalam kondisi aman.</span>
              </div>
            ) : (
              <div className="space-y-2">
                {dash.expiringCount > 0 && (
                  <button
                    type="button"
                    onClick={() => onNavigate('pengingat')}
                    className="w-full min-h-[44px] text-left alert-row-danger rounded-xl px-3 py-2.5 flex items-center justify-between gap-2 text-xs font-semibold transition hover:opacity-90"
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
                      <span className="truncate">
                        {dash.expiringCount} obat kadaluarsa / ED &le; 30 hari
                      </span>
                    </span>
                    <span className="font-extrabold shrink-0">Cek</span>
                  </button>
                )}
                {dash.lowStockCount > 0 && (
                  <button
                    type="button"
                    onClick={() => onNavigate('pengingat')}
                    className="w-full min-h-[44px] text-left alert-row-warn rounded-xl px-3 py-2.5 flex items-center justify-between gap-2 text-xs font-semibold transition hover:opacity-90"
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      <Package className="w-4 h-4 shrink-0" aria-hidden="true" />
                      <span className="truncate">
                        {dash.lowStockCount} obat stok menipis / habis (&lt; 10)
                      </span>
                    </span>
                    <span className="font-extrabold shrink-0">Cek</span>
                  </button>
                )}
                {dash.creditPbfCount > 0 && (
                  <button
                    type="button"
                    onClick={() => onNavigate('pbf')}
                    className="w-full min-h-[44px] text-left alert-row-info rounded-xl px-3 py-2.5 flex items-center justify-between gap-2 text-xs font-semibold transition hover:opacity-90"
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      <TrendingUp className="w-4 h-4 shrink-0" aria-hidden="true" />
                      <span className="truncate">
                        {dash.creditPbfCount} faktur PBF kredit belum lunas
                      </span>
                    </span>
                    <span className="font-extrabold shrink-0">Lihat</span>
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

// ============================================================================
// 2. STOK OBAT SCREEN (Matches StokScreen.kt + CSV Import/Export)
// ============================================================================
export const StokScreen: React.FC = () => {
  const { medicines, saveMedicine, deleteMedicine, bulkImportMedicines } = useStore();
  const { notify, askConfirm } = useFeedback();
  const [search, setSearch] = useState('');
  const [catFilter, setCatFilter] = useState('Semua');
  const [stockFilter, setStockFilter] = useState<'ALL' | 'LOW' | 'EXP'>('ALL');
  const [sortAz, setSortAz] = useState(true);
  const deferredSearch = useDeferredValue(search);
  const isMdUp = useIsMdUp();
  const desktopScrollRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const [editing, setEditing] = useState<Medicine | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!showModal) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setShowModal(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [showModal]);

  // Form state
  const [name, setName] = useState('');
  const [category, setCategory] = useState('Obat Bebas');
  const [medicineType, setMedicineType] = useState('');
  const [unit, setUnit] = useState('Strip');
  const [buyPrice, setBuyPrice] = useState('');
  const [sellPrice, setSellPrice] = useState('');
  const [wholesalePrice, setWholesalePrice] = useState('');
  const [stock, setStock] = useState('');
  const [expiryDate, setExpiryDate] = useState('');

  const openAdd = () => {
    setEditing(null);
    setName('');
    setCategory('Obat Bebas');
    setMedicineType('');
    setUnit('Strip');
    setBuyPrice('');
    setSellPrice('');
    setWholesalePrice('');
    setStock('0');
    setExpiryDate('');
    setShowModal(true);
  };

  const openEdit = (m: Medicine) => {
    setEditing(m);
    setName(m.name);
    setCategory(m.category);
    setMedicineType(m.medicineType || '');
    setUnit(m.unit);
    setBuyPrice(String(m.buyPrice || ''));
    setSellPrice(String(m.sellPrice || ''));
    setWholesalePrice(String(m.wholesalePrice || ''));
    setStock(String(m.stock));
    setExpiryDate(m.expiryDate || '');
    setShowModal(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    try {
      saveMedicine(
        {
          name,
          category,
          medicineType: medicineType.trim(),
          unit,
          buyPrice: parseInt(buyPrice, 10) || 0,
          sellPrice: parseInt(sellPrice, 10) || 0,
          wholesalePrice: parseInt(wholesalePrice, 10) || 0,
          stock: parseInt(stock, 10) || 0,
          expiryDate: expiryDate.trim() || null,
          barcode: editing?.barcode ?? null,
        },
        editing?.id
      );
      setShowModal(false);
      setMsg(editing ? 'Obat berhasil diperbarui' : 'Obat baru berhasil ditambahkan');
    } catch (err: any) {
      notify(err.message, 'error');
    }
  };

  const exportMedicineSheet = (
    filename: string,
    title: string,
    subtitle: string,
    rows: Record<string, string | number>[]
  ) => {
    exportRowsToXlsx(filename, 'Stok Obat', rows, {
      title,
      subtitle,
      currencyColumns: ['Harga Beli', 'Harga Jual Ecer', 'Harga Grosir', 'Nilai Stok (Rp)'],
      sumColumns: ['Stok', 'Nilai Stok (Rp)'],
    });
  };

  const handleExportExcel = () => {
    const rows = medicines.map((m) => ({
      'Nama Obat': m.name,
      Golongan: m.category,
      'Jenis Obat': m.medicineType || '',
      Satuan: m.unit,
      'Harga Beli': m.buyPrice,
      'Harga Jual Ecer': m.sellPrice,
      'Harga Grosir': m.wholesalePrice,
      Stok: m.stock,
      'Kadaluarsa (YYYY-MM-DD)': m.expiryDate || '',
      Barcode: m.barcode || '',
      'Nilai Stok (Rp)': m.buyPrice * m.stock,
    }));
    exportMedicineSheet(
      `stok-apotek-lestari-${dayKeyID()}.xlsx`,
      'APOTEK LESTARI - LAPORAN STOK OBAT',
      `Dicetak: ${new Date().toLocaleString('id-ID')} | Total SKU: ${rows.length}`,
      rows
    );
  };

  const handleDownloadTemplate = () => {
    exportMedicineTemplateXlsx(
      'template-stok-obat-apotek-lestari.xlsx',
      CATEGORIES,
      UNITS,
      MEDICINE_TYPES
    );
  };

  const handleImportSpreadsheet = async (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const matrix = await readSpreadsheetMatrix(file);
      const { rows, skippedInvalid } = parseMedicineSpreadsheet(matrix);
      const res = bulkImportMedicines(rows);
      setMsg(
        `Impor selesai: ${res.inserted} baru, ${res.updated} diperbarui, ${res.skipped + skippedInvalid} dilewati.`
      );
    } catch (err: any) {
      notify('Gagal membaca Excel/CSV: ' + err.message, 'error');
    } finally {
      e.target.value = '';
    }
  };

  const categoryOptions = useMemo(
    () => [...new Set([...CATEGORIES, ...medicines.map((m) => m.category).filter(Boolean)])],
    [medicines]
  );

  const filtered = useMemo(() => {
    const preFiltered = medicines.filter((m) => {
      if (catFilter !== 'Semua' && m.category !== catFilter) return false;
      if (stockFilter === 'LOW' && m.stock >= 10) return false;
      if (stockFilter === 'EXP') {
        const days = daysUntilExpiry(m.expiryDate);
        if (days === null || days > 30) return false;
      }
      return true;
    });
    return filterAndRankMedicines(preFiltered, deferredSearch, { sortAz });
  }, [medicines, deferredSearch, catFilter, stockFilter, sortAz]);

  const stokPager = useProgressiveList(filtered, {
    initial: 60,
    step: 60,
    resetKey: `${deferredSearch.trim().toLowerCase()}|${catFilter}|${stockFilter}|${sortAz}|${isMdUp ? 'd' : 'm'}`,
    scrollContainerRef: isMdUp ? desktopScrollRef : undefined,
  });

  return (
    <div>
      <PageHeader
        title="Stok Obat"
        subtitle={`${medicines.length} jenis obat terdaftar • Tersimpan lokal & cloud Web (tukar ke Android lewat JSON/Excel)`}
        action={
          <>
            <button
              type="button"
              onClick={handleDownloadTemplate}
              className="min-h-[44px] px-3 py-2 rounded-xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-[#1C1917] text-xs font-bold flex items-center gap-1.5 transition"
            >
              <Download className="w-4 h-4" aria-hidden="true" /> Template Excel
            </button>
            <label className="min-h-[44px] cursor-pointer px-3 py-2 rounded-xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-[#1C1917] text-xs font-bold flex items-center gap-1.5 transition">
              <Upload className="w-4 h-4" aria-hidden="true" /> Impor Excel/CSV
              <input
                type="file"
                accept=".xlsx,.xls,.csv"
                onChange={handleImportSpreadsheet}
                className="hidden"
              />
            </label>
            <button
              type="button"
              onClick={handleExportExcel}
              className="min-h-[44px] px-3 py-2 rounded-xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-[#1C1917] text-xs font-bold flex items-center gap-1.5 transition"
            >
              <Download className="w-4 h-4" aria-hidden="true" /> Ekspor Excel
            </button>
            <button
              type="button"
              onClick={openAdd}
              className="min-h-[44px] px-4 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold flex items-center gap-1.5 transition"
            >
              <Plus className="w-4 h-4" aria-hidden="true" /> Tambah Obat
            </button>
          </>
        }
      />

      {msg && (
        <div className="mb-4 p-3 rounded-2xl bg-[#DCFCE7] border border-[#B7D6C3] text-sm text-[#1C1917] flex items-center justify-between gap-2">
          <span>{msg}</span>
          <button
            type="button"
            onClick={() => setMsg(null)}
            className="min-h-[44px] px-3 text-xs font-bold text-[#166534] hover:underline shrink-0"
          >
            Tutup
          </button>
        </div>
      )}

      {/* Search & Filter Controls */}
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <div className="relative w-full min-w-0 sm:flex-1 sm:min-w-[220px]">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none text-[#57534E]" aria-hidden="true" />
          <input
            ref={searchInputRef}
            type="text"
            aria-label="Cari nama obat, golongan, atau jenis obat"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape' && search) {
                e.preventDefault();
                setSearch('');
              }
            }}
            placeholder="Cari nama obat (mis. para 500), golongan, atau jenis..."
            className="w-full min-h-[44px] pl-10 pr-12 py-2.5 rounded-2xl bg-[#FAF8F4] border border-[#B7D6C3] text-sm text-[#1C1917] placeholder:text-[#57534E] focus:outline-none focus:ring-2 focus:ring-[#15803D]"
          />
          {search && (
            <button
              type="button"
              onClick={() => {
                setSearch('');
                searchInputRef.current?.focus();
              }}
              aria-label="Hapus pencarian"
              className="absolute right-1.5 top-1/2 -translate-y-1/2 min-w-[36px] min-h-[36px] flex items-center justify-center rounded-xl text-[#57534E] hover:text-[#1C1917] hover:bg-[#DCEBE2]"
            >
              <X className="w-4 h-4" aria-hidden="true" />
            </button>
          )}
        </div>
        <select
          aria-label="Filter golongan obat"
          value={catFilter}
          onChange={(e) => setCatFilter(e.target.value)}
          className="min-h-[44px] px-3.5 py-2.5 rounded-2xl bg-[#DCEBE2] border border-[#B7D6C3] text-sm font-medium text-[#1C1917]"
        >
          <option value="Semua">Semua Golongan</option>
          {categoryOptions.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <div
          className="flex flex-wrap rounded-2xl bg-[#DCEBE2] border border-[#B7D6C3] p-1 text-xs font-bold gap-1"
          role="group"
          aria-label="Filter status stok"
        >
          {(
            [
              ['ALL', 'Semua'],
              ['LOW', 'Stok < 10'],
              ['EXP', 'Kadaluarsa <=30h'],
            ] as const
          ).map(([k, lbl]) => (
            <button
              type="button"
              key={k}
              onClick={() => setStockFilter(k)}
              className={`min-h-[38px] px-3 py-1.5 rounded-xl transition ${
                stockFilter === k ? 'bg-[#15803D] text-white' : 'text-[#57534E] hover:text-[#1C1917]'
              }`}
            >
              {lbl}
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={() => setSortAz(!sortAz)}
          className="min-h-[44px] px-3.5 py-2.5 rounded-2xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-xs font-bold text-[#1C1917]"
        >
          Urut: {sortAz ? 'A-Z' : 'Z-A'}
        </button>
      </div>

      {/* Mobile Card List OR Desktop Sticky-Header Table (single active mount) */}
      {!isMdUp ? (
        <div className="space-y-2.5">
          {filtered.length === 0 ? (
            <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] py-10 text-center text-[#57534E] text-sm">
              Tidak ada data obat yang cocok.
            </div>
          ) : (
            <>
              {stokPager.visibleItems.map((m) => {
                const expDays = daysUntilExpiry(m.expiryDate);
                return (
                  <div
                    key={m.id}
                    className="cv-card rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-3.5 space-y-2.5 shadow-xs"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="font-bold text-sm text-[#1C1917] leading-snug break-words">{m.name}</p>
                        <p className="text-xs text-[#57534E] mt-0.5">
                          {m.category} • {m.unit}
                          {m.medicineType ? ` • Jenis: ${m.medicineType}` : ''}
                        </p>
                      </div>
                      <StockPill stock={m.stock} />
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-[#B7D6C3] text-xs">
                      <div className="space-y-0.5">
                        <p className="font-extrabold text-sm text-[#166534]">
                          Ecer: {m.sellPrice > 0 ? formatIDR(m.sellPrice) : 'Belum diisi'}
                        </p>
                        <p className="text-[#57534E]">
                          Beli: {m.buyPrice > 0 ? formatIDR(m.buyPrice) : '-'}
                          {m.wholesalePrice > 0 ? ` • Grosir: ${formatIDR(m.wholesalePrice)}` : ''}
                        </p>
                        {m.expiryDate && (
                          <p
                            className={
                              expDays !== null && expDays <= 0
                                ? 'text-[#BA1A1A] font-bold'
                                : expDays !== null && expDays <= 30
                                ? 'text-[#92400E] font-bold'
                                : 'text-[#57534E]'
                            }
                          >
                            ED: {formatDateID(m.expiryDate)}
                            {expDays !== null && expDays <= 30 && ` (${expDays}h)`}
                          </p>
                        )}
                      </div>
                      <div className="flex items-center gap-1.5 shrink-0">
                        <button
                          type="button"
                          onClick={() => openEdit(m)}
                          className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl bg-[#DCEBE2] border border-[#B7D6C3] hover:bg-[#D0E3D8] text-[#1C1917]"
                          title="Edit Obat"
                          aria-label={`Edit ${m.name}`}
                        >
                          <Edit2 className="w-4 h-4" aria-hidden="true" />
                        </button>
                        <button
                          type="button"
                          onClick={() =>
                            askConfirm(
                              `Hapus obat "${m.name}"?`,
                              () => {
                                deleteMedicine(m.id);
                                notify(`Obat "${m.name}" dihapus`, 'info');
                              },
                              { title: 'Hapus Obat', danger: true, confirmLabel: 'Hapus' }
                            )
                          }
                          className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl bg-[#FEE2E2] text-[#BA1A1A] hover:bg-[#FECACA]"
                          title="Hapus Obat"
                          aria-label={`Hapus ${m.name}`}
                        >
                          <Trash2 className="w-4 h-4" aria-hidden="true" />
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
              <ListPaginationFooter
                shown={stokPager.shownCount}
                total={stokPager.total}
                remaining={stokPager.remaining}
                step={stokPager.step}
                itemLabel="obat"
                onLoadMore={stokPager.loadMore}
                sentinelRef={stokPager.sentinelRef}
                className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3]"
              />
            </>
          )}
        </div>
      ) : (
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] shadow-xs overflow-hidden">
          <div
            ref={desktopScrollRef}
            className="overflow-auto max-h-[max(260px,calc(100dvh-14.5rem))] overscroll-contain"
          >
            <table className="w-full text-left text-sm">
              <thead className="sticky top-0 z-10 bg-[#DCEBE2] shadow-[inset_0_-1px_0_#B7D6C3] text-xs uppercase text-[#1C1917]">
                <tr>
                  <th className="py-3.5 px-4">Nama Obat</th>
                  <th className="py-3.5 px-3">Golongan / Satuan</th>
                  <th className="py-3.5 px-3">Jenis Obat</th>
                  <th className="py-3.5 px-3 text-right">Harga Beli</th>
                  <th className="py-3.5 px-3 text-right">Harga Ecer</th>
                  <th className="py-3.5 px-3 text-right">Harga Grosir</th>
                  <th className="py-3.5 px-3">Status Stok</th>
                  <th className="py-3.5 px-3">Kadaluarsa</th>
                  <th className="py-3.5 px-4 text-right">Aksi</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#B7D6C3]">
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="py-10 text-center text-[#57534E]">
                      Tidak ada data obat yang cocok.
                    </td>
                  </tr>
                ) : (
                  stokPager.visibleItems.map((m) => {
                    const expDays = daysUntilExpiry(m.expiryDate);
                    return (
                      <tr
                        key={m.id}
                        className="hover:bg-[#DCEBE2]/60 transition"
                      >
                        <td className="py-3 px-4 font-bold text-[#1C1917]">
                          <div>{m.name}</div>
                          {m.barcode && (
                            <div className="text-[11px] font-normal text-[#57534E]">
                              Barcode: {m.barcode}
                            </div>
                          )}
                        </td>
                        <td className="py-3 px-3 text-xs text-[#1C1917]">
                          <span className="font-semibold">{m.category}</span> • {m.unit}
                        </td>
                        <td className="py-3 px-3 text-xs text-[#1C1917]">
                          {m.medicineType || '-'}
                        </td>
                        <td className="py-3 px-3 text-right text-[#57534E] whitespace-nowrap">
                          {m.buyPrice > 0 ? formatIDR(m.buyPrice) : '-'}
                        </td>
                        <td className="py-3 px-3 text-right font-bold text-[#166534] whitespace-nowrap">
                          {m.sellPrice > 0 ? formatIDR(m.sellPrice) : '-'}
                        </td>
                        <td className="py-3 px-3 text-right text-[#57534E] whitespace-nowrap">
                          {m.wholesalePrice > 0 ? formatIDR(m.wholesalePrice) : '-'}
                        </td>
                        <td className="py-3 px-3 whitespace-nowrap">
                          <StockPill stock={m.stock} />
                        </td>
                        <td className="py-3 px-3 text-xs whitespace-nowrap">
                          {m.expiryDate ? (
                            <span
                              className={
                                expDays !== null && expDays <= 0
                                  ? 'text-[#BA1A1A] font-bold'
                                  : expDays !== null && expDays <= 30
                                  ? 'text-[#92400E] font-bold'
                                  : 'text-[#1C1917]'
                              }
                            >
                              {formatDateID(m.expiryDate)}
                              {expDays !== null && expDays <= 30 && ` (${expDays}h)`}
                            </span>
                          ) : (
                            '-'
                          )}
                        </td>
                        <td className="py-3 px-4 text-right">
                          <div className="inline-flex items-center gap-1.5">
                            <button
                              type="button"
                              onClick={() => openEdit(m)}
                              className="min-w-[44px] min-h-[44px] inline-flex items-center justify-center rounded-xl hover:bg-[#DCEBE2] text-[#1C1917]"
                              title="Edit Obat"
                              aria-label={`Edit ${m.name}`}
                            >
                              <Edit2 className="w-4 h-4" aria-hidden="true" />
                            </button>
                            <button
                              type="button"
                              onClick={() =>
                                askConfirm(
                                  `Hapus obat "${m.name}"?`,
                                  () => {
                                    deleteMedicine(m.id);
                                    notify(`Obat "${m.name}" dihapus`, 'info');
                                  },
                                  { title: 'Hapus Obat', danger: true, confirmLabel: 'Hapus' }
                                )
                              }
                              className="min-w-[44px] min-h-[44px] inline-flex items-center justify-center rounded-xl text-[#BA1A1A] hover:bg-red-500/10"
                              title="Hapus Obat"
                              aria-label={`Hapus ${m.name}`}
                            >
                              <Trash2 className="w-4 h-4" aria-hidden="true" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
            <ListPaginationFooter
              shown={stokPager.shownCount}
              total={stokPager.total}
              remaining={stokPager.remaining}
              step={stokPager.step}
              itemLabel="obat"
              onLoadMore={stokPager.loadMore}
              sentinelRef={stokPager.sentinelRef}
              className="sticky left-0 border-t border-[#B7D6C3] bg-[#DCEBE2]/55"
            />
          </div>
        </div>
      )}

      {/* Add/Edit Medicine Modal */}
      {showModal && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="med-modal-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowModal(false);
          }}
          className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4"
        >
          <div className="bg-[#F6F4EF] border border-[#B7D6C3] rounded-2xl max-w-lg w-full p-6 shadow-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 id="med-modal-title" className="text-lg font-extrabold text-[#1C1917]">
                {editing ? 'Ubah Data Obat' : 'Tambah Obat Baru'}
              </h3>
              <button
                type="button"
                onClick={() => setShowModal(false)}
                aria-label="Tutup modal obat"
                className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl hover:bg-black/5"
              >
                <X className="w-5 h-5" aria-hidden="true" />
              </button>
            </div>
            <form onSubmit={handleSave} className="space-y-3.5 text-sm">
              <div>
                <label htmlFor="med-name" className="block text-xs font-bold text-[#1C1917] mb-1">
                  Nama Obat *
                </label>
                <input
                  id="med-name"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
                  placeholder="Contoh: Paracetamol 500mg"
                />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="min-w-0">
                  <label htmlFor="med-cat" className="block text-xs font-bold text-[#1C1917] mb-1">
                    Golongan
                  </label>
                  <select
                    id="med-cat"
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="w-full min-w-0 min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
                  >
                    {category && !CATEGORIES.includes(category) && (
                      <option value={category}>{category}</option>
                    )}
                    {CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="min-w-0">
                  <label htmlFor="med-type" className="block text-xs font-bold text-[#1C1917] mb-1">
                    Jenis Obat
                  </label>
                  <select
                    id="med-type"
                    value={medicineType}
                    onChange={(e) => setMedicineType(e.target.value)}
                    className="w-full min-w-0 min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
                  >
                    <option value="">Pilih Jenis Obat</option>
                    {medicineType && !MEDICINE_TYPES.includes(medicineType) && (
                      <option value={medicineType}>{medicineType}</option>
                    )}
                    {MEDICINE_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="min-w-0">
                  <label htmlFor="med-unit" className="block text-xs font-bold text-[#1C1917] mb-1">
                    Satuan
                  </label>
                  <select
                    id="med-unit"
                    value={unit}
                    onChange={(e) => setUnit(e.target.value)}
                    className="w-full min-w-0 min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
                  >
                    {UNITS.map((u) => (
                      <option key={u} value={u}>
                        {u}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="min-w-0">
                  <label htmlFor="med-buy" className="block text-xs font-bold text-[#1C1917] mb-1">
                    Harga Beli (HPP)
                  </label>
                  <input
                    id="med-buy"
                    type="number"
                    min="0"
                    value={buyPrice}
                    onChange={(e) => setBuyPrice(e.target.value)}
                    className="w-full min-w-0 min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
                    placeholder="0"
                  />
                </div>
                <div className="min-w-0">
                  <label htmlFor="med-sell" className="block text-xs font-bold text-[#1C1917] mb-1">
                    Harga Jual Ecer
                  </label>
                  <input
                    id="med-sell"
                    type="number"
                    min="0"
                    value={sellPrice}
                    onChange={(e) => setSellPrice(e.target.value)}
                    className="w-full min-w-0 min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
                    placeholder="0"
                  />
                </div>
                <div className="min-w-0">
                  <label htmlFor="med-wholesale" className="block text-xs font-bold text-[#1C1917] mb-1">
                    Harga Grosir
                  </label>
                  <input
                    id="med-wholesale"
                    type="number"
                    min="0"
                    value={wholesalePrice}
                    onChange={(e) => setWholesalePrice(e.target.value)}
                    className="w-full min-w-0 min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
                    placeholder="0"
                  />
                </div>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="min-w-0">
                  <label htmlFor="med-stock" className="block text-xs font-bold text-[#1C1917] mb-1">
                    Stok *
                  </label>
                  <input
                    id="med-stock"
                    required
                    type="number"
                    min="0"
                    value={stock}
                    onChange={(e) => setStock(e.target.value)}
                    className="w-full min-w-0 min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
                  />
                </div>
                <div className="min-w-0">
                  <label htmlFor="med-exp" className="block text-xs font-bold text-[#1C1917] mb-1">
                    Tgl Kadaluarsa
                  </label>
                  <input
                    id="med-exp"
                    type="date"
                    value={expiryDate}
                    onChange={(e) => setExpiryDate(e.target.value)}
                    className="w-full min-w-0 min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="min-h-[44px] px-4 py-2.5 rounded-xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-[#1C1917] font-bold"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="min-h-[44px] px-5 py-2.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white font-bold"
                >
                  Simpan Obat
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

// ============================================================================
// 3. KASIR POS SCREEN + THERMAL RECEIPT PRINT (Matches KasirScreen.kt)
// ============================================================================
export const ReceiptModal: React.FC<{
  sale: Sale;
  items: SaleItem[];
  onClose: () => void;
}> = ({ sale, items, onClose }) => {
  const [paperWidth, setPaperWidth] = useState<'58' | '80'>(() =>
    localStorage.getItem('apotek_receipt_paper') === '58' ? '58' : '80'
  );

  const selectPaper = (w: '58' | '80') => {
    setPaperWidth(w);
    localStorage.setItem('apotek_receipt_paper', w);
  };

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return createPortal(
    <div
      id="receipt-print-portal"
      role="dialog"
      aria-modal="true"
      aria-label="Struk Pembayaran"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4"
    >
      <style>{`@media print { @page { size: ${paperWidth}mm auto; margin: 0; } }`}</style>
      <div className="bg-white text-black rounded-3xl max-w-sm w-full p-6 shadow-2xl max-h-[92vh] overflow-y-auto">
        <div
          id="printable-receipt"
          data-paper={paperWidth}
          className={`font-mono space-y-2 ${
            paperWidth === '58' ? 'max-w-[220px] mx-auto text-[10px]' : 'text-xs'
          }`}
        >
          <div className="text-center border-b border-dashed border-gray-400 pb-2">
            <img
              src="/logo-512.jpg"
              alt="Logo Apotek Lestari"
              width={paperWidth === '58' ? 40 : 48}
              height={paperWidth === '58' ? 40 : 48}
              className={`mx-auto mb-1 object-contain grayscale contrast-125 ${
                paperWidth === '58' ? 'w-10 h-10' : 'w-12 h-12'
              }`}
            />
            <h2
              className={`font-extrabold tracking-wide ${
                paperWidth === '58' ? 'text-sm' : 'text-base'
              }`}
            >
              APOTEK LESTARI
            </h2>
            <p className="text-[11px]">Struk Pembayaran Resmi</p>
            <p className="text-[11px]">{formatDateTimeID(sale.createdAt)} WIB</p>
            <p className="text-[11px] font-bold">{sale.invoiceNo}</p>
          </div>
          <div className="space-y-1.5 border-b border-dashed border-gray-400 pb-2">
            {items.map((it, idx) => (
              <div key={idx}>
                <div className="font-bold">
                  {it.medicineName}{' '}
                  {it.priceType === 'GROSIR' && (
                    <span className="text-[10px] font-normal">(GROSIR)</span>
                  )}
                </div>
                <div className="flex justify-between">
                  <span>
                    {it.qty} {it.unit} x {formatIDR(it.price)}
                  </span>
                  <span className="font-bold">{formatIDR(it.subtotal)}</span>
                </div>
              </div>
            ))}
          </div>
          <div className="space-y-1">
            <div
              className={`flex justify-between font-bold ${
                paperWidth === '58' ? 'text-xs' : 'text-sm'
              }`}
            >
              <span>TOTAL</span>
              <span>{formatIDR(sale.total)}</span>
            </div>
            <div className="flex justify-between">
              <span>Metode Bayar</span>
              <span>{sale.paymentMethod}</span>
            </div>
            <div className="flex justify-between">
              <span>Dibayar</span>
              <span>{formatIDR(sale.amountPaid)}</span>
            </div>
            <div className="flex justify-between font-bold">
              <span>Kembalian</span>
              <span>{formatIDR(sale.changeAmount)}</span>
            </div>
          </div>
          <div className="text-center border-t border-dashed border-gray-400 pt-2 text-[11px]">
            Terima kasih, semoga lekas sembuh!
          </div>
        </div>

        <div className="no-print mt-4 space-y-2.5">
          <div className="flex items-center justify-between gap-2 text-xs">
            <span className="font-semibold text-gray-600">Ukuran Kertas:</span>
            <div className="flex rounded-lg bg-gray-100 p-0.5 font-bold">
              <button
                type="button"
                onClick={() => selectPaper('58')}
                className={`min-h-[44px] px-3.5 py-1.5 rounded-md ${
                  paperWidth === '58'
                    ? 'bg-[#15803D] text-white'
                    : 'text-gray-700'
                }`}
              >
                58mm
              </button>
              <button
                type="button"
                onClick={() => selectPaper('80')}
                className={`min-h-[44px] px-3.5 py-1.5 rounded-md ${
                  paperWidth === '80'
                    ? 'bg-[#15803D] text-white'
                    : 'text-gray-700'
                }`}
              >
                80mm
              </button>
            </div>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => window.print()}
              className="flex-1 min-h-[44px] py-2.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white font-bold text-sm flex items-center justify-center gap-2"
            >
              <Printer className="w-4 h-4" aria-hidden="true" /> Cetak Struk
            </button>
            <button
              type="button"
              onClick={onClose}
              className="min-h-[44px] px-4 py-2.5 rounded-xl bg-gray-200 hover:bg-gray-300 text-gray-800 font-bold text-sm"
            >
              Selesai
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
};

const formatThousands = (digits: string) =>
  digits ? digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.') : '';

export const KasirScreen: React.FC = () => {
  const { medicines, createSale } = useStore();
  const { notify } = useFeedback();
  const searchInputRef = useRef<HTMLInputElement>(null);
  const pickerScrollRef = useRef<HTMLDivElement>(null);
  const [search, setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);
  const [cart, setCart] = useState<
    Array<{ medicineId: number; qty: number; useWholesale: boolean }>
  >([]);
  const [paymentMethod, setPaymentMethod] = useState<'Tunai' | 'QRIS' | 'Transfer'>(
    'Tunai'
  );
  const [amountPaidStr, setAmountPaidStr] = useState('');
  const [checkingOut, setCheckingOut] = useState(false);
  const checkingOutRef = useRef(false);
  const [lastReceipt, setLastReceipt] = useState<{
    sale: Sale;
    items: SaleItem[];
  } | null>(null);

  const medById = useMemo(
    () => new Map(medicines.map((m) => [m.id, m])),
    [medicines]
  );

  const filteredMeds = useMemo(
    () =>
      filterAndRankMedicines(medicines, deferredSearch, {
        prioritizeSellable: true,
      }),
    [medicines, deferredSearch]
  );

  const kasirPager = useProgressiveList(filteredMeds, {
    initial: 60,
    step: 60,
    resetKey: deferredSearch.trim().toLowerCase(),
    scrollContainerRef: pickerScrollRef,
  });

  const addToCart = (m: Medicine) => {
    if (m.stock <= 0) return;
    if (m.sellPrice <= 0) {
      notify(`Harga jual ${m.name} belum diatur`, 'error');
      return;
    }
    setCart((prev) => {
      const existing = prev.find((c) => c.medicineId === m.id);
      if (existing) {
        if (existing.qty >= m.stock) return prev;
        return prev.map((c) =>
          c.medicineId === m.id ? { ...c, qty: c.qty + 1 } : c
        );
      }
      return [...prev, { medicineId: m.id, qty: 1, useWholesale: false }];
    });
  };

  const updateQty = (medId: number, qty: number) => {
    const med = medById.get(medId);
    if (!med) return;
    if (qty <= 0) {
      setCart((prev) => prev.filter((c) => c.medicineId !== medId));
    } else if (qty <= med.stock) {
      setCart((prev) =>
        prev.map((c) => (c.medicineId === medId ? { ...c, qty } : c))
      );
    }
  };

  const toggleWholesale = (medId: number) => {
    setCart((prev) =>
      prev.map((c) =>
        c.medicineId === medId ? { ...c, useWholesale: !c.useWholesale } : c
      )
    );
  };

  const cartQtyById = useMemo(
    () => new Map(cart.map((c) => [c.medicineId, c.qty])),
    [cart]
  );

  const cartItemCount = useMemo(
    () => cart.reduce((a, c) => a + c.qty, 0),
    [cart]
  );

  const cartTotal = useMemo(() => {
    return cart.reduce((acc, c) => {
      const m = medById.get(c.medicineId);
      if (!m) return acc;
      const price =
        c.useWholesale && m.wholesalePrice > 0 ? m.wholesalePrice : m.sellPrice;
      return acc + price * c.qty;
    }, 0);
  }, [cart, medById]);

  const paidNum =
    paymentMethod !== 'Tunai'
      ? cartTotal
      : parseInt(amountPaidStr.replace(/\D/g, ''), 10) || 0;
  const changeAmount = Math.max(0, paidNum - cartTotal);

  const handleCheckout = async () => {
    if (checkingOutRef.current) return;
    checkingOutRef.current = true;
    setCheckingOut(true);
    try {
      const finalPaid = paymentMethod !== 'Tunai' ? cartTotal : paidNum;
      const res = await createSale(cart, paymentMethod, finalPaid);
      setCart([]);
      setAmountPaidStr('');
      setLastReceipt(res);
      notify('Transaksi berhasil disimpan', 'success');
    } catch (err: any) {
      notify(err?.message || 'Gagal memproses transaksi', 'error');
    } finally {
      checkingOutRef.current = false;
      setCheckingOut(false);
    }
  };

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'F9') {
        e.preventDefault();
        if (!checkingOutRef.current && !lastReceipt && cart.length > 0 && paidNum >= cartTotal) {
          void handleCheckout();
        }
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [cart, paidNum, cartTotal, paymentMethod, lastReceipt]);

  return (
    <div className={cart.length > 0 ? 'pb-24 lg:pb-0' : ''}>
      <PageHeader
        title="Kasir"
        subtitle="Pilih obat, atur harga Ecer/Grosir, proses pembayaran & cetak struk"
      />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Medicine Picker */}
        <div className="lg:col-span-7 min-w-0 space-y-3">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none text-[#57534E]" aria-hidden="true" />
            <input
              ref={searchInputRef}
              type="text"
              aria-label="Cari obat atau scan barcode"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Escape' && search) {
                  e.preventDefault();
                  setSearch('');
                  return;
                }
                if (e.key === 'Enter' && search.trim()) {
                  const q = search.trim().toLowerCase();
                  const exact = medicines.find(
                    (m) => m.barcode?.toLowerCase() === q
                  );
                  if (exact) {
                    addToCart(exact);
                    setSearch('');
                    return;
                  }
                  const liveMatches = filterAndRankMedicines(medicines, search, {
                    prioritizeSellable: true,
                  });
                  if (
                    liveMatches.length === 1 &&
                    liveMatches[0].stock > 0 &&
                    liveMatches[0].sellPrice > 0
                  ) {
                    addToCart(liveMatches[0]);
                    setSearch('');
                  }
                }
              }}
              placeholder="Cari obat (mis. para 500) atau scan barcode lalu Enter..."
              className="w-full min-h-[44px] pl-10 pr-12 py-2.5 rounded-2xl bg-[#FAF8F4] border border-[#B7D6C3] text-sm text-[#1C1917] placeholder:text-[#57534E] focus:outline-none focus:ring-2 focus:ring-[#15803D]"
            />
            {search && (
              <button
                type="button"
                onClick={() => {
                  setSearch('');
                  searchInputRef.current?.focus();
                }}
                aria-label="Hapus pencarian"
                className="absolute right-1.5 top-1/2 -translate-y-1/2 min-w-[36px] min-h-[36px] flex items-center justify-center rounded-xl text-[#57534E] hover:text-[#1C1917] hover:bg-[#DCEBE2]"
              >
                <X className="w-4 h-4" aria-hidden="true" />
              </button>
            )}
          </div>

          <div
            ref={pickerScrollRef}
            className="grid grid-cols-1 sm:grid-cols-2 gap-3 p-1 -m-1 lg:max-h-[65vh] lg:overflow-y-auto lg:overscroll-contain lg:pr-1"
          >
            {filteredMeds.length === 0 ? (
              <div className="sm:col-span-2 rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] py-10 px-4 text-center text-sm text-[#57534E]">
                Obat tidak ditemukan.
              </div>
            ) : (
              kasirPager.visibleItems.map((m) => {
                const inCart = cartQtyById.get(m.id) || 0;
                const unavailable = m.stock <= 0 || m.sellPrice <= 0;
                return (
                  <button
                    type="button"
                    key={m.id}
                    disabled={unavailable}
                    onClick={() => addToCart(m)}
                    className={`cv-card text-left p-4 rounded-2xl transition flex flex-col justify-between border shadow-xs ${
                      inCart > 0
                        ? 'bg-[#DCFCE7] border-[#15803D]'
                        : 'bg-[#E8F2EC] border-[#B7D6C3] hover:border-[#15803D]'
                    } ${unavailable ? 'opacity-60 cursor-not-allowed' : ''}`}
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <p className="font-bold text-sm text-[#1C1917]">{m.name}</p>
                        {inCart > 0 && (
                          <span className="px-2 py-0.5 rounded-full bg-[#15803D] text-white text-xs font-bold">
                            {inCart}x
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-[#57534E] mt-0.5">
                        {m.category}
                        {m.medicineType ? ` • ${m.medicineType}` : ''} • {m.unit}
                      </p>
                    </div>
                    <div className="flex items-center justify-between gap-2 mt-3 pt-2 border-t border-[#B7D6C3]">
                      <div>
                        <p className={`font-extrabold text-sm ${m.sellPrice > 0 ? 'text-[#166534]' : 'text-[#BA1A1A]'}`}>
                          {m.sellPrice > 0 ? formatIDR(m.sellPrice) : 'Belum ada harga'}
                        </p>
                        {m.wholesalePrice > 0 && (
                          <p className="text-[11px] text-[#57534E]">
                            Grosir: {formatIDR(m.wholesalePrice)}
                          </p>
                        )}
                      </div>
                      <StockPill stock={m.stock} />
                    </div>
                  </button>
                );
              })
            )}
            <ListPaginationFooter
              shown={kasirPager.shownCount}
              total={kasirPager.total}
              remaining={kasirPager.remaining}
              step={kasirPager.step}
              itemLabel="obat"
              onLoadMore={kasirPager.loadMore}
              sentinelRef={kasirPager.sentinelRef}
              className="sm:col-span-2 rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3]"
            />
          </div>
        </div>

        {/* Cart & Payment Panel */}
        <div id="kasir-cart" className="lg:col-span-5 scroll-mt-20">
          <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] shadow-xs p-4 sm:p-5 lg:sticky lg:top-4 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base text-[#1C1917]">
                Keranjang ({cartItemCount} item)
              </h3>
              {cart.length > 0 && (
                <button
                  type="button"
                  onClick={() => setCart([])}
                  className="min-h-[44px] px-2 -mr-2 text-xs text-[#BA1A1A] font-bold hover:underline"
                >
                  Kosongkan
                </button>
              )}
            </div>

            {cart.length === 0 ? (
              <div className="py-12 text-center text-[#57534E] text-sm">
                Ketuk obat di sebelah kiri untuk memasukkan ke keranjang.
              </div>
            ) : (
              <div className="space-y-2.5 max-h-72 overflow-y-auto overscroll-contain pr-1">
                {cart.map((c) => {
                  const m = medById.get(c.medicineId);
                  if (!m) return null;
                  const unitPrice =
                    c.useWholesale && m.wholesalePrice > 0
                      ? m.wholesalePrice
                      : m.sellPrice;
                  return (
                    <div
                      key={c.medicineId}
                      className="p-3 rounded-2xl bg-[#DCEBE2] border border-[#B7D6C3] space-y-2"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="font-bold text-sm text-[#1C1917]">{m.name}</p>
                          <p className="text-xs text-[#57534E]">
                            {formatIDR(unitPrice)} / {m.unit}
                          </p>
                        </div>
                        <p className="font-extrabold text-sm text-[#1C1917]">
                          {formatIDR(unitPrice * c.qty)}
                        </p>
                      </div>
                      <div className="flex items-center justify-between gap-2">
                        {m.wholesalePrice > 0 ? (
                          <div className="flex gap-1.5 text-xs font-bold">
                            <button
                              type="button"
                              onClick={() => c.useWholesale && toggleWholesale(m.id)}
                              className={`min-h-[44px] px-3 rounded-xl border ${
                                !c.useWholesale
                                  ? 'bg-[#15803D] border-[#15803D] text-white'
                                  : 'bg-[#FAF8F4] border-[#B7D6C3] text-[#1C1917]'
                              }`}
                            >
                              Ecer
                            </button>
                            <button
                              type="button"
                              onClick={() => !c.useWholesale && toggleWholesale(m.id)}
                              className={`min-h-[44px] px-3 rounded-xl border ${
                                c.useWholesale
                                  ? 'bg-[#15803D] border-[#15803D] text-white'
                                  : 'bg-[#FAF8F4] border-[#B7D6C3] text-[#1C1917]'
                              }`}
                            >
                              Grosir
                            </button>
                          </div>
                        ) : (
                          <span className="text-[11px] text-[#57534E]">Harga Ecer</span>
                        )}
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => updateQty(m.id, c.qty - 1)}
                            aria-label={`Kurangi jumlah ${m.name}`}
                            className="min-w-[44px] min-h-[44px] rounded-xl bg-[#FAF8F4] hover:bg-white border border-[#B7D6C3] font-bold text-[#1C1917]"
                          >
                            -
                          </button>
                          <span className="font-bold text-sm w-6 text-center text-[#1C1917]">
                            {c.qty}
                          </span>
                          <button
                            type="button"
                            onClick={() => updateQty(m.id, c.qty + 1)}
                            aria-label={`Tambah jumlah ${m.name}`}
                            className="min-w-[44px] min-h-[44px] rounded-xl bg-[#FAF8F4] hover:bg-white border border-[#B7D6C3] font-bold text-[#1C1917]"
                          >
                            +
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            <div className="border-t border-[#B7D6C3] pt-4 space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-sm font-bold text-[#57534E]">Total Tagihan</span>
                <span className="text-2xl font-extrabold text-[#166534]">
                  {formatIDR(cartTotal)}
                </span>
              </div>

              {/* Payment Method */}
              <div>
                <p className="block text-xs font-bold text-[#57534E] mb-1.5">
                  Metode Pembayaran
                </p>
                <div className="grid grid-cols-3 gap-2" role="group" aria-label="Metode pembayaran">
                  {(['Tunai', 'QRIS', 'Transfer'] as const).map((pm) => (
                    <button
                      key={pm}
                      type="button"
                      onClick={() => setPaymentMethod(pm)}
                      className={`min-h-[44px] py-2 rounded-xl border text-xs font-bold transition ${
                        paymentMethod === pm
                          ? 'bg-[#15803D] border-[#15803D] text-white'
                          : 'bg-[#DCEBE2] border-[#B7D6C3] text-[#1C1917] hover:bg-[#D0E3D8]'
                      }`}
                    >
                      {pm}
                    </button>
                  ))}
                </div>
              </div>

              {paymentMethod === 'Tunai' && (
                <div>
                  <label htmlFor="kasir-paid" className="block text-xs font-bold text-[#57534E] mb-1">
                    Uang Diterima (Rp)
                  </label>
                  <input
                    id="kasir-paid"
                    type="text"
                    inputMode="numeric"
                    value={formatThousands(amountPaidStr)}
                    onChange={(e) => setAmountPaidStr(e.target.value.replace(/\D/g, ''))}
                    placeholder={formatThousands(String(cartTotal))}
                    className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] font-bold text-sm text-[#1C1917] placeholder:text-[#57534E]"
                  />
                  <div className="flex flex-wrap gap-1.5 mt-2">
                    {[cartTotal, 20000, 50000, 100000]
                      .filter((v, i, a) => v > 0 && a.indexOf(v) === i)
                      .map((nom) => (
                        <button
                          key={nom}
                          type="button"
                          onClick={() => setAmountPaidStr(String(nom))}
                          className="min-h-[44px] px-3 py-1.5 rounded-xl bg-[#DCEBE2] border border-[#B7D6C3] text-xs font-bold text-[#1C1917] hover:bg-[#15803D] hover:text-white transition"
                        >
                          {nom === cartTotal ? 'Uang Pas' : formatCompactIDR(nom)}
                        </button>
                      ))}
                  </div>
                </div>
              )}

              <div className="flex justify-between text-sm font-bold pt-1">
                <span className="text-[#1C1917]">Kembalian</span>
                <span className="text-[#166534]">
                  {formatIDR(changeAmount)}
                </span>
              </div>

              <button
                type="button"
                disabled={checkingOut || cart.length === 0 || paidNum < cartTotal}
                onClick={() => void handleCheckout()}
                className="w-full min-h-[48px] py-3.5 rounded-2xl bg-[#15803D] hover:bg-[#166534] disabled:opacity-40 text-white font-extrabold text-sm flex items-center justify-center gap-2 transition"
              >
                <CheckCircle2 className="w-5 h-5" aria-hidden="true" />{' '}
                {checkingOut ? 'Memproses...' : 'Bayar & Cetak Struk'}
                <kbd className="hidden sm:inline-block px-1.5 py-0.5 rounded bg-white/20 text-[10px] font-bold">
                  F9
                </kbd>
              </button>
            </div>
          </div>
        </div>
      </div>

      {cart.length > 0 && (
        <div className="no-print lg:hidden fixed bottom-[max(0.75rem,env(safe-area-inset-bottom))] inset-x-3 z-20">
          <button
            type="button"
            onClick={() =>
              document.getElementById('kasir-cart')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
            }
            className="w-full min-h-[48px] px-4 py-3 rounded-2xl bg-[#142E27] text-white shadow-xl border border-[#244C41] flex items-center justify-between gap-3"
          >
            <span className="text-xs font-bold">
              {cartItemCount} item • {formatIDR(cartTotal)}
            </span>
            <span className="px-3 py-1 rounded-xl bg-[#15803D] text-xs font-extrabold">
              Lihat Keranjang
            </span>
          </button>
        </div>
      )}

      {lastReceipt && (
        <ReceiptModal
          sale={lastReceipt.sale}
          items={lastReceipt.items}
          onClose={() => setLastReceipt(null)}
        />
      )}
    </div>
  );
};
