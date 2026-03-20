# WiFi Cracker

Professional WiFi security auditing application for Android. Designed for authorized penetration testing.

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

### Report Module
- Professional PDF/HTML/JSON report generation
- CVSS v3.1 scoring with automatic severity classification
- Executive summary with security grade (A-F)
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

MIT License - see [LICENSE](LICENSE) for details.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
