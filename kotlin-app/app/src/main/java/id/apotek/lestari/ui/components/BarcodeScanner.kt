package id.apotek.lestari.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native barcode scanner: CameraX Preview + ML Kit Barcode Scanning.
 * Handles CAMERA runtime permission, and always offers manual code
 * entry as fallback (also works with USB scanners that type + Enter).
 * Frames are processed on-device by ML Kit; nothing is uploaded.
 *
 * Preview dilengkapi bingkai scan (kotak bersudut + overlay gelap di
 * luarnya) supaya jelas ke mana barcode diarahkan, dan tombol senter
 * untuk ruangan redup. Senter dimatikan & kamera dilepas saat dialog
 * ditutup.
 */
@Composable
fun BarcodeScanner(onDetected: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var manualCode by remember { mutableStateOf("") }
    // Penanda sekali-kirim: AtomicBoolean supaya aman dibaca/ditulis lintas
    // thread (analyzer ML Kit vs tombol manual) — onDetected hanya boleh
    // terpanggil satu kali.
    val handled = remember { AtomicBoolean(false) }
    var submitDone by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    // Executor analisis + client ML Kit dibuat sekali dan dilepas saat dialog
    // ditutup (sebelumnya dibuat tiap buka dialog dan tidak pernah dilepas).
    // Format dibatasi ke yang diklaim UI (EAN-13, EAN-8, UPC, Code 128, QR).
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE
            )
            .build()
        BarcodeScanning.getClient(options)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted }

    // Kirim kode sekali saja (scan kamera maupun input manual). Dipanggil di
    // main thread; compareAndSet mencegah double-callback bila scan dan
    // tombol manual balapan.
    fun submitCode(code: String) {
        val c = code.trim()
        if (c.isEmpty()) return
        if (handled.compareAndSet(false, true)) {
            submitDone = true
            onDetected(c)
        }
    }

    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    // Matikan senter, lepas kamera, tutup client ML Kit & executor saat
    // dialog ditutup.
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            runCatching { cameraControl?.enableTorch(false) }
            runCatching { cameraProvider?.unbindAll() }
            runCatching { barcodeScanner.close() }
            analysisExecutor.shutdown()
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Scan Barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (hasPermission) {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                providerFuture.addListener({
                                    if (disposed) return@addListener
                                    val provider = try {
                                        providerFuture.get()
                                    } catch (e: Exception) {
                                        // get() bisa melempar ExecutionException /
                                        // InterruptedException — tampilkan error,
                                        // jangan crash dan jangan preview hitam.
                                        cameraError = "Kamera tidak dapat dibuka: ${e.message ?: "gagal memuat kamera"}"
                                        return@addListener
                                    }
                                    cameraProvider = provider
                                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                    val analysis = ImageAnalysis.Builder().build()
                                    analysis.setAnalyzer(analysisExecutor) { proxy ->
                                        val media = proxy.image
                                        if (media == null || handled.get()) {
                                            proxy.close()
                                            return@setAnalyzer
                                        }
                                        try {
                                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                            barcodeScanner.process(image)
                                                .addOnSuccessListener { codes ->
                                                    val code = codes.firstOrNull { it.rawValue != null }?.rawValue?.trim()
                                                    if (!code.isNullOrEmpty()) submitCode(code)
                                                }
                                                .addOnFailureListener {
                                                    // Scan gagal (mis. model ML Kit belum
                                                    // ter-download dan HP offline): beri tahu
                                                    // user sekali, jangan diam-diam.
                                                    if (cameraError == null) {
                                                        cameraError = "Scan gagal: model barcode belum tersedia (perlu internet sekali untuk mengunduh)"
                                                    }
                                                }
                                                .addOnCompleteListener { proxy.close() }
                                        } catch (_: Exception) {
                                            // fromMediaImage/process gagal sebelum
                                            // onComplete terdaftar — tutup proxy di
                                            // sini supaya pipeline tidak macet.
                                            proxy.close()
                                        }
                                    }
                                    try {
                                        provider.unbindAll()
                                        val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                        cameraControl = camera.cameraControl
                                        hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                                    } catch (e: Exception) {
                                        // Kamera gagal di-bind (dipakai aplikasi
                                        // lain / tanpa kamera belakang): tampilkan
                                        // pesan, jangan preview hitam tanpa info.
                                        cameraError = "Kamera tidak dapat dibuka: ${e.message ?: "gagal mengikat kamera"}"
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        // Bingkai scan: overlay gelap di luar kotak + sudut putih.
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val frameW = size.width * 0.80f
                            val frameH = (frameW * 0.55f).coerceAtMost(size.height * 0.62f)
                            val left = (size.width - frameW) / 2f
                            val top = (size.height - frameH) / 2f
                            val right = left + frameW
                            val bottom = top + frameH
                            val dim = Color.Black.copy(alpha = 0.55f)
                            drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, top))
                            drawRect(dim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                            drawRect(dim, topLeft = Offset(0f, top), size = Size(left, frameH))
                            drawRect(dim, topLeft = Offset(right, top), size = Size(size.width - right, frameH))
                            val cornerLen = 28.dp.toPx()
                            val stroke = 4.dp.toPx()
                            val c = Color.White
                            val cap = StrokeCap.Round
                            drawLine(c, Offset(left, top + cornerLen), Offset(left, top), strokeWidth = stroke, cap = cap)
                            drawLine(c, Offset(left, top), Offset(left + cornerLen, top), strokeWidth = stroke, cap = cap)
                            drawLine(c, Offset(right - cornerLen, top), Offset(right, top), strokeWidth = stroke, cap = cap)
                            drawLine(c, Offset(right, top), Offset(right, top + cornerLen), strokeWidth = stroke, cap = cap)
                            drawLine(c, Offset(left, bottom - cornerLen), Offset(left, bottom), strokeWidth = stroke, cap = cap)
                            drawLine(c, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeWidth = stroke, cap = cap)
                            drawLine(c, Offset(right, bottom - cornerLen), Offset(right, bottom), strokeWidth = stroke, cap = cap)
                            drawLine(c, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeWidth = stroke, cap = cap)
                        }
                        // Tombol senter (hanya jika HP punya flash).
                        if (hasFlashUnit) {
                            IconButton(
                                onClick = { torchOn = !torchOn; cameraControl?.enableTorch(torchOn) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                    contentDescription = if (torchOn) "Matikan senter" else "Nyalakan senter",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    // Error kamera/scan (bila ada) menggantikan teks petunjuk.
                    cameraError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    } ?: Text("Arahkan barcode ke kamera", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Izin kamera belum diberikan. Ketik kode barcode secara manual di bawah, atau gunakan scanner USB.")
                }
                OutlinedTextField(value = manualCode, onValueChange = { manualCode = it }, label = { Text("Kode barcode manual") }, placeholder = { Text("cth: 8991234567890") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { submitCode(manualCode) }, enabled = !submitDone, modifier = Modifier.fillMaxWidth()) { Text("Gunakan Kode") }
                Text("Format didukung: EAN-13, EAN-8, UPC, Code 128, QR — sama seperti versi web.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}
