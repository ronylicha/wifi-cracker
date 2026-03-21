# Architecture — WiFi Cracker

> Android WiFi penetration testing application
> Platform: Android 12+ (API 31+) | Language: Kotlin 2.0 | Build: Gradle KTS

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [Module Dependency Graph](#2-module-dependency-graph)
3. [Module Reference](#3-module-reference)
4. [Cross-Module Data Flow](#4-cross-module-data-flow)
5. [State Management](#5-state-management)
6. [Dependency Injection Graph](#6-dependency-injection-graph)
7. [Build System](#7-build-system)
8. [Security Considerations](#8-security-considerations)

---

## 1. High-Level Architecture

The application follows a **feature-modular, unidirectional data flow** architecture. Each pentest phase (scan, attack, crack, report) lives in its own Android library module. The `core` module provides shared infrastructure. The `app` module wires navigation and owns the Application class.

```
+------------------------------------------------------------------+
|                         USER INTERFACE                           |
|                                                                  |
|  +-----------+  +-----------+  +-----------+  +---------------+ |
|  |ScanScreen |  |AttackDash |  |CrackDash  |  |ReportDashboard| |
|  +-----+-----+  +-----+-----+  +-----+-----+  +------+--------+ |
|        |               |               |               |         |
|  +-----v-----+  +-----v-----+  +-----v-----+  +------v--------+ |
|  |ScanVM     |  |AttackVM   |  |CrackVM    |  |ReportVM       | |
|  +-----+-----+  +-----+-----+  +-----+-----+  +------+--------+ |
|        |               |               |               |         |
+--------+---------------+---------------+---------------+---------+
         |         DOMAIN / ORCHESTRATION LAYER          |
  +------v------+  +------------------+  +--------------v------+
  |ScanEngine   |  |AttackOrchestrator|  |CrackOrchestrator    |
  +------+------+  +------------------+  +---------------------+
         |
+--------+--------------------------------------------------------+
|                         CORE SERVICES                           |
|                                                                 |
|  +---------------------+    +------------------------------+   |
|  | SelectedNetworkRepo |    |       SessionCollector        |   |
|  | StateFlow<Network?> |    |  StateFlow<SessionData>       |   |
|  +---------------------+    +------------------------------+   |
|                                                                 |
|  +---------------+  +----------------+  +------------------+   |
|  | ShellExecutor |  | BinaryInstaller|  |   AuditLogger    |   |
|  | (root shell)  |  | (aircrack-ng   |  |   (JSONL/disk)   |   |
|  +---------------+  |  suite)        |  +------------------+   |
|                     +----------------+                          |
|  +-------------------------------------------------------------+ |
|  |          WiFi Hardware Abstraction Layer                    | |
|  | InterfaceManager  ChipsetDetector  MonitorModeManager       | |
|  | UsbWifiDetector   ChipsetMonitorHelper  MtkMonitorCapture   | |
|  +-------------------------------------------------------------+ |
|                                                                 |
|  +-----------------------+   +------------------------------+  |
|  |  Room AppDatabase     |   |  PentestForegroundService    |  |
|  |  (vulns)              |   |  JobQueue + ProgressBcast.   |  |
|  +-----------------------+   +------------------------------+  |
+-----------------------------------------------------------------+
        | root su shell                    | Android APIs
        v                                  v
  +-----------------+             +------------------+
  | aircrack-ng     |             | WifiManager API  |
  | aireplay-ng     |             | /proc /sys fs    |
  | airodump-ng     |             | /dev/fw_log_ics  |
  | airmon-ng       |             |  (MTK ICS dev)   |
  | /data/local/    |             +------------------+
  |  tmp/wificracker|
  +-----------------+
```

### Startup Sequence

```
Application.onCreate()
  +--> Hilt component graph initialised
  +--> VulnDatabase.seedOrUpdate()        <- seeds vulns.json into Room
  +--> BinaryInstaller.installAllFromAssets() <- deploys aircrack suite

MainActivity.onCreate()
  +--> AppNavGraph (Compose)
         +--> isLoading=true       -> CircularProgressIndicator
         +--> isRooted=false       -> RootErrorScreen (terminal, no proceed)
         +--> disclaimerAccepted=false -> DisclaimerScreen (legal gate)
         +--> all checks pass      -> MainDashboard (NavHost)
```

---

## 2. Module Dependency Graph

```
+---------------------------------------------+
|                   :app                      |
| (application, navigation, theme, AppModule) |
+------+------+--------+-------+--------------+
       |      |        |       |
       v      v        v       v
   :scan  :attack   :crack  :report
       |      |        |       |
       |      +--------+       |
       |      v                |
       +-->:core<--------------+
```

**Dependency rules (enforced by Gradle declarations):**

| Module  | Depends on          | Notes                                                   |
|---------|---------------------|---------------------------------------------------------|
| `app`   | core, scan, attack, crack, report | Orchestration only, no business logic     |
| `scan`  | core                | Reads VulnDao, uses ShellExecutor, MonitorMode          |
| `attack`| core, scan          | Uses scan.WifiInterface model for target selection      |
| `crack` | core                | Uses BinaryInstaller for aircrack-ng path               |
| `report`| core, scan          | Reads ScanEngine.scanState, VulnMatcher                 |
| `core`  | (none)              | Zero upward dependencies                                |

**Constraint:** No circular dependencies. `attack` depends on `scan` solely for the `WifiInterface` model type. Feature modules never depend on each other except this one permitted edge.

---

## 3. Module Reference

### 3.1 `app` Module

**Package:** `com.wificracker.app`
**Type:** `com.android.application`

**Responsibility:** Entry point, navigation host, application lifecycle, theme, and legal gate. Contains no pentest business logic.

**Key Classes:**

| Class | Role |
|-------|------|
| `WifiCrackerApp` | `@HiltAndroidApp` Application subclass. Triggers VulnDB seed and binary installation on startup via a supervised `CoroutineScope(Dispatchers.IO)`. |
| `MainActivity` | Single-activity host. Installs splash screen, enables edge-to-edge, sets Compose content. |
| `AppNavGraph` | Root composable. Collects `MainViewModel.uiState` and gates the entire UI behind root detection and legal disclaimer acceptance. |
| `MainDashboard` | Inner NavHost with `BottomNavBar` (Scan / Attack / Crack / Reports) and a `ModalNavigationDrawer` for secondary screens (Company Profile, Clients, VulnDB, Audit Log, Wordlists, Settings). |
| `MainViewModel` | Checks `RootChecker.isRooted()` and reads `SharedPreferences` for disclaimer flag. Exposes `MainUiState` via `StateFlow`. |
| `AppModule` | Hilt `@Singleton` module. Provides `SharedPreferences` and the `Map<String, String>` OUI vendor lookup table (parsed from `assets/oui.tsv`). |

**Navigation Routes:**

```
BottomNavTab.Scan        -> ScanScreen
BottomNavTab.Attack      -> AttackDashboard
BottomNavTab.Crack       -> CrackDashboard
BottomNavTab.Reports     -> ReportDashboard
network_detail/{bssid}   -> NetworkDetailScreen (reuses ScanViewModel from backstack)
company_profile          -> CompanyProfileScreen
client_list              -> ClientListScreen
client_edit/{id}         -> ClientEditScreen
vuln_database            -> VulnDatabaseScreen
audit_log                -> AuditLogScreen
wordlists                -> WordlistScreen
settings                 -> SettingsScreen
about                    -> AboutScreen (inline composable)
```

**Permissions declared in `AndroidManifest.xml`:**

```
ACCESS_WIFI_STATE, CHANGE_WIFI_STATE
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
NEARBY_WIFI_DEVICES
FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE
POST_NOTIFICATIONS
```

`android.allowBackup = false` prevents ADB backup of sensitive capture data.

---

### 3.2 `core` Module

**Package:** `com.wificracker.core`
**Type:** `com.android.library`

**Responsibility:** All shared infrastructure. No UI. Consumed by every other module.

#### Sub-packages

##### `root/` — Root Shell Execution

| Class | Role |
|-------|------|
| `ShellExecutor` | Executes shell commands via `Runtime.getRuntime().exec()` (unprivileged) or `ProcessBuilder("su")` (root). Returns `ShellResult(exitCode, stdout, stderr)`. Enforces configurable timeout (default 30s). |
| `RootChecker` | Calls `su -c id` and checks for `uid=0`. Also detects root type: `MAGISK`, `KERNELSU`, `SUPERSU`, or `UNKNOWN`. |
| `BinaryInstaller` | Extracts the aircrack-ng binary suite from APK assets to `/data/local/tmp/wificracker/` with `chmod 755`. Skips already-installed binaries. Handles shared library extraction to `lib/` subdirectory. |
| `RootType` | Enum: `NONE`, `MAGISK`, `KERNELSU`, `SUPERSU`, `UNKNOWN`. |
| `ShellResult` | Data class: `exitCode: Int`, `stdout: String`, `stderr: String`. Property `isSuccess = exitCode == 0`. |

**Installed binary paths:**

```
/data/local/tmp/wificracker/
  aircrack-ng
  aireplay-ng
  airodump-ng
  airmon-ng
  lib/
    libaircrack-ce-wpa-1.7.0.so
    libaircrack-ce-wpa-arm-neon-1.7.0.so
    libaircrack-ce-wpa.so
    libaircrack-osdep-1.7.0.so
    libaircrack-osdep.so
```

##### `wifi/` — Hardware Abstraction

| Class | Role |
|-------|------|
| `InterfaceManager` | Lists all WiFi interfaces using `iw dev` with fallback to `/proc/net/wireless` and `/sys/class/net`. Merges internal and USB adapters. Returns `List<WifiInterface>`. |
| `ChipsetDetector` | Reads driver from `/sys/class/net/<iface>/device/driver` symlink. Checks driver name against a known set of monitor-capable drivers (ath9k, mt76, rt2800usb, etc.). |
| `ChipsetMonitorHelper` | Detects chip vendor (Qualcomm/Broadcom/MediaTek) from sysfs/properties. Implements vendor-specific monitor mode enable/disable sequences. For MTK, verifies the patched driver SHA-256 and patch version (v3 or v4). |
| `MonitorModeManager` | Facade over `ChipsetMonitorHelper`. Detects current monitor mode state from `iw dev info` or `/sys/class/net/<iface>/type` (value 803 = monitor). |
| `UsbWifiDetector` | Enumerates USB network interfaces, reads `idVendor:idProduct`, and matches against a hardcoded table of known monitor-capable chipsets (Realtek RTL881x, Ralink RT3070/5370, Atheros AR9271, MediaTek MT7921). |
| `WifiInterface` | Data class: name, macAddress, chipset, driver, supportsMonitor, isMonitorMode. |
| `ChipsetMonitorCapability` | Data class: vendor, chipName, supportsInternalMonitor, monitorMethod, patchInstalled, patchVersion, supportsRawTx. |

**MediaTek ICS (Internal Capture System):**

```
monitor/
  MtkMonitorCapture  -- Reads raw IEEE 802.11 frames from /dev/fw_log_ics
                        via SNIFFER command + ics_enable binary.
                        Emits Flow<Ieee80211Frame> via channelFlow.
  IcsPacketParser    -- Parses the proprietary MTK ICS packet format.
  Ieee80211Parser    -- Extracts BSSID (addr3), SSID, channel, RSSI
                        from raw IEEE 802.11 management frames.
```

Chipset vendor detection flow:

```
ChipsetMonitorHelper.detectChipVendor()
  +--> /sys/module/wlan/parameters/con_mode  exists?  -> QUALCOMM (QCACLD con_mode)
  +--> /sys/module/bcmdhd* or dhd/parameters exists?  -> BROADCOM (Nexmon)
  +--> getprop ro.vendor.wlan.gen not blank?           -> MEDIATEK
       +--> sha256 wlan_drv_gen4m_6878.ko              -> patchInstalled (v3/v4)
       +--> sha256 cfg80211.ko                         -> supportsRawTx
  +--> none matched                                    -> UNKNOWN
```

##### `service/` — Job and Progress Infrastructure

| Class | Role |
|-------|------|
| `PentestForegroundService` | Android `Service` with `START_STICKY`. Manages a `JobQueue` and broadcasts progress. Channel ID: `wificracker_service`. |
| `JobQueue` | Thread-safe `ConcurrentLinkedQueue<Job>`. Operations: enqueue, dequeue, cancel(id), clear, peek, size. |
| `Job` | Sealed interface with three subtypes: `ScanJob`, `AttackJob`, `CrackJob`. Each has an `id` (UUID) and `description`. |
| `JobStatus` | Enum: `QUEUED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`. |
| `ProgressBroadcaster` | Singleton. Holds `MutableStateFlow<JobProgress>`. Any component calls `update()` to push live progress. |
| `SelectedNetworkRepository` | Singleton. Cross-module shared state. See Section 4.1. |
| `SessionCollector` | Singleton. Cross-module session aggregator. See Section 4.2. |

##### `database/` — Room Persistence

| Class | Role |
|-------|------|
| `AppDatabase` | `@Database(entities = [VulnEntity], version = 1)`. Single-table database. Schema exported to `core/schemas/`. |
| `VulnDatabase` | Seeder service. Reads `assets/vulns.json` on startup and inserts rows if DB count is below asset count. |
| `VulnDao` | Room DAO. Key query: `getByProtocol(protocol: String): Flow<List<VulnEntity>>`. Also `count(): Int`. |
| `VulnEntity` | Columns: cveId, protocol, title, description, severity, cvssScore (Float), recommendation, affectedVersions. |

##### `logging/` — Audit Trail

| Class | Role |
|-------|------|
| `AuditLogger` | Writes newline-delimited JSON (`audit.jsonl`) to `filesDir/audit_logs/`. Uses `kotlinx.coroutines.sync.Mutex` for write concurrency. Supports `log()`, `getEntries()`, `exportJson()`, `purge()`. |
| `AuditEntry` | `@Serializable` data class: timestamp, action, module, target, result, details. |

**Log actions used across modules:**

```
SCAN_START / SCAN_STOP / SCAN_ERROR
ATTACK_START / ATTACK_STOP
CRACK_START / CRACK_DONE / CRACK_STOP
MTK_CAPTURE_ENABLED / MTK_CAPTURE_DISABLED / MTK_CAPTURE_FATAL
ICS_ENABLE_FAILED
```

##### `model/`

| Class | Role |
|-------|------|
| `SelectedNetwork` | Minimal shared representation of a targeted network: bssid, ssid, channel, encryption, signalStrength. Used by scan to publish, by attack/crack to consume. |

##### `util/`

| Class | Role |
|-------|------|
| `FileManager` | Utility for managing capture files and wordlists on-device. |
| `MacVendorLookup` | Resolves MAC OUI prefix to vendor name using the injected `Map<String, String>` from `AppModule`. |

**Hilt modules in `core`:**

| Module | Component | Provides |
|--------|-----------|----------|
| `CoreModule` | `SingletonComponent` | `AuditLogger` (path = `filesDir/audit_logs`) |
| `DatabaseModule` | `SingletonComponent` | `AppDatabase`, `VulnDao` |

---

### 3.3 `scan` Module

**Package:** `com.wificracker.scan`
**Type:** `com.android.library` with Compose

**Responsibility:** WiFi network discovery. Dual-path scanning: Android `cmd wifi` shell API (always available) and MTK ICS passive monitor capture (MTK patched driver only). Vulnerability matching against the VulnDB.

**Key Classes:**

| Class | Role |
|-------|------|
| `ScanEngine` | `@Singleton`. Central scan controller. Owns `MutableStateFlow<ScanResult>`. Launches `androidScanFlow` (polling `cmd wifi list-scan-results` every 8s) and optionally `MtkMonitorCapture` in parallel. Merges results by BSSID. Logs to `AuditLogger`. |
| `WifiCommandRunner` | `@Singleton`. Wraps airodump-ng: starts it with `--output-format csv --background 1`, then polls the CSV file every 2s. Parses networks and client station sections. |
| `ScanViewModel` | `@HiltViewModel`. Bridges UI to `ScanEngine`, `VulnMatcher`, `ChannelHopper`, `PcapExporter`, `SelectedNetworkRepository`, and `SessionCollector`. Exposes `ScanUiState` via `StateFlow`. |
| `NetworkAnalyzer` | Analyzes signal trends and network characteristics. |
| `VulnMatcher` | Queries `VulnDao.getByProtocol()` for each scanned network's encryption type. Adds hardcoded entries for OPEN networks (CVSS 10.0) and WPS-enabled networks (CVSS 7.0). Returns `Map<BSSID, List<VulnMatch>>`. |
| `ChannelHopper` | Cycles through 2.4 GHz and 5 GHz channels on the selected interface. Emits `Flow<Int>` (current channel). |
| `PcapExporter` | Starts a PCAP capture session on demand. |

**Models:**

| Model | Fields |
|-------|--------|
| `Network` | bssid, ssid, channel, frequency, signalStrength, encryption (EncryptionType), cipher, authentication, wps, clients, firstSeen, lastSeen |
| `EncryptionType` | Enum: OPEN (CRITICAL), WEP (CRITICAL), WPA (HIGH), WPA2 (MEDIUM), WPA3 (LOW), UNKNOWN (HIGH) |
| `ScanResult` | interfaceName, status (ScanStatus), networks, duration, timestamp |
| `Client` | macAddress, bssid, signalStrength, packets, probeRequests |
| `VulnMatch` | cveId, title, severity, cvssScore, recommendation |

**UI Components:**

```
ScanScreen
  +-- NetworkCard             -- per-network row: signal indicator, encryption badge, vuln badge
  +-- SignalStrengthIndicator
  +-- EncryptionBadge         -- color-coded by RiskLevel
  +-- VulnBadge               -- shows count of matched CVEs
  +-- ClientList              -- associated station list

NetworkDetailScreen           -- full network detail + vuln list + "Launch Attack" CTA
```

**Scan engine dual-path logic:**

```
ScanEngine.startScan(interfaceName)
  +-- startAndroidScan()           <- always runs
  |     cmd wifi start-scan
  |     delay 3s
  |     cmd wifi list-scan-results -> parse -> emit
  |     delay 5s -> loop
  |
  +-- (if MTK patched driver detected)
        startIcsCapture()
          MtkMonitorCapture.enableCapture()    <- SNIFFER + ics_enable 1
          MtkMonitorCapture.startCapture()     <- reads /dev/fw_log_ics
            IcsPacketParser.parsePacket()
            Ieee80211Parser.parseFrame()       <- beacon/probe resp -> Network
          mergeNetworks(existing, incoming)    <- by BSSID, best signal wins
```

---

### 3.4 `attack` Module

**Package:** `com.wificracker.attack`
**Type:** `com.android.library` with Compose

**Responsibility:** Orchestrates all active attack types against a selected target. Receives target from `SelectedNetworkRepository`. Implements the Strategy pattern for attacks.

**Key Classes:**

| Class | Role |
|-------|------|
| `AttackOrchestrator` | `@Singleton`. Central controller. Runs `PrerequisiteCheck` before launching. Dispatches to the correct `WifiAttack` impl. Collects `Flow<String>` console output. Records completed attacks into `SessionCollector`. |
| `AttackViewModel` | `@HiltViewModel`. Observes `SelectedNetworkRepository` to pre-populate target fields. Forwards UI actions to `AttackOrchestrator`. |
| `PrerequisiteCheck` | Validates that required binaries exist and the interface is in monitor mode before any attack. Returns `PrerequisiteResult(satisfied, missingPrerequisites)`. |

**Attack Strategy Interface:**

```kotlin
interface WifiAttack {
    fun execute(attack: Attack): Flow<String>   // emits live console lines
    fun stop(attack: Attack)
}
```

**Attack Implementations:**

| Class | AttackType | Mechanism |
|-------|-----------|-----------|
| `DeauthAttack` | `DEAUTH` | MTK path: `wpa_driver AP_STA_DISASSOC` (50 frames, 100ms interval). Standard path: `aireplay-ng --deauth 0`. |
| `HandshakeCapture` | `HANDSHAKE_CAPTURE` | Triggers deauth to force reconnect, captures WPA 4-way handshake with airodump-ng. |
| `PmkidCapture` | `PMKID_CAPTURE` | Captures PMKID hash from Association frames without requiring a connected client. |
| `EvilTwinAttack` | `EVIL_TWIN` | Creates a rogue AP cloning the target SSID. |
| `ProbeSniff` | `PROBE_SNIFF` | Passively captures probe request frames to enumerate client preferred networks. |

**Models:**

| Model | Fields |
|-------|--------|
| `Attack` | type (AttackType), targetBssid, targetSsid, interfaceName, status (AttackStatus), startTime, endTime |
| `AttackType` | Enum: DEAUTH, HANDSHAKE_CAPTURE, PMKID_CAPTURE, EVIL_TWIN, PROBE_SNIFF |
| `AttackStatus` | Enum: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED |
| `Capture` | Path to captured file (.cap / .hccapx) |

**UI Components:**

```
AttackDashboard
  +-- TargetSelector    -- shows pre-selected target from SelectedNetworkRepository
  +-- AttackTypeCard    -- one card per AttackType with description and risk label
  +-- LiveConsole       -- real-time scrolling output of Flow<String> console lines
```

---

### 3.5 `crack` Module

**Package:** `com.wificracker.crack`
**Type:** `com.android.library` with Compose

**Responsibility:** Offline password cracking against captured WPA handshakes or PMKID hashes. Implements multiple cracking strategies. Reports results to `SessionCollector`.

**Key Classes:**

| Class | Role |
|-------|------|
| `CrackOrchestrator` | `@Singleton`. Receives a `CrackJob`. If no hash file is provided, calls `HashConverter.convertCapToHc22000()` first. Delegates to the selected `CrackStrategyImpl`. Emits `CrackProgress` and final `CrackResult`. Records to `SessionCollector`. |
| `HashConverter` | Converts `.cap` / `.pcap` files to `hc22000` hashcat-compatible format. |
| `WordlistManager` | Scans the device filesystem for wordlist files. Returns `List<Wordlist>` with metadata (path, size, line count). |
| `CrackViewModel` | `@HiltViewModel`. Observes `SelectedNetworkRepository` to auto-fill BSSID/SSID. Manages wordlist selection and crack job lifecycle. |

**Cracking Strategy Interface:**

```kotlin
interface CrackStrategyImpl {
    fun execute(job: CrackJob): Flow<CrackProgress>
    fun stop()
}
```

**Strategy Implementations:**

| Class | Strategy | Mechanism |
|-------|----------|-----------|
| `DictionaryAttack` | `DICTIONARY` | `aircrack-ng -w <wordlist> -b <bssid> <hashfile>`. Timeout 3600s. Parses `KEY FOUND!` from stdout. |
| `BruteForceAttack` | `BRUTE_FORCE` | Systematic character-set enumeration with progressive key length. |
| `RuleBasedAttack` | `RULE_BASED` | Applies mutation rules (leet speak, capitalisation, numeric suffixes) to wordlist entries. |
| `CombinatorAttack` | `COMBINATOR` | Combines pairs of words from two wordlists. |

**Models:**

| Model | Fields |
|-------|--------|
| `CrackJob` | id (UUID), capturePath, hashPath, targetBssid, targetSsid, strategy, wordlistPath |
| `CrackProgress` | jobId, status (CrackStatus), progress (0.0-1.0), currentKey, keysTested, message |
| `CrackResult` | jobId, success, password, duration |
| `CrackStrategy` | Enum: DICTIONARY, BRUTE_FORCE, RULE_BASED, COMBINATOR |
| `CrackStatus` | Enum: IDLE, CONVERTING, RUNNING, COMPLETED, FAILED, CANCELLED |
| `Wordlist` | path, name, sizeBytes, lineCount |

**UI Components:**

```
CrackDashboard
  +-- ProgressGauge    -- animated radial gauge showing crack progress (0-100%)
```

---

### 3.6 `report` Module

**Package:** `com.wificracker.report`
**Type:** `com.android.library` with Compose, Serialization, Coil

**Responsibility:** Aggregates results from all pentest phases via `SessionCollector` and direct `ScanEngine` state reads, produces a structured security report, and exports it as PDF, HTML, or JSON.

**Key Classes:**

| Class | Role |
|-------|------|
| `ReportGenerator` | Pure function: takes `MissionInfo`, `CompanyProfile`, `List<Finding>`, optional manual recommendations, and `SessionStats`. Calls `AutoRecommender`, `RiskRating`, and `CvssCalculator`. Returns an immutable `Report`. |
| `DataAggregator` | Converts raw session data into typed `Finding` objects. Handles: network encryption findings, VulnDB matches, attack outcome findings (Deauth -> PMF advisory, Handshake -> CVSS 7.5, EvilTwin -> CVSS 9.0), cracked password findings (CVSS 10.0). |
| `CvssCalculator` | Maps CVSS float score to severity label. Computes per-finding severity. |
| `RiskRating` | Aggregates all findings into a `RiskSummary` with counts per severity and an overall letter grade (A-F). |
| `AutoRecommender` | Generates `Recommendation` objects from finding patterns. Deduplicates and sorts by priority. |
| `ExportManager` | Produces PDF (Android `PdfDocument` API, A4, dark theme), HTML (self-contained), or JSON. Writes to `/data/local/tmp/wificracker/reports/`. |
| `ReportViewModel` | `@HiltViewModel`. Implements a 4-step wizard. The `collectSessionResults()` method reads from `SessionCollector`, `ScanEngine`, and `VulnMatcher` to auto-populate findings. |

**Report Wizard Steps:**

```
ReportStep.MISSION_INFO   -> enter pentest title, scope, select client
ReportStep.FINDINGS       -> review auto-collected + add manual findings
ReportStep.PREVIEW        -> generated executive summary and full report
ReportStep.EXPORT         -> choose PDF / HTML / JSON and export
```

**Report Database (ReportDatabase):**

```
wificracker_reports.db
  company_profile   -- CompanyProfileEntity (name, address, SIRET, certifications, logo)
  client_profiles   -- ClientProfileEntity (companyName, contactName, contractReference, logo)
```

Two separate Room databases exist: `wificracker.db` (core, vulns) and `wificracker_reports.db` (report module, company/client profiles).

**Models:**

| Model | Fields |
|-------|--------|
| `Report` | id, missionInfo, companyProfile, findings, recommendations, overallScore, executiveSummary, status, createdAt |
| `Finding` | id, title, description, severity (Severity), cvssScore, evidence, networkBssid, networkSsid, recommendation |
| `MissionInfo` | title, scope, date, clientProfile |
| `CompanyProfile` | id, name, address, siret, contactName/Email/Phone, certifications, legalMention, logoPath |
| `ClientProfile` | companyName, address, contactName/Title/Email, contractReference, logoPath |
| `ExportFormat` | Enum: PDF, HTML, JSON |
| `Severity` | Enum: CRITICAL (CVSS 9+), HIGH (7-9), MEDIUM (4-7), LOW (0.1-4), INFO (0) |

---

## 4. Cross-Module Data Flow

### 4.1 `SelectedNetworkRepository`

`SelectedNetworkRepository` is the primary inter-module communication channel for target selection. It is a `@Singleton` with no external dependencies, living entirely in `:core`.

```
Location: core/service/SelectedNetworkRepository.kt
Scope:    @Singleton (Hilt SingletonComponent)
State:    MutableStateFlow<SelectedNetwork?>
```

**Full data flow for "select network and attack":**

```
[User taps a network in ScanScreen]
        |
        v
ScanViewModel.selectNetwork(bssid)
  +--> SelectedNetworkRepository.select(
         SelectedNetwork(bssid, ssid, channel, encryption, signalStrength)
       )
        |
        |  StateFlow emission (hot, shared singleton)
        |
        +--------> AttackViewModel.init { }
        |            .selectedNetwork
        |            .filterNotNull()
        |            .distinctUntilChanged()
        |            .collect { network ->
        |                _uiState.value = _uiState.value.copy(
        |                    targetBssid = network.bssid,
        |                    targetSsid  = network.ssid,
        |                    hasPreselectedTarget = true,
        |                )
        |            }
        |
        +--------> CrackViewModel.init { }
                     .selectedNetwork
                     .filterNotNull()
                     .distinctUntilChanged()
                     .collect { network ->
                         _uiState.value = _uiState.value.copy(
                             targetBssid = network.bssid,
                             targetSsid  = network.ssid,
                             hasTargetNetwork = true,
                         )
                     }
```

**Sequence diagram:**

```
ScanViewModel       SelectedNetworkRepo      AttackViewModel     CrackViewModel
     |                       |                      |                   |
     | select(network)       |                      |                   |
     |---------------------->|                      |                   |
     |                       | StateFlow<network>   |                   |
     |                       |--------------------->| auto-fill target  |
     |                       |                      |                   |
     |                       | StateFlow<network>   |                   |
     |                       |----------------------------------------->| auto-fill target
```

`SelectedNetworkRepository.clear()` should be called when starting a new session or explicitly de-selecting a target.

---

### 4.2 `SessionCollector`

`SessionCollector` aggregates operational results from all active modules into a single `StateFlow<SessionData>`, enabling the `report` module to automatically populate findings without direct coupling to feature module domain logic.

```
Location: core/service/SessionCollector.kt
Scope:    @Singleton (Hilt SingletonComponent)
State:    MutableStateFlow<SessionData>
```

**Data structure:**

```
SessionData {
  scannedNetworkCount: Int
  scanDuration: Long (ms)
  scanInterfaceName: String
  attacksPerformed: List<AttackRecord>
  crackAttempts: List<CrackRecord>
}

AttackRecord { type, targetBssid, targetSsid, status, startTime, endTime }
CrackRecord  { targetBssid, targetSsid, strategy, success, password, keysTested, duration }
```

**Write paths (push model — modules write, report reads):**

```
ScanViewModel.observeScanState()
  +--> sessionCollector.updateScanStats(networkCount, duration, interfaceName)
       (called when scan completes or networks list is non-empty)

AttackOrchestrator.launchAttack()
  +--> sessionCollector.recordAttack(AttackRecord(...))
       (called on attack completion, status = COMPLETED)

CrackOrchestrator.startCrack()
  +--> sessionCollector.recordCrack(CrackRecord(...))
       (called when CrackStatus.COMPLETED, includes recovered password if found)
```

**Read path (report consumption):**

```
ReportViewModel.collectSessionResults()
  |
  +--> sessionCollector.sessionData.value           <- snapshot of all session activity
  |
  +--> scanEngine.scanState.value.networks          <- current discovered networks
  |      +--> vulnMatcher.matchAllNetworks()        <- CVE matches per network
  |             +--> dataAggregator.networkToFindings() -> Finding objects
  |
  +--> session.attacksPerformed
  |      +--> dataAggregator.attackRecordToFindings() -> Finding objects
  |
  +--> session.crackAttempts (success == true only)
         +--> dataAggregator.crackedPasswordFinding() -> CVSS 10.0 Finding
```

**Complete data lineage:**

```
          SCAN              ATTACK              CRACK
            |                  |                  |
   ScanEngine.scanState  AttackOrchestrator  CrackOrchestrator
            |                  |                  |
            +------------------+------------------+
                               |
                        SessionCollector
                        (in-memory StateFlow)
                               |
                        ReportViewModel
                        .collectSessionResults()
                               |
                         DataAggregator
                   (network + attack + crack -> findings)
                               |
                        ReportGenerator
                   (findings + profiles -> Report)
                               |
                         ExportManager
                        (PDF / HTML / JSON)
```

---

## 5. State Management

The application uses a consistent **ViewModel + StateFlow** pattern across all modules. There is no global Redux-style store; each ViewModel owns its own `UiState` data class.

### Pattern

```kotlin
// 1. Immutable state data class
data class ScanUiState(
    val scanResult: ScanResult = ScanResult(),
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
)

// 2. ViewModel holds MutableStateFlow, exposes read-only StateFlow
@HiltViewModel
class ScanViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // 3. All mutations use immutable copy()
    private fun someAction() {
        _uiState.value = _uiState.value.copy(isScanning = true)
    }
}

// 4. UI collects with lifecycle-aware collectAsState()
@Composable
fun ScanScreen(viewModel: ScanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
}
```

### State ownership per module

| ViewModel | Owns | Observes (external) |
|-----------|------|---------------------|
| `MainViewModel` | `MainUiState` (loading, rooted, disclaimer) | — |
| `ScanViewModel` | `ScanUiState` (networks, interfaces, vulnMatches) | `ScanEngine.scanState` |
| `AttackViewModel` | `AttackUiState` (target, attack type, console) | `SelectedNetworkRepository`, `AttackOrchestrator.attackState`, `AttackOrchestrator.consoleOutput` |
| `CrackViewModel` | `CrackUiState` (strategy, wordlist, progress, result) | `SelectedNetworkRepository`, `CrackOrchestrator.progress`, `CrackOrchestrator.result` |
| `ReportViewModel` | `ReportUiState` (step, findings, profiles, generated report) | `SessionCollector.sessionData`, `ClientProfileDao`, `CompanyProfileDao` |

### Domain-level StateFlows (singleton services)

These flows live in `@Singleton` services and survive ViewModel recreation independently:

| Service | StateFlow |
|---------|-----------|
| `ScanEngine` | `StateFlow<ScanResult>` |
| `AttackOrchestrator` | `StateFlow<Attack>`, `StateFlow<List<String>>` (console output) |
| `CrackOrchestrator` | `StateFlow<CrackProgress>`, `StateFlow<CrackResult?>` |
| `ProgressBroadcaster` | `StateFlow<JobProgress>` |
| `SelectedNetworkRepository` | `StateFlow<SelectedNetwork?>` |
| `SessionCollector` | `StateFlow<SessionData>` |

### Threading model

All shell commands run on `Dispatchers.IO`. All StateFlow emissions are collected on `Dispatchers.Main` (via `collectAsState()` which is Main-safe). Long-running operations (scan, attack, crack) are launched in `viewModelScope` with explicit `Dispatchers.IO`:

```kotlin
viewModelScope.launch(Dispatchers.IO) {
    scanEngine.startScan(iface.name)   // blocks on IO thread
}
// StateFlow updates propagate to Main automatically
```

The `AuditLogger` uses `Mutex` for coroutine-safe concurrent writes on `Dispatchers.IO`.

---

## 6. Dependency Injection Graph

The application uses **Hilt 2.51** with KSP code generation. All components are scoped to `SingletonComponent` (application lifetime).

### Hilt Module Summary

| Module | Location | Provides |
|--------|----------|----------|
| `AppModule` | `app/di/AppModule.kt` | `SharedPreferences`, `Map<String, String>` (OUI table) |
| `CoreModule` | `core/di/CoreModule.kt` | `AuditLogger` |
| `DatabaseModule` | `core/di/DatabaseModule.kt` | `AppDatabase`, `VulnDao` |
| `ReportModule` | `report/di/ReportModule.kt` | `ReportDatabase`, `CompanyProfileDao`, `ClientProfileDao` |

### Constructor-injected singletons

All classes with `@Singleton` + `@Inject constructor()` are auto-bound by Hilt without explicit `@Provides`:

```
ShellExecutor
  +-- RootChecker
  +-- BinaryInstaller
  +-- ChipsetDetector
  +-- UsbWifiDetector
  +-- ChipsetMonitorHelper
  |     +-- MonitorModeManager
  |     +-- InterfaceManager (also injects ChipsetDetector, UsbWifiDetector)
  +-- WifiCommandRunner
  +-- ScanEngine         (also: MonitorModeManager, MacVendorLookup, AuditLogger)
  +-- ChannelHopper
  +-- PcapExporter        (also: BinaryInstaller)
  +-- DeauthAttack        (also: BinaryInstaller, ChipsetMonitorHelper)
  +-- HandshakeCapture    (also: BinaryInstaller)
  +-- PmkidCapture        (also: BinaryInstaller)
  +-- EvilTwinAttack      (also: BinaryInstaller)
  +-- ProbeSniff
  +-- PrerequisiteCheck   (also: BinaryInstaller)
  +-- DictionaryAttack    (also: BinaryInstaller)
  +-- BruteForceAttack    (also: BinaryInstaller)
  +-- RuleBasedAttack     (also: BinaryInstaller)
  +-- CombinatorAttack    (also: BinaryInstaller)
  +-- HashConverter       (also: BinaryInstaller)
  +-- WordlistManager

AuditLogger (CoreModule)
  +-- used by: ScanEngine, AttackOrchestrator, CrackOrchestrator, MtkMonitorCapture

VulnDao (DatabaseModule via AppDatabase)
  +-- VulnDatabase (seeder, injected by WifiCrackerApp)
  +-- VulnMatcher

SelectedNetworkRepository (no deps)
SessionCollector           (no deps)
ProgressBroadcaster        (no deps)
JobQueue                   (no deps)

AttackOrchestrator
  +-- DeauthAttack, HandshakeCapture, PmkidCapture, EvilTwinAttack, ProbeSniff
  +-- PrerequisiteCheck, AuditLogger, SessionCollector

CrackOrchestrator
  +-- DictionaryAttack, BruteForceAttack, RuleBasedAttack, CombinatorAttack
  +-- HashConverter, AuditLogger, SessionCollector

ReportDatabase (ReportModule) -> CompanyProfileDao, ClientProfileDao
  +-- used by: ReportViewModel, CompanyProfileViewModel, ClientListViewModel
```

### Simplified key injection paths

```
SingletonComponent
|
+-- ShellExecutor <------------------ all shell-dependent singletons
|
+-- AuditLogger (CoreModule)
|     written by: ScanEngine, AttackOrchestrator, CrackOrchestrator
|
+-- SelectedNetworkRepository
|     written by: ScanViewModel
|     read by: AttackViewModel, CrackViewModel
|
+-- SessionCollector
|     written by: ScanViewModel, AttackOrchestrator, CrackOrchestrator
|     read by: ReportViewModel
|
+-- AppDatabase -> VulnDao
|     seeded by: VulnDatabase (on WifiCrackerApp.onCreate)
|     queried by: VulnMatcher
|
+-- ReportDatabase -> CompanyProfileDao + ClientProfileDao
      used by: ReportViewModel, CompanyProfileViewModel, ClientListViewModel
```

### ViewModel scoping

ViewModels are injected via `@HiltViewModel` + `hiltViewModel()` in Compose. Each navigation destination gets its own ViewModel instance scoped to its `NavBackStackEntry`.

**Exception:** The `network_detail/{bssid}` route deliberately retrieves `ScanViewModel` from the parent `BottomNavTab.Scan.route` backstack entry, reusing the existing instance to read already-discovered networks without redundant re-scanning.

---

## 7. Build System

### Root `build.gradle.kts`

Plugin versions declared once at root level with `apply false` (applied per-module):

```
com.android.application             8.3.0
com.android.library                 8.3.0
org.jetbrains.kotlin.android        2.0.0
org.jetbrains.kotlin.plugin.compose 2.0.0
com.google.dagger.hilt.android      2.51
com.google.devtools.ksp             2.0.0-1.0.22
```

### Key build configuration

| Setting | Value | Notes |
|---------|-------|-------|
| `compileSdk` | 35 | All modules |
| `minSdk` | 31 (Android 12) | Required for `NEARBY_WIFI_DEVICES` permission |
| `targetSdk` | 35 | App module only |
| `jvmTarget` / `sourceCompatibility` | 17 | All modules |
| `isMinifyEnabled` | true (release only) | App module, with `proguard-android-optimize.txt` |
| `android.allowBackup` | false | Declared in app manifest |
| Compose BOM | `androidx.compose:compose-bom:2024.06.00` | Used by all UI modules |

### KSP configuration

KSP is used in place of kapt for both Hilt and Room. The `core` module passes a Room schema export argument:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Schema file: `core/schemas/com.wificracker.core.database.AppDatabase/1.json`

### Module capability matrix

| Module  | app plugin | Compose | Hilt | Room | Serialization | Coil |
|---------|------------|---------|------|------|---------------|------|
| `app`   | application| yes     | yes  | no   | no            | no   |
| `core`  | library    | no      | yes  | yes  | yes           | no   |
| `scan`  | library    | yes     | yes  | yes  | no            | no   |
| `attack`| library    | yes     | yes  | no   | no            | no   |
| `crack` | library    | yes     | yes  | no   | no            | no   |
| `report`| library    | yes     | yes  | yes  | yes           | yes  |

### Assets bundled in `:core`

```
core/src/main/assets/
  binaries/
    aircrack-ng      <- precompiled ARM64 binary
    aireplay-ng
    airodump-ng
    airmon-ng
    lib/
      libaircrack-ce-wpa-1.7.0.so
      libaircrack-ce-wpa-arm-neon-1.7.0.so
      libaircrack-ce-wpa.so
      libaircrack-osdep-1.7.0.so
      libaircrack-osdep.so
  oui.tsv            <- IEEE OUI vendor lookup table (TSV format)
  vulns.json         <- WiFi CVE vulnerability database
```

---

## 8. Security Considerations

### Root Requirement

The application requires root access for all pentest operations. `RootChecker` gates the entire UI at startup via `MainViewModel`. Without `uid=0` access, the user reaches `RootErrorScreen` and cannot proceed. All privileged operations go through `ShellExecutor.executeAsRoot()` which spawns a `su` process.

### Input Validation and Argument Handling

All command arguments (BSSID, interface name, file paths) that originate from user input or scanned data are interpolated directly into shell command strings by `ShellExecutor`. Callers must sanitise inputs before interpolation.

Current sanitisation points:
- BSSID format is validated by regex (`[0-9A-Fa-f:]{17}`) in `WifiCommandRunner.parseCsvOutput()` and `ScanEngine.parseAndroidScanResults()` before a `Network` object is created.
- Interface names come from `InterfaceManager` which reads `/sys/class/net` — user cannot freely supply them.
- File paths for capture/wordlist are selected from the filesystem by `WordlistManager` and `PcapExporter`, not directly typed by the user.

**Remaining risk:** The BSSID stored in `SelectedNetwork` and passed to attack/crack commands has only been validated at parse time. Re-validation at command construction time would add defence in depth.

### Binary Integrity

The aircrack-ng suite is extracted from APK assets to `/data/local/tmp/wificracker/` on first launch. `BinaryInstaller.isBinaryInstalled()` checks only file existence and execute bit — it does not verify cryptographic integrity after installation.

The MediaTek patched driver IS verified by specific SHA-256 hashes in `ChipsetMonitorHelper.detectChipVendor()` before enabling MTK-specific paths. This pattern (hash verification before privileged use) should be extended to the extracted binaries.

### Data Storage

| Data | Location | Readable by |
|------|----------|-------------|
| VulnDB (Room) | app private storage (`wificracker.db`) | App process only |
| Report profiles (Room) | app private storage (`wificracker_reports.db`) | App process only |
| Audit logs | `filesDir/audit_logs/audit.jsonl` | App process only |
| Captured handshakes | `/data/local/tmp/wificracker/scans/` | Root-capable processes |
| Exported reports | `/data/local/tmp/wificracker/reports/` | Root-capable processes |

Capture files and exported reports are written to `/data/local/tmp/` because they are created by root shell commands. Moving them to app-private storage post-creation and using `FileProvider` for sharing would narrow the exposure window.

### Sensitive Data in Memory

The `SessionCollector.CrackRecord` stores recovered passwords in-memory in the `StateFlow`. These are surfaced in the `ReportViewModel` and written to the exported report file. No passwords are written to the Room databases.

### Legal Disclaimer Gate

`AppNavGraph` enforces acceptance of a legal disclaimer before any features are accessible. The flag is stored in `SharedPreferences("wificracker", MODE_PRIVATE)`. This is a UX-level control, not a cryptographic or OS-level restriction.

### Signing Configuration

The release signing config in `app/build.gradle.kts` currently contains hardcoded keystore path and passwords:

```
storeFile = file("../wificracker-release.keystore")
storePassword = "wificracker2026"
keyPassword = "wificracker2026"
```

These must be externalised before any CI/CD integration or source-controlled distribution. Recommended approach: `local.properties` (excluded from VCS) or environment variables (`KEYSTORE_PASSWORD`, `KEY_PASSWORD`) read via `System.getenv()` in the build script.

### `android.allowBackup = false`

Set in `AndroidManifest.xml`. Prevents ADB full-backup (`adb backup`) from extracting Room databases, audit logs, or cached capture files from a connected development device.

### Network Permissions vs. RF Operations

The application holds `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`, and WiFi state permissions. However, all actual packet-level RF operations bypass the Android WiFi API entirely and use the aircrack-ng suite via root shell. Android's permission model does not constrain the underlying hardware operations once root is obtained.
