package com.wificracker.core.util

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor() {
    fun ensureDirectory(path: String): File = File(path).also { if (!it.exists()) it.mkdirs() }
    fun listFiles(directory: String, extension: String? = null): List<File> { val dir = File(directory); if (!dir.exists()) return emptyList(); return dir.listFiles()?.filter { it.isFile && (extension == null || it.extension == extension) }?.sortedByDescending { it.lastModified() } ?: emptyList() }
    fun fileSizeFormatted(file: File): String { val b = file.length(); return when { b < 1024 -> "$b B"; b < 1048576 -> "${b/1024} KB"; b < 1073741824 -> "${b/1048576} MB"; else -> "${b/1073741824} GB" } }
    fun deleteFile(path: String): Boolean = File(path).delete()
}
