import React, { useState, useMemo } from 'react';
import { useStore } from '../store';
import { useFeedback } from './Feedback';
import { exportRowsToXlsx } from '../excel';
import {
  UNITS,
  Sale,
  SaleItem,
  formatIDR,
  jakartaDayStartMillis,
  jakartaMonthStartMillis,
  parseISODate,
  dayKeyID,
  monthKeyID,
  formatDateTimeID,
  formatDateID,
  daysUntilExpiry,
  dueDaysLeft,
  namaBulanIndonesia,
} from '../types';
import { PageHeader, StockPill, ReceiptModal } from './ScreensPart1';
import { AnimatedNumber } from './AnimatedNumber';
import {
  Plus,
  Trash2,
  Printer,
  Download,
  Upload,
  CheckCircle,
  Lock,
  Unlock,
  AlertTriangle,
  Calendar,
} from 'lucide-react';

// Helper for previous months list in Tutup Buku
function getPastMonthsList(count = 12): string[] {
  const list: string[] = [];
  const nowM = monthKeyID(new Date());
  let [y, m] = nowM.split('-').map(Number);
  for (let i = 0; i < count; i++) {
    m -= 1;
    if (m === 0) {
      m = 12;
      y -= 1;
    }
    list.push(`${y}-${String(m).padStart(2, '0')}`);
  }
  return list;
}

