package id.apotek.lestari

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
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
import id.apotek.lestari.ui.rememberLogoBitmap
import id.apotek.lestari.ui.screens.*
import id.apotek.lestari.ui.theme.ApotikLestariTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
// Tab bar bawah (Kasir tidak di sini — jadi tombol FAB di tengah).
val TABS = listOf(
    TabDef("beranda", "Beranda", Icons.Default.Home),
    TabDef("stok", "Stok", Icons.Default.Medication),
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
    // Animasi splash saat aplikasi dibuka: logo muncul di tengah layar lalu
    // "terbang" mengecil ke posisi logo header Beranda, baru aplikasi tampil.
    // Beranda sudah di-compose di bawah splash (jadi datanya sempat dimuat),
    // dan posisi logo header-nya diukur untuk jadi target pendaratan.
    var showSplash by remember { mutableStateOf(true) }
    var headerLogoRect by remember { mutableStateOf<Rect?>(null) }
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
    // Box akar: menampung Scaffold + overlay splash di atasnya (splash menutupi
    // seluruh layar, termasuk bar bawah, sampai animasinya selesai).
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            // Bar bawah seperti awal: bar datar selebar layar (80dp) menempel di tepi bawah,
            // tanpa pil mengambang dan tanpa sembunyi saat gulir. FAB Kasir 60dp di tengah
            // naik di atas bar (tetap di dalam area bottomBar 104dp, tidak menutupi konten).
            Box(modifier = Modifier.fillMaxWidth().height(104.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    // Sudut atas bar membulat (24dp); sudut bawah tetap menempel di tepi layar.
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter)
                ) {
                    // Indikator pill yang bergeser halus antar tab (bukan langsung pindah).
                    // NavigationBarItem bawaan tidak menggeser indikatornya, jadi pill
                    // digambar sendiri dan posisi-x-nya dianimasikan mengikuti tab aktif.
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val slotWidth = maxWidth / 5 // 4 tab + 1 celah Kasir di tengah
                        val pillWidth = 64.dp
                        val pillHeight = 32.dp
                        // Slot tab yang dipilih: Beranda 0, Stok 1, Keuangan 3, Lainnya 4.
                        // -1 saat di Kasir (tidak ada tab yang dipilih).
                        val selectedSlot = when {
                            current == "beranda" -> 0
                            current == "stok" -> 1
                            current == "keuangan" -> 3
                            current in listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang") -> 4
                            else -> -1
                        }
                        // Simpan slot terakhir supaya pill tidak melompat saat di Kasir.
                        var lastSlot by remember { mutableIntStateOf(0) }
                        LaunchedEffect(selectedSlot) {
                            if (selectedSlot >= 0) lastSlot = selectedSlot
                        }
                        val pillX by animateDpAsState(
                            targetValue = slotWidth * lastSlot + (slotWidth - pillWidth) / 2,
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            label = "pillX"
                        )
                        val pillAlpha by animateFloatAsState(
                            targetValue = if (selectedSlot >= 0) 1f else 0f,
                            animationSpec = tween(200),
                            label = "pillAlpha"
                        )
                        // Pill indikator (di belakang ikon tab).
                        Box(
                            modifier = Modifier
                                .offset(x = pillX, y = 14.dp)
                                .size(pillWidth, pillHeight)
                                .graphicsLayer { alpha = pillAlpha }
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )
                        Row(
                            modifier = Modifier.fillMaxSize().selectableGroup(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TABS.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = if (tab.route == "lainnya") current in listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang") else current == tab.route,
                                    onClick = { if (tab.route == "lainnya") showMoreSheet = true else goToTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(26.dp)) },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        // Indikator bawaan dimatikan (transparan) — diganti pill geser di atas.
                                        indicatorColor = Color.Transparent,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                // Celah selebar satu tab di tengah untuk tombol Kasir.
                                if (index == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                // Tombol Kasir: FAB biru besar di tengah + label "Kasir" di bawahnya.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    FloatingActionButton(
                        onClick = { goToTab("kasir") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = "Kasir", modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kasir",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (current == "kasir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                composable("beranda") { BerandaScreen(onGoStok = { goToTab("stok") }, onGoPengingat = { goToTab("pengingat") }, onGoLaba = { goToTab("laba") }, onGoRiwayat = { goToTab("riwayat") }, onHeaderLogoPositioned = { rect -> if (showSplash) headerLogoRect = rect }) }
                composable("kasir") { KasirScreen() }
                composable("stok") { StokScreen() }
                composable("keuangan") { KeuanganScreen() }
                composable("laba") { LabaScreen() }
                composable("pengingat") { PengingatScreen(onGoStok = { goToTab("stok") }) }
                composable("riwayat") { RiwayatScreen() }
                composable("backup") { BackupScreen() }
                composable("terlaris") { ObatTerlarisScreen() }
                composable("tentang") { TentangScreen() }
            }
        }
    }
        // Splash pembuka menutupi seluruh layar sampai animasi logo selesai.
        if (showSplash) {
            SplashOverlay(headerLogoRect = headerLogoRect, onFinished = { showSplash = false })
        }
    }
    // Sheet "Lainnya": Laba, Pengingat, Riwayat, Backup & Restore.
    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = moreSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 24.dp)) {
                Text("Menu Lainnya", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
                listOf(
                    Triple("laba", "Laba", Icons.Default.TrendingUp),
                    Triple("terlaris", "Obat Terlaris", Icons.Default.Leaderboard),
                    Triple("pengingat", "Pengingat", Icons.Default.NotificationsActive),
                    Triple("riwayat", "Riwayat", Icons.Default.ReceiptLong),
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

// Animasi splash saat aplikasi dibuka: logo muncul di tengah layar (fade + membesar),
// lalu "terbang" mengecil ke posisi logo header Beranda, kemudian seluruh splash memudar
// sehingga logo header yang asli (di posisi & ukuran yang sama) terlihat — seolah-olah
// logo splash berubah menjadi logo header. Total sekitar 2 detik, hanya saat cold start
// (state showSplash di ApotekApp; kembali dari background tidak mengulang splash).
// Target pendaratan diukur dari logo header Beranda (koordinat window) supaya pas di
// semua ukuran layar — bukan posisi kira-kira.
@Composable
private fun SplashOverlay(
    headerLogoRect: Rect?,
    onFinished: () -> Unit
) {
    val logo = rememberLogoBitmap()
    val density = LocalDensity.current
    // Posisi window Box splash sendiri (untuk mengubah koordinat window -> lokal).
    var splashRootPos by remember { mutableStateOf(Offset.Zero) }
    // Target pendaratan (koordinat lokal, ditangkap sekali saat animasi mulai supaya
    // tidak bergeser di tengah animasi).
    var flyTarget by remember { mutableStateOf<Rect?>(null) }
    val intro = remember { Animatable(0f) }
    val fly = remember { Animatable(0f) }
    val splashAlpha = remember { Animatable(1f) }
    LaunchedEffect(headerLogoRect) {
        if (headerLogoRect == null) return@LaunchedEffect
        flyTarget = Rect(headerLogoRect.topLeft - splashRootPos, headerLogoRect.size)
        intro.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        delay(300)
        fly.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        splashAlpha.animateTo(0f, tween(300))
        onFinished()
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer { alpha = splashAlpha.value }
            .onGloballyPositioned { splashRootPos = it.positionInWindow() }
            // Blokir sentuhan selama splash (tanpa ripple) supaya tidak ada
            // ketukan "buta" ke layar Beranda di bawahnya.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        val splashSizePx = with(density) { 120.dp.toPx() }
        val centerX = with(density) { maxWidth.toPx() / 2 }
        val centerY = with(density) { maxHeight.toPx() / 2 }
        val target = flyTarget
        val targetCenterX = target?.center?.x ?: centerX
        val targetCenterY = target?.center?.y ?: centerY
        val targetSize = target?.width ?: splashSizePx
        // Posisi + ukuran logo saat ini: lerp tengah -> target mengikuti progress terbang.
        val curCenterX = centerX + (targetCenterX - centerX) * fly.value
        val curCenterY = centerY + (targetCenterY - centerY) * fly.value
        val curSize = splashSizePx + (targetSize - splashSizePx) * fly.value
        // Skala intro (0.7 -> 1): selama intro logo membesar di tengah; selama terbang
        // intro sudah 1 sehingga ukuran murni mengikuti lerp.
        val dispSize = curSize * (0.7f + 0.3f * intro.value)
        val left = (curCenterX - dispSize / 2).roundToInt()
        val top = (curCenterY - dispSize / 2).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(left, top) }
                .size(with(density) { dispSize.toDp() })
                .graphicsLayer { alpha = intro.value },
            contentAlignment = Alignment.Center
        ) {
            if (logo != null) {
                // Struktur sama persis seperti logo header (lingkaran putih + logo
                // di dalamnya) supaya saat mendarat (44dp) terlihat identik.
                Surface(color = Color.White, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                    Image(bitmap = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp).fillMaxSize())
                }
            }
        }
        // Nama aplikasi di bawah logo tengah — muncul saat intro, memudar saat logo terbang.
        Text(
            "Apotek Lestari",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = with(density) { (splashSizePx / 2 + 16.dp.toPx()).toDp() })
                .graphicsLayer { alpha = intro.value * (1f - fly.value) }
        )
    }
}




