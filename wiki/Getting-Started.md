# Getting Started

This page covers everything needed to build WiFi Cracker from source, install it on a device, and prepare the device for a first audit session.

---

## Prerequisites

### Development Environment

| Requirement | Version |
|-------------|---------|
| Android Studio | Hedgehog (2023.1.1) or later |
| Android SDK | 35 |
| JDK | 17 |
| Kotlin | Bundled with Android Studio |

### Target Device

| Requirement | Details |
|-------------|---------|
| Android version | 12 or later (API 31+) |
| Root access | Magisk, KernelSU, or SuperSU |
| USB OTG | Required for external WiFi adapter |
| External WiFi adapter | Required for most attack types (see [Supported Devices](Supported-Devices)) |

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/wifi-cracker.git
cd wifi-cracker

# Debug build
./gradlew assembleDebug

# Release build (signed with the included keystore)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Install directly on a connected device
adb install -r app/build/outputs/apk/release/app-release.apk
```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

---

## Installing Pentest Tools

WiFi Cracker delegates scanning, attacking, and cracking to external ARM64 binaries (aircrack-ng, hashcat, and others). These must be installed before any module can run. The application includes a **Modules** screen that checks which tools are present and guides you through installation.

Navigate to: **Drawer menu > Modules**

### Method 1 — Via Termux (Recommended)

1. Install Termux from [F-Droid](https://f-droid.org/packages/com.termux/). Do not use the Play Store version — it is outdated and cannot install packages.

2. Open Termux and run:

```bash
pkg update && pkg install -y aircrack-ng hcxdumptool hcxtools hashcat hostapd dnsmasq iw
```

3. Open WiFi Cracker, go to **Drawer > Modules**, and tap **Install all missing modules**. The app copies the binaries from Termux to its working directory at `/data/local/tmp/wificracker/`.

### Method 2 — Via Kali NetHunter

If Kali NetHunter is installed on the device:

1. The required tools are already available inside the NetHunter chroot at `/data/local/nhsystem/kali-arm64/usr/bin/`.
2. Go to **Drawer > Modules** and tap **Install all missing modules**. The app detects and copies from the NetHunter chroot automatically.

### Method 3 — Automatic Download

If neither Termux nor NetHunter is installed, the Modules screen will attempt to download pre-compiled ARM64 binaries from community repositories using `curl`. This requires an active internet connection.

### Method 4 — Manual via ADB

Push pre-compiled ARM64 binaries directly from a PC:

```bash
adb push aircrack-ng /data/local/tmp/wificracker/
adb push airodump-ng /data/local/tmp/wificracker/
adb push aireplay-ng /data/local/tmp/wificracker/
adb push hcxdumptool /data/local/tmp/wificracker/
adb push hcxpcapngtool /data/local/tmp/wificracker/
adb push hashcat /data/local/tmp/wificracker/
adb push hostapd /data/local/tmp/wificracker/
adb push dnsmasq /data/local/tmp/wificracker/
adb push iw /data/local/tmp/wificracker/
adb shell "su -c 'chmod 755 /data/local/tmp/wificracker/*'"
```

### Required Binaries Summary

| Binary | Source Package | Purpose |
|--------|---------------|---------|
| `aircrack-ng` | aircrack-ng | Password cracking from handshake files |
| `airodump-ng` | aircrack-ng | Network scanner and packet capture |
| `aireplay-ng` | aircrack-ng | Deauthentication and packet injection |
| `hcxdumptool` | hcxdumptool | PMKID capture without connected clients |
| `hcxpcapngtool` | hcxtools | Convert `.cap` to `.hc22000` hash format |
| `hashcat` | hashcat | Advanced password recovery |
| `hostapd` | hostapd | Rogue access point for Evil Twin attacks |
| `dnsmasq` | dnsmasq | DHCP/DNS server for Evil Twin |
| `iw` | iw | Wireless interface configuration |

---

## Device Setup for MediaTek (MT6878 / Dimensity 7300)

MediaTek devices do not natively support monitor mode. WiFi Cracker provides a custom Magisk module that patches the kernel driver to enable it.

### Requirements

- Bootloader unlocked
- Magisk installed
- Device: Unihertz Titan 2 or another device with `wlan_drv_gen4m_6878.ko` and `cfg80211.ko` matching the patched versions (see [Supported Devices](Supported-Devices))

### Installation

**Option A — via ADB:**

```bash
adb push firmware-dump/mtk_wifi_monitor_magisk.zip /data/local/tmp/
adb shell "su -c 'magisk --install-module /data/local/tmp/mtk_wifi_monitor_magisk.zip'"
adb reboot
```

**Option B — via Magisk Manager:**

1. Open Magisk Manager
2. Go to **Modules**
3. Tap **Install from storage**
4. Select `firmware-dump/mtk_wifi_monitor_magisk.zip`
5. Reboot when prompted

### Verification

After reboot, verify the patched drivers are active:

```bash
# Driver patch (v4)
adb shell "su -c 'sha256sum /vendor/lib/modules/wlan_drv_gen4m_6878.ko | cut -c1-16'"
# Expected: 1551f37b6b388250

