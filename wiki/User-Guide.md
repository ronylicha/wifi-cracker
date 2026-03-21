# User Guide

This guide walks through a complete penetration testing workflow from network discovery to report generation. Each section corresponds to one module of the application.

---

## Overview of the Workflow

```
1. Scan     — Discover networks, identify targets, match CVEs
2. Attack   — Capture handshake or PMKID from the target
3. Crack    — Recover the password from the captured data
4. Report   — Generate a professional audit report
```

Each step feeds into the next. Captured files from the Attack module are used directly by the Crack module. Findings from all modules are aggregated into the Report.

---

## Step 1 — Scanning

### Starting a Scan

1. Open the **Scan** tab (bottom navigation bar).
2. Select the WiFi interface to use. The default is `wlan0` (internal adapter). If an external USB adapter is connected, select its interface (typically `wlan1`).
3. Tap **Start Scan**.

The scan uses two parallel sources:
- **Android system API** — triggers `cmd wifi start-scan` every 8 seconds and parses scan results. Works on all devices without special requirements.
- **ICS passive capture** (MediaTek only) — if the MTK patch is installed and detected, the engine simultaneously reads raw 802.11 beacon and probe response frames from `/dev/fw_log_ics`. This allows passive discovery without injecting any scan requests.

### Reading Scan Results

Each discovered network card shows:

| Field | Description |
|-------|-------------|
| SSID | Network name |
| BSSID | Access point MAC address |
| Channel | Operating channel (2.4 GHz or 5 GHz) |
| Signal | RSSI in dBm |
| Encryption | Open / WEP / WPA / WPA2 / WPA3 |
| WPS | Whether Wi-Fi Protected Setup is advertised |
| Clients | MAC addresses of connected devices (if detected) |
| CVEs | Number of matching vulnerabilities from the database |

Networks are sorted by signal strength. Tap a network card to expand the full details, including the list of matched CVE entries with CVSS scores.

### Vulnerability Matching

The scan engine automatically queries a built-in database of 76+ CVE entries. Matching is based on the encryption protocol reported by the network:

- Open networks receive a fixed CRITICAL rating (CVSS 10.0) in addition to any CVEs.
- Networks with WPS enabled receive an additional HIGH-severity flag (CVSS 7.0) warning about PIN brute-force vulnerability.
- Entries are sorted by CVSS score, highest first.

### Stopping the Scan

Tap **Stop Scan**. The results remain visible for use in the Attack and Report modules. The scan duration and total network count are recorded in the audit log.

---

## Step 2 — Selecting a Target and Launching an Attack

### Navigating to the Attack Module

1. From the Scan results, tap a network and then tap **Attack this network**, or open the **Attack** tab directly.
2. In the Attack screen, use the **target selector** to pick the BSSID and SSID of the target network.

### Available Attack Types

WiFi Cracker provides five attack types. Select the one appropriate for your test objective.

#### Deauthentication

Sends deauthentication frames to force client disconnection. Typically used to make a client reconnect, which triggers a new WPA handshake capture.

**Method selection is automatic based on the detected chipset:**
- On MediaTek devices with the patched driver: uses `AP_STA_DISASSOC` via the internal MTK firmware TX path (no external adapter required).
- On all other devices: uses `aireplay-ng --deauth` (requires an external adapter in monitor mode).

**Configuration:**
- Target BSSID
- Optionally, a specific client MAC to deauth (leave blank to deauth all clients)
- Number of deauth frames (default: continuous until stopped)

#### WPA/WPA2 Handshake Capture

Runs `airodump-ng` on the target channel and waits for a 4-way EAPOL handshake. Often used together with a simultaneous deauth attack to force a reconnect.

**Output:** A `.cap` file saved to `/data/local/tmp/wificracker/captures/`. This file can be sent directly to the Crack module.

#### PMKID Capture

Runs `hcxdumptool` to capture the PMKID from the first EAPOL frame. Unlike handshake capture, no connected clients are required — the attack works against the access point directly.

**Output:** A `.pcapng` file that is automatically converted to `.hc22000` format by the Crack module.

#### Evil Twin

Creates a rogue access point that clones the target network's SSID. Uses `hostapd` for the access point and `dnsmasq` for DHCP. Clients that connect see a captive portal.

**Configuration:**
- SSID to clone (pre-filled from scan results)
- Interface for the rogue AP (requires a second wireless interface or a USB adapter)

**Note:** Running a simultaneous deauthentication attack on the real AP is a common technique to push clients toward the Evil Twin.

#### Probe Request Sniffing

Passively listens for 802.11 probe request frames, which reveal the SSIDs of networks a device has previously connected to. Useful for target reconnaissance.

**Output:** A list of SSID names and source MAC addresses displayed in the live console.

### Reading the Live Console

All attacks stream real-time output to a color-coded console:

| Prefix | Color | Meaning |
|--------|-------|---------|
| `[*]` | White | Informational status |
| `[+]` | Green | Success or positive event |
| `[-]` | Red | Error or failure |
| `[!]` | Yellow | Warning |

The console auto-scrolls. Use the copy button (top-right of the console) to export the full log.

### Stopping an Attack

Tap **Stop**. The underlying processes (`aireplay-ng`, `airodump-ng`, `hcxdumptool`, `hostapd`, `dnsmasq`) are terminated cleanly. Capture files already written to disk are preserved.

---

## Step 3 — Cracking

### Opening the Crack Module

