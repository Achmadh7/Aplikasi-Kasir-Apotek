package id.apotek.lestari.ui

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Foto Faktur PBF (OCR) — baca foto faktur lalu isi formulir pembelian.
 *
 * Cara kerja: ML Kit Text Recognition v2 (model Latin ikut di APK, gratis,
 * jalan di HP tanpa internet) membaca huruf di foto; parseFaktur() lalu
 * menebak nama PBF, nomor faktur, tanggal, baris obat, dan PPN dari teksnya.
 *
 * JUJUR: format faktur tiap PBF beda-beda (letak kolom, singkatan, pemisah),
 * jadi parser ini heuristik — bukan pembaca sempurna. Formulir tetap jadi
 * layar periksa: semua hasil tebakan bisa dibetulkan sebelum Simpan. Bila
 * Achmad mengirim foto faktur asli dari PBF langganannya, parser ini yang
 * disesuaikan dulu (bukan ditebak buta).
 */

/** Satu baris obat hasil baca faktur. hargaSatuan dalam rupiah. */
data class FakturItemHasil(
    val nama: String,
    val qty: Int,
    val hargaSatuan: Int,
    val satuan: String = ""
)

/** Hasil baca satu foto faktur — tiap field boleh kosong bila tak terbaca. */
data class FakturHasil(
    val pbfName: String = "",
    val invoiceNo: String = "",
    val tanggal: String = "", // kunci hari "yyyy-MM-dd"; "" bila tak terbaca
    val items: List<FakturItemHasil> = emptyList(),
    val ppn: Int = 0
)

/** Kata yang menandai baris BUKAN item obat (header, total, catatan). */
private val KATA_LEWAT = listOf(
    "subtotal", "total", "ppn", "vat", "diskon", "discount", "bayar",
    "kembali", "tunai", "transfer", "qris", "grand", "jumlah",
    "terbilang", "catatan", "note", "ttd", "hormat", "tanggal", "date",
    "no.", "nomor", "faktur", "invoice", "kepada", "alamat", "telp",
    "phone", "npwp", "customer", "pelanggan", "sales", "kasir",
    "cabang", "item", "nama", "qty", "harga", "satuan",
    "ongkir", "ongkos", "materai", "admin", "biaya", "layanan", "tempo", "terima"
)

private val BULAN = mapOf(
    "jan" to 1, "januari" to 1, "january" to 1,
    "feb" to 2, "februari" to 2, "february" to 2,
    "mar" to 3, "maret" to 3, "march" to 3,
    "apr" to 4, "april" to 4,
    "mei" to 5, "may" to 5,
    "jun" to 6, "juni" to 6, "june" to 6,
    "jul" to 7, "juli" to 7, "july" to 7,
    "agu" to 8, "agustus" to 8, "august" to 8,
    "sep" to 9, "september" to 9,
    "okt" to 10, "oktober" to 10, "october" to 10,
    "nov" to 11, "november" to 11,
    "des" to 12, "desember" to 12, "december" to 12
)

