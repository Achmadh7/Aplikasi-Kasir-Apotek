package id.apotek.lestari

import id.apotek.lestari.data.BackupData
import id.apotek.lestari.data.BackupHelper
import id.apotek.lestari.data.ExcelHelper
import id.apotek.lestari.data.Medicine
import id.apotek.lestari.data.ProfitByMedicine
import id.apotek.lestari.data.RekapHarian
import id.apotek.lestari.data.RekapPenjualan
import id.apotek.lestari.data.agregatPenjualanPerMetode
import id.apotek.lestari.data.agregatRekapPenjualan
import id.apotek.lestari.data.hitungLaba
import id.apotek.lestari.data.isExpiryValid
import id.apotek.lestari.data.local.CashEntryEntity
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.data.local.PurchaseEntity
import id.apotek.lestari.data.local.PurchaseItemEntity
import id.apotek.lestari.data.local.SaleEntity
import id.apotek.lestari.data.local.SaleItemEntity
import id.apotek.lestari.ui.screens.dueDaysLeft
import id.apotek.lestari.ui.theme.dayKeyID
import id.apotek.lestari.ui.theme.parseISODate
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Unit test JVM untuk logika uang & data (audit 2026-11-04, temuan S1):
 * backup JSON (BackupHelper), impor/ekspor Excel (ExcelHelper), dan hitung
 * hari jatuh tempo PBF. Fungsi-fungsi ini menjaga data penjualan dan uang —
 * regresinya harus tertangkap di CI, bukan di HP.
 */
class MoneyLogicTest {

    // ---------- BackupHelper: JSON backup ----------

    private fun sampleBackup() = BackupData(
        medicines = listOf(
            MedicineEntity(id = 1, name = "Paracetamol 500mg", category = "Pereda Nyeri", unit = "Strip", buyPrice = 0, sellPrice = 12000, wholesalePrice = 10500, stock = 37, expiryDate = "2027-12-31", barcode = null, createdAt = "2026-10-26T00:15:00.000Z"),
            MedicineEntity(id = 2, name = "Vitamin C 1000mg", category = "Vitamin", unit = "Botol", buyPrice = 0, sellPrice = 50000, wholesalePrice = 0, stock = 13, expiryDate = null, barcode = "8991234567890", createdAt = "2026-10-26T00:16:00.000Z")
        ),
        sales = listOf(
            SaleEntity(id = 1, invoiceNo = "INV - 04/11/26", total = 136000, paymentMethod = "Tunai", amountPaid = 140000, changeAmount = 4000, createdAt = "2026-11-04T05:21:00.000Z")
        ),
        saleItems = listOf(
            SaleItemEntity(id = 1, saleId = 1, medicineId = 1, medicineName = "Paracetamol 500mg", category = "Pereda Nyeri", unit = "Strip", qty = 3, price = 12000, priceType = "ECER", buyPrice = 0, subtotal = 36000),
            SaleItemEntity(id = 2, saleId = 1, medicineId = 2, medicineName = "Vitamin C 1000mg", category = "Vitamin", unit = "Botol", qty = 2, price = 50000, priceType = "GROSIR", buyPrice = 0, subtotal = 100000)
        ),
        cashEntries = listOf(
            CashEntryEntity(id = 1, type = "in", category = "Penjualan", description = "INV - 04/11/26", amount = 136000, source = "sale", createdAt = "2026-11-04T05:21:00.000Z")
        ),
        purchases = listOf(
            PurchaseEntity(id = 1, pbfName = "Kimia Farma", invoiceNo = "FK-001", date = "2026-11-04", total = 399555, ppn = 24555, paymentType = "KREDIT", dueDate = "2026-12-04", createdAt = "2026-11-04T06:24:00.000Z")
        ),
        purchaseItems = listOf(
            PurchaseItemEntity(id = 1, purchaseId = 1, medicineName = "Paracetamol 500mg", qty = 15, unit = "Strip", buyPrice = 5000, subtotal = 75000),
            PurchaseItemEntity(id = 2, purchaseId = 1, medicineName = "Vitamin C 1000mg", qty = 20, unit = "Botol", buyPrice = 15000, subtotal = 300000)
        )
    )

    @Test
    fun backup_roundTrip_preservesEveryTable() {
        val sample = sampleBackup()
        val parsed = BackupHelper.parseJson(BackupHelper.buildJson(sample))
        assertEquals(sample.medicines, parsed.medicines)
        assertEquals(sample.sales, parsed.sales)
        assertEquals(sample.saleItems, parsed.saleItems)
        assertEquals(sample.cashEntries, parsed.cashEntries)
        assertEquals(sample.purchases, parsed.purchases)
        assertEquals(sample.purchaseItems, parsed.purchaseItems)
        // Cap waktu pembuatan ditulis saat buildJson (bukan 0 seperti file lama).
        assertTrue(parsed.exportedAtMillis > 0)
    }

