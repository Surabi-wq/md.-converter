package id.surabi.mdconverter

import android.net.Uri
import android.content.Intent
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import android.os.Bundle
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MergeType
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConvertedFile(
    val id: Long,
    val name: String,
    val format: String,
    val markdown: String,
    val warnings: List<String>,
    val error: String?,
    val cacheFilePath: String? = null
) {
    fun readMarkdown(): String =
        cacheFilePath?.let { java.io.File(it).readText(Charsets.UTF_8) } ?: markdown

    fun writeMarkdownTo(writer: java.io.Writer) {
        val path = cacheFilePath
        if (path == null) writer.write(markdown)
        else java.io.File(path).bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(16 * 1024)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                writer.write(buffer, 0, count)
            }
        }
    }

    fun writeBodyTo(writer: java.io.Writer) {
        val path = cacheFilePath
        if (path == null) {
            val body = markdown.substringAfter("\n\n", markdown).trim()
            writer.write(body.ifBlank { "_Tidak ada konten teks yang dapat ditampilkan._" })
            return
        }

        java.io.File(path).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLine() // Drop the source-title heading; merger supplies its own section heading.
            var line: String?
            do {
                line = reader.readLine()
            } while (line != null && line.isBlank())
            var pendingBlankLines = 0
            var wroteContent = false
            while (line != null) {
                if (line.isBlank()) {
                    pendingBlankLines++
                } else {
                    repeat(pendingBlankLines) { writer.write("\n") }
                    pendingBlankLines = 0
                    writer.write(line)
                    writer.write("\n")
                    wroteContent = true
                }
                line = reader.readLine()
            }
            if (!wroteContent) writer.write("_Tidak ada konten teks yang dapat ditampilkan._")
        }
    }

    fun deleteCachedContent() {
        cacheFilePath?.let { java.io.File(it).delete() }
    }
}

class MainActivity : ComponentActivity() {
    private var incomingSharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingSharedUris = extractSharedUris(intent)
        PDFBoxResourceLoader.init(applicationContext)
        setContent { Screen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingSharedUris = extractSharedUris(intent)
    }

