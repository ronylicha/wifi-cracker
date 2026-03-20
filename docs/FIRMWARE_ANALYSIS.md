# Analyse Firmware WiFi MT6878/MT6631 — Unihertz Titan 2

> **Date**: 2026-03-20
> **Statut**: Analyse driver + firmware complete — strategie definie
> **Cible**: connsys_wifi_mt6878_mt6631

---

## 1. Identification du Device

| Info | Valeur |
|------|--------|
| Telephone | Unihertz Titan 2 |
| SoC | MediaTek MT6878 (Dimensity 7300) |
| Android | 16 (API 36) |
| Bootloader | Deverrouille (OEM unlock) |
| Root | Oui |
| Driver WiFi | wlan_drv_gen4m_6878 (proprietaire) |
| Modules kernel | wlan_drv_gen4m_6878.ko + wmt_chrdev_wifi_connac2.ko |
| Interface | wlan0 (MAC: c2:66:4b:f2:b3:93) |
| /dev/wmtWifi | Present, writable |
| mac80211 | Charge mais non utilise par le driver MTK |
| Ioctl SNIFFER | 0x8BE5 — accepte par driver, refuse par firmware |

---

## 2. Structure du Firmware (connsys_wifi.img)

### 2.1 Format de l'image

| Offset | Taille | Contenu |
|--------|--------|---------|
| 0x000000 | 1,516,292 B | **connsys_wifi_mt6878_mt6631** (notre cible) |
| 0x172510 | 1,709 B | cert1 (signature) |
| 0x172DC0 | 995 B | cert2 (signature) |
| 0x1733B0 | 1,618,512 B | connsys_wifi_mt6878_mt6637 (variante) |
| 0x2FE800 | 1,709 B | cert1 (pour MT6637) |
| 0x2FF0B0 | 995 B | cert2 (pour MT6637) |

> **Note**: Bootloader deverrouille = signatures cert1/cert2 non bloquantes.

### 2.2 Header du firmware MT6631

```
Offset 0x00: Magic    = 0x58881688
Offset 0x04: Size     = 0x00172304 (1,516,292 bytes)
Offset 0x08: Name     = "connsys_wifi_mt6878_mt6631"
Offset 0x30: Magic2   = 0x58891689
Offset 0x34: CodeOff  = 0x00000200
Offset 0x38: Flags    = 0x00000001
Offset 0x44: Align    = 0x00000010
Offset 0x50-0x1FF: Padding (0xFF)
```

### 2.3 Carte memoire du firmware

```
Section 1: 0x000200 - 0x040000  (256 KB)  — Code/data init (entropie 6.2-6.5)
Gap:        0x040000 - 0x091000  (320 KB)  — Padding zero (reserve RAM)
Section 2:  0x091000 - 0x140000  (820 KB)  — CODE PRINCIPAL (entropie 6.4-6.8)
Section 3:  0x140000 - 0x160000  (128 KB)  — Strings + data constantes
Section 4:  0x160000 - 0x172510  (74 KB)   — Tables, metadata, trailing
```

---

## 3. Architecture CPU

| Test | Resultats |
|------|-----------|
| ARM Thumb PUSH (0xB5xx) | 572 (faux positifs) |
| ARM32 STMDB (0xE92D) | 0 |
| RISC-V addi sp,sp,-N | 0 |
| RISC-V JAL ra | 7 |
| MIPS addiu sp,sp,-N | 0 |
| **NDS32 patterns (0xB8xxxxxx)** | **361** |
| NDS32 SETHI (0x46xxxxxx) | **1344** |

