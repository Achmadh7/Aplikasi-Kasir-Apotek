import { defineAction, z, type ActionsModule, type Ctx } from "@hatch/space-sdk";
import { desc, eq } from "drizzle-orm";
import * as XLSX from "xlsx";
import * as schema from "./schema";

function fmtDate(d: Date) { return d.toISOString(); }

/* --- Business timezone: Asia/Jakarta (WIB, UTC+7, no DST) ---
   The server host runs in UTC, so every "today / this month / last N days"
   boundary must be computed in Jakarta time — using the server's local
   calendar made "Hari Ini" roll over at 07:00 WIB instead of midnight,
   so yesterday's figures kept showing as today's until 7am. A Jakarta
   calendar day is the instant range [midnight WIB, next midnight WIB). */
const JAKARTA_TZ = "Asia/Jakarta";
function jakartaDateKey(d: Date): string {
  // en-CA formats as YYYY-MM-DD
  return new Intl.DateTimeFormat("en-CA", { timeZone: JAKARTA_TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
}
function jakartaDayStart(d: Date): Date {
  return new Date(`${jakartaDateKey(d)}T00:00:00+07:00`);
}
function jakartaDayStartOffset(d: Date, offsetDays: number): Date {
  return new Date(jakartaDayStart(d).getTime() + offsetDays * 24 * 60 * 60 * 1000);
}
function jakartaMonthStart(d: Date): Date {
  return new Date(`${jakartaDateKey(d).slice(0, 7)}-01T00:00:00+07:00`);
}
function jakartaStamp(d: Date): string {
  return jakartaDateKey(d).replace(/-/g, "");
}
const DAY_NAMES_ID = ["Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"];
function jakartaDayLabel(dayStart: Date): string {
  // The calendar weekday of a Jakarta date key: read it at noon UTC so the
  // UTC weekday equals the Jakarta calendar weekday.
  const noonUtc = new Date(`${jakartaDateKey(dayStart)}T12:00:00Z`);
  return DAY_NAMES_ID[noonUtc.getUTCDay()]!;
}

/* --- Excel file generation (server-side) ---
   The .xlsx bytes are stored as a blob and served back through a real
   same-origin URL (ctx.blobs.getUrl). Client-side Blob object URLs created
   inside the sandboxed webview iframe are silently blocked on the user's
   device, so downloads must go through a real URL instead. */
const XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const EXCEL_HEADERS = ["Nama Obat", "Barcode", "Kategori", "Satuan", "Harga Beli", "Harga Jual", "Stok", "Stok Minimum", "Tanggal Kadaluarsa"];

type MedRow = { name: string; barcode?: string | null; category: string; unit: string; buyPrice: number; sellPrice: number; stock: number; minStock: number; expiryDate: string | null };

function buildStockWorkbook(meds: MedRow[]) {
  const data = meds.map(m => ({ "Nama Obat": m.name, "Barcode": m.barcode || "", "Kategori": m.category, "Satuan": m.unit, "Harga Beli": m.buyPrice, "Harga Jual": m.sellPrice, "Stok": m.stock, "Stok Minimum": m.minStock, "Tanggal Kadaluarsa": m.expiryDate || "" }));
  const ws = XLSX.utils.json_to_sheet(data, { header: [...EXCEL_HEADERS] });
  ws["!cols"] = [{ wch: 34 }, { wch: 16 }, { wch: 16 }, { wch: 10 }, { wch: 14 }, { wch: 14 }, { wch: 8 }, { wch: 14 }, { wch: 19 }];
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "Stok Obat");
  return wb;
}

function addInfoSheet(wb: XLSX.WorkBook, lines: string[]) {
  const info = XLSX.utils.aoa_to_sheet(lines.map(l => [l]));
  info["!cols"] = [{ wch: 100 }];
  XLSX.utils.book_append_sheet(wb, info, "Petunjuk");
}

function workbookBytes(wb: XLSX.WorkBook): ArrayBuffer {
  return XLSX.write(wb, { bookType: "xlsx", type: "array" }) as ArrayBuffer;
}

/* --- Profit report computation ---
   Gross profit = sales revenue − HPP (cost of goods sold), where the cost of
   each sold item is the buy price snapshot stored on the sale item at sale
   time. Items sold before snapshots existed (buy_price = 0) fall back to the
   medicine's current buy price; items whose medicine was deleted and have no
   snapshot are counted in costMissingQty so the UI can warn that their cost
   is unknown (profit for them is overstated). Net profit estimate subtracts
   operational cash-out entries, excluding "Beli Stok" (stock purchases are
   already accounted for through HPP, so subtracting them again would double
   count). */
type ProfitPeriod = "today" | "month" | "last30" | "all";

async function computeProfit(ctx: Ctx, period: ProfitPeriod) {
  const db = ctx.db<typeof schema>();
  const salesRows = await db.select().from(schema.sales);
  const items = await db.select().from(schema.saleItems);
  const meds = await db.select().from(schema.medicines);
  const entries = await db.select().from(schema.cashEntries);
  const medById = new Map(meds.map(m => [m.id, m]));
  const now = new Date();
  const startToday = jakartaDayStart(now);
  const endToday = jakartaDayStartOffset(now, 1);
  const startMonth = jakartaMonthStart(now);
  const startLast30 = jakartaDayStartOffset(now, -29);
  const start = period === "today" ? startToday.getTime() : period === "month" ? startMonth.getTime() : period === "last30" ? startLast30.getTime() : 0;
  // Periods are Jakarta calendar windows: [start, tomorrow midnight WIB).
  // The end bound matters for "today" — without it, anything timestamped
  // later than today would leak into today's report.
  const end = period === "all" ? Number.POSITIVE_INFINITY : endToday.getTime();
  const inPeriod = (d: Date) => { const t = d.getTime(); return t >= start && t < end; };
  const periodSales = salesRows.filter(s => inPeriod(s.createdAt));
  const periodSaleIds = new Set(periodSales.map(s => s.id));
  const periodItems = items.filter(i => periodSaleIds.has(i.saleId));

  const costOf = (it: typeof items[number]) => it.buyPrice > 0 ? it.buyPrice : (medById.get(it.medicineId)?.buyPrice ?? 0);
  const revenue = periodItems.reduce((s, i) => s + i.subtotal, 0);
  const hpp = periodItems.reduce((s, i) => s + costOf(i) * i.qty, 0);
  const grossProfit = revenue - hpp;
  const marginPct = revenue > 0 ? Math.round((grossProfit / revenue) * 1000) / 10 : 0;
  const itemsSold = periodItems.reduce((s, i) => s + i.qty, 0);
  const costMissingQty = periodItems.filter(i => costOf(i) === 0).reduce((s, i) => s + i.qty, 0);

  const periodOut = entries.filter(e => e.type === "out" && inPeriod(e.createdAt));
  const operationalExpense = periodOut.filter(e => e.category !== "Beli Stok").reduce((s, e) => s + e.amount, 0);
  const stockPurchaseExpense = periodOut.filter(e => e.category === "Beli Stok").reduce((s, e) => s + e.amount, 0);
  const netProfit = grossProfit - operationalExpense;

  type Agg = { qty: number; revenue: number; hpp: number };
  const medAgg = new Map<string, Agg & { category: string }>();
  const catAgg = new Map<string, Agg>();
  for (const it of periodItems) {
    const cost = costOf(it) * it.qty;
    const category = medById.get(it.medicineId)?.category ?? "Lainnya";
    const cur = medAgg.get(it.medicineName) ?? { qty: 0, revenue: 0, hpp: 0, category };
    cur.qty += it.qty; cur.revenue += it.subtotal; cur.hpp += cost;
    medAgg.set(it.medicineName, cur);
    const cc = catAgg.get(category) ?? { qty: 0, revenue: 0, hpp: 0 };
    cc.qty += it.qty; cc.revenue += it.subtotal; cc.hpp += cost;
    catAgg.set(category, cc);
  }
  const withMargin = (a: Agg) => ({ qty: a.qty, revenue: a.revenue, hpp: a.hpp, profit: a.revenue - a.hpp, marginPct: a.revenue > 0 ? Math.round(((a.revenue - a.hpp) / a.revenue) * 1000) / 10 : 0 });
  const byMedicine = Array.from(medAgg.entries()).map(([name, v]) => ({ name, category: v.category, ...withMargin(v) })).sort((a, b) => b.profit - a.profit);
  const byCategory = Array.from(catAgg.entries()).map(([category, v]) => ({ category, ...withMargin(v) })).sort((a, b) => b.profit - a.profit);

  // Daily gross profit for the last 7 Jakarta calendar days (independent
  // of the selected period)
  const saleById = new Map(salesRows.map(s => [s.id, s]));
  const daily: { date: string; label: string; revenue: number; hpp: number; profit: number }[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = jakartaDayStartOffset(now, -i);
    const next = new Date(d.getTime() + 24 * 60 * 60 * 1000);
    let rev = 0, cost = 0;
    for (const it of items) {
      const sale = saleById.get(it.saleId);
      if (!sale) continue;
      const t = sale.createdAt.getTime();
      if (t >= d.getTime() && t < next.getTime()) { rev += it.subtotal; cost += costOf(it) * it.qty; }
    }
    daily.push({ date: jakartaDateKey(d), label: jakartaDayLabel(d), revenue: rev, hpp: cost, profit: rev - cost });
  }

  return {
    revenue, hpp, grossProfit, marginPct, operationalExpense, stockPurchaseExpense, netProfit,
    transactionCount: periodSales.length, itemsSold, costMissingQty, byMedicine, byCategory, daily,
  };
}

const profitAggShape = { qty: z.number(), revenue: z.number(), hpp: z.number(), profit: z.number(), marginPct: z.number() };

export const Actions = {
  listMedicines: defineAction({
    request: z.object({ search: z.string().optional().default("") }),
    response: z.object({
      medicines: z.array(z.object({
        id: z.number(), name: z.string(), category: z.string(), unit: z.string(),
        buyPrice: z.number(), sellPrice: z.number(), stock: z.number(), minStock: z.number(),
        expiryDate: z.string().nullable(), barcode: z.string().nullable(), createdAt: z.string(),
      })),
    }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const rows = await db.select().from(schema.medicines).orderBy(desc(schema.medicines.id));
      const filtered = args.search ? rows.filter(r => r.name.toLowerCase().includes(args.search.toLowerCase()) || r.category.toLowerCase().includes(args.search.toLowerCase()) || (r.barcode ?? "").includes(args.search.trim())) : rows;
      return { medicines: filtered.map(r => ({ id: r.id, name: r.name, category: r.category, unit: r.unit, buyPrice: r.buyPrice, sellPrice: r.sellPrice, stock: r.stock, minStock: r.minStock, expiryDate: r.expiryDate, barcode: r.barcode, createdAt: fmtDate(r.createdAt) })) };
    },
  }),

  findMedicineByBarcode: defineAction({
    request: z.object({ barcode: z.string().min(1) }),
    response: z.object({
      medicine: z.object({
        id: z.number(), name: z.string(), category: z.string(), unit: z.string(),
        buyPrice: z.number(), sellPrice: z.number(), stock: z.number(), minStock: z.number(),
        expiryDate: z.string().nullable(), barcode: z.string().nullable(), createdAt: z.string(),
      }).nullable(),
    }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const code = args.barcode.trim();
      const rows = await db.select().from(schema.medicines);
      const found = rows.find(r => r.barcode === code);
      if (!found) return { medicine: null };
      return { medicine: { id: found.id, name: found.name, category: found.category, unit: found.unit, buyPrice: found.buyPrice, sellPrice: found.sellPrice, stock: found.stock, minStock: found.minStock, expiryDate: found.expiryDate, barcode: found.barcode, createdAt: fmtDate(found.createdAt) } };
    },
  }),

  createMedicine: defineAction({
    request: z.object({
      name: z.string().min(1), category: z.string().min(1), unit: z.string().min(1),
      buyPrice: z.number().int().min(0), sellPrice: z.number().int().min(0),
      stock: z.number().int().min(0), minStock: z.number().int().min(0),
      expiryDate: z.string().nullable().optional(),
      barcode: z.string().nullable().optional(),
    }),
    response: z.object({ id: z.number() }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const barcode = args.barcode?.trim() || null;
      if (barcode) {
        const dup = (await db.select().from(schema.medicines)).find(m => m.barcode === barcode);
        if (dup) throw new Error(`Barcode ${barcode} sudah dipakai untuk ${dup.name}`);
      }
      const res = await db.insert(schema.medicines).values({
        name: args.name, category: args.category, unit: args.unit,
        buyPrice: args.buyPrice, sellPrice: args.sellPrice, stock: args.stock, minStock: args.minStock,
        expiryDate: args.expiryDate ?? null, barcode,
      }).returning({ id: schema.medicines.id });
      ctx.invalidateQueries();
      return { id: res[0]!.id };
    },
  }),

  updateMedicine: defineAction({
    request: z.object({
      id: z.number(), name: z.string().min(1), category: z.string().min(1), unit: z.string().min(1),
      buyPrice: z.number().int().min(0), sellPrice: z.number().int().min(0),
      stock: z.number().int().min(0), minStock: z.number().int().min(0),
      expiryDate: z.string().nullable().optional(),
      barcode: z.string().nullable().optional(),
    }),
    response: z.object({ ok: z.boolean() }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const barcode = args.barcode?.trim() || null;
      if (barcode) {
        const dup = (await db.select().from(schema.medicines)).find(m => m.barcode === barcode && m.id !== args.id);
        if (dup) throw new Error(`Barcode ${barcode} sudah dipakai untuk ${dup.name}`);
      }
      await db.update(schema.medicines).set({
        name: args.name, category: args.category, unit: args.unit,
        buyPrice: args.buyPrice, sellPrice: args.sellPrice, stock: args.stock, minStock: args.minStock,
        expiryDate: args.expiryDate ?? null, barcode,
      }).where(eq(schema.medicines.id, args.id));
      ctx.invalidateQueries();
      return { ok: true };
    },
  }),

  deleteMedicine: defineAction({
    request: z.object({ id: z.number() }),
    response: z.object({ ok: z.boolean() }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      await db.delete(schema.medicines).where(eq(schema.medicines.id, args.id));
      ctx.invalidateQueries();
      return { ok: true };
    },
  }),

  bulkImportMedicines: defineAction({
    request: z.object({
      items: z.array(z.object({
        name: z.string().min(1),
        category: z.string().min(1),
        unit: z.string().min(1),
        buyPrice: z.number().int().min(0),
        sellPrice: z.number().int().min(0),
        stock: z.number().int().min(0),
        minStock: z.number().int().min(0),
        expiryDate: z.string().nullable().optional(),
        barcode: z.string().nullable().optional(),
      })).min(1).max(1000),
      mode: z.enum(["upsert", "insert_only"]).default("upsert"),
    }),
    response: z.object({
      inserted: z.number(),
      updated: z.number(),
      skipped: z.number(),
      total: z.number(),
    }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const existing = await db.select().from(schema.medicines);
      const byName = new Map<string, typeof existing[number]>();
      const byBarcode = new Map<string, typeof existing[number]>();
      for (const m of existing) {
        byName.set(m.name.trim().toLowerCase(), m);
        if (m.barcode) byBarcode.set(m.barcode, m);
      }
      let inserted = 0;
      let updated = 0;
      let skipped = 0;
      // Dedupe within file by name (last wins)
      const seen = new Map<string, typeof args.items[number]>();
      for (const it of args.items) {
        const key = it.name.trim().toLowerCase();
        if (!key) { skipped++; continue; }
        seen.set(key, { ...it, name: it.name.trim(), barcode: it.barcode?.trim() || null });
      }
      for (const [key, it] of seen.entries()) {
        const bc = it.barcode ?? null;
        // Match an existing medicine by barcode first (when the file provides
        // one), then by name — so re-importing an export updates the right row
        // even if the name was edited.
        const ex = (bc ? byBarcode.get(bc) : undefined) ?? byName.get(key);
        // A barcode already owned by a different medicine must not be stolen.
        const barcodeTaken = bc ? byBarcode.get(bc) : undefined;
        const safeBarcode = barcodeTaken && ex && barcodeTaken.id !== ex.id ? null : bc;
        if (ex) {
          if (args.mode === "insert_only") { skipped++; continue; }
          await db.update(schema.medicines).set({
            category: it.category || ex.category,
            unit: it.unit || ex.unit,
            buyPrice: it.buyPrice,
            sellPrice: it.sellPrice,
            stock: it.stock,
            minStock: it.minStock,
            expiryDate: it.expiryDate ?? null,
            barcode: safeBarcode ?? ex.barcode,
          }).where(eq(schema.medicines.id, ex.id));
          updated++;
        } else {
          await db.insert(schema.medicines).values({
            name: it.name,
            category: it.category,
            unit: it.unit,
            buyPrice: it.buyPrice,
            sellPrice: it.sellPrice,
            stock: it.stock,
            minStock: it.minStock,
            expiryDate: it.expiryDate ?? null,
            barcode: barcodeTaken ? null : bc,
          });
          inserted++;
        }
      }
      ctx.invalidateQueries();
      return { inserted, updated, skipped, total: args.items.length };
    },
  }),

  downloadTemplate: defineAction({
    request: z.object({}),
    response: z.object({ ok: z.literal(true), url: z.string(), filename: z.string() }),
    async handler(ctx): Promise<{ ok: true; url: string; filename: string }> {
      const examples: MedRow[] = [
        { name: "Paracetamol 500mg", barcode: "8991234567890", category: "Pereda Nyeri", unit: "Strip", buyPrice: 8000, sellPrice: 12000, stock: 50, minStock: 10, expiryDate: "2027-12-31" },
        { name: "Amoxicillin 500mg", barcode: "8991234567891", category: "Antibiotik", unit: "Strip", buyPrice: 15000, sellPrice: 22000, stock: 30, minStock: 10, expiryDate: "2026-11-30" },
        { name: "Vitamin C 1000mg", barcode: "8991234567892", category: "Vitamin", unit: "Botol", buyPrice: 35000, sellPrice: 50000, stock: 20, minStock: 5, expiryDate: "2028-06-30" },
      ];
      const wb = buildStockWorkbook(examples);
      addInfoSheet(wb, [
        "Template Import Stok Obat — Petunjuk",
        "",
        "1. Isi data obat di sheet 'Stok Obat', satu obat per baris.",
        "2. Hapus atau ganti 3 baris contoh sebelum import jika tidak diperlukan.",
        "3. Kolom wajib: Nama Obat. Kolom lain boleh kosong (akan diisi nilai bawaan).",
        "   - Barcode: kode barcode pada kemasan obat (boleh kosong). Barcode yang sama tidak boleh dipakai dua obat berbeda.",
        "   - Kategori: Umum, Antibiotik, Vitamin, Pereda Nyeri, Obat Batuk & Flu, Pencernaan, Kulit, Mata, Lainnya",
        "   - Satuan: Strip, Botol, Box, Tablet, Kapsul, Tube, Sachet, Pcs",
        "   - Harga Beli & Harga Jual: angka saja, contoh 12000 (tanpa Rp / titik)",
        "   - Stok & Stok Minimum: angka bulat",
        "   - Tanggal Kadaluarsa: format YYYY-MM-DD, contoh 2027-12-31 (boleh kosong)",
        "4. Simpan file, lalu di aplikasi buka Stok > Import Excel dan pilih file ini.",
        "5. Jika Nama Obat sudah ada, mode 'Perbarui' akan menimpa datanya; mode 'Lewati' akan melewatinya.",
      ]);
      const filename = "Template-Import-Stok-Obat.xlsx";
      const key = "downloads/template-import-stok-obat.xlsx";
      await ctx.blobs.put(key, workbookBytes(wb), { contentType: XLSX_CONTENT_TYPE });
      const url = await ctx.blobs.getUrl(key);
      return { ok: true as const, url, filename };
    },
  }),

  downloadExport: defineAction({
    request: z.object({}),
    response: z.object({ ok: z.boolean(), url: z.string(), filename: z.string(), message: z.string() }),
    async handler(ctx) {
      const db = ctx.db<typeof schema>();
      const rows = await db.select().from(schema.medicines);
      if (rows.length === 0) return { ok: false, url: "", filename: "", message: "Belum ada data obat untuk diekspor." };
      const sorted = [...rows].sort((a, b) => a.name.localeCompare(b.name));
      const wb = buildStockWorkbook(sorted.map(m => ({ name: m.name, barcode: m.barcode, category: m.category, unit: m.unit, buyPrice: m.buyPrice, sellPrice: m.sellPrice, stock: m.stock, minStock: m.minStock, expiryDate: m.expiryDate })));
      addInfoSheet(wb, [
        "Petunjuk Export Stok Obat",
        "",
        "File ini berisi seluruh stok obat saat ini.",
        "Anda bisa mengedit Harga Beli, Harga Jual, Stok, dan Tanggal Kadaluarsa di file ini, lalu Import kembali.",
        "Jika Nama Obat sama persis, data akan diperbarui (mode Perbarui).",
        "Jangan mengubah nama kolom di baris pertama sheet 'Stok Obat'.",
        "Format Tanggal Kadaluarsa: YYYY-MM-DD, contoh 2027-12-31.",
      ]);
      const stamp = jakartaStamp(new Date());
      const filename = `Stok-Obat-${stamp}.xlsx`;
      const key = "downloads/export-stok-obat.xlsx";
      await ctx.blobs.put(key, workbookBytes(wb), { contentType: XLSX_CONTENT_TYPE });
      const url = await ctx.blobs.getUrl(key);
      return { ok: true, url, filename, message: "" };
    },
  }),

  createSale: defineAction({
    request: z.object({
      items: z.array(z.object({ medicineId: z.number(), qty: z.number().int().positive() })).min(1),
      paymentMethod: z.string(), amountPaid: z.number().int().min(0),
    }),
    response: z.object({ id: z.number(), invoiceNo: z.string(), total: z.number(), changeAmount: z.number() }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const meds = await db.select().from(schema.medicines);
      let total = 0;
      const lineItems: { medicineId: number; medicineName: string; qty: number; price: number; buyPrice: number; subtotal: number }[] = [];
      for (const it of args.items) {
        const med = meds.find(m => m.id === it.medicineId);
        if (!med) throw new Error("Obat tidak ditemukan");
        if (med.stock < it.qty) throw new Error(`Stok ${med.name} tidak cukup`);
        const subtotal = med.sellPrice * it.qty;
        total += subtotal;
        lineItems.push({ medicineId: med.id, medicineName: med.name, qty: it.qty, price: med.sellPrice, buyPrice: med.buyPrice, subtotal });
      }
      const changeAmount = Math.max(0, args.amountPaid - total);
      const now = new Date();
      const invoiceNo = `INV-${jakartaStamp(now)}-${Date.now().toString().slice(-6)}`;
      const saleRes = await db.insert(schema.sales).values({ invoiceNo, total, paymentMethod: args.paymentMethod, amountPaid: args.amountPaid, changeAmount, createdAt: now }).returning({ id: schema.sales.id });
      const saleId = saleRes[0]!.id;
      for (const li of lineItems) {
        await db.insert(schema.saleItems).values({ saleId, medicineId: li.medicineId, medicineName: li.medicineName, qty: li.qty, price: li.price, buyPrice: li.buyPrice, subtotal: li.subtotal });
        const med = meds.find(m => m.id === li.medicineId)!;
        await db.update(schema.medicines).set({ stock: med.stock - li.qty }).where(eq(schema.medicines.id, li.medicineId));
      }
      await db.insert(schema.cashEntries).values({ type: "in", category: "Penjualan", description: `Penjualan ${invoiceNo}`, amount: total, source: "sale", saleId, createdAt: now });
      ctx.invalidateQueries();
      return { id: saleId, invoiceNo, total, changeAmount };
    },
  }),

  listSales: defineAction({
    request: z.object({ limit: z.number().int().positive().max(200).default(50) }),
    response: z.object({
      sales: z.array(z.object({
        id: z.number(), invoiceNo: z.string(), total: z.number(), paymentMethod: z.string(),
        amountPaid: z.number(), changeAmount: z.number(), createdAt: z.string(),
        items: z.array(z.object({ medicineName: z.string(), qty: z.number(), price: z.number(), subtotal: z.number() })),
      })),
    }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const rows = await db.select().from(schema.sales).orderBy(desc(schema.sales.id)).limit(args.limit);
      const allItems = await db.select().from(schema.saleItems);
      return { sales: rows.map(s => ({ id: s.id, invoiceNo: s.invoiceNo, total: s.total, paymentMethod: s.paymentMethod, amountPaid: s.amountPaid, changeAmount: s.changeAmount, createdAt: fmtDate(s.createdAt), items: allItems.filter(i => i.saleId === s.id).map(i => ({ medicineName: i.medicineName, qty: i.qty, price: i.price, subtotal: i.subtotal })) })) };
    },
  }),

  listCashEntries: defineAction({
    request: z.object({ limit: z.number().int().positive().max(500).default(200) }),
    response: z.object({
      entries: z.array(z.object({ id: z.number(), type: z.string(), category: z.string(), description: z.string(), amount: z.number(), source: z.string(), createdAt: z.string() })),
    }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const rows = await db.select().from(schema.cashEntries).orderBy(desc(schema.cashEntries.id)).limit(args.limit);
      return { entries: rows.map(r => ({ id: r.id, type: r.type, category: r.category, description: r.description, amount: r.amount, source: r.source, createdAt: fmtDate(r.createdAt) })) };
    },
  }),

  createCashEntry: defineAction({
    request: z.object({ type: z.enum(["in","out"]), category: z.string().min(1), description: z.string().min(1), amount: z.number().int().positive() }),
    response: z.object({ id: z.number() }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      const res = await db.insert(schema.cashEntries).values({ type: args.type, category: args.category, description: args.description, amount: args.amount, source: "manual" }).returning({ id: schema.cashEntries.id });
      ctx.invalidateQueries();
      return { id: res[0]!.id };
    },
  }),

  deleteCashEntry: defineAction({
    request: z.object({ id: z.number() }),
    response: z.object({ ok: z.boolean() }),
    async handler(ctx, args) {
      const db = ctx.db<typeof schema>();
      await db.delete(schema.cashEntries).where(eq(schema.cashEntries.id, args.id));
      ctx.invalidateQueries();
      return { ok: true };
    },
  }),

  getDashboard: defineAction({
    request: z.object({}),
    response: z.object({
      todayIn: z.number(), todayOut: z.number(), todayProfit: z.number(),
      monthIn: z.number(), monthOut: z.number(), monthProfit: z.number(),
      totalMedicines: z.number(), lowStockCount: z.number(), expiringCount: z.number(),
      totalSalesToday: z.number(),
      bestSellers: z.array(z.object({ name: z.string(), qty: z.number(), revenue: z.number() })),
      last7Days: z.array(z.object({ date: z.string(), label: z.string(), income: z.number(), expense: z.number() })),
      recentSales: z.array(z.object({ id: z.number(), invoiceNo: z.string(), total: z.number(), createdAt: z.string() })),
    }),
    async handler(ctx) {
      const db = ctx.db<typeof schema>();
      const entries = await db.select().from(schema.cashEntries);
      const meds = await db.select().from(schema.medicines);
      const salesRows = await db.select().from(schema.sales).orderBy(desc(schema.sales.id));
      const items = await db.select().from(schema.saleItems);
      const now = new Date();
      // All day/month boundaries in Jakarta time (see helpers above):
      // "hari ini" is [midnight WIB today, midnight WIB tomorrow).
      const startToday = jakartaDayStart(now);
      const endToday = jakartaDayStartOffset(now, 1);
      const startMonth = jakartaMonthStart(now);
      let todayIn=0, todayOut=0, monthIn=0, monthOut=0;
      for (const e of entries) {
        const t = e.createdAt.getTime();
        if (t >= startToday.getTime() && t < endToday.getTime()) { if (e.type==="in") todayIn+=e.amount; else todayOut+=e.amount; }
        if (t >= startMonth.getTime() && t < endToday.getTime()) { if (e.type==="in") monthIn+=e.amount; else monthOut+=e.amount; }
      }
      const lowStockCount = meds.filter(m => m.stock <= m.minStock).length;
      // Expiry dates are calendar dates (YYYY-MM-DD): compare them as
      // Jakarta date keys, not instants, so a medicine expiring today or
      // within the next 30 Jakarta days is counted.
      const todayKey = jakartaDateKey(now);
      const in30Key = jakartaDateKey(jakartaDayStartOffset(now, 30));
      const expiringCount = meds.filter(m => m.expiryDate && m.expiryDate >= todayKey && m.expiryDate <= in30Key).length;
      const todaySales = salesRows.filter(s => { const t = s.createdAt.getTime(); return t >= startToday.getTime() && t < endToday.getTime(); });
      // "Obat yang Terjual" on Beranda is a TODAY list: only items from
      // today's sales count. (It previously aggregated every sale ever
      // recorded, so it never reset on a new day.)
      const todaySaleIds = new Set(todaySales.map(s => s.id));
      const map = new Map<string, {qty:number; revenue:number}>();
      for (const it of items) { if (!todaySaleIds.has(it.saleId)) continue; const cur = map.get(it.medicineName) || {qty:0,revenue:0}; cur.qty+=it.qty; cur.revenue+=it.subtotal; map.set(it.medicineName, cur); }
      const bestSellers = Array.from(map.entries()).map(([name,v])=>({name, qty:v.qty, revenue:v.revenue})).sort((a,b)=>b.qty-a.qty).slice(0,5);
      const last7Days: {date:string;label:string;income:number;expense:number}[] = [];
      for (let i=6;i>=0;i--) {
        const d = jakartaDayStartOffset(now, -i);
        const next = new Date(d.getTime()+24*60*60*1000);
        let income=0, expense=0;
        for (const e of entries) { const t=e.createdAt.getTime(); if (t>=d.getTime() && t<next.getTime()) { if(e.type==="in") income+=e.amount; else expense+=e.amount; } }
        last7Days.push({ date: jakartaDateKey(d), label: jakartaDayLabel(d), income, expense });
      }
      return {
        todayIn, todayOut, todayProfit: todayIn-todayOut,
        monthIn, monthOut, monthProfit: monthIn-monthOut,
        totalMedicines: meds.length, lowStockCount, expiringCount,
        totalSalesToday: todaySales.length,
        bestSellers, last7Days,
        // "Penjualan Terakhir" on Beranda is also today-only: yesterday's
        // invoices stay in Riwayat, they are not deleted — they just no
        // longer appear as if they happened today.
        recentSales: todaySales.slice(0,5).map(s=>({id:s.id, invoiceNo:s.invoiceNo, total:s.total, createdAt: fmtDate(s.createdAt)})),
      };
    },
  }),

  getProfitReport: defineAction({
    request: z.object({ period: z.enum(["today", "month", "last30", "all"]).default("month") }),
    response: z.object({
      period: z.string(),
      revenue: z.number(), hpp: z.number(), grossProfit: z.number(), marginPct: z.number(),
      operationalExpense: z.number(), stockPurchaseExpense: z.number(), netProfit: z.number(),
      transactionCount: z.number(), itemsSold: z.number(), costMissingQty: z.number(),
      byMedicine: z.array(z.object({ name: z.string(), category: z.string(), ...profitAggShape })),
      byCategory: z.array(z.object({ category: z.string(), ...profitAggShape })),
      daily: z.array(z.object({ date: z.string(), label: z.string(), revenue: z.number(), hpp: z.number(), profit: z.number() })),
    }),
    async handler(ctx, args) {
      const r = await computeProfit(ctx, args.period);
      return { period: args.period, ...r };
    },
  }),

  downloadProfitReport: defineAction({
    request: z.object({ period: z.enum(["today", "month", "last30", "all"]).default("month") }),
    response: z.object({ ok: z.boolean(), url: z.string(), filename: z.string(), message: z.string() }),
    async handler(ctx, args) {
      const r = await computeProfit(ctx, args.period);
      if (r.transactionCount === 0) return { ok: false, url: "", filename: "", message: "Belum ada penjualan pada periode ini untuk dibuatkan laporan." };
      const periodLabel = args.period === "today" ? "Hari Ini" : args.period === "month" ? "Bulan Ini" : args.period === "last30" ? "30 Hari Terakhir" : "Semua";
      const wb = XLSX.utils.book_new();
      const summary = XLSX.utils.aoa_to_sheet([
        ["Laporan Laba Apotek"],
        ["Periode", periodLabel],
        ["Dibuat", new Date().toLocaleString("id-ID", { timeZone: JAKARTA_TZ })],
        [],
        ["Total Penjualan (Omzet)", r.revenue],
        ["HPP / Modal Obat Terjual", r.hpp],
        ["Laba Kotor", r.grossProfit],
        ["Margin Laba Kotor (%)", r.marginPct],
        ["Pengeluaran Operasional (di luar Beli Stok)", r.operationalExpense],
        ["Estimasi Laba Bersih", r.netProfit],
        [],
        ["Jumlah Transaksi", r.transactionCount],
        ["Item Terjual", r.itemsSold],
        ["Kas Keluar Beli Stok (info, tidak dikurangkan lagi)", r.stockPurchaseExpense],
      ]);
      summary["!cols"] = [{ wch: 46 }, { wch: 22 }];
      XLSX.utils.book_append_sheet(wb, summary, "Ringkasan");
      const medSheet = XLSX.utils.json_to_sheet(r.byMedicine.map(m => ({ "Nama Obat": m.name, "Kategori": m.category, "Qty Terjual": m.qty, "Penjualan": m.revenue, "HPP": m.hpp, "Laba": m.profit, "Margin (%)": m.marginPct })));
      medSheet["!cols"] = [{ wch: 34 }, { wch: 16 }, { wch: 12 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 11 }];
      XLSX.utils.book_append_sheet(wb, medSheet, "Per Obat");
      const catSheet = XLSX.utils.json_to_sheet(r.byCategory.map(c => ({ "Kategori": c.category, "Qty Terjual": c.qty, "Penjualan": c.revenue, "HPP": c.hpp, "Laba": c.profit, "Margin (%)": c.marginPct })));
      catSheet["!cols"] = [{ wch: 20 }, { wch: 12 }, { wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 11 }];
      XLSX.utils.book_append_sheet(wb, catSheet, "Per Kategori");
      addInfoSheet(wb, [
        "Laporan Laba — Keterangan",
        "",
        "Laba Kotor = Total Penjualan − HPP (harga beli obat yang terjual).",
        "Harga beli yang dipakai adalah harga beli saat obat terjual; untuk penjualan lama, dipakai harga beli obat saat ini.",
        "Estimasi Laba Bersih = Laba Kotor − Pengeluaran Operasional dari menu Keuangan.",
        "Pengeluaran 'Beli Stok' tidak dikurangkan lagi karena modal obat sudah dihitung lewat HPP.",
      ]);
      const stamp = jakartaStamp(new Date());
      const filename = `Laporan-Laba-${stamp}.xlsx`;
      const key = "downloads/laporan-laba.xlsx";
      await ctx.blobs.put(key, workbookBytes(wb), { contentType: XLSX_CONTENT_TYPE });
      const url = await ctx.blobs.getUrl(key);
      return { ok: true, url, filename, message: "" };
    },
  }),

  /* Fallback Web App Manifest, served as a real same-origin URL through
     blob storage (application/manifest+json). The primary manifest is the
     static file ./assets/manifest.json copied into the client build by
     client/build.mjs; the client only asks for this URL when that file
     cannot be fetched. Icon and start URLs are relative to this blob's
     location (./blobs/<key>): "../assets/…" resolves to the built icons and
     "../" to the app itself, mirroring the static manifest. A blob: or
     data: URL created in the page is never used as the manifest. */
  getWebAppManifest: defineAction({
    request: z.object({}),
    response: z.object({ ok: z.literal(true), url: z.string() }),
    async handler(ctx): Promise<{ ok: true; url: string }> {
      const manifest = {
        name: "Aplikasi Apotik Lestari",
        short_name: "Apotik Lestari",
        description: "Kasir, stok obat, keuangan, dan laporan laba Apotek Lestari.",
        lang: "id",
        dir: "ltr",
        id: "../",
        start_url: "../",
        scope: "../",
        display: "standalone",
        orientation: "any",
        background_color: "#F5FAF9",
        theme_color: "#00696D",
        icons: [
          { src: "../assets/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
          { src: "../assets/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
          { src: "../assets/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      };
      const bytes = new TextEncoder().encode(JSON.stringify(manifest, null, 2));
      const key = "pwa/manifest.json";
      await ctx.blobs.put(key, bytes, { contentType: "application/manifest+json" });
      const url = await ctx.blobs.getUrl(key);
      return { ok: true as const, url };
    },
  }),
} satisfies ActionsModule;
