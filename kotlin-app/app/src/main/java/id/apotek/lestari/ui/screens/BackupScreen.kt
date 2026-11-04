@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import id.apotek.lestari.BuildConfig
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.BtPrinter
import id.apotek.lestari.ui.buildReceiptBytes
import id.apotek.lestari.ui.getPairedPrinters
import id.apotek.lestari.ui.hasBluetoothPermission
import id.apotek.lestari.ui.isBluetoothEnabled
import id.apotek.lestari.ui.loadSavedPrinter
import id.apotek.lestari.ui.printReceiptBitmap
import id.apotek.lestari.ui.printReceiptToPrinter
import id.apotek.lestari.ui.rememberLogoBitmap
import id.apotek.lestari.ui.savePrinter
import id.apotek.lestari.ui.shareReceiptBitmap
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Backup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(tr("Simpan dan pulihkan semua data apotek.", "Save and restore all of the pharmacy's data."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(SpaceL))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(SpaceL))
        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Kenapa backup itu penting?", "Why does backup matter?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(SpaceS))
                Text(
                    tr("Semua data (obat, penjualan, kas) hanya tersimpan di HP ini. Kalau HP hilang, rusak, atau aplikasi terhapus, data tidak bisa dikembalikan. Simpan file backup di tempat aman (mis. Google Drive atau laptop) secara berkala.", "All data (medicines, sales, cash) is stored only on this phone. If the phone is lost, damaged, or the app is uninstalled, the data cannot be recovered. Keep backup files somewhere safe (e.g. Google Drive or a laptop) regularly."),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(SpaceL))
        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Backup Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).format(Date())
                        suggestedBackupName = "backup-apotek-lestari-$stamp.json"
                        backupLauncher.launch(suggestedBackupName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Backup Data") }
            }
        }
        Spacer(Modifier.height(SpaceL))
        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
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
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(SpaceL))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
    SimpleDateFormat("d MMM yyyy, HH.mm", appLocale()).format(Date(millis))

/** Teks lengkap lisensi MIT (sama dengan file LICENSE di repo). */
private const val MIT_LICENSE_TEXT = """MIT License

Copyright (c) 2026 Achmad Hidayatullah

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

/** Tentang Aplikasi (desain T10, pilihan Achmad 2026-11-04): kartu
 *  identitas ramping, deskripsi polos tanpa kartu, dan baris pintasan
 *  aksi — Cadangkan Data membuka Backup & Restore, Lisensi membuka
 *  teks lengkap MIT, Versi Database adalah baris info. */
@Composable
fun TentangScreen(onGoBackup: () -> Unit) {
    // Versi dari BuildConfig (versionName di build.gradle.kts) — sejak
    // audit R2 (2026-11-04) buildConfig diaktifkan, jadi angka ini selalu
    // mengikuti rilis dan tidak perlu disamakan manual lagi.
    val version = BuildConfig.VERSION_NAME
    // Versi skema Room saat ini (AppDatabase) — samakan bila migrasi menaikkannya.
    val dbVersion = 12
    var showLicense by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(SpaceL)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Tentang", "About"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            // Kartu identitas: logo kecil sejajar nama dan versi.
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val logo = rememberLogoBitmap()
                    if (logo != null) {
                        Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(56.dp)) {
                            Image(bitmap = logo, contentDescription = "Logo Apotek Lestari", contentScale = ContentScale.Fit, modifier = Modifier.padding(6.dp).fillMaxSize())
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    Column {
                        Text("Apotek Lestari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text(tr("Versi $version • Database $dbVersion", "Version $version • Database $dbVersion"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr("Aplikasi kasir & manajemen stok obat", "Cashier & medicine stock management app"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text(
                tr("Aplikasi kasir dan manajemen stok obat untuk Apotek Lestari. Data obat, penjualan, dan kas tersimpan di perangkat.", "A cashier and medicine stock management app for Apotek Lestari. Medicine, sales and cash data are stored on the device."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().clickable { onGoBackup() }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Cadangkan Data", "Back Up Data"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(tr("Buka Backup & Restore", "Open Backup & Restore"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().clickable { showLicense = true }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Lisensi", "License"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(tr("MIT • ketuk untuk teks lengkap", "MIT • tap for the full text"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            // Baris info versi database (bukan aksi, tanpa chevron).
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Versi Database", "Database Version"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(tr("$dbVersion • skema Room saat ini", "$dbVersion • current Room schema"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text(tr("Lisensi MIT", "MIT License")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(MIT_LICENSE_TEXT, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text(tr("Tutup", "Close")) }
            }
        )
    }
}
