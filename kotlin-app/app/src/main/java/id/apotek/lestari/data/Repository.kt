package id.apotek.lestari.data

import id.apotek.lestari.ui.theme.dayKeyID
import id.apotek.lestari.ui.theme.daysUntilExpiry
import id.apotek.lestari.ui.theme.monthKeyID
import id.apotek.lestari.ui.theme.parseISODate
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Repository — satu-satunya pintu data untuk UI.
 *
 * MODE SERVER: jika BuildConfig.BASE_URL sudah diganti dari
 * https://example.invalid/ ke alamat asli server aplikasi web,
 * semua fungsi di bawah memanggil server actions lewat Retrofit
 * (lihat ApiService) dan datanya sama persis dengan versi web.
 *
 * MODE DEMO LOKAL (BASE_URL masih example.invalid): fungsi-fungsi
 * memakai data contoh lokal dari DemoData di bawah ini. Data demo
 * ini disimpan di memori HP dan BENAR-BENAR berubah saat dipakai:
 * hapus obat menghapusnya dari daftar, bayar di Kasir menambah
 * penjualan ke Riwayat/Keuangan/Beranda dan mengurangi stok.
 * Ini JELAS mode demo/offline untuk mencoba tampilan Kotlin tanpa
 * server — data contoh TIDAK tersambung ke stok/penjualan asli
 * Apotek Lestari. Ganti BASE_URL di app/build.gradle.kts untuk
 * memakai data asli.
 */