    @Suppress("DEPRECATION")
    private fun extractSharedUris(source: Intent?): List<Uri> {
        if (source == null) return emptyList()
        val fromExtra = when (source.action) {
            Intent.ACTION_SEND -> listOfNotNull(source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        val fromClip = source.clipData?.let { clip ->
            (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index).uri }
        }.orEmpty()
        return (fromExtra + fromClip + listOfNotNull(source.data)).distinct()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        val conversionViewModel: ConversionViewModel = viewModel()
        LaunchedEffect(conversionViewModel) {
            conversionViewModel.initialize(applicationContext)
        }
        val files = conversionViewModel.files
        val busy = conversionViewModel.busy
        val historyReady = conversionViewModel.historyReady
        var message by remember { mutableStateOf(conversionViewModel.message) }
        LaunchedEffect(conversionViewModel.message) { message = conversionViewModel.message }
        var preview by remember { mutableStateOf<ConvertedFile?>(null) }
        var showHelp by remember { mutableStateOf(false) }
        var pendingMarkdown by remember { mutableStateOf<String?>(null) }
        var pendingFile by remember { mutableStateOf<ConvertedFile?>(null) }
        var pendingMergeFiles by remember { mutableStateOf<List<ConvertedFile>?>(null) }
        var pendingName by remember { mutableStateOf("hasil.md") }
        val selectedUris = conversionViewModel.selectedUris
        var outputFolder by remember { mutableStateOf(DocumentStorage.loadOutputFolder(this@MainActivity)) }
        LaunchedEffect(incomingSharedUris) {
            val shared = incomingSharedUris
            if (shared.isNotEmpty()) {
                val existing = selectedUris.map { it.toString() }.toHashSet()
                val fresh = shared.filter { existing.add(it.toString()) }
                fresh.forEach { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                selectedUris.addAll(fresh)
                message = if (fresh.isEmpty()) "File yang dibagikan sudah ada di antrean."
                    else "${fresh.size} file diterima melalui Share Android."
                incomingSharedUris = emptyList()
            }
        }
        val mergeScope = rememberCoroutineScope()
        val successfulCount = files.count { it.error == null }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty() && !busy) {
                val existing = selectedUris.map { it.toString() }.toHashSet()
                val fresh = uris.filter { existing.add(it.toString()) }
                fresh.forEach { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
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
            val sourceFile = pendingFile
            val mergeFiles = pendingMergeFiles
            if (uri != null && (markdown != null || sourceFile != null || mergeFiles != null)) {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val output = contentResolver.openOutputStream(uri, "wt")
                                ?: error("Lokasi penyimpanan tidak dapat dibuka.")
                            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                                when {
                                    sourceFile != null -> sourceFile.writeMarkdownTo(writer)
                                    mergeFiles != null -> MarkdownMerger.writeTo(mergeFiles, writer)
                                    markdown != null -> writer.write(markdown)
                                }
                            }
                        }
                        message = "Markdown berhasil disimpan."
                    } catch (e: Exception) {
                        message = "Gagal menyimpan: ${e.message ?: "kesalahan tidak diketahui"}"
                    } finally {
                        pendingMarkdown = null
                        pendingFile = null
                        pendingMergeFiles = null
                    }
                }
            } else {
                pendingMarkdown = null
                pendingFile = null
                pendingMergeFiles = null
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
                        },
                        actions = {
                            IconButton(onClick = { showHelp = true }) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Panduan penggunaan Marka")
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
                                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy && historyReady, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("1. Pilih file")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${selectedUris.size} file menunggu konfirmasi", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                                    if (selectedUris.isNotEmpty()) TextButton(onClick = {
                                        selectedUris.toList().forEach { DocumentStorage.releaseReadPermission(this@MainActivity, it) }
                                        selectedUris.clear()
                                        message = "Antrean pilihan dikosongkan."
                                    }, enabled = !busy) { Text("Kosongkan") }
                                }
                                if (selectedUris.isNotEmpty()) {
                                    Text("File terpilih (${selectedUris.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF315F55))
                                    selectedUris.take(4).forEach { uri ->
                                        val displayName by produceState(initialValue = "Memuat nama file…", key1 = uri) {
                                            val resolvedName = withContext(Dispatchers.IO) {
                                                runCatching { DocumentStorage.queryName(this@MainActivity, uri) }
                                                    .getOrDefault("File terpilih")
                                            }
                                            value = resolvedName
                                        }
                                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F5F2)) {
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color(0xFF315F55), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(displayName.take(48), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                IconButton(onClick = {
                                                    selectedUris.remove(uri)
                                                    DocumentStorage.releaseReadPermission(this@MainActivity, uri)
                                                }, enabled = !busy) {
                                                    Icon(Icons.Default.Close, "Hapus dari antrean", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                    if (selectedUris.size > 4) Text("+ ${selectedUris.size - 4} file lainnya", fontSize = 11.sp, color = Color(0xFF49635D))
                                }
                                Button(onClick = {
                                    val batch = selectedUris.toList()
                                    selectedUris.clear()
                                    conversionViewModel.start(applicationContext, batch, outputFolder)
                                }, enabled = !busy && historyReady && selectedUris.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("2. Konfirmasi & mulai konversi")
                                }
                                OutlinedButton(onClick = { folderPicker.launch(outputFolder) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp)); Text(if (outputFolder == null) "Pilih folder keluaran" else "Ganti folder keluaran")
                                }
                                Text(
                                    if (outputFolder == null) "Folder belum dipilih. Hasil dapat diekspor saat proses selesai."
                                    else "Hasil tiap file disimpan otomatis di folder ini. Dokumen gabungan dibuat terpisah melalui tombol 3.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    "Nama hasil mengikuti nama file asli; hanya ekstensinya yang berubah menjadi .md.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
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
                            if (files.isNotEmpty()) {
                                TextButton(onClick = { conversionViewModel.clearResults(this@MainActivity) }, enabled = !busy && historyReady) {
                                    Text("Hapus hasil")
                                }
                            }
                        }
                    }

                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text("3. Dokumen gabungan", fontWeight = FontWeight.Bold)
                                Text(
                                    if (successfulCount == 0) "Setelah konversi selesai, semua hasil yang berhasil akan muncul di sini."
                                    else successfulCount.toString() + " hasil siap disatukan menjadi satu dokumen Markdown. Isi setiap sumber menjadi bagian tersendiri dan urutannya mengikuti daftar hasil.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    if (successfulCount == 0) "Nama file gabungan: belum tersedia"
                                    else "Nama file gabungan: " + MarkdownFileNames.merged(files.filter { it.error == null }.map { it.name }),
                                    color = Color(0xFF315F55),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Button(
                                    onClick = {
                                        val successful = files.filter { it.error == null }
                                        val documentName = MarkdownFileNames.merged(successful.map { it.name })
                                        val folder = outputFolder
                                        mergeScope.launch {
                                            try {
                                                if (folder == null) {
                                                    pendingMarkdown = null
                                                    pendingFile = null
                                                    pendingMergeFiles = successful
                                                    pendingName = documentName
                                                    saver.launch(documentName)
                                                } else {
                                                    val savedName = withContext(Dispatchers.IO) {
                                                        DocumentStorage.saveMergedToFolder(this@MainActivity, folder, documentName, successful)
                                                    }
                                                    message = "Dokumen gabungan berhasil disimpan sebagai " + savedName
                                                }
                                            } catch (error: Exception) {
                                                message = "Gagal menggabungkan atau menyimpan hasil: " + (error.message ?: "kesalahan tidak diketahui")
                                            }
                                        }
                                    },
                                    enabled = !busy && successfulCount >= 2,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.MergeType, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("3. Gabungkan & simpan satu file .md")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val successful = files.filter { it.error == null }
                                        mergeScope.launch {
                                            try {
                                                require(MarkdownMerger.estimatedSourceBytes(successful) <= 8L * 1024 * 1024) {
                                                    "Isi gabungan terlalu besar untuk pratinjau dalam memori. Gunakan tombol Gabungkan & simpan agar hasil ditulis langsung ke penyimpanan."
                                                }
                                                val markdown = withContext(Dispatchers.IO) { combineMarkdown(successful) }
                                                preview = ConvertedFile(
                                                    -1L,
                                                    MarkdownFileNames.merged(successful.map { it.name }),
                                                    "md",
                                                    markdown,
                                                    emptyList(),
                                                    null
                                                )
                                            } catch (error: Exception) {
                                                message = "Gagal membuat pratinjau gabungan: " + (error.message ?: "kesalahan tidak diketahui")
                                            }
                                        }
                                    },
                                    enabled = !busy && successfulCount >= 2,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Lihat pratinjau hasil gabungan") }
                                Text(
                                    if (successfulCount < 2) "Pilih dan konversi minimal dua file untuk mengaktifkan penggabungan. Hasil tiap file tetap dapat disimpan sendiri."
                                    else "Konversi satu per satu tetap tersimpan terpisah. Penggabungan adalah langkah opsional.",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
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
                                            mergeScope.launch {
                                                try {
                                                    pendingMarkdown = null
                                                    pendingMergeFiles = null
                                                    pendingFile = file
                                                    pendingName = MarkdownFileNames.fromSource(file.name)
                                                    saver.launch(pendingName)
                                                } catch (error: Exception) {
                                                    message = "Cache hasil tidak tersedia. Jalankan ulang konversi file ini."
                                                }
                                            }
                                        },
                                        enabled = file.error == null
                                    ) { Icon(Icons.Default.Download, "Simpan") }
                                }
                                file.warnings.forEach { Text("⚠ $it", fontSize = 12.sp, color = Color(0xFF946B20)) }
                                if (file.error != null) Text(file.error, color = Color.Red, fontSize = 12.sp)
                                if (file.error == null) {
                                    OutlinedButton(
                                        onClick = {
                                            mergeScope.launch {
                                                try {
                                                    val sourceBytes = file.cacheFilePath?.let { java.io.File(it).length() }
                                                        ?: file.markdown.toByteArray(Charsets.UTF_8).size.toLong()
                                                    require(sourceBytes <= 4L * 1024 * 1024) {
                                                        "File terlalu besar untuk pratinjau dalam memori. Gunakan tombol Simpan untuk mengekspor hasil secara langsung."
                                                    }
                                                    val markdown = withContext(Dispatchers.IO) { file.readMarkdown() }
                                                    preview = file.copy(markdown = markdown, cacheFilePath = null)
                                                } catch (error: Exception) {
                                                    message = "Cache hasil tidak tersedia. Jalankan ulang konversi file ini."
                                                }
                                            }
                                        },
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
                                Text(if (historyReady) message else "Memulihkan riwayat konversi…", color = Color.Gray, fontSize = 13.sp)
                                if (busy) {
                                    LinearProgressIndicator(
                                        progress = { conversionViewModel.progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "${conversionViewModel.completed} dari ${conversionViewModel.total} file selesai",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                    OutlinedButton(
                                        onClick = { conversionViewModel.cancel() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Close, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Batalkan konversi")
                                    }
                                }
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

            if (showHelp) {
                AlertDialog(
                    onDismissRequest = { showHelp = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF315F55))
                            Text("Panduan penggunaan Marka")
                        }
                    },
                    text = {
                        Column(
                            Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Cara menggunakan", fontWeight = FontWeight.Bold, color = Color(0xFF315F55))
                            UsageGuide.steps.forEachIndexed { index, step ->
                                Text((index + 1).toString() + ". " + step, fontSize = 13.sp)
                            }
                            HorizontalDivider()
                            Text("Aturan nama hasil", fontWeight = FontWeight.Bold, color = Color(0xFF315F55))
                            Text(UsageGuide.naming, fontSize = 12.sp)
                            Text(UsageGuide.merge, fontSize = 12.sp)
                            HorizontalDivider()
                            Text("Format yang didukung", fontWeight = FontWeight.Bold, color = Color(0xFF315F55))
                            SupportedFormats.groups.forEach { group ->
                                Text(group.title + " — " + group.extensions.joinToString(", ") { "." + it }, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(group.note, fontSize = 11.sp, color = Color.Gray)
                            }
                            HorizontalDivider()
                            Text("Batasan penting", fontWeight = FontWeight.Bold, color = Color(0xFF315F55))
                            Text("Format DOC, XLS, dan PPT lama belum didukung. PDF hasil scan dapat menjalani OCR pada maksimal 50 halaman. Audio belum ditranskripsikan menjadi ucapan/lirik; OCR video hanya membaca teks pada cuplikan, bukan dialog.", fontSize = 12.sp)
                            Text(UsageGuide.privacy, fontSize = 12.sp, color = Color.Gray)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showHelp = false }) { Text("Mengerti") }
                    }
                )
            }

            preview?.let { item ->
                AlertDialog(
                    onDismissRequest = { preview = null },
                    title = { Text(if (item.id == -1L) "Preview Markdown Gabungan" else "Preview Markdown") },
                    text = {
                        Column(Modifier.heightIn(max = 460.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            MarkdownPreview(item.markdown, Modifier.heightIn(max = 390.dp).fillMaxWidth())
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingMarkdown = item.markdown
                            pendingFile = null
                            pendingMergeFiles = null
                            pendingName = MarkdownFileNames.fromSource(item.name)
                            saver.launch(pendingName)
                        }) { Text("Ekspor .md") }
                    },
                    dismissButton = { TextButton(onClick = { preview = null }) { Text("Tutup") } }
                )
            }
        }
    }

    private fun combineMarkdown(items: List<ConvertedFile>): String = MarkdownMerger.combine(items)

    }
