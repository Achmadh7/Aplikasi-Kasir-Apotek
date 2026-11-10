package id.apotek.lestari.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import id.apotek.lestari.data.local.DatabaseProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

/**
 * Lapisan online opsional (pola yang sama dengan KasBulanan, docs/ONLINE.md).
 * Semua akses Firebase dijaga: tanpa google-services.json Firebase tidak
 * pernah terinisialisasi dan lapisan ini diam — aplikasi berjalan offline
 * persis seperti sebelumnya.
 */
object FirebaseKit {
    fun siap(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun auth(): FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }

    fun firestore(): FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    fun emailMasuk(): String? = auth()?.currentUser?.email

    fun keluar() {
        auth()?.signOut()
    }

    /**
     * Login Google hanya mungkin bila google-services.json memuat web
     * client id (muncul setelah provider Google diaktifkan dan sidik
     * jari SHA-1 didaftarkan — lihat docs/ONLINE.md). Dicek lewat
     * resource yang dibangkitkan plugin, bukan referensi langsung,
     * supaya kode tetap terkompilasi saat file-nya belum memuatnya.
     */
    fun googleSiap(context: Context): Boolean =
        context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        ) != 0

    suspend fun masukDenganGoogle(context: Context): String {
        val auth = auth() ?: throw IllegalStateException("Firebase belum dikonfigurasi")
        val idRes = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        if (idRes == 0) throw IllegalStateException("Login Google belum dikonfigurasi")
        val opsi = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(idRes))
            .build()
        val permintaan = GetCredentialRequest.Builder()
            .addCredentialOption(opsi)
            .build()
        val hasil = CredentialManager.create(context).getCredential(context, permintaan)
        val kredensial = hasil.credential
        if (kredensial is CustomCredential &&
            kredensial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val akun = GoogleIdTokenCredential.createFrom(kredensial.data)
            val hasilMasuk = auth.signInWithCredential(
                GoogleAuthProvider.getCredential(akun.idToken, null)
            ).await()
            return hasilMasuk.user?.email ?: akun.id
        }
        throw IllegalStateException("Kredensial Google tidak dikenal")
    }

    /** Kirim email atur ulang kata sandi (dibangunkan Firebase Auth). */
}

sealed interface HasilReset {
    data object Berhasil : HasilReset
    data object BelumMasuk : HasilReset
    data object BelumDikonfigurasi : HasilReset
    data class Gagal(val pesan: String) : HasilReset
}

sealed interface HasilSync {
    data object BelumDikonfigurasi : HasilSync
    data object BelumMasuk : HasilSync
    data class Berhasil(val didorong: Int, val ditarik: Int) : HasilSync
    data class Gagal(val pesan: String) : HasilSync
}

/**
 * Sinkronisasi dua arah Room <-> Firestore: dorong baris kotor (induk
 * dulu, baru itemnya), lalu tarik semua dokumen dan terapkan yang
 * menang menurut last-write-wins. Struktur server di bawah
 * users/{uid}/: obat, penjualan, penjualan_item, kas, pbf, pbf_item —
 * satu dokumen per baris, dikunci remoteId-nya.
 */
class SyncManager(private val repository: Repository) {

