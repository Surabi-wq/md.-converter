package id.surabi.mdconverter

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Retains batch state across Activity recreation. Markdown bodies are spooled to cache
 * files so a long multi-file batch does not keep every converted document in RAM.
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

    private var conversionJob: Job? = null

    fun start(context: Context, uris: List<Uri>, outputFolder: Uri?) {
        if (busy || uris.isEmpty()) return
        val appContext = context.applicationContext

        conversionJob = viewModelScope.launch {
            busy = true
            completed = 0
            total = uris.size
            progress = 0f
            message = "Menyiapkan ${uris.size} file…"
            var savedCount = 0
            val saveErrors = mutableListOf<String>()
            val batchResults = mutableListOf<ConvertedFile>()

            try {
                for (uri in uris) {
                    val converted = withContext(Dispatchers.IO) {
                        val name = runCatching { DocumentStorage.queryName(appContext, uri) }
                            .getOrDefault("dokumen")
                        try {
                            val result = ConverterEngine.convert(appContext, uri, name)
                            val cacheFile = File(appContext.cacheDir, "marka-${UUID.randomUUID()}.md")
                            val cachePath = runCatching {
                                cacheFile.outputStream().bufferedWriter(Charsets.UTF_8).use {
                                    it.write(result.markdown)
                                }
                                cacheFile.absolutePath
                            }.getOrElse {
                                cacheFile.delete()
                                null
                            }
                            ConvertedFile(
                                id = System.nanoTime(),
                                name = name,
                                format = result.format,
                                markdown = if (cachePath == null) result.markdown else "",
                                warnings = result.warnings,
                                error = null,
                                cacheFilePath = cachePath
                            )
                        } catch (error: CancellationException) {
                            throw error
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
                    progress = completed.toFloat() / total.coerceAtLeast(1)
                    message = "Memproses file $completed dari $total…"

                    if (converted.error == null && outputFolder != null) {
                        try {
                            withContext(Dispatchers.IO) {
                                val cachePath = converted.cacheFilePath
                                if (cachePath != null) {
                                    DocumentStorage.saveCachedFile(
                                        appContext,
                                        outputFolder,
                                        MarkdownFileNames.fromSource(converted.name),
                                        File(cachePath)
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
                }

                val successCount = batchResults.count { it.error == null }
                message = when {
                    successCount == 0 -> "Batch selesai: tidak ada file yang berhasil dikonversi."
                    outputFolder != null -> {
                        val base = "Batch selesai: $successCount dari ${uris.size} file berhasil dikonversi; $savedCount hasil tersimpan otomatis."
                        if (saveErrors.isEmpty()) base else base + " Gagal menyimpan: " + saveErrors.joinToString("; ")
                    }
                    else -> "Batch selesai: $successCount dari ${uris.size} file berhasil dikonversi. Hasil tetap tersedia; pilih folder keluaran atau ekspor tiap file."
                }
            } catch (error: CancellationException) {
                message = "Proses dibatalkan. $completed dari $total file selesai; hasil yang sudah selesai tetap dipertahankan."
                throw error
            } catch (error: Exception) {
                message = "Proses terhenti: " + (error.message ?: "kesalahan tidak diketahui")
            } finally {
                busy = false
                conversionJob = null
            }
        }
    }

    fun cancel() {
        conversionJob?.cancel()
    }

    fun clearResults() {
        if (busy) return
        files.forEach { it.deleteCachedContent() }
        files.clear()
        progress = 0f
        completed = 0
        total = 0
        message = "Daftar hasil konversi dikosongkan."
    }

    override fun onCleared() {
        files.forEach { it.deleteCachedContent() }
        super.onCleared()
    }
}
