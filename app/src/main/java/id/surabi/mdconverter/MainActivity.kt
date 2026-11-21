package id.surabi.mdconverter

import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
        val selectedUris = remember { mutableStateListOf<Uri>() }
        var outputFolder by remember { mutableStateOf(loadOutputFolder()) }
        val successfulCount = files.count { it.error == null }
        val combined = remember(files.toList()) { files.filter { it.error == null }.takeIf { it.isNotEmpty() }?.let(::combineMarkdown).orEmpty() }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty() && !busy) {
                val existing = selectedUris.map { it.toString() }.toHashSet()
                val fresh = uris.filter { existing.add(it.toString()) }
                selectedUris.addAll(fresh)
                message = if (fresh.isEmpty()) "File yang dipilih sudah ada di antrean." else "${fresh.size} file ditambahkan. Total ${selectedUris.size} file menunggu konfirmasi."
            }
        }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            if (tree != null) runCatching {
                contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("output_tree", tree.toString()).apply()
                outputFolder = tree
                message = "Folder keluaran tersimpan. Hasil berikutnya akan disimpan otomatis."
            }.onFailure { message = "Gagal menyimpan izin folder: ${it.message}" }
        }
        val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
            val markdown = pendingMarkdown
            if (uri != null && markdown != null) {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            (contentResolver.openOutputStream(uri, "wt") ?: error("Lokasi penyimpanan tidak dapat dibuka."))
                                .bufferedWriter(Charsets.UTF_8).use { it.write(markdown) }
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
                        title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MARKA", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("File to Markdown", fontSize = 10.sp, color = Color(0xFF49635D))
                        } },
                        navigationIcon = {
                            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF315F55), modifier = Modifier.padding(start = 14.dp)) {
                                Icon(painterResource(R.drawable.ic_marka_foreground), "Logo Marka", tint = Color.Unspecified, modifier = Modifier.padding(4.dp).size(30.dp))
                            }
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
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EEE9))
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF315F55)) {
                                        Icon(painterResource(R.drawable.ic_marka_foreground), "Logo Marka", tint = Color.Unspecified, modifier = Modifier.padding(4.dp).size(30.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("MARKA", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Color(0xFF183D36))
                                        Text("File to Markdown", fontSize = 12.sp, color = Color(0xFF49635D))
                                    }
                                }
                                Text(
                                    "Ubah file jadi Markdown. Konversi dokumen dan media langsung di perangkat, lalu tinjau atau gabungkan hasilnya dalam satu dokumen terstruktur.",
                                    color = Color(0xFF49635D)
                                )
                                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("1. Pilih file")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${selectedUris.size} file menunggu konfirmasi", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                                    if (selectedUris.isNotEmpty()) TextButton(onClick = { selectedUris.clear(); message = "Antrean pilihan dikosongkan." }, enabled = !busy) { Text("Kosongkan") }
                                }
                                if (selectedUris.isNotEmpty()) {
                                    Text("File terpilih (${selectedUris.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF315F55))
                                    selectedUris.take(4).forEach { uri ->
                                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F5F2)) {
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFF315F55), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(uri.lastPathSegment?.substringAfterLast('/')?.take(48) ?: "Dokumen terpilih", fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { selectedUris.remove(uri) }, enabled = !busy) {
                                                    Icon(Icons.Default.Close, "Hapus dari antrean", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (selectedUris.size > 4) Text("+ ${selectedUris.size - 4} file lainnya", fontSize = 11.sp, color = Color(0xFF49635D))
                                }
                                Button(onClick = {
                                    files.clear()
                                    val batch = selectedUris.toList()
                                    selectedUris.clear()
                                    processFiles(batch, files, outputFolder) { isBusy, status -> busy = isBusy; message = status }
                                }, enabled = !busy && selectedUris.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("2. Konfirmasi & mulai konversi")
                                }
                                OutlinedButton(onClick = { folderPicker.launch(outputFolder) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text(if (outputFolder == null) "Pilih folder keluaran" else "Ganti folder keluaran")
                                }
                                Text(if (outputFolder == null) "Folder belum dipilih. Hasil dapat disimpan manual." else "Hasil gabungan akan disimpan otomatis ke folder pilihan.", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Hasil konversi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("${files.size} file diproses", color = Color.Gray, fontSize = 12.sp)
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text("$successfulCount berhasil") },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp)) },
                                enabled = false
                            )
                            if (files.any { it.error != null }) {
                                AssistChip(onClick = {}, label = { Text("${files.count { it.error != null }} gagal") }, enabled = false)
                            }
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
                                        Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
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

    private fun combineMarkdown(items: List<ConvertedFile>): String = MarkdownMerger.combine(items)

    private fun processFiles(
        uris: List<Uri>,
        files: MutableList<ConvertedFile>,
        outputFolder: Uri?,
        onStatus: (Boolean, String) -> Unit
    ) {
        lifecycleScope.launch {
            onStatus(true, "Memproses ${uris.size} file…")
            var completed = 0
            try {
                for (uri in uris) {
                    val converted = withContext(Dispatchers.IO) {
                        val name = runCatching { queryName(uri) }.getOrDefault("dokumen")
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
                val successful = files.filter { it.error == null }
                if (successful.isNotEmpty() && outputFolder != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            saveToFolder(outputFolder, "konversi-${System.currentTimeMillis()}.md", combineMarkdown(successful))
                        }
                        onStatus(false, "Selesai. $completed file diproses dan hasil disimpan otomatis.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onStatus(false, "Konversi selesai, tetapi penyimpanan otomatis gagal: ${e.message ?: "izin folder mungkin telah dicabut"}. Ekspor hasil secara manual atau pilih folder lain.")
                    }
                } else if (successful.isNotEmpty()) onStatus(false, "Selesai. $completed file diperiksa. Pilih folder keluaran untuk simpan otomatis.")
                else onStatus(false, "Tidak ada file yang berhasil dikonversi.")
            } catch (e: CancellationException) {
                onStatus(false, "Proses dibatalkan setelah $completed file.")
                throw e
            } catch (e: Exception) {
                onStatus(false, "Proses terhenti: ${e.message ?: "kesalahan tidak diketahui"}")
            }
        }
    }

    private fun loadOutputFolder(): Uri? {
        val saved = getSharedPreferences("settings", MODE_PRIVATE).getString("output_tree", null) ?: return null
        val uri = runCatching { Uri.parse(saved) }.getOrNull() ?: return null
        val granted = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        if (!granted) getSharedPreferences("settings", MODE_PRIVATE).edit().remove("output_tree").apply()
        return uri.takeIf { granted }
    }

    private fun saveToFolder(tree: Uri, name: String, markdown: String) {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val output = DocumentsContract.createDocument(contentResolver, root, "text/markdown", name)
            ?: error("Tidak dapat membuat file pada folder tujuan.")
        try {
            (contentResolver.openOutputStream(output, "wt")
                ?: error("Tidak dapat membuka file keluaran."))
                .bufferedWriter(Charsets.UTF_8).use { it.write(markdown) }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, output) }
            throw e
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
