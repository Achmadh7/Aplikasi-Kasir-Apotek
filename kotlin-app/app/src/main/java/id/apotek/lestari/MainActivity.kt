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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import id.apotek.lestari.data.FirebaseKit
import id.apotek.lestari.data.HasilReset
import id.apotek.lestari.data.HasilSync
import id.apotek.lestari.data.Repository
import id.apotek.lestari.data.SyncManager
import id.apotek.lestari.data.jadwalkanSyncBerkala
import id.apotek.lestari.data.local.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.room.InvalidationTracker
import id.apotek.lestari.ui.rememberLogoBitmap
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

enum class StatusSync { NONAKTIF, BELUM_MASUK, PROSES, BERHASIL, GAGAL }

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
    // bawah, karena saat itu baris tema masih di dalam sheet bawah yang
    // posisinya tidak stabil. Sejak A2 baris tema tinggal di halaman penuh,
    // jadi AN3 (pilihan Achmad 2026-11-04) memindahkan titik awalnya ke
    // tengah baris tema itu sendiri — lingkaran membesar dari persis yang
    // diketuk; ikon tab tetap jadi cadangan bila baris belum terukur.
    // Bila tangkapan gagal, tema berganti seketika seperti sebelumnya.
    val rootView = LocalView.current
    val appScope = rememberCoroutineScope()
    // ---- Mode Online (Firebase): gerbang login + status sinkronisasi.
    // onlineSiap hanya benar bila build memuat google-services.json;
    // tanpa itu seluruh lapisan ini diam dan aplikasi murni offline.
    val context = LocalContext.current
    val onlinePrefs = remember { context.getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE) }
    val repoOnline = remember { Repository() }
    val onlineSiap = remember { FirebaseKit.siap(context) }
    var emailOnline by remember { mutableStateOf(if (onlineSiap) FirebaseKit.emailMasuk() else null) }
    var lewatiLogin by remember { mutableStateOf(false) }
    var statusSync by remember {
        mutableStateOf(
            when {
                !onlineSiap -> StatusSync.NONAKTIF
                emailOnline != null -> StatusSync.BERHASIL
                else -> StatusSync.BELUM_MASUK
            }
        )
    }
    var syncTerakhir by remember { mutableLongStateOf(onlinePrefs.getLong("online_sync_terakhir", 0L)) }
    var sibukOnline by remember { mutableStateOf(false) }
    var pesanOnline by remember { mutableStateOf<String?>(null) }
    var pesanStatus by remember { mutableStateOf<String?>(null) }
    var tampilInfoOnline by remember { mutableStateOf(false) }
    var tampilKonfirmasiKeluar by remember { mutableStateOf(false) }
    var tampilKonfirmasiReset by remember { mutableStateOf(false) }
    var sibukReset by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val perluLogin = onlineSiap && emailOnline == null && !lewatiLogin

    // senyap = sinkron latar (otomatis): tanpa snackbar "Sinkronisasi
    // selesai" — laporan Achmad 2026-11-10 bahwa notifikasinya muncul
    // terus setiap sinkron otomatis berjalan. Status tetap terlihat
    // dari ikon awan di Beranda. Sinkron manual & saat masuk tetap
    // memberi kabar.
    fun jalankanSync(senyap: Boolean = false) {
        if (!onlineSiap || emailOnline == null || statusSync == StatusSync.PROSES) return
        statusSync = StatusSync.PROSES
        appScope.launch {
            when (val h = SyncManager(repoOnline).sinkronSekarang(context)) {
                is HasilSync.Berhasil -> {
                    statusSync = StatusSync.BERHASIL
                    syncTerakhir = System.currentTimeMillis()
                    onlinePrefs.edit().putLong("online_sync_terakhir", syncTerakhir).apply()
                    if (!senyap) pesanStatus = tr("Sinkronisasi selesai", "Sync complete")
                }
                is HasilSync.Gagal -> {
                    statusSync = StatusSync.GAGAL
                    if (!senyap) pesanStatus = h.pesan
                }
                HasilSync.BelumMasuk -> statusSync = StatusSync.BELUM_MASUK
                HasilSync.BelumDikonfigurasi -> statusSync = StatusSync.NONAKTIF
            }
        }
    }

    // Reset Semua Data (opsi B, permintaan Achmad 2026-11-10): hapus
    // awan lebih dulu — bila awan gagal dibersihkan, lokal TIDAK
    // dihapus karena datanya pasti turun lagi. Lokal dikosongkan
    // sesudahnya, penanda backup + waktu sinkron terakhir dibersihkan,
    // akun tetap masuk (yang dihapus datanya, bukan akunnya).
    fun aksiResetSemua() {
        appScope.launch {
            sibukReset = true
            try {
                if (onlineSiap && emailOnline != null) {
                    val h = SyncManager(repoOnline).hapusSemuaDataCloud(context)
                    if (h is HasilReset.Gagal) {
                        pesanStatus = tr("Gagal menghapus data awan: ", "Failed to delete cloud data: ") + h.pesan
                        return@launch
                    }
                }
                repoOnline.hapusSemuaDataLokal()
                onlinePrefs.edit()
                    .remove("online_sync_terakhir")
                    .remove("last_backup_at")
                    .remove("last_backup_name")
                    .apply()
                syncTerakhir = 0L
                tampilKonfirmasiReset = false
                pesanStatus = tr("Semua data dihapus. Mulai dari nol.", "All data deleted. Starting from zero.")
            } finally {
                sibukReset = false
            }
        }
    }

    fun aksiSinkronTap() {
        when {
            !onlineSiap -> tampilInfoOnline = true
            emailOnline != null -> jalankanSync()
            else -> lewatiLogin = false
        }
    }

    fun aksiMasukGoogle() {
        appScope.launch {
            sibukOnline = true
            pesanOnline = null
            try {
                emailOnline = FirebaseKit.masukDenganGoogle(context)
                lewatiLogin = false
            } catch (e: Exception) {
                pesanOnline = e.message ?: tr("Gagal masuk dengan Google", "Google sign-in failed")
            } finally {
                sibukOnline = false
            }
        }
    }

    LaunchedEffect(emailOnline) {
        if (emailOnline != null) {
            jadwalkanSyncBerkala(context)
            jalankanSync()
        } else if (onlineSiap) {
            statusSync = StatusSync.BELUM_MASUK
        }
    }

    // Sinkron otomatis ±5 detik setelah data berubah selagi masuk.
    // Awalnya Apotek hanya sinkron saat masuk, saat diketuk manual, dan
    // lewat pekerja berkala 6 jam — jadi impor Excel dan perubahan lain
    // terasa "tidak langsung tersinkron" (laporan Achmad 2026-11-10;
    // KasBulanan sudah punya pemicu ini). Pengamat Room atas keenam
    // tabel menangkap tulisan apa pun (catat, impor, hapus lunak); hasil
    // tarikan sinkron sendiri bisa memicu satu putaran ekstra yang
    // kosong, lalu berhenti.
    DisposableEffect(emailOnline) {
        if (!onlineSiap || emailOnline == null) {
            onDispose { }
        } else {
            var tunda: Job? = null
            val pengamat = object : InvalidationTracker.Observer(
                "medicines", "sales", "sale_items", "cash_entries", "purchases", "purchase_items"
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    tunda?.cancel()
                    tunda = appScope.launch {
                        delay(5000)
                        // Hanya jalan bila memang ada data kotor: tulisan
                        // dari sinkron itu sendiri tidak memicu putaran
                        // kosong, dan sinkron otomatis berjalan senyap.
                        if (repoOnline.adaDataKotor()) jalankanSync(senyap = true)
                    }
                }
            }
            DatabaseProvider.db.invalidationTracker.addObserver(pengamat)
            onDispose {
                tunda?.cancel()
                DatabaseProvider.db.invalidationTracker.removeObserver(pengamat)
            }
        }
    }
    LaunchedEffect(pesanStatus) {
        pesanStatus?.let {
            snackbarHostState.showSnackbar(it)
            pesanStatus = null
        }
    }
    // Urutan tab untuk swipe (kiri ke kanan): Beranda, Stok, Kasir, Keuangan,
    // Lainnya. Geser ke kiri dari Keuangan membuka halaman Lainnya (sama
    // seperti mengetuk tab Lainnya).
    val swipeableTabs = listOf("beranda", "stok", "kasir", "keuangan", "lainnya")
    // Arah navigasi antar tab (1 = maju/ke kanan, -1 = mundur/ke kiri) —
    // dipakai transisi NavHost supaya layar mengikuti arah geser/ketukan.
    var navDirection by remember { mutableIntStateOf(1) }
    // Sub-menu yang ditumpuk di atas tab (dari Beranda/Lainnya) — dipakai
    // logika Kembali di goToTab dan penyama durasi transisi NavHost.
    val subRoutes = listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang", "pbf")
    fun tabPosition(route: String): Int = when (route) {
        "beranda" -> 0
        "stok" -> 1
        "kasir" -> 2
        "keuangan" -> 3
        else -> 4 // Lainnya + sub-menunya
    }
    fun goToTab(route: String) {
        navDirection = if (tabPosition(route) >= tabPosition(current)) 1 else -1
        // Kembali ke tab induk dari sub-menu (mis. Tentang -> Lainnya lewat
        // bar bawah) harus POP: navigate() biasa memainkan transisi MAJU
        // (sub-menu keluar hanya -1/4 ke kiri) sehingga terasa seperti
        // membuka halaman baru, bukan kembali. popBackStack memainkan
        // transisi mundur yang benar (sub-menu keluar penuh ke kanan).
        // Bila tab tujuan tidak ada di tumpukan, popBackStack false dan
        // alur navigate biasa di bawah yang berjalan.
        if (current in subRoutes && nav.popBackStack(route, false)) return
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
    if (perluLogin) {
        LayarLogin(
            googleSiap = FirebaseKit.googleSiap(context),
            sibuk = sibukOnline,
            pesan = pesanOnline,
            onGoogle = { aksiMasukGoogle() },
            onLewati = { lewatiLogin = true }
        )
    } else {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Transisi sub-halaman TR1 (pilihan Achmad 2026-11-04, "Geser
            // Penuh ala iOS") \u2014 hanya untuk tujuan sub-halaman dari
            // Lainnya (laba, pengingat, riwayat, backup, terlaris, pbf,
            // tentang): halaman baru meluncur PENUH dari kanan (350ms),
            // Transisi sub-menu: TANPA animasi (None) — sejak penghapusan
            // semua animasi 2026-11-10 halaman sub tampil seketika; yang
            // beranimasi hanya pindah antar tab utama (T3 di NavHost).
            val subEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { EnterTransition.None }
            val subExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { ExitTransition.None }
            val subPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { EnterTransition.None }
            val subPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { ExitTransition.None }
            NavHost(
                navController = nav,
                startDestination = "beranda",
                // Transisi antar tab = T3 (pilihan Achmad 2026-11-10 dari
                // preview GIF T1-T5): layar baru memudar masuk sambil
                // membesar mikro dari skala 0,98 ke penuh, 150ms; layar
                // lama cukup memudar keluar. Enteng, tanpa geser sama
                // sekali (geser 200-350ms dulu terasa jitter/bergulir).
                enterTransition = { fadeIn(tween(150)) + scaleIn(initialScale = 0.98f, animationSpec = tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) + scaleIn(initialScale = 0.98f, animationSpec = tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
                modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth()
            ) {
                composable("beranda") { BerandaScreen(onGoStok = { goToTab("stok") }, onGoPengingat = { goToTab("pengingat") }, onGoLaba = { goToTab("laba") }, onGoRiwayat = { goToTab("riwayat") }, statusSync = statusSync, onSyncTap = { aksiSinkronTap() }) }
                composable("kasir") { KasirScreen() }
                composable("stok") { StokScreen() }
                composable("keuangan") { KeuanganScreen() }
                // Petak Lainnya memakai navigate() biasa (tumpuk), BUKAN goToTab:
                // goToTab me-popUpTo("beranda") sehingga halaman Lainnya ikut
                // terbuang dari tumpukan dan tombol Kembali dari sub-menu
                // mendarat di Beranda, bukan di Lainnya (laporan Achmad
                // 2026-11-04). Dengan tumpukan biasa, Kembali dari
                // Laba/Pengingat/dll. kembali ke halaman Lainnya.
                composable("lainnya") { LainnyaScreen(onGo = { route -> navDirection = 1; nav.navigate(route) { launchSingleTop = true } }, isDark = isDark, onToggleTheme = onToggleTheme, appLang = appLang, onLanguageChange = onLanguageChange, onlineSiap = onlineSiap, emailOnline = emailOnline, statusSync = statusSync, syncTerakhir = syncTerakhir, onSinkronTap = { aksiSinkronTap() }, onKeluarTap = { tampilKonfirmasiKeluar = true }, onResetTap = { tampilKonfirmasiReset = true }) }
                composable("laba", enterTransition = subEnter, exitTransition = subExit, popEnterTransition = subPopEnter, popExitTransition = subPopExit) { LabaScreen() }
                composable("pengingat", enterTransition = subEnter, exitTransition = subExit, popEnterTransition = subPopEnter, popExitTransition = subPopExit) { PengingatScreen(onGoStok = { goToTab("stok") }, onGoPbf = { goToTab("pbf") }) }
                composable("riwayat", enterTransition = subEnter, exitTransition = subExit, popEnterTransition = subPopEnter, popExitTransition = subPopExit) { RiwayatScreen() }
                composable("backup", enterTransition = subEnter, exitTransition = subExit, popEnterTransition = subPopEnter, popExitTransition = subPopExit) { BackupScreen() }
                composable("terlaris", enterTransition = subEnter, exitTransition = subExit, popEnterTransition = subPopEnter, popExitTransition = subPopExit) { ObatTerlarisScreen() }
                composable("pbf", enterTransition = subEnter, exitTransition = subExit, popEnterTransition = subPopEnter, popExitTransition = subPopExit) { PbfScreen() }
                composable("tentang", enterTransition = subEnter, exitTransition = subExit, popEnterTransition = subPopEnter, popExitTransition = subPopExit) { TentangScreen(onGoBackup = { navDirection = 1; nav.navigate("backup") { launchSingleTop = true } }) }
            }
        }
    }
    if (tampilInfoOnline) {
        AlertDialog(
            onDismissRequest = { tampilInfoOnline = false },
            title = { Text(tr("Mode Online", "Online Mode")) },
            text = {
                Text(
                    tr(
                        "Build ini belum dikonfigurasi untuk Mode Online, jadi aplikasi berjalan lokal sepenuhnya di HP ini. Mode Online aktif setelah proyek Firebase disiapkan dan file google-services.json masuk ke repo.",
                        "This build is not configured for Online Mode yet, so the app runs fully locally on this phone. Online Mode activates once a Firebase project is set up and google-services.json is added to the repo."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { tampilInfoOnline = false }) { Text(tr("Mengerti", "Got it")) }
            }
        )
    }
    if (tampilKonfirmasiKeluar) {
        AlertDialog(
            onDismissRequest = { tampilKonfirmasiKeluar = false },
            title = { Text(tr("Keluar Akun?", "Sign Out?")) },
            text = {
                Text(
                    tr(
                        "Data di HP ini tidak berubah dan tidak terhapus. Sinkronisasi berhenti sampai kamu masuk lagi.",
                        "Data on this phone stays unchanged and is not deleted. Syncing stops until you sign in again."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tampilKonfirmasiKeluar = false
                        FirebaseKit.keluar()
                        emailOnline = null
                        lewatiLogin = false
                    }
                ) { Text(tr("Keluar", "Sign Out"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { tampilKonfirmasiKeluar = false }) { Text(tr("Batal", "Cancel")) }
            }
        )
    }

if (tampilKonfirmasiReset) {
        AlertDialog(
            onDismissRequest = { if (!sibukReset) tampilKonfirmasiReset = false },
            title = { Text(tr("Reset Semua Data?", "Reset All Data?")) },
            text = {
                Text(
                    if (emailOnline != null) tr(
                        "Semua data obat, penjualan, kas, dan Faktur PBF di HP ini DAN salinan awannya untuk akun " + emailOnline + " akan dihapus permanen. Aplikasi mulai dari kosong. Ini tidak bisa dibatalkan — buat Backup dulu bila masih ada yang dibutuhkan.",
                        "All medicine, sales, cash, and PBF invoice data on this phone AND its cloud copy for account " + emailOnline + " will be permanently deleted. The app starts empty. This cannot be undone — make a Backup first if anything is still needed."
                    ) else tr(
                        "Semua data obat, penjualan, kas, dan Faktur PBF di HP ini akan dihapus permanen dan aplikasi mulai dari kosong. Ini tidak bisa dibatalkan — buat Backup dulu bila masih ada yang dibutuhkan. (Mode tamu: bila nanti masuk ke akun yang sudah punya data, data akun itu akan turun lagi.)",
                        "All medicine, sales, cash, and PBF invoice data on this phone will be permanently deleted and the app starts empty. This cannot be undone — make a Backup first if anything is still needed. (Guest mode: signing in later to an account that already has data will bring that data back down.)"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { aksiResetSemua() }, enabled = !sibukReset) {
                    Text(if (sibukReset) tr("Menghapus…", "Deleting…") else tr("Ya", "Yes"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { tampilKonfirmasiReset = false }, enabled = !sibukReset) { Text(tr("Tidak", "No")) }
            }
        )
    }
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
    onLanguageChange: (String) -> Unit,
    onlineSiap: Boolean,
    emailOnline: String?,
    statusSync: StatusSync,
    syncTerakhir: Long,
    onSinkronTap: () -> Unit,
    onKeluarTap: () -> Unit,
    onResetTap: () -> Unit
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
            LainnyaTile("terlaris", tr("Terlaris", "Best Sellers"), tr("Peringkat penjualan", "Sales ranking"), Icons.Default.Leaderboard),
            LainnyaTile("pengingat", tr("Pengingat", "Reminders"), tr("Stok & tagihan", "Stock & bills"), Icons.Default.NotificationsActive),
            LainnyaTile("riwayat", tr("Riwayat", "History"), tr("Semua penjualan", "All sales"), Icons.Default.ReceiptLong),
            LainnyaTile("pbf", tr("Faktur PBF", "PBF Invoices"), tr("Pembelian distributor", "Distributor purchases"), Icons.Default.LocalShipping),
            LainnyaTile("backup", tr("Backup", "Backup"), tr("Cadangkan data", "Back up data"), Icons.Default.Backup)
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
        // Baris ini juga melaporkan titik tengahnya untuk asal animasi AN3.
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
        // Status Mode Online/Offline (kartu info) + sinkronisasi manual +
        // keluar akun — pola yang sama dengan KasBulanan.
        val subStatus = when {
            emailOnline != null -> emailOnline + "\n" + (
                if (syncTerakhir > 0) {
                    tr("Sinkron terakhir: ", "Last sync: ") +
                        SimpleDateFormat("d MMM yyyy, HH.mm", Locale("id", "ID")).format(Date(syncTerakhir))
                } else {
                    tr("Belum pernah sinkron", "Never synced")
                }
                )
            onlineSiap -> tr("Mode tamu \u2014 data hanya tersimpan di HP ini", "Guest mode \u2014 data is only stored on this phone")
            else -> tr("Aplikasi berjalan lokal sepenuhnya di HP ini", "The app runs fully locally on this phone")
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = SpaceS)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (emailOnline != null) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    null,
                    tint = if (emailOnline != null) Color(0xFF43A047) else Color(0xFFE3B341),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (emailOnline != null) tr("Mode Online", "Online Mode") else tr("Mode Offline", "Offline Mode"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        ListItem(
            headlineContent = { Text(tr("Sinkronisasi", "Sync"), style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text(
                    when {
                        statusSync == StatusSync.PROSES -> tr("Sedang menyinkron\u2026", "Syncing\u2026")
                        emailOnline != null -> tr("Ketuk untuk sinkron sekarang", "Tap to sync now")
                        onlineSiap -> tr("Masuk untuk mengaktifkan cadangan awan", "Sign in to enable cloud backup")
                        else -> tr("Belum dikonfigurasi", "Not configured")
                    }
                )
            },
            leadingContent = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
            },
            trailingContent = {
                if (statusSync == StatusSync.PROSES) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onSinkronTap() }
        )
        if (emailOnline != null) {
            ListItem(
                headlineContent = { Text(tr("Keluar Akun", "Sign Out"), style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(emailOnline) },
                leadingContent = {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onKeluarTap() }
            )
        }
        // Tentang Aplikasi, di bawah ganti bahasa.
        ListItem(
            headlineContent = { Text(tr("Tentang", "About"), style = MaterialTheme.typography.titleMedium) },
            leadingContent = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
                }
            },
            trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onGo("tentang") }
        )
        // Zona berbahaya paling bawah: Reset Semua Data (opsi B).
        ListItem(
            headlineContent = { Text(tr("Reset Semua Data", "Reset All Data"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text(tr("Hapus data di HP ini dan di awan, mulai dari nol", "Delete data on this phone and in the cloud, start from zero")) },
            leadingContent = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                    Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(10.dp).size(22.dp))
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onResetTap() }
        )
    }
}

// ---- Gerbang login Mode Online (pola L1+L4 yang sama dengan KasBulanan):
// satu kartu besar di tengah, ikon aplikasi melingkari tepi atas kartu,
// tombol Google putih, lalu Lewati sebagai tamu. Sejak 2026-11-10 login
// HANYA Google (keputusan Achmad): formulir email/kata sandi dihapus —
// akunnya memang akun Google, dan jalur email hanya membuka risiko
// akun terbelah. Hanya tampil bila build terkonfigurasi Firebase dan
// belum ada yang masuk.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarLogin(
    googleSiap: Boolean,
    sibuk: Boolean,
    pesan: String?,
    onGoogle: () -> Unit,
    onLewati: () -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.TopCenter) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 38.dp)
                ) {
                    Column(
                        Modifier.padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Apotek Lestari",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            tr("Masuk untuk mencadangkan data ke akunmu", "Sign in to back up your data to your account"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(24.dp))
                        if (googleSiap) {
                            Button(
                                onClick = onGoogle,
                                enabled = !sibuk,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF1F1F1F)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                if (sibuk) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1F1F1F))
                                } else {
                                    LogoGoogle(Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(tr("Masuk dengan Google", "Sign in with Google"), fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text(
                                tr("Masuk dengan Google belum tersedia di build ini.", "Google sign-in is not available in this build."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (pesan != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                pesan,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onLewati, enabled = !sibuk) {
                            Text(tr("Lewati — Pakai Tanpa Akun", "Skip — Use Without an Account"))
                        }
                        Text(
                            tr("Mode tamu: data hanya tersimpan di HP ini", "Guest mode: data is only stored on this phone"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val logoGate = rememberLogoBitmap()
                if (logoGate != null) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Image(
                            logoGate,
                            null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.padding(8.dp).fillMaxSize()
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalPharmacy,
                            null,
                            tint = Color.White,
                            modifier = Modifier.padding(18.dp).fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// Logo "G" Google digambar dengan Canvas (empat warna resmi di atas cakram
// putih) — tanpa aset gambar tambahan.
@Composable
private fun LogoGoogle(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.White, r, c)
        val stroke = r * 0.5f
        val arcR = r * 0.6f
        val kiri = Offset(c.x - arcR, c.y - arcR)
        val uk = Size(arcR * 2f, arcR * 2f)
        drawArc(Color(0xFF4285F4), -50f, 100f, false, kiri, uk, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFFEA4335), -140f, 90f, false, kiri, uk, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFFFBBC05), 130f, 80f, false, kiri, uk, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawArc(Color(0xFF34A853), 50f, 80f, false, kiri, uk, style = Stroke(stroke, cap = StrokeCap.Butt))
        drawRect(
            Color(0xFF4285F4),
            topLeft = Offset(c.x - stroke * 0.2f, c.y - stroke / 2f),
            size = Size(arcR + stroke * 0.2f, stroke)
        )
    }
}
