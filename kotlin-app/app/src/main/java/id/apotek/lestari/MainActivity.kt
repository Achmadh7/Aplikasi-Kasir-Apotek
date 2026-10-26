package id.apotek.lestari

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import id.apotek.lestari.work.StockCheckWorker
import java.util.concurrent.TimeUnit
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
        // Pengecekan stok & kadaluarsa di background tiap 6 jam (WorkManager):
        // notifikasi muncul walau aplikasi tidak dibuka. KEEP = jadwal lama
        // dipertahankan kalau sudah ada. Waktu pastinya mengikuti Doze /
        // optimasi baterai Android, jadi bisa bergeser sedikit.
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "stock-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StockCheckWorker>(6, TimeUnit.HOURS).build()
        )
        setContent {
            // Kelas lebar jendela (HP/tablet, portrait/landscape) untuk layout adaptif.
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowWidthClass provides windowSizeClass.widthSizeClass) {
                ApotikLestariTheme { ApotekApp() }
            }
        }
    }
}

data class TabDef(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
val TABS = listOf(
    TabDef("beranda", "Beranda", Icons.Default.Home),
    TabDef("kasir", "Kasir", Icons.Default.PointOfSale),
    TabDef("stok", "Stok", Icons.Default.Medication),
    TabDef("keuangan", "Keuangan", Icons.Default.AccountBalanceWallet),
    TabDef("lainnya", "Lainnya", Icons.Default.MoreHoriz)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApotekApp() {
    // Minta izin notifikasi sekali saat aplikasi pertama dibuka (Android 13+).
    // Kalau ditolak, tidak terjadi apa-apa: pop-up stok menipis di aplikasi tetap tampil.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Hasilnya tidak mengubah apa pun. */ }
    var notificationPermissionAsked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionAsked) {
            notificationPermissionAsked = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "beranda"
    // Sheet "Lainnya": Laba, Pengingat, Riwayat — menu yang tidak lagi ada di bar bawah.
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState()
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = if (tab.route == "lainnya") current in listOf("laba", "pengingat", "riwayat") else current == tab.route,
                        onClick = { if (tab.route == "lainnya") showMoreSheet = true else nav.navigate(tab.route) { popUpTo("beranda"); launchSingleTop = true } },
                        icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(26.dp)) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { padding ->
        // Lebar konten dibatasi ~840.dp dan dipusatkan di layar lebar (tablet/landscape),
        // supaya daftar tidak merentang dari ujung ke ujung. Navigasi bawah tetap selebar layar.
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            NavHost(
                navController = nav,
                startDestination = "beranda",
                // Transisi antar tab: pudar + geser halus, bukan ganti mendadak.
                enterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(250), initialOffsetX = { it / 4 }) },
                exitTransition = { fadeOut(animationSpec = tween(250)) + slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { -it / 4 }) },
                popEnterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(250), initialOffsetX = { -it / 4 }) },
                popExitTransition = { fadeOut(animationSpec = tween(250)) + slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { it / 4 }) },
                modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth()
            ) {
                composable("beranda") { BerandaScreen(onGoStok = { nav.navigate("stok") }, onGoPengingat = { nav.navigate("pengingat") }, onGoLaba = { nav.navigate("laba") }, onGoRiwayat = { nav.navigate("riwayat") }) }
                composable("kasir") { KasirScreen() }
                composable("stok") { StokScreen() }
                composable("keuangan") { KeuanganScreen() }
                composable("laba") { LabaScreen() }
                composable("pengingat") { PengingatScreen(onGoStok = { nav.navigate("stok") }) }
                composable("riwayat") { RiwayatScreen() }
            }
        }
    }
    // Sheet "Lainnya": Laba, Pengingat, Riwayat.
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
                    Triple("pengingat", "Pengingat", Icons.Default.NotificationsActive),
                    Triple("riwayat", "Riwayat", Icons.Default.ReceiptLong)
                ).forEach { (route, label, icon) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            showMoreSheet = false
                            nav.navigate(route) { popUpTo("beranda"); launchSingleTop = true }
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
            }
        }
    }
}


