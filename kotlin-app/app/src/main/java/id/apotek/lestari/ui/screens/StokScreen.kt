@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
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

// Layar Stok Obat dan formulir tambah/edit obat.

@Composable
fun StokScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf("az") } // az | za | low (stok terendah)
    // Mode pilih banyak (hapus massal): kartu menampilkan checkbox, ketuk kartu
    // untuk centang; tombol Hapus muncul di bar seleksi. Disimpan sebagai
    // Map id -> obat (bukan cuma id) supaya hapus massal + Urungkan tetap
    // benar walau daftar berubah karena pencarian/filter selama memilih.
    var selectMode by remember { mutableStateOf(false) }
    var selectedMeds by remember { mutableStateOf<Map<Int, Medicine>>(emptyMap()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var msg by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Medicine?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var importSummary by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    fun reload() { scope.launch { (repo.listMedicines(search) as? ApiResult.Success)?.let { medicines = it.data.medicines } } }
    // File Excel dibuka/disimpan lewat Storage Access Framework — tanpa izin penyimpanan.
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelHelper.MIME_XLSX)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = medicines
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(ExcelHelper.templateBytes(current)) }
                        ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
                }
            }
            msg = if (result.isSuccess) tr("Template Excel berhasil disimpan (${if (current.isEmpty()) 3 else current.size} obat).", "Excel template saved (${if (current.isEmpty()) 3 else current.size} medicines).")
            else tr("Gagal menyimpan template Excel: ", "Failed to save the Excel template: ") + (result.exceptionOrNull()?.message ?: "")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelHelper.MIME_XLSX)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = medicines
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(ExcelHelper.exportBytes(current)) }
                        ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
                }.isSuccess
            }
            msg = if (ok) tr("Export Excel berhasil: ${current.size} obat.", "Excel export saved: ${current.size} medicines.") else tr("Gagal menyimpan export Excel.", "Failed to save the Excel export.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Tidak bisa membaca file")
                    ExcelHelper.parseImport(bytes)
                }
                val response = repo.bulkImport(BulkImportArgs(parsed.items))
                if (response is ApiResult.Success) {
                    val r = response.data
                    importSummary = buildString {
                        append(tr("${r.inserted} obat baru ditambahkan, ${r.updated} diperbarui", "${r.inserted} new medicines added, ${r.updated} updated"))
                        if (r.skipped > 0) append(tr(", ${r.skipped} baris dilewati (nama kosong)", ", ${r.skipped} rows skipped (empty name)"))
                        if (parsed.skippedRows.isNotEmpty()) append(tr("\nBaris dilewati: ${parsed.skippedRows.joinToString(", ")}", "\nRows skipped: ${parsed.skippedRows.joinToString(", ")}"))
                        append(tr("\nTotal baris diproses: ${r.total}", "\nTotal rows processed: ${r.total}"))
                    }
                    showImportDialog = true
                    reload()
                } else {
                    msg = tr("Import gagal: ", "Import failed: ") + ((response as? ApiResult.Error)?.message ?: tr("tidak diketahui", "unknown"))
                }
            } catch (e: Exception) {
                msg = tr("Import gagal: ", "Import failed: ") + (e.message ?: "")
            }
        }
    }
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME) — pola yang
    // sama seperti Laba/Obat Terlaris/Pengingat/Faktur PBF, supaya data
    // selalu segar setelah ada perubahan dari menu lain.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val listState = rememberLazyListState()
    // FAB Tambah Obat: sembunyi saat daftar digulir ke bawah, muncul lagi
    // saat digulir ke atas (permintaan Achmad 2026-10-31).
    var fabVisible by remember { mutableStateOf(true) }
    var lastScrollIndex by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                fabVisible = when {
                    index == 0 && offset == 0 -> true
                    index > lastScrollIndex -> false
                    index < lastScrollIndex -> true
                    offset > lastScrollOffset + 8 -> false
                    offset < lastScrollOffset - 8 -> true
                    else -> fabVisible
                }
                lastScrollIndex = index
                lastScrollOffset = offset
            }
    }
    // Muat awal + setiap kali layar kembali tampil (resumeKey): langsung
    // tanpa menunggu. Dulu muat awal ikut antre di debounce pencarian di
    // bawah, jadi menu Stok terasa delay saat dibuka (perbaikan 2026-11-01).
    LaunchedEffect(resumeKey) { reload() }
    // Pencarian di-debounce: satu reload 300ms setelah ketikan terakhir,
    // supaya coroutine pencarian lama tidak saling mendahului hasil terbaru.
    // Jalan pertama dilewati karena muat awal sudah ditangani efek di atas.
    var searchEffectReady by remember { mutableStateOf(false) }
    LaunchedEffect(search) {
        if (!searchEffectReady) { searchEffectReady = true; return@LaunchedEffect }
        delay(300)
        reload()
    }
    // Daftar muncul bertahap begitu data pertama kali tiba; tidak berulang saat scroll.
    // Ringkasan + filter status stok (Aman >20, Sedang ==20, Rendah <20, Habis 0).
    val countAman = medicines.count { stockLabel(it.stock) == "Aman" }
    val countSedang = medicines.count { stockLabel(it.stock) == "Sedang" }
    val countRendah = medicines.count { stockLabel(it.stock) == "Rendah" }
    val countHabis = medicines.count { stockLabel(it.stock) == "Habis" }
    val shown = when (statusFilter) {
        "aman" -> medicines.filter { stockLabel(it.stock) == "Aman" }
        "sedang" -> medicines.filter { stockLabel(it.stock) == "Sedang" }
        "rendah" -> medicines.filter { stockLabel(it.stock) == "Rendah" }
        "habis" -> medicines.filter { stockLabel(it.stock) == "Habis" }
        else -> medicines
    }
    val sorted = when (sortMode) {
        "za" -> shown.sortedByDescending { it.name.lowercase() }
        // Stok terendah dulu (habis/menipis paling atas); nama A-Z sebagai
        // penyeimbang supaya urutan stabil bila stoknya sama.
        "low" -> shown.sortedWith(compareBy({ it.stock }, { it.name.lowercase() }))
        else -> shown.sortedBy { it.name.lowercase() }
    }
    Box(Modifier.fillMaxSize()) {
    // Satu LazyColumn untuk seluruh layar (header + daftar) supaya layar bisa
    // digulir penuh saat lanskap — header tetap + LazyColumn weight(1f) membuat
    // daftar 0dp dan layar tidak bisa digulir kalau header lebih tinggi dari layar.
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(CardGap),
        contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
    ) {
        item {
            Column {
            KartuHeaderGradasi(tr("Stok", "Stock"), Icons.Default.Medication, tr("${medicines.size} jenis obat tercatat", "${medicines.size} medicines recorded"))
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            SearchField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth()
            )
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(SpaceS))
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(SpaceL))
            // Kartu Import / Export / Template Excel — isi banyak obat sekaligus.
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                            Icon(Icons.Default.TableChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Import / Export Excel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(tr("Isi banyak obat sekaligus lewat file .xlsx", "Add many medicines at once via an .xlsx file"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(SpaceM))
                    Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { templateLauncher.launch("Template-Import-Stok-Obat.xlsx") },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Template", maxLines = 1)
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf(ExcelHelper.MIME_XLSX)) },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).format(Date())
                                exportLauncher.launch("Stok-Obat-$stamp.xlsx")
                            },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export", maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            // Ringkasan stok: total jenis, menipis (Rendah), habis.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${medicines.size}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(tr(" jenis obat", " medicines"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  •  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$countRendah", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                Text(tr(" menipis", " low"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  •  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$countHabis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(tr(" habis", " out"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                // Tombol Pilih/Batal (mode pilih banyak) + tombol urut 3 mode
                // (A-Z -> Z-A -> Terendah -> kembali ke A-Z).
                OutlinedButton(
                    onClick = {
                        if (selectMode) { selectMode = false; selectedMeds = emptyMap() }
                        else selectMode = true
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(if (selectMode) Icons.Default.Close else Icons.Default.Checklist, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (selectMode) tr("Batal", "Cancel") else tr("Pilih", "Select"), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        sortMode = when (sortMode) {
                            "az" -> "za"
                            "za" -> "low"
                            else -> "az"
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when (sortMode) {
                            "za" -> "Z-A"
                            "low" -> tr("Terendah", "Lowest")
                            else -> "A-Z"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(SpaceS))
            // Filter status stok (menggantikan legenda; jumlah per status terlihat di chip).
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "all" to tr("Semua (${medicines.size})", "All (${medicines.size})"),
                    "aman" to tr("Aman ($countAman)", "Safe ($countAman)"),
                    "sedang" to tr("Sedang ($countSedang)", "Medium ($countSedang)"),
                    "rendah" to tr("Rendah ($countRendah)", "Low ($countRendah)"),
                    "habis" to tr("Habis ($countHabis)", "Out ($countHabis)")
                ).forEach { (k, l) ->
                    FilterChip(selected = statusFilter == k, onClick = { statusFilter = k }, label = { Text(l) })
                }
            }
            // Bar seleksi (hanya di mode pilih banyak): jumlah terpilih + Hapus massal.
            if (selectMode) {
                Spacer(Modifier.height(SpaceS))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (selectedMeds.isEmpty()) tr("Ketuk kartu obat untuk memilih.", "Tap a medicine card to select.")
                        else tr("${selectedMeds.size} obat dipilih", "${selectedMeds.size} medicines selected"),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { showBulkDeleteConfirm = true },
                        enabled = selectedMeds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Hapus (${selectedMeds.size})", "Delete (${selectedMeds.size})"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(SpaceXS))
            }
        }
        if (sorted.isEmpty()) {
            item {
                Text(
                    when {
                        medicines.isEmpty() -> tr("Belum ada obat. Ketuk \"Tambah Obat\" atau Import Excel untuk mengisi stok.", "No medicines yet. Tap \"Add Medicine\" or Import Excel to fill the stock.")
                        statusFilter == "habis" -> tr("Tidak ada obat yang habis.", "No medicines are out of stock.")
                        statusFilter == "rendah" -> tr("Tidak ada obat yang stoknya rendah.", "No medicines have low stock.")
                        statusFilter == "sedang" -> tr("Tidak ada obat yang stoknya sedang.", "No medicines have medium stock.")
                        statusFilter == "aman" -> tr("Tidak ada obat yang stoknya aman.", "No medicines have safe stock.")
                        else -> tr("Tidak ada obat yang cocok.", "No matching medicines.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            }
        }
        itemsIndexed(sorted) { index, m ->
            
            ExpressiveCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().then(
                    if (selectMode) Modifier.clickable {
                        selectedMeds = if (selectedMeds.containsKey(m.id)) selectedMeds - m.id else selectedMeds + (m.id to m)
                    } else Modifier
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Di mode pilih banyak: checkbox ganti tombol Edit/Hapus,
                    // dan ketuk kartu = centang/batal centang.
                    if (selectMode) {
                        Checkbox(
                            checked = selectedMeds.containsKey(m.id),
                            onCheckedChange = {
                                selectedMeds = if (selectedMeds.containsKey(m.id)) selectedMeds - m.id else selectedMeds + (m.id to m)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            StockBadge(m.stock, showCount = false)
                        }
                        Spacer(Modifier.height(SpaceXS))
                        Text(
                            tr("${m.category} • ${m.unit} • Jual ${formatIDR(m.sellPrice)}", "${m.category} • ${m.unit} • Sell ${formatIDR(m.sellPrice)}") +
                                if (m.expiryDate != null) " • Exp ${formatDateID(m.expiryDate)}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${m.stock}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (m.stock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(m.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!selectMode) {
                        IconButton(onClick = { editing = m; showForm = true }) { Icon(Icons.Default.Edit, tr("Edit obat", "Edit medicine")) }
                        IconButton(onClick = { deleteTarget = m }) {
                            Icon(Icons.Default.Delete, tr("Hapus obat", "Delete medicine"), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            
        }
    }
        // FAB Tambah Obat: melayang di kanan bawah; naik sedikit saat
        // Snackbar tampil supaya tombol aksinya (mis. Urungkan) tidak
        // tertutup.
        val fabBottomPadding = if (snackbarHostState.currentSnackbarData != null) 84.dp else 16.dp
        AnimatedVisibility(
            visible = fabVisible,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = fabBottomPadding),
            enter = EnterTransition.None,
            exit = ExitTransition.None
        ) {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showForm = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(28.dp),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(tr("Tambah Obat", "Add Medicine"), style = MaterialTheme.typography.titleMedium) }
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (showForm) MedicineFormDialog(editing = editing, onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
    if (showImportDialog && importSummary != null) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(tr("Import Excel Selesai", "Excel Import Complete")) },
            text = { Text(importSummary.orEmpty()) },
            confirmButton = { TextButton(onClick = { showImportDialog = false }) { Text("OK") } }
        )
    }
    if (deleteTarget != null) {
        val target = checkNotNull(deleteTarget)
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Hapus Obat?", "Delete Medicine?")) },
            text = { Text(tr("Hapus ${target.name} dari stok?", "Remove ${target.name} from stock?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            repo.deleteMedicine(target.id)
                            reload()
                            val result = snackbarHostState.showSnackbar(
                                message = tr("Obat ${target.name} dihapus.", "${target.name} deleted."),
                                actionLabel = tr("Urungkan", "Undo"),
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                repo.createMedicine(
                                    MedicineArgs(
                                        target.name, target.category, target.unit,
                                        target.sellPrice, target.stock,
                                        target.expiryDate, target.barcode,
                                        wholesalePrice = target.wholesalePrice
                                    )
                                )
                                reload()
                                msg = "Obat ${target.name} dikembalikan."
                            }
                        }
                    }
                ) { Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
    // Konfirmasi hapus massal: hapus semua yang terpilih, lalu Snackbar
    // "Urungkan" membuat ulang obat-obat itu (pola yang sama seperti hapus satuan).
    if (showBulkDeleteConfirm) {
        val count = selectedMeds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(tr("Hapus $count Obat?", "Delete $count Medicines?")) },
            text = { Text(tr("Hapus $count obat yang dipilih dari stok?", "Remove the $count selected medicines from stock?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteConfirm = false
                        val toDelete = selectedMeds.values.toList()
                        selectMode = false
                        selectedMeds = emptyMap()
                        scope.launch {
                            toDelete.forEach { repo.deleteMedicine(it.id) }
                            reload()
                            val result = snackbarHostState.showSnackbar(
                                message = tr("${toDelete.size} obat dihapus.", "${toDelete.size} medicines deleted."),
                                actionLabel = tr("Urungkan", "Undo"),
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                toDelete.forEach {
                                    repo.createMedicine(
                                        MedicineArgs(
                                            it.name, it.category, it.unit,
                                            it.sellPrice, it.stock,
                                            it.expiryDate, it.barcode,
                                            wholesalePrice = it.wholesalePrice
                                        )
                                    )
                                }
                                reload()
                                msg = "${toDelete.size} obat dikembalikan."
                            }
                        }
                    }
                ) { Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteConfirm = false }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}


// Pemilih opsi berupa bottom sheet dengan daftar radio (dipakai Kategori & Satuan di form Stok).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionPickerSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(options) { opt ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(opt) }.padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(opt, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        RadioButton(selected = opt == selected, onClick = { onSelect(opt) })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}


@Composable
fun MedicineFormDialog(editing: Medicine?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var category by remember(editing) { mutableStateOf(editing?.category ?: "Umum") }
    var unit by remember(editing) { mutableStateOf(editing?.unit ?: "Strip") }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showUnitSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Ketukan pada field read-only ditangkap lewat interactionSource:
    // Modifier.clickable di OutlinedTextField tidak jalan karena field
    // menelan ketukan untuk fokus. Pola ini sama seperti dropdown bawaan.
    val categoryInteraction = remember { MutableInteractionSource() }
    val unitInteraction = remember { MutableInteractionSource() }
    val expiryInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(categoryInteraction) {
        categoryInteraction.interactions.collect { if (it is PressInteraction.Press) showCategorySheet = true }
    }
    LaunchedEffect(unitInteraction) {
        unitInteraction.interactions.collect { if (it is PressInteraction.Press) showUnitSheet = true }
    }
    LaunchedEffect(expiryInteraction) {
        expiryInteraction.interactions.collect { if (it is PressInteraction.Press) showDatePicker = true }
    }
    var sell by remember(editing) { mutableStateOf(TextFieldValue(formatDecimal(editing?.sellPrice?.toString() ?: ""))) }
    // Harga jual grosir: boleh kosong (= belum diatur, nilai 0); ditampilkan
    // kosong bila 0 supaya tidak tertukar dengan harga beneran.
    var grosir by remember(editing) { mutableStateOf(TextFieldValue(formatDecimal(editing?.wholesalePrice?.takeIf { it > 0 }?.toString() ?: ""))) }
    var stock by remember(editing) { mutableStateOf(editing?.stock?.toString() ?: "") }
    var expiry by remember(editing) { mutableStateOf(editing?.expiryDate ?: "") }
    var formError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Lebar sheet adaptif (lihat adaptiveSheetMaxWidth): selebar layar di HP potret, 360dp di HP lanskap, 640dp di tablet.
        sheetMaxWidth = adaptiveSheetMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) tr("Tambah Obat", "Add Medicine") else tr("Edit Obat", "Edit Medicine"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Tutup", "Close")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Formulir kartu berbagian (TO1), warna kartu sama seperti
            // TO1 — pilihan Achmad 2026-11-04. Berlaku untuk Tambah
            // maupun Edit Obat. (Strip margin langsung TO2 dihapus
            // 2026-11-10 atas permintaan Achmad.)
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
                    Text(tr("IDENTITAS", "IDENTITY"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(name, { name = it }, label = { Text(tr("Nama Obat", "Medicine Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                        OutlinedTextField(
                            value = category, onValueChange = {}, label = { Text(tr("Kategori", "Category")) },
                            readOnly = true, singleLine = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih kategori") },
                            interactionSource = categoryInteraction,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unit, onValueChange = {}, label = { Text(tr("Satuan", "Unit")) },
                            readOnly = true, singleLine = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = tr("Pilih satuan", "Pick a unit")) },
                            interactionSource = unitInteraction,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
                    Text(tr("HARGA", "PRICE"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(sell, { sell = formatDecimalFieldValue(it) }, label = { Text(tr("Harga Jual", "Sell Price")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(grosir, { grosir = formatDecimalFieldValue(it) }, label = { Text(tr("Harga Jual Grosir (boleh kosong)", "Wholesale Sell Price (optional)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
                    Text(tr("STOK & KADALUWARSA", "STOCK & EXPIRY"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                        OutlinedTextField(stock, { stock = it.filter { c -> c.isDigit() } }, label = { Text(tr("Stok", "Stock")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = if (expiry.isBlank()) "" else formatDateID(expiry),
                            onValueChange = {},
                            label = { Text(tr("Kadaluwarsa (boleh kosong)", "Expiry (optional)")) },
                            readOnly = true, singleLine = true,
                            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = tr("Pilih tanggal", "Pick a date")) },
                            interactionSource = expiryInteraction,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(SpaceS))
            if (formError.isNotEmpty()) {
                Text(formError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f).height(52.dp)) { Text(tr("Batal", "Cancel")) }
                Button(
                    onClick = {
                        scope.launch {
                            val cat = category.ifBlank { "Umum" }
                            val unt = unit.ifBlank { "Strip" }
                            val s = parseDecimal(sell.text)
                            val st = stock.toIntOrNull() ?: 0
                            val exp = expiry.ifBlank { null }
                            // Barcode tidak lagi diisi lewat formulir (kolomnya
                            // dihapus atas permintaan Achmad 2026-10-29); saat edit,
                            // barcode lama (mis. dari impor Excel) dipertahankan
                            // supaya pencocokan impor berikutnya tidak rusak.
                            val bc = editing?.barcode
                            val result = if (editing == null) {
                                repo.createMedicine(MedicineArgs(name, cat, unt, s, st, exp, bc, wholesalePrice = parseDecimal(grosir.text)))
                            } else {
                                repo.updateMedicine(UpdateMedicineArgs(editing.id, name, cat, unt, s, st, exp, bc, wholesalePrice = parseDecimal(grosir.text)))
                            }
                            if (result is ApiResult.Success) {
                                onSaved()
                            } else {
                                formError = (result as? ApiResult.Error)?.message ?: tr("Gagal menyimpan obat.", "Failed to save the medicine.")
                            }
                        }
                    },
                    enabled = name.isNotBlank() && parseDecimal(sell.text) > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text(tr("Simpan", "Save")) }
            }
        }
    }
    if (showDatePicker) {
        // Tanggal dinormalisasi ke tengah malam UTC supaya DatePicker tidak
        // menggeser tanggal 1 hari (parseISODate memakai timezone HP).
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(expiry))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { expiry = dayKeyID(Date(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { expiry = ""; showDatePicker = false }) { Text(tr("Hapus", "Clear")) }
                TextButton(onClick = { showDatePicker = false }) { Text(tr("Batal", "Cancel")) }
            }
        ) {
            // DatePicker bertumpuk kalau tinggi layar terbatas (lanskap):
            // bungkus dengan scroll vertikal supaya DatePicker mengukur tinggi
            // alaminya dan dialog bisa digulir, bukan memampatkan isinya.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = datePickerState)
            }
        }
    }
    if (showCategorySheet) {
        // Nilai lama yang tidak ada di daftar tetap ditampilkan di atas agar tidak hilang.
        val options = if (category in CATEGORIES) CATEGORIES else listOf(category) + CATEGORIES
        OptionPickerSheet(title = tr("Kategori", "Category"), options = options, selected = category, onSelect = { category = it; showCategorySheet = false }, onDismiss = { showCategorySheet = false })
    }
    if (showUnitSheet) {
        val options = if (unit in UNITS) UNITS else listOf(unit) + UNITS
        OptionPickerSheet(title = tr("Satuan", "Unit"), options = options, selected = unit, onSelect = { unit = it; showUnitSheet = false }, onDismiss = { showUnitSheet = false })
    }
}

// ---------- Keuangan ----------
