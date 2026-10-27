package id.apotek.lestari.data

import id.apotek.lestari.data.local.CashEntryEntity
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.data.local.SaleEntity
import id.apotek.lestari.data.local.SaleItemEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup & Restore — salinan seluruh isi database lokal (obat, penjualan,
 * item penjualan, kas) dalam satu file JSON.
 *
 * Format file: {"version":1, "app":"apotek-lestari",
 *   "medicines":[...], "sales":[...], "saleItems":[...], "cashEntries":[...]}
 * Dipakai org.json bawaan Android, jadi tidak perlu dependensi baru.
 * Restore MENGGANTI seluruh data saat ini dengan isi file backup
 * (ID dipertahankan supaya relasi penjualan-item tetap utuh).
 */
data class BackupData(
    val medicines: List<MedicineEntity> = emptyList(),
    val sales: List<SaleEntity> = emptyList(),
    val saleItems: List<SaleItemEntity> = emptyList(),
    val cashEntries: List<CashEntryEntity> = emptyList()
)

object BackupHelper {
    const val VERSION = 1

    fun buildJson(data: BackupData): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("app", "apotek-lestari")

        val meds = JSONArray()
        for (m in data.medicines) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("name", m.name)
            o.put("category", m.category)
            o.put("unit", m.unit)
            o.put("buyPrice", m.buyPrice)
            o.put("sellPrice", m.sellPrice)
            o.put("stock", m.stock)
            o.put("minStock", m.minStock)
            if (m.expiryDate != null) o.put("expiryDate", m.expiryDate) else o.put("expiryDate", JSONObject.NULL)
            if (m.barcode != null) o.put("barcode", m.barcode) else o.put("barcode", JSONObject.NULL)
            o.put("createdAt", m.createdAt)
            meds.put(o)
        }
        root.put("medicines", meds)

        val sales = JSONArray()
        for (s in data.sales) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("invoiceNo", s.invoiceNo)
            o.put("total", s.total)
            o.put("paymentMethod", s.paymentMethod)
            o.put("amountPaid", s.amountPaid)
            o.put("changeAmount", s.changeAmount)
            o.put("createdAt", s.createdAt)
            sales.put(o)
        }
        root.put("sales", sales)

        val items = JSONArray()
        for (it in data.saleItems) {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("saleId", it.saleId)
            o.put("medicineId", it.medicineId)
            o.put("medicineName", it.medicineName)
            o.put("qty", it.qty)
            o.put("price", it.price)
            o.put("buyPrice", it.buyPrice)
            o.put("subtotal", it.subtotal)
            items.put(o)
        }
        root.put("saleItems", items)

        val entries = JSONArray()
        for (e in data.cashEntries) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("type", e.type)
            o.put("category", e.category)
            o.put("description", e.description)
            o.put("amount", e.amount)
            o.put("source", e.source)
            o.put("createdAt", e.createdAt)
            entries.put(o)
        }
        root.put("cashEntries", entries)

        return root.toString(2)
    }

    /** Membaca file backup. Melempar IllegalArgumentException kalau formatnya bukan backup yang sah. */
    fun parseJson(json: String): BackupData {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("File bukan backup yang sah (bukan JSON)")
        }
        val version = root.optInt("version", 0)
        if (version != VERSION) throw IllegalArgumentException("Versi backup tidak dikenali (versi $version)")
        if (root.optString("app", "") != "apotek-lestari") throw IllegalArgumentException("File bukan backup Apotek Lestari")

        val medicines = mutableListOf<MedicineEntity>()
        val medArr = root.optJSONArray("medicines") ?: JSONArray()
        for (i in 0 until medArr.length()) {
            val o = medArr.getJSONObject(i)
            medicines.add(
                MedicineEntity(
                    id = o.getInt("id"),
                    name = o.getString("name"),
                    category = o.optString("category", "Umum"),
                    unit = o.optString("unit", "Strip"),
                    buyPrice = o.optInt("buyPrice", 0),
                    sellPrice = o.optInt("sellPrice", 0),
                    stock = o.optInt("stock", 0),
                    minStock = o.optInt("minStock", 10),
                    expiryDate = if (o.isNull("expiryDate")) null else o.optString("expiryDate"),
                    barcode = if (o.isNull("barcode")) null else o.optString("barcode"),
                    createdAt = o.optString("createdAt", "")
                )
            )
        }

        val sales = mutableListOf<SaleEntity>()
        val saleArr = root.optJSONArray("sales") ?: JSONArray()
        for (i in 0 until saleArr.length()) {
            val o = saleArr.getJSONObject(i)
            sales.add(
                SaleEntity(
                    id = o.getInt("id"),
                    invoiceNo = o.getString("invoiceNo"),
                    total = o.optInt("total", 0),
                    paymentMethod = o.optString("paymentMethod", "Tunai"),
                    amountPaid = o.optInt("amountPaid", 0),
                    changeAmount = o.optInt("changeAmount", 0),
                    createdAt = o.optString("createdAt", "")
                )
            )
        }

        val items = mutableListOf<SaleItemEntity>()
        val itemArr = root.optJSONArray("saleItems") ?: JSONArray()
        for (i in 0 until itemArr.length()) {
            val o = itemArr.getJSONObject(i)
            items.add(
                SaleItemEntity(
                    id = o.getInt("id"),
                    saleId = o.getInt("saleId"),
                    medicineId = o.getInt("medicineId"),
                    medicineName = o.optString("medicineName", ""),
                    qty = o.optInt("qty", 0),
                    price = o.optInt("price", 0),
                    buyPrice = o.optInt("buyPrice", 0),
                    subtotal = o.optInt("subtotal", 0)
                )
            )
        }

        val entries = mutableListOf<CashEntryEntity>()
        val entryArr = root.optJSONArray("cashEntries") ?: JSONArray()
        for (i in 0 until entryArr.length()) {
            val o = entryArr.getJSONObject(i)
            entries.add(
                CashEntryEntity(
                    id = o.getInt("id"),
                    type = o.optString("type", "in"),
                    category = o.optString("category", ""),
                    description = o.optString("description", ""),
                    amount = o.optInt("amount", 0),
                    source = o.optString("source", "manual"),
                    createdAt = o.optString("createdAt", "")
                )
            )
        }

        return BackupData(medicines, sales, items, entries)
    }
}