class Repository(private val api: ApiService = ApiClient.service) {

    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Gagal terhubung ke server", e)
    }

    suspend fun getDashboard(): ApiResult<Dashboard> {
        if (ApiClient.isDemoMode) { delay(300); return ApiResult.Success(demoDashboard()) }
        return call { api.getDashboard(ActionRequest("getDashboard", EmptyArgs())) }
    }
    suspend fun listMedicines(search: String = ""): ApiResult<ListMedicinesResponse> {
        if (ApiClient.isDemoMode) { delay(200); return ApiResult.Success(ListMedicinesResponse(DemoData.medicines.filter { search.isBlank() || it.name.contains(search, true) || it.category.contains(search, true) })) }
        return call { api.listMedicines(ActionRequest("listMedicines", ListMedicinesArgs(search))) }
    }
    suspend fun findByBarcode(code: String): ApiResult<FindByBarcodeResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(FindByBarcodeResponse(DemoData.medicines.firstOrNull { it.barcode == code }))
        return call { api.findMedicineByBarcode(ActionRequest("findMedicineByBarcode", BarcodeArgs(code))) }
    }
    suspend fun createMedicine(args: MedicineArgs): ApiResult<IdResponse> {
        if (ApiClient.isDemoMode) {
            val newId = (DemoData.medicines.maxOfOrNull { it.id } ?: 0) + 1
            DemoData.medicines.add(
                Medicine(newId, args.name, args.category, args.unit, args.buyPrice, args.sellPrice, args.stock, args.minStock, args.expiryDate, args.barcode)
            )
            return ApiResult.Success(IdResponse(newId))
        }
        return call { api.createMedicine(ActionRequest("createMedicine", args)) }
    }
    suspend fun updateMedicine(args: UpdateMedicineArgs): ApiResult<OkResponse> {
        if (ApiClient.isDemoMode) {
            val idx = DemoData.medicines.indexOfFirst { it.id == args.id }
            if (idx >= 0) {
                val old = DemoData.medicines[idx]
                DemoData.medicines[idx] = Medicine(args.id, args.name, args.category, args.unit, args.buyPrice, args.sellPrice, args.stock, args.minStock, args.expiryDate, args.barcode, old.createdAt)
            }
            return ApiResult.Success(OkResponse(true))
        }
        return call { api.updateMedicine(ActionRequest("updateMedicine", args)) }
    }
    suspend fun deleteMedicine(id: Int): ApiResult<OkResponse> {
        if (ApiClient.isDemoMode) {
            DemoData.medicines.removeAll { it.id == id }
            return ApiResult.Success(OkResponse(true))
        }
        return call { api.deleteMedicine(ActionRequest("deleteMedicine", IdArgs(id))) }
    }
    suspend fun bulkImport(args: BulkImportArgs): ApiResult<BulkImportResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(BulkImportResponse(inserted = args.items.size, total = args.items.size))
        return call { api.bulkImportMedicines(ActionRequest("bulkImportMedicines", args)) }
    }
    suspend fun downloadTemplate(): ApiResult<DownloadResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Error("Download template hanya tersedia dalam mode server")
        return call { api.downloadTemplate(ActionRequest("downloadTemplate", EmptyArgs())) }
    }
    suspend fun downloadExport(): ApiResult<DownloadResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Error("Export hanya tersedia dalam mode server")
        return call { api.downloadExport(ActionRequest("downloadExport", EmptyArgs())) }
    }
    suspend fun createSale(args: CreateSaleArgs): ApiResult<CreateSaleResponse> {
        if (ApiClient.isDemoMode) {
            delay(200)
            val today = dayKeyID(Date())
            val total = args.items.sumOf { item ->
                (DemoData.medicines.firstOrNull { it.id == item.medicineId }?.sellPrice ?: 0) * item.qty
            }
            val newId = (DemoData.sales.maxOfOrNull { it.id } ?: 0) + 1
            val invoiceNo = "INV-DEMO-" + "%03d".format(newId)
            val change = (args.amountPaid - total).coerceAtLeast(0)
            val saleItems = args.items.mapNotNull { item ->
                val med = DemoData.medicines.firstOrNull { it.id == item.medicineId } ?: return@mapNotNull null
                SaleItem(med.name, item.qty, med.sellPrice, med.sellPrice * item.qty)
            }
            DemoData.sales.add(Sale(newId, invoiceNo, total, args.paymentMethod, args.amountPaid, change, today, saleItems))
            // Kurangi stok obat yang terjual
            args.items.forEach { item ->
                val idx = DemoData.medicines.indexOfFirst { it.id == item.medicineId }
                if (idx >= 0) {
                    val med = DemoData.medicines[idx]
                    DemoData.medicines[idx] = med.copy(stock = (med.stock - item.qty).coerceAtLeast(0))
                }
            }
            // Catat kas masuk supaya Keuangan & Beranda ikut berubah
            val cashId = (DemoData.cashEntries.maxOfOrNull { it.id } ?: 0) + 1
            DemoData.cashEntries.add(CashEntry(cashId, "in", "Penjualan", "Penjualan $invoiceNo", total, "sale", today))
            return ApiResult.Success(CreateSaleResponse(newId, invoiceNo, total, change))
        }
        return call { api.createSale(ActionRequest("createSale", args)) }
    }
    suspend fun listSales(limit: Int = 50): ApiResult<ListSalesResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(ListSalesResponse(DemoData.sales.takeLast(limit)))
        return call { api.listSales(ActionRequest("listSales", ListLimitArgs(limit))) }
    }
    suspend fun listCashEntries(limit: Int = 200): ApiResult<ListCashResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(ListCashResponse(DemoData.cashEntries.takeLast(limit)))
        return call { api.listCashEntries(ActionRequest("listCashEntries", ListLimitArgs(limit))) }
    }
    suspend fun createCashEntry(args: CreateCashEntryArgs): ApiResult<IdResponse> {
        if (ApiClient.isDemoMode) {
            val newId = (DemoData.cashEntries.maxOfOrNull { it.id } ?: 0) + 1
            DemoData.cashEntries.add(CashEntry(newId, args.type, args.category, args.description, args.amount, "manual", dayKeyID(Date())))
            return ApiResult.Success(IdResponse(newId))
        }
        return call { api.createCashEntry(ActionRequest("createCashEntry", args)) }
    }
    suspend fun deleteCashEntry(id: Int): ApiResult<OkResponse> {
        if (ApiClient.isDemoMode) {
            DemoData.cashEntries.removeAll { it.id == id }
            return ApiResult.Success(OkResponse(true))
        }
        return call { api.deleteCashEntry(ActionRequest("deleteCashEntry", IdArgs(id))) }
    }
    suspend fun getProfitReport(period: String = "month"): ApiResult<ProfitReport> {
        if (ApiClient.isDemoMode) return ApiResult.Success(DemoData.profit)
        return call { api.getProfitReport(ActionRequest("getProfitReport", ProfitArgs(period))) }
    }
    suspend fun downloadProfitReport(period: String = "month"): ApiResult<DownloadResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Error("Download laporan hanya tersedia dalam mode server")
        return call { api.downloadProfitReport(ActionRequest("downloadProfitReport", ProfitArgs(period))) }
    }
    suspend fun getWebAppManifest(): ApiResult<ManifestResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(ManifestResponse(true, ""))
        return call { api.getWebAppManifest(ActionRequest("getWebAppManifest", EmptyArgs())) }
    }

    // ---- Dashboard demo: dihitung ulang dari daftar demo yang sedang berjalan ----
    private fun demoDashboard(): Dashboard {
        val todayKey = dayKeyID(Date())
        val monthKey = monthKeyID(Date())
        fun cashSum(type: String, match: (Date) -> Boolean): Int =
            DemoData.cashEntries.filter { it.type == type && parseISODate(it.createdAt)?.let(match) == true }.sumOf { it.amount }
        val todayIn = cashSum("in") { dayKeyID(it) == todayKey }
        val todayOut = cashSum("out") { dayKeyID(it) == todayKey }
        val monthIn = cashSum("in") { monthKeyID(it) == monthKey }
        val monthOut = cashSum("out") { monthKeyID(it) == monthKey }
        val todaySales = DemoData.sales.filter { parseISODate(it.createdAt)?.let { d -> dayKeyID(d) == todayKey } == true }
        val bestSellers = DemoData.sales.flatMap { it.items }
            .groupBy { it.medicineName }
            .map { (name, items) -> BestSeller(name, items.sumOf { it.qty }, items.sumOf { it.subtotal }) }
            .sortedByDescending { it.qty }.take(5)
        val recentSales = DemoData.sales.sortedByDescending { it.id }.take(5)
            .map { RecentSale(it.id, it.invoiceNo, it.total, it.createdAt) }
        val lowStockCount = DemoData.medicines.count { it.stock <= it.minStock }
        val expiringCount = DemoData.medicines.count { val d = daysUntilExpiry(it.expiryDate); d != null && d in 0..90 }
        val jakarta = TimeZone.getTimeZone("Asia/Jakarta")
        val last7Days = (6 downTo 0).map { offset ->
            val cal = Calendar.getInstance(jakarta).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val key = dayKeyID(cal.time)
            val income = cashSum("in") { dayKeyID(it) == key }
            val expense = cashSum("out") { dayKeyID(it) == key }
            val label = SimpleDateFormat("EEE", Locale("id", "ID")).apply { timeZone = jakarta }.format(cal.time)
            DayCash(key, label, income, expense)
        }
        return Dashboard(
            todayIn = todayIn, todayOut = todayOut, todayProfit = todayIn - todayOut,
            monthIn = monthIn, monthOut = monthOut, monthProfit = monthIn - monthOut,
            totalMedicines = DemoData.medicines.size, lowStockCount = lowStockCount, expiringCount = expiringCount,
            totalSalesToday = todaySales.size, bestSellers = bestSellers, last7Days = last7Days, recentSales = recentSales
        )
    }
}

