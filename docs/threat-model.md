# FamGuard — Threat Model and Security Architecture

## 1. Assets and Attack Surfaces
- **Target Location Telemetry**: Coordinates must only be accessible to authorized guardians within the same family during an active session.
- **App Usage Data**: Timeline of app transitions must only be readable when `app_activity_enabled` consent is granted.
- **Device Authentication Credentials**: Enrolled devices use a 256-bit random secret (`device_secret`) stored in the Android Keystore. The server stores only the SHA-256 hash (`device_secret_hash`).
- **Pairing Process**: Prevents unauthorized device linking via short-lived, single-use cryptographically hashed tokens.

## 2. Threat Analysis & Mitigations

### 2.1 Unauthorized Family Cross-Access
- **Threat**: A user in Family A attempts to read location or app activity for a device in Family B.
- **Mitigation**: PostgreSQL Row Level Security (RLS) policies enforce `is_family_member()` and `is_family_guardian()` checks on all exposed tables. Edge Functions authenticate guardian JWTs and verify membership.

### 2.2 Pairing Token Replay or Brute-Force
- **Threat**: An attacker attempts to guess or replay a pairing QR token to pair an unauthorized device.
- **Mitigation**:
  - Tokens are 32-character high-entropy random hex strings.
  - Expiry is enforced at 15 minutes.
  - Server marks token `consumed` atomically using `UPDATE ... WHERE status = 'pending'`. Replayed tokens are rejected.

### 2.3 Post-Session Location Leakage
- **Threat**: A rogue client attempts to upload coordinates after a live location session has ended or expired.
- **Mitigation**: `update-location` Edge Function checks session `status` and verifies `now() < expires_at`. Coordinates received after expiration or stop are rejected with HTTP 410.

### 2.4 Device Credential Extraction
- **Threat**: An attacker accesses physical device storage to extract server credentials.
- **Mitigation**: Credentials are stored via `EncryptedSharedPreferences` backed by Android Keystore hardware keys (`AES256_GCM`).
