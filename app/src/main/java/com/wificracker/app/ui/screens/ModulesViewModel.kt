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

    private val _uiState = MutableStateFlow(ModulesUiState())
    val uiState: StateFlow<ModulesUiState> = _uiState.asStateFlow()

    private val requiredModules = listOf(
        ModuleInfo("aircrack-ng", "WiFi password cracking suite", checkPath = "/data/local/tmp/wificracker/aircrack-ng"),
        ModuleInfo("airodump-ng", "WiFi network scanner and packet capture", checkPath = "/data/local/tmp/wificracker/airodump-ng"),
        ModuleInfo("aireplay-ng", "Deauthentication and packet injection", checkPath = "/data/local/tmp/wificracker/aireplay-ng"),
        ModuleInfo("hcxdumptool", "PMKID capture without clients", checkPath = "/data/local/tmp/wificracker/hcxdumptool"),
        ModuleInfo("hcxpcapngtool", "Convert .cap to .hc22000 hash format", checkPath = "/data/local/tmp/wificracker/hcxpcapngtool"),
        ModuleInfo("hashcat", "Advanced password recovery (GPU)", checkPath = "/data/local/tmp/wificracker/hashcat"),
        ModuleInfo("hostapd", "Rogue access point (Evil Twin)", checkPath = "/data/local/tmp/wificracker/hostapd"),
        ModuleInfo("dnsmasq", "DHCP/DNS server for Evil Twin", checkPath = "/data/local/tmp/wificracker/dnsmasq"),
        ModuleInfo("iw", "Wireless interface configuration", checkPath = "/data/local/tmp/wificracker/iw"),
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

            // Try multiple install methods
            val result = tryInstallMethods(moduleName)

            if (result) {
                appendLog("$moduleName installed successfully")
            } else {
                appendLog("Failed to install $moduleName. Please install manually via Termux or Nethunter.")
            }

            updateModuleInstalling(moduleName, false)
            checkModules()
        }
    }

    fun installAllMissing() {
        viewModelScope.launch(Dispatchers.IO) {
            val missing = _uiState.value.modules.filter { !it.isInstalled }
            appendLog("Installing ${missing.size} missing modules...")

            for (module in missing) {
                updateModuleInstalling(module.name, true)
                appendLog("→ ${module.name}...")
                tryInstallMethods(module.name)
                updateModuleInstalling(module.name, false)
            }

            appendLog("Done. Checking status...")
            checkModules()
        }
    }

    private fun tryInstallMethods(name: String): Boolean {
        val installDir = "/data/local/tmp/wificracker"
        shellExecutor.executeAsRoot("mkdir -p $installDir")

        // Method 1: Check if binary exists in Termux
        val termuxBin = "/data/data/com.termux/files/usr/bin/$name"
        val termuxCheck = shellExecutor.executeAsRoot("test -x $termuxBin && echo found")
        if (termuxCheck.stdout.contains("found")) {
            val copy = shellExecutor.executeAsRoot("cp $termuxBin $installDir/$name && chmod 755 $installDir/$name")
            if (copy.isSuccess) { appendLog("  Copied from Termux"); return true }
        }

        // Method 2: Check Nethunter chroot
        val nhBin = "/data/local/nhsystem/kali-arm64/usr/bin/$name"
        val nhSbin = "/data/local/nhsystem/kali-arm64/usr/sbin/$name"
        for (path in listOf(nhBin, nhSbin)) {
            val nhCheck = shellExecutor.executeAsRoot("test -x $path && echo found")
            if (nhCheck.stdout.contains("found")) {
                val copy = shellExecutor.executeAsRoot("cp $path $installDir/$name && chmod 755 $installDir/$name")
                if (copy.isSuccess) { appendLog("  Copied from Nethunter"); return true }
            }
        }

        // Method 3: Try apt/pkg install via Termux
        val aptResult = shellExecutor.executeAsRoot("su -c '/data/data/com.termux/files/usr/bin/pkg install -y $name 2>&1'", timeoutSeconds = 120)
        if (aptResult.isSuccess) {
            val copy = shellExecutor.executeAsRoot("cp $termuxBin $installDir/$name && chmod 755 $installDir/$name")
            if (copy.isSuccess) { appendLog("  Installed via Termux pkg"); return true }
        }

        // Method 4: Check system PATH
        val whichResult = shellExecutor.executeAsRoot("which $name 2>/dev/null")
        if (whichResult.isSuccess) {
            val sysPath = whichResult.stdout.trim()
            val copy = shellExecutor.executeAsRoot("cp $sysPath $installDir/$name && chmod 755 $installDir/$name")
            if (copy.isSuccess) { appendLog("  Copied from system"); return true }
        }

        appendLog("  ✗ Not found in Termux, Nethunter, or system PATH")
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
