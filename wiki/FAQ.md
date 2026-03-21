# FAQ

Frequently asked questions about WiFi Cracker.

---

## General

### Is root access required?

Yes. Root access is mandatory. WiFi Cracker relies on root for several core functions:

- Executing `cmd wifi start-scan` and reading `cmd wifi list-scan-results`
- Running `aircrack-ng`, `airodump-ng`, `aireplay-ng`, `hcxdumptool`, `hashcat`, `hostapd`, and `dnsmasq` as privileged processes
- Enabling monitor mode by writing to `/sys/module/wlan/parameters/con_mode` (Qualcomm), running `nexutil` (Broadcom), or issuing ioctl commands to the MTK driver
- Reading raw frames from `/dev/fw_log_ics` (MediaTek patched driver)

The application checks for root at startup. If root is not granted, a `RootErrorScreen` is shown and no functionality is available. Supported root managers: Magisk, KernelSU, SuperSU.

---

### Does it work on unrooted devices?

No. There is no partial functionality for unrooted devices. The attack, crack, and low-level scan features all require privileged shell access that is only available with root.

---

### Can I use it on an emulator?

No. Emulators do not have WiFi hardware, so scanning and all attack operations are not possible.

---

## Hardware and Adapters

### Which external WiFi adapter should I use?

The recommended adapter is the **Alfa AWUS036AXML** (MediaTek MT7921AU, WiFi 6E). It provides the best compatibility with the aircrack-ng suite, supports all bands including 6 GHz, and has mature Linux driver support.

For a more affordable option, the **Alfa AWUS036ACH** (Realtek RTL8812AU, ~35 EUR) is widely tested and compatible.

See [Supported Devices](Supported-Devices) for a full comparison table.

---

### Does WiFi Cracker support WiFi 6 (802.11ax)?

Scanning detects WiFi 6 networks — they appear as WPA3 or WPA2 networks depending on their configuration. However, handshake capture and PMKID capture against WiFi 6 access points require an adapter that also supports 802.11ax. The Alfa AWUS036AXML (MT7921AU, WiFi 6E) is the recommended adapter for this use case.

Deauthentication attacks may be less effective against WiFi 6 networks that implement Management Frame Protection (MFP/802.11w). If the AP enforces MFP, deauth frames from unauthenticated sources are discarded.

---

### My USB adapter is not recognized. What should I do?

1. Verify USB OTG support on your device. Most modern Android devices support it, but some do not.
2. Check that the adapter appears in the system USB device list: `adb shell "su -c 'lsusb'"`.
3. Verify that the kernel module for the adapter's chipset is loaded: `adb shell "su -c 'lsmod | grep <chipset>'"`. For RTL8812AU, the module is `8812au` or `rtl8812au`.
4. On some devices, the USB OTG port cannot supply enough power. Use a powered USB hub between the device and the adapter.
5. After connecting, check that the interface appears: `adb shell "su -c 'iw dev'"`. It should list a second interface (e.g., `wlan1`).

---

### Do I need an external adapter if my device has a Qualcomm or Broadcom chipset?

It depends on the operation:

- **Scan (passive discovery)**: The internal adapter works for both Qualcomm and Broadcom devices.
- **Handshake capture and PMKID capture**: Requires monitor mode. Qualcomm QCACLD and Broadcom + Nexmon support this internally.
- **Deauthentication**: Requires packet injection. Qualcomm `con_mode=4` and Broadcom + Nexmon support this.
- **Evil Twin**: Requires a second wireless interface to run the rogue AP while keeping the original interface active. An external adapter is required.

For the best experience, an external adapter is still recommended even on Qualcomm and Broadcom devices, as internal monitor mode support can vary across driver versions and Android builds.

---

## MediaTek / MTK

### My device has a MediaTek chipset. What do I need?

You need to install the `mtk_wifi_monitor` Magisk module, which patches the kernel driver to enable monitor mode. Without the patch, the MTK driver has all monitor mode functions compiled as empty stubs.

