package id.apotek.lestari.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.components.BarcodeScanner
import id.apotek.lestari.ui.theme.formatIDR
import id.apotek.lestari.ui.theme.stockLabel
import kotlinx.coroutines.launch

private val repo = Repository()
val CATEGORIES = listOf("Umum","Antibiotik","Vitamin","Pereda Nyeri","Obat Batuk & Flu","Pencernaan","Kulit","Mata","Lainnya")

@Composable
fun DemoBanner() {
    if (ApiClient.isDemoMode) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("MODE DEMO LOKAL — data contoh di HP saja, belum tersambung ke server. Ganti BASE_URL di app/build.gradle.kts dengan alamat server aslimu.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun BerandaScreen(onGoKasir: () -> Unit, onGoStok: () -> Unit, onGoPengingat: () -> Unit) {
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    LaunchedEffect(Unit) { (repo.getDashboard() as? ApiResult.Success)?.let { dashboard = it.data } }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Ringkasan Penjualan Hari Ini", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); DemoBanner() }
        dashboard?.let { d ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text("Pemasukan Hari Ini: ${formatIDR(d.todayIn)} (${d.totalSalesToday} transaksi)")
                    Text("Pengeluaran Hari Ini: ${formatIDR(d.todayOut)}")
                    Text("Selisih Kas: ${formatIDR(d.todayProfit)}", style = MaterialTheme.typography.titleMedium)
                    Text("Bulan ini: ${formatIDR(d.monthProfit)} • ${d.totalMedicines} jenis obat")
                    if (d.lowStockCount > 0 || d.expiringCount > 0) Text("Perlu Perhatian: ${d.lowStockCount} stok menipis • ${d.expiringCount} dekat kadaluarsa", color = MaterialTheme.colorScheme.error)
                } }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onGoKasir) { Icon(Icons.Default.PointOfSale, null); Spacer(Modifier.width(6.dp)); Text("Mulai Kasir") }
                    OutlinedButton(onClick = onGoStok) { Text("Stok") }
                    OutlinedButton(onClick = onGoPengingat) { Text("Pengingat") }
                }
                Spacer(Modifier.height(16.dp)); Text("Obat yang Terjual", style = MaterialTheme.typography.titleMedium)
            }
            items(d.bestSellers) { b -> ListItem(headlineContent = { Text(b.name) }, trailingContent = { Text("${b.qty} • ${formatIDR(b.revenue)}") }) }
            item { Spacer(Modifier.height(8.dp)); Text("Penjualan Terakhir", style = MaterialTheme.typography.titleMedium) }
            items(d.recentSales) { s -> ListItem(headlineContent = { Text(s.invoiceNo) }, trailingContent = { Text(formatIDR(s.total)) }) }
        } ?: item { CircularProgressIndicator() }
    }
}

data class CartItem(val medicine: Medicine, var qty: Int)

@Composable
fun KasirScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var scanMsg by remember { mutableStateOf("") }
    val cart = remember { mutableStateListOf<CartItem>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines } }
    if (showScanner) BarcodeScanner(onDetected = { code -> showScanner = false; scope.launch { val r = repo.findByBarcode(code); val med = (r as? ApiResult.Success)?.data?.medicine; if (med != null) { val ex = cart.firstOrNull { it.medicine.id == med.id }; if (ex != null) ex.qty++ else cart.add(CartItem(med, 1)); scanMsg = "${med.name} ditambahkan" } else scanMsg = "Barcode $code tidak ditemukan" } }, onClose = { showScanner = false })
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Kasir", style = MaterialTheme.typography.headlineSmall); DemoBanner()
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari obat, kategori, atau barcode…") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, "Scan barcode") } })
        if (scanMsg.isNotEmpty()) Text(scanMsg, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f)) {
            items(medicines.filter { search.isBlank() || it.name.contains(search, true) || (it.barcode?.contains(search) == true) }) { m ->
                ListItem(headlineContent = { Text(m.name) }, supportingContent = { Text("${formatIDR(m.sellPrice)} • Stok ${m.stock} (${stockLabel(m.stock)})") }, trailingContent = { IconButton(onClick = { val ex = cart.firstOrNull { it.medicine.id == m.id }; if (ex != null) ex.qty++ else cart.add(CartItem(m, 1)) }) { Icon(Icons.Default.AddCircle, "Tambah ke keranjang") } })
            }
        }
        val total = cart.sumOf { it.medicine.sellPrice * it.qty }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            cart.forEach { ci -> Text("${ci.medicine.name} × ${ci.qty} = ${formatIDR(ci.medicine.sellPrice * ci.qty)}") }
            Text("Total: ${formatIDR(total)}", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { scope.launch { repo.createSale(CreateSaleArgs(cart.map { CreateSaleItemArgs(it.medicine.id, it.qty) }, "Tunai", total)); cart.clear() } }, enabled = cart.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Bayar (Tunai)") }
            Text("Metode lain (QRIS/Transfer/Kartu) ada di versi web; di Kotlin ini alur Tunai + kembalian ditampilkan setelah bayar.", style = MaterialTheme.typography.bodySmall)
        } }
    }
}

