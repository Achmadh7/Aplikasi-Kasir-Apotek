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

// Layar Beranda.

@Composable
fun BerandaScreen(onGoStok: () -> Unit, onGoPengingat: () -> Unit, onGoLaba: () -> Unit, onGoRiwayat: () -> Unit) {
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(dashboard) { if (dashboard != null && !listEntered) listEntered = true }
    var salesTab by remember { mutableIntStateOf(0) }
    // Periode panel ringkasan BR5 (pilihan Achmad 2026-11-04):
    // 0 = Hari Ini, 1 = Bulan Ini.
    var summaryPeriod by remember { mutableIntStateOf(0) }
    var outOfStockNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var lowStockMeds by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var expiringMeds by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var dueBills by remember { mutableStateOf<List<Purchase>>(emptyList()) }
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
    LaunchedEffect(resumeKey) {
        (repo.getDashboard() as? ApiResult.Success)?.let { dashboard = it.data }
        val meds = (repo.listMedicines() as? ApiResult.Success)?.data?.medicines.orEmpty()
        outOfStockNames = meds.filter { it.stock <= 0 }.map { it.name }.sorted()
        // Stok menipis = Rendah (1-9 pada skala status baru) — diurutkan dari
        // yang paling sedikit supaya yang paling mendesak tampil duluan.
        // Stok 0 tidak ikut di sini karena sudah tampil di bagian Habis.
        lowStockMeds = meds.filter { it.stock in 1..9 }
            .sortedBy { it.stock }
            .map { it.name to it.stock }
        val in30Key = dayKeyID(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
        // Tanpa batas bawah: obat yang sudah kadaluarsa ikut tampil di sini
        // (konsisten dengan expiringCount di Dashboard).
        expiringMeds = meds.filter { it.expiryDate != null && it.expiryDate <= in30Key }
            .sortedBy { it.expiryDate }
            .mapNotNull { med -> med.expiryDate?.let { exp -> med.name to exp } }
        // Tagihan PBF: pembelian Kredit yang jatuh temponya <= 7 hari lagi
        // atau sudah lewat (pilihan Achmad 2026-10-30, saran no. 1) —
        // diurutkan dari jatuh tempo paling awal.
        val purchases = (repo.listPurchases() as? ApiResult.Success)?.data?.purchases.orEmpty()
        dueBills = purchases.filter { p ->
            p.paymentType == "KREDIT" && p.dueDate.isNotBlank() && (dueDaysLeft(p.dueDate) ?: Long.MAX_VALUE) <= 7L
        }.sortedBy { it.dueDate }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        // Jarak antar kartu diperbesar 12 -> 20dp (permintaan Achmad
        // 2026-10-30 dari screenshot Beranda — kartu-kartu tampak terlalu rapat).
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = listEntered,
                enter = fadeIn(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)) { it / 3 } +
                    expandVertically(animationSpec = tween(300, entranceDelay(0), FastOutSlowInEasing)),
                exit = fadeOut()
            ) {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo = rememberLogoBitmap()
                if (logo != null) {
                    Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                        Image(bitmap = logo, contentDescription = "Logo Apotek Lestari", contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp).fillMaxSize())
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text("Apotek Lestari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(todayLabelID(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(SpaceS))
            Text(tr("Beranda", "Home"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.height(SpaceL))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(SpaceL))
            DemoBanner()
            }
            }
        }
        dashboard?.let { d ->
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(1), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                // Panel ringkasan BR5 (pilihan Achmad 2026-11-04): hero
                // Pemasukan dan kartu uang lama digabung menjadi SATU
                // panel bertab periode Hari Ini / Bulan Ini, supaya angka
                // harian dan bulanan tidak tampil berulang dalam dua kartu
                // terpisah. Tab memakai SingleChoiceSegmentedButtonRow
                // resmi Material 3 (sama seperti pemilih metode bayar).
                val monthMode = summaryPeriod == 1
                val diff = d.todayIn - d.yesterdayIn
                val panelIn = if (monthMode) d.monthIn else d.todayIn
                val panelOut = if (monthMode) d.monthOut else d.todayOut
                val panelProfit = if (monthMode) d.monthProfit else d.todayProfit
                val panelSales = if (monthMode) d.totalSalesMonth else d.totalSalesToday
                Column {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(tr("Hari Ini", "Today"), tr("Bulan Ini", "This Month")).forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = summaryPeriod == index,
                                onClick = { summaryPeriod = index },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                                modifier = Modifier.weight(1f)
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(SpaceM))
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth()
                            .animateContentSize(animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (monthMode) tr("PEMASUKAN BULAN INI", "THIS MONTH'S INCOME") else tr("PEMASUKAN HARI INI", "TODAY'S INCOME"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                    Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(6.dp).size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(SpaceS))
                            AnimatedAmount(panelIn, MaterialTheme.typography.headlineLarge, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(SpaceXS))
                            if (!monthMode) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when {
                                        diff > 0 -> {
                                            Icon(Icons.Default.TrendingUp, null, tint = profitGreen(), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(tr("Naik ${formatIDR(diff)} dari kemarin", "Up ${formatIDR(diff)} from yesterday"), color = profitGreen(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        }
                                        diff < 0 -> {
                                            Icon(Icons.Default.TrendingDown, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(tr("Turun ${formatIDR(-diff)} dari kemarin", "Down ${formatIDR(-diff)} from yesterday"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        }
                                        else -> {
                                            Text(tr("Sama seperti kemarin", "Same as yesterday"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            } else {
                                Text(tr("Penjualan pada bulan berjalan", "Sales in the current month"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(SpaceM))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(SpaceM))
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Pengeluaran", "Expenses"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(SpaceXS))
                                    Text(formatIDR(panelOut), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Selisih", "Difference"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(SpaceXS))
                                    Text(formatIDR(panelProfit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (panelProfit >= 0) profitGreen() else MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Transaksi", "Transactions"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(SpaceXS))
                                    Text("$panelSales", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(2), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                // Shortcut ke menu yang tidak lagi ada di bar bawah (Laba, Pengingat, Riwayat).
                Row(horizontalArrangement = Arrangement.spacedBy(SpaceM), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard(label = tr("Laba", "Profit"), icon = Icons.Default.TrendingUp, modifier = Modifier.weight(1f), onClick = onGoLaba)
                    ShortcutCard(label = tr("Pengingat", "Reminders"), icon = Icons.Default.NotificationsActive, modifier = Modifier.weight(1f), onClick = onGoPengingat)
                    ShortcutCard(label = tr("Riwayat", "History"), icon = Icons.Default.ReceiptLong, modifier = Modifier.weight(1f), onClick = onGoRiwayat)
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(3), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(tr("Pengingat", "Reminders"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(SpaceS))
                        // Stok menipis: nama obat + sisa stok, ditulis sama seperti
                        // stok habis (titik & nama merah) supaya sama-sama terlihat mendesak.
                        if (lowStockMeds.isNotEmpty()) {
                            lowStockMeds.take(5).forEach { (name, stock) ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(tr("Sisa: $stock", "Left: $stock"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (lowStockMeds.size > 5) Text(tr("+${lowStockMeds.size - 5} lainnya", "+${lowStockMeds.size - 5} more"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                        }
                        // Stok habis: nama obat + keterangan "Habis".
                        if (outOfStockNames.isNotEmpty()) {
                            outOfStockNames.take(5).forEach { name ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(tr("Habis", "Out of stock"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (outOfStockNames.size > 5) Text(tr("+${outOfStockNames.size - 5} lainnya", "+${outOfStockNames.size - 5} more"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                        }
                        if (d.expiringCount > 0) Text(tr("• ${d.expiringCount} obat dekat kadaluarsa", "• ${d.expiringCount} medicines expiring soon"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                        if (expiringMeds.isNotEmpty()) {
                            expiringMeds.take(5).forEach { (name, exp) ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatDateID(exp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (expiringMeds.size > 5) Text(tr("+${expiringMeds.size - 5} lainnya", "+${expiringMeds.size - 5} more"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                        }
                        // Tagihan PBF mendekati jatuh tempo: nama PBF ditulis
                        // lengkap + sisa hari di kanan (permintaan Achmad
                        // 2026-10-30 supaya informasinya lengkap), maks. 3.
                        if (dueBills.isNotEmpty()) {
                            Text(tr("• ${dueBills.size} tagihan PBF mendekati jatuh tempo", "• ${dueBills.size} PBF bills nearing due date"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                            dueBills.take(3).forEach { p ->
                                val daysLeft = dueDaysLeft(p.dueDate) ?: 0L
                                val (dueLabel, dueColor) = when {
                                    daysLeft < 0L -> tr("Lewat ${-daysLeft} hari", "${-daysLeft} days overdue") to MaterialTheme.colorScheme.error
                                    daysLeft == 0L -> tr("Jatuh tempo hari ini", "Due today") to MaterialTheme.colorScheme.error
                                    daysLeft == 1L -> tr("Besok jatuh tempo", "Due tomorrow") to warningAmber()
                                    else -> tr("$daysLeft hari lagi", "Due in $daysLeft days") to warningAmber()
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(6.dp).background(dueColor, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(p.pbfName, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(dueLabel, style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = dueColor)
                                }
                            }
                            if (dueBills.size > 3) Text(tr("+${dueBills.size - 3} lainnya", "+${dueBills.size - 3} more"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
                        }
                        if (d.lowStockCount == 0 && d.expiringCount == 0 && dueBills.isEmpty()) Text(tr("Semua stok dan kadaluarsa aman.", "All stock and expiry dates are safe."), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                        if (d.lowStockCount > 0 || d.expiringCount > 0 || dueBills.isNotEmpty()) {
                            Spacer(Modifier.height(SpaceS))
                            TextButton(onClick = onGoPengingat) { Text(tr("Lihat Pengingat", "View Reminders"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)) }
                        }
                        Spacer(Modifier.height(SpaceS))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(SpaceS))
                        Text(tr("${d.totalMedicines} jenis obat terdaftar di Stok", "${d.totalMedicines} medicines registered in Stock"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                }
            }
            item {
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(5), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(5), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(5), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                // Penjualan Hari Ini: Obat Terjual dan Transaksi dalam satu kartu bertab (compact).
                ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                            listOf(tr("Obat Terjual", "Items Sold") to 0, tr("Transaksi", "Transactions") to 1).forEach { (label, idx) ->
                                val selected = salesTab == idx
                                Surface(
                                    onClick = { salesTab = idx },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(SpaceS))
                        if (salesTab == 0) {
                        if (d.bestSellers.isEmpty()) {
                            Text(tr("Belum ada obat terjual hari ini.", "No items sold today yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            d.bestSellers.forEachIndexed { index, b ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${b.qty} item • ${formatIDR(b.revenue)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        } else {
                        if (d.recentSales.isEmpty()) {
                            Text(tr("Belum ada penjualan hari ini.", "No sales today yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            d.recentSales.forEachIndexed { index, s ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.invoiceNo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text(formatTimeID(s.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(formatIDR(s.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        }
                    }
                }
                }
            }
        } ?: item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
    }
}


@Composable
fun ShortcutCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ExpressiveCard(shape = RoundedCornerShape(20.dp), modifier = modifier.clickable { onClick() }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(SpaceS))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}
