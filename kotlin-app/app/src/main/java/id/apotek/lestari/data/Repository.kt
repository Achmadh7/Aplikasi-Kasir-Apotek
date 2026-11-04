package id.apotek.lestari.data

import id.apotek.lestari.data.local.CashEntryEntity
import id.apotek.lestari.data.local.DatabaseProvider
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.data.local.PurchaseEntity
import id.apotek.lestari.data.local.PurchaseItemEntity
import id.apotek.lestari.data.local.SaleEntity
import id.apotek.lestari.data.local.SaleItemEntity
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import id.apotek.lestari.ui.theme.dayKeyID
import id.apotek.lestari.ui.theme.tr
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
    } catch (e: CancellationException) {
        throw e
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

    /** Label tanggal Indonesia: "27 Okt" (untuk grafik laba per periode). */
    private fun dayDateLabelID(date: Date): String =
        SimpleDateFormat("d MMM", Locale("id", "ID")).apply { timeZone = jakarta }.format(date)

    private fun marginPctOf(revenue: Int, profit: Int): Double =
        if (revenue > 0) Math.round(profit.toDouble() / revenue * 1000) / 10.0 else 0.0

    // ---- Pemetaan Entity -> Model UI ----

    private fun MedicineEntity.toModel() = Medicine(
        id, name, category, unit, buyPrice, sellPrice, wholesalePrice, stock, expiryDate, barcode, createdAt
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
        val startToday = jakartaDayStartMillis(0)
        val endToday = jakartaDayStartMillis(1)
        val startYesterday = jakartaDayStartMillis(-1)
        val startMonth = jakartaMonthStartMillis()
        val in30Key = dayKeyID(Date(jakartaDayStartMillis(30)))
        val medById = meds.associateBy { it.id }

        fun instantOf(iso: String): Long? = parseISODate(iso)?.time
        var todayIn = 0
        var yesterdayIn = 0
        var todayOut = 0
        var monthIn = 0
        var monthOut = 0
        for (e in entries) {
            val t = instantOf(e.createdAt) ?: continue
            // Pemasukan hanya dari penjualan (source "sale") — kas masuk manual
            // (mis. tambah modal) tidak ikut, supaya tidak nyampur dengan hasil
            // penjualan. Kas masuk manual tetap tercatat di Saldo Kas (Keuangan).
            if (t in startToday until endToday) {
                if (e.type == "in" && e.source == "sale") todayIn += e.amount
                else if (e.type == "out") todayOut += e.amount
            }
            // Pemasukan kemarin (untuk insight "vs kemarin" di hero Beranda).
            if (t in startYesterday until startToday) {
                if (e.type == "in" && e.source == "sale") yesterdayIn += e.amount
            }
            if (t in startMonth until endToday) {
                if (e.type == "in" && e.source == "sale") monthIn += e.amount
                else if (e.type == "out") monthOut += e.amount
            }
        }
        // Stok perlu perhatian: Habis (0) + Rendah (1-9) pada skala status
        // stok yang baru — dipakai Beranda sebagai penanda ada stok mendesak.
        val lowStockCount = meds.count { it.stock < 10 }
        // Kadaluarsa: semua obat yang kedaluwarsa maupun akan kedaluwarsa
        // dalam 30 hari ke depan (WIB) — tanpa batas bawah, supaya obat yang
        // SUDAH kedaluwarsa ikut terhitung.
        val expiringCount = meds.count {
            it.expiryDate != null && it.expiryDate <= in30Key
        }
        val todaySales = salesRows.filter { (instantOf(it.createdAt) ?: -1) in startToday until endToday }
        // "Obat yang Terjual" di Beranda hanya dari penjualan hari ini.
        val todaySaleIds = todaySales.map { it.id }.toSet()
        val bestSellers = items.filter { it.saleId in todaySaleIds }
            .groupBy { it.medicineId }
            .map { (id, list) -> BestSeller(medById[id]?.name ?: list.first().medicineName, list.sumOf { it.qty }, list.sumOf { it.subtotal }) }
            .sortedByDescending { it.qty }
        val last7Days = (6 downTo 0).map { offset ->
            val dayStart = jakartaDayStartMillis(-offset)
            val next = dayStart + 24L * 60 * 60 * 1000
            var income = 0
            var expense = 0
            for (e in entries) {
                val t = instantOf(e.createdAt) ?: continue
                if (t in dayStart until next) {
                    if (e.type == "in" && e.source == "sale") income += e.amount
                    else if (e.type == "out") expense += e.amount
                }
            }
            val date = Date(dayStart)
            DayCash(dayKeyID(date), dayLabelID(date), income, expense)
        }
        Dashboard(
            todayIn = todayIn, yesterdayIn = yesterdayIn, todayOut = todayOut, todayProfit = todayIn - todayOut,
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
                (it.barcode?.contains(q, ignoreCase = true) == true)
        }
        ListMedicinesResponse(filtered.map { it.toModel() })
    }

    suspend fun findByBarcode(code: String): ApiResult<FindByBarcodeResponse> = call {
        val med = DatabaseProvider.db.medicineDao().findByBarcode(code.trim())
        FindByBarcodeResponse(med?.toModel())
    }

    suspend fun createMedicine(args: MedicineArgs): ApiResult<IdResponse> = call {
        if (args.name.isBlank()) throw IllegalArgumentException(tr("Nama obat tidak boleh kosong", "Medicine name must not be empty"))
        if (args.buyPrice <= 0) throw IllegalArgumentException(tr("Harga beli harus lebih dari 0", "Buy price must be more than 0"))
        if (args.sellPrice <= 0) throw IllegalArgumentException(tr("Harga jual harus lebih dari 0", "Sell price must be more than 0"))
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
                wholesalePrice = args.wholesalePrice,
                stock = args.stock,
                expiryDate = args.expiryDate, barcode = barcode, createdAt = nowIso()
            )
        ).toInt()
        IdResponse(id)
    }

    suspend fun updateMedicine(args: UpdateMedicineArgs): ApiResult<OkResponse> = call {
        if (args.name.isBlank()) throw IllegalArgumentException(tr("Nama obat tidak boleh kosong", "Medicine name must not be empty"))
        if (args.buyPrice <= 0) throw IllegalArgumentException(tr("Harga beli harus lebih dari 0", "Buy price must be more than 0"))
        if (args.sellPrice <= 0) throw IllegalArgumentException(tr("Harga jual harus lebih dari 0", "Sell price must be more than 0"))
        val dao = DatabaseProvider.db.medicineDao()
        val barcode = args.barcode?.trim().orEmpty().ifBlank { null }
        if (barcode != null) {
            val dup = dao.findByBarcode(barcode)
            if (dup != null && dup.id != args.id) {
                throw IllegalStateException("Barcode $barcode sudah dipakai untuk ${dup.name}")
            }
        }
        val old = dao.getById(args.id)
        val changed = dao.update(
            MedicineEntity(
                id = args.id, name = args.name, category = args.category, unit = args.unit,
                buyPrice = args.buyPrice, sellPrice = args.sellPrice,
                wholesalePrice = args.wholesalePrice,
                stock = args.stock,
                expiryDate = args.expiryDate, barcode = barcode,
                createdAt = old?.createdAt ?: nowIso()
            )
        )
        if (changed == 0) throw IllegalStateException("Obat tidak ditemukan")
        OkResponse(true)
    }

    suspend fun deleteMedicine(id: Int): ApiResult<OkResponse> = call {
        // Riwayat penjualan tetap utuh: item penjualan menyimpan snapshot
        // nama obat, jadi menghapus obat tidak menghapus riwayat.
        val deleted = DatabaseProvider.db.medicineDao().deleteById(id)
        if (deleted == 0) throw IllegalStateException("Obat tidak ditemukan")
        OkResponse(true)
    }

    suspend fun bulkImport(args: BulkImportArgs): ApiResult<BulkImportResponse> = call {
        val db = DatabaseProvider.db
        val dao = db.medicineDao()
        var inserted = 0
        var updated = 0
        var skipped = 0
        db.withTransaction {
            for (item in args.items) {
                if (item.name.isBlank()) {
                    skipped++
                    continue
                }
                if (item.buyPrice <= 0 || item.sellPrice <= 0) {
                    skipped++
                    continue
                }
                val barcode = item.barcode?.trim().orEmpty().ifBlank { null }
                // Baris tanpa barcode: cocokkan juga via nama (+ kategori/satuan,
                // case-insensitive) supaya impor ulang tidak menggandakan obat.
                val existing = if (barcode != null) {
                    dao.findByBarcode(barcode)
                } else {
                    dao.getAll().firstOrNull {
                        it.name.trim().equals(item.name.trim(), ignoreCase = true) &&
                            it.category.trim().equals(item.category.trim(), ignoreCase = true) &&
                            it.unit.trim().equals(item.unit.trim(), ignoreCase = true)
                    }
                }
                if (existing != null) {
                    dao.update(
                        existing.copy(
                            name = item.name, category = item.category, unit = item.unit,
                            buyPrice = item.buyPrice, sellPrice = item.sellPrice,
                            // Harga grosir dari file dipakai bila terisi (> 0);
                            // file lama tanpa kolom itu / sel kosong TIDAK
                            // menghapus harga grosir yang sudah tersimpan.
                            wholesalePrice = if (item.wholesalePrice > 0) item.wholesalePrice else existing.wholesalePrice,
                            stock = item.stock,
                            // Baris impor tanpa barcode: pertahankan barcode lama
                            // (jangan ditimpa null — obat jadi tidak bisa dipindai).
                            expiryDate = item.expiryDate, barcode = barcode ?: existing.barcode
                        )
                    )
                    updated++
                } else {
                    dao.insert(
                        MedicineEntity(
                            name = item.name, category = item.category, unit = item.unit,
                            buyPrice = item.buyPrice, sellPrice = item.sellPrice,
                            wholesalePrice = item.wholesalePrice,
                            stock = item.stock,
                            expiryDate = item.expiryDate, barcode = barcode, createdAt = nowIso()
                        )
                    )
                    inserted++
                }
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
        if (args.items.isEmpty()) throw IllegalArgumentException(tr("Keranjang kosong", "The cart is empty"))
        for (item in args.items) {
            if (item.qty <= 0) throw IllegalArgumentException(tr("Jumlah obat harus lebih dari 0", "Quantity must be more than 0"))
        }
        // Agregasi qty per obat dulu (D19): keranjang bisa berisi obat yang
        // sama dua kali, jadi cek stok harus atas total qty gabungan.
        val qtyById = args.items.groupBy { it.medicineId }
            .mapValues { (_, list) -> list.sumOf { it.qty } }
        var total = 0
        data class Line(val med: MedicineEntity, val qty: Int, val unitPrice: Int, val priceType: String, val subtotal: Int)
        val lines = args.items.map { item ->
            val med = db.medicineDao().getById(item.medicineId)
                ?: throw IllegalStateException("Obat tidak ditemukan")
            // Harga satuan yang berlaku: grosir bila dipilih DAN harga grosir
            // obat itu sudah diatur (> 0); selain itu harga jual ecer biasa.
            // Jenisnya disimpan juga (priceType) untuk keterangan di invoice.
            val wholesale = item.useWholesale && med.wholesalePrice > 0
            val unitPrice = if (wholesale) med.wholesalePrice else med.sellPrice
            val subtotal = unitPrice * item.qty
            total += subtotal
            Line(med, item.qty, unitPrice, if (wholesale) "GROSIR" else "ECER", subtotal)
        }
        // Kurang bayar ditolak sebagai Error (bukan lunas dengan kembalian 0).
        if (args.amountPaid < total) throw IllegalArgumentException(tr("Uang dibayar kurang dari total", "The amount paid is less than the total"))
        val change = args.amountPaid - total
        val now = Date()
        // Format nomor invoice: "INV - dd/MM/yy" (mis. "INV - 01/11/26"),
        // tanpa nomor urut supaya pendek (permintaan Achmad 2026-11-01).
        // Konsekuensi yang disadari: semua penjualan di hari yang sama
        // berbagi nomor invoice yang sama; data penjualan sendiri tetap
        // terpisah per ID internal.
        val invoiceNo = "INV - " + SimpleDateFormat("dd/MM/yy", Locale("id", "ID")).apply {
            timeZone = jakarta
        }.format(now)
        val createdAt = nowIso()
        val nameById = lines.associate { it.med.id to it.med.name }
        db.withTransaction {
            // Kurangi stok secara atomik; decrementStock return 0 = stok tidak cukup.
            for ((medicineId, qty) in qtyById) {
                val changed = db.medicineDao().decrementStock(medicineId, qty)
                if (changed == 0) throw IllegalStateException(tr("Stok ${nameById[medicineId] ?: "obat"} tidak cukup", "Not enough stock of ${nameById[medicineId] ?: "the medicine"}"))
            }
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
                        qty = it.qty, price = it.unitPrice, priceType = it.priceType, buyPrice = it.med.buyPrice,
                        subtotal = it.subtotal, category = it.med.category, unit = it.med.unit
                    )
                }
            )
            // Catat kas masuk supaya Keuangan & Beranda ikut berubah
            db.cashDao().insert(
                CashEntryEntity(
                    type = "in", category = "Penjualan", description = "Penjualan $invoiceNo",
                    amount = total, source = "sale", createdAt = createdAt
                )
            )
            CreateSaleResponse(saleId, invoiceNo, total, change)
        }
    }

    // Tanpa batas bawaan (audit 2026-11-02, M6): getAll() memang sudah
    // memuat semua baris, jadi take(200) sebelumnya hanya memotong
    // tampilan Riwayat secara diam-diam — penjualan lama seolah hilang
    // padahal Laba tetap menghitungnya.
    suspend fun listSales(limit: Int = Int.MAX_VALUE): ApiResult<ListSalesResponse> = call {
        val db = DatabaseProvider.db
        val sales = db.saleDao().getAll().take(limit).map { s ->
            val items = db.saleDao().getItemsForSale(s.id).map {
                SaleItem(it.medicineName, it.qty, it.price, it.subtotal, it.category, it.unit, it.priceType)
            }
            Sale(s.id, s.invoiceNo, s.total, s.paymentMethod, s.amountPaid, s.changeAmount, s.createdAt, items)
        }
        ListSalesResponse(sales)
    }

    // ---- Kas ----

    suspend fun getCashBalance(): ApiResult<CashBalance> = call {
        val dao = DatabaseProvider.db.cashDao()
        CashBalance(dao.sumIn(), dao.sumOut())
    }

    // Bawaan: muat SEMUA entri (bukan 200 teratas). Keuangan menyaring per
    // periode di UI dari daftar ini dan Saldo Kas dihitung dari data lengkap,
    // jadi daftar yang terpotong membuat daftar dan saldo tidak cocok.
    suspend fun listCashEntries(limit: Int = Int.MAX_VALUE): ApiResult<ListCashResponse> = call {
        val entries = DatabaseProvider.db.cashDao().getAll().take(limit).map { it.toModel() }
        ListCashResponse(entries)
    }

    suspend fun createCashEntry(args: CreateCashEntryArgs): ApiResult<IdResponse> = call {
        val id = DatabaseProvider.db.cashDao().insert(
            CashEntryEntity(
                type = args.type, category = args.category, description = args.description,
                amount = args.amount, source = "manual", createdAt = args.createdAt ?: nowIso()
            )
        ).toInt()
        IdResponse(id)
    }

    suspend fun deleteCashEntry(id: Int): ApiResult<OkResponse> = call {
        // Kas dari penjualan tidak bisa dihapus — kalau bisa, Saldo Kas dan
        // laporan tidak lagi cocok dengan riwayat penjualan.
        val entry = DatabaseProvider.db.cashDao().getAll().firstOrNull { it.id == id }
        if (entry?.source == "sale") throw IllegalStateException("Kas penjualan tidak bisa dihapus")
        val deleted = DatabaseProvider.db.cashDao().deleteById(id)
        if (deleted == 0) throw IllegalStateException("Kas tidak ditemukan")
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
            "all" -> 0L
            else -> throw IllegalArgumentException(tr("Periode tidak dikenal: $period", "Unknown period: $period"))
        }
        val end = if (period == "all") Long.MAX_VALUE else endToday
        fun inPeriod(iso: String): Boolean {
            val t = parseISODate(iso)?.time ?: return false
            return t in start until end
        }
        val periodSales = salesRows.filter { inPeriod(it.createdAt) }
        val periodSaleIds = periodSales.map { it.id }.toSet()
        val periodItems = items.filter { it.saleId in periodSaleIds }

        // Biaya tiap item: HANYA snapshot harga beli saat transaksi.
        // Kalau snapshot 0 (mis. obat dibuat sebelum snapshot dicatat),
        // biayanya 0 dan item dihitung di costMissingQty — jangan fallback
        // ke harga beli saat ini, karena itu mengubah laba histori.
        fun costOf(it: SaleItemEntity): Int =
            if (it.buyPrice > 0) it.buyPrice else 0

        val revenue = periodItems.sumOf { it.subtotal }
        val hpp = periodItems.sumOf { costOf(it) * it.qty }
        val grossProfit = revenue - hpp
        val marginPct = marginPctOf(revenue, grossProfit)
        val itemsSold = periodItems.sumOf { it.qty }
        val costMissingQty = periodItems.filter { costOf(it) == 0 }.sumOf { it.qty }

        val periodOut = entries.filter { it.type == "out" && inPeriod(it.createdAt) }
        val operationalExpense = periodOut.filter { !it.category.trim().equals("Beli Stok", ignoreCase = true) }.sumOf { it.amount }
        val stockPurchaseExpense = periodOut.filter { it.category.trim().equals("Beli Stok", ignoreCase = true) }.sumOf { it.amount }
        val netProfit = grossProfit - operationalExpense

        data class Agg(var qty: Int = 0, var revenue: Int = 0, var hpp: Int = 0)
        val medAgg = linkedMapOf<Int, Agg>()
        val medName = mutableMapOf<Int, String>()
        val medCategory = mutableMapOf<Int, String>()
        val catAgg = linkedMapOf<String, Agg>()
        for (it in periodItems) {
            val cost = costOf(it) * it.qty
            val category = medById[it.medicineId]?.category ?: "Lainnya"
            val cur = medAgg.getOrPut(it.medicineId) { Agg() }
            cur.qty += it.qty; cur.revenue += it.subtotal; cur.hpp += cost
            // Nama tampilan: nama obat saat ini, fallback ke snapshot nama item.
            medName[it.medicineId] = medById[it.medicineId]?.name ?: it.medicineName
            medCategory[it.medicineId] = category
            val cc = catAgg.getOrPut(category) { Agg() }
            cc.qty += it.qty; cc.revenue += it.subtotal; cc.hpp += cost
        }
        fun withMargin(a: Agg) = ProfitAgg(
            a.qty, a.revenue, a.hpp, a.revenue - a.hpp, marginPctOf(a.revenue, a.revenue - a.hpp)
        )
        val byMedicine = medAgg.entries.map { (id, a) ->
            val m = withMargin(a)
            ProfitByMedicine(medName[id] ?: "", medCategory[id] ?: "Lainnya", m.qty, m.revenue, m.hpp, m.profit, m.marginPct)
        }.sortedByDescending { it.profit }
        val byCategory = catAgg.entries.map { (category, a) ->
            val m = withMargin(a)
            ProfitByCategory(category, m.qty, m.revenue, m.hpp, m.profit, m.marginPct)
        }.sortedByDescending { it.profit }

        // Laba harian untuk grafik: mengikuti periode, kecuali "Hari Ini" yang
        // tetap 7 hari terakhir supaya jelas (1 batang saja tidak memberi konteks
        // tren). "Semua" memakai hari penjualan pertama; dibatasi 365 hari supaya
        // grafik tidak meledak bila data sudah bertahun-tahun. Total laba/
        // revenue/HPP di atas grafik tetap mengikuti periode yang dipilih.
        val saleById = salesRows.associateBy { it.id }
        val firstDayStart = when (period) {
            "today" -> jakartaDayStartMillis(-6)
            "month" -> startMonth
            "last30" -> startLast30
            "all" -> periodSales.minOfOrNull { parseISODate(it.createdAt)?.time ?: Long.MAX_VALUE }
                ?.let { t ->
                    Calendar.getInstance(jakarta).apply {
                        time = Date(t)
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                } ?: startToday
            else -> startToday
        }
        val dayCount = ((endToday - firstDayStart) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1).coerceAtMost(365)
        val daily = (dayCount - 1 downTo 0).map { offset ->
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
            // Label tanggal ("27 Okt") supaya tidak ambigu bila grafik panjang
            // (nama hari saja berulang); Keuangan tetap pakai dayLabelID.
            ProfitDaily(dayKeyID(date), dayDateLabelID(date), rev, cost, rev - cost)
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

    // ---- Obat Terlaris (diurutkan jumlah terjual, terbanyak dulu) ----

    suspend fun getBestSellers(period: String = "month"): ApiResult<List<BestSeller>> = call {
        val db = DatabaseProvider.db
        val salesRows = db.saleDao().getAll()
        val items = db.saleDao().getAllItems()
        val medById = db.medicineDao().getAll().associateBy { it.id }
        val startToday = jakartaDayStartMillis(0)
        val endToday = jakartaDayStartMillis(1)
        val startMonth = jakartaMonthStartMillis()
        val startLast30 = jakartaDayStartMillis(-29)
        val start = when (period) {
            "today" -> startToday
            "month" -> startMonth
            "last30" -> startLast30
            "all" -> 0L
            else -> throw IllegalArgumentException(tr("Periode tidak dikenal: $period", "Unknown period: $period"))
        }
        val end = if (period == "all") Long.MAX_VALUE else endToday
        fun inPeriod(iso: String): Boolean {
            val t = parseISODate(iso)?.time ?: return false
            return t in start until end
        }
        val periodSaleIds = salesRows.filter { inPeriod(it.createdAt) }.map { it.id }.toSet()
        items.filter { it.saleId in periodSaleIds }
            .groupBy { it.medicineId }
            .map { (id, list) -> BestSeller(medById[id]?.name ?: list.first().medicineName, list.sumOf { it.qty }, list.sumOf { it.subtotal }) }
            .sortedByDescending { it.qty }
            .take(20)
    }

    suspend fun downloadProfitReport(period: String = "month"): ApiResult<DownloadResponse> =
        ApiResult.Error("Download laporan tidak tersedia di mode offline")

    suspend fun getWebAppManifest(): ApiResult<ManifestResponse> =
        ApiResult.Error("Manifest web tidak tersedia di mode offline")

    // ================= PEMBELIAN DARI PBF (catatan saja) =================
    // Keputusan Achmad 2026-10-29: fitur PBF MURNI catatan pembelian —
    // TIDAK menambah stok, TIDAK membuat entri kas, TIDAK mengubah harga
    // beli obat. Gunanya menjawab "hari ini belanja di PBF mana saja dan
    // uang keluar berapa" lewat riwayat per hari.

    private fun PurchaseItemEntity.toModel() =
        PurchaseItem(medicineName, qty, unit, buyPrice, subtotal)

    suspend fun listPurchases(): ApiResult<ListPurchasesResponse> = call {
        val dao = DatabaseProvider.db.purchaseDao()
        val itemsByPurchase = dao.getAllItems().groupBy { it.purchaseId }
        val purchases = dao.getAll().map { p ->
            Purchase(
                id = p.id,
                pbfName = p.pbfName,
                invoiceNo = p.invoiceNo,
                date = p.date,
                total = p.total,
                ppn = p.ppn,
                paymentType = p.paymentType,
                dueDate = p.dueDate,
                createdAt = p.createdAt,
                items = itemsByPurchase[p.id].orEmpty().map { it.toModel() }
            )
        }
        ListPurchasesResponse(purchases)
    }

    suspend fun createPurchase(args: CreatePurchaseArgs): ApiResult<IdResponse> = call {
        if (args.pbfName.isBlank()) throw IllegalArgumentException(tr("Faktur PBF tidak boleh kosong", "The PBF name must not be empty"))
        if (args.items.isEmpty()) throw IllegalArgumentException(tr("Isi minimal satu obat yang dibeli", "Add at least one purchased medicine"))
        args.items.forEach {
            if (it.medicineName.isBlank()) throw IllegalArgumentException(tr("Nama obat tidak boleh kosong", "Medicine name must not be empty"))
            if (it.qty <= 0) throw IllegalArgumentException(tr("Jumlah obat harus lebih dari 0", "Quantity must be more than 0"))
            if (it.buyPrice <= 0) throw IllegalArgumentException(tr("Harga beli harus lebih dari 0", "Buy price must be more than 0"))
        }
        // PPN ikut divalidasi (audit 2026-11-02, M5): PPN negatif menggerus
        // total pembelian sampai di bawah jumlah subtotal itemnya.
        if (args.ppn < 0) throw IllegalArgumentException(tr("PPN tidak boleh negatif", "PPN/VAT must not be negative"))
        val db = DatabaseProvider.db
        val dao = db.purchaseDao()
        var newId = 0
        db.withTransaction {
            val total = args.items.sumOf { it.qty * it.buyPrice } + args.ppn
            newId = dao.insert(
                PurchaseEntity(
                    pbfName = args.pbfName.trim(),
                    invoiceNo = args.invoiceNo.trim(),
                    date = args.date,
                    total = total,
                    ppn = args.ppn,
                    paymentType = args.paymentType,
                    // Jatuh tempo hanya berlaku untuk Kredit; selain itu
                    // selalu disimpan kosong supaya tidak ada sisa tanggal.
                    dueDate = if (args.paymentType == "KREDIT") args.dueDate.trim() else "",
                    createdAt = nowIso()
                )
            ).toInt()
            dao.insertItems(args.items.map {
                PurchaseItemEntity(
                    purchaseId = newId,
                    medicineName = it.medicineName.trim(),
                    qty = it.qty,
                    unit = it.unit.ifBlank { "Strip" },
                    buyPrice = it.buyPrice,
                    subtotal = it.qty * it.buyPrice
                )
            })
        }
        IdResponse(newId)
    }

    suspend fun deletePurchase(id: Int): ApiResult<OkResponse> = call {
        val db = DatabaseProvider.db
        val dao = db.purchaseDao()
        db.withTransaction {
            val deleted = dao.deleteById(id)
            if (deleted == 0) throw IllegalStateException("Catatan pembelian tidak ditemukan")
            dao.deleteItemsForPurchase(id)
        }
        OkResponse(true)
    }

    /** Tandai pembelian Kredit sebagai lunas: paymentType diubah ke CASH,
     *  sehingga nominalnya masuk ringkasan belanja (sesuai tanggal belinya). */
    suspend fun markPurchasePaid(id: Int): ApiResult<OkResponse> = call {
        val changed = DatabaseProvider.db.purchaseDao().updatePaymentType(id, "CASH")
        if (changed == 0) throw IllegalStateException("Catatan pembelian tidak ditemukan")
        OkResponse(true)
    }

    /** Atur/ubah tanggal jatuh tempo pembelian Kredit yang SUDAH tercatat
     *  (permintaan Achmad 2026-11-04) — sebelumnya tanggal hanya bisa diisi
     *  dari formulir pembelian baru, jadi catatan lama tidak pernah bisa
     *  muncul di Pengingat. "" berarti hapus tanggalnya. Hanya menyentuh
     *  kolom dueDate; tanggal disimpan apa adanya (pemanggil memastikan
     *  hanya untuk Kredit). */
    suspend fun setPurchaseDueDate(id: Int, dueDate: String): ApiResult<OkResponse> = call {
        val changed = DatabaseProvider.db.purchaseDao().updateDueDate(id, dueDate.trim())
        if (changed == 0) throw IllegalStateException("Catatan pembelian tidak ditemukan")
        OkResponse(true)
    }

    // ---- Backup & Restore (semua data lokal dalam satu file JSON) ----

    /** Seluruh isi database (obat, penjualan, item, kas) sebagai string JSON. */
    suspend fun exportBackup(): ApiResult<String> = call {
        val db = DatabaseProvider.db
        val data = BackupData(
            medicines = db.medicineDao().getAll(),
            sales = db.saleDao().getAll(),
            saleItems = db.saleDao().getAllItems(),
            cashEntries = db.cashDao().getAll(),
            purchases = db.purchaseDao().getAll(),
            purchaseItems = db.purchaseDao().getAllItems()
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
            db.purchaseDao().deleteAll()
            db.purchaseDao().deleteAllItems()
            data.medicines.forEach { db.medicineDao().insert(it) }
            data.sales.forEach { db.saleDao().insert(it) }
            if (data.saleItems.isNotEmpty()) db.saleDao().insertItems(data.saleItems)
            data.cashEntries.forEach { db.cashDao().insert(it) }
            data.purchases.forEach { db.purchaseDao().insert(it) }
            if (data.purchaseItems.isNotEmpty()) db.purchaseDao().insertItems(data.purchaseItems)
        }
        tr("Dipulihkan: ${data.medicines.size} obat, ${data.sales.size} penjualan, ${data.saleItems.size} item, ${data.cashEntries.size} kas, ${data.purchases.size} pembelian PBF.", "Restored: ${data.medicines.size} medicines, ${data.sales.size} sales, ${data.saleItems.size} items, ${data.cashEntries.size} cash entries, ${data.purchases.size} PBF purchases.")
    }
}

