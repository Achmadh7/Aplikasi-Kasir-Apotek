package id.apotek.lestari.ui

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import id.apotek.lestari.data.Sale
import id.apotek.lestari.ui.theme.formatDateTimeID
import id.apotek.lestari.ui.theme.formatIDR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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

/**
 * Ratakan teks ke tengah untuk huruf lebar ganda (DOUBLE_ON): tiap karakter
 * menempati 2 kolom, jadi padding dihitung dari lebar efektif (×2).
 * Tanpa ini "APOTEK LESTARI" (14 karakter) jadi 14×2 + 9 = 37 kolom dan
 * ter-wrap ke baris kedua di kertas 58mm.
 */
private fun centerDouble(text: String): String {
    val t = text.take(LINE_WIDTH / 2)
    val pad = ((LINE_WIDTH - t.length * 2) / 2).coerceAtLeast(0)
    return " ".repeat(pad) + t
}

/**
 * Teks kiri + teks kanan dalam satu baris 32 karakter.
 * Kanan (nominal uang) TIDAK PERNAH dipotong; kiri yang mengalah bila
 * kepanjangan — supaya digit uang di struk tidak hilang.
 */
private fun leftRight(left: String, right: String): String {
    val r = right.take(LINE_WIDTH)
    val maxLeft = (LINE_WIDTH - r.length - 1).coerceAtLeast(0)
    val l = left.take(maxLeft)
    val spaces = (LINE_WIDTH - l.length - r.length).coerceAtLeast(if (l.isEmpty()) 0 else 1)
    return l + " ".repeat(spaces) + r
}

/**
 * Teks jadi byte ASCII untuk ESC/POS. Huruf beraksen dinormalisasi ke
 * padanannya (é→e, ñ→n, ü→u) supaya tetap terbaca; karakter lain yang
 * tak terwakili jadi '?'.
 */
private fun String.toAscii(): ByteArray {
    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
    val stripped = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val safe = stripped.map { c -> if (c.code in 32..126) c else '?' }.joinToString("")
    return safe.toByteArray(Charsets.US_ASCII)
}

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
    // Nama memakai centerDouble karena dicetak lebar ganda (2 kolom/karakter).
    raw(ALIGN_CENTER)
    raw(DOUBLE_ON)
    raw(BOLD_ON)
    line(centerDouble("APOTEK LESTARI"))
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
    // Metode dicocokkan case-insensitive ("tunai"/"Cash" tidak lagi
    // menghilangkan baris Dibayar/Kembalian).
    if (sale.paymentMethod.trim().equals("Tunai", ignoreCase = true)) {
        line(leftRight("Dibayar", formatIDR(sale.amountPaid)))
        if (sale.changeAmount > 0) {
            line(leftRight("Kembalian", formatIDR(sale.changeAmount)))
        } else if (sale.amountPaid < sale.total) {
            // Kurang bayar: tampilkan kekurangannya supaya struk tidak
            // terlihat lunas padahal belum.
            line(leftRight("Kurang", formatIDR(sale.total - sale.amountPaid)))
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

/**
 * True kalau perangkat jelas-jelas BUKAN printer (audio, komputer, HP) —
 * disaring dari daftar supaya picker tidak penuh headphone/speaker.
 * Perangkat yang tidak dikenali tetap ditampilkan (lebih aman ketimbang
 * menyembunyikan printer yang namanya tidak biasa).
 */
private fun isClearlyNotPrinter(device: BluetoothDevice): Boolean {
    val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
    if (major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
        major == BluetoothClass.Device.Major.COMPUTER ||
        major == BluetoothClass.Device.Major.PHONE
    ) return true
    val n = (runCatching { device.name }.getOrNull() ?: "").lowercase()
    return n.contains("headphone") || n.contains("headset") || n.contains("earbud") ||
        n.contains("airpod") || n.contains("speaker") || n.contains("galaxy buds")
}

/** Daftar perangkat Bluetooth yang sudah terpasang (bonded), diurutkan berdasarkan nama. Kosong kalau Bluetooth mati / izin belum diberikan. Perangkat yang jelas-jelas bukan printer (audio, komputer, HP) disaring. */
fun getPairedPrinters(context: Context): List<BtPrinter> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices
            .filterNot { runCatching { isClearlyNotPrinter(it) }.getOrDefault(false) }
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

/**
 * Printer yang tersimpan, atau null kalau belum pernah memilih — atau kalau
 * printer itu sudah tidak terpasang lagi (unpaired), supaya aplikasi tidak
 * mencoba mencetak ke perangkat yang sudah tidak ada.
 */
fun loadSavedPrinter(context: Context): BtPrinter? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val address = prefs.getString(KEY_PRINTER_ADDRESS, null) ?: return null
    val name = prefs.getString(KEY_PRINTER_NAME, address) ?: address
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val stillBonded = try {
        manager?.adapter?.bondedDevices?.any { it.address.equals(address, ignoreCase = true) }
    } catch (_: SecurityException) {
        null
    }
    // null = tidak bisa memeriksa (mis. izin belum diberikan): tetap kembalikan
    // supaya tidak memblokir; false = pasti sudah tidak terpasang.
    if (stillBonded == false) return null
    return BtPrinter(name, address)
}

/** Hapus printer tersimpan (dipakai saat mau ganti printer). */
fun clearSavedPrinter(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
}

// ---- Cetak ----

/** Batas waktu connect ke printer (connect() Bluetooth tanpa timeout bisa blokir ±12 detik bila printer mati). */
private const val CONNECT_TIMEOUT_MS = 10_000L

/** Mutex cetak: hanya satu pencetakan dalam satu waktu supaya dua struk tidak interleave bila tombol Cetak diketuk 2×. */
private val printMutex = Mutex()

/**
 * Kirim byte struk ke printer lewat Bluetooth SPP.
 * Dijalankan di Dispatchers.IO (jangan di main thread — connect bisa blokir).
 * Gagal kalau: Bluetooth mati, izin belum diberikan, printer tidak terjangkau /
 * mati, atau alamatnya tidak valid.
 */
suspend fun printReceiptToPrinter(context: Context, address: String, bytes: ByteArray): Result<Unit> =
    printMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = manager?.adapter
                    ?: return@withContext Result.failure(IllegalStateException("Bluetooth tidak tersedia di HP ini"))
                val device = adapter.getRemoteDevice(address)
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    // connect() memblokir tanpa timeout; jalankan di thread
                    // sendiri dan tutup socket bila melewati batas supaya
                    // tidak menggantung ±12 detik.
                    val connectExecutor = Executors.newSingleThreadExecutor()
                    try {
                        val future = connectExecutor.submit { socket.connect() }
                        try {
                            future.get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        } catch (_: TimeoutException) {
                            runCatching { socket.close() }
                            return@withContext Result.failure(IOException("Printer tidak merespons (timeout ${CONNECT_TIMEOUT_MS / 1000} dtk) — pastikan printer menyala dan dalam jangkauan"))
                        } catch (e: java.util.concurrent.ExecutionException) {
                            throw e.cause ?: e
                        }
                    } finally {
                        connectExecutor.shutdownNow()
                    }
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
    }
