# WiFi Cracker — Wiki

WiFi Cracker is a professional WiFi security auditing application for Android. It is designed exclusively for authorized penetration testing and legitimate security assessments.

---

## Quick Links

| Page | Description |
|------|-------------|
| [Getting Started](Getting-Started) | Prerequisites, build instructions, first launch, device setup |
| [User Guide](User-Guide) | Full workflow: scan, attack, crack, report |
| [Supported Devices](Supported-Devices) | Chipset compatibility, adapter recommendations, tested hardware |
| [Module Reference](Module-Reference) | Detailed documentation for each module |
| [Security and Legal](Security-and-Legal) | Legal disclaimer, responsible use, audit logging, GDPR |
| [FAQ](FAQ) | Common questions and troubleshooting |

---

## Project Status

| Area | Status |
|------|--------|
| Scan module | Stable |
| Attack module (5 types) | Stable |
| Crack module (4 strategies) | Stable |
| Report module (PDF/HTML/JSON) | Stable |
| MediaTek MT6878 monitor mode | Stable (v4 patch) |
| Qualcomm QCACLD monitor mode | Stable |
| Broadcom / Nexmon monitor mode | Stable |
| Wordlist manager with download | In progress |
| USB WiFi adapter auto-detection | In progress |
| Channel hopping during scan | Planned |
| F-Droid / Play Store distribution | Planned |

---

## What WiFi Cracker Does

WiFi Cracker integrates four modules into a single Android application:

**Scan** — Discovers nearby WiFi networks in real time using the Android system API and, where available, passive 802.11 frame capture via a patched kernel driver. It detects encryption type, connected clients, WPS status, and automatically matches discovered networks against a built-in CVE database of 76+ entries.

**Attack** — Executes five attack techniques: deauthentication, WPA/WPA2 handshake capture, PMKID capture without connected clients, Evil Twin rogue access point, and probe request sniffing. All attacks run with real-time colored console output.

**Crack** — Recovers passwords from captured handshake or PMKID files using four strategies: dictionary, brute-force, rule-based mutation, and combinator. Includes automatic `.cap` to `.hc22000` conversion and supports pause/resume of crack sessions.

**Report** — Generates professional audit reports in PDF, HTML, or JSON format with CVSS v3.1 scoring, executive summary, security grade (A–F), and auto-generated remediation recommendations. Supports bilingual output (English/French) and custom company/client profiles.

---

## Architecture

```
wifi-cracker/
├── app/            Main application — navigation, theme, Hilt DI wiring
├── core/           Shared services — root management, WiFi control, Room DB, logging
├── scan/           WiFi scanning and network discovery
├── attack/         Attack implementations (5 types)
├── crack/          Password cracking (4 strategies)
├── report/         PDF/HTML/JSON report generation
├── firmware-dump/  MediaTek driver patch files and Magisk module
└── docs/           Technical documentation
```

**Tech stack:** Kotlin, Jetpack Compose + Material 3, MVVM + Clean Architecture, Hilt, Room, NDK/C ARM64 binaries.

---

## Requirements at a Glance

- Android 12 or later (API 31+)
- Rooted device — Magisk, KernelSU, or SuperSU
- External WiFi adapter with monitor mode support for most attack types (recommended: Alfa AWUS036AXML)
- USB OTG adapter

For MediaTek devices, an additional Magisk module is required. See [Getting Started](Getting-Started) for details.

---

## Legal Notice

This application is designed **exclusively for authorized security testing**. Unauthorized access to computer networks is illegal in most jurisdictions. By using WiFi Cracker, you confirm that you hold written authorization to test the target networks and that you are conducting tests as part of a legitimate security audit.

See [Security and Legal](Security-and-Legal) for the full disclaimer.