// ============================================================================
// 4. KEUANGAN / KAS SCREEN (Matches KeuanganScreen.kt)
// ============================================================================
export const KeuanganScreen: React.FC = () => {
  const { cashEntries, createCashEntry, deleteCashEntry } = useStore();
  const { notify } = useFeedback();
  const [period, setPeriod] = useState<'today' | 'month' | 'all'>('month');
  const [type, setType] = useState<'in' | 'out'>('out');
  const [category, setCategory] = useState('Operasional');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');

  const startToday = jakartaDayStartMillis(0);
  const endToday = jakartaDayStartMillis(1);
  const startMonth = jakartaMonthStartMillis();

  const filtered = useMemo(() => {
    return cashEntries
      .filter((e) => {
        if (period === 'all') return true;
        const t = parseISODate(e.createdAt)?.getTime() ?? -1;
        if (period === 'today') return t >= startToday && t < endToday;
        return t >= startMonth && t < endToday;
      })
      .sort(
        (a, b) =>
          (parseISODate(b.createdAt)?.getTime() ?? 0) -
          (parseISODate(a.createdAt)?.getTime() ?? 0)
      );
  }, [cashEntries, period, startToday, endToday, startMonth]);

  const totals = useMemo(() => {
    let totalIn = 0,
      totalOut = 0,
      allBalance = 0;
    for (const e of cashEntries) {
      if (e.type === 'in') allBalance += e.amount;
      else allBalance -= e.amount;
    }
    for (const e of filtered) {
      if (e.type === 'in') totalIn += e.amount;
      else totalOut += e.amount;
    }
    return { totalIn, totalOut, allBalance };
  }, [cashEntries, filtered]);

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    try {
      createCashEntry(type, category, description, parseInt(amount, 10) || 0);
      setDescription('');
      setAmount('');
      notify('Catatan kas tersimpan', 'success');
    } catch (err: any) {
      notify(err.message, 'error');
    }
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Keuangan & Arus Kas"
        subtitle={`Saldo Kas Keseluruhan: ${formatIDR(totals.allBalance)}`}
      />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Form Catat Kas */}
        <div className="lg:col-span-4">
          <form
            onSubmit={handleAdd}
            className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-3.5 text-sm"
          >
            <h3 className="font-extrabold text-base">Catat Kas Manual</h3>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => {
                  setType('in');
                  setCategory('Tambah Modal');
                }}
                className={`py-2.5 rounded-xl font-bold text-xs ${
                  type === 'in'
                    ? 'bg-[#15803D] text-white'
                    : 'bg-[#FAF8F4] dark:bg-[#0F241E]'
                }`}
              >
                + Kas Masuk
              </button>
              <button
                type="button"
                onClick={() => {
                  setType('out');
                  setCategory('Operasional');
                }}
                className={`py-2.5 rounded-xl font-bold text-xs ${
                  type === 'out'
                    ? 'bg-[#BA1A1A] text-white'
                    : 'bg-[#FAF8F4] dark:bg-[#0F241E]'
                }`}
              >
                - Kas Keluar
              </button>
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Kategori</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
              >
                {type === 'out'
                  ? ['Operasional', 'Beli Stok', 'Listrik & Air', 'Gaji', 'Lainnya'].map(
                      (c) => (
                        <option key={c} value={c}>
                          {c}
                        </option>
                      )
                    )
                  : ['Tambah Modal', 'Pendapatan Lain', 'Lainnya'].map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Nominal (Rp) *</label>
              <input
                required
                type="number"
                min="1"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="50000"
                className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Keterangan</label>
              <input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Contoh: Token listrik apotek"
                className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
              />
            </div>
            <button
              type="submit"
              className="w-full py-3 rounded-xl bg-[#15803D] text-white font-bold"
            >
              Simpan Catatan Kas
            </button>
          </form>
        </div>

        {/* Daftar Riwayat Kas */}
        <div className="lg:col-span-8 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex rounded-2xl bg-[#FAF8F4] dark:bg-[#0F241E] p-1 text-xs font-bold">
              {(
                [
                  ['today', 'Hari Ini'],
                  ['month', 'Bulan Ini'],
                  ['all', 'Semua'],
                ] as const
              ).map(([k, lbl]) => (
                <button
                  key={k}
                  onClick={() => setPeriod(k)}
                  className={`px-3.5 py-1.5 rounded-xl ${
                    period === k ? 'bg-[#15803D] text-white' : 'opacity-70'
                  }`}
                >
                  {lbl}
                </button>
              ))}
            </div>
            <div className="flex gap-4 text-xs font-bold">
              <span className="text-[#2E7D32] dark:text-[#7EDC9A]">
                Masuk: <AnimatedNumber value={totals.totalIn} format={formatIDR} />
              </span>
              <span className="text-[#BA1A1A]">
                Keluar: <AnimatedNumber value={totals.totalOut} format={formatIDR} />
              </span>
            </div>
          </div>

          <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] divide-y divide-black/5 dark:divide-white/5 overflow-hidden">
            {filtered.length === 0 ? (
              <p className="py-10 text-center text-sm opacity-60">
                Belum ada catatan kas pada periode ini.
              </p>
            ) : (
              filtered.map((e) => (
                <div
                  key={e.id}
                  className="p-4 flex items-center justify-between gap-3"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span
                        className={`px-2 py-0.5 rounded-md text-[10px] font-bold uppercase ${
                          e.type === 'in'
                            ? 'bg-[#15803D]/20 text-[#15803D] dark:text-[#86EFAC]'
                            : 'bg-[#BA1A1A]/20 text-[#BA1A1A]'
                        }`}
                      >
                        {e.category}
                      </span>
                      <span className="text-xs opacity-60">
                        {formatDateTimeID(e.createdAt)}
                      </span>
                    </div>
                    <p className="font-bold text-sm mt-1">
                      {e.description || e.category}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={`font-extrabold text-sm ${
                        e.type === 'in'
                          ? 'text-[#2E7D32] dark:text-[#7EDC9A]'
                          : 'text-[#BA1A1A]'
                      }`}
                    >
                      {e.type === 'in' ? '+' : '-'} {formatIDR(e.amount)}
                    </span>
                    {e.source !== 'sale' && (
                      <button
                        onClick={() => deleteCashEntry(e.id)}
                        className="p-1.5 rounded-lg text-[#BA1A1A] hover:bg-red-500/10"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

// ============================================================================
// 5. LAPORAN LABA & HPP SCREEN (Matches LabaScreen.kt + Repository.getProfitReport)
// ============================================================================
export const LabaScreen: React.FC = () => {
  const { medicines, sales, saleItems, cashEntries } = useStore();
  const [period, setPeriod] = useState<'today' | 'month' | 'last30' | 'all'>('today');

  const report = useMemo(() => {
    const startToday = jakartaDayStartMillis(0);
    const endToday = jakartaDayStartMillis(1);
    const startMonth = jakartaMonthStartMillis();
    const startLast30 = jakartaDayStartMillis(-29);
    const start =
      period === 'today'
        ? startToday
        : period === 'month'
        ? startMonth
        : period === 'last30'
        ? startLast30
        : 0;
    const end = period === 'all' ? Number.MAX_SAFE_INTEGER : endToday;

    const inPeriod = (iso: string) => {
      const t = parseISODate(iso)?.getTime();
      return t !== undefined && t >= start && t < end;
    };

    const periodSales = sales.filter((s) => inPeriod(s.createdAt));
    const saleIds = new Set(periodSales.map((s) => s.id));
    const pItems = saleItems.filter((it) => saleIds.has(it.saleId));

    const revenue = pItems.reduce((a, it) => a + it.subtotal, 0);
    const itemsSold = pItems.reduce((a, it) => a + it.qty, 0);
    const hpp = pItems.reduce((a, it) => a + it.qty * (it.buyPrice || 0), 0);
    const grossProfit = revenue - hpp;
    const grossMarginPct = revenue > 0 ? (grossProfit / revenue) * 100 : 0;

    const periodOut = cashEntries.filter(
      (e) => e.type === 'out' && inPeriod(e.createdAt)
    );
    const operationalExpense = periodOut
      .filter((e) => e.category.trim().toLowerCase() !== 'beli stok')
      .reduce((a, e) => a + e.amount, 0);
    const stockPurchaseExpense = periodOut
      .filter((e) => e.category.trim().toLowerCase() === 'beli stok')
      .reduce((a, e) => a + e.amount, 0);

    const netProfit = grossProfit - operationalExpense;
    const marginPct = revenue > 0 ? (netProfit / revenue) * 100 : 0;

    const medById = new Map(medicines.map((m) => [m.id, m]));
    const byMedMap = new Map<
      string,
      { name: string; category: string; qty: number; revenue: number; hpp: number }
    >();
    for (const it of pItems) {
      const key = it.medicineId > 0 ? String(it.medicineId) : `name:${it.medicineName}`;
      const med = medById.get(it.medicineId);
      const cur = byMedMap.get(key) || {
        name: med?.name || it.medicineName,
        category: med?.category || it.category || 'Umum',
        qty: 0,
        revenue: 0,
        hpp: 0,
      };
      cur.qty += it.qty;
      cur.revenue += it.subtotal;
      cur.hpp += it.qty * (it.buyPrice || 0);
      byMedMap.set(key, cur);
    }

    return {
      revenue,
      hpp,
      grossProfit,
      grossMarginPct,
      operationalExpense,
      stockPurchaseExpense,
      netProfit,
      marginPct,
      transactionCount: periodSales.length,
      itemsSold,
      byMedicine: Array.from(byMedMap.values()).sort(
        (a, b) => b.revenue - a.revenue
      ),
    };
  }, [medicines, sales, saleItems, cashEntries, period]);

  const exportRekapExcel = () => {
    const periodLabel =
      period === 'today'
        ? 'Hari Ini'
        : period === 'month'
          ? 'Bulan Ini'
          : period === 'last30'
            ? '30 Hari Terakhir'
            : 'Semua Waktu';
    const rows = report.byMedicine.map((m) => {
      const laba = m.revenue - m.hpp;
      return {
        'Nama Obat': m.name,
        Kategori: m.category,
        'Qty Terjual': m.qty,
        'Omzet (Rp)': m.revenue,
        'HPP (Rp)': m.hpp,
        'Laba Kotor (Rp)': laba,
        'Margin (%)': m.revenue > 0 ? laba / m.revenue : 0,
      };
    });
    exportRowsToXlsx(
      `rekap-laba-apotek-${period}-${dayKeyID()}.xlsx`,
      'Rekap Laba',
      rows,
      {
        title: `APOTEK LESTARI — REKAP LABA & HPP (${periodLabel.toUpperCase()})`,
        subtitle: `Dicetak: ${new Date().toLocaleString('id-ID')} | Produk Terjual: ${rows.length}`,
        currencyColumns: ['Omzet (Rp)', 'HPP (Rp)', 'Laba Kotor (Rp)'],
        percentColumns: ['Margin (%)'],
        sumColumns: ['Qty Terjual', 'Omzet (Rp)', 'HPP (Rp)', 'Laba Kotor (Rp)'],
        totalRatioColumns: { 'Margin (%)': ['Laba Kotor (Rp)', 'Omzet (Rp)'] },
      }
    );
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Laporan Laba & HPP"
        subtitle="Perhitungan Laba Kotor (Omzet − HPP snapshot) & Laba Bersih"
        action={
          <button
            onClick={exportRekapExcel}
            className="px-3.5 py-2 rounded-xl bg-[#15803D] text-white text-xs font-bold flex items-center gap-1.5"
          >
            <Download className="w-4 h-4" /> Ekspor Rekap Excel
          </button>
        }
      />

      <div className="rounded-2xl bg-white dark:bg-[#142E27] border border-[#E7E3D8] dark:border-[#244C41] p-3.5 sm:p-4 shadow-xs min-w-0">
        <div className="flex flex-wrap gap-2 min-w-0" role="group" aria-label="Filter periode laporan laba">
          {(
            [
              ['today', 'Hari Ini'],
              ['month', 'Bulan Ini'],
              ['last30', '30 Hari Terakhir'],
              ['all', 'Semua Waktu'],
            ] as const
          ).map(([k, lbl]) => {
            const isActive = period === k;
            return (
              <button
                key={k}
                type="button"
                onClick={() => setPeriod(k)}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border text-xs font-semibold transition whitespace-nowrap ${
                  isActive
                    ? 'bg-[#142E27] text-white border-[#142E27] dark:bg-[#15803D] dark:border-[#15803D]'
                    : 'bg-[#FAF8F4] dark:bg-[#0F241E] text-[#78716C] dark:text-[#93B5A9] border-[#E7E3D8] dark:border-[#244C41] hover:border-[#15803D]/50 hover:text-[#1C1917] dark:hover:text-white'
                }`}
              >
                {lbl}
              </button>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5">
          <span className="text-xs opacity-70">Total Penjualan (Omzet)</span>
          <p className="text-2xl font-extrabold mt-1">
            <AnimatedNumber value={report.revenue} format={formatIDR} />
          </p>
          <span className="text-xs opacity-60">
            {report.transactionCount} transaksi • {report.itemsSold} unit
          </span>
        </div>
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5">
          <span className="text-xs opacity-70">HPP (Modal Obat Terjual)</span>
          <p className="text-2xl font-extrabold mt-1 text-amber-500">
            <AnimatedNumber value={report.hpp} format={formatIDR} />
          </p>
          <span className="text-xs opacity-60">
            Laba Kotor: {formatIDR(report.grossProfit)} (
            {report.grossMarginPct.toFixed(1)}%)
          </span>
        </div>
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5">
          <span className="text-xs opacity-70">Biaya Operasional</span>
          <p className="text-2xl font-extrabold mt-1 text-[#BA1A1A]">
            <AnimatedNumber value={report.operationalExpense} format={formatIDR} />
          </p>
          <span className="text-xs opacity-60">
            Kas keluar selain Beli Stok ({formatIDR(report.stockPurchaseExpense)})
          </span>
        </div>
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5">
          <span className="text-xs opacity-70">Laba Bersih</span>
          <p className="text-2xl font-extrabold mt-1 text-emerald-600 dark:text-[#7EDC9A]">
            <AnimatedNumber value={report.netProfit} format={formatIDR} />
          </p>
          <span className="text-xs opacity-60">
            Margin Bersih: {report.marginPct.toFixed(1)}%
          </span>
        </div>
      </div>

      {/* Breakdown per Medicine */}
      <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] overflow-hidden">
        <div className="p-4 font-extrabold text-sm border-b border-black/5 dark:border-white/5">
          Rincian Penjualan & Laba per Obat ({report.byMedicine.length} jenis)
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-[#E1E2E8] dark:bg-[#0F241E] text-xs uppercase opacity-75">
              <tr>
                <th className="py-3 px-4">Nama Obat</th>
                <th className="py-3 px-3">Kategori</th>
                <th className="py-3 px-3 text-right">Terjual</th>
                <th className="py-3 px-3 text-right">Omzet</th>
                <th className="py-3 px-3 text-right">HPP</th>
                <th className="py-3 px-4 text-right">Laba Kotor</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-black/5 dark:divide-white/5">
              {report.byMedicine.map((m, i) => (
                <tr key={i}>
                  <td className="py-3 px-4 font-bold">{m.name}</td>
                  <td className="py-3 px-3 text-xs opacity-75">{m.category}</td>
                  <td className="py-3 px-3 text-right font-bold">{m.qty}</td>
                  <td className="py-3 px-3 text-right">{formatIDR(m.revenue)}</td>
                  <td className="py-3 px-3 text-right opacity-75">
                    {formatIDR(m.hpp)}
                  </td>
                  <td className="py-3 px-4 text-right font-bold text-[#2E7D32] dark:text-[#7EDC9A]">
                    {formatIDR(m.revenue - m.hpp)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

// ============================================================================
// 6. FAKTUR PBF & PEMASOK SCREEN (Matches PbfScreen.kt & PemasokScreen.kt)
// ============================================================================
export const PbfScreen: React.FC = () => {
  const {
    purchases,
    purchaseItems,
    suppliers,
    createPurchase,
    markPurchasePaid,
    deletePurchase,
    saveSupplier,
    deleteSupplier,
  } = useStore();
  const { notify, askConfirm } = useFeedback();

  const [tab, setTab] = useState<'FAKTUR' | 'PEMASOK'>('FAKTUR');
  const [pbfName, setPbfName] = useState('');
  const [invoiceNo, setInvoiceNo] = useState('');
  const [date, setDate] = useState(dayKeyID());
  const [paymentType, setPaymentType] = useState<'CASH' | 'KREDIT'>('CASH');
  const [dueDate, setDueDate] = useState('');
  const [ppn, setPpn] = useState('');
  const [items, setItems] = useState<
    Array<{ medicineName: string; qty: string; unit: string; buyPrice: string }>
  >([{ medicineName: '', qty: '1', unit: 'Box', buyPrice: '' }]);

  const [supName, setSupName] = useState('');
  const [supContact, setSupContact] = useState('');

  const handleSavePurchase = (e: React.FormEvent) => {
    e.preventDefault();
    try {
      createPurchase({
        pbfName,
        invoiceNo,
        date,
        ppn: parseInt(ppn, 10) || 0,
        paymentType,
        dueDate,
        items: items.map((it) => ({
          medicineName: it.medicineName,
          qty: parseInt(it.qty, 10) || 0,
          unit: it.unit,
          buyPrice: parseInt(it.buyPrice, 10) || 0,
        })),
      });
      setPbfName('');
      setInvoiceNo('');
      setPpn('');
      setItems([{ medicineName: '', qty: '1', unit: 'Box', buyPrice: '' }]);
      notify('Faktur PBF tersimpan', 'success');
    } catch (err: any) {
      notify(err.message, 'error');
    }
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Faktur PBF & Daftar Pemasok"
        subtitle="Catatan belanja PBF (Tunai/Kredit), pengingat jatuh tempo, dan kontak pemasok"
        action={
          <div className="flex rounded-xl bg-white/15 p-1 text-xs font-bold">
            <button
              onClick={() => setTab('FAKTUR')}
              className={`px-3 py-1.5 rounded-lg ${
                tab === 'FAKTUR' ? 'bg-[#15803D] text-white' : ''
              }`}
            >
              Faktur PBF ({purchases.length})
            </button>
            <button
              onClick={() => setTab('PEMASOK')}
              className={`px-3 py-1.5 rounded-lg ${
                tab === 'PEMASOK' ? 'bg-[#15803D] text-white' : ''
              }`}
            >
              Pemasok ({suppliers.length})
            </button>
          </div>
        }
      />

      {tab === 'FAKTUR' ? (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <form
            onSubmit={handleSavePurchase}
            className="lg:col-span-5 rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-3 text-sm h-fit"
          >
            <h3 className="font-extrabold text-base">Catat Faktur PBF Baru</h3>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold mb-1">Nama PBF *</label>
                <input
                  required
                  list="pbf-suppliers-list"
                  value={pbfName}
                  onChange={(e) => setPbfName(e.target.value)}
                  placeholder="Pilih / ketik PBF"
                  className="w-full px-3 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                />
                <datalist id="pbf-suppliers-list">
                  {suppliers.map((s) => (
                    <option key={s.id} value={s.name} />
                  ))}
                </datalist>
              </div>
              <div>
                <label className="block text-xs font-bold mb-1">No. Faktur</label>
                <input
                  value={invoiceNo}
                  onChange={(e) => setInvoiceNo(e.target.value)}
                  placeholder="Opsional"
                  className="w-full px-3 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="block text-xs font-bold mb-1">Tgl Beli *</label>
                <input
                  required
                  type="date"
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold mb-1">Pembayaran</label>
                <select
                  value={paymentType}
                  onChange={(e) =>
                    setPaymentType(e.target.value as 'CASH' | 'KREDIT')
                  }
                  className="w-full px-3 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                >
                  <option value="CASH">TUNAI (CASH)</option>
                  <option value="KREDIT">KREDIT (TEMPO)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-bold mb-1">PPN (Rp)</label>
                <input
                  type="number"
                  min="0"
                  value={ppn}
                  onChange={(e) => setPpn(e.target.value)}
                  placeholder="0"
                  className="w-full px-3 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                />
              </div>
            </div>

            {paymentType === 'KREDIT' && (
              <div>
                <label className="block text-xs font-bold mb-1">
                  Tgl Jatuh Tempo Kredit
                </label>
                <input
                  type="date"
                  value={dueDate}
                  onChange={(e) => setDueDate(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
                />
              </div>
            )}

            <div className="space-y-2 pt-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-bold">Daftar Obat Dibeli</label>
                <button
                  type="button"
                  onClick={() =>
                    setItems([
                      ...items,
                      { medicineName: '', qty: '1', unit: 'Box', buyPrice: '' },
                    ])
                  }
                  className="text-xs font-bold text-[#15803D] dark:text-[#86EFAC]"
                >
                  + Tambah Baris
                </button>
              </div>
              {items.map((it, idx) => (
                <div key={idx} className="grid grid-cols-12 gap-1.5 items-center">
                  <input
                    required
                    placeholder="Nama obat"
                    value={it.medicineName}
                    onChange={(e) =>
                      setItems(
                        items.map((x, i) =>
                          i === idx ? { ...x, medicineName: e.target.value } : x
                        )
                      )
                    }
                    className="col-span-5 px-2.5 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E] text-xs"
                  />
                  <input
                    required
                    type="number"
                    min="1"
                    placeholder="Qty"
                    value={it.qty}
                    onChange={(e) =>
                      setItems(
                        items.map((x, i) =>
                          i === idx ? { ...x, qty: e.target.value } : x
                        )
                      )
                    }
                    className="col-span-2 px-2 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E] text-xs"
                  />
                  <input
                    required
                    type="number"
                    min="1"
                    placeholder="Harga"
                    value={it.buyPrice}
                    onChange={(e) =>
                      setItems(
                        items.map((x, i) =>
                          i === idx ? { ...x, buyPrice: e.target.value } : x
                        )
                      )
                    }
                    className="col-span-4 px-2.5 py-2 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E] text-xs"
                  />
                  {items.length > 1 && (
                    <button
                      type="button"
                      onClick={() => setItems(items.filter((_, i) => i !== idx))}
                      className="col-span-1 text-[#BA1A1A] flex justify-center"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              ))}
            </div>

            <button
              type="submit"
              className="w-full py-3 rounded-xl bg-[#15803D] text-white font-bold"
            >
              Simpan Faktur PBF
            </button>
          </form>

          <div className="lg:col-span-7 space-y-3">
            {purchases.length === 0 ? (
              <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-10 text-center text-sm opacity-60">
                Belum ada catatan faktur pembelian PBF.
              </div>
            ) : (
              purchases.map((p) => {
                const pItems = purchaseItems.filter((it) => it.purchaseId === p.id);
                const left = dueDaysLeft(p.dueDate);
                return (
                  <div
                    key={p.id}
                    className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-3"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-extrabold text-base">
                            {p.pbfName}
                          </span>
                          <span
                            className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold ${
                              p.paymentType === 'KREDIT'
                                ? 'bg-amber-500/20 text-amber-500'
                                : 'bg-[#15803D]/20 text-[#15803D] dark:text-[#86EFAC]'
                            }`}
                          >
                            {p.paymentType}
                          </span>
                        </div>
                        <p className="text-xs opacity-70">
                          Tgl: {formatDateID(p.date)}
                          {p.invoiceNo && ` • Faktur: ${p.invoiceNo}`}
                          {p.paymentType === 'KREDIT' &&
                            p.dueDate &&
                            ` • Tempo: ${formatDateID(p.dueDate)} (${
                              left !== null ? `${left} hari` : ''
                            })`}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="font-extrabold text-base text-[#15803D] dark:text-[#86EFAC]">
                          {formatIDR(p.total)}
                        </p>
                        {p.ppn > 0 && (
                          <p className="text-[11px] opacity-65">
                            Termasuk PPN {formatIDR(p.ppn)}
                          </p>
                        )}
                      </div>
                    </div>

                    <div className="text-xs space-y-1 bg-[#FAF8F4] dark:bg-[#0F241E] p-3 rounded-2xl">
                      {pItems.map((it) => (
                        <div key={it.id} className="flex justify-between">
                          <span>
                            {it.medicineName} ({it.qty} {it.unit} x{' '}
                            {formatIDR(it.buyPrice)})
                          </span>
                          <span className="font-bold">
                            {formatIDR(it.subtotal)}
                          </span>
                        </div>
                      ))}
                    </div>

                    <div className="flex justify-end gap-2">
                      {p.paymentType === 'KREDIT' && (
                        <button
                          onClick={() => markPurchasePaid(p.id)}
                          className="px-3 py-1.5 rounded-xl bg-[#43A047] text-white text-xs font-bold flex items-center gap-1"
                        >
                          <CheckCircle className="w-3.5 h-3.5" /> Tandai Lunas
                        </button>
                      )}
                      <button
                        onClick={() =>
                          askConfirm(
                            `Hapus faktur ${p.pbfName}?`,
                            () => {
                              deletePurchase(p.id);
                              notify(`Faktur ${p.pbfName} dihapus`, 'info');
                            },
                            { title: 'Hapus Faktur PBF', danger: true, confirmLabel: 'Hapus' }
                          )
                        }
                        className="px-3 py-1.5 rounded-xl bg-red-500/15 text-[#BA1A1A] text-xs font-bold"
                      >
                        Hapus
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              try {
                saveSupplier(supName, supContact);
                setSupName('');
                setSupContact('');
                notify('Pemasok ditambahkan', 'success');
              } catch (err: any) {
                notify(err.message, 'error');
              }
            }}
            className="lg:col-span-4 rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-3 text-sm h-fit"
          >
            <h3 className="font-extrabold text-base">Tambah Pemasok PBF</h3>
            <div>
              <label className="block text-xs font-bold mb-1">Nama Pemasok *</label>
              <input
                required
                value={supName}
                onChange={(e) => setSupName(e.target.value)}
                placeholder="Contoh: PT Enseval Putera"
                className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">
                Kontak / WhatsApp
              </label>
              <input
                value={supContact}
                onChange={(e) => setSupContact(e.target.value)}
                placeholder="0812..."
                className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E]"
              />
            </div>
            <button
              type="submit"
              className="w-full py-2.5 rounded-xl bg-[#15803D] text-white font-bold"
            >
              Simpan Pemasok
            </button>
          </form>

          <div className="lg:col-span-8 rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] divide-y divide-black/5 dark:divide-white/5">
            {suppliers.length === 0 ? (
              <p className="p-10 text-center text-sm opacity-60">
                Belum ada pemasok terdaftar.
              </p>
            ) : (
              suppliers.map((s) => (
                <div
                  key={s.id}
                  className="p-4 flex items-center justify-between"
                >
                  <div>
                    <p className="font-bold text-sm">{s.name}</p>
                    <p className="text-xs opacity-70">
                      {s.contact || 'Tanpa nomor kontak'}
                    </p>
                  </div>
                  <button
                    onClick={() => deleteSupplier(s.id)}
                    className="p-2 rounded-xl text-[#BA1A1A] hover:bg-red-500/10"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};

// ============================================================================
// 7. PENGINGAT STOK, KADALUARSA & JATUH TEMPO PBF (Matches PengingatScreen.kt)
// ============================================================================
export const PengingatScreen: React.FC = () => {
  const { medicines, purchases, markPurchasePaid } = useStore();

  const lowStock = useMemo(
    () =>
      medicines
        .filter((m) => m.stock < 10)
        .sort((a, b) => a.stock - b.stock),
    [medicines]
  );

  const expiring = useMemo(
    () =>
      medicines
        .filter((m) => {
          const d = daysUntilExpiry(m.expiryDate);
          return d !== null && d <= 30;
        })
        .sort(
          (a, b) =>
            (daysUntilExpiry(a.expiryDate) ?? 999) -
            (daysUntilExpiry(b.expiryDate) ?? 999)
        ),
    [medicines]
  );

  const creditPbf = useMemo(
    () =>
      purchases
        .filter((p) => p.paymentType === 'KREDIT')
        .sort((a, b) => (a.dueDate || '9999').localeCompare(b.dueDate || '9999')),
    [purchases]
  );

  return (
    <div className="space-y-5">
      <PageHeader
        title="Pengingat & Peringatan"
        subtitle="Pantau obat stok menipis, kadaluarsa ≤30 hari, dan tagihan kredit PBF"
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Stok Menipis */}
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-3">
          <h3 className="font-extrabold text-base flex items-center justify-between">
            <span>Stok Rendah / Habis</span>
            <span className="px-2.5 py-0.5 rounded-full bg-[#BA1A1A] text-white text-xs">
              {lowStock.length}
            </span>
          </h3>
          {lowStock.length === 0 ? (
            <p className="text-xs opacity-60 py-6 text-center">
              Semua stok obat aman (≥10).
            </p>
          ) : (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {lowStock.map((m) => (
                <div
                  key={m.id}
                  className="p-3 rounded-2xl bg-[#FAF8F4] dark:bg-[#0F241E] flex items-center justify-between"
                >
                  <div>
                    <p className="font-bold text-sm">{m.name}</p>
                    <p className="text-xs opacity-70">{m.category}</p>
                  </div>
                  <StockPill stock={m.stock} />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Kadaluarsa */}
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-3">
          <h3 className="font-extrabold text-base flex items-center justify-between">
            <span>Kadaluarsa ≤ 30 Hari</span>
            <span className="px-2.5 py-0.5 rounded-full bg-amber-500 text-white text-xs">
              {expiring.length}
            </span>
          </h3>
          {expiring.length === 0 ? (
            <p className="text-xs opacity-60 py-6 text-center">
              Tidak ada obat yang mendekati kadaluarsa.
            </p>
          ) : (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {expiring.map((m) => {
                const d = daysUntilExpiry(m.expiryDate) ?? 0;
                return (
                  <div
                    key={m.id}
                    className="p-3 rounded-2xl bg-[#FAF8F4] dark:bg-[#0F241E] flex items-center justify-between"
                  >
                    <div>
                      <p className="font-bold text-sm">{m.name}</p>
                      <p className="text-xs opacity-70">
                        Exp: {formatDateID(m.expiryDate)}
                      </p>
                    </div>
                    <span
                      className={`px-2.5 py-1 rounded-full text-xs font-bold text-white ${
                        d <= 0 ? 'bg-[#BA1A1A]' : 'bg-amber-600'
                      }`}
                    >
                      {d <= 0 ? 'Sudah Exp' : `${d} hari lagi`}
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Tagihan Kredit PBF */}
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-3">
          <h3 className="font-extrabold text-base flex items-center justify-between">
            <span>Faktur Kredit PBF</span>
            <span className="px-2.5 py-0.5 rounded-full bg-[#15803D] text-white text-xs">
              {creditPbf.length}
            </span>
          </h3>
          {creditPbf.length === 0 ? (
            <p className="text-xs opacity-60 py-6 text-center">
              Tidak ada hutang faktur PBF yang belum lunas.
            </p>
          ) : (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {creditPbf.map((p) => {
                const left = dueDaysLeft(p.dueDate);
                return (
                  <div
                    key={p.id}
                    className="p-3 rounded-2xl bg-[#FAF8F4] dark:bg-[#0F241E] space-y-2"
                  >
                    <div className="flex justify-between items-start">
                      <div>
                        <p className="font-bold text-sm">{p.pbfName}</p>
                        <p className="text-xs opacity-70">
                          Tempo: {p.dueDate ? formatDateID(p.dueDate) : 'Belum diatur'}
                        </p>
                      </div>
                      <p className="font-extrabold text-sm text-amber-500">
                        {formatIDR(p.total)}
                      </p>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold">
                        {left === null
                          ? ''
                          : left < 0
                          ? `Lewat ${Math.abs(left)} hari!`
                          : `${left} hari lagi`}
                      </span>
                      <button
                        onClick={() => markPurchasePaid(p.id)}
                        className="px-2.5 py-1 rounded-lg bg-[#43A047] text-white text-xs font-bold"
                      >
                        Lunasi
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

// ============================================================================
// 8. RIWAYAT PENJUALAN SCREEN (Matches RiwayatScreen.kt)
// ============================================================================
type PaymentFilter = 'ALL' | 'Tunai' | 'QRIS' | 'Transfer';

export const RiwayatScreen: React.FC = () => {
  const { sales, saleItems } = useStore();
  const [search, setSearch] = useState('');
  const [paymentFilter, setPaymentFilter] = useState<PaymentFilter>('ALL');
  const [selectedSale, setSelectedSale] = useState<{
    sale: Sale;
    items: SaleItem[];
  } | null>(null);

  const itemsBySale = useMemo(() => {
    const map = new Map<number, SaleItem[]>();
    for (const it of saleItems) {
      const arr = map.get(it.saleId) || [];
      arr.push(it);
      map.set(it.saleId, arr);
    }
    return map;
  }, [saleItems]);

  const monthByMethod = useMemo(() => {
    const startM = jakartaMonthStartMillis();
    const endT = jakartaDayStartMillis(1);
    const res = { Tunai: 0, QRIS: 0, Transfer: 0 };
    for (const s of sales) {
      const t = parseISODate(s.createdAt)?.getTime() ?? -1;
      if (t >= startM && t < endT) {
        res[s.paymentMethod] = (res[s.paymentMethod] || 0) + s.total;
      }
    }
    return res;
  }, [sales]);

  const methodCounts = useMemo(() => {
    const counts = { ALL: sales.length, Tunai: 0, QRIS: 0, Transfer: 0 };
    for (const s of sales) {
      if (s.paymentMethod === 'Tunai') counts.Tunai += 1;
      else if (s.paymentMethod === 'QRIS') counts.QRIS += 1;
      else if (s.paymentMethod === 'Transfer') counts.Transfer += 1;
    }
    return counts;
  }, [sales]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return [...sales]
      .sort(
        (a, b) =>
          (parseISODate(b.createdAt)?.getTime() ?? 0) -
          (parseISODate(a.createdAt)?.getTime() ?? 0)
      )
      .filter((s) => {
        if (paymentFilter !== 'ALL' && s.paymentMethod !== paymentFilter) return false;
        if (!q) return true;
        const its = itemsBySale.get(s.id) || [];
        return (
          s.invoiceNo.toLowerCase().includes(q) ||
          s.paymentMethod.toLowerCase().includes(q) ||
          its.some((i) => i.medicineName.toLowerCase().includes(q))
        );
      });
  }, [sales, itemsBySale, search, paymentFilter]);

  const filteredTotal = useMemo(
    () => filtered.reduce((sum, s) => sum + s.total, 0),
    [filtered]
  );

  const PILLS: { id: PaymentFilter; label: string; activeCls: string }[] = [
    {
      id: 'ALL',
      label: 'Semua',
      activeCls: 'bg-[#142E27] text-white border-[#142E27] dark:bg-[#15803D] dark:border-[#15803D]',
    },
    {
      id: 'Tunai',
      label: 'Cash',
      activeCls: 'bg-[#DCFCE7] text-[#166534] border-[#86EFAC] dark:bg-[#15803D]/30 dark:text-[#86EFAC] dark:border-[#15803D]',
    },
    {
      id: 'QRIS',
      label: 'QRIS',
      activeCls: 'bg-[#E0F2FE] text-[#0369A1] border-[#7DD3FC] dark:bg-[#0369A1]/30 dark:text-[#7DD3FC] dark:border-[#0369A1]',
    },
    {
      id: 'Transfer',
      label: 'Transfer',
      activeCls: 'bg-[#FEF3C7] text-[#B45309] border-[#FCD34D] dark:bg-[#B45309]/30 dark:text-[#FDE68A] dark:border-[#B45309]',
    },
  ];

  const activePillLabel = PILLS.find((p) => p.id === paymentFilter)?.label || 'Semua';

  return (
    <div className="space-y-4 min-w-0">
      <PageHeader
        title="Riwayat Penjualan"
        subtitle={`${sales.length} transaksi tercatat • Klik Cetak Struk untuk mencetak ulang`}
      />

      {/* MB1 Month Summary by Payment Method */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {(['Tunai', 'QRIS', 'Transfer'] as const).map((m) => (
          <div
            key={m}
            className="rounded-2xl bg-white dark:bg-[#142E27] border border-[#E7E3D8] dark:border-[#244C41] p-4 shadow-xs min-w-0"
          >
            <span className="text-xs text-[#78716C] dark:text-[#93B5A9]">
              Bulan Ini • {m === 'Tunai' ? 'Cash' : m}
            </span>
            <p className="text-lg font-extrabold mt-0.5 text-[#15803D] dark:text-[#86EFAC] tabular-nums">
              <AnimatedNumber value={monthByMethod[m]} format={formatIDR} />
            </p>
          </div>
        ))}
      </div>

      {/* Filter Card: Payment Pill Group + Search + Dynamic Summary Bar */}
      <div className="rounded-2xl bg-white dark:bg-[#142E27] border border-[#E7E3D8] dark:border-[#244C41] p-3.5 sm:p-4 shadow-xs space-y-3 min-w-0">
        <div className="flex flex-wrap gap-2 min-w-0" role="group" aria-label="Filter metode pembayaran">
          {PILLS.map((p) => {
            const isActive = paymentFilter === p.id;
            return (
              <button
                key={p.id}
                type="button"
                onClick={() => setPaymentFilter(p.id)}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border text-xs font-semibold transition whitespace-nowrap ${
                  isActive
                    ? p.activeCls
                    : 'bg-[#FAF8F4] dark:bg-[#0F241E] text-[#78716C] dark:text-[#93B5A9] border-[#E7E3D8] dark:border-[#244C41] hover:border-[#15803D]/50 hover:text-[#1C1917] dark:hover:text-white'
                }`}
              >
                <span>{p.label}</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold tabular-nums ${
                    isActive
                      ? p.id === 'ALL'
                        ? 'bg-white/20 text-white'
                        : 'bg-black/10 dark:bg-white/15'
                      : 'bg-[#E7E3D8]/70 dark:bg-white/10 text-[#1C1917] dark:text-white'
                  }`}
                >
                  {methodCounts[p.id]}
                </span>
              </button>
            );
          })}
        </div>

        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Cari nomor invoice, nama obat, atau cara bayar..."
          className="w-full px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E] border border-[#E7E3D8] dark:border-[#244C41] text-sm text-[#1C1917] dark:text-white placeholder:text-[#78716C] focus:outline-none focus:border-[#15803D]"
        />

        <div className="pt-2.5 border-t border-dashed border-[#E7E3D8] dark:border-[#244C41] flex flex-wrap items-center justify-between gap-2 text-xs text-[#78716C] dark:text-[#93B5A9]">
          <span>
            Menampilkan <strong className="text-[#1C1917] dark:text-white">{filtered.length}</strong> transaksi ({activePillLabel})
          </span>
          <span className="font-extrabold text-[#1C1917] dark:text-white tabular-nums">
            {formatIDR(filteredTotal)}
          </span>
        </div>
      </div>

      <div className="space-y-3 min-w-0">
        {filtered.length === 0 ? (
          <div className="rounded-2xl bg-white dark:bg-[#142E27] border border-[#E7E3D8] dark:border-[#244C41] p-10 text-center text-sm text-[#78716C] dark:text-[#93B5A9]">
            Belum ada riwayat transaksi penjualan.
          </div>
        ) : (
          filtered.map((s) => {
            const its = itemsBySale.get(s.id) || [];
            const methodTone =
              s.paymentMethod === 'QRIS'
                ? 'bg-[#E0F2FE] text-[#0369A1] dark:bg-[#0369A1]/30 dark:text-[#7DD3FC]'
                : s.paymentMethod === 'Transfer'
                ? 'bg-[#FEF3C7] text-[#B45309] dark:bg-[#B45309]/30 dark:text-[#FDE68A]'
                : 'bg-[#DCFCE7] text-[#166534] dark:bg-[#15803D]/30 dark:text-[#86EFAC]';
            return (
              <div
                key={s.id}
                className="rounded-2xl bg-white dark:bg-[#142E27] border border-[#E7E3D8] dark:border-[#244C41] p-4 shadow-xs flex flex-wrap items-center justify-between gap-3 min-w-0"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 min-w-0">
                    <span className="font-extrabold text-sm text-[#1C1917] dark:text-white">{s.invoiceNo}</span>
                    <span className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${methodTone}`}>
                      {s.paymentMethod === 'Tunai' ? 'Cash' : s.paymentMethod}
                    </span>
                    <span className="text-xs text-[#78716C] dark:text-[#93B5A9]">
                      {formatDateTimeID(s.createdAt)}
                    </span>
                  </div>
                  <p className="text-xs text-[#78716C] dark:text-[#93B5A9] mt-1 break-words">
                    {its.map((i) => `${i.medicineName} (${i.qty})`).join(', ')}
                  </p>
                </div>
                <div className="flex items-center gap-3 shrink-0 ml-auto">
                  <div className="text-right">
                    <p className="font-extrabold text-base text-[#15803D] dark:text-[#86EFAC] tabular-nums">
                      {formatIDR(s.total)}
                    </p>
                    <p className="text-[11px] text-[#78716C] dark:text-[#93B5A9] tabular-nums">
                      Bayar: {formatIDR(s.amountPaid)} • Kembali:{' '}
                      {formatIDR(s.changeAmount)}
                    </p>
                  </div>
                  <button
                    onClick={() => setSelectedSale({ sale: s, items: its })}
                    className="px-3 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold flex items-center gap-1.5 transition"
                  >
                    <Printer className="w-3.5 h-3.5" /> Struk
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {selectedSale && (
        <ReceiptModal
          sale={selectedSale.sale}
          items={selectedSale.items}
          onClose={() => setSelectedSale(null)}
        />
      )}
    </div>
  );
};

// ============================================================================
// 9. TUTUP BUKU & BACKUP JSON SCREEN (Matches TutupBukuScreen.kt & BackupScreen.kt)
// ============================================================================
export const BackupAndTutupBukuScreen: React.FC = () => {
  const {
    closedMonths,
    tutupBulan,
    bukaBulan,
    exportBackupJson,
    importBackupJson,
    resetAllData,
  } = useStore();
  const { notify, askConfirm } = useFeedback();
  const [msg, setMsg] = useState<string | null>(null);
  const pastMonths = useMemo(() => getPastMonthsList(12), []);

  const handleDownloadBackup = () => {
    const json = exportBackupJson();
    const blob = new Blob([json], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `backup-apotek-lestari-${dayKeyID()}.json`;
    a.click();
    setMsg('File backup JSON berhasil diunduh.');
  };

  const handleRestoreFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const rawText = String(reader.result || '');
      askConfirm(
        'Perhatian: Restore akan MENGGANTI seluruh data saat ini dengan isi file backup. Lanjutkan?',
        () => {
          try {
            const summary = importBackupJson(rawText);
            setMsg(summary);
            notify('Restore backup berhasil', 'success');
          } catch (err: any) {
            notify('Restore gagal: ' + err.message, 'error');
          }
        },
        { title: 'Konfirmasi Restore', danger: true, confirmLabel: 'Restore' }
      );
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Backup Data & Tutup Buku Bulanan"
        subtitle="Kompatibel penuh dengan file Backup JSON aplikasi Android Apotek Lestari"
      />

      {msg && (
        <div className="p-4 rounded-2xl bg-[#15803D]/15 border border-[#15803D]/30 text-sm font-bold">
          {msg}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* Backup & Restore JSON */}
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-4">
          <h3 className="font-extrabold text-base">
            Backup & Restore (.json Android / Web)
          </h3>
          <p className="text-xs opacity-75">
            File JSON dari Web ini bisa di-restore langsung di aplikasi Android, dan file backup dari HP Android juga bisa di-restore di sini.
          </p>
          <div className="flex flex-wrap gap-3">
            <button
              onClick={handleDownloadBackup}
              className="px-4 py-2.5 rounded-xl bg-[#15803D] text-white text-xs font-bold flex items-center gap-2"
            >
              <Download className="w-4 h-4" /> Unduh Backup (.json)
            </button>
            <label className="cursor-pointer px-4 py-2.5 rounded-xl bg-[#FAF8F4] dark:bg-[#0F241E] text-xs font-bold flex items-center gap-2">
              <Upload className="w-4 h-4" /> Restore dari File (.json)
              <input
                type="file"
                accept="application/json,.json"
                onChange={handleRestoreFile}
                className="hidden"
              />
            </label>
          </div>

          <div className="pt-4 border-t border-black/10 dark:border-white/10">
            <button
              onClick={() =>
                askConfirm(
                  'PERINGATAN: Hapus SEMUA data lokal dan cloud secara permanen?',
                  async () => {
                    await resetAllData();
                    setMsg('Semua data telah direset.');
                    notify('Semua data telah direset', 'info');
                  },
                  { title: 'Reset Semua Data', danger: true, confirmLabel: 'Reset Permanen' }
                )
              }
              className="px-4 py-2 rounded-xl bg-[#BA1A1A]/15 text-[#BA1A1A] text-xs font-bold"
            >
              Reset Semua Data
            </button>
          </div>
        </div>

        {/* Tutup Buku Bulanan */}
        <div className="rounded-3xl bg-[#FFFFFF] dark:bg-[#142E27] p-5 space-y-3">
          <h3 className="font-extrabold text-base">
            Tutup Buku Bulanan (Kunci Periode)
          </h3>
          <p className="text-xs opacity-75">
            Bulan yang sudah ditutup terkunci dari penambahan/penghapusan kas, penjualan, dan faktur PBF.
          </p>
          <div className="space-y-2 max-h-72 overflow-y-auto">
            {pastMonths.map((b) => {
              const locked = closedMonths.includes(b);
              return (
                <div
                  key={b}
                  className="p-3 rounded-2xl bg-[#FAF8F4] dark:bg-[#0F241E] flex items-center justify-between"
                >
                  <div className="flex items-center gap-2">
                    <Calendar className="w-4 h-4 opacity-60" />
                    <span className="font-bold text-sm">
                      {namaBulanIndonesia(b)}
                    </span>
                  </div>
                  {locked ? (
                    <button
                      onClick={() => bukaBulan(b)}
                      className="px-3 py-1.5 rounded-xl bg-amber-500/20 text-amber-500 text-xs font-bold flex items-center gap-1"
                    >
                      <Unlock className="w-3.5 h-3.5" /> Terkunci (Buka)
                    </button>
                  ) : (
                    <button
                      onClick={() => tutupBulan(b)}
                      className="px-3 py-1.5 rounded-xl bg-[#15803D] text-white text-xs font-bold flex items-center gap-1"
                    >
                      <Lock className="w-3.5 h-3.5" /> Tutup Bulan
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};
