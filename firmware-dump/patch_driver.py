#!/usr/bin/env python3
"""
MediaTek WiFi Driver Patcher v2 — wlan_drv_gen4m_6878.ko

Combined patch:
  1. ICS Capture: Injects nicRxProcessIcsLog call in the RX path
     → enables /dev/fw_log_ics packet capture
  2. Promiscuous RX: Replaces nicRxEnablePromiscuousMode stub with real code
     that sends firmware cmd 10 (SET_PACKET_FILTER) with value 0x0F
     → firmware forwards ALL frames (unicast, multicast, broadcast)
     → enables EAPOL / 4-way handshake capture from other devices
"""

import struct
import sys
import hashlib
from pathlib import Path

SYMBOLS = {
    'nicRxProcessPktWithoutReorder': 0x5b13c,
    'nicRxProcessIcsLog':           0x5ddb8,
    'nicRxEnablePromiscuousMode':   0x5f514,
    'nicRxDisablePromiscuousMode':  0x5f51c,
    'wlanSetPromiscuousMode':       0x1fbc4,
    'wlanoidSetPacketFilter':       0x3c274,
    'nicCmdEventSetCommon':         None,  # resolved from wlanoidSetCurrentPacketFilter
    'nicOidCmdTimeoutCommon':       None,
    'wlanSendSetQueryCmd':          0x62794,
    'priv_driver_set_ap_sta_disassoc': 0xb0a28,
    'ap_sta_disassoc_role_check':  0xb0a98,  # cbnz w0, skip — the AP role check to NOP
}

ICS_FLAG_OFFSET = 0x2a86b0
PACKET_FILTER_OFFSET = 0x10  # adapter + 0x10 stores current packet filter
PROMISCUOUS_FILTER = 0x0F    # bits 0-3: accept all frame types
NOP_ARM64 = 0xd503201f      # ARM64 NOP instruction


def read_elf_sections(data):
    e_shoff = struct.unpack_from('<Q', data, 40)[0]
    e_shentsize = struct.unpack_from('<H', data, 58)[0]
    e_shnum = struct.unpack_from('<H', data, 60)[0]
    sections = []
    for i in range(e_shnum):
        off = e_shoff + i * e_shentsize
        shdr = data[off:off+e_shentsize]
        sections.append({
            'name_idx': struct.unpack_from('<I', shdr, 0)[0],
            'type': struct.unpack_from('<I', shdr, 4)[0],
            'flags': struct.unpack_from('<Q', shdr, 8)[0],
            'addr': struct.unpack_from('<Q', shdr, 16)[0],
            'offset': struct.unpack_from('<Q', shdr, 24)[0],
            'size': struct.unpack_from('<Q', shdr, 32)[0],
            'hdr_offset': off,
        })
    return sections


def find_text_section(data, sections):
    for s in sections:
        if s['type'] == 1 and (s['flags'] & 4) and s['size'] > 100000:
            return s
    return None


def encode_bl(from_off, to_off):
    offset = (to_off - from_off) >> 2
    if offset < -(1 << 25) or offset >= (1 << 25):
        raise ValueError(f"BL offset out of range: {offset}")
    return struct.pack('<I', 0x94000000 | (offset & 0x3FFFFFF))


def encode_b(from_off, to_off):
    offset = (to_off - from_off) >> 2
    if offset < -(1 << 25) or offset >= (1 << 25):
        raise ValueError(f"B offset out of range: {offset}")
    return struct.pack('<I', 0x14000000 | (offset & 0x3FFFFFF))


