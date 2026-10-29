package id.apotek.lestari.data

import id.apotek.lestari.data.local.CashEntryEntity
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.data.local.PurchaseEntity
import id.apotek.lestari.data.local.PurchaseItemEntity
import id.apotek.lestari.data.local.SaleEntity
import id.apotek.lestari.data.local.SaleItemEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Backup & Restore — salinan seluruh isi database lokal (obat, penjualan,
 * item penjualan, kas) dalam satu file JSON.
 *
 * Format file: {"version":1, "app":"apotek-lestari",
 *   "medicines":[...], "sales":[...], "saleItems":[...], "cashEntries":[...],
 *   "purchases":[...], "purchaseItems":[...]}
 * Array purchases/purchaseItems (fitur PBF) bersifat OPSIONAL saat dibaca:
 * backup lama yang belum punya kedua array itu tetap bisa dipulihkan.
 * Dipakai org.json bawaan Android, jadi tidak perlu dependensi baru.
 * Restore MENGGANTI seluruh data saat ini dengan isi file backup
 * (ID dipertahankan supaya relasi penjualan-item tetap utuh).
 */
data class BackupData(
    val medicines: List<MedicineEntity> = emptyList(),
    val sales: List<SaleEntity> = emptyList(),
    val saleItems: List<SaleItemEntity> = emptyList(),
    val cashEntries: List<CashEntryEntity> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val purchaseItems: List<PurchaseItemEntity> = emptyList()
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
            o.put("category", it.category)
            o.put("unit", it.unit)
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

        val purchases = JSONArray()
        for (p in data.purchases) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("pbfName", p.pbfName)
            o.put("date", p.date)
            o.put("total", p.total)
            o.put("createdAt", p.createdAt)
            purchases.put(o)
        }
        root.put("purchases", purchases)

        val purchaseItems = JSONArray()
        for (pi in data.purchaseItems) {
            val o = JSONObject()
            o.put("id", pi.id)
            o.put("purchaseId", pi.purchaseId)
            o.put("medicineName", pi.medicineName)
            o.put("qty", pi.qty)
            o.put("unit", pi.unit)
            o.put("buyPrice", pi.buyPrice)
            o.put("subtotal", pi.subtotal)
            purchaseItems.put(o)
        }
        root.put("purchaseItems", purchaseItems)

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

        // A3: keempat array wajib ada — file tanpa array bukan backup kosong,
        // melainkan backup tidak lengkap dan harus ditolak.
        val medArr = requireArray(root, "medicines")
        val saleArr = requireArray(root, "sales")
        val itemArr = requireArray(root, "saleItems")
        val entryArr = requireArray(root, "cashEntries")

        val medicines = mutableListOf<MedicineEntity>()
        for (i in 0 until medArr.length()) {
            val label = "Baris obat ke-${i + 1}"
            val o = rowObject(medArr, i, label)
            try {
                medicines.add(
                    MedicineEntity(
                        id = reqInt(o, "id", label),
                        name = reqString(o, "name", label),
                        category = o.optString("category", "Umum"),
                        unit = o.optString("unit", "Strip"),
                        buyPrice = reqInt(o, "buyPrice", label),
                        sellPrice = reqInt(o, "sellPrice", label),
                        stock = reqInt(o, "stock", label),
                        expiryDate = if (o.isNull("expiryDate")) null else o.optString("expiryDate"),
                        barcode = if (o.isNull("barcode")) null else o.optString("barcode"),
                        createdAt = o.optString("createdAt", "")
                    )
                )
            } catch (e: JSONException) {
                throw IllegalArgumentException("$label rusak: ${e.message}")
            }
        }

        val sales = mutableListOf<SaleEntity>()
        for (i in 0 until saleArr.length()) {
            val label = "Baris penjualan ke-${i + 1}"
            val o = rowObject(saleArr, i, label)
            try {
                sales.add(
                    SaleEntity(
                        id = reqInt(o, "id", label),
                        invoiceNo = reqString(o, "invoiceNo", label),
                        total = reqInt(o, "total", label),
                        paymentMethod = o.optString("paymentMethod", "Tunai"),
                        amountPaid = reqInt(o, "amountPaid", label),
                        changeAmount = reqInt(o, "changeAmount", label),
                        createdAt = o.optString("createdAt", "")
                    )
                )
            } catch (e: JSONException) {
                throw IllegalArgumentException("$label rusak: ${e.message}")
            }
        }

        val items = mutableListOf<SaleItemEntity>()
        for (i in 0 until itemArr.length()) {
            val label = "Baris item penjualan ke-${i + 1}"
            val o = rowObject(itemArr, i, label)
            try {
                val qty = reqInt(o, "qty", label)
                val price = reqInt(o, "price", label)
                val subtotal = reqInt(o, "subtotal", label)
                // A4: subtotal harus konsisten dengan qty * price (data uang).
                if (subtotal.toLong() != qty.toLong() * price.toLong()) {
                    throw IllegalArgumentException(
                        "$label rusak: subtotal $subtotal tidak sama dengan qty $qty x harga $price"
                    )
                }
                items.add(
                    SaleItemEntity(
                        id = reqInt(o, "id", label),
                        saleId = reqInt(o, "saleId", label),
                        medicineId = reqInt(o, "medicineId", label),
                        medicineName = o.optString("medicineName", ""),
                        category = o.optString("category", ""),
                        unit = o.optString("unit", ""),
                        qty = qty,
                        price = price,
                        buyPrice = reqInt(o, "buyPrice", label),
                        subtotal = subtotal
                    )
                )
            } catch (e: JSONException) {
                throw IllegalArgumentException("$label rusak: ${e.message}")
            }
        }

        val entries = mutableListOf<CashEntryEntity>()
        for (i in 0 until entryArr.length()) {
            val label = "Baris kas ke-${i + 1}"
            val o = rowObject(entryArr, i, label)
            try {
                // A4: type wajib "in"/"out" — jangan default ke "in" karena
                // itu bisa membalik pengeluaran jadi pemasukan.
                val type = o.optString("type", "")
                if (type != "in" && type != "out") {
                    throw IllegalArgumentException(
                        "$label rusak: type \"$type\" tidak valid (harus \"in\" atau \"out\")"
                    )
                }
                entries.add(
                    CashEntryEntity(
                        id = reqInt(o, "id", label),
                        type = type,
                        category = o.optString("category", ""),
                        description = o.optString("description", ""),
                        amount = reqInt(o, "amount", label),
                        source = o.optString("source", "manual"),
                        createdAt = o.optString("createdAt", "")
                    )
                )
            } catch (e: JSONException) {
                throw IllegalArgumentException("$label rusak: ${e.message}")
            }
        }

        // A4: validasi relasi — setiap item penjualan harus punya penjualan induk.
        val saleIds = sales.map { it.id }.toSet()
        for ((index, item) in items.withIndex()) {
            if (item.saleId !in saleIds) {
                throw IllegalArgumentException(
                    "Baris item penjualan ke-${index + 1} rusak: saleId ${item.saleId} " +
                        "tidak ditemukan di daftar sales"
                )
            }
        }

        // Pembelian PBF: array OPSIONAL — backup lama tidak punya array ini
        // dan tetap sah (dianggap belum ada catatan pembelian).
        val purchases = mutableListOf<PurchaseEntity>()
        val purchaseArr = root.optJSONArray("purchases")
        if (purchaseArr != null) {
            for (i in 0 until purchaseArr.length()) {
                val label = "Baris pembelian ke-${i + 1}"
                val o = rowObject(purchaseArr, i, label)
                try {
                    purchases.add(
                        PurchaseEntity(
                            id = reqInt(o, "id", label),
                            pbfName = reqString(o, "pbfName", label),
                            date = o.optString("date", ""),
                            total = reqInt(o, "total", label),
                            createdAt = o.optString("createdAt", "")
                        )
                    )
                } catch (e: JSONException) {
                    throw IllegalArgumentException("$label rusak: ${e.message}")
                }
            }
        }

        val purchaseItems = mutableListOf<PurchaseItemEntity>()
        val purchaseItemArr = root.optJSONArray("purchaseItems")
        if (purchaseItemArr != null) {
            for (i in 0 until purchaseItemArr.length()) {
                val label = "Baris item pembelian ke-${i + 1}"
                val o = rowObject(purchaseItemArr, i, label)
                try {
                    val qty = reqInt(o, "qty", label)
                    val buyPrice = reqInt(o, "buyPrice", label)
                    val subtotal = reqInt(o, "subtotal", label)
                    // A4: subtotal harus konsisten dengan qty * harga beli.
                    if (subtotal.toLong() != qty.toLong() * buyPrice.toLong()) {
                        throw IllegalArgumentException(
                            "$label rusak: subtotal $subtotal tidak sama dengan qty $qty x harga $buyPrice"
                        )
                    }
                    purchaseItems.add(
                        PurchaseItemEntity(
                            id = reqInt(o, "id", label),
                            purchaseId = reqInt(o, "purchaseId", label),
                            medicineName = o.optString("medicineName", ""),
                            qty = qty,
                            unit = o.optString("unit", "Strip"),
                            buyPrice = buyPrice,
                            subtotal = subtotal
                        )
                    )
                } catch (e: JSONException) {
                    throw IllegalArgumentException("$label rusak: ${e.message}")
                }
            }
        }

        // A4: validasi relasi — setiap item pembelian harus punya pembelian induk.
        val purchaseIds = purchases.map { it.id }.toSet()
        for ((index, pItem) in purchaseItems.withIndex()) {
            if (pItem.purchaseId !in purchaseIds) {
                throw IllegalArgumentException(
                    "Baris item pembelian ke-${index + 1} rusak: purchaseId ${pItem.purchaseId} " +
                        "tidak ditemukan di daftar purchases"
                )
            }
        }

        return BackupData(medicines, sales, items, entries, purchases, purchaseItems)
    }

    /** A3: array wajib ada dan benar-benar JSONArray; kalau tidak, backup ditolak. */
    private fun requireArray(root: JSONObject, key: String): JSONArray {
        if (!root.has(key) || root.optJSONArray(key) == null) {
            throw IllegalArgumentException("Backup tidak lengkap: array \"$key\" tidak ditemukan")
        }
        return root.getJSONArray(key)
    }

    /** A4: ambil objek baris ke-i; kalau elemennya bukan objek JSON, laporkan barisnya. */
    private fun rowObject(arr: JSONArray, index: Int, label: String): JSONObject {
        return try {
            arr.getJSONObject(index)
        } catch (e: JSONException) {
            throw IllegalArgumentException("$label rusak: ${e.message}")
        }
    }

    /**
     * A4: field wajib bertipe Int. Bila key hilang / null / tipenya salah,
     * lempar IllegalArgumentException (bukan diam-diam 0) — berlaku untuk
     * field uang (total, amountPaid, changeAmount, qty, price, buyPrice,
     * subtotal, amount) dan field kunci/relasi (id, saleId, medicineId).
     */
    private fun reqInt(o: JSONObject, key: String, label: String): Int {
        if (!o.has(key) || o.isNull(key)) {
            throw IllegalArgumentException("$label rusak: field \"$key\" hilang")
        }
        return try {
            o.getInt(key)
        } catch (e: JSONException) {
            throw IllegalArgumentException("$label rusak: field \"$key\" tidak valid (${e.message})")
        }
    }

    /** A4: field wajib bertipe String (field kunci/relasi seperti name dan invoiceNo). */
    private fun reqString(o: JSONObject, key: String, label: String): String {
        if (!o.has(key) || o.isNull(key)) {
            throw IllegalArgumentException("$label rusak: field \"$key\" hilang")
        }
        return try {
            o.getString(key)
        } catch (e: JSONException) {
            throw IllegalArgumentException("$label rusak: field \"$key\" tidak valid (${e.message})")
        }
    }
}
