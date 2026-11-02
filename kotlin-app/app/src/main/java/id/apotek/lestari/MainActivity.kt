package id.apotek.lestari

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import kotlin.math.hypot
import kotlinx.coroutines.launch
import id.apotek.lestari.ui.screens.*
import id.apotek.lestari.ui.theme.ApotikLestariTheme
import id.apotek.lestari.ui.theme.AppLang
import id.apotek.lestari.ui.theme.tr

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
            // Bahasa aplikasi (id/en) — tersimpan seperti tema, dan disalin
            // ke AppLang.code yang dibaca tr() di semua layar.
            var appLang by remember { mutableStateOf(prefs.getString("app_language", "id") ?: "id") }
            AppLang.code = appLang
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
                        },
                        appLang = appLang,
                        onLanguageChange = { next ->
                            appLang = next
                            prefs.edit().putString("app_language", next).apply()
                        }
                    )
                }
            }
        }
    }
}

/** Tangkapan bitmap tampilan saat ini, untuk animasi pergantian tema. */
private fun captureScreen(view: View): Bitmap? = try {
    if (view.width <= 0 || view.height <= 0) {
        null
    } else {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        bmp
    }
} catch (e: Exception) {
    null
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApotekApp(isDark: Boolean, onToggleTheme: () -> Unit, appLang: String, onLanguageChange: (String) -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "beranda"
    // Sheet "Lainnya": Laba, Pengingat, Riwayat, Backup & Restore — menu yang tidak lagi ada di bar bawah.
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState()
    // Animasi lingkaran tema (dipilih Achmad 2026-11-01, opsi 1 — sama seperti
    // di Image HD Upscale): tangkapan layar tema lama ditumpuk di atas tema
    // baru, dilubangi lingkaran yang membesar, sehingga tema baru tersingkap
    // mulai dari titik awalnya. Titik awalnya adalah ikon tab "Lainnya" di bar
    // bawah (tombol yang membuka sheet berisi baris tema) — baris tema sendiri
    // berada di jendela sheet yang terpisah, jadi posisinya tidak bisa dipakai
    // sebagai titik ukur yang stabil; pola yang sama dipakai HD Upscale dari
    // tombol gir pembuka Pengaturan. Bila tangkapan gagal, tema berganti
    // seketika seperti sebelumnya.
    val rootView = LocalView.current
    val appScope = rememberCoroutineScope()
    var lainnyaIconCenter by remember { mutableStateOf(Offset.Zero) }
    var revealBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val revealRadius = remember { Animatable(0f) }
    fun toggleThemeWithReveal() {
        val shot = if (revealBitmap == null) captureScreen(rootView) else null
        if (shot != null && lainnyaIconCenter != Offset.Zero) {
            revealBitmap = shot
            onToggleTheme()
            val center = lainnyaIconCenter
            val w = rootView.width.toFloat()
            val h = rootView.height.toFloat()
            val maxR = hypot(
                maxOf(center.x, w - center.x).toDouble(),
                maxOf(center.y, h - center.y).toDouble()
            ).toFloat() + 8f
            appScope.launch {
                revealRadius.snapTo(0f)
                revealRadius.animateTo(
                    maxR,
                    tween(650, easing = FastOutSlowInEasing)
                )
                revealBitmap = null
            }
        } else {
            onToggleTheme()
        }
    }
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
        // saveState + restoreState (audit 2026-11-02, H1): state saveable
        // tiap layar (keranjang Kasir, pencarian, metode bayar, dst.)
        // disimpan saat tab ditinggalkan dan dipulihkan saat kembali —
        // tanpa ini, popUpTo membuang SavedStateRegistry layar yang di-pop
        // sehingga keranjang Kasir hilang setiap pindah tab.
        nav.navigate(route) {
            popUpTo("beranda") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
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
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            // Bar bawah memakai komponen RESMI Material 3 Expressive
            // (permintaan Achmad 2026-11-01, menggantikan dock liquid
            // glass buatan tangan): ShortNavigationBar +
            // ShortNavigationBarItem dari Material3 1.5.0-alpha08.
            // Warna mengikuti bawaan komponen (wadah surfaceContainer,
            // indikator primaryContainer) dan gerak indikator mengikuti
            // MotionScheme expressive dari tema. Susunan EqualWeight
            // sesuai anjuran dokumentasi untuk 3-5 tujuan di layar kecil.
            // Label selalu tampil (bawaan komponen); ikon aktif filled,
            // tidak aktif outlined. Tab "Lainnya" bukan tujuan: ketuknya
            // membuka sheet, dan ia tampak terpilih hanya saat berada di
            // salah satu sub-menunya (sama seperti perilaku dock lama).
            ShortNavigationBar(arrangement = ShortNavigationBarArrangement.EqualWeight) {
                TABS.forEach { tab ->
                    val selected = when (tab.route) {
                        "beranda" -> current == "beranda"
                        "stok" -> current == "stok"
                        "kasir" -> current == "kasir"
                        "keuangan" -> current == "keuangan"
                        else -> current in listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang", "pbf")
                    }
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { if (tab.route == "lainnya") showMoreSheet = true else goToTab(tab.route) },
                        icon = {
                            Icon(
                                if (selected) tab.icon else tab.iconOutlined,
                                contentDescription = tab.label,
                                modifier = if (tab.route == "lainnya") {
                                    Modifier.onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot()
                                        lainnyaIconCenter = Offset(
                                            pos.x + coords.size.width / 2f,
                                            pos.y + coords.size.height / 2f
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                        },
                        label = {
                            Text(
                                when (tab.route) {
                                    "beranda" -> tr("Beranda", "Home")
                                    "stok" -> tr("Stok", "Stock")
                                    "kasir" -> tr("Kasir", "Cashier")
                                    "keuangan" -> tr("Keuangan", "Finance")
                                    else -> tr("Lainnya", "More")
                                }
                            )
                        }
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
    // Lapisan animasi lingkaran tema: tangkapan tema lama dengan lubang
    // lingkaran yang membesar dari ikon tab Lainnya; di bawah lubang itu
    // tema baru sudah tampil, jadi tema baru tampak mengembang keluar.
    val shot = revealBitmap
    if (shot != null) {
        val center = lainnyaIconCenter
        val r = revealRadius.value
        Image(
            bitmap = shot.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .clip(GenericShape { size, _ ->
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    if (r > 0f) {
                        addOval(
                            Rect(
                                Offset(center.x - r, center.y - r),
                                Size(r * 2f, r * 2f)
                            )
                        )
                    }
                })
        )
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
                Text(tr("Menu Lainnya", "More Menu"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                listOf(
                    Triple("laba", tr("Laba", "Profit"), Icons.Default.TrendingUp),
                    Triple("terlaris", tr("Obat Terlaris", "Best Sellers"), Icons.Default.Leaderboard),
                    Triple("pengingat", tr("Pengingat", "Reminders"), Icons.Default.NotificationsActive),
                    Triple("riwayat", tr("Riwayat", "History"), Icons.Default.ReceiptLong),
                    Triple("pbf", tr("Faktur PBF", "PBF Invoices"), Icons.Default.LocalShipping),
                    Triple("backup", tr("Backup & Restore", "Backup & Restore"), Icons.Default.Backup)
                ).forEach { (route, label, icon) ->
                    ListItem(
                        headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
                        leadingContent = {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                            }
                        },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            showMoreSheet = false
                            goToTab(route)
                        }
                    )
                }
                // Ganti tema Gelap <-> Terang (pilihan tersimpan, dipakai lagi saat aplikasi dibuka).
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                ListItem(
                    headlineContent = {
                        Text(
                            if (isDark) tr("Mode Terang", "Light Mode") else tr("Mode Gelap", "Dark Mode"),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    leadingContent = {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                            Icon(
                                if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(22.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        toggleThemeWithReveal()
                        showMoreSheet = false
                    }
                )
                // Ganti bahasa Indonesia <-> Inggris, di bawah ganti tema
                // (permintaan Achmad 2026-11-02). Pola sama seperti baris
                // tema: ketuk langsung berganti ke bahasa satunya dan sheet
                // menutup; pilihan tersimpan untuk kunjungan berikutnya.
                ListItem(
                    headlineContent = {
                        Text(
                            if (appLang == "en") "Bahasa Indonesia" else "English",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    leadingContent = {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                            Icon(
                                Icons.Default.Language,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(22.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onLanguageChange(if (appLang == "en") "id" else "en")
                        showMoreSheet = false
                    }
                )
                // Tentang Aplikasi, di bawah ganti tema.
                ListItem(
                    headlineContent = { Text(tr("Tentang Aplikasi", "About App"), style = MaterialTheme.typography.titleMedium) },
                    leadingContent = {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                        }
                    },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        showMoreSheet = false
                        goToTab("tentang")
                    }
                )
            }
        }
    }
}