    suspend fun sinkronSekarang(context: Context): HasilSync {
        if (!FirebaseKit.siap(context)) return HasilSync.BelumDikonfigurasi
        val uid = FirebaseKit.auth()?.currentUser?.uid ?: return HasilSync.BelumMasuk
        val fs = FirebaseKit.firestore() ?: return HasilSync.BelumDikonfigurasi
        return try {
            var didorong = 0
            var ditarik = 0
            val akar = fs.collection("users").document(uid)

            // 1) Dorong baris kotor: induk (obat, penjualan, pembelian,
            // kas) lebih dulu, item sesudahnya karena item menunjuk
            // remoteId induknya.
            for (e in repository.obatKotor()) {
                if (e.remoteId.isBlank()) continue
                akar.collection("obat").document(e.remoteId)
                    .set(SyncLogic.obatKePeta(e)).await()
                repository.tandaiBersihObat(e.remoteId, e.updatedAt)
                didorong++
            }
            for (e in repository.penjualanKotor()) {
                if (e.remoteId.isBlank()) continue
                akar.collection("penjualan").document(e.remoteId)
                    .set(SyncLogic.penjualanKePeta(e)).await()
                repository.tandaiBersihPenjualan(e.remoteId, e.updatedAt)
                didorong++
            }
            for (e in repository.pbfKotor()) {
                if (e.remoteId.isBlank()) continue
                akar.collection("pbf").document(e.remoteId)
                    .set(SyncLogic.pbfKePeta(e)).await()
                repository.tandaiBersihPbf(e.remoteId, e.updatedAt)
                didorong++
            }
            for (e in repository.kasKotor()) {
                if (e.remoteId.isBlank()) continue
                akar.collection("kas").document(e.remoteId)
                    .set(SyncLogic.kasKePeta(e)).await()
                repository.tandaiBersihKas(e.remoteId, e.updatedAt)
                didorong++
            }
            for (e in repository.itemJualKotor()) {
                if (e.remoteId.isBlank()) continue
                val induk = repository.penjualanByIdSync(e.saleId) ?: continue
                val obat = repository.obatByIdSync(e.medicineId)
                akar.collection("penjualan_item").document(e.remoteId)
                    .set(SyncLogic.itemJualKePeta(e, induk.remoteId, obat?.remoteId ?: "")).await()
                repository.tandaiBersihItemJual(e.remoteId, e.updatedAt)
                didorong++
            }
            for (e in repository.itemPbfKotor()) {
                if (e.remoteId.isBlank()) continue
                val induk = repository.pembelianByIdSync(e.purchaseId) ?: continue
                akar.collection("pbf_item").document(e.remoteId)
                    .set(SyncLogic.itemPbfKePeta(e, induk.remoteId)).await()
                repository.tandaiBersihItemPbf(e.remoteId, e.updatedAt)
                didorong++
            }

            // 2) Tarik semua dokumen dengan urutan yang sama; kemenangan
            // diputuskan di Repository.terapkanRemote* (last-write-wins).
            for (doc in akar.collection("obat").get().await().documents) {
                val e = SyncLogic.obatDariPeta(doc.data ?: continue) ?: continue
                repository.terapkanRemoteObat(e)
                ditarik++
            }
            for (doc in akar.collection("penjualan").get().await().documents) {
                val e = SyncLogic.penjualanDariPeta(doc.data ?: continue) ?: continue
                repository.terapkanRemotePenjualan(e)
                ditarik++
            }
            for (doc in akar.collection("pbf").get().await().documents) {
                val e = SyncLogic.pbfDariPeta(doc.data ?: continue) ?: continue
                repository.terapkanRemotePbf(e)
                ditarik++
            }
            for (doc in akar.collection("kas").get().await().documents) {
                val e = SyncLogic.kasDariPeta(doc.data ?: continue) ?: continue
                repository.terapkanRemoteKas(e)
                ditarik++
            }
            for (doc in akar.collection("penjualan_item").get().await().documents) {
                val data = doc.data ?: continue
                val indukRemote = SyncLogic.itemJualIndukRemoteId(data) ?: continue
                val induk = repository.penjualanByRemoteId(indukRemote) ?: continue
                val obatRemote = SyncLogic.itemJualObatRemoteId(data)
                val obat = if (obatRemote != null) repository.obatByRemoteId(obatRemote) else null
                val e = SyncLogic.itemJualDariPeta(data, induk.id, obat?.id ?: 0) ?: continue
                repository.terapkanRemoteItemJual(e)
                ditarik++
            }
            for (doc in akar.collection("pbf_item").get().await().documents) {
                val data = doc.data ?: continue
                val indukRemote = SyncLogic.itemPbfIndukRemoteId(data) ?: continue
                val induk = repository.pembelianByRemoteId(indukRemote) ?: continue
                val e = SyncLogic.itemPbfDariPeta(data, induk.id) ?: continue
                repository.terapkanRemoteItemPbf(e)
                ditarik++
            }

            repository.purgeTombstones()
            HasilSync.Berhasil(didorong, ditarik)
        } catch (e: Exception) {
            HasilSync.Gagal(e.message ?: "Sinkronisasi gagal")
        }
    }

    /**
     * Hapus SEMUA dokumen awan milik pengguna ini di keenam koleksi
     * (Reset Semua Data, pilihan Achmad 2026-11-10). Gagal di tengah
     * jalan dikembalikan sebagai Gagal supaya pemanggil TIDAK menghapus
     * data lokal sebelum awannya bersih — kalau tidak, data awan akan
     * turun lagi pada sinkron berikutnya dan reset menjadi sia-sia.
     */
    suspend fun hapusSemuaDataCloud(context: Context): HasilReset {
        if (!FirebaseKit.siap(context)) return HasilReset.BelumDikonfigurasi
        val uid = FirebaseKit.auth()?.currentUser?.uid ?: return HasilReset.BelumMasuk
        val fs = FirebaseKit.firestore() ?: return HasilReset.BelumDikonfigurasi
        return try {
            val akar = fs.collection("users").document(uid)
            for (nama in listOf("obat", "penjualan", "penjualan_item", "kas", "pbf", "pbf_item")) {
                val docs = akar.collection(nama).get().await().documents
                for (potongan in docs.chunked(400)) {
                    val batch = fs.batch()
                    for (doc in potongan) batch.delete(doc.reference)
                    batch.commit().await()
                }
            }
            HasilReset.Berhasil
        } catch (e: Exception) {
            HasilReset.Gagal(e.message ?: "Gagal menghapus data awan")
        }
    }
}

/** Sync berkala di latar belakang; diam bila belum dikonfigurasi/masuk. */

class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!FirebaseKit.siap(applicationContext)) return Result.success()
        if (FirebaseKit.auth()?.currentUser == null) return Result.success()
        if (!DatabaseProvider.isInitialized()) DatabaseProvider.init(applicationContext)
        val repository = Repository()
        return when (val hasil = SyncManager(repository).sinkronSekarang(applicationContext)) {
            is HasilSync.Berhasil -> {
                applicationContext
                    .getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("online_sync_terakhir", System.currentTimeMillis())
                    .apply()
                Result.success()
            }
            is HasilSync.Gagal -> Result.retry()
            else -> Result.success()
        }
    }
}

/** Jadwalkan sync berkala 6 jam sekali (hanya berjalan saat ada jaringan). */
fun jadwalkanSyncBerkala(context: Context) {
    val syarat = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val permintaan = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
        .setConstraints(syarat)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "apotek-sync",
        ExistingPeriodicWorkPolicy.KEEP,
        permintaan
    )
}
