package id.apotek.lestari.ui.components

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import id.apotek.lestari.MainActivity
import id.apotek.lestari.data.Medicine
import id.apotek.lestari.ui.theme.dayKeyID
import java.util.Date

private const val LOW_STOCK_CHANNEL_ID = "stok-menipis"
private const val LOW_STOCK_NOTIFICATION_ID = 1001
private const val EXPIRY_CHANNEL_ID = "segera-kadaluarsa"
private const val EXPIRY_NOTIFICATION_ID = 1002

/** Membuat channel notifikasi "Stok Menipis" (Android 8+). Aman dipanggil berulang kali. */
fun ensureLowStockChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(LOW_STOCK_CHANNEL_ID) != null) return
    val channel = NotificationChannel(
        LOW_STOCK_CHANNEL_ID,
        "Stok Menipis",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = "Pemberitahuan ketika ada obat yang stoknya menipis" }
    manager.createNotificationChannel(channel)
}

/**
 * Mengirim notifikasi sistem ketika ada obat yang stoknya menipis.
 * Mengetuk notifikasi akan membuka aplikasi. Fungsi ini bersifat tambahan:
 * jika izin notifikasi belum diberikan atau pengiriman gagal, tidak terjadi
 * apa-apa (tidak crash) — pop-up di dalam aplikasi tetap tampil.
 */
fun postLowStockNotification(context: Context, lowStock: List<Medicine>) {
    if (lowStock.isEmpty()) return
    try {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureLowStockChannel(context)
        val critical = lowStock.count { it.stock <= 0 }
        val text = if (critical > 0) {
            "${lowStock.size} obat stoknya menipis, $critical kritis — buka Stok untuk restock"
        } else {
            "${lowStock.size} obat stoknya menipis — buka Stok untuk restock"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, LOW_STOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Stok menipis")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(LOW_STOCK_NOTIFICATION_ID, notification)
    } catch (_: Exception) {
        // Notifikasi sistem bersifat tambahan; pop-up di aplikasi tetap tampil.
    }
}

/** Membuat channel notifikasi "Segera Kadaluarsa" (Android 8+). Aman dipanggil berulang kali. */
fun ensureExpiryChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(EXPIRY_CHANNEL_ID) != null) return
    val channel = NotificationChannel(
        EXPIRY_CHANNEL_ID,
        "Segera Kadaluarsa",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = "Pemberitahuan ketika ada obat yang segera kadaluarsa" }
    manager.createNotificationChannel(channel)
}

/**
 * Mengirim notifikasi sistem ketika ada obat yang segera kadaluarsa (dalam 30 hari)
 * atau sudah kadaluarsa. Dipakai pengecekan berkala di background. Seperti notifikasi
 * stok menipis: kalau izin belum diberikan atau pengiriman gagal, tidak terjadi
 * apa-apa (tidak crash).
 */
fun postExpiryNotification(context: Context, expiring: List<Medicine>) {
    if (expiring.isEmpty()) return
    try {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureExpiryChannel(context)
        val expired = expiring.count { it.expiryDate != null && it.expiryDate < dayKeyID(Date()) }
        val text = if (expired > 0) {
            "${expiring.size} obat segera kadaluarsa, $expired sudah kadaluarsa — buka Pengingat untuk detail"
        } else {
            "${expiring.size} obat segera kadaluarsa (dalam 30 hari) — buka Pengingat untuk detail"
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, EXPIRY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Segera kadaluarsa")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(EXPIRY_NOTIFICATION_ID, notification)
    } catch (_: Exception) {
        // Notifikasi sistem bersifat tambahan; tidak terjadi apa-apa kalau gagal.
    }
}
