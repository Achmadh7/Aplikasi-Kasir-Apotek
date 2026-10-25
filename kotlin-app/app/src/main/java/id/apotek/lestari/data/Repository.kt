package id.apotek.lestari.data

import kotlinx.coroutines.delay

/**
 * Repository — satu-satunya pintu data untuk UI.
 *
 * MODE SERVER: jika BuildConfig.BASE_URL sudah diganti dari
 * https://example.invalid/ ke alamat asli server aplikasi web,
 * semua fungsi di bawah memanggil server actions lewat Retrofit
 * (lihat ApiService) dan datanya sama persis dengan versi web.
 *
 * MODE DEMO LOKAL (BASE_URL masih example.invalid): fungsi-fungsi
 * mengembalikan data contoh lokal dari DemoData di bawah ini.
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
        if (ApiClient.isDemoMode) { delay(300); return ApiResult.Success(DemoData.dashboard) }
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
        if (ApiClient.isDemoMode) return ApiResult.Success(IdResponse(DemoData.medicines.size + 1))
        return call { api.createMedicine(ActionRequest("createMedicine", args)) }
    }
    suspend fun updateMedicine(args: UpdateMedicineArgs): ApiResult<OkResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(OkResponse(true))
        return call { api.updateMedicine(ActionRequest("updateMedicine", args)) }
    }
    suspend fun deleteMedicine(id: Int): ApiResult<OkResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(OkResponse(true))
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
        if (ApiClient.isDemoMode) return ApiResult.Success(CreateSaleResponse(id = 1, invoiceNo = "INV-DEMO-001", total = 0, changeAmount = args.amountPaid))
        return call { api.createSale(ActionRequest("createSale", args)) }
    }
    suspend fun listSales(limit: Int = 50): ApiResult<ListSalesResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(ListSalesResponse(DemoData.sales))
        return call { api.listSales(ActionRequest("listSales", ListLimitArgs(limit))) }
    }
    suspend fun listCashEntries(limit: Int = 200): ApiResult<ListCashResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(ListCashResponse(DemoData.cashEntries))
        return call { api.listCashEntries(ActionRequest("listCashEntries", ListLimitArgs(limit))) }
    }
    suspend fun createCashEntry(args: CreateCashEntryArgs): ApiResult<IdResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(IdResponse(1))
        return call { api.createCashEntry(ActionRequest("createCashEntry", args)) }
    }
    suspend fun deleteCashEntry(id: Int): ApiResult<OkResponse> {
        if (ApiClient.isDemoMode) return ApiResult.Success(OkResponse(true))
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
}

object DemoData {
    // DATA CONTOH LOKAL — hanya untuk mode demo (BASE_URL = example.invalid).
    // Bukan data asli Apotek Lestari.
    val medicines = listOf(
        Medicine(1, "Paracetamol 500mg", "Pereda Nyeri", "Strip", 8000, 12000, 50, 10, "2027-12-31", "8991234567890"),
        Medicine(2, "Amoxicillin 500mg", "Antibiotik", "Strip", 15000, 22000, 18, 10, "2026-11-30", "8991234567891"),
        Medicine(3, "Vitamin C 1000mg", "Vitamin", "Botol", 35000, 50000, 20, 5, "2028-06-30", "8991234567892")
    )
    val sales = listOf(
        Sale(1, "INV-DEMO-001", 24000, "Tunai", 50000, 26000, "2026-10-26", listOf(SaleItem("Paracetamol 500mg", 2, 12000, 24000)))
    )
    val cashEntries = listOf(
        CashEntry(1, "in", "Penjualan", "Penjualan INV-DEMO-001", 24000, "sale", "2026-10-26"),
        CashEntry(2, "out", "Operasional", "Contoh pengeluaran demo", 10000, "manual", "2026-10-26")
    )
    val dashboard = Dashboard(
        todayIn = 24000, todayOut = 10000, todayProfit = 14000,
        monthIn = 24000, monthOut = 10000, monthProfit = 14000,
        totalMedicines = 3, lowStockCount = 1, expiringCount = 0, totalSalesToday = 1,
        bestSellers = listOf(BestSeller("Paracetamol 500mg", 2, 24000)),
        last7Days = listOf(DayCash("2026-10-26", "Sen", 24000, 10000)),
        recentSales = listOf(RecentSale(1, "INV-DEMO-001", 24000, "2026-10-26"))
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
