package id.apotek.lestari.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.apotek.lestari.data.Medicine
import id.apotek.lestari.data.local.DatabaseProvider
import id.apotek.lestari.data.local.MedicineEntity
import id.apotek.lestari.ui.components.postExpiryNotification
import id.apotek.lestari.ui.theme.dayKeyID
import java.util.Date

// Pengecekan berkala di background (WorkManager, tiap 6 jam): baca database lokal
// lalu kirim notifikasi kalau ada obat yang segera kadaluarsa, tanpa perlu
// membuka aplikasi. Anti-spam: notifikasi hanya dikirim kalau daftar obat yang
// bermasalah berubah sejak pengecekan terakhir. (Notifikasi stok menipis
// dihapus 2026-10-27 atas permintaan Achmad: stok menipis cukup terlihat
// di menu Pengingat, tanpa pop-up/notifikasi.)
class StockCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            DatabaseProvider.init(applicationContext)
            val meds = DatabaseProvider.db.medicineDao().getAll().map { it.toModel() }
            val in30Key = dayKeyID(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            // Kadaluarsa: sudah kadaluarsa atau kadaluarsa dalam 30 hari ke depan (WIB).
            val expiring = meds.filter { it.expiryDate != null && it.expiryDate <= in30Key }
            val signature = "exp:${expiring.map { it.id }.sorted().joinToString(",")}"
            val prefs = applicationContext.getSharedPreferences("stock-check", Context.MODE_PRIVATE)
            if (prefs.getString("signature", null) != signature) {
                prefs.edit().putString("signature", signature).apply()
                if (expiring.isNotEmpty()) postExpiryNotification(applicationContext, expiring)
            }
            Result.success()
        } catch (_: Exception) {
            // Jangan retry tanpa henti; pengecekan berikutnya tetap jalan sesuai jadwal.
            Result.success()
        }
    }

    private fun MedicineEntity.toModel() = Medicine(
        id, name, category, unit, buyPrice, sellPrice, stock, minStock, expiryDate, barcode, createdAt
    )
}
