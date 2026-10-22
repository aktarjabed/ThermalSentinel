package com.thermalsentinel.engine.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.thermalsentinel.R
import com.thermalsentinel.engine.data.ThermalHistoryRepository
import com.thermalsentinel.engine.domain.ThermalFormatting
import com.thermalsentinel.engine.monitoring.RetentionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A finished export. */
data class ExportResult(
    val file: File,
    val rowCount: Int,
    /** The window actually exported, which can be shorter than the requested one. */
    val windowMillis: Long,
    /**
     * True when the request asked for more than raw retention can serve. The
     * caller must say so rather than presenting a shortened window as complete.
     */
    val truncatedToRetention: Boolean,
    /** MIME type is carried with the artifact so the chooser receives the correct type. */
    val mimeType: String = ThermalExportService.MIME_TYPE
)

/** Export failures are surfaced to the user, never logged and forgotten. */
sealed interface ExportOutcome {
    data class Success(val result: ExportResult) : ExportOutcome
    data class Failure(val message: String) : ExportOutcome
    data class Empty(val message: String) : ExportOutcome
}

/**
 * CSV export of local history.
 *
 * Only the sample table is exported, because it is the only table that is a
 * faithful record of measurements. Buckets are aggregates, and exporting an
 * hourly average as if it were a reading would make the file misleading.
 *
 * The export window is clamped to raw retention and the result says so, so a
 * request for 30 days never silently returns 7 days of data as though it were the
 * whole answer. Nothing is uploaded: the file goes to the app cache and the user
 * decides where it goes from the share sheet.
 */
