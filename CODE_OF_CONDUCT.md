# Code of Conduct

## Our Pledge

We as contributors and maintainers of WiFi Cracker pledge to make participation in our community
a harassment-free experience for everyone, regardless of age, body size, visible or invisible
disability, ethnicity, sex characteristics, gender identity and expression, level of experience,
education, socio-economic status, nationality, personal appearance, race, caste, color, religion,
or sexual identity and orientation.

We pledge to act and interact in ways that contribute to an open, welcoming, diverse, inclusive,
and healthy community.

---

## Security Tool Conduct

WiFi Cracker is a professional penetration testing tool. Contributors and users are held to an
additional standard that reflects the sensitive nature of this software.

### Authorized Testing Only

- This tool must only be used against networks and devices **you own** or for which you have
  **explicit written permission** from the owner.
- Do not use this tool against public networks, neighbors' access points, corporate networks
  without a signed scope agreement, or any infrastructure you do not control.
- Penetration test engagements must be governed by a written authorization document (statement
  of work, rules of engagement, or equivalent) before any active testing begins.
- If you are unsure whether your use case is authorized, it is not.

### Anti-Malware Policy

Contributions to this project must not introduce malicious capabilities. The following are
strictly prohibited:

- Code or scripts designed to exfiltrate captured credentials to third-party servers.
- Backdoors, remote access trojans, or covert persistence mechanisms of any kind.
- Telemetry or tracking that transmits user data, scan targets, or captured hashes without
  explicit opt-in consent.
- Obfuscated code whose purpose cannot be clearly determined through review.
- Modifications that cause the tool to target systems outside the user's explicitly defined scope.
- Bundling malware, adware, or cryptocurrency mining software alongside legitimate features.

Any pull request containing such code will be rejected immediately and the contributor will be
permanently banned from the project.

### Responsible Research

- Disclose vulnerabilities found in this tool responsibly — see [SECURITY.md](SECURITY.md).
- Do not use this project's issue tracker or discussions to share captured credentials, handshake
  files from unauthorized targets, or other illegally obtained data.
- Do not request help using this tool for illegal purposes. Such requests will be removed without
  response.

---

## Our Standards

Examples of behavior that contributes to a positive environment:

- Demonstrating empathy and kindness toward other community members.
- Being respectful of differing opinions, viewpoints, and experience levels.
- Giving and gracefully accepting constructive feedback.
- Accepting responsibility when our contributions cause problems and working to correct them.
- Focusing on what is best for the community and the tool's legitimate users.

Examples of unacceptable behavior:

- The use of sexualized language or imagery, and sexual attention or advances of any kind.
- Trolling, insulting or derogatory comments, and personal or political attacks.
- Public or private harassment.
- Publishing others' private information (physical or email addresses) without explicit permission.
- Requesting assistance with or glorifying unauthorized access to computer systems or networks.
- Any conduct that would violate the Computer Fraud and Abuse Act (CFAA), the Computer Misuse Act,
  or equivalent legislation in the contributor's jurisdiction.
- Other conduct which could reasonably be considered inappropriate in a professional setting.

---

## Enforcement Responsibilities

Project maintainers are responsible for clarifying and enforcing this Code of Conduct. They will
take appropriate and fair corrective action in response to any behavior they deem inappropriate,
threatening, offensive, or harmful.

Maintainers have the right and responsibility to remove, edit, or reject comments, commits, code,
issues, and other contributions that do not align with this Code of Conduct, and will communicate
reasons for moderation decisions when appropriate.

---

## Scope

This Code of Conduct applies within all project spaces — GitHub issues, pull requests, discussions,
the project wiki, and any official communication channel. It also applies when an individual is
officially representing the project in public spaces (e.g. posting as a project representative on
social media or at a conference).

---

## Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be reported to the
project maintainers at:

```
conduct@wificracker.dev
```

All complaints will be reviewed promptly and investigated fairly. Maintainers are obligated to
respect the privacy and security of the reporter.

### Enforcement Guidelines

Maintainers will follow these guidelines when determining consequences:

**1. Correction**
_Impact:_ Use of inappropriate language or minor unprofessional behavior.
_Consequence:_ A private written warning with clarity about the violation. A public apology may
be requested.

**2. Warning**
_Impact:_ A single serious violation or a pattern of minor violations.
_Consequence:_ A warning with consequences for continued behavior. No interaction with the people
involved for a specified period. Violating these terms may lead to a temporary or permanent ban.

**3. Temporary Ban**
_Impact:_ A serious violation of community standards, including sustained inappropriate behavior.
_Consequence:_ A temporary ban from all community interaction and public communication. Violating
these terms results in a permanent ban.

**4. Permanent Ban**
_Impact:_ Demonstrating a pattern of serious violations, harassment, aggression toward individuals,
or any submission of malicious code.
_Consequence:_ Permanent ban from all project spaces with no possibility of reinstatement.

---

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant](https://www.contributor-covenant.org),
version 2.1, available at https://www.contributor-covenant.org/version/2/1/code_of_conduct.html,
with additions specific to the security tool context of this project.

Community Impact Guidelines are inspired by
[Mozilla's code of conduct enforcement ladder](https://github.com/mozilla/diversity).
