package id.apotek.lestari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.components.BarcodeScanner
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Date

private val repo = Repository()
val CATEGORIES = listOf("Umum","Antibiotik","Vitamin","Pereda Nyeri","Obat Batuk & Flu","Pencernaan","Kulit","Mata","Lainnya")

// Warna aksen mengikuti tampilan web app (mode gelap)
private val LightTealCard = Color(0xFF8FE3E8)
private val OnLightTeal = Color(0xFF002021)
private val TealButton = Color(0xFF00696D)
private val GreenButton = Color(0xFF2E7D32)
private val RedBadgeBg = Color(0xFFBA1A1A)
private val AmanBg = Color(0xFF004F52)
private val AmanFg = Color(0xFF7ED3D8)
private val SedangBg = Color(0xFF4A3F00)
private val SedangFg = Color(0xFFFFD97A)
private val BurukBg = Color(0xFF601410)
private val BurukFg = Color(0xFFFFB4AB)

@Composable
fun DemoBanner() {
    if (ApiClient.isDemoMode) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("MODE DEMO LOKAL — data contoh di HP saja, belum tersambung ke server. Ganti BASE_URL di app/build.gradle.kts dengan alamat server aslimu.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StockBadge(stock: Int, showCount: Boolean = true) {
    val label = stockLabel(stock)
    val bg = when (label) {
        "Aman" -> AmanBg
        "Sedang" -> SedangBg
        else -> BurukBg
    }
    val fg = when (label) {
        "Aman" -> AmanFg
        "Sedang" -> SedangFg
        else -> BurukFg
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            if (showCount) "Stok $stock • $label" else label,
            color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LegendChip(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

// ---------- Beranda ----------

@Composable
fun BerandaScreen(onGoKasir: () -> Unit, onGoStok: () -> Unit, onGoPengingat: () -> Unit) {
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    LaunchedEffect(Unit) { (repo.getDashboard() as? ApiResult.Success)?.let { dashboard = it.data } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(todayLabelID(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text("Ringkasan Apotek", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Hari Ini", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Pemasukan, pengeluaran, dan stok — semuanya dalam satu pandangan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            DemoBanner()
        }
        dashboard?.let { d ->
            item {
                // Kartu besar Pemasukan Hari Ini
                Card(colors = CardDefaults.cardColors(containerColor = LightTealCard), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("PEMASUKAN HARI INI", color = OnLightTeal.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(color = Color.White.copy(alpha = 0.25f), shape = CircleShape) {
                                Icon(Icons.Default.TrendingUp, null, tint = OnLightTeal, modifier = Modifier.padding(8.dp).size(20.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(formatIDR(d.todayIn), color = OnLightTeal, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, null, tint = OnLightTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${d.totalSalesToday} transaksi penjualan", color = OnLightTeal, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Surface(color = RedBadgeBg, shape = CircleShape) {
                                Icon(Icons.Default.ArrowUpward, null, tint = Color.White, modifier = Modifier.padding(8.dp).size(20.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("PENGELUARAN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(formatIDR(d.todayOut), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
                                Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(8.dp).size(20.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("SELISIH KAS HARI INI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(formatIDR(d.todayProfit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("SELISIH KAS BULAN INI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(formatIDR(d.monthProfit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Masuk ${formatIDR(d.monthIn)}", style = MaterialTheme.typography.bodyMedium)
                                Text("Keluar ${formatIDR(d.monthOut)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("${d.totalMedicines} jenis obat   •   ${d.lowStockCount} stok menipis   •   ${d.expiringCount} dekat kadaluarsa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(
                    onClick = onGoKasir,
                    colors = ButtonDefaults.buttonColors(containerColor = TealButton, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.PointOfSale, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mulai Transaksi Kasir", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Perlu Perhatian", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (d.lowStockCount > 0) Text("• ${d.lowStockCount} obat stok menipis", style = MaterialTheme.typography.bodyMedium)
                        if (d.expiringCount > 0) Text("• ${d.expiringCount} obat dekat kadaluarsa", style = MaterialTheme.typography.bodyMedium)
                        if (d.lowStockCount == 0 && d.expiringCount == 0) Text("Semua stok dan kadaluarsa aman.", style = MaterialTheme.typography.bodyMedium)
                        if (d.lowStockCount > 0 || d.expiringCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onGoPengingat) { Text("Lihat Pengingat") }
                        }
                    }
                }
            }
            item { Text("Obat yang Terjual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(d.bestSellers) { b ->
                ListItem(headlineContent = { Text(b.name) }, trailingContent = { Text("${b.qty} • ${formatIDR(b.revenue)}") })
            }
            item { Text("Penjualan Terakhir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(d.recentSales) { s ->
                ListItem(
                    headlineContent = { Text(s.invoiceNo) },
                    supportingContent = { Text(formatDateTimeID(s.createdAt)) },
                    trailingContent = { Text(formatIDR(s.total)) }
                )
            }
        } ?: item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

class CartItem(val medicine: Medicine, qty: Int) {
    var qty by mutableStateOf(qty)
}

// ---------- Kasir ----------

@Composable
fun KasirScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var scanMsg by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Tunai") }
    var uangDiterima by remember { mutableIntStateOf(0) }
    var payMsg by remember { mutableStateOf("") }
    val cart = remember { mutableStateListOf<CartItem>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines } }
    if (showScanner) BarcodeScanner(onDetected = { code ->
        showScanner = false
        scope.launch {
            val r = repo.findByBarcode(code)
            val med = (r as? ApiResult.Success)?.data?.medicine
            if (med != null) {
                val ex = cart.firstOrNull { it.medicine.id == med.id }
                if (ex != null) ex.qty++ else cart.add(CartItem(med, 1))
                scanMsg = "${med.name} ditambahkan"
            } else scanMsg = "Barcode $code tidak ditemukan"
        }
    }, onClose = { showScanner = false })
    val filtered = medicines.filter { search.isBlank() || it.name.contains(search, true) || it.category.contains(search, true) || (it.barcode?.contains(search) == true) }
    val total = cart.sumOf { it.medicine.sellPrice * it.qty }
    val paid = uangDiterima
    val change = if (method == "Tunai") (paid - total).coerceAtLeast(0) else 0
    val canPay = cart.isNotEmpty() && (method != "Tunai" || paid >= total)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Kasir", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Ketuk obat untuk menambahkan ke keranjang, lalu selesaikan pembayaran.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        DemoBanner()
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            label = { Text("Cari nama obat…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, "Scan barcode") } },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        if (scanMsg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(scanMsg, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { m ->
                        val out = m.stock <= 0
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).clickable(enabled = !out) {
                                val ex = cart.firstOrNull { it.medicine.id == m.id }
                                if (ex != null) ex.qty++ else cart.add(CartItem(m, 1))
                            }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = if (out) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                )
                                Text("${m.category} • ${m.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatIDR(m.sellPrice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    StockBadge(m.stock)
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Keranjang
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingCart, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Keranjang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Surface(color = LightTealCard, shape = CircleShape) {
                        Text("${cart.sumOf { it.qty }}", color = OnLightTeal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                if (cart.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.align(Alignment.CenterHorizontally).size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Keranjang kosong.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("Ketuk obat untuk menambahkan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(8.dp))
                    cart.forEach { ci ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(ci.medicine.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(formatIDR(ci.medicine.sellPrice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { if (ci.qty > 1) ci.qty-- else cart.remove(ci) }) { Icon(Icons.Default.Remove, "Kurangi") }
                            Text("${ci.qty}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                            IconButton(onClick = { if (ci.qty < ci.medicine.stock) ci.qty++ }) { Icon(Icons.Default.Add, "Tambah") }
                            Text(formatIDR(ci.medicine.sellPrice * ci.qty), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total bayar", style = MaterialTheme.typography.bodyLarge)
                    Text(formatIDR(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text("METODE BAYAR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Tunai", "QRIS", "Transfer").forEach { m ->
                        val selected = method == m
                        if (selected) {
                            Button(onClick = { method = m }, colors = ButtonDefaults.buttonColors(containerColor = TealButton, contentColor = Color.White), shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(m)
                            }
                        } else {
                            OutlinedButton(onClick = { method = m }, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) { Text(m) }
                        }
                    }
                }
                if (method == "Tunai") {
                    Spacer(Modifier.height(12.dp))
                    Text("UANG DITERIMA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(20000 to "Rp20.000", 50000 to "Rp50.000", 100000 to "Rp100.000").forEach { (nominal, label) ->
                            OutlinedButton(
                                onClick = { uangDiterima += nominal },
                                shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Ketuk nominal untuk menambah ke jumlah uang diterima — bisa ditekan berkali-kali.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = if (uangDiterima == 0) "" else uangDiterima.toString(),
                        onValueChange = { uangDiterima = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                        label = { Text("cth: 50000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (paid > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("Kembalian: ${formatIDR(change)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("Dibayar penuh via $method sebesar ${formatIDR(total)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val amt = if (method == "Tunai") paid else total
                            repo.createSale(CreateSaleArgs(cart.map { CreateSaleItemArgs(it.medicine.id, it.qty) }, method, amt))
                            payMsg = "Pembayaran $method berhasil. Total ${formatIDR(total)}" + if (method == "Tunai") " • Kembalian ${formatIDR(change)}" else ""
                            cart.clear()
                            uangDiterima = 0
                            (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines }
                        }
                    },
                    enabled = canPay,
                    colors = ButtonDefaults.buttonColors(containerColor = TealButton, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Bayar (${formatIDR(total)})", style = MaterialTheme.typography.titleMedium) }
                if (payMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(payMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- Stok Obat ----------

@Composable
fun StokScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() { scope.launch { (repo.listMedicines(search) as? ApiResult.Success)?.let { medicines = it.data.medicines } } }
    LaunchedEffect(Unit) { reload() }
    if (showScanner) BarcodeScanner(onDetected = { code ->
        showScanner = false
        scope.launch {
            val med = (repo.findByBarcode(code) as? ApiResult.Success)?.data?.medicine
            msg = if (med != null) "Ditemukan: ${med.name} — stok ${med.stock}" else "Barcode $code belum terdaftar — gunakan Tambah Obat"
            if (med == null) { editing = null; showForm = true }
        }
    }, onClose = { showScanner = false })
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stok Obat", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("${medicines.size} jenis obat tercatat", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { editing = null; showForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = LightTealCard, contentColor = OnLightTeal),
                shape = RoundedCornerShape(24.dp)
            ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Tambah Obat") }
        }
        Spacer(Modifier.height(12.dp))
        DemoBanner()
        OutlinedTextField(
            value = search, onValueChange = { search = it; reload() },
            label = { Text("Cari obat atau kategori…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, "Scan barcode stok") } },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text("Status stok:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LegendChip("Aman — di atas 20", AmanBg, AmanFg)
            LegendChip("Sedang — pas 20", SedangBg, SedangFg)
            LegendChip("Rendah — di bawah 20", BurukBg, BurukFg)
            LegendChip("Habis — 0", BurukBg, BurukFg)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(medicines) { m ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                StockBadge(m.stock, showCount = false)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${m.category} • ${m.unit} • Jual ${formatIDR(m.sellPrice)} • Beli ${formatIDR(m.buyPrice)}" +
                                    if (m.expiryDate != null) " • Exp ${formatDateID(m.expiryDate)}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${m.stock}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (m.stock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Text(m.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = m; showForm = true }) { Icon(Icons.Default.Edit, "Edit obat") }
                        IconButton(onClick = { scope.launch { repo.deleteMedicine(m.id); msg = "Obat ${m.name} dihapus."; reload() } }) {
                            Icon(Icons.Default.Delete, "Hapus obat", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (showForm) MedicineFormDialog(editing = editing, onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
}

@Composable
private fun MedicineFormDialog(editing: Medicine?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var barcode by remember(editing) { mutableStateOf(editing?.barcode ?: "") }
    var category by remember(editing) { mutableStateOf(editing?.category ?: "Umum") }
    var unit by remember(editing) { mutableStateOf(editing?.unit ?: "Strip") }
    var buy by remember(editing) { mutableStateOf(editing?.buyPrice?.toString() ?: "") }
    var sell by remember(editing) { mutableStateOf(editing?.sellPrice?.toString() ?: "") }
    var stock by remember(editing) { mutableStateOf(editing?.stock?.toString() ?: "") }
    var minStock by remember(editing) { mutableStateOf(editing?.minStock?.toString() ?: "10") }
    var expiry by remember(editing) { mutableStateOf(editing?.expiryDate ?: "") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Tambah Obat" else "Edit Obat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama Obat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode (boleh kosong)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Kategori") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, label = { Text("Satuan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(buy, { buy = it.filter { c -> c.isDigit() } }, label = { Text("Harga Beli") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sell, { sell = it.filter { c -> c.isDigit() } }, label = { Text("Harga Jual") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stock, { stock = it.filter { c -> c.isDigit() } }, label = { Text("Stok") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(minStock, { minStock = it.filter { c -> c.isDigit() } }, label = { Text("Stok Minimum") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(expiry, { expiry = it }, label = { Text("Tanggal Kadaluarsa (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val cat = category.ifBlank { "Umum" }
                        val unt = unit.ifBlank { "Strip" }
                        val b = buy.toIntOrNull() ?: 0
                        val s = sell.toIntOrNull() ?: 0
                        val st = stock.toIntOrNull() ?: 0
                        val ms = minStock.toIntOrNull() ?: 10
                        val exp = expiry.ifBlank { null }
                        val bc = barcode.ifBlank { null }
                        if (editing == null) repo.createMedicine(MedicineArgs(name, cat, unt, b, s, st, ms, exp, bc))
                        else repo.updateMedicine(UpdateMedicineArgs(editing.id, name, cat, unt, b, s, st, ms, exp, bc))
                        onSaved()
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ---------- Keuangan ----------

@Composable
fun KeuanganScreen() {
    var entries by remember { mutableStateOf<List<CashEntry>>(emptyList()) }
    var period by remember { mutableStateOf("all") } // today | month | all
    var typeFilter by remember { mutableStateOf("all") } // all | in | out
    var showForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun reload() { scope.launch { (repo.listCashEntries() as? ApiResult.Success)?.let { entries = it.data.entries } } }
    LaunchedEffect(Unit) { reload() }
    val todayKey = dayKeyID(Date())
    val monthKey = monthKeyID(Date())
    val periodEntries = entries.filter { e ->
        val d = parseISODate(e.createdAt)
        when (period) {
            "today" -> d != null && dayKeyID(d) == todayKey
            "month" -> d != null && monthKeyID(d) == monthKey
            else -> true
        }
    }
    val masuk = periodEntries.filter { it.type == "in" }.sumOf { it.amount }
    val keluar = periodEntries.filter { it.type == "out" }.sumOf { it.amount }
    val shown = periodEntries.filter { typeFilter == "all" || it.type == typeFilter }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Keuangan", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Kas masuk & keluar tercatat rinci, termasuk penjualan otomatis dari Kasir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        DemoBanner()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("today" to "Hari Ini", "month" to "Bulan Ini", "all" to "Semua").forEach { (k, l) ->
                FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SouthWest, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("MASUK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatIDR(masuk), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NorthEast, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("KELUAR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatIDR(keluar), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SALDO KAS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatIDR(masuk - keluar), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showForm = true },
            colors = ButtonDefaults.buttonColors(containerColor = TealButton, contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Catat Kas Masuk / Keluar", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Semua", "in" to "Masuk", "out" to "Keluar").forEach { (k, l) ->
                FilterChip(selected = typeFilter == k, onClick = { typeFilter = k }, label = { Text(l) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown) { e ->
                val isIn = e.type == "in"
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (isIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
                            Icon(
                                if (isIn) Icons.Default.SouthWest else Icons.Default.NorthEast, null,
                                tint = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp).size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${e.category} • ${formatDateTimeID(e.createdAt)} • ${if (e.source == "sale") "Otomatis" else "Manual"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            (if (isIn) "+ " else "- ") + formatIDR(e.amount),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
    if (showForm) CashEntryFormDialog(onDismiss = { showForm = false }, onSaved = { showForm = false; reload() })
}

@Composable
private fun CashEntryFormDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    var type by remember { mutableStateOf("in") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Kas Masuk / Keluar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("in" to "Masuk", "out" to "Keluar").forEach { (k, l) ->
                        FilterChip(selected = type == k, onClick = { type = k }, label = { Text(l) })
                    }
                }
                OutlinedTextField(category, { category = it }, label = { Text("Kategori") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Keterangan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() } }, label = { Text("Jumlah (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        repo.createCashEntry(
                            CreateCashEntryArgs(
                                type,
                                category.ifBlank { "Lainnya" },
                                description.ifBlank { if (type == "in") "Kas masuk" else "Kas keluar" },
                                amount.toIntOrNull() ?: 0
                            )
                        )
                        onSaved()
                    }
                },
                enabled = (amount.toIntOrNull() ?: 0) > 0
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

// ---------- Laporan Laba ----------

@Composable
fun LabaScreen() {
    var report by remember { mutableStateOf<ProfitReport?>(null) }
    var period by remember { mutableStateOf("month") }
    var exportMsg by remember { mutableStateOf("") }
    LaunchedEffect(period) { (repo.getProfitReport(period) as? ApiResult.Success)?.let { report = it.data } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Laporan Laba", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Laba dihitung dari penjualan dikurangi harga beli (modal) obat yang terjual.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            DemoBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("today" to "Hari Ini", "month" to "Bulan Ini", "last30" to "30 Hari", "all" to "Semua").forEach { (k, l) ->
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
        }
        report?.let { r ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LightTealCard), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("LABA KOTOR", color = OnLightTeal.copy(alpha = 0.75f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    "Margin ${marginTextID(r.marginPct)}%", color = OnLightTeal,
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(formatIDR(r.grossProfit), color = OnLightTeal, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("${r.transactionCount} transaksi • ${r.itemsSold} item terjual", color = OnLightTeal, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            LabaStat(Icons.Default.Receipt, "PENJUALAN", formatIDR(r.revenue), null, Modifier.weight(1f))
                            LabaStat(Icons.Default.Inventory, "MODAL (HPP)", formatIDR(r.hpp), null, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            LabaStat(Icons.Default.NorthEast, "BIAYA OPERASIONAL", formatIDR(r.operationalExpense), null, Modifier.weight(1f))
                            LabaStat(Icons.Default.Savings, "ESTIMASI LABA BERSIH", formatIDR(r.netProfit), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Laba kotor = penjualan − modal obat terjual (harga beli saat transaksi). Estimasi laba bersih = laba kotor − biaya operasional dari Keuangan. Kas keluar \"Beli Stok\" (${formatIDR(r.stockPurchaseExpense)}) tidak dikurangkan lagi karena modalnya sudah dihitung lewat HPP.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (r.costMissingQty > 0) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Ada ${r.costMissingQty} item terjual yang harga belinya tidak diketahui (obat sudah dihapus dari stok), jadi laba untuk item tersebut terhitung lebih besar dari seharusnya.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { exportMsg = "Export laporan Excel tersedia penuh di versi web (mode server)." },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenButton, contentColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Export Laporan ke Excel", style = MaterialTheme.typography.titleMedium) }
                if (exportMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(exportMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Laba Kotor 7 Hari Terakhir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        val daily = r.daily.takeLast(7)
                        if (daily.isEmpty()) {
                            Text("Belum ada data laba harian.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val maxProfit = daily.maxOf { it.profit }.coerceAtLeast(1)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                                daily.forEach { day ->
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.width(28.dp).height((110f * day.profit / maxProfit).dp)
                                                .background(TealButton, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(day.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Text("Per Obat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(r.byMedicine) { m -> ListItem(headlineContent = { Text(m.name) }, trailingContent = { Text(formatIDR(m.profit)) }) }
            item { Text("Per Kategori", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(r.byCategory) { c -> ListItem(headlineContent = { Text(c.category) }, trailingContent = { Text(formatIDR(c.profit)) }) }
        } ?: item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun LabaStat(icon: ImageVector, label: String, value: String, valueColor: Color?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

// ---------- Pengingat ----------

@Composable
fun PengingatScreen(onGoStok: () -> Unit) {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var window by remember { mutableStateOf(90) }
    var filter by remember { mutableStateOf("all") } // all | expiry | low | critical
    LaunchedEffect(Unit) { (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines } }
    val expired = medicines.filter { (daysUntilExpiry(it.expiryDate) ?: Int.MAX_VALUE) < 0 }
    val withinWindow = medicines.filter { val d = daysUntilExpiry(it.expiryDate); d != null && d >= 0 && d <= window }
    val low = medicines.filter { it.stock <= it.minStock }
    val critical = medicines.filter { it.stock == 0 }
    val priority = medicines.filter { m ->
        val d = daysUntilExpiry(m.expiryDate)
        m.stock <= m.minStock || (d != null && d <= window)
    }.sortedWith(
        compareBy<Medicine> { if (it.stock == 0) 0 else 1 }
            .thenBy { daysUntilExpiry(it.expiryDate) ?: Int.MAX_VALUE }
            .thenBy { it.stock }
    )
    val shown = priority.filter { m ->
        val d = daysUntilExpiry(m.expiryDate)
        when (filter) {
            "expiry" -> d != null && d <= window
            "low" -> m.stock <= m.minStock
            "critical" -> m.stock == 0
            else -> true
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Pengingat", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Kadaluwarsa & stok menipis digabung jadi satu daftar prioritas, diurutkan dari yang paling mendesak. Daftar ini otomatis mengikuti data di Stok.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            DemoBanner()
            Text("MASA KADALUARSA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90).forEach { w ->
                    FilterChip(selected = window == w, onClick = { window = w }, label = { Text("$w Hari") })
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    ReminderCount(Icons.Default.EventBusy, "EXPIRED", "${expired.size}", if (expired.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                    ReminderCount(Icons.Default.Schedule, "≤ $window HARI", "${withinWindow.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    ReminderCount(Icons.Default.Inventory, "STOK NIPIS", "${low.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    ReminderCount(Icons.Default.PriorityHigh, "KRITIS", "${critical.size}", if (critical.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("all" to "Semua Prioritas", "expiry" to "Kadaluwarsa", "low" to "Stok Menipis", "critical" to "Kritis Saja").forEach { (k, l) ->
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(l) })
                }
            }
        }
        items(shown) { m ->
            val d = daysUntilExpiry(m.expiryDate)
            val isCritical = m.stock == 0
            val suggest = (2 * m.minStock - m.stock).coerceAtLeast(0)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = if (isCritical) RedBadgeBg else MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Icon(
                                if (isCritical) Icons.Default.ShoppingCart else Icons.Default.Inventory, null,
                                tint = if (isCritical) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(10.dp).size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Surface(color = if (isCritical) RedBadgeBg else MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        if (isCritical) "KRITIS" else "PANTAU",
                                        color = if (isCritical) Color.White else MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                "${m.category} • Stok ${m.stock} ${m.unit} (min. ${m.minStock})" +
                                    if (m.expiryDate != null) {
                                        " • Exp ${formatDateID(m.expiryDate)}" + if (d != null) {
                                            if (d < 0) " • Lewat ${-d} hari" else " • Sisa $d hari"
                                        } else ""
                                    } else " • Tanpa tanggal kadaluarsa",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onGoStok, shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.Inventory, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stok")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isCritical) "Stok habis — restock segera, disarankan $suggest ${m.unit} (target 2× stok minimum)."
                            else if (m.stock <= m.minStock) "Stok menipis — disarankan restock $suggest ${m.unit} (target 2× stok minimum ${m.minStock})."
                            else if (d != null && d < 0) "Sudah kadaluarsa — jangan dijual, segera pisahkan dari stok."
                            else "Kedaluwarsa dalam ${d ?: 0} hari — prioritaskan penjualan.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item { Text("Tidak ada pengingat — semua stok dan kadaluarsa aman.", style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            Text(
                "Jumlah restock adalah saran otomatis (target stok 2× stok minimum). Untuk mengubah stok, tanggal kadaluarsa, atau stok minimum, buka obatnya di menu Stok.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReminderCount(icon: ImageVector, label: String, count: String, countColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = countColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = countColor)
    }
}

// ---------- Riwayat Penjualan ----------

@Composable
fun RiwayatScreen() {
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { (repo.listSales() as? ApiResult.Success)?.let { sales = it.data.sales } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Riwayat Penjualan", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Ketuk invoice untuk melihat detail lengkapnya.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        DemoBanner()
        if (sales.isEmpty()) Text("Belum ada penjualan tercatat.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sales) { s ->
                val isOpen = expanded == s.id
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { expanded = if (isOpen) null else s.id }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                Icon(Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.invoiceNo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatDateTimeID(s.createdAt)} • ${s.paymentMethod} • ${s.items.size} item",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(formatIDR(s.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Icon(
                                if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isOpen) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("APOTEK LESTARI — Invoice Penjualan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Lunas", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            s.items.forEach { Text("${it.medicineName} × ${it.qty} = ${formatIDR(it.subtotal)}", style = MaterialTheme.typography.bodyMedium) }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Total: ${formatIDR(s.total)} • Dibayar: ${formatIDR(s.amountPaid)} • Kembalian: ${formatIDR(s.changeAmount)}",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
