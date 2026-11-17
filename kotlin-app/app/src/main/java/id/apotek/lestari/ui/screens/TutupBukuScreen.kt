@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.apotek.lestari.data.*
import id.apotek.lestari.data.local.ClosedMonthEntity
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Layar Tutup Buku Bulanan (TB1, pilihan Achmad 2026-11-17): kunci bulan
// yang sudah lewat supaya penjualan dan kasnya tidak bisa diubah lagi.
// Data tetap tersimpan dan tiap bulan tertutup bisa diekspor ke Excel
// (format J5 yang sama seperti ekspor Laba).

@Composable
fun TutupBukuScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var tutup by remember { mutableStateOf<List<ClosedMonthEntity>>(emptyList()) }
    // S11 (audit 2026-11-17): bulan lampau yang terlewat (lebih tua dari
    // bulan lalu, belum ditutup, ada penjualannya) — supaya bisa ditutup.
    var terlewat by remember { mutableStateOf<List<String>>(emptyList()) }
    var rekapPerBulan by remember { mutableStateOf<Map<String, RekapPenjualan>>(emptyMap()) }
    var muatUlang by remember { mutableIntStateOf(0) }
    var konfirmasiTutup by remember { mutableStateOf<String?>(null) }
    var konfirmasiBuka by remember { mutableStateOf<String?>(null) }
    var pendingExcel by remember { mutableStateOf<ByteArray?>(null) }
    // Teks untuk dipakai di dalam lambda (tr() tidak boleh dipanggil di
    // dalam onClick / launcher — tangkap dulu saat komposisi).
    val pesanExcelTersimpan = tr("Rekap Excel bulan berhasil disimpan.", "Monthly Excel recap saved.")
    val pesanExcelGagal = tr("Gagal menyimpan rekap Excel.", "Failed to save the Excel recap.")
    val pesanGagalMuat = tr("Gagal memuat rekap: ", "Failed to load recap: ")
    val pesanGagalBuat = tr("Gagal membuat rekap Excel: ", "Failed to create the Excel recap: ")
    val tidakDiketahui = tr("tidak diketahui", "unknown")
    val awalanBerhasil = tr("Bulan ", "Month ")
    val akhiranTutup = tr(" berhasil ditutup.", " closed successfully.")
    val akhiranBuka = tr(" dibuka lagi.", " reopened.")
    val bulanIni = bulanKeyDariMillis(System.currentTimeMillis())
    val bulanLalu = bulanSebelumnya(bulanIni) ?: ""
    val tutupSet = tutup.map { it.bulan }.toSet()
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelHelper.MIME_XLSX)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = pendingExcel ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
                }.isSuccess
            }
            msg = if (ok) pesanExcelTersimpan else pesanExcelGagal
        }
    }
    fun exportBulan(bulan: String) {
        scope.launch {
            runCatching {
                when (val r = repo.getRekapPenjualanBulan(bulan)) {
                    is ApiResult.Success -> {
                        val label = namaBulanIndonesia(bulan)
                        val bytes = withContext(Dispatchers.IO) {
                            ExcelHelper.rekapPenjualanBytes(r.data, label)
                        }
                        pendingExcel = bytes
                        excelLauncher.launch("Tutup-Buku-$bulan.xlsx")
                    }
                    is ApiResult.Error -> msg = pesanGagalMuat + r.message
                    is ApiResult.Loading -> Unit
                }
            }.onFailure { e -> msg = pesanGagalBuat + (e.message ?: tidakDiketahui) }
        }
    }
    fun muat() {
        scope.launch {
            when (val r = repo.listBulanTertutup()) {
                is ApiResult.Success -> {
                    tutup = r.data
                    val tutupSetMuatan = r.data.map { it.bulan }.toSet()
                    // S11: bulan lampau yang terlewat — mundur sampai 12
                    // bulan dari bulan lalu; yang ada penjualannya ikut
                    // dimuat supaya bisa ditutup/diekspor.
                    val kandidat = mutableListOf<String>()
                    var mundur = bulanSebelumnya(bulanLalu)
                    repeat(12) {
                        val b = mundur ?: return@repeat
                        if (b !in tutupSetMuatan && b != bulanIni) kandidat.add(b)
                        mundur = bulanSebelumnya(b)
                    }
                    val kunci = (listOf(bulanIni, bulanLalu) + kandidat + r.data.map { it.bulan }).distinct().filter { it.isNotEmpty() }
                    val isi = mutableMapOf<String, RekapPenjualan>()
                    for (b in kunci) {
                        when (val rk = repo.getRekapPenjualanBulan(b)) {
                            is ApiResult.Success -> isi[b] = rk.data
                            else -> Unit
                        }
                    }
                    rekapPerBulan = isi
                    terlewat = kandidat.filter { (isi[it]?.totalTransactions ?: 0) > 0 }
                }
                is ApiResult.Error -> msg = pesanGagalMuat + r.message
                is ApiResult.Loading -> Unit
            }
        }
    }
    // Muat ulang setiap kali layar kembali tampil (pola yang sama dengan
    // LabaScreen): penjualan dari Kasir ikut terhitung.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(muatUlang, resumeKey) { muat() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(SpaceL)
    ) {
        item {
            Column {
                KartuHeaderPremium(tr("Tutup Buku", "Close Books"), Icons.Default.Lock)
                Spacer(Modifier.height(SpaceL))
                if (msg.isNotEmpty()) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(SpaceS))
                }
                KartuBulanTutup(
                    bulan = bulanIni,
                    rekap = rekapPerBulan[bulanIni],
                    status = tr("Berjalan", "In progress"),
                    statusColor = Color(0xFF2E7D32),
                    ditutupPada = null
                )
                Spacer(Modifier.height(SpaceM))
                if (bulanLalu.isNotEmpty() && bulanLalu !in tutupSet) {
                    KartuBulanTutup(
                        bulan = bulanLalu,
                        rekap = rekapPerBulan[bulanLalu],
                        status = tr("Selesai — bisa ditutup", "Ended — can be closed"),
                        statusColor = Color(0xFF8D6E00),
                        ditutupPada = null,
                        onTutup = { konfirmasiTutup = bulanLalu }
                    )
                    Spacer(Modifier.height(SpaceM))
                }
                // S11: bulan yang terlewat — bisa ditutup seperti bulan lalu.
                if (terlewat.isNotEmpty()) {
                    Text(tr("Bulan yang terlewat", "Missed months"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(SpaceS))
                    for (b in terlewat) {
                        KartuBulanTutup(
                            bulan = b,
                            rekap = rekapPerBulan[b],
                            status = tr("Selesai — bisa ditutup", "Ended — can be closed"),
                            statusColor = Color(0xFF8D6E00),
                            ditutupPada = null,
                            onTutup = { konfirmasiTutup = b }
                        )
                        Spacer(Modifier.height(SpaceM))
                    }
                }
                Text(tr("Bulan yang ditutup", "Closed months"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(SpaceS))
                if (tutup.isEmpty()) {
                    Text(tr("Belum ada bulan yang ditutup.", "No months closed yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(tutup) { row ->
            KartuBulanTutup(
                bulan = row.bulan,
                rekap = rekapPerBulan[row.bulan],
                status = tr("Terkunci", "Locked"),
                statusColor = Color(0xFF8D6E00),
                ditutupPada = row.ditutupPada,
                onExcel = { exportBulan(row.bulan) },
                onBuka = { konfirmasiBuka = row.bulan }
            )
        }
    }
    konfirmasiTutup?.let { bulan ->
        AlertDialog(
            onDismissRequest = { konfirmasiTutup = null },
            title = { Text(tr("Tutup bulan ", "Close month ") + namaBulanIndonesia(bulan) + "?") },
            text = { Text(tr("Penjualan dan kas bulan ini tidak bisa diubah lagi. Data tetap tersimpan dan bisa diekspor Excel.", "Sales and cash for this month can no longer be changed. Data stays saved and can be exported to Excel.")) },
            confirmButton = {
                Button(onClick = {
                    konfirmasiTutup = null
                    scope.launch {
                        when (val r = repo.tutupBulan(bulan)) {
                            is ApiResult.Success -> {
                                msg = awalanBerhasil + namaBulanIndonesia(bulan) + akhiranTutup
                                muatUlang++
                            }
                            is ApiResult.Error -> msg = r.message
                            is ApiResult.Loading -> Unit
                        }
                    }
                }) { Text(tr("Tutup Bulan", "Close Month")) }
            },
            dismissButton = {
                TextButton(onClick = { konfirmasiTutup = null }) { Text(tr("Batal", "Cancel")) }
            }
        )
    }
    konfirmasiBuka?.let { bulan ->
        AlertDialog(
            onDismissRequest = { konfirmasiBuka = null },
            title = { Text(tr("Buka lagi bulan ", "Reopen month ") + namaBulanIndonesia(bulan) + "?") },
            text = { Text(tr("Penjualan dan kas bulan ini bisa diubah lagi.", "Sales and cash for this month can be changed again.")) },
            confirmButton = {
                Button(onClick = {
                    konfirmasiBuka = null
                    scope.launch {
                        when (val r = repo.bukaBulan(bulan)) {
                            is ApiResult.Success -> {
                                msg = awalanBerhasil + namaBulanIndonesia(bulan) + akhiranBuka
                                muatUlang++
                            }
                            is ApiResult.Error -> msg = r.message
                            is ApiResult.Loading -> Unit
                        }
                    }
                }) { Text(tr("Buka Lagi", "Reopen")) }
            },
            dismissButton = {
                TextButton(onClick = { konfirmasiBuka = null }) { Text(tr("Batal", "Cancel")) }
            }
        )
    }
}

/** Kartu satu bulan: nama, ringkasan, status, dan tombol aksinya. */
@Composable
private fun KartuBulanTutup(
    bulan: String,
    rekap: RekapPenjualan?,
    status: String,
    statusColor: Color,
    ditutupPada: Long?,
    onTutup: (() -> Unit)? = null,
    onExcel: (() -> Unit)? = null,
    onBuka: (() -> Unit)? = null
) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(12.dp)) {
            Text(namaBulanIndonesia(bulan), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (rekap != null) tr("Transaksi: ", "Transactions: ") + rekap.totalTransactions + "  •  " + formatIDR(rekap.totalRevenue)
                else tr("Memuat…", "Loading…"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = statusColor)
            if (ditutupPada != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("Ditutup: ", "Closed: ") + SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id", "ID")).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }.format(Date(ditutupPada)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onTutup != null || onExcel != null || onBuka != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onTutup != null) {
                        OutlinedButton(onClick = onTutup, shape = RoundedCornerShape(20.dp)) {
                            Text(tr("Tutup Bulan Ini", "Close This Month"), maxLines = 1)
                        }
                    }
                    if (onExcel != null) {
                        OutlinedButton(onClick = onExcel, shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Excel", "Excel"), maxLines = 1)
                        }
                    }
                    if (onBuka != null) {
                        TextButton(onClick = onBuka) {
                            Text(tr("Buka Lagi", "Reopen"), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
