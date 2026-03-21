package com.wificracker.core.root

import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BinaryInstaller @Inject constructor(private val shellExecutor: ShellExecutor) {
    companion object {
        const val INSTALL_DIR = "/data/local/tmp/wificracker"
        private const val LIB_DIR = "$INSTALL_DIR/lib"
    }

    fun installAllFromAssets(context: Context) {
        shellExecutor.executeAsRoot("mkdir -p $INSTALL_DIR $LIB_DIR")

        // Extract binaries from APK assets — always overwrite to ensure latest version
        val tmpDir = context.cacheDir.resolve("binaries_extract")
        tmpDir.mkdirs()

        try {
            // Copy main binaries (always overwrite)
            val binaries = context.assets.list("binaries")?.filter { it != "lib" } ?: emptyList()
            for (name in binaries) {
                val tmpFile = File(tmpDir, name)
                context.assets.open("binaries/$name").use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                }
                shellExecutor.executeAsRoot("cp ${tmpFile.absolutePath} $INSTALL_DIR/$name")
                shellExecutor.executeAsRoot("chmod 755 $INSTALL_DIR/$name")
                tmpFile.delete()
            }

            // Copy shared libraries
            val libs = context.assets.list("binaries/lib") ?: emptyArray()
            for (lib in libs) {
                val tmpFile = File(tmpDir, lib)
                context.assets.open("binaries/lib/$lib").use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                }
                shellExecutor.executeAsRoot("cp ${tmpFile.absolutePath} $LIB_DIR/$lib")
                shellExecutor.executeAsRoot("chmod 644 $LIB_DIR/$lib")
                tmpFile.delete()
            }
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    fun installBinary(binaryName: String, assetSourceDir: String): ShellResult {
        val steps = listOf("mkdir -p $INSTALL_DIR", "cp $assetSourceDir/$binaryName $INSTALL_DIR/$binaryName", "chmod 755 $INSTALL_DIR/$binaryName")
        var lastResult = ShellResult(0, "", "")
        for (cmd in steps) { lastResult = shellExecutor.executeAsRoot(cmd); if (!lastResult.isSuccess) return lastResult }
        return lastResult
    }

    fun isBinaryInstalled(binaryName: String): Boolean {
        val result = shellExecutor.executeAsRoot("test -x $INSTALL_DIR/$binaryName && echo exists")
        return result.isSuccess && result.stdout.contains("exists")
    }

    fun listInstalledBinaries(): List<String> {
        val result = shellExecutor.executeAsRoot("ls $INSTALL_DIR/")
        if (!result.isSuccess) return emptyList()
        return result.stdout.lines().filter { it.isNotBlank() }
    }

    fun getBinaryPath(binaryName: String): String = "$INSTALL_DIR/$binaryName"
}
