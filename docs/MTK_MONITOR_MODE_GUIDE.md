# Monitor Mode WiFi — MediaTek MT6878 (Dimensity 7300)

> **Version patch**: v4 (ICS capture + promiscuous + deauth + cfg80211 raw TX)
> **Date**: 2026-03-21
> **Chipset**: MediaTek MT6878 (Dimensity 7300), co-processeur WiFi MT6631 (NDS32 Andes N9)
> **Device**: Unihertz Titan 2 (`g71v78c2k_dfl_eea`), Android 16 (API 36)
> **Driver**: `wlan_drv_gen4m_6878.ko` (gen4m CONNAC 2.0+, SOC 7.0)
> **Prerequis**: Root Magisk, bootloader deverrouille

---

## TL;DR

Deux modules kernel patches (driver WiFi + cfg80211) distribues via un module Magisk unique. **152 bytes** modifies dans le driver (7 MB) + **12 bytes** dans cfg80211 (2 MB) activent :
- Capture de toutes les trames 802.11 (promiscuous) via `/dev/fw_log_ics`
- Injection de deauth via la commande `AP_STA_DISASSOC`
- Injection de frames 802.11 arbitraires via `NL80211_CMD_FRAME` (cfg80211 deverrouille)

---

## Installation

```bash
adb push firmware-dump/mtk_wifi_monitor_magisk.zip /data/local/tmp/
adb shell "su -c 'magisk --install-module /data/local/tmp/mtk_wifi_monitor_magisk.zip'"
adb reboot
```

OU via Magisk Manager : Modules → Installer depuis stockage → reboot.

### Verification

```bash
adb shell "su -c 'sha256sum /vendor/lib/modules/wlan_drv_gen4m_6878.ko | cut -c1-16'"
# Attendu: 1551f37b6b388250
adb shell "su -c 'sha256sum /vendor/lib/modules/cfg80211.ko | cut -c1-16'"
# Attendu: 6313f82e09073087
```

### Desinstallation

```bash
adb shell "su -c 'rm -rf /data/adb/modules/mtk_wifi_monitor && reboot'"
```

---

## Utilisation

### Capture de frames (RX promiscuous)

```bash
# 1. Activer le sniffer ICS
wpa_driver "SNIFFER 2 0 0 0 0 0 0 0 0 0"

# 2. Ouvrir le canal de capture
ics_enable 1

# 3. Lire les paquets
cat /dev/fw_log_ics > capture.bin

# 4. Desactiver
ics_enable 0
```

Le WiFi reste connecte pendant la capture. Tous les frames du canal actif sont captures : beacons, data, EAPOL, control frames, y compris le trafic d'autres devices (mode promiscuous).

### Injection de deauth

```bash
# Deauth un client specifique
wpa_driver "AP_STA_DISASSOC Mac=AA:BB:CC:DD:EE:FF"
```

Le deauth est envoye via le chemin TX interne du driver. Le patch NOP le check de role AP dans `priv_driver_set_ap_sta_disassoc` pour qu'il fonctionne en mode STA.

### Injection de frames arbitraires (raw TX)

Le patch cfg80211 autorise `NL80211_CMD_FRAME` pour tous les types de management frames en mode STA. Utilisation via un outil compatible libnl :

```bash
# Deauth via nl80211 (necessite un binaire compatible libnl)
iw dev wlan0 mgmt tx c0003a01<dest_mac><src_mac><bssid>000007000000 5200

# Probe Request broadcast
iw dev wlan0 mgmt tx 4000<dur><bcast><our_mac><bcast><seq><ssid_ie> 5200
```

> **Note** : l'outil `iw` standard (v6.17 installe via Termux) n'a pas la sous-commande `mgmt tx`. Il faut soit une version patchee d'`iw`, soit un outil custom utilisant libnl pour envoyer `NL80211_CMD_FRAME`. Le patch cfg80211 cote kernel EST en place et fonctionnel.

---

## Capacites

| Capacite | Statut | Methode |
|----------|--------|---------|
| Scan passif (beacons) | Fonctionnel | ICS capture + parseur 802.11 |
| Capture data unicast (notre device) | Fonctionnel | ICS T1 trampoline |
| Capture data unicast (AUTRES devices) | Fonctionnel | Mode promiscuous (T2, fw cmd 10) |
| Capture EAPOL / 4-way handshake | Fonctionnel | Mode promiscuous + ICS |
| Capture control frames (RTS/CTS/ACK) | Fonctionnel | ICS capture |
| Injection deauth | Fonctionnel | AP_STA_DISASSOC (T4 NOP) |
| Injection raw management frames | Pret cote kernel | cfg80211 patche, attend outil libnl |
| Channel hopping | Non implemente | Faisable via SET_TEST_CMD 1 <freq> |
| Injection data frames | Non supporte | Le driver n'a pas de raw data TX |

---

## Format des paquets captures