**Verdict: Andes NDS32 (AndeStar V3)** — confirme par:
- Analyse statistique des opcodes
- [cyrozap/mediatek-wifi-re](https://github.com/cyrozap/mediatek-wifi-re) qui documente NDS32 pour les WiFi cores MTK
- Absence totale de patterns ARM32/RISC-V/MIPS coherents

Le co-processeur WiFi MT6631 utilise un coeur **Andes N9 ou N10**.

---

## 4. Strings Critiques Identifiees

### 4.1 Test Mode / Sniffer (le verrou)

| Offset | String | Signification |
|--------|--------|---------------|
| 0x14AB78 | `testEngRxProcessPkt not under test mode` | **GUARD PRINCIPAL**: rejette les paquets RX si pas en test mode |
| 0x158435 | `[%s] Test mode is not enabled %d` | Check d'activation du test mode |
| 0x143414 | `Test Mode[%d],Phy Set change 0x%x->0x%x` | Changement PHY en test mode |
| 0x15D8E8 | `SwTestMode` | Nom du mode test logiciel |
| 0x144900 | `RFTEST` | Flag de mode test RF |

### 4.2 Packet Filtering (la decision)

| Offset | String | Signification |
|--------|--------|---------------|
| 0x142FFC | `Drop the policy packet` | **Decision de DROP** — paquets filtres |
| 0x143014 | `Send the packet to host` | **Decision de FORWARD** — paquets envoyes au host |
| 0x1443E4 | `Rx filter:%08x=%08x,%08x=%08x,...` | Configuration des filtres RX |

### 4.3 MDDP Monitor (path alternatif)

| Offset | String | Signification |
|--------|--------|---------------|
| 0x14475C | `MDDP_PF_MONITOR: %x:%x` | MediaTek Data Plane Driver — mode monitor |
| 0x153DBC | `pfCmdSetMddpFilterRule` | Commande pour configurer les filtres MDDP |

### 4.4 Fonctions Test Engine

| Offset | String |
|--------|--------|
| 0x14C8E8 | `testEngInit` |
| 0x14AB78 | `testEngRxProcessPkt not under test mode` |
| 0x14xxxx | `testEngListModeStartRx`, `testEngEnableLP`, `testEngCmdAccessListMode` |

---

## 5. Analyse du Driver Kernel (wlan_drv_gen4m_6878.ko)

### 5.0 Informations generales

- **Format**: ELF 64-bit ARM aarch64, NOT stripped
- **Taille**: 6.8 MB (7,082,400 bytes)
- **Symboles**: 22,073 total, 17,840 text
- **Dependances**: conninfra, wmt_chrdev_wifi_connac2, cfg80211, mddp, connfem, connadp

### 5.1 Fonctions STUB (volontairement desactivees par MediaTek)

```
nicRxEnablePromiscuousMode  @ 0x5f514 : ret          ← STUB
nicRxDisablePromiscuousMode @ 0x5f51c : ret          ← STUB
wlanSetPromiscuousMode      @ 0x1fbc4 : ret          ← STUB
mt_op_set_rx_filter         @ 0x153c0 : mov w0,#0;ret ← STUB
mt_op_set_rf_test_mode      @ 0x1585c : mov w0,#0;ret ← STUB
```

### 5.2 Fonctions REELLES (operationnelles)

```
priv_driver_sniffer         @ 0xaad08 : ~370 bytes, parse args + kalIoctl(wlanoidSetIcsSniffer)
wlanoidSetIcsSniffer        @ 0x3af2c : ~520 bytes, configure flags ICS dans adapter structure
mt_op_set_test_mode_start   @ 0x15868 : appel indirect via vtable [adapter+152]
mt_op_set_test_mode_abort   @ 0x158c4 : idem avec w1=1
wlanSendSetQueryCmdAdv      @ 0x38220 : envoi commandes au firmware
wlanSendSetQueryCmd         @ 0x62794 : idem
nicTxCmd                    @ 0x53e6c : envoi commandes TX
AteCmdSetHandle             @ 0xc46a4 : handler commandes ATE (test)
```

### 5.3 Variables globales cles

```
g_u4RXFilter     @ 0x51bb0 (BSS) : filtre RX courant
g_fgMddpEnabled  @ 0x12308 (DATA) : flag MDDP actif
gMddpFunc        @ 0x122f8 (DATA) : table fonctions MDDP
gMddpWFunc       @ 0x12298 (DATA) : table fonctions MDDP WiFi
```

### 5.4 Tables de commandes HQA (Hardware Quality Assurance)

```
HQA_CMD_SET0 @ 0x7e28 — HQA_CMD_SET6 @ 0x8388
CMD_SET0     @ 0x0b78 — CMD_SET6     @ 0x12e8
```

Ces tables contiennent les commandes du mode test RF. Elles sont REELLES et utilisees par `AteCmdSetHandle`.

### 5.5 Mecanisme de double verrouillage identifie

```
Verrou 1 (Driver) :
  nicRxEnablePromiscuousMode = STUB → hardware RX filter jamais configure
  mt_op_set_rx_filter = STUB → configuration filtre RX impossible
  wlanSetPromiscuousMode = STUB → mode promiscuous jamais active

Verrou 2 (Firmware) :
  testEngRxProcessPkt verifie fgTestMode → rejette paquets si pas en test mode
  Policy filter → "Drop the policy packet" pour frames non-associes
```

---

## 5b. Analyse du Mecanisme de Blocage

### 5.1 Flux de la commande SNIFFER

```
App (ioctl 0x8BE5 "SNIFFER 1 0")
  → wlan_drv_gen4m_6878.ko (driver kernel)
    → Accepte la commande
    → Envoie au firmware via WMT bus (/dev/wmtWifi)
      → Firmware MT6631 (NDS32)
        → testEngRxProcessPkt() verifie fgTestMode
        → fgTestMode == false → "not under test mode" → DROP
        → Paquets captures = 0
```

### 5.2 Hypothese du verrou

Le firmware a une variable globale `fgTestMode` (ou equivalente):
- `SwTestMode` l'active en mode test RF
- `testEngRxProcessPkt` verifie cette variable avant de traiter les paquets RX
- Sans `fgTestMode = true`, les paquets sont droppes par le filtre de politique
- La commande SNIFFER via ioctl active le mode dans le driver mais PAS dans le firmware

### 5.3 Decision tree dans le firmware

```
Paquet RX recu
  → Rx filter check (registres hardware)
  → Policy check:
    → if (policy_drop) → "Drop the policy packet" → discard
    → else → "Send the packet to host" → forward to driver → userspace
```

---

## 6. Strategie d'Attaque (4 Vecteurs — mis a jour apres analyse driver)

### Vecteur 0: Mode Test HQA (NOUVEAU — faisabilite tres haute)

**Principe**: `mt_op_set_test_mode_start` est REELLE et appelle un handler firmware via vtable. En entrant en mode test, le firmware active `testEngRxProcessPkt` qui TRAITE les paquets RX. Combiné avec les commandes HQA (AteCmdSetHandle), on peut configurer le firmware pour capturer tous les frames.

**Etapes**:
```bash
# 1. Depuis le device root, trouver comment invoquer mt_op_set_test_mode_start
# C'est probablement accessible via:
#   a) iwpriv wlan0 set TestMode (si iwpriv est installe)
#   b) echo "ATE ATESTART" > /proc/driver/wlan0/... (interface proc ATE)
#   c) Envoi direct via /dev/wmtWifi
#   d) Via la commande privee du driver (DRIVER SET_TEST_MODE)

# 2. Trouver l'interface ATE/HQA
adb shell "su -c 'find /proc /sys -name \"*ate*\" -o -name \"*hqa*\" -o -name \"*rftest*\" 2>/dev/null'"

# 3. Sequence d'activation:
#   a) Entrer en test mode (mt_op_set_test_mode_start)
#   b) Configurer RX pour recevoir tous les frames (HQA_CMD_SET)
#   c) Lire les frames captures
#   d) Quitter le test mode (mt_op_set_test_mode_abort)
```

**Avantages**: Pas de patch binaire necessaire ! Utilise des fonctions EXISTANTES et non-stubbees.
**Risques**: Le mode test peut desactiver la communication WiFi normale.

### Vecteur 1: Patch du Driver Kernel (faisabilite haute)

**Principe**: Modifier `wlan_drv_gen4m_6878.ko` pour:
1. Configurer les registres RX filter du hardware WiFi directement
2. Bypasser la verification firmware en interceptant les paquets au niveau DMA
3. Forcer le mode promiscuous dans le WFDMA (WiFi DMA)

**Etapes**:
```bash
# 1. Extraire le module du device
adb pull /vendor/lib/modules/wlan_drv_gen4m_6878.ko

# 2. Analyser avec Ghidra (architecture ARM64 — c'est du kernel Android)
# Chercher: priv_cmd_handler, set_sniffer, rx_filter, WFDMA config

# 3. Identifier les fonctions:
#    - Handler ioctl SNIFFER (0x8BE5)
#    - Configuration Rx filter registers
#    - WFDMA ring buffer setup
#    - Frame forwarding vers netdev

# 4. Patcher le .ko:
#    - Apres le SNIFFER ioctl, aussi configurer Rx filter hardware
#    - Modifier le Rx path pour forward TOUS les frames
#    - Ou: hook le DMA interrupt pour capturer les raw frames

# 5. Recharger:
adb shell "rmmod wlan_drv_gen4m_6878 && insmod /data/local/tmp/wlan_patched.ko"
```

**Avantages**: ARM64 bien supporte par Ghidra, pas besoin de toucher au firmware
**Risques**: Le driver pourrait ne pas avoir acces direct aux registres Rx filter

### Vecteur 2: Commandes MDDP/WMT (faisabilite moyenne)

**Principe**: Utiliser `/dev/wmtWifi` pour envoyer des commandes raw au firmware:
1. Activer `SwTestMode` via la bonne sequence de commandes WMT
2. Configurer les filtres MDDP pour le mode monitor (`pfCmdSetMddpFilterRule`)
3. Modifier les registres Rx filter via commandes firmware

**Etapes**:
```bash
# 1. Tracer les commandes WMT existantes
adb shell "cat /proc/driver/wlan0/wlan_drv_gen4m_6878"
adb shell "echo 'SNIFFER 1 0' > /dev/wmtWifi"

# 2. Examiner les ioctl disponibles dans le driver
# Le driver expose probablement:
#   - SET_TEST_MODE
#   - SET_RX_FILTER
#   - MDDP_FILTER_RULE

# 3. Envoyer la sequence:
#   a) Activer SwTestMode (via ioctl ou /proc)
#   b) Configurer Rx filter pour promiscuous
#   c) Activer SNIFFER
#   d) Lire les paquets via /dev/wmtWifi ou socket raw
```

**Avantages**: Pas de modification binaire, potentiellement reversible
**Risques**: Les commandes exactes sont inconnues, trial-and-error

### Vecteur 3: Patch du Firmware NDS32 (faisabilite basse — long terme)

**Principe**: Modifier le firmware `connsys_wifi_mt6878_mt6631` directement:
1. Desassembler avec Ghidra + NDS32 processor module
2. Trouver `testEngRxProcessPkt` et la verification `fgTestMode`
3. NOP le check ou forcer la branche "Send the packet to host"
4. Flasher sur la partition connsys_wifi

**Prerequis**:
- Ghidra avec support NDS32 (disponible nativement)
- Adresse de base du firmware (a determiner via analyse SETHI/ORI)
- Comprendre le format de section (potentiellement chiffre/obfusque)

**Patch cible** (pseudo-assembleur NDS32):
```
; Avant (dans testEngRxProcessPkt):
  lwi  $r0, [$gp + fgTestMode_offset]
  beqz $r0, drop_packet          ; if (!fgTestMode) goto drop
  ; ... process packet ...
drop_packet:
  ; "testEngRxProcessPkt not under test mode"

; Apres (patch):
  lwi  $r0, [$gp + fgTestMode_offset]
  nop                              ; NOP le branch conditionnel
  nop
  ; ... toujours process packet ...
```

**OU** modifier le policy check:
```
; Avant:
  ; ... policy evaluation ...
  bnez $r0, drop_policy_packet    ; if (policy_drop) goto drop
  ; "Send the packet to host"

; Apres:
  nop                              ; NOP — toujours send
  nop
  ; "Send the packet to host"     ; toujours execute
```

**Flashage**:
```bash
# Via fastboot (bootloader deverrouille)
fastboot flash connsys_wifi_a connsys_wifi_patched.img

# OU via dd en root (plus risque)
adb shell "dd if=/data/local/tmp/connsys_wifi_patched.img of=/dev/block/by-name/connsys_wifi_a"
```

---

## 7. Plan d'Execution Recommande

### Phase 1: Reconnaissance on-device (1-2 jours)

```bash
# Extraire le driver kernel
adb pull /vendor/lib/modules/wlan_drv_gen4m_6878.ko ./
adb pull /vendor/lib/modules/wmt_chrdev_wifi_connac2.ko ./

# Lister les interfaces proc/sys du driver
adb shell "ls -la /proc/driver/wlan*/"
adb shell "cat /proc/net/wireless"
adb shell "find /sys/module/wlan_drv_gen4m_6878/ -type f -exec echo {} \; -exec cat {} \;"

# Examiner les ioctl disponibles
adb shell "grep -r 'SNIFFER\|sniffer\|monitor\|MONITOR\|TEST_MODE\|RX_FILTER' /proc/driver/ 2>/dev/null"

# Chercher les commandes privees du driver
adb shell "iwpriv wlan0 2>/dev/null || echo 'iwpriv absent'"
adb shell "ls -la /dev/wmtWifi"

# Dump firmware depuis la partition (backup)
adb shell "dd if=/dev/block/by-name/connsys_wifi_a of=/data/local/tmp/connsys_wifi_backup.img"
adb pull /data/local/tmp/connsys_wifi_backup.img ./
```

### Phase 2: Analyse du driver kernel ARM64 (3-5 jours)

```
1. Charger wlan_drv_gen4m_6878.ko dans Ghidra (ARM64 / AARCH64)
2. Identifier les symboles:
   - priv_driver_handler / priv_cmd_set_sniffer
   - nicCmdSetMonitor / wlanoidSetSwCtrlWrite
   - kalIoctl / kal_ioctl_entry
3. Tracer le flux SNIFFER:
   ioctl → handler → cmd build → firmware send
4. Identifier les registres hardware accessibles depuis le driver:
   - WFDMA registers
   - Rx filter registers
   - MAC control registers
5. Concevoir le patch driver
```

### Phase 3: Implementation du patch (2-3 jours)

Selon le vecteur choisi apres la Phase 2.

### Phase 4: Integration dans wifi-cracker (1-2 jours)

Module Kotlin dans `core/wifi/firmware/`:
- `FirmwarePatcher.kt` — logique de patch
- `DriverPatcher.kt` — patch du module kernel
- `MonitorModeEnabler.kt` — orchestration
- `FirmwareBackup.kt` — backup/restore securise

---

## 8. Outils Necessaires

| Outil | Usage | Installation |
|-------|-------|-------------|
| Ghidra | Desassemblage NDS32 + ARM64 | Download ghidra-sre.org |
| radare2 | Analyse rapide du driver .ko | `apt install radare2` (installe) |
| binwalk | Extraction firmware | `apt install binwalk` (installe) |
| capstone | Desassemblage Python | `pip install capstone` (installe) |
| adb/fastboot | Communication device | Android SDK |
| extract_fw.py | Extraction firmware MTK | mediatek-wifi-re repo (clone) |

---

## 9. Risques et Mitigations

| Risque | Impact | Mitigation |
|--------|--------|------------|
| Brick WiFi | WiFi inutilisable | Backup partition + partition B (A/B scheme) |
| Kernel panic | Reboot device | Module unload avant test, recovery mode |
| Firmware chiffre | Impossible a patcher | Vecteur 1 (driver) ne necessite pas de dechiffrement |
| Regression scan/data | WiFi fonctionnel casse | Tests incrementaux, rollback automatique |
| Verification signature | Firmware refuse | Bootloader deverrouille = pas de verif |

---

## Sources

- [cyrozap/mediatek-wifi-re](https://github.com/cyrozap/mediatek-wifi-re) — RE toolkit MediaTek WiFi
- [kagaimiq/mediatek-connsys](https://github.com/kagaimiq/mediatek-connsys) — CONNSYS subsystem docs
- [MT6631 Datasheet](https://www.scribd.com/document/805410071/MT6631-Datasheet) — Overview chip WiFi
- [XDA Forums MT6628 monitor mode](https://xdaforums.com/t/mt6628-monitor-mode.2804776/) — Tentatives anterieures
