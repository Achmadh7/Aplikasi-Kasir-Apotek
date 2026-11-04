package id.apotek.lestari

import id.apotek.lestari.ui.theme.dayKeyID
import id.apotek.lestari.ui.theme.daysUntilExpiry
import id.apotek.lestari.ui.theme.formatCompactIDR
import id.apotek.lestari.ui.theme.formatDecimal
import id.apotek.lestari.ui.theme.formatIDR
import id.apotek.lestari.ui.theme.marginTextID
import id.apotek.lestari.ui.theme.parseDecimal
import id.apotek.lestari.ui.theme.stockLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit test JVM untuk logika murni di Theme.kt (format uang/teks, skala
 * status stok, hitung hari kedaluwarsa) — fungsi-fungsi ini menentukan
 * angka yang dilihat pengguna di semua layar, jadi regresinya harus
 * tertangkap di CI sebelum APK dibangun.
 */
class ThemeLogicTest {

    @Test
    fun formatIDR_groupsThousandsWithDots() {
        assertEquals("Rp 0", formatIDR(0))
        assertEquals("Rp 999", formatIDR(999))
        assertEquals("Rp 1.000", formatIDR(1000))
        assertEquals("Rp 1.500.000", formatIDR(1_500_000))
        assertEquals("Rp 25.750", formatIDR(25_750))
    }

    @Test
    fun formatDecimal_groupsAndStripsLeadingZeros() {
        assertEquals("", formatDecimal(""))
        assertEquals("0", formatDecimal("0"))
        assertEquals("0", formatDecimal("000"))
        assertEquals("7", formatDecimal("007"))
        assertEquals("1.000", formatDecimal("1000"))
        assertEquals("1.000.000", formatDecimal("1000000"))
        // Karakter non-digit dibuang.
        assertEquals("12.500", formatDecimal("Rp12.500"))
    }

    @Test
    fun parseDecimal_readsFormattedAndClampsOverflow() {
        assertEquals(0, parseDecimal(""))
        assertEquals(0, parseDecimal("Rp "))
        assertEquals(1_000_000, parseDecimal("1.000.000"))
        assertEquals(25_750, parseDecimal("25750"))
        // Di atas rentang Int dibatasi ke Int.MAX_VALUE, bukan jadi 0.
        assertEquals(Int.MAX_VALUE, parseDecimal("999999999999"))
        assertEquals(Int.MAX_VALUE, parseDecimal("99999999999999999999999999"))
    }

    @Test
    fun formatDecimal_parseDecimal_roundTrip() {
        for (n in listOf(0, 5, 999, 1000, 123_456_789, 2_000_000_000)) {
            assertEquals(n, parseDecimal(formatDecimal(n.toString())))
        }
    }

    @Test
    fun formatCompactIDR_shortensForChartLabels() {
        // Format aplikasi memang TANPA spasi ("150rb" / "2,5jt" seperti
        // KDoc fungsinya) — beda dari formatRupiahShort di aplikasi Kasir.
        assertEquals("0", formatCompactIDR(0))
        assertEquals("999", formatCompactIDR(999))
        assertEquals("96rb", formatCompactIDR(96_000))
        assertEquals("1,5rb", formatCompactIDR(1_500))
        assertEquals("1,5jt", formatCompactIDR(1_500_000))
        assertEquals("2jt", formatCompactIDR(2_000_000))
        assertEquals("-1,5rb", formatCompactIDR(-1_500))
    }

    @Test
    fun stockLabel_followsTheFixedScale() {
        // Skala tetap keputusan 2026-10-29: 0 Habis, 1-9 Rendah,
        // 10-15 Sedang, 16+ Aman. String ini kunci logika antar layar.
        assertEquals("Habis", stockLabel(0))
        assertEquals("Habis", stockLabel(-3))
        assertEquals("Rendah", stockLabel(1))
        assertEquals("Rendah", stockLabel(9))
        assertEquals("Sedang", stockLabel(10))
        assertEquals("Sedang", stockLabel(15))
        assertEquals("Aman", stockLabel(16))
        assertEquals("Aman", stockLabel(500))
    }

    @Test
    fun marginTextID_usesIndonesianDecimalComma() {
        assertEquals("0,0", marginTextID(0.0))
        assertEquals("12,3", marginTextID(12.34))
        assertEquals("40,9", marginTextID(40.94))
    }

    @Test
    fun daysUntilExpiry_countsFromTodayJakarta() {
        assertEquals(0, daysUntilExpiry(dayKeyID(Date())))
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 10)
        assertEquals(10, daysUntilExpiry(dayKeyID(cal.time)))
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, -2)
        assertEquals(-2, daysUntilExpiry(dayKeyID(cal.time)))
        assertNull(daysUntilExpiry(""))
        assertNull(daysUntilExpiry(null))
        assertNull(daysUntilExpiry("bukan-tanggal"))
    }
}
