# API Reference — WiFi Cracker Android App

## Table of Contents

1. [Project Architecture](#1-project-architecture)
2. [Module: core](#2-module-core)
   - [Data Models](#21-data-models)
   - [Enums](#22-enums)
   - [Singleton Services](#23-singleton-services)
   - [WiFi Subsystem](#24-wifi-subsystem)
   - [Root & Shell Subsystem](#25-root--shell-subsystem)
   - [Logging Subsystem](#26-logging-subsystem)
   - [Room Database (AppDatabase)](#27-room-database-appdatabase)
   - [Utility Classes](#28-utility-classes)
3. [Module: scan](#3-module-scan)
   - [Data Models](#31-data-models)
   - [Enums](#32-enums)
   - [Domain Layer](#33-domain-layer)
   - [Data Layer](#34-data-layer)
   - [ScanViewModel](#35-scanviewmodel)
4. [Module: attack](#4-module-attack)
   - [Data Models](#41-data-models)
   - [Enums](#42-enums)
   - [Domain Layer](#43-domain-layer)
   - [Attack Implementations (WifiAttack)](#44-attack-implementations-wifiattack)
   - [AttackViewModel](#45-attackviewmodel)
5. [Module: crack](#5-module-crack)
   - [Data Models](#51-data-models)
   - [Enums](#52-enums)
   - [Domain Layer](#53-domain-layer)
   - [Crack Strategy Implementations](#54-crack-strategy-implementations)
   - [CrackViewModel](#55-crackviewmodel)
6. [Module: report](#6-module-report)
   - [Data Models](#61-data-models)
   - [Enums](#62-enums)
   - [Report Generation Pipeline](#63-report-generation-pipeline)
   - [Room Database (ReportDatabase)](#64-room-database-reportdatabase)
   - [ViewModels](#65-viewmodels)
7. [Module: app](#7-module-app)
   - [ViewModels](#71-viewmodels)
   - [Dependency Injection](#72-dependency-injection)
8. [Cross-Module StateFlow Contracts](#8-cross-module-stateflow-contracts)
9. [File System Conventions](#9-file-system-conventions)
10. [Binary Dependencies](#10-binary-dependencies)

---

## 1. Project Architecture

The application is structured as a multi-module Gradle project. Each module is a self-contained feature with its own domain, model, and UI layers.

```
wifi-cracker/
├── app/          — Application shell, navigation, DI root, app-level ViewModels
├── core/         — Shared infrastructure: root, shell, WiFi interfaces, DB, logging
├── scan/         — WiFi network scanning, channel hopping, vulnerability matching
├── attack/       — Active attack orchestration (deauth, handshake, PMKID, evil twin, probe sniff)
├── crack/        — Password cracking orchestration (dictionary, brute-force, rule-based, combinator)
└── report/       — Report generation, export pipeline, client/company profile management
```

**DI Framework:** Hilt (Dagger 2 backend), `@Singleton` scope for all engines/orchestrators.

**Reactive State:** Kotlin `StateFlow` / `Flow` throughout. No LiveData.

**Database:** Room, two separate databases:
- `AppDatabase` (core module) — vulnerability knowledge base
- `ReportDatabase` (report module) — client and company profiles

---

## 2. Module: core

Package root: `com.wificracker.core`

### 2.1 Data Models

#### `SelectedNetwork`
**Package:** `com.wificracker.core.model`

Lightweight representation of a target network shared across all feature modules via `SelectedNetworkRepository`. Contains only the fields required for targeting — it is not the full scan model.

| Field | Type | Description |
|---|---|---|
| `bssid` | `String` | MAC address of the access point (e.g. `"AA:BB:CC:DD:EE:FF"`) |
| `ssid` | `String` | Network name |
| `channel` | `Int` | WiFi channel number (default `0`) |
| `encryption` | `String` | Encryption label as a human-readable string (e.g. `"WPA2"`) |
| `signalStrength` | `Int` | Signal level in dBm (default `0`) |

#### `ShellResult`
**Package:** `com.wificracker.core.root`

Represents the outcome of a shell command executed by `ShellExecutor`.

| Field | Type | Description |
|---|---|---|
| `exitCode` | `Int` | Process exit code. `0` indicates success. |
| `stdout` | `String` | Standard output, trimmed. |
| `stderr` | `String` | Standard error output, trimmed. |
| `isSuccess` | `Boolean` (computed) | `true` when `exitCode == 0` |

#### `WifiInterface`
**Package:** `com.wificracker.core.wifi`

Represents a network interface available on the device.

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Interface name (e.g. `"wlan0"`, `"wlan1"`) |
| `macAddress` | `String` | Hardware MAC address |
| `chipset` | `String` | Chipset identifier string |
| `driver` | `String` | Kernel driver name |
| `supportsMonitor` | `Boolean` | Whether the interface can enter monitor mode |
| `isMonitorMode` | `Boolean` | Whether the interface is currently in monitor mode (default `false`) |

#### `ChipsetInfo`
**Package:** `com.wificracker.core.wifi`

Basic chipset metadata returned by `ChipsetDetector.detect()`.

| Field | Type | Description |
|---|---|---|
| `chipset` | `String` | Chipset name from sysfs or driver |
| `driver` | `String` | Kernel driver name |
| `supportsMonitor` | `Boolean` | `true` if the driver is in the known monitor-capable set |

#### `ChipsetMonitorCapability`
**Package:** `com.wificracker.core.wifi`

Extended chipset capability assessment returned by `ChipsetMonitorHelper.detectChipVendor()`.

| Field | Type | Description |
|---|---|---|
| `vendor` | `WifiChipVendor` | Detected vendor enum |
| `chipName` | `String` | Human-readable chip name (e.g. `"MediaTek mt6878"`) |
| `supportsInternalMonitor` | `Boolean` | `true` if the chipset can capture without an external adapter |
| `monitorMethod` | `String` | Description of the method used (e.g. `"MTK ICS + promiscuous + deauth (v3)"`) |
| `requiresPatch` | `Boolean` | `true` if firmware/driver patching is required (default `false`) |
| `patchInstalled` | `Boolean` | `true` if the required patch is detected as installed (default `false`) |
| `supportsRawTx` | `Boolean` | `true` if raw frame injection is supported — requires patch v4 (default `false`) |
| `patchVersion` | `Int` | Patch version: `0` = unpatched, `3` = ICS+deauth, `4` = ICS+deauth+raw TX (default `0`) |

#### `UsbWifiAdapter`
**Package:** `com.wificracker.core.wifi`

Represents a detected USB WiFi adapter.

| Field | Type | Description |
|---|---|---|
| `interfaceName` | `String` | Linux interface name (e.g. `"wlan1"`) |
| `vendorId` | `String` | USB vendor ID (4-character hex, e.g. `"0bda"`) |
| `productId` | `String` | USB product ID (4-character hex, e.g. `"8812"`) |
| `chipset` | `String` | Resolved chipset name (e.g. `"Realtek RTL8812AU"`) |
| `driver` | `String` | Driver name (e.g. `"rtl8812au"`) |
| `supportsMonitor` | `Boolean` | Whether this chipset is known to support monitor mode |

#### `AuditEntry`
**Package:** `com.wificracker.core.logging`

A single structured audit log entry. Serialized to JSON Lines format.

| Field | Type | Description |
|---|---|---|
| `timestamp` | `Long` | Unix epoch milliseconds (default: current time) |
| `action` | `String` | Action identifier, uppercase (e.g. `"SCAN_START"`, `"ATTACK_STOP"`) |
| `module` | `String` | Source module name (e.g. `"scan"`, `"attack"`, `"crack"`) |
| `target` | `String` | Target identifier — interface name or BSSID (default `""`) |
| `result` | `String` | Outcome string (e.g. `"FOUND"`, `"NOT_FOUND"`, `"SUCCESS"`) (default `""`) |
| `details` | `String` | Additional context or error message (default `""`) |

#### `JobProgress`
**Package:** `com.wificracker.core.service`

Progress snapshot for a job being executed by `PentestForegroundService`.

| Field | Type | Description |
|---|---|---|
| `jobId` | `String` | UUID of the job (default `""`) |
| `status` | `JobStatus` | Current execution status |
| `progress` | `Float` | Completion fraction `0.0..1.0` (default `0f`) |
| `message` | `String` | Human-readable status message (default `""`) |
| `output` | `String` | Raw console output (default `""`) |

#### `IcsPacket`
**Package:** `com.wificracker.core.wifi.monitor`

A parsed packet from the MediaTek ICS firmware log device `/dev/fw_log_ics`.

| Field | Type | Description |
|---|---|---|
| `sequence` | `Int` | Packet sequence number from ICS header |
| `frameLength` | `Int` | Length of the raw 802.11 frame payload in bytes |
| `rawFrame` | `ByteArray` | Raw 802.11 frame bytes (empty if `isTimesync = true`) |
| `isTimesync` | `Boolean` | `true` for firmware timesync messages — these must be discarded |

#### `Ieee80211Frame`
**Package:** `com.wificracker.core.wifi.monitor`

A parsed IEEE 802.11 frame.

| Field | Type | Description |
|---|---|---|
| `frameType` | `Int` | Frame type: `0` = Management, `1` = Control, `2` = Data |
| `frameSubtype` | `Int` | Frame subtype (e.g. `8` = Beacon, `4` = Probe Request) |
| `addr1` | `String` | Destination address |
| `addr2` | `String` | Source address |
| `addr3` | `String` | BSSID (for management frames) |
| `ssid` | `String?` | SSID extracted from Information Element tag (nullable) |
| `channel` | `Int?` | Channel extracted from DS Parameter Set IE (nullable) |
| `rssi` | `Int?` | Signal strength in dBm — currently `null` (not decoded from MTK header) |
| `isBeacon` | `Boolean` | Management subtype 8 |
| `isProbeReq` | `Boolean` | Management subtype 4 |
| `isProbeResp` | `Boolean` | Management subtype 5 |
| `isData` | `Boolean` | Data type, subtype 0 or 8 (QoS) |
| `isAuth` | `Boolean` | Management subtype 11 |
| `isDeauth` | `Boolean` | Management subtype 12 |

### 2.2 Enums

#### `RootType`
**Package:** `com.wificracker.core.root`

| Value | Description |
|---|---|
| `MAGISK` | Rooted via Magisk |
| `KERNELSU` | Rooted via KernelSU |
| `SUPERSU` | Rooted via SuperSU |
| `UNKNOWN` | Rooted but method undetected |
| `NONE` | Device is not rooted |

#### `WifiChipVendor`
**Package:** `com.wificracker.core.wifi`

| Value | `label` | Description |
|---|---|---|
| `QUALCOMM` | `"Qualcomm (Snapdragon)"` | Detected via `/sys/module/wlan/parameters/con_mode` |
| `BROADCOM` | `"Broadcom (Exynos/Pixel)"` | Detected via `/sys/module/bcmdhd*/parameters/` |
| `MEDIATEK` | `"MediaTek"` | Detected via `ro.vendor.wlan.gen` system property |
| `UNKNOWN` | `"Unknown"` | Detection failed |

#### `JobStatus`
**Package:** `com.wificracker.core.service`

| Value | Description |
|---|---|
| `QUEUED` | Job waiting in the queue |
| `RUNNING` | Job currently executing |
| `PAUSED` | Job suspended |
| `COMPLETED` | Job finished successfully |
| `FAILED` | Job terminated with error |
| `CANCELLED` | Job cancelled by user |

### 2.3 Singleton Services

#### `SelectedNetworkRepository`
**Package:** `com.wificracker.core.service`
**Scope:** `@Singleton`

In-memory shared repository that carries the currently targeted network across the scan, attack, and crack modules. There is no persistence — the selection is lost when the process ends.

**StateFlow:**

| Property | Type | Description |
|---|---|---|
| `selectedNetwork` | `StateFlow<SelectedNetwork?>` | Currently selected network. `null` when nothing is selected. |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `select` | `fun select(network: SelectedNetwork)` | Sets the selected network. Immediately emits to all collectors. |
| `clear` | `fun clear()` | Clears the selection, emits `null`. |

#### `SessionCollector`
**Package:** `com.wificracker.core.service`
**Scope:** `@Singleton`

Accumulates structured activity data across all modules during a single app session. The `ReportViewModel` reads this to auto-populate findings when generating a report.

**StateFlow:**

| Property | Type | Description |
|---|---|---|
| `sessionData` | `StateFlow<SessionData>` | Accumulated session data. |

**Nested Types:**

`SessionData`:

| Field | Type | Description |
|---|---|---|
| `scannedNetworkCount` | `Int` | Number of networks found in the last scan |
| `scanDuration` | `Long` | Duration of the last scan in milliseconds |
| `scanInterfaceName` | `String` | Interface used for the last scan |
| `attacksPerformed` | `List<AttackRecord>` | All recorded attack completions |
| `crackAttempts` | `List<CrackRecord>` | All recorded crack attempts |

`AttackRecord`:

| Field | Type | Description |
|---|---|---|
| `type` | `String` | Attack type name (matches `AttackType.name`) |
| `targetBssid` | `String` | Target AP MAC address |
| `targetSsid` | `String` | Target network name |
| `status` | `String` | Final status (matches `AttackStatus.name`) |
| `startTime` | `Long` | Unix epoch milliseconds |
| `endTime` | `Long` | Unix epoch milliseconds |

`CrackRecord`:

| Field | Type | Description |
|---|---|---|
| `targetBssid` | `String` | Target AP MAC address |
| `targetSsid` | `String` | Target network name |
| `strategy` | `String` | Strategy name (matches `CrackStrategy.name`) |
| `success` | `Boolean` | Whether the password was found |
| `password` | `String` | Recovered password (empty string if not found) |
| `keysTested` | `Long` | Number of candidates tested |
| `duration` | `Long` | Total crack duration in milliseconds |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `updateScanStats` | `fun updateScanStats(networkCount: Int, duration: Long, interfaceName: String)` | Called by `ScanViewModel` after scan completes or updates. |
| `recordAttack` | `fun recordAttack(record: AttackRecord)` | Called by `AttackOrchestrator` after each attack completes. |
| `recordCrack` | `fun recordCrack(record: CrackRecord)` | Called by `CrackOrchestrator` after each crack attempt completes. |
| `reset` | `fun reset()` | Clears all accumulated session data. |

#### `JobQueue`
**Package:** `com.wificracker.core.service`
**Scope:** `@Singleton`

Thread-safe FIFO queue backed by `ConcurrentLinkedQueue`. Used by `PentestForegroundService`.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `enqueue` | `fun enqueue(job: Job)` | Adds a job to the tail of the queue. |
| `dequeue` | `fun dequeue(): Job?` | Removes and returns the head job, or `null` if empty. |
| `cancel` | `fun cancel(jobId: String)` | Removes all jobs matching the given ID. |
| `clear` | `fun clear()` | Removes all jobs. |
| `size` | `fun size(): Int` | Current queue depth. |
| `isEmpty` | `fun isEmpty(): Boolean` | `true` when queue has no pending jobs. |
| `peek` | `fun peek(): Job?` | Returns head without removing it, or `null`. |

#### `ProgressBroadcaster`
**Package:** `com.wificracker.core.service`
**Scope:** `@Singleton`

Single-slot broadcaster for `JobProgress` updates from `PentestForegroundService`.

**StateFlow:**

| Property | Type | Description |
|---|---|---|
| `currentProgress` | `StateFlow<JobProgress>` | Latest progress snapshot. |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `update` | `fun update(progress: JobProgress)` | Publishes a new progress snapshot. |
| `reset` | `fun reset()` | Resets to the default `JobProgress()` value. |

#### `PentestForegroundService`
**Package:** `com.wificracker.core.service`

Android foreground service that dequeues `Job` instances and maintains the persistent notification. Annotated `@AndroidEntryPoint` for Hilt injection.

**Intent Actions:**

| Constant | Value | Description |
|---|---|---|
| `ACTION_START` | `"com.wificracker.START"` | Starts the service and processes the next job. |
| `ACTION_STOP` | `"com.wificracker.STOP"` | Stops the service and removes the notification. |

**Notification:**

| Constant | Value |
|---|---|
| `CHANNEL_ID` | `"wificracker_service"` |
| `NOTIFICATION_ID` | `1` |

### 2.4 WiFi Subsystem

#### `InterfaceManager`
**Package:** `com.wificracker.core.wifi`
**Scope:** `@Singleton`

Enumerates all available WiFi interfaces. Prefers `iw dev` when available; falls back to `/proc/net/wireless` and `/sys/class/net/`. Also detects USB adapters via `UsbWifiDetector`.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `listInterfaces` | `fun listInterfaces(): List<WifiInterface>` | Returns all detected WiFi interfaces, including USB adapters. |

#### `MonitorModeManager`
**Package:** `com.wificracker.core.wifi`
**Scope:** `@Singleton`

Delegates monitor mode enable/disable to `ChipsetMonitorHelper` using the correct vendor-specific method.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `enableMonitorMode` | `fun enableMonitorMode(interfaceName: String): ShellResult` | Puts the interface into monitor mode. |
| `disableMonitorMode` | `fun disableMonitorMode(interfaceName: String): ShellResult` | Returns the interface to managed mode. |
| `isMonitorMode` | `fun isMonitorMode(interfaceName: String): Boolean` | `true` if the interface type is `monitor` or sysfs type is `803`. |
| `getChipsetInfo` | `fun getChipsetInfo(): ChipsetMonitorCapability` | Returns full chipset capability assessment. |

#### `ChipsetMonitorHelper`
**Package:** `com.wificracker.core.wifi`
**Scope:** `@Singleton`

Vendor-aware chipset detection and monitor mode management.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `detectChipVendor` | `fun detectChipVendor(): ChipsetMonitorCapability` | Probes sysfs and system properties to identify the WiFi chipset and its capabilities. Results are not cached — each call re-probes. |
| `enableMonitorMode` | `fun enableMonitorMode(interfaceName: String): ShellResult` | Vendor-specific enable: Qualcomm uses `con_mode=4`, Broadcom uses Nexmon/nexutil, MediaTek uses SNIFFER command + ics_enable, unknown uses `iw set type monitor`. |
| `disableMonitorMode` | `fun disableMonitorMode(interfaceName: String): ShellResult` | Reverses the vendor-specific enable. |

#### `ChipsetDetector`
**Package:** `com.wificracker.core.wifi`
**Scope:** `@Singleton`

Resolves chipset and driver information for a specific named interface from sysfs.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `detect` | `fun detect(interfaceName: String): ChipsetInfo` | Reads `/sys/class/net/{iface}/device/driver` and `uevent`. Returns `ChipsetInfo` with `supportsMonitor = true` if the driver is in the known set: `ath9k`, `ath9k_htc`, `ath10k`, `ath11k`, `rt2800usb`, `rt73usb`, `rtl8187`, `carl9170`, `b43`, `brcmfmac`, `mt76`, `mt7601u`, `mt7921e`. |

#### `UsbWifiDetector`
**Package:** `com.wificracker.core.wifi`
**Scope:** `@Singleton`

Detects externally connected USB WiFi adapters by cross-referencing interface sysfs paths with a built-in USB ID database.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `detectUsbAdapters` | `fun detectUsbAdapters(): List<UsbWifiAdapter>` | Scans `/sys/class/net/` for interfaces backed by USB devices. Resolves chipset from `vendorId:productId` lookup table. |

**Known USB Chipsets (built-in lookup table):**

| USB ID | Chipset | Driver | Monitor |
|---|---|---|---|
| `0bda:8812` | Realtek RTL8812AU | rtl8812au | Yes |
| `0bda:b812` | Realtek RTL8812BU | rtl88x2bu | Yes |
| `148f:3070` | Ralink RT3070 | rt2800usb | Yes |
| `148f:5370` | Ralink RT5370 | rt2800usb | Yes |
| `0cf3:9271` | Atheros AR9271 | ath9k_htc | Yes |
| `0e8d:7961` | MediaTek MT7921AU | mt7921u | Yes |
| `0e8d:7612` | MediaTek MT7612U | mt76x2u | Yes |

#### `MtkMonitorCapture`
**Package:** `com.wificracker.core.wifi.monitor`

Non-singleton class. Manages capture from the MediaTek ICS firmware log device `/dev/fw_log_ics`. Requires patched `wlan_drv_gen4m` driver and `ics_enable` binary.

**Constructor parameters:** `shellExecutor: ShellExecutor`, `auditLogger: AuditLogger`

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `enableCapture` | `suspend fun enableCapture(): Boolean` | Sends `SNIFFER 2 0 0 0 0 0 0 0 0 0` via `wpa_driver` then calls `ics_enable 1`. Returns `false` on failure. |
| `disableCapture` | `suspend fun disableCapture(): Boolean` | Stops the capture job and calls `ics_enable 0`. |
| `startCapture` | `fun startCapture(onFrame: suspend (Ieee80211Frame) -> Unit): Flow<Ieee80211Frame>` | Opens `/dev/fw_log_ics` via `su` pipe, reads raw bytes, calls `IcsPacketParser` then `Ieee80211Parser` for each packet. Non-timesync frames are emitted and passed to `onFrame`. Closes with `IOException` if the device does not exist. |
| `stopCapture` | `suspend fun stopCapture()` | Cancels the internal capture coroutine job. |

#### `IcsPacketParser`
**Package:** `com.wificracker.core.wifi.monitor`

Pure stateless object. Parses raw ICS binary stream from MTK firmware.

**Constants:**

| Constant | Value | Description |
|---|---|---|
| `ICS_MAGIC` | `0x44d9c99aL` | 4-byte little-endian magic number at the start of each ICS packet |
| `TIMESYNC_INFO` | `0x0008011000000000L` | 8-byte info field value identifying timesync packets |
| `MTK_RX_DESC_SIZE` | `120` | Size of MTK RX descriptor prepended before the 802.11 frame payload |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `parsePacket` | `fun parsePacket(buffer: ByteArray, offset: Int): IcsPacket?` | Validates magic, parses header fields, extracts the 802.11 frame payload. Returns `null` if the buffer is too short or magic does not match. |
| `findNextPacketOffset` | `fun findNextPacketOffset(buffer: ByteArray, offset: Int): Int?` | Scans forward byte-by-byte for the next `ICS_MAGIC` occurrence. Returns the offset or `null` if not found. |

#### `Ieee80211Parser`
**Package:** `com.wificracker.core.wifi.monitor`

Pure stateless object. Parses raw 802.11 frame bytes into `Ieee80211Frame`.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `parseFrame` | `fun parseFrame(raw: ByteArray): Ieee80211Frame?` | Parses Frame Control field, extracts three MAC addresses, determines frame type/subtype flags, and decodes IE tags for SSID and DS channel. Returns `null` if fewer than 24 bytes. |

### 2.5 Root & Shell Subsystem

#### `ShellExecutor`
**Package:** `com.wificracker.core.root`
**Scope:** `@Singleton`

Low-level command execution. All blocking — must be called from `Dispatchers.IO`.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `execute` | `fun execute(command: String, timeoutSeconds: Long = 30): ShellResult` | Runs a command via `sh -c`. Kills the process on timeout. |
| `executeAsRoot` | `fun executeAsRoot(command: String, timeoutSeconds: Long = 30): ShellResult` | Runs a command by piping it to `su` via stdin. Properly handles shell operators (`&&`, `\|`, etc.). Kills the process on timeout. |

#### `RootChecker`
**Package:** `com.wificracker.core.root`
**Scope:** `@Singleton`

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `isRooted` | `fun isRooted(): Boolean` | Runs `su -c id` and checks for `uid=0`. |
| `detectRootType` | `fun detectRootType(): RootType` | Probes for Magisk, KernelSU, and SuperSU binaries to identify the root manager. |

#### `BinaryInstaller`
**Package:** `com.wificracker.core.root`
**Scope:** `@Singleton`

Manages pentest binary installation to `/data/local/tmp/wificracker/`.

**Constants:**

| Constant | Value |
|---|---|
| `INSTALL_DIR` | `"/data/local/tmp/wificracker"` |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `installAllFromAssets` | `fun installAllFromAssets(context: Context)` | Extracts all binaries from APK `assets/binaries/` and shared libraries from `assets/binaries/lib/`. Skips already-installed binaries. |
| `installBinary` | `fun installBinary(binaryName: String, assetSourceDir: String): ShellResult` | Copies a single binary from a path into `INSTALL_DIR` and sets `chmod 755`. |
| `isBinaryInstalled` | `fun isBinaryInstalled(binaryName: String): Boolean` | Checks `test -x {INSTALL_DIR}/{binaryName}`. |
| `listInstalledBinaries` | `fun listInstalledBinaries(): List<String>` | Lists all files present in `INSTALL_DIR`. |
| `getBinaryPath` | `fun getBinaryPath(binaryName: String): String` | Returns the absolute path `{INSTALL_DIR}/{binaryName}`. |

### 2.6 Logging Subsystem

#### `AuditLogger`
**Package:** `com.wificracker.core.logging`
**Scope:** `@Singleton`

Thread-safe append-only audit log written in JSON Lines format to `{filesDir}/audit_logs/audit.jsonl`.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `log` | `suspend fun log(entry: AuditEntry)` | Serializes the entry to JSON and appends a line to the log file. Uses a `Mutex` for concurrency safety. |
| `getEntries` | `suspend fun getEntries(): List<AuditEntry>` | Reads and deserializes all entries. Returns empty list if the file does not exist. |
| `exportJson` | `suspend fun exportJson(): String` | Returns the full log as a pretty-printed JSON array string. |
| `purge` | `suspend fun purge()` | Deletes the log file entirely. |

**Known `action` Values by Module:**

| Action | Module | Description |
|---|---|---|
| `SCAN_START` | scan | Scan initiated on an interface |
| `SCAN_STOP` | scan | Scan stopped; result includes network count |
| `SCAN_ERROR` | scan | Scan flow emitted an error |
| `ICS_ENABLE_FAILED` | scan | MTK ICS capture could not be enabled |
| `ATTACK_START` | attack | Attack launched |
| `ATTACK_STOP` | attack | Attack cancelled |
| `CRACK_START` | crack | Crack job started |
| `CRACK_DONE` | crack | Crack job completed; result is `"FOUND"` or `"NOT_FOUND"` |
| `CRACK_STOP` | crack | Crack job cancelled |
| `MTK_CAPTURE_ENABLED` | MtkMonitorCapture | ICS capture successfully enabled |
| `MTK_CAPTURE_ENABLE_FAILED` | MtkMonitorCapture | ICS capture failed to enable |
| `MTK_CAPTURE_DISABLED` | MtkMonitorCapture | ICS capture disabled |
| `MTK_CAPTURE_READ_ERROR` | MtkMonitorCapture | Read error on `/dev/fw_log_ics` |
| `MTK_CAPTURE_FATAL` | MtkMonitorCapture | Fatal exception in capture loop |

### 2.7 Room Database (AppDatabase)

**Package:** `com.wificracker.core.database`
**Class:** `AppDatabase : RoomDatabase()`
**Database version:** `1`
**Export schema:** `true`
**Entities:** `VulnEntity`

#### Entity: `VulnEntity`

**Table:** `vulnerabilities`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `cveId` | `String` | `@PrimaryKey` | CVE identifier (e.g. `"CVE-2017-13077"`) or synthetic ID (e.g. `"WPS-ENABLED"`) |
| `protocol` | `String` | | WiFi protocol this vulnerability applies to: `"WEP"`, `"WPA"`, `"WPA2"`, `"WPA3"`, `"OPEN"`, `"UNKNOWN"` |
| `title` | `String` | | Short vulnerability title |
| `description` | `String` | | Detailed vulnerability description |
| `severity` | `String` | | Severity label: `"CRITICAL"`, `"HIGH"`, `"MEDIUM"`, `"LOW"`, `"INFO"` |
| `cvssScore` | `Float` | | CVSS score `0.0..10.0` |
| `recommendation` | `String` | | Remediation guidance |
| `affectedVersions` | `String` | | Comma-separated or range string of affected versions |

#### DAO: `VulnDao`

| Method | Return Type | Query |
|---|---|---|
| `getAll()` | `Flow<List<VulnEntity>>` | All vulnerabilities, ordered by `cvssScore DESC` |
| `getByProtocol(protocol: String)` | `Flow<List<VulnEntity>>` | Filter by `protocol`, ordered by `cvssScore DESC` |
| `getBySeverity(severity: String)` | `Flow<List<VulnEntity>>` | Filter by `severity` |
| `search(query: String)` | `Flow<List<VulnEntity>>` | LIKE search on `title` and `description` |
| `getByCveId(cveId: String)` | `suspend VulnEntity?` | Exact match by primary key |
| `insertAll(vulns: List<VulnEntity>)` | `suspend Unit` | Bulk insert with `REPLACE` conflict strategy |
| `count()` | `suspend Int` | Total row count |

### 2.8 Utility Classes

#### `FileManager`
**Package:** `com.wificracker.core.util`
**Scope:** `@Singleton`

| Method | Signature | Description |
|---|---|---|
| `ensureDirectory` | `fun ensureDirectory(path: String): File` | Creates the directory (and parents) if it does not exist. Returns the `File` object. |
| `listFiles` | `fun listFiles(directory: String, extension: String? = null): List<File>` | Lists files in a directory, optionally filtered by extension. Sorted by last-modified descending. Returns empty list if directory does not exist. |
| `fileSizeFormatted` | `fun fileSizeFormatted(file: File): String` | Returns a human-readable size string (`"1.2 MB"`, `"345 KB"`, etc.). |
| `deleteFile` | `fun deleteFile(path: String): Boolean` | Deletes the file at the given path. Returns `true` on success. |

#### `MacVendorLookup`
**Package:** `com.wificracker.core.util`
**Scope:** `@Singleton`

Resolves OUI MAC address prefixes to vendor names from the bundled `oui.tsv` asset.

| Method | Signature | Description |
|---|---|---|
| `resolve` | `fun resolve(macAddress: String): String` | Takes a full MAC address, extracts the first 8 characters as the OUI prefix (uppercased, colon-separated), looks up in the OUI map. Returns `"Unknown"` if not found. |

#### `LocaleManager`
**Package:** `com.wificracker.core.i18n`
**Scope:** `@Singleton`

| Method | Signature | Description |
|---|---|---|
| `getCurrentLocale` | `fun getCurrentLocale(): Locale` | Returns the saved locale from `SharedPreferences`, or the system default. |
| `setLocale` | `fun setLocale(locale: Locale)` | Persists the locale language tag to `SharedPreferences`. |
| `applyLocale` | `fun applyLocale(context: Context): Context` | Creates and returns a new context with the configured locale applied. |
| `getSupportedLocales` | Companion: `val SUPPORTED_LOCALES` | `listOf(Locale.ENGLISH, Locale.FRENCH)` |

---

## 3. Module: scan

Package root: `com.wificracker.scan`

### 3.1 Data Models

#### `Network`
**Package:** `com.wificracker.scan.model`

A discovered WiFi access point.

| Field | Type | Default | Description |
|---|---|---|---|
| `bssid` | `String` | | MAC address of the AP |
| `ssid` | `String` | | Network name (may be empty for hidden networks) |
| `channel` | `Int` | | WiFi channel number |
| `frequency` | `Int` | `0` | Frequency in MHz (e.g. `2437` for channel 6) |
| `signalStrength` | `Int` | | Signal level in dBm (e.g. `-65`) |
| `encryption` | `EncryptionType` | | Security classification enum |
| `cipher` | `String` | `""` | Cipher suite string (e.g. `"CCMP"`, `"TKIP"`) |
| `authentication` | `String` | `""` | Authentication method (e.g. `"PSK"`, `"SAE"`, `"EAP"`) |
| `wps` | `Boolean` | `false` | Whether WPS is advertised |
| `clients` | `List<Client>` | `emptyList()` | Associated client devices (populated by airodump-ng) |
| `firstSeen` | `Long` | current time | Unix epoch milliseconds |
| `lastSeen` | `Long` | current time | Unix epoch milliseconds, updated on each merge |

#### `Client`
**Package:** `com.wificracker.scan.model`

A WiFi client (station) observed by the scanner.

| Field | Type | Default | Description |
|---|---|---|---|
| `macAddress` | `String` | | Client hardware MAC address |
| `bssid` | `String` | | BSSID of the AP the client is associated with (`""` if probing) |
| `signalStrength` | `Int` | | Signal in dBm |
| `vendor` | `String` | `"Unknown"` | OUI-resolved vendor name |
| `probeRequests` | `List<String>` | `emptyList()` | SSIDs seen in probe request frames |
| `packets` | `Int` | `0` | Total packet count from airodump-ng |
| `firstSeen` | `Long` | current time | Unix epoch milliseconds |
| `lastSeen` | `Long` | current time | Unix epoch milliseconds |

#### `ScanResult`
**Package:** `com.wificracker.scan.model`

Snapshot of the entire current scan state emitted by `ScanEngine`.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `String` | UUID | Unique scan session identifier |
| `timestamp` | `Long` | current time | When the scan was started |
| `interfaceName` | `String` | `""` | Name of the interface used |
| `duration` | `Long` | `0` | Elapsed milliseconds since scan start |
| `networks` | `List<Network>` | `emptyList()` | Discovered networks, sorted by signal strength descending |
| `clients` | `List<Client>` | `emptyList()` | Discovered clients |
| `status` | `ScanStatus` | `IDLE` | Current scan lifecycle state |

#### `VulnMatch`
**Package:** `com.wificracker.scan.model`

A matched vulnerability from the database for a specific network.

| Field | Type | Description |
|---|---|---|
| `cveId` | `String` | CVE or synthetic identifier |
| `title` | `String` | Vulnerability title |
| `severity` | `String` | Severity label string |
| `cvssScore` | `Float` | CVSS score `0.0..10.0` |
| `recommendation` | `String` | Remediation text |

#### `NetworkAnalysis`
**Package:** `com.wificracker.scan.domain`

Analysis result returned by `NetworkAnalyzer.analyze()`.

| Field | Type | Description |
|---|---|---|
| `signalQuality` | `SignalQuality` | Qualitative signal assessment |
| `riskLevel` | `RiskLevel` | Overall risk classification for this network |
| `riskFactors` | `List<String>` | Human-readable descriptions of identified risk factors |
| `channelCongestion` | `ChannelCongestion` | Channel usage assessment relative to all visible networks |

#### `ScanUpdate`
**Package:** `com.wificracker.scan.data`

Intermediate result emitted by `WifiCommandRunner.startScan()`.

| Field | Type | Description |
|---|---|---|
| `networks` | `List<Network>` | Networks parsed from the latest airodump-ng CSV poll |
| `clients` | `List<Client>` | Clients parsed from the latest airodump-ng CSV poll |

### 3.2 Enums

#### `EncryptionType`
**Package:** `com.wificracker.scan.model`

| Value | `label` | `riskLevel` | Description |
|---|---|---|---|
| `OPEN` | `"Open"` | `CRITICAL` | No encryption |
| `WEP` | `"WEP"` | `CRITICAL` | WEP — cryptographically broken |
| `WPA` | `"WPA"` | `HIGH` | WPA with TKIP — deprecated |
| `WPA2` | `"WPA2"` | `MEDIUM` | WPA2/RSN — current standard |
| `WPA3` | `"WPA3"` | `LOW` | WPA3/SAE — modern standard |
| `UNKNOWN` | `"Unknown"` | `HIGH` | Could not determine encryption type |

#### `RiskLevel`
**Package:** `com.wificracker.scan.model`

`LOW` | `MEDIUM` | `HIGH` | `CRITICAL`

#### `ScanStatus`
**Package:** `com.wificracker.scan.model`

`IDLE` | `SCANNING` | `PAUSED` | `COMPLETED` | `FAILED`

#### `SignalQuality`
**Package:** `com.wificracker.scan.domain`

| Value | `label` | dBm Range |
|---|---|---|
| `EXCELLENT` | `"Excellent"` | >= -50 |
| `GOOD` | `"Good"` | -60 to -51 |
| `FAIR` | `"Fair"` | -70 to -61 |
| `WEAK` | `"Weak"` | -80 to -71 |
| `VERY_WEAK` | `"Very Weak"` | < -80 |

#### `ChannelCongestion`
**Package:** `com.wificracker.scan.domain`

`LOW` (< 3 APs on channel) | `MEDIUM` (3–4 APs) | `HIGH` (>= 5 APs)

### 3.3 Domain Layer

#### `ScanEngine`
**Package:** `com.wificracker.scan.domain`
**Scope:** `@Singleton`

The central scanning engine. On `startScan()`, it simultaneously runs:
1. An Android system scan loop (`cmd wifi start-scan` / `cmd wifi list-scan-results`) every 8 seconds.
2. An MTK ICS passive capture loop (only when `ChipsetMonitorCapability.patchInstalled == true` and vendor is `MEDIATEK`).

Both sources merge their discovered networks into a single `scanState` flow using BSSID-keyed deduplication. Networks are sorted descending by signal strength.

**StateFlow:**

| Property | Type | Description |
|---|---|---|
| `scanState` | `StateFlow<ScanResult>` | Current scan state. Updates in real time as networks are discovered. |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `startScan` | `suspend fun startScan(interfaceName: String)` | Initializes state, starts Android scan loop, conditionally starts ICS capture. No-op if already scanning. |
| `stopScan` | `suspend fun stopScan()` | Cancels scan and ICS coroutines, disables ICS capture, calls `commandRunner.stopScan()`, transitions state to `COMPLETED`. |

#### `NetworkAnalyzer`
**Package:** `com.wificracker.scan.domain`
**Scope:** `@Singleton`

Pure analysis — no I/O or side effects.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `analyze` | `fun analyze(network: Network, allNetworks: List<Network> = emptyList()): NetworkAnalysis` | Computes signal quality, risk factors, overall risk level, and channel congestion. |
| `assessSignalQuality` | `fun assessSignalQuality(dbm: Int): SignalQuality` | Maps a dBm value to a `SignalQuality` enum. |
| `identifyRiskFactors` | `fun identifyRiskFactors(network: Network): List<String>` | Returns a list of human-readable risk descriptions based on encryption type, WPS, SSID visibility, and cipher. |

#### `VulnMatcher`
**Package:** `com.wificracker.scan.domain`
**Scope:** `@Singleton`

Matches networks against the vulnerability database and applies hardcoded rules for `OPEN` networks and WPS.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `matchVulnerabilities` | `suspend fun matchVulnerabilities(network: Network): List<VulnMatch>` | Queries `VulnDao.getByProtocol()`, appends synthetic entries for OPEN and WPS, sorts by CVSS score descending. |
| `matchAllNetworks` | `suspend fun matchAllNetworks(networks: List<Network>): Map<String, List<VulnMatch>>` | Returns a map of `bssid -> List<VulnMatch>` for a list of networks. |

#### `ChannelHopper`
**Package:** `com.wificracker.scan.domain`
**Scope:** `@Singleton`

Cycles through WiFi channels on the given interface. Supports MediaTek chipsets via `SET_TEST_CMD` frequency tuning, and standard interfaces via `iw dev set channel`.

**Constants:**

| Constant | Value |
|---|---|
| `CHANNELS_2_4GHZ` | Channels 1–13 |
| `CHANNELS_5GHZ` | Channels 36, 40, ..., 165 |
| `ALL_CHANNELS` | `CHANNELS_2_4GHZ + CHANNELS_5GHZ` |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `startHopping` | `fun startHopping(interfaceName: String, channels: List<Int> = CHANNELS_2_4GHZ, dwellTimeMs: Long = 500): Flow<Int>` | Returns a cold flow emitting the current channel number after each successful hop. Loops indefinitely until cancelled. |
| `stopHopping` | `fun stopHopping()` | For MTK: exits test mode. Has no effect on standard interfaces (the caller must cancel the flow). |
| `setChannel` | `fun setChannel(interfaceName: String, channel: Int): Boolean` | Sets a specific channel. Returns `true` on success. |
| `getCurrentChannel` | `fun getCurrentChannel(interfaceName: String): Int` | Returns the current channel from `iw dev info`, or `0` on failure. |

#### `PcapExporter`
**Package:** `com.wificracker.scan.domain`
**Scope:** `@Singleton`

**Export directory:** `/data/local/tmp/wificracker/exports`

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `startCapture` | `fun startCapture(interfaceName: String, outputFile: String? = null): String` | Starts live packet capture using `tcpdump` (preferred) or `airodump-ng --output-format pcap`. Returns the output file path. |
| `stopCapture` | `fun stopCapture()` | Kills all `tcpdump` and `airodump-ng` processes. |
| `exportScanResultJson` | `fun exportScanResultJson(scanResult: ScanResult): String` | Writes a JSON summary of the scan result. Returns the output file path. |
| `listCaptures` | `fun listCaptures(): List<File>` | Lists all `.pcap` and `.json` files in the export directory. |

### 3.4 Data Layer

#### `WifiCommandRunner`
**Package:** `com.wificracker.scan.data`
**Scope:** `@Singleton`

Airodump-ng CSV-based scanner. Starts `airodump-ng` with CSV output, polls the file every 2 seconds, and emits parsed `ScanUpdate` objects.

**Constants:**

| Constant | Value |
|---|---|
| `SCAN_OUTPUT_DIR` | `"/data/local/tmp/wificracker/scans"` |
| `POLL_INTERVAL_MS` | `2000` |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `startScan` | `fun startScan(interfaceName: String): Flow<ScanUpdate>` | Launches `airodump-ng` in background with `--output-format csv`, polls the CSV output file and emits updates. Flow runs on `Dispatchers.IO`. |
| `stopScan` | `fun stopScan(interfaceName: String): ShellResult` | Kills the `airodump-ng` process for the given interface. |
| `parseCsvOutput` | `fun parseCsvOutput(csvContent: String): ScanUpdate` | Parses an airodump-ng CSV string into a `ScanUpdate`. Handles both the network section (above `Station MAC` header) and client section. |

### 3.5 ScanViewModel

**Package:** `com.wificracker.scan.ui`
**Class:** `ScanViewModel`
**Annotation:** `@HiltViewModel`

#### `ScanUiState`

| Field | Type | Default | Description |
|---|---|---|---|
| `scanResult` | `ScanResult` | `ScanResult()` | Latest scan state from `ScanEngine` |
| `interfaces` | `List<WifiInterface>` | `emptyList()` | Available WiFi interfaces |
| `selectedInterface` | `WifiInterface?` | `null` | Interface chosen for scanning |
| `vulnMatches` | `Map<String, List<VulnMatch>>` | `emptyMap()` | BSSID-keyed vulnerability matches, updated after each scan update |
| `isScanning` | `Boolean` | `false` | `true` when `scanResult.status == SCANNING` |
| `isStarting` | `Boolean` | `false` | `true` during the brief period between user action and first scan update |
| `errorMessage` | `String?` | `null` | User-facing error text |
| `chipsetInfo` | `String` | `""` | Human-readable chipset description (e.g. `"MediaTek: MediaTek mt6878"`) |
| `supportsInternalMonitor` | `Boolean` | `false` | Whether the device chipset supports passive capture without external hardware |
| `currentChannel` | `Int` | `0` | Currently active channel when hopping is enabled |
| `packetCount` | `Long` | `0` | Running packet count (for future use) |
| `channelHopping` | `Boolean` | `false` | Whether channel hopping is currently active |
| `selectedBssid` | `String?` | `null` | BSSID of the network selected for targeting |

**StateFlow:**

| Property | Type |
|---|---|
| `uiState` | `StateFlow<ScanUiState>` |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `selectInterface` | `fun selectInterface(iface: WifiInterface)` | Updates the selected interface. |
| `selectNetwork` | `fun selectNetwork(bssid: String)` | Publishes the network to `SelectedNetworkRepository` and sets `selectedBssid`. |
| `startScan` | `fun startScan()` | Calls `ScanEngine.startScan()` on the selected interface. |
| `stopScan` | `fun stopScan()` | Stops hopping, cancels packet count, calls `ScanEngine.stopScan()`. |
| `toggleChannelHopping` | `fun toggleChannelHopping()` | Starts or stops `ChannelHopper.startHopping()` on the selected interface. |
| `exportPcap` | `fun exportPcap()` | Calls `PcapExporter.startCapture()` on the selected interface. |

---

## 4. Module: attack

Package root: `com.wificracker.attack`

### 4.1 Data Models

#### `Attack`
**Package:** `com.wificracker.attack.model`

Represents a single attack session.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `String` | UUID | Unique attack session identifier |
| `type` | `AttackType` | | The attack variant to execute |
| `targetBssid` | `String` | | Target AP MAC address |
| `targetSsid` | `String` | `""` | Target network name (required for Evil Twin) |
| `interfaceName` | `String` | | Network interface to use |
| `status` | `AttackStatus` | `PENDING` | Current lifecycle state |
| `startTime` | `Long` | `0` | Unix epoch milliseconds when execution began |
| `endTime` | `Long` | `0` | Unix epoch milliseconds when execution ended |
| `output` | `String` | `""` | Accumulated console output (for persistence; live output is in `AttackOrchestrator.consoleOutput`) |

#### `AttackResult`
**Package:** `com.wificracker.attack.model`

Summary of a completed attack.

| Field | Type | Default | Description |
|---|---|---|---|
| `attackId` | `String` | | ID from the originating `Attack` |
| `success` | `Boolean` | | Whether the attack achieved its objective |
| `message` | `String` | `""` | Human-readable outcome description |
| `captures` | `List<Capture>` | `emptyList()` | Files produced by this attack |
| `duration` | `Long` | `0` | Duration in milliseconds |

#### `Capture`
**Package:** `com.wificracker.attack.model`

A file artifact produced by an attack.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `String` | UUID | Unique capture identifier |
| `attackId` | `String` | | ID of the attack that produced this capture |
| `type` | `CaptureType` | | Type of captured data |
| `filePath` | `String` | | Absolute path to the file on device |
| `fileSize` | `Long` | `0` | File size in bytes |
| `targetBssid` | `String` | | BSSID of the targeted AP |
| `targetSsid` | `String` | `""` | SSID of the targeted network |
| `timestamp` | `Long` | current time | Unix epoch milliseconds |

#### `PrerequisiteResult`
**Package:** `com.wificracker.attack.domain`

Result of a prerequisite check before an attack.

| Field | Type | Default | Description |
|---|---|---|---|
| `satisfied` | `Boolean` | | `true` if all prerequisites are met |
| `missingPrerequisites` | `List<String>` | `emptyList()` | Human-readable descriptions of what is missing |

### 4.2 Enums

#### `AttackType`
**Package:** `com.wificracker.attack.model`

| Value | `label` | `description` | Required Binary |
|---|---|---|---|
| `DEAUTH` | `"Deauthentication"` | Force client disconnection to capture handshake | `aireplay-ng` or MTK `wpa_driver` |
| `HANDSHAKE_CAPTURE` | `"Handshake Capture"` | Capture WPA/WPA2 4-way handshake | `airodump-ng` or MTK ICS |
| `PMKID_CAPTURE` | `"PMKID Capture"` | Capture PMKID without connected clients | `hcxdumptool` |
| `EVIL_TWIN` | `"Evil Twin"` | Create fake access point to intercept traffic | `hostapd`, `dnsmasq` |
| `PROBE_SNIFF` | `"Probe Sniffing"` | Capture probe requests to identify remembered networks | `airodump-ng` |

#### `AttackStatus`
**Package:** `com.wificracker.attack.model`

`PENDING` | `RUNNING` | `COMPLETED` | `FAILED` | `CANCELLED`

#### `CaptureType`
**Package:** `com.wificracker.attack.model`

`HANDSHAKE` | `PMKID` | `PCAP` | `PROBE_LOG`

### 4.3 Domain Layer

#### `AttackOrchestrator`
**Package:** `com.wificracker.attack.domain`
**Scope:** `@Singleton`

Central controller for the attack module. Runs prerequisite checks, dispatches to the correct `WifiAttack` implementation, streams console output, and records results to `SessionCollector`.

**StateFlow:**

| Property | Type | Description |
|---|---|---|
| `attackState` | `StateFlow<Attack>` | Current attack instance with its `AttackStatus`. |
| `consoleOutput` | `StateFlow<List<String>>` | Accumulated console lines from the running attack. Reset to empty on each new attack launch. |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `launchAttack` | `suspend fun launchAttack(attack: Attack)` | Runs `PrerequisiteCheck.check()`. On failure, emits a `[!]` line and sets status to `FAILED`. On success, sets status to `RUNNING`, dispatches to the appropriate `WifiAttack`, collects all output lines, then sets status to `COMPLETED` and records to `SessionCollector`. |
| `stopAttack` | `suspend fun stopAttack()` | Cancels the running coroutine job, calls `WifiAttack.stop()`, sets status to `CANCELLED`. |
| `getAttackImpl` | `fun getAttackImpl(type: AttackType): WifiAttack` | Returns the `WifiAttack` implementation for a given type. |

#### `PrerequisiteCheck`
**Package:** `com.wificracker.attack.domain`
**Scope:** `@Singleton`

Validates device readiness before an attack. Differentiates between standard (external adapter + monitor mode) and MTK patched driver paths.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `check` | `fun check(attackType: AttackType, interfaceName: String): PrerequisiteResult` | Checks root, monitor mode (if no MTK patch), and required binaries per attack type. |

**Required binaries by attack type (standard path):**

| `AttackType` | Required Binaries |
|---|---|
| `DEAUTH` | `aireplay-ng` |
| `HANDSHAKE_CAPTURE` | `airodump-ng` |
| `PMKID_CAPTURE` | `hcxdumptool` |
| `EVIL_TWIN` | `hostapd`, `dnsmasq` |
| `PROBE_SNIFF` | `airodump-ng` |

**MTK patched path:**
- `DEAUTH` and `HANDSHAKE_CAPTURE`: requires `wpa_driver` binary at `/data/local/tmp/wpa_driver`.
- `EVIL_TWIN`: same as standard path.
- Other types: no additional binaries required.

### 4.4 Attack Implementations (WifiAttack)

**Interface:** `com.wificracker.attack.domain.attacks.WifiAttack`

```kotlin
interface WifiAttack {
    fun execute(attack: Attack): Flow<String>
    fun stop(attack: Attack)
}
```

All implementations emit `Flow<String>` where each string is a console log line prefixed with `[*]` (info), `[+]` (success), or `[!]` (error/warning).

#### `DeauthAttack`
**Scope:** `@Singleton`

- **MTK path:** Sends `AP_STA_DISASSOC Mac={bssid}` via `wpa_driver` 50 times with 100ms intervals.
- **Standard path:** Runs `aireplay-ng --deauth 0 -a {bssid} {interface}`.
- **Stop:** Kills `aireplay-ng` processes targeting the specific BSSID.

#### `HandshakeCapture`
**Scope:** `@Singleton`

Capture directory: `/data/local/tmp/wificracker/captures`

- **MTK path:** Enables ICS capture (`SNIFFER` + `ics_enable 1`), reads `/dev/fw_log_ics` to a `.ics` file, sends 5 deauth frames to force reconnection, waits 15 seconds, stops capture (`ics_enable 0`).
- **Standard path:** Runs `airodump-ng --bssid {bssid} --write {prefix} --output-format pcap` for 120 seconds.
- **Stop:** Kills both `cat /dev/fw_log_ics` and `airodump-ng` processes; calls `ics_enable 0`.

#### `PmkidCapture`
**Scope:** `@Singleton`

Capture directory: `/data/local/tmp/wificracker/captures`

- Runs `hcxdumptool -i {interface} --filterlist_ap={bssid} --filtermode=2 -o {output.pcapng}` for 60 seconds.
- No MTK-specific path.
- **Stop:** Kills `hcxdumptool`.

#### `EvilTwinAttack`
**Scope:** `@Singleton`

- Generates a `hostapd` configuration targeting the SSID of the original network (open, channel 6).
- Starts `hostapd` and `dnsmasq` for DHCP (range `192.168.1.2–.30/24`).
- **Stop:** Kills both `hostapd` and `dnsmasq`.

#### `ProbeSniff`
**Scope:** `@Singleton`

Capture directory: `/data/local/tmp/wificracker/captures`

- Runs `airodump-ng {interface} --write {prefix} --output-format csv` for 60 seconds.
- **Stop:** Kills all `airodump-ng` processes.

### 4.5 AttackViewModel

**Package:** `com.wificracker.attack.ui`
**Class:** `AttackViewModel`
**Annotation:** `@HiltViewModel`

#### `AttackUiState`

| Field | Type | Default | Description |
|---|---|---|---|
| `selectedAttackType` | `AttackType` | `DEAUTH` | Currently configured attack type |
| `targetBssid` | `String` | `""` | Target BSSID (may be pre-populated from `SelectedNetworkRepository`) |
| `targetSsid` | `String` | `""` | Target SSID |
| `interfaces` | `List<WifiInterface>` | `emptyList()` | Only interfaces with `supportsMonitor == true` |
| `selectedInterface` | `WifiInterface?` | `null` | Interface chosen for the attack |
| `attackStatus` | `AttackStatus` | `PENDING` | Mirrors `AttackOrchestrator.attackState.status` |
| `consoleLines` | `List<String>` | `emptyList()` | Mirrors `AttackOrchestrator.consoleOutput` |
| `isRunning` | `Boolean` | `false` | `true` when `attackStatus == RUNNING` |
| `hasPreselectedTarget` | `Boolean` | `false` | `true` when target was set via `SelectedNetworkRepository` |

**StateFlow:**

| Property | Type |
|---|---|
| `uiState` | `StateFlow<AttackUiState>` |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `selectAttackType` | `fun selectAttackType(type: AttackType)` | Updates attack type. |
| `setTarget` | `fun setTarget(bssid: String, ssid: String)` | Manually sets target when not pre-populated. |
| `selectInterface` | `fun selectInterface(iface: WifiInterface)` | Updates the selected interface. |
| `launchAttack` | `fun launchAttack()` | Constructs an `Attack` from current state and calls `AttackOrchestrator.launchAttack()`. |
| `stopAttack` | `fun stopAttack()` | Calls `AttackOrchestrator.stopAttack()`. |

---

## 5. Module: crack

Package root: `com.wificracker.crack`

### 5.1 Data Models

#### `CrackJob`
**Package:** `com.wificracker.crack.model`

Configuration for a single password recovery attempt.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `String` | UUID | Unique job identifier |
| `capturePath` | `String` | | Path to the `.cap`, `.pcapng`, or `.hccapx` capture file |
| `hashPath` | `String` | `""` | Path to a pre-converted `.hc22000` hash file. If blank, `CrackOrchestrator` converts `capturePath` first. |
| `targetBssid` | `String` | `""` | Target AP MAC address (passed to `aircrack-ng -b`) |
| `targetSsid` | `String` | `""` | Target network name (for logging and reporting) |
| `strategy` | `CrackStrategy` | | Which cracking strategy to use |
| `wordlistPath` | `String` | `""` | Path to the primary wordlist file |
| `secondWordlistPath` | `String` | `""` | Path to the second wordlist (Combinator strategy only) |
| `charset` | `String` | `""` | Character set for brute-force (passed as hashcat `-1` custom charset). Empty uses default. |
| `minLength` | `Int` | `8` | Minimum password length for brute-force |
| `maxLength` | `Int` | `12` | Maximum password length for brute-force |
| `ruleset` | `String` | `""` | Path to a hashcat rule file. Empty uses `best64.rule`. |
| `status` | `CrackStatus` | `PENDING` | Lifecycle state |
| `startTime` | `Long` | `0` | Unix epoch milliseconds |

#### `CrackProgress`
**Package:** `com.wificracker.crack.model`

Real-time progress snapshot emitted during cracking.

| Field | Type | Default | Description |
|---|---|---|---|
| `jobId` | `String` | `""` | ID of the active `CrackJob` |
| `status` | `CrackStatus` | `PENDING` | Current lifecycle state |
| `keysPerSecond` | `Long` | `0` | Throughput in candidates per second |
| `keysTested` | `Long` | `0` | Total candidates tested so far |
| `keysTotal` | `Long` | `0` | Total candidates in the keyspace (0 if unknown) |
| `progress` | `Float` | `0f` | Fraction complete `0.0..1.0` |
| `eta` | `String` | `""` | Human-readable estimated time to completion |
| `currentKey` | `String` | `""` | Last tested candidate, or the found password when `status == COMPLETED` |
| `message` | `String` | `""` | Status message for display |

#### `CrackResult`
**Package:** `com.wificracker.crack.model`

Final outcome of a crack job.

| Field | Type | Default | Description |
|---|---|---|---|
| `jobId` | `String` | | ID of the `CrackJob` |
| `success` | `Boolean` | | `true` if password was recovered |
| `password` | `String` | `""` | Recovered password (`""` if not found) |
| `duration` | `Long` | `0` | Total job duration in milliseconds |
| `keysTested` | `Long` | `0` | Total candidates tested |
| `keysPerSecond` | `Long` | `0` | Average throughput |

#### `Wordlist`
**Package:** `com.wificracker.crack.model`

Represents a wordlist file available for cracking.

| Field | Type | Default | Description |
|---|---|---|---|
| `name` | `String` | | Display name |
| `path` | `String` | | Absolute file path |
| `size` | `Long` | `0` | File size in bytes |
| `wordCount` | `Long` | `0` | Number of lines/words |
| `isBuiltIn` | `Boolean` | `false` | `true` for bundled wordlists |

#### `ConversionResult`
**Package:** `com.wificracker.crack.domain`

Result from `HashConverter.convertCapToHc22000()`.

| Field | Type | Default | Description |
|---|---|---|---|
| `success` | `Boolean` | | `true` if conversion succeeded |
| `outputPath` | `String` | `""` | Absolute path of the generated `.hc22000` file |
| `format` | `String` | `""` | Output format identifier (`"hc22000"`) |
| `error` | `String` | `""` | Error message from `hcxpcapngtool` on failure |

### 5.2 Enums

#### `CrackStrategy`
**Package:** `com.wificracker.crack.model`

| Value | `label` | `description` | Binary |
|---|---|---|---|
| `DICTIONARY` | `"Dictionary"` | Test passwords from a wordlist file | `aircrack-ng` |
| `BRUTE_FORCE` | `"Brute Force"` | Try all combinations of a character set | `hashcat` (mode `22000`, attack `3`) |
| `RULE_BASED` | `"Rule Based"` | Apply mutation rules to a wordlist | `hashcat` (mode `22000`, attack `0`) |
| `COMBINATOR` | `"Combinator"` | Combine two wordlists together | `hashcat` (mode `22000`, attack `1`) |

#### `CrackStatus`
**Package:** `com.wificracker.crack.model`

`PENDING` | `CONVERTING` | `RUNNING` | `PAUSED` | `COMPLETED` | `FAILED` | `CANCELLED`

Note: `CONVERTING` is the state during `hcxpcapngtool` conversion, before the actual cracking starts.

### 5.3 Domain Layer

#### `CrackOrchestrator`
**Package:** `com.wificracker.crack.domain`
**Scope:** `@Singleton`

Manages the lifecycle of a crack job. If `hashPath` is blank on the submitted job, it first converts the capture file via `HashConverter`. Then dispatches to the appropriate `CrackStrategyImpl`.

**StateFlow:**

| Property | Type | Description |
|---|---|---|
| `progress` | `StateFlow<CrackProgress>` | Real-time progress updates. |
| `result` | `StateFlow<CrackResult?>` | Final result when `status == COMPLETED`. `null` before completion. |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `startCrack` | `suspend fun startCrack(job: CrackJob)` | Optionally converts the capture, then runs the strategy. On completion, records to `SessionCollector` and emits to `result`. |
| `stopCrack` | `suspend fun stopCrack()` | Cancels the job coroutine, calls `stop()` on the dictionary strategy (generic kill). Sets status to `CANCELLED`. |

#### `HashConverter`
**Package:** `com.wificracker.crack.domain`
**Scope:** `@Singleton`

**Output directory:** `/data/local/tmp/wificracker/hashes`

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `convertCapToHc22000` | `fun convertCapToHc22000(capFilePath: String): ConversionResult` | Runs `hcxpcapngtool -o {output}.hc22000 {input}`. Returns the output path on success. |
| `detectCaptureType` | `fun detectCaptureType(filePath: String): String` | Runs `file {path}` and classifies as `"pcapng"`, `"pcap"`, `"hccapx"`, or `"unknown"`. |

#### `WordlistManager`
**Package:** `com.wificracker.crack.domain`
**Scope:** `@Singleton`

**Wordlist directory:** `/data/local/tmp/wificracker/wordlists`

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `getInstalledWordlists` | `fun getInstalledWordlists(): List<Wordlist>` | Lists all `.txt` files in the wordlist directory with metadata. |
| `getBuiltInWordlists` | `fun getBuiltInWordlists(): List<Wordlist>` | Returns a static list of 2 bundled wordlists: `wifi_top1000.txt` and `common_passwords.txt`. |
| `getCustomWordlists` | `fun getCustomWordlists(): List<Wordlist>` | Lists user-added wordlists (excluding built-in file name prefixes). |
| `getAllWordlists` | `fun getAllWordlists(): List<Wordlist>` | `getBuiltInWordlists() + getCustomWordlists()` |
| `downloadWordlist` | `fun downloadWordlist(downloadable: DownloadableWordlist, onProgress: (String) -> Unit): Boolean` | Downloads via `curl`, then `wget`, then Termux `curl`. Returns `true` on success. |
| `importWordlist` | `fun importWordlist(sourcePath: String, name: String): Wordlist` | Copies a file into the wordlist directory and returns the `Wordlist` metadata. |
| `deleteWordlist` | `fun deleteWordlist(path: String): Boolean` | Deletes the wordlist file. |

**Downloadable Wordlists (built-in catalog):**

| ID | Label | Estimated Size |
|---|---|---|
| `rockyou` | RockYou (14M passwords, 134MB) | 139.9 MB |
| `wifi-top1000` | WiFi Top 1000 | 48 KB |
| `common-passwords-10k` | Common 10K | 100 KB |
| `darkweb-top10k` | Dark Web Top 10K | 100 KB |
| `probable-wpa` | Probable WPA (4800 passwords) | 48 KB |
| `french-passwords` | French passwords (common FR) | 50 KB |

### 5.4 Crack Strategy Implementations

**Interface:** `com.wificracker.crack.domain.strategies.CrackStrategyImpl`

```kotlin
interface CrackStrategyImpl {
    fun execute(job: CrackJob): Flow<CrackProgress>
    fun stop()
}
```

All implementations run on `Dispatchers.IO`. They emit a `RUNNING` progress on start, then a `COMPLETED` progress with either the found password in `currentKey` or an empty string.

#### `DictionaryAttack`
Runs `aircrack-ng -w {wordlist} -b {bssid} {hashFile}` (up to 3600s timeout). Parses `KEY FOUND! [ ... ]` from stdout.

#### `BruteForceAttack`
Runs `hashcat -m 22000 -a 3 [-1 {charset}] --increment --increment-min={min} --increment-max={max} {hashPath} {mask}` (up to 7200s). Uses `?1` mask tokens. Detects crack by `"Cracked"` in stdout.

#### `RuleBasedAttack`
Runs `hashcat -m 22000 -a 0 -r {ruleset} {hashPath} {wordlist}` (up to 7200s). Defaults to `/data/local/tmp/wificracker/rules/best64.rule` if `CrackJob.ruleset` is blank.

#### `CombinatorAttack`
Runs `hashcat -m 22000 -a 1 {hashPath} {wordlist1} {wordlist2}` (up to 7200s).

### 5.5 CrackViewModel

**Package:** `com.wificracker.crack.ui`
**Class:** `CrackViewModel`
**Annotation:** `@HiltViewModel`

#### `CrackUiState`

| Field | Type | Default | Description |
|---|---|---|---|
| `capturePath` | `String` | `""` | Path to the capture file to crack |
| `targetBssid` | `String` | `""` | Target BSSID (may be pre-populated from `SelectedNetworkRepository`) |
| `targetSsid` | `String` | `""` | Target SSID |
| `selectedStrategy` | `CrackStrategy` | `DICTIONARY` | Currently chosen strategy |
| `selectedWordlist` | `Wordlist?` | `null` | Chosen wordlist for dictionary/rule/combinator strategies |
| `wordlists` | `List<Wordlist>` | `emptyList()` | All available wordlists from `WordlistManager` |
| `progress` | `CrackProgress` | `CrackProgress()` | Mirrors `CrackOrchestrator.progress` |
| `result` | `CrackResult?` | `null` | Mirrors `CrackOrchestrator.result` |
| `isRunning` | `Boolean` | `false` | `true` when status is `RUNNING` or `CONVERTING` |
| `hasTargetNetwork` | `Boolean` | `false` | `true` when target was set via `SelectedNetworkRepository` |

**StateFlow:**

| Property | Type |
|---|---|
| `uiState` | `StateFlow<CrackUiState>` |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `setCapture` | `fun setCapture(path: String, bssid: String = "", ssid: String = "")` | Sets the capture file and optional target identifiers. |
| `selectStrategy` | `fun selectStrategy(strategy: CrackStrategy)` | Updates the selected strategy. |
| `selectWordlist` | `fun selectWordlist(wordlist: Wordlist)` | Updates the selected wordlist. |
| `startCrack` | `fun startCrack()` | Constructs a `CrackJob` from current state and calls `CrackOrchestrator.startCrack()`. |
| `stopCrack` | `fun stopCrack()` | Calls `CrackOrchestrator.stopCrack()`. |

---

## 6. Module: report

Package root: `com.wificracker.report`

### 6.1 Data Models

#### `Report`
**Package:** `com.wificracker.report.model`

The root document object representing a complete pentest report.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `String` | UUID | Unique report identifier |
| `missionInfo` | `MissionInfo` | `MissionInfo()` | Mission metadata (title, date, scope, client) |
| `companyProfile` | `CompanyProfile` | `CompanyProfile()` | Auditor/pentester company details |
| `findings` | `List<Finding>` | `emptyList()` | All findings, sorted by CVSS score descending |
| `recommendations` | `List<Recommendation>` | `emptyList()` | Auto-generated and manual recommendations |
| `overallScore` | `String` | `"N/A"` | Security grade: `"A"` through `"F"` |
| `executiveSummary` | `String` | `""` | Auto-generated multi-line text summary |
| `createdAt` | `Long` | current time | Unix epoch milliseconds |
| `status` | `ReportStatus` | `DRAFT` | Report lifecycle state |

#### `Finding`
**Package:** `com.wificracker.report.model`

A single security finding identified during the assessment.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `String` | UUID | Unique finding identifier |
| `title` | `String` | | Short finding title |
| `description` | `String` | | Detailed description of the vulnerability or observation |
| `severity` | `Severity` | | CVSS-based severity classification |
| `cvssScore` | `Float` | | CVSS score `0.0..10.0` |
| `cvssVector` | `String` | `""` | CVSS v3 vector string (optional) |
| `impact` | `String` | `""` | Business or technical impact description |
| `evidence` | `String` | `""` | Technical evidence (e.g. captured file paths, packet details) |
| `recommendation` | `String` | `""` | Specific remediation guidance |
| `networkBssid` | `String` | `""` | BSSID of the affected network |
| `networkSsid` | `String` | `""` | SSID of the affected network |

#### `Recommendation`
**Package:** `com.wificracker.report.model`

A remediation recommendation linked to one or more findings.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `String` | UUID | Unique recommendation identifier |
| `title` | `String` | | Short title |
| `description` | `String` | | Detailed remediation guidance |
| `priority` | `Int` | `0` | Priority rank — lower is higher priority (`1` = urgent) |
| `relatedFindings` | `List<String>` | `emptyList()` | IDs of findings this recommendation addresses |

#### `MissionInfo`
**Package:** `com.wificracker.report.model`

Metadata about the pentest engagement.

| Field | Type | Default | Description |
|---|---|---|---|
| `title` | `String` | `""` | Mission or engagement title |
| `date` | `Long` | current time | Date of the assessment |
| `scope` | `String` | `""` | Description of what was in scope |
| `methodology` | `String` | `"WiFi security assessment using WiFi Cracker tool suite"` | Assessment methodology |
| `clientProfile` | `ClientProfile` | `ClientProfile()` | Embedded client data snapshot |

#### `ClientProfile`
**Package:** `com.wificracker.report.model`

Client organization data embedded in a report.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `Long` | `0` | Database row ID (`0` for unsaved) |
| `companyName` | `String` | `""` | Client company name |
| `address` | `String` | `""` | Client postal address |
| `contactName` | `String` | `""` | Primary contact full name |
| `contactTitle` | `String` | `""` | Contact job title |
| `contactEmail` | `String` | `""` | Contact email address |
| `contractReference` | `String` | `""` | Contract or purchase order reference |
| `logoPath` | `String` | `""` | Absolute path to client logo image file |

#### `CompanyProfile`
**Package:** `com.wificracker.report.model`

Auditor/pentester company data embedded in a report.

| Field | Type | Default | Description |
|---|---|---|---|
| `id` | `Long` | `0` | Database row ID (`0` for unsaved) |
| `name` | `String` | `""` | Company name |
| `address` | `String` | `""` | Company postal address |
| `siret` | `String` | `""` | French SIRET company registration number |
| `contactName` | `String` | `""` | Contact full name |
| `contactEmail` | `String` | `""` | Contact email |
| `contactPhone` | `String` | `""` | Contact phone number |
| `certifications` | `List<String>` | `emptyList()` | Professional certifications (e.g. `["OSCP", "CEH"]`) |
| `legalMention` | `String` | `""` | Legal disclaimer or mention |
| `logoPath` | `String` | `""` | Absolute path to company logo image file |

#### `RiskSummary`
**Package:** `com.wificracker.report.domain`

Computed statistics over a set of findings.

| Field | Type | Description |
|---|---|---|
| `overallGrade` | `String` | Security grade `"A"` through `"F"` based on average CVSS |
| `criticalCount` | `Int` | Number of CRITICAL findings |
| `highCount` | `Int` | Number of HIGH findings |
| `mediumCount` | `Int` | Number of MEDIUM findings |
| `lowCount` | `Int` | Number of LOW findings |
| `infoCount` | `Int` | Number of INFO findings |
| `averageCvss` | `Float` | Mean CVSS score across all findings |

#### `SessionStats`
**Package:** `com.wificracker.report.ui`

Aggregated session activity statistics used in report executive summary.

| Field | Type | Default | Description |
|---|---|---|---|
| `networksScanned` | `Int` | `0` | Total networks discovered |
| `attacksPerformed` | `Int` | `0` | Total attacks run |
| `attacksSuccessful` | `Int` | `0` | Attacks with status `COMPLETED` |
| `cracksAttempted` | `Int` | `0` | Total crack jobs run |
| `cracksSuccessful` | `Int` | `0` | Crack jobs that found a password |
| `passwordsFound` | `List<String>` | `emptyList()` | SSIDs for which passwords were recovered |

### 6.2 Enums

#### `Severity`
**Package:** `com.wificracker.report.model`

| Value | `label` | `color` (ARGB hex) | CVSS Range |
|---|---|---|---|
| `CRITICAL` | `"Critical"` | `0xFFFF4444` (red) | >= 9.0 |
| `HIGH` | `"High"` | `0xFFFF8C00` (orange) | 7.0–8.9 |
| `MEDIUM` | `"Medium"` | `0xFFFFD700` (gold) | 4.0–6.9 |
| `LOW` | `"Low"` | `0xFF00C853` (green) | 0.1–3.9 |
| `INFO` | `"Info"` | `0xFF58A6FF` (blue) | 0.0 |

#### `ExportFormat`
**Package:** `com.wificracker.report.model`

| Value | `extension` | `mimeType` |
|---|---|---|
| `PDF` | `"pdf"` | `"application/pdf"` |
| `HTML` | `"html"` | `"text/html"` |
| `JSON` | `"json"` | `"application/json"` |

#### `ReportStatus`
**Package:** `com.wificracker.report.model`

`DRAFT` | `IN_PROGRESS` | `COMPLETED` | `EXPORTED`

#### `ReportStep`
**Package:** `com.wificracker.report.ui`

UI wizard step enum used by `ReportViewModel`.

`MISSION_INFO` → `FINDINGS` → `PREVIEW` → `EXPORT`

### 6.3 Report Generation Pipeline

The report pipeline follows a strict three-stage sequence:

```
SessionCollector (raw data)
        |
        v
DataAggregator   ← ScanEngine.scanState (networks + vulnMatches)
   + VulnMatcher ← AttackOrchestrator (attack records via SessionCollector)
                 ← CrackOrchestrator  (crack records via SessionCollector)
        |
        v (List<Finding>)
ReportGenerator
   + CvssCalculator
   + RiskRating
   + AutoRecommender
        |
        v (Report)
ExportManager
        |
        v (file path: .pdf / .html / .json)
```

#### `DataAggregator`
**Package:** `com.wificracker.report.domain`
**Scope:** `@Singleton`

Converts raw operational data into typed `Finding` objects.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `networkToFindings` | `fun networkToFindings(network: Network, vulnMatches: List<VulnMatch>): List<Finding>` | Generates findings from encryption type (OPEN/WEP/WPA each produce a finding), all matched CVEs, and WPS. Sorted by CVSS descending. |
| `crackedPasswordFinding` | `fun crackedPasswordFinding(bssid: String, ssid: String, password: String): Finding` | Produces a CRITICAL finding (`cvssScore = 10.0`) for a successfully recovered password. |
| `attackRecordToFindings` | `fun attackRecordToFindings(record: SessionCollector.AttackRecord): List<Finding>` | Maps a completed `AttackRecord` to the appropriate `Finding` by type. Returns empty list for non-`COMPLETED` records. |

**Finding generation by attack type:**

| Attack Type | Severity | CVSS |
|---|---|---|
| `DEAUTH` | MEDIUM | 5.0 |
| `HANDSHAKE_CAPTURE` | HIGH | 7.5 |
| `PMKID_CAPTURE` | HIGH | 7.5 |
| `EVIL_TWIN` | CRITICAL | 9.0 |
| `PROBE_SNIFF` | LOW | 3.0 |

#### `ReportGenerator`
**Package:** `com.wificracker.report.domain`
**Scope:** `@Singleton`

Assembles a complete `Report` object from its inputs.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `generateReport` | `fun generateReport(missionInfo: MissionInfo, companyProfile: CompanyProfile, findings: List<Finding>, manualRecommendations: List<Recommendation> = emptyList(), sessionStats: SessionStats = SessionStats()): Report` | Generates auto-recommendations, deduplicates against manual ones, computes risk summary, builds executive summary, and returns a `Report` with `status = COMPLETED`. |

#### `ExportManager`
**Package:** `com.wificracker.report.domain`
**Scope:** `@Singleton`

**Export directory:** `/data/local/tmp/wificracker/reports`

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `export` | `fun export(context: Context, report: Report, format: ExportFormat): String` | Dispatches to the format-specific export method. Returns the absolute path of the exported file. Filename pattern: `report_{id[:8]}_{timestamp}.{ext}`. |

**PDF Export:**
- Uses Android `PdfDocument` API (no external library).
- Page size: A4 (595×842 pts), 40pt margin.
- Page 1: Title page with client name, auditor company, overall grade.
- Page 2: Executive summary (pre-formatted text).
- Page 3+: Findings list with severity, CVSS, description, and recommendation. Auto page-break at 80pt remaining.
- Color scheme: dark background (`#0D1117`), green accents (`#00FF41`).

**HTML Export:**
- Inline CSS with dark theme.
- Sections: title, executive summary, findings list (severity-colored), recommendations.

**JSON Export:**
- Flat structure: `id`, `overallScore`, `client`, `date`, `findingsCount`, `findings[]`, `recommendations[]`.

#### `CvssCalculator`
**Package:** `com.wificracker.report.domain`
**Scope:** `@Singleton`

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `scoreToCvss` | `fun scoreToCvss(score: Float): Severity` | Maps a float CVSS score to the `Severity` enum. |
| `calculateOverallScore` | `fun calculateOverallScore(scores: List<Float>): Float` | Returns the maximum score from a list. Returns `0f` for empty list. |
| `scoreToGrade` | `fun scoreToGrade(averageScore: Float): String` | Returns `"A"` (avg <= 1.0) through `"F"` (avg > 7.0). |

**Grade thresholds:**

| Grade | Average CVSS Range |
|---|---|
| A | <= 1.0 |
| B | 1.1–3.0 |
| C | 3.1–5.0 |
| D | 5.1–7.0 |
| F | > 7.0 |

#### `RiskRating`
**Package:** `com.wificracker.report.domain`
**Scope:** `@Singleton`

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `computeSummary` | `fun computeSummary(findings: List<Finding>): RiskSummary` | Counts findings by severity, computes average CVSS, derives overall grade. |

#### `AutoRecommender`
**Package:** `com.wificracker.report.domain`
**Scope:** `@Singleton`

Generates recommendations from findings by text-based pattern matching.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `generateRecommendations` | `fun generateRecommendations(findings: List<Finding>): List<Recommendation>` | Infers recommendation keys from finding title+description text. Deduplicates by title. Returns sorted by priority. |

**Built-in recommendation catalog:**

| Key | Title | Priority |
|---|---|---|
| `OPEN` | Enable WiFi Encryption | 1 |
| `WEP` | Replace WEP with WPA3 | 1 |
| `KRACK` | Patch KRACK Vulnerability | 1 |
| `WEAK_PASSWORD` | Strengthen WiFi Password | 1 |
| `WPA-TKIP` | Upgrade from WPA-TKIP | 2 |
| `WPS` | Disable WPS | 2 |
| `DEFAULT_SSID` | Change Default SSID | 3 |
| `HIDDEN_SSID` | Hidden SSID Not Effective | 4 |

### 6.4 Room Database (ReportDatabase)

**Package:** `com.wificracker.report.data`
**Class:** `ReportDatabase : RoomDatabase()`
**Database version:** `1`
**Export schema:** `false`
**Entities:** `CompanyProfileEntity`, `ClientProfileEntity`

#### Entity: `CompanyProfileEntity`

**Table:** `company_profiles`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `Long` | `@PrimaryKey(autoGenerate = true)` | Auto-incremented row ID |
| `name` | `String` | | Company name |
| `address` | `String` | | Postal address |
| `siret` | `String` | | SIRET registration number |
| `contactName` | `String` | | Contact person name |
| `contactEmail` | `String` | | Contact email |
| `contactPhone` | `String` | | Contact phone |
| `certifications` | `String` | | Comma-separated certifications string |
| `legalMention` | `String` | | Legal disclaimer text |
| `logoPath` | `String` | | Absolute path to logo file |

#### DAO: `CompanyProfileDao`

| Method | Return Type | Description |
|---|---|---|
| `getCompanyProfile()` | `Flow<CompanyProfileEntity?>` | Single-row query (LIMIT 1). Emits `null` if no profile saved. |
| `save(profile: CompanyProfileEntity)` | `suspend Long` | Upsert with `REPLACE` conflict. Returns the row ID. |
| `update(profile: CompanyProfileEntity)` | `suspend Unit` | Standard Room `@Update`. |

#### Entity: `ClientProfileEntity`

**Table:** `client_profiles`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `Long` | `@PrimaryKey(autoGenerate = true)` | Auto-incremented row ID |
| `companyName` | `String` | | Client company name |
| `address` | `String` | | Postal address |
| `contactName` | `String` | | Contact person name |
| `contactTitle` | `String` | | Contact job title |
| `contactEmail` | `String` | | Contact email |
| `contractReference` | `String` | | Contract or PO reference |
| `logoPath` | `String` | | Absolute path to logo file |

#### DAO: `ClientProfileDao`

| Method | Return Type | Description |
|---|---|---|
| `getAllClients()` | `Flow<List<ClientProfileEntity>>` | All clients ordered by `companyName ASC`. |
| `getById(id: Long)` | `suspend ClientProfileEntity?` | Single client by primary key. |
| `save(profile: ClientProfileEntity)` | `suspend Long` | Upsert with `REPLACE` conflict. Returns row ID. |
| `update(profile: ClientProfileEntity)` | `suspend Unit` | Standard Room `@Update`. |
| `delete(profile: ClientProfileEntity)` | `suspend Unit` | Standard Room `@Delete`. |

### 6.5 ViewModels

#### `ReportViewModel`
**Package:** `com.wificracker.report.ui`
**Annotation:** `@HiltViewModel`

The primary ViewModel for the multi-step report wizard.

#### `ReportUiState`

| Field | Type | Default | Description |
|---|---|---|---|
| `companyProfile` | `CompanyProfile` | `CompanyProfile()` | Current auditor company profile (loaded from DB on init) |
| `clientProfile` | `ClientProfile` | `ClientProfile()` | Current client profile |
| `missionTitle` | `String` | `""` | Engagement title |
| `missionScope` | `String` | `""` | Scope definition |
| `findings` | `List<Finding>` | `emptyList()` | Findings for the report |
| `generatedReport` | `Report?` | `null` | The assembled `Report` object after `generateReport()` |
| `exportedPath` | `String` | `""` | Absolute path of the last exported file |
| `isGenerating` | `Boolean` | `false` | `true` while report assembly is running |
| `step` | `ReportStep` | `MISSION_INFO` | Current wizard step |
| `savedClients` | `List<ClientProfileEntity>` | `emptyList()` | All clients from the database (reactive) |
| `selectedClientId` | `Long` | `0` | ID of the client selected from `savedClients` |
| `sessionStats` | `SessionStats` | `SessionStats()` | Populated by `collectSessionResults()` |
| `sessionCollected` | `Boolean` | `false` | `true` after `collectSessionResults()` completes |

**StateFlow:**

| Property | Type |
|---|---|
| `uiState` | `StateFlow<ReportUiState>` |

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `updateCompanyProfile` | `fun updateCompanyProfile(profile: CompanyProfile)` | |
| `updateClientProfile` | `fun updateClientProfile(profile: ClientProfile)` | |
| `updateMission` | `fun updateMission(title: String, scope: String)` | |
| `addFinding` | `fun addFinding(finding: Finding)` | Appends a finding to the list. |
| `removeFinding` | `fun removeFinding(id: String)` | Removes a finding by ID. |
| `nextStep` | `fun nextStep()` | Advances the wizard step. |
| `prevStep` | `fun prevStep()` | Retreats the wizard step. |
| `selectClient` | `fun selectClient(client: ClientProfileEntity)` | Loads a saved client entity into `clientProfile`. |
| `collectSessionResults` | `fun collectSessionResults()` | Aggregates all session data from `ScanEngine`, `SessionCollector` attack records, and crack records into findings. Deduplicates by `title:bssid`. Updates `sessionStats`. |
| `generateReport` | `fun generateReport()` | Calls `ReportGenerator.generateReport()` and transitions to `PREVIEW` step. |
| `exportReport` | `fun exportReport(format: ExportFormat)` | Calls `ExportManager.export()` and transitions to `EXPORT` step. |

#### `ClientEditViewModel`
**Package:** `com.wificracker.report.ui`
**Annotation:** `@HiltViewModel`

#### `ClientEditUiState`

| Field | Type | Description |
|---|---|---|
| `companyName` / `address` / `contactName` / `contactTitle` / `contactEmail` / `contractRef` / `logoPath` | `String` | Editable form fields |
| `saved` | `Boolean` | Becomes `true` after successful `save()` |
| `existingId` | `Long` | `0` for new clients; non-zero for edit |

**StateFlow:** `uiState: StateFlow<ClientEditUiState>`

**Methods:** Individual `set*` methods for each field + `loadClient(id: Long)` + `save()`.

#### `ClientListViewModel`
**Package:** `com.wificracker.report.ui`
**Annotation:** `@HiltViewModel`

| Property/Method | Type/Signature | Description |
|---|---|---|
| `clients` | `Flow<List<ClientProfileEntity>>` | All saved clients from `ClientProfileDao`. Not wrapped in StateFlow — callers use `collectAsState()`. |
| `delete` | `fun delete(client: ClientProfileEntity)` | Deletes the client record. |

#### `CompanyProfileViewModel`
**Package:** `com.wificracker.report.ui`
**Annotation:** `@HiltViewModel`

#### `CompanyProfileUiState`

| Field | Type | Description |
|---|---|---|
| `name` / `address` / `siret` / `contactName` / `contactEmail` / `contactPhone` / `certifications` / `legalMention` / `logoPath` | `String` | Editable form fields. Note: `certifications` is a raw comma-separated string in the UI state (unlike `CompanyProfile.certifications: List<String>`). |
| `saved` | `Boolean` | Becomes `true` after `save()`, reset by `resetSaved()`. |
| `existingId` | `Long` | DB row ID of the currently loaded profile. |

**StateFlow:** `uiState: StateFlow<CompanyProfileUiState>`

**Methods:** Individual `set*` methods + `resetSaved()` + `save()`.

---

## 7. Module: app

Package root: `com.wificracker.app`

### 7.1 ViewModels

#### `MainViewModel`
**Package:** `com.wificracker.app.ui.navigation`
**Annotation:** `@HiltViewModel`

Bootstraps app state: checks root access and disclaimer acceptance.

#### `MainUiState`

| Field | Type | Default | Description |
|---|---|---|---|
| `isLoading` | `Boolean` | `true` | `true` until root check completes |
| `isRooted` | `Boolean` | `false` | Device root status |
| `disclaimerAccepted` | `Boolean` | `false` | Persisted in `SharedPreferences["disclaimer_accepted"]` |

**StateFlow:** `uiState: StateFlow<MainUiState>`

**Methods:**

| Method | Description |
|---|---|
| `acceptDisclaimer()` | Persists acceptance to `SharedPreferences` and updates state. |

#### `AuditLogViewModel`
**Package:** `com.wificracker.app.ui.screens`
**Annotation:** `@HiltViewModel`

**StateFlow:** `entries: StateFlow<List<AuditEntry>>`

**Methods:** `refresh()`, `purge()`, `export()` (writes JSON to audit log path).

#### `SettingsViewModel`
**Package:** `com.wificracker.app.ui.screens`
**Annotation:** `@HiltViewModel`

#### `SettingsUiState`

| Field | Type | Description |
|---|---|---|
| `darkTheme` | `Boolean` | Persisted to `SharedPreferences["dark_theme"]` |
| `currentLocale` | `Locale` | Current app locale |

**StateFlow:** `uiState: StateFlow<SettingsUiState>`

**Methods:** `setDarkTheme(enabled: Boolean)`, `setLocale(locale: Locale)`, `getSupportedLocales(): List<Locale>` → `[Locale.ENGLISH, Locale.FRENCH]`.

#### `VulnDatabaseViewModel`
**Package:** `com.wificracker.app.ui.screens`
**Annotation:** `@HiltViewModel`

| Property | Type | Description |
|---|---|---|
| `vulns` | `Flow<List<VulnEntity>>` | All vulnerabilities from `VulnDao.getAll()`. Sorted by CVSS descending. Not wrapped in StateFlow. |

#### `WordlistViewModel`
**Package:** `com.wificracker.app.ui.screens`
**Annotation:** `@HiltViewModel`

#### `WordlistUiState`

| Field | Type | Default | Description |
|---|---|---|---|
| `installed` | `List<Wordlist>` | `emptyList()` | Currently installed wordlists |
| `available` | `List<WordlistManager.DownloadableWordlist>` | Full catalog | Downloadable wordlists catalog |
| `downloading` | `String` | `""` | ID of the wordlist currently downloading |
| `downloadLog` | `String` | `""` | Progress messages from the active download |
| `isLoading` | `Boolean` | `true` | `true` while `getInstalledWordlists()` is running |

**StateFlow:** `uiState: StateFlow<WordlistUiState>`

**Methods:** `refresh()`, `download(wl: DownloadableWordlist)`, `delete(path: String)`.

#### `ModulesViewModel`
**Package:** `com.wificracker.app.ui.screens`
**Annotation:** `@HiltViewModel`

Manages binary dependency checking and installation.

#### `ModuleInfo`

| Field | Type | Description |
|---|---|---|
| `name` | `String` | Binary name |
| `description` | `String` | Human-readable description |
| `isInstalled` | `Boolean` | Whether the binary is found at `checkPath` |
| `isInstalling` | `Boolean` | `true` during active installation |
| `checkPath` | `String` | Absolute path to check for the binary |
| `packageName` | `String` | Termux package name |
| `downloadUrl` | `String` | Alternative download URL |

#### `ModulesUiState`

| Field | Type | Default | Description |
|---|---|---|---|
| `modules` | `List<ModuleInfo>` | `emptyList()` | Status of all 11 tracked binaries |
| `isChecking` | `Boolean` | `true` | `true` during status scan |
| `installLog` | `String` | `""` | Accumulated installation log |

**StateFlow:** `uiState: StateFlow<ModulesUiState>`

**Methods:** `checkModules()`, `installFromTermux(moduleName: String)`, `installAllMissing()`.

**Tracked binaries:** `aircrack-ng`, `airodump-ng`, `aireplay-ng`, `hcxdumptool`, `hcxpcapngtool`, `hashcat`, `hostapd`, `dnsmasq`, `iw`, `wpa_driver`, `ics_enable`.

**Installation strategy (in order):** copy from Termux → copy from Kali NetHunter → download static ARM64 binary → copy from system `$PATH`.

### 7.2 Dependency Injection

#### `AppModule`
**Package:** `com.wificracker.app.di`
**Scope:** `SingletonComponent`

| Provides | Type | Description |
|---|---|---|
| `provideSharedPreferences` | `SharedPreferences` | `"wificracker"` private preferences file |
| `provideOuiMap` | `Map<String, String>` | Loaded from `assets/oui.tsv` (tab-separated: `PREFIX\tVENDOR_NAME`). Used by `MacVendorLookup`. |

#### `CoreModule`
**Package:** `com.wificracker.core.di`
**Scope:** `SingletonComponent`

| Provides | Type | Description |
|---|---|---|
| `provideAuditLogger` | `AuditLogger` | Instantiated with `{filesDir}/audit_logs` as the log directory. |

---

## 8. Cross-Module StateFlow Contracts

The following `StateFlow` instances are injected across module boundaries and serve as the primary data contracts.

| StateFlow | Owner | Consumers | Description |
|---|---|---|---|
| `ScanEngine.scanState` | `scan` module | `ScanViewModel`, `ReportViewModel` | Current scan state including all discovered networks |
| `AttackOrchestrator.attackState` | `attack` module | `AttackViewModel` | Current attack instance and status |
| `AttackOrchestrator.consoleOutput` | `attack` module | `AttackViewModel` | Live console output lines |
| `CrackOrchestrator.progress` | `crack` module | `CrackViewModel` | Real-time crack progress |
| `CrackOrchestrator.result` | `crack` module | `CrackViewModel` | Final crack result |
| `SelectedNetworkRepository.selectedNetwork` | `core` module | `ScanViewModel` (write), `AttackViewModel` (read), `CrackViewModel` (read) | Shared target network reference |
| `SessionCollector.sessionData` | `core` module | `ReportViewModel` (read), `ScanViewModel` (write), `AttackOrchestrator` (write), `CrackOrchestrator` (write) | Accumulated session activity |
| `ProgressBroadcaster.currentProgress` | `core` module | `PentestForegroundService` | Job progress for foreground service notification |

---

## 9. File System Conventions

All operational files are written under `/data/local/tmp/wificracker/` (requires root).

| Path | Content |
|---|---|
| `/data/local/tmp/wificracker/` | Binary installation directory (`BinaryInstaller.INSTALL_DIR`) |
| `/data/local/tmp/wificracker/lib/` | Shared library dependencies for installed binaries |
| `/data/local/tmp/wificracker/scans/` | airodump-ng CSV output files (`WifiCommandRunner`) |
| `/data/local/tmp/wificracker/captures/` | Handshake `.cap`, PMKID `.pcapng`, and ICS `.ics` capture files |
| `/data/local/tmp/wificracker/exports/` | PCAP captures and scan result JSON files (`PcapExporter`) |
| `/data/local/tmp/wificracker/hashes/` | Converted `.hc22000` hash files (`HashConverter`) |
| `/data/local/tmp/wificracker/wordlists/` | Wordlist `.txt` files (`WordlistManager.WORDLIST_DIR`) |
| `/data/local/tmp/wificracker/reports/` | Exported report files (`ExportManager`) |
| `/data/local/tmp/wificracker/rules/` | Hashcat rule files (e.g. `best64.rule`) |
| `/data/local/tmp/wpa_driver` | MediaTek chipset command binary |
| `/data/local/tmp/ics_enable` | MediaTek ICS capture toggle binary |
| `/dev/fw_log_ics` | MediaTek ICS firmware log character device (read-only) |
| `{filesDir}/audit_logs/audit.jsonl` | Audit log (JSON Lines), in app's internal storage |

---

## 10. Binary Dependencies

These ARM64 binaries must be installed in `/data/local/tmp/wificracker/` for full functionality. Sourced from Termux, Kali NetHunter, or direct download.

| Binary | Package | Used by | Purpose |
|---|---|---|---|
| `aircrack-ng` | aircrack-ng | `DictionaryAttack` | Dictionary-based WPA/WEP cracking |
| `airodump-ng` | aircrack-ng | `WifiCommandRunner`, `HandshakeCapture`, `ProbeSniff` | Packet capture and CSV scan |
| `aireplay-ng` | aircrack-ng | `DeauthAttack` | Deauthentication frame injection |
| `hcxdumptool` | hcxdumptool | `PmkidCapture` | Client-less PMKID capture |
| `hcxpcapngtool` | hcxtools | `HashConverter` | Convert .cap/.pcapng to .hc22000 |
| `hashcat` | hashcat | `BruteForceAttack`, `RuleBasedAttack`, `CombinatorAttack` | GPU/CPU-accelerated hash cracking |
| `hostapd` | hostapd | `EvilTwinAttack` | Rogue access point creation |
| `dnsmasq` | dnsmasq | `EvilTwinAttack` | DHCP server for rogue AP |
| `iw` | iw | `ChannelHopper`, `MonitorModeManager` | Wireless interface configuration |
| `wpa_driver` | (MTK-specific) | `DeauthAttack`, `HandshakeCapture`, `ChipsetMonitorHelper` | MediaTek driver command interface |
| `ics_enable` | (MTK-specific) | `MtkMonitorCapture`, `ChipsetMonitorHelper` | MediaTek ICS capture enable/disable |
