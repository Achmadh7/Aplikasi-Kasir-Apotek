package id.apotek.lestari.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.apotek.lestari.data.local.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Backup otomatis terjadwal (G6, pilihan Achmad 2026-11-12): salinan JSON
 * yang sama seperti backup manual, ditulis berkala oleh WorkManager ke
 * folder yang dipilih pengguna lewat SAF (izin baca+tulis disimpan
 * permanen, jadi worker bisa menulis tanpa aplikasi dibuka).
 *
 * Semua pengaturan tinggal di apotek_prefs (bukan database), jadi tidak
 * ada migrasi skema. Berjalan tanpa akun dan tanpa jaringan; sukses
 * senyap seperti auto-sync — penanda terakhirnya tampil di layar Backup.
 * File diberi nama tanggal dan dibiarkan menumpuk di folder pilihan;
 * pengguna menghapus file lama langsung dari folder bila perlu.
 */

const val PREF_AUTO_BACKUP_AKTIF = "auto_backup_aktif"
const val PREF_AUTO_BACKUP_FOLDER_URI = "auto_backup_folder_uri"
const val PREF_AUTO_BACKUP_FOLDER_NAMA = "auto_backup_folder_nama"
const val PREF_AUTO_BACKUP_FREKUENSI = "auto_backup_frekuensi"
const val PREF_AUTO_BACKUP_TERAKHIR = "auto_backup_terakhir"
const val PREF_AUTO_BACKUP_NAMA_TERAKHIR = "auto_backup_nama_terakhir"

const val AUTO_BACKUP_WORK_NAME = "apotek-autobackup"
const val AUTO_BACKUP_ONETIME_NAME = "apotek-autobackup-sekali"

enum class FrekuensiBackup {
    HARIAN, MINGGUAN;

    companion object {
        fun dariString(nilai: String?): FrekuensiBackup =
            if (nilai == "harian") HARIAN else MINGGUAN
    }

    fun keString(): String = if (this == HARIAN) "harian" else "mingguan"
}

data class PengaturanBackupOtomatis(
    val aktif: Boolean = false,
    val folderUri: String? = null,
    val folderNama: String = "",
    val frekuensi: FrekuensiBackup = FrekuensiBackup.MINGGUAN,
    val terakhir: Long = 0L,
    val namaTerakhir: String = ""
)

fun bacaPengaturanBackupOtomatis(context: Context): PengaturanBackupOtomatis {
    val prefs = context.getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE)
    return PengaturanBackupOtomatis(
        aktif = prefs.getBoolean(PREF_AUTO_BACKUP_AKTIF, false),
        folderUri = prefs.getString(PREF_AUTO_BACKUP_FOLDER_URI, null),
        folderNama = prefs.getString(PREF_AUTO_BACKUP_FOLDER_NAMA, "") ?: "",
        frekuensi = FrekuensiBackup.dariString(prefs.getString(PREF_AUTO_BACKUP_FREKUENSI, null)),
        terakhir = prefs.getLong(PREF_AUTO_BACKUP_TERAKHIR, 0L),
        namaTerakhir = prefs.getString(PREF_AUTO_BACKUP_NAMA_TERAKHIR, "") ?: ""
    )
}

/** Nama file backup otomatis, mis. "backup-otomatis-2026-11-12.json". */
fun namaFileBackupOtomatis(tanggal: Date = Date()): String =
    "backup-otomatis-" + SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).format(tanggal) + ".json"

/**
 * Jadwalkan (atau batalkan) backup otomatis sesuai pengaturan tersimpan.
 * Dipanggil dari layar Backup saat pengaturan berubah, dan dari MainActivity
 * tiap app dibuka supaya jadwal tetap tegak bila WorkManager direset.
 */
fun jadwalkanBackupOtomatis(context: Context) {
    val pengaturan = bacaPengaturanBackupOtomatis(context)
    val workManager = WorkManager.getInstance(context)
    if (!pengaturan.aktif || pengaturan.folderUri.isNullOrBlank()) {
        workManager.cancelUniqueWork(AUTO_BACKUP_WORK_NAME)
        return
    }
    val intervalHari = if (pengaturan.frekuensi == FrekuensiBackup.HARIAN) 1L else 7L
    val permintaan = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalHari, TimeUnit.DAYS).build()
    workManager.enqueueUniquePeriodicWork(
        AUTO_BACKUP_WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        permintaan
    )
}

/** Jalankan satu kali sekarang (tombol "Cadangkan Sekarang" di layar Backup). */
fun jalankanBackupOtomatisSekali(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        AUTO_BACKUP_ONETIME_NAME,
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
    )
}

class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE)
        val folderUriString = prefs.getString(PREF_AUTO_BACKUP_FOLDER_URI, null)
            ?: return Result.success() // belum ada folder = tidak ada yang dikerjakan
        val folderUri = Uri.parse(folderUriString)
        if (!DatabaseProvider.isInitialized()) DatabaseProvider.init(applicationContext)
        val hasil = Repository().exportBackup()
        if (hasil !is ApiResult.Success) return Result.retry()
        return try {
            val nama = namaFileBackupOtomatis()
            val fileUri = DocumentsContract.createDocument(
                applicationContext.contentResolver, folderUri, "application/json", nama
            ) ?: return Result.retry()
            applicationContext.contentResolver.openOutputStream(fileUri)?.use {
                it.write(hasil.data.toByteArray(Charsets.UTF_8))
            } ?: return Result.retry()
            val stamp = System.currentTimeMillis()
            prefs.edit()
                .putLong(PREF_AUTO_BACKUP_TERAKHIR, stamp)
                .putString(PREF_AUTO_BACKUP_NAMA_TERAKHIR, nama)
                .putLong("last_backup_at", stamp)
                .putString("last_backup_name", nama)
                .apply()
            Result.success()
        } catch (e: SecurityException) {
            // Izin folder dicabut pengguna — retry tidak akan menolong.
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
