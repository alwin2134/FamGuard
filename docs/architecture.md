# FamGuard — Architecture Documentation

## 1. System Topology
FamGuard separates roles into two native Android applications interacting with a Supabase backend:
- **`apps/android-controller`**: Used by authorized guardians to manage family devices, view locations on Google Maps, review app-usage summaries, and send permitted device controls.
- **`apps/android-agent`**: Installed on the target family device. Operates strictly within standard Android sandbox boundaries (no root, no device owner, no enterprise enrollment).
- **`packages/api-contracts`**: Shared Kotlin Multiplatform/Android library defining serializable data contracts, DTOs, and state enums.
- **`supabase/`**: PostgreSQL database with Row Level Security (RLS), tables, 7-day retention cleanup jobs, and 10 TypeScript/Deno Edge Functions.

```
       [ Guardian Controller App ]
                    │
            HTTPS / Supabase Auth
                    ▼
       ┌─────────────────────────┐
       │   Supabase Backend      │
       │  - PostgreSQL + RLS     │
       │  - Edge Functions       │
       │  - 7-Day Auto Retention │
       └─────────────────────────┘
                    ▲
            HTTPS / Enrolled Creds
                    │
        [ Target Agent App ]
           ├─ Room DB & Cursor
           ├─ WorkManager (App Usage)
           ├─ FusedLocationProviderClient (Live Session)
           └─ Android Keystore (EncryptedSharedPreferences)
```

## 2. Component Design

### 2.1 Target Agent
- **`DeviceKeystoreManager`**: Encrypts and persists `deviceId`, `deviceSecret`, and session tokens in `EncryptedSharedPreferences` backed by Android Keystore (`MasterKey.KeyScheme.AES256_GCM`).
- **`AgentDatabase` (Room)**: Buffers un-uploaded usage events and maintains a synchronization cursor timestamp (`SyncCursorEntity`) to avoid rescanning large event windows.
- **`UsageEventReader` & `AppSessionTracker`**: Reads `UsageEvents` in bounded windows with overlap; detects `ACTIVITY_RESUMED` and `ACTIVITY_PAUSED` transitions without inventing end times.
- **`ActivitySyncWorker` (WorkManager)**: Enqueues periodic 15-minute batched synchronization and expedited on-demand syncs with network constraints.
- **`LocationTrackingService`**: Time-limited Foreground Service with `FOREGROUND_SERVICE_TYPE_LOCATION` and persistent notification. Uploads coordinates every ~12s during active sessions and terminates immediately upon session expiry.
- **`CommandExecutor`**: Dispatches authorized `refresh_location` and audible `ring` alerts.

### 2.2 Controller
- **`ControllerApiClient`**: Supabase Auth and Edge Function client.
- **`GoogleMapsLocationScreen`**: Embeds Google Maps Compose with live marker, accuracy radius circle, stale indicator, and session countdown timer.
- **`AppActivityDashboardScreen`**: Shows latest detected app, today's aggregated usage bars, and chronological event timeline.
- **`DeviceCommandsScreen`**: Triggers ring alerts and location refreshes with live status lifecycle tracking.
- **`QrCodeGenerator`**: Generates single-use pairing QR codes with 15-minute expiry.
