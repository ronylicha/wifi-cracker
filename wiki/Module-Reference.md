# Module Reference

This page provides technical documentation for each of the four functional modules: Scan, Attack, Crack, and Report. It describes features, configuration options, internal behavior, and key classes.

---

## Scan Module

**Gradle module:** `:scan`
**Key classes:** `ScanEngine`, `WifiCommandRunner`, `VulnMatcher`, `IcsPacketParser`, `Ieee80211Parser`

### Architecture

```
ScanDashboard (UI)
  └── ScanViewModel
        └── ScanEngine  ──────────────────────────────────────────┐
              ├── androidScanFlow()                                │
              │     └── ShellExecutor ("cmd wifi start-scan")     │
              └── startIcsCapture() [MTK only]                    │
                    └── MtkMonitorCapture ─── /dev/fw_log_ics     │
                          └── Ieee80211Parser                     │
                                                                  │
              VulnMatcher ◄── VulnDao ◄── vulns.json (assets) ◄──┘
```

### Network Discovery

Two parallel sources run concurrently when a scan is started:

**Android system scan (always active)**

Calls `cmd wifi start-scan` every 8 seconds via root shell, then reads results from `cmd wifi list-scan-results`. Results include BSSID, frequency, RSSI, SSID, and capability flags (`[WPA2-PSK-CCMP][WPS][ESS]`). The parser extracts encryption type, cipher suite, authentication method, channel, and WPS status.

**ICS passive capture (MediaTek with patch only)**

When the MTK patched driver is detected (`ChipsetMonitorHelper.detectChipVendor()` returns `MEDIATEK` with `patchInstalled = true`), the engine additionally starts `MtkMonitorCapture`, which:

1. Calls `wpa_driver "SNIFFER 2 0 0 0 0 0 0 0 0 0"` to issue firmware command `0x93` (SET_ICS_SNIFFER).
2. Calls `ics_enable 1` to open the ICS device via ioctl.
3. Reads raw bytes from `/dev/fw_log_ics` in a continuous coroutine on `Dispatchers.IO`.
4. Each packet is parsed by `IcsPacketParser` (136-byte MTK RX descriptor + raw 802.11 frame) then by `Ieee80211Parser`.
5. Beacon and probe response frames are converted to `Network` objects and merged into the scan state.

The WiFi connection remains active during ICS capture.

### Result Merging

Both sources contribute to a single `List<Network>` state flow. When the same BSSID appears from both sources, the merge logic:
- Keeps the most recent signal strength if it is stronger.
- Keeps the non-UNKNOWN encryption type.
- Updates `lastSeen` timestamp.
- Preserves the most complete SSID.

### Vulnerability Matching

`VulnMatcher.matchVulnerabilities(network)` runs on every completed scan:

1. Maps the network's `EncryptionType` to a protocol string (`WPA2`, `WEP`, etc.).
2. Queries `VulnDao.getByProtocol()` against the bundled `vulns.json` asset (76+ CVE entries).
3. Appends hard-coded findings for Open networks (CVSS 10.0) and WPS-enabled networks (CVSS 7.0).
4. Returns the list sorted by CVSS score descending.

### Data Model

```kotlin
data class Network(
    val bssid: String,
    val ssid: String,
    val channel: Int,
    val frequency: Int,         // MHz
    val signalStrength: Int,    // dBm
    val encryption: EncryptionType,
    val cipher: String,         // "CCMP", "TKIP", "CCMP-256"
    val authentication: String, // "PSK", "SAE", "EAP"
    val wps: Boolean,
    val clients: List<Client>,
    val firstSeen: Long,
    val lastSeen: Long,
)

enum class EncryptionType { OPEN, WEP, WPA, WPA2, WPA3, UNKNOWN }
```

### Settings

| Setting | Location | Default |
|---------|----------|---------|
| WiFi interface | Settings screen | `wlan0` |
| Scan interval | Hardcoded | 8 seconds |
| ICS capture | Auto-enabled | When MTK patch detected |

---

## Attack Module

**Gradle module:** `:attack`
**Key classes:** `AttackOrchestrator`, `DeauthAttack`, `HandshakeCapture`, `PmkidCapture`, `EvilTwinAttack`, `ProbeSniff`

### Attack Types

#### DEAUTH — Deauthentication

Sends IEEE 802.11 deauthentication frames to force client disconnection.

**Chipset-aware behavior:**

| Condition | Method | Requires |
|-----------|--------|----------|
| MTK + patch installed | `AP_STA_DISASSOC` via `wpa_driver` | Only root |
| All others | `aireplay-ng --deauth 0 -a <BSSID> <iface>` | External adapter in monitor mode |

The MTK path sends 50 deauth frames with 100 ms intervals using the internal firmware TX path (`authSendDeauthFrame` → `mboxSendMsg`). The aireplay-ng path sends deauths continuously until stopped.

**Audit log entry:** `ATTACK_START` / `ATTACK_STOP` with target BSSID.

