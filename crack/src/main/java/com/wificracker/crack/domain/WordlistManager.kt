package com.wificracker.crack.domain

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.util.FileManager
import com.wificracker.crack.model.Wordlist
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordlistManager @Inject constructor(
    private val fileManager: FileManager,
    private val shellExecutor: ShellExecutor,
) {
    companion object {
        const val WORDLIST_DIR = "/data/local/tmp/wificracker/wordlists"

        val DOWNLOADABLE_WORDLISTS = listOf(
            DownloadableWordlist("rockyou", "RockYou (14M passwords, 134MB)", "https://github.com/brannondorsey/naive-hashcat/releases/download/data/rockyou.txt", 139921507),
            DownloadableWordlist("wifi-top1000", "WiFi Top 1000 (common WiFi passwords)", "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/WiFi-WPA/probable-v2-wpa-top4800.txt", 48000),
            DownloadableWordlist("common-passwords-10k", "Common 10K (most used passwords)", "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/Common-Credentials/10-million-password-list-top-10000.txt", 100000),
            DownloadableWordlist("darkweb-top10k", "Dark Web Top 10K", "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/darkweb2017-top10000.txt", 100000),
            DownloadableWordlist("probable-wpa", "Probable WPA (4800 WiFi passwords)", "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/WiFi-WPA/probable-v2-wpa-top4800.txt", 48000),
            DownloadableWordlist("french-passwords", "French passwords (common FR)", "https://raw.githubusercontent.com/danielmiessler/SecLists/master/Passwords/Leaked-Databases/bible-french.txt", 50000),
        )
    }

    data class DownloadableWordlist(val id: String, val label: String, val url: String, val estimatedSize: Long)

    fun ensureDir() { fileManager.ensureDirectory(WORDLIST_DIR) }

    fun getInstalledWordlists(): List<Wordlist> {
        ensureDir()
        return fileManager.listFiles(WORDLIST_DIR, "txt").map { file ->
            Wordlist(
                name = file.nameWithoutExtension.replace("_", " ").replaceFirstChar { it.uppercase() },
                path = file.absolutePath,
                size = file.length(),
                wordCount = countLines(file),
            )
        }
    }

    fun downloadWordlist(downloadable: DownloadableWordlist, onProgress: (String) -> Unit): Boolean {
        ensureDir()
        val destPath = "$WORDLIST_DIR/${downloadable.id}.txt"
        onProgress("Downloading ${downloadable.label}...")

        // Try curl first
        val curlResult = shellExecutor.executeAsRoot(
            "curl -sL -o '$destPath' '${downloadable.url}' && echo ok",
            timeoutSeconds = 300,
        )
        if (curlResult.stdout.contains("ok")) {
            val file = File(destPath)
            if (file.exists() && file.length() > 100) {
                onProgress("Downloaded: ${fileManager.fileSizeFormatted(file)}")
                return true
            }
        }

        // Try wget
        val wgetResult = shellExecutor.executeAsRoot(
            "wget -q -O '$destPath' '${downloadable.url}' && echo ok",
            timeoutSeconds = 300,
        )
        if (wgetResult.stdout.contains("ok")) {
            onProgress("Downloaded via wget")
            return true
        }

        // Try Termux curl
        val termuxCurl = shellExecutor.executeAsRoot(
            "/data/data/com.termux/files/usr/bin/curl -sL -o '$destPath' '${downloadable.url}' && echo ok",
            timeoutSeconds = 300,
        )
        if (termuxCurl.stdout.contains("ok")) {
            onProgress("Downloaded via Termux")
            return true
        }

        onProgress("Download failed")
        return false
    }

    fun deleteWordlist(path: String): Boolean {
        return fileManager.deleteFile(path)
    }

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

    private fun countLines(file: File): Long {
        return try {
            val result = shellExecutor.executeAsRoot("wc -l < '${file.absolutePath}'")
            result.stdout.trim().toLongOrNull() ?: 0
        } catch (e: Exception) { 0 }
    }
}
