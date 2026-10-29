package id.apotek.lestari.data

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.TimeZone

/**
 * Import / Export / Template Excel (.xlsx) untuk Stok Obat.
 * Formatnya sama dengan template web app (Aplikasi Apotik Lestari), jadi satu
 * file bisa dipakai di keduanya.
 *
 * Kolom: Nama Obat | Kategori | Satuan | Harga Beli | Harga Jual |
 * Harga Jual Grosir | Stok | Tanggal Kadaluarsa (YYYY-MM-DD)
 */
object ExcelHelper {
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SHEET_STOK = "Stok Obat"
    const val SHEET_PETUNJUK = "Petunjuk"

    val HEADERS = arrayOf(
        "Nama Obat", "Kategori", "Satuan",
        "Harga Beli", "Harga Jual", "Harga Jual Grosir", "Stok", "Tanggal Kadaluarsa"
    )

    private val TEMPLATE_EXAMPLES = listOf(
        arrayOf<Any>("Paracetamol 500mg", "Pereda Nyeri", "Strip", 8000, 12000, 10500, 50, "2027-12-31"),
        arrayOf<Any>("Amoxicillin 500mg", "Antibiotik", "Strip", 15000, 22000, 19000, 30, "2026-11-30"),
        arrayOf<Any>("Vitamin C 1000mg", "Vitamin", "Botol", 35000, 50000, 42000, 20, "2028-06-30"),
    )

    private val PETUNJUK = listOf(
        "Template Import Stok Obat — Petunjuk",
        "",
        "1. Isi data obat di sheet 'Stok Obat', satu obat per baris.",
        "2. Template ini sudah berisi stok obat saat ini (atau 3 baris contoh jika stok masih kosong).",
        "   Edit, tambah, atau hapus baris sesuai kebutuhan, lalu simpan sebelum import.",
        "3. Kolom wajib: Nama Obat. Kolom lain boleh kosong (akan diisi nilai bawaan).",
        "   - Kategori: Umum, Obat Bebas, Obat Bebas Terbatas, Obat Keras, Antibiotik, Vitamin, Pereda Nyeri, Obat Batuk & Flu, Pencernaan, Kulit, Mata, Lainnya",
        "   - Satuan: Strip, Botol, Vial, Ampul, Box, Tablet, Kapsul, Tube, Drop, Sachet, Pcs",
        "   - Harga Beli & Harga Jual: angka saja, contoh 12000 (tanpa Rp / titik)",
        "   - Harga Jual Grosir: harga khusus pembelian banyak (boleh kosong = tidak ada harga grosir)",
        "   - Stok: angka bulat",
        "   - Tanggal Kadaluarsa: format YYYY-MM-DD, contoh 2027-12-31 (boleh kosong)",
        "4. Simpan file, lalu di aplikasi buka Stok > Import Excel dan pilih file ini.",
        "5. Jika obat dengan Nama + Kategori + Satuan yang sama sudah ada di stok, data obat itu diperbarui; jika belum ada, ditambahkan sebagai obat baru.",
    )

    /** Template berisi stok saat ini (supaya tinggal diedit lalu di-import lagi untuk
     *  sinkron), atau 3 baris contoh jika stok masih kosong. Dilengkapi sheet Petunjuk. */
    fun templateBytes(medicines: List<Medicine>): ByteArray {
        val wb = XSSFWorkbook()
        val rows = if (medicines.isEmpty()) TEMPLATE_EXAMPLES else medicines.map { m ->
            arrayOf<Any>(
                m.name, m.category, m.unit,
                m.buyPrice, m.sellPrice, m.wholesalePrice, m.stock, m.expiryDate ?: ""
            )
        }
        buildStockSheet(wb, rows)
        val info = wb.createSheet(SHEET_PETUNJUK)
        PETUNJUK.forEachIndexed { i, line -> info.createRow(i).createCell(0).setCellValue(line) }
        info.setColumnWidth(0, 110 * 256)
        return wbBytes(wb)
    }

    /** Export seluruh stok saat ini ke .xlsx (satu sheet, tanpa Petunjuk). */
    fun exportBytes(medicines: List<Medicine>): ByteArray {
        val wb = XSSFWorkbook()
        val rows = medicines.map { m ->
            arrayOf<Any>(
                m.name, m.category, m.unit,
                m.buyPrice, m.sellPrice, m.wholesalePrice, m.stock, m.expiryDate ?: ""
            )
        }
        buildStockSheet(wb, rows)
        return wbBytes(wb)
    }

    data class ParsedImport(val items: List<MedicineArgs>, val skippedRows: List<Int>)

