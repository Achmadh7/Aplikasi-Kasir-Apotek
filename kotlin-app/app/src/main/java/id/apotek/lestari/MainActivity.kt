package id.apotek.lestari

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
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
import id.apotek.lestari.data.PREF_AUTO_BACKUP_NAMA_TERAKHIR
import id.apotek.lestari.data.PREF_AUTO_BACKUP_TERAKHIR
import id.apotek.lestari.data.Repository
import id.apotek.lestari.data.SyncManager
import id.apotek.lestari.data.jadwalkanBackupOtomatis
import id.apotek.lestari.data.jadwalkanSyncBerkala
import id.apotek.lestari.data.local.DatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.room.InvalidationTracker
import id.apotek.lestari.ui.rememberLogoBitmap
import id.apotek.lestari.ui.screens.*
import id.apotek.lestari.ui.theme.ApotikLestariTheme
import id.apotek.lestari.ui.theme.AppLang
import id.apotek.lestari.ui.theme.ForestMint
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
            // Ikon bar sistem: status bar SELALU putih karena strip
            // navy solid di atas layar (lihat strip di Box akar
            // ApotekApp); bar navigasi tetap mengikuti tema aplikasi.
            // SideEffect jalan tiap komposisi ulang, jadi ikut berubah
            // saat saklar tema diketuk. (Sebelumnya ikon status mengikuti
            // isDark — tidak cukup: di mode terang jam/baterai tetap
            // putih dan nyaris tak terbaca di latar #F8F9FF, laporan
            // Achmad 2026-11-16 + screenshot 01:15 WIB.)
            val activity = LocalContext.current as Activity
            SideEffect {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = !isDark
                }
            }
            // Bahasa aplikasi (id/en) — tersimpan seperti tema, dan disalin
            // ke AppLang.code yang dibaca tr() di semua layar.
            var appLang by remember { mutableStateOf(prefs.getString("app_language", "id") ?: "id") }
            AppLang.code = appLang
            // BM2 Memudar (2026-11-18, pilihan Achmad): ganti mode/bahasa
            // tidak lagi instan — layar lama ditangkap jadi bitmap SEBELUM
            // state berubah, lalu bitmap itu memudar 300ms di atas layar
            // baru (anak terakhir Box akar, di bawah). Satu mekanisme
            // untuk mode dan bahasa. Bila tangkapan gagal, ganti seketika.
            val rootView = LocalView.current
            var gambarLama by remember { mutableStateOf<ImageBitmap?>(null) }
            val alphaLama by animateFloatAsState(
                targetValue = if (gambarLama == null) 1f else 0f,
                animationSpec = tween(300), label = "memudar tema/bahasa"
            )
            LaunchedEffect(gambarLama) {
                if (gambarLama != null) {
                    delay(300)
                    gambarLama = null
                }
            }
            fun tangkapLalu(aksi: () -> Unit) {
                if (gambarLama != null) return // sedang memudar — abaikan
                gambarLama = runCatching { rootView.drawToBitmap().asImageBitmap() }.getOrNull()
                aksi()
            }
            // Kelas lebar jendela (HP/tablet, portrait/landscape) untuk layout adaptif.
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowWidthClass provides windowSizeClass.widthSizeClass) {
                ApotikLestariTheme(darkTheme = isDark) {
                    Box(Modifier.fillMaxSize()) {
                        ApotekApp(
                            isDark = isDark,
                            onToggleTheme = {
                                tangkapLalu {
                                    val next = !isDark
                                    isDark = next
                                    prefs.edit().putBoolean("dark_theme", next).apply()
                                }
                            },
                            appLang = appLang,
                            onLanguageChange = { next ->
                                tangkapLalu {
                                    appLang = next
                                    prefs.edit().putString("app_language", next).apply()
                                }
                            }
                        )
                        // Bitmap layar lama di atas segalanya, memudar 300ms.
                        gambarLama?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize().alpha(alphaLama)
                            )
                        }
                    }
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
    // Animasi ganti tema/bahasa (BM2, pilihan Achmad 2026-11-18)
    // tinggal di setContent: layar lama ditangkap jadi bitmap lalu
    // memudar 300ms di atas layar baru. (AN3 lingkaran tema dulu di
    // sini — dihapus 2026-11-16.)
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
            // R13 (audit 2026-11-15): jangan BERHASIL sebelum sinkron
            // pertama sesi ini. PROSES pun tidak bisa dipakai sebagai
            // nilai awal — guard jalankanSync menolak jalan bila status
            // sudah PROSES, jadi sinkron pertama tidak akan pernah
            // berjalan. BELUM_MASUK netral sampai hasil pertama tiba
            // (efek startup langsung memanggil jalankanSync).
            when {
                !onlineSiap -> StatusSync.NONAKTIF
                else -> StatusSync.BELUM_MASUK
            }
        )
    }
    var syncTerakhir by remember { mutableLongStateOf(onlinePrefs.getLong("online_sync_terakhir", 0L)) }
    var sibukOnline by remember { mutableStateOf(false) }
    // True hanya bila masuk Google-nya terjadi di sesi proses ini
    // (lewat gerbang), bukan sesi lama yang tersimpan saat app
    // dibuka - membedakan kabar sinkron saat buka app (pola yang
    // sama seperti Kasir PlayStation).
    var baruSajaMasuk by remember { mutableStateOf(false) }
    // Lencana "Tersinkron" di atas-tengah (desain S12 warna N2):
    // pengganti snackbar untuk kabar sinkron BERHASIL. Galat tetap
    // lewat snackbar supaya pesannya terbaca utuh. Catatan: geser+
    // memudar lencana ini satu animasi kecil di luar lima animasi
    // yang dipulihkan - diminta Achmad eksplisit 2026-11-11
    // ("implement kaya gini" mengikuti Kasir).
    var tampilLencanaSync by remember { mutableStateOf(false) }
    // R12 (audit 2026-11-15): nonce naik tiap kali lencana ditampilkan
    // supaya timer 2,6 detik restart walau lencana sudah tampil saat
    // sinkron kedua berhasil (kunci Boolean saja tidak me-rekey efek).
    var lencanaSyncKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(lencanaSyncKey) {
        if (tampilLencanaSync) {
            delay(2600)
            tampilLencanaSync = false
        }
    }
    var pesanOnline by remember { mutableStateOf<String?>(null) }
    var pesanStatus by remember { mutableStateOf<String?>(null) }
    var tampilInfoOnline by remember { mutableStateOf(false) }
    var tampilKonfirmasiKeluar by remember { mutableStateOf(false) }
    var tampilKonfirmasiReset by remember { mutableStateOf(false) }
    var sibukReset by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val perluLogin = onlineSiap && emailOnline == null && !lewatiLogin

    // senyap = sinkron latar (otomatis): tanpa kabar apa pun -
    // laporan Achmad 2026-11-10 bahwa notifikasinya muncul terus
    // setiap sinkron otomatis berjalan. Status tetap terlihat dari
    // ikon awan di Beranda. Sinkron manual & saat masuk yang
    // BERHASIL berkabar lewat lencana "Tersinkron" di atas-tengah;
    // yang GAGAL tetap lewat snackbar.
    fun jalankanSync(senyap: Boolean = false) {
        if (!onlineSiap || emailOnline == null || statusSync == StatusSync.PROSES) return
        statusSync = StatusSync.PROSES
        appScope.launch {
            when (val h = SyncManager(repoOnline).sinkronSekarang(context)) {
                is HasilSync.Berhasil -> {
                    statusSync = StatusSync.BERHASIL
                    syncTerakhir = System.currentTimeMillis()
                    onlinePrefs.edit().putLong("online_sync_terakhir", syncTerakhir).apply()
                    // R12: naikkan nonce supaya timer lencana restart.
                    if (!senyap) { tampilLencanaSync = true; lencanaSyncKey++ }
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
                    .remove(PREF_AUTO_BACKUP_TERAKHIR)
                    .remove(PREF_AUTO_BACKUP_NAMA_TERAKHIR)
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
                baruSajaMasuk = true
                lewatiLogin = false
            } catch (e: Exception) {
                pesanOnline = e.message ?: tr("Gagal masuk dengan Google", "Google sign-in failed")
            } finally {
                sibukOnline = false
            }
        }
    }

    LaunchedEffect(emailOnline) {
        // Backup otomatis (G6): tegaskan jadwal tiap app dibuka, terlepas
        // dari status masuk — cadangan lokal tidak butuh akun.
        jadwalkanBackupOtomatis(context)
        if (emailOnline != null) {
            // Konsolidasi ganti akun: bila akun yang masuk berbeda dari
            // akun sinkron terakhir (atau belum pernah tercatat, mis.
            // setelah pembaruan dari versi sebelum fitur ini), naikkan
            // seluruh isi lokal ke akun ini supaya data awan tidak
            // terbelah mengikuti akun. Penanda disimpan lebih dulu;
            // baris yang gagal naik tetap kotor dan ikut putaran
            // sinkron berikutnya.
            val emailSebelumnya = onlinePrefs.getString("online_email_terakhir", null)
            if (emailSebelumnya != emailOnline) {
                repoOnline.tandaiSemuaKotor()
                onlinePrefs.edit().putString("online_email_terakhir", emailOnline).apply()
            }
            jadwalkanSyncBerkala(context)
            // Kabar sinkron hanya untuk masuk yang benar-benar baru
            // (lewat gerbang) dan ketukan manual. Buka app dengan
            // sesi tersimpan = sinkron senyap, supaya kabar itu
            // tidak muncul di setiap app dibuka (permintaan Achmad
            // 2026-11-11, pola yang sama seperti Kasir).
            val bersuara = baruSajaMasuk
            baruSajaMasuk = false
            jalankanSync(senyap = !bersuara)
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
                // S14 (audit 2026-11-15): "suppliers" ikut diamati supaya
                // tambah/ubah/hapus pemasok juga memicu auto-sync ±5 detik.
                "medicines", "sales", "sale_items", "cash_entries", "purchases", "purchase_items", "suppliers"
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
    // R24 (audit 2026-11-15): query pencarian awal Stok saat dibuka dari
    // Pengingat — nonce membuat ketukan pada obat yang sama dua kali tetap
    // diterapkan (kunci LaunchedEffect di StokScreen harus berubah).
    var stokCariAwal by remember { mutableStateOf("") }
    var stokCariNonce by remember { mutableIntStateOf(0) }
    // Sub-menu yang ditumpuk di atas tab (dari Beranda/Lainnya) — dipakai
    // logika Kembali di goToTab dan penyama durasi transisi NavHost.
    val subRoutes = listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang", "pbf", "pemasok", "tutupBuku")
    fun tabPosition(route: String): Int = when (route) {
        "beranda" -> 0
        "stok" -> 1
        "kasir" -> 2
        "keuangan" -> 3
        else -> 4 // Lainnya + sub-menunya
    }
    fun goToTab(route: String) {
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
    // (Swipe pindah tab DIHAPUS 2026-11-15 atas permintaan Achmad — pindah
    // tab hanya lewat ketukan bar bawah. density di bawah tetap dipakai
    // transisi S3 (geserSumbuX).)
    val density = LocalDensity.current
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
            if (current != "tentang") {
                ShortNavigationBar(
                    arrangement = ShortNavigationBarArrangement.EqualWeight,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    TABS.forEach { tab ->
                        val selected = when (tab.route) {
                            "beranda" -> current == "beranda"
                            "stok" -> current == "stok"
                            "kasir" -> current == "kasir"
                            "keuangan" -> current == "keuangan"
                            else -> current == "lainnya" || current in listOf("laba", "pengingat", "riwayat", "backup", "terlaris", "tentang", "pbf", "pemasok", "tutupBuku")
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
        }
    ) { padding ->
        // Lebar konten dibatasi ~840.dp dan dipusatkan di layar lebar (tablet/landscape),
        // supaya daftar tidak merentang dari ujung ke ujung. Navigasi bawah tetap selebar layar.
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // Transisi sub-halaman S3 SUMBU-X MATERIAL (pilihan Achmad
            // 2026-11-12 dari demo S1-S6): kedelapan sub-halaman dari
            // Lainnya (laba, pengingat, riwayat, backup, terlaris, pbf,
            // tentang, tutupBuku) memakai pola shared axis resmi Material — kedua
            // layar bergeser hanya 30dp berlawanan arah + memudar,
            // 300ms FastOutSlowInEasing. Maju: halaman baru masuk dari
            // +30dp, Lainnya keluar ke -30dp. Mundur (tombol Kembali /
            // bar bawah): kebalikannya — sub-halaman keluar ke +30dp,
            // Lainnya masuk dari -30dp. Transisi antar tab utama kini
            // TB1 (pilihan Achmad 2026-11-18): pola S3 yang sama, arahnya
            // mengikuti urutan tab (dihitung dari rute awal/tujuan).
            val geserSumbuX = with(density) { 30.dp.toPx().toInt() }
            val subEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { geserSumbuX } + fadeIn(tween(300)) }
            val subExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -geserSumbuX } + fadeOut(tween(300)) }
            val subPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -geserSumbuX } + fadeIn(tween(300)) }
            val subPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { geserSumbuX } + fadeOut(tween(300)) }
            // TB1 (pilihan Achmad 2026-11-18): transisi antar tab = S3 yang
            // sama dengan sub-halaman (geser 30dp + memudar, 300ms) — arah
            // mengikuti urutan tab, dihitung dari rute awal/tujuan di dalam
            // lambda (bukan navDirection: nilainya basi saat tombol Kembali
            // sistem ditekan, jadi arahnya bisa terbalik). Dipakai default
            // NavHost + cabang kondisional saat layar lawan adalah tab
            // utama. Masuk: dari +30dp bila tujuan di kanan, dari -30dp
            // bila di kiri; keluar kebalikannya.
            val tabMasuk: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { if (tabPosition(targetState.destination.route ?: "") >= tabPosition(initialState.destination.route ?: "")) geserSumbuX else -geserSumbuX } + fadeIn(tween(300)) }
            val tabKeluar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { if (tabPosition(targetState.destination.route ?: "") >= tabPosition(initialState.destination.route ?: "")) -geserSumbuX else geserSumbuX } + fadeOut(tween(300)) }
            // Transisi kondisional (perbaikan 2026-11-15, TB1 2026-11-18):
            // S3 300ms hanya bila layar lawan adalah Lainnya/sub-halaman;
            // bila lawan tab utama, pakai TB1 300ms (arah dari rute).
            // Tanpa ini, satu sisi selesai 150ms sementara sisi lain masih
            // bergeser sampai 300ms — animasi buka/tutup terasa patah,
            // terutama Kembali dari sub-halaman ke Lainnya.
            val diSubAtauLainnya = { route: String? -> route == "lainnya" || route in subRoutes }
            val subMasuk: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { if (diSubAtauLainnya(initialState.destination.route)) subEnter() else tabMasuk() }
            val subKeluar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { if (diSubAtauLainnya(targetState.destination.route)) subExit() else tabKeluar() }
            val subPopMasuk: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { if (diSubAtauLainnya(initialState.destination.route)) subPopEnter() else tabMasuk() }
            val subPopKeluar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { if (diSubAtauLainnya(targetState.destination.route)) subPopExit() else tabKeluar() }
            val lainnyaMasuk: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { if (initialState.destination.route in subRoutes) subPopEnter() else tabMasuk() }
            val lainnyaKeluar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { if (targetState.destination.route in subRoutes) subExit() else tabKeluar() }
            val lainnyaPopMasuk: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { if (initialState.destination.route in subRoutes) subPopEnter() else tabMasuk() }
            val lainnyaPopKeluar: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { if (targetState.destination.route in subRoutes) subPopExit() else tabKeluar() }
            NavHost(
                navController = nav,
                startDestination = "beranda",
                // Transisi antar tab = TB1 (pilihan Achmad 2026-11-18):
                // pola S3 yang sama dengan sub-halaman — geser 30dp +
                // memudar, 300ms, arah mengikuti urutan tab.
                enterTransition = tabMasuk,
                exitTransition = tabKeluar,
                popEnterTransition = tabMasuk,
                popExitTransition = tabKeluar,
                modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth()
            ) {
                composable("beranda") { BerandaScreen(onGoStok = { goToTab("stok") }, onGoPengingat = { goToTab("pengingat") }, onGoLaba = { goToTab("laba") }, onGoRiwayat = { goToTab("riwayat") }, statusSync = statusSync, onSyncTap = { aksiSinkronTap() }) }
                composable("kasir") { KasirScreen() }
                composable("stok") { StokScreen(cariAwal = stokCariAwal, cariNonce = stokCariNonce) }
                composable("keuangan") { KeuanganScreen() }
                // Petak Lainnya memakai navigate() biasa (tumpuk), BUKAN goToTab:
                // goToTab me-popUpTo("beranda") sehingga halaman Lainnya ikut
                // terbuang dari tumpukan dan tombol Kembali dari sub-menu
                // mendarat di Beranda, bukan di Lainnya (laporan Achmad
                // 2026-11-04). Dengan tumpukan biasa, Kembali dari
                // Laba/Pengingat/dll. kembali ke halaman Lainnya.
                composable("lainnya", enterTransition = lainnyaMasuk, exitTransition = lainnyaKeluar, popEnterTransition = lainnyaPopMasuk, popExitTransition = lainnyaPopKeluar) { LainnyaScreen(onGo = { route -> nav.navigate(route) { launchSingleTop = true } }, isDark = isDark, onToggleTheme = onToggleTheme, appLang = appLang, onLanguageChange = onLanguageChange, onlineSiap = onlineSiap, emailOnline = emailOnline, statusSync = statusSync, syncTerakhir = syncTerakhir, onSinkronTap = { aksiSinkronTap() }, onKeluarTap = { tampilKonfirmasiKeluar = true }, onResetTap = { tampilKonfirmasiReset = true }) }
                composable("laba", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { LabaScreen() }
                composable("pengingat", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { PengingatScreen(onGoStok = { nama -> stokCariAwal = nama; stokCariNonce++; goToTab("stok") }, onGoPbf = { goToTab("pbf") }) }
                composable("riwayat", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { RiwayatScreen() }
                composable("backup", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { BackupScreen() }
                composable("terlaris", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { ObatTerlarisScreen() }
                composable("pbf", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { PbfScreen() }
                composable("pemasok", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { PemasokScreen() }
                composable("tentang", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { TentangScreen(onBack = { nav.popBackStack() }) }
                composable("tutupBuku", enterTransition = subMasuk, exitTransition = subKeluar, popEnterTransition = subPopMasuk, popExitTransition = subPopKeluar) { TutupBukuScreen(onMintaSync = { jalankanSync() }) }
            }
        }
    }
    // Strip status bar solid (perbaikan 2026-11-16, laporan Achmad
    // "tembus sampai status bar" + "tidak sampai status bar"): strip
    // navy WarnaHeaderN2 setinggi status bar, digambar sendiri di atas
    // layar. Android 15+ memaksa edge-to-edge (targetSdk 36) sehingga
    // sistem tidak lagi menggambar latar status bar — strip ini yang
    // memberi batas tegas di kedua mode. Konten Scaffold sudah dipadding
    // di bawah status bar, jadi strip tidak menutupi apa pun.
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(WarnaHeaderN2)
    )
    // Lencana sinkron S12: pil WarnaHeaderN2 melayang di atas-tengah,
    // awan CloudDone cyan #40C4FF + teks "Tersinkron". Muncul 2,6 detik
    // lalu hilang sendiri. PENEMPATAN (perbaikan 2026-11-12): lencana
    // HARUS jadi anak langsung Box layar penuh ini — waktu masih di
    // dalam lambda konten Scaffold, .align(TopCenter) tidak berlaku
    // (Scaffold menaruhnya di kiri atas) meski kodenya terkompilasi.
    AnimatedVisibility(
        visible = tampilLencanaSync,
        enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(250)) { -it } + fadeOut(tween(250)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = WarnaHeaderN2,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = ForestMint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("Tersinkron", "Synced"),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
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

// Baris menu berkelompok gaya KasBulanan untuk halaman Lainnya:
// kotak ikon membulat + judul + subjudul + chevron (atau isi trailing lain).
// N1 KOTAK SENADA 12DP (pilihan Achmad 2026-11-16): kotak ikon
// primaryContainer + ikon onPrimaryContainer; bila warnaIkon diisi
// (Keluar Akun = error) kotaknya errorContainer + onErrorContainer.
@Composable
private fun BarisGrupLainnya(
    ikon: androidx.compose.ui.graphics.vector.ImageVector,
    judul: String,
    sub: String?,
    warnaIkon: Color? = null,
    warnaKotak: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LM5: warnaKotak menimpa kotak ikon (mis. hijau/kuning
        // status akun) dengan ikon putih; warnaIkon = errorContainer.
        val kotakIkon = warnaKotak ?: if (warnaIkon != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        val tintIkon = if (warnaKotak != null) Color.White else if (warnaIkon != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        Surface(color = kotakIkon, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
            // A4 (animasi Lainnya, 2026-11-15): ikon crossfade saat berganti
            // (tema bulan<->matahari) — fade 250ms, tanpa geser/skala.
            AnimatedContent(
                targetState = ikon,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "ikonBarisLainnya"
            ) { ikonAnim ->
                Icon(ikonAnim, null, tint = tintIkon, modifier = Modifier.padding(9.dp).fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(judul, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            // A4: subjudul crossfade saat berganti (tema Aktif<->Nonaktif,
            // bahasa English<->Bahasa Indonesia, status sinkronisasi).
            AnimatedContent(
                targetState = sub,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "subBarisLainnya"
            ) { subAnim ->
                if (subAnim != null) {
                    Text(subAnim, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        trailing()
    }
}

@Composable
private fun PemisahGrup() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 68.dp, end = 16.dp)
    )
}

// Halaman "Lainnya" — gaya L1 persis KasBulanan (pilihan Achmad
// 2026-11-10, menggantikan petak besar A2): identitas tengah (cakram
// logo + nama + pil versi), kartu status Mode Online, satu kartu grup
// berisi enam menu tujuan sebagai baris, satu kartu grup pengaturan
// (tema sebagai saklar, bahasa, sinkronisasi, keluar akun), kartu
// Tentang, lalu Reset Semua Data paling bawah.
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
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {
        KartuHeaderPremium(tr("Lainnya", "More"), Icons.Default.MoreHoriz)
        Spacer(Modifier.height(SpaceM))
        // Status Mode Online/Offline (kartu info)
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
        val kotakStatus = if (emailOnline != null) Color(0xFF15803D) else Color(0xFFD97706)
        val tiles = listOf(
            LainnyaTile("laba", tr("Laba", "Profit"), tr("Laba & margin", "Profit & margins"), Icons.Default.TrendingUp),
            LainnyaTile("terlaris", tr("Terlaris", "Best Sellers"), tr("Peringkat penjualan", "Sales ranking"), Icons.Default.Leaderboard),
            LainnyaTile("pengingat", tr("Pengingat", "Reminders"), tr("Stok & tagihan", "Stock & bills"), Icons.Default.NotificationsActive),
            LainnyaTile("riwayat", tr("Riwayat", "History"), tr("Semua penjualan", "All sales"), Icons.Default.ReceiptLong),
            LainnyaTile("tutupBuku", tr("Tutup Buku", "Close Books"), tr("Kunci bulan lalu", "Lock past months"), Icons.Default.Lock),
            LainnyaTile("pbf", tr("Faktur PBF", "PBF Invoices"), tr("Pembelian distributor", "Distributor purchases"), Icons.Default.LocalShipping),
            LainnyaTile("pemasok", tr("Pemasok", "Suppliers"), tr("Daftar PBF", "PBF list"), Icons.Default.Store),
            LainnyaTile("backup", tr("Backup", "Backup"), tr("Cadangkan data", "Back up data"), Icons.Default.Backup)
        )
        Text(
            tr("MENU", "MENU"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        ExpressiveCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 6.dp)) {
                tiles.forEachIndexed { idx, tile ->
                    if (idx > 0) PemisahGrup()
                    BarisGrupLainnya(tile.icon, tile.label, tile.sub, onClick = { onGo(tile.route) })
                }
            }
        }
        Spacer(Modifier.height(SpaceM))
        Text(
            tr("AKUN", "ACCOUNT"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        ExpressiveCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 6.dp)) {
                BarisGrupLainnya(
                    if (emailOnline != null) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    if (emailOnline != null) tr("Mode Online", "Online Mode") else tr("Mode Offline", "Offline Mode"),
                    subStatus,
                    warnaKotak = kotakStatus,
                    trailing = { }
                )
                PemisahGrup()
                BarisGrupLainnya(
                    Icons.Default.CloudSync,
                    tr("Sinkronisasi", "Sync"),
                    when {
                        statusSync == StatusSync.PROSES -> tr("Sedang menyinkron\u2026", "Syncing\u2026")
                        emailOnline != null -> tr("Ketuk untuk sinkron sekarang", "Tap to sync now")
                        onlineSiap -> tr("Masuk untuk mengaktifkan cadangan awan", "Sign in to enable cloud backup")
                        else -> tr("Belum dikonfigurasi", "Not configured")
                    },
                    onClick = { onSinkronTap() },
                    trailing = {
                        if (statusSync == StatusSync.PROSES) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
                if (emailOnline != null) {
                    PemisahGrup()
                    BarisGrupLainnya(
                        Icons.Default.Logout,
                        tr("Keluar Akun", "Sign Out"),
                        emailOnline,
                        warnaIkon = MaterialTheme.colorScheme.error,
                        onClick = { onKeluarTap() }
                    )
                }
            }
        }
        Spacer(Modifier.height(SpaceM))
        Text(
            tr("PENGATURAN", "SETTINGS"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        ExpressiveCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 6.dp)) {
                BarisGrupLainnya(
                    if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    tr("Mode Gelap", "Dark Mode"),
                    if (isDark) tr("Aktif", "On") else tr("Nonaktif", "Off"),
                    trailing = { Switch(checked = isDark, onCheckedChange = { onToggleTheme() }) }
                )
                PemisahGrup()
                BarisGrupLainnya(
                    Icons.Default.Language,
                    tr("Bahasa", "Language"),
                    if (appLang == "en") "English" else "Bahasa Indonesia",
                    onClick = { onLanguageChange(if (appLang == "en") "id" else "en") }
                )
            }
        }
        Spacer(Modifier.height(SpaceM))
        Text(
            tr("TENTANG", "ABOUT"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        ExpressiveCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(vertical = 6.dp)) {
                BarisGrupLainnya(
                    Icons.Default.Info,
                    tr("Tentang Aplikasi", "About App"),
                    tr("Versi ${BuildConfig.VERSION_NAME} • Info aplikasi", "Version ${BuildConfig.VERSION_NAME} • App info"),
                    onClick = { onGo("tentang") }
                )
            }
        }
        Spacer(Modifier.height(SpaceM))
        // Zona berbahaya paling bawah: Reset Semua Data (opsi B).
        ListItem(
            headlineContent = { Text(tr("Reset Semua Data", "Reset All Data"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text(tr("Hapus data di HP ini dan di awan, mulai dari nol", "Delete data on this phone and in the cloud, start from zero")) },
            leadingContent = {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
                    Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(10.dp).size(22.dp))
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { onResetTap() }
        )
    }
}

// ---- Gerbang login Mode Online: desain M2 GELAP PREMIUM (pilihan
// Achmad 2026-11-10, menggantikan G2): latar nyaris hitam yang tetap
// sama di kedua mode tema (seperti pita K3 yang selalu navy), logo di
// dalam lingkaran putih bercincin cyan #40C4FF, garis aksen cyan, nama
// ditumpuk APOTEK / LESTARI, tombol Google putih, Lewati berwarna cyan.
// Sejak 2026-11-10 login HANYA Google (keputusan Achmad): formulir
// email/kata sandi dihapus - akunnya memang akun Google, dan jalur
// email hanya membuka risiko akun terbelah. Hanya tampil bila build
// terkonfigurasi Firebase dan belum ada yang masuk.
@OptIn(ExperimentalMaterial3Api::class)
// LOGIN GATE L6 GELAP PREMIUM V2 (pilihan Achmad 2026-11-13 dari board
// L1-L6, menggantikan M2): logo di ubin putih sudut membulat, "LESTARI"
// cyan, aksen cyan di bawah nama, tombol Lewati jadi pil berbingkai cyan.
// Tetap gelap premium di kedua tema; perilaku + string tr() tidak berubah.
// L8 MINIMAL RATA (pilihan Achmad 2026-11-16, dari board L7-L9):
// subjudul "Kasir Apotek" dihapus, jeda ke kalimat masuk 48dp -> 32dp,
// pemisah "atau" antara tombol Google dan Lewati, Lewati selebar tombol
// Google. Logo ASLI sudah dipakai sejak awal (rememberLogoBitmap di
// LogoData.kt, JPG 512px) — bukan ikon generik.
@Composable
fun LayarLogin(
    googleSiap: Boolean,
    sibuk: Boolean,
    pesan: String?,
    onGoogle: () -> Unit,
    onLewati: () -> Unit
) {
    val cyan = Color(0xFF40C4FF)
    Surface(Modifier.fillMaxSize(), color = Color(0xFF070A0E)) {
        // R11 (audit 2026-11-15): Column + verticalScroll mengukur tinggi
        // tak terbatas, jadi fillMaxSize + Arrangement.Center tidak
        // memusatkan apa-apa. Box yang bisa digulir + contentAlignment
        // Center memusatkan konten pendek dan tetap bisa digulir bila
        // konten lebih tinggi dari layar.
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val logoGate = rememberLogoBitmap()
                Box(
                    Modifier
                        .size(100.dp)
                        .border(2.5.dp, cyan, RoundedCornerShape(24.dp))
                        .padding(4.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (logoGate != null) {
                        Image(logoGate, null, contentScale = ContentScale.Fit, modifier = Modifier.padding(8.dp).fillMaxSize())
                    } else {
                        Icon(Icons.Default.LocalPharmacy, null, tint = Color(0xFF0A2A5C), modifier = Modifier.padding(22.dp).fillMaxSize())
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "APOTEK",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = Color(0xFFE8EEF5)
                )
                Text(
                    "LESTARI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = cyan
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .width(48.dp)
                        .height(3.dp)
                        .background(cyan, RoundedCornerShape(2.dp))
                )
                // L8: subjudul "Kasir Apotek" dihapus (permintaan Achmad);
                // jeda ke kalimat masuk dirapatkan 48dp -> 32dp.
                Spacer(Modifier.height(32.dp))
                Text(
                    tr("Masuk untuk mencadangkan data ke akunmu", "Sign in to back up your data to your account"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFC7D2DE),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                if (googleSiap) {
                    Button(
                        onClick = onGoogle,
                        enabled = !sibuk,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F1F1F)
                        ),
                        shape = RoundedCornerShape(50),
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
                        color = Color(0xFF93A5B8),
                        textAlign = TextAlign.Center
                    )
                }
                if (pesan != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pesan,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB4AB),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(14.dp))
                // L8 MINIMAL RATA: pemisah "atau" antara tombol Google
                // dan Lewati.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A3542))
                    Text(tr("atau", "or"), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6E7C8C), modifier = Modifier.padding(horizontal = 12.dp))
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A3542))
                }
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onLewati,
                    enabled = !sibuk,
                    border = BorderStroke(1.5.dp, cyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cyan),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr("Lewati \u2014 Pakai Tanpa Akun", "Skip \u2014 Use Without an Account"), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    tr("Mode tamu: data hanya tersimpan di HP ini", "Guest mode: data is only stored on this phone"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6E7C8C)
                )
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
