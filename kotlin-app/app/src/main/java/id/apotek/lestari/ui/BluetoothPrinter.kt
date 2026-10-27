package id.apotek.lestari.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import id.apotek.lestari.data.Sale
import id.apotek.lestari.ui.theme.formatDateTimeID
import id.apotek.lestari.ui.theme.formatIDR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

// ---------- Cetak struk ke printer thermal Bluetooth (ESC/POS) ----------
// Printer thermal (58mm, yang umum dipakai kasir kecil) menerima perintah
// ESC/POS lewat Bluetooth SPP. Alurnya:
//   1. Pasangkan printer di Pengaturan Bluetooth HP dulu (sekali saja).
//   2. Di aplikasi, pilih printer itu dari daftar perangkat yang sudah
//      terpasang — pilihannya disimpan, jadi berikutnya langsung cetak.
//   3. Struk dibangun sebagai teks ESC/POS dari data Sale (bukan bitmap),
//      supaya muat di kertas 58mm dan tercetak cepat.
//
// Yang TIDAK dipakai: pemindaian (scan) perangkat baru — hanya perangkat yang
// sudah terpasang (bonded) yang ditampilkan, jadi tidak butuh izin lokasi
// maupun BLUETOOTH_SCAN. Di Android 12+ tetap butuh izin BLUETOOTH_CONNECT
// (runtime) untuk terhubung; di bawah itu cukup izin BLUETOOTH/BLUETOOTH_ADMIN
// di manifest.

/** Printer Bluetooth yang sudah terpasang (bonded). */
data class BtPrinter(val name: String, val address: String)

/** Profil Serial Port — yang dipakai hampir semua printer thermal Bluetooth. */
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/** Lebar kertas 58mm: 32 karakter per baris (font default). */
private const val LINE_WIDTH = 32

private const val PREFS_NAME = "bt_printer"
private const val KEY_PRINTER_NAME = "name"
private const val KEY_PRINTER_ADDRESS = "address"

// ---- Perintah ESC/POS ----
private val ESC_INIT = byteArrayOf(0x1B, 0x40) // reset printer
private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
private val DOUBLE_ON = byteArrayOf(0x1D, 0x21, 0x11) // huruf lebar+tinggi ganda
private val DOUBLE_OFF = byteArrayOf(0x1D, 0x21, 0x00)
private val CUT = byteArrayOf(0x1D, 0x56, 0x00) // potong kertas penuh
private val LF = byteArrayOf(0x0A)

/** Ratakan teks ke tengah (dipotong kalau lebih dari lebar kertas). */
private fun center(text: String): String {
    val t = text.take(LINE_WIDTH)
    val pad = ((LINE_WIDTH - t.length) / 2).coerceAtLeast(0)
    return " ".repeat(pad) + t
}

/** Teks kiri + teks kanan dalam satu baris 32 karakter. */
private fun leftRight(left: String, right: String): String {
    val l = left.take(LINE_WIDTH)
    val r = right.take(LINE_WIDTH)
    val spaces = (LINE_WIDTH - l.length - r.length).coerceAtLeast(1)
    return (l + " ".repeat(spaces) + r).take(LINE_WIDTH)
}

/** Teks jadi byte ASCII (karakter non-ASCII jadi '?') — aman untuk ESC/POS. */
private fun String.toAscii(): ByteArray = toByteArray(Charsets.US_ASCII)

/**
 * Bangun isi struk sebagai byte ESC/POS dari data penjualan.
 * Isinya sama dengan kartu invoice di layar: nama apotek, no. invoice,
 * tanggal, metode bayar, rincian obat, total, dibayar/kembalian (Tunai).
 */
