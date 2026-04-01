package com.example.sekairatingsystem.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

data class ScannedImageFile(
    val uri: Uri,
    val lastModified: Long?,
)

object ImageFileScanner {
    fun getImageFiles(context: Context, folderUri: Uri): List<ScannedImageFile> {
        val root = DocumentFile.fromTreeUri(context, folderUri)
            ?: return emptyList()

        val imageFiles = mutableListOf<ScannedImageFile>()
        val pendingDirectories = ArrayDeque<DocumentFile>()
        pendingDirectories.add(root)

        while (pendingDirectories.isNotEmpty()) {
            val directory = pendingDirectories.removeFirst()
            for (file in directory.listFiles()) {
                when {
                    file.isDirectory -> pendingDirectories.addLast(file)
                    file.isFile && file.isLikelyImageFile() -> imageFiles.add(
                        ScannedImageFile(
                            uri = file.uri,
                            lastModified = file.lastModified().takeIf { timestamp -> timestamp > 0L },
                        ),
                    )
                }
            }
        }

        return imageFiles.sortedBy { imageFile -> imageFile.uri.toString() }
    }

    fun getImageUris(context: Context, folderUri: Uri): List<Uri> =
        getImageFiles(context, folderUri).map(ScannedImageFile::uri)

    private fun DocumentFile.isLikelyImageFile(): Boolean {
        val typeHint = type
        if (typeHint?.startsWith("image/") == true) {
            return true
        }

        val nameHint = name?.lowercase(Locale.ROOT) ?: return false
        return nameHint.endsWith(".png") ||
            nameHint.endsWith(".jpg") ||
            nameHint.endsWith(".jpeg") ||
            nameHint.endsWith(".webp")
    }
}