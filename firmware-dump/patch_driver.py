#!/usr/bin/env python3
"""
MediaTek WiFi Driver Patcher — wlan_drv_gen4m_6878.ko
Injects a call to nicRxProcessIcsLog in the RX path to enable ICS packet capture.

Strategy: Patch nicRxProcessPktWithoutReorder to call nicRxProcessIcsLog
before processing each packet. This enables /dev/fw_log_ics to output
captured WiFi frames when ICS sniffer is enabled.

The patch works by:
1. Finding the stub function nicRxEnablePromiscuousMode (currently just 'ret')
2. Replacing it + nearby stubs with trampoline code
3. Redirecting the first instruction of nicRxProcessPktWithoutReorder
   to branch to our trampoline
4. The trampoline checks ICS enable flag, calls nicRxProcessIcsLog if set,
   then continues the original function
"""

import struct
import sys
import shutil
import hashlib
from pathlib import Path

# Symbol offsets (from nm output) — these are .text-relative offsets
SYMBOLS = {
    'nicRxProcessPktWithoutReorder': 0x5b13c,
    'nicRxProcessIcsLog':           0x5ddb8,
    'nicRxEnablePromiscuousMode':   0x5f514,
    'nicRxDisablePromiscuousMode':  0x5f51c,
    'wlanSetPromiscuousMode':       0x1fbc4,
    'wlanRxSetBroadcast':           0x1fbcc,
}

# ICS enable flag offset in adapter structure
ICS_FLAG_OFFSET = 0x2a86b0


def read_elf_sections(data):
    """Parse ELF64 section headers."""
    e_shoff = struct.unpack_from('<Q', data, 40)[0]
    e_shentsize = struct.unpack_from('<H', data, 58)[0]
    e_shnum = struct.unpack_from('<H', data, 60)[0]
    e_shstrndx = struct.unpack_from('<H', data, 62)[0]

    sections = []
    for i in range(e_shnum):
        off = e_shoff + i * e_shentsize
        shdr = data[off:off+e_shentsize]
        sh = {
            'name_idx': struct.unpack_from('<I', shdr, 0)[0],
            'type': struct.unpack_from('<I', shdr, 4)[0],
            'flags': struct.unpack_from('<Q', shdr, 8)[0],
            'addr': struct.unpack_from('<Q', shdr, 16)[0],
            'offset': struct.unpack_from('<Q', shdr, 24)[0],
            'size': struct.unpack_from('<Q', shdr, 32)[0],
            'hdr_offset': off,
        }
        sections.append(sh)

    return sections


def find_text_section(data, sections):
    """Find .text section (executable PROGBITS)."""
    for s in sections:
        if s['type'] == 1 and (s['flags'] & 4) and s['size'] > 100000:
            return s
    return None


def encode_bl(from_off, to_off):
    """Encode ARM64 BL instruction (branch and link)."""
    offset = (to_off - from_off) >> 2
    if offset < -(1 << 25) or offset >= (1 << 25):
        raise ValueError(f"BL offset out of range: {offset}")
    imm26 = offset & 0x3FFFFFF
    return struct.pack('<I', 0x94000000 | imm26)


def encode_b(from_off, to_off):
    """Encode ARM64 B instruction (unconditional branch)."""
    offset = (to_off - from_off) >> 2
    if offset < -(1 << 25) or offset >= (1 << 25):
        raise ValueError(f"B offset out of range: {offset}")
    imm26 = offset & 0x3FFFFFF
    return struct.pack('<I', 0x14000000 | imm26)


