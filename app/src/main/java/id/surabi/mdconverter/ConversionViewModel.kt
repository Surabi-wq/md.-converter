package id.surabi.mdconverter

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Keeps batch state across Activity recreation and persists completed Markdown results
 * outside cacheDir. Results are read from disk only when the user previews or merges them.
 */
internal class ConversionViewModel : ViewModel() {
    val files = mutableStateListOf<ConvertedFile>()
    val selectedUris = mutableStateListOf<Uri>()

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("Pilih dokumen atau media untuk mulai.")
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var completed by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var historyReady by mutableStateOf(false)
        private set

    private var cancelRequested = false
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext

        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { loadHistory(appContext) }
            files.addAll(restored)
            val activeFiles = files.mapNotNull { it.cacheFilePath }.toSet()
            withContext(Dispatchers.IO) { removeOrphanedFiles(appContext, activeFiles) }
            enforceHistoryLimits()
            persistHistory(appContext)
            historyReady = true
            if (restored.isNotEmpty()) {
                message = "Riwayat lokal dipulihkan: ${restored.size} hasil. Hasil disimpan di perangkat ini."
            }
        }
    }

    fun start(context: Context, uris: List<Uri>, outputFolder: Uri?) {
        if (!historyReady || busy || uris.isEmpty()) return
        val appContext = context.applicationContext
        cancelRequested = false

        viewModelScope.launch {
            busy = true
            completed = 0
            total = uris.size
            progress = 0f
            message = "Menyiapkan ${uris.size} file…"
            var savedCount = 0
            var wasCancelled = false
            var historyTrimmed = false
            val saveErrors = mutableListOf<String>()
            val batchResults = mutableListOf<ConvertedFile>()
            var nextIndex = 0

            try {
                for ((index, uri) in uris.withIndex()) {
                    if (cancelRequested) {
                        selectedUris.addAll(uris.drop(index))
                        wasCancelled = true
                        break
                    }

                    val converted = withContext(Dispatchers.IO) {
                        val name = runCatching { DocumentStorage.queryName(appContext, uri) }
                            .getOrDefault("dokumen")
                        try {
                            val result = ConverterEngine.convert(appContext, uri, name)
                            val stored = writeHistoryContent(appContext, result.markdown)
                            ConvertedFile(
                                id = System.nanoTime(),
                                name = name,
                                format = result.format,
                                markdown = if (stored == null) result.markdown else "",
                                warnings = if (stored == null) {
                                    result.warnings + "Hasil belum dapat disimpan ke riwayat lokal; ekspor file ini sebelum menutup aplikasi."
                                } else result.warnings,
                                error = null,
                                cacheFilePath = stored
                            )
                        } catch (error: Exception) {
                            ConvertedFile(
                                id = System.nanoTime(),
                                name = name,
                                format = name.substringAfterLast('.', "unknown"),
                                markdown = "",
                                warnings = emptyList(),
                                error = error.message ?: "Konversi gagal."
                            )
                        }
                    }

                    files.add(converted)
                    batchResults.add(converted)
                    completed++
                    nextIndex = index + 1
                    progress = completed.toFloat() / total.coerceAtLeast(1)
                    message = "Memproses file $completed dari $total…"

                    if (converted.error == null && outputFolder != null) {
                        try {
                            withContext(Dispatchers.IO) {
                                val path = converted.cacheFilePath
                                if (path != null) {
                                    DocumentStorage.saveCachedFile(
                                        appContext,
                                        outputFolder,
                                        MarkdownFileNames.fromSource(converted.name),
                                        File(path)
                                    )
                                } else {
                                    DocumentStorage.saveToFolder(
                                        appContext,
                                        outputFolder,
                                        MarkdownFileNames.fromSource(converted.name),
                                        converted.markdown
                                    )
                                }
                            }
                            savedCount++
                        } catch (error: Exception) {
                            saveErrors.add(converted.name + ": " + (error.message ?: "gagal disimpan"))
                        }
                    }

                    historyTrimmed = enforceHistoryLimits() || historyTrimmed
                    persistHistory(appContext)

                    if (cancelRequested) {
                        if (index + 1 < uris.size) selectedUris.addAll(uris.drop(index + 1))
                        wasCancelled = true
                        break
                    }
                }

                val successCount = batchResults.count { it.error == null }
                val summary = when {
                    outputFolder != null -> "Batch selesai: $completed dari $total file diproses; $savedCount hasil batch ini tersimpan otomatis."
                    else -> "Batch selesai: $completed dari $total file diproses. Hasil tersedia pada riwayat lokal."
                }
                message = when {
                    wasCancelled -> "Proses dibatalkan dengan aman setelah $completed file selesai. Hasil yang sudah selesai tetap disimpan; file yang belum diproses dikembalikan ke antrean."
                    successCount == 0 -> "Batch selesai: tidak ada file yang berhasil dikonversi."
                    else -> summary + if (saveErrors.isEmpty()) "" else " Gagal menyimpan: " + saveErrors.joinToString("; ")
                }
                if (historyTrimmed) {
                    message += " Hasil paling lama dihapus karena batas riwayat lokal tercapai."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (nextIndex < uris.size) selectedUris.addAll(uris.drop(nextIndex))
                message = "Proses terhenti: " + (error.message ?: "kesalahan tidak diketahui") +
                    " File yang belum diproses dikembalikan ke antrean."
            } finally {
                busy = false
                cancelRequested = false
            }
        }
    }

    /** Requests cancellation at a safe boundary; the currently active file is allowed to finish. */
    fun cancel() {
        if (!busy) return
        cancelRequested = true
        message = "Permintaan pembatalan diterima. File yang sedang aktif akan dituntaskan terlebih dahulu."
    }

    fun clearResults(context: Context) {
        if (busy) return
        val removed = files.toList()
        files.clear()
        progress = 0f
        completed = 0
        total = 0
        message = "Daftar hasil konversi dikosongkan."
        historyReady = false
        viewModelScope.launch(Dispatchers.IO) {
            removed.forEach { it.deleteCachedContent() }
            writeHistoryIndex(context.applicationContext, emptyList())
            withContext(Dispatchers.Main) { historyReady = true }
        }
    }

    private fun historyDirectory(context: Context) = File(context.filesDir, "marka-history")

    private fun writeHistoryContent(context: Context, markdown: String): String? = runCatching {
        val directory = historyDirectory(context)
        check(directory.exists() || directory.mkdirs()) { "Folder riwayat lokal tidak dapat dibuat." }
        val id = UUID.randomUUID().toString()
        val temporary = File(directory, ".$id.tmp")
        val destination = File(directory, "$id.md")
        temporary.outputStream().bufferedWriter(Charsets.UTF_8).use { it.write(markdown) }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        destination.absolutePath
    }.getOrNull()

    private suspend fun persistHistory(context: Context) {
        val snapshot = files.toList()
        withContext(Dispatchers.IO) { writeHistoryIndex(context, snapshot) }
    }

    private fun writeHistoryIndex(context: Context, items: List<ConvertedFile>) {
        val directory = historyDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) return

        val entries = JSONArray()
        items.forEach { item ->
            val warnings = JSONArray()
            item.warnings.forEach(warnings::put)
            val entry = JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("format", item.format)
                .put("warnings", warnings)
                .put("error", item.error ?: JSONObject.NULL)
                .put("content", item.cacheFilePath?.let { File(it).name } ?: JSONObject.NULL)
            entries.put(entry)
        }

        val target = File(directory, "index.json")
        val temporary = File(directory, "index.tmp")
        temporary.writeText(entries.toString(), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.delete()
            check(temporary.renameTo(target)) { "Indeks riwayat lokal tidak dapat disimpan." }
        }
    }

    private fun loadHistory(context: Context): List<ConvertedFile> = runCatching {
        val directory = historyDirectory(context)
        val index = File(directory, "index.json")
        if (!index.isFile) return@runCatching emptyList()

        val entries = JSONArray(index.readText(Charsets.UTF_8))
        buildList {
            for (position in 0 until entries.length()) {
                val entry = entries.optJSONObject(position) ?: continue
                val name = entry.optString("name").ifBlank { "dokumen" }
                val format = entry.optString("format").ifBlank { "unknown" }
                val filename = entry.optString("content").takeIf { it.isNotBlank() && it != "null" }
                val contentFile = filename?.let { File(directory, it) }
                val storedError = entry.optString("error").takeIf { it.isNotBlank() && it != "null" }
                val missingContent = storedError == null && (contentFile == null || !contentFile.isFile)
                val warningsArray = entry.optJSONArray("warnings") ?: JSONArray()
                val warnings = buildList {
                    for (i in 0 until warningsArray.length()) add(warningsArray.optString(i))
                    if (missingContent) add("Isi riwayat lokal tidak ditemukan. File sumber perlu dikonversi ulang.")
                }
                add(
                    ConvertedFile(
                        id = entry.optLong("id", System.nanoTime()),
                        name = name,
                        format = format,
                        markdown = "",
                        warnings = warnings,
                        error = storedError ?: if (missingContent) "Isi hasil tidak tersedia pada penyimpanan lokal." else null,
                        cacheFilePath = contentFile?.takeIf { it.isFile }?.absolutePath
                    )
                )
            }
        }
    }.getOrElse { emptyList() }

    private fun removeOrphanedFiles(context: Context, activeFiles: Set<String>) {
        val directory = historyDirectory(context)
        val staleBefore = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        directory.listFiles()
            ?.filter {
                it.absolutePath !in activeFiles && (
                    it.name.endsWith(".tmp") ||
                    (it.name.endsWith(".md") && it.lastModified() in 1..staleBefore)
                )
            }
            ?.forEach { it.delete() }
    }

    /** Keep recent conversion history bounded while preserving the newest result. */
    private fun enforceHistoryLimits(): Boolean {
        val maximumItems = 50
        val maximumBytes = 100L * 1024 * 1024
        var totalBytes = files.sumOf { it.cacheFilePath?.let { path -> File(path).length() } ?: 0L }
        var removedAny = false

        while (files.size > maximumItems || (totalBytes > maximumBytes && files.size > 1)) {
            val oldest = files.removeAt(0)
            val size = oldest.cacheFilePath?.let { File(it).length() } ?: 0L
            totalBytes = (totalBytes - size).coerceAtLeast(0L)
            oldest.deleteCachedContent()
            removedAny = true
        }
        return removedAny
    }
}
