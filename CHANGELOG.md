# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-03-21

### Added

#### Application Shell
- Android project scaffold with Hilt dependency injection, Jetpack Compose, and SplashScreen API
- Dark/light theme support with EN/FR string resources
- Navigation shell with bottom navigation bar, disclaimer gate, and root access check at launch
- Navigation drawer with Company Profile, Clients, and About screens
- WiFi Cracker adaptive icon and splash screen with cracked padlock + WiFi waves logo

#### Core Module
- `RootChecker` and `ShellExecutor` with multi-method root detection (`su` stdin execution)
- Hilt DI modules and `ChipsetDetector` with unit tests
- Auto-detection of WiFi chipset and vendor-specific monitor mode capabilities
- USB WiFi adapter auto-detection supporting 14 known chipsets
- Vulnerability database seeded at launch with 76+ CVE entries and auto-update on new entries
- `LocaleManager` for runtime EN/FR locale switching

#### Scan Module
- Data models, domain logic, and UI components for WiFi network scanning
- `ScanEngine` and `NetworkAnalyzer` with per-network risk assessment (signal strength, encryption, open ports)
- `ScanScreen` and `NetworkDetailScreen` with `ScanViewModel`
- Android system scan integration for passive network discovery
- Channel hopping, PCAP export, and live packet count display
- MTK ICS capture path for networks discovered via patched MediaTek driver
- Loading state on FAB, chipset info in scan header, and inline error messages
- Scrollable scan page layout with header and network list in a single `LazyColumn`

#### Attack Module
- Domain logic, UI screens, and command generators for five attack types:
  - **Deauthentication** — targeted or broadcast deauth frame injection
  - **PMKID Capture** — clientless WPA2 handshake harvesting
  - **WPS Pixie Dust** — offline WPS PIN brute-force
  - **Evil Twin** — rogue access point with captive portal
  - **EAPOL Replay** — injected EAPOL handshake replay
- Wired into main navigation alongside Crack and Report screens

#### Crack Module
- Domain logic, UI screens, and command generators for four cracking strategies:
  - **Dictionary** — wordlist-based passphrase attack via `aircrack-ng`
  - **Brute Force** — exhaustive character-space attack via `hashcat`
  - **Rainbow Table** — precomputed hash lookup
  - **PMKID Hash** — offline PMKID hash cracking via `hcxtools` + `hashcat`
- Wordlist manager with one-tap download for `rockyou.txt` and SecLists collections

#### Report Module
- Domain logic, UI screens, and generators for PDF, HTML, and JSON export formats
- Report wizard with scrollable layout and client selector dropdown
- Company and client profile management with Room database persistence
- Auto-load of active company profile when opening the report wizard
- `SessionCollector` for accumulating scan, attack, and crack results within a pentest session
- `SelectedNetworkRepository` enabling cross-tab network context sharing (scan → attack → crack → report)

#### MediaTek Monitor Mode
- Patched MediaTek MT6631 driver support through four integration patches:
  - v1 — initial monitor mode scaffold
  - v2 — promiscuous mode + EAPOL frame capture
    - v3 — deauthentication injection via `AP_STA_DISASSOC`
  - v4 — `cfg80211` raw TX path + automated channel hopping
- MTK firmware analysis notes and monitor mode enablement guide bundled in `docs/`

#### Modules & Tooling
- Modules installer screen with `aircrack-ng`, `hcxtools`, `hashcat`, and `iw` dependency management
- Bundled `aircrack-ng` binaries in APK with auto-install on first launch (fallback for Termux unavailability)
- Termux integration: launch via intent, run package install as Termux user, copy-to-clipboard command card
- Fallback WiFi interface detection via `/proc/net/wireless` when `iw` is absent

#### Security & Compliance
- Vulnerability database browser exposing all 76+ CVE entries with severity and remediation details
- Audit log screen recording every privileged shell command with timestamp, module, and exit code
- ProGuard / R8 rules configured to protect sensitive class names in release builds
- Release keystore excluded from version control via `.gitignore`

#### Internationalisation
- Full French (FR) translations across all modules: Scan, Attack, Crack, Report, Settings, Modules, and About

#### Quality
- 13 integration tests covering critical paths across all four modules
- `executeAsRoot` rewritten to pass shell operators correctly through `su` stdin

#### Documentation
- `README.md`, `CONTRIBUTING.md`, and GPL-3.0 `LICENSE`
- Modules installation guide and project roadmap
- MTK firmware analysis, monitor mode enablement guide, and patched driver source files

[1.0.0]: https://github.com/rony/wifi-cracker/releases/tag/v1.0.0