```
Offset  Taille  Champ
0       4       Magic = 0x44D9C99A (LE)
4       2       Type = 0x0001
6       2       Sequence
8       4       Info (0=data, timesync_const=sync)
12      2       SubType (0x000C = data)
14      2       Frame length
16      120     MTK RX Descriptor (RSSI, rate, timestamp)
136     N       802.11 Frame brute (FC + addrs + payload)
```

Timesync : quand `Info == 0x0008011000000000` → 24 bytes total, pas de frame.

---

## Architecture du patch

### Module 1 : wlan_drv_gen4m_6878.ko (4 patches)

| # | Type | Taille | Cible | Effet |
|---|------|--------|-------|-------|
| T1 | Trampoline | 52 B | `nicRxProcessPktWithoutReorder` → `nicRxProcessIcsLog` | Capture frames via /dev/fw_log_ics |
| T2 | Trampoline | 48 B | `nicRxEnablePromiscuousMode` → fw cmd 10 (0x0F) | Mode promiscuous ON |
| T3 | Trampoline | 48 B | `nicRxDisablePromiscuousMode` → fw cmd 10 (0x01) | Mode promiscuous OFF |
| T4 | NOP | 4 B | `priv_driver_set_ap_sta_disassoc` role check | Deauth en mode STA |

Total : 152 bytes modifies dans un .ko de 7 MB.

### Module 2 : cfg80211.ko (3 patches)

| # | Type | Taille | Cible dans `cfg80211_mlme_mgmt_tx` | Effet |
|---|------|--------|-------------------------------------|-------|
| N1 | NOP | 4 B | `tbz w8, #0, error` @ 0x406c8 | Skip registration bitmap check |
| N2 | NOP | 4 B | `b.ne error` @ 0x407e0 | Skip subtype filter (Auth/Deauth/Action only) |
| N3 | NOP | 4 B | `tbz w8, #0, error` @ 0x407ec | Skip capability bit check |

Total : 12 bytes modifies dans un .ko de 2 MB.

### Commandes firmware

| CMD ID | Nom | Role |
|--------|-----|------|
| 0x93 (147) | SET_ICS_SNIFFER | Configure les bandes/canaux ICS |
| 0x0A (10) | SET_PACKET_FILTER | Configure le filtre RX (0x0F=promiscuous) |

### Chaine d'activation

```
Capture:
  wpa_driver "SNIFFER 2 0 ..." → fw cmd 0x93 → adapter.ics_enabled = 1
  ics_enable 1 → ioctl FC00/FC01 → ICS device ready
  Chaque paquet RX → [T1] check flag → nicRxProcessIcsLog → /dev/fw_log_ics

Deauth:
  wpa_driver "AP_STA_DISASSOC Mac=XX" → priv_driver_set_ap_sta_disassoc
  → [T4] NOP role check → mboxSendMsg → authSendDeauthFrame → TX

Raw TX (quand outil libnl disponible):
  NL80211_CMD_FRAME → nl80211_tx_mgmt → [N1,N2,N3] skip checks
  → cfg80211_mlme_mgmt_tx → mtk_cfg80211_mgmt_tx → cnmPktAllocWrapper
  → memcpy(frame) → mboxSendMsg → TX
```

---

## Outils userspace

### wpa_driver

Envoie des commandes DRIVER via wpa_supplicant (`/data/vendor/wifi/wpa/sockets/wlan0`).

```c
// Compile: aarch64-linux-gnu-gcc -static -o wpa_driver wpa_driver.c
// Usage: wpa_driver "SNIFFER 2 0 0 0 0 0 0 0 0 0"
//        wpa_driver "AP_STA_DISASSOC Mac=AA:BB:CC:DD:EE:FF"
```

### ics_enable

Active/desactive la capture ICS via ioctl sur `/dev/fw_log_ics`.

```c
// Compile: aarch64-linux-gnu-gcc -static -o ics_enable ics_enable.c
// Usage: ics_enable 1   (enable, level 2)
//        ics_enable 0   (disable)
```

---

## Resultats de capture valides

```
981 paquets (305 KB) en 5 secondes

Frame types: RTS(190) CTS(163) Beacon(98) QoS-Data(61) ACK(27) ProbeResp(2) ...

5 BSSIDs / SSIDs detectes:
  34:98:b5:43:6a:a7  LichaWireless
  34:98:b5:46:39:27  LichaWireless
  3e:98:b5:43:6a:a7  LichaWireless-IOT
  3e:98:b5:46:39:27  LichaWireless-IOT
  ae:ad:f3:c2:e2:a5  LichaWireless
```

---

## Compatibilite

| Parametre | Valeur testee |
|-----------|--------------|
| SoC | MediaTek MT6878 (Dimensity 7300) |
| WiFi | MT6631 (NDS32 Andes N9), 5GHz ch40 (5200 MHz) |
| Driver | `wlan_drv_gen4m_6878.ko` gen4m CONNAC 2.0+ |
| cfg80211 | `cfg80211.ko` kernel 6.1.145 |
| Device | Unihertz Titan 2 (`g71v78c2k_dfl_eea`) |
| Android | 16 (API 36) |
| Root | Magisk |