# cfg80211 patch (v4)
adb shell "su -c 'sha256sum /vendor/lib/modules/cfg80211.ko | cut -c1-16'"
# Expected: 6313f82e09073087
```

If both hashes match, the patch is active. WiFi Cracker will detect it automatically at startup.

### What the Patch Does

The module patches two kernel drivers with a total of 164 bytes of modifications:

- **`wlan_drv_gen4m_6878.ko`** (152 bytes): Four patches enable ICS frame capture via `/dev/fw_log_ics`, promiscuous mode, and deauthentication injection in STA mode.
- **`cfg80211.ko`** (12 bytes): Three NOPs remove restrictions on management frame transmission, enabling raw 802.11 frame injection via `NL80211_CMD_FRAME`.

The WiFi connection remains active during capture. See [docs/MTK_MONITOR_MODE_GUIDE.md](../docs/MTK_MONITOR_MODE_GUIDE.md) for the full technical reference.

### Compiling MTK Userspace Tools (Optional)

Two additional binaries are needed for the MTK path. They are compiled from source in `firmware-dump/` using the Android NDK:

```bash
NDK_CC="$ANDROID_HOME/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android31-clang"
$NDK_CC -static -o wpa_driver firmware-dump/wpa_driver.c
$NDK_CC -static -o ics_enable firmware-dump/ics_enable.c
adb push wpa_driver ics_enable /data/local/tmp/
adb shell "su -c 'chmod 755 /data/local/tmp/wpa_driver /data/local/tmp/ics_enable'"
```

### Uninstallation

```bash
adb shell "su -c 'rm -rf /data/adb/modules/mtk_wifi_monitor && reboot'"
```

---

## First Launch

1. Open WiFi Cracker. On first launch, a **legal disclaimer** is displayed. Read it and tap **I Understand and Accept** to proceed. The app will not function until you accept.

2. If root access is not detected, a **Root Error** screen is shown. Grant root permissions via your root manager and relaunch.

3. Navigate to **Drawer > Modules** and verify all required tools are installed (green checkmarks). Install any missing ones before proceeding.

4. Navigate to **Drawer > Settings** to configure:
   - Theme (dark/light)
   - Language (English / French)
   - Default WiFi interface

5. You are ready to start your first scan. Go to the **Scan** tab.

---

## Uninstall

Uninstalling the APK removes all application data. Working files (captured packets, wordlists) remain in `/data/local/tmp/wificracker/` and must be removed manually:

```bash
adb shell "su -c 'rm -rf /data/local/tmp/wificracker/'"
```

If the MTK Magisk module was installed, remove it as described above.
