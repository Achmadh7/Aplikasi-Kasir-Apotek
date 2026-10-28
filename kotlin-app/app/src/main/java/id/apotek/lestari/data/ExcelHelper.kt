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
import java.util.Locale

/**
 * Import / Export / Template Excel (.xlsx) untuk Stok Obat.
 * Formatnya sama dengan template web app (Aplikasi Apotik Lestari), jadi satu
 * file bisa dipakai di keduanya.
 *
 * Kolom: Nama Obat | Barcode | Kategori | Satuan | Harga Beli | Harga Jual |
 * Stok | Stok Minimum | Tanggal Kadaluarsa (YYYY-MM-DD)
 */
object ExcelHelper {
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SHEET_STOK = "Stok Obat"
    const val SHEET_PETUNJUK = "Petunjuk"

    val HEADERS = arrayOf(
        "Nama Obat", "Barcode", "Kategori", "Satuan",
        "Harga Beli", "Harga Jual", "Stok", "Stok Minimum", "Tanggal Kadaluarsa"
    )

    private val TEMPLATE_EXAMPLES = listOf(
        arrayOf<Any>("Paracetamol 500mg", "8991234567890", "Pereda Nyeri", "Strip", 8000, 12000, 50, 10, "2027-12-31"),
        arrayOf<Any>("Amoxicillin 500mg", "8991234567891", "Antibiotik", "Strip", 15000, 22000, 30, 10, "2026-11-30"),
        arrayOf<Any>("Vitamin C 1000mg", "8991234567892", "Vitamin", "Botol", 35000, 50000, 20, 5, "2028-06-30"),
    )

    private val PETUNJUK = listOf(
        "Template Import Stok Obat — Petunjuk",
        "",
        "1. Isi data obat di sheet 'Stok Obat', satu obat per baris.",
        "2. Template ini sudah berisi stok obat saat ini (atau 3 baris contoh jika stok masih kosong).",
        "   Edit, tambah, atau hapus baris sesuai kebutuhan, lalu simpan sebelum import.",
        "3. Kolom wajib: Nama Obat. Kolom lain boleh kosong (akan diisi nilai bawaan).",
        "   - Barcode: kode barcode pada kemasan obat (boleh kosong). Barcode yang sama tidak boleh dipakai dua obat berbeda.",
        "   - Kategori: Umum, Antibiotik, Vitamin, Pereda Nyeri, Obat Batuk & Flu, Pencernaan, Kulit, Mata, Lainnya",
        "   - Satuan: Strip, Botol, Box, Tablet, Kapsul, Tube, Sachet, Pcs",
        "   - Harga Beli & Harga Jual: angka saja, contoh 12000 (tanpa Rp / titik)",
        "   - Stok & Stok Minimum: angka bulat",
        "   - Tanggal Kadaluarsa: format YYYY-MM-DD, contoh 2027-12-31 (boleh kosong)",
        "4. Simpan file, lalu di aplikasi buka Stok > Import Excel dan pilih file ini.",
        "5. Jika Barcode sudah ada di stok, data obat itu diperbarui; jika belum ada, ditambahkan sebagai obat baru.",
    )

    /** Template berisi stok saat ini (supaya tinggal diedit lalu di-import lagi untuk
     *  sinkron), atau 3 baris contoh jika stok masih kosong. Dilengkapi sheet Petunjuk. */
    fun templateBytes(medicines: List<Medicine>): ByteArray {
        val wb = XSSFWorkbook()
        val rows = if (medicines.isEmpty()) TEMPLATE_EXAMPLES else medicines.map { m ->
            arrayOf<Any>(
                m.name, m.barcode ?: "", m.category, m.unit,
                m.buyPrice, m.sellPrice, m.stock, m.minStock, m.expiryDate ?: ""
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
                m.name, m.barcode ?: "", m.category, m.unit,
                m.buyPrice, m.sellPrice, m.stock, m.minStock, m.expiryDate ?: ""
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
                fun idx(name: String, fallback: Int) = colIndex[name.lowercase()] ?: fallback
                fun text(row: Row, name: String, fallback: Int): String {
                    val cell = row.getCell(idx(name, fallback)) ?: return ""
                    return formatter.formatCellValue(cell).trim()
                }
                for (row in sheet) {
                    if (row.rowNum == 0) continue
                    val name = text(row, "Nama Obat", 0)
                    if (name.isBlank()) {
                        val anyValue = (0..8).any { text(row, HEADERS[it], it).isNotEmpty() }
                        if (anyValue) skipped.add(row.rowNum + 1)
                        continue
                    }
                    val barcode = text(row, "Barcode", 1).ifBlank { null }
                    val category = text(row, "Kategori", 2).ifBlank { "Umum" }
                    val unit = text(row, "Satuan", 3).ifBlank { "Strip" }
                    val buy = text(row, "Harga Beli", 4).toIntOrZero()
                    val sell = text(row, "Harga Jual", 5).toIntOrZero()
                    val stock = text(row, "Stok", 6).toIntOrZero()
                    val minStock = text(row, "Stok Minimum", 7).toIntOrZero().let { if (it <= 0) 10 else it }
                    val expiry = readExpiry(row, idx("Tanggal Kadaluarsa", 8), formatter)
                    items.add(MedicineArgs(name, category, unit, buy, sell, stock, minStock, expiry, barcode))
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
        intArrayOf(30, 18, 18, 10, 13, 13, 8, 14, 18).forEachIndexed { i, w ->
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

    private fun String.toIntOrZero(): Int = filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun readExpiry(row: Row, colIdx: Int, formatter: DataFormatter): String? {
        val cell = row.getCell(colIdx) ?: return null
        if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cell.dateCellValue)
        }
        return normalizeDate(formatter.formatCellValue(cell))
    }

    private fun normalizeDate(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return s
        Regex("(\\d{2})/(\\d{2})/(\\d{4})").matchEntire(s)?.let {
            return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}"
        }
        Regex("(\\d{2})-(\\d{2})-(\\d{4})").matchEntire(s)?.let {
            return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}"
        }
        return null
    }
}