    @Test
    fun backup_rejectsTamperedSaleItemSubtotal() {
        val root = JSONObject(BackupHelper.buildJson(sampleBackup()))
        root.getJSONArray("saleItems").getJSONObject(0).put("subtotal", 1)
        assertThrows(IllegalArgumentException::class.java) {
            BackupHelper.parseJson(root.toString())
        }
    }

    @Test
    fun backup_rejectsNegativeValues() {
        // R3 (audit 2026-11-15): stok negatif ditolak.
        val root = JSONObject(BackupHelper.buildJson(sampleBackup()))
        root.getJSONArray("medicines").getJSONObject(0).put("stock", -5)
        assertThrows(IllegalArgumentException::class.java) {
            BackupHelper.parseJson(root.toString())
        }
        // qty -1 x harga 12000 = subtotal -12000 tetap "konsisten",
        // jadi cek subtotal saja tidak menangkapnya — cek negatif yang menangkap.
        val root2 = JSONObject(BackupHelper.buildJson(sampleBackup()))
        root2.getJSONArray("saleItems").getJSONObject(0).put("qty", -1)
        root2.getJSONArray("saleItems").getJSONObject(0).put("subtotal", -12000)
        assertThrows(IllegalArgumentException::class.java) {
            BackupHelper.parseJson(root2.toString())
        }
    }

    @Test
    fun backup_rejectsDuplicateMedicineId() {
        val root = JSONObject(BackupHelper.buildJson(sampleBackup()))
        val meds = root.getJSONArray("medicines")
        meds.put(meds.getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) {
            BackupHelper.parseJson(root.toString())
        }
    }

    @Test
    fun backup_rejectsMissingRequiredArray() {
        val root = JSONObject(BackupHelper.buildJson(sampleBackup()))
        root.remove("cashEntries")
        assertThrows(IllegalArgumentException::class.java) {
            BackupHelper.parseJson(root.toString())
        }
    }

