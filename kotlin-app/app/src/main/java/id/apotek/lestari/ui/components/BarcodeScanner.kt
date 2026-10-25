package id.apotek.lestari.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Native barcode scanner: CameraX Preview + ML Kit Barcode Scanning.
 * Handles CAMERA runtime permission, and always offers manual code
 * entry as fallback (also works with USB scanners that type + Enter).
 * Frames are processed on-device by ML Kit; nothing is uploaded.
 */
@Composable
fun BarcodeScanner(onDetected: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var manualCode by remember { mutableStateOf("") }
    var handled by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Scan Barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val scanner = BarcodeScanning.getClient()
                                val analysis = ImageAnalysis.Builder().build()
                                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                                    val media = proxy.image
                                    if (media != null && !handled) {
                                        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                        scanner.process(image).addOnSuccessListener { codes ->
                                            val code = codes.firstOrNull { it.rawValue != null }?.rawValue?.trim()
                                            if (!code.isNullOrEmpty() && !handled) { handled = true; onDetected(code) }
                                        }.addOnCompleteListener { proxy.close() }
                                    } else proxy.close()
                                }
                                try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) } catch (_: Exception) {}
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )
                    Text("Arahkan barcode ke kamera", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Izin kamera belum diberikan. Ketik kode barcode secara manual di bawah, atau gunakan scanner USB.")
                }
                OutlinedTextField(value = manualCode, onValueChange = { manualCode = it }, label = { Text("Kode barcode manual") }, placeholder = { Text("cth: 8991234567890") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { val c = manualCode.trim(); if (c.isNotEmpty()) onDetected(c) }, modifier = Modifier.fillMaxWidth()) { Text("Gunakan Kode") }
                Text("Format didukung: EAN-13, EAN-8, UPC, Code 128, QR — sama seperti versi web.", style = MaterialTheme.typography.bodySmall)
                @Suppress("unused") val formats = listOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_QR_CODE)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}
