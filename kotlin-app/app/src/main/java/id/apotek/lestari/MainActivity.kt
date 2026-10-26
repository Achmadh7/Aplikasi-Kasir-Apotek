package id.apotek.lestari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.apotek.lestari.data.local.DatabaseProvider
import id.apotek.lestari.ui.screens.*
import id.apotek.lestari.ui.theme.ApotikLestariTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(applicationContext)
        setContent { ApotikLestariTheme { ApotekApp() } }
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
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "beranda"
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { nav.navigate(tab.route) { popUpTo("beranda"); launchSingleTop = true } },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
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
