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

// Layar Pengingat (stok menipis/habis, kedaluwarsa, tagihan PBF).

@Composable
fun PengingatScreen(onGoStok: () -> Unit, onGoPbf: () -> Unit) {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var dueBills by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var noDateBills by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var window by remember { mutableStateOf(90) }
    var filter by remember { mutableStateOf("all") } // all | expiry | low | critical
    var listEntered by remember { mutableStateOf(false) }
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME, pola yang sama
    // seperti Laba) supaya tagihan yang baru ditandai lunas di Faktur PBF
    // langsung hilang dari sini.
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
        (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines }
        // Tagihan PBF: pembelian Kredit yang jatuh temponya <= 7 hari lagi
        // atau sudah lewat (pilihan Achmad 2026-10-30, saran no. 1) —
        // diurutkan dari jatuh tempo paling awal (paling mendesak di atas).
        val purchases = (repo.listPurchases() as? ApiResult.Success)?.data?.purchases.orEmpty()
        dueBills = purchases.filter { p ->
            p.paymentType == "KREDIT" && p.dueDate.isNotBlank() && (dueDaysLeft(p.dueDate) ?: Long.MAX_VALUE) <= 7L
        }.sortedBy { it.dueDate }
        // Tagihan Kredit TANPA tanggal jatuh tempo (permintaan Achmad
        // 2026-11-04): sebelumnya tidak tampil di mana-mana selain daftar
        // Faktur PBF, jadi gampang terlupakan. Urutan mengikuti daftar
        // pembelian (tanggal beli terbaru di atas).
        noDateBills = purchases.filter { p ->
            p.paymentType == "KREDIT" && p.dueDate.isBlank()
        }
    }
    LaunchedEffect(medicines) { if (medicines.isNotEmpty() && !listEntered) listEntered = true }
    val expired = medicines.filter { (daysUntilExpiry(it.expiryDate) ?: Int.MAX_VALUE) < 0 }
    val withinWindow = medicines.filter { val d = daysUntilExpiry(it.expiryDate); d != null && d >= 0 && d <= window }
    val low = medicines.filter { it.stock in 1..9 }
    val critical = medicines.filter { it.stock == 0 }
    val priority = medicines.filter { m ->
        val d = daysUntilExpiry(m.expiryDate)
        m.stock < 10 || (d != null && d <= window)
    }.sortedWith(
        compareBy<Medicine> { if (it.stock == 0) 0 else 1 }
            .thenBy { daysUntilExpiry(it.expiryDate) ?: Int.MAX_VALUE }
            .thenBy { it.stock }
    )
    val shown = priority.filter { m ->
        val d = daysUntilExpiry(m.expiryDate)
        when (filter) {
            "expiry" -> d != null && d <= window
            "low" -> m.stock in 1..9
            "critical" -> m.stock == 0
            else -> true
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(SpaceL)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(tr("Pengingat", "Reminders"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            Text(tr("MASA KADALUARSA", "EXPIRY WINDOW"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                listOf(30, 60, 90).forEach { w ->
                    FilterChip(selected = window == w, onClick = { window = w }, label = { Text(tr("$w Hari", "$w Days")) })
                }
            }
        }
        item {
            // Ringkasan 2x2: empat kartu kecil (ikon + angka besar + label),
            // supaya tiap angka lebih menonjol dan layarnya tidak monoton.
            Column(verticalArrangement = Arrangement.spacedBy(SpaceM)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceM)) {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        ReminderCount(Icons.Default.EventBusy, "EXPIRED", "${expired.size}", if (expired.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth().padding(16.dp))
                    }
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        ReminderCount(Icons.Default.Schedule, "≤ $window HARI", "${withinWindow.size}", MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceM)) {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        ReminderCount(Icons.Default.Inventory, tr("RENDAH", "LOW"), "${low.size}", MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth().padding(16.dp))
                    }
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.weight(1f)) {
                        ReminderCount(Icons.Default.PriorityHigh, tr("HABIS", "OUT"), "${critical.size}", if (critical.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth().padding(16.dp))
                    }
                }
            }
        }
        if (dueBills.isNotEmpty()) {
            item {
                // Seksi tagihan PBF: nama PBF ditulis lengkap + nominal +
                // sisa hari (permintaan Achmad 2026-10-30 supaya informasinya
                // lengkap). Kartu bisa diketuk untuk membuka Faktur PBF.
                Text(tr("TAGIHAN PBF JATUH TEMPO", "PBF BILLS DUE"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(SpaceS))
                Column(verticalArrangement = Arrangement.spacedBy(SpaceM)) {
                    dueBills.forEach { p ->
                        val daysLeft = dueDaysLeft(p.dueDate) ?: 0L
                        val (dueLabel, dueColor) = when {
                            daysLeft < 0L -> tr("Lewat ${-daysLeft} hari", "${-daysLeft} days overdue") to MaterialTheme.colorScheme.error
                            daysLeft == 0L -> tr("Jatuh tempo hari ini", "Due today") to MaterialTheme.colorScheme.error
                            daysLeft == 1L -> tr("Besok jatuh tempo", "Due tomorrow") to warningAmber()
                            else -> tr("$daysLeft hari lagi", "Due in $daysLeft days") to warningAmber()
                        }
                        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().clickable { onGoPbf() }) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                        Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(p.pbfName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatIDR(p.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(SpaceS))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = dueColor, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Jatuh tempo ${formatDateID(p.dueDate)} • $dueLabel", "Due ${formatDateID(p.dueDate)} • $dueLabel"), style = MaterialTheme.typography.bodySmall, color = dueColor)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (noDateBills.isNotEmpty()) {
            item {
                // Seksi tagihan Kredit tanpa jatuh tempo: kartu diketuk
                // membuka Faktur PBF, tempat tanggalnya bisa diatur lewat
                // tombol Atur Jatuh Tempo di detail pembelian.
                Text(tr("KREDIT TANPA JATUH TEMPO", "CREDIT WITHOUT A DUE DATE"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(SpaceS))
                Column(verticalArrangement = Arrangement.spacedBy(SpaceM)) {
                    noDateBills.forEach { p ->
                        ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().clickable { onGoPbf() }) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                        Icon(Icons.Default.LocalShipping, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(p.pbfName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatIDR(p.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(SpaceS))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Beli ${formatDateID(p.date)} • belum ada jatuh tempo", "Bought ${formatDateID(p.date)} • no due date yet"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            // Istilah stok di Pengingat disamakan dengan menu Stok (keputusan
            // Achmad 2026-10-29): stok 1-9 disebut "Rendah" dan stok 0 disebut
            // "Habis" — bukan lagi "Stok Menipis"/"Kritis" — supaya informasi
            // di kedua menu konsisten. Label pendek juga membuat keempat chip
            // filter muat satu baris tanpa terpotong; scroll horizontal
            // dipertahankan sebagai cadangan untuk layar yang sangat sempit.
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("all" to tr("Semua", "All"), "expiry" to tr("Kadaluwarsa", "Expiry"), "low" to tr("Rendah", "Low"), "critical" to tr("Habis", "Out")).forEach { (k, l) ->
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(l) })
                }
            }
        }
        itemsIndexed(shown) { index, m ->
            val d = daysUntilExpiry(m.expiryDate)
            val isCritical = m.stock == 0
            // Saran restock: jumlah untuk mencapai batas Aman (16) pada skala
            // status baru; hanya ditampilkan bila sarannya > 0.
            val suggest = (16 - m.stock).coerceAtLeast(0)
            val showSuggest = suggest > 0
            // Keterangan digabung: kalau obat bebarengan bermasalah stok
            // (habis/menipis) DAN kadaluarsa, kedua keterangan ditulis supaya
            // informasinya jelas (sebelumnya hanya satu yang tampil karena
            // rantai if/else berhenti di kondisi pertama yang cocok).
            val stockNote = when {
                isCritical -> if (showSuggest) tr("Stok habis — restock segera, disarankan $suggest ${m.unit} (target stok Aman 16).", "Out of stock — restock soon, suggested $suggest ${m.unit} (Safe stock target 16).") else tr("Stok habis — restock segera.", "Out of stock — restock soon.")
                m.stock in 1..9 -> if (showSuggest) tr("Stok rendah — disarankan restock $suggest ${m.unit} (target stok Aman 16).", "Low stock — suggested restock $suggest ${m.unit} (Safe stock target 16).") else tr("Stok rendah — restock segera.", "Low stock — restock soon.")
                else -> null
            }
            val expiryNote = when {
                d != null && d < 0 -> tr("Sudah kadaluarsa — jangan dijual, segera pisahkan dari stok.", "Expired — do not sell it, separate it from stock right away.")
                d != null -> tr("Kedaluwarsa dalam $d hari — prioritaskan penjualan.", "Expires in $d days — prioritize selling it.")
                else -> null
            }
            val advice = listOfNotNull(stockNote, expiryNote).joinToString(" ")
            AnimatedVisibility(
                visible = listEntered,
                enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                    expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
            Column {
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                            Icon(
                                if (isCritical) Icons.Default.ShoppingCart else Icons.Default.Inventory, null,
                                tint = if (isCritical) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Surface(color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = if (isCritical) Modifier.pulseKritis() else Modifier) {
                                    Text(
                                        if (isCritical) tr("HABIS", "OUT") else tr("PANTAU", "WATCH"),
                                        color = if (isCritical) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                "${m.category} • Stok ${m.stock} ${m.unit} • ${stockLabel(m.stock)}" +
                                    if (m.expiryDate != null) {
                                        " • Exp ${formatDateID(m.expiryDate)}" + if (d != null) {
                                            if (d < 0) tr(" • Lewat ${-d} hari", " • ${-d} days overdue") else tr(" • Sisa $d hari", " • $d days left")
                                        } else ""
                                    } else " • Tanpa tanggal kadaluarsa",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onGoStok, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.Inventory, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Stok", "Stock"))
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            advice,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (index < shown.size - 1) {
                Spacer(Modifier.height(SpaceM))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            }
            }
        }
        if (shown.isEmpty()) {
            item { Text(tr("Tidak ada pengingat — semua stok dan kadaluarsa aman.", "No reminders — all stock and expiry dates are safe."), style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            Text(
                tr("Jumlah restock adalah saran otomatis (target stok Aman, 16). Untuk mengubah stok atau tanggal kadaluarsa, buka obatnya di menu Stok.", "Restock amounts are automatic suggestions (Safe stock target, 16). To change stock or an expiry date, open the medicine in the Stock menu."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun ReminderCount(icon: ImageVector, label: String, count: String, countColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = countColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(SpaceXS))
        Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = countColor)
    }
}