#### HANDSHAKE_CAPTURE — WPA/WPA2 4-Way Handshake

Runs `airodump-ng --bssid <BSSID> -c <channel> -w <output_prefix> <interface>`. Monitors for EAPOL frames that constitute a complete 4-way handshake.

**Typical workflow:**
1. Start handshake capture (this page)
2. Simultaneously start a deauth attack to force a client reconnect
3. Wait for `[WPA handshake: <BSSID>]` in the console output
4. Stop the capture
5. Load the resulting `.cap` file in the Crack module

**Output file:** `/data/local/tmp/wificracker/captures/<BSSID>-01.cap`

#### PMKID_CAPTURE — Clientless PMKID

Runs `hcxdumptool -i <interface> --filterlist_ap=<BSSID> --filtermode=2 -o <output.pcapng>`. The PMKID is extracted from the first EAPOL RSN message from the AP, which does not require an associated client.

**Output file:** `/data/local/tmp/wificracker/captures/<BSSID>.pcapng`

The Crack module automatically converts this to `.hc22000` using `hcxpcapngtool`.

#### EVIL_TWIN — Rogue Access Point

Starts a rogue AP cloning the target SSID using `hostapd` and provides DHCP/DNS via `dnsmasq`. A captive portal intercepts HTTP connections.

**Configuration parameters:**

| Parameter | Description |
|-----------|-------------|
| SSID | Target network name (pre-filled from scan) |
| AP interface | Wireless interface for the rogue AP |
| Channel | Operating channel |
| DHCP range | IP range for connected clients |

**Processes started:** `hostapd`, `dnsmasq`

#### PROBE_SNIFF — Probe Request Sniffing

Passively captures 802.11 probe request frames using `airodump-ng` without writing to a file. Displays the source MAC address and queried SSID name for each probe.

**Output:** Live console only. Results are not persisted automatically — copy the console output if retention is needed.

### Live Console

All attacks stream output to a `LiveConsole` Compose component. Output is color-coded:

```
[*] informational    → white
[+] success          → green
[-] error            → red
[!] warning          → yellow
```

The console displays up to the last 500 lines. A monospace font (Fira Code / system monospace) is used. A copy-all button at the top-right copies the full buffer to the clipboard.

### Stopping Attacks

Stopping an attack sends `SIGTERM` to the relevant process via `pkill -f '<binary>.*<BSSID>'` and cancels the coroutine job. Files already captured are preserved.

---

## Crack Module

**Gradle module:** `:crack`
**Key classes:** `CrackOrchestrator`, `HashConverter`, `DictionaryAttack`, `BruteForceAttack`, `RuleBasedAttack`, `CombinatorAttack`

### Hash Conversion

Before cracking, `.cap` files must be converted to hashcat-compatible `.hc22000` format. `HashConverter.convertCapToHc22000()` runs:

```bash
hcxpcapngtool -o <output.hc22000> <input.cap>
```

If the input is already a `.hc22000` file, conversion is skipped. If the conversion fails (no valid handshake or PMKID in the file), the crack job reports `FAILED` with a descriptive error.

### Strategies

#### DICTIONARY — aircrack-ng

```bash
aircrack-ng -w <wordlist> -b <BSSID> <input.hc22000>
```

Progress is parsed from aircrack-ng's stdout: `[Current passphrase: <word>]`, `<N> keys tested (<speed>/s)`.

#### BRUTE_FORCE — hashcat mask attack (mode -a 3)

```bash
hashcat -m 22000 -a 3 <input.hc22000> <mask>
```

Where `<mask>` is constructed from the configured character set and length range, e.g., `?d?d?d?d?d?d?d?d` for 8-digit numeric passwords.

| Mask token | Character set |
|-----------|---------------|
| `?l` | Lowercase a-z |
| `?u` | Uppercase A-Z |
| `?d` | Digits 0-9 |
| `?s` | Special characters |
| `?a` | All printable ASCII |

#### RULE_BASED — hashcat wordlist + rules (mode -a 0 with -r)

```bash
hashcat -m 22000 -a 0 -r <rules_file> <input.hc22000> <wordlist>
```

Bundled rule files:
- `best64.rule` — 64 high-value transformations
- `toggles.rule` — case permutations
- `leetspeak.rule` — character substitutions (e → 3, a → @, etc.)

#### COMBINATOR — hashcat two-wordlist attack (mode -a 1)

```bash
hashcat -m 22000 -a 1 <input.hc22000> <wordlist1> <wordlist2>
```

Each word from `wordlist1` is concatenated with each word from `wordlist2`.

### Progress Tracking

`CrackOrchestrator` emits a `CrackProgress` state flow consumed by `CrackViewModel`:

```kotlin
data class CrackProgress(
    val jobId: String = "",
    val status: CrackStatus,   // CONVERTING, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED
    val keysTested: Long = 0,
    val keysPerSecond: Long = 0,
    val etaSeconds: Long = -1,
    val currentKey: String = "",
    val message: String = "",
)
```

