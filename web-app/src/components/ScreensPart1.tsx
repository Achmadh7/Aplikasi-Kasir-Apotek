import React, { useState, useMemo, useEffect, useRef } from 'react';
import { useStore } from '../store';
import { useFeedback } from './Feedback';
import { AnimatedNumber } from './AnimatedNumber';
import { exportRowsToXlsx, readSpreadsheetMatrix } from '../excel';
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
  ShoppingBag,
  Package,
  CheckCircle2,
  X,
} from 'lucide-react';

// ---- Shared Header matching KartuHeaderPremium (N2 dark #0B1526 / H6 light #D3E3F3) ----
export const PageHeader: React.FC<{
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}> = ({ title, subtitle, action }) => (
  <div className="rounded-[22px] bg-[#D3E3F3] dark:bg-[#0B1526] dark:border dark:border-white/15 p-4 mb-5 flex flex-wrap items-center justify-between gap-3">
    <div>
      <h1 className="text-xl font-extrabold text-[#0B1526] dark:text-white">{title}</h1>
      {subtitle && (
        <p className="text-xs text-[#3D5A75] dark:text-white/70 mt-0.5">{subtitle}</p>
      )}
    </div>
    {action && <div className="flex items-center gap-2">{action}</div>}
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
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold text-white ${color}`}
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
  const { medicines, sales, saleItems, cashEntries } = useStore();

  const dash = useMemo(() => {
    const startToday = jakartaDayStartMillis(0);
    const endToday = jakartaDayStartMillis(1);
    const startYesterday = jakartaDayStartMillis(-1);
    const startMonth = jakartaMonthStartMillis();

    let todayIn = 0,
      yesterdayIn = 0,
      todayOut = 0,
      monthIn = 0,
      monthOut = 0;

    for (const e of cashEntries) {
      const t = parseISODate(e.createdAt)?.getTime();
      if (!t) continue;
      if (t >= startToday && t < endToday) {
        if (e.type === 'in' && e.source === 'sale') todayIn += e.amount;
        else if (e.type === 'out') todayOut += e.amount;
      }
      if (t >= startYesterday && t < startToday) {
        if (e.type === 'in' && e.source === 'sale') yesterdayIn += e.amount;
      }
      if (t >= startMonth && t < endToday) {
        if (e.type === 'in' && e.source === 'sale') monthIn += e.amount;
        else if (e.type === 'out') monthOut += e.amount;
      }
    }

    const lowStockCount = medicines.filter((m) => m.stock < 10).length;
    const expiringCount = medicines.filter((m) => {
      const d = daysUntilExpiry(m.expiryDate);
      return d !== null && d <= 30;
    }).length;

    const todaySales = sales.filter((s) => {
      const t = parseISODate(s.createdAt)?.getTime() ?? -1;
      return t >= startToday && t < endToday;
    });
    const monthSales = sales.filter((s) => {
      const t = parseISODate(s.createdAt)?.getTime() ?? -1;
      return t >= startMonth && t < endToday;
    });

    const medById = new Map(medicines.map((m) => [m.id, m]));
    const todaySaleIds = new Set(todaySales.map((s) => s.id));
    const medMap = new Map<string, { name: string; qty: number; revenue: number }>();
    for (const it of saleItems) {
      if (!todaySaleIds.has(it.saleId)) continue;
      const key = it.medicineId > 0 ? String(it.medicineId) : `name:${it.medicineName}`;
      const displayName = medById.get(it.medicineId)?.name || it.medicineName;
      const cur = medMap.get(key) || {
        name: displayName,
        qty: 0,
        revenue: 0,
      };
      cur.qty += it.qty;
      cur.revenue += it.subtotal;
      medMap.set(key, cur);
    }
    const bestSellers = Array.from(medMap.values())
      .sort((a, b) => b.qty - a.qty)
      .slice(0, 6);

    const last7Days = Array.from({ length: 7 }, (_, i) => {
      const offset = 6 - i;
      const dStart = jakartaDayStartMillis(-offset);
      const dEnd = dStart + 86_400_000;
      let inc = 0,
        exp = 0;
      for (const e of cashEntries) {
        const t = parseISODate(e.createdAt)?.getTime();
        if (!t || t < dStart || t >= dEnd) continue;
        if (e.type === 'in' && e.source === 'sale') inc += e.amount;
        else if (e.type === 'out') exp += e.amount;
      }
      const d = new Date(dStart);
      const label = new Intl.DateTimeFormat('id-ID', {
        timeZone: 'Asia/Jakarta',
        weekday: 'short',
      }).format(d);
      return { label, inc, exp };
    });

    return {
      todayIn,
      yesterdayIn,
      todayOut,
      todayProfit: todayIn - todayOut,
      monthIn,
      monthOut,
      monthProfit: monthIn - monthOut,
      lowStockCount,
      expiringCount,
      totalSalesToday: todaySales.length,
      totalSalesMonth: monthSales.length,
      bestSellers,
      last7Days,
      recentSales: todaySales.slice(0, 5),
    };
  }, [medicines, sales, saleItems, cashEntries]);

  const maxBar = Math.max(
    1,
    ...dash.last7Days.map((d) => Math.max(d.inc, d.exp))
  );

  const [chartReady, setChartReady] = useState(false);
  useEffect(() => {
    const id = requestAnimationFrame(() => setChartReady(true));
    return () => cancelAnimationFrame(id);
  }, []);

  return (
    <div className="space-y-5">
      {/* Hero Card */}
      <div className="rounded-3xl bg-[#0B1526] text-white p-6 border border-white/10 shadow-lg">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-wider text-[#9FCAFF] font-semibold">
              Pemasukan Penjualan Hari Ini
            </p>
            <p className="text-3xl sm:text-4xl font-extrabold mt-1">
              <AnimatedNumber value={dash.todayIn} format={formatIDR} />
            </p>
            <p className="text-xs text-white/70 mt-1">
              Kemarin: {formatIDR(dash.yesterdayIn)} • {dash.totalSalesToday} transaksi hari ini
            </p>
          </div>
          <div className="flex gap-3">
            <button
              onClick={() => onNavigate('kasir')}
              className="px-4 py-2.5 rounded-2xl bg-[#0061A4] hover:bg-[#00497D] text-white font-bold text-sm flex items-center gap-2 transition"
            >
              <ShoppingBag className="w-4 h-4" /> Buka Kasir
            </button>
            <button
              onClick={() => onNavigate('laba')}
              className="px-4 py-2.5 rounded-2xl bg-white/10 hover:bg-white/20 text-white font-bold text-sm flex items-center gap-2 transition"
            >
              <TrendingUp className="w-4 h-4" /> Laporan Laba
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-6 pt-5 border-t border-white/10">
          <div>
            <span className="text-xs text-white/60">Pengeluaran Hari Ini</span>
            <p className="text-base font-bold text-red-300">
              <AnimatedNumber value={dash.todayOut} format={formatIDR} />
            </p>
          </div>
          <div>
            <span className="text-xs text-white/60">Selisih Kas Hari Ini</span>
            <p className="text-base font-bold text-[#7EDC9A]">
              <AnimatedNumber value={dash.todayProfit} format={formatIDR} />
            </p>
          </div>
          <div>
            <span className="text-xs text-white/60">Penjualan Bulan Ini</span>
            <p className="text-base font-bold text-white">
              <AnimatedNumber value={dash.monthIn} format={formatIDR} /> ({dash.totalSalesMonth}x)
            </p>
          </div>
          <div>
            <span className="text-xs text-white/60">Total Jenis Obat</span>
            <p className="text-base font-bold text-[#9FCAFF]">
              <AnimatedNumber value={medicines.length} /> Obat
            </p>
          </div>
        </div>
      </div>

      {/* Warning Banners */}
      {(dash.lowStockCount > 0 || dash.expiringCount > 0) && (
        <div
          onClick={() => onNavigate('pengingat')}
          className="cursor-pointer rounded-2xl bg-amber-500/15 border border-amber-500/30 p-4 flex items-center justify-between hover:bg-amber-500/20 transition"
        >
          <div className="flex items-center gap-3">
            <AlertTriangle className="w-6 h-6 text-amber-500 shrink-0" />
            <div>
              <p className="font-bold text-sm">Perhatian Stok & Kadaluarsa</p>
              <p className="text-xs opacity-80">
                {dash.lowStockCount} obat stok rendah/habis • {dash.expiringCount} obat mendekati/sudah kadaluarsa (≤30 hari)
              </p>
            </div>
          </div>
          <span className="text-xs font-bold text-amber-500">Lihat Pengingat →</span>
        </div>
      )}

      {/* 7-day chart & Today's Best Sellers */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <div className="rounded-3xl bg-[#ECEEF4] dark:bg-[#1D2024] p-5">
          <h3 className="font-bold text-base mb-4">Arus Kas 7 Hari Terakhir</h3>
          <div className="flex items-end justify-between gap-2 h-40 pt-4">
            {dash.last7Days.map((d, i) => (
              <div key={i} className="flex-1 flex flex-col items-center gap-1">
                <div className="w-full flex justify-center items-end gap-1 h-28">
                  <div
                    title={`Masuk: ${formatIDR(d.inc)}`}
                    style={{
                      height: chartReady ? `${Math.max(6, (d.inc / maxBar) * 100)}%` : '0%',
                      transition: 'height 620ms cubic-bezier(0.22, 1, 0.36, 1)',
                      transitionDelay: `${i * 55}ms`,
                    }}
                    className="w-3 sm:w-4 bg-[#0061A4] dark:bg-[#9FCAFF] rounded-t"
                  />
                  <div
                    title={`Keluar: ${formatIDR(d.exp)}`}
                    style={{
                      height: chartReady ? `${Math.max(6, (d.exp / maxBar) * 100)}%` : '0%',
                      transition: 'height 620ms cubic-bezier(0.22, 1, 0.36, 1)',
                      transitionDelay: `${i * 55 + 40}ms`,
                    }}
                    className="w-3 sm:w-4 bg-[#BA1A1A] rounded-t opacity-80"
                  />
                </div>
                <span className="text-[11px] opacity-75">{d.label}</span>
              </div>
            ))}
          </div>
          <div className="flex items-center justify-center gap-6 mt-3 text-xs opacity-75">
            <span className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-sm bg-[#0061A4] dark:bg-[#9FCAFF]" /> Penjualan
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-sm bg-[#BA1A1A]" /> Pengeluaran
            </span>
          </div>
        </div>

        <div className="rounded-3xl bg-[#ECEEF4] dark:bg-[#1D2024] p-5">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-bold text-base">Obat Terjual Hari Ini</h3>
            <button
              onClick={() => onNavigate('riwayat')}
              className="text-xs font-bold text-[#0061A4] dark:text-[#9FCAFF]"
            >
              Riwayat Transaksi →
            </button>
          </div>
          {dash.bestSellers.length === 0 ? (
            <p className="text-sm opacity-60 py-8 text-center">
              Belum ada penjualan hari ini.
            </p>
          ) : (
            <div className="space-y-2.5">
              {dash.bestSellers.map((b, i) => (
                <div
                  key={i}
                  className="flex items-center justify-between p-3 rounded-2xl bg-[#E6E8EE] dark:bg-[#272A2F]"
                >
                  <div>
                    <p className="font-bold text-sm">{b.name}</p>
                    <p className="text-xs opacity-70">{b.qty} unit terjual</p>
                  </div>
                  <p className="font-bold text-sm text-[#0061A4] dark:text-[#9FCAFF]">
                    {formatIDR(b.revenue)}
                  </p>
                </div>
              ))}
            </div>
          )}
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
    exportRowsToXlsx(
      `stok-apotek-lestari-${dayKeyID()}.xlsx`,
      'Stok Obat',
      rows,
      {
        title: 'APOTEK LESTARI — LAPORAN STOK OBAT',
        subtitle: `Dicetak: ${new Date().toLocaleString('id-ID')} | Total SKU: ${rows.length}`,
        currencyColumns: ['Harga Beli', 'Harga Jual Ecer', 'Harga Grosir', 'Nilai Stok (Rp)'],
        sumColumns: ['Stok', 'Nilai Stok (Rp)'],
      }
    );
  };

  const handleImportSpreadsheet = async (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const matrix = await readSpreadsheetMatrix(file);
      if (matrix.length < 2) throw new Error('File kosong');
      const headerIdx = matrix.findIndex(
        (cols) => cols[0]?.trim().toLowerCase() === 'nama obat'
      );
      const dataRows = headerIdx >= 0 ? matrix.slice(headerIdx + 1) : matrix.slice(1);
      const rows = dataRows
        .filter((cols) => cols[0] && cols[0].trim().toUpperCase() !== 'TOTAL')
        .map((cols) => ({
        name: cols[0] || '',
        category: cols[1] || 'Umum',
        unit: cols[2] || 'Strip',
        buyPrice: parseInt(cols[3], 10) || 0,
        sellPrice: parseInt(cols[4], 10) || 0,
        wholesalePrice: parseInt(cols[5], 10) || 0,
        stock: parseInt(cols[6], 10) || 0,
        expiryDate: cols[7] || null,
        barcode: cols[8] || null,
      }));
      const res = bulkImportMedicines(rows);
      setMsg(
        `Impor selesai: ${res.inserted} baru, ${res.updated} diperbarui, ${res.skipped} dilewati.`
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
        subtitle={`${medicines.length} jenis obat terdaftar • Sinkron otomatis dengan aplikasi Android`}
        action={
          <>
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
              className="px-4 py-2 rounded-xl bg-[#0061A4] hover:bg-[#00497D] text-white text-xs font-bold flex items-center gap-1.5"
            >
              <Plus className="w-4 h-4" /> Tambah Obat
            </button>
          </>
        }
      />

      {msg && (
        <div className="mb-4 p-3 rounded-2xl bg-[#0061A4]/15 border border-[#0061A4]/30 text-sm flex items-center justify-between">
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
            className="w-full pl-10 pr-4 py-2.5 rounded-2xl bg-[#E6E8EE] dark:bg-[#272A2F] text-sm focus:outline-none focus:ring-2 focus:ring-[#0061A4]"
          />
        </div>
        <select
          value={catFilter}
          onChange={(e) => setCatFilter(e.target.value)}
          className="px-3.5 py-2.5 rounded-2xl bg-[#E6E8EE] dark:bg-[#272A2F] text-sm font-medium"
        >
          <option value="Semua">Semua Kategori</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <div className="flex rounded-2xl bg-[#E6E8EE] dark:bg-[#272A2F] p-1 text-xs font-bold">
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
                stockFilter === k ? 'bg-[#0061A4] text-white' : 'opacity-70'
              }`}
            >
              {lbl}
            </button>
          ))}
        </div>
        <button
          onClick={() => setSortAz(!sortAz)}
          className="px-3.5 py-2.5 rounded-2xl bg-[#E6E8EE] dark:bg-[#272A2F] text-xs font-bold"
        >
          Urut: {sortAz ? 'A-Z' : 'Z-A'}
        </button>
      </div>

      {/* Desktop Table / Card List */}
      <div className="rounded-3xl bg-[#ECEEF4] dark:bg-[#1D2024] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-[#E1E2E8] dark:bg-[#272A2F] text-xs uppercase opacity-75">
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
                      <td className="py-3 px-3 text-right font-bold text-[#0061A4] dark:text-[#9FCAFF]">
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
                        <div className="inline-flex items-center gap-1">
                          <button
                            onClick={() => openEdit(m)}
                            className="p-2 rounded-xl hover:bg-black/10 dark:hover:bg-white/10"
                            title="Edit Obat"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
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
                            className="p-2 rounded-xl text-[#BA1A1A] hover:bg-red-500/10"
                            title="Hapus Obat"
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
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
          <div className="bg-[#F8F9FF] dark:bg-[#1D2024] rounded-3xl max-w-lg w-full p-6 shadow-2xl max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-extrabold">
                {editing ? 'Ubah Data Obat' : 'Tambah Obat Baru'}
              </h3>
              <button onClick={() => setShowModal(false)} className="p-1">
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
                  className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
                  placeholder="Contoh: Paracetamol 500mg"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold mb-1">Kategori</label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
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
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
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
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
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
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
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
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
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
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold mb-1">Tgl Kadaluarsa</label>
                  <input
                    type="date"
                    value={expiryDate}
                    onChange={(e) => setExpiryDate(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold mb-1">Barcode</label>
                  <input
                    value={barcode}
                    onChange={(e) => setBarcode(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F]"
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
                  className="px-5 py-2.5 rounded-xl bg-[#0061A4] text-white font-bold"
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

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
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
                    ? 'bg-[#0061A4] text-white'
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
                    ? 'bg-[#0061A4] text-white'
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
              className="flex-1 py-2.5 rounded-xl bg-[#0061A4] text-white font-bold text-sm flex items-center justify-center gap-2"
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
    </div>
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
              className="w-full pl-10 pr-14 py-2.5 rounded-2xl bg-[#E6E8EE] dark:bg-[#272A2F] text-sm focus:outline-none focus:ring-2 focus:ring-[#0061A4]"
            />
            <kbd className="hidden sm:inline-block absolute right-3 top-2.5 px-1.5 py-0.5 rounded bg-black/10 dark:bg-white/10 text-[10px] font-bold opacity-70">
              F2
            </kbd>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-h-[65vh] overflow-y-auto pr-1">
            {filteredMeds.map((m) => {
              const inCart = cart.find((c) => c.medicineId === m.id)?.qty || 0;
              return (
                <button
                  key={m.id}
                  disabled={m.stock <= 0}
                  onClick={() => addToCart(m)}
                  className={`text-left p-4 rounded-2xl transition flex flex-col justify-between border ${
                    inCart > 0
                      ? 'bg-[#0061A4]/15 border-[#0061A4]'
                      : 'bg-[#ECEEF4] dark:bg-[#1D2024] border-transparent hover:border-[#0061A4]/40'
                  } ${m.stock <= 0 ? 'opacity-50 cursor-not-allowed' : ''}`}
                >
                  <div>
                    <div className="flex items-start justify-between gap-2">
                      <p className="font-bold text-sm">{m.name}</p>
                      {inCart > 0 && (
                        <span className="px-2 py-0.5 rounded-full bg-[#0061A4] text-white text-xs font-bold">
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
                      <p className="font-extrabold text-sm text-[#0061A4] dark:text-[#9FCAFF]">
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
        <div className="lg:col-span-5">
          <div className="rounded-3xl bg-[#ECEEF4] dark:bg-[#1D2024] p-5 sticky top-4 space-y-4">
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
                      className="p-3 rounded-2xl bg-[#E6E8EE] dark:bg-[#272A2F] space-y-2"
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
                              className={`px-2.5 py-1 rounded-lg ${
                                !c.useWholesale
                                  ? 'bg-[#0061A4] text-white'
                                  : 'bg-black/10 dark:bg-white/10'
                              }`}
                            >
                              Ecer
                            </button>
                            <button
                              onClick={() => !c.useWholesale && toggleWholesale(m.id)}
                              className={`px-2.5 py-1 rounded-lg ${
                                c.useWholesale
                                  ? 'bg-[#0061A4] text-white'
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
                            className="w-7 h-7 rounded-lg bg-black/10 dark:bg-white/10 font-bold"
                          >
                            -
                          </button>
                          <span className="font-bold text-sm w-6 text-center">
                            {c.qty}
                          </span>
                          <button
                            onClick={() => updateQty(m.id, c.qty + 1)}
                            className="w-7 h-7 rounded-lg bg-black/10 dark:bg-white/10 font-bold"
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
                <span className="text-2xl font-extrabold text-[#0061A4] dark:text-[#9FCAFF]">
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
                          ? 'bg-[#0061A4] text-white'
                          : 'bg-[#E6E8EE] dark:bg-[#272A2F]'
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
                    className="w-full px-3.5 py-2.5 rounded-xl bg-[#E6E8EE] dark:bg-[#272A2F] font-bold text-sm"
                  />
                  <div className="flex flex-wrap gap-1.5 mt-2">
                    {[cartTotal, 20000, 50000, 100000]
                      .filter((v, i, a) => v > 0 && a.indexOf(v) === i)
                      .map((nom) => (
                        <button
                          key={nom}
                          type="button"
                          onClick={() => setAmountPaidStr(String(nom))}
                          className="px-2.5 py-1 rounded-lg bg-[#E6E8EE] dark:bg-[#272A2F] text-xs font-bold hover:bg-[#0061A4] hover:text-white transition"
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
                className="w-full py-3.5 rounded-2xl bg-[#0061A4] hover:bg-[#00497D] disabled:opacity-40 text-white font-extrabold text-sm flex items-center justify-center gap-2 transition"
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
