package id.apotek.lestari.data

import id.apotek.lestari.data.local.CashEntryEntity
import id.apotek.lestari.data.local.DatabaseProvider
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.data.local.SaleEntity
import id.apotek.lestari.data.local.SaleItemEntity
import androidx.room.withTransaction
import id.apotek.lestari.ui.theme.dayKeyID
import id.apotek.lestari.ui.theme.parseISODate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Repository — satu-satunya pintu data untuk UI.
 *
 * MODE OFFLINE: semua data tersimpan di HP dalam database lokal Room
 * (SQLite, file "apotek-lestari.db"). Tidak ada server, tidak butuh
 * internet, dan data tetap ada setelah aplikasi ditutup/dibuka lagi.
 * Aplikasi mulai KOSONG — pengguna menambah obat dulu lewat Stok,
 * sama seperti versi web. Logika bisnis (dashboard, laba/HPP, batas
 * tanggal) mengikuti server/src/actions.ts di web app, dengan semua
 * batas hari/bulan dihitung dalam zona Asia/Jakarta (WIB).
 */
class Repository {

    private val jakarta: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Terjadi kesalahan", e)
    }

    // ---- Waktu: semua batas hari/bulan dalam Asia/Jakarta (WIB) ----

    /** Waktu sekarang sebagai string ISO UTC, sama seperti fmtDate() di web. */
    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    /** Awal hari (00:00 WIB) pada tanggal hari ini + offset, dalam milidetik epoch. */
    private fun jakartaDayStartMillis(offsetDays: Int = 0): Long =
        Calendar.getInstance(jakarta).apply {
            time = Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }.timeInMillis

    /** Awal bulan ini (00:00 WIB tanggal 1), dalam milidetik epoch. */
    private fun jakartaMonthStartMillis(): Long =
        Calendar.getInstance(jakarta).apply {
            time = Date()
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Label hari Indonesia: "Sen", "Sel", ... "Min". */
    private fun dayLabelID(date: Date): String =
        SimpleDateFormat("EEE", Locale("id", "ID")).apply { timeZone = jakarta }.format(date)

    private fun marginPctOf(revenue: Int, profit: Int): Double =
        if (revenue > 0) Math.round(profit.toDouble() / revenue * 1000) / 10.0 else 0.0

    // ---- Pemetaan Entity -> Model UI ----

    private fun MedicineEntity.toModel() = Medicine(
        id, name, category, unit, buyPrice, sellPrice, stock, minStock, expiryDate, barcode, createdAt
    )

    private fun CashEntryEntity.toModel() = CashEntry(
        id, type, category, description, amount, source, createdAt
    )

    // ---- Obat ----

    suspend fun getDashboard(): ApiResult<Dashboard> = call {
        val db = DatabaseProvider.db
        val entries = db.cashDao().getAll()
        val meds = db.medicineDao().getAll()
        val salesRows = db.saleDao().getAll()
        val items = db.saleDao().getAllItems()
        val now = Date()
        val startToday = jakartaDayStartMillis(0)
        val endToday = jakartaDayStartMillis(1)
        val startMonth = jakartaMonthStartMillis()
        val todayKey = dayKeyID(now)
        val in30Key = dayKeyID(Date(jakartaDayStartMillis(30)))

        fun instantOf(iso: String): Long? = parseISODate(iso)?.time
        var todayIn = 0
        var todayOut = 0
        var monthIn = 0
        var monthOut = 0
        for (e in entries) {
            val t = instantOf(e.createdAt) ?: continue
            if (t in startToday until endToday) {
                if (e.type == "in") todayIn += e.amount else todayOut += e.amount
            }
            if (t in startMonth until endToday) {
                if (e.type == "in") monthIn += e.amount else monthOut += e.amount
            }
        }
        // Stok menipis: di bawah 20 (sama dengan status "Rendah" di Stok).
        val lowStockCount = meds.count { it.stock < 10 }
        // Kadaluarsa: tanggal kalender hari ini s.d. 30 hari ke depan (WIB).
        val expiringCount = meds.count {
            it.expiryDate != null && it.expiryDate >= todayKey && it.expiryDate <= in30Key
        }
        val todaySales = salesRows.filter { (instantOf(it.createdAt) ?: -1) in startToday until endToday }
        // "Obat yang Terjual" di Beranda hanya dari penjualan hari ini.
        val todaySaleIds = todaySales.map { it.id }.toSet()
        val bestSellers = items.filter { it.saleId in todaySaleIds }
            .groupBy { it.medicineName }
            .map { (name, list) -> BestSeller(name, list.sumOf { it.qty }, list.sumOf { it.subtotal }) }
            .sortedByDescending { it.qty }
        val last7Days = (6 downTo 0).map { offset ->
            val dayStart = jakartaDayStartMillis(-offset)
            val next = dayStart + 24L * 60 * 60 * 1000
            var income = 0
            var expense = 0
            for (e in entries) {
                val t = instantOf(e.createdAt) ?: continue
                if (t in dayStart until next) {
                    if (e.type == "in") income += e.amount else expense += e.amount
                }
            }
            val date = Date(dayStart)
            DayCash(dayKeyID(date), dayLabelID(date), income, expense)
        }
        Dashboard(
            todayIn = todayIn, todayOut = todayOut, todayProfit = todayIn - todayOut,
            monthIn = monthIn, monthOut = monthOut, monthProfit = monthIn - monthOut,
            totalMedicines = meds.size, lowStockCount = lowStockCount, expiringCount = expiringCount,
            totalSalesToday = todaySales.size, bestSellers = bestSellers, last7Days = last7Days,
            // "Penjualan Terakhir" di Beranda juga hanya hari ini.
            recentSales = todaySales.take(5).map { RecentSale(it.id, it.invoiceNo, it.total, it.createdAt) }
        )
    }

    suspend fun listMedicines(search: String = ""): ApiResult<ListMedicinesResponse> = call {
        val rows = DatabaseProvider.db.medicineDao().getAll()
        val q = search.trim()
        val filtered = if (q.isBlank()) rows else rows.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                (it.barcode?.contains(q) == true)
        }
        ListMedicinesResponse(filtered.map { it.toModel() })
    }

    suspend fun findByBarcode(code: String): ApiResult<FindByBarcodeResponse> = call {
        val med = DatabaseProvider.db.medicineDao().findByBarcode(code.trim())
        FindByBarcodeResponse(med?.toModel())
    }

    suspend fun createMedicine(args: MedicineArgs): ApiResult<IdResponse> = call {
        val dao = DatabaseProvider.db.medicineDao()
        val barcode = args.barcode?.trim().orEmpty().ifBlank { null }
        if (barcode != null) {
            val dup = dao.findByBarcode(barcode)
            if (dup != null) throw IllegalStateException("Barcode $barcode sudah dipakai untuk ${dup.name}")
        }
        val id = dao.insert(
            MedicineEntity(
                name = args.name, category = args.category, unit = args.unit,
                buyPrice = args.buyPrice, sellPrice = args.sellPrice,
                stock = args.stock, minStock = args.minStock,
                expiryDate = args.expiryDate, barcode = barcode, createdAt = nowIso()
            )
        ).toInt()
        IdResponse(id)
    }

    suspend fun updateMedicine(args: UpdateMedicineArgs): ApiResult<OkResponse> = call {
        val dao = DatabaseProvider.db.medicineDao()
        val barcode = args.barcode?.trim().orEmpty().ifBlank { null }
        if (barcode != null) {
            val dup = dao.findByBarcode(barcode)
            if (dup != null && dup.id != args.id) {
                throw IllegalStateException("Barcode $barcode sudah dipakai untuk ${dup.name}")
            }
        }
        val old = dao.getById(args.id)
        dao.update(
            MedicineEntity(
                id = args.id, name = args.name, category = args.category, unit = args.unit,
                buyPrice = args.buyPrice, sellPrice = args.sellPrice,
                stock = args.stock, minStock = args.minStock,
                expiryDate = args.expiryDate, barcode = barcode,
                createdAt = old?.createdAt ?: nowIso()
            )
        )
        OkResponse(true)
    }

    suspend fun deleteMedicine(id: Int): ApiResult<OkResponse> = call {
        // Riwayat penjualan tetap utuh: item penjualan menyimpan snapshot
        // nama obat, jadi menghapus obat tidak menghapus riwayat.
        DatabaseProvider.db.medicineDao().deleteById(id)
        OkResponse(true)
    }

    suspend fun bulkImport(args: BulkImportArgs): ApiResult<BulkImportResponse> = call {
        val dao = DatabaseProvider.db.medicineDao()
        var inserted = 0
        var updated = 0
        var skipped = 0
        for (item in args.items) {
            if (item.name.isBlank()) {
                skipped++
                continue
            }
            val barcode = item.barcode?.trim().orEmpty().ifBlank { null }
            val existing = if (barcode != null) dao.findByBarcode(barcode) else null
            if (existing != null) {
                dao.update(
                    existing.copy(
                        name = item.name, category = item.category, unit = item.unit,
                        buyPrice = item.buyPrice, sellPrice = item.sellPrice,
                        stock = item.stock, minStock = item.minStock,
                        expiryDate = item.expiryDate, barcode = barcode
                    )
                )
                updated++
            } else {
                dao.insert(
                    MedicineEntity(
                        name = item.name, category = item.category, unit = item.unit,
                        buyPrice = item.buyPrice, sellPrice = item.sellPrice,
                        stock = item.stock, minStock = item.minStock,
                        expiryDate = item.expiryDate, barcode = barcode, createdAt = nowIso()
                    )
                )
                inserted++
            }
        }
        BulkImportResponse(inserted = inserted, updated = updated, skipped = skipped, total = args.items.size)
    }

    suspend fun downloadTemplate(): ApiResult<DownloadResponse> =
        ApiResult.Error("Download template tidak tersedia di mode offline")

    suspend fun downloadExport(): ApiResult<DownloadResponse> =
        ApiResult.Error("Export tidak tersedia di mode offline")

    // ---- Penjualan ----

    suspend fun createSale(args: CreateSaleArgs): ApiResult<CreateSaleResponse> = call {
        val db = DatabaseProvider.db
        if (args.items.isEmpty()) throw IllegalArgumentException("Keranjang kosong")
        var total = 0
        data class Line(val med: MedicineEntity, val qty: Int, val subtotal: Int)
        val lines = args.items.map { item ->
            val med = db.medicineDao().getById(item.medicineId)
                ?: throw IllegalStateException("Obat tidak ditemukan")
            if (med.stock < item.qty) throw IllegalStateException("Stok ${med.name} tidak cukup")
            val subtotal = med.sellPrice * item.qty
            total += subtotal
            Line(med, item.qty, subtotal)
        }
        val change = (args.amountPaid - total).coerceAtLeast(0)
        val now = Date()
        val invoiceNo = "INV-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = jakarta
        }.format(now)
        val createdAt = nowIso()
        val saleId = db.saleDao().insert(
            SaleEntity(
                invoiceNo = invoiceNo, total = total, paymentMethod = args.paymentMethod,
                amountPaid = args.amountPaid, changeAmount = change, createdAt = createdAt
            )
        ).toInt()
        db.saleDao().insertItems(
            lines.map {
                SaleItemEntity(
                    saleId = saleId, medicineId = it.med.id, medicineName = it.med.name,
                    qty = it.qty, price = it.med.sellPrice, buyPrice = it.med.buyPrice,
                    subtotal = it.subtotal
                )
            }
        )
        // Kurangi stok obat yang terjual
        lines.forEach { line ->
            db.medicineDao().update(line.med.copy(stock = (line.med.stock - line.qty).coerceAtLeast(0)))
        }
        // Catat kas masuk supaya Keuangan & Beranda ikut berubah
        db.cashDao().insert(
            CashEntryEntity(
                type = "in", category = "Penjualan", description = "Penjualan $invoiceNo",
                amount = total, source = "sale", createdAt = createdAt
            )
        )
        CreateSaleResponse(saleId, invoiceNo, total, change)
    }

    suspend fun listSales(limit: Int = 50): ApiResult<ListSalesResponse> = call {
        val db = DatabaseProvider.db
        val medById = db.medicineDao().getAll().associateBy { it.id }
        val sales = db.saleDao().getAll().take(limit).map { s ->
            val items = db.saleDao().getItemsForSale(s.id).map {
                val med = medById[it.medicineId]
                SaleItem(it.medicineName, it.qty, it.price, it.subtotal, med?.category ?: "", med?.unit ?: "")
            }
            Sale(s.id, s.invoiceNo, s.total, s.paymentMethod, s.amountPaid, s.changeAmount, s.createdAt, items)
        }
        ListSalesResponse(sales)
    }

    // ---- Kas ----

    suspend fun listCashEntries(limit: Int = 200): ApiResult<ListCashResponse> = call {
        val entries = DatabaseProvider.db.cashDao().getAll().take(limit).map { it.toModel() }
        ListCashResponse(entries)
    }

    suspend fun createCashEntry(args: CreateCashEntryArgs): ApiResult<IdResponse> = call {
        val id = DatabaseProvider.db.cashDao().insert(
            CashEntryEntity(
                type = args.type, category = args.category, description = args.description,
                amount = args.amount, source = "manual", createdAt = nowIso()
            )
        ).toInt()
        IdResponse(id)
    }

    suspend fun deleteCashEntry(id: Int): ApiResult<OkResponse> = call {
        DatabaseProvider.db.cashDao().deleteById(id)
        OkResponse(true)
    }

    // ---- Laporan Laba (dihitung dari data lokal, seperti computeProfit di web) ----

    suspend fun getProfitReport(period: String = "month"): ApiResult<ProfitReport> = call {
        val db = DatabaseProvider.db
        val salesRows = db.saleDao().getAll()
        val items = db.saleDao().getAllItems()
        val meds = db.medicineDao().getAll()
        val entries = db.cashDao().getAll()
        val medById = meds.associateBy { it.id }
        val now = Date()
        val startToday = jakartaDayStartMillis(0)
        val endToday = jakartaDayStartMillis(1)
        val startMonth = jakartaMonthStartMillis()
        val startLast30 = jakartaDayStartMillis(-29)
        val start = when (period) {
            "today" -> startToday
            "month" -> startMonth
            "last30" -> startLast30
            else -> 0L // "all"
        }
        val end = if (period == "all") Long.MAX_VALUE else endToday
        fun inPeriod(iso: String): Boolean {
            val t = parseISODate(iso)?.time ?: return false
            return t in start until end
        }
        val periodSales = salesRows.filter { inPeriod(it.createdAt) }
        val periodSaleIds = periodSales.map { it.id }.toSet()
        val periodItems = items.filter { it.saleId in periodSaleIds }

        // Biaya tiap item: snapshot harga beli saat transaksi; kalau 0,
        // pakai harga beli obat saat ini; kalau obat sudah dihapus dan tidak
        // ada snapshot, biayanya 0 dan dihitung di costMissingQty.
        fun costOf(it: SaleItemEntity): Int =
            if (it.buyPrice > 0) it.buyPrice else (medById[it.medicineId]?.buyPrice ?: 0)

        val revenue = periodItems.sumOf { it.subtotal }
        val hpp = periodItems.sumOf { costOf(it) * it.qty }
        val grossProfit = revenue - hpp
        val marginPct = marginPctOf(revenue, grossProfit)
        val itemsSold = periodItems.sumOf { it.qty }
        val costMissingQty = periodItems.filter { costOf(it) == 0 }.sumOf { it.qty }

        val periodOut = entries.filter { it.type == "out" && inPeriod(it.createdAt) }
        val operationalExpense = periodOut.filter { it.category != "Beli Stok" }.sumOf { it.amount }
        val stockPurchaseExpense = periodOut.filter { it.category == "Beli Stok" }.sumOf { it.amount }
        val netProfit = grossProfit - operationalExpense

        data class Agg(var qty: Int = 0, var revenue: Int = 0, var hpp: Int = 0)
        val medAgg = linkedMapOf<String, Agg>()
        val medCategory = mutableMapOf<String, String>()
        val catAgg = linkedMapOf<String, Agg>()
        for (it in periodItems) {
            val cost = costOf(it) * it.qty
            val category = medById[it.medicineId]?.category ?: "Lainnya"
            val cur = medAgg.getOrPut(it.medicineName) { Agg() }
            cur.qty += it.qty; cur.revenue += it.subtotal; cur.hpp += cost
            medCategory[it.medicineName] = category
            val cc = catAgg.getOrPut(category) { Agg() }
            cc.qty += it.qty; cc.revenue += it.subtotal; cc.hpp += cost
        }
        fun withMargin(a: Agg) = ProfitAgg(
            a.qty, a.revenue, a.hpp, a.revenue - a.hpp, marginPctOf(a.revenue, a.revenue - a.hpp)
        )
        val byMedicine = medAgg.entries.map { (name, a) ->
            val m = withMargin(a)
            ProfitByMedicine(name, medCategory[name] ?: "Lainnya", m.qty, m.revenue, m.hpp, m.profit, m.marginPct)
        }.sortedByDescending { it.profit }
        val byCategory = catAgg.entries.map { (category, a) ->
            val m = withMargin(a)
            ProfitByCategory(category, m.qty, m.revenue, m.hpp, m.profit, m.marginPct)
        }.sortedByDescending { it.profit }

        // Laba kotor 7 hari terakhir (tidak tergantung periode yang dipilih)
        val saleById = salesRows.associateBy { it.id }
        val daily = (6 downTo 0).map { offset ->
            val dayStart = jakartaDayStartMillis(-offset)
            val next = dayStart + 24L * 60 * 60 * 1000
            var rev = 0
            var cost = 0
            for (it in items) {
                val sale = saleById[it.saleId] ?: continue
                val t = parseISODate(sale.createdAt)?.time ?: continue
                if (t in dayStart until next) {
                    rev += it.subtotal
                    cost += costOf(it) * it.qty
                }
            }
            val date = Date(dayStart)
            ProfitDaily(dayKeyID(date), dayLabelID(date), rev, cost, rev - cost)
        }

        ProfitReport(
            period = period, revenue = revenue, hpp = hpp, grossProfit = grossProfit,
            marginPct = marginPct, operationalExpense = operationalExpense,
            stockPurchaseExpense = stockPurchaseExpense, netProfit = netProfit,
            transactionCount = periodSales.size, itemsSold = itemsSold,
            costMissingQty = costMissingQty, byMedicine = byMedicine,
            byCategory = byCategory, daily = daily
        )
    }

    suspend fun downloadProfitReport(period: String = "month"): ApiResult<DownloadResponse> =
        ApiResult.Error("Download laporan tidak tersedia di mode offline")

    suspend fun getWebAppManifest(): ApiResult<ManifestResponse> =
        ApiResult.Error("Manifest web tidak tersedia di mode offline")

    // ---- Backup & Restore (semua data lokal dalam satu file JSON) ----

    /** Seluruh isi database (obat, penjualan, item, kas) sebagai string JSON. */
    suspend fun exportBackup(): ApiResult<String> = call {
        val db = DatabaseProvider.db
        val data = BackupData(
            medicines = db.medicineDao().getAll(),
            sales = db.saleDao().getAll(),
            saleItems = db.saleDao().getAllItems(),
            cashEntries = db.cashDao().getAll()
        )
        BackupHelper.buildJson(data)
    }

    /**
     * Pulihkan data dari file backup JSON. MENGGANTI seluruh data saat ini
     * dengan isi backup (ID dipertahankan supaya relasi penjualan-item utuh).
     * Dijalankan dalam satu transaksi — kalau ada yang gagal, tidak ada
     * data yang berubah.
     */
    suspend fun importBackup(json: String): ApiResult<String> = call {
        val data = BackupHelper.parseJson(json) // melempar kalau format bukan backup
        val db = DatabaseProvider.db
        db.withTransaction {
            db.medicineDao().deleteAll()
            db.saleDao().deleteAll()
            db.saleDao().deleteAllItems()
            db.cashDao().deleteAll()
            data.medicines.forEach { db.medicineDao().insert(it) }
            data.sales.forEach { db.saleDao().insert(it) }
            if (data.saleItems.isNotEmpty()) db.saleDao().insertItems(data.saleItems)
            data.cashEntries.forEach { db.cashDao().insert(it) }
        }
        "Dipulihkan: ${data.medicines.size} obat, ${data.sales.size} penjualan, ${data.saleItems.size} item, ${data.cashEntries.size} kas."
    }
}