@Composable
fun StokScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() { scope.launch { (repo.listMedicines(search) as? ApiResult.Success)?.let { medicines = it.data.medicines } } }
    LaunchedEffect(Unit) { reload() }
    if (showScanner) BarcodeScanner(onDetected = { code -> showScanner = false; scope.launch { val med = (repo.findByBarcode(code) as? ApiResult.Success)?.data?.medicine; msg = if (med != null) "Ditemukan: ${med.name} — stok ${med.stock}" else "Barcode $code belum terdaftar — gunakan Tambah Obat"; if (med == null) showForm = true } }, onClose = { showScanner = false })
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row { Text("Stok Obat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)); Button(onClick = { showForm = true }) { Text("Tambah Obat") } }
        Text("${medicines.size} jenis obat tercatat", style = MaterialTheme.typography.bodySmall); DemoBanner()
        OutlinedTextField(value = search, onValueChange = { search = it; reload() }, label = { Text("Cari obat, kategori, atau barcode…") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, "Scan barcode stok") } })
        if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
        Text("Import & Export Excel tersedia penuh di versi web (mode server). Di Kotlin, tambah/edit obat lewat form di bawah.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f)) { items(medicines) { m -> ListItem(headlineContent = { Text(m.name) }, supportingContent = { Text("${m.category} • ${formatIDR(m.sellPrice)} • Exp ${m.expiryDate ?: "-"} • Barcode ${m.barcode ?: "-"}") }, trailingContent = { AssistChip(onClick = {}, label = { Text("${m.stock} • ${stockLabel(m.stock)}") }) }) } }
    }
    if (showForm) {
        var name by remember { mutableStateOf("") }; var barcode by remember { mutableStateOf("") }; var sell by remember { mutableStateOf("") }; var buy by remember { mutableStateOf("") }; var stock by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showForm = false }, title = { Text("Tambah Obat") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nama Obat") }); OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode (boleh kosong)") }); OutlinedTextField(buy, { buy = it }, label = { Text("Harga Beli") }); OutlinedTextField(sell, { sell = it }, label = { Text("Harga Jual") }); OutlinedTextField(stock, { stock = it }, label = { Text("Stok") }) } }, confirmButton = { Button(onClick = { scope.launch { repo.createMedicine(MedicineArgs(name, "Umum", "Strip", buy.toIntOrNull() ?: 0, sell.toIntOrNull() ?: 0, stock.toIntOrNull() ?: 0, 10, null, barcode.ifBlank { null })); showForm = false; reload() } }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { showForm = false }) { Text("Batal") } })
    }
}

@Composable
fun KeuanganScreen() {
    var entries by remember { mutableStateOf<List<CashEntry>>(emptyList()) }
    LaunchedEffect(Unit) { (repo.listCashEntries() as? ApiResult.Success)?.let { entries = it.data.entries } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Keuangan", style = MaterialTheme.typography.headlineSmall); DemoBanner()
        val masuk = entries.filter { it.type == "in" }.sumOf { it.amount }; val keluar = entries.filter { it.type == "out" }.sumOf { it.amount }
        Text("Masuk: ${formatIDR(masuk)} • Keluar: ${formatIDR(keluar)} • Saldo: ${formatIDR(masuk - keluar)}", style = MaterialTheme.typography.titleMedium)
        LazyColumn { items(entries) { e -> ListItem(headlineContent = { Text(e.description) }, supportingContent = { Text("${e.category} • ${e.createdAt}") }, trailingContent = { Text((if (e.type == "in") "+" else "-") + formatIDR(e.amount)) }) } }
    }
}

