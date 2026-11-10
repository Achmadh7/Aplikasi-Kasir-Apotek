package id.apotek.lestari

import id.apotek.lestari.data.SyncLogic
import id.apotek.lestari.data.local.CashEntryEntity
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.data.local.PurchaseEntity
import id.apotek.lestari.data.local.PurchaseItemEntity
import id.apotek.lestari.data.local.SaleEntity
import id.apotek.lestari.data.local.SaleItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM untuk logika sinkronisasi Mode Online (SyncLogic):
 * pemetaan Entity <-> peta dokumen Firestore harus bolak-balik utuh,
 * dan aturan konflik last-write-wins harus konsisten — regresinya
 * tertangkap di CI, bukan di HP.
 */
class SyncLogicTest {

    @Test
    fun remoteMenang_hanyaBilaLebihBaru() {
        assertTrue(SyncLogic.remoteMenang(200, false, 100, false))
        assertFalse(SyncLogic.remoteMenang(100, false, 200, false))
        assertFalse(SyncLogic.remoteMenang(100, false, 100, false))
    }

    @Test
    fun remoteMenang_capSamaTombstoneYangMenang() {
        assertTrue(SyncLogic.remoteMenang(100, true, 100, false))
        assertFalse(SyncLogic.remoteMenang(100, false, 100, true))
        assertFalse(SyncLogic.remoteMenang(100, true, 100, true))
    }

    @Test
    fun obat_bolakBalikPeta() {
        val asal = MedicineEntity(
            id = 7, name = "Paracetamol 500mg", category = "Pereda Nyeri", unit = "Strip",
            buyPrice = 3000, sellPrice = 5000, wholesalePrice = 4500, stock = 33,
            expiryDate = "2026-11-30", barcode = "8991234567890",
            createdAt = "2026-10-26T00:15:00.000Z",
            remoteId = "obat-7", updatedAt = 123456789L, terhapus = 0, kotor = 1
        )
        val hasil = SyncLogic.obatDariPeta(SyncLogic.obatKePeta(asal))!!
        assertEquals(0, hasil.id)
        assertEquals(asal.name, hasil.name)
        assertEquals(asal.category, hasil.category)
        assertEquals(0, hasil.buyPrice) // Harga Beli tidak lagi disinkron (dihapus total 2026-11-10)
        assertEquals(asal.sellPrice, hasil.sellPrice)
        assertEquals(asal.wholesalePrice, hasil.wholesalePrice)
        assertEquals(asal.stock, hasil.stock)
        assertEquals(asal.expiryDate, hasil.expiryDate)
        assertEquals(asal.barcode, hasil.barcode)
        assertEquals(asal.createdAt, hasil.createdAt)
        assertEquals("obat-7", hasil.remoteId)
        assertEquals(123456789L, hasil.updatedAt)
        assertEquals(0, hasil.terhapus)
        assertEquals(0, hasil.kotor)
    }

    @Test
    fun obat_tanpaRemoteIdDitolak() {
        val peta = SyncLogic.obatKePeta(
            MedicineEntity(name = "X", category = "Umum", unit = "Strip",
                buyPrice = 1, sellPrice = 2, stock = 0, expiryDate = null,
                barcode = null, createdAt = "", remoteId = "")
        )
        assertNull(SyncLogic.obatDariPeta(peta))
    }

