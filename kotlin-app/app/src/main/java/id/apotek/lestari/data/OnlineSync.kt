package id.apotek.lestari.data

import android.content.Context
import android.util.Log
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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
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
    data class Gagal(val pesan: String, val permanen: Boolean = false) : HasilSync
}

/**
 * Sinkronisasi dua arah Room <-> Firestore: dorong baris kotor (induk
 * dulu, baru itemnya), lalu tarik semua dokumen dan terapkan yang
 * menang menurut last-write-wins. Struktur server di bawah
 * users/{uid}/: obat, penjualan, penjualan_item, kas, pbf, pbf_item,
 * pemasok — satu dokumen per baris, dikunci remoteId-nya.
 */
class SyncManager(private val repository: Repository) {

    // R50 (audit 2026-11-15): cache updatedAt per koleksi untuk satu
    // putaran sinkron. awanLebihBaru() dulu melakukan satu get() Firestore
    // per baris kotor (N+1 read + latensi per sinkron). Sekarang tiap
    // koleksi diambil sekali — malas, hanya bila ada baris kotor di
    // koleksi itu — dan guard membaca peta. Semantik LWW tidak berubah:
    // yang dibandingkan tetap updatedAt dokumen yang sama. Instans
    // SyncManager selalu dibuat baru per sinkron; cache juga dibersihkan
    // di awal sinkronSekarang supaya peta basi tidak pernah dipakai.
    private val cacheAwan = mutableMapOf<String, Map<String, Long>>()

    private suspend fun petaAwan(akar: DocumentReference, koleksi: String): Map<String, Long> {
        // getOrPut tidak bisa dipakai di sini: lambdanya bukan suspend,
        // jadi await() di dalamnya tidak compile.
        cacheAwan[koleksi]?.let { return it }
        val peta = akar.collection(koleksi).get().await().documents.associate { doc ->
            doc.id to (doc.getLong("updatedAt") ?: 0L)
        }
        cacheAwan[koleksi] = peta
        return peta
    }

    /** True bila dokumen awan untuk baris ini lebih baru daripada baris
     *  lokal (S4): dorongan harus dilewati supaya data awan yang lebih
     *  baru tidak tertimpa. Dokumen yang tidak ada = tidak lebih baru. */
    private suspend fun awanLebihBaru(
        akar: DocumentReference,
        koleksi: String,
        remoteId: String,
        lokalUpdatedAt: Long
    ): Boolean {
        val remoteUpdatedAt = petaAwan(akar, koleksi)[remoteId] ?: return false
        return remoteUpdatedAt > lokalUpdatedAt
    }

    /** R2 (audit 2026-11-18): galat permanen — retry tidak akan pernah
     *  berhasil (aturan Firestore menolak, kredensial tidak sah, argumen
     *  tidak valid). Selain itu dianggap transient dan boleh retry. */
    private fun galatSyncPermanen(e: Exception): Boolean {
        val firestore = e as? FirebaseFirestoreException ?: return false
        return firestore.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            firestore.code == FirebaseFirestoreException.Code.UNAUTHENTICATED ||
            firestore.code == FirebaseFirestoreException.Code.INVALID_ARGUMENT
    }

