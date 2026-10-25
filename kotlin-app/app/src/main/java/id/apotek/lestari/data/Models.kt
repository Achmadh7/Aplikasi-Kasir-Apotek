package id.apotek.lestari.data

import com.google.gson.annotations.SerializedName

// Models mirroring server/src/schema.ts + server/src/actions.ts (web app).
// Field names use @SerializedName matching the JSON returned by server actions.

data class Medicine(
    val id: Int = 0,
    val name: String = "",
    val category: String = "Umum",
    val unit: String = "Strip",
    @SerializedName("buyPrice") val buyPrice: Int = 0,
    @SerializedName("sellPrice") val sellPrice: Int = 0,
    val stock: Int = 0,
    @SerializedName("minStock") val minStock: Int = 10,
    @SerializedName("expiryDate") val expiryDate: String? = null,
    val barcode: String? = null,
    @SerializedName("createdAt") val createdAt: String = ""
)

data class SaleItem(
    @SerializedName("medicineName") val medicineName: String = "",
    val qty: Int = 0,
    val price: Int = 0,
    val subtotal: Int = 0
)

data class Sale(
    val id: Int = 0,
    @SerializedName("invoiceNo") val invoiceNo: String = "",
    val total: Int = 0,
    @SerializedName("paymentMethod") val paymentMethod: String = "Tunai",
    @SerializedName("amountPaid") val amountPaid: Int = 0,
    @SerializedName("changeAmount") val changeAmount: Int = 0,
    @SerializedName("createdAt") val createdAt: String = "",
    val items: List<SaleItem> = emptyList()
)

data class CashEntry(
    val id: Int = 0,
    val type: String = "in", // "in" | "out"
    val category: String = "",
    val description: String = "",
    val amount: Int = 0,
    val source: String = "manual",
    @SerializedName("createdAt") val createdAt: String = ""
)

data class BestSeller(val name: String = "", val qty: Int = 0, val revenue: Int = 0)
data class DayCash(val date: String = "", val label: String = "", val income: Int = 0, val expense: Int = 0)
data class RecentSale(val id: Int = 0, @SerializedName("invoiceNo") val invoiceNo: String = "", val total: Int = 0, @SerializedName("createdAt") val createdAt: String = "")

data class Dashboard(
    @SerializedName("todayIn") val todayIn: Int = 0,
    @SerializedName("todayOut") val todayOut: Int = 0,
    @SerializedName("todayProfit") val todayProfit: Int = 0,
    @SerializedName("monthIn") val monthIn: Int = 0,
    @SerializedName("monthOut") val monthOut: Int = 0,
    @SerializedName("monthProfit") val monthProfit: Int = 0,
    @SerializedName("totalMedicines") val totalMedicines: Int = 0,
    @SerializedName("lowStockCount") val lowStockCount: Int = 0,
    @SerializedName("expiringCount") val expiringCount: Int = 0,
    @SerializedName("totalSalesToday") val totalSalesToday: Int = 0,
    @SerializedName("bestSellers") val bestSellers: List<BestSeller> = emptyList(),
    @SerializedName("last7Days") val last7Days: List<DayCash> = emptyList(),
    @SerializedName("recentSales") val recentSales: List<RecentSale> = emptyList()
)

data class ProfitAgg(val qty: Int = 0, val revenue: Int = 0, val hpp: Int = 0, val profit: Int = 0, @SerializedName("marginPct") val marginPct: Double = 0.0)
data class ProfitByMedicine(val name: String = "", val category: String = "", val qty: Int = 0, val revenue: Int = 0, val hpp: Int = 0, val profit: Int = 0, @SerializedName("marginPct") val marginPct: Double = 0.0)
data class ProfitByCategory(val category: String = "", val qty: Int = 0, val revenue: Int = 0, val hpp: Int = 0, val profit: Int = 0, @SerializedName("marginPct") val marginPct: Double = 0.0)
data class ProfitDaily(val date: String = "", val label: String = "", val revenue: Int = 0, val hpp: Int = 0, val profit: Int = 0)

data class ProfitReport(
    val period: String = "month",
    val revenue: Int = 0, val hpp: Int = 0,
    @SerializedName("grossProfit") val grossProfit: Int = 0,
    @SerializedName("marginPct") val marginPct: Double = 0.0,
    @SerializedName("operationalExpense") val operationalExpense: Int = 0,
    @SerializedName("stockPurchaseExpense") val stockPurchaseExpense: Int = 0,
    @SerializedName("netProfit") val netProfit: Int = 0,
    @SerializedName("transactionCount") val transactionCount: Int = 0,
    @SerializedName("itemsSold") val itemsSold: Int = 0,
    @SerializedName("costMissingQty") val costMissingQty: Int = 0,
    @SerializedName("byMedicine") val byMedicine: List<ProfitByMedicine> = emptyList(),
    @SerializedName("byCategory") val byCategory: List<ProfitByCategory> = emptyList(),
    val daily: List<ProfitDaily> = emptyList()
)

// ---- Generic envelope for this platform's server actions ----
// Every action is POST ./actions with body { action: "<name>", args: {...} }
data class ActionRequest<T>(val action: String, val args: T)
data class EmptyArgs(val unused: Int = 0)

// ---- Per-action request args ----
data class ListMedicinesArgs(val search: String = "")
data class BarcodeArgs(val barcode: String)
data class MedicineArgs(val name: String, val category: String, val unit: String, @SerializedName("buyPrice") val buyPrice: Int, @SerializedName("sellPrice") val sellPrice: Int, val stock: Int, @SerializedName("minStock") val minStock: Int, @SerializedName("expiryDate") val expiryDate: String? = null, val barcode: String? = null)
data class UpdateMedicineArgs(val id: Int, val name: String, val category: String, val unit: String, @SerializedName("buyPrice") val buyPrice: Int, @SerializedName("sellPrice") val sellPrice: Int, val stock: Int, @SerializedName("minStock") val minStock: Int, @SerializedName("expiryDate") val expiryDate: String? = null, val barcode: String? = null)
data class IdArgs(val id: Int)
data class BulkImportArgs(val items: List<MedicineArgs>, val mode: String = "upsert")
data class CreateSaleItemArgs(@SerializedName("medicineId") val medicineId: Int, val qty: Int)
data class CreateSaleArgs(val items: List<CreateSaleItemArgs>, @SerializedName("paymentMethod") val paymentMethod: String, @SerializedName("amountPaid") val amountPaid: Int)
data class ListLimitArgs(val limit: Int = 50)
data class CreateCashEntryArgs(val type: String, val category: String, val description: String, val amount: Int)
data class ProfitArgs(val period: String = "month")

// ---- Per-action responses ----
data class ListMedicinesResponse(val medicines: List<Medicine> = emptyList())
data class FindByBarcodeResponse(val medicine: Medicine? = null)
data class IdResponse(val id: Int = 0)
data class OkResponse(val ok: Boolean = true)
data class BulkImportResponse(val inserted: Int = 0, val updated: Int = 0, val skipped: Int = 0, val total: Int = 0)
data class DownloadResponse(val ok: Boolean = false, val url: String = "", val filename: String = "", val message: String = "")
data class CreateSaleResponse(val id: Int = 0, @SerializedName("invoiceNo") val invoiceNo: String = "", val total: Int = 0, @SerializedName("changeAmount") val changeAmount: Int = 0)
data class ListSalesResponse(val sales: List<Sale> = emptyList())
data class ListCashResponse(val entries: List<CashEntry> = emptyList())
data class ManifestResponse(val ok: Boolean = true, val url: String = "")

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}
