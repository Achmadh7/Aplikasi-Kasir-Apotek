package id.apotek.lestari

import id.apotek.lestari.data.BackupData
import id.apotek.lestari.data.BackupHelper
import id.apotek.lestari.data.ExcelHelper
import id.apotek.lestari.data.Medicine
import id.apotek.lestari.data.local.CashEntryEntity
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.data.local.PurchaseEntity
import id.apotek.lestari.data.local.PurchaseItemEntity
import id.apotek.lestari.data.local.SaleEntity
import id.apotek.lestari.data.local.SaleItemEntity
import id.apotek.lestari.ui.screens.dueDaysLeft
import id.apotek.lestari.ui.theme.dayKeyID
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertEquals(37, first.stock)
        assertEquals("2027-12-31", first.expiryDate)
        // Grosir 0 (belum diatur) tetap 0, bukan terisi nilai lain.
        assertEquals(0, parsed.items[1].wholesalePrice)
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
        assertEquals(18 * 256, sheet.getColumnWidth(6))
        assertTrue(sheet.paneInformation?.isFreezePane == true)
        val ct = (sheet as XSSFSheet).ctWorksheet
        assertEquals("A1:G1", ct.autoFilter?.ref)
        val validations = sheet.dataValidations
        assertEquals(2, validations.size)
        assertTrue(validations[0].validationConstraint.formula1.contains("Antibiotik"))
        assertTrue(validations[1].validationConstraint.formula1.contains("Strip"))
        wb.close()
    }

    @Test
    fun excel_export_writesFormattedNumbersAndRealDates() {
        // X3: harga/stok pakai format #,##0, kadaluarsa sel tanggal asli.
        val wb = WorkbookFactory.create(ByteArrayInputStream(ExcelHelper.exportBytes(sampleMedicines())))
        val sheet = wb.getSheet(ExcelHelper.SHEET_STOK)
        val row = sheet.getRow(1)
        assertEquals("#,##0", row.getCell(3).cellStyle.dataFormatString)
        assertEquals("#,##0", row.getCell(5).cellStyle.dataFormatString)
        val expiryCell = row.getCell(6)
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
        row.createCell(5).apply { setCellValue(50.0); cellStyle = numberStyle }
        row.createCell(6).setCellValue("")
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
}