/** "12/11/2026", "12-11-26", "12 Nov 2026" -> "2026-11-12"; null bila bukan tanggal. */
fun parseTanggal(line: String): String? {
    val angka = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})""")
    for (m in angka.findAll(line)) {
        val (d, mo, y) = m.destructured
        val day = d.toIntOrNull() ?: continue
        val month = mo.toIntOrNull() ?: continue
        var year = y.toIntOrNull() ?: continue
        if (year < 100) year += 2000
        if (day in 1..31 && month in 1..12 && year in 2000..2099) {
            return "$year-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
        }
    }
    val namaBulan = Regex(
        """(\d{1,2})\s+(januari|februari|maret|april|mei|juni|juli|agustus|september|oktober|november|desember|january|february|march|may|june|july|august|october|december|jan|feb|mar|apr|jun|jul|agu|sep|okt|nov|des)\s+(\d{4})""",
        RegexOption.IGNORE_CASE
    ).find(line) ?: return null
    val (d, b, y) = namaBulan.destructured
    val day = d.toIntOrNull() ?: return null
    val month = BULAN[b.lowercase()] ?: return null
    val year = y.toIntOrNull() ?: return null
    if (day !in 1..31) return null
    return "$year-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

/** Semua angka dalam teks, "12.000" -> 12000, "12.000,50" -> 12000. */
fun angkaUang(teks: String): List<Int> {
    return Regex("""\d[\d.,]*""").findAll(teks).mapNotNull { m ->
        val t = m.value.trimEnd('.', ',')
        if (t.isEmpty()) return@mapNotNull null
        t.replace(".", "").substringBefore(",").toIntOrNull()
    }.toList()
}

/**
 * Satu baris item: "Paracetamol 500mg 10 12.000 120.000" -> nama/qty/harga.
 * Token angka di AKHIR baris dibaca sebagai qty, harga, subtotal (boleh
 * "10 x 12.000"); nama = semua sebelum token angka itu. Bila qty x harga
 * tidak sama dengan subtotal, harga dihitung dari subtotal/qty bila pas.
 * Null bila baris tak punya angka atau namanya kosong.
 */
fun parseBarisItem(line: String): FakturItemHasil? {
    val tanpaNomor = line.trim().replace(Regex("""^\d+[.)]\s*"""), "")
    val token = tanpaNomor.split(Regex("""\s+""")).filter { it.isNotEmpty() }
    if (token.isEmpty()) return null
    // Kumpulkan token angka murni dari ujung baris ("x" di antara boleh).
    val angka = mutableListOf<Int>()
    var i = token.size - 1
    while (i >= 0) {
        val t = token[i]
        if (t.equals("x", ignoreCase = true)) {
            i--
            continue
        }
        val v = if (Regex("""^\d[\d.,]*$""").matches(t)) {
            t.replace(".", "").substringBefore(",").toIntOrNull()
        } else null
        if (v == null) break
        angka.add(0, v)
        i--
    }
    if (angka.isEmpty()) return null
    val nama = token.subList(0, i + 1).joinToString(" ")
        .replace(Regex("""(?i)\brp\b"""), "")
        .replace(Regex("""[xX\-–—]+\s*$"""), "")
        .replace(Regex("""\s+"""), " ").trim()
    if (nama.length < 2 || !nama.any { it.isLetter() }) return null
    val (qty, harga) = when {
        angka.size >= 3 -> {
            val q = angka[angka.size - 3]
            val h = angka[angka.size - 2]
            val sub = angka[angka.size - 1]
            if (q in 1..9999 && q * h == sub) q to h
            else if (q in 1..9999 && sub % q == 0 && sub / q > 0) q to (sub / q)
            else if (q in 1..9999) q to h
            else 1 to h
        }
        angka.size == 2 -> {
            val a = angka[0]
            val b = angka[1]
            if (a in 1..999) a to b
            else if (b >= a) 1 to a // (harga, subtotal) dengan qty 1
            else 1 to b
        }
        else -> 1 to angka[0]
    }
    if (qty <= 0 || harga <= 0) return null
    return FakturItemHasil(nama = nama, qty = qty, hargaSatuan = harga)
}

/** Samakan nama obat hasil OCR dengan nama di Stok: abaikan huruf besar,
 *  tanda baca, dan spasi di antara angka dan satuan ("500 mg" = "500mg"). */
fun normalisasiNama(s: String): String {
    val tanpaSatuan = s.lowercase()
        .replace(Regex("""(\d)\s+(mg|ml|g|mcg|iu|tab|tablet|kapsul|cap)\b""")) {
            it.groupValues[1] + it.groupValues[2]
        }
    return tanpaSatuan.replace(Regex("[^a-z0-9 ]"), " ")
        .split(" ").filter { it.isNotEmpty() }.joinToString(" ")
}

fun cocokkanNama(nama: String, kandidat: List<String>): String? {
    val n = normalisasiNama(nama)
    if (n.isEmpty()) return null
    kandidat.firstOrNull { normalisasiNama(it) == n }?.let { return it }
    val cocok = kandidat.filter { k ->
        val kn = normalisasiNama(k)
        kn.contains(n) || n.contains(kn)
    }
    if (cocok.isEmpty()) return null
    return cocok.minByOrNull { kotlin.math.abs(normalisasiNama(it).length - n.length) }
}

/** Tebak isi faktur dari baris-baris teks hasil OCR (urutan baca atas-bawah). */
fun parseFaktur(baris: List<String>): FakturHasil {
    val bersih = baris.map { it.trim() }.filter { it.isNotEmpty() }
    var invoiceNo = ""
    var tanggal = ""
    var ppn = 0
    val items = mutableListOf<FakturItemHasil>()
    val noRegex = Regex("""(?i)\b(?:no\.?|nomor|faktur|invoice|inv)\b[^A-Za-z0-9]{0,3}([A-Za-z0-9][A-Za-z0-9\-/]{2,})""")
    for (line in bersih) {
        val low = line.lowercase()
        if (invoiceNo.isEmpty()) {
            noRegex.find(line)?.let { invoiceNo = it.groupValues[1].trimEnd('.', ',', ';', ')') }
        }
        val t = parseTanggal(line)
        if (t != null) {
            if (tanggal.isEmpty()) tanggal = t
            continue
        }
        if (low.contains("ppn") || low.contains("vat")) {
            if (ppn == 0) {
                angkaUang(line).filter { it >= 100 }.maxOrNull()?.let { ppn = it }
            }
            continue
        }
        if (KATA_LEWAT.any { low.contains(it) }) continue
        parseBarisItem(line)?.let { items.add(it) }
    }
    // Nama PBF: baris paling atas yang tampak seperti nama perusahaan.
    val namaPbf = bersih.firstOrNull { l ->
        val low = l.lowercase()
        l.length >= 3 && l.any { it.isLetter() } && angkaUang(l).isEmpty() &&
            !low.contains("faktur") && !low.contains("invoice") &&
            !low.contains("tanggal") && !low.contains("total") &&
            !low.contains("ppn") && !low.contains("nomor") &&
            !low.startsWith("no ") && !low.startsWith("no.") &&
            !low.contains("kepada") && !low.contains("alamat") &&
            !low.contains("telp") && !low.contains("npwp")
    } ?: ""
    return FakturHasil(pbfName = namaPbf, invoiceNo = invoiceNo, tanggal = tanggal, items = items, ppn = ppn)
}

/** Baca satu foto faktur (kamera/galeri) lewat ML Kit, kembalikan hasil parse.
 *  Model Latin ikut di APK (bundled) — tanpa internet, tanpa biaya. */
suspend fun bacaFakturDariUri(context: Context, uri: Uri): FakturHasil {
    val image = InputImage.fromFilePath(context, uri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val result = recognizer.process(image).await()
        val baris = result.textBlocks
            .flatMap { it.lines }
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .map { it.text }
        return parseFaktur(baris)
    } finally {
        recognizer.close()
    }
}
