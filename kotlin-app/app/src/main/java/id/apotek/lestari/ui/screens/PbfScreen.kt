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

// Layar Faktur PBF dan formulir catat pembelian.

class PurchaseItemRow {
    var name by mutableStateOf("")
    var unit by mutableStateOf("Strip")
    var qty by mutableStateOf("")
    var price by mutableStateOf(TextFieldValue(""))
}


@Composable
fun PbfScreen() {
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(purchases) { if (purchases.isNotEmpty() && !listEntered) listEntered = true }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Purchase?>(null) }
    var paidTarget by remember { mutableStateOf<Purchase?>(null) }
    var dueTarget by remember { mutableStateOf<Purchase?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Muat ulang setiap kali layar kembali tampil (ON_RESUME), pola yang
    // sama seperti Obat Terlaris.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) {
        (repo.listPurchases() as? ApiResult.Success)?.let { purchases = it.data.purchases }
    }

    // Tanggal pembelian disimpan sebagai kunci hari "yyyy-MM-dd", jadi
    // pengelompokan per hari cukup membandingkan string (tanpa konversi).
    val todayKey = dayKeyID(Date())
    val yesterdayKey = dayKeyID(Date(System.currentTimeMillis() - 86_400_000L))
    val monthPrefix = todayKey.substring(0, 7)
    val monthLabel = SimpleDateFormat("MMMM yyyy", appLocale()).format(Date())
    // Ringkasan belanja hanya menghitung pembelian CASH (permintaan Achmad
    // 2026-10-30): pembelian Kredit belum masuk total Hari Ini/Bulan Ini.
    // Daftar riwayat di bawah tetap menampilkan semuanya.
    val todayPurchases = purchases.filter { it.date == todayKey && it.paymentType == "CASH" }
    val todayTotal = todayPurchases.sumOf { it.total }
    val monthTotal = purchases.filter { it.date.startsWith(monthPrefix) && it.paymentType == "CASH" }.sumOf { it.total }
    val sortedGroups = purchases.groupBy { it.date }.entries.sortedByDescending { it.key }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SpaceL)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Faktur PBF", "PBF Invoices"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(SpaceL))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(SpaceL))
                DemoBanner()
                // Ringkasan belanja (desain PF1, pilihan Achmad 2026-11-04):
                // dua kartu netral sejajar menggantikan blok biru lebar —
                // Hari Ini dengan jumlah pembelian, Bulan Ini dengan nama
                // bulan berjalan. Tetap hanya menghitung pembelian CASH.
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceM), modifier = Modifier.fillMaxWidth()) {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("BELANJA HARI INI", "TODAY'S PURCHASES"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(SpaceXS))
                            AnimatedAmount(todayTotal, MaterialTheme.typography.titleLarge, MaterialTheme.colorScheme.onSurface)
                            Text(tr("${todayPurchases.size} pembelian", "${todayPurchases.size} purchases"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(tr("BELANJA BULAN INI", "THIS MONTH'S PURCHASES"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(SpaceXS))
                            AnimatedAmount(monthTotal, MaterialTheme.typography.titleLarge, MaterialTheme.colorScheme.onSurface)
                            Text(monthLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Kartu Kredit Belum Lunas (desain P1C, pilihan Achmad
            // 2026-11-04): total pembelian Kredit yang belum dilunasi
            // (paymentType masih KREDIT) + rincian terdekat jatuh temponya.
            // Hanya tampil bila memang ada kredit yang belum lunas.
            val outstanding = purchases.filter { it.paymentType == "KREDIT" }
            if (outstanding.isNotEmpty()) {
                item {
                    val outstandingTotal = outstanding.sumOf { it.total }
                    val outstandingSorted = outstanding.sortedWith(compareBy({ it.dueDate.isBlank() }, { it.dueDate }))
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, containerColor = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("KREDIT BELUM LUNAS", "OUTSTANDING CREDIT"), color = warningAmber(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(formatIDR(outstandingTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Spacer(Modifier.height(SpaceS))
                            outstandingSorted.take(3).forEach { p ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { expanded = p.id }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(8.dp).background(warningAmber(), CircleShape))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.pbfName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        val dueText = if (p.dueDate.isNotBlank()) {
                                            val daysLeft = dueDaysLeft(p.dueDate)
                                            if (daysLeft != null) {
                                                val lbl = when {
                                                    daysLeft < 0L -> tr("Lewat ${-daysLeft} hari", "${-daysLeft} days overdue")
                                                    daysLeft == 0L -> tr("Jatuh tempo hari ini", "Due today")
                                                    daysLeft == 1L -> tr("Besok jatuh tempo", "Due tomorrow")
                                                    else -> tr("$daysLeft hari lagi", "Due in $daysLeft days")
                                                }
                                                tr("Jatuh tempo ${formatDateID(p.dueDate)} • $lbl", "Due ${formatDateID(p.dueDate)} • $lbl")
                                            } else formatDateID(p.dueDate)
                                        } else tr("Tanpa jatuh tempo", "No due date")
                                        Text(dueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
                                    }
                                    Text(formatIDR(p.total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                            if (outstandingSorted.size > 3) {
                                Text(tr("+${outstandingSorted.size - 3} pembelian kredit lainnya", "+${outstandingSorted.size - 3} more credit purchases"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
                            }
                            Spacer(Modifier.height(SpaceS))
                            Text(tr("Ketuk baris untuk membuka kartunya — Tandai Lunas dan Atur Jatuh Tempo ada di detail kartu.", "Tap a row to open its card — Mark as Paid and Set Due Date are in the card details."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
                        }
                    }
                }
            }
            if (purchases.isEmpty()) {
                item {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Desain PF1: ikon truk di lingkaran biru, judul,
                            // lalu tombol aksi yang membuka formulir yang
                            // sama dengan FAB.
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp).size(28.dp))
                            }
                            Spacer(Modifier.height(SpaceM))
                            Text(tr("Belum ada catatan pembelian", "No purchase records yet"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(SpaceXS))
                            Text(tr("Catat belanja dari PBF lewat tombol + di kanan bawah — ini murni catatan, tidak mengubah stok dan kas.", "Record PBF purchases with the + button at the bottom right — these are records only and do not change stock or cash."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            sortedGroups.forEach { (day, dayPurchases) ->
                item(key = "pbf-header-$day") {
                    val label = when (day) {
                        todayKey -> tr("Hari ini", "Today")
                        yesterdayKey -> tr("Kemarin", "Yesterday")
                        else -> formatDateID(day)
                    }
                    val dayTotal = dayPurchases.sumOf { it.total }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("${dayPurchases.size} pembelian • ${formatIDR(dayTotal)}", "${dayPurchases.size} purchases • ${formatIDR(dayTotal)}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(dayPurchases, key = { _, item -> "pbf-${item.id}" }) { index, p ->
                    AnimatedVisibility(
                        visible = listEntered,
                        enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                            slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                            expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                        exit = fadeOut()
                    ) {
                    val isOpen = expanded == p.id
                    ExpressiveCard(
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)).clickable { expanded = if (isOpen) null else p.id }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.pbfName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val namesPreview = p.items.take(3).joinToString(", ") { it.medicineName }
                                    val suffix = if (p.items.size > 3) " +${p.items.size - 3} lagi" else ""
                                    Text(tr("${p.items.size} obat • $namesPreview$suffix", "${p.items.size} medicines • $namesPreview$suffix"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(8.dp))
                                // Chip penanda cara bayar, di tengah antara nama
                                // PBF dan nominal (permintaan Achmad 2026-10-30).
                                PurchasePaymentChip(p.paymentType)
                                Spacer(Modifier.width(10.dp))
                                Text(formatIDR(p.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Icon(if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Info jatuh tempo untuk pembelian Kredit yang
                            // tanggalnya diisi (permintaan Achmad 2026-10-30):
                            // tanggal + sisa hari. Merah bila sudah lewat /
                            // hari ini, oranye bila <= 7 hari, abu bila masih
                            // lama. Hilang sendiri setelah Tandai Lunas
                            // (paymentType berubah jadi CASH).
                            if (p.paymentType == "KREDIT" && p.dueDate.isNotBlank()) {
                                val daysLeft = dueDaysLeft(p.dueDate)
                                if (daysLeft != null) {
                                    val (dueLabel, dueColor) = when {
                                        daysLeft < 0L -> tr("Lewat ${-daysLeft} hari", "${-daysLeft} days overdue") to MaterialTheme.colorScheme.error
                                        daysLeft == 0L -> tr("Jatuh tempo hari ini", "Due today") to MaterialTheme.colorScheme.error
                                        daysLeft == 1L -> tr("Besok jatuh tempo", "Due tomorrow") to warningAmber()
                                        daysLeft <= 7L -> tr("$daysLeft hari lagi", "Due in $daysLeft days") to warningAmber()
                                        else -> "$daysLeft hari lagi" to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                        Icon(Icons.Default.Schedule, null, tint = dueColor, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(tr("Jatuh tempo ${formatDateID(p.dueDate)} • $dueLabel", "Due ${formatDateID(p.dueDate)} • $dueLabel"), style = MaterialTheme.typography.bodySmall, color = dueColor)
                                    }
                                }
                            }
                            if (isOpen) {
                                Spacer(Modifier.height(SpaceM))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(SpaceM))
                                if (p.invoiceNo.isNotBlank()) {
                                    Text(tr("No. Faktur: ${p.invoiceNo}", "Invoice No.: ${p.invoiceNo}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(SpaceS))
                                }
                                p.items.forEach { pItem ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(pItem.medicineName, style = MaterialTheme.typography.bodyMedium)
                                            Text("${pItem.qty} ${pItem.unit} × ${formatIDR(pItem.buyPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(formatIDR(pItem.subtotal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                // Blok ringkasan KP1 (pilihan Achmad 2026-11-04):
                                // Subtotal item selalu tampil supaya hitungan
                                // menuju Total transparan (sebelumnya hanya PPN
                                // dan total di kepala kartu yang terlihat,
                                // subtotalnya tidak pernah ditampilkan).
                                Spacer(Modifier.height(SpaceS))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(SpaceS))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr("Subtotal", "Subtotal"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(formatIDR(p.items.sumOf { it.subtotal }), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                if (p.ppn > 0) {
                                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("PPN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        Text(formatIDR(p.ppn), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr("Total", "Total"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(formatIDR(p.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(SpaceS))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (p.paymentType == "KREDIT") Arrangement.SpaceBetween else Arrangement.End) {
                                    // Tandai Lunas hanya untuk pembelian Kredit:
                                    // mengubahnya jadi Cash supaya masuk ringkasan.
                                    if (p.paymentType == "KREDIT") {
                                        TextButton(onClick = { paidTarget = p }) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr("Tandai Lunas", "Mark as Paid"), color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    TextButton(onClick = { deleteTarget = p }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (p.paymentType == "KREDIT") {
                                    // Atur Jatuh Tempo untuk catatan Kredit
                                    // yang sudah tersimpan (permintaan Achmad
                                    // 2026-11-04): membuka pemilih tanggal
                                    // (editor di bawah); bisa juga menghapus
                                    // tanggalnya dari sana.
                                    Row(Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { dueTarget = p }) {
                                            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr("Atur Jatuh Tempo", "Set Due Date"), color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 88.dp, bottom = 16.dp))
        // Tombol melayang Catat Pembelian (desain P1C) menggantikan
        // tombol lebar di bawah kartu ringkasan.
        FloatingActionButton(
            onClick = { showForm = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, tr("Catat Pembelian", "Record Purchase"))
        }
    }

    if (showForm) {
        PurchaseFormSheet(
            onDismiss = { showForm = false },
            onSaved = {
                showForm = false
                resumeKey++
                scope.launch { snackbarHostState.showSnackbar(tr("Pembelian tercatat.", "Purchase recorded.")) }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Hapus Catatan Pembelian?", "Delete Purchase Record?")) },
            text = { Text(tr("Catatan belanja dari ${target.pbfName} (${formatDateID(target.date)}) sebesar ${formatIDR(target.total)} akan dihapus. Ini hanya menghapus catatan — stok dan kas tidak berubah.", "The purchase record from ${target.pbfName} (${formatDateID(target.date)}) of ${formatIDR(target.total)} will be deleted. This only deletes the record — stock and cash are unchanged.")) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val res = repo.deletePurchase(target.id)
                        if (res is ApiResult.Success) {
                            if (expanded == target.id) expanded = null
                            resumeKey++
                            snackbarHostState.showSnackbar(tr("Catatan pembelian dihapus.", "Purchase record deleted."))
                        } else {
                            snackbarHostState.showSnackbar((res as? ApiResult.Error)?.message ?: tr("Gagal menghapus", "Failed to delete"))
                        }
                    }
                }) { Text(tr("Hapus", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }

    // Simpan/hapus tanggal jatuh tempo dari editor di detail pembelian.
    val saveDueDate: (Purchase, String) -> Unit = { target, newDue ->
        scope.launch {
            val res = repo.setPurchaseDueDate(target.id, newDue)
            if (res is ApiResult.Success) {
                resumeKey++
                snackbarHostState.showSnackbar(
                    if (newDue.isBlank()) tr("Jatuh tempo dihapus.", "Due date cleared.")
                    else tr("Jatuh tempo disimpan.", "Due date saved.")
                )
            } else {
                snackbarHostState.showSnackbar((res as? ApiResult.Error)?.message ?: tr("Gagal menyimpan jatuh tempo", "Failed to save the due date"))
            }
        }
    }

    if (dueTarget != null) {
        val target = checkNotNull(dueTarget)
        // Pemilih tanggal jatuh tempo untuk catatan yang sudah tersimpan:
        // pola sama seperti pemilih di formulir (tengah malam UTC supaya
        // tanggal tidak bergeser, "Hapus" untuk mengosongkan). Tanggal masa
        // depan memang tujuan fitur ini, jadi tanpa NoFutureDates.
        val dueEditState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(target.dueDate.ifBlank { target.date }))
        DatePickerDialog(
            onDismissRequest = { dueTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    val newDue = dueEditState.selectedDateMillis?.let { dayKeyID(Date(it)) } ?: target.dueDate
                    dueTarget = null
                    saveDueDate(target, newDue)
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (target.dueDate.isNotBlank()) {
                        TextButton(onClick = {
                            dueTarget = null
                            saveDueDate(target, "")
                        }) { Text(tr("Hapus", "Clear")) }
                    }
                    TextButton(onClick = { dueTarget = null }) { Text(tr("Batal", "Cancel")) }
                }
            }
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = dueEditState)
            }
        }
    }

    paidTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { paidTarget = null },
            title = { Text(tr("Tandai Lunas?", "Mark as Paid?")) },
            text = { Text(tr("Pembelian dari ${target.pbfName} (${formatDateID(target.date)}) sebesar ${formatIDR(target.total)} akan diubah jadi Cash. Nominalnya masuk ke ringkasan Belanja Hari Ini/Bulan Ini mengikuti tanggal belinya.", "The purchase from ${target.pbfName} (${formatDateID(target.date)}) of ${formatIDR(target.total)} will be changed to Cash. Its amount joins the Today/This Month Purchases summary following its purchase date.")) },
            confirmButton = {
                TextButton(onClick = {
                    paidTarget = null
                    scope.launch {
                        val res = repo.markPurchasePaid(target.id)
                        if (res is ApiResult.Success) {
                            resumeKey++
                            snackbarHostState.showSnackbar(tr("Pembelian ditandai lunas.", "Purchase marked as paid."))
                        } else {
                            snackbarHostState.showSnackbar((res as? ApiResult.Error)?.message ?: tr("Gagal menandai lunas", "Failed to mark as paid"))
                        }
                    }
                }) { Text(tr("Tandai Lunas", "Mark as Paid"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { paidTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}


@Composable
fun PurchaseFormSheet(onDismiss: () -> Unit, onSaved: () -> Unit) {
    var pbfName by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var ppn by remember { mutableStateOf(TextFieldValue("")) }
    var paymentType by remember { mutableStateOf("CASH") }
    var dueDate by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(dayKeyID(Date())) }
    var rows by remember { mutableStateOf(listOf(PurchaseItemRow())) }
    var activeRow by remember { mutableStateOf<PurchaseItemRow?>(null) }
    var unitPickerRow by remember { mutableStateOf<PurchaseItemRow?>(null) }
    var meds by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Saran nama obat dari data Stok (hanya saran ketik — memilihnya TIDAK
    // mengubah stok obat yang bersangkutan). Memilih saran juga mengisi
    // satuan baris ini dengan satuan obat itu di Stok.
    LaunchedEffect(Unit) {
        (repo.listMedicines() as? ApiResult.Success)?.let { res -> meds = res.data.medicines }
    }

    // Field tanggal read-only: ketukan ditangkap lewat interactionSource
    // (pola yang sama seperti field Kadaluwarsa di formulir obat).
    val dateInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(dateInteraction) {
        dateInteraction.interactions.collect { if (it is PressInteraction.Press) showDatePicker = true }
    }
    // Field Tanggal Jatuh Tempo: pola read-only + interactionSource yang sama.
    val dueInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(dueInteraction) {
        dueInteraction.interactions.collect { if (it is PressInteraction.Press) showDuePicker = true }
    }

    // Total Belanja = subtotal semua obat + PPN (PPN diketik sebagai nominal
    // rupiah; kosong = 0).
    val itemsTotal = rows.sumOf { (it.qty.toIntOrNull() ?: 0) * parseDecimal(it.price.text) }
    val total = itemsTotal + parseDecimal(ppn.text)
    val valid = pbfName.isNotBlank() && rows.all {
        it.name.isNotBlank() && (it.qty.toIntOrNull() ?: 0) > 0 && parseDecimal(it.price.text) > 0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = adaptiveSheetMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Catat Pembelian PBF", "Record PBF Purchase"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, tr("Tutup", "Close")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Formulir berbagian kartu (desain F1, pilihan Achmad
            // 2026-11-04): FAKTUR, ITEM BELANJA, RINGKASAN.
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
                    Text(tr("FAKTUR", "INVOICE"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(pbfName, { pbfName = it }, label = { Text(tr("Nama PBF", "PBF Name")) }, placeholder = { Text(tr("cth: Kimia Farma", "e.g. Kimia Farma")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(invoiceNo, { invoiceNo = it }, label = { Text(tr("Nomor Faktur (boleh kosong)", "Invoice Number (optional)")) }, placeholder = { Text(tr("mis. nomor faktur dari PBF", "e.g. the invoice number from the PBF")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = formatDateID(date), onValueChange = {}, label = { Text(tr("Tanggal Beli", "Purchase Date")) },
                    readOnly = true, singleLine = true,
                    trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = tr("Pilih tanggal", "Pick a date")) },
                    interactionSource = dateInteraction,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Pembayaran", "Payment"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    PriceModeChip("Cash", selected = paymentType == "CASH") { paymentType = "CASH" }
                    Spacer(Modifier.width(6.dp))
                    PriceModeChip(tr("Kredit", "Credit"), selected = paymentType == "KREDIT") { paymentType = "KREDIT" }
                }
                // Tanggal jatuh tempo hanya untuk pembelian Kredit (permintaan
                // Achmad 2026-10-30); boleh kosong — tanpa tanggal, kartu tidak
                // menampilkan hitung mundur. Untuk Cash field ini tidak tampil.
                if (paymentType == "KREDIT") {
                    OutlinedTextField(
                        value = if (dueDate.isBlank()) "" else formatDateID(dueDate), onValueChange = {}, label = { Text(tr("Tanggal Jatuh Tempo (boleh kosong)", "Due Date (optional)")) },
                        placeholder = { Text(tr("Pilih tanggal", "Pick a date")) },
                        readOnly = true, singleLine = true,
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = tr("Pilih tanggal jatuh tempo", "Pick the due date")) },
                        interactionSource = dueInteraction,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                }
            }
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
                    Text(tr("ITEM BELANJA", "PURCHASED ITEMS"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                rows.forEachIndexed { index, row ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Obat ${index + 1}", "Medicine ${index + 1}"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (rows.size > 1) {
                                IconButton(
                                    onClick = { rows = rows - row; if (activeRow == row) activeRow = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, tr("Hapus obat ini", "Delete this item"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        // Pemilih satuan per baris: ketukan ditangkap lewat
                        // interactionSource baris ini (pola field read-only yang
                        // sama seperti Tanggal Beli), membuka OptionPickerSheet
                        // untuk baris yang bersangkutan. Sumber + efek DIKUNCI ke
                        // objek baris (audit N3, 2026-10-30): tanpa kunci, slot
                        // remember bergeser setelah baris lain dihapus dan
                        // ketukan bisa membuka pemilih untuk baris yang salah.
                        val unitInteraction = remember(row) { MutableInteractionSource() }
                        LaunchedEffect(row, unitInteraction) {
                            unitInteraction.interactions.collect { if (it is PressInteraction.Press) unitPickerRow = row }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                            OutlinedTextField(
                                value = row.name,
                                onValueChange = { row.name = it; activeRow = row },
                                label = { Text(tr("Nama Obat", "Medicine Name")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.unit, onValueChange = {}, label = { Text(tr("Satuan", "Unit")) },
                                readOnly = true, singleLine = true,
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = tr("Pilih satuan", "Pick a unit")) },
                                interactionSource = unitInteraction,
                                modifier = Modifier.width(118.dp)
                            )
                        }
                        if (activeRow == row && row.name.isNotBlank()) {
                            val suggestions = meds.filter {
                                it.name.contains(row.name.trim(), ignoreCase = true) && !it.name.equals(row.name.trim(), ignoreCase = true)
                            }.take(5)
                            if (suggestions.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                                    suggestions.forEach { s ->
                                        AssistChip(onClick = { row.name = s.name; row.unit = s.unit }, label = { Text(s.name) })
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(SpaceS))
                        Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                            OutlinedTextField(
                                value = row.qty,
                                onValueChange = { row.qty = it.filter { c -> c.isDigit() } },
                                label = { Text(tr("Jumlah", "Quantity")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.price,
                                onValueChange = { row.price = formatDecimalFieldValue(it) },
                                label = { Text(tr("Harga Satuan", "Unit Price")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                        }
                        val rowSubtotal = (row.qty.toIntOrNull() ?: 0) * parseDecimal(row.price.text)
                        if (rowSubtotal > 0) {
                            Text("Subtotal: ${formatIDR(rowSubtotal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { rows = rows + PurchaseItemRow(); activeRow = null },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Tambah Obat", "Add Item"))
                }
                }
            }
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(SpaceS)) {
                    Text(tr("RINGKASAN", "SUMMARY"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(ppn, { ppn = formatDecimalFieldValue(it) }, label = { Text(tr("Jumlah PPN (Rp)", "PPN/VAT Amount (Rp)")) }, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Total Belanja", "Total Purchases"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(formatIDR(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                }
            }
            if (formError.isNotEmpty()) Text(formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    scope.launch {
                        formError = ""
                        val res = repo.createPurchase(
                            CreatePurchaseArgs(
                                pbfName = pbfName,
                                date = date,
                                items = rows.map { CreatePurchaseItemArgs(it.name, it.qty.toIntOrNull() ?: 0, parseDecimal(it.price.text), it.unit) },
                                invoiceNo = invoiceNo,
                                ppn = parseDecimal(ppn.text),
                                paymentType = paymentType,
                                dueDate = if (paymentType == "KREDIT") dueDate else ""
                            )
                        )
                        if (res is ApiResult.Success) onSaved()
                        else formError = (res as? ApiResult.Error)?.message ?: tr("Gagal menyimpan", "Failed to save")
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(tr("Simpan", "Save")) }
        }
    }
    if (showDatePicker) {
        // Sama seperti pemilih tanggal kadaluwarsa: normalisasi ke tengah
        // malam UTC supaya tanggal tidak bergeser 1 hari.
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(date), selectableDates = NoFutureDates)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = dayKeyID(Date(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(tr("Batal", "Cancel")) }
            }
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = datePickerState)
            }
        }
    }
    if (showDuePicker) {
        // Pemilih tanggal jatuh tempo: pola yang sama seperti Tanggal Beli.
        // "Hapus" mengosongkan tanggal (jatuh tempo memang boleh kosong).
        val duePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(dueDate.ifBlank { date }))
        DatePickerDialog(
            onDismissRequest = { showDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    duePickerState.selectedDateMillis?.let { dueDate = dayKeyID(Date(it)) }
                    showDuePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { dueDate = ""; showDuePicker = false }) { Text(tr("Hapus", "Clear")) }
                    TextButton(onClick = { showDuePicker = false }) { Text(tr("Batal", "Cancel")) }
                }
            }
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = duePickerState)
            }
        }
    }
    // Pemilih satuan untuk baris yang diketuk — daftar UNITS yang sama
    // persis dengan formulir Stok (keputusan Achmad 2026-10-29: satuan PBF
    // disamakan dengan Stok; sebelumnya sempat hanya Botol/Box).
    unitPickerRow?.let { target ->
        val options = if (target.unit in UNITS) UNITS else listOf(target.unit) + UNITS
        OptionPickerSheet(title = tr("Satuan", "Unit"), options = options, selected = target.unit, onSelect = { target.unit = it; unitPickerRow = null }, onDismiss = { unitPickerRow = null })
    }
}
