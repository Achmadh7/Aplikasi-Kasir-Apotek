package id.apotek.lestari

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
import id.apotek.lestari.ui.theme.ExpressiveCard
import id.apotek.lestari.ui.theme.SpaceL
import id.apotek.lestari.ui.theme.SpaceM
import id.apotek.lestari.ui.theme.SpaceS

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
    // Halaman "Lainnya" (redesain A2, pilihan Achmad 2026-11-04): dulu sheet
    // bawah, kini layar penuh berisi petak besar menu + baris pengaturan.
    // Animasi lingkaran tema (dipilih Achmad 2026-11-01, opsi 1 — sama seperti
    // di Image HD Upscale): tangkapan layar tema lama ditumpuk di atas tema
    // baru, dilubangi lingkaran yang membesar, sehingga tema baru tersingkap
    // mulai dari titik awalnya. Titik awalnya adalah ikon tab "Lainnya" di bar
    // bawah — baris tema sendiri berada di halaman Lainnya, jadi posisinya
    // tidak bisa dipakai sebagai titik ukur yang stabil; pola yang sama dipakai HD Upscale dari
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
    // Lainnya. Geser ke kiri dari Keuangan membuka halaman Lainnya (sama
    // seperti mengetuk tab Lainnya).
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
                        goToTab(swipeableTabs[currentIndex + 1]) // Geser ke kiri -> tab berikutnya
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
            // tidak aktif outlined. Tab "Lainnya" kini tujuan sungguhan
            // (halaman petak besar A2) dan tampak terpilih di halamannya
            // sendiri maupun di salah satu sub-menunya.
            ShortNavigationBar(arrangement = ShortNavigationBarArrangement.EqualWeight) {
                TABS.forEach { tab ->
                    val selected = when (tab.route) {
                        "beranda" -> current == "beranda"
                        "stok" -> current == "stok"
                        "kasir" -> current == "kasir"
                        "keuangan" -> current == "keuangan"
                        else -> current == "lainnya" || current in listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang", "pbf")
                    }
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { goToTab(tab.route) },
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
                // Petak Lainnya memakai navigate() biasa (tumpuk), BUKAN goToTab:
                // goToTab me-popUpTo("beranda") sehingga halaman Lainnya ikut
                // terbuang dari tumpukan dan tombol Kembali dari sub-menu
                // mendarat di Beranda, bukan di Lainnya (laporan Achmad
                // 2026-11-04). Dengan tumpukan biasa, Kembali dari
                // Laba/Pengingat/dll. kembali ke halaman Lainnya.
                composable("lainnya") { LainnyaScreen(onGo = { route -> navDirection = 1; nav.navigate(route) { launchSingleTop = true } }, isDark = isDark, onToggleTheme = { toggleThemeWithReveal() }, appLang = appLang, onLanguageChange = onLanguageChange) }
                composable("laba") { LabaScreen() }
                composable("pengingat") { PengingatScreen(onGoStok = { goToTab("stok") }, onGoPbf = { goToTab("pbf") }) }
                composable("riwayat") { RiwayatScreen() }
                composable("backup") { BackupScreen() }
                composable("terlaris") { ObatTerlarisScreen() }
                composable("pbf") { PbfScreen() }
                composable("tentang") { TentangScreen(onGoBackup = { navDirection = 1; nav.navigate("backup") { launchSingleTop = true } }) }
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
}

private data class LainnyaTile(
    val route: String,
    val label: String,
    val sub: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// Halaman "Lainnya" — redesain A2 "Halaman Petak Besar" (pilihan Achmad
// 2026-11-04, menggantikan sheet bawah): enam menu utama sebagai petak
// besar dua kolom dengan subjudul penjelas; di bawahnya baris pengaturan
// (tema, bahasa, tentang) berperilaku sama seperti dulu di sheet. Halaman
// bisa digulir supaya tetap utuh di mode lanskap.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LainnyaScreen(
    onGo: (String) -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    appLang: String,
    onLanguageChange: (String) -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                Icon(Icons.Default.MoreHoriz, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(tr("Lainnya", "More"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(SpaceL))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(SpaceL))
        val tiles = listOf(
            LainnyaTile("laba", tr("Laba", "Profit"), tr("Laba & margin", "Profit & margins"), Icons.Default.TrendingUp),
            LainnyaTile("terlaris", tr("Obat Terlaris", "Best Sellers"), tr("Peringkat penjualan", "Sales ranking"), Icons.Default.Leaderboard),
            LainnyaTile("pengingat", tr("Pengingat", "Reminders"), tr("Stok & tagihan", "Stock & bills"), Icons.Default.NotificationsActive),
            LainnyaTile("riwayat", tr("Riwayat", "History"), tr("Semua penjualan", "All sales"), Icons.Default.ReceiptLong),
            LainnyaTile("pbf", tr("Faktur PBF", "PBF Invoices"), tr("Pembelian distributor", "Distributor purchases"), Icons.Default.LocalShipping),
            LainnyaTile("backup", tr("Backup & Restore", "Backup & Restore"), tr("Cadangkan data", "Back up data"), Icons.Default.Backup)
        )
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceM), modifier = Modifier.fillMaxWidth()) {
                rowTiles.forEach { tile ->
                    ExpressiveCard(modifier = Modifier.weight(1f).height(128.dp).clickable { onGo(tile.route) }) {
                        Column(Modifier.padding(16.dp)) {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                                Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                            }
                            Spacer(Modifier.height(SpaceM))
                            Text(tile.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(tile.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(SpaceM))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = SpaceS))
        // Ganti tema Gelap <-> Terang (pilihan tersimpan, dipakai lagi saat aplikasi dibuka).
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
            modifier = Modifier.clickable { onToggleTheme() }
        )
        // Ganti bahasa Indonesia <-> Inggris, di bawah ganti tema
        // (permintaan Achmad 2026-11-02). Ketuk langsung berganti ke bahasa
        // satunya; pilihan tersimpan untuk kunjungan berikutnya.
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
            modifier = Modifier.clickable { onLanguageChange(if (appLang == "en") "id" else "en") }
        )
        // Tentang Aplikasi, di bawah ganti bahasa.
        ListItem(
            headlineContent = { Text(tr("Tentang Aplikasi", "About App"), style = MaterialTheme.typography.titleMedium) },
            leadingContent = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
            },
            trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onGo("tentang") }
        )
    }
}
