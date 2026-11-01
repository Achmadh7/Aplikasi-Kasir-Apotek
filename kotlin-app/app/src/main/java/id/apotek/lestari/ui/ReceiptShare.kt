package id.apotek.lestari.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
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
//
// Perbaikan FC saat Cetak (2026-10-27): adapter cetak sebelumnya memakai
// pdf.startPage(1) padahal nomor halaman dimulai dari 0 dan dokumen ini
// hanya 1 halaman (page 0), dan semua pengecualian di callback cetak tidak
// ditangkap sehingga aplikasi langsung force close. Sekarang halaman dimulai
// dari 0, bitmap kosong ditolak dengan onWriteFailed, dan semua pengecualian
// di onLayout/onWrite dilaporkan ke sistem lewat callback (bukan crash).

/**
 * Simpan bitmap struk sebagai PNG di cache lalu bagikan lewat share sheet.
 * Bitmap yang belum siap (recycle/kosong) ditolak dengan pesan jelas;
 * file PNG lama di cache dibersihkan dulu supaya tidak menumpuk.
 */
fun shareReceiptBitmap(context: Context, bitmap: Bitmap, invoiceNo: String) {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
        throw IllegalStateException("Struk belum siap dibagikan")
    }
    try {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        // Bersihkan PNG lama sebelum menulis yang baru (cache/receipts
        // menumpuk satu file per struk yang dibagikan).
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        // Nama berkas harus dibersihkan: format nomor invoice sejak
        // 2026-11-01 adalah "INV - dd/MM/yy" yang mengandung garis miring;
        // "/" di nama berkas dianggap pemisah folder sehingga penulisan
        // PNG gagal total ("Gagal menyiapkan struk"). Karakter di luar
        // huruf/angka/titik/strip diganti "-", strip beruntun dirapikan.
        val safeName = invoiceNo.replace(Regex("[^A-Za-z0-9._-]"), "-").replace(Regex("-+"), "-")
        val file = File(dir, "Struk-$safeName.png")
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IOException("Gagal menulis gambar struk")
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Struk $invoiceNo — Apotek Lestari")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Chooser bisa dipanggil dari konteks non-Activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan struk"))
    } catch (e: IllegalStateException) {
        throw e
    } catch (e: IOException) {
        throw IllegalStateException("Tidak bisa menyimpan struk: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("Tidak bisa membagikan struk: ${e.message}", e)
    }
}

/** Cetak bitmap struk lewat PrintManager (printer atau "Simpan sebagai PDF"). */
fun printReceiptBitmap(context: Context, bitmap: Bitmap, invoiceNo: String) {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
        throw IllegalStateException("Struk belum siap dicetak")
    }
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        ?: throw IllegalStateException("Cetak tidak tersedia di HP ini")
    printManager.print("Struk $invoiceNo", ReceiptPrintAdapter(context, bitmap, "Struk $invoiceNo"), null)
}

private class ReceiptPrintAdapter(
    private val context: Context,
    private val bitmap: Bitmap,
    private val jobName: String
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null
    private var pageCount = 1

    /**
     * Jumlah halaman dari tinggi struk (skala pas-lebar, bukan pas-1-halaman).
     * Ukuran halaman dihitung dari media size atribut (points, 1/72 inci).
     */
    private fun computePageCount(attrs: PrintAttributes): Int {
        val media = attrs.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
        val pageW = (media.widthMils * 72f / 1000f).coerceAtLeast(1f)
        val pageH = (media.heightMils * 72f / 1000f).coerceAtLeast(1f)
        val scale = pageW / bitmap.width.toFloat()
        val scaledH = bitmap.height * scale
        return maxOf(1, kotlin.math.ceil(scaledH / pageH).toInt())
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        try {
            attributes = newAttributes
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            // Struk kosong (layer belum sempat direkam) tidak bisa dicetak.
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                callback?.onLayoutFailed("Struk belum siap dicetak")
                return
            }
            val attrs = newAttributes ?: oldAttributes
            if (attrs != null) pageCount = computePageCount(attrs)
            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                .setPageCount(pageCount)
                .build()
            callback?.onLayoutFinished(info, newAttributes != oldAttributes)
        } catch (e: Exception) {
            callback?.onLayoutFailed(e.message)
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            val attrs = attributes
            if (attrs == null) {
                callback?.onWriteFailed("Atribut cetak belum tersedia")
                return
            }
            // Struk kosong (layer belum sempat direkam) tidak bisa dicetak:
            // laporkan gagal daripada menghitung skala dari ukuran 0.
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                callback?.onWriteFailed("Struk belum siap dicetak")
                return
            }
            if (cancellationSignal?.isCanceled == true) {
                callback?.onWriteCancelled()
                return
            }
            val pdf = PrintedPdfDocument(context, attrs)
            try {
                // Struk panjang dibagi ke beberapa halaman (pas-lebar) supaya
                // teks tetap terbaca — bukan dikecilkan ke 1 halaman.
                val written = mutableListOf<PageRange>()
                for (range in pages) {
                    val start = range.start.coerceAtLeast(0)
                    val end = range.end.coerceAtMost(pageCount - 1)
                    if (start > end) continue
                    for (pageNo in start..end) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            return
                        }
                        val page = pdf.startPage(pageNo)
                        val pageWidth = page.canvas.width.toFloat()
                        val pageHeight = page.canvas.height.toFloat()
                        val scale = (pageWidth / bitmap.width.toFloat()).coerceAtLeast(0.01f)
                        val srcSliceH = (pageHeight / scale).toInt().coerceAtLeast(1)
                        val srcTop = pageNo * srcSliceH
                        val srcBottom = minOf(srcTop + srcSliceH, bitmap.height)
                        if (srcTop < bitmap.height) {
                            val src = Rect(0, srcTop, bitmap.width, srcBottom)
                            val dst = RectF(0f, 0f, pageWidth, (srcBottom - srcTop) * scale)
                            page.canvas.drawBitmap(bitmap, src, dst, null)
                        }
                        pdf.finishPage(page)
                    }
                    written.add(PageRange(start, end))
                }
                try {
                    FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
                    callback?.onWriteFinished(written.toTypedArray())
                } catch (e: IOException) {
                    callback?.onWriteFailed(e.message)
                }
            } finally {
                pdf.close()
            }
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}
