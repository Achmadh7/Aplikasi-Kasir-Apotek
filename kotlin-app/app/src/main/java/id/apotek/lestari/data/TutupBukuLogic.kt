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

/** Kunci bulan dari string ISO UTC ("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
 *  "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss", atau tanggal
 *  "yyyy-MM-dd" saja); null bila tidak terbaca. Semua pola dibaca sebagai
 *  UTC lalu dikonversi ke WIB — jangan potong mentah 7 karakter pertama,
 *  karena jam-jam sebelum tengah malam UTC bisa sudah bulan berikutnya
 *  di WIB (R5, audit 2026-11-18). */
fun bulanKeyDariIso(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val s = iso.trim()
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (p in patterns) {
        val parsed = try {
            SimpleDateFormat(p, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(s)
        } catch (_: Exception) {
            null
        }
        if (parsed != null) return bulanKeyDariMillis(parsed.time)
    }
    // Fallback: kunci bulan mentah "yyyy-MM" saja.
    return if (s.length == 7 && bulanValid(s)) s else null
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
