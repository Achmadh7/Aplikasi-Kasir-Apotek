package id.apotek.lestari

import id.apotek.lestari.ui.cocokkanNama
import id.apotek.lestari.ui.parseBarisItem
import id.apotek.lestari.ui.parseFaktur
import id.apotek.lestari.ui.parseTanggal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test JVM untuk parser Foto Faktur (OCR): nomor faktur, tanggal,
 * baris obat (qty/harga/subtotal), PPN, dan pencocokan nama obat.
 * Parser ini murni String -> data, jadi bisa diuji di CI tanpa HP.
 */
class FakturParserTest {

    @Test
    fun `nomor faktur dan tanggal terbaca`() {
        val hasil = parseFaktur(
            listOf(
                "PT Kimia Farma",
                "Faktur No. INV/2026/001",
                "Tanggal: 12/11/2026",
                "Paracetamol 500mg 10 12.000 120.000",
                "TOTAL 120.000"
            )
        )
        assertEquals("PT Kimia Farma", hasil.pbfName)
        assertEquals("INV/2026/001", hasil.invoiceNo)
        assertEquals("2026-11-12", hasil.tanggal)
        assertEquals(1, hasil.items.size)
        assertEquals("Paracetamol 500mg", hasil.items[0].nama)
        assertEquals(10, hasil.items[0].qty)
        assertEquals(12000, hasil.items[0].hargaSatuan)
    }

    @Test
    fun `baris item dengan x dan tanpa subtotal`() {
        val a = parseBarisItem("Amoxicillin 500mg 5 x 22.000 110.000")
        assertEquals("Amoxicillin 500mg", a?.nama)
        assertEquals(5, a?.qty)
        assertEquals(22000, a?.hargaSatuan)
        val b = parseBarisItem("Vitamin C 1000mg 2 50.000")
        assertEquals(2, b?.qty)
        assertEquals(50000, b?.hargaSatuan)
        assertNull(parseBarisItem("Paracetamol 500mg"))
        assertEquals(0, parseFaktur(listOf("TOTAL 364.000")).items.size)
    }

    @Test
    fun `tanggal nama bulan dan ppn terbaca`() {
        assertEquals("2026-11-12", parseTanggal("12 Nov 2026"))
        assertEquals("2026-01-05", parseTanggal("5 Januari 2026"))
        assertEquals("2026-11-12", parseTanggal("12-11-26"))
        assertNull(parseTanggal("1.200.000"))
        val hasil = parseFaktur(listOf("PPN 11% 13.200", "Paracetamol 500mg 10 12.000 120.000"))
        assertEquals(13200, hasil.ppn)
        assertEquals(1, hasil.items.size)
    }

    @Test
    fun `cocokkan nama obat abaikan satuan dan huruf besar`() {
        assertEquals(
            "Paracetamol 500mg",
            cocokkanNama("paracetamol 500 mg", listOf("Paracetamol 500mg", "Amoxicillin 500mg"))
        )
        assertEquals(
            "Amoxicillin 500mg",
            cocokkanNama("Amoxicillin", listOf("Paracetamol 500mg", "Amoxicillin 500mg"))
        )
        assertNull(cocokkanNama("Obat Tidak Ada", listOf("Paracetamol 500mg")))
    }
}
