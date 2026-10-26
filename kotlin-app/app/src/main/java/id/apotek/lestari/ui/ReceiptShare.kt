package id.apotek.lestari.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// ---------- Bagikan & cetak struk ----------
// Struk ditangkap dari kartu invoice (GraphicsLayer) menjadi bitmap, lalu:
// - dibagikan sebagai gambar PNG lewat share sheet (WhatsApp, dll), atau
// - dicetak lewat PrintManager (printer, atau "Simpan sebagai PDF").

/** Simpan bitmap struk sebagai PNG di cache lalu bagikan lewat share sheet. */
fun shareReceiptBitmap(context: Context, bitmap: Bitmap, invoiceNo: String) {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "Struk-$invoiceNo.png")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Struk $invoiceNo — Apotek Lestari")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan struk"))
}

/** Cetak bitmap struk lewat PrintManager (printer atau "Simpan sebagai PDF"). */
fun printReceiptBitmap(context: Context, bitmap: Bitmap, invoiceNo: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print("Struk $invoiceNo", ReceiptPrintAdapter(context, bitmap, "Struk $invoiceNo"), null)
}

private class ReceiptPrintAdapter(
    private val context: Context,
    private val bitmap: Bitmap,
    private val jobName: String
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        attributes = newAttributes
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
            .setPageCount(1)
            .build()
        callback?.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        val attrs = attributes
        if (attrs == null) {
            callback?.onWriteFailed("Atribut cetak belum tersedia")
            return
        }
        val pdf = PrintedPdfDocument(context, attrs)
        val page = pdf.startPage(1)
        val pageWidth = page.canvas.width.toFloat()
        val pageHeight = page.canvas.height.toFloat()
        val scale = minOf(pageWidth / bitmap.width, pageHeight / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        page.canvas.drawBitmap(bitmap, null, RectF((pageWidth - width) / 2f, 0f, (pageWidth + width) / 2f, height), null)
        pdf.finishPage(page)
        try {
            pdf.writeTo(FileOutputStream(destination.fileDescriptor))
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback?.onWriteFailed(e.message)
        } finally {
            pdf.close()
        }
    }
}