    suspend fun sinkronSekarang(context: Context): HasilSync {
        if (!FirebaseKit.siap(context)) return HasilSync.BelumDikonfigurasi
        val uid = FirebaseKit.auth()?.currentUser?.uid ?: return HasilSync.BelumMasuk
        val fs = FirebaseKit.firestore() ?: return HasilSync.BelumDikonfigurasi
        cacheAwan.clear() // R50: peta guard selalu segar per sinkron.
        return try {
            var didorong = 0
            var ditarik = 0
            val akar = fs.collection("users").document(uid)
            val prefs = context.getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE)

            // Hormati reset epoch awan agar perangkat lain tidak membangkitkan data lama
            val akarSnap = akar.get().await()
            val remoteResetAt = akarSnap.getLong("resetAt") ?: 0L
            val lastResetSeen = prefs.getLong("last_reset_seen_at", 0L)
            if (remoteResetAt > lastResetSeen) {
                repository.bersihkanDataSebelumReset(remoteResetAt)
                prefs.edit().putLong("last_reset_seen_at", remoteResetAt).apply()
            }

            // 0) Gabungkan obat duplikat lokal, lalu hapus dokumen awan dalam batch
            for (potongan in repository.gabungkanObatDuplikat().chunked(400)) {
                val batch = fs.batch()
                for (remoteId in potongan) batch.delete(akar.collection("obat").document(remoteId))
                batch.commit().await()
            }

            // 1) Dorong baris kotor dalam batch 400 (induk dulu, lalu item).
            suspend fun <T> dorongKoleksi(
                koleksi: String,
                rows: List<T>,
                remoteIdOf: (T) -> String,
                updatedAtOf: (T) -> Long,
                bolehDorong: suspend (T) -> Boolean = { true },
                petaOf: suspend (T) -> Map<String, Any?>?,
                tandaiBersih: suspend (String, Long) -> Unit
            ) {
                val toPush = mutableListOf<Triple<String, Long, Map<String, Any?>>>()
                for (e in rows) {
                    val rid = remoteIdOf(e)
                    if (rid.isBlank() || !bolehDorong(e)) continue
                    val uAt = updatedAtOf(e)
                    if (awanLebihBaru(akar, koleksi, rid, uAt)) continue
                    val map = petaOf(e) ?: continue
                    toPush.add(Triple(rid, uAt, map))
                }
                for (potongan in toPush.chunked(400)) {
                    val batch = fs.batch()
                    for ((rid, _, map) in potongan) {
                        batch.set(akar.collection(koleksi).document(rid), map)
                    }
                    batch.commit().await()
                    for ((rid, uAt, _) in potongan) {
                        tandaiBersih(rid, uAt)
                        didorong++
                    }
                }
            }

            dorongKoleksi(
                "obat",
                repository.obatKotor(),
                { it.remoteId },
                { it.updatedAt },
                petaOf = { SyncLogic.obatKePeta(it) },
                tandaiBersih = { rid, uAt -> repository.tandaiBersihObat(rid, uAt) }
            )
            dorongKoleksi(
                "penjualan",
                repository.penjualanKotor(),
                { it.remoteId },
                { it.updatedAt },
                bolehDorong = { e ->
                    val b = bulanKeyDariIso(e.createdAt)
                    if (b != null && repository.bulanTerkunci(b)) {
                        Log.w("TutupBuku", "Lewati dorong penjualan di bulan tertutup $b")
                        false
                    } else true
                },
                petaOf = { SyncLogic.penjualanKePeta(it) },
                tandaiBersih = { rid, uAt -> repository.tandaiBersihPenjualan(rid, uAt) }
            )
            dorongKoleksi(
                "pbf",
                repository.pbfKotor(),
                { it.remoteId },
                { it.updatedAt },
                bolehDorong = { e ->
                    val b = bulanKeyDariIso(e.date)
                    if (b != null && repository.bulanTerkunci(b)) {
                        Log.w("TutupBuku", "Lewati dorong PBF di bulan tertutup $b")
                        false
                    } else true
                },
                petaOf = { SyncLogic.pbfKePeta(it) },
                tandaiBersih = { rid, uAt -> repository.tandaiBersihPbf(rid, uAt) }
            )
            dorongKoleksi(
                "kas",
                repository.kasKotor(),
                { it.remoteId },
                { it.updatedAt },
                bolehDorong = { e ->
                    val b = bulanKeyDariIso(e.createdAt)
                    if (b != null && repository.bulanTerkunci(b)) {
                        Log.w("TutupBuku", "Lewati dorong kas di bulan tertutup $b")
                        false
                    } else true
                },
                petaOf = { SyncLogic.kasKePeta(it) },
                tandaiBersih = { rid, uAt -> repository.tandaiBersihKas(rid, uAt) }
            )
            dorongKoleksi(
                "pemasok",
                repository.pemasokKotor(),
                { it.remoteId },
                { it.updatedAt },
                petaOf = { SyncLogic.pemasokKePeta(it) },
                tandaiBersih = { rid, uAt -> repository.tandaiBersihPemasok(rid, uAt) }
            )
            dorongKoleksi(
                "penjualan_item",
                repository.itemJualKotor(),
                { it.remoteId },
                { it.updatedAt },
                petaOf = { e ->
                    val induk = repository.penjualanByIdSync(e.saleId)
                    if (induk == null) null
                    else {
                        val b = bulanKeyDariIso(induk.createdAt)
                        if (b != null && repository.bulanTerkunci(b)) {
                            Log.w("TutupBuku", "Lewati dorong item jual di bulan tertutup $b")
                            null
                        } else {
                            val obat = repository.obatByIdSync(e.medicineId)
                            SyncLogic.itemJualKePeta(e, induk.remoteId, obat?.remoteId ?: "")
                        }
                    }
                },
                tandaiBersih = { rid, uAt -> repository.tandaiBersihItemJual(rid, uAt) }
            )
            dorongKoleksi(
                "pbf_item",
                repository.itemPbfKotor(),
                { it.remoteId },
                { it.updatedAt },
                petaOf = { e ->
                    val induk = repository.pembelianByIdSync(e.purchaseId)
                    if (induk == null) null
                    else {
                        val b = bulanKeyDariIso(induk.date)
                        if (b != null && repository.bulanTerkunci(b)) {
                            Log.w("TutupBuku", "Lewati dorong item PBF di bulan tertutup $b")
                            null
                        } else SyncLogic.itemPbfKePeta(e, induk.remoteId)
                    }
                },
                tandaiBersih = { rid, uAt -> repository.tandaiBersihItemPbf(rid, uAt) }
            )

            // 2) Tarik semua dokumen dengan urutan yang sama; jika habis restore
            // backup JSON, dokumen awan lama yang tidak ada di backup ditandai
            // tombstone terlebih dahulu agar tidak ditarik turun lagi.
            val pendingRestoreAt = prefs.getLong("pending_restore_replace_at", 0L)
            val aktifIds = if (pendingRestoreAt > 0L) repository.remoteIdsAktif() else null

            suspend fun bacaDanBersihkan(
                nama: String,
                aktifSet: Set<String>?
            ): List<Map<String, Any>> {
                val docs = akar.collection(nama).get().await().documents
                val hasil = mutableListOf<Map<String, Any>>()
                val stale = mutableListOf<DocumentSnapshot>()
                for (doc in docs) {
                    val data = doc.data ?: continue
                    val remUpdated = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                    val sudahTerhapus = data["terhapus"] == true
                    if (pendingRestoreAt > 0L && aktifSet != null && !aktifSet.contains(doc.id) && !sudahTerhapus && remUpdated <= pendingRestoreAt) {
                        stale.add(doc)
                        continue
                    }
                    hasil.add(data)
                }
                if (stale.isNotEmpty()) {
                    for (potongan in stale.chunked(400)) {
                        val batch = fs.batch()
                        for (d in potongan) {
                            val merged = (d.data ?: emptyMap()).toMutableMap()
                            merged["remoteId"] = d.id
                            merged["terhapus"] = true
                            merged["updatedAt"] = pendingRestoreAt
                            batch.set(d.reference, merged)
                        }
                        batch.commit().await()
                    }
                }
                return hasil
            }

            for (data in bacaDanBersihkan("obat", aktifIds?.obat)) {
                val e = SyncLogic.obatDariPeta(data) ?: continue
                val digabung = repository.terapkanRemoteObat(e)
                if (digabung != null) {
                    akar.collection("obat").document(digabung).delete().await()
                }
                ditarik++
            }
            for (data in bacaDanBersihkan("penjualan", aktifIds?.penjualan)) {
                val e = SyncLogic.penjualanDariPeta(data) ?: continue
                repository.terapkanRemotePenjualan(e)
                ditarik++
            }
            for (data in bacaDanBersihkan("pbf", aktifIds?.pbf)) {
                val e = SyncLogic.pbfDariPeta(data) ?: continue
                repository.terapkanRemotePbf(e)
                ditarik++
            }
            for (data in bacaDanBersihkan("kas", aktifIds?.kas)) {
                val e = SyncLogic.kasDariPeta(data) ?: continue
                repository.terapkanRemoteKas(e)
                ditarik++
            }
            for (data in bacaDanBersihkan("pemasok", aktifIds?.pemasok)) {
                val e = SyncLogic.pemasokDariPeta(data) ?: continue
                repository.terapkanRemotePemasok(e)
                ditarik++
            }
            for (data in bacaDanBersihkan("penjualan_item", aktifIds?.penjualanItem)) {
                val indukRemote = SyncLogic.itemJualIndukRemoteId(data) ?: continue
                val induk = repository.penjualanByRemoteId(indukRemote) ?: continue
                val obatRemote = SyncLogic.itemJualObatRemoteId(data)
                val sementara = SyncLogic.itemJualDariPeta(data, induk.id, 0) ?: continue
                var obat = if (obatRemote != null) repository.obatByRemoteId(obatRemote) else null
                if (obat == null && obatRemote != null) {
                    obat = repository.obatByNama(sementara.medicineName, sementara.category, sementara.unit)
                }
                val e = sementara.copy(medicineId = obat?.id ?: 0)
                repository.terapkanRemoteItemJual(e)
                ditarik++
            }
            for (data in bacaDanBersihkan("pbf_item", aktifIds?.pbfItem)) {
                val indukRemote = SyncLogic.itemPbfIndukRemoteId(data) ?: continue
                val induk = repository.pembelianByRemoteId(indukRemote) ?: continue
                val e = SyncLogic.itemPbfDariPeta(data, induk.id) ?: continue
                repository.terapkanRemoteItemPbf(e)
                ditarik++
            }

            // Setelah dorong+tarik, pastikan isi cacheAwan tidak dipakai lintas siklus
            cacheAwan.clear()
            if (pendingRestoreAt > 0L) {
                prefs.edit().remove("pending_restore_replace_at").apply()
            }

            // M3 (audit 2026-11-16): rekonsiliasi silang setelah semua
            // item ditarik — penjualan yang totalnya tidak cocok dengan
            // jumlah subtotal itemnya hanya dicatat di log (tidak
            // dihapus; lihat Repository.rekonsiliasiPenjualanDitarik).
            val tidakCocok = repository.rekonsiliasiPenjualanDitarik()
            if (tidakCocok > 0) Log.w("OnlineSync", "rekonsiliasi: $tidakCocok penjualan totalnya tidak cocok dengan jumlah subtotal itemnya (periksa log Repository)")
            repository.purgeTombstones()
            HasilSync.Berhasil(didorong, ditarik)
        } catch (e: Exception) {
            HasilSync.Gagal(e.message ?: "Sinkronisasi gagal", galatSyncPermanen(e))
        }
    }

    /**
     * Hapus SEMUA dokumen awan milik pengguna ini di ketujuh koleksi
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
            val resetMs = System.currentTimeMillis()
            val akar = fs.collection("users").document(uid)
            for (nama in listOf("obat", "penjualan", "penjualan_item", "kas", "pbf", "pbf_item", "pemasok")) {
                val docs = akar.collection(nama).get().await().documents
                for (potongan in docs.chunked(400)) {
                    val batch = fs.batch()
                    for (doc in potongan) batch.delete(doc.reference)
                    batch.commit().await()
                }
            }
            akar.set(mapOf("resetAt" to resetMs), SetOptions.merge()).await()
            context.getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_reset_seen_at", resetMs)
                .remove("pending_restore_replace_at")
                .apply()
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
            is HasilSync.Gagal -> if (hasil.permanen) Result.failure() else Result.retry()
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