def build_ics_trampoline(tramp_off, ics_log_off, return_off):
    """
    Trampoline 1: ICS packet logging in the RX path.
    Called at entry of nicRxProcessPktWithoutReorder.
    x0 = adapter, x1 = rxb (packet buffer)
    """
    code = bytearray()
    pc = tramp_off

    # stp x29, x30, [sp, #-48]!
    code += struct.pack('<I', 0xa9bd7bfd); pc += 4
    # stp x0, x1, [sp, #16]
    code += struct.pack('<I', 0xa90107e0); pc += 4
    # stp x2, x3, [sp, #32]
    code += struct.pack('<I', 0xa9020fe2); pc += 4

    # movz x2, #0x86b0 (low part of ICS_FLAG_OFFSET)
    code += struct.pack('<I', 0xd2900d62); pc += 4
    # movk x2, #0x2a, lsl #16
    code += struct.pack('<I', 0xf2a00542); pc += 4
    # ldrb w2, [x0, x2]
    code += struct.pack('<I', 0x38626802); pc += 4
    # cbz w2, skip (skip 1 instruction = +8)
    code += struct.pack('<I', 0x34000042); pc += 4
    # bl nicRxProcessIcsLog
    code += encode_bl(pc, ics_log_off); pc += 4

    # skip:
    # ldp x2, x3, [sp, #32]
    code += struct.pack('<I', 0xa9420fe2); pc += 4
    # ldp x0, x1, [sp, #16]
    code += struct.pack('<I', 0xa94107e0); pc += 4
    # ldp x29, x30, [sp], #48
    code += struct.pack('<I', 0xa8c37bfd); pc += 4
    # paciasp (original first instruction)
    code += struct.pack('<I', 0xd503233f); pc += 4
    # b nicRxProcessPktWithoutReorder + 4
    code += encode_b(pc, return_off); pc += 4

    return bytes(code)


def build_promiscuous_trampoline(tramp_off, set_pkt_filter_off):
    """
    Trampoline 2: Enables promiscuous mode by calling
    wlanoidSetPacketFilter(adapter, &filter_val, 4, NULL, 0)
    with filter = 0x0F (accept all).

    Replaces nicRxEnablePromiscuousMode stub.
    x0 = adapter (first param from caller)
    """
    code = bytearray()
    pc = tramp_off

    # stp x29, x30, [sp, #-32]!
    code += struct.pack('<I', 0xa9be7bfd); pc += 4
    # mov x29, sp
    code += struct.pack('<I', 0x910003fd); pc += 4

    # Store filter value 0x0F on stack at [sp, #16]
    # mov w8, #0x0F
    code += struct.pack('<I', 0x528001e8); pc += 4
    # str w8, [sp, #16]
    code += struct.pack('<I', 0xb90013e8); pc += 4

    # Also store 0x0F in adapter->packet_filter (adapter + 0x10)
    # str w8, [x0, #0x10]
    code += struct.pack('<I', 0xb90013e8 & 0 | 0xb9001008); pc += 4

    # Prepare args for wlanoidSetPacketFilter(adapter, &filter, 4, NULL, 0)
    # x0 = adapter (already set)
    # x1 = &filter_value = sp + 16
    # add x1, sp, #16
    code += struct.pack('<I', 0x910043e1); pc += 4
    # w2 = 4 (size of filter value)
    # mov w2, #4
    code += struct.pack('<I', 0x52800082); pc += 4
    # x3 = NULL
    # mov x3, xzr
    code += struct.pack('<I', 0xaa1f03e3); pc += 4
    # w4 = 0
    # mov w4, wzr
    code += struct.pack('<I', 0x2a1f03e4); pc += 4

    # bl wlanoidSetPacketFilter
    code += encode_bl(pc, set_pkt_filter_off); pc += 4

    # ldp x29, x30, [sp], #32
    code += struct.pack('<I', 0xa8c27bfd); pc += 4
    # ret
    code += struct.pack('<I', 0xd65f03c0); pc += 4

    return bytes(code)


def build_disable_promiscuous(tramp_off, set_pkt_filter_off):
    """
    Trampoline 3: Disables promiscuous mode (restores filter to 0x01 = unicast only).
    Replaces nicRxDisablePromiscuousMode stub.
    """
    code = bytearray()
    pc = tramp_off

    # stp x29, x30, [sp, #-32]!
    code += struct.pack('<I', 0xa9be7bfd); pc += 4
    # mov x29, sp
    code += struct.pack('<I', 0x910003fd); pc += 4

    # Store filter value 0x01 on stack
    # mov w8, #0x01
    code += struct.pack('<I', 0x52800028); pc += 4
    # str w8, [sp, #16]
    code += struct.pack('<I', 0xb90013e8); pc += 4
    # str w8, [x0, #0x10]
    code += struct.pack('<I', 0xb9001008); pc += 4

    # wlanoidSetPacketFilter(adapter, &filter, 4, NULL, 0)
    # add x1, sp, #16
    code += struct.pack('<I', 0x910043e1); pc += 4
    # mov w2, #4
    code += struct.pack('<I', 0x52800082); pc += 4
    # mov x3, xzr
    code += struct.pack('<I', 0xaa1f03e3); pc += 4
    # mov w4, wzr
    code += struct.pack('<I', 0x2a1f03e4); pc += 4
    # bl wlanoidSetPacketFilter
    code += encode_bl(pc, set_pkt_filter_off); pc += 4

    # ldp x29, x30, [sp], #32
    code += struct.pack('<I', 0xa8c27bfd); pc += 4
    # ret
    code += struct.pack('<I', 0xd65f03c0); pc += 4

    return bytes(code)


