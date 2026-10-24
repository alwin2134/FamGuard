# FamGuard

**Consent-based Android Family Device-Management and Safety Application**

FamGuard is a native Android and Supabase solution designed for family safety, live location sharing, permitted app-activity monitoring, and remote device controls with mutual transparency and battery efficiency.

---

## Repository Structure
```
FamGuard/
  apps/
    android-controller/       # Guardian Controller Jetpack Compose Application
      app/
        src/main/java/com/famguard/controller/
          auth/               # Supabase Auth client & credentials
          devices/            # QR code generation & device cards
          ui/                 # Compose dashboard, Google Maps, activity timeline
    android-agent/            # Target Device Managed Agent Application
      app/
        src/main/java/com/famguard/agent/
          activity/           # UsageStatsManager reader, Room DB, WorkManager sync
          auth/               # Agent API client for Edge Functions
          commands/           # Remote command executor (refresh-location, ring alert)
          location/           # FusedLocationProviderClient live tracking service
          security/           # Android Keystore EncryptedSharedPreferences
          ui/                 # Transparent status badges, consent toggles, pairing approval
  packages/
    api-contracts/            # Shared Kotlin serialization models and contracts
  supabase/
    migrations/               # PostgreSQL schema, RLS policies, 7-day retention function
    functions/                # 10 Supabase Edge Functions (Deno / TypeScript)
      create-pairing/
      complete-pairing/
      update-location/
      start-location-session/
      stop-location-session/
      ingest-app-activity/
      get-app-activity/
      device-command/
      device-ack/
      revoke-device/
  docs/                       # Architecture, permissions, privacy, threat model, testing guides
```

---

## Core Capabilities
- **Mutual Consent Pairing**: Single-use, short-lived QR pairing tokens; explicit target-side approval of requested scopes before enrollment.
- **On-Demand Live Location**: Time-bounded (5 min) sessions using `FusedLocationProviderClient` with persistent foreground notification. Terminated immediately upon session expiry or user stop.
- **Google Maps Integration**: Displays live target location, accuracy radius circle, stale indicator, and session countdown timer.
- **Battery-Conscious App Activity**: `UsageStatsManager` queries within bounded windows, Room DB queue, and `WorkManager` batched sync. No CPU wakelock abuse or permanent background services.
- **Remote Device Controls**: Dispatches `refresh_location` and audible `ring` alerts with live lifecycle tracking (`pending` -> `executing` -> `succeeded` / `failed` / `blocked`).
- **Supabase Security**: Row Level Security (RLS) policies on all tables, SHA-256 hashed device credentials, and automated 7-day retention cleanup.

---

## Build & Run Instructions

### Prerequisites
- Android SDK (API 34)
- Java 21 (bundled with Android Studio JBR at `C:/Program Files/Android/Android Studio/jbr`)

### Build Both Android Applications
```bash
./gradlew assembleDebug
```

### Run Automated Unit Tests
```bash
./gradlew test
```