@Composable
fun LabaScreen() {
    var report by remember { mutableStateOf<ProfitReport?>(null) }
    var period by remember { mutableStateOf("month") }
    LaunchedEffect(period) { (repo.getProfitReport(period) as? ApiResult.Success)?.let { report = it.data } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Laporan Laba", style = MaterialTheme.typography.headlineSmall); DemoBanner()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("today" to "Hari Ini", "month" to "Bulan Ini", "last30" to "30 Hari", "all" to "Semua").forEach { (k, l) -> FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) }) } } }
        report?.let { r -> item { Card(Modifier.fillMaxWidth().padding(top = 12.dp)) { Column(Modifier.padding(16.dp)) {
            Text("Omzet: ${formatIDR(r.revenue)}"); Text("HPP: ${formatIDR(r.hpp)}"); Text("Laba Kotor: ${formatIDR(r.grossProfit)} (margin ${r.marginPct}%)", style = MaterialTheme.typography.titleMedium); Text("Operasional: ${formatIDR(r.operationalExpense)}"); Text("Estimasi Laba Bersih: ${formatIDR(r.netProfit)}", style = MaterialTheme.typography.titleMedium); Text("${r.transactionCount} transaksi • ${r.itemsSold} item terjual")
            if (r.costMissingQty > 0) Text("Peringatan: ${r.costMissingQty} item tanpa harga beli — laba bisa overstated", color = MaterialTheme.colorScheme.error)
        } } }
            item { Spacer(Modifier.height(12.dp)); Text("Per Obat", style = MaterialTheme.typography.titleMedium) }
            items(r.byMedicine) { m -> ListItem(headlineContent = { Text(m.name) }, trailingContent = { Text(formatIDR(m.profit)) }) }
            item { Text("Per Kategori", style = MaterialTheme.typography.titleMedium) }
            items(r.byCategory) { c -> ListItem(headlineContent = { Text(c.category) }, trailingContent = { Text(formatIDR(c.profit)) }) }
            item { Text("Download Excel laporan: tersedia dalam mode server lewat downloadProfitReport.", style = MaterialTheme.typography.bodySmall) }
        } ?: item { CircularProgressIndicator() }
    }
}

@Composable
fun PengingatScreen(onGoStok: () -> Unit) {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines } }
    val low = medicines.filter { it.stock <= it.minStock }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pengingat", style = MaterialTheme.typography.headlineSmall); DemoBanner()
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Cari nama / kategori obat…") }, modifier = Modifier.fillMaxWidth())
        Text("Stok menipis (${low.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn { items(low.filter { search.isBlank() || it.name.contains(search, true) || it.category.contains(search, true) }) { m -> ListItem(headlineContent = { Text(m.name) }, supportingContent = { Text("Sisa ${m.stock} ${m.unit} • minimum ${m.minStock}") }) }
            item { Button(onClick = onGoStok, modifier = Modifier.fillMaxWidth()) { Text("Buka Stok") } } }
    }
}

@Composable
fun RiwayatScreen() {
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { (repo.listSales() as? ApiResult.Success)?.let { sales = it.data.sales } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Riwayat Penjualan", style = MaterialTheme.typography.headlineSmall); DemoBanner()
        if (sales.isEmpty()) Text("Belum ada penjualan tercatat.")
        LazyColumn { items(sales) { s -> Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Column(Modifier.padding(12.dp)) {
            Text("${s.invoiceNo} • ${formatIDR(s.total)}", style = MaterialTheme.typography.titleSmall); Text("${s.createdAt} • ${s.paymentMethod}")
            TextButton(onClick = { expanded = if (expanded == s.id) null else s.id }) { Text(if (expanded == s.id) "Tutup Invoice" else "Lihat Invoice — Apotek Lestari") }
            if (expanded == s.id) { Text("APOTEK LESTARI — Invoice Penjualan", style = MaterialTheme.typography.titleMedium); s.items.forEach { Text("${it.medicineName} × ${it.qty} = ${formatIDR(it.subtotal)}") }; Text("Total: ${formatIDR(s.total)} • Dibayar: ${formatIDR(s.amountPaid)} • Kembalian: ${formatIDR(s.changeAmount)}") }
        } } } }
    }
}