Full installation instructions: [Getting Started — Device Setup for MediaTek](Getting-Started#device-setup-for-mediatek-mt6878--dimensity-7300).

---

### The MTK patch was tested on the Unihertz Titan 2. Does it work on my device?

The patch targets specific binary offsets in `wlan_drv_gen4m_6878.ko` and `cfg80211.ko` for the exact driver versions present on the Unihertz Titan 2 (MT6878, Android 16, kernel 6.1.145). It will not work on devices with different driver versions, even if they use the same SoC.

To port the patch to another device:

1. Extract `wlan_drv_gen4m_6878.ko` from the target device: `adb pull /vendor/lib/modules/wlan_drv_gen4m_*.ko`
2. Verify that the stub functions are present: `nm wlan_drv_gen4m_*.ko | grep nicRxEnablePromiscuousMode`
3. Load the driver in Ghidra (AArch64) and locate the same target functions using symbol names.
4. Update the offsets in `firmware-dump/patch_driver.py` and regenerate the patched `.ko`.
5. Package as a Magisk module and test.

See [docs/FIRMWARE_ANALYSIS.md](../docs/FIRMWARE_ANALYSIS.md) for the full reverse engineering methodology.

---

### After installing the MTK Magisk module, the app still says the patch is not installed.

The app verifies the patch by checking the SHA-256 hash of `/vendor/lib/modules/wlan_drv_gen4m_6878.ko`. Check the following:

1. Did you reboot after installing the module? The module replaces the `.ko` file on boot.
2. Verify the hash manually:
```bash
adb shell "su -c 'sha256sum /vendor/lib/modules/wlan_drv_gen4m_6878.ko | cut -c1-16'"
# Expected: 1551f37b6b388250
```
3. If the hash does not match, the patch was not applied to your driver version. See the previous question.
4. Check whether a system OTA update replaced the patched driver. Reinstall the module and reboot.

---

### The MTK capture only sees traffic on one channel.

This is a known limitation. The ICS capture reads frames on the current association channel only. Channel hopping is not yet implemented.

**Workaround options:**
- Use the Scan module to identify the target network's channel, then verify your device is associated to a network on that same channel before starting the ICS capture.
- Use an external USB adapter for multi-channel capture.
- For manual channel switching (breaks the WiFi connection):
```bash
adb shell "su -c '/data/local/tmp/wpa_driver \"SET_TEST_MODE 2011\"'"
adb shell "su -c '/data/local/tmp/wpa_driver \"SET_TEST_CMD 1 2437\"'"  # Channel 6
# capture...
adb shell "su -c '/data/local/tmp/wpa_driver \"SET_TEST_MODE 0\"'"  # Restore
```

---

## Tools and Modules

### Are all pentest tools included in the APK?

Yes. All 12 ARM64 binaries (aircrack-ng, hashcat, hcxdumptool, hostapd, dnsmasq, iw, etc.) are bundled directly in the APK and auto-installed on first launch to `/data/local/tmp/wificracker/`. No Termux, no downloads, no manual setup required.

---

### Where are the installed binaries stored?

The app copies all binaries to `/data/local/tmp/wificracker/`. This directory is accessible to root and persists across app reinstalls (unless manually deleted).

---

### Can I use my own compiled version of aircrack-ng or hashcat?

Yes. Place the ARM64 binary in `/data/local/tmp/wificracker/` with the correct filename and executable permissions:

```bash
adb push my-aircrack-ng /data/local/tmp/wificracker/aircrack-ng
adb shell "su -c 'chmod 755 /data/local/tmp/wificracker/aircrack-ng'"
```

The app resolves binary paths via `BinaryInstaller.getBinaryPath()`, which looks in the working directory first.

---

## Reports

### What report formats are available?

PDF, HTML, and JSON. Select the format when configuring the report in the Report module.

- **PDF** is suitable for formal client delivery.
- **HTML** is useful for quick browser-based review or embedding in a web portal.
- **JSON** is useful for programmatic integration with other security tools or ticketing systems.

---

### Can I generate a report in French?

Yes. Go to **Drawer > Settings** and switch the language to **French**. All subsequent reports will be generated in French, including section headings, severity labels, and auto-generated recommendations.

---

### The executive summary says "0 attacks performed" even though I ran attacks.

The Report module aggregates data from the current session via `SessionCollector`. If you closed and reopened the app between the attack and report generation, session data was not retained (session state is in-memory only). To include attack and crack results in the report, complete all testing steps within the same app session.

---

### How is the security grade calculated?

The grade (A–F) is based on the **average CVSS score** across all findings:

| Grade | Average CVSS |
|-------|-------------|
| A | 0.0 – 1.0 |
| B | 1.1 – 3.0 |
| C | 3.1 – 5.0 |
| D | 5.1 – 7.0 |
| F | 7.1 – 10.0 |

A network with one critical finding (CVSS 10.0) and five low findings (CVSS 2.0 each) would have an average of `(10.0 + 5 * 2.0) / 6 = 3.33`, resulting in a grade of C.

---

## Troubleshooting

### The app crashes on startup.

1. Verify root access is granted to the app in your root manager.
2. Check that Android version is 12 or higher.
3. Clear app data and cache, then relaunch.
4. Check logcat for crash details: `adb logcat -s wificracker 2>&1 | head -100`

---

### Scan returns no results.

1. Check that WiFi is enabled on the device.
2. Verify that `cmd wifi` commands work: `adb shell "su -c 'cmd wifi start-scan && sleep 2 && cmd wifi list-scan-results'"`
3. If results appear in the shell but not in the app, this may be a permissions issue — verify the app has `ACCESS_FINE_LOCATION` and `NEARBY_WIFI_DEVICES` permissions.
4. On Android 13+, `NEARBY_WIFI_DEVICES` must be granted explicitly.

---

### Handshake capture completes but the crack module reports "no valid handshake."

The `.cap` file may not contain a complete 4-way EAPOL handshake. This happens if only one or two of the four EAPOL frames were captured.

1. Verify the capture contains EAPOL frames: `adb pull /data/local/tmp/wificracker/captures/<file>.cap && tcpdump -r <file>.cap eapol`
2. Repeat the capture while simultaneously running a deauth attack to force a client reconnect.
3. Ensure the capture interface is on the correct channel: `adb shell "su -c 'iw dev'"` and verify the channel.
4. For PMKID capture (`hcxdumptool`), the AP may not expose a PMKID. Fall back to handshake capture.

---

### Hashcat runs but finds no password.

1. The wordlist may not contain the password. Try a larger wordlist (e.g., `rockyou.txt` from SecLists) or a rule-based attack.
2. The hash may be from a WPA3-SAE network. WPA3 uses SAE (Simultaneous Authentication of Equals), which is not vulnerable to offline dictionary attacks. The `.hc22000` format only covers WPA2 and earlier.
3. Verify the `.hc22000` file is not empty: `wc -l /data/local/tmp/wificracker/captures/<file>.hc22000`