object DemoData {
    // DATA CONTOH LOKAL — hanya untuk mode demo (BASE_URL = example.invalid).
    // Bukan data asli Apotek Lestari. Daftar ini sengaja mutable supaya
    // hapus/tambah/edit obat dan penjualan di mode demo benar-benar tersimpan
    // selama aplikasi berjalan.
    val medicines = mutableListOf(
        Medicine(1, "Paracetamol 500mg", "Pereda Nyeri", "Strip", 8000, 12000, 50, 10, "2027-12-31", "8991234567890"),
        Medicine(2, "Amoxicillin 500mg", "Antibiotik", "Strip", 15000, 22000, 18, 10, "2026-11-30", "8991234567891"),
        Medicine(3, "Vitamin C 1000mg", "Vitamin", "Botol", 35000, 50000, 20, 5, "2028-06-30", "8991234567892")
    )
    val sales = mutableListOf(
        Sale(1, "INV-DEMO-001", 24000, "Tunai", 50000, 26000, "2026-10-26", listOf(SaleItem("Paracetamol 500mg", 2, 12000, 24000)))
    )
    val cashEntries = mutableListOf(
        CashEntry(1, "in", "Penjualan", "Penjualan INV-DEMO-001", 24000, "sale", "2026-10-26"),
        CashEntry(2, "out", "Operasional", "Contoh pengeluaran demo", 10000, "manual", "2026-10-26")
    )
    val profit = ProfitReport(
        period = "month", revenue = 24000, hpp = 16000, grossProfit = 8000, marginPct = 33.3,
        operationalExpense = 10000, stockPurchaseExpense = 0, netProfit = -2000,
        transactionCount = 1, itemsSold = 2, costMissingQty = 0,
        byMedicine = listOf(ProfitByMedicine("Paracetamol 500mg", "Pereda Nyeri", 2, 24000, 16000, 8000, 33.3)),
        byCategory = listOf(ProfitByCategory("Pereda Nyeri", 2, 24000, 16000, 8000, 33.3)),
        daily = listOf(ProfitDaily("2026-10-26", "Sen", 24000, 16000, 8000))
    )
}
