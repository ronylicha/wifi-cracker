# Monitor Mode WiFi — MediaTek MT6878 (Dimensity 7300)

> **Statut**: Fonctionnel et valide
> **Date**: 2026-03-20
> **Chipset teste**: MediaTek MT6878 (Dimensity 7300) avec co-processeur WiFi **MT6631**
> **Device teste**: Unihertz Titan 2 (codename `g71v78c2k_dfl_eea`)
> **Driver teste**: `wlan_drv_gen4m_6878.ko` (gen4m / CONNAC 2.0+)
> **Module WMT**: `wmt_chrdev_wifi_connac2.ko`
> **Android**: 16 (API 36)
> **Kernel WiFi**: `soc7_0` (SOC 7.0 firmware series)
> **Parametres MTK**: `ro.vendor.wlan.gen = gen4m_6878`
> **Prerequis**: Root (Magisk), Bootloader deverrouille (OEM unlock)

---

## TL;DR

Le monitor mode WiFi sur le Titan 2 fonctionne grace a un **driver kernel patche** distribue via un **module Magisk**. Le patch injecte un appel a `nicRxProcessIcsLog` dans le chemin RX du driver, permettant la capture de toutes les trames 802.11 via `/dev/fw_log_ics`.

---

## Installation

### 1. Installer le module Magisk

```bash
# Depuis un PC avec adb
adb push firmware-dump/mtk_wifi_monitor_magisk.zip /data/local/tmp/
adb shell "su -c 'magisk --install-module /data/local/tmp/mtk_wifi_monitor.zip'"
adb reboot
```

**OU** via l'app Magisk :
1. Ouvrir Magisk Manager
2. Modules → Installer depuis le stockage
3. Selectionner `mtk_wifi_monitor_magisk.zip`
4. Redemarrer

**Fichier** : `firmware-dump/mtk_wifi_monitor_magisk.zip`
**SHA256** : `524b7d85cb98067f65f41bf325551b80e4283e3be873517038520f385d3a3fe6`

### 2. Verifier l'installation

```bash
# Le hash du driver doit correspondre au driver patche
adb shell "su -c 'sha256sum /vendor/lib/modules/wlan_drv_gen4m_6878.ko'"
# Attendu: aaead92bedc1e69f2642aaa54cb0a314ec3d28fec8781bf2263f4e203310534a
```

### 3. Desinstaller (rollback)

```bash
# Via Magisk Manager : desactiver ou supprimer le module, puis reboot
# OU via CLI :
adb shell "su -c 'rm -rf /data/adb/modules/mtk_wifi_monitor && reboot'"
```

---

## Utilisation

### Activer la capture

```bash
# 1. Envoyer la commande SNIFFER via wpa_supplicant
# (Le wpa_driver binary doit etre present dans /data/local/tmp/)
adb shell "su -c '/data/local/tmp/wpa_driver \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"'"

# 2. Activer le device ICS (via le binary ics_enable)
adb shell "su -c '/data/local/tmp/ics_enable 1'"

# 3. Lire les paquets captures
adb shell "su -c 'cat /dev/fw_log_ics'" > capture.bin
```

### Format des paquets captures

Chaque paquet dans `/dev/fw_log_ics` a le format suivant :

```
+-------+------+------+---------+--------+----+------------------+-----------------+
| Magic | Type |  Seq |  Info   | SubType| Len| MTK RX Desc      | 802.11 Frame    |
| 4B    | 2B   |  2B  |  4B     |  2B    | 2B | 120 bytes        | variable        |
+-------+------+------+---------+--------+----+------------------+-----------------+
| 0x44d9c99a  |  LE  | 0 or TS |  0x0c  |    | RX status/RSSI   | FC + addrs +    |
|             |      |         |        |    | rate info etc     | payload         |
+-------+------+------+---------+--------+----+------------------+-----------------+
```

- **Magic** : `0x44D9C99A` (little-endian)
- **Type** : `0x0001`
- **Timesync** : quand Info = `0x0008011000000000`, le paquet est un timesync (24 bytes total)
- **Data packets** : SubType = `0x000C`, Len = taille du frame data incluant le descripteur MTK
- **MTK RX Descriptor** : 120 bytes de metadata (RSSI, rate, timestamp, etc.)
- **802.11 Frame** : commence a offset 120 dans le frame data, format standard IEEE 802.11

### Desactiver la capture

```bash
adb shell "su -c '/data/local/tmp/ics_enable 0'"
```

