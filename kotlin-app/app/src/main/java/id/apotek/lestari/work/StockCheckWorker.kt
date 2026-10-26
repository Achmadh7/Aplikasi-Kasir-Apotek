package id.apotek.lestari.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.apotek.lestari.data.Medicine
import id.apotek.lestari.data.local.DatabaseProvider
import id.apotek.lestari.ui.components.postLowStockNotification

/**
 * Pengecekan stok menipis di latar belakang (WorkManager, tiap 6 jam).
 *
 * Berjalan walau aplikasi tidak dibuka: WorkManager menyimpan jadwal ini
 * dan membangunkan pekerja secara berkala. Waktu persisnya diatur oleh
 * sistem — Doze / optimasi baterai bisa menundanya, jadi notifikasi
 * tidak dijamin tepat tiap 6 jam, tapi akan tetap datang.
 *
 * Supaya pengguna tidak dispam, notifikasi hanya dikirim kalau daftar
 * obat yang menipis BERUBAH sejak notifikasi terakhir (disimpan sebagai
 * "tanda tangan" id:stok di SharedPreferences). Kalau tidak ada obat yang
 * menipis, tandanya dihapus supaya kejadian menipis berikutnya tetap
 * memberi notifikasi.
 */
class StockCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(applicationContext)
            }
            val lowStock = DatabaseProvider.db.medicineDao().getAll()
                .filter { it.stock <= it.minStock }
                .sortedWith(compareBy({ it.stock }, { it.id }))
                .map {
                    Medicine(
                        it.id, it.name, it.category, it.unit, it.buyPrice, it.sellPrice,
                        it.stock, it.minStock, it.expiryDate, it.barcode, it.createdAt
                    )
                }
            val prefs = applicationContext.getSharedPreferences(
                "stock_notif_prefs", Context.MODE_PRIVATE
            )
            if (lowStock.isEmpty()) {
                // Tidak ada yang menipis: hapus tanda, supaya kejadian
                // menipis berikutnya tetap memberi notifikasi.
                prefs.edit().remove("last_low_stock_signature").apply()
                return Result.success()
            }
            val signature = lowStock
                .sortedBy { it.id }
                .joinToString(",") { "${it.id}:${it.stock}" }
            val lastSignature = prefs.getString("last_low_stock_signature", null)
            if (signature != lastSignature) {
                postLowStockNotification(applicationContext, lowStock)
                prefs.edit().putString("last_low_stock_signature", signature).apply()
            }
            Result.success()
        } catch (_: Exception) {
            // Jangan pernah retry-loop: jadwal berkala berikutnya tetap jalan.
            Result.success()
        }
    }
}
