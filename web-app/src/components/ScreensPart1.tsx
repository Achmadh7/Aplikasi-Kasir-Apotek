import React, { useState, useMemo, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { useStore } from '../store';
import { useFeedback } from './Feedback';
import { AnimatedNumber } from './AnimatedNumber';
import {
  exportRowsToXlsx,
  exportMedicineTemplateXlsx,
  readSpreadsheetMatrix,
  parseMedicineSpreadsheet,
} from '../excel';
import {
  CATEGORIES,
  UNITS,
  Medicine,
  Sale,
  SaleItem,
  formatIDR,
  formatCompactIDR,
  stockLabel,
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
  <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] shadow-xs p-4 mb-5 flex flex-wrap items-center justify-between gap-3">
    <div className="min-w-0">
      <h1 className="hdr-slideup text-xl font-extrabold tracking-tight text-[#1C1917] dark:text-white">{title}</h1>
      {subtitle && (
        <p className="text-xs text-[#78716C] dark:text-[#93B5A9] mt-0.5">{subtitle}</p>
      )}
    </div>
    {action && <div className="flex items-center gap-2 flex-wrap">{action}</div>}
  </div>
);

export const StockPill: React.FC<{ stock: number }> = ({ stock }) => {
  const label = stockLabel(stock);
  const color =
    label === 'Aman'
      ? 'bg-[#43A047]'
      : label === 'Sedang'
      ? 'bg-[#0284C7]'
      : label === 'Rendah'
      ? 'bg-[#E3B341]'
      : 'bg-[#BA1A1A] animate-pulse';
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold text-[#1C1917] ${color}`}
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
      <div>
        <h1 className="text-lg sm:text-xl font-extrabold tracking-tight text-[#1C1917] dark:text-white">
          Ringkasan Apotek
        </h1>
        <p className="text-xs text-[#78716C] dark:text-[#93B5A9]">
          {dash.totalSalesToday} transaksi hari ini • Kemarin {formatIDR(dash.yesterdayIn)}
        </p>
      </div>

      {/* 2-Card KPI Strip: Penjualan Hari Ini & Transaksi Hari Ini ONLY */}
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-3.5 sm:p-4 shadow-xs min-w-0">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[11px] font-bold uppercase tracking-wide text-[#1C1917] dark:text-white truncate">
              Penjualan Hari Ini
            </p>
            <span className="hidden sm:inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold bg-[#DCFCE7] text-[#1C1917] dark:bg-[#15803D]/30 dark:text-white">
              {diffYesterday >= 0 ? 'Naik' : 'Turun'}
            </span>
          </div>
          <p className="text-lg sm:text-2xl font-extrabold mt-1.5 tabular-nums text-[#1C1917] dark:text-white truncate">
            <AnimatedNumber value={dash.todayIn} format={formatIDR} />
          </p>
          <p className="text-[11px] mt-1 text-[#1C1917] dark:text-white truncate">
            {diffYesterday >= 0 ? '+' : ''}
            {formatCompactIDR(diffYesterday)} vs kemarin
          </p>
        </div>

        <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-3.5 sm:p-4 shadow-xs min-w-0">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[11px] font-bold uppercase tracking-wide text-[#1C1917] dark:text-white truncate">
              Transaksi Hari Ini
            </p>
            <span className="hidden sm:inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold bg-[#E0F2FE] text-[#1C1917] dark:bg-[#0369A1]/30 dark:text-white">
              Struk
            </span>
          </div>
          <p className="text-lg sm:text-2xl font-extrabold mt-1.5 tabular-nums text-[#1C1917] dark:text-white">
            <AnimatedNumber value={dash.totalSalesToday} /> Struk
          </p>
          <p className="text-[11px] mt-1 text-[#1C1917] dark:text-white truncate">
            Rata-rata {formatCompactIDR(avgTicket)} / struk
          </p>
        </div>
      </div>

      {/* Main Content: 7-col Sales Chart with Numbers + 5-col Transaksi Terbaru & Pengingat Penting */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-start">
        <div className="lg:col-span-7 rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-4 sm:p-5 shadow-xs min-w-0">
          <div className="flex items-center justify-between gap-2 mb-3">
            <div>
              <h3 className="font-bold text-sm sm:text-base text-[#1C1917] dark:text-white">
                Penjualan 7 Hari Terakhir
              </h3>
              <p className="text-[11px] text-[#78716C] dark:text-[#93B5A9]">
                Total omzet harian beserta angka nominal
              </p>
            </div>
            <span className="px-2.5 py-1 rounded-full text-[11px] font-semibold bg-[#DCFCE7] text-[#166534] dark:bg-[#15803D]/30 dark:text-[#86EFAC] shrink-0">
              7 Hari
            </span>
          </div>

          <div className="pt-4">
            <div className="grid grid-cols-7 gap-1.5 sm:gap-2.5 items-end h-44 sm:h-52 border-b border-[#B7D6C3] dark:border-[#244C41] pb-2">
              {dash.last7Days.map((d, i) => (
                <div key={i} className="flex flex-col items-center justify-end h-full gap-1.5 min-w-0">
                  <span className="text-[9.5px] sm:text-[11px] font-bold text-[#1C1917] dark:text-white tabular-nums whitespace-nowrap">
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
                      d.isToday
                        ? 'bg-[#15803D]'
                        : 'bg-[#86EFAC] dark:bg-[#4ADE80]/80'
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
                    d.isToday
                      ? 'text-[#166534] dark:text-[#86EFAC] font-bold'
                      : 'text-[#78716C] dark:text-[#93B5A9]'
                  }`}
                >
                  {d.label}
                </span>
              ))}
            </div>
          </div>

          <div className="mt-3.5 pt-3 border-t border-dashed border-[#B7D6C3] dark:border-[#244C41] flex flex-wrap justify-between gap-2 text-xs text-[#78716C] dark:text-[#93B5A9]">
            <span>
              Total 7 Hari:{' '}
              <strong className="text-[#1C1917] dark:text-white tabular-nums">
                {formatIDR(dash.total7Days)}
              </strong>
            </span>
            <span>
              Rata-rata / Hari:{' '}
              <strong className="text-[#1C1917] dark:text-white tabular-nums">
                {formatIDR(dash.avg7Days)}
              </strong>
            </span>
          </div>
        </div>

        {/* Right Stack: Transaksi Terbaru ABOVE Pengingat Penting */}
        <div className="lg:col-span-5 space-y-4 min-w-0">
          {/* 1. Transaksi Terbaru */}
          <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-4 sm:p-5 shadow-xs">
            <div className="flex items-center justify-between mb-3 gap-2">
              <div>
                <h3 className="font-bold text-sm text-[#1C1917] dark:text-white">
                  Transaksi Terbaru
                </h3>
                <p className="text-[11px] text-[#1C1917] dark:text-white">
                  {dash.recentSales.length} penjualan terakhir
                </p>
              </div>
              <button
                onClick={() => onNavigate('riwayat')}
                className="text-xs font-bold text-[#1C1917] dark:text-white hover:underline shrink-0"
              >
                Lihat Semua →
              </button>
            </div>
            {dash.recentSales.length === 0 ? (
              <p className="text-xs text-[#1C1917] dark:text-white py-4 text-center">
                Belum ada transaksi.
              </p>
            ) : (
              <div className="space-y-2">
                {dash.recentSales.map((s) => {
                  const itemCount = dash.itemsCountBySale.get(s.id) || 0;
                  const payTone =
                    s.paymentMethod === 'QRIS'
                      ? 'bg-[#E0F2FE] text-[#1C1917]'
                      : s.paymentMethod === 'Transfer'
                      ? 'bg-[#FEF3C7] text-[#1C1917]'
                      : 'bg-[#DCFCE7] text-[#1C1917]';
                  return (
                    <div
                      key={s.id}
                      onClick={() => onNavigate('riwayat')}
                      className="cursor-pointer rounded-xl bg-[#DCEBE2] dark:bg-[#0F241E] border border-[#B7D6C3] dark:border-[#244C41] px-3 py-2.5 flex items-center justify-between gap-2.5 hover:border-[#15803D]/50 transition"
                    >
                      <div className="min-w-0">
                        <p className="font-bold text-xs text-[#1C1917] dark:text-white truncate">
                          {s.invoiceNo}
                        </p>
                        <div className="flex items-center gap-1.5 mt-0.5 text-[11px] text-[#1C1917] dark:text-white flex-wrap">
                          <span>{formatDateTimeID(s.createdAt)}</span>
                          <span className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${payTone}`}>
                            {s.paymentMethod === 'Tunai' ? 'Cash' : s.paymentMethod}
                          </span>
                          {itemCount > 0 && <span>• {itemCount} item</span>}
                        </div>
                      </div>
                      <span className="font-extrabold text-xs sm:text-sm text-[#1C1917] dark:text-white tabular-nums shrink-0">
                        {formatIDR(s.total)}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* 2. Pengingat Penting (High-contrast rows on white background) */}
          <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-4 sm:p-5 shadow-xs">
            <div className="flex items-center justify-between mb-3 gap-2">
              <div>
                <h3 className="font-bold text-sm text-[#1C1917] dark:text-white">
                  Pengingat Penting
                </h3>
                <p className="text-[11px] text-[#78716C] dark:text-[#93B5A9]">
                  Stok menipis, kadaluarsa & tagihan
                </p>
              </div>
              <span
                className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${
                  totalAlerts > 0
                    ? 'bg-[#FEE2E2] text-[#DC2626]'
                    : 'bg-[#DCFCE7] text-[#166534]'
                }`}
              >
                {totalAlerts} info
              </span>
            </div>

            {totalAlerts === 0 ? (
              <div className="alert-row-info rounded-xl px-3 py-2.5 flex items-center gap-2 text-xs font-semibold">
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                <span>Semua stok, ED, dan faktur dalam kondisi aman.</span>
              </div>
            ) : (
              <div className="space-y-2">
                {dash.expiringCount > 0 && (
                  <button
                    type="button"
                    onClick={() => onNavigate('pengingat')}
                    className="w-full text-left alert-row-danger rounded-xl px-3 py-2.5 flex items-center justify-between gap-2 text-xs font-semibold transition hover:opacity-90"
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      <AlertTriangle className="w-4 h-4 shrink-0" />
                      <span className="truncate">
                        {dash.expiringCount} obat kadaluarsa / ED ≤ 30 hari
                      </span>
                    </span>
                    <span className="font-extrabold shrink-0">Cek →</span>
                  </button>
                )}
                {dash.lowStockCount > 0 && (
                  <button
                    type="button"
                    onClick={() => onNavigate('pengingat')}
                    className="w-full text-left alert-row-warn rounded-xl px-3 py-2.5 flex items-center justify-between gap-2 text-xs font-semibold transition hover:opacity-90"
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      <Package className="w-4 h-4 shrink-0" />
                      <span className="truncate">
                        {dash.lowStockCount} obat stok menipis / habis (&lt; 10)
                      </span>
                    </span>
                    <span className="font-extrabold shrink-0">Cek →</span>
                  </button>
                )}
                {dash.creditPbfCount > 0 && (
                  <button
                    type="button"
                    onClick={() => onNavigate('pbf')}
                    className="w-full text-left alert-row-info rounded-xl px-3 py-2.5 flex items-center justify-between gap-2 text-xs font-semibold transition hover:opacity-90"
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      <TrendingUp className="w-4 h-4 shrink-0" />
                      <span className="truncate">
                        {dash.creditPbfCount} faktur PBF kredit belum lunas
                      </span>
                    </span>
                    <span className="font-extrabold shrink-0">Lihat →</span>
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
  const [category, setCategory] = useState('Umum');
  const [unit, setUnit] = useState('Strip');
  const [buyPrice, setBuyPrice] = useState('');
  const [sellPrice, setSellPrice] = useState('');
  const [wholesalePrice, setWholesalePrice] = useState('');
  const [stock, setStock] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [barcode, setBarcode] = useState('');

  const openAdd = () => {
    setEditing(null);
    setName('');
    setCategory('Umum');
    setUnit('Strip');
    setBuyPrice('');
    setSellPrice('');
    setWholesalePrice('');
    setStock('0');
    setExpiryDate('');
    setBarcode('');
    setShowModal(true);
  };

  const openEdit = (m: Medicine) => {
    setEditing(m);
    setName(m.name);
    setCategory(m.category);
    setUnit(m.unit);
    setBuyPrice(String(m.buyPrice || ''));
    setSellPrice(String(m.sellPrice || ''));
    setWholesalePrice(String(m.wholesalePrice || ''));
    setStock(String(m.stock));
    setExpiryDate(m.expiryDate || '');
    setBarcode(m.barcode || '');
    setShowModal(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    try {
      saveMedicine(
        {
          name,
          category,
          unit,
          buyPrice: parseInt(buyPrice, 10) || 0,
          sellPrice: parseInt(sellPrice, 10) || 0,
          wholesalePrice: parseInt(wholesalePrice, 10) || 0,
          stock: parseInt(stock, 10) || 0,
          expiryDate: expiryDate.trim() || null,
          barcode: barcode.trim() || null,
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
      Kategori: m.category,
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
      UNITS
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

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return medicines
      .filter((m) => {
        if (catFilter !== 'Semua' && m.category !== catFilter) return false;
        if (stockFilter === 'LOW' && m.stock >= 10) return false;
        if (stockFilter === 'EXP') {
          const days = daysUntilExpiry(m.expiryDate);
          if (days === null || days > 30) return false;
        }
        if (!q) return true;
        return (
          m.name.toLowerCase().includes(q) ||
          m.category.toLowerCase().includes(q) ||
          (m.barcode && m.barcode.toLowerCase().includes(q))
        );
      })
      .sort((a, b) =>
        sortAz ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name)
      );
  }, [medicines, search, catFilter, stockFilter, sortAz]);

  return (
    <div>
      <PageHeader
        title="Stok Obat"
        subtitle={`${medicines.length} jenis obat terdaftar • Tersimpan lokal & cloud Web (tukar ke Android lewat JSON/Excel)`}
        action={
          <>
            <button
              onClick={handleDownloadTemplate}
              className="px-3 py-2 rounded-xl bg-white/20 hover:bg-white/30 text-xs font-bold flex items-center gap-1.5"
            >
              <Download className="w-4 h-4" /> Template Excel
            </button>
            <label className="cursor-pointer px-3 py-2 rounded-xl bg-white/20 hover:bg-white/30 text-xs font-bold flex items-center gap-1.5">
              <Upload className="w-4 h-4" /> Impor Excel/CSV
              <input
                type="file"
                accept=".xlsx,.xls,.csv"
                onChange={handleImportSpreadsheet}
                className="hidden"
              />
            </label>
            <button
              onClick={handleExportExcel}
              className="px-3 py-2 rounded-xl bg-white/20 hover:bg-white/30 text-xs font-bold flex items-center gap-1.5"
            >
              <Download className="w-4 h-4" /> Ekspor Excel
            </button>
            <button
              onClick={openAdd}
              className="px-4 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold flex items-center gap-1.5"
            >
              <Plus className="w-4 h-4" /> Tambah Obat
            </button>
          </>
        }
      />

      {msg && (
        <div className="mb-4 p-3 rounded-2xl bg-[#15803D]/15 border border-[#15803D]/30 text-sm flex items-center justify-between">
          <span>{msg}</span>
          <button onClick={() => setMsg(null)} className="text-xs font-bold">
            Tutup
          </button>
        </div>
      )}

      {/* Search & Filter Controls */}
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <div className="relative flex-1 min-w-[220px]">
          <Search className="w-4 h-4 absolute left-3.5 top-3.5 opacity-50" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Cari nama obat, kategori, atau scan barcode..."
            className="w-full pl-10 pr-4 py-2.5 rounded-2xl bg-[#FAF8F4] dark:bg-[#0F241E] text-sm focus:outline-none focus:ring-2 focus:ring-[#15803D]"
          />
        </div>
        <select
          value={catFilter}
          onChange={(e) => setCatFilter(e.target.value)}
          className="px-3.5 py-2.5 rounded-2xl bg-[#DCEBE2] dark:bg-[#0F241E] text-sm font-medium"
        >
          <option value="Semua">Semua Kategori</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <div className="flex rounded-2xl bg-[#DCEBE2] dark:bg-[#0F241E] p-1 text-xs font-bold">
          {(
            [
              ['ALL', 'Semua'],
              ['LOW', 'Stok < 10'],
              ['EXP', 'Kadaluarsa ≤30h'],
            ] as const
          ).map(([k, lbl]) => (
            <button
              key={k}
              onClick={() => setStockFilter(k)}
              className={`px-3 py-1.5 rounded-xl transition ${
                stockFilter === k ? 'bg-[#15803D] text-white' : 'opacity-70'
              }`}
            >
              {lbl}
            </button>
          ))}
        </div>
        <button
          onClick={() => setSortAz(!sortAz)}
          className="px-3.5 py-2.5 rounded-2xl bg-[#DCEBE2] dark:bg-[#0F241E] text-xs font-bold"
        >
          Urut: {sortAz ? 'A-Z' : 'Z-A'}
        </button>
      </div>

      {/* Mobile Card List + Desktop Table */}
      <div className="md:hidden space-y-2.5">
        {filtered.length === 0 ? (
          <div className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] py-10 text-center opacity-60 text-sm">
            Tidak ada data obat yang cocok.
          </div>
        ) : (
          filtered.map((m) => {
            const expDays = daysUntilExpiry(m.expiryDate);
            return (
              <div
                key={m.id}
                className="rounded-2xl bg-[#E8F2EC] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] p-3.5 space-y-2.5"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-bold text-sm leading-snug break-words">{m.name}</p>
                    <p className="text-xs opacity-65 mt-0.5">
                      {m.category} • {m.unit}
                    </p>
                  </div>
                  <StockPill stock={m.stock} />
                </div>
                <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-black/5 dark:border-white/5 text-xs">
                  <div className="space-y-0.5">
                    <p className="font-extrabold text-sm text-[#15803D] dark:text-[#86EFAC]">
                      Ecer: {formatIDR(m.sellPrice)}
                    </p>
                    <p className="opacity-70">
                      Beli: {m.buyPrice > 0 ? formatIDR(m.buyPrice) : '-'}
                      {m.wholesalePrice > 0 ? ` • Grosir: ${formatIDR(m.wholesalePrice)}` : ''}
                    </p>
                    {m.expiryDate && (
                      <p
                        className={
                          expDays !== null && expDays <= 0
                            ? 'text-[#BA1A1A] font-bold'
                            : expDays !== null && expDays <= 30
                            ? 'text-amber-500 font-bold'
                            : 'opacity-70'
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
                      className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl bg-[#DCEBE2] dark:bg-[#0F241E] hover:bg-black/10 dark:hover:bg-white/10"
                      title="Edit Obat"
                      aria-label={`Edit ${m.name}`}
                    >
                      <Edit2 className="w-4 h-4" />
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
                      className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl bg-red-500/10 text-[#BA1A1A] dark:text-[#FCA5A5]"
                      title="Hapus Obat"
                      aria-label={`Hapus ${m.name}`}
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      <div className="hidden md:block rounded-3xl bg-[#E8F2EC] dark:bg-[#142E27] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-[#E1E2E8] dark:bg-[#0F241E] text-xs uppercase opacity-75">
              <tr>
                <th className="py-3.5 px-4">Nama Obat</th>
                <th className="py-3.5 px-3">Kategori / Satuan</th>
                <th className="py-3.5 px-3 text-right">Harga Beli</th>
                <th className="py-3.5 px-3 text-right">Harga Ecer</th>
                <th className="py-3.5 px-3 text-right">Harga Grosir</th>
                <th className="py-3.5 px-3">Status Stok</th>
                <th className="py-3.5 px-3">Kadaluarsa</th>
                <th className="py-3.5 px-4 text-right">Aksi</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-black/5 dark:divide-white/5">
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-10 text-center opacity-60">
                    Tidak ada data obat yang cocok.
                  </td>
                </tr>
              ) : (
                filtered.map((m) => {
                  const expDays = daysUntilExpiry(m.expiryDate);
                  return (
                    <tr
                      key={m.id}
                      className="hover:bg-black/5 dark:hover:bg-white/5 transition"
                    >
                      <td className="py-3 px-4 font-bold">
                        <div>{m.name}</div>
                        {m.barcode && (
                          <div className="text-[11px] font-normal opacity-60">
                            Barcode: {m.barcode}
                          </div>
                        )}
                      </td>
                      <td className="py-3 px-3 text-xs">
                        <span className="font-semibold">{m.category}</span> • {m.unit}
                      </td>
                      <td className="py-3 px-3 text-right opacity-80">
                        {m.buyPrice > 0 ? formatIDR(m.buyPrice) : '-'}
                      </td>
                      <td className="py-3 px-3 text-right font-bold text-[#15803D] dark:text-[#86EFAC]">
                        {formatIDR(m.sellPrice)}
                      </td>
                      <td className="py-3 px-3 text-right opacity-80">
                        {m.wholesalePrice > 0 ? formatIDR(m.wholesalePrice) : '-'}
                      </td>
                      <td className="py-3 px-3">
                        <StockPill stock={m.stock} />
                      </td>
                      <td className="py-3 px-3 text-xs">
                        {m.expiryDate ? (
                          <span
                            className={
                              expDays !== null && expDays <= 0
                                ? 'text-[#BA1A1A] font-bold'
                                : expDays !== null && expDays <= 30
                                ? 'text-amber-500 font-bold'
                                : ''
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
                            className="p-2.5 rounded-xl hover:bg-black/10 dark:hover:bg-white/10"
                            title="Edit Obat"
                            aria-label={`Edit ${m.name}`}
                          >
                            <Edit2 className="w-4 h-4" />
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
                            className="p-2.5 rounded-xl text-[#BA1A1A] dark:text-[#FCA5A5] hover:bg-red-500/10"
                            title="Hapus Obat"
                            aria-label={`Hapus ${m.name}`}
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

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
          <div className="bg-[#F6F4EF] dark:bg-[#142E27] border border-[#B7D6C3] dark:border-[#244C41] rounded-3xl max-w-lg w-full p-6 shadow-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 id="med-modal-title" className="text-lg font-extrabold">
                {editing ? 'Ubah Data Obat' : 'Tambah Obat Baru'}
              </h3>
              <button
                type="button"
                onClick={() => setShowModal(false)}
                aria-label="Tutup modal obat"
                className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl hover:bg-black/5 dark:hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleSave} className="space-y-3.5 text-sm">
              <div>
                <label className="block text-xs font-bold mb-1">Nama Obat *</label>
                <input
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                  placeholder="Contoh: Paracetamol 500mg"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold mb-1">Kategori</label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                  >
                    {CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-bold mb-1">Satuan</label>
                  <select
                    value={unit}
                    onChange={(e) => setUnit(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                  >
                    {UNITS.map((u) => (
                      <option key={u} value={u}>
                        {u}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-bold mb-1">Harga Beli (HPP)</label>
                  <input
                    type="number"
                    min="0"
                    value={buyPrice}
                    onChange={(e) => setBuyPrice(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                    placeholder="0"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold mb-1">Harga Jual Ecer *</label>
                  <input
                    required
                    type="number"
                    min="1"
                    value={sellPrice}
                    onChange={(e) => setSellPrice(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                    placeholder="10000"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold mb-1">Harga Grosir</label>
                  <input
                    type="number"
                    min="0"
                    value={wholesalePrice}
                    onChange={(e) => setWholesalePrice(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                    placeholder="0"
                  />
                </div>
              </div>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-bold mb-1">Stok *</label>
                  <input
                    required
                    type="number"
                    min="0"
                    value={stock}
                    onChange={(e) => setStock(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold mb-1">Tgl Kadaluarsa</label>
                  <input
                    type="date"
                    value={expiryDate}
                    onChange={(e) => setExpiryDate(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold mb-1">Barcode</label>
                  <input
                    value={barcode}
                    onChange={(e) => setBarcode(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                    placeholder="Opsional"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2.5 rounded-xl bg-black/10 dark:bg-white/10 font-bold"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  className="px-5 py-2.5 rounded-xl bg-[#15803D] text-white font-bold"
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
                className={`px-3 py-1 rounded-md ${
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
                className={`px-3 py-1 rounded-md ${
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
              onClick={() => window.print()}
              className="flex-1 py-2.5 rounded-xl bg-[#15803D] text-white font-bold text-sm flex items-center justify-center gap-2"
            >
              <Printer className="w-4 h-4" /> Cetak Struk
            </button>
            <button
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl bg-gray-200 text-gray-800 font-bold text-sm"
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
  const [search, setSearch] = useState('');
  const [cart, setCart] = useState<
    Array<{ medicineId: number; qty: number; useWholesale: boolean }>
  >([]);
  const [paymentMethod, setPaymentMethod] = useState<'Tunai' | 'QRIS' | 'Transfer'>(
    'Tunai'
  );
  const [amountPaidStr, setAmountPaidStr] = useState('');
  const [lastReceipt, setLastReceipt] = useState<{
    sale: Sale;
    items: SaleItem[];
  } | null>(null);

  const medById = useMemo(
    () => new Map(medicines.map((m) => [m.id, m])),
    [medicines]
  );

  const filteredMeds = useMemo(() => {
    const q = search.trim().toLowerCase();
    return medicines
      .filter((m) => {
        if (!q) return true;
        return (
          m.name.toLowerCase().includes(q) ||
          m.category.toLowerCase().includes(q) ||
          (m.barcode && m.barcode.toLowerCase().includes(q))
        );
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [medicines, search]);

  const addToCart = (m: Medicine) => {
    if (m.stock <= 0) return;
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

  const handleCheckout = () => {
    try {
      const finalPaid = paymentMethod !== 'Tunai' ? cartTotal : paidNum;
      const res = createSale(cart, paymentMethod, finalPaid);
      setCart([]);
      setAmountPaidStr('');
      setLastReceipt(res);
      notify('Transaksi berhasil disimpan', 'success');
    } catch (err: any) {
      notify(err.message, 'error');
    }
  };

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'F2') {
        e.preventDefault();
        searchInputRef.current?.focus();
        searchInputRef.current?.select();
      } else if (e.key === 'F9') {
        e.preventDefault();
        if (!lastReceipt && cart.length > 0 && paidNum >= cartTotal) {
          handleCheckout();
        }
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [cart, paidNum, cartTotal, paymentMethod, lastReceipt]);

  return (
    <div>
      <PageHeader
        title="Kasir"
        subtitle="Pilih obat, atur harga Ecer/Grosir, proses pembayaran & cetak struk"
      />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Medicine Picker */}
        <div className="lg:col-span-7 space-y-3">
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3.5 top-3.5 opacity-50" />
            <input
              ref={searchInputRef}
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && search.trim()) {
                  const exact = medicines.find(
                    (m) =>
                      m.barcode?.toLowerCase() === search.trim().toLowerCase()
                  );
                  if (exact) {
                    addToCart(exact);
                    setSearch('');
                  }
                }
              }}
              placeholder="Cari obat atau scan barcode lalu tekan Enter..."
              className="w-full pl-10 pr-14 py-2.5 rounded-2xl bg-[#FAF8F4] dark:bg-[#0F241E] text-sm focus:outline-none focus:ring-2 focus:ring-[#15803D]"
            />
            <kbd className="hidden sm:inline-block absolute right-3 top-2.5 px-1.5 py-0.5 rounded bg-black/10 dark:bg-white/10 text-[10px] font-bold opacity-70">
              F2
            </kbd>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 lg:max-h-[65vh] lg:overflow-y-auto lg:pr-1">
            {filteredMeds.map((m) => {
              const inCart = cart.find((c) => c.medicineId === m.id)?.qty || 0;
              return (
                <button
                  key={m.id}
                  disabled={m.stock <= 0}
                  onClick={() => addToCart(m)}
                  className={`text-left p-4 rounded-2xl transition flex flex-col justify-between border ${
                    inCart > 0
                      ? 'bg-[#15803D]/15 border-[#15803D]'
                      : 'bg-[#E8F2EC] dark:bg-[#142E27] border-transparent hover:border-[#15803D]/40'
                  } ${m.stock <= 0 ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  <div>
                    <div className="flex items-start justify-between gap-2">
                      <p className="font-bold text-sm">{m.name}</p>
                      {inCart > 0 && (
                        <span className="px-2 py-0.5 rounded-full bg-[#15803D] text-white text-xs font-bold">
                          {inCart}x
                        </span>
                      )}
                    </div>
                    <p className="text-xs opacity-65 mt-0.5">
                      {m.category} • {m.unit}
                    </p>
                  </div>
                  <div className="flex items-center justify-between mt-3 pt-2 border-t border-black/5 dark:border-white/5">
                    <div>
                      <p className="font-extrabold text-sm text-[#15803D] dark:text-[#86EFAC]">
                        {formatIDR(m.sellPrice)}
                      </p>
                      {m.wholesalePrice > 0 && (
                        <p className="text-[11px] opacity-70">
                          Grosir: {formatIDR(m.wholesalePrice)}
                        </p>
                      )}
                    </div>
                    <StockPill stock={m.stock} />
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Cart & Payment Panel */}
        <div id="kasir-cart" className="lg:col-span-5 scroll-mt-20">
          <div className="rounded-3xl bg-[#E8F2EC] dark:bg-[#142E27] p-4 sm:p-5 lg:sticky lg:top-4 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-base">
                Keranjang ({cart.reduce((a, c) => a + c.qty, 0)} item)
              </h3>
              {cart.length > 0 && (
                <button
                  onClick={() => setCart([])}
                  className="text-xs text-[#BA1A1A] font-bold"
                >
                  Kosongkan
                </button>
              )}
            </div>

            {cart.length === 0 ? (
              <div className="py-12 text-center opacity-50 text-sm">
                Ketuk obat di sebelah kiri untuk memasukkan ke keranjang.
              </div>
            ) : (
              <div className="space-y-2.5 max-h-72 overflow-y-auto pr-1">
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
                      className="p-3 rounded-2xl bg-[#DCEBE2] dark:bg-[#0F241E] space-y-2"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="font-bold text-sm">{m.name}</p>
                          <p className="text-xs opacity-75">
                            {formatIDR(unitPrice)} / {m.unit}
                          </p>
                        </div>
                        <p className="font-extrabold text-sm">
                          {formatIDR(unitPrice * c.qty)}
                        </p>
                      </div>
                      <div className="flex items-center justify-between">
                        {m.wholesalePrice > 0 ? (
                          <div className="flex gap-1 text-xs font-bold">
                            <button
                              onClick={() => c.useWholesale && toggleWholesale(m.id)}
                              className={`px-2.5 py-1.5 sm:py-1 rounded-lg ${
                                !c.useWholesale
                                  ? 'bg-[#15803D] text-white'
                                  : 'bg-black/10 dark:bg-white/10'
                              }`}
                            >
                              Ecer
                            </button>
                            <button
                              onClick={() => !c.useWholesale && toggleWholesale(m.id)}
                              className={`px-2.5 py-1.5 sm:py-1 rounded-lg ${
                                c.useWholesale
                                  ? 'bg-[#15803D] text-white'
                                  : 'bg-black/10 dark:bg-white/10'
                              }`}
                            >
                              Grosir
                            </button>
                          </div>
                        ) : (
                          <span className="text-[11px] opacity-60">Harga Ecer</span>
                        )}
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => updateQty(m.id, c.qty - 1)}
                            className="w-9 h-9 sm:w-7 sm:h-7 rounded-lg bg-black/10 dark:bg-white/10 font-bold"
                          >
                            -
                          </button>
                          <span className="font-bold text-sm w-6 text-center">
                            {c.qty}
                          </span>
                          <button
                            onClick={() => updateQty(m.id, c.qty + 1)}
                            className="w-9 h-9 sm:w-7 sm:h-7 rounded-lg bg-black/10 dark:bg-white/10 font-bold"
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

            <div className="border-t border-black/10 dark:border-white/10 pt-4 space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-sm font-bold opacity-75">Total Tagihan</span>
                <span className="text-2xl font-extrabold text-[#15803D] dark:text-[#86EFAC]">
                  {formatIDR(cartTotal)}
                </span>
              </div>

              {/* Payment Method */}
              <div>
                <label className="block text-xs font-bold mb-1.5 opacity-75">
                  Metode Pembayaran
                </label>
                <div className="grid grid-cols-3 gap-2">
                  {(['Tunai', 'QRIS', 'Transfer'] as const).map((pm) => (
                    <button
                      key={pm}
                      type="button"
                      onClick={() => setPaymentMethod(pm)}
                      className={`py-2 rounded-xl text-xs font-bold transition ${
                        paymentMethod === pm
                          ? 'bg-[#15803D] text-white'
                          : 'bg-[#DCEBE2] dark:bg-[#0F241E]'
                      }`}
                    >
                      {pm}
                    </button>
                  ))}
                </div>
              </div>

              {paymentMethod === 'Tunai' && (
                <div>
                  <label className="block text-xs font-bold mb-1 opacity-75">
                    Uang Diterima (Rp)
                  </label>
                  <input
                    type="text"
                    inputMode="numeric"
                    value={formatThousands(amountPaidStr)}
                    onChange={(e) => setAmountPaidStr(e.target.value.replace(/\D/g, ''))}
                    placeholder={formatThousands(String(cartTotal))}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E] font-bold text-sm"
                  />
                  <div className="flex flex-wrap gap-1.5 mt-2">
                    {[cartTotal, 20000, 50000, 100000]
                      .filter((v, i, a) => v > 0 && a.indexOf(v) === i)
                      .map((nom) => (
                        <button
                          key={nom}
                          type="button"
                          onClick={() => setAmountPaidStr(String(nom))}
                          className="px-2.5 py-1 rounded-lg bg-[#DCEBE2] dark:bg-[#0F241E] text-xs font-bold hover:bg-[#15803D] hover:text-white transition"
                        >
                          {nom === cartTotal ? 'Uang Pas' : formatCompactIDR(nom)}
                        </button>
                      ))}
                  </div>
                </div>
              )}

              <div className="flex justify-between text-sm font-bold pt-1">
                <span>Kembalian</span>
                <span className="text-[#2E7D32] dark:text-[#7EDC9A]">
                  {formatIDR(changeAmount)}
                </span>
              </div>

              <button
                disabled={cart.length === 0 || paidNum < cartTotal}
                onClick={handleCheckout}
                className="w-full py-3.5 rounded-2xl bg-[#15803D] hover:bg-[#166534] disabled:opacity-40 text-white font-extrabold text-sm flex items-center justify-center gap-2 transition"
              >
                <CheckCircle2 className="w-5 h-5" /> Bayar & Cetak Struk
                <kbd className="hidden sm:inline-block px-1.5 py-0.5 rounded bg-white/20 text-[10px] font-bold">
                  F9
                </kbd>
              </button>
            </div>
          </div>
        </div>
      </div>

      {cart.length > 0 && (
        <div className="no-print lg:hidden fixed bottom-[calc(4.25rem+env(safe-area-inset-bottom))] inset-x-3 z-20">
          <button
            type="button"
            onClick={() =>
              document.getElementById('kasir-cart')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
            }
            className="w-full px-4 py-3 rounded-2xl bg-[#142E27] text-white shadow-xl border border-[#244C41] flex items-center justify-between gap-3"
          >
            <span className="text-xs font-bold">
              {cart.reduce((a, c) => a + c.qty, 0)} item • {formatIDR(cartTotal)}
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
