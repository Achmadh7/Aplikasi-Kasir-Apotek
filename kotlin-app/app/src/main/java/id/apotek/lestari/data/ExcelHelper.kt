package id.apotek.lestari.data

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.PrintOrientation
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.CellRangeAddressList
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFColor
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
 * Kolom: Nama Obat | Kategori | Satuan | Harga Jual |
 * Harga Jual Grosir | Harga Beli | Stok | Tanggal Kadaluarsa (YYYY-MM-DD)
 * (Harga Beli kembali 2026-11-15 untuk HPP; sejak 2026-11-15 posisinya
 * di samping Harga Jual Grosir. Export menambah kolom Barcode paling
 * ujung — R6, audit 2026-11-15. File lama tanpa kolom Harga Beli tetap
 * bisa diimpor — Harga Beli dibaca 0.)
 * Format (X1+X2+X3, 2026-11-14): lebar kolom diperbaiki (Kadaluarsa 18,
 * bukan 8), baris judul dibekukan + filter otomatis, Kategori & Satuan
 * punya dropdown validasi, angka tampil dengan pemisah ribuan (12.000)
 * dan Kadaluarsa ditulis sebagai tanggal Excel asli (bukan teks).
 * Import tetap membaca dari nama header (urutan bebas) dan menerima file
 * lama apa adanya.
 */
object ExcelHelper {
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val SHEET_STOK = "Stok Obat"
    const val SHEET_PETUNJUK = "Petunjuk"
    const val SHEET_REKAP_HARIAN = "Rekap Harian"
    const val SHEET_REKAP_OBAT = "Per Obat"

    val HEADERS = arrayOf(
        "Nama Obat", "Kategori", "Satuan",
        "Harga Jual", "Harga Jual Grosir", "Harga Beli", "Stok", "Tanggal Kadaluarsa"
    )

    // Daftar kanonis — SAMA dengan CATEGORIES/UNITS di Screens.kt (dipakai
    // dropdown validasi X2 dan teks Petunjuk, supaya ketiganya tidak melenceng).
    val KATEGORI_LIST = arrayOf(
        "Umum", "Obat Bebas", "Obat Bebas Terbatas", "Obat Keras", "Antibiotik",
        "Vitamin", "Pereda Nyeri", "Obat Batuk & Flu", "Pencernaan", "Kulit",
        "Mata", "Lainnya"
    )
    val SATUAN_LIST = arrayOf(
        "Strip", "Botol", "Vial", "Ampul", "Box", "Tablet", "Kapsul", "Tube",
        "Drop", "Sachet", "Pcs"
    )

    private val TEMPLATE_EXAMPLES = listOf(
        arrayOf<Any>("Paracetamol 500mg", "Pereda Nyeri", "Strip", 12000, 10500, 8000, 50, "2027-12-31"),
        arrayOf<Any>("Amoxicillin 500mg", "Antibiotik", "Strip", 22000, 19000, 15000, 30, "2026-11-30"),
        arrayOf<Any>("Vitamin C 1000mg", "Vitamin", "Botol", 50000, 42000, 35000, 20, "2028-06-30"),
    )

