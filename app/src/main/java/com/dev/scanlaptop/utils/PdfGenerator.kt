package com.dev.scanlaptop.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.dev.scanlaptop.data.HistoryLog
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfGenerator {

    fun generateReport(
        context: Context,
        historyList: List<HistoryLog>,
        rentangWaktu: String,
        dateTitle: String
    ): Uri? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create() // A4 Landscape: 842 x 595
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas

        val paint = Paint()
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = Color.parseColor("#1A237E")
            textAlign = Paint.Align.CENTER
        }
        
        val subtitlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 12f
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }

        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            color = Color.WHITE
        }

        val textPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 10f
            color = Color.BLACK
        }

        // Draw Title
        canvas.drawText("Laporan Aktivitas Laptop - $rentangWaktu", pageInfo.pageWidth / 2f, 50f, titlePaint)
        canvas.drawText("Tanggal: $dateTitle", pageInfo.pageWidth / 2f, 70f, subtitlePaint)
        canvas.drawText("Total Aktivitas: ${historyList.size}", pageInfo.pageWidth / 2f, 90f, subtitlePaint)

        // Draw Table Header
        var yPos = 130f
        val startX = 40f
        
        // Perangkat ditambah 30f, Petugas dikurangi 30f
        val colWidths = floatArrayOf(140f, 152f, 140f, 100f, 100f, 130f)
        val headers = arrayOf("PEMILIK", "DIVISI/INSTANSI", "PERANGKAT", "MASUK", "KELUAR", "PETUGAS")

        // Draw Header Background
        paint.color = Color.parseColor("#1A237E")
        canvas.drawRect(startX, yPos - 15f, pageInfo.pageWidth - startX, yPos + 10f, paint)

        // Draw Header Texts
        var currentX = startX + 5f
        for (i in headers.indices) {
            canvas.drawText(headers[i], currentX, yPos, headerPaint)
            currentX += colWidths[i]
        }

        yPos += 25f
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1f

        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("id", "ID"))
        val textPaintText = android.text.TextPaint(textPaint)

        for (log in historyList) {
            if (yPos > pageInfo.pageHeight - 50f) {
                // New Page
                pdfDocument.finishPage(currentPage)
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                yPos = 50f
            }

            val formattedDate = try {
                ZonedDateTime.parse(log.created_at).format(dateFormatter)
            } catch (e: Exception) {
                if (log.created_at.length >= 16) {
                    log.created_at.take(16).replace("T", " ")
                } else {
                    log.created_at
                }
            }
            
            val pemilik = log.registrasi_laptop?.nama_pengguna ?: "-"
            val divisi = log.registrasi_laptop?.instansi_divisi ?: "-"
            val petugas = log.users?.nama_lengkap ?: log.petugas_npp ?: "-"
            
            val masuk = if (log.status_io == "IN") formattedDate else "-"
            val keluar = if (log.status_io == "OUT") formattedDate else "-"
            
            val devices = log.details.joinToString("\n") { 
                "${it.merk} ${it.tipe}"
            }.ifEmpty { "-" }
            
            val deviceLines = devices.split("\n")

            // Draw line divider
            canvas.drawLine(startX, yPos - 15f, pageInfo.pageWidth - startX, yPos - 15f, paint)

            val rowHeight = (deviceLines.size * 15f) + 10f

            // Draw content
            var textX = startX + 5f
            
            // Col 1: Pemilik
            val safePemilik = android.text.TextUtils.ellipsize(pemilik, textPaintText, colWidths[0] - 10f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(safePemilik, textX, yPos, textPaint)
            textX += colWidths[0]
            
            // Col 2: Divisi
            val safeDivisi = android.text.TextUtils.ellipsize(divisi, textPaintText, colWidths[1] - 10f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(safeDivisi, textX, yPos, textPaint)
            textX += colWidths[1]
            
            // Col 3: Perangkat
            var deviceY = yPos
            for (line in deviceLines) {
                val safeLine = android.text.TextUtils.ellipsize(line, textPaintText, colWidths[2] - 10f, android.text.TextUtils.TruncateAt.END).toString()
                canvas.drawText(safeLine, textX, deviceY, textPaint)
                deviceY += 15f
            }
            textX += colWidths[2]

            // Col 4: Masuk
            val safeMasuk = android.text.TextUtils.ellipsize(masuk, textPaintText, colWidths[3] - 10f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(safeMasuk, textX, yPos, textPaint)
            textX += colWidths[3]
            
            // Col 5: Keluar
            val safeKeluar = android.text.TextUtils.ellipsize(keluar, textPaintText, colWidths[4] - 10f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(safeKeluar, textX, yPos, textPaint)
            textX += colWidths[4]

            // Col 6: Petugas
            val safePetugas = android.text.TextUtils.ellipsize(petugas, textPaintText, colWidths[5] - 10f, android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(safePetugas, textX, yPos, textPaint)

            yPos += rowHeight
        }

        // Draw last line divider
        canvas.drawLine(startX, yPos - 15f, pageInfo.pageWidth - startX, yPos - 15f, paint)

        pdfDocument.finishPage(currentPage)

        // Save PDF to cache
        return try {
            val reportDir = File(context.cacheDir, "reports")
            if (!reportDir.exists()) {
                reportDir.mkdirs()
            }
            
            val safeDateTitle = dateTitle.replace("/", "-").replace(" ", "_")
            val fileName = "Laporan_${rentangWaktu}_${safeDateTitle}.pdf"
            val file = File(reportDir, fileName)
            
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
