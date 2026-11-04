@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import id.apotek.lestari.data.*
import id.apotek.lestari.ui.BtPrinter
import id.apotek.lestari.ui.buildReceiptBytes
import id.apotek.lestari.ui.getPairedPrinters
import id.apotek.lestari.ui.hasBluetoothPermission
import id.apotek.lestari.ui.isBluetoothEnabled
import id.apotek.lestari.ui.loadSavedPrinter
import id.apotek.lestari.ui.printReceiptBitmap
import id.apotek.lestari.ui.printReceiptToPrinter
import id.apotek.lestari.ui.rememberLogoBitmap
import id.apotek.lestari.ui.savePrinter
import id.apotek.lestari.ui.shareReceiptBitmap
import id.apotek.lestari.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Layar Kasir (keranjang, pembayaran, struk).

class CartItem(val medicine: Medicine, qty: Int) {
    var qty by mutableStateOf(qty)
    // Pilihan harga per item di keranjang: ecer (bawaan) atau grosir
    // (permintaan Achmad 2026-10-29). Harga satuan yang berlaku dihitung
    // dari pilihan ini; Repository.createSale menerapkan aturan yang sama
    // saat harga disimpan ke penjualan.
    var useWholesale by mutableStateOf(false)
    val unitPrice: Int
        get() = if (useWholesale && medicine.wholesalePrice > 0) medicine.wholesalePrice else medicine.sellPrice
}


/**
 * Millis tengah malam UTC untuk tanggal "yyyy-MM-dd" — dipakai sebagai
 * initialSelectedDateMillis DatePicker. DatePicker memakai UTC, sedangkan
 * parseISODate memakai timezone default HP untuk pola "yyyy-MM-dd", sehingga
 * tanggal yang dipilih sebelumnya bisa geser 1 hari di zona WIB.
 */
fun utcMidnightMillis(dateKey: String): Long? {
    if (dateKey.isBlank()) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(dateKey)?.time
    } catch (_: Exception) {
        null
    }
}


/** Pemilih tanggal maksimum hari ini — untuk tanggal yang tidak boleh dari
 * masa depan (tanggal entri Catat Kas & Tanggal Beli PBF; audit 2026-11-02,
 * M3: entri bertanggal depan terhitung di Saldo Kas tapi tak terlihat di
 * filter Hari Ini/Bulan Ini). Tanggal kedaluwarsa & jatuh tempo TIDAK
 * memakai ini karena memang berada di masa depan. */
object NoFutureDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val todayMillis = utcMidnightMillis(dayKeyID(Date())) ?: return true
        return utcTimeMillis <= todayMillis
    }
}


/** "yyyy-MM-dd" (WIB) -> string ISO UTC ("2026-10-27T00:00:00.000Z") untuk createdAt entri kas. */
fun dayKeyToIsoUtc(dayKey: String): String? {
    val millis = utcMidnightMillis(dayKey) ?: return null
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))
}