Le patch depend des offsets binaires exacts. Un driver/cfg80211 de version differente necessite re-analyse Ghidra + adaptation de `patch_driver.py`.

---

## Limites

1. **Canal unique** : capture sur le canal courant. Channel hopping necessite deconnexion WiFi ou `SET_TEST_CMD 1 <freq>`.
2. **Injection TX limitee** : deauth via `AP_STA_DISASSOC` fonctionne. Raw TX (tout type de frame) est pret cote kernel (cfg80211 patche) mais necessite un outil libnl pour envoyer `NL80211_CMD_FRAME`.
3. **Specifique MT6878/MT6631** : adapte au driver exact teste. Methodologie reproductible pour d'autres SoC MTK (Ghidra + patch_driver.py).
4. **OTA** : reinstaller le module Magisk apres chaque mise a jour systeme.

---

## Structure des fichiers

```
firmware-dump/
  mtk_wifi_monitor_magisk.zip      Module Magisk v4 (2 .ko patches)
  patch_driver.py                   Script patch wlan driver (T1-T4)
  wlan_drv_gen4m_6878.ko.ORIGINAL  Backup driver original
  cfg80211.ko                       cfg80211 original (pour reference)

docs/
  MTK_MONITOR_MODE_GUIDE.md        Ce document
  FIRMWARE_ANALYSIS.md              Analyse RE detaillee du firmware NDS32

wifi-cracker/core/.../wifi/
  monitor/
    IcsPacketParser.kt              Parse format ICS binaire
    Ieee80211Parser.kt              Parse trames 802.11
    MtkMonitorCapture.kt            Moteur de capture (Flow/coroutines)
    MonitorModeManager.kt           Manager haut niveau (@Singleton)
  ChipsetMonitorHelper.kt          Detection chipset + enable/disable MTK
```

---

## Hashes

| Fichier | SHA256 |
|---------|--------|
| wlan driver original | `1b3a2244bd25769a438f57250c5dece29b421a1825c7cd636cffa0d611f8a257` |
| wlan driver patche v4 | `1551f37b6b3882505a9a30229aa6f768d8b01589d5c3e3366e1c653f43d66d48` |
| cfg80211 original | `7f0bca381fed2f065072dda44c47e706a79160c03ba357aee2ef720bd09bbb1f` |
| cfg80211 patche v4 | `6313f82e09073087c3ad977c4988d46d28778b2ba47b89d2866fac31a8f391f8` |

---

## Chronologie

1. Analyse firmware `connsys_wifi.img` → architecture NDS32 (Andes N9/N10)
2. Extraction driver `wlan_drv_gen4m_6878.ko` (7 MB, ARM64, 22K symboles, non strippe)
3. Decompilation Ghidra headless : 24+ fonctions critiques en C
4. Decouverte : 5 fonctions monitor mode = stubs vides + `nicRxProcessIcsLog` = 0 appelants
5. Decouverte : firmware cmd 0x0A (10) = SET_PACKET_FILTER (promiscuous)
6. v1 : trampoline ICS (52B) → capture paquets RX
7. v2 : + trampolines promiscuous enable/disable (2x48B) → capture ALL frames
8. v3 : + NOP role check AP_STA_DISASSOC (4B) → deauth injection en mode STA
9. Decompilation `cfg80211_mlme_mgmt_tx` → 3 checks identifes (bitmap, subtype filter, capability)
10. v4 : + 3 NOPs dans cfg80211.ko → raw management frame TX autorise en mode STA
11. Validation : 981 paquets captures, 5 reseaux detectes, deauth fonctionnel
12. Distribution : module Magisk v4 avec 2 .ko patches

---

## Pour aller plus loin

### Channel hopping

```bash
# En mode test (coupe le WiFi normal)
wpa_driver "SET_TEST_MODE 2011"
wpa_driver "SET_TEST_CMD 1 2412"   # ch1
# capturer...
wpa_driver "SET_TEST_CMD 1 2437"   # ch6
# capturer...
wpa_driver "SET_TEST_MODE 0"       # retour normal
```

### Outil raw TX avec libnl

Pour envoyer des frames 802.11 arbitraires, compiler un outil utilisant libnl3 qui envoie `NL80211_CMD_FRAME` correctement. Le patch cfg80211 (N1/N2/N3) est deja en place.

### Adaptation a d'autres devices MediaTek

```bash
# 1. Extraire le driver
adb pull /vendor/lib/modules/wlan_drv_gen4m_*.ko

# 2. Verifier les stubs (nm | grep nicRxEnablePromiscuousMode)
# 3. Decompiler avec Ghidra, adapter les offsets dans patch_driver.py
# 4. Executer le patcher → tester
```
