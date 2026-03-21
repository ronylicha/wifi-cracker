package com.wificracker.attack.model

enum class AttackType(val label: String, val description: String) {
    AUTO_ATTACK("Auto Attack", "Chain all techniques automatically until password is found"),
    DEAUTH("Deauthentication", "Force client disconnection to capture handshake"),
    HANDSHAKE_CAPTURE("Handshake Capture", "Capture WPA/WPA2 4-way handshake"),
    PMKID_CAPTURE("PMKID Capture", "Capture PMKID without connected clients"),
    EVIL_TWIN("Evil Twin", "Create fake access point to intercept traffic"),
    PROBE_SNIFF("Probe Sniffing", "Capture probe requests to identify remembered networks"),
}
