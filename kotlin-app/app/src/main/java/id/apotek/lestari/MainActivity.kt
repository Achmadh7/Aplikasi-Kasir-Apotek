package id.apotek.lestari

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
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

data class TabDef(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconOutlined: androidx.compose.ui.graphics.vector.ImageVector
)
// Tab bar bawah: 5 tab (Kasir tab biasa, bukan FAB melayang). Tiap tab punya
// ikon garis (outlined, saat tidak aktif) dan ikon terisi (filled, saat aktif).
val TABS = listOf(
    TabDef("beranda", "Beranda", Icons.Default.Home, Icons.Outlined.Home),
    TabDef("stok", "Stok", Icons.Default.Medication, Icons.Outlined.Medication),
    TabDef("kasir", "Kasir", Icons.Default.PointOfSale, Icons.Outlined.PointOfSale),
    TabDef("keuangan", "Keuangan", Icons.Default.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    TabDef("lainnya", "Lainnya", Icons.Default.MoreHoriz, Icons.Outlined.MoreHoriz)
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
            // Bar bawah MELAYANG LEMBUT model dock (pilihan Achmad 2026-10-30,
            // opsi 1 anti-kaku): NavigationBar rata dibungkus Surface membulat
            // 28dp, berjarak 16dp dari kiri/kanan dan 12dp dari bawah layar,
            // dengan bayangan lembut — terlepas dari tepi layar TANPA kembali
            // ke pill kapsul lama. Warna bar tetap sama dengan latar halaman
            // + bordir rambut outlineVariant (polesan elegan 1+2); ikon tidak
            // aktif outlined, aktif filled; indikator kapsul bawaan
            // NavigationBarItem; ikon + label selalu tampil.
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0)
                    ) {
                        TABS.forEach { tab ->
                            val selected = when (tab.route) {
                                "beranda" -> current == "beranda"
                                "stok" -> current == "stok"
                                "kasir" -> current == "kasir"
                                "keuangan" -> current == "keuangan"
                                else -> current in listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang", "pbf")
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { if (tab.route == "lainnya") showMoreSheet = true else goToTab(tab.route) },
                                icon = { Icon(if (selected) tab.icon else tab.iconOutlined, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                composable("pengingat") { PengingatScreen(onGoStok = { goToTab("stok") }, onGoPbf = { goToTab("pbf") }) }
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