Le WiFi continue de fonctionner normalement pendant et apres la capture.

---

## Comment ca marche — Details techniques

### Le probleme

MediaTek a **volontairement desactive** le monitor mode dans le driver WiFi `wlan_drv_gen4m_6878.ko` pour les appareils consommateurs :

| Fonction | Etat original | Role |
|----------|--------------|------|
| `nicRxEnablePromiscuousMode` | STUB (`ret`) | Active le mode promiscuous hardware |
| `nicRxDisablePromiscuousMode` | STUB (`ret`) | Desactive le mode promiscuous |
| `wlanSetPromiscuousMode` | STUB (`ret`) | Configure le mode promiscuous |
| `mt_op_set_rx_filter` | STUB (`return 0`) | Configure le filtre RX hardware |
| `nicRxProcessIcsLog` | **Jamais appelee** (0 xrefs) | Ecrit les paquets captures dans /dev/fw_log_ics |

Le mecanisme ICS (Internal Capture Service) est complet dans le driver — la fonction `nicRxProcessIcsLog` (612 bytes) sait ecrire les paquets via `wifi_ics_fwlog_write` vers `/dev/fw_log_ics`. Mais aucun code dans le driver ne l'appelle.

### Le patch

Le patch injecte **52 bytes** de code ARM64 (trampoline) a la fin de la section `.text` du driver :

```
nicRxProcessPktWithoutReorder (point d'entree RX):
  [PATCH] B trampoline          ← remplace 'paciasp'

trampoline (52 bytes, .text + 0x222BB0):
  stp x29, x30, [sp, #-48]!    ; save frame
  stp x0, x1, [sp, #16]        ; save adapter, rxb
  stp x2, x3, [sp, #32]        ; save scratch
  mov x2, #0x86b0              ; ICS flag offset
  movk x2, #0x2a, lsl #16     ; = 0x2A86B0
  ldrb w2, [x0, x2]           ; check adapter->ics_enabled
  cbz w2, skip                ; skip if ICS not enabled
  bl nicRxProcessIcsLog        ; LOG THE PACKET!
skip:
  ldp x2, x3, [sp, #32]       ; restore
  ldp x0, x1, [sp, #16]       ; restore
  ldp x29, x30, [sp], #48     ; restore
  paciasp                      ; original instruction
  b nicRxProcessPktWithoutReorder+4  ; continue original flow
```

Le trampoline :
1. Verifie si l'ICS est active (flag a `adapter + 0x2A86B0`)
2. Si oui : appelle `nicRxProcessIcsLog(adapter, rxb)` — envoie le paquet vers `/dev/fw_log_ics`
3. Continue l'execution normale du driver

**Impact** : Zero regression. Le WiFi fonctionne normalement. La capture ICS ne s'active que quand explicitement demandee via la commande SNIFFER + ioctl ICS.

### Architecture du firmware

| Composant | Architecture | Role |
|-----------|-------------|------|
| Driver kernel (`.ko`) | ARM64 (AArch64) | Relais Android ↔ firmware |
| Firmware WiFi (connsys_wifi) | NDS32 (Andes N9/N10) | Traitement 802.11 sur le co-processeur |
| Commande 0x93 | Firmware cmd | Configure l'ICS sniffer dans le firmware |

Le firmware NDS32 n'a **pas ete modifie**. Seul le driver kernel ARM64 est patche.

---

## Structure des fichiers

```
firmware-dump/
+-- wlan_drv_gen4m_6878.ko.ORIGINAL    # Driver original (backup)
+-- wlan_drv_gen4m_6878_patched.ko     # Driver patche
+-- mtk_wifi_monitor_magisk.zip        # Module Magisk pret a installer
+-- wmt_chrdev_wifi_connac2.ko.ORIGINAL # Module WMT (non modifie)
+-- connsys_wifi.img                    # Firmware WiFi (non modifie)
+-- patch_driver.py                     # Script de patch Python
+-- driver_decompiled.c                 # 24 fonctions decompilees (Ghidra)
+-- rx_path_decompiled.c                # Chemin RX decompile
+-- ghidra_project/                     # Projet Ghidra complet

docs/
+-- FIRMWARE_ANALYSIS.md               # Analyse complete du firmware
+-- MTK_MONITOR_MODE_GUIDE.md          # Ce document
```

---

## Outils necessaires sur le device

Les binaires natifs suivants doivent etre presents dans `/data/local/tmp/` :

