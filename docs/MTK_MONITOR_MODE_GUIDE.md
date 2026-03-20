# Monitor Mode WiFi — MediaTek MT6878 (Dimensity 7300)

> **Version patch**: v2 (ICS capture + mode promiscuous)
> **Date**: 2026-03-20
> **Chipset**: MediaTek MT6878 (Dimensity 7300), co-processeur WiFi MT6631 (NDS32 Andes N9)
> **Device**: Unihertz Titan 2 (`g71v78c2k_dfl_eea`), Android 16 (API 36)
> **Driver**: `wlan_drv_gen4m_6878.ko` (gen4m CONNAC 2.0+, SOC 7.0)
> **Prerequis**: Root Magisk, bootloader deverrouille

---

## TL;DR

Un patch de **148 bytes** dans le driver kernel (7 MB) active le monitor mode WiFi complet : capture de toutes les trames 802.11 (beacons, data, EAPOL handshakes, control frames) y compris celles d'autres devices via le mode promiscuous. Distribue comme module Magisk, zero regression sur le WiFi normal.

---

## Installation

```bash
# Depuis un PC avec adb
adb push firmware-dump/mtk_wifi_monitor_magisk.zip /data/local/tmp/
adb shell "su -c 'magisk --install-module /data/local/tmp/mtk_wifi_monitor_magisk.zip'"
adb reboot
```

**OU** via Magisk Manager : Modules → Installer depuis stockage → selectionner le zip → reboot.

### Verification

```bash
adb shell "su -c 'sha256sum /vendor/lib/modules/wlan_drv_gen4m_6878.ko'"
# Attendu: f646d28573cb32e3ae9378ae604c86613320aacfe88ad35f684ba713f6602c30
```

### Desinstallation (rollback)

```bash
adb shell "su -c 'rm -rf /data/adb/modules/mtk_wifi_monitor && reboot'"
```

Le driver original est restaure automatiquement au reboot.

---

## Utilisation

### Activer la capture

```bash
# 1. Activer le SNIFFER (configure le firmware pour le mode ICS)
adb shell "su -c '/data/local/tmp/wpa_driver \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"'"

# 2. Activer le device ICS (ouvre le canal de capture)
adb shell "su -c '/data/local/tmp/ics_enable 1'"

# 3. Lire les paquets captures
adb shell "su -c 'cat /dev/fw_log_ics'" > capture.bin
```

### Desactiver la capture

```bash
adb shell "su -c '/data/local/tmp/ics_enable 0'"
```

Le WiFi reste fonctionnel pendant et apres la capture.

---

## Ce que le patch capture

| Type de trame | Capture v1 | Capture v2 (actuel) |
|---------------|-----------|---------------------|
| Beacons (scan reseau) | oui | oui |
| Probe Request / Response | oui | oui |
| Auth / Deauth / Disassoc | oui | oui |
| RTS / CTS / ACK | oui | oui |
| Data / QoS-Data (notre device) | oui | oui |
| **Data / QoS-Data (AUTRES devices)** | **non** | **oui** |
| **EAPOL / 4-way handshake** | **non** | **oui** |
| **Frames unicast entre tiers** | **non** | **oui** |

Le v2 ajoute le **mode promiscuous** : le firmware forward TOUTES les trames recues sur le canal actif, pas seulement celles destinees a notre MAC.

---

## Format des paquets captures

Chaque paquet dans `/dev/fw_log_ics` :

```
Offset  Taille  Champ
0       4       Magic = 0x44D9C99A (little-endian)
4       2       Type = 0x0001
6       2       Sequence number
8       4       Info (0 pour data, timesync constant pour sync)
12      2       SubType (0x000C pour data)
14      2       Frame length (incluant le descripteur MTK)
16      120     MTK RX Descriptor (RSSI, rate, timestamp, flags)
136     N       Trame IEEE 802.11 brute (FC + addrs + payload)
```

**Timesync** : quand `Info == 0x0008011000000000` → paquet de synchronisation (24 bytes total, pas de frame data).

**Trame 802.11** : commence a l'offset **120** dans le frame data. Format standard IEEE 802.11 avec Frame Control, Duration, 3 adresses MAC, Sequence Control, puis payload.

---

## Architecture technique du patch

### Le probleme : double verrouillage MediaTek

MediaTek a desactive le monitor mode a deux niveaux dans les builds consommateurs :

**Verrou 1 — Driver kernel (ARM64)** : 5 fonctions remplacees par des stubs vides