class ThermalExportService(
    private val context: Context,
    private val history: ThermalHistoryRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun exportSamplesCsv(windowMillis: Long): ExportOutcome {
        val now = clock()
        val rawRetention = RetentionPolicy.RAW_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS
        val effectiveWindow = windowMillis.coerceAtMost(rawRetention)
        val from = now - effectiveWindow

        // The repository's read is a suspend Room query, but the entity-to-domain
        // mapping after it runs on the caller's dispatcher, and for a full export
        // that is tens of thousands of objects.
        val samples = withContext(Dispatchers.IO) { history.samplesSince(from) }
        if (samples.isEmpty()) {
            return ExportOutcome.Empty(
                "No samples are stored in the last ${ThermalFormatting.duration(effectiveWindow)}, so there is " +
                    "nothing to export yet."
            )
        }

        // Rendering and file IO run on the IO dispatcher. This is called from a
        // ViewModel scope, which is `Main.immediate`, and a full raw-retention
        // export is roughly twenty thousand rows: building the text and writing it
        // to storage on the main thread is an ANR waiting for a slow device, and
        // nothing here needs the UI thread until the result is published.
        val csv = withContext(Dispatchers.IO) { ThermalCsv.render(samples.map(CsvRow::from)) }

        return try {
            withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, DIRECTORY)
                if (directory.exists() || directory.mkdirs()) {
                    // Trim before writing, so the file that is about to be shared is
                    // never the one that gets collected.
                    pruneOldExports()
                    val label = ThermalFormatting.clock(now, pattern = "yyyyMMdd-HHmm")
                    val file = File(directory, "thermal-history-$label.csv")
                    file.writeText(csv)
                    ExportOutcome.Success(
                        ExportResult(
                            file = file,
                            rowCount = samples.size,
                            windowMillis = effectiveWindow,
                            truncatedToRetention = windowMillis > rawRetention
                        )
                    )
                } else {
                    ExportOutcome.Failure("The export directory could not be created.")
                }
            }
        } catch (io: IOException) {
            ExportOutcome.Failure("Writing the export failed: ${io.message ?: "unknown error"}")
        }
    }


    /** Export a paginated, human-readable PDF of retained raw samples. */
    suspend fun exportSamplesPdf(windowMillis: Long): ExportOutcome {
        val now = clock()
        val rawRetention = RetentionPolicy.RAW_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS
        val effectiveWindow = windowMillis.coerceAtMost(rawRetention)
        val samples = withContext(Dispatchers.IO) {
            history.samplesSince(now - effectiveWindow)
        }
        if (samples.isEmpty()) {
            return ExportOutcome.Empty(
                "No samples are stored in the last ${ThermalFormatting.duration(effectiveWindow)}, so there is nothing to export yet."
            )
        }

        return try {
            withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, DIRECTORY)
                if (!directory.exists() && !directory.mkdirs()) {
                    return@withContext ExportOutcome.Failure("The export directory could not be created.")
                }
                pruneOldExports()
                val label = ThermalFormatting.clock(now, pattern = "yyyyMMdd-HHmm")
                val file = File(directory, "thermal-history-$label.pdf")
                writePdf(file, samples.map(CsvRow::from), now, effectiveWindow)
                ExportOutcome.Success(
                    ExportResult(
                        file = file,
                        rowCount = samples.size,
                        windowMillis = effectiveWindow,
                        truncatedToRetention = windowMillis > rawRetention,
                        mimeType = PDF_MIME_TYPE
                    )
                )
            }
        } catch (io: IOException) {
            ExportOutcome.Failure("Writing the PDF report failed: ${io.message ?: "unknown error"}")
        } catch (failure: RuntimeException) {
            ExportOutcome.Failure("Creating the PDF report failed: ${failure.message ?: "unknown error"}")
        }
    }

    private fun writePdf(file: File, rows: List<CsvRow>, generatedAt: Long, windowMillis: Long) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val left = 28f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val rowsPerPage = 38
        val pageCount = maxOf(1, (rows.size + rowsPerPage - 1) / rowsPerPage)
        try {
            for (pageNumber in 0 until pageCount) {
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create()
                )
                val canvas = page.canvas
                canvas.drawText("Thermal Sentinel — Thermal History", left, 34f, titlePaint)
                canvas.drawText("Generated: ${ThermalCsv.isoTimestamp(generatedAt)}", left, 50f, paint)
                canvas.drawText(
                    "Window: ${ThermalFormatting.duration(windowMillis)} · Raw samples: ${rows.size}",
                    left, 63f, paint
                )
                canvas.drawText("Missing readings are shown as —; battery temperature is not device temperature.", left, 76f, paint)
                canvas.drawText("Timestamp (local offset)", left, 98f, headerPaint)
                canvas.drawText("Battery °C", 205f, 98f, headerPaint)
                canvas.drawText("Thermal status", 275f, 98f, headerPaint)
                canvas.drawText("Headroom", 375f, 98f, headerPaint)
                canvas.drawText("Battery %", 455f, 98f, headerPaint)
                canvas.drawText("Charging", 510f, 98f, headerPaint)

                val start = pageNumber * rowsPerPage
                val end = minOf(rows.size, start + rowsPerPage)
                var y = 114f
                for (index in start until end) {
                    val row = rows[index]
                    canvas.drawText(ThermalCsv.isoTimestamp(row.timestampMillis), left, y, paint)
                    canvas.drawText(row.batteryTemperatureC?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—", 205f, y, paint)
                    canvas.drawText((row.androidThermalStatus ?: "—").take(16), 275f, y, paint)
                    canvas.drawText(row.thermalHeadroom?.let { String.format(java.util.Locale.ROOT, "%.3f", it) } ?: "—", 375f, y, paint)
                    canvas.drawText(row.batteryPercent?.toString() ?: "—", 455f, y, paint)
                    canvas.drawText((row.chargingStatus ?: "—").take(12), 510f, y, paint)
                    y += 16f
                }
                canvas.drawText("Page ${pageNumber + 1} of $pageCount", pageWidth - 75f, pageHeight - 18f, paint)
                page.finishPage()
            }
            file.outputStream().buffered().use(document::writeTo)
        } finally {
            document.close()
        }
    }

    /** Export the same retained raw sample window as structured, versioned JSON. */
    suspend fun exportSamplesJson(windowMillis: Long): ExportOutcome {
        val now = clock()
        val rawRetention = RetentionPolicy.RAW_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS
        val effectiveWindow = windowMillis.coerceAtMost(rawRetention)
        val samples = withContext(Dispatchers.IO) {
            history.samplesSince(now - effectiveWindow)
        }
        if (samples.isEmpty()) {
            return ExportOutcome.Empty(
                "No samples are stored in the last ${ThermalFormatting.duration(effectiveWindow)}, so there is nothing to export yet."
            )
        }

        return try {
            withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, DIRECTORY)
                if (!directory.exists() && !directory.mkdirs()) {
                    return@withContext ExportOutcome.Failure("The export directory could not be created.")
                }
                pruneOldExports()
                val label = ThermalFormatting.clock(now, pattern = "yyyyMMdd-HHmm")
                val file = File(directory, "thermal-history-$label.json")
                file.writeText(ThermalJson.render(samples.map(CsvRow::from), generatedAtMillis = now))
                ExportOutcome.Success(
                    ExportResult(
                        file = file,
                        rowCount = samples.size,
                        windowMillis = effectiveWindow,
                        truncatedToRetention = windowMillis > rawRetention,
                        mimeType = JSON_MIME_TYPE
                    )
                )
            }
        } catch (io: IOException) {
            ExportOutcome.Failure("Writing the JSON export failed: ${io.message ?: "unknown error"}")
        }
    }

    /**
     * Share sheet for a finished export. The FileProvider authority is derived
     * from the application id so a debug build cannot hand out the release app's
     * files.
     */
    fun shareIntent(result: ExportResult): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.engine.files", result.file)
        val windowText = ThermalFormatting.duration(result.windowMillis)
        return Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(
                    when (result.mimeType) {
                        JSON_MIME_TYPE -> R.string.export_json_subject
                        PDF_MIME_TYPE -> R.string.export_pdf_subject
                        else -> R.string.export_share_title
                    }
                )
            )
            putExtra(
                Intent.EXTRA_TEXT,
                "${result.rowCount} samples covering the last $windowText. " +
                    when (result.mimeType) {
                        JSON_MIME_TYPE -> "Missing measurements are represented as JSON null."
                        PDF_MIME_TYPE -> "Missing measurements are shown as dashes; only raw samples are included."
                        else -> "Empty fields mean the platform did not report that value."
                    }
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Keeps the export directory bounded, and returns how many files were removed.
     *
     * The newest [keep] files survive: a share sheet that has only just handed a
     * file to another app must not have it deleted out from under the reader. A
     * device that exports once a day therefore holds a small, fixed number of
     * files instead of one permanent file per export, because the timestamp in the
     * name makes every export a new file rather than an overwrite.
     */
    fun pruneOldExports(keep: Int = 1): Int {
        val directory = File(context.cacheDir, DIRECTORY)
        val stale = directory.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep.coerceAtLeast(0))
            ?: return 0
        var removed = 0
        stale.forEach { file -> if (file.delete()) removed++ }
        return removed
    }

    companion object {
        const val DIRECTORY = "engine-exports"
        const val MIME_TYPE = "text/csv"
        const val JSON_MIME_TYPE = "application/json"
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
