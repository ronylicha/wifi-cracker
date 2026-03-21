# Supported Devices

WiFi Cracker automatically detects the WiFi chipset vendor at startup and selects the appropriate method for enabling monitor mode. This page documents supported chipsets, tested devices, and recommended external adapters.

---

## Internal Chipset Compatibility

### Detection Logic

On launch, the `ChipsetMonitorHelper` probes the device in sequence:

1. Checks for `/sys/module/wlan/parameters/con_mode` — indicates a Qualcomm QCACLD driver.
2. Checks for `/sys/module/bcmdhd*/parameters/` or `/sys/module/dhd/parameters/` — indicates a Broadcom driver.
3. Checks the `ro.vendor.wlan.gen` system property — indicates a MediaTek driver. Verifies the SHA-256 hash of the loaded `.ko` file to determine whether the patch is installed.

The result is displayed on the Modules screen and in the scan interface.

### Chipset Support Table

| Vendor | SoC Family | Method | Monitor Mode | Notes |
|--------|-----------|--------|:------------:|-------|
| **Qualcomm** | Snapdragon | QCACLD `con_mode=4` | Native | Sets `/sys/module/wlan/parameters/con_mode` to 4 then brings interface up. No extra software required. |
| **Broadcom** | Exynos, Pixel | Nexmon firmware patch | Requires Nexmon | Uses `nexutil -m2` if Nexmon is installed. Falls back to `iw set type monitor` if nexutil is absent. |
| **MediaTek** | Dimensity | ICS capture + patched `.ko` | Requires Magisk module | Requires the `mtk_wifi_monitor` Magisk module (see below). Validated on MT6878 (Dimensity 7300). |
| **Unknown** | Any | Generic `iw` | Depends on driver | Attempts `iw dev <iface> set type monitor`. Success depends on the driver's monitor mode support. |

---

## MediaTek Support Detail (MT6878 / Dimensity 7300)

### Tested Configuration

| Parameter | Value |
|-----------|-------|
| Device | Unihertz Titan 2 (`g71v78c2k_dfl_eea`) |
| SoC | MediaTek MT6878 (Dimensity 7300) |
| WiFi co-processor | MT6631 (NDS32 Andes N9) |
| Android version | 16 (API 36) |
| Kernel | 6.1.145 |
| Driver | `wlan_drv_gen4m_6878.ko` (gen4m CONNAC 2.0+, SOC 7.0) |
| Root | Magisk |
| Bootloader | Unlocked |
| Patch version | v4 |

### Validated Capabilities (MT6878)

| Capability | Status | Method |
|------------|--------|--------|
| Passive scan (beacon frames) | Working | ICS capture + 802.11 parser |
| Unicast frame capture (own device) | Working | ICS T1 trampoline |
| Promiscuous capture (other devices) | Working | Firmware command 0x0A (filter 0x0F) |
| EAPOL / 4-way handshake capture | Working | Promiscuous mode + ICS |
| Control frame capture (RTS/CTS/ACK) | Working | ICS capture |
| Deauthentication injection | Working | `AP_STA_DISASSOC` (T4 NOP) |
| Raw management frame injection | Kernel ready | cfg80211 patched; userspace libnl tool needed |
| Channel hopping | Not implemented | Feasible via `SET_TEST_CMD 1 <freq>` |

**Measured capture rate:** 981 packets (305 KB) in 5 seconds on channel 40 (5200 MHz), detecting 5 distinct BSSIDs.

### Extending to Other MediaTek Devices

The patch targets specific binary offsets in `wlan_drv_gen4m_6878.ko`. Devices with a different driver version require re-analysis with Ghidra and offset adaptation using `firmware-dump/patch_driver.py`. The methodology is documented in [docs/FIRMWARE_ANALYSIS.md](../docs/FIRMWARE_ANALYSIS.md).