    @Test
    fun backup_rejectsForeignOrBrokenFiles() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupHelper.parseJson("ini bukan json")
        }
        val root = JSONObject(BackupHelper.buildJson(sampleBackup()))
        root.put("app", "aplikasi-lain")
        assertThrows(IllegalArgumentException::class.java) {
            BackupHelper.parseJson(root.toString())
        }
    }

    @Test
    fun backup_legacyFileWithoutPurchasesStillParses() {
        val root = JSONObject(BackupHelper.buildJson(sampleBackup()))
        root.remove("purchases")
        root.remove("purchaseItems")
        val parsed = BackupHelper.parseJson(root.toString())
        assertTrue(parsed.purchases.isEmpty())
        assertTrue(parsed.purchaseItems.isEmpty())
        assertEquals(sampleBackup().medicines, parsed.medicines)
    }

    // ---------- ExcelHelper: impor / ekspor stok ----------

    private fun sampleMedicines() = listOf(
        Medicine(id = 1, name = "Paracetamol 500mg", category = "Pereda Nyeri", unit = "Strip", buyPrice = 8000, sellPrice = 12000, wholesalePrice = 10500, stock = 37, expiryDate = "2027-12-31"),
        Medicine(id = 2, name = "Vitamin C 1000mg", category = "Vitamin", unit = "Botol", buyPrice = 35000, sellPrice = 50000, wholesalePrice = 0, stock = 13, expiryDate = null)
    )

    @Test
    fun excel_exportThenImport_roundTripsAllColumns() {
        val parsed = ExcelHelper.parseImport(ExcelHelper.exportBytes(sampleMedicines()))
        assertTrue(parsed.skippedRows.isEmpty())
        assertEquals(2, parsed.items.size)
        val first = parsed.items[0]
        assertEquals("Paracetamol 500mg", first.name)
        assertEquals("Pereda Nyeri", first.category)
        assertEquals("Strip", first.unit)
        assertEquals(12000, first.sellPrice)
        assertEquals(10500, first.wholesalePrice)
        assertEquals(8000, first.buyPrice)
        assertEquals(37, first.stock)
        assertEquals("2027-12-31", first.expiryDate)
        // Grosir 0 (belum diatur) tetap 0, bukan terisi nilai lain.
        assertEquals(0, parsed.items[1].wholesalePrice)
        assertEquals(35000, parsed.items[1].buyPrice)
        assertNull(parsed.items[1].expiryDate)
    }

    @Test
    fun excel_templateExamples_parseAsThreeMedicines() {
        val parsed = ExcelHelper.parseImport(ExcelHelper.templateBytes(emptyList()))
        assertEquals(3, parsed.items.size)
        assertEquals("Paracetamol 500mg", parsed.items[0].name)
        assertEquals(10500, parsed.items[0].wholesalePrice)
    }

    @Test
    fun excel_legacySevenColumnFile_getsNoInventedWholesalePrice() {
        // Template lama 7 kolom (tanpa "Harga Jual Grosir"): posisi kolom 5 di
        // file lama adalah Stok — parser tidak boleh mengarang harga grosir
        // dari kolom lain (bug N1, audit 2026-10-30).
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(ExcelHelper.SHEET_STOK)
        val headers = listOf("Nama Obat", "Kategori", "Satuan", "Harga Beli", "Harga Jual", "Stok", "Tanggal Kadaluarsa")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        val row = sheet.createRow(1)
        row.createCell(0).setCellValue("Obat Lama")
        row.createCell(1).setCellValue("Umum")
        row.createCell(2).setCellValue("Strip")
        row.createCell(3).setCellValue(5000.0)
        row.createCell(4).setCellValue(9000.0)
        row.createCell(5).setCellValue(25.0)
        row.createCell(6).setCellValue("2027-01-31")
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        val parsed = ExcelHelper.parseImport(out.toByteArray())
        assertEquals(1, parsed.items.size)
        assertEquals(25, parsed.items[0].stock)
        assertEquals(0, parsed.items[0].wholesalePrice)
    }

    @Test
    fun excel_rowWithBrokenNumbers_isSkippedAndReported() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(ExcelHelper.SHEET_STOK)
        val headerRow = sheet.createRow(0)
        ExcelHelper.HEADERS.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        val row = sheet.createRow(1)
        row.createCell(0).setCellValue("Obat Rusak")
        row.createCell(1).setCellValue("Umum")
        row.createCell(2).setCellValue("Strip")
        row.createCell(3).setCellValue("delapan ribu")
        row.createCell(4).setCellValue(12000.0)
        row.createCell(5).setCellValue(0.0)
        row.createCell(6).setCellValue(10.0)
        row.createCell(7).setCellValue("2027-12-31")
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        val parsed = ExcelHelper.parseImport(out.toByteArray())
        assertTrue(parsed.items.isEmpty())
        assertEquals(listOf(2), parsed.skippedRows)
    }

    @Test
    fun excel_template_hasFrozenHeaderFilterFixedWidthsAndDropdowns() {
        // X1+X2: lebar Kadaluarsa 18 (bukan 8), header dibekukan + filter,
        // dropdown Kategori & Satuan.
        val wb = WorkbookFactory.create(ByteArrayInputStream(ExcelHelper.templateBytes(emptyList())))
        val sheet = wb.getSheet(ExcelHelper.SHEET_STOK)
        assertEquals(18 * 256, sheet.getColumnWidth(7))
        assertTrue(sheet.paneInformation?.isFreezePane == true)
        val ct = (sheet as XSSFSheet).ctWorksheet
        assertEquals("A1:H1", ct.autoFilter?.ref)
        assertEquals(14 * 256, sheet.getColumnWidth(5))
        val validations = sheet.dataValidations
        assertEquals(2, validations.size)
        assertTrue(validations[0].validationConstraint.formula1.contains("Antibiotik"))
        assertTrue(validations[1].validationConstraint.formula1.contains("Strip"))
        wb.close()
    }

    @Test
    fun excel_webTemplateWithBannerAndFooter_parsesCorrectColumns() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Input Stok Obat")
        sheet.createRow(0).createCell(0).setCellValue("APOTEK LESTARI - TEMPLATE")
        sheet.createRow(1).createCell(0).setCellValue("Petunjuk pengisian")
        val headers = listOf(
            "Nama Obat *", "Kategori", "Satuan", "Harga Beli *",
            "Harga Jual Ecer *", "Harga Grosir", "Stok *", "Kadaluarsa (YYYY-MM-DD)"
        )
        val headerRow = sheet.createRow(3)
        headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        val row = sheet.createRow(4)
        row.createCell(0).setCellValue("Paracetamol Web")
        row.createCell(1).setCellValue("Pereda Nyeri")
        row.createCell(2).setCellValue("Strip")
        row.createCell(3).setCellValue(8000.0)
        row.createCell(4).setCellValue(12000.0)
        row.createCell(5).setCellValue(10500.0)
        row.createCell(6).setCellValue(50.0)
        row.createCell(7).setCellValue("2028-06-30")
        val totalRow = sheet.createRow(5)
        totalRow.createCell(0).setCellValue("TOTAL")
        totalRow.createCell(6).setCellValue(50.0)

        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()

        val parsed = ExcelHelper.parseImport(out.toByteArray())
        assertEquals(1, parsed.items.size)
        assertTrue(parsed.skippedRows.isEmpty())
        val item = parsed.items[0]
        assertEquals("Paracetamol Web", item.name)
        assertEquals(8000, item.buyPrice)
        assertEquals(12000, item.sellPrice)
        assertEquals(10500, item.wholesalePrice)
        assertEquals(50, item.stock)
        assertEquals("2028-06-30", item.expiryDate)
    }

    @Test
    fun excel_webTemplateWithGolonganAndEmptyPriceStock_parsesCleanly() {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Input Stok Obat")
        sheet.createRow(0).createCell(0).setCellValue("APOTEK LESTARI — TEMPLATE IMPOR STOK OBAT")
        sheet.createRow(1).createCell(0).setCellValue("Petunjuk pengisian")
        val headers = listOf(
            "Nama Obat *", "Golongan", "Jenis Obat", "Satuan",
            "Harga Beli *", "Harga Jual Ecer *", "Harga Grosir", "Stok *",
            "Kadaluarsa (YYYY-MM-DD)", "Barcode"
        )
        val headerRow = sheet.createRow(3)
        headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        val row = sheet.createRow(4)
        row.createCell(0).setCellValue("Acarbose 100 Mg")
        row.createCell(1).setCellValue("Obat Keras")
        row.createCell(2).setCellValue("Kardiovaskular")
        row.createCell(3).setCellValue("Strip")
        row.createCell(9).setCellValue("89900123")

        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()

        val parsed = ExcelHelper.parseImport(out.toByteArray())
        assertEquals(1, parsed.items.size)
        assertTrue(parsed.skippedRows.isEmpty())
        val item = parsed.items[0]
        assertEquals("Acarbose 100 Mg", item.name)
        assertEquals("Obat Keras", item.category)
        assertEquals("Strip", item.unit)
        assertEquals(0, item.buyPrice)
        assertEquals(0, item.sellPrice)
        assertEquals(0, item.wholesalePrice)
        assertEquals(0, item.stock)
        assertNull(item.expiryDate)
        assertEquals("89900123", item.barcode)
    }

    @Test
    fun excel_export_writesFormattedNumbersAndRealDates() {
        // X3: harga/stok pakai format #,##0, kadaluarsa sel tanggal asli.
        val wb = WorkbookFactory.create(ByteArrayInputStream(ExcelHelper.exportBytes(sampleMedicines())))
        val sheet = wb.getSheet(ExcelHelper.SHEET_STOK)
        val row = sheet.getRow(1)
        assertEquals("#,##0", row.getCell(3).cellStyle.dataFormatString)
        assertEquals("#,##0", row.getCell(5).cellStyle.dataFormatString)
        val expiryCell = row.getCell(7)
        assertEquals(CellType.NUMERIC, expiryCell.cellType)
        assertTrue(DateUtil.isCellDateFormatted(expiryCell))
        val formatted = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(expiryCell.dateCellValue)
        assertEquals("2027-12-31", formatted)
        wb.close()
    }

    @Test
    fun excel_groupedNumberCell_parsesAsFullValue() {
        // S7: sel angka 12000 berformat #,##0 harus dibaca 12000, bukan 12 —
        // parser dipaksa locale Indonesia (DataFormatter default di CI
        // merendernya "12,000").
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(ExcelHelper.SHEET_STOK)
        val headerRow = sheet.createRow(0)
        ExcelHelper.HEADERS.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        val numberStyle = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0")
        }
        val row = sheet.createRow(1)
        row.createCell(0).setCellValue("Obat Format")
        row.createCell(1).setCellValue("Umum")
        row.createCell(2).setCellValue("Strip")
        row.createCell(3).apply { setCellValue(12000.0); cellStyle = numberStyle }
        row.createCell(4).setCellValue(0.0)
        row.createCell(5).setCellValue(0.0)
        row.createCell(6).apply { setCellValue(50.0); cellStyle = numberStyle }
        row.createCell(7).setCellValue("")
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        val parsed = ExcelHelper.parseImport(out.toByteArray())
        assertEquals(1, parsed.items.size)
        assertEquals(12000, parsed.items[0].sellPrice)
        assertEquals(50, parsed.items[0].stock)
    }

    // ---------- Jatuh tempo PBF ----------

    private fun dayKeyPlus(days: Int): String {
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return dayKeyID(cal.time)
    }

    @Test
    fun dueDaysLeft_countsFromToday() {
        assertEquals(0L, dueDaysLeft(dayKeyPlus(0)))
        assertEquals(7L, dueDaysLeft(dayKeyPlus(7)))
        assertEquals(-3L, dueDaysLeft(dayKeyPlus(-3)))
        assertNull(dueDaysLeft(""))
        assertNull(dueDaysLeft("bukan-tanggal"))
    }

    // ---------- Rekap penjualan Excel (J5) ----------

    @Test
    fun rekap_aggregation_groupsByDayAndMedicine() {
        val meds = mapOf(
            1 to MedicineEntity(id = 1, name = "Paracetamol 500mg", category = "Pereda Nyeri", unit = "Strip", buyPrice = 0, sellPrice = 12000, stock = 37, expiryDate = "2027-12-31", barcode = null, createdAt = "2026-10-26T00:15:00.000Z"),
            2 to MedicineEntity(id = 2, name = "Vitamin C 1000mg", category = "Vitamin", unit = "Botol", buyPrice = 0, sellPrice = 50000, stock = 13, expiryDate = null, barcode = null, createdAt = "2026-10-26T00:16:00.000Z")
        )
        val sales = listOf(
            SaleEntity(id = 1, invoiceNo = "INV-1", total = 136000, paymentMethod = "Tunai", amountPaid = 136000, changeAmount = 0, createdAt = "2026-11-04T05:21:00.000Z"),
            SaleEntity(id = 2, invoiceNo = "INV-2", total = 40000, paymentMethod = "Tunai", amountPaid = 40000, changeAmount = 0, createdAt = "2026-11-05T02:00:00.000Z"),
            // Di luar jendela: tidak ikut rekap.
            SaleEntity(id = 3, invoiceNo = "INV-0", total = 99999, paymentMethod = "Tunai", amountPaid = 99999, changeAmount = 0, createdAt = "2026-10-20T05:00:00.000Z")
        )
        val items = listOf(
            SaleItemEntity(id = 1, saleId = 1, medicineId = 1, medicineName = "Paracetamol 500mg", category = "Pereda Nyeri", unit = "Strip", qty = 3, price = 12000, priceType = "ECER", buyPrice = 8000, subtotal = 36000),
            SaleItemEntity(id = 2, saleId = 1, medicineId = 2, medicineName = "Vitamin C 1000mg", category = "Vitamin", unit = "Botol", qty = 2, price = 50000, priceType = "GROSIR", buyPrice = 40000, subtotal = 100000),
            SaleItemEntity(id = 3, saleId = 2, medicineId = 1, medicineName = "Paracetamol 500mg", category = "Pereda Nyeri", unit = "Strip", qty = 1, price = 12000, priceType = "ECER", buyPrice = 8000, subtotal = 12000),
            // Obat sudah dihapus dari Stok: nama + kategori dari snapshot item.
            SaleItemEntity(id = 4, saleId = 2, medicineId = 99, medicineName = "Obat Hilang", category = "Umum", unit = "Strip", qty = 4, price = 7000, priceType = "ECER", buyPrice = 5000, subtotal = 28000),
            SaleItemEntity(id = 5, saleId = 3, medicineId = 1, medicineName = "Paracetamol 500mg", category = "Pereda Nyeri", unit = "Strip", qty = 9, price = 12000, priceType = "ECER", buyPrice = 0, subtotal = 108000)
        )
        val start = parseISODate("2026-11-01T00:00:00.000Z")!!.time
        val end = parseISODate("2026-12-01T00:00:00.000Z")!!.time
        val rekap = agregatRekapPenjualan(sales, items, meds, start, end, "month")
        assertEquals(
            listOf(
                RekapHarian("2026-11-04", 1, 5, 136000, 104000),
                RekapHarian("2026-11-05", 1, 5, 40000, 28000)
            ),
            rekap.daily
        )
        assertEquals(2, rekap.totalTransactions)
        assertEquals(10, rekap.totalItemsSold)
        assertEquals(176000, rekap.totalRevenue)
        assertEquals(132000, rekap.totalHpp)
        assertEquals(
            listOf(
                ProfitByMedicine("Vitamin C 1000mg", "Vitamin", 2, 100000, 80000),
                ProfitByMedicine("Paracetamol 500mg", "Pereda Nyeri", 4, 48000, 32000),
                ProfitByMedicine("Obat Hilang", "Umum", 4, 28000, 20000)
            ),
            rekap.byMedicine
        )
    }

    @Test
    fun rekap_aggregation_zeroMedicineIdGroupsByName() {
        // R30 (audit 2026-11-15): dua item medicineId == 0 dengan nama
        // berbeda harus jadi dua baris, bukan satu baris salah label.
        val meds = emptyMap<Int, MedicineEntity>()
        val sales = listOf(
            SaleEntity(id = 1, invoiceNo = "INV-1", total = 30000, paymentMethod = "Tunai", amountPaid = 30000, changeAmount = 0, createdAt = "2026-11-04T05:21:00.000Z")
        )
        val items = listOf(
            SaleItemEntity(id = 1, saleId = 1, medicineId = 0, medicineName = "Obat A", category = "Umum", unit = "Strip", qty = 2, price = 10000, priceType = "ECER", buyPrice = 0, subtotal = 20000),
            SaleItemEntity(id = 2, saleId = 1, medicineId = 0, medicineName = "Obat B", category = "Umum", unit = "Strip", qty = 1, price = 10000, priceType = "ECER", buyPrice = 0, subtotal = 10000)
        )
        val start = parseISODate("2026-11-01T00:00:00.000Z")!!.time
        val end = parseISODate("2026-12-01T00:00:00.000Z")!!.time
        val rekap = agregatRekapPenjualan(sales, items, meds, start, end, "month")
        assertEquals(
            listOf(
                ProfitByMedicine("Obat A", "Umum", 2, 20000),
                ProfitByMedicine("Obat B", "Umum", 1, 10000)
            ),
            rekap.byMedicine
        )
    }

    @Test
    fun rekap_overflow_dijepitBukanDibungkus() {
        // S8 (audit 2026-11-17): tiga item @ Rp 1 miliar = Rp 3 miliar —
        // tanpa perbaikan, revenue/HPP harian dan per obat membungkus
        // jadi negatif (-1.294.967.296); total sudah benar sejak M5.
        val meds = mapOf(
            1 to MedicineEntity(id = 1, name = "Obat Mahal", category = "Pereda Nyeri", unit = "Strip", buyPrice = 0, sellPrice = 12000, stock = 37, expiryDate = null, barcode = null, createdAt = "2026-11-04T00:15:00.000Z")
        )
        val sales = listOf(
            SaleEntity(id = 1, invoiceNo = "INV-1", total = 1000000000, paymentMethod = "Tunai", amountPaid = 1000000000, changeAmount = 0, createdAt = "2026-11-04T05:21:00.000Z")
        )
        val items = (1..3).map { i ->
            SaleItemEntity(id = i, saleId = 1, medicineId = 1, medicineName = "Obat Mahal", category = "Pereda Nyeri", unit = "Strip", qty = 1, price = 1000000000, priceType = "ECER", buyPrice = 1000000000, subtotal = 1000000000)
        }
        val start = parseISODate("2026-11-01T00:00:00.000Z")!!.time
        val end = parseISODate("2026-12-01T00:00:00.000Z")!!.time
        val rekap = agregatRekapPenjualan(sales, items, meds, start, end, "month")
        assertEquals(Int.MAX_VALUE, rekap.totalRevenue)
        assertEquals(Int.MAX_VALUE, rekap.totalHpp)
        assertEquals(
            listOf(RekapHarian("2026-11-04", 1, 3, Int.MAX_VALUE, Int.MAX_VALUE)),
            rekap.daily
        )
        assertEquals(
            listOf(ProfitByMedicine("Obat Mahal", "Pereda Nyeri", 3, Int.MAX_VALUE, Int.MAX_VALUE)),
            rekap.byMedicine
        )
    }

    @Test
    fun rekap_bytes_hasTwoSheetsWithHeadersTotalsAndRealDates() {
        val rekap = RekapPenjualan(
            period = "month",
            daily = listOf(
                RekapHarian("2026-11-04", 1, 5, 136000, 104000),
                RekapHarian("2026-11-05", 1, 5, 40000, 28000)
            ),
            byMedicine = listOf(
                ProfitByMedicine("Vitamin C 1000mg", "Vitamin", 2, 100000, 80000),
                ProfitByMedicine("Paracetamol 500mg", "Pereda Nyeri", 4, 48000, 32000)
            ),
            totalTransactions = 2, totalItemsSold = 10, totalRevenue = 176000, totalHpp = 132000
        )
        val wb = WorkbookFactory.create(ByteArrayInputStream(ExcelHelper.rekapPenjualanBytes(rekap, "Bulan Ini")))
        // Sheet 1: Rekap Harian.
        val harian = wb.getSheet(ExcelHelper.SHEET_REKAP_HARIAN)
        assertEquals("Rekap Penjualan \u2014 Bulan Ini", harian.getRow(0).getCell(0).stringCellValue)
        assertEquals("Tanggal", harian.getRow(1).getCell(0).stringCellValue)
        assertEquals("Total Penjualan (Rp)", harian.getRow(1).getCell(3).stringCellValue)
        assertEquals("HPP (Rp)", harian.getRow(1).getCell(4).stringCellValue)
        assertEquals("Laba Kotor (Rp)", harian.getRow(1).getCell(5).stringCellValue)
        val dayRow = harian.getRow(2)
        assertEquals(CellType.NUMERIC, dayRow.getCell(0).cellType)
        assertTrue(DateUtil.isCellDateFormatted(dayRow.getCell(0)))
        val day = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(dayRow.getCell(0).dateCellValue)
        assertEquals("2026-11-04", day)
        assertEquals(1.0, dayRow.getCell(1).numericCellValue, 0.0)
        assertEquals("#,##0", dayRow.getCell(3).cellStyle.dataFormatString)
        assertEquals(104000.0, dayRow.getCell(4).numericCellValue, 0.0)
        assertEquals(32000.0, dayRow.getCell(5).numericCellValue, 0.0)
        val totalRow = harian.getRow(4)
        assertEquals("TOTAL", totalRow.getCell(0).stringCellValue)
        assertEquals(2.0, totalRow.getCell(1).numericCellValue, 0.0)
        assertEquals(10.0, totalRow.getCell(2).numericCellValue, 0.0)
        assertEquals(176000.0, totalRow.getCell(3).numericCellValue, 0.0)
        assertEquals(132000.0, totalRow.getCell(4).numericCellValue, 0.0)
        assertEquals(44000.0, totalRow.getCell(5).numericCellValue, 0.0)
        assertTrue(harian.paneInformation?.isFreezePane == true)
        // Sheet 2: Per Obat.
        val obat = wb.getSheet(ExcelHelper.SHEET_REKAP_OBAT)
        assertEquals("Nama Obat", obat.getRow(1).getCell(0).stringCellValue)
        assertEquals("HPP (Rp)", obat.getRow(1).getCell(4).stringCellValue)
        assertEquals("Laba Kotor (Rp)", obat.getRow(1).getCell(5).stringCellValue)
        assertEquals("Vitamin C 1000mg", obat.getRow(2).getCell(0).stringCellValue)
        assertEquals(100000.0, obat.getRow(2).getCell(3).numericCellValue, 0.0)
        assertEquals(80000.0, obat.getRow(2).getCell(4).numericCellValue, 0.0)
        assertEquals(20000.0, obat.getRow(2).getCell(5).numericCellValue, 0.0)
        val obatTotal = obat.getRow(4)
        assertEquals("TOTAL", obatTotal.getCell(0).stringCellValue)
        // S8: TOTAL per obat diambil dari total rekap — bukan jumlah baris —
        // jadi selalu cocok dengan TOTAL sheet Rekap Harian (176000/132000/44000),
        // bukan jumlah baris per obat (148000/112000/36000).
        assertEquals(176000.0, obatTotal.getCell(3).numericCellValue, 0.0)
        assertEquals(132000.0, obatTotal.getCell(4).numericCellValue, 0.0)
        assertEquals(44000.0, obatTotal.getCell(5).numericCellValue, 0.0)
        wb.close()
    }

    // ---------- Hitung laba (HPP kembali 2026-11-15) ----------

    @Test
    fun laba_hitung_dasar_dariAngkaNyata() {
        // Angka nyata 2026-11-04: Penjualan 1.056.000, HPP 726.000,
        // Biaya Operasional 150.000 -> Laba Kotor 330.000, Bersih 180.000.
        val hasil = hitungLaba(1056000, 726000, 150000)
        assertEquals(726000, hasil.hpp)
        assertEquals(330000, hasil.grossProfit)
        assertEquals(180000, hasil.netProfit)
        assertEquals(31.3, hasil.grossMarginPct, 0.001)
        assertEquals(17.0, hasil.marginPct, 0.001)
    }

    @Test
    fun laba_tanpaHpp_labaKotorSamaDenganPenjualan() {
        val hasil = hitungLaba(500000, 0, 100000)
        assertEquals(500000, hasil.grossProfit)
        assertEquals(400000, hasil.netProfit)
        assertEquals(100.0, hasil.grossMarginPct, 0.001)
        assertEquals(80.0, hasil.marginPct, 0.001)
    }

    @Test
    fun laba_revenueNol_marginNolTanpaCrash() {
        val hasil = hitungLaba(0, 0, 50000)
        assertEquals(0, hasil.grossProfit)
        assertEquals(-50000, hasil.netProfit)
        assertEquals(0.0, hasil.grossMarginPct, 0.001)
        assertEquals(0.0, hasil.marginPct, 0.001)
    }

    @Test
    fun laba_overflow_dijepitBukanDibungkus() {
        // S7 (audit 2026-11-17): selisih yang melewati Int.MAX harus
        // dijepit, bukan membungkus jadi positif (tanpa perbaikan, laba
        // bersih di bawah ini jadi +794.967.396).
        val hasil = hitungLaba(100, 2_000_000_000, 1_500_000_000)
        assertEquals(-1_999_999_900, hasil.grossProfit)
        assertEquals(Int.MIN_VALUE, hasil.netProfit)
        // Batas atas juga dijepit.
        val besar = hitungLaba(Int.MAX_VALUE, 0, 0)
        assertEquals(Int.MAX_VALUE, besar.grossProfit)
        assertEquals(Int.MAX_VALUE, besar.netProfit)
    }

    @Test
    fun excel_legacyFileWithoutBuyPrice_getsZeroBuyPrice() {
        // File lama 7 kolom (tanpa "Harga Beli"): obat tetap terimpor
        // dengan Harga Beli 0 (belum diatur), bukan nilai karangan.
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(ExcelHelper.SHEET_STOK)
        val headers = listOf("Nama Obat", "Kategori", "Satuan", "Harga Jual", "Harga Jual Grosir", "Stok", "Tanggal Kadaluarsa")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        val row = sheet.createRow(1)
        row.createCell(0).setCellValue("Obat Lama")
        row.createCell(1).setCellValue("Umum")
        row.createCell(2).setCellValue("Strip")
        row.createCell(3).setCellValue(9000.0)
        row.createCell(4).setCellValue(0.0)
        row.createCell(5).setCellValue(25.0)
        row.createCell(6).setCellValue("2027-01-31")
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        val parsed = ExcelHelper.parseImport(out.toByteArray())
        assertEquals(1, parsed.items.size)
        assertEquals(9000, parsed.items[0].sellPrice)
        assertEquals(0, parsed.items[0].buyPrice)
    }

    // M20 (audit 2026-11-16): predikat tanggal kadaluarsa wajib (W2).
    @Test
    fun w2_isExpiryValid_tanggalAtauCentang() {
        // Tanggal terisi tanpa centang: valid.
        assertTrue(isExpiryValid("2026-11-21", false))
        // Tanggal kosong tanpa centang: tidak valid (Simpan harus menolak).
        assertFalse(isExpiryValid("", false))
        // Tanggal kosong + centang: valid.
        assertTrue(isExpiryValid("", true))
        // Tanggal terisi + centang: valid.
        assertTrue(isExpiryValid("2026-11-21", true))
        // Hanya spasi bukan tanggal: tidak valid.
        assertFalse(isExpiryValid("   ", false))
    }

    // M21 (audit 2026-11-16): agregasi MB1 per cara bayar.
    private fun penjualanUntukMb1(id: Int, total: Int, method: String, createdAt: String) =
        SaleEntity(
            id = id, invoiceNo = "INV-$id", total = total,
            paymentMethod = method, amountPaid = total, changeAmount = 0,
            createdAt = createdAt
        )

    private fun wibMillis(tahun: Int, bulan: Int, tanggal: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            set(tahun, bulan, tanggal, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun mb1_agregatPerMetode_totalPerMetodeDanNormalisasi() {
        val awal = wibMillis(2026, Calendar.NOVEMBER, 1)
        val akhir = wibMillis(2026, Calendar.DECEMBER, 1)
        val sales = listOf(
            penjualanUntukMb1(1, 100000, "Tunai", "2026-11-05T10:00:00.000Z"),
            penjualanUntukMb1(2, 50000, "tunai", "2026-11-05T10:00:00.000Z"),
            penjualanUntukMb1(3, 75000, " QRIS", "2026-11-06T10:00:00.000Z"),
            penjualanUntukMb1(4, 200000, "Transfer ", "2026-11-07T10:00:00.000Z"),
            // Metode tak dikenal: dilewati total, tapi dilaporkan.
            penjualanUntukMb1(5, 99999, "E-Wallet", "2026-11-08T10:00:00.000Z"),
            penjualanUntukMb1(6, 12345, "", "2026-11-08T10:00:00.000Z"),
            // Tanggal tidak bisa diparse: dilewati seluruhnya.
            penjualanUntukMb1(7, 77777, "Tunai", "bukan-tanggal"),
            // Di luar bulan: dilewati.
            penjualanUntukMb1(8, 11111, "Tunai", "2026-10-15T10:00:00.000Z")
        )
        val hasil = agregatPenjualanPerMetode(sales, awal, akhir)
        assertEquals(150000L, hasil.totals["Tunai"])
        assertEquals(75000L, hasil.totals["QRIS"])
        assertEquals(200000L, hasil.totals["Transfer"])
        assertEquals(2, hasil.metodeTakDikenal.size)
        assertTrue(hasil.metodeTakDikenal.contains("E-Wallet"))
        assertTrue(hasil.metodeTakDikenal.contains(""))
    }

    @Test
    fun mb1_agregatPerMetode_batasBulanWIB() {
        val awal = wibMillis(2026, Calendar.NOVEMBER, 1)
        val akhir = wibMillis(2026, Calendar.DECEMBER, 1)
        val sales = listOf(
            // Tepat 00:00 WIB 1 Nov (17:00 UTC 31 Okt): masuk.
            penjualanUntukMb1(1, 1000, "Tunai", "2026-10-31T17:00:00.000Z"),
            // Sedetik sebelum awal bulan WIB: di luar.
            penjualanUntukMb1(2, 2000, "Tunai", "2026-10-31T16:59:59.000Z"),
            // Tepat 00:00 WIB 1 Des (akhir eksklusif): di luar.
            penjualanUntukMb1(3, 3000, "Tunai", "2026-11-30T17:00:00.000Z"),
            // Sedetik sebelum akhir bulan WIB: masuk.
            penjualanUntukMb1(4, 4000, "Tunai", "2026-11-30T16:59:59.000Z")
        )
        val hasil = agregatPenjualanPerMetode(sales, awal, akhir)
        assertEquals(5000L, hasil.totals["Tunai"])
        assertTrue(hasil.metodeTakDikenal.isEmpty())
    }
}