| Binary | Role | Source |
|--------|------|--------|
| `wpa_driver` | Envoie des commandes DRIVER via wpa_supplicant | Compile depuis wpa_driver.c |
| `ics_enable` | Active/desactive l'ICS via ioctl | Compile depuis ics_enable.c |

Compilation (depuis le PC) :
```bash
aarch64-linux-gnu-gcc -static -o wpa_driver wpa_driver.c
aarch64-linux-gnu-gcc -static -o ics_enable ics_enable.c
adb push wpa_driver ics_enable /data/local/tmp/
adb shell "su -c 'chmod 755 /data/local/tmp/wpa_driver /data/local/tmp/ics_enable'"
```

---

## Resultats de capture valides

Avec le driver patche, une capture de 5 secondes produit typiquement :

```
981 paquets captures (305 KB)

Types de trames :
  RTS             : 190
  CTS             : 163
  Beacon          :  98
  QoS-Data        :  61
  ACK             :  27
  ProbeResp       :   2
  ProbeReq        :   1
  Action          :   1
  ...

BSSIDs detectes : 5 reseaux WiFi
```

---

## Compatibilite

| Parametre | Valeur testee | Notes |
|-----------|--------------|-------|
| **SoC** | MediaTek MT6878 (Dimensity 7300) | Seul SoC teste |
| **Co-processeur WiFi** | MT6631 (NDS32 / Andes N9) | Identifie dans le header firmware |
| **Driver** | `wlan_drv_gen4m_6878.ko` gen4m CONNAC 2.0+ | Hash SHA256: `aaead92b...` |
| **Module WMT** | `wmt_chrdev_wifi_connac2.ko` | Non modifie |
| **Firmware WiFi** | `connsys_wifi_mt6878_mt6631` (partition `connsys_wifi_a`) | Non modifie |
| **Device** | Unihertz Titan 2 (`g71v78c2k_dfl_eea`) | Seul device teste |
| **Android** | 16 (API 36) | |
| **Build firmware** | `2026020617` | |
| **Root** | Magisk | Requis pour le module et les commandes |
| **Bootloader** | Deverrouille + OEM unlock | Requis pour charger le driver modifie |

**Potentiellement compatible** (non teste) : tout device utilisant le meme SoC MT6878 avec le driver `wlan_drv_gen4m_6878.ko` de meme hash. Le patch depend des offsets de fonctions dans le binaire — un driver de version differente aura des offsets differents et necessitera une nouvelle analyse Ghidra + re-patch.

---

## Limites connues

1. **Canal unique** : la capture se fait sur le canal courant du WiFi (celui auquel le device est connecte). Le channel hopping necessite de deconnecter le WiFi.
2. **Pas de vrai mode promiscuous** : le patch capture les trames vues par le firmware sur le canal actif, pas tous les frames de tous les canaux.
3. **Specifique au chipset MT6878/MT6631** : le patch est compile pour le driver `wlan_drv_gen4m_6878.ko` (gen4m CONNAC 2.0+) specifique au SoC MediaTek **Dimensity 7300 (MT6878)** avec co-processeur WiFi **MT6631**. Teste uniquement sur le **Unihertz Titan 2** (`g71v78c2k_dfl_eea`, Android 16, firmware `2026020617`). D'autres devices avec le meme chipset MT6878 pourraient fonctionner si le driver est identique (meme hash SHA256). D'autres SoC MediaTek (Dimensity 8000, 9200, Helio, etc.) utilisent des drivers differents et necessiteraient un patch adapte avec la meme methodologie.
4. **Mises a jour OTA** : une mise a jour du systeme peut remplacer le driver. Le module Magisk doit etre reinstalle apres.

---

## Chronologie de la decouverte

1. Analyse du firmware `connsys_wifi.img` → architecture NDS32 (Andes N9/N10)
2. Extraction du driver kernel `wlan_drv_gen4m_6878.ko` (7 MB, ARM64, non strippe)
3. Decompilation Ghidra : 24 fonctions critiques analysees
4. Decouverte : 4 fonctions de monitor mode = STUBS vides
5. Decouverte : `nicRxProcessIcsLog` (612 bytes) = jamais appelee (0 cross-references)
6. Patch : injection de 52 bytes de trampoline dans le chemin RX
7. Distribution : module Magisk pour installation/rollback propre
8. Validation : 981 paquets 802.11 captures en 5 secondes
