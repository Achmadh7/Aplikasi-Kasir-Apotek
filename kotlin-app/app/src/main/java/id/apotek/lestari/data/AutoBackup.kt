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
import androidx.work.workDataOf
import androidx.work.WorkerParameters
import id.apotek.lestari.data.local.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
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

/** Nama file backup otomatis, mis. "backup-otomatis-2026-11-12-143022.json".
 *  R5 (audit 2026-11-15): stempel jam-menit-detik supaya dua backup di
 *  hari yang sama tidak menimpa/menduplikasi nama; zona Asia/Jakarta
 *  seperti stempel nama file rekap Excel (R32). */
fun namaFileBackupOtomatis(tanggal: Date = Date()): String =
    "backup-otomatis-" + SimpleDateFormat("yyyy-MM-dd-HHmmssSSS", Locale("id", "ID")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
    }.format(tanggal) + ".json"

/** Hapus file *.tmp di folder backup yang terakhir diubah lebih dari 1 jam
 *  lalu (sisa worker yang mati di tengah jalan). Batas 1 jam supaya tidak
 *  mengganggu worker lain yang sedang menulis. */
private fun sapuTmpLama(context: Context, folderUri: Uri) {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        folderUri, DocumentsContract.getTreeDocumentId(folderUri)
    )
    val jamLalu = System.currentTimeMillis() - 3_600_000L
    context.contentResolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        ), null, null, null
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIdx) ?: continue
            if (!name.endsWith(".tmp")) continue
            if (modIdx >= 0 && cursor.getLong(modIdx) > jamLalu) continue
            val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, cursor.getString(idIdx))
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }
        }
    }
}

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

/**
 * Jalankan satu kali sekarang (tombol "Cadangkan Sekarang" di layar Backup).
 * Mengembalikan id permintaan supaya layar bisa mengamati hasilnya (R49,
 * audit 2026-11-15); bila ada pekerjaan sekali yang masih antre, kebijakan
 * KEEP memakai yang lama dan id baru ini tidak berjalan — layar hanya
 * mengamatinya bila belum ada pengamatan berjalan.
 */
fun jalankanBackupOtomatisSekali(context: Context): UUID {
    // R3: tandai pekerjaan sekali supaya doWork() bisa membedakannya dari
    // pekerjaan berkala (yang wajib tunduk pada flag aktif).
    val permintaan = OneTimeWorkRequestBuilder<AutoBackupWorker>()
        .setInputData(workDataOf("sekali" to true))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        AUTO_BACKUP_ONETIME_NAME,
        ExistingWorkPolicy.KEEP,
        permintaan
    )
    return permintaan.id
}

class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE)
        val folderUriString = prefs.getString(PREF_AUTO_BACKUP_FOLDER_URI, null)
            ?: return Result.success() // belum ada folder = tidak ada yang dikerjakan
        val folderUri = Uri.parse(folderUriString)
        // R3 (audit 2026-11-18): jangan menulis bila auto-backup sudah
        // dimatikan (pekerjaan yang sudah antre tetap jalan walau
        // pengaturannya berubah) — kecuali pekerjaan sekali ("Cadangkan
        // Sekarang") yang memang diminta pengguna.
        if (!inputData.getBoolean("sekali", false) &&
            !prefs.getBoolean(PREF_AUTO_BACKUP_AKTIF, false)) return Result.success()
        // R3: sapu file .tmp lama dari worker yang mati di tengah jalan.
        runCatching { sapuTmpLama(applicationContext, folderUri) }
        if (!DatabaseProvider.isInitialized()) DatabaseProvider.init(applicationContext)
        val hasil = Repository().exportBackup()
        if (hasil !is ApiResult.Success) return Result.retry()
        return try {
            val nama = namaFileBackupOtomatis()
            // R5 (audit 2026-11-15): tulis ke file temp dulu, lalu rename
            // ke nama akhir — bila tulis/rename gagal, file parsial dihapus
            // dan tidak ada backup rusak yang tertinggal di folder.
            val tempUri = DocumentsContract.createDocument(
                applicationContext.contentResolver, folderUri, "application/json", "$nama.tmp"
            ) ?: return Result.retry()
            val tulisOk = try {
                applicationContext.contentResolver.openOutputStream(tempUri)?.use {
                    it.write(hasil.data.toByteArray(Charsets.UTF_8))
                } != null
            } catch (e: Exception) {
                false
            }
            if (!tulisOk) {
                runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, tempUri) }
                return Result.retry()
            }
            val finalUri = try {
                DocumentsContract.renameDocument(applicationContext.contentResolver, tempUri, nama)
            } catch (e: Exception) {
                null
            }
            if (finalUri == null) {
                runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, tempUri) }
                return Result.retry()
            }
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