fun buildReceiptBytes(sale: Sale): ByteArray {
    val out = ByteArrayOutputStream()
    fun raw(bytes: ByteArray) {
        out.write(bytes)
    }
    fun line(text: String = "") {
        out.write(text.toAscii())
        out.write(LF)
    }

    raw(ESC_INIT)
    // Header: nama apotek (tengah, tebal, ukuran ganda) + jenis struk.
    raw(ALIGN_CENTER)
    raw(DOUBLE_ON)
    raw(BOLD_ON)
    line(center("APOTEK LESTARI"))
    raw(DOUBLE_OFF)
    raw(BOLD_OFF)
    line(center("Invoice Penjualan"))
    raw(ALIGN_LEFT)
    line("-".repeat(LINE_WIDTH))
    line("No: ${sale.invoiceNo}".take(LINE_WIDTH))
    line("Tgl: ${formatDateTimeID(sale.createdAt)}".take(LINE_WIDTH))
    line("Metode: ${sale.paymentMethod}".take(LINE_WIDTH))
    line("-".repeat(LINE_WIDTH))
    sale.items.forEach { item ->
        line(item.medicineName.take(LINE_WIDTH))
        line(leftRight("  ${item.qty} x ${formatIDR(item.price)}", formatIDR(item.subtotal)))
    }
    line("-".repeat(LINE_WIDTH))
    line(leftRight("Total", formatIDR(sale.total)))
    if (sale.paymentMethod == "Tunai") {
        line(leftRight("Dibayar", formatIDR(sale.amountPaid)))
        if (sale.changeAmount > 0) {
            line(leftRight("Kembalian", formatIDR(sale.changeAmount)))
        }
    }
    line("-".repeat(LINE_WIDTH))
    raw(ALIGN_CENTER)
    line(center("Terima kasih"))
    line(center("Barang yang sudah dibeli"))
    line(center("tidak dapat ditukar"))
    raw(ALIGN_LEFT)
    // Jeda sebelum potong supaya struk tidak terpotong mepet.
    line()
    line()
    line()
    raw(CUT)
    return out.toByteArray()
}

// ---- Izin & status Bluetooth ----

/** True kalau aplikasi boleh terhubung ke perangkat Bluetooth. Di Android 12+ butuh izin runtime BLUETOOTH_CONNECT; di bawah itu selalu true. */
fun hasBluetoothPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
}

/** True kalau Bluetooth HP menyala (dan perangkat punya Bluetooth). */
fun isBluetoothEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return manager?.adapter?.isEnabled == true
}

// ---- Daftar & pilihan printer tersimpan ----

/** Daftar perangkat Bluetooth yang sudah terpasang (bonded), diurutkan berdasarkan nama. Kosong kalau Bluetooth mati / izin belum diberikan. */
fun getPairedPrinters(context: Context): List<BtPrinter> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices
            .map { BtPrinter(it.name ?: it.address, it.address) }
            .sortedBy { it.name.lowercase() }
    } catch (_: SecurityException) {
        emptyList()
    }
}

/** Simpan printer pilihan supaya berikutnya langsung cetak tanpa memilih lagi. */
fun savePrinter(context: Context, printer: BtPrinter) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_PRINTER_NAME, printer.name)
        .putString(KEY_PRINTER_ADDRESS, printer.address)
        .apply()
}

/** Printer yang tersimpan, atau null kalau belum pernah memilih. */
fun loadSavedPrinter(context: Context): BtPrinter? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val address = prefs.getString(KEY_PRINTER_ADDRESS, null) ?: return null
    val name = prefs.getString(KEY_PRINTER_NAME, address) ?: address
    return BtPrinter(name, address)
}

/** Hapus printer tersimpan (dipakai saat mau ganti printer). */
fun clearSavedPrinter(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
}

// ---- Cetak ----

/**
 * Kirim byte struk ke printer lewat Bluetooth SPP.
 * Dijalankan di Dispatchers.IO (jangan di main thread — connect bisa blokir).
 * Gagal kalau: Bluetooth mati, izin belum diberikan, printer tidak terjangkau /
 * mati, atau alamatnya tidak valid.
 */
suspend fun printReceiptToPrinter(context: Context, address: String, bytes: ByteArray): Result<Unit> =
    withContext(Dispatchers.IO) {
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
                ?: return@withContext Result.failure(IllegalStateException("Bluetooth tidak tersedia di HP ini"))
            val device = adapter.getRemoteDevice(address)
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                socket.connect()
                val out = socket.outputStream
                out.write(bytes)
                out.flush()
            } finally {
                runCatching { socket.close() }
            }
            Result.success(Unit)
        } catch (_: SecurityException) {
            Result.failure(IllegalStateException("Izin Bluetooth belum diberikan"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
