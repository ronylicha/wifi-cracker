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

    /**
     * Load mt76/mt7921 kernel modules for USB WiFi adapters (Alfa AWUS036AXML).
     * Modules are stored in /data/local/tmp/wificracker/ and loaded in dependency order.
     * Safe to call even without an adapter connected — modules load but stay idle.
     */
    fun loadUsbWifiModules(): Boolean {
        val modules = listOf("mt76.ko", "mt76-usb.ko", "mt76-connac-lib.ko", "mt7921-common.ko", "mt7921u.ko")
        val firmware = listOf("WIFI_MT7961_patch_mcu_1_2_hdr.bin", "WIFI_RAM_CODE_MT7961_1.bin")

        // Check if mt7921u already loaded
        val loaded = shellExecutor.executeAsRoot("lsmod | grep mt7921u")
        if (loaded.stdout.contains("mt7921u")) return true

        // Check if all module files exist
        val allPresent = modules.all { mod ->
            shellExecutor.executeAsRoot("test -f $INSTALL_DIR/$mod && echo yes").stdout.contains("yes")
        }
        if (!allPresent) return false

        // Install firmware to vendor overlay via Magisk (if not already there)
        val fwDir = "/data/adb/modules/mt76_usb/system/vendor/firmware/mediatek"
        shellExecutor.executeAsRoot("mkdir -p $fwDir")
        for (fw in firmware) {
            shellExecutor.executeAsRoot("test -f $fwDir/$fw || cp $INSTALL_DIR/$fw $fwDir/$fw 2>/dev/null")
        }

        // Load modules in order — each depends on the previous
        for (mod in modules) {
            val result = shellExecutor.executeAsRoot("insmod $INSTALL_DIR/$mod 2>&1")
            // Ignore "File exists" (already loaded)
            if (!result.isSuccess && !result.stderr.contains("File exists")) {
                // Non-fatal: adapter might not be connected, module loads but has no device
                continue
            }
        }

        // Check if wlan1 appeared (USB adapter detected)
        val iface = shellExecutor.executeAsRoot("ip link show wlan1 2>/dev/null")
        return iface.isSuccess
    }
}