def verify_stub(data, offset, name):
    insn = struct.unpack_from('<I', data, offset)[0]
    if insn == 0xd65f03c0:
        print(f"  [+] {name} @ 0x{offset:x} = ret (stub)")
        return True
    print(f"  [!] {name} @ 0x{offset:x} = 0x{insn:08x} (NOT a stub)")
    return False


def patch_driver(input_path, output_path):
    print(f"[*] Reading {input_path}")
    with open(input_path, 'rb') as f:
        data = bytearray(f.read())

    orig_hash = hashlib.sha256(data).hexdigest()
    print(f"[*] Original SHA256: {orig_hash}")

    sections = read_elf_sections(data)
    text_sec = find_text_section(data, sections)
    if not text_sec:
        print("[!] Cannot find .text section")
        return False

    text_off = text_sec['offset']
    text_size = text_sec['size']
    print(f"[*] .text: offset=0x{text_off:x}, size=0x{text_size:x}")

    # Compute file offsets
    def sym_off(name):
        return text_off + SYMBOLS[name]

    # Verify stubs
    print("[*] Verifying stubs...")
    if not verify_stub(data, sym_off('nicRxEnablePromiscuousMode'), 'nicRxEnablePromiscuousMode'):
        return False
    verify_stub(data, sym_off('nicRxDisablePromiscuousMode'), 'nicRxDisablePromiscuousMode')

    # Verify RX entry
    target_off = sym_off('nicRxProcessPktWithoutReorder')
    insn = struct.unpack_from('<I', data, target_off)[0]
    if insn != 0xd503233f:
        print(f"[!] nicRxProcessPktWithoutReorder @ 0x{target_off:x} not paciasp (0x{insn:08x})")
        return False
    print(f"  [+] nicRxProcessPktWithoutReorder @ 0x{target_off:x} = paciasp")

    # === BUILD ALL TRAMPOLINES ===
    cursor = text_off + text_size  # append after .text

    # Trampoline 1: ICS capture in RX path
    t1_off = cursor
    t1_code = build_ics_trampoline(t1_off, sym_off('nicRxProcessIcsLog'), target_off + 4)
    cursor += len(t1_code)
    # Align to 4
    while cursor % 4:
        cursor += 1

    # Trampoline 2: promiscuous enable
    t2_off = cursor
    t2_code = build_promiscuous_trampoline(t2_off, sym_off('wlanoidSetPacketFilter'))
    cursor += len(t2_code)
    while cursor % 4:
        cursor += 1

    # Trampoline 3: promiscuous disable
    t3_off = cursor
    t3_code = build_disable_promiscuous(t3_off, sym_off('wlanoidSetPacketFilter'))
    cursor += len(t3_code)

    total_patch_size = cursor - (text_off + text_size)
    print(f"\n[*] Trampolines built: {total_patch_size} bytes total")
    print(f"    T1 (ICS capture):        {len(t1_code):3d} bytes @ 0x{t1_off:x}")
    print(f"    T2 (promiscuous enable): {len(t2_code):3d} bytes @ 0x{t2_off:x}")
    print(f"    T3 (promiscuous disable):{len(t3_code):3d} bytes @ 0x{t3_off:x}")

    # === APPLY PATCHES ===
    print(f"\n[*] Applying patches...")

    # Extend file if needed
    if cursor > len(data):
        data.extend(b'\x00' * (cursor - len(data)))

    # Patch 1: RX path → T1
    data[target_off:target_off+4] = encode_b(target_off, t1_off)
    print(f"  [1] nicRxProcessPktWithoutReorder: B → T1 (ICS capture)")

    # Write T1
    data[t1_off:t1_off+len(t1_code)] = t1_code

    # Patch 2: nicRxEnablePromiscuousMode → T2
    enable_off = sym_off('nicRxEnablePromiscuousMode')
    data[enable_off:enable_off+4] = encode_b(enable_off, t2_off)
    print(f"  [2] nicRxEnablePromiscuousMode: B → T2 (promiscuous 0x0F)")

    # Write T2
    data[t2_off:t2_off+len(t2_code)] = t2_code

    # Patch 3: nicRxDisablePromiscuousMode → T3
    disable_off = sym_off('nicRxDisablePromiscuousMode')
    data[disable_off:disable_off+4] = encode_b(disable_off, t3_off)
    print(f"  [3] nicRxDisablePromiscuousMode: B → T3 (filter restore 0x01)")

    # Write T3
    data[t3_off:t3_off+len(t3_code)] = t3_code

    # Patch 4: NOP the AP role check in priv_driver_set_ap_sta_disassoc
    # This allows "AP_STA_DISASSOC Mac=<target>" to work in STA mode for deauth injection
    role_check_off = sym_off('ap_sta_disassoc_role_check')
    orig_insn = struct.unpack_from('<I', data, role_check_off)[0]
    if (orig_insn & 0xFF000000) == 0x35000000:  # cbnz
        data[role_check_off:role_check_off+4] = struct.pack('<I', NOP_ARM64)
        print(f"  [4] AP_STA_DISASSOC role check: cbnz → NOP (deauth in STA mode)")
    else:
        print(f"  [!] AP_STA_DISASSOC role check @ 0x{role_check_off:x}: unexpected insn 0x{orig_insn:08x}, skipping")

    # Update .text section size
    new_text_size = (text_size + total_patch_size + 15) & ~15
    struct.pack_into('<Q', data, text_sec['hdr_offset'] + 32, new_text_size)
    print(f"  [5] .text size: 0x{text_size:x} → 0x{new_text_size:x}")

    # Write output
    print(f"\n[*] Writing {output_path}")
    with open(output_path, 'wb') as f:
        f.write(data)

    patched_hash = hashlib.sha256(data).hexdigest()

    print(f"\n{'='*60}")
    print("PATCH v3 SUMMARY")
    print(f"{'='*60}")
    print(f"SHA256 original: {orig_hash}")
    print(f"SHA256 patched:  {patched_hash}")
    print(f"\nPatches applied:")
    print(f"  1. RX path → ICS logging     (capture all received frames)")
    print(f"  2. Enable promiscuous mode    (firmware cmd 10, filter=0x0F)")
    print(f"  3. Disable promiscuous mode   (firmware cmd 10, filter=0x01)")
    print(f"  4. AP_STA_DISASSOC role check → NOP (deauth injection in STA mode)")
    print(f"\nCapabilities:")
    print(f"  ✓ Beacons, Probe Req/Resp     (scan)")
    print(f"  ✓ Data, QoS-Data              (traffic)")
    print(f"  ✓ RTS, CTS, ACK               (control)")
    print(f"  ✓ EAPOL / 4-way handshake     (WPA cracking)")
    print(f"  ✓ Deauth injection            (AP_STA_DISASSOC in STA mode)")
    print(f"  ✓ Frames d'AUTRES devices     (mode promiscuous)")
    print(f"\nDeauth usage:")
    print(f"  wpa_driver 'AP_STA_DISASSOC Mac=AA:BB:CC:DD:EE:FF'")

    return True


if __name__ == '__main__':
    input_ko = '/home/rony/Projets/pentest-app/firmware-dump/wlan_drv_gen4m_6878.ko.ORIGINAL'
    output_ko = '/home/rony/Projets/pentest-app/firmware-dump/wlan_drv_gen4m_6878_patched.ko'

    if not Path(input_ko).exists():
        print(f"[!] Input not found: {input_ko}")
        sys.exit(1)

    if patch_driver(input_ko, output_ko):
        print("\n[+] PATCH v2 SUCCESSFUL")
    else:
        print("\n[!] PATCH FAILED")
        sys.exit(1)
