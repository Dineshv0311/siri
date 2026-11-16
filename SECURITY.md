# Security & Privacy Policy

The **Siri-Style Android Voice Assistant** project is engineered with a strict **Privacy-First, Security-By-Design** architecture. Voice assistants interact with sensitive operating system resources (microphones, contacts, calls, notifications, and application state). This document outlines our security model, threat mitigations, and vulnerability reporting process.

---

## 1. Security Architecture & Principles

### A. On-Device Voice & Microphone Privacy
- **Zero Continuous Cloud Streaming**: Standby wake-word detection and Voice Activity Detection (VAD) execute **100% on-device**. Raw PCM audio buffers are analyzed in transient volatile memory (`ShortArray`) and are never streamed to remote servers, logged to disk, or stored.
- **Microphone State Transparency**: The microphone is accessed strictly under an active Android Foreground Service (`AssistantForegroundService`) with `FOREGROUND_SERVICE_TYPE_MICROPHONE`. A persistent, non-dismissible system notification is always visible whenever the assistant is running, keeping the user in full control.
- **Adaptive VAD Floor**: Standby voice activity detection dynamically adapts to ambient background acoustics using an exponential moving average. It activates speech recognition only when sustained human speech (>68 dB) is detected, completely eliminating microphone strobe-flickering and unnecessary speech sessions.

### B. Execution Boundaries & Injection Defense
- **Zero Arbitrary Execution**: The assistant does not contain or execute any shell interpreters (`sh`, `bash`, `Runtime.getRuntime().exec()`), dynamic code loading (`DexClassLoader`), or raw string-to-command evaluation.
- **Strict Intent Validation**: All natural language inputs parsed by Gemini or rule engines are converted into strongly-typed Kotlin sealed classes (`AssistantIntent`). Every parameter (package names, contact phone numbers, alarm times) is validated against system schemas before execution.
- **Safe Intent Delegation**:
  - Calling defaults to `Intent.ACTION_DIAL` (presenting the dialer to the user for review) unless `CALL_PHONE` has been explicitly authorized by the user.
  - Messaging drafts are composed via standard Android intents (`Intent.ACTION_SENDTO`, `whatsapp://send`), prompting the user before dispatch.
  - App launches resolve exclusively against legitimate installed launch activities verified via `PackageManager.queryIntentActivities`.

### C. Component Isolation & Android Manifest Security
- **Explicit Non-Exported Components**: Internal services (e.g., `AssistantForegroundService`) are marked `android:exported="false"`. They cannot be invoked, bound to, or manipulated by malicious third-party apps installed on the device.
- **System-Permission-Guarded Services**:
  - `AssistantVoiceInteractionService` is protected by `android.permission.BIND_VOICE_INTERACTION` (enforceable only by the Android OS framework).
  - `AssistantNotificationListenerService` is guarded by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and requires explicit user enablement in Android System Settings.
- **No Insecure Broadcast Receivers**: System receivers (e.g., `BootReceiver`) only process official system broadcasts (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`). No unauthenticated custom broadcast actions are exposed.

### D. Data Storage & API Key Protection
- **Local SQLite Database (Room)**: Conversation transcripts and action logs are stored in the app's sandboxed private data directory (`/data/user/0/com.siri.assistant/databases/`). Other apps cannot inspect or read this database on non-rooted devices.
- **Credential Storage**: Gemini API keys and optional Porcupine access keys are stored in private `SharedPreferences` (`Context.MODE_PRIVATE`). API keys are never hardcoded in source code or committed to version control.
- **Transport Security**: All external AI communication (Gemini API, Open-Meteo weather) enforces TLS 1.3 HTTPS. No cleartext HTTP traffic is permitted (`android:usesCleartextTraffic="false"`).

---

## 2. Permission Model

| Permission | Category | Purpose | Security Mitigation |
| :--- | :--- | :--- | :--- |
| `RECORD_AUDIO` | Dangerous | Hands-free wake word & voice commands | Foreground service notification active; zero persistent storage of audio. |
| `FOREGROUND_SERVICE_MICROPHONE` | Normal / FGS | Android 14/15 mic service compliance | Requires foreground activation and notification visibility. |
| `READ_CONTACTS` | Dangerous | Contact name & phone number lookup | Read-only in-memory search; contacts are never uploaded or synced. |
| `CALL_PHONE` | Dangerous | Direct phone calls via voice | Only used when explicitly granted; falls back to `ACTION_DIAL`. |
| `QUERY_ALL_PACKAGES` | Restricted | Resolving installed app names to packages | Read-only launcher queries; no package modification. |
| `CAMERA` | Dangerous | Flashlight toggle via `CameraManager` | Camera frame data is never accessed; only torch mode is toggled. |
| `POST_NOTIFICATIONS` | Normal | Displaying persistent Siri status & alerts | Standard Android notification compliance. |
| `SYSTEM_ALERT_WINDOW` | Special App Access | Seamless background execution & overlays | User must explicitly grant in Android Settings. |

---

## 3. Reporting a Vulnerability

We appreciate the community's help in maintaining the highest standard of security for this application.

If you discover a security vulnerability or privacy concern:
1. **Do not disclose it publicly** on GitHub issues, pull requests, or forums.
2. Email the maintainer directly at: **security@dineshv.dev** (or open a private GitHub Security Advisory).
3. Include:
   - Detailed description of the vulnerability.
   - Steps to reproduce or proof-of-concept (PoC).
   - Affected device, Android version, and app commit hash.
4. We commit to acknowledging receipt within **48 hours** and providing an assessment and patch schedule within **7 business days**.

---

## 4. Supported Versions

| Version | Supported | Security Updates |
| :--- | :--- | :--- |
| `1.0.x` (Main) | Yes | Actively patched |
| Older releases | No | Please upgrade to `main` |