@Composable
fun KasirScreen() {
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var listEntered by remember { mutableStateOf(false) }
    LaunchedEffect(medicines) { if (medicines.isNotEmpty() && !listEntered) listEntered = true }
    var search by rememberSaveable { mutableStateOf("") }
    var sortAz by remember { mutableStateOf(true) }
    // Pesan umpan balik di bawah kolom cari (mis. stok tidak cukup saat obat
    // diketuk). Dulu juga dipakai hasil scan barcode; scan kamera dihapus
    // atas permintaan Achmad 2026-10-29.
    var cartMsg by remember { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("Tunai") }
    // TextFieldValue (bukan Int) + formatDecimalFieldValue supaya kursor tidak
    // meloncat saat mengetik nominal; TextFieldValue punya Saver bawaan.
    var uangDiterima by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var payMsg by rememberSaveable { mutableStateOf("") }
    var payCount by remember { mutableIntStateOf(0) }
    // Fase sukses besar setelah Bayar (varian 1 pilihan Achmad 2026-11-04):
    // sheet menampilkan centang besar di tengah sebelum struk muncul,
    // menggantikan baris centang kecil lama yang gampang terlewat.
    var successPhase by remember { mutableStateOf(false) }
    var successMethod by remember { mutableStateOf("") }
    var successTotal by remember { mutableIntStateOf(0) }
    var successChange by remember { mutableIntStateOf(0) }
    // Struk terakhir disimpan sebagai id saja, lalu dimuat ulang dari repo.
    // Deklarasi ini SENGAJA di atas blok animasi struk: LaunchedEffect di
    // bawah membaca lastSale, dan Kotlin 2.4 menolak referensi ke variabel
    // yang deklarasinya masih di bawah (build 2026-11-04: "Unresolved
    // reference 'lastSale'"). Jangan pindahkan kembali ke bawah.
    var lastSaleId by rememberSaveable { mutableIntStateOf(0) }
    var lastSale by remember { mutableStateOf<Sale?>(null) }
    // Animasi masuk blok struk (laporan Achmad 2026-11-04: struk muncul
    // tanpa animasi sama sekali setelah fase sukses). Hanya alpha +
    // translation + skala kecil lewat graphicsLayer — BUKAN animasi tinggi
    // atau AnimatedVisibility, supaya tidak bertabrakan dengan pegas sheet
    // (alasan blok struk memang polos selama ini).
    val receiptEnter = remember { Animatable(0f) }
    LaunchedEffect(lastSale?.id, successPhase) {
        if (!successPhase && lastSale != null) {
            receiptEnter.snapTo(0f)
            receiptEnter.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
        }
    }
    // Pratinjau struk di sheet dilipat secara bawaan (desain K1, pilihan
    // Achmad 2026-11-04) supaya tombol Bayar selalu terlihat tanpa gulir.
    var showReceiptPreview by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val receiptLayer = rememberGraphicsLayer()
    // Keranjang survive rotasi lewat pasangan (medicineId, qty) yang saveable;
    // cart sendiri dibangun ulang dari daftar obat begitu tersedia.
    var savedCart by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    val cart = remember { mutableStateListOf<CartItem>() }
    // Penanda pemulihan awal keranjang sudah selesai (daftar obat pertama
    // kali tersedia). Selama false, penulisan savedCart ditahan supaya data
    // tersimpan tidak tertimpa keranjang kosong sebelum sempat dipulihkan.
    var cartInitialized by remember { mutableStateOf(false) }
    // Obat yang jumlahnya sedang diubah lewat dialog ketik angka (null = dialog tutup).
    var qtyEditTarget by remember { mutableStateOf<CartItem?>(null) }
    val scope = rememberCoroutineScope()
    // Menutup sheet Kasir DENGAN animasi: sheetState.hide() menganimasikan
    // sheet turun terlebih dahulu, baru sheet dilepas dari komposisi.
    // Menyetel showSheet = false secara langsung membuat sheet hilang
    // sekejap tanpa animasi (dilaporkan Achmad 2026-11-01 pada tombol
    // Tutup setelah pembayaran selesai).
    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) showSheet = false
        }
    }
    // Supaya field Uang Diterima otomatis tergulir ke atas keyboard saat diketuk.
    val amountBringIntoView = remember { BringIntoViewRequester() }
    // Animasi "terbang ke keranjang": saat obat ditambahkan, ikon obat melayang
    // dari kartu obat yang diketuk ke bar keranjang. Posisi dicatat dalam
    // koordinat window lalu diubah ke koordinat lokal Box root (dikurangi
    // posisi window Box root). cardCenters = pusat kartu obat per id obat.
    var rootWindowPos by remember { mutableStateOf(Offset.Zero) }
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var cartBarCenter by remember { mutableStateOf<Offset?>(null) }
    val cardCenters = remember { mutableStateMapOf<Int, Offset>() }
    var flyFrom by remember { mutableStateOf<Offset?>(null) }
    var flyTo by remember { mutableStateOf(Offset.Zero) }
    var flyId by remember { mutableIntStateOf(0) }
    // Density dibaca di sini (saat komposisi) supaya bisa dipakai di dalam
    // handler klik untuk menghitung posisi fallback target.
    val flyDensity = LocalDensity.current
    // Muat ulang setiap kali layar kembali tampil (ON_RESUME) — pola yang
    // sama seperti Laba/Obat Terlaris/Pengingat/Faktur PBF, supaya data
    // selalu segar setelah ada perubahan dari menu lain.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeKey) { (repo.listMedicines() as? ApiResult.Success)?.let { medicines = it.data.medicines } }
    // Pulihkan keranjang dari data tersimpan, sekali saja, begitu daftar
    // obat pertama kali tersedia. Sesudah itu cartInitialized menyala dan
    // penulisan savedCart di bawah mengambil alih.
    LaunchedEffect(medicines) {
        if (medicines.isNotEmpty() && !cartInitialized) {
            if (cart.isEmpty() && savedCart.isNotEmpty()) {
                // Format lama (berpasangan id+qty) tidak berkelipatan 3 —
                // buang saja daripada memulihkan keranjang yang salah tafsir.
                if (savedCart.size % 3 == 0) {
                    val byId = medicines.associateBy { it.id }
                    savedCart.chunked(3).forEach { triple ->
                        if (triple.size == 3) byId[triple[0]]?.let { med ->
                            cart.add(CartItem(med, triple[1]).also { ci -> ci.useWholesale = triple[2] == 1 })
                        }
                    }
                }
            }
            cartInitialized = true
        }
    }
    // Simpan isi keranjang (id + qty + pilihan grosir) setiap kali berubah —
    // TERMASUK saat kosong, supaya keranjang yang dikosongkan manual tidak
    // bangkit lagi setelah rotasi atau pindah tab (audit 2026-11-02, M1:
    // sebelumnya penulisan diskip saat kosong, jadi isi lama bertahan di
    // savedCart dan dipulihkan lagi). Pilihan grosir ikut disimpan (audit
    // N2, 2026-10-30): tanpa itu, item yang dipulihkan diam-diam kembali ke
    // harga Ecer dan total berubah sementara isian Uang Diterima tetap.
    val cartSnapshot = cart.map { Triple(it.medicine.id, it.qty, it.useWholesale) }
    LaunchedEffect(cartSnapshot, cartInitialized) {
        if (cartInitialized) {
            savedCart = cartSnapshot.flatMap { listOf(it.first, it.second, if (it.third) 1 else 0) }
        }
    }
    // Timer fase sukses: beri waktu animasi centang besar selesai
    // (~1,5 detik), lalu fase berakhir dan blok struk mengambil alih.
    LaunchedEffect(successPhase) {
        if (successPhase) {
            delay(1500)
            successPhase = false
        }
    }
    // Muat ulang struk terakhir dari id tersimpan (untuk restore setelah rotasi).
    LaunchedEffect(lastSaleId) {
        if (lastSaleId != 0 && lastSale?.id != lastSaleId) {
            lastSale = (repo.listSales() as? ApiResult.Success)?.data?.sales?.firstOrNull { it.id == lastSaleId }
        }
    }
    val filtered = medicines.filter { search.isBlank() || it.name.contains(search, true) || it.category.contains(search, true) || (it.barcode?.contains(search) == true) }
    val sorted = if (sortAz) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
    val total = cart.sumOf { it.unitPrice * it.qty }
    val cartQty = cart.sumOf { it.qty }
    val paid = parseDecimal(uangDiterima.text)
    val change = if (method == "Tunai") (paid - total).coerceAtLeast(0) else 0
    val canPay = cart.isNotEmpty() && (method != "Tunai" || paid >= total)
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned {
                rootWindowPos = it.positionInWindow()
                rootSizePx = it.size
            }
    ) {
        // Daftar obat memenuhi layar; keranjang & pembayaran pindah ke bottom sheet.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                Icon(Icons.Default.PointOfSale, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(tr("Kasir", "Cashier"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(SpaceL))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(SpaceL))
        DemoBanner()
        SearchField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth()
        )
        if (cartMsg.isNotEmpty()) {
            Spacer(Modifier.height(SpaceS))
            Text(cartMsg, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(SpaceL))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr("${filtered.size} obat", "${filtered.size} medicines"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SortToggle(sortAz = sortAz, onToggle = { sortAz = !sortAz })
        }
        Spacer(Modifier.height(SpaceS))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sorted.forEachIndexed { index, m ->
                AnimatedVisibility(
                    visible = listEntered,
                    enter = fadeIn(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)) { it / 3 } +
                        expandVertically(animationSpec = tween(300, entranceDelay(index), FastOutSlowInEasing)),
                    exit = fadeOut()
                ) {
                val out = m.stock <= 0
                ExpressiveCard(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                        .onGloballyPositioned { cardCenters[m.id] = it.boundsInWindow().center }
                        .clickable(enabled = !out) {
                        val ex = cart.firstOrNull { it.medicine.id == m.id }
                        if (ex != null && ex.qty >= m.stock) {
                            cartMsg = tr("Stok ${m.name} hanya ${m.stock} — tidak bisa ditambah lagi", "Only ${m.stock} of ${m.name} in stock — can't add more")
                        } else {
                            lastSale = null
                            lastSaleId = 0
                            payMsg = ""
                            if (ex != null) ex.qty++ else cart.add(CartItem(m, 1))
                            // Picu animasi terbang ke keranjang: dari pusat kartu
                            // obat ini ke pusat bar keranjang (koordinat lokal Box).
                            // Kalau posisi bar keranjang belum tercatat (keranjang
                            // masih kosong saat obat pertama ditambah), pakai
                            // perkiraan tengah-bawah layar sebagai target.
                            val start = cardCenters[m.id]
                            if (start != null) {
                                val fallbackTarget = Offset(
                                    rootSizePx.width / 2f,
                                    rootSizePx.height - with(flyDensity) { 64.dp.toPx() }
                                )
                                flyFrom = start - rootWindowPos
                                flyTo = (cartBarCenter ?: fallbackTarget) - rootWindowPos
                                flyId++
                            }
                        }
                    }
                ) {
                    // Baris sangat padat KS6 (pilihan Achmad 2026-11-04):
                    // dua baris saja — nama + harga, lalu kategori + stok
                    // sebagai teks berwarna tanpa kotak lencana — supaya
                    // 6-8 obat muat dalam satu layar saat stok bertambah
                    // banyak. Ketuk kartu & animasi terbang tidak berubah.
                    val stockKey = stockLabel(m.stock)
                    val stockText = when (stockKey) {
                        "Aman" -> tr("Stok ${m.stock} • Aman", "Stock ${m.stock} • Safe")
                        "Sedang" -> tr("Stok ${m.stock} • Sedang", "Stock ${m.stock} • Medium")
                        "Rendah" -> tr("Stok ${m.stock} • Rendah", "Stock ${m.stock} • Low")
                        else -> tr("Stok ${m.stock} • Habis", "Stock ${m.stock} • Out of stock")
                    }
                    val stockColor = when (stockKey) {
                        "Aman" -> MaterialTheme.colorScheme.primary
                        "Sedang" -> MaterialTheme.colorScheme.tertiary
                        "Rendah" -> warningAmber()
                        else -> MaterialTheme.colorScheme.error
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                m.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (out) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(formatIDR(m.sellPrice), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${m.category} • ${m.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Text(stockText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = stockColor, textAlign = TextAlign.End)
                        }
                    }
                }
                }
            }
        }
            // Ruang untuk bar keranjang yang nempel di bawah, supaya obat terakhir tetap bisa digulir ke atasnya.
            if (cart.isNotEmpty()) Spacer(Modifier.height(96.dp))
        }

        // Bar keranjang nempel di bawah: total + tombol Bayar (membuka bottom sheet).
        AnimatedVisibility(
            visible = cart.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExpressiveCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .onGloballyPositioned { cartBarCenter = it.boundsInWindow().center }
                    .clickable { showSheet = true }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    key(cartQty) {
                        val cartPop = remember { Animatable(1.4f) }
                        LaunchedEffect(cartQty) { cartPop.animateTo(1f, expressiveSpring()) }
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape, modifier = Modifier.graphicsLayer(scaleX = cartPop.value, scaleY = cartPop.value)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("$cartQty", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$cartQty item", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formatIDR(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = { showSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.height(48.dp)
                    ) { Text(tr("Bayar", "Pay"), style = MaterialTheme.typography.titleMedium) }
                }
            }
        }

        // Overlay animasi terbang ke keranjang: ikon obat melayang dari kartu
        // yang diketuk ke bar keranjang, mengecil & memudar. Overlay ini tidak
        // menghalangi sentuhan (tidak ada clickable di dalamnya).
        // Posisi dianimasikan lewat progress 0f -> 1f + lerp manual, bukan
        // Animatable<Offset> — Offset.VectorConverter tidak ter-resolve di
        // versi Compose ini (itu penyebab error build). coroutineScope
        // menunggu ketiga animasi selesai dulu sebelum overlay dihapus —
        // tanpa itu, flyFrom = null langsung jalan dan overlay hilang
        // sebelum animasi terlihat.
        if (flyFrom != null) {
            key(flyId) {
                val start = checkNotNull(flyFrom)
                val end = flyTo
                val iconHalfPx = with(flyDensity) { 22.dp.toPx() }
                val progress = remember { Animatable(0f) }
                val scale = remember { Animatable(1f) }
                val alpha = remember { Animatable(1f) }
                LaunchedEffect(flyId) {
                    coroutineScope {
                        launch { progress.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing)) }
                        launch { scale.animateTo(0.4f, animationSpec = tween(500)) }
                        launch { alpha.animateTo(0f, animationSpec = tween(500)) }
                    }
                    flyFrom = null
                }
                val current = Offset(
                    x = start.x + (end.x - start.x) * progress.value,
                    y = start.y + (end.y - start.y) * progress.value
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (current.x - iconHalfPx).roundToInt(),
                                    (current.y - iconHalfPx).roundToInt()
                                )
                            }
                            .size(44.dp)
                            .graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Medication, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet: keranjang + pembayaran (dan struk setelah bayar berhasil).
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = closeSheet,
            sheetState = sheetState,
            // Lebar sheet adaptif (lihat adaptiveSheetMaxWidth): selebar layar
            // di HP potret, 360dp di HP lanskap, 640dp di tablet.
            sheetMaxWidth = adaptiveSheetMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (cart.isNotEmpty()) tr("Keranjang (${cart.sumOf { it.qty }})", "Cart (${cart.sumOf { it.qty }})") else tr("Keranjang", "Cart"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = closeSheet) { Icon(Icons.Default.Close, tr("Tutup", "Close")) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                if (successPhase) {
                    // Tampilan sukses penuh (varian 1): centang besar
                    // beranimasi di tengah sheet + rincian pembayaran.
                    // Struk di bawah menunggu fase ini selesai supaya
                    // animasinya tidak tertutup perubahan isi sheet.
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        key(payCount) { AnimatedCheckmark(size = 120.dp) }
                        Spacer(Modifier.height(SpaceL))
                        Text(tr("Pembayaran Berhasil", "Payment Successful"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(SpaceS))
                        Text(tr("Dibayar via $successMethod \u2022 Total ${formatIDR(successTotal)}", "Paid via $successMethod \u2022 Total ${formatIDR(successTotal)}"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (successChange > 0) {
                            Spacer(Modifier.height(SpaceXS))
                            Text(tr("Kembalian ${formatIDR(successChange)}", "Change ${formatIDR(successChange)}"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (cart.isNotEmpty()) {
                    // Panel hero KR4 (pilihan Achmad 2026-11-04): total
                    // besar + jumlah item/jenis + pilihan metode bayar
                    // menyatu di SATU panel biru di atas daftar item —
                    // menggantikan bar Total bayar dan baris METODE BAYAR
                    // yang dulu di bawah item. Segmen terpilih putih dengan
                    // teks primer (terbalik dari panelnya) supaya terbaca
                    // jelas di atas biru.
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("TOTAL BAYAR", "TOTAL TO PAY"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                                Text(tr("${cart.sumOf { it.qty }} item \u2022 ${cart.size} jenis", "${cart.sumOf { it.qty }} items \u2022 ${cart.size} kinds"), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                            }
                            Spacer(Modifier.height(SpaceXS))
                            Text(formatIDR(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Spacer(Modifier.height(SpaceM))
                            val methods = listOf(
                                "Tunai" to Icons.Default.Money,
                                "QRIS" to Icons.Default.QrCode,
                                "Transfer" to Icons.Default.AccountBalance
                            )
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                methods.forEachIndexed { index, (m, icon) ->
                                    SegmentedButton(
                                        selected = method == m,
                                        onClick = { method = m },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = methods.size),
                                        icon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = Color.White,
                                            activeContentColor = MaterialTheme.colorScheme.primary,
                                            activeBorderColor = Color.White,
                                            inactiveContainerColor = Color.Transparent,
                                            inactiveContentColor = Color.White,
                                            inactiveBorderColor = Color.White.copy(alpha = 0.55f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (m == "Tunai") tr("Tunai", "Cash") else m, maxLines = 1) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                AnimatedContent(
                    targetState = method,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(250)) + slideInVertically(animationSpec = tween(250)) { it / 4 }) togetherWith
                        (fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 4 })
                    },
                    label = "methodContent"
                ) { targetMethod ->
                    if (targetMethod == "Tunai") {
                        Column {
                            Spacer(Modifier.height(SpaceL))
                            Text(tr("UANG DITERIMA", "CASH RECEIVED"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(SpaceS))
                            // Kartu nominal UD4 (pilihan Achmad 2026-11-04): empat
                            // kartu besar — Uang Pas + tiga pembulatan 50 rb ke atas
                            // dari total — masing-masing menampilkan kembaliannya;
                            // mengetuk kartu mengisi kolom uang diterima. Kartu yang
                            // nilainya sama dengan isi kolom tampil terpilih (biru).
                            val firstRoundUp = (total / 50000 + 1) * 50000
                            val nominalOptions = listOf(total, firstRoundUp, firstRoundUp + 50000, firstRoundUp + 100000)
                            nominalOptions.chunked(2).forEach { rowOptions ->
                                Row(horizontalArrangement = Arrangement.spacedBy(SpaceS)) {
                                    rowOptions.forEach { amount ->
                                        val selectedCard = paid == amount
                                        Surface(
                                            onClick = {
                                                val formatted = formatDecimal(amount.toString())
                                                uangDiterima = TextFieldValue(formatted, TextRange(formatted.length))
                                            },
                                            color = if (selectedCard) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                                Text(
                                                    if (amount == total) tr("Uang Pas", "Exact Amount") else formatIDR(amount),
                                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1,
                                                    color = if (selectedCard) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    if (amount == total) formatIDR(total) else tr("Kembalian", "Change") + " " + formatCompactIDR(amount - total),
                                                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                                    color = if (selectedCard) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(SpaceS))
                            }
                            Text(tr("Jumlah lain (ketik sendiri)", "Other amount (type it yourself)"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(SpaceXS))
                            Spacer(Modifier.height(SpaceS))
                            OutlinedTextField(
                                value = uangDiterima,
                                onValueChange = { uangDiterima = formatDecimalFieldValue(it) },
                                label = { Text(tr("cth: 50.000", "e.g. 50.000")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                                    .bringIntoViewRequester(amountBringIntoView)
                                    .onFocusEvent { if (it.isFocused) scope.launch { amountBringIntoView.bringIntoView() } }
                            )
                            AnimatedVisibility(visible = change > 0) {
                                Column {
                                    Spacer(Modifier.height(SpaceS))
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(tr("Kembalian", "Change"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(formatIDR(change), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column {
                            Spacer(Modifier.height(SpaceS))
                            Text(tr("Dibayar penuh via $targetMethod sebesar ${formatIDR(total)}.", "Paid in full via $targetMethod, ${formatIDR(total)}."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                    cart.forEachIndexed { index, ci ->
                        // Dua baris per item: (nama | total baris), lalu
                        // (pilihan Ecer/Grosir | stepper jumlah | hapus | harga
                        // satuan). Pilihan harga ditaruh persis di samping tombol
                        // -/+ jumlah, dan ikon hapus di sebelah kanan tombol +
                        // (keduanya sesuai permintaan Achmad 2026-10-29).
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ci.medicine.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(formatIDR(ci.unitPrice * ci.qty), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.End)
                            }
                            Spacer(Modifier.height(SpaceXS))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Pilihan harga hanya muncul bila harga grosir
                                // obat ini sudah diatur (> 0) di data obat.
                                if (ci.medicine.wholesalePrice > 0) {
                                    PriceModeChip(tr("Ecer", "Retail"), selected = !ci.useWholesale) { ci.useWholesale = false }
                                    Spacer(Modifier.width(6.dp))
                                    PriceModeChip(tr("Grosir", "Wholesale"), selected = ci.useWholesale) { ci.useWholesale = true }
                                    Spacer(Modifier.width(12.dp))
                                }
                                StepperButton(Icons.Default.Remove, tr("Kurangi", "Decrease")) { if (ci.qty > 1) ci.qty-- else cart.remove(ci) }
                                // Animasi "pop" angka jumlah HANYA saat jumlah berubah
                                // SETELAH komposisi pertama. Dulu key(ci.qty) +
                                // Animatable(1.35f) membuat pop berjalan di semua
                                // baris tepat saat animasi buka sheet berjalan —
                                // salah satu sumber jitter saat membuka keranjang.
                                val qtyPop = remember { Animatable(1f) }
                                var qtyPopArmed by remember { mutableStateOf(false) }
                                LaunchedEffect(ci.qty) {
                                    if (qtyPopArmed) {
                                        qtyPop.snapTo(1.35f)
                                        qtyPop.animateTo(1f, expressiveSpring())
                                    } else {
                                        qtyPopArmed = true
                                    }
                                }
                                // Angka jumlah bisa diketuk untuk ketik jumlah langsung
                                // (pill abu-abu menandakan bisa diketuk).
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp).clickable { qtyEditTarget = ci }
                                ) {
                                    Text(
                                        "${ci.qty}",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.width(44.dp).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically).graphicsLayer(scaleX = qtyPop.value, scaleY = qtyPop.value),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                StepperButton(Icons.Default.Add, tr("Tambah", "Increase")) { if (ci.qty < ci.medicine.stock) ci.qty++ }
                                Spacer(Modifier.width(8.dp))
                                // Hapus: keluarkan obat dari keranjang (di kanan
                                // tombol + jumlah).
                                IconButton(onClick = { cart.remove(ci) }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Delete, tr("Hapus", "Delete"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Text("@ ${formatIDR(ci.unitPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (index < cart.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                // Pratinjau struk sebelum bayar — kasir bisa cek isi, total,
                // metode, dan kembalian dulu; ikut berubah saat keranjang,
                // metode, atau uang diterima diubah.
                if (cart.isNotEmpty()) {
                    Spacer(Modifier.height(SpaceL))
                    // Desain K1: pratinjau dilipat di balik tombol Lihat —
                    // dibuka hanya saat kasir mau memeriksa isi struk.
                    // Tanpa AnimatedVisibility (aturan anti-jitter sheet).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("PRATINJAU STRUK", "RECEIPT PREVIEW"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showReceiptPreview = !showReceiptPreview }) {
                            Text(if (showReceiptPreview) tr("Sembunyikan", "Hide") else tr("Lihat", "View"))
                            Icon(if (showReceiptPreview) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (showReceiptPreview) {
                        Spacer(Modifier.height(SpaceS))
                        ReceiptPreviewCard(
                            items = cart,
                            total = total,
                            method = method,
                            paid = paid,
                            change = change
                        )
                    }
                }
                Spacer(Modifier.height(SpaceL))
                val bayarInteraction = remember { MutableInteractionSource() }
                val bayarScale = pressBounceScale(bayarInteraction)
                Button(
                    onClick = {
                        scope.launch {
                            val amt = if (method == "Tunai") paid else total
                            val result = repo.createSale(CreateSaleArgs(cart.map { CreateSaleItemArgs(it.medicine.id, it.qty, it.useWholesale) }, method, amt))
                            if (result is ApiResult.Success) {
                                // Masuk fase sukses besar: tangkap rincian
                                // pembayaran SEBELUM keranjang dikosongkan.
                                successMethod = method
                                successTotal = total
                                successChange = change
                                successPhase = true
                                payMsg = ""
                                payCount++
                                cart.clear()
                                savedCart = emptyList()
                                uangDiterima = TextFieldValue("")
                                val after = (repo.listMedicines() as? ApiResult.Success)?.data?.medicines.orEmpty()
                                medicines = after
                                val fresh = (repo.listSales() as? ApiResult.Success)?.data?.sales.orEmpty()
                                lastSale = fresh.firstOrNull { it.id == result.data.id }
                                lastSaleId = result.data.id
                            } else {
                                // Gagal: keranjang & uang diterima dibiarkan utuh supaya
                                // kasir bisa mencoba lagi tanpa mengulang input.
                                payMsg = tr("Pembayaran gagal: ", "Payment failed: ") + ((result as? ApiResult.Error)?.message ?: tr("tidak diketahui", "unknown"))
                            }
                        }
                    },
                    enabled = canPay,
                    interactionSource = bayarInteraction,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).graphicsLayer(scaleX = bayarScale, scaleY = bayarScale)
                ) { Text(tr("Bayar (${formatIDR(total)})", "Pay (${formatIDR(total)})"), style = MaterialTheme.typography.titleMedium) }
                }
                // Pesan galat pembayaran saja (keberhasilan sekarang lewat
                // fase sukses besar di atas). Sengaja TANPA AnimatedVisibility:
                // animasi mengembangnya bertabrakan dengan animasi tinggi
                // sheet (pegas draggable) sehingga gerak sheet terasa jitter.
                if (payMsg.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(SpaceS))
                        Text(payMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                // Tetap TANPA AnimatedVisibility (alasan sama seperti blok
                // pesan sukses di atas): animasi mengembang membuat sheet
                // jitter. Sebagai gantinya konten struk diberi animasi masuk
                // graphicsLayer (memudar + naik + membesar tipis) yang tidak
                // mengubah ukuran sama sekali — lihat receiptEnter di atas.
                if (!successPhase && lastSale != null) {
                    Column(
                        Modifier.graphicsLayer {
                            alpha = receiptEnter.value
                            translationY = (1f - receiptEnter.value) * 24.dp.toPx()
                            scaleX = 0.98f + 0.02f * receiptEnter.value
                            scaleY = 0.98f + 0.02f * receiptEnter.value
                        }
                    ) {
                        Spacer(Modifier.height(SpaceL))
                        lastSale?.let {
                            InvoiceDetailCard(sale = it, graphicsLayer = receiptLayer)
                            Spacer(Modifier.height(SpaceM))
                            ReceiptActionsRow(graphicsLayer = receiptLayer, invoiceNo = it.invoiceNo, sale = it)
                            Spacer(Modifier.height(SpaceM))
                            OutlinedButton(
                                onClick = closeSheet,
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text(tr("Tutup", "Close")) }
                        }
                    }
                }
            }
        }
    }
    // Dialog ubah jumlah langsung: ketuk angka jumlah di keranjang, lalu ketik
    // angka pasti — tidak perlu mengetuk + berulang kali. Jumlah 0 mengeluarkan
    // obat dari keranjang (sama seperti − saat jumlah 1); jumlah di atas stok
    // dijepit ke stok (sama seperti tombol +).
    if (qtyEditTarget != null) {
        val target = checkNotNull(qtyEditTarget)
        var qtyText by remember { mutableStateOf("${target.qty}") }
        var qtyError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { qtyEditTarget = null },
            title = { Text(tr("Ubah Jumlah", "Change Quantity")) },
            text = {
                Column {
                    Text(target.medicine.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(SpaceXS))
                    Text(tr("Stok tersedia: ${target.medicine.stock} ${target.medicine.unit}", "Stock available: ${target.medicine.stock} ${target.medicine.unit}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(SpaceM))
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { ch -> ch.isDigit() }; qtyError = "" },
                        label = { Text(tr("Jumlah", "Quantity")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (qtyError.isNotEmpty()) {
                        Spacer(Modifier.height(SpaceXS))
                        Text(qtyError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val v = qtyText.toIntOrNull()
                        if (v == null) {
                            qtyError = tr("Masukkan angka yang valid.", "Enter a valid number.")
                        } else {
                            val clamped = minOf(v, target.medicine.stock)
                            if (clamped <= 0) cart.remove(target) else target.qty = clamped
                            qtyEditTarget = null
                        }
                    }
                ) { Text(tr("Simpan", "Save"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { qtyEditTarget = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}

// ---------- Stok Obat ----------
