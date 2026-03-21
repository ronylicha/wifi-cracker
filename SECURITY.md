# Security Policy

## Overview

WiFi Cracker is an open-source security audit tool designed exclusively for authorized penetration testing.
We take the security of this project seriously and welcome responsible disclosure of vulnerabilities
in the tool itself.

---

## Scope

Because WiFi Cracker is a penetration testing tool, please read the scope carefully before submitting
a report.

### In Scope

The following categories constitute valid security reports:

| Category | Examples |
|----------|---------|
| Code execution in the app's own process | RCE via malformed scan results, malicious `.cap` file parsing |
| Privilege escalation beyond intended root usage | Unintended escape from app sandbox to system |
| Credential / key exposure | Hardcoded secrets, keystore passwords committed to repo |
| Unsafe shell injection | Unsanitized SSID or BSSID values passed to shell commands |
| Insecure data storage | Sensitive audit logs or captured hashes stored world-readable |
| Dependency vulnerabilities | CVEs in third-party libraries with a demonstrated attack path |
| Supply chain issues | Compromised binary assets in `firmware-dump/` or bundled tools |

### Out of Scope

The following are **not** valid security reports for this project:

- Attacks performed against networks or devices **you do not own or have explicit written permission to test** — that is a legal matter, not a bug in this tool.
- The fact that the tool can capture handshakes, perform deauth, or crack passwords — this is the intended and documented functionality.
- Vulnerabilities in the target environment (access point firmware, router OS, etc.).
- Missing rate-limiting or brute-force protection against external networks — again, intended behavior.
- Issues that require physical access to an already-rooted and unlocked device with USB debugging enabled.
- Theoretical attacks without a working proof-of-concept.
- Scanner results from automated tools with no manual triage (e.g. raw Snyk/Dependabot output without a demonstrated impact).

---

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Send your report to:

```
security@wificracker.dev
```

### What to Include

A complete report helps us triage faster. Please provide:

1. **Description** — A clear summary of the vulnerability and its potential impact.
2. **Affected component** — Module name, file path, or function (e.g. `core/ShellExecutor`, `crack/HashConverter`).
3. **Steps to reproduce** — Minimal, numbered steps. Include commands, inputs, or files needed.
4. **Proof of concept** — Code snippet, screen recording, or logcat output demonstrating the issue.
5. **Suggested fix** — Optional but appreciated.
6. **Your environment** — Android version, device model, app version or commit hash.

### Encryption

If your report contains sensitive details (e.g. a working exploit), you may request our PGP public key
by emailing `security@wificracker.dev` before submitting.

---

## Response Timeline

We aim to handle reports according to the following schedule:

| Milestone | Target |
|-----------|--------|
| Acknowledgement of receipt | 48 hours |
| Initial triage and scope confirmation | 5 business days |
| Status update (fix in progress / rejected / needs more info) | 10 business days |
| Patch release for confirmed Critical/High issues | 30 days |
| Public disclosure (coordinated with reporter) | 90 days after initial report |

If you have not received an acknowledgement within 48 hours, please follow up — emails occasionally
get lost.

We follow a **coordinated disclosure** model. We ask that you do not publicly disclose the vulnerability
before a patch is available, or before the 90-day window has elapsed, whichever comes first. If special
circumstances require an earlier disclosure, please discuss this with us in advance.

---

## Supported Versions

Security fixes are applied to the latest release only. We do not backport patches to older versions.

| Version | Supported |
|---------|-----------|
| Latest release (`main`) | Yes |
| Older releases | No |

---

## Hall of Fame

We publicly thank researchers who report valid, in-scope vulnerabilities in good faith.

If you would like to be credited, include your preferred name or handle and a URL (GitHub, personal
site, LinkedIn) in your report. Credit appears here after the patch is released.

| Researcher | Issue | Date |
|------------|-------|------|
| _No entries yet — be the first._ | | |

---

## Legal Notice

WiFi Cracker is provided for lawful security research and authorized penetration testing only.
Reporting a vulnerability in this tool does not grant authorization to test any network or system
other than your own lab environment. All testing conducted during vulnerability research must target
infrastructure you own or have explicit written permission to access.

We will not pursue legal action against researchers who discover and report vulnerabilities in good
faith, follow this policy, and do not cause harm to users or third parties in the process.