    /**
     * Baca file .xlsx hasil pilihan pengguna. Kolom dikenali dari baris header,
     * jadi urutan kolom boleh beda. Baris tanpa Nama Obat dilewati (dicatat
     * nomor barisnya); baris kosong seluruhnya diabaikan diam-diam.
     * Baris dengan angka rusak atau tanggal kadaluarsa tidak valid juga
     * dilewati dan dicatat.
     */
    fun parseImport(bytes: ByteArray): ParsedImport {
        val items = mutableListOf<MedicineArgs>()
        val skipped = mutableListOf<Int>()
        ByteArrayInputStream(bytes).use { input ->
            val wb = WorkbookFactory.create(input)
            try {
                val sheet = wb.getSheet(SHEET_STOK) ?: wb.getSheetAt(0)
                val formatter = DataFormatter()
                val colIndex = mutableMapOf<String, Int>()
                sheet.getRow(0)?.forEach { cell ->
                    val h = formatter.formatCellValue(cell).trim()
                    if (h.isNotEmpty()) colIndex[h.lowercase()] = cell.columnIndex
                }
                // C11: file harus template Stok Obat — kolom "Nama Obat" wajib ada.
                if (!colIndex.containsKey("nama obat")) {
                    throw IllegalArgumentException(
                        "File bukan template Stok Obat: kolom 'Nama Obat' tidak ditemukan"
                    )
                }
                fun idx(name: String, fallback: Int) = colIndex[name.lowercase()] ?: fallback
                fun text(row: Row, name: String, fallback: Int): String {
                    val cell = row.getCell(idx(name, fallback)) ?: return ""
                    return formatter.formatCellValue(cell).trim()
                }
                for (row in sheet) {
                    if (row.rowNum == 0) continue
                    val name = text(row, "Nama Obat", 0)
                    if (name.isBlank()) {
                        val anyValue = (0..7).any { text(row, HEADERS[it], it).isNotEmpty() }
                        if (anyValue) skipped.add(row.rowNum + 1)
                        continue
                    }
                    // Kolom Barcode sudah tidak dipakai (dihapus dari template
                    // 2026-10-29); file lama yang masih punya kolom itu tetap
                    // bisa diimpor — kolomnya diabaikan dan obat dicocokkan
                    // lewat Nama + Kategori + Satuan di Repository.bulkImport.
                    val category = text(row, "Kategori", 1).ifBlank { "Umum" }
                    val unit = text(row, "Satuan", 2).ifBlank { "Strip" }
                    // A7: angka rusak (null dari parser) jangan diam-diam jadi 0 —
                    // baris dilewati dan dicatat di skippedRows.
                    val buy = text(row, "Harga Beli", 3).toIntOrNullStrict()
                    val sell = text(row, "Harga Jual", 4).toIntOrNullStrict()
                    val stock = text(row, "Stok", 6).toIntOrNullStrict()
                    if (buy == null || sell == null || stock == null) {
                        skipped.add(row.rowNum + 1)
                        continue
                    }
                    // Harga grosir OPSIONAL: kosong / isinya rusak -> 0
                    // (belum diatur), baris tetap diterima. Kolom ini dibaca
                    // HANYA bila header-nya benar-benar ada di file — jangan
                    // pakai fallback posisi: di template lama, posisi 5 itu
                    // Harga Jual (layout era barcode) atau Stok (layout 7
                    // kolom), jadi file lama akan mengisi harga grosir palsu
                    // (bug N1, audit 2026-10-30).
                    val grosir = if (colIndex.containsKey("harga jual grosir")) {
                        text(row, "Harga Jual Grosir", 5).toIntOrNullStrict() ?: 0
                    } else {
                        0
                    }
                    // C7: tanggal terisi tapi tidak bisa diparse -> baris dilewati;
                    // tanggal kosong -> null (boleh).
                    val expiryRaw = text(row, "Tanggal Kadaluarsa", 7)
                    val expiry = if (expiryRaw.isBlank()) {
                        null
                    } else {
                        readExpiry(row, idx("Tanggal Kadaluarsa", 7), formatter)
                    }
                    if (expiryRaw.isNotBlank() && expiry == null) {
                        skipped.add(row.rowNum + 1)
                        continue
                    }
                    items.add(MedicineArgs(name, category, unit, buy, sell, stock, expiry, wholesalePrice = grosir))
                }
            } finally {
                wb.close()
            }
        }
        return ParsedImport(items, skipped)
    }

