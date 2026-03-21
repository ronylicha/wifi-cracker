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

We developed a **custom kernel driver patch** that enables WiFi monitor mode on MediaTek Dimensity 7300 (MT6878) devices. The patch injects 52 bytes of ARM64 trampoline code into the RX path to redirect captured frames to `/dev/fw_log_ics`.

**Quick install:**
```bash
# Install Magisk module
adb push firmware-dump/mtk_wifi_monitor_magisk.zip /data/local/tmp/
adb shell "su -c 'magisk --install-module /data/local/tmp/mtk_wifi_monitor_magisk.zip'"
adb reboot
```

**Documentation:**
- [MTK Monitor Mode Guide](docs/MTK_MONITOR_MODE_GUIDE.md) — Installation, usage, and technical details
- [Firmware Analysis](docs/FIRMWARE_ANALYSIS.md) — Complete reverse engineering analysis

**Tested on:** Unihertz Titan 2 (MT6878, Android 16) — 981 packets captured in 5 seconds.

## Installing Pentest Tools (Modules)

WiFi Cracker requires external ARM64 binaries for scanning, attacking, and cracking. The app includes a **Modules** screen (Drawer > Modules) that checks which tools are installed and installs missing ones automatically.

### Required Tools

| Binary | Package | Purpose |
|--------|---------|---------|
| aircrack-ng | aircrack-ng | WiFi password cracking suite |
| airodump-ng | aircrack-ng | WiFi network scanner and packet capture |
| aireplay-ng | aircrack-ng | Deauthentication and packet injection |
| hcxdumptool | hcxdumptool | PMKID capture without connected clients |
| hcxpcapngtool | hcxtools | Convert .cap to .hc22000 hash format |
| hashcat | hashcat | Advanced password recovery |
| hostapd | hostapd | Rogue access point (Evil Twin) |
| dnsmasq | dnsmasq | DHCP/DNS server for Evil Twin |
| iw | iw | Wireless interface configuration |

### Method 1: Via Termux (Recommended)

The easiest way to get all tools:

1. **Install Termux** from [F-Droid](https://f-droid.org/packages/com.termux/) (NOT from Play Store — the Play Store version is outdated and broken)

2. **Open Termux** and run:
```bash
pkg update && pkg install -y aircrack-ng hcxdumptool hcxtools hashcat hostapd dnsmasq iw
```

3. **Open WiFi Cracker** > Drawer > **Modules** > tap **"Install all missing modules"**

The app will automatically copy the binaries from Termux to its working directory (`/data/local/tmp/wificracker/`).

### Method 2: Via Kali Nethunter

If you have Kali Nethunter installed:

1. The tools are already available in the chroot at `/data/local/nhsystem/kali-arm64/usr/bin/`
2. Open WiFi Cracker > Drawer > **Modules** > tap **"Install all missing modules"**
3. The app will detect and copy from the Nethunter chroot automatically

### Method 3: Automatic Download

If neither Termux nor Nethunter is installed, the Modules screen will attempt to download pre-compiled ARM64 binaries from community repositories via `curl`. This requires an internet connection on the device.

### Method 4: Manual Installation

You can manually push binaries compiled for ARM64 Android:

```bash
# From your PC, push pre-compiled binaries
adb push aircrack-ng /data/local/tmp/wificracker/
adb push airodump-ng /data/local/tmp/wificracker/
# ... etc
adb shell "su -c 'chmod 755 /data/local/tmp/wificracker/*'"
```

### MediaTek-Specific Tools

For devices using the patched MediaTek driver (see above), two additional binaries are needed:

| Binary | Purpose |
|--------|---------|
| wpa_driver | Sends SNIFFER command to the MTK driver |
| ics_enable | Toggles ICS (Internal Capture Service) |

These are compiled from the source in `firmware-dump/` using the Android NDK:

```bash
# Cross-compile from PC
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