    @Test
    fun penjualanDanItem_bolakBalikPeta() {
        val jual = SaleEntity(
            id = 3, invoiceNo = "INV - 10/11/26", total = 15000, paymentMethod = "QRIS",
            amountPaid = 15000, changeAmount = 0, createdAt = "2026-11-10T02:00:00.000Z",
            remoteId = "jual-3", updatedAt = 500L, terhapus = 1, kotor = 1
        )
        val hasilJual = SyncLogic.penjualanDariPeta(SyncLogic.penjualanKePeta(jual))!!
        assertEquals(jual.invoiceNo, hasilJual.invoiceNo)
        assertEquals(jual.total, hasilJual.total)
        assertEquals(jual.paymentMethod, hasilJual.paymentMethod)
        assertEquals(1, hasilJual.terhapus)

        val item = SaleItemEntity(
            id = 9, saleId = 3, medicineId = 7, medicineName = "Paracetamol 500mg",
            category = "Pereda Nyeri", unit = "Strip", qty = 3, price = 5000,
            priceType = "GROSIR", buyPrice = 3000, subtotal = 15000,
            remoteId = "jualitem-9", updatedAt = 600L, terhapus = 0, kotor = 1
        )
        val peta = SyncLogic.itemJualKePeta(item, "jual-3", "obat-7")
        assertEquals("jual-3", SyncLogic.itemJualIndukRemoteId(peta))
        assertEquals("obat-7", SyncLogic.itemJualObatRemoteId(peta))
        val hasilItem = SyncLogic.itemJualDariPeta(peta, saleIdLokal = 42, medicineIdLokal = 24)!!
        assertEquals(42, hasilItem.saleId)
        assertEquals(24, hasilItem.medicineId)
        assertEquals(item.medicineName, hasilItem.medicineName)
        assertEquals(item.qty, hasilItem.qty)
        assertEquals(item.priceType, hasilItem.priceType)
        assertEquals(item.subtotal, hasilItem.subtotal)
    }

    @Test
    fun kas_bolakBalikPeta_danTypeTidakSahDitolak() {
        val kas = CashEntryEntity(
            id = 5, type = "out", category = "Operasional", description = "Beli plastik",
            amount = 25000, source = "manual", createdAt = "2026-11-10T03:00:00.000Z",
            remoteId = "kas-5", updatedAt = 700L, terhapus = 0, kotor = 1
        )
        val hasil = SyncLogic.kasDariPeta(SyncLogic.kasKePeta(kas))!!
        assertEquals(kas.type, hasil.type)
        assertEquals(kas.amount, hasil.amount)
        val rusak = SyncLogic.kasKePeta(kas).toMutableMap().apply { put("type", "samping") }
        assertNull(SyncLogic.kasDariPeta(rusak))
    }

    @Test
    fun pbfDanItem_bolakBalikPeta() {
        val pbf = PurchaseEntity(
            id = 2, pbfName = "PBF Sehat", invoiceNo = "FKT-001", date = "2026-11-09",
            total = 399555, ppn = 0, paymentType = "KREDIT", dueDate = "2026-12-09",
            createdAt = "2026-11-09T01:00:00.000Z",
            remoteId = "pbf-2", updatedAt = 800L, terhapus = 0, kotor = 1
        )
        val hasil = SyncLogic.pbfDariPeta(SyncLogic.pbfKePeta(pbf))!!
        assertEquals(pbf.pbfName, hasil.pbfName)
        assertEquals(pbf.invoiceNo, hasil.invoiceNo)
        assertEquals(pbf.paymentType, hasil.paymentType)
        assertEquals(pbf.dueDate, hasil.dueDate)
        assertEquals(pbf.total, hasil.total)

        val item = PurchaseItemEntity(
            id = 4, purchaseId = 2, medicineName = "Amoxicillin 500mg", qty = 10,
            unit = "Box", buyPrice = 39955, subtotal = 399550,
            remoteId = "pbfitem-4", updatedAt = 900L, terhapus = 0, kotor = 1
        )
        val peta = SyncLogic.itemPbfKePeta(item, "pbf-2")
        assertEquals("pbf-2", SyncLogic.itemPbfIndukRemoteId(peta))
        val hasilItem = SyncLogic.itemPbfDariPeta(peta, purchaseIdLokal = 11)!!
        assertEquals(11, hasilItem.purchaseId)
        assertEquals(item.medicineName, hasilItem.medicineName)
        assertEquals(item.subtotal, hasilItem.subtotal)
    }
}
