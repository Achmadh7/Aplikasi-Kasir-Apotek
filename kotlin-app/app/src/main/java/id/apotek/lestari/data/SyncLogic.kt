package id.apotek.lestari.data

import id.apotek.lestari.data.local.CashEntryEntity
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.data.local.PurchaseEntity
import id.apotek.lestari.data.local.PurchaseItemEntity
import id.apotek.lestari.data.local.SaleEntity
import id.apotek.lestari.data.local.SaleItemEntity

/**
 * Logika sinkronisasi Mode Online yang murni (tanpa Firebase, tanpa
 * Android) supaya bisa diuji di JVM: pemetaan Entity <-> peta dokumen
 * Firestore dan aturan konflik last-write-wins.
 *
 * Item penjualan/pembelian menunjuk induknya lewat remoteId induk
 * (saleRemoteId / purchaseRemoteId), karena ID lokal Int berbeda di
 * setiap perangkat. Semua angka disimpan sebagai Long di dokumen.
 */
object SyncLogic {

    /**
     * Aturan konflik: salinan remote menang bila cap waktunya lebih baru;
     * bila cap waktunya sama persis, tombstone (penghapusan) yang menang
     * supaya penghapusan tidak hidup lagi.
     */
    fun remoteMenang(
        remoteUpdatedAt: Long,
        remoteTerhapus: Boolean,
        lokalUpdatedAt: Long,
        lokalTerhapus: Boolean
    ): Boolean =
        if (remoteUpdatedAt != lokalUpdatedAt) remoteUpdatedAt > lokalUpdatedAt
        else remoteTerhapus && !lokalTerhapus

    private fun Map<String, Any?>.teks(kunci: String): String? = this[kunci] as? String
    private fun Map<String, Any?>.teksWajib(kunci: String): String = this[kunci] as? String ?: ""
    private fun Map<String, Any?>.angka(kunci: String): Long =
        (this[kunci] as? Number)?.toLong() ?: 0L
    private fun Map<String, Any?>.int(kunci: String): Int = angka(kunci).toInt()
    private fun Map<String, Any?>.terhapus(): Int =
        if (this["terhapus"] as? Boolean == true) 1 else 0

    private fun dasar(e: String, updatedAt: Long, terhapus: Int): MutableMap<String, Any?> =
        mutableMapOf(
            "remoteId" to e,
            "updatedAt" to updatedAt,
            "terhapus" to (terhapus == 1)
        )

    // ---- Obat ----

    fun obatKePeta(e: MedicineEntity): Map<String, Any?> = dasar(e.remoteId, e.updatedAt, e.terhapus).apply {
        put("name", e.name)
        put("category", e.category)
        put("unit", e.unit)
        put("sellPrice", e.sellPrice.toLong())
        put("wholesalePrice", e.wholesalePrice.toLong())
        put("stock", e.stock.toLong())
        put("expiryDate", e.expiryDate)
        put("barcode", e.barcode)
        put("createdAt", e.createdAt)
    }

    fun obatDariPeta(m: Map<String, Any?>): MedicineEntity? {
        val remoteId = m.teks("remoteId")?.takeIf { it.isNotBlank() } ?: return null
        return MedicineEntity(
            id = 0,
            name = m.teksWajib("name"),
            category = m.teks("category") ?: "Umum",
            unit = m.teks("unit") ?: "Strip",
            buyPrice = 0,
            sellPrice = m.int("sellPrice"),
            wholesalePrice = m.int("wholesalePrice"),
            stock = m.int("stock"),
            expiryDate = m.teks("expiryDate"),
            barcode = m.teks("barcode"),
            createdAt = m.teksWajib("createdAt"),
            remoteId = remoteId,
            updatedAt = m.angka("updatedAt"),
            terhapus = m.terhapus(),
            kotor = 0
        )
    }

    // ---- Penjualan ----

    fun penjualanKePeta(e: SaleEntity): Map<String, Any?> = dasar(e.remoteId, e.updatedAt, e.terhapus).apply {
        put("invoiceNo", e.invoiceNo)
        put("total", e.total.toLong())
        put("paymentMethod", e.paymentMethod)
        put("amountPaid", e.amountPaid.toLong())
        put("changeAmount", e.changeAmount.toLong())
        put("createdAt", e.createdAt)
    }

    fun penjualanDariPeta(m: Map<String, Any?>): SaleEntity? {
        val remoteId = m.teks("remoteId")?.takeIf { it.isNotBlank() } ?: return null
        return SaleEntity(
            id = 0,
            invoiceNo = m.teksWajib("invoiceNo"),
            total = m.int("total"),
            paymentMethod = m.teks("paymentMethod") ?: "Tunai",
            amountPaid = m.int("amountPaid"),
            changeAmount = m.int("changeAmount"),
            createdAt = m.teksWajib("createdAt"),
            remoteId = remoteId,
            updatedAt = m.angka("updatedAt"),
            terhapus = m.terhapus(),
            kotor = 0
        )
    }

    // ---- Item penjualan ----

