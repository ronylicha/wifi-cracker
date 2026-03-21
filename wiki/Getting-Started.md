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
git clone https://github.com/ronylicha/wifi-cracker.git
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

## Pentest Tools

**All 12 pentest tools are bundled directly in the APK.** On first launch, WiFi Cracker automatically extracts and installs them to `/data/local/tmp/wificracker/`. No Termux, no downloads, no manual setup required.

| Binary | Purpose |
|--------|---------|
| `aircrack-ng` | Password cracking from handshake files |
| `airodump-ng` | Network scanner and packet capture |
| `aireplay-ng` | Deauthentication and packet injection |
| `airmon-ng` | Monitor mode management |
| `hcxdumptool` | PMKID capture without connected clients |
| `hcxpcapngtool` | Convert `.cap` to `.hc22000` hash format |
| `hashcat` | Advanced password recovery |
| `hostapd` | Rogue access point for Evil Twin attacks |
| `dnsmasq` | DHCP/DNS server for Evil Twin |
| `iw` | Wireless interface configuration |
| `wpa_driver` | MediaTek sniffer firmware command |
| `ics_enable` | MediaTek ICS capture toggle |

All binaries are statically compiled for ARM64 (aarch64) and cross-compiled from source.

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

3. All 12 pentest tools are automatically installed on first launch. No manual action required.

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
