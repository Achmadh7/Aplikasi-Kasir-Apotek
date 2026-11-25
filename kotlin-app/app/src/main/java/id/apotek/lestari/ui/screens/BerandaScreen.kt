@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.apotek.lestari.data.*
import id.apotek.lestari.StatusSync
import id.apotek.lestari.ui.rememberLogoBitmap
import id.apotek.lestari.ui.theme.*
import java.util.Date

// Layar Beranda.

@Composable
fun BerandaScreen(onGoStok: () -> Unit, onGoPengingat: () -> Unit, onGoLaba: () -> Unit, onGoRiwayat: () -> Unit, statusSync: StatusSync, onSyncTap: () -> Unit) {
    val gelap = LocalIsDarkTheme.current
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    // Pesan kegagalan muat (S10): tanpa ini, kegagalan dashboard membuat
    // LoadingIndicator berputar selamanya.
    var loadError by remember { mutableStateOf<String?>(null) }
    // Gagal muat daftar obat/pembelian — spanduk Pengingat tidak boleh
    // menyimpulkan "Semua stok aman" dari daftar yang tidak termuat.
    var reminderLoadFailed by remember { mutableStateOf(false) }
    // R26 (audit 2026-11-15): daftar pengingat belum tiba — teks "semua
    // aman" dikunci pada flag ini supaya tidak berkilas sebelum daftar
    // pengingat selesai dimuat.
    var remindersLoaded by remember { mutableStateOf(false) }
    // Periode panel ringkasan BR5 (pilihan Achmad 2026-11-04):
    // 0 = Hari Ini, 1 = Bulan Ini.
    // R10 (audit 2026-11-15): pilihan periode selamat dari rotasi.
    var summaryPeriod by rememberSaveable { mutableIntStateOf(0) }
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
        remindersLoaded = false
        when (val r = repo.getDashboard()) {
            is ApiResult.Success -> { dashboard = r.data; loadError = null }
            is ApiResult.Error -> loadError = r.message
            // Loading tidak pernah dihasilkan repo (call() hanya Success/
            // Error); cabang ini hanya untuk memenuhi exhaustive 'when'.
            is ApiResult.Loading -> Unit
        }
        // Kegagalan muat daftar pengingat dilacak terpisah (S10): tanpa
        // ini, daftar kosong disimpulkan "Semua stok aman" padahal data
        // tidak termuat.
        var gagalPengingat = false
        val meds = when (val r = repo.listMedicines()) {
            is ApiResult.Success -> r.data?.medicines.orEmpty()
            else -> { gagalPengingat = true; emptyList() }
        }
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
        val purchases = when (val r = repo.listPurchases()) {
            is ApiResult.Success -> r.data?.purchases.orEmpty()
            else -> { gagalPengingat = true; emptyList() }
        }
        dueBills = purchases.filter { p ->
            p.paymentType == "KREDIT" && p.dueDate.isNotBlank() && (dueDaysLeft(p.dueDate) ?: Long.MAX_VALUE) <= 7L
        }.sortedBy { it.dueDate }
        reminderLoadFailed = gagalPengingat
        remindersLoaded = true
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val brush = forestHeroBrush()
            val logo = rememberLogoBitmap()
            val monthMode = summaryPeriod == 1
            val d = dashboard
            val diff = (d?.todayIn ?: 0) - (d?.yesterdayIn ?: 0)
            val panelIn = if (monthMode) (d?.monthIn ?: 0) else (d?.todayIn ?: 0)
            val panelOut = if (monthMode) (d?.monthOut ?: 0) else (d?.todayOut ?: 0)
            val panelProfit = if (monthMode) (d?.monthProfit ?: 0) else (d?.todayProfit ?: 0)

            // Unified Forest Emerald Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusHero))
                    .background(brush)
                    .padding(16.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(RadiusSmall),
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (logo != null) {
                                Image(
                                    bitmap = logo,
                                    contentDescription = "Logo Apotek Lestari",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.LocalPharmacy, null, tint = ForestHeroLightStart, modifier = Modifier.padding(8.dp).fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Apotek Lestari",
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.weight(1f))
                        if (statusSync != StatusSync.NONAKTIF) {
                            val syncBorder = if (statusSync == StatusSync.GAGAL) ForestErrorSoft else ForestMint.copy(alpha = 0.42f)
                            val syncTint = if (statusSync == StatusSync.GAGAL) ForestErrorSoft else ForestMint
                            Surface(
                                color = ForestEmeraldAccent.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(RadiusSmall),
                                border = BorderStroke(1.dp, syncBorder),
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable { onSyncTap() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    when (statusSync) {
                                        StatusSync.PROSES -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ForestMint)
                                        StatusSync.BERHASIL -> Icon(Icons.Default.CloudDone, null, tint = syncTint, modifier = Modifier.size(16.dp))
                                        else -> Icon(Icons.Default.CloudOff, null, tint = syncTint, modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        tr("Sinkron", "Sync"),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = syncTint
                                    )
                                }
                            }
                        }
                    }

                    if (d != null) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (monthMode) tr("Penjualan bulan ini", "This month's sales") else tr("Penjualan hari ini", "Today's sales"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = ForestMintSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(tr("Hari", "Day"), tr("Bulan", "Month")).forEachIndexed { idx, label ->
                                    val selected = summaryPeriod == idx
                                    Surface(
                                        color = if (selected) ForestEmeraldAccent else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(RadiusSmall),
                                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 44.dp).clickable { summaryPeriod = idx }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (selected) ForestOnAccent else ForestMintMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        AnimatedAmount(
                            value = panelIn,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!monthMode) {
                            Spacer(Modifier.height(8.dp))
                            val isUp = diff >= 0
                            val arrowColor = if (isUp) ForestMint else ForestErrorSoft
                            Surface(
                                color = if (isUp) ForestEmeraldAccent.copy(alpha = 0.22f) else ForestErrorSoft.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(RadiusSmall),
                                border = BorderStroke(1.dp, arrowColor.copy(alpha = 0.36f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (isUp) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = arrowColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        if (diff == 0) tr("Sama vs kemarin", "Same vs yesterday")
                                        else "${if (isUp) "+" else "-"}${formatIDR(kotlin.math.abs(diff))} ${tr("vs kemarin", "vs yesterday")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = ForestMintLight,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // 2 sub-tiles (Keluar & Selisih)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = ForestErrorSoft, modifier = Modifier.size(12.dp))
                                        Text(tr("Keluar", "Out"), style = MaterialTheme.typography.labelSmall, color = ForestMintMuted, maxLines = 1)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        formatIDR(panelOut),
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = ForestErrorSoft,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    val pos = panelProfit >= 0
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            if (pos) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = if (pos) ForestMint else ForestErrorSoft,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(tr("Selisih", "Net"), style = MaterialTheme.typography.labelSmall, color = ForestMintMuted, maxLines = 1)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        (if (pos) "+" else "-") + formatIDR(kotlin.math.abs(panelProfit)),
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (pos) Color.White else ForestErrorSoft,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            DemoBanner()
        }
        dashboard?.let { d ->
            if (loadError != null) {
                item {
                    ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Gagal memperbarui Beranda.", "Failed to refresh Home."), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(4.dp))
                                Text(loadError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = { loadError = null; resumeKey++ }) { Text(tr("Coba lagi", "Try again")) }
                        }
                    }
                }
            }
            item {
                // Compact 1-line Alert Strip (Mix Opsi 3 + Accent Opsi 1)
                val shortParts = buildList {
                    if (outOfStockNames.isNotEmpty()) add(tr("${outOfStockNames.size} Habis", "${outOfStockNames.size} Out"))
                    if (lowStockMeds.isNotEmpty()) add(tr("${lowStockMeds.size} Menipis", "${lowStockMeds.size} Low"))
                    if (d.expiringCount > 0) add("${d.expiringCount} ED")
                    if (dueBills.isNotEmpty()) add(tr("${dueBills.size} Tagihan PBF", "${dueBills.size} PBF Bills"))
                }
                val stripText = when {
                    reminderLoadFailed -> tr("Gagal memuat pengingat", "Failed to load reminders")
                    shortParts.isNotEmpty() -> shortParts.joinToString(" • ")
                    !remindersLoaded -> tr("Memuat pengingat…", "Loading reminders…")
                    else -> tr("Semua stok & ED aman", "All stock & expiry safe")
                }
                val accent = warningAmberText()
                Surface(
                    color = amberBg(),
                    shape = RoundedCornerShape(RadiusCard),
                    border = BorderStroke(1.dp, warningAmber().copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable { onGoPengingat() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stripText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                tr("Cek", "View"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accent
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            item {
                // 3 Shortcut Cards with quiet neutral icon badges
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ShortcutCard(
                        label = tr("Laba", "Profit"),
                        icon = Icons.Default.TrendingUp,
                        modifier = Modifier.weight(1f),
                        onClick = onGoLaba
                    )
                    ShortcutCard(
                        label = tr("Stok", "Stock"),
                        icon = Icons.Default.Medication,
                        modifier = Modifier.weight(1f),
                        onClick = onGoStok
                    )
                    ShortcutCard(
                        label = tr("Riwayat", "History"),
                        icon = Icons.Default.ReceiptLong,
                        modifier = Modifier.weight(1f),
                        onClick = onGoRiwayat
                    )
                }
            }
            item {
                // Obat Terjual Hari Ini dengan Rank Badge 1 2 3
                ExpressiveCard(shape = RoundedCornerShape(RadiusCard), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tr("Obat terjual hari ini", "Items sold today"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (d.bestSellers.isNotEmpty()) {
                                Text(
                                    "Top ${minOf(d.bestSellers.size, 3)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (d.bestSellers.isEmpty()) {
                            Text(
                                tr("Belum ada obat terjual hari ini.", "No items sold today yet."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column {
                                d.bestSellers.forEachIndexed { idx, b ->
                                    if (idx > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = if (idx == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shape = RoundedCornerShape(RadiusSmall),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "${idx + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (idx == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                b.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                tr("${b.qty} terjual", "${b.qty} sold"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            formatIDR(b.revenue),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } ?: item {
            val err = loadError
            if (err != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Gagal memuat Beranda.", "Failed to load Home."), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(SpaceS))
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(SpaceS))
                    Button(onClick = { loadError = null; resumeKey++ }) {
                        Text(tr("Coba lagi", "Try again"))
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}


@Composable
fun ShortcutCard(
    label: String,
    icon: ImageVector,
    badgeBg: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    badgeTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ExpressiveCard(
        shape = RoundedCornerShape(RadiusCard),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = badgeBg,
                shape = RoundedCornerShape(RadiusSmall),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = badgeTint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
