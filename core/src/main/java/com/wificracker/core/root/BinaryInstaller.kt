package com.wificracker.core.root

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BinaryInstaller @Inject constructor(private val shellExecutor: ShellExecutor) {
    private val installDir = "/data/local/tmp/wificracker"

    fun installBinary(binaryName: String, assetSourceDir: String): ShellResult {
        val steps = listOf("mkdir -p $installDir", "cp $assetSourceDir/$binaryName $installDir/$binaryName", "chmod 755 $installDir/$binaryName")
        var lastResult = ShellResult(0, "", "")
        for (cmd in steps) { lastResult = shellExecutor.executeAsRoot(cmd); if (!lastResult.isSuccess) return lastResult }
        return lastResult
    }

    fun isBinaryInstalled(binaryName: String): Boolean {
        val result = shellExecutor.executeAsRoot("test -x $installDir/$binaryName && echo exists")
        return result.isSuccess && result.stdout.contains("exists")
    }

    fun listInstalledBinaries(): List<String> {
        val result = shellExecutor.executeAsRoot("ls $installDir/")
        if (!result.isSuccess) return emptyList()
        return result.stdout.lines().filter { it.isNotBlank() }
    }

    fun getBinaryPath(binaryName: String): String = "$installDir/$binaryName"
}