The `ProgressGauge` component renders a circular gauge showing keys-tested vs estimated total, plus numeric readouts for speed and ETA.

### Session Recording

When a crack completes, `SessionCollector.recordCrack()` saves the result (BSSID, SSID, strategy, success flag, recovered password if any, keys tested, duration) to the session store. This data is later retrieved by the Report module's `DataAggregator`.

---

## Report Module

**Gradle module:** `:report`
**Key classes:** `ReportGenerator`, `ExportManager`, `CvssCalculator`, `AutoRecommender`, `DataAggregator`, `RiskRating`

### Report Structure

A generated `Report` object contains:

```kotlin
data class Report(
    val missionInfo: MissionInfo,         // Date, scope, tester name
    val companyProfile: CompanyProfile,   // Auditing firm details and logo
    val findings: List<Finding>,          // Sorted by CVSS score descending
    val recommendations: List<Recommendation>,
    val overallScore: String,             // "A" through "F"
    val executiveSummary: String,
    val status: ReportStatus,
)
```

### CVSS Scoring

`CvssCalculator` maps numeric CVSS scores to severity labels and grades:

| CVSS Range | Severity |
|-----------|----------|
| 9.0 – 10.0 | Critical |
| 7.0 – 8.9 | High |
| 4.0 – 6.9 | Medium |
| 0.1 – 3.9 | Low |
| 0.0 | Info |

The overall score is the **maximum** CVSS score across all findings. The security grade maps the average CVSS score using the following scale:

| Average CVSS | Grade |
|-------------|-------|
| 0.0 – 1.0 | A |
| 1.1 – 3.0 | B |
| 3.1 – 5.0 | C |
| 5.1 – 7.0 | D |
| 7.1 – 10.0 | F |

### Executive Summary

`ReportGenerator.buildExecutiveSummary()` produces a structured text block including:

- Assessment date and scope
- Activity summary (networks scanned, attacks performed, crack attempts)
- Overall security grade and average CVSS score
- Finding counts by severity
- Warning if any passwords were recovered during the session

### Auto-Generated Recommendations

`AutoRecommender.generateRecommendations(findings)` inspects each finding and produces targeted, actionable remediation steps. Examples:

| Finding Condition | Generated Recommendation |
|------------------|--------------------------|
| Open network | Enable WPA3-SAE encryption immediately |
| WEP encryption | Migrate to WPA2/WPA3; WEP is cryptographically broken |
| WPA (TKIP) | Upgrade to WPA2/WPA3; TKIP is deprecated |
| WPS enabled | Disable WPS to prevent PIN brute-force attacks |
| Password recovered | Increase password complexity; use 12+ characters with mixed character sets |

Recommendations are deduplicated by title and sorted by priority (Critical issues first).

### Export Formats

`ExportManager` handles serialization and file writing:

| Format | Implementation | Output Location |
|--------|---------------|-----------------|
| PDF | Android `PdfDocument` API | `Documents/wificracker/<date>-<client>.pdf` |
| HTML | Templated string builder with embedded CSS | `Documents/wificracker/<date>-<client>.html` |
| JSON | `kotlinx.serialization` | `Documents/wificracker/<date>-<client>.json` |

### Bilingual Support

Report text is rendered in the language selected in Settings. String resources are provided in:
- `res/values/strings.xml` (English, default)
- `res/values-fr/strings.xml` (French)

The language selection applies to report body text, severity labels, section headings, and auto-generated recommendation text.

### Company and Client Profiles

Profiles are stored in a Room database (`ReportDatabase`) with two DAOs:

- `CompanyProfileDao` — single company profile (auditing firm)
- `ClientProfileDao` — multiple client profiles

Each profile supports a logo (stored as a file path). The company logo is embedded in the PDF report header.

---

## Core Module

**Gradle module:** `:core`

Shared services used by all other modules:

| Class | Responsibility |
|-------|---------------|
| `ShellExecutor` | Execute shell commands as root, return `ShellResult(exitCode, stdout, stderr)` |
| `RootChecker` | Detect root access (Magisk / KernelSU / SuperSU) |
| `AuditLogger` | Append-only JSONL audit log with mutex-protected writes |
| `BinaryInstaller` | Locate and install ARM64 binaries from Termux or NetHunter |
| `PentestForegroundService` | Android foreground service keeping the process alive during long operations |
| `ChipsetMonitorHelper` | Detect WiFi chipset vendor and enable/disable monitor mode accordingly |
| `MonitorModeManager` | High-level `@Singleton` wrapping `ChipsetMonitorHelper` |
| `MtkMonitorCapture` | Read and parse raw frames from `/dev/fw_log_ics` |
| `IcsPacketParser` | Parse the 136-byte MTK RX descriptor + 802.11 frame |
| `Ieee80211Parser` | Parse IEEE 802.11 frame fields (FC, addresses, SSID IE, RSSI) |
| `MacVendorLookup` | Resolve OUI prefix to vendor name |
| `SessionCollector` | Accumulate session data (networks, attacks, cracks) for report aggregation |
