package id.surabi.mdconverter

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/** SAF storage helpers shared by the activity and the conversion ViewModel. */
internal object DocumentStorage {
    fun loadOutputFolder(context: Context): Uri? {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val saved = preferences.getString("output_tree", null) ?: return null
        val uri = runCatching { Uri.parse(saved) }.getOrNull() ?: return null
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        if (!granted) preferences.edit().remove("output_tree").apply()
        return uri.takeIf { granted }
    }

    fun queryName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) {
                return cursor.getString(index)?.takeIf { it.isNotBlank() } ?: "dokumen"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "dokumen"
    }

    fun saveToFolder(context: Context, tree: Uri, requestedName: String, markdown: String): String {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val finalName = uniqueDocumentName(context, tree, rootId, requestedName)
        val output = DocumentsContract.createDocument(context.contentResolver, root, "text/markdown", finalName)
            ?: error("Tidak dapat membuat file pada folder tujuan.")
        try {
            (context.contentResolver.openOutputStream(output, "wt")
                ?: error("Tidak dapat membuka file keluaran."))
                .bufferedWriter(Charsets.UTF_8).use { it.write(markdown) }
            return finalName
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }
            throw error
        }
    }

    fun saveCachedFile(context: Context, tree: Uri, requestedName: String, cacheFile: File): String {
        require(cacheFile.isFile) { "Cache hasil konversi tidak ditemukan." }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val finalName = uniqueDocumentName(context, tree, rootId, requestedName)
        val output = DocumentsContract.createDocument(context.contentResolver, root, "text/markdown", finalName)
            ?: error("Tidak dapat membuat file pada folder tujuan.")
        try {
            val target = context.contentResolver.openOutputStream(output, "wt")
                ?: error("Tidak dapat membuka file keluaran.")
            target.buffered().use { destination ->
                cacheFile.inputStream().buffered().use { input ->
                    input.copyTo(destination, DEFAULT_BUFFER_SIZE)
                }
            }
            return finalName
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }
            throw error
        }
    }

    private fun uniqueDocumentName(context: Context, tree: Uri, rootId: String, requested: String): String {
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val existing = runCatching {
            context.contentResolver.query(
                childUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                buildSet {
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (cursor.moveToNext() && index >= 0) add(cursor.getString(index))
                }
            }.orEmpty()
        }.getOrDefault(emptySet())

        if (requested !in existing) return requested
        val dot = requested.lastIndexOf('.')
        val base = if (dot > 0) requested.substring(0, dot) else requested
        val extension = if (dot > 0) requested.substring(dot) else ""
        var index = 2
        while (base + " (" + index + ")" + extension in existing) index++
        return base + " (" + index + ")" + extension
    }
}
