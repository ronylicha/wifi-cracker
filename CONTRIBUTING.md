# Contributing to WiFi Cracker

Thank you for your interest in contributing to WiFi Cracker! This guide will help you get started.

## Code of Conduct

- This tool is for **authorized security testing only**
- Do not submit code designed for malicious purposes
- Be respectful in all interactions

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 35
- JDK 17
- A rooted Android device for testing (recommended)

### Setup

```bash
git clone https://github.com/YOUR_USERNAME/wifi-cracker.git
cd wifi-cracker
./gradlew assembleDebug
```

## Project Architecture

### Modules

| Module | Purpose | Key Classes |
|--------|---------|-------------|
| `app` | Navigation, theme, DI wiring | `MainActivity`, `MainDashboard`, `AppModule` |
| `core` | Shared services | `ShellExecutor`, `RootChecker`, `AuditLogger`, `PentestForegroundService` |
| `scan` | WiFi scanning | `ScanEngine`, `WifiCommandRunner`, `VulnMatcher` |
| `attack` | Attack execution | `AttackOrchestrator`, `DeauthAttack`, `HandshakeCapture` |
| `crack` | Password cracking | `CrackOrchestrator`, `DictionaryAttack`, `HashConverter` |
| `report` | Report generation | `ReportGenerator`, `ExportManager`, `CvssCalculator` |

### Architecture Pattern

Each module follows MVVM + Clean Architecture:
```
module/
├── model/      # Data classes and enums
├── domain/     # Business logic (use cases, engines)
├── data/       # Data sources (Room DAOs, shell commands)
└── ui/         # Compose screens and ViewModels
```

## How to Contribute

### Reporting Bugs

Open an issue with:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output if applicable

### Adding Vulnerabilities

The vulnerability database is at `core/src/main/assets/vulns.json`. To add entries:

```json
{
    "cveId": "CVE-YYYY-NNNNN",
    "protocol": "WPA2",
    "title": "Short title",
    "description": "Technical description",
    "severity": "CRITICAL|HIGH|MEDIUM|LOW",
    "cvssScore": 9.8,
    "recommendation": "Remediation steps",
    "affectedVersions": "Affected devices/versions"
}
```

### Adding Translations

String resources are in each module's `res/values/strings.xml` (EN) and `res/values-fr/strings.xml` (FR). To add a new language:

1. Create `res/values-XX/strings.xml` in each module
2. Translate all strings
3. Submit a PR

### Code Style

- Kotlin with explicit types on public APIs
- Jetpack Compose for all UI
- Hilt for dependency injection
- Follow existing patterns in each module
- Tests required for domain logic (TDD preferred)

### Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Follow existing code patterns
4. Add tests for new domain logic
5. Ensure all tests pass: `./gradlew test`
6. Ensure release builds: `./gradlew assembleRelease`
7. Submit PR with clear description

## Testing

```bash
# All tests
./gradlew test

# Specific module
./gradlew :core:testDebugUnitTest
./gradlew :scan:testDebugUnitTest

# With coverage
./gradlew testDebugUnitTest --info
```

## Roadmap

- [ ] Settings screen (theme toggle, language switch, storage config)
- [ ] Vulnerability database browser screen
- [ ] Audit log viewer/exporter
- [ ] Wordlist manager with download support
- [ ] USB WiFi adapter auto-detection
- [ ] Dark/Light theme toggle in settings
