@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package id.apotek.lestari.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Layar Riwayat Penjualan beserta kartu faktur/struknya.

// ---------- Invoice detail (struk) ----------
// Struk putih solid seperti di web app, supaya tetap terbaca di atas latar gelap
// dan enak kalau dicetak/di-screenshot.
val InvoicePaper = Color.White


val InvoiceInk = InvoiceInkToken


val InvoiceMuted = InvoiceMutedToken


val InvoiceBoxBg = InvoiceBoxBgToken


val InvoiceBlue = InvoiceBlueToken


val InvoicePaidBg = InvoicePaidBgToken


val InvoicePaidFg = InvoicePaidFgToken


val InvoiceDash = InvoiceDashToken


@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = InvoiceDash,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )
    }
}


@Composable
fun InvoiceInfoBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = InvoiceMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(SpaceXS))
            Text(value, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}


// Pratinjau struk sebelum bayar — tampil di sheet keranjang supaya kasir bisa
// cek isi, total, metode, dan kembalian sebelum pembayaran diproses.
// Formatnya mengikuti kartu struk asli (InvoiceDetailCard) tapi lebih ringkas:
// tanpa nomor invoice & tanggal (belum ada penjualan), dengan lencana PRATINJAU.
@Composable
fun ReceiptPreviewCard(
    items: List<CartItem>,
    total: Long,
    method: String,
    paid: Long,
    change: Long,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InvoicePaper),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "APOTEK LESTARI", color = InvoiceBlue,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceXS))
            Text(
                tr("Pratinjau Struk", "Receipt Preview"), color = InvoiceMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceM))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(RadiusCard)) {
                    Text(
                        tr("Pratinjau", "Preview"), color = InvoiceMuted,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(SpaceM))
            DashedDivider()
            Spacer(Modifier.height(SpaceM))
            Text(tr("Rincian obat", "Medicine details"), color = InvoiceMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(SpaceS))
            items.forEachIndexed { index, ci ->
                if (index > 0) DashedDivider(modifier = Modifier.padding(vertical = 8.dp))
                // SR1: tanpa lingkaran nomor.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(ci.medicine.name, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        if (ci.medicine.category.isNotBlank() || ci.medicine.unit.isNotBlank()) {
                            Text("${ci.medicine.category} • ${ci.medicine.unit}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${ci.qty} × ${formatIDR(ci.unitPrice)} • ${if (ci.useWholesale && ci.medicine.wholesalePrice > 0) tr("Grosir", "Wholesale") else tr("Ecer", "Retail")}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatIDR(ci.unitPrice.toLong() * ci.qty), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(SpaceL))
            Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Total (${items.size} item)", "Total (${items.size} items)"), color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(formatIDR(total), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(SpaceS))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Metode Bayar", "Payment Method"), color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(method, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    if (method == "Tunai") {
                        Spacer(Modifier.height(SpaceS))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tr("Dibayar", "Paid"), color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                            Text(formatIDR(paid), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (change > 0) {
                            Spacer(Modifier.height(SpaceS))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tr("Kembalian", "Change"), color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                                Text(formatIDR(change), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    HorizontalDivider(color = InvoiceDash)
                    Spacer(Modifier.height(SpaceS))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Total Bayar", "Total to Pay"), color = InvoiceInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(formatIDR(total), color = InvoiceBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            Text(
                tr("Semoga Cepat Sembuh", "Get Well Soon"),
                color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                tr("Terima Kasih Atas Kunjungan Anda", "Thank You for Your Visit"),
                color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceM))
            Text(
                tr("Pratinjau: struk resmi dibuat setelah pembayaran berhasil.", "Preview: the official receipt is created after a successful payment."),
                color = InvoiceMuted, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Composable
fun InvoiceDetailCard(sale: Sale, modifier: Modifier = Modifier, graphicsLayer: GraphicsLayer? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InvoicePaper),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth().let {
            if (graphicsLayer != null) it.drawWithContent {
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            } else it
        }
    ) {
        Column(Modifier.padding(20.dp)) {
            val logo = rememberLogoBitmap()
            if (logo != null) {
                Image(
                    bitmap = logo,
                    contentDescription = "Logo Apotek Lestari",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(SpaceS))
            }
            Text(
                "APOTEK LESTARI", color = InvoiceBlue,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceXS))
            Text(
                tr("Invoice Penjualan", "Sales Invoice"), color = InvoiceMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SpaceM))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(color = InvoicePaidBg, shape = RoundedCornerShape(RadiusCard)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = InvoicePaidFg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Lunas", "Paid"), color = InvoicePaidFg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(SpaceM))
            DashedDivider()
            Spacer(Modifier.height(SpaceM))
            // SR1: kotak info jadi grid 2x2.
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                InvoiceInfoBox(tr("No. invoice", "Invoice no."), sale.invoiceNo, Modifier.weight(1f))
                InvoiceInfoBox(tr("Tanggal", "Date"), formatFullDateID(sale.createdAt), Modifier.weight(1f))
            }
            Spacer(Modifier.height(SpaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
                InvoiceInfoBox(tr("Metode bayar", "Payment method"), if (sale.paymentMethod == "Tunai") tr("Tunai", "Cash") else sale.paymentMethod, Modifier.weight(1f))
                InvoiceInfoBox(tr("Isi invoice", "Items"), tr("${sale.items.size} item", "${sale.items.size} items"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(SpaceL))
            Text(tr("Rincian obat", "Medicine details"), color = InvoiceMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(SpaceS))
            sale.items.forEachIndexed { index, it ->
                if (index > 0) DashedDivider(modifier = Modifier.padding(vertical = 8.dp))
                // SR1: tanpa lingkaran nomor.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(it.medicineName, color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        if (it.category.isNotBlank() || it.unit.isNotBlank()) {
                            Text("${it.category} • ${it.unit}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${it.qty} × ${formatIDR(it.price)} • ${if (it.priceType == "GROSIR") tr("Grosir", "Wholesale") else tr("Ecer", "Retail")}", color = InvoiceMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatIDR(it.subtotal), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(SpaceL))
            Surface(color = InvoiceBoxBg, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tr("Subtotal (${sale.items.size} item)", "Subtotal (${sale.items.size} items)"), color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(formatIDR(sale.total), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    if (sale.paymentMethod == "Tunai") {
                        Spacer(Modifier.height(SpaceS))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tr("Dibayar", "Paid"), color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                            Text(formatIDR(sale.amountPaid), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (sale.changeAmount > 0) {
                            Spacer(Modifier.height(SpaceS))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tr("Kembalian", "Change"), color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium)
                                Text(formatIDR(sale.changeAmount), color = InvoiceInk, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    HorizontalDivider(color = InvoiceDash)
                    Spacer(Modifier.height(SpaceS))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Total Bayar", "Total to Pay"), color = InvoiceInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(formatIDR(sale.total), color = InvoiceBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(SpaceL))
            Text(
                tr("Semoga Cepat Sembuh", "Get Well Soon"),
                color = InvoiceInk, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                tr("Terima Kasih Atas Kunjungan Anda", "Thank You for Your Visit"),
                color = InvoiceMuted, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


// Tombol di bawah struk: tangkap kartu invoice jadi gambar, lalu bagikan atau cetak.
// Tombolnya sengaja di luar kartu struk supaya tidak ikut masuk ke gambar struk.
@Composable
fun ReceiptActionsRow(graphicsLayer: GraphicsLayer, invoiceNo: String, sale: Sale, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Cetak struk ke printer thermal Bluetooth (ESC/POS).
    var showPrinterPicker by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<BtPrinter>>(emptyList()) }
    var savedPrinter by remember { mutableStateOf(loadSavedPrinter(context)) }

    fun doPrint(printer: BtPrinter) {
        scope.launch {
            val result = printReceiptToPrinter(context, printer.address, buildReceiptBytes(sale))
            result.onSuccess {
                Toast.makeText(context, tr("Struk terkirim ke ${printer.name}", "Receipt sent to ${printer.name}"), Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, tr("Gagal mencetak: ${e.message}", "Printing failed: ${e.message}"), Toast.LENGTH_LONG).show()
            }
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // Izin diberikan — ulangi alur cetak dari awal.
            if (!isBluetoothEnabled(context)) {
                Toast.makeText(context, tr("Nyalakan Bluetooth dulu", "Turn on Bluetooth first"), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val saved = loadSavedPrinter(context)
            if (saved != null) {
                doPrint(saved)
            } else {
                val paired = getPairedPrinters(context)
                if (paired.isEmpty()) {
                    Toast.makeText(context, tr("Tidak ada printer Bluetooth yang terpasang. Pasangkan printer di Pengaturan Bluetooth HP dulu.", "No Bluetooth printer is paired. Pair the printer in the phone's Bluetooth settings first."), Toast.LENGTH_LONG).show()
                } else {
                    pairedPrinters = paired
                    showPrinterPicker = true
                }
            }
        } else {
            Toast.makeText(context, tr("Izin Bluetooth ditolak, aktifkan di Pengaturan HP", "Bluetooth permission denied, enable it in the phone's Settings"), Toast.LENGTH_LONG).show()
        }
    }

    fun startBluetoothPrint() {
        // Android 12+ butuh izin runtime BLUETOOTH_CONNECT untuk terhubung.
        if (!hasBluetoothPermission(context)) {
            btPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        if (!isBluetoothEnabled(context)) {
            Toast.makeText(context, tr("Nyalakan Bluetooth dulu", "Turn on Bluetooth first"), Toast.LENGTH_SHORT).show()
            return
        }
        // Printer sudah pernah dipilih: langsung cetak ke printer itu.
        val saved = loadSavedPrinter(context)
        if (saved != null) {
            doPrint(saved)
            return
        }
        // Belum ada printer tersimpan: tampilkan daftar perangkat terpasang.
        val paired = getPairedPrinters(context)
        if (paired.isEmpty()) {
            Toast.makeText(context, tr("Tidak ada printer Bluetooth yang terpasang. Pasangkan printer di Pengaturan Bluetooth HP dulu.", "No Bluetooth printer is paired. Pair the printer in the phone's Bluetooth settings first."), Toast.LENGTH_LONG).show()
            return
        }
        pairedPrinters = paired
        showPrinterPicker = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            shareReceiptBitmap(context, graphicsLayer.toImageBitmap().asAndroidBitmap(), invoiceNo)
                        } catch (e: Exception) {
                            Toast.makeText(context, tr("Gagal menyiapkan struk", "Failed to prepare the receipt"), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Bagikan", "Share"))
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            printReceiptBitmap(context, graphicsLayer.toImageBitmap().asAndroidBitmap(), invoiceNo)
                        } catch (e: Exception) {
                            Toast.makeText(context, tr("Gagal mencetak struk", "Failed to print the receipt"), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Cetak", "Print"))
            }
        }
        Spacer(Modifier.height(SpaceS))
        Button(
            onClick = { startBluetoothPrint() },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Cetak Struk Bluetooth", "Print Bluetooth Receipt"))
        }
        // Printer yang tersimpan + opsi ganti (supaya bisa pindah printer).
        savedPrinter?.let { sp ->
            Spacer(Modifier.height(SpaceXS))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Printer: ${sp.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val paired = getPairedPrinters(context)
                    if (paired.isEmpty()) {
                        Toast.makeText(context, tr("Tidak ada printer Bluetooth yang terpasang.", "No Bluetooth printer is paired."), Toast.LENGTH_SHORT).show()
                    } else {
                        pairedPrinters = paired
                        showPrinterPicker = true
                    }
                }) { Text(tr("Ganti", "Change")) }
            }
        }
    }

    if (showPrinterPicker) {
        AlertDialog(
            onDismissRequest = { showPrinterPicker = false },
            title = { Text(tr("Pilih Printer Bluetooth", "Choose a Bluetooth Printer")) },
            text = {
                Column {
                    pairedPrinters.forEachIndexed { index, printer ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                savePrinter(context, printer)
                                savedPrinter = printer
                                showPrinterPicker = false
                                doPrint(printer)
                            }.padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(printer.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(printer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (savedPrinter?.address == printer.address) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(SpaceS))
                    Text(
                        "Pasangkan printer dulu di Pengaturan Bluetooth HP kalau belum muncul di daftar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterPicker = false }) { Text(tr("Batal", "Cancel")) }
            }
        )
    }
}

// ---------- Riwayat Penjualan ----------


@Composable
fun RiwayatScreen() {
    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    // R10 (audit 2026-11-15): expanded + salesLimit + filterBayar selamat
    // dari rotasi (Int?/Int/String semuanya saveable).
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    // Filter cara bayar (H6): "Semua" tampilkan semua; lainnya samakan
    // paymentMethod tanpa peka huruf (audit LOW: "tunai" kecil pun Tunai).
    var filterBayar by rememberSaveable { mutableStateOf("Semua") }
    var salesLimit by rememberSaveable { mutableIntStateOf(50) }
    // MB1: total penjualan bulan ini per cara bayar (dimuat bersama daftar).
    var monthByMethod by remember { mutableStateOf(mapOf("Tunai" to 0L, "QRIS" to 0L, "Transfer" to 0L)) }
    // Pesan kegagalan muat (S11, audit 2026-11-15): tanpa ini, kegagalan
    // muat riwayat diam-diam menampilkan daftar kosong.
    var loadError by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(salesLimit, resumeKey) {
        // R8 (audit 2026-11-15): muat limit+1 baris; baris ekstra hanya
        // penanda "ada lagi", tidak ditampilkan (lihat salesTampilBatas).
        when (val r = repo.listSales(salesLimit + 1)) {
            is ApiResult.Success -> {
                sales = r.data.sales
                loadError = null
            }
            is ApiResult.Error -> loadError = r.message
            is ApiResult.Loading -> Unit
        }
        // MB1: ringkasan bulan ini dimuat ulang bersama daftar (ON_RESUME).
        when (val m = repo.getMonthSalesByMethod()) {
            is ApiResult.Success -> monthByMethod = m.data
            else -> Unit
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        KartuHeaderPremium(tr("Riwayat", "History"), Icons.Default.ReceiptLong)
        Spacer(Modifier.height(SpaceL))
        DemoBanner()
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Semua" to tr("Semua", "All"), "Tunai" to tr("Tunai", "Cash"), "QRIS" to "QRIS", "Transfer" to tr("Transfer", "Transfer")).forEach { (k, l) ->
                FilterChip(selected = filterBayar == k, onClick = { expanded = null; filterBayar = k }, label = { Text(l) })
            }
        }
        Spacer(Modifier.height(SpaceL))
        // MB1 (pilihan Achmad 2026-11-16): ringkasan total penjualan BULAN
        // INI per cara bayar — tiga kotak senada 14dp surfaceContainerHigh
        // (angka penuh 12sp bold + Ellipsis, pola perbaikan N1).
        Text(tr("Bulan ini", "This month"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(SpaceXS))
        Row(horizontalArrangement = Arrangement.spacedBy(SpaceS), modifier = Modifier.fillMaxWidth()) {
            listOf("Tunai" to tr("Tunai", "Cash"), "QRIS" to "QRIS", "Transfer" to tr("Transfer", "Transfer")).forEach { (k, l) ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                        Text(l, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatIDR(monthByMethod[k] ?: 0L), style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(SpaceL))
        // S11: kartu galat muat + tombol coba lagi (pola Beranda S10).
        if (loadError != null) {
            ExpressiveCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Gagal memuat riwayat penjualan.", "Failed to load the sales history."), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(SpaceS))
                    Text(loadError ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(SpaceS))
                    Button(onClick = { loadError = null; resumeKey++ }) { Text(tr("Coba lagi", "Try again")) }
                }
            }
            Spacer(Modifier.height(SpaceL))
        }
        // H6: saring dulu menurut cara bayar; hitungan "N transaksi • Rp X"
        // di header hari ikut tersaring karena dihitung dari daySales.
        // Saring+kelompokkan HANYA dihitung ulang saat data/filter/limit berubah.
        val salesTampil by remember {
            derivedStateOf { if (filterBayar == "Semua") sales else sales.filter { it.paymentMethod.equals(filterBayar, ignoreCase = true) } }
        }
        // R8+R29 (audit 2026-11-15): tampilkan hanya `salesLimit` pertama
        // SETELAH filter; hasMore dihitung dari daftar tersaring (melebihi
        // limit = pasti ada lagi) ATAU dari jendela muat yang belum habis
        // (baris terambil > limit) supaya filter sempit pun bisa memuat
        // halaman berikut — aproksimasi, karena repo tidak punya paging
        // terfilter; tombol memperbaiki diri saat jendela habis.
        val salesTampilBatas by remember { derivedStateOf { salesTampil.take(salesLimit) } }
        val hasMore by remember { derivedStateOf { salesTampil.size > salesLimit || sales.size > salesLimit } }
        if (sales.isEmpty()) Text(tr("Belum ada penjualan tercatat.", "No sales recorded yet."))
        else if (salesTampil.isEmpty()) Text(tr("Belum ada penjualan $filterBayar.", "No $filterBayar sales yet."))
        // Kelompokkan penjualan per hari (WIB): header tanggal + total omzet
        // harian, supaya riwayat lebih mudah dipindai daripada daftar datar.
        // dayKeyID/parseISODate sudah dipakai layar Keuangan untuk batas WIB.
        val jakartaTz = TimeZone.getTimeZone("Asia/Jakarta")
        val todayCal = Calendar.getInstance(jakartaTz).apply {
            time = Date()
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayKey = dayKeyID(todayCal.time)
        val yesterdayKey = dayKeyID((todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
        // Urutkan grup dari hari terbaru (pakai createdAt terbesar di grup).
        val sortedGroups by remember {
            derivedStateOf {
                salesTampilBatas.groupBy { s -> parseISODate(s.createdAt)?.let { dayKeyID(it) } ?: "" }
                    .entries.sortedByDescending { (_, list) ->
                        list.maxOfOrNull { parseISODate(it.createdAt)?.time ?: 0L } ?: 0L
                    }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceM)) {
            var offset = 0
            sortedGroups.forEach { (dayKey, daySales) ->
                val groupOffset = offset
                offset += daySales.size
                // Header tanggal: label hari + jumlah transaksi & total omzet hari itu.
                item(key = "header-$dayKey") {
                    val dayDate = daySales.firstNotNullOfOrNull { parseISODate(it.createdAt) }
                    val label = when (dayKey) {
                        todayKey -> tr("Hari ini", "Today")
                        yesterdayKey -> tr("Kemarin", "Yesterday")
                        else -> dayDate?.let {
                            SimpleDateFormat("d MMM yyyy", if (AppLang.code == "en") Locale.ENGLISH else Locale("id", "ID")).apply { timeZone = jakartaTz }.format(it)
                        } ?: tr("Tanpa tanggal", "No date")
                    }
                    val dayTotal = daySales.sumOf { it.total }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${daySales.size} transaksi • ${formatIDR(dayTotal)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // R9 (audit 2026-11-15): key per penjualan supaya state
                // baris (mis. pemilih printer) tidak menempel ke baris lain
                // saat daftar berubah; R17: GraphicsLayer per kartu supaya
                // bitmap struk yang dibagikan/dicetak selalu milik kartu ini.
                itemsIndexed(daySales, key = { _, s -> s.id }) { index, s ->
                    val receiptLayer = rememberGraphicsLayer()
                    val globalIndex = groupOffset + index
                    val isOpen = expanded == s.id
                    
                    // Kartu H1 KOTAK SENADA (pilihan Achmad 2026-11-16):
                    // 20dp + surfaceContainerHigh, lingkaran ikon biru.
                    ExpressiveCard(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clickable { expanded = if (isOpen) null else s.id }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                    Icon(Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(10.dp).size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.invoiceNo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${formatDateTimeID(s.createdAt)} • ${s.items.size} item",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Chip cara bayar di tengah antara nomor invoice
                                // dan nominal, seperti Faktur PBF — hanya untuk
                                // QRIS dan Transfer; Tunai tanpa chip (permintaan
                                // Achmad 2026-11-01). Perbandingan tidak peka
                                // huruf besar/kecil (audit LOW): "tunai" kecil
                                // pun tetap tanpa chip.
                                if (!s.paymentMethod.equals("Tunai", ignoreCase = true)) {
                                    Spacer(Modifier.width(8.dp))
                                    SalePaymentChip(s.paymentMethod)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(formatIDR(s.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Icon(
                                    if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // B2 Geser + fade (pilihan Achmad 2026-11-14):
                            // pratinjau 2 item mengembang/menyusut + fade 250ms,
                            // disamakan dengan detail struk: kedua blok
                            // beranimasi bersamaan, jadi durasi + easing harus
                            // sama supaya tinggi kartu bergerak pada satu kurva
                            // (sebelumnya 150ms linear vs 250ms FastOutSlowIn —
                            // kartu sempat menciut dulu saat dibuka).
                            AnimatedVisibility(
                                visible = !isOpen && s.items.isNotEmpty(),
                                enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(250)),
                                exit = shrinkVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(250))
                            ) {
                                Column {
                                    // Pratinjau isi (desain R2, pilihan Achmad
                                    // 2026-11-04): dua item pertama tampil tanpa
                                    // membuka kartu, sejajar kolom invoice; item
                                    // selebihnya diringkas "+N lainnya". Saat
                                    // kartu terbuka pratinjau hilang — rincian
                                    // lengkap sudah ditampilkan InvoiceDetailCard.
                                    Spacer(Modifier.height(SpaceS))
                                    Column(Modifier.padding(start = 52.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        s.items.take(2).forEach { item ->
                                            Text(
                                                "${item.medicineName} ×${item.qty}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (s.items.size > 2) {
                                            Text(
                                                tr("+${s.items.size - 2} lainnya", "+${s.items.size - 2} more"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            // B2 Geser + fade (pilihan Achmad 2026-11-14):
                            // detail struk meluncur turun dari header +
                            // fade 250ms saat dibuka, sebaliknya saat ditutup.
                            AnimatedVisibility(
                                visible = isOpen,
                                enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(250)),
                                exit = shrinkVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(250))
                            ) {
                                Column {
                                    Spacer(Modifier.height(SpaceL))
                                    InvoiceDetailCard(sale = s, graphicsLayer = receiptLayer)
                                    Spacer(Modifier.height(SpaceM))
                                    ReceiptActionsRow(graphicsLayer = receiptLayer, invoiceNo = s.invoiceNo, sale = s)
                                }
                            }
                        }
                    }
                    
                }
            }
        }
        if (hasMore) {
            Spacer(Modifier.height(SpaceM))
            OutlinedButton(
                onClick = { salesLimit += 50 },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(tr("Tampilkan lebih banyak", "Show more")) }
        }
    }
}






// ---------- Backup & Restore ----------
