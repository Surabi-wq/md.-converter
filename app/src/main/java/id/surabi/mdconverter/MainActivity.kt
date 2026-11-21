package id.surabi.mdconverter

import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConvertedFile(
    val id: Long,
    val name: String,
    val format: String,
    val markdown: String,
    val warnings: List<String>,
    val error: String?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent { Screen() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        val files = remember { mutableStateListOf<ConvertedFile>() }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("Pilih dokumen atau media untuk mulai.") }
        var preview by remember { mutableStateOf<ConvertedFile?>(null) }
        var pendingMarkdown by remember { mutableStateOf<String?>(null) }
        var pendingName by remember { mutableStateOf("hasil-konversi.md") }
        val successfulCount = files.count { it.error == null }
        val combined = remember(files.toList()) { files.filter { it.error == null }.takeIf { it.isNotEmpty() }?.let(::combineMarkdown).orEmpty() }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty() && !busy) processFiles(uris, files) { isBusy, status ->
                busy = isBusy
                message = status
            }
        }
        val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
            val markdown = pendingMarkdown
            if (uri != null && markdown != null) {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(markdown) }
                                ?: error("Lokasi penyimpanan tidak dapat dibuka.")
                        }
                        message = "Markdown berhasil disimpan."
                    } catch (e: Exception) {
                        message = "Gagal menyimpan: ${e.message ?: "kesalahan tidak diketahui"}"
                    } finally {
                        pendingMarkdown = null
                    }
                }
            } else {
                pendingMarkdown = null
            }
        }

        MaterialTheme(colorScheme = lightColorScheme(
            primary = Color(0xFF315F55),
            secondary = Color(0xFF82A99A),
            background = Color(0xFFF4F7F5)
        )) {
            Scaffold(
                containerColor = Color(0xFFF4F7F5),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("MD Converter", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            Icon(Icons.Default.Description, null, Modifier.padding(start = 16.dp), tint = Color(0xFF315F55))
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EEE9))
                        ) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Konversi dan gabungkan file ke Markdown", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color(0xFF183D36))
                                Text(
                                    "Pilih berbagai jenis file sekaligus. Setiap hasil akan menjadi bagian tersendiri dalam satu dokumen Markdown.",
                                    color = Color(0xFF49635D)
                                )
                                Button(
                                    onClick = { picker.launch(arrayOf("*/*")) },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pilih file")
                                }
                                Text("Dapat memilih beberapa file dengan format berbeda.", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Antrean konversi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${files.size} file • $successfulCount berhasil", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text("Dokumen gabungan", fontWeight = FontWeight.Bold)
                                Text(
                                    if (successfulCount == 0) "Konversikan minimal satu file agar dapat membuat dokumen gabungan."
                                    else "$successfulCount bagian siap digabung. Urutannya mengikuti urutan file dalam antrean.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Button(
                                    onClick = {
                                        pendingMarkdown = combined
                                        pendingName = "gabungan-markdown.md"
                                        saver.launch(pendingName)
                                    },
                                    enabled = !busy && successfulCount > 0,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.MergeType, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Gabungkan dan simpan satu .md")
                                }
                                OutlinedButton(
                                    onClick = { preview = ConvertedFile(-1L, "gabungan-markdown.md", "md", combined, emptyList(), null) },
                                    enabled = successfulCount > 0,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Pratinjau dokumen gabungan") }
                            }
                        }
                    }

                    if (files.isEmpty()) item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(
                                Modifier.fillMaxWidth().padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF82A99A), modifier = Modifier.size(42.dp))
                                Text("Belum ada file", fontWeight = FontWeight.SemiBold)
                                Text("File yang dipilih akan tampil di sini.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }

                    items(files, key = { it.id }) { file ->
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFF315F55))
                                    Spacer(Modifier.width(9.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(
                                            file.format.uppercase() + " • " + if (file.error == null) "Selesai" else "Gagal",
                                            fontSize = 12.sp,
                                            color = if (file.error == null) Color(0xFF28734E) else Color.Red
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            pendingMarkdown = file.markdown
                                            pendingName = file.name.substringBeforeLast('.', file.name).ifBlank { "hasil" } + ".md"
                                            saver.launch(pendingName)
                                        },
                                        enabled = file.error == null
                                    ) { Icon(Icons.Default.Download, "Simpan") }
                                }
                                file.warnings.forEach { Text("⚠ $it", fontSize = 12.sp, color = Color(0xFF946B20)) }
                                if (file.error != null) Text(file.error, color = Color.Red, fontSize = 12.sp)
                                if (file.error == null) {
                                    OutlinedButton(
                                        onClick = { preview = file },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Lihat Markdown") }
                                }
                            }
                        }
                    }

                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("Status", fontWeight = FontWeight.Bold)
                                Text(message, color = Color.Gray, fontSize = 13.sp)
                                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                    item {
                        Text(
                            "Pemrosesan dilakukan secara lokal. File asli tidak diubah dan tidak diunggah ke server.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            preview?.let { item ->
                AlertDialog(
                    onDismissRequest = { preview = null },
                    title = { Text(if (item.id == -1L) "Preview Markdown Gabungan" else "Preview Markdown") },
                    text = {
                        Column(Modifier.heightIn(max = 420.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(item.markdown, fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingMarkdown = item.markdown
                            pendingName = item.name.substringBeforeLast('.', item.name).ifBlank { "hasil" } + ".md"
                            saver.launch(pendingName)
                        }) { Text("Ekspor .md") }
                    },
                    dismissButton = { TextButton(onClick = { preview = null }) { Text("Tutup") } }
                )
            }
        }
    }

    private fun combineMarkdown(items: List<ConvertedFile>): String {
        require(items.isNotEmpty()) { "Tidak ada hasil konversi yang dapat digabungkan." }
        return buildString {
            appendLine("# Dokumen Gabungan")
            appendLine()
            appendLine("> Dokumen ini dibuat dari ${items.size} file sumber. Setiap file dipisahkan dalam bagian tersendiri.")
            appendLine()
            appendLine("## Daftar Isi")
            items.forEachIndexed { index, item ->
                val heading = "${index + 1}. ${item.name}"
                val anchor = heading.lowercase()
                    .replace(Regex("[^a-z0-9\\s-]"), "")
                    .trim()
                    .replace(Regex("\\s+"), "-")
                appendLine("- [${heading.replace("[", "\\[").replace("]", "\\]")}](#${anchor})")
            }
            items.forEachIndexed { index, item ->
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ${index + 1}. ${item.name}")
                appendLine()
                appendLine("- **Format sumber:** ${item.format.uppercase()}")
                appendLine("- **Status:** Berhasil dikonversi")
                if (item.warnings.isNotEmpty()) {
                    appendLine()
                    appendLine("### Catatan konversi")
                    item.warnings.forEach { appendLine("- $it") }
                }
                val body = item.markdown.substringAfter("\n\n", item.markdown).trim()
                appendLine()
                appendLine(body.ifBlank { "_Tidak ada konten teks yang dapat ditampilkan._" })
                appendLine()
            }
        }.trimEnd() + "\n"
    }

    private fun processFiles(
        uris: List<Uri>,
        files: MutableList<ConvertedFile>,
        onStatus: (Boolean, String) -> Unit
    ) {
        lifecycleScope.launch {
            onStatus(true, "Memproses ${uris.size} file…")
            var completed = 0
            try {
                for (uri in uris) {
                    val name = runCatching { queryName(uri) }.getOrDefault("dokumen")
                    val converted = withContext(Dispatchers.IO) {
                        try {
                            val result = ConverterEngine.convert(this@MainActivity, uri, name)
                            ConvertedFile(
                                System.nanoTime(),
                                name,
                                result.format,
                                result.markdown,
                                result.warnings,
                                null
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ConvertedFile(
                                System.nanoTime(),
                                name,
                                name.substringAfterLast('.', "unknown"),
                                "",
                                emptyList(),
                                e.message ?: "Konversi gagal."
                            )
                        }
                    }
                    files.add(converted)
                    completed++
                    onStatus(true, "Memproses file $completed dari ${uris.size}…")
                }
                onStatus(false, "Selesai. $completed file telah diperiksa.")
            } catch (e: CancellationException) {
                onStatus(false, "Proses dibatalkan setelah $completed file.")
                throw e
            } catch (e: Exception) {
                onStatus(false, "Proses terhenti: ${e.message ?: "kesalahan tidak diketahui"}")
            }
        }
    }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) {
                return cursor.getString(index)?.takeIf { it.isNotBlank() } ?: "dokumen"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "dokumen"
    }
}