    private val PETUNJUK = listOf(
        "Template Import Stok Obat — Petunjuk",
        "",
        "1. Isi data obat di sheet 'Stok Obat', satu obat per baris.",
        "2. Template ini sudah berisi stok obat saat ini (atau 3 baris contoh jika stok masih kosong).",
        "   Edit, tambah, atau hapus baris sesuai kebutuhan, lalu simpan sebelum import.",
        "3. Kolom wajib: Nama Obat. Kolom lain boleh kosong (akan diisi nilai bawaan).",
        "   - Kategori: " + KATEGORI_LIST.joinToString(", "),
        "   - Satuan: " + SATUAN_LIST.joinToString(", "),
        "   - Harga Jual: angka saja, contoh 12000 atau 12.000 (boleh pakai titik ribuan)",
        "   - Harga Jual Grosir: harga khusus pembelian banyak (boleh kosong = tidak ada harga grosir)",
        "   - Harga Beli: harga modal per satuan, dipakai menghitung HPP dan Laba Kotor (boleh kosong = 0)",
        "   - Stok: angka bulat",
        "   - Tanggal Kadaluarsa: format YYYY-MM-DD, contoh 2027-12-31 (boleh kosong)",
        "   - Kategori dan Satuan punya daftar pilihan — ketuk panah di sel untuk memilih; nilai di luar daftar ditolak.",
        "   - Baris judul kolom dibekukan (tetap terlihat saat menggulir) dan bisa difilter.",
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
                m.sellPrice, m.wholesalePrice, m.buyPrice, m.stock, m.expiryDate ?: ""
            )
        }
        buildStockSheet(wb, rows)
        val info = wb.createSheet(SHEET_PETUNJUK)
        PETUNJUK.forEachIndexed { i, line -> info.createRow(i).createCell(0).setCellValue(line) }
        info.setColumnWidth(0, 110 * 256)
        return wbBytes(wb)
    }

    /** Export seluruh stok saat ini ke .xlsx (satu sheet, tanpa Petunjuk).
     *  R6 (audit 2026-11-15): kolom "Barcode" ditambahkan paling ujung.
     *  "Harga Beli" (HPP, 2026-11-15) di samping Harga Jual Grosir
     *  (indeks 5); tanggal kadaluarsa di indeks 7. */
    fun exportBytes(medicines: List<Medicine>): ByteArray {
        val wb = XSSFWorkbook()
        val rows = medicines.map { m ->
            arrayOf<Any>(
                m.name, m.category, m.unit,
                m.sellPrice, m.wholesalePrice, m.buyPrice, m.stock, m.expiryDate ?: "", m.barcode ?: ""
            )
        }
        // "Harga Beli" di samping Harga Jual Grosir (indeks 5);
        // "Barcode" paling ujung; tanggal kadaluarsa di indeks 7.
        buildStockSheet(wb, rows, HEADERS + "Barcode", intArrayOf(32, 20, 10, 14, 16, 14, 10, 18, 20))
        return wbBytes(wb)
    }

    /** Rekap penjualan (J5) ke .xlsx: sheet "Rekap Harian" (satu baris per
     *  hari berisi penjualan + HPP + Laba Kotor + baris TOTAL) dan sheet
     *  "Per Obat" (satu baris per obat + TOTAL). Laba Kotor = Total Penjualan
     *  - HPP. Format mengikuti template Stok (X1+X2+X3): judul dibekukan +
     *  filter otomatis, angka #,##0, tanggal sel Excel asli. Gaya EX2 Steel
     *  Biru (2026-11-17, pilihan Achmad): header biru baja + teks putih,
     *  baris selang-seling abu muda, TOTAL biru muda + teks navy + garis
     *  atas, judul navy, cetak landscape + judul diulang tiap halaman. */
    fun rekapPenjualanBytes(rekap: RekapPenjualan, judulPeriode: String): ByteArray {
        val wb = XSSFWorkbook()
        buildRekapSheet(
            wb, SHEET_REKAP_HARIAN, "Rekap Penjualan \u2014 $judulPeriode",
            arrayOf("Tanggal", "Transaksi", "Item Terjual", "Total Penjualan (Rp)", "HPP (Rp)", "Laba Kotor (Rp)"),
            intArrayOf(18, 12, 14, 22, 22, 22),
            rekap.daily.map { listOf<Any>(it.date, it.transactions, it.itemsSold, it.revenue, it.hpp, it.revenue - it.hpp) },
            dateCol = 0,
            totalRow = listOf<Any>("TOTAL", rekap.totalTransactions, rekap.totalItemsSold, rekap.totalRevenue, rekap.totalHpp, rekap.totalRevenue - rekap.totalHpp)
        )
        buildRekapSheet(
            wb, SHEET_REKAP_OBAT, "Per Obat \u2014 $judulPeriode",
            arrayOf("Nama Obat", "Kategori", "Item Terjual", "Total Penjualan (Rp)", "HPP (Rp)", "Laba Kotor (Rp)"),
            intArrayOf(32, 20, 14, 22, 22, 22),
            rekap.byMedicine.map { listOf<Any>(it.name, it.category, it.qty, it.revenue, it.hpp, it.revenue - it.hpp) },
            dateCol = -1,
            // S8: TOTAL per obat diambil dari total rekap (Long+jepit),
            // bukan jumlah baris — supaya selalu cocok dengan TOTAL
            // sheet Rekap Harian.
            totalRow = listOf<Any>("TOTAL", "", rekap.byMedicine.sumOf { it.qty }, rekap.totalRevenue, rekap.totalHpp, rekap.totalRevenue - rekap.totalHpp)
        )
        return wbBytes(wb)
    }

    /** Satu sheet rekap: baris 0 judul (digabung), baris 1 header, lalu data
     *  dan baris TOTAL di akhir. Kolom tanggal ditulis sebagai sel
     *  tanggal Excel asli (seperti X3), bukan teks. Gaya EX2 Steel Biru:
     *  header biru baja + teks putih, baris data selang-seling abu muda,
     *  TOTAL biru muda + teks navy + garis atas tebal, judul navy. */
    private fun buildRekapSheet(
        wb: XSSFWorkbook,
        sheetName: String,
        title: String,
        headers: Array<String>,
        widths: IntArray,
        rows: List<List<Any>>,
        dateCol: Int,
        totalRow: List<Any>
    ) {
        val sheet = wb.createSheet(sheetName)
        // EX2 (2026-11-17, pilihan Achmad): gaya "Steel Biru" — judul navy,
        // header biru baja + teks putih, baris selang-seling abu muda, TOTAL
        // biru muda + teks navy + garis atas. Warna custom WAJIB lewat
        // setter eksplisit (setFillForegroundColor/setColor) — assignment
        // properti mengikat overload Short dan gagal kompilasi.
        // am215: java.awt.Color TIDAK ADA di Android (crash ekspor
        // di HP) — bangun XSSFColor dari byte[] RGB saja, konstruktor
        // ini terverifikasi ada di POI 5.5.1.
        fun xwarna(r: Int, g: Int, b: Int) =
            XSSFColor(byteArrayOf(r.toByte(), g.toByte(), b.toByte()), null)
        val biruBaja = xwarna(46, 117, 182)
        val biruMuda = xwarna(217, 226, 243)
        val navyTeks = xwarna(31, 56, 100)
        val abuBaris = xwarna(242, 242, 242)
        val titleStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply {
                bold = true
                fontHeightInPoints = 14
                setColor(navyTeks)
            })
        }
        val titleRow = sheet.createRow(0)
        titleRow.createCell(0).apply { setCellValue(title); cellStyle = titleStyle }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, headers.size - 1))
        val headerStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            })
            setFillForegroundColor(biruBaja)
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val header = sheet.createRow(1)
        headers.forEachIndexed { i, h ->
            header.createCell(i).apply { setCellValue(h); cellStyle = headerStyle }
        }
        val numberStyle = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0")
        }
        val numberStyleBand = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0")
            setFillForegroundColor(abuBaris)
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val boldNumberStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply { bold = true; setColor(navyTeks) })
            dataFormat = wb.createDataFormat().getFormat("#,##0")
            setFillForegroundColor(biruMuda)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderTop = BorderStyle.MEDIUM
            setTopBorderColor(navyTeks)
        }
        val boldStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply { bold = true; setColor(navyTeks) })
            setFillForegroundColor(biruMuda)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderTop = BorderStyle.MEDIUM
            setTopBorderColor(navyTeks)
        }
        val dateStyle = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("yyyy-mm-dd")
        }
        val dateStyleBand = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("yyyy-mm-dd")
            setFillForegroundColor(abuBaris)
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val textStyleBand = wb.createCellStyle().apply {
            setFillForegroundColor(abuBaris)
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        fun writeRow(rowIdx: Int, values: List<Any>, bold: Boolean, banded: Boolean = false) {
            val row = sheet.createRow(rowIdx)
            values.forEachIndexed { c, v ->
                val cell = row.createCell(c)
                when (v) {
                    is Int -> {
                        cell.setCellValue(v.toDouble())
                        cell.cellStyle = if (bold) boldNumberStyle else if (banded) numberStyleBand else numberStyle
                    }
                    is Long -> {
                        cell.setCellValue(v.toDouble())
                        cell.cellStyle = if (bold) boldNumberStyle else if (banded) numberStyleBand else numberStyle
                    }
                    else -> {
                        val s = v.toString()
                        if (c == dateCol) {
                            val parsed = runCatching { LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                            if (parsed != null) {
                                cell.setCellValue(DateUtil.getExcelDate(parsed))
                                cell.cellStyle = if (banded) dateStyleBand else dateStyle
                            } else {
                                // R23 (audit 2026-11-18): label TOTAL juga
                                // bergaya tebal seperti angka di baris sama.
                                cell.setCellValue(s)
                                if (bold) cell.cellStyle = boldStyle
                            }
                        } else {
                            cell.setCellValue(s)
                            if (bold) cell.cellStyle = boldStyle
                            else if (banded) cell.cellStyle = textStyleBand
                        }
                    }
                }
            }
        }
        rows.forEachIndexed { i, values -> writeRow(i + 2, values, false, i % 2 == 1) }
        writeRow(rows.size + 2, totalRow, true)
        widths.forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }
        sheet.createFreezePane(0, 2)
        // R26 (audit 2026-11-18): filter hanya sampai baris data terakhir —
        // baris TOTAL tidak ikut terfilter.
        sheet.setAutoFilter(CellRangeAddress(1, rows.size + 1, 0, headers.size - 1))
        // EX2: setelan cetak — landscape, muat 1 halaman lebar, judul +
        // header diulang di tiap halaman cetak. (XSSFPrintSetup memakai
        // nama fitWidth/fitHeight — BUKAN fitToWidth/fitToHeight.)
        sheet.printSetup.orientation = PrintOrientation.LANDSCAPE
        sheet.setFitToPage(true)
        sheet.printSetup.fitWidth = 1
        sheet.printSetup.fitHeight = 0
        sheet.setRepeatingRows(CellRangeAddress(0, 1, 0, headers.size - 1))
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
                // S7 (audit 2026-11-14): paksa locale Indonesia — DataFormatter
                // default (locale HP/CI) bisa merender angka 12000 berformat
                // #,##0 sebagai "12,000" yang lalu diparse jadi 12.
                val formatter = DataFormatter(Locale("id", "ID"))
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
                        val anyValue = HEADERS.indices.any { text(row, HEADERS[it], it).isNotEmpty() }
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
                    // baris dilewati dan dicatat di skippedRows. R24: sel KOSONG
                    // berarti 0 (sesuai Petunjuk); hanya isi yang tidak bisa
                    // di-parse yang membuat baris dilewati.
                    val sellText = text(row, "Harga Jual", 3)
                    val stockText = text(row, "Stok", 6)
                    val sell = if (sellText.isBlank()) 0 else sellText.toIntOrNullStrict()
                    val stock = if (stockText.isBlank()) 0 else stockText.toIntOrNullStrict()
                    if (sell == null || stock == null) {
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
                        text(row, "Harga Jual Grosir", 4).toIntOrNullStrict() ?: 0
                    } else {
                        0
                    }
                    // Harga Beli OPSIONAL seperti grosir: dibaca HANYA bila
                    // header-nya ada (pola yang sama — jangan pakai fallback
                    // posisi, supaya file lama tidak terisi nilai palsu).
                    // Kosong / rusak -> 0 (belum diatur).
                    val beli = if (colIndex.containsKey("harga beli")) {
                        text(row, "Harga Beli", 5).toIntOrNullStrict() ?: 0
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
                    items.add(MedicineArgs(name, category, unit, sell, stock, expiry, wholesalePrice = grosir, buyPrice = beli))
                }
            } finally {
                wb.close()
            }
        }
        return ParsedImport(items, skipped)
    }

    // R6: header + lebar kolom bisa ditimpa pemanggil (export menambah
    // kolom Barcode di ujung); template memakai default 8 kolom.
    private fun buildStockSheet(
        wb: XSSFWorkbook,
        rows: List<Array<Any>>,
        headers: Array<String> = HEADERS,
        widths: IntArray = intArrayOf(32, 20, 10, 14, 16, 14, 10, 18)
    ) {
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
        headers.forEachIndexed { i, h ->
            header.createCell(i).apply { setCellValue(h); cellStyle = headerStyle }
        }
        // X3: angka (Harga Jual, Grosir, Stok) tampil dengan pemisah ribuan
        // (12.000) dan Kadaluarsa ditulis sebagai tanggal Excel asli — bukan
        // teks — supaya bisa diurutkan/difilter di Excel. Parser sudah menerima
        // sel tanggal Excel (readExpiry) dan angka berformat Indonesia.
        val numberStyle = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0")
        }
        val dateStyle = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("yyyy-mm-dd")
        }
        rows.forEachIndexed { r, values ->
            val row = sheet.createRow(r + 1)
            values.forEachIndexed { c, v ->
                val cell = row.createCell(c)
                when (v) {
                    is Int -> { cell.setCellValue(v.toDouble()); cell.cellStyle = numberStyle }
                    is Long -> { cell.setCellValue(v.toDouble()); cell.cellStyle = numberStyle }
                    else -> {
                        val s = v.toString()
                        if (c == 7 && s.isNotBlank()) {
                            // Kolom Kadaluarsa: tulis sebagai sel tanggal
                            // (serial tanggal tanpa zona, R22) bila formatnya
                            // tanggal valid.
                            val parsed = runCatching { LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                            if (parsed != null) {
                                cell.setCellValue(DateUtil.getExcelDate(parsed))
                                cell.cellStyle = dateStyle
                            } else cell.setCellValue(s)
                        } else cell.setCellValue(s)
                    }
                }
            }
        }
        // X1: lebar kolom diperbaiki — Kadaluarsa 8 -> 18 (tanggal "2027-12-31"
        // terpotong di lebar lama), dan nilai ke-8 yang mati (kolom H kosong)
        // dihapus. Header dibekukan + filter otomatis.
        widths.forEachIndexed { i, w ->
            sheet.setColumnWidth(i, w * 256)
        }
        sheet.createFreezePane(0, 1)
        sheet.setAutoFilter(CellRangeAddress(0, 0, 0, headers.size - 1))
        // X2: dropdown Kategori & Satuan dari daftar kanonis — cegah salah
        // ketik jadi kategori nyasar. Berlaku untuk baris data 2..1001.
        val validationHelper = sheet.dataValidationHelper
        fun addDropdown(col: Int, items: Array<String>) {
            val constraint = validationHelper.createExplicitListConstraint(items)
            val range = CellRangeAddressList(1, 1000, col, col)
            val validation = validationHelper.createValidation(constraint, range)
            validation.showErrorBox = true
            validation.suppressDropDownArrow = false
            sheet.addValidationData(validation)
        }
        addDropdown(1, KATEGORI_LIST)
        addDropdown(2, SATUAN_LIST)
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
