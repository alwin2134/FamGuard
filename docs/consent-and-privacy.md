# FamGuard — Consent and Privacy Model

## 1. Principles of Operation
FamGuard is built on mutual, transparent consent:
1. **No Covert Surveillance**: No hidden services, concealed icons, deceptive accessibility services, or keystroke/screen logging.
2. **Granular Scopes**: Pairing does not grant universal access. Location sharing, app-activity monitoring, and remote commands are independent scopes.
3. **Mutual Revocability**: The target user can pause or permanently revoke any capability or unlink the device entirely at any time.
4. **Time-Limited Location**: Location sharing is on-demand for authorized 5-minute sessions with visible persistent notifications and immediate stop controls.

## 2. Data Collection Boundaries
- **What is Collected**:
  - Timestamps and package names for foreground/background transitions.
  - GPS coordinates, accuracy radius, and timestamps during active live sessions.
  - Device battery level and online/offline connectivity heartbeat.
- **What is NEVER Collected**:
  - Text messages, chats, keystrokes, passwords, or emails.
  - Camera feeds, photos, microphone audio, or phone call logs.
  - Screen contents or application payload data.

## 3. Data Retention Policy
- App usage events and location points are retained for a maximum of 7 days.
- The PostgreSQL `purge_expired_telemetry()` function automatically prunes events and location fixes older than 7 days.
- Local Room database caches on the target device purge synced events older than 48 hours.
