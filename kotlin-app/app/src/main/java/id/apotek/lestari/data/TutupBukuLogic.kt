package id.apotek.lestari.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Logika murni Tutup Buku Bulanan (TB1, pilihan Achmad 2026-11-17) —
// tanpa dependensi Android, supaya bisa dites unit murni. Semua batas
// bulan dihitung dalam Asia/Jakarta (WIB), sama seperti semua logika
// periode/hari lain di Repository.
private val ZONA_WIB: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
private val POLA_BULAN = Regex("""\d{4}-\d{2}""")

/** Kunci bulan ("yyyy-MM") valid: 4 digit tahun + bulan 01-12. */
fun bulanValid(bulan: String): Boolean {
    if (!POLA_BULAN.matches(bulan)) return false
    val month = bulan.substring(5, 7).toInt()
    return month in 1..12
}

/** Kunci bulan ("yyyy-MM") dari epoch ms, dalam WIB. */
fun bulanKeyDariMillis(ms: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = ZONA_WIB }.format(Date(ms))

/** Kunci bulan dari string ISO UTC ("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
 *  null bila tidak terbaca. Tanggal "yyyy-MM-dd" saja juga diterima. */
fun bulanKeyDariIso(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val parsed = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(iso.trim())
    } catch (_: Exception) {
        null
    }
    if (parsed != null) return bulanKeyDariMillis(parsed.time)
    // Fallback: tujuh karakter pertama tanggal "yyyy-MM-dd".
    val s = iso.trim()
    return if (s.length >= 7 && bulanValid(s.substring(0, 7))) s.substring(0, 7) else null
}

/** Rentang [awal, akhir) satu bulan dalam epoch ms WIB; null bila kunci tak valid. */
fun rentangBulan(bulan: String): Pair<Long, Long>? {
    if (!bulanValid(bulan)) return null
    val year = bulan.substring(0, 4).toInt()
    val month = bulan.substring(5, 7).toInt()
    val start = Calendar.getInstance(ZONA_WIB).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
    val end = Calendar.getInstance(ZONA_WIB).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, 1)
    }.timeInMillis
    return start to end
}

/** Benar bila bulan sudah lewat seluruhnya (akhir bulan <= awal bulan berjalan). */
fun bulanSelesai(bulan: String, sekarangMs: Long = System.currentTimeMillis()): Boolean {
    val end = rentangBulan(bulan)?.second ?: return false
    val awalBulanBerjalan = Calendar.getInstance(ZONA_WIB).apply {
        timeInMillis = sekarangMs
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return end <= awalBulanBerjalan
}

/** Bulan sebelumnya ("yyyy-MM"); null bila kunci tak valid. */
fun bulanSebelumnya(bulan: String): String? {
    val start = rentangBulan(bulan)?.first ?: return null
    return bulanKeyDariMillis(start - 1)
}

/** Nama bulan Indonesia: "November 2026". Kunci tak valid dikembalikan apa adanya. */
fun namaBulanIndonesia(bulan: String): String {
    val start = rentangBulan(bulan)?.first ?: return bulan
    return SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).apply { timeZone = ZONA_WIB }.format(Date(start))
}