```bash
# Extract the driver from another MTK device
adb pull /vendor/lib/modules/wlan_drv_gen4m_*.ko

# Verify stub functions are present (prerequisite for patching)
nm wlan_drv_gen4m_*.ko | grep nicRxEnablePromiscuousMode

# Load in Ghidra (AArch64), adapt offsets in patch_driver.py, then test
```

### Driver Patch File Hashes

| File | SHA-256 |
|------|---------|
| `wlan_drv_gen4m_6878.ko` (original) | `1b3a2244bd25769a438f57250c5dece29b421a1825c7cd636cffa0d611f8a257` |
| `wlan_drv_gen4m_6878.ko` (patched v4) | `1551f37b6b3882505a9a30229aa6f768d8b01589d5c3e3366e1c653f43d66d48` |
| `cfg80211.ko` (original) | `7f0bca381fed2f065072dda44c47e706a79160c03ba357aee2ef720bd09bbb1f` |
| `cfg80211.ko` (patched v4) | `6313f82e09073087c3ad977c4988d46d28778b2ba47b89d2866fac31a8f391f8` |

---

## External WiFi Adapter Recommendations

Most internal chipsets do not natively support frame injection or monitor mode across all channels. An external USB adapter provides the most reliable and chipset-independent experience.

### Recommended Adapters

| Adapter | Chipset | Standard | Price (approx.) | Notes |
|---------|---------|----------|----------------|-------|
| **Alfa AWUS036AXML** | MT7921AU | WiFi 6E (6 GHz) | ~70 EUR | Best all-round choice. Full monitor mode and injection. Broad Linux driver support. |
| **Alfa AWUS036ACM** | MT7612U | WiFi 5 (5 GHz) | ~40 EUR | Solid 5 GHz support. Well-tested with aircrack-ng suite. |
| **Alfa AWUS036ACH** | RTL8812AU | WiFi 5 (5 GHz) | ~35 EUR | Wide compatibility. Requires `rtl8812au` driver on Android. |

### Adapter Setup

1. Connect the adapter via USB OTG to the Android device.
2. Grant the USB device permission when prompted.
3. The interface typically appears as `wlan1`. Verify with:

```bash
adb shell "su -c 'iw dev'"
```

4. In WiFi Cracker, select `wlan1` (or the detected interface) in the Scan and Attack screens.

### USB OTG Requirements

- The Android device must support USB OTG. Most modern devices do, but verify for your specific model.
- A USB-C to USB-A OTG adapter or hub may be needed depending on the adapter's connector.
- Some devices limit USB OTG current. If the adapter does not initialize, use a powered USB hub.

---

## Encryption Type Risk Levels

WiFi Cracker assigns a risk level to each encryption type detected during scanning:

| Encryption | Risk Level | Rationale |
|------------|------------|-----------|
| Open | Critical | No encryption — all traffic readable by any observer |
| WEP | Critical | Broken encryption — crackable in under 1 minute |
| WPA (TKIP) | High | Deprecated — known cryptographic weaknesses |
| WPA2 (CCMP) | Medium | Current standard — vulnerable to offline dictionary attacks |
| WPA3 (SAE) | Low | Current best practice — resistant to offline attacks |
| Unknown | High | Conservative default when protocol cannot be determined |

---

## Known Limitations

| Limitation | Affects | Workaround |
|------------|---------|------------|
| Channel hopping not implemented (MTK) | MTK internal capture | Scan on current association channel only; use external adapter for multi-channel |
| Monitor mode on MTK requires exact `.ko` version | MT6878 patch | Re-analyze and re-patch for other versions |
| Magisk module must be reinstalled after OTA updates | MTK devices | Reinstall `mtk_wifi_monitor_magisk.zip` after each system update |
| Raw management frame TX needs libnl tool (MTK) | Advanced raw TX | cfg80211 patch is ready; userspace tooling pending |
| Nexmon not pre-installed | Broadcom devices | Install Nexmon separately from the Nexmon GitHub releases |
