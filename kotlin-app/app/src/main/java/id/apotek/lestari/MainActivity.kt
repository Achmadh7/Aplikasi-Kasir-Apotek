package id.apotek.lestari

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import id.apotek.lestari.data.local.DatabaseProvider
import id.apotek.lestari.work.StockCheckWorker
import java.util.concurrent.TimeUnit
import id.apotek.lestari.ui.screens.*
import id.apotek.lestari.ui.theme.ApotikLestariTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(applicationContext)
        scheduleStockCheck()
        setContent { ApotikLestariTheme { ApotekApp() } }
    }

    /**
     * Jadwalkan pengecekan stok menipis berkala di latar belakang
     * (tiap 6 jam, tanpa butuh internet). WorkManager menyimpan jadwal
     * ini sehingga tetap jalan walau aplikasi ditutup/dibuka lagi;
     * KEEP berarti penjadwalan ulang tidak membuat jadwal ganda.
     * Waktu persisnya diatur sistem (Doze/baterai bisa menunda).
     */
    private fun scheduleStockCheck() {
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "stock-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StockCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()
        )
    }
}

data class TabDef(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
val TABS = listOf(
    TabDef("beranda", "Beranda", Icons.Default.Home),
    TabDef("kasir", "Kasir", Icons.Default.PointOfSale),
    TabDef("stok", "Stok", Icons.Default.Medication),
    TabDef("keuangan", "Keuangan", Icons.Default.AccountBalanceWallet),
    TabDef("laba", "Laba", Icons.Default.TrendingUp),
    TabDef("pengingat", "Pengingat", Icons.Default.NotificationsActive),
    TabDef("riwayat", "Riwayat", Icons.Default.ReceiptLong)
)

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
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp
            ) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { nav.navigate(tab.route) { popUpTo("beranda"); launchSingleTop = true } },
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
        NavHost(navController = nav, startDestination = "beranda", modifier = Modifier.padding(padding)) {
            composable("beranda") { BerandaScreen(onGoKasir = { nav.navigate("kasir") }, onGoStok = { nav.navigate("stok") }, onGoPengingat = { nav.navigate("pengingat") }) }
            composable("kasir") { KasirScreen() }
            composable("stok") { StokScreen() }
            composable("keuangan") { KeuanganScreen() }
            composable("laba") { LabaScreen() }
            composable("pengingat") { PengingatScreen(onGoStok = { nav.navigate("stok") }) }
            composable("riwayat") { RiwayatScreen() }
        }
    }
}
