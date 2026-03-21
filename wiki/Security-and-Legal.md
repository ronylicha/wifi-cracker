# Security and Legal

---

## Legal Disclaimer

WiFi Cracker is designed **exclusively for authorized security testing**. Unauthorized access to computer networks is a criminal offense in most jurisdictions worldwide, including but not limited to:

- **European Union**: Directive 2013/40/EU on attacks against information systems; national implementations (e.g., French Loi Godfrain, German §202a StGB, UK Computer Misuse Act)
- **United States**: Computer Fraud and Abuse Act (18 U.S.C. § 1030)
- **Canada**: Criminal Code sections 342.1 and 430
- **Australia**: Criminal Code Act 1995 Part 10.7

By installing and using WiFi Cracker, you confirm that:

1. You have **explicit written authorization** from the network owner to test the target networks.
2. You are conducting tests as part of a **legitimate security assessment, penetration test, or academic research**.
3. You will not use this application to access, intercept, or disrupt networks, systems, or communications that you are not authorized to test.
4. You accept **full legal responsibility** for your actions and any consequences thereof.

**The developers of WiFi Cracker assume no liability for any misuse of this tool. Use at your own risk.**

---

## Responsible Use Guidelines

### Before Starting an Assessment

- Obtain **written authorization** (signed scope of work or rules of engagement document) from the network owner before testing.
- Define a **clear scope** — list the specific BSSIDs and locations in scope. Do not test networks outside the agreed scope, even if they are visible.
- Establish a **point of contact** at the client who can confirm authorization if questioned.
- Agree on **testing windows** to minimize disruption to production environments.
- Notify stakeholders of potential service interruptions, particularly when using deauthentication attacks.

### During Testing

- Conduct tests only during the authorized window and within the authorized scope.
- Keep all captured data (`.cap` files, recovered passwords, scan results) **confidential**. Do not share with unauthorized parties.
- If you accidentally access data outside the authorized scope, stop immediately and report it to the client.
- Do not retain credentials or sensitive data beyond the duration of the engagement.

### After Testing

- Securely delete all captured data once the report has been delivered and the engagement is closed.
- Do not reuse test findings for any purpose other than the authorized assessment.
- Report all findings to the client. Do not withhold vulnerabilities or use them for personal gain.

---

## Audit Logging

WiFi Cracker maintains an immutable, append-only audit log for every significant action. Logging is implemented in `AuditLogger` using JSON Lines format (`audit.jsonl`).

### What is Logged

| Event | Module | Details |
|-------|--------|---------|
| `SCAN_START` | scan | Interface name |
| `SCAN_STOP` | scan | Interface name, number of networks found |
| `SCAN_ERROR` | scan | Error message |
| `ICS_ENABLE_FAILED` | scan | Failure reason |
| `ATTACK_START` | attack | Attack type, target BSSID |
| `ATTACK_STOP` | attack | Target BSSID |
| `CRACK_START` | crack | Strategy, target BSSID |
| `CRACK_DONE` | crack | Target BSSID, result (FOUND / NOT_FOUND) |
| `CRACK_STOP` | crack | — |

### Log Format

Each entry is a single-line JSON object:

```json
{"timestamp":1742553600000,"action":"CRACK_DONE","module":"crack","target":"aa:bb:cc:dd:ee:ff","result":"FOUND","details":""}
```

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | Long (epoch ms) | UTC timestamp of the event |
| `action` | String | Event type (see table above) |
| `module` | String | Module that generated the event |
| `target` | String | Target identifier (BSSID, interface, etc.) |
| `result` | String | Outcome (empty if not applicable) |
| `details` | String | Additional context or error message |

### Accessing the Audit Log

Navigate to **Drawer > Audit Log** in the application. The log viewer displays entries in chronological order.

**Export:** Tap the export icon to save the complete log as a formatted JSON file to the device's Documents folder.

**Purge:** Tap the purge icon to permanently delete all log entries. This action cannot be undone. Only purge after an engagement is fully closed and the audit log has been exported.

### Log File Location

The log file is stored at:
```
/data/data/com.wificracker.app/files/audit/audit.jsonl
```

It is private to the application and not accessible to other apps without root.

---

## Data Handling and GDPR Compliance

WiFi Cracker collects and processes data only on the local device. No data is transmitted to external servers by the application itself.

### Data Categories

| Data Type | Where Stored | Retention |
|-----------|-------------|-----------|
| Scan results (SSIDs, BSSIDs, signal strength) | In memory during session; Room DB if saved | Until manually deleted |
| Captured packet files (`.cap`, `.pcapng`, `.hc22000`) | `/data/local/tmp/wificracker/captures/` | Until manually deleted |
| Recovered passwords | Audit log + session data in Room DB | Until purged |
| Company and client profiles | Room DB | Until manually deleted |
| Audit log | `audit.jsonl` file | Until purged |
| MAC vendor lookup | Bundled OUI database (read-only) | Not user data |
| CVE database | Bundled `vulns.json` (read-only) | Not user data |

### Personal Data Considerations

WiFi scanning may capture data that qualifies as personal data under GDPR and similar regulations:

- **MAC addresses** of client devices can be linked to individuals.
- **SSIDs** may reveal information about individuals or organizations.
- **Captured packets** may contain personal communications.

**Obligations under GDPR Article 5 (principles):**
- **Purpose limitation**: Use captured data only for the authorized security assessment.
- **Data minimization**: Capture only what is necessary for the assessment scope.
- **Storage limitation**: Delete captured data once the assessment is complete and the report delivered.
- **Integrity and confidentiality**: Store data securely, restrict access to authorized testers only.

**If you are conducting a security assessment for a client that is subject to GDPR**, ensure that your engagement contract includes appropriate data processing terms and that data handling during the assessment is covered in your Data Processing Agreement (DPA).

### Data Stored on Device

All data is stored under the application's private data directory and in `/data/local/tmp/wificracker/`. Neither location is accessible to third-party applications without root.

**To remove all data after an engagement:**

```bash
# Remove captured files
adb shell "su -c 'rm -rf /data/local/tmp/wificracker/captures/'"

# Remove the application and all its data
adb uninstall com.wificracker.app

# Optionally remove working directory residue
adb shell "su -c 'rm -rf /data/local/tmp/wificracker/'"
```

The application also provides a purge function for the audit log under **Drawer > Audit Log > Purge**.

---

## Network Permissions

WiFi Cracker requires the following Android permissions:

| Permission | Purpose |
|------------|---------|
| `ACCESS_WIFI_STATE` | Read WiFi interface state |
| `CHANGE_WIFI_STATE` | Modify WiFi settings |
| `ACCESS_FINE_LOCATION` | Required by Android for WiFi scanning (not used for location tracking) |
| `NEARBY_WIFI_DEVICES` | Required on Android 13+ for WiFi scanning |
| `FOREGROUND_SERVICE` | Keep the app running during long operations |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Foreground service type for USB OTG |
| `INTERNET` | Download tool binaries (Modules installer) |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | Read wordlists; write reports and captures |

The `ACCESS_FINE_LOCATION` permission is required by the Android platform for any application that scans for WiFi networks. WiFi Cracker does not use location data for any purpose other than enabling the system WiFi scan API.

---

## Responsible Disclosure

If you discover a vulnerability in WiFi Cracker itself, please report it responsibly:

1. Do not disclose the vulnerability publicly until a fix has been released.
2. Open a private security advisory on the GitHub repository, or contact the maintainers directly.
3. Provide a clear description, reproduction steps, and potential impact.
4. Allow reasonable time (90 days) for a fix before public disclosure.
