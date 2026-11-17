package id.apotek.lestari

import id.apotek.lestari.data.bulanKeyDariIso
import id.apotek.lestari.data.bulanKeyDariMillis
import id.apotek.lestari.data.bulanSebelumnya
import id.apotek.lestari.data.bulanSelesai
import id.apotek.lestari.data.bulanValid
import id.apotek.lestari.data.namaBulanIndonesia
import id.apotek.lestari.data.rentangBulan
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM untuk logika murni Tutup Buku Bulanan (TB1): kunci
 * bulan, rentang bulan, dan aturan "bulan sudah selesai" — semuanya
 * dalam Asia/Jakarta (WIB). Regresinya tertangkap di CI, bukan di HP.
 */
class TutupBukuLogicTest {

    /** Epoch ms untuk tanggal/jam WIB tertentu. */
    private fun wib(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    @Test
    fun bulanKeyDariMillis_memakaiWibBukanUtc() {
        // 1 Nov 2026 00:00 WIB = 31 Okt 2026 17:00 UTC — kunci harus November.
        assertEquals("2026-11", bulanKeyDariMillis(wib(2026, 11, 1, 0, 0, 0)))
        assertEquals("2026-10", bulanKeyDariMillis(wib(2026, 10, 31, 23, 59, 59)))
        assertEquals("2025-12", bulanKeyDariMillis(wib(2025, 12, 31, 23, 59, 59)))
    }

    @Test
    fun bulanKeyDariIso_mengonversiUtcKeWib() {
        // 31 Okt 17:00 UTC = 1 Nov 00:00 WIB.
        assertEquals("2026-11", bulanKeyDariIso("2026-10-31T17:00:00.000Z"))
        // 31 Okt 16:59:59 UTC = 31 Okt 23:59:59 WIB.
        assertEquals("2026-10", bulanKeyDariIso("2026-10-31T16:59:59.000Z"))
        assertEquals("2026-11", bulanKeyDariIso("2026-11-17T10:30:00.000Z"))
        // Tanggal saja (tanpa jam) juga diterima.
        assertEquals("2026-11", bulanKeyDariIso("2026-11-05"))
        assertNull(bulanKeyDariIso(null))
        assertNull(bulanKeyDariIso(""))
        assertNull(bulanKeyDariIso("2026-13-01"))
        assertNull(bulanKeyDariIso("bukan-tanggal"))
    }

    @Test
    fun rentangBulan_batasAwalDanAkhir() {
        val november = rentangBulan("2026-11")!!
        assertEquals("2026-11", bulanKeyDariMillis(november.first))
        assertEquals("2026-12", bulanKeyDariMillis(november.second))
        // November 30 hari.
        assertEquals(30L * 24 * 3600 * 1000, november.second - november.first)
        // Februari 2026 (bukan kabisat) 28 hari.
        val februari = rentangBulan("2026-02")!!
        assertEquals(28L * 24 * 3600 * 1000, februari.second - februari.first)
        assertNull(rentangBulan("2026-13"))
        assertNull(rentangBulan("2026-1"))
        assertNull(rentangBulan("2026-11-01"))
    }

    @Test
    fun bulanSelesai_hanyaBulanYangSudahLewat() {
        val pertengahanNovember = wib(2026, 11, 17, 12, 0, 0)
        assertTrue(bulanSelesai("2026-10", pertengahanNovember))
        assertFalse(bulanSelesai("2026-11", pertengahanNovember))
        assertFalse(bulanSelesai("2026-12", pertengahanNovember))
        // Batas persis: 1 Des 00:00 WIB, November selesai.
        assertTrue(bulanSelesai("2026-11", wib(2026, 12, 1, 0, 0, 0)))
        // Satu detik sebelumnya, November belum selesai.
        assertFalse(bulanSelesai("2026-11", wib(2026, 11, 30, 23, 59, 59)))
    }

    @Test
    fun bulanSebelumnya_melewatiPergantianTahun() {
        assertEquals("2025-12", bulanSebelumnya("2026-01"))
        assertEquals("2026-10", bulanSebelumnya("2026-11"))
        assertNull(bulanSebelumnya("2026-13"))
    }

    @Test
    fun bulanValid_menolakBulanDiLuarKalender() {
        assertTrue(bulanValid("2026-11"))
        assertTrue(bulanValid("2026-01"))
        assertFalse(bulanValid("2026-00"))
        assertFalse(bulanValid("2026-13"))
        assertFalse(bulanValid("2026-1"))
        assertFalse(bulanValid("2026-11-01"))
    }

    @Test
    fun namaBulanIndonesia_namaBulanBahasaIndonesia() {
        assertEquals("November 2026", namaBulanIndonesia("2026-11"))
        assertEquals("Januari 2026", namaBulanIndonesia("2026-01"))
        assertEquals("Agustus 2026", namaBulanIndonesia("2026-08"))
        assertEquals("2026-13", namaBulanIndonesia("2026-13"))
    }
}
