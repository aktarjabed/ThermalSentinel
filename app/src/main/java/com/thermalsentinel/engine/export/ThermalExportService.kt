package com.thermalsentinel.engine.export

import android.content.Context
import android.content.Intent
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
    val truncatedToRetention: Boolean
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

    /**
     * Share sheet for a finished export. The FileProvider authority is derived
     * from the application id so a debug build cannot hand out the release app's
     * files.
     */
    fun shareIntent(result: ExportResult): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.engine.files", result.file)
        val windowText = ThermalFormatting.duration(result.windowMillis)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_share_title))
            putExtra(
                Intent.EXTRA_TEXT,
                "${result.rowCount} samples covering the last $windowText. " +
                    "Empty fields mean the platform did not report that value."
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
    }
}
