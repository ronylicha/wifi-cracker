package com.wificracker.crack.domain

import com.wificracker.core.util.FileManager
import com.wificracker.crack.model.Wordlist
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordlistManager @Inject constructor(private val fileManager: FileManager) {
    companion object { private const val WORDLIST_DIR = "/data/local/tmp/wificracker/wordlists" }

    fun getBuiltInWordlists(): List<Wordlist> = listOf(
        Wordlist(name = "WiFi Top 1000", path = "$WORDLIST_DIR/wifi_top1000.txt", isBuiltIn = true),
        Wordlist(name = "Common Passwords", path = "$WORDLIST_DIR/common_passwords.txt", isBuiltIn = true),
    )

    fun getCustomWordlists(): List<Wordlist> {
        return fileManager.listFiles(WORDLIST_DIR, "txt").filter { !it.name.startsWith("wifi_") && !it.name.startsWith("common_") }.map { file ->
            Wordlist(name = file.nameWithoutExtension, path = file.absolutePath, size = file.length(), wordCount = file.readLines().size.toLong())
        }
    }

    fun getAllWordlists(): List<Wordlist> = getBuiltInWordlists() + getCustomWordlists()

    fun importWordlist(sourcePath: String, name: String): Wordlist {
        fileManager.ensureDirectory(WORDLIST_DIR)
        val dest = "$WORDLIST_DIR/$name.txt"
        File(sourcePath).copyTo(File(dest), overwrite = true)
        val file = File(dest)
        return Wordlist(name = name, path = dest, size = file.length(), wordCount = file.readLines().size.toLong())
    }
}