    private fun buildStockSheet(wb: XSSFWorkbook, rows: List<Array<Any>>) {
        val sheet = wb.createSheet(SHEET_STOK)
        // Header: tebal + warna hitam eksplisit + latar biru muda, supaya judul kolom
        // jelas terlihat di semua penampil Excel (beberapa penampil menampilkan
        // font tanpa warna sebagai teks tak terlihat di atas latar putih).
        val headerStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply {
                bold = true
                color = IndexedColors.BLACK.index
            })
            fillForegroundColor = IndexedColors.PALE_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val header = sheet.createRow(0)
        HEADERS.forEachIndexed { i, h ->
            header.createCell(i).apply { setCellValue(h); cellStyle = headerStyle }
        }
        rows.forEachIndexed { r, values ->
            val row = sheet.createRow(r + 1)
            values.forEachIndexed { c, v ->
                val cell = row.createCell(c)
                when (v) {
                    is Int -> cell.setCellValue(v.toDouble())
                    is Long -> cell.setCellValue(v.toDouble())
                    else -> cell.setCellValue(v.toString())
                }
            }
        }
        intArrayOf(30, 18, 10, 13, 13, 15, 8, 18).forEachIndexed { i, w ->
            sheet.setColumnWidth(i, w * 256)
        }
    }

    private fun wbBytes(wb: XSSFWorkbook): ByteArray {
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        val bytes = out.toByteArray()
        // Jangan diam-diam menghasilkan file kosong — kalau POI tidak menulis
        // apa-apa (mis. class POI terbuang R8), lempar supaya pesannya jelas.
        require(bytes.isNotEmpty()) { "File Excel kosong (POI tidak menulis apa-apa)" }
        return bytes
    }

    /**
     * A7: parser angka Indonesia yang ketat. Terima pemisah ribuan `.` dan
     * desimal `,` (mis. "12.000,50" -> 12000.50). Kembalikan null (bukan 0)
     * bila: string kosong, ada huruf / karakter lain, negatif, atau hasilnya
     * bukan bilangan bulat (mis. "12.5"). Pemanggil memperlakukan null
     * sebagai "baris bermasalah" (masuk skippedRows).
     */
    private fun String.toIntOrNullStrict(): Int? {
        val s = replace(" ", "").trim()
        if (s.isEmpty()) return null
        if (s.startsWith("-")) return null
        if (s.any { !it.isDigit() && it != '.' && it != ',' }) return null
        val normalized = when {
            s.contains(',') && s.contains('.') -> s.replace(".", "").replace(',', '.')
            s.contains(',') -> s.replace(',', '.')
            s.contains('.') && s.matches(Regex("\\d{1,3}(\\.\\d{3})+")) -> s.replace(".", "")
            else -> s
        }
        val value = normalized.toDoubleOrNull() ?: return null
        if (value < 0 || value % 1.0 != 0.0) return null
        if (value > Int.MAX_VALUE.toDouble()) return null
        return value.toInt()
    }

    private fun readExpiry(row: Row, colIdx: Int, formatter: DataFormatter): String? {
        val cell = row.getCell(colIdx) ?: return null
        if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            // C7: pakai zona Asia/Jakarta (bukan default HP) supaya tanggal
            // tidak geser 1 hari.
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Jakarta")
            }.format(cell.dateCellValue)
        }
        return normalizeDate(formatter.formatCellValue(cell))
    }

    /**
     * C7: normalisasi tanggal dengan validasi KALENDER yang ketat
     * (LocalDate STRICT — tolak "2027-13-45", "2027-02-30").
     * Format yang diterima: "yyyy-MM-dd", "DD/MM/YYYY" (didahulukan, format
     * Indonesia), "YYYY/MM/DD", "DD-MM-YYYY". Format lain -> null.
     */
    private fun normalizeDate(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            return try {
                LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)).toString()
            } catch (e: DateTimeException) {
                null
            }
        }
        Regex("(\\d{2})/(\\d{2})/(\\d{4})").matchEntire(s)?.let {
            // DD/MM/YYYY (Indonesia); bila hari > 12 maka pasti DD/MM,
            // bila keduanya <= 12 tetap dianggap DD/MM.
            return strictDate(it.groupValues[3], it.groupValues[2], it.groupValues[1])
        }
        Regex("(\\d{4})/(\\d{2})/(\\d{2})").matchEntire(s)?.let {
            return strictDate(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        Regex("(\\d{2})-(\\d{2})-(\\d{4})").matchEntire(s)?.let {
            return strictDate(it.groupValues[3], it.groupValues[2], it.groupValues[1])
        }
        return null
    }

    /** Validasi kalender ketat via LocalDate; kembalikan "yyyy-MM-dd" atau null. */
    private fun strictDate(year: String, month: String, day: String): String? {
        return try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toString()
        } catch (e: DateTimeException) {
            null
        }
    }
}
