@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.apotek.lestari.BuildConfig
import id.apotek.lestari.data.*
import id.apotek.lestari.data.local.DATABASE_VERSION
import id.apotek.lestari.ui.rememberLogoBitmap
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// Layar Backup & Restore dan Tentang Aplikasi.

@Composable
fun BackupScreen() {
    var msg by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Pair<BackupData, BackupData>?>(null) }
    var pendingJson by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Catatan cadangan terakhir (Paket 1+2, 2026-11-04): waktu + nama file
    // backup sukses terakhir, disimpan di apotek_prefs.
    val prefs = remember { context.getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE) }
    var lastBackupAt by remember { mutableStateOf(prefs.getLong("last_backup_at", 0L)) }
    var lastBackupName by remember { mutableStateOf(prefs.getString("last_backup_name", "") ?: "") }
    var suggestedBackupName by remember { mutableStateOf("") }
    // Backup otomatis terjadwal (G6): folder SAF + frekuensi, dibaca dari
    // apotek_prefs; jadwalnya ditegakkan lewat jadwalkanBackupOtomatis.
    var autoAktif by remember { mutableStateOf(prefs.getBoolean(PREF_AUTO_BACKUP_AKTIF, false)) }
    var autoFolderUri by remember { mutableStateOf(prefs.getString(PREF_AUTO_BACKUP_FOLDER_URI, null)) }
    var autoFolderNama by remember { mutableStateOf(prefs.getString(PREF_AUTO_BACKUP_FOLDER_NAMA, "") ?: "") }
    var autoFrekuensi by remember { mutableStateOf(FrekuensiBackup.dariString(prefs.getString(PREF_AUTO_BACKUP_FREKUENSI, null))) }
    var autoTerakhir by remember { mutableStateOf(prefs.getLong(PREF_AUTO_BACKUP_TERAKHIR, 0L)) }
    var autoNamaTerakhir by remember { mutableStateOf(prefs.getString(PREF_AUTO_BACKUP_NAMA_TERAKHIR, "") ?: "") }
    var mintaAktifkan by remember { mutableStateOf(false) }
    // R49 (audit 2026-11-15): id pekerjaan "Cadangkan Sekarang" yang sedang
    // diamati hasilnya; null bila tidak ada yang berjalan.
    var autoWorkId by remember { mutableStateOf<UUID?>(null) }

    // R49: amati hasil "Cadangkan Sekarang" — dulu hanya ada pesan "dimulai"
    // dan hasil (berhasil/gagal) tidak pernah dilaporkan, begitu juga stempel
    // terakhirnya tidak diperbarui sampai layar dibuka ulang.
    val autoWorkFlow = remember(autoWorkId) {
        autoWorkId?.let { WorkManager.getInstance(context).getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }
    val autoWorkInfo by autoWorkFlow.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(autoWorkInfo) {
        val info = autoWorkInfo ?: return@LaunchedEffect
        if (!info.state.isFinished) return@LaunchedEffect
        // Worker menulis stempel terakhir ke prefs saat sukses — baca ulang
        // supaya kartu langsung menampilkannya tanpa buka ulang layar.
        autoTerakhir = prefs.getLong(PREF_AUTO_BACKUP_TERAKHIR, 0L)
        autoNamaTerakhir = prefs.getString(PREF_AUTO_BACKUP_NAMA_TERAKHIR, "") ?: ""
        lastBackupAt = prefs.getLong("last_backup_at", 0L)
        lastBackupName = prefs.getString("last_backup_name", "") ?: ""
        msg = when (info.state) {
            WorkInfo.State.SUCCEEDED -> if (autoNamaTerakhir.isNotBlank()) tr("Backup otomatis berhasil: $autoNamaTerakhir.", "Automatic backup succeeded: $autoNamaTerakhir.") else tr("Backup otomatis berhasil.", "Automatic backup succeeded.")
            WorkInfo.State.FAILED -> tr("Backup otomatis gagal, periksa izin folder.", "Automatic backup failed, check the folder permission.")
            else -> tr("Backup otomatis tidak selesai.", "Automatic backup did not finish.")
        }
        autoWorkId = null
    }

    // Pilih folder tujuan backup otomatis (SAF): izin baca+tulis disimpan
    // permanen supaya worker latar belakang bisa menulis tanpa aplikasi dibuka.
    // Bila saklar dinyalakan sebelum folder dipilih, pemilihan folder ini
    // sekaligus mengaktifkan jadwalnya (mintaAktifkan).
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            mintaAktifkan = false
            return@rememberLauncherForActivityResult
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // R23b (audit 2026-11-15): bila izin persisten gagal diambil,
        // JANGAN simpan URI folder dan jangan aktifkan jadwal — worker
        // latar belakang nanti gagal menulis tanpa penjelasan.
        val izinOk = runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
        if (!izinOk) {
            mintaAktifkan = false
            msg = tr("Gagal menyimpan izin folder, backup otomatis tidak diaktifkan.", "Failed to save the folder permission, automatic backup was not enabled.")
            return@rememberLauncherForActivityResult
        }
        val nama = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: tr("Folder pilihan", "Chosen folder")
        prefs.edit()
            .putString(PREF_AUTO_BACKUP_FOLDER_URI, uri.toString())
            .putString(PREF_AUTO_BACKUP_FOLDER_NAMA, nama)
            .apply()
        autoFolderUri = uri.toString()
        autoFolderNama = nama
        if (mintaAktifkan) {
            mintaAktifkan = false
            prefs.edit().putBoolean(PREF_AUTO_BACKUP_AKTIF, true).apply()
            autoAktif = true
            jadwalkanBackupOtomatis(context)
            msg = tr("Backup otomatis diaktifkan.", "Automatic backup enabled.")
        }
    }

    // Backup: pilih lokasi simpan, lalu tulis JSON hasil export.
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = repo.exportBackup()
            if (result is ApiResult.Success) {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(result.data.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
                    }.isSuccess
                }
                if (ok) {
                    val stamp = System.currentTimeMillis()
                    val displayName = runCatching {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: suggestedBackupName
                    prefs.edit().putLong("last_backup_at", stamp).putString("last_backup_name", displayName).apply()
                    lastBackupAt = stamp
                    lastBackupName = displayName
                }
                msg = if (ok) tr("Backup berhasil disimpan.", "Backup saved successfully.") else tr("Gagal menyimpan backup.", "Failed to save the backup.")
            } else {
                msg = tr("Backup gagal: ", "Backup failed: ") + ((result as? ApiResult.Error)?.message ?: tr("tidak diketahui", "unknown"))
            }
        }
    }
    // Restore (Paket 1+2): pilih file, lalu BACA + VALIDASI dulu untuk
    // pratinjau — data belum berubah sampai pengguna menekan Pulihkan Data
    // di dialog pratinjau.
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IllegalStateException("Tidak bisa membaca file")
                }
                val result = repo.previewBackup(json)
                if (result is ApiResult.Success) {
                    pendingJson = json
                    preview = result.data
                } else {
                    msg = tr("Restore gagal: ", "Restore failed: ") + ((result as? ApiResult.Error)?.message ?: tr("tidak diketahui", "unknown"))
                }
            } catch (e: Exception) {
                msg = tr("Restore gagal: ", "Restore failed: ") + (e.message ?: "")
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        KartuHeaderPremium("Backup", Icons.Default.Backup, tr("Simpan dan pulihkan semua data apotek.", "Save and restore all of the pharmacy's data."))
        Spacer(Modifier.height(SpaceL))
        // R23a (audit 2026-11-15): pesan hasil dulu di ujung bawah kolom
        // yang bisa digulir, sehingga tidak terlihat tanpa menggulir.
        // Sekarang tepat di bawah header supaya langsung terbaca.
        if (msg.isNotEmpty()) {
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(SpaceL))
        }
        // Kartu B1 KOTAK SENADA (pilihan Achmad 2026-11-16):
        // 20dp + surfaceContainerHigh, lingkaran ikon biru.
        ExpressiveCard(shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Backup Data", "Backup Data"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(tr("Simpan salinan semua data ke file .json", "Save a copy of all data to a .json file"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(SpaceS))
                if (lastBackupAt > 0) {
                    Text(
                        tr("Terakhir dicadangkan: ", "Last backed up: ") + formatBackupStamp(lastBackupAt) + if (lastBackupName.isNotBlank()) " • $lastBackupName" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        tr("Belum pernah dicadangkan", "Never backed up"),
                        style = MaterialTheme.typography.bodySmall, color = warningAmber(), fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(SpaceM))
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }.format(Date())
                        suggestedBackupName = "backup-apotek-lestari-$stamp.json"
                        backupLauncher.launch(suggestedBackupName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(tr("Backup Data", "Backup Data")) }
            }
        }
        Spacer(Modifier.height(SpaceL))
        // Backup Otomatis (G6): saklar + folder tujuan + frekuensi + jalan sekali.
        // Kartu B1 KOTAK SENADA (pilihan Achmad 2026-11-16).
        ExpressiveCard(shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Backup Otomatis", "Automatic Backup"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(tr("Cadangkan berkala ke folder pilihan, tanpa perlu diingat", "Back up on a schedule to a chosen folder, no need to remember"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoAktif,
                        onCheckedChange = { nyala ->
                            if (nyala && autoFolderUri.isNullOrBlank()) {
                                mintaAktifkan = true
                                folderLauncher.launch(null)
                            } else {
                                prefs.edit().putBoolean(PREF_AUTO_BACKUP_AKTIF, nyala).apply()
                                autoAktif = nyala
                                jadwalkanBackupOtomatis(context)
                                msg = if (nyala) tr("Backup otomatis diaktifkan.", "Automatic backup enabled.") else tr("Backup otomatis dimatikan.", "Automatic backup disabled.")
                            }
                        }
                    )
                }
                Spacer(Modifier.height(SpaceS))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { folderLauncher.launch(null) }) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Folder tujuan", "Destination folder"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            if (autoFolderNama.isNotBlank()) autoFolderNama else tr("Belum dipilih, ketuk untuk memilih", "Not chosen yet, tap to choose"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(SpaceS))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Frekuensi", "Frequency"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(10.dp))
                    FilterChip(
                        selected = autoFrekuensi == FrekuensiBackup.HARIAN,
                        onClick = {
                            autoFrekuensi = FrekuensiBackup.HARIAN
                            prefs.edit().putString(PREF_AUTO_BACKUP_FREKUENSI, FrekuensiBackup.HARIAN.keString()).apply()
                            jadwalkanBackupOtomatis(context)
                        },
                        label = { Text(tr("Harian", "Daily")) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = autoFrekuensi == FrekuensiBackup.MINGGUAN,
                        onClick = {
                            autoFrekuensi = FrekuensiBackup.MINGGUAN
                            prefs.edit().putString(PREF_AUTO_BACKUP_FREKUENSI, FrekuensiBackup.MINGGUAN.keString()).apply()
                            jadwalkanBackupOtomatis(context)
                        },
                        label = { Text(tr("Mingguan", "Weekly")) }
                    )
                }
                Spacer(Modifier.height(SpaceS))
                if (autoTerakhir > 0) {
                    Text(
                        tr("Terakhir berjalan: ", "Last run: ") + formatBackupStamp(autoTerakhir) + if (autoNamaTerakhir.isNotBlank()) " • $autoNamaTerakhir" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        tr("Belum pernah berjalan", "Never run yet"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(SpaceM))
                OutlinedButton(
                    onClick = {
                        // R49: hanya amati bila belum ada pengamatan berjalan —
                        // ketukan kedua saat pekerjaan masih antre/berjalan tidak
                        // boleh menimpa pengamatan pertama dengan id yang KEEP.
                        // R19 (audit 2026-11-18): ketukan kedua tidak lagi
                        // mengklaim backup "dimulai" padahal sedang berjalan.
                        if (autoWorkId == null) {
                            autoWorkId = jalankanBackupOtomatisSekali(context)
                            msg = tr("Backup otomatis dimulai, periksa folder sebentar lagi.", "Automatic backup started, check the folder shortly.")
                        } else {
                            msg = tr("Backup otomatis sedang berjalan, periksa folder sebentar lagi.", "Automatic backup is running, check the folder shortly.")
                        }
                    },
                    enabled = !autoFolderUri.isNullOrBlank(),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(tr("Cadangkan Sekarang", "Back Up Now")) }
                Spacer(Modifier.height(SpaceS))
                Text(
                    tr("File diberi nama tanggal dan menumpuk di folder pilihan. Hapus file lama langsung dari folder bila perlu.", "Files are named by date and accumulate in the chosen folder. Delete old files from the folder when needed."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(SpaceL))
        // Kartu B1 KOTAK SENADA (pilihan Achmad 2026-11-16).
        ExpressiveCard(shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Restore Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(tr("Pulihkan data dari file backup .json", "Restore data from a .json backup file"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(SpaceS))
                Text(
                    tr("Perhatian: restore akan MENGGANTI semua data saat ini dengan isi file backup.", "Warning: restoring will REPLACE all current data with the backup file's contents."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(SpaceM))
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Restore Data") }
            }
        }
    }
    preview?.let { (fileData, current) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(tr("Pratinjau Backup", "Backup Preview")) },
            text = {
                Column {
                    Text(
                        if (fileData.exportedAtMillis > 0) tr("Dibuat: ", "Created: ") + formatBackupStamp(fileData.exportedAtMillis)
                        else tr("Dibuat: tanggal tidak tercatat (file lama)", "Created: date not recorded (older file)"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(SpaceS))
                    Text(
                        tr("Isi file: ${fileData.medicines.size} obat • ${fileData.sales.size} penjualan • ${fileData.cashEntries.size} kas • ${fileData.purchases.size} pembelian PBF", "File contents: ${fileData.medicines.size} medicines • ${fileData.sales.size} sales • ${fileData.cashEntries.size} cash entries • ${fileData.purchases.size} PBF purchases"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        tr("Data sekarang: ${current.medicines.size} obat • ${current.sales.size} penjualan • ${current.cashEntries.size} kas • ${current.purchases.size} pembelian PBF", "Current data: ${current.medicines.size} medicines • ${current.sales.size} sales • ${current.cashEntries.size} cash entries • ${current.purchases.size} PBF purchases"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(SpaceS))
                    Text(
                        tr("Restore akan MENGGANTI semua data sekarang dengan isi file ini.", "Restoring will REPLACE all current data with this file's contents."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingJson
                    preview = null
                    scope.launch {
                        val result = repo.importBackup(json)
                        msg = if (result is ApiResult.Success) tr("Restore berhasil. ${result.data}", "Restore successful. ${result.data}") else tr("Restore gagal: ", "Restore failed: ") + ((result as? ApiResult.Error)?.message ?: tr("tidak diketahui", "unknown"))
                    }
                }) { Text(tr("Pulihkan Data", "Restore Data")) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}

/** Cap waktu backup ("4 Nov 2026, 13.05") dalam bahasa aplikasi aktif. */
private fun formatBackupStamp(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH.mm", appLocale()).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }.format(Date(millis))



/** Tentang Aplikasi (desain TT4 Kartu Terpisah, pilihan Achmad
 *  2026-11-14, menggantikan T2 Satu Kartu Grup): satu kartu besar
 *  dipecah jadi kartu-kartu terpisah per bagian — identitas, versi,
 *  fitur. Statistik (jumlah obat/transaksi) dan aksi Cadangkan Data
 *  dihapus dari layar ini atas permintaannya (14 Nov 2026); Backup &
 *  Restore tetap bisa dibuka dari jalurnya sendiri di Lainnya.
 *  Versi database hanya tampil di satu tempat (baris info), tidak
 *  lagi ganda di identitas. */
@Composable
fun TentangScreen(onBack: () -> Unit = {}) {
    val version = BuildConfig.VERSION_NAME
    val buildCode = BuildConfig.VERSION_CODE
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            KartuHeaderPremium(tr("Tentang Aplikasi", "About App"), Icons.Default.Info, onBack = onBack)
        }
        item {
            ExpressiveCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val logo = rememberLogoBitmap()
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(64.dp)
                    ) {
                        if (logo != null) {
                            Image(
                                bitmap = logo,
                                contentDescription = "Logo Apotek Lestari",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.LocalPharmacy, null, tint = ForestHeroLightStart, modifier = Modifier.padding(14.dp).fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Apotek Lestari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tr("Kasir, Stok & Keuangan Apotek", "Pharmacy Cashier, Stock & Finance"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            tr("Versi $version (Build $buildCode)", "Version $version (Build $buildCode)"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        item {
            Text(
                tr("INFORMASI APLIKASI", "APP INFORMATION"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ExpressiveCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    BarisInfoTentang(tr("Apotek", "Pharmacy"), "Apotek Lestari")
                    PemisahGrupTentang()
                    BarisInfoTentang(tr("Versi Web", "Web Version"), "apoteklestari.web.id", highlight = true)
                    PemisahGrupTentang()
                    BarisInfoTentang(tr("Platform", "Platform"), "Android 8.0+")
                }
            }
        }
        item {
            Text(
                tr("FITUR UTAMA", "KEY FEATURES"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ExpressiveCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BarisFiturTentang(
                        Icons.Default.PointOfSale,
                        tr("Kasir Cepat", "Fast Cashier"),
                        tr("Hitung transaksi & cetak/bagikan struk", "Calculate sales & print/share receipts")
                    )
                    PemisahGrupTentang()
                    BarisFiturTentang(
                        Icons.Default.Medication,
                        tr("Stok & Kedaluwarsa", "Stock & Expiry"),
                        tr("Pantau stok kritis, batch & pengingat ED", "Track low stock, batches & expiry alerts")
                    )
                    PemisahGrupTentang()
                    BarisFiturTentang(
                        Icons.Default.AccountBalanceWallet,
                        tr("Keuangan & Laba", "Finance & Profit"),
                        tr("Arus kas, faktur PBF & tutup buku bulanan", "Cash flow, PBF invoices & monthly closing")
                    )
                }
            }
        }
        item {
            Text(
                tr("© 2026 Apotek Lestari\nDibuat untuk operasional kasir & stok apotek", "© 2026 Apotek Lestari\nBuilt for pharmacy cashier & stock operations"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }
    }
}

/** Garis rambut pemisah antargrup di kartu Tentang. */
@Composable
private fun PemisahGrupTentang() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Baris info label-kiri nilai-kanan di kartu Tentang. */
@Composable
private fun BarisInfoTentang(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BarisFiturTentang(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