    fun itemJualKePeta(e: SaleItemEntity, saleRemoteId: String, medicineRemoteId: String): Map<String, Any?> =
        dasar(e.remoteId, e.updatedAt, e.terhapus).apply {
            put("saleRemoteId", saleRemoteId)
            put("medicineRemoteId", medicineRemoteId)
            put("medicineName", e.medicineName)
            put("category", e.category)
            put("unit", e.unit)
            put("qty", e.qty.toLong())
            put("price", e.price.toLong())
            put("priceType", e.priceType)
            put("subtotal", e.subtotal.toLong())
        }

    fun itemJualIndukRemoteId(m: Map<String, Any?>): String? =
        m.teks("saleRemoteId")?.takeIf { it.isNotBlank() }

    fun itemJualObatRemoteId(m: Map<String, Any?>): String? =
        m.teks("medicineRemoteId")?.takeIf { it.isNotBlank() }

    fun itemJualDariPeta(m: Map<String, Any?>, saleIdLokal: Int, medicineIdLokal: Int): SaleItemEntity? {
        val remoteId = m.teks("remoteId")?.takeIf { it.isNotBlank() } ?: return null
        return SaleItemEntity(
            id = 0,
            saleId = saleIdLokal,
            medicineId = medicineIdLokal,
            medicineName = m.teksWajib("medicineName"),
            category = m.teksWajib("category"),
            unit = m.teksWajib("unit"),
            qty = m.int("qty"),
            price = m.int("price"),
            priceType = m.teks("priceType") ?: "ECER",
            buyPrice = 0,
            subtotal = m.int("subtotal"),
            remoteId = remoteId,
            updatedAt = m.angka("updatedAt"),
            terhapus = m.terhapus(),
            kotor = 0
        )
    }

    // ---- Kas ----

    fun kasKePeta(e: CashEntryEntity): Map<String, Any?> = dasar(e.remoteId, e.updatedAt, e.terhapus).apply {
        put("type", e.type)
        put("category", e.category)
        put("description", e.description)
        put("amount", e.amount.toLong())
        put("source", e.source)
        put("createdAt", e.createdAt)
    }

    fun kasDariPeta(m: Map<String, Any?>): CashEntryEntity? {
        val remoteId = m.teks("remoteId")?.takeIf { it.isNotBlank() } ?: return null
        val type = m.teks("type") ?: return null
        if (type != "in" && type != "out") return null
        return CashEntryEntity(
            id = 0,
            type = type,
            category = m.teksWajib("category"),
            description = m.teksWajib("description"),
            amount = m.int("amount"),
            source = m.teks("source") ?: "manual",
            createdAt = m.teksWajib("createdAt"),
            remoteId = remoteId,
            updatedAt = m.angka("updatedAt"),
            terhapus = m.terhapus(),
            kotor = 0
        )
    }

    // ---- Pembelian PBF ----

    fun pbfKePeta(e: PurchaseEntity): Map<String, Any?> = dasar(e.remoteId, e.updatedAt, e.terhapus).apply {
        put("pbfName", e.pbfName)
        put("invoiceNo", e.invoiceNo)
        put("date", e.date)
        put("total", e.total.toLong())
        put("ppn", e.ppn.toLong())
        put("paymentType", e.paymentType)
        put("dueDate", e.dueDate)
        put("createdAt", e.createdAt)
    }

    fun pbfDariPeta(m: Map<String, Any?>): PurchaseEntity? {
        val remoteId = m.teks("remoteId")?.takeIf { it.isNotBlank() } ?: return null
        return PurchaseEntity(
            id = 0,
            pbfName = m.teksWajib("pbfName"),
            invoiceNo = m.teksWajib("invoiceNo"),
            date = m.teksWajib("date"),
            total = m.int("total"),
            ppn = m.int("ppn"),
            paymentType = m.teks("paymentType") ?: "CASH",
            dueDate = m.teksWajib("dueDate"),
            createdAt = m.teksWajib("createdAt"),
            remoteId = remoteId,
            updatedAt = m.angka("updatedAt"),
            terhapus = m.terhapus(),
            kotor = 0
        )
    }

    // ---- Item pembelian PBF ----

    fun itemPbfKePeta(e: PurchaseItemEntity, purchaseRemoteId: String): Map<String, Any?> =
        dasar(e.remoteId, e.updatedAt, e.terhapus).apply {
            put("purchaseRemoteId", purchaseRemoteId)
            put("medicineName", e.medicineName)
            put("qty", e.qty.toLong())
            put("unit", e.unit)
            put("buyPrice", e.buyPrice.toLong())
            put("subtotal", e.subtotal.toLong())
        }

    fun itemPbfIndukRemoteId(m: Map<String, Any?>): String? =
        m.teks("purchaseRemoteId")?.takeIf { it.isNotBlank() }

    fun itemPbfDariPeta(m: Map<String, Any?>, purchaseIdLokal: Int): PurchaseItemEntity? {
        val remoteId = m.teks("remoteId")?.takeIf { it.isNotBlank() } ?: return null
        return PurchaseItemEntity(
            id = 0,
            purchaseId = purchaseIdLokal,
            medicineName = m.teksWajib("medicineName"),
            qty = m.int("qty"),
            unit = m.teks("unit") ?: "Strip",
            buyPrice = m.int("buyPrice"),
            subtotal = m.int("subtotal"),
            remoteId = remoteId,
            updatedAt = m.angka("updatedAt"),
            terhapus = m.terhapus(),
            kotor = 0
        )
    }
}
