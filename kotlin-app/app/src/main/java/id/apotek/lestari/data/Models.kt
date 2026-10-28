package id.apotek.lestari.data

// Model data lokal (Room) — semua data tersimpan di HP, tanpa server.

data class Medicine(
    val id: Int = 0,
    val name: String = "",
    val category: String = "Umum",
    val unit: String = "Strip",
    val buyPrice: Int = 0,
    val sellPrice: Int = 0,
    val stock: Int = 0,
    val minStock: Int = 10,
    val expiryDate: String? = null,
    val barcode: String? = null,
    val createdAt: String = ""
)

data class SaleItem(
    val medicineName: String = "",
    val qty: Int = 0,
    val price: Int = 0,
    val subtotal: Int = 0,
    // Kategori & satuan obat saat penjualan terjadi (kosong kalau obatnya sudah dihapus dari Stok).
    val category: String = "",
    val unit: String = ""
)

data class Sale(
    val id: Int = 0,
    val invoiceNo: String = "",
    val total: Int = 0,
    val paymentMethod: String = "Tunai",
    val amountPaid: Int = 0,
    val changeAmount: Int = 0,
    val createdAt: String = "",
    val items: List<SaleItem> = emptyList()
)

data class CashEntry(
    val id: Int = 0,
    val type: String = "in", // "in" | "out"
    val category: String = "",
    val description: String = "",
    val amount: Int = 0,
    val source: String = "manual",
    val createdAt: String = ""
)

data class CashBalance(val totalIn: Int = 0, val totalOut: Int = 0) {
    val balance: Int get() = totalIn - totalOut
}

data class BestSeller(val name: String = "", val qty: Int = 0, val revenue: Int = 0)
data class DayCash(val date: String = "", val label: String = "", val income: Int = 0, val expense: Int = 0)
data class RecentSale(val id: Int = 0, val invoiceNo: String = "", val total: Int = 0, val createdAt: String = "")

data class Dashboard(
    val todayIn: Int = 0,
    val yesterdayIn: Int = 0,
    val todayOut: Int = 0,
    val todayProfit: Int = 0,
    val monthIn: Int = 0,
    val monthOut: Int = 0,
    val monthProfit: Int = 0,
    val totalMedicines: Int = 0,
    val lowStockCount: Int = 0,
    val expiringCount: Int = 0,
    val totalSalesToday: Int = 0,
    val bestSellers: List<BestSeller> = emptyList(),
    val last7Days: List<DayCash> = emptyList(),
    val recentSales: List<RecentSale> = emptyList()
)

data class ProfitAgg(val qty: Int = 0, val revenue: Int = 0, val hpp: Int = 0, val profit: Int = 0, val marginPct: Double = 0.0)
data class ProfitByMedicine(val name: String = "", val category: String = "", val qty: Int = 0, val revenue: Int = 0, val hpp: Int = 0, val profit: Int = 0, val marginPct: Double = 0.0)
data class ProfitByCategory(val category: String = "", val qty: Int = 0, val revenue: Int = 0, val hpp: Int = 0, val profit: Int = 0, val marginPct: Double = 0.0)
data class ProfitDaily(val date: String = "", val label: String = "", val revenue: Int = 0, val hpp: Int = 0, val profit: Int = 0)

data class ProfitReport(
    val period: String = "month",
    val revenue: Int = 0, val hpp: Int = 0,
    val grossProfit: Int = 0,
    val marginPct: Double = 0.0,
    val operationalExpense: Int = 0,
    val stockPurchaseExpense: Int = 0,
    val netProfit: Int = 0,
    val transactionCount: Int = 0,
    val itemsSold: Int = 0,
    val costMissingQty: Int = 0,
    val byMedicine: List<ProfitByMedicine> = emptyList(),
    val byCategory: List<ProfitByCategory> = emptyList(),
    val daily: List<ProfitDaily> = emptyList()
)

// ---- Per-action request args ----
data class MedicineArgs(val name: String, val category: String, val unit: String, val buyPrice: Int, val sellPrice: Int, val stock: Int, val minStock: Int, val expiryDate: String? = null, val barcode: String? = null)
data class UpdateMedicineArgs(val id: Int, val name: String, val category: String, val unit: String, val buyPrice: Int, val sellPrice: Int, val stock: Int, val minStock: Int, val expiryDate: String? = null, val barcode: String? = null)
data class BulkImportArgs(val items: List<MedicineArgs>)
data class CreateSaleItemArgs(val medicineId: Int, val qty: Int)
data class CreateSaleArgs(val items: List<CreateSaleItemArgs>, val paymentMethod: String, val amountPaid: Int)
data class CreateCashEntryArgs(val type: String, val category: String, val description: String, val amount: Int, val createdAt: String? = null)

// ---- Per-action responses ----
data class ListMedicinesResponse(val medicines: List<Medicine> = emptyList())
data class FindByBarcodeResponse(val medicine: Medicine? = null)
data class IdResponse(val id: Int = 0)
data class OkResponse(val ok: Boolean = true)
data class BulkImportResponse(val inserted: Int = 0, val updated: Int = 0, val skipped: Int = 0, val total: Int = 0)
data class DownloadResponse(val ok: Boolean = false, val url: String = "", val filename: String = "", val message: String = "")
data class CreateSaleResponse(val id: Int = 0, val invoiceNo: String = "", val total: Int = 0, val changeAmount: Int = 0)
data class ListSalesResponse(val sales: List<Sale> = emptyList())
data class ListCashResponse(val entries: List<CashEntry> = emptyList())
data class ManifestResponse(val ok: Boolean = true, val url: String = "")

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}