1. Go to the **Crack** tab.
2. Tap **Load capture file** and select the `.cap` or `.pcapng` file produced during the Attack step. If a `.cap` file is loaded, it is automatically converted to `.hc22000` using `hcxpcapngtool` before cracking begins.

### Choosing a Strategy

| Strategy | Tool | Best For |
|----------|------|----------|
| Dictionary | aircrack-ng | Common passwords, known patterns |
| Brute Force | hashcat | Short passwords, when character set is known |
| Rule Based | hashcat | Dictionary augmented with mutation rules |
| Combinator | hashcat | Two wordlists combined |

#### Dictionary Attack

Tests every password in a wordlist file against the captured hash.

**Configuration:**
- Wordlist file path (e.g., `/data/local/tmp/rockyou.txt`)
- Target BSSID and SSID (pre-filled if coming from the Attack module)

#### Brute Force Attack

Tries all combinations of a character set up to a specified maximum length.

**Configuration:**
- Character set (lowercase, uppercase, digits, special characters, or custom)
- Minimum and maximum password length

**Warning:** Brute force on long passwords is computationally expensive. Estimated time is displayed before the attack starts.

#### Rule-Based Attack

Applies hashcat mutation rules (e.g., `best64.rule`) to a wordlist. Generates permutations such as capitalizing the first letter, appending digits, or reversing the string.

**Configuration:**
- Wordlist file
- Rule file (bundled rules available: `best64.rule`, `toggles.rule`, `leetspeak.rule`)

#### Combinator Attack

Combines every word from wordlist A with every word from wordlist B. Useful for passwords constructed from two common words.

**Configuration:**
- Wordlist A
- Wordlist B

### Monitoring Progress

The progress screen shows:

| Metric | Description |
|--------|-------------|
| Status | CONVERTING / RUNNING / PAUSED / COMPLETED / FAILED |
| Keys tested | Number of passwords tried so far |
| Speed | Keys per second |
| ETA | Estimated time to completion (when calculable) |
| Current key | The last password tested (dictionary/rule-based) |

### Pause and Resume

Tap **Pause** to suspend the crack job. The current position is saved. Tap **Resume** to continue from where it stopped.

### When a Password is Found

If the password is recovered, the result screen displays:
- The recovered password in plain text
- Total keys tested
- Total time elapsed
- BSSID and SSID of the target

The result is recorded in the audit log and in the session data used by the Report module.

---

## Step 4 — Generating a Report

### Setting Up Company and Client Profiles

Before generating your first report, configure the company and client profiles under **Drawer > Company Profile** and **Drawer > Clients**.

**Company Profile:**
- Company name and logo
- Contact details
- Default language for reports (English or French)

**Client Profile:**
- Client name and organization
- Contact person
- Notes

Profiles are stored locally in the Room database and persist across sessions.

### Opening the Report Module

Go to the **Report** tab.

### Configuring the Report

1. **Select client** — choose from the saved client profiles.
2. **Mission info** — fill in the assessment date, scope description, and tester name.
3. **Findings** — the module aggregates findings automatically from the current session: scanned networks, vulnerability matches, attack results, and passwords recovered.
4. **Manual recommendations** — optionally add custom remediation notes.

### Security Grading

The report engine computes:

- A **CVSS v3.1 score** for each finding using the `CvssCalculator`.
- An **overall security grade** (A through F) based on the highest CVSS score identified:

| Grade | Maximum CVSS Score |
|-------|--------------------|
| A | 0.0 – 1.0 |
| B | 1.1 – 3.0 |
| C | 3.1 – 5.0 |
| D | 5.1 – 7.0 |
| F | 7.1 – 10.0 |

### Auto-Generated Recommendations

The `AutoRecommender` analyzes findings and generates targeted remediation steps. For example:
- Networks using WEP or WPA receive an upgrade recommendation.
- Open networks receive an immediate encryption enforcement recommendation.
- WPS-enabled networks receive a disable-WPS recommendation.
- Successfully recovered passwords receive a complexity improvement recommendation.

Manual recommendations are merged with auto-generated ones, deduplicated by title, and sorted by priority.

### Exporting the Report

Select the output format and tap **Generate Report**:

| Format | File Extension | Use Case |
|--------|---------------|----------|
| PDF | `.pdf` | Formal delivery to clients |
| HTML | `.html` | Web-based review, easy sharing |
| JSON | `.json` | Programmatic integration, archival |

The exported file is saved to the device's Documents folder and can be shared via the Android share sheet.

---

## Audit Log

Every action performed by WiFi Cracker is recorded in a structured JSON Lines log file (`audit.jsonl`). This includes:

- Scan start/stop events
- Attack start/stop events with target BSSID
- Crack start/stop events with strategy and result (FOUND / NOT_FOUND)
- Errors

To view or export the log: **Drawer > Audit Log**

The log viewer displays entries in chronological order. Use the **Export** button to save the log as a JSON file. Use **Purge** to clear all entries.

See [Security and Legal](Security-and-Legal) for data retention and GDPR considerations.

---

## Vulnerability Database

The built-in CVE database can be browsed independently of a scan session.

Navigate to: **Drawer > Vulnerability Database**

The browser supports:
- Full-text search across CVE ID, title, and description
- Filtering by severity (Critical, High, Medium, Low)
- Filtering by protocol (WPA, WPA2, WEP, WPA3, Open)

Each entry shows the CVE ID, CVSS score, affected versions, and recommended remediation.
