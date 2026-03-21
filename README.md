# WiFi Cracker

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Release](https://img.shields.io/badge/Release-v1.0.0-green.svg)](https://github.com/ronylicha/wifi-cracker/releases/tag/v1.0.0)
[![Android](https://img.shields.io/badge/Android-12%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)

Professional WiFi security auditing toolkit for Android, designed for penetration testers and security professionals conducting authorized wireless assessments.

**[Getting Started](wiki/Getting-Started.md)** | **[User Guide](wiki/User-Guide.md)** | **[Architecture](docs/ARCHITECTURE.md)** | **[API Reference](docs/API_REFERENCE.md)** | **[Changelog](CHANGELOG.md)**

## Features

### Scan Module
- Real-time WiFi network discovery via monitor mode
- Encryption type detection (Open, WEP, WPA, WPA2, WPA3)
- Client device identification with MAC vendor lookup
- Automatic vulnerability matching from built-in CVE database (76+ entries)
- Signal strength analysis and channel congestion detection

### Attack Module
- Deauthentication attack (aireplay-ng)
- WPA/WPA2 4-way handshake capture (airodump-ng)
- PMKID capture without connected clients (hcxdumptool)
- Evil Twin access point (hostapd + dnsmasq)
- Probe request sniffing
- Real-time console output with color-coded logs

### Crack Module
- Dictionary attack (aircrack-ng)
- Brute-force attack (hashcat)
- Rule-based mutations (hashcat rules)
- Combinator attack (two wordlists)
- Automatic .cap to .hc22000 hash conversion
- Pause/resume crack sessions
- Real-time progress with speed and ETA

### Cross-Tab Workflow
- Select a network on scan page → auto-fills attack and crack targets
- Visual target indicator across all tabs (GPS icon + primary border)
- Network context maintained when switching between modules
- "Launch Attack" from network detail navigates directly to attack tab

### Report Module
- **One-click session collection** — aggregates scan, attack, and crack results automatically
- Professional PDF/HTML/JSON report generation
- CVSS v3.1 scoring with automatic severity classification
- Executive summary with security grade (A-F) and full session activity stats
- Auto-generated recommendations based on findings
- Company and client profile management with logo support
- Bilingual reports (English/French)

## Screenshots

_Coming soon_

## Requirements

- Android 12+ (API 31)
- **Rooted device** (Magisk, KernelSU, or SuperSU)
- External WiFi adapter with monitor mode support (recommended: Alfa AWUS036AXML)
- USB OTG adapter

### Recommended Hardware

| Adapter | Chipset | WiFi | Price |
|---------|---------|------|-------|
| Alfa AWUS036AXML | MT7921AU | WiFi 6E | ~70EUR |
| Alfa AWUS036ACM | MT7612U | WiFi 5 | ~40EUR |
| Alfa AWUS036ACH | RTL8812AU | WiFi 5 | ~35EUR |

> **Note:** Most internal WiFi chipsets do NOT support monitor mode out of the box. See the chipset compatibility table below.

### Internal WiFi Chipset Compatibility

WiFi Cracker **auto-detects** your chipset and uses the appropriate method:

| Chipset Vendor | Method | Monitor Mode | Notes |
|----------------|--------|:------------:|-------|
| **Qualcomm (Snapdragon)** | QCACLD `con_mode=4` | Yes | Native support via driver parameter |
| **Broadcom (Exynos/Pixel)** | Nexmon firmware patch | Yes | Requires Nexmon installed |
| **MediaTek (Dimensity)** | ICS capture (patched driver) | Yes* | Requires our custom Magisk module |

*\* MediaTek support requires installing the `mtk_wifi_monitor` Magisk module. See [MediaTek Monitor Mode Guide](docs/MTK_MONITOR_MODE_GUIDE.md).*

### MediaTek Patched Driver (Unihertz Titan 2 / MT6878)

We developed a **custom kernel driver patch** (v4) that enables WiFi monitor mode on MediaTek Dimensity 7300 (MT6878) devices. The patch modifies two kernel modules via a Magisk module:

| Module | Patches | Size | Purpose |
|--------|---------|------|---------|
| `wlan_drv_gen4m_6878.ko` (7 MB) | 4 ARM64 trampolines | 152 bytes | ICS sniffer enable, promiscuous RX, deauth TX |
| `cfg80211.ko` (2 MB) | 3 NOP patches | 12 bytes | Allow raw 802.11 management frame TX in STA mode |

**How it works:** The firmware command `SNIFFER 2 0 0 0 0 0 0 0 0 0` enables ICS (Internal Capture Service) logging. Raw 802.11 frames (with MTK RX descriptors) are read from `/dev/fw_log_ics`. Deauthentication is sent via `AP_STA_DISASSOC` with the role check patched out.

**Quick install:**
```bash
# Install Magisk module (includes patched .ko + wpa_driver + ics_enable)
adb push firmware-dump/mtk_wifi_monitor_magisk.zip /data/local/tmp/
adb shell "su -c 'magisk --install-module /data/local/tmp/mtk_wifi_monitor_magisk.zip'"
adb reboot
```

The app auto-detects the patch version by SHA256 hash of the loaded kernel modules and adapts its behavior accordingly (v0/v3/v4).

**Documentation:**
- [MTK Monitor Mode Guide](docs/MTK_MONITOR_MODE_GUIDE.md) — Installation, patch architecture, firmware commands
- [Firmware Analysis](docs/FIRMWARE_ANALYSIS.md) — Complete reverse engineering of MT6631 WiFi co-processor (NDS32)

**Tested on:** Unihertz Titan 2 (MT6878, Android 16, WiFi co-processor MT6631) — 981 packets captured in 5 seconds.

## Pentest Tools (Modules)

### Bundled Binaries (Auto-installed)

The core aircrack-ng suite is **bundled directly in the APK** under `core/src/main/assets/binaries/`. On first launch, the app automatically extracts and installs them to `/data/local/tmp/wificracker/`:

| Binary | Bundled | Purpose |
|--------|:-------:|---------|
| aircrack-ng | Yes | WiFi password cracking |
| airodump-ng | Yes | Network scanning and packet capture |
| aireplay-ng | Yes | Deauthentication and packet injection |
| airmon-ng | Yes | Monitor mode management |

No setup required — these are ready to use immediately after installation.

### Additional Tools (Modules Screen)

Additional tools for advanced attacks can be installed via **Drawer > Modules**. The Modules screen shows the status of all 11 tracked binaries and provides multiple installation methods (tried in this order):

1. **Termux** — copies from `/data/data/com.termux/files/usr/bin/`
2. **Kali Nethunter** — copies from `/data/local/nhsystem/kali-arm64/usr/bin/`
3. **Download** — fetches pre-compiled ARM64 binaries from community repositories
4. **System PATH** — detects already-installed binaries

| Binary | Source | Purpose |
|--------|--------|---------|
| hcxdumptool | Termux/download | PMKID capture without connected clients |
| hcxpcapngtool | Termux/download | Convert .cap to .hc22000 hash format |
| hashcat | Termux/download | Advanced password recovery (GPU-accelerated) |
| hostapd | Termux/download | Rogue access point (Evil Twin) |
| dnsmasq | Termux/download | DHCP/DNS server for Evil Twin |
| iw | Termux/download | Wireless interface configuration |

**Quick setup via Termux** (installs all missing tools at once):
```bash
pkg update && pkg install -y hcxdumptool hcxtools hashcat hostapd dnsmasq iw
```
Then tap **"Install all missing modules"** in the Modules screen.

### MediaTek-Specific Tools

For devices using the patched MediaTek driver, two additional binaries are required. These are **included in the Magisk module** (`mtk_wifi_monitor_magisk.zip`) and installed automatically at `/data/local/tmp/`:

| Binary | Included in | Purpose |
|--------|-------------|---------|
| wpa_driver | Magisk module | Sends SNIFFER firmware command to the MTK driver |
| ics_enable | Magisk module | Toggles ICS (Internal Capture Service) on `/dev/fw_log_ics` |

If you need to compile them manually from source:
```bash
NDK_CC="$ANDROID_HOME/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android31-clang"
$NDK_CC -static -o wpa_driver firmware-dump/wpa_driver.c
$NDK_CC -static -o ics_enable firmware-dump/ics_enable.c
adb push wpa_driver ics_enable /data/local/tmp/
adb shell "su -c 'chmod 755 /data/local/tmp/wpa_driver /data/local/tmp/ics_enable'"
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Database | Room |
| Native | NDK/C (ARM64 binaries) |
| Binaries | aircrack-ng, hcxtools, hcxdumptool, hostapd, dnsmasq |

## Project Structure

```
wifi-cracker/
├── app/            # Main application (navigation, theme, DI)
├── core/           # Shared: root management, WiFi control, Room DB, logging
├── scan/           # WiFi scanning and network discovery
├── attack/         # Attack implementations (5 attack types)
├── crack/          # Password cracking (4 strategies)
├── report/         # PDF/HTML/JSON report generation
├── firmware-dump/  # MediaTek driver patch files and Magisk module
└── docs/           # Technical documentation (firmware analysis, guides)
```

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (signed)
./gradlew assembleRelease

# Run tests
./gradlew test

# Install on device
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Legal Disclaimer

This application is designed **exclusively for authorized security testing**. Unauthorized access to computer networks is illegal in most jurisdictions.

By using WiFi Cracker, you confirm that:
- You have **written authorization** to test the target networks
- You are conducting tests as part of a **legitimate security audit**
- You accept full responsibility for your actions

The developers assume no liability for misuse of this tool.

## License

This project is licensed under the **GNU General Public License v3.0** - see [LICENSE](LICENSE) for details.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
