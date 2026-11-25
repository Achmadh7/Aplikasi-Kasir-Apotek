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
  ArrowDownLeft,
  ArrowUpRight,
} from 'lucide-react';

const ACTIVE_PILL_CLS = 'bg-[#15803D] text-white border-[#15803D]';
const INACTIVE_PILL_CLS =
  'bg-[#DCEBE2] text-[#1C1917] border-[#B7D6C3] hover:border-[#15803D] hover:bg-[#D0E3D8]';

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

function formatThousandID(raw: string): string {
  const digits = raw.replace(/\D/g, '').replace(/^0+(?=\d)/, '');
  if (!digits) return '';
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

function parseThousandID(formatted: string): number {
  const digits = formatted.replace(/\D/g, '');
  return digits ? parseInt(digits, 10) : 0;
}

// ============================================================================
// 4. KEUANGAN / KAS SCREEN (Matches KeuanganScreen.kt)
// ============================================================================
export const KeuanganScreen: React.FC = () => {
  const { cashEntries, createCashEntry, deleteCashEntry } = useStore();
  const { notify } = useFeedback();
  const [period, setPeriod] = useState<'today' | 'month' | 'all'>('today');
  const [typeFilter, setTypeFilter] = useState<'all' | 'in' | 'out'>('all');
  const [type, setType] = useState<'in' | 'out'>('out');
  const [category, setCategory] = useState('Operasional');
  const [customCategory, setCustomCategory] = useState('');
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
      inCount = 0,
      outCount = 0,
      allBalance = 0;
    for (const e of cashEntries) {
      if (e.type === 'in') allBalance += e.amount;
      else allBalance -= e.amount;
    }
    for (const e of filtered) {
      if (e.type === 'in') {
        totalIn += e.amount;
        inCount += 1;
      } else {
        totalOut += e.amount;
        outCount += 1;
      }
    }
    return {
      totalIn,
      totalOut,
      inCount,
      outCount,
      netFlow: totalIn - totalOut,
      allBalance,
    };
  }, [cashEntries, filtered]);

  const visibleEntries = useMemo(
    () =>
      typeFilter === 'all'
        ? filtered
        : filtered.filter((e) => e.type === typeFilter),
    [filtered, typeFilter]
  );

  const periodLabel =
    period === 'today' ? 'Hari Ini' : period === 'month' ? 'Bulan Ini' : 'Semua';

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const finalCategory =
        category === 'Lainnya' ? customCategory.trim() || 'Lainnya' : category;
      createCashEntry(type, finalCategory, description, parseThousandID(amount));
      setCustomCategory('');
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

      <div
        className="flex flex-wrap gap-2 min-w-0"
        role="group"
        aria-label="Filter periode keuangan"
      >
        {(
          [
            ['today', 'Hari Ini'],
            ['month', 'Bulan Ini'],
            ['all', 'Semua'],
          ] as const
        ).map(([k, lbl]) => {
          const isActive = period === k;
          return (
            <button
              key={k}
              type="button"
              onClick={() => setPeriod(k)}
              className={`min-h-[44px] inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full border text-xs font-semibold transition whitespace-nowrap ${
                isActive ? ACTIVE_PILL_CLS : INACTIVE_PILL_CLS
              }`}
            >
              {lbl}
            </button>
          );
        })}
      </div>

      {/* 3-KPI Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-4 shadow-xs">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-semibold text-[#1C1917]">
              Uang Masuk
            </span>
            <span className="w-7 h-7 rounded-lg bg-[#DCFCE7] text-[#166534] flex items-center justify-center">
              <ArrowDownLeft className="w-4 h-4" aria-hidden="true" />
            </span>
          </div>
          <p className="mt-2 text-xl sm:text-2xl font-extrabold tabular-nums text-[#166534]">
            + <AnimatedNumber value={totals.totalIn} format={formatIDR} />
          </p>
          <p className="mt-1 text-xs text-[#57534E]">
            {totals.inCount} transaksi masuk
          </p>
        </div>

        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-4 shadow-xs">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-semibold text-[#1C1917]">
              Uang Keluar
            </span>
            <span className="w-7 h-7 rounded-lg bg-[#FEE2E2] text-[#BA1A1A] flex items-center justify-center">
              <ArrowUpRight className="w-4 h-4" aria-hidden="true" />
            </span>
          </div>
          <p className="mt-2 text-xl sm:text-2xl font-extrabold tabular-nums text-[#BA1A1A]">
            - <AnimatedNumber value={totals.totalOut} format={formatIDR} />
          </p>
          <p className="mt-1 text-xs text-[#57534E]">
            {totals.outCount} transaksi keluar
          </p>
        </div>

        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-4 shadow-xs">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-semibold text-[#1C1917]">
              Arus Kas Bersih
            </span>
            <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-[#DCEBE2] text-[#1C1917] border border-[#B7D6C3]">
              {periodLabel}
            </span>
          </div>
          <p
            className={`mt-2 text-xl sm:text-2xl font-extrabold tabular-nums ${
              totals.netFlow >= 0 ? 'text-[#166534]' : 'text-[#BA1A1A]'
            }`}
          >
            {totals.netFlow >= 0 ? '+' : '-'}{' '}
            <AnimatedNumber
              value={Math.abs(totals.netFlow)}
              format={formatIDR}
            />
          </p>
          <p className="mt-1 text-xs text-[#57534E]">Selisih masuk &amp; keluar</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
        {/* Form Catat Kas */}
        <div className="lg:col-span-4">
          <form
            onSubmit={handleAdd}
            className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-3.5 text-sm shadow-xs"
          >
            <h3 className="font-extrabold text-base text-[#1C1917]">Catat Kas Manual</h3>
            <div className="grid grid-cols-2 gap-1.5 p-1 rounded-xl bg-[#DCEBE2] border border-[#B7D6C3]">
              <button
                type="button"
                onClick={() => {
                  setType('in');
                  setCategory('Tambah Modal');
                  setCustomCategory('');
                }}
                className={`min-h-[44px] py-2 rounded-lg font-bold text-xs transition ${
                  type === 'in'
                    ? 'bg-[#15803D] text-white shadow-xs'
                    : 'text-[#57534E] hover:text-[#1C1917]'
                }`}
              >
                + Uang Masuk
              </button>
              <button
                type="button"
                onClick={() => {
                  setType('out');
                  setCategory('Operasional');
                  setCustomCategory('');
                }}
                className={`min-h-[44px] py-2 rounded-lg font-bold text-xs transition ${
                  type === 'out'
                    ? 'bg-[#BA1A1A] text-white shadow-xs'
                    : 'text-[#57534E] hover:text-[#1C1917]'
                }`}
              >
                - Uang Keluar
              </button>
            </div>
            <div>
              <label htmlFor="kas-category" className="block text-xs font-bold text-[#1C1917] mb-1">
                Kategori
              </label>
              <select
                id="kas-category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
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
            {category === 'Lainnya' && (
              <div>
                <label htmlFor="kas-custom-cat" className="block text-xs font-bold text-[#1C1917] mb-1">
                  Kategori Lainnya *
                </label>
                <input
                  id="kas-custom-cat"
                  required
                  value={customCategory}
                  onChange={(e) => setCustomCategory(e.target.value)}
                  placeholder="Tulis nama kategori..."
                  className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
                />
              </div>
            )}
            <div>
              <label htmlFor="kas-amount" className="block text-xs font-bold text-[#1C1917] mb-1">
                Nominal (Rp) *
              </label>
              <input
                id="kas-amount"
                required
                type="text"
                inputMode="numeric"
                value={amount}
                onChange={(e) => setAmount(formatThousandID(e.target.value))}
                placeholder="50.000"
                className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
              />
            </div>
            <div>
              <label htmlFor="kas-desc" className="block text-xs font-bold text-[#1C1917] mb-1">
                Keterangan
              </label>
              <input
                id="kas-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Contoh: Token listrik apotek"
                className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
              />
            </div>
            <button
              type="submit"
              className="w-full min-h-[44px] py-3 rounded-xl bg-[#142E27] hover:bg-[#1C3D34] text-white font-bold transition"
            >
              Simpan Catatan Kas
            </button>
          </form>
        </div>

        {/* Daftar Riwayat Kas */}
        <div className="lg:col-span-8">
          <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] shadow-xs overflow-hidden">
            <div className="p-3.5 sm:p-4 border-b border-[#B7D6C3] flex flex-wrap items-center justify-between gap-3">
              <h3 className="font-extrabold text-sm sm:text-base text-[#1C1917]">Riwayat Kas</h3>
              <div
                className="flex flex-wrap gap-2 min-w-0"
                role="group"
                aria-label="Filter tipe kas"
              >
                {(
                  [
                    ['all', `Semua (${filtered.length})`],
                    ['in', `Uang Masuk (${totals.inCount})`],
                    ['out', `Uang Keluar (${totals.outCount})`],
                  ] as const
                ).map(([k, lbl]) => {
                  const isActive = typeFilter === k;
                  return (
                    <button
                      key={k}
                      type="button"
                      onClick={() => setTypeFilter(k)}
                      className={`min-h-[44px] inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full border text-xs font-semibold transition whitespace-nowrap ${
                        isActive ? ACTIVE_PILL_CLS : INACTIVE_PILL_CLS
                      }`}
                    >
                      {lbl}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="divide-y divide-[#B7D6C3]">
              {visibleEntries.length === 0 ? (
                <p className="py-10 text-center text-sm text-[#57534E]">
                  Belum ada catatan kas pada filter ini.
                </p>
              ) : (
                visibleEntries.map((e) => (
                  <div
                    key={e.id}
                    className="p-4 flex items-center justify-between gap-3 hover:bg-[#DCEBE2]/70 transition"
                  >
                    <div className="flex items-start gap-3 min-w-0">
                      <span
                        className={`w-9 h-9 rounded-xl shrink-0 flex items-center justify-center ${
                          e.type === 'in'
                            ? 'bg-[#DCFCE7] text-[#166534]'
                            : 'bg-[#FEE2E2] text-[#BA1A1A]'
                        }`}
                      >
                        {e.type === 'in' ? (
                          <ArrowDownLeft className="w-4 h-4" aria-hidden="true" />
                        ) : (
                          <ArrowUpRight className="w-4 h-4" aria-hidden="true" />
                        )}
                      </span>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className={`px-2 py-0.5 rounded-md text-[10px] font-bold uppercase ${
                              e.type === 'in'
                                ? 'bg-[#DCFCE7] text-[#166534]'
                                : 'bg-[#FEE2E2] text-[#BA1A1A]'
                            }`}
                          >
                            {e.category}
                          </span>
                          <span className="text-xs text-[#57534E]">
                            {formatDateTimeID(e.createdAt)}
                          </span>
                        </div>
                        <p className="font-bold text-sm text-[#1C1917] mt-1 truncate">
                          {e.description || e.category}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <span
                        className={`font-extrabold text-sm tabular-nums ${
                          e.type === 'in'
                            ? 'text-[#166534]'
                            : 'text-[#BA1A1A]'
                        }`}
                      >
                        {e.type === 'in' ? '+' : '-'} {formatIDR(e.amount)}
                      </span>
                      {e.source !== 'sale' && (
                        <button
                          type="button"
                          onClick={() => {
                            try {
                              deleteCashEntry(e.id);
                              notify('Catatan kas dihapus', 'info');
                            } catch (err: any) {
                              notify(err.message, 'error');
                            }
                          }}
                          aria-label="Hapus catatan kas"
                          className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl text-[#BA1A1A] hover:bg-red-500/10"
                        >
                          <Trash2 className="w-4 h-4" aria-hidden="true" />
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
        title: `APOTEK LESTARI - REKAP LABA & HPP (${periodLabel.toUpperCase()})`,
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
        subtitle="Perhitungan Laba Kotor (Omzet - HPP snapshot) & Laba Bersih"
        action={
          <button
            type="button"
            onClick={exportRekapExcel}
            className="min-h-[44px] px-3.5 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold flex items-center gap-1.5 transition"
          >
            <Download className="w-4 h-4" aria-hidden="true" /> Ekspor Rekap Excel
          </button>
        }
      />

      <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-3.5 sm:p-4 shadow-xs min-w-0">
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
                className={`min-h-[44px] inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full border text-xs font-semibold transition whitespace-nowrap ${
                  isActive ? ACTIVE_PILL_CLS : INACTIVE_PILL_CLS
                }`}
              >
                {lbl}
              </button>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 shadow-xs">
          <span className="text-xs font-semibold text-[#1C1917]">Total Penjualan (Omzet)</span>
          <p className="text-2xl font-extrabold mt-1 text-[#1C1917]">
            <AnimatedNumber value={report.revenue} format={formatIDR} />
          </p>
          <span className="text-xs text-[#57534E]">
            {report.transactionCount} transaksi • {report.itemsSold} unit
          </span>
        </div>
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 shadow-xs">
          <span className="text-xs font-semibold text-[#1C1917]">HPP (Modal Obat Terjual)</span>
          <p className="text-2xl font-extrabold mt-1 text-[#92400E]">
            <AnimatedNumber value={report.hpp} format={formatIDR} />
          </p>
          <span className="text-xs text-[#57534E]">
            Laba Kotor: {formatIDR(report.grossProfit)} (
            {report.grossMarginPct.toFixed(1)}%)
          </span>
        </div>
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 shadow-xs">
          <span className="text-xs font-semibold text-[#1C1917]">Biaya Operasional</span>
          <p className="text-2xl font-extrabold mt-1 text-[#BA1A1A]">
            <AnimatedNumber value={report.operationalExpense} format={formatIDR} />
          </p>
          <span className="text-xs text-[#57534E]">
            Kas keluar selain Beli Stok ({formatIDR(report.stockPurchaseExpense)})
          </span>
        </div>
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 shadow-xs">
          <span className="text-xs font-semibold text-[#1C1917]">Laba Bersih</span>
          <p className="text-2xl font-extrabold mt-1 text-[#166534]">
            <AnimatedNumber value={report.netProfit} format={formatIDR} />
          </p>
          <span className="text-xs text-[#57534E]">
            Margin Bersih: {report.marginPct.toFixed(1)}%
          </span>
        </div>
      </div>

      {/* Breakdown per Medicine */}
      <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] shadow-xs overflow-hidden">
        <div className="p-4 font-extrabold text-sm text-[#1C1917] border-b border-[#B7D6C3]">
          Rincian Penjualan &amp; Laba per Obat ({report.byMedicine.length} jenis)
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-[#DCEBE2] border-b border-[#B7D6C3] text-xs uppercase text-[#1C1917]">
              <tr>
                <th className="py-3 px-4">Nama Obat</th>
                <th className="py-3 px-3">Kategori</th>
                <th className="py-3 px-3 text-right">Terjual</th>
                <th className="py-3 px-3 text-right">Omzet</th>
                <th className="py-3 px-3 text-right">HPP</th>
                <th className="py-3 px-4 text-right">Laba Kotor</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#B7D6C3]">
              {report.byMedicine.map((m, i) => (
                <tr key={i}>
                  <td className="py-3 px-4 font-bold text-[#1C1917]">{m.name}</td>
                  <td className="py-3 px-3 text-xs text-[#57534E]">{m.category}</td>
                  <td className="py-3 px-3 text-right font-bold text-[#1C1917]">{m.qty}</td>
                  <td className="py-3 px-3 text-right text-[#1C1917]">{formatIDR(m.revenue)}</td>
                  <td className="py-3 px-3 text-right text-[#57534E]">
                    {formatIDR(m.hpp)}
                  </td>
                  <td className="py-3 px-4 text-right font-bold text-[#166534]">
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
        ppn: parseThousandID(ppn),
        paymentType,
        dueDate,
        items: items.map((it) => ({
          medicineName: it.medicineName,
          qty: parseInt(it.qty, 10) || 0,
          unit: it.unit,
          buyPrice: parseThousandID(it.buyPrice),
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
        title="Faktur PBF & Daftar Supplier"
        subtitle="Catatan belanja PBF (Tunai/Kredit), pengingat jatuh tempo, dan kontak supplier"
        action={
          <div className="flex rounded-xl bg-[#DCEBE2] border border-[#B7D6C3] p-1 text-xs font-bold gap-1">
            <button
              type="button"
              onClick={() => setTab('FAKTUR')}
              className={`min-h-[44px] px-3.5 py-2 rounded-lg transition ${
                tab === 'FAKTUR' ? 'bg-[#15803D] text-white' : 'text-[#1C1917] hover:bg-[#D0E3D8]'
              }`}
            >
              Faktur PBF ({purchases.length})
            </button>
            <button
              type="button"
              onClick={() => setTab('PEMASOK')}
              className={`min-h-[44px] px-3.5 py-2 rounded-lg transition ${
                tab === 'PEMASOK' ? 'bg-[#15803D] text-white' : 'text-[#1C1917] hover:bg-[#D0E3D8]'
              }`}
            >
              Supplier ({suppliers.length})
            </button>
          </div>
        }
      />

      {tab === 'FAKTUR' ? (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <form
            onSubmit={handleSavePurchase}
            className="lg:col-span-5 rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-3 text-sm h-fit shadow-xs"
          >
            <h3 className="font-extrabold text-base text-[#1C1917]">Catat Faktur PBF Baru</h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label htmlFor="pbf-name" className="block text-xs font-bold text-[#1C1917] mb-1">
                  Nama PBF *
                </label>
                <input
                  id="pbf-name"
                  required
                  list="pbf-suppliers-list"
                  value={pbfName}
                  onChange={(e) => setPbfName(e.target.value)}
                  placeholder="Pilih / ketik PBF"
                  className="w-full min-h-[44px] px-3 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
                />
                <datalist id="pbf-suppliers-list">
                  {suppliers.map((s) => (
                    <option key={s.id} value={s.name} />
                  ))}
                </datalist>
              </div>
              <div>
                <label htmlFor="pbf-invoice" className="block text-xs font-bold text-[#1C1917] mb-1">
                  No. Faktur
                </label>
                <input
                  id="pbf-invoice"
                  value={invoiceNo}
                  onChange={(e) => setInvoiceNo(e.target.value)}
                  placeholder="Opsional"
                  className="w-full min-h-[44px] px-3 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              <div>
                <label htmlFor="pbf-date" className="block text-xs font-bold text-[#1C1917] mb-1">
                  Tgl Beli *
                </label>
                <input
                  id="pbf-date"
                  required
                  type="date"
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                  className="w-full min-h-[44px] px-3 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
                />
              </div>
              <div>
                <label htmlFor="pbf-payment" className="block text-xs font-bold text-[#1C1917] mb-1">
                  Pembayaran
                </label>
                <select
                  id="pbf-payment"
                  value={paymentType}
                  onChange={(e) =>
                    setPaymentType(e.target.value as 'CASH' | 'KREDIT')
                  }
                  className="w-full min-h-[44px] px-3 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
                >
                  <option value="CASH">TUNAI (CASH)</option>
                  <option value="KREDIT">KREDIT (TEMPO)</option>
                </select>
              </div>
              <div>
                <label htmlFor="pbf-ppn" className="block text-xs font-bold text-[#1C1917] mb-1">
                  PPN (Rp)
                </label>
                <input
                  id="pbf-ppn"
                  type="text"
                  inputMode="numeric"
                  value={ppn}
                  onChange={(e) => setPpn(formatThousandID(e.target.value))}
                  placeholder="0"
                  className="w-full min-h-[44px] px-3 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
                />
              </div>
            </div>

            {paymentType === 'KREDIT' && (
              <div>
                <label htmlFor="pbf-due" className="block text-xs font-bold text-[#1C1917] mb-1">
                  Tgl Jatuh Tempo Kredit
                </label>
                <input
                  id="pbf-due"
                  type="date"
                  value={dueDate}
                  onChange={(e) => setDueDate(e.target.value)}
                  className="w-full min-h-[44px] px-3 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917]"
                />
              </div>
            )}

            <div className="space-y-2 pt-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#1C1917]">Daftar Obat Dibeli</span>
                <button
                  type="button"
                  onClick={() =>
                    setItems([
                      ...items,
                      { medicineName: '', qty: '1', unit: 'Box', buyPrice: '' },
                    ])
                  }
                  className="min-h-[44px] px-2 -mr-2 text-xs font-bold text-[#166534] hover:underline"
                >
                  + Tambah Baris
                </button>
              </div>
              {items.map((it, idx) => (
                <div key={idx} className="grid grid-cols-12 gap-1.5 items-center">
                  <input
                    required
                    aria-label={`Nama obat baris ${idx + 1}`}
                    placeholder="Nama obat"
                    value={it.medicineName}
                    onChange={(e) =>
                      setItems(
                        items.map((x, i) =>
                          i === idx ? { ...x, medicineName: e.target.value } : x
                        )
                      )
                    }
                    className="col-span-12 sm:col-span-5 min-h-[44px] px-2.5 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-xs text-[#1C1917] placeholder:text-[#57534E]"
                  />
                  <input
                    required
                    aria-label={`Jumlah baris ${idx + 1}`}
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
                    className="col-span-4 sm:col-span-2 min-h-[44px] px-2 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-xs text-[#1C1917]"
                  />
                  <input
                    required
                    aria-label={`Harga beli baris ${idx + 1}`}
                    type="text"
                    inputMode="numeric"
                    placeholder="Harga"
                    value={it.buyPrice}
                    onChange={(e) =>
                      setItems(
                        items.map((x, i) =>
                          i === idx
                            ? { ...x, buyPrice: formatThousandID(e.target.value) }
                            : x
                        )
                      )
                    }
                    className={`${items.length > 1 ? 'col-span-6 sm:col-span-4' : 'col-span-8 sm:col-span-5'} min-h-[44px] px-2.5 py-2 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-xs text-[#1C1917] placeholder:text-[#57534E]`}
                  />
                  {items.length > 1 && (
                    <button
                      type="button"
                      onClick={() => setItems(items.filter((_, i) => i !== idx))}
                      aria-label={`Hapus baris obat ${idx + 1}`}
                      className="col-span-2 sm:col-span-1 min-w-[44px] min-h-[44px] text-[#BA1A1A] flex items-center justify-center rounded-xl hover:bg-red-500/10"
                    >
                      <Trash2 className="w-4 h-4" aria-hidden="true" />
                    </button>
                  )}
                </div>
              ))}
            </div>

            <button
              type="submit"
              className="w-full min-h-[44px] py-3 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white font-bold transition"
            >
              Simpan Faktur PBF
            </button>
          </form>

          <div className="lg:col-span-7 space-y-3">
            {purchases.length === 0 ? (
              <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-10 text-center text-sm text-[#57534E] shadow-xs">
                Belum ada catatan faktur pembelian PBF.
              </div>
            ) : (
              purchases.map((p) => {
                const pItems = purchaseItems.filter((it) => it.purchaseId === p.id);
                const left = dueDaysLeft(p.dueDate);
                return (
                  <div
                    key={p.id}
                    className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-3 shadow-xs"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-extrabold text-base text-[#1C1917]">
                            {p.pbfName}
                          </span>
                          <span
                            className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold ${
                              p.paymentType === 'KREDIT'
                                ? 'bg-[#FEF3C7] text-[#92400E] border border-[#FCD34D]'
                                : 'bg-[#DCFCE7] text-[#166534]'
                            }`}
                          >
                            {p.paymentType}
                          </span>
                        </div>
                        <p className="text-xs text-[#57534E]">
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
                        <p className="font-extrabold text-base text-[#166534]">
                          {formatIDR(p.total)}
                        </p>
                        {p.ppn > 0 && (
                          <p className="text-[11px] text-[#57534E]">
                            Termasuk PPN {formatIDR(p.ppn)}
                          </p>
                        )}
                      </div>
                    </div>

                    <div className="text-xs space-y-1 bg-[#DCEBE2] border border-[#B7D6C3] p-3 rounded-xl text-[#1C1917]">
                      {pItems.map((it) => (
                        <div key={it.id} className="flex justify-between gap-2">
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
                          type="button"
                          onClick={() => {
                            try {
                              markPurchasePaid(p.id);
                              notify(`Faktur ${p.pbfName} ditandai lunas`, 'success');
                            } catch (err: any) {
                              notify(err.message, 'error');
                            }
                          }}
                          className="min-h-[44px] px-3.5 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold flex items-center gap-1.5 transition"
                        >
                          <CheckCircle className="w-3.5 h-3.5" aria-hidden="true" /> Tandai Lunas
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() =>
                          askConfirm(
                            `Hapus faktur ${p.pbfName}?`,
                            () => {
                              try {
                                deletePurchase(p.id);
                                notify(`Faktur ${p.pbfName} dihapus`, 'info');
                              } catch (err: any) {
                                notify(err.message, 'error');
                              }
                            },
                            { title: 'Hapus Faktur PBF', danger: true, confirmLabel: 'Hapus' }
                          )
                        }
                        className="min-h-[44px] px-3.5 py-2 rounded-xl bg-[#FEE2E2] hover:bg-[#FECACA] text-[#BA1A1A] text-xs font-bold transition"
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
                notify('Supplier ditambahkan', 'success');
              } catch (err: any) {
                notify(err.message, 'error');
              }
            }}
            className="lg:col-span-4 rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-3 text-sm h-fit shadow-xs"
          >
            <h3 className="font-extrabold text-base text-[#1C1917]">Tambah Supplier PBF</h3>
            <div>
              <label htmlFor="sup-name" className="block text-xs font-bold text-[#1C1917] mb-1">
                Nama Supplier *
              </label>
              <input
                id="sup-name"
                required
                value={supName}
                onChange={(e) => setSupName(e.target.value)}
                placeholder="Contoh: PT Enseval Putera"
                className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
              />
            </div>
            <div>
              <label htmlFor="sup-contact" className="block text-xs font-bold text-[#1C1917] mb-1">
                Kontak / WhatsApp
              </label>
              <input
                id="sup-contact"
                value={supContact}
                onChange={(e) => setSupContact(e.target.value)}
                placeholder="0812..."
                className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-[#1C1917] placeholder:text-[#57534E]"
              />
            </div>
            <button
              type="submit"
              className="w-full min-h-[44px] py-2.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white font-bold transition"
            >
              Simpan Supplier
            </button>
          </form>

          <div className="lg:col-span-8 rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] divide-y divide-[#B7D6C3] shadow-xs overflow-hidden">
            {suppliers.length === 0 ? (
              <p className="p-10 text-center text-sm text-[#57534E]">
                Belum ada supplier terdaftar.
              </p>
            ) : (
              suppliers.map((s) => (
                <div
                  key={s.id}
                  className="p-4 flex items-center justify-between gap-2"
                >
                  <div>
                    <p className="font-bold text-sm text-[#1C1917]">{s.name}</p>
                    <p className="text-xs text-[#57534E]">
                      {s.contact || 'Tanpa nomor kontak'}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => deleteSupplier(s.id)}
                    aria-label={`Hapus supplier ${s.name}`}
                    className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl text-[#BA1A1A] hover:bg-red-500/10"
                  >
                    <Trash2 className="w-4 h-4" aria-hidden="true" />
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
  const { notify } = useFeedback();

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
        subtitle="Pantau obat stok menipis, kadaluarsa <=30 hari, dan tagihan kredit PBF"
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Stok Menipis */}
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-3 shadow-xs">
          <h3 className="font-extrabold text-base text-[#1C1917] flex items-center justify-between">
            <span>Stok Rendah / Habis</span>
            <span className="px-2.5 py-0.5 rounded-full bg-[#BA1A1A] text-white text-xs">
              {lowStock.length}
            </span>
          </h3>
          {lowStock.length === 0 ? (
            <p className="text-xs text-[#57534E] py-6 text-center">
              Semua stok obat aman (&ge;10).
            </p>
          ) : (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {lowStock.map((m) => (
                <div
                  key={m.id}
                  className="p-3 rounded-2xl bg-[#DCEBE2] border border-[#B7D6C3] flex items-center justify-between gap-2"
                >
                  <div>
                    <p className="font-bold text-sm text-[#1C1917]">{m.name}</p>
                    <p className="text-xs text-[#57534E]">{m.category}</p>
                  </div>
                  <StockPill stock={m.stock} />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Kadaluarsa */}
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-3 shadow-xs">
          <h3 className="font-extrabold text-base text-[#1C1917] flex items-center justify-between">
            <span>Kadaluarsa &le; 30 Hari</span>
            <span className="px-2.5 py-0.5 rounded-full bg-[#92400E] text-white text-xs">
              {expiring.length}
            </span>
          </h3>
          {expiring.length === 0 ? (
            <p className="text-xs text-[#57534E] py-6 text-center">
              Tidak ada obat yang mendekati kadaluarsa.
            </p>
          ) : (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {expiring.map((m) => {
                const d = daysUntilExpiry(m.expiryDate) ?? 0;
                return (
                  <div
                    key={m.id}
                    className="p-3 rounded-2xl bg-[#DCEBE2] border border-[#B7D6C3] flex items-center justify-between gap-2"
                  >
                    <div>
                      <p className="font-bold text-sm text-[#1C1917]">{m.name}</p>
                      <p className="text-xs text-[#57534E]">
                        Exp: {formatDateID(m.expiryDate)}
                      </p>
                    </div>
                    <span
                      className={`px-2.5 py-1 rounded-full text-xs font-bold text-white shrink-0 ${
                        d <= 0 ? 'bg-[#BA1A1A]' : 'bg-[#92400E]'
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
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-3 shadow-xs">
          <h3 className="font-extrabold text-base text-[#1C1917] flex items-center justify-between">
            <span>Faktur Kredit PBF</span>
            <span className="px-2.5 py-0.5 rounded-full bg-[#15803D] text-white text-xs">
              {creditPbf.length}
            </span>
          </h3>
          {creditPbf.length === 0 ? (
            <p className="text-xs text-[#57534E] py-6 text-center">
              Tidak ada hutang faktur PBF yang belum lunas.
            </p>
          ) : (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {creditPbf.map((p) => {
                const left = dueDaysLeft(p.dueDate);
                return (
                  <div
                    key={p.id}
                    className="p-3 rounded-2xl bg-[#DCEBE2] border border-[#B7D6C3] space-y-2"
                  >
                    <div className="flex justify-between items-start gap-2">
                      <div>
                        <p className="font-bold text-sm text-[#1C1917]">{p.pbfName}</p>
                        <p className="text-xs text-[#57534E]">
                          Tempo: {p.dueDate ? formatDateID(p.dueDate) : 'Belum diatur'}
                        </p>
                      </div>
                      <p className="font-extrabold text-sm text-[#92400E]">
                        {formatIDR(p.total)}
                      </p>
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-bold text-[#1C1917]">
                        {left === null
                          ? ''
                          : left < 0
                          ? `Lewat ${Math.abs(left)} hari!`
                          : `${left} hari lagi`}
                      </span>
                      <button
                        type="button"
                        onClick={() => {
                          try {
                            markPurchasePaid(p.id);
                            notify(`Faktur ${p.pbfName} ditandai lunas`, 'success');
                          } catch (err: any) {
                            notify(err.message, 'error');
                          }
                        }}
                        className="min-h-[44px] px-3.5 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold transition"
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

  const PILLS: { id: PaymentFilter; label: string }[] = [
    { id: 'ALL', label: 'Semua' },
    { id: 'Tunai', label: 'Cash' },
    { id: 'QRIS', label: 'QRIS' },
    { id: 'Transfer', label: 'Transfer' },
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
            className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-4 shadow-xs min-w-0"
          >
            <span className="text-xs text-[#57534E]">
              Bulan Ini • {m === 'Tunai' ? 'Cash' : m}
            </span>
            <p className="text-lg font-extrabold mt-0.5 text-[#166534] tabular-nums">
              <AnimatedNumber value={monthByMethod[m]} format={formatIDR} />
            </p>
          </div>
        ))}
      </div>

      {/* Filter Card: Payment Pill Group + Search + Dynamic Summary Bar */}
      <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-3.5 sm:p-4 shadow-xs space-y-3 min-w-0">
        <div className="flex flex-wrap gap-2 min-w-0" role="group" aria-label="Filter metode pembayaran">
          {PILLS.map((p) => {
            const isActive = paymentFilter === p.id;
            return (
              <button
                key={p.id}
                type="button"
                onClick={() => setPaymentFilter(p.id)}
                className={`min-h-[44px] inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full border text-xs font-semibold transition whitespace-nowrap ${
                  isActive ? ACTIVE_PILL_CLS : INACTIVE_PILL_CLS
                }`}
              >
                <span>{p.label}</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-[11px] font-bold tabular-nums ${
                    isActive
                      ? 'bg-white/20 text-white'
                      : 'bg-[#FAF8F4] text-[#1C1917]'
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
          aria-label="Cari nomor invoice, nama obat, atau cara bayar"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Cari nomor invoice, nama obat, atau cara bayar..."
          className="w-full min-h-[44px] px-3.5 py-2.5 rounded-xl bg-[#FAF8F4] border border-[#B7D6C3] text-sm text-[#1C1917] placeholder:text-[#57534E] focus:ring-2 focus:ring-[#15803D]"
        />

        <div className="pt-2.5 border-t border-dashed border-[#B7D6C3] flex flex-wrap items-center justify-between gap-2 text-xs text-[#57534E]">
          <span>
            Menampilkan <strong className="text-[#1C1917]">{filtered.length}</strong> transaksi ({activePillLabel})
          </span>
          <span className="font-extrabold text-[#1C1917] tabular-nums">
            {formatIDR(filteredTotal)}
          </span>
        </div>
      </div>

      <div className="space-y-3 min-w-0">
        {filtered.length === 0 ? (
          <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-10 text-center text-sm text-[#57534E]">
            Belum ada riwayat transaksi penjualan.
          </div>
        ) : (
          filtered.map((s) => {
            const its = itemsBySale.get(s.id) || [];
            const methodTone =
              s.paymentMethod === 'QRIS'
                ? 'bg-[#E0F2FE] text-[#0369A1]'
                : s.paymentMethod === 'Transfer'
                ? 'bg-[#FEF3C7] text-[#92400E]'
                : 'bg-[#DCFCE7] text-[#166534]';
            return (
              <div
                key={s.id}
                className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-4 shadow-xs flex flex-wrap items-center justify-between gap-3 min-w-0"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 min-w-0">
                    <span className="font-extrabold text-sm text-[#1C1917]">{s.invoiceNo}</span>
                    <span className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${methodTone}`}>
                      {s.paymentMethod === 'Tunai' ? 'Cash' : s.paymentMethod}
                    </span>
                    <span className="text-xs text-[#57534E]">
                      {formatDateTimeID(s.createdAt)}
                    </span>
                  </div>
                  <p className="text-xs text-[#57534E] mt-1 break-words">
                    {its.map((i) => `${i.medicineName} (${i.qty})`).join(', ')}
                  </p>
                </div>
                <div className="flex items-center gap-3 shrink-0 ml-auto">
                  <div className="text-right">
                    <p className="font-extrabold text-base text-[#166534] tabular-nums">
                      {formatIDR(s.total)}
                    </p>
                    <p className="text-[11px] text-[#57534E] tabular-nums">
                      Bayar: {formatIDR(s.amountPaid)} • Kembali:{' '}
                      {formatIDR(s.changeAmount)}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSelectedSale({ sale: s, items: its })}
                    className="min-h-[44px] px-3.5 py-2 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold flex items-center gap-1.5 transition"
                  >
                    <Printer className="w-3.5 h-3.5" aria-hidden="true" /> Struk
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
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `backup-apotek-lestari-${dayKeyID()}.json`;
    a.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
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
        <div className="p-4 rounded-2xl bg-[#DCFCE7] border border-[#B7D6C3] text-sm font-bold text-[#1C1917]">
          {msg}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* Backup & Restore JSON */}
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-4 shadow-xs">
          <h3 className="font-extrabold text-base text-[#1C1917]">
            Backup &amp; Restore (.json Android / Web)
          </h3>
          <p className="text-xs text-[#57534E]">
            File JSON dari Web ini bisa di-restore langsung di aplikasi Android, dan file backup dari HP Android juga bisa di-restore di sini.
          </p>
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={handleDownloadBackup}
              className="min-h-[44px] px-4 py-2.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold flex items-center gap-2 transition"
            >
              <Download className="w-4 h-4" aria-hidden="true" /> Unduh Backup (.json)
            </button>
            <label className="min-h-[44px] cursor-pointer px-4 py-2.5 rounded-xl bg-[#DCEBE2] hover:bg-[#D0E3D8] border border-[#B7D6C3] text-[#1C1917] text-xs font-bold flex items-center gap-2 transition">
              <Upload className="w-4 h-4" aria-hidden="true" /> Restore dari File (.json)
              <input
                type="file"
                accept="application/json,.json"
                onChange={handleRestoreFile}
                className="hidden"
              />
            </label>
          </div>

          <div className="pt-4 border-t border-[#B7D6C3]">
            <button
              type="button"
              onClick={() =>
                askConfirm(
                  'PERINGATAN: Hapus SEMUA data lokal dan cloud secara permanen?',
                  async () => {
                    try {
                      await resetAllData();
                      setMsg('Semua data telah direset.');
                      notify('Semua data telah direset', 'info');
                    } catch (err: any) {
                      notify(err?.message || 'Gagal mereset data', 'error');
                    }
                  },
                  { title: 'Reset Semua Data', danger: true, confirmLabel: 'Reset Permanen' }
                )
              }
              className="min-h-[44px] px-4 py-2 rounded-xl bg-[#FEE2E2] hover:bg-[#FECACA] text-[#BA1A1A] text-xs font-bold transition"
            >
              Reset Semua Data
            </button>
          </div>
        </div>

        {/* Tutup Buku Bulanan */}
        <div className="rounded-2xl bg-[#E8F2EC] border border-[#B7D6C3] p-5 space-y-3 shadow-xs">
          <h3 className="font-extrabold text-base text-[#1C1917]">
            Tutup Buku Bulanan (Kunci Periode)
          </h3>
          <p className="text-xs text-[#57534E]">
            Bulan yang sudah ditutup terkunci dari penambahan/penghapusan kas, penjualan, dan faktur PBF.
          </p>
          <div className="space-y-2 max-h-72 overflow-y-auto">
            {pastMonths.map((b) => {
              const locked = closedMonths.includes(b);
              return (
                <div
                  key={b}
                  className="p-3 rounded-2xl bg-[#DCEBE2] border border-[#B7D6C3] flex items-center justify-between gap-2"
                >
                  <div className="flex items-center gap-2">
                    <Calendar className="w-4 h-4 text-[#57534E]" aria-hidden="true" />
                    <span className="font-bold text-sm text-[#1C1917]">
                      {namaBulanIndonesia(b)}
                    </span>
                  </div>
                  {locked ? (
                    <button
                      type="button"
                      onClick={() => {
                        try {
                          bukaBulan(b);
                          notify(`${namaBulanIndonesia(b)} dibuka kembali`, 'info');
                        } catch (err: any) {
                          notify(err.message, 'error');
                        }
                      }}
                      className="min-h-[44px] px-3 py-1.5 rounded-xl bg-[#FEF3C7] border border-[#FCD34D] text-[#92400E] text-xs font-bold flex items-center gap-1.5 transition"
                    >
                      <Unlock className="w-3.5 h-3.5" aria-hidden="true" /> Terkunci (Buka)
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => {
                        try {
                          tutupBulan(b);
                          notify(`${namaBulanIndonesia(b)} ditutup`, 'success');
                        } catch (err: any) {
                          notify(err.message, 'error');
                        }
                      }}
                      className="min-h-[44px] px-3 py-1.5 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white text-xs font-bold flex items-center gap-1.5 transition"
                    >
                      <Lock className="w-3.5 h-3.5" aria-hidden="true" /> Tutup Bulan
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