def build_trampoline(tramp_off, target_func_off, ics_log_off, orig_func_off):
    """
    Build the trampoline ARM64 machine code.

    tramp_off: file offset of trampoline
    target_func_off: file offset of nicRxProcessPktWithoutReorder (original)
    ics_log_off: file offset of nicRxProcessIcsLog
    orig_func_off: file offset to return to (target_func_off + 4)

    Register state at entry:
      x0 = adapter (param_1)
      x1 = rxb/packet (param_2)
      x30 = return address (caller of nicRxProcessPktWithoutReorder)
    """
    code = bytearray()
    pc = tramp_off

    # stp x29, x30, [sp, #-48]!   — save frame + LR + extra space
    code += struct.pack('<I', 0xa9bd7bfd)
    pc += 4

    # stp x0, x1, [sp, #16]       — save adapter and rxb
    code += struct.pack('<I', 0xa90107e0)
    pc += 4

    # stp x2, x3, [sp, #32]       — save scratch regs
    code += struct.pack('<I', 0xa9020fe2)
    pc += 4

    # Check ICS flag: adapter + 0x2a86b0
    # movz x2, #0x86b0
    code += struct.pack('<I', 0xd2900d62)
    pc += 4

    # movk x2, #0x2a, lsl #16
    code += struct.pack('<I', 0xf2a00542)
    pc += 4

    # ldrb w2, [x0, x2]
    code += struct.pack('<I', 0x38626802)
    pc += 4

    # cbz w2, skip_ics  (skip 2 instructions = +8 bytes)
    code += struct.pack('<I', 0x34000042)  # cbz w2, pc+8
    pc += 4

    # bl nicRxProcessIcsLog  — x0=adapter, x1=rxb already set
    code += encode_bl(pc, ics_log_off)
    pc += 4

    # skip_ics:
    # ldp x2, x3, [sp, #32]       — restore scratch
    code += struct.pack('<I', 0xa9420fe2)
    pc += 4

    # ldp x0, x1, [sp, #16]       — restore adapter and rxb
    code += struct.pack('<I', 0xa94107e0)
    pc += 4

    # ldp x29, x30, [sp], #48     — restore frame + LR
    code += struct.pack('<I', 0xa8c37bfd)
    pc += 4

    # Execute original first instruction (paciasp = 0xd503233f)
    code += struct.pack('<I', 0xd503233f)
    pc += 4

    # b nicRxProcessPktWithoutReorder + 4   — continue original function
    code += encode_b(pc, orig_func_off)
    pc += 4

    return bytes(code)