| Fonction | Adresse .text | Code original | Role |
|----------|--------------|---------------|------|
| `nicRxEnablePromiscuousMode` | 0x5f514 | `ret` (4 bytes) | Active le mode promiscuous |
| `nicRxDisablePromiscuousMode` | 0x5f51c | `ret` (4 bytes) | Desactive le mode promiscuous |
| `wlanSetPromiscuousMode` | 0x1fbc4 | `ret` (4 bytes) | Wrapper promiscuous |
| `mt_op_set_rx_filter` | 0x153c0 | `mov w0,#0; ret` (8 bytes) | Configure le filtre RX hardware |
| `nicRxProcessIcsLog` | 0x5ddb8 | **612 bytes de code mort** (0 appelants) | Ecrit les paquets dans /dev/fw_log_ics |

**Verrou 2 — Firmware NDS32 (non modifie)** : le firmware filtre les paquets selon sa politique.

### La solution : 3 trampolines ARM64

Le patch ajoute **148 bytes** de code executable a la fin de la section `.text` du driver :

#### Trampoline 1 — Capture ICS (52 bytes)

Injecte dans `nicRxProcessPktWithoutReorder` (point d'entree de TOUS les paquets RX).

```
Avant:                              Apres:
nicRxProcessPktWithoutReorder:      nicRxProcessPktWithoutReorder:
  paciasp                             B trampoline_1
  sub sp, sp, #0x50                   sub sp, sp, #0x50
  ...                                 ...

trampoline_1 (.text + 0x222BB0):
  stp x29, x30, [sp, #-48]!     ; sauvegarder frame + LR
  stp x0, x1, [sp, #16]         ; sauvegarder adapter, rxb
  stp x2, x3, [sp, #32]         ; sauvegarder scratch

  mov x2, #0x86b0               ; offset du flag ICS
  movk x2, #0x2a, lsl #16       ; x2 = 0x2A86B0
  ldrb w2, [x0, x2]             ; charger adapter->ics_band0_enable
  cbz w2, .skip                 ; si ICS pas active, sauter

  bl nicRxProcessIcsLog          ; ECRIRE LE PAQUET dans /dev/fw_log_ics

.skip:
  ldp x2, x3, [sp, #32]         ; restaurer
  ldp x0, x1, [sp, #16]
  ldp x29, x30, [sp], #48
  paciasp                        ; instruction originale
  b nicRxProcessPktWithoutReorder+4  ; continuer le flux normal
```

#### Trampoline 2 — Mode promiscuous ON (48 bytes)

Remplace le stub `nicRxEnablePromiscuousMode`.

```
Avant:                              Apres:
nicRxEnablePromiscuousMode:         nicRxEnablePromiscuousMode:
  ret                                 B trampoline_2

trampoline_2 (.text + 0x222BE4):
  stp x29, x30, [sp, #-32]!
  mov x29, sp

  mov w8, #0x0F                  ; filtre promiscuous = TOUT accepter
  str w8, [sp, #16]              ; stocker sur la pile
  str w8, [x0, #0x10]           ; mettre a jour adapter->packet_filter

  add x1, sp, #16               ; x1 = &filter_value
  mov w2, #4                    ; taille = 4 bytes
  mov x3, xzr                   ; NULL
  mov w4, wzr                   ; 0
  bl wlanoidSetPacketFilter      ; ENVOYER firmware cmd 10 (SET_PACKET_FILTER)

  ldp x29, x30, [sp], #32
  ret
```

Le firmware recoit la commande **0x0A (10)** avec la valeur **0x0F** et configure le filtre RX hardware pour accepter TOUTES les trames : unicast, multicast, broadcast, y compris celles destinees a d'autres MAC.

#### Trampoline 3 — Mode promiscuous OFF (48 bytes)

Remplace le stub `nicRxDisablePromiscuousMode`.

```
trampoline_3 (.text + 0x222C14):
  ; Identique a T2 mais avec filter = 0x01 (unicast seulement)
  mov w8, #0x01                  ; filtre normal
  ...
  bl wlanoidSetPacketFilter      ; firmware cmd 10 avec 0x01
```

### Commandes firmware utilisees

| CMD ID | Nom | Direction | Taille data | Role |
|--------|-----|-----------|-------------|------|
| 0x93 (147) | SET_ICS_SNIFFER | Set | 148 bytes | Configure les bandes/canaux ICS |
| 0x0A (10) | SET_PACKET_FILTER | Set | 68 bytes | Configure le filtre RX (promiscuous) |

Ces commandes sont envoyees au co-processeur WiFi MT6631 (NDS32) via `wlanSendSetQueryCmd`. Le firmware NDS32 n'est **pas modifie** — seul le driver kernel ARM64 est patche.

### Chaine d'activation complete

```
1. wpa_driver "SNIFFER 2 0 ..."
   → wpa_supplicant → cfg80211 vendor cmd → priv_driver_cmds
   → priv_driver_sniffer → wlanoidSetIcsSniffer
   → firmware cmd 0x93 → firmware configure ICS
   → adapter->ics_band0_enable = 1

2. ics_enable 1
   → open(/dev/fw_log_ics) → ioctl ICS_SET_LVL(2) → ioctl ICS_ON_OFF(1)
   → wlanoidSetIcsSniffer(enable) → firmware active le log ICS

3. Chaque paquet RX:
   → DMA → nicRxProcessRFBs → nicRxProcessPktWithoutReorder
   → [PATCH T1] check ICS flag → appel nicRxProcessIcsLog
   → wifi_ics_fwlog_write → /dev/fw_log_ics → userspace

4. (Si promiscuous) nicRxEnablePromiscuousMode
   → [PATCH T2] wlanoidSetPacketFilter(0x0F)
   → firmware cmd 10 → hardware RX filter = accept all
```

---

## Outils natifs necessaires

Deux binaires ARM64 statiques doivent etre sur le device dans `/data/local/tmp/` :

### wpa_driver

Envoie des commandes `DRIVER` via le socket wpa_supplicant.

```c
// wpa_driver.c — compile: aarch64-linux-gnu-gcc -static -o wpa_driver wpa_driver.c
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <poll.h>

int main(int argc, char *argv[]) {
    if (argc < 2) { printf("Usage: %s <command>\n", argv[0]); return 1; }
    int sock = socket(AF_UNIX, SOCK_DGRAM, 0);
    struct sockaddr_un local = {.sun_family = AF_UNIX};
    snprintf(local.sun_path, 108, "/data/vendor/wifi/wpa/sockets/wpa_ctrl_%d-99", getpid());
    unlink(local.sun_path);
    bind(sock, (struct sockaddr *)&local, sizeof(local));
    struct sockaddr_un remote = {.sun_family = AF_UNIX};
    strncpy(remote.sun_path, "/data/vendor/wifi/wpa/sockets/wlan0", 107);
    connect(sock, (struct sockaddr *)&remote, sizeof(remote));
    char cmd[512];
    snprintf(cmd, sizeof(cmd), "DRIVER %s", argv[1]);
    send(sock, cmd, strlen(cmd), 0);
    struct pollfd pfd = {.fd = sock, .events = POLLIN};
    if (poll(&pfd, 1, 3000) > 0) {
        char buf[4096];
        int n = recv(sock, buf, sizeof(buf)-1, 0);
        if (n > 0) { buf[n] = 0; printf("%s\n", buf); }
    }
    unlink(local.sun_path);
    close(sock);
    return 0;
}
```

### ics_enable

Active/desactive la capture ICS via ioctl sur `/dev/fw_log_ics`.

```c
// ics_enable.c — compile: aarch64-linux-gnu-gcc -static -o ics_enable ics_enable.c
#include <stdio.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <stdlib.h>

int main(int argc, char *argv[]) {
    int enable = (argc > 1) ? atoi(argv[1]) : 1;
    int fd = open("/dev/fw_log_ics", 2);
    if (fd < 0) { perror("open"); return 1; }
    if (enable) {
        ioctl(fd, 0x4004FC01, 2);  // ICS_SET_LEVEL = 2
        ioctl(fd, 0x4004FC00, 1);  // ICS_ON_OFF = enable
        printf("ICS enabled (level 2)\n");
    } else {
        ioctl(fd, 0x4004FC00, 0);  // ICS_ON_OFF = disable
        printf("ICS disabled\n");
    }
    close(fd);
    return 0;
}
```

### Compilation et deploiement

```bash
aarch64-linux-gnu-gcc -static -o wpa_driver wpa_driver.c
aarch64-linux-gnu-gcc -static -o ics_enable ics_enable.c
adb push wpa_driver ics_enable /data/local/tmp/
adb shell "su -c 'chmod 755 /data/local/tmp/wpa_driver /data/local/tmp/ics_enable'"
```

---

## Resultats de capture valides

Capture de 5 secondes sur le canal actif :

```
981 paquets (305 KB)

Types de trames :
  RTS             : 190      CTS         : 163
  Beacon          :  98      QoS-Data    :  61
  ACK             :  27      ProbeResp   :   2
  ProbeReq        :   1      Action      :   1

5 BSSIDs / SSIDs detectes :
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
| Co-processeur WiFi | MT6631 (NDS32 / Andes N9) |
| Driver | `wlan_drv_gen4m_6878.ko` gen4m CONNAC 2.0+ |
| Device | Unihertz Titan 2 (`g71v78c2k_dfl_eea`) |
| Android | 16 (API 36) |
| Build firmware | `2026020617` |
| Root | Magisk |
| Bootloader | Deverrouille + OEM unlock |

Le patch depend des offsets de fonctions dans le binaire. Un driver de version differente necessite une re-analyse Ghidra + `patch_driver.py` adapte.

---

## Limites connues

1. **Canal unique** : capture sur le canal courant. Channel hopping necessite deconnexion WiFi.
2. **Pas d'injection TX** : le patch est RX only. Le driver n'a pas de fonction `nicTxRawFrame`. L'envoi de deauth/injection necessite un patch supplementaire.
3. **Specifique MT6878/MT6631** : adapte uniquement au driver exact teste. Autres SoC MediaTek necessitent la meme methodologie (Ghidra + repatch).
4. **OTA** : une mise a jour systeme remplace le driver. Reinstaller le module Magisk apres.

---

## Structure des fichiers

```
firmware-dump/
  mtk_wifi_monitor_magisk.zip      Module Magisk v2 (livrable)
  patch_driver.py                   Script Python de patch (reproductible)
  wlan_drv_gen4m_6878.ko.ORIGINAL  Backup du driver original (rollback)

docs/
  MTK_MONITOR_MODE_GUIDE.md        Ce document
  FIRMWARE_ANALYSIS.md              Analyse detaillee du firmware NDS32

wifi-cracker/core/.../wifi/
  monitor/
    IcsPacketParser.kt              Parse le format binaire ICS
    Ieee80211Parser.kt              Parse les trames 802.11
    MtkMonitorCapture.kt            Moteur de capture (coroutines/Flow)
    MonitorModeManager.kt           Manager haut niveau (@Singleton)
  ChipsetMonitorHelper.kt          Detection chipset + enable/disable MTK
```

---

## Hashes de reference

| Fichier | SHA256 |
|---------|--------|
| Driver original | `1b3a2244bd25769a438f57250c5dece29b421a1825c7cd636cffa0d611f8a257` |
| Driver patche v2 | `f646d28573cb32e3ae9378ae604c86613320aacfe88ad35f684ba713f6602c30` |
| Module Magisk v2 | `a95095720e4898aecc73834cfe4d63b2bce2799d6c52ed4d46a0a941385221ef` |

---

## Chronologie

1. Analyse firmware `connsys_wifi.img` → architecture NDS32 (Andes N9/N10)
2. Extraction driver `wlan_drv_gen4m_6878.ko` (7 MB, ARM64, 22K symboles, non strippe)
3. Decompilation Ghidra headless : 24+ fonctions critiques en C
4. Decouverte : 5 fonctions monitor mode = stubs vides (desactivees par MediaTek)
5. Decouverte : `nicRxProcessIcsLog` (612 bytes) = 0 appelants (code mort)
6. Decouverte : firmware cmd 0x0A (10) = SET_PACKET_FILTER (promiscuous via `wlanoidSetPacketFilter`)
7. Patch v1 : trampoline ICS (52 bytes) → capture paquets RX
8. Patch v2 : + trampolines promiscuous enable/disable (2x 48 bytes) → capture ALL frames
9. Distribution : module Magisk pour install/rollback propre
10. Validation : 981 paquets 802.11 captures en 5 secondes, 5 reseaux detectes

---

## Pour aller plus loin

### Injection TX (deauth)

Le driver a `authSendDeauthFrame` @ 0x13807c (fonction reelle). Pour l'injection :
- Trouver le contexte requis (STA record, BSS info) via Ghidra
- Ecrire un trampoline T4 qui construit un frame deauth et l'envoie
- Ou utiliser `wlanSendSetQueryCmd` avec le bon cmd ID pour TX management frames

### Channel hopping

Le driver supporte `SET_TEST_CMD 1 <freq>` (firmware func_id 1 = SET_CHANNEL_FREQ). En mode test, on peut changer de canal sans association. Sequence :
```
SET_TEST_MODE 2011 → SET_TEST_CMD 1 2412 → capture → SET_TEST_CMD 1 2437 → ...
```

### Adaptation a d'autres devices MediaTek

Methodologie reproductible :
1. `adb pull /vendor/lib/modules/wlan_drv_gen4m_*.ko`
2. `nm` pour verifier si les memes fonctions stub existent
3. Ghidra headless pour decompiler et trouver les offsets
4. Adapter `SYMBOLS` dans `patch_driver.py`
5. Executer le patcher → tester
