package id.apotek.lestari

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.apotek.lestari.data.local.DatabaseProvider
import id.apotek.lestari.ui.screens.*
import id.apotek.lestari.ui.theme.ApotikLestariTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(applicationContext)
        setContent {
            // Pilihan tema (Gelap/Terang) tersimpan di SharedPreferences,
            // dibaca sekali saat aplikasi dibuka.
            val prefs = remember { getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE) }
            var isDark by remember { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
            // Kelas lebar jendela (HP/tablet, portrait/landscape) untuk layout adaptif.
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowWidthClass provides windowSizeClass.widthSizeClass) {
                ApotikLestariTheme(darkTheme = isDark) {
                    ApotekApp(
                        isDark = isDark,
                        onToggleTheme = {
                            val next = !isDark
                            isDark = next
                            prefs.edit().putBoolean("dark_theme", next).apply()
                        }
                    )
                }
            }
        }
    }
}

data class TabDef(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
// Tab pill bawah: 5 tab (Kasir kini tab biasa, bukan FAB melayang).
val TABS = listOf(
    TabDef("beranda", "Beranda", Icons.Default.Home),
    TabDef("stok", "Stok", Icons.Default.Medication),
    TabDef("kasir", "Kasir", Icons.Default.PointOfSale),
    TabDef("keuangan", "Keuangan", Icons.Default.AccountBalanceWallet),
    TabDef("lainnya", "Lainnya", Icons.Default.MoreHoriz)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApotekApp(isDark: Boolean, onToggleTheme: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "beranda"
    // Sheet "Lainnya": Laba, Pengingat, Riwayat, Backup & Restore — menu yang tidak lagi ada di bar bawah.
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState()
    // Urutan tab untuk swipe (kiri ke kanan): Beranda, Stok, Kasir, Keuangan,
    // Lainnya. "Lainnya" membuka sheet, bukan layar — geser ke kiri dari
    // Keuangan membuka sheet Lainnya (sama seperti mengetuk tab Lainnya).
    val swipeableTabs = listOf("beranda", "stok", "kasir", "keuangan", "lainnya")
    // Arah navigasi antar tab (1 = maju/ke kanan, -1 = mundur/ke kiri) —
    // dipakai transisi NavHost supaya layar mengikuti arah geser/ketukan.
    var navDirection by remember { mutableIntStateOf(1) }
    fun tabPosition(route: String): Int = when (route) {
        "beranda" -> 0
        "stok" -> 1
        "kasir" -> 2
        "keuangan" -> 3
        else -> 4 // Lainnya + sub-menunya
    }
    fun goToTab(route: String) {
        navDirection = if (tabPosition(route) >= tabPosition(current)) 1 else -1
        nav.navigate(route) { popUpTo("beranda"); launchSingleTop = true }
    }
    // Geser kiri/kanan di konten untuk pindah tab (hanya di tab utama;
    // dimatikan di sub-menu Lainnya supaya tidak bentrok dengan gulir horizontal).
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeModifier = if (current in swipeableTabs) {
        Modifier.pointerInput(current) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val currentIndex = swipeableTabs.indexOf(current)
                    if (swipeOffset < -swipeThresholdPx && currentIndex < swipeableTabs.lastIndex) {
                        val next = swipeableTabs[currentIndex + 1]
                        if (next == "lainnya") {
                            showMoreSheet = true // Geser ke kiri dari Keuangan -> buka sheet Lainnya
                        } else {
                            goToTab(next) // Geser ke kiri -> tab berikutnya
                        }
                    } else if (swipeOffset > swipeThresholdPx && currentIndex > 0) {
                        goToTab(swipeableTabs[currentIndex - 1]) // Geser ke kanan -> tab sebelumnya
                    }
                    swipeOffset = 0f
                },
                onHorizontalDrag = { _, dragAmount ->
                    swipeOffset += dragAmount
                }
            )
        }
    } else {
        Modifier
    }
    Scaffold(
        bottomBar = {
            // Pill mengambang (kapsul): 5 tab (Beranda, Stok, Kasir, Keuangan,
            // Lainnya). Kasir kini tab biasa (bukan FAB melayang). Pill
            // dibatasi 480dp supaya di tablet tidak merentang terlalu lebar.
            // Gaya KACA (pilihan Achmad 2026-10-30, opsi 4): badan pill
            // semi-transparan bergradasi halus + kilau di tepi atas, bordir
            // biru tipis dengan bayangan biru lembut (efek menyala), dan tab
            // terpilih berupa lingkaran gradasi biru berisi ikon/label putih.
            val pillShape = RoundedCornerShape(36.dp)
            val glowColor = Color(0xFF42A5F5)
            val glassBrush = if (isDark) {
                Brush.verticalGradient(
                    listOf(Color(0xFF262C34).copy(alpha = 0.88f), Color(0xFF14171C).copy(alpha = 0.88f))
                )
            } else {
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.94f), Color(0xFFEDF3FA).copy(alpha = 0.94f))
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .height(72.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = pillShape,
                            ambientColor = glowColor.copy(alpha = 0.30f),
                            spotColor = glowColor.copy(alpha = 0.45f)
                        )
                        .background(brush = glassBrush, shape = pillShape)
                        .border(
                            1.dp,
                            if (isDark) glowColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            pillShape
                        )
                ) {
                    // Kilau kaca: sorotan putih tipis di separuh atas pill.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = if (isDark) 0.10f else 0.35f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
                            )
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TABS.forEach { tab ->
                            val selected = when (tab.route) {
                                "beranda" -> current == "beranda"
                                "stok" -> current == "stok"
                                "kasir" -> current == "kasir"
                                "keuangan" -> current == "keuangan"
                                else -> current in listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang", "pbf")
                            }
                            PillTab(
                                selected = selected,
                                icon = tab.icon,
                                label = tab.label,
                                isDark = isDark,
                                onClick = { if (tab.route == "lainnya") showMoreSheet = true else goToTab(tab.route) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        // Lebar konten dibatasi ~840.dp dan dipusatkan di layar lebar (tablet/landscape),
        // supaya daftar tidak merentang dari ujung ke ujung. Navigasi bawah tetap selebar layar.
        // swipeModifier: geser kiri/kanan di konten untuk pindah tab (hanya di tab utama).
        Box(modifier = Modifier.padding(padding).fillMaxSize().then(swipeModifier), contentAlignment = Alignment.TopCenter) {
            NavHost(
                navController = nav,
                startDestination = "beranda",
                // Transisi antar tab: pudar + geser halus, bukan ganti mendadak.
                // Arah geser mengikuti navDirection (1 = maju, -1 = mundur).
                enterTransition = { fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(200), initialOffsetX = { it / 4 * navDirection }) },
                exitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { -it / 4 * navDirection }) },
                popEnterTransition = { fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(200), initialOffsetX = { -it / 4 * navDirection }) },
                popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it / 4 * navDirection }) },
                modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth()
            ) {
                composable("beranda") { BerandaScreen(onGoStok = { goToTab("stok") }, onGoPengingat = { goToTab("pengingat") }, onGoLaba = { goToTab("laba") }, onGoRiwayat = { goToTab("riwayat") }) }
                composable("kasir") { KasirScreen() }
                composable("stok") { StokScreen() }
                composable("keuangan") { KeuanganScreen() }
                composable("laba") { LabaScreen() }
                composable("pengingat") { PengingatScreen(onGoStok = { goToTab("stok") }) }
                composable("riwayat") { RiwayatScreen() }
                composable("backup") { BackupScreen() }
                composable("terlaris") { ObatTerlarisScreen() }
                composable("pbf") { PbfScreen() }
                composable("tentang") { TentangScreen() }
            }
        }
    }
    // Sheet "Lainnya": Laba, Pengingat, Riwayat, Backup & Restore.
    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = moreSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            // Isi sheet bisa DIGULIR: di mode lanskap (HP/tablet) tinggi layar
            // pendek sehingga tanpa gulir baris terbawah (Mode Terang/Gelap
            // dan Tentang Aplikasi) terpotong dan tidak bisa diketuk
            // (laporan Achmad 2026-10-29).
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 24.dp)) {
                Text("Menu Lainnya", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
                listOf(
                    Triple("laba", "Laba", Icons.Default.TrendingUp),
                    Triple("terlaris", "Obat Terlaris", Icons.Default.Leaderboard),
                    Triple("pengingat", "Pengingat", Icons.Default.NotificationsActive),
                    Triple("riwayat", "Riwayat", Icons.Default.ReceiptLong),
                    Triple("pbf", "Faktur PBF", Icons.Default.LocalShipping),
                    Triple("backup", "Backup & Restore", Icons.Default.Backup)
                ).forEach { (route, label, icon) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            showMoreSheet = false
                            goToTab(route)
                        }.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Ganti tema Gelap <-> Terang (pilihan tersimpan, dipakai lagi saat aplikasi dibuka).
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        onToggleTheme()
                        showMoreSheet = false
                    }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(
                            if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp).size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isDark) "Mode Terang" else "Mode Gelap",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Tentang Aplikasi, di bawah ganti tema.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        showMoreSheet = false
                        goToTab("tentang")
                    }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Tentang Aplikasi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}



// Satu tab di pill bawah: ikon di atas, label di bawah (seperti referensi).
// Bila terpilih, tampil lingkaran gradasi biru di belakangnya + ikon/label
// PUTIH (kontras di atas gradasi biru); bila tidak terpilih, ikon/label
// putih/abu. Ketukan tanpa ripple supaya tampilannya bersih seperti referensi.
@Composable
private fun RowScope.PillTab(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val unselectedColor = if (isDark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.60f)
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    // 56 -> 64dp (permintaan Achmad 2026-10-30): label panjang
                    // seperti "Keuangan" sebelumnya tidak muat di dalam
                    // lingkaran 56dp; 64dp masih pas di pill setinggi 72dp.
                    .size(64.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF64B5F6), Color(0xFF1E88E5))
                        ),
                        shape = CircleShape
                    )
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else unselectedColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else unselectedColor,
                maxLines = 1
            )
        }
    }
}