def patch_driver(input_path, output_path):
    """Apply the monitor mode patch to the WiFi driver."""

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
    print(f"[*] .text section: offset=0x{text_off:x}, size=0x{text_size:x}")

    # Calculate file offsets for our symbols
    sym_file_offsets = {}
    for name, text_rel in SYMBOLS.items():
        file_off = text_off + text_rel
        sym_file_offsets[name] = file_off
        # Verify the symbol location makes sense
        if name == 'nicRxEnablePromiscuousMode':
            insn = struct.unpack_from('<I', data, file_off)[0]
            if insn != 0xd65f03c0:  # ret
                print(f"[!] {name} @ 0x{file_off:x} is not 'ret' (got 0x{insn:08x})")
                print("[!] Symbol offsets may be wrong. Aborting.")
                return False
            print(f"[+] Verified {name} @ 0x{file_off:x} = ret (stub)")

    # Verify other stubs
    for name in ['nicRxDisablePromiscuousMode', 'wlanSetPromiscuousMode', 'wlanRxSetBroadcast']:
        file_off = sym_file_offsets[name]
        insn = struct.unpack_from('<I', data, file_off)[0]
        if insn == 0xd65f03c0:
            print(f"[+] Verified {name} @ 0x{file_off:x} = ret (stub)")

    # Verify nicRxProcessPktWithoutReorder starts with paciasp
    target_off = sym_file_offsets['nicRxProcessPktWithoutReorder']
    insn = struct.unpack_from('<I', data, target_off)[0]
    if insn != 0xd503233f:  # paciasp
        print(f"[!] nicRxProcessPktWithoutReorder @ 0x{target_off:x} doesn't start with paciasp (got 0x{insn:08x})")
        print("[!] Cannot safely patch. Aborting.")
        return False
    print(f"[+] Verified nicRxProcessPktWithoutReorder @ 0x{target_off:x} = paciasp")

    # === TRAMPOLINE LOCATION ===
    # We'll place the trampoline at nicRxEnablePromiscuousMode (stub)
    # and use the space from there through nicRxDisablePromiscuousMode + a few more bytes
    # Available: 0x5f514 to ~0x5f524 = 16 bytes (not enough for our 52-byte trampoline)
    #
    # Better: append to .text section
    # We extend .text by adding our trampoline at the end

    tramp_text_rel = text_size  # place at end of .text
    tramp_file_off = text_off + tramp_text_rel

    # Build the trampoline
    tramp_code = build_trampoline(
        tramp_off=tramp_file_off,
        target_func_off=target_off,
        ics_log_off=sym_file_offsets['nicRxProcessIcsLog'],
        orig_func_off=target_off + 4,  # skip patched instruction
    )

    print(f"[*] Trampoline: {len(tramp_code)} bytes at file offset 0x{tramp_file_off:x}")
    print(f"    (.text + 0x{tramp_text_rel:x})")

    # === APPLY PATCHES ===

    # 1. Patch nicRxProcessPktWithoutReorder: replace paciasp with B trampoline
    print(f"\n[*] Patch 1: Redirect nicRxProcessPktWithoutReorder entry to trampoline")
    b_insn = encode_b(target_off, tramp_file_off)
    data[target_off:target_off+4] = b_insn
    print(f"    0x{target_off:x}: paciasp -> B 0x{tramp_file_off:x}")

    # 2. Insert trampoline code at end of .text
    print(f"[*] Patch 2: Write trampoline at 0x{tramp_file_off:x}")
    # Ensure we have space (might need to extend the file)
    if tramp_file_off + len(tramp_code) > len(data):
        # Need to extend the file
        extend_by = (tramp_file_off + len(tramp_code)) - len(data)
        data.extend(b'\x00' * extend_by)
        print(f"    Extended file by {extend_by} bytes")

    data[tramp_file_off:tramp_file_off+len(tramp_code)] = tramp_code

    # 3. Update .text section size in ELF header
    new_text_size = text_size + len(tramp_code)
    # Align to 16 bytes
    new_text_size = (new_text_size + 15) & ~15
    struct.pack_into('<Q', data, text_sec['hdr_offset'] + 32, new_text_size)
    print(f"    Updated .text size: 0x{text_size:x} -> 0x{new_text_size:x}")

    # === WRITE OUTPUT ===
    print(f"\n[*] Writing patched driver to {output_path}")
    with open(output_path, 'wb') as f:
        f.write(data)

    patched_hash = hashlib.sha256(data).hexdigest()
    print(f"[*] Patched SHA256: {patched_hash}")

    # Print summary
    print(f"\n{'='*60}")
    print("PATCH SUMMARY")
    print(f"{'='*60}")
    print(f"Original: {input_path}")
    print(f"Patched:  {output_path}")
    print(f"Changes:")
    print(f"  1. nicRxProcessPktWithoutReorder entry: B -> trampoline")
    print(f"  2. Trampoline at .text+0x{tramp_text_rel:x}:")
    print(f"     - Checks ICS flag (adapter+0x{ICS_FLAG_OFFSET:x})")
    print(f"     - If set: calls nicRxProcessIcsLog(adapter, rxb)")
    print(f"     - Executes original paciasp")
    print(f"     - Returns to original function flow")
    print(f"  3. .text section extended by {len(tramp_code)} bytes")
    print(f"\nTo use:")
    print(f"  1. Push to device: adb push {output_path} /data/local/tmp/")
    print(f"  2. Enable ICS: /data/local/tmp/wpa_driver 'SNIFFER 2 0 0 0 0 0 0 0 0 0'")
    print(f"  3. Enable ICS device: ioctl 0x4004FC01 level=2, ioctl 0x4004FC00 enable=1")
    print(f"  4. Reload module:")
    print(f"     rmmod wlan_drv_gen4m_6878")
    print(f"     insmod /data/local/tmp/wlan_drv_gen4m_6878_patched.ko")
    print(f"  5. Read captures: cat /dev/fw_log_ics | xxd")

    return True


if __name__ == '__main__':
    input_ko = '/home/rony/Projets/pentest-app/firmware-dump/wlan_drv_gen4m_6878.ko.ORIGINAL'
    output_ko = '/home/rony/Projets/pentest-app/firmware-dump/wlan_drv_gen4m_6878_patched.ko'

    if not Path(input_ko).exists():
        print(f"[!] Input file not found: {input_ko}")
        sys.exit(1)

    if patch_driver(input_ko, output_ko):
        print("\n[+] PATCH SUCCESSFUL")
    else:
        print("\n[!] PATCH FAILED")
        sys.exit(1)
