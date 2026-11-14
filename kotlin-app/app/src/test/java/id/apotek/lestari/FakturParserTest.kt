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

    @Test
    fun `faktur APL Anugerah Pharmindo Lestari terbaca`() {
        // Baris-baris dari foto faktur asli 15 Nov 2026 (No. 211697802).
        val hasil = parseFaktur(
            listOf(
                "PT ANUGERAH PHARMINDO LESTARI",
                "No 211697802 (RIT)",
                "8211742720 Normal 03.11.2026 03.12.2026 21PG02/01 1921TSD015 21D42 0,00",
                "SIIPDC1NBLS PANADOL CAPLET 100S - BLISTER (F)-E0/07,860 4K6C 19.03.2029 10 BLS 9.780 97.800",
                "SIIPDE1NBLS PANADOL EXTRA 100S - BLISTER (F)-E0/0/0,880 553P 25.03.2029 20 BLS 10.990 219.800",
                "SBSEV4N SCOTT'S EMULSION VITA 400 ML (NEW) (F)-E0/0/2,000 6OJ147 29.09.2028 2 BTL 70.700 141.400",
                "SZVEL10N VOLTAREN EMULGEL 10 GR NEW (F)-E0/0/4,000 GU2N 16.08.2028 6 TUB 50.800 304.800",
                "TOTAL 1 POT. PENJUALAN POT. CASH TOTAL 2 P.P.N METERAI JUMLAH TAGIHAN",
                "763.800 37.819 0 725.981 79.858 0 805.839"
            )
        )
        assertEquals("PT ANUGERAH PHARMINDO LESTARI", hasil.pbfName)
        assertEquals("211697802", hasil.invoiceNo)
        assertEquals("2026-11-03", hasil.tanggal)
        assertEquals(4, hasil.items.size)
        assertEquals("PANADOL CAPLET 100S - BLISTER", hasil.items[0].nama)
        assertEquals(10, hasil.items[0].qty)
        assertEquals(9780, hasil.items[0].hargaSatuan)
        assertEquals("PANADOL EXTRA 100S - BLISTER", hasil.items[1].nama)
        assertEquals(20, hasil.items[1].qty)
        assertEquals(10990, hasil.items[1].hargaSatuan)
        assertEquals("SCOTT'S EMULSION VITA 400 ML (NEW)", hasil.items[2].nama)
        assertEquals(2, hasil.items[2].qty)
        assertEquals(70700, hasil.items[2].hargaSatuan)
        assertEquals("VOLTAREN EMULGEL 10 GR NEW", hasil.items[3].nama)
        assertEquals(6, hasil.items[3].qty)
        assertEquals(50800, hasil.items[3].hargaSatuan)
    }
}
