package com.wificracker.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.core.root.ShellExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModuleInfo(
    val name: String,
    val description: String,
    val isInstalled: Boolean = false,
    val isInstalling: Boolean = false,
    val checkPath: String,
    val packageName: String = "",
    val downloadUrl: String = "",
)

data class ModulesUiState(
    val modules: List<ModuleInfo> = emptyList(),
    val isChecking: Boolean = true,
    val installLog: String = "",
)

@HiltViewModel
class ModulesViewModel @Inject constructor(
    private val shellExecutor: ShellExecutor,
) : ViewModel() {

    companion object {
        private const val INSTALL_DIR = "/data/local/tmp/wificracker"
    }

    private val _uiState = MutableStateFlow(ModulesUiState())
    val uiState: StateFlow<ModulesUiState> = _uiState.asStateFlow()

    private val requiredModules = listOf(
        ModuleInfo("aircrack-ng", "WiFi password cracking suite", checkPath = "$INSTALL_DIR/aircrack-ng", packageName = "aircrack-ng"),
        ModuleInfo("airodump-ng", "WiFi network scanner and packet capture", checkPath = "$INSTALL_DIR/airodump-ng", packageName = "aircrack-ng"),
        ModuleInfo("aireplay-ng", "Deauthentication and packet injection", checkPath = "$INSTALL_DIR/aireplay-ng", packageName = "aircrack-ng"),
        ModuleInfo("hcxdumptool", "PMKID capture without clients", checkPath = "$INSTALL_DIR/hcxdumptool", packageName = "hcxdumptool"),
        ModuleInfo("hcxpcapngtool", "Convert .cap to .hc22000 hash format", checkPath = "$INSTALL_DIR/hcxpcapngtool", packageName = "hcxtools"),
        ModuleInfo("hashcat", "Advanced password recovery", checkPath = "$INSTALL_DIR/hashcat", packageName = "hashcat"),
        ModuleInfo("hostapd", "Rogue access point (Evil Twin)", checkPath = "$INSTALL_DIR/hostapd", packageName = "hostapd"),
        ModuleInfo("dnsmasq", "DHCP/DNS server for Evil Twin", checkPath = "$INSTALL_DIR/dnsmasq", packageName = "dnsmasq"),
        ModuleInfo("iw", "Wireless interface configuration", checkPath = "$INSTALL_DIR/iw", packageName = "iw"),
        ModuleInfo("wpa_driver", "MediaTek sniffer driver command", checkPath = "/data/local/tmp/wpa_driver"),
        ModuleInfo("ics_enable", "MediaTek ICS capture toggle", checkPath = "/data/local/tmp/ics_enable"),
    )

    init { checkModules() }

    fun checkModules() {
        _uiState.value = _uiState.value.copy(isChecking = true)
        viewModelScope.launch(Dispatchers.IO) {
            val checked = requiredModules.map { module ->
                val whichResult = shellExecutor.executeAsRoot("which ${module.name} 2>/dev/null")
                val pathResult = shellExecutor.executeAsRoot("test -x ${module.checkPath} && echo found")
                val installed = whichResult.isSuccess || pathResult.stdout.contains("found")
                module.copy(isInstalled = installed)
            }
            _uiState.value = ModulesUiState(modules = checked, isChecking = false)
        }
    }

    fun installFromTermux(moduleName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updateModuleInstalling(moduleName, true)
            appendLog("Installing $moduleName...")
            val result = tryInstallMethods(moduleName)
            if (result) {
                appendLog("$moduleName installed successfully!")
            } else {
                appendLog("✗ $moduleName: all methods failed")
            }
            updateModuleInstalling(moduleName, false)
            checkModules()
        }
    }

    fun installAllMissing() {
        viewModelScope.launch(Dispatchers.IO) {
            val missing = _uiState.value.modules.filter { !it.isInstalled }
            if (missing.isEmpty()) { appendLog("All modules already installed!"); return@launch }
            appendLog("Installing ${missing.size} missing modules...\n")

            // First ensure Termux packages are installed
            val packages = missing.mapNotNull { it.packageName.ifBlank { null } }.distinct()
            if (packages.isNotEmpty()) {
                appendLog("→ Installing Termux packages: ${packages.joinToString(", ")}")
                installTermuxPackages(packages)
            }

            for (module in missing) {
                updateModuleInstalling(module.name, true)
                appendLog("→ ${module.name}...")
                tryInstallMethods(module.name)
                updateModuleInstalling(module.name, false)
            }

            appendLog("\nDone. Refreshing status...")
            checkModules()
        }
    }

    private fun installTermuxPackages(packages: List<String>) {
        val termuxPkg = "/data/data/com.termux/files/usr/bin/pkg"

        // Check if Termux is installed
        val termuxCheck = shellExecutor.executeAsRoot("test -x $termuxPkg && echo found")
        if (!termuxCheck.stdout.contains("found")) {
            appendLog("  Termux not found. Install Termux from F-Droid first.")
            return
        }

        // Launch Termux via am broadcast with RUN_COMMAND intent
        // This runs the command inside Termux's own context (network, DNS, etc.)
        val pkgList = packages.joinToString(" ")
        val installCmd = "pkg update -y && pkg install -y $pkgList"
        val logFile = "/data/local/tmp/wificracker/termux_install.log"

        appendLog("  Launching Termux to install: $pkgList")
        appendLog("  This will open Termux on your screen...")

        // Use am to send RUN_COMMAND to Termux
        val amResult = shellExecutor.executeAsRoot(
            "am broadcast --user 0 " +
            "-n com.termux/.app.TermuxOpenReceiver " +
            "-a com.termux.RUN_COMMAND " +
            "--es com.termux.RUN_COMMAND_PATH /data/data/com.termux/files/usr/bin/bash " +
            "--esa com.termux.RUN_COMMAND_ARGUMENTS '-c','$installCmd 2>&1 | tee $logFile; echo DONE >> $logFile' " +
            "--es com.termux.RUN_COMMAND_WORKDIR /data/data/com.termux/files/home " +
            "2>&1",
        )

        if (!amResult.isSuccess) {
            // Fallback: try launching Termux with a script
            appendLog("  Intent failed, trying script method...")
            shellExecutor.executeAsRoot("mkdir -p $INSTALL_DIR")
            shellExecutor.executeAsRoot("echo '#!/data/data/com.termux/files/usr/bin/bash\n$installCmd 2>&1 | tee $logFile\necho DONE >> $logFile' > /data/local/tmp/termux_run.sh && chmod 755 /data/local/tmp/termux_run.sh")
            shellExecutor.executeAsRoot(
                "am start -n com.termux/.app.TermuxActivity 2>&1",
            )
            appendLog("  Termux opened. Run this in Termux:")
            appendLog("  bash /data/local/tmp/termux_run.sh")
        }

        // Wait for installation to complete (poll log file)
        appendLog("  Waiting for Termux installation...")
        shellExecutor.executeAsRoot("mkdir -p $INSTALL_DIR && rm -f $logFile")
        var waited = 0
        val maxWait = 300 // 5 minutes
        while (waited < maxWait) {
            Thread.sleep(3000)
            waited += 3
            val logCheck = shellExecutor.executeAsRoot("cat $logFile 2>/dev/null")
            if (logCheck.stdout.contains("DONE")) {
                appendLog("  ✓ Termux installation completed!")
                // Show last lines of log
                val lastLines = logCheck.stdout.lines().takeLast(5).joinToString("\n")
                appendLog("  $lastLines")
                return
            }
            if (waited % 15 == 0) {
                appendLog("  Still installing... (${waited}s)")
            }
        }
        appendLog("  ⚠ Timeout waiting for Termux. Check Termux manually.")
    }

    private fun tryInstallMethods(name: String): Boolean {
        shellExecutor.executeAsRoot("mkdir -p $INSTALL_DIR")

        // Method 1: Copy from Termux
        if (copyFromTermux(name)) return true

        // Method 2: Copy from Nethunter
        if (copyFromNethunter(name)) return true

        // Method 3: Download via curl/wget on device
        if (downloadBinary(name)) return true

        // Method 4: Copy from system PATH
        if (copyFromSystem(name)) return true

        return false
    }

    private fun copyFromTermux(name: String): Boolean {
        val termuxBin = "/data/data/com.termux/files/usr/bin/$name"
        val check = shellExecutor.executeAsRoot("test -x $termuxBin && echo found")
        if (check.stdout.contains("found")) {
            val copy = shellExecutor.executeAsRoot("cp $termuxBin $INSTALL_DIR/$name && chmod 755 $INSTALL_DIR/$name")
            if (copy.isSuccess) { appendLog("  ✓ Copied from Termux"); return true }
        }
        return false
    }

    private fun copyFromNethunter(name: String): Boolean {
        val paths = listOf(
            "/data/local/nhsystem/kali-arm64/usr/bin/$name",
            "/data/local/nhsystem/kali-arm64/usr/sbin/$name",
        )
        for (path in paths) {
            val check = shellExecutor.executeAsRoot("test -x $path && echo found")
            if (check.stdout.contains("found")) {
                val copy = shellExecutor.executeAsRoot("cp $path $INSTALL_DIR/$name && chmod 755 $INSTALL_DIR/$name")
                if (copy.isSuccess) { appendLog("  ✓ Copied from Nethunter"); return true }
            }
        }
        return false
    }

    private fun downloadBinary(name: String): Boolean {
        // Map binary names to their Termux package for direct download from Termux repos
        val module = requiredModules.find { it.name == name } ?: return false
        val pkg = module.packageName.ifBlank { return false }

        // Try downloading from Termux package repo using device's curl
        val curlCheck = shellExecutor.executeAsRoot("which curl 2>/dev/null || test -x /data/data/com.termux/files/usr/bin/curl && echo found")

        if (curlCheck.isSuccess || curlCheck.stdout.contains("found")) {
            val curlBin = if (shellExecutor.executeAsRoot("which curl 2>/dev/null").isSuccess) "curl" else "/data/data/com.termux/files/usr/bin/curl"

            // Try downloading a static ARM64 build from common pentesting repos
            val urls = listOf(
                "https://raw.githubusercontent.com/AdrianCX/android-aircrack/main/binaries/arm64/$name",
                "https://kali.download/nethunter-images/current/rootfs/kalifs-arm64/usr/bin/$name",
            )

            for (url in urls) {
                appendLog("  Trying download: ${url.substringAfterLast("/")}")
                val result = shellExecutor.executeAsRoot(
                    "$curlBin -sL -o $INSTALL_DIR/$name '$url' && chmod 755 $INSTALL_DIR/$name && test -s $INSTALL_DIR/$name && echo ok",
                    timeoutSeconds = 60,
                )
                if (result.stdout.contains("ok")) {
                    // Verify it's a real binary (not HTML error page)
                    val fileCheck = shellExecutor.executeAsRoot("file $INSTALL_DIR/$name")
                    if (fileCheck.stdout.contains("ELF") && fileCheck.stdout.contains("aarch64")) {
                        appendLog("  ✓ Downloaded ARM64 binary")
                        return true
                    } else {
                        shellExecutor.executeAsRoot("rm -f $INSTALL_DIR/$name")
                        appendLog("  Downloaded file is not a valid ARM64 binary")
                    }
                }
            }
        }

        return false
    }

    private fun copyFromSystem(name: String): Boolean {
        val whichResult = shellExecutor.executeAsRoot("which $name 2>/dev/null")
        if (whichResult.isSuccess) {
            val sysPath = whichResult.stdout.trim()
            val copy = shellExecutor.executeAsRoot("cp $sysPath $INSTALL_DIR/$name && chmod 755 $INSTALL_DIR/$name")
            if (copy.isSuccess) { appendLog("  ✓ Copied from system"); return true }
        }
        return false
    }

    private fun updateModuleInstalling(name: String, installing: Boolean) {
        _uiState.value = _uiState.value.copy(
            modules = _uiState.value.modules.map {
                if (it.name == name) it.copy(isInstalling = installing) else it
            }
        )
    }

    private fun appendLog(line: String) {
        _uiState.value = _uiState.value.copy(installLog = _uiState.value.installLog + line + "\n")
    }
}
