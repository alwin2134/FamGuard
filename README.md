<div align="center">

# 🛡️ FamGuard

**Open-Source, Privacy-First Android Family Safety & Device Management Suite**

[![Release](https://img.shields.io/github/v/release/alwin2134/FamGuard?color=10B981&label=Release)](https://github.com/alwin2134/FamGuard/releases)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9334)-green.svg)](https://developer.android.com)
[![Backend](https://img.shields.io/badge/Backend-Supabase%20%2B%20PostgreSQL-3ECF8E.svg)](https://supabase.com)
[![Build Status](https://img.shields.io/github/actions/workflow/status/alwin2134/FamGuard/build-and-release.yml?branch=main)](https://github.com/alwin2134/FamGuard/actions)

FamGuard is a fully open-source, self-hostable family safety suite consisting of a **Guardian Controller app**, a **Child Agent app**, and a **Supabase backend**. Built on mutual consent, end-to-end data ownership, and transparent Android foreground services—with zero covert spyware or ad-tracker surveillance.

[Features](#-key-features) • [Architecture](#-architecture) • [Backend Setup](#-self-hosting--backend-setup) • [App Setup](#-android-apps-setup) • [Pairing Guide](#-pairing--onboarding-walkthrough) • [Contributing](#-contributing)

</div>

---

## ✨ Key Features

- **📍 Dual-Location Live Map & Distance Telemetry**:
  - Live tracking with simultaneous display of **both** the Child (🧒 emerald radar) and the Guardian (🛡️ royal blue ring).
  - Real-time geodesic distance calculation (e.g. `180 m` or `3.2 km`) and auto-framing camera bounds.
  - One-tap turn-by-turn Google Maps navigation shortcut to child.
- **🗺️ Dual Map Engine (Zero-Config OpenStreetMap + Google Maps)**:
  - **OpenStreetMap (Leaflet)**: Renders street tiles out-of-the-box with **zero API keys and zero billing accounts**.
  - **Google Maps**: Built-in toggle for Google Maps vector tiles and 3D buildings.
- **🚨 Real-Time Live Location Alerts**:
  - Guardian is instantly alerted via in-app banner and Android heads-up notification whenever the child starts or stops live sharing.
  - 1-tap **`[View Map]`** shortcut from notifications directly loads the child's position.
- **📱 Battery-Conscious App Activity Monitoring**:
  - Queries Android `UsageStatsManager` in bounded windows, buffers transitions in a local Room database, and syncs via batched `WorkManager` tasks. No battery-draining CPU wakelocks.
- **🖥️ Screen Mirroring & High-FPS Snapshot Streaming**:
  - Multi-tier screen mirroring leveraging WebRTC with an accessibility-assisted high-fps snapshot streaming engine fallback.
- **📞 Direct Voice Calling (WebRTC)**:
  - Encrypted, peer-to-peer parent-to-child voice calls with loudspeaker routing and custom call screens.
- **🔔 Remote Device Controls**:
  - Remote on-demand GPS location refresh.
  - Emergency audible siren (`ring`) that overrides silent mode at full volume.
  - Remote app lock and sync commands with real-time lifecycle tracking (`pending` → `executing` → `succeeded`).
- **🤝 Mutual Consent & Transparent Pairing**:
  - Enrolled via single-use, 15-minute QR codes.
  - Child device displays permanent status badges, explicit permission review, and an instant **Unlink & Revoke All Consents** kill switch.

---

## 🏛 Architecture

FamGuard separates roles into two native Android apps communicating with a self-hosted Supabase cloud instance:

```
        ┌────────────────────────────────────────────────────────┐
        │            Guardian Controller Android App             │
        │           (Jetpack Compose + Google Maps / OSM)        │
        └───────────────────────────┬────────────────────────────┘
                                    │ HTTPS (Supabase Auth / JWT)
                                    ▼
        ┌────────────────────────────────────────────────────────┐
        │                 Supabase Cloud Backend                 │
        │  ├── PostgreSQL Database (Row-Level Security enforced) │
        │  ├── 11 Deno / TypeScript Edge Functions               │
        │  ├── Realtime WebRTC Signaling (webrtc_signals table)   │
        │  └── Automated 7-Day Data Retention Pruning            │
        └───────────────────────────▲────────────────────────────┘
                                    │ HTTPS (Enrolled Device Credentials)
                                    │
        ┌───────────────────────────┴────────────────────────────┐
        │               Target Child Agent Android App           │
        │  ├── Foreground Location Tracking Service              │
        │  ├── Room DB & UsageStatsManager Reader                │
        │  ├── Android Keystore (AES-256 GCM encrypted prefs)    │
        │  └── WebRTC Audio & Accessibility Snapshot Streaming   │
        └────────────────────────────────────────────────────────┘
```

---

## 🚀 Self-Hosting & Backend Setup

You can host the backend on the **free tier of Supabase Cloud** or run it on your own server with Docker.

### 1. Create a Supabase Project
1. Go to [database.new](https://database.new) and create a free project.
2. Note down your **Project URL** and **API Keys** from **Project Settings → API**:
   - `Project URL` (e.g., `https://abcdefghijkl.supabase.co`)
   - `anon` `public` key
   - `service_role` `secret` key

### 2. Apply Database Migrations
You can apply the database schema either using the **Supabase Web Dashboard** or the **Supabase CLI**:

#### Option A: Via Supabase Dashboard (Easiest)
1. Open your project on [Supabase Dashboard](https://supabase.com/dashboard).
2. Go to the **SQL Editor** on the left menu.
3. Open and run the migration files in order:
   - Copy & paste [`supabase/migrations/20261024000001_initial_famguard_schema.sql`](supabase/migrations/20261024000001_initial_famguard_schema.sql) → Click **Run**.
   - Copy & paste [`supabase/migrations/20261026000002_add_webrtc_actions_and_signals.sql`](supabase/migrations/20261026000002_add_webrtc_actions_and_signals.sql) → Click **Run**.

#### Option B: Via Supabase CLI
```bash
# 1. Install Supabase CLI (if needed)
npm install -g supabase

# 2. Login and link your remote project
supabase login
supabase link --project-ref <your-project-ref>

# 3. Push migrations
supabase db push
```

### 3. Deploy Supabase Edge Functions
FamGuard uses 11 lightweight TypeScript Edge Functions for pairing, commands, and telemetry ingestion.

Run the following command from the root of the repository:

```bash
# Deploy all Edge Functions with no-verify-jwt (functions perform internal auth/device validation)
supabase functions deploy create-pairing --no-verify-jwt
supabase functions deploy complete-pairing --no-verify-jwt
supabase functions deploy update-location --no-verify-jwt
supabase functions deploy start-location-session --no-verify-jwt
supabase functions deploy stop-location-session --no-verify-jwt
supabase functions deploy ingest-app-activity --no-verify-jwt
supabase functions deploy get-app-activity --no-verify-jwt
supabase functions deploy get-pending-commands --no-verify-jwt
supabase functions deploy device-command --no-verify-jwt
supabase functions deploy device-ack --no-verify-jwt
supabase functions deploy revoke-device --no-verify-jwt
```

*(Note: `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are automatically injected into Edge Functions by Supabase).*

---

## 📲 Android Apps Setup

You have two options to run the apps:

### Method A: Use Pre-Built APKs (No Compilation Needed)
1. Go to **[GitHub Releases](https://github.com/alwin2134/FamGuard/releases)** and download:
   - `famguard-controller.apk` (Install on Guardian phone)
   - `famguard-agent.apk` (Install on Child phone)
2. On initial launch, tap **"Configure Server & Updates"** on the login screen.
3. Paste your **Supabase Project URL** and **Anon Key**.
4. The apps are immediately connected to your private backend!

---

### Method B: Build from Source

#### Prerequisites
- **Android Studio Hedgehog (2023.1.1)** or newer.
- **JDK 17** (bundled with Android Studio JBR).
- **Android SDK** API 34.

#### Configuration (`local.properties`)
Create or edit `local.properties` in the project root:

```properties
sdk.dir=C:\\Users\\<YourUser>\\AppData\\Local\\Android\\Sdk

# Your Supabase Backend Credentials
SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# Optional: Google Maps API Key (OpenStreetMap works without this!)
MAPS_API_KEY=AIzaSy...
```

#### Build Commands
```bash
# Build Debug APKs for both Controller and Agent
./gradlew assembleDebug

# Build Release APKs with signing
./gradlew assembleRelease

# Run unit tests across all modules
./gradlew test
```

The compiled APKs will be located in:
- `apps/android-controller/app/build/outputs/apk/debug/app-debug.apk`
- `apps/android-agent/app/build/outputs/apk/debug/app-debug.apk`

---

## 🗺️ Google Maps vs. OpenStreetMap

| Feature | OpenStreetMap (Street Tiles) | Google Maps |
| :--- | :--- | :--- |
| **Setup Required** | **Zero Setup** (Default) | Requires Google Cloud API Key |
| **Billing Required** | 100% Free / No Billing | Requires Google Cloud Billing account |
| **Live Child Marker (🧒)** | ✅ Yes (Emerald halo) | ✅ Yes (Azure marker + accuracy circle) |
| **Guardian Marker (🛡️)** | ✅ Yes (Royal blue halo) | ✅ Yes (Blue marker + accuracy circle) |
| **Real-Time Distance** | ✅ Yes | ✅ Yes |
| **Auto-Framing Camera** | ✅ Yes (`map.fitBounds`) | ✅ Yes (`CameraUpdateFactory.newLatLngBounds`) |
| **Dashed Route Line** | ✅ Yes | ✅ Yes |

> **To use Google Maps**: Enable **Maps SDK for Android** in the [Google Cloud Console](https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com), add `MAPS_API_KEY=AIzaSy...` to your `local.properties`, and recompile.

---

## 📱 Pairing & Onboarding Walkthrough

```
[Guardian Controller]                             [Child Agent]
         │                                              │
         ├─ 1. Register & Sign in with email            │
         ├─ 2. Tap "Add Device"                         │
         │     (Generates 15-min QR token)              │
         │                                              ├─ 3. Open FamGuard Agent
         │                                              ├─ 4. Tap "Scan Guardian QR"
         │◄──────────────── Scan QR Code ───────────────┤
         │                                              ├─ 5. Review requested scopes:
         │                                              │     - Location sharing
         │                                              │     - App usage monitoring
         │                                              │     - Remote ring & lock
         │                                              ├─ 6. Tap "Approve & Enroll"
         │                                              │
         ▼                                              ▼
[Family Dashboard Active]                     [Protected & Live Syncing]
```

1. **Controller App**:
   - Create a Guardian account with email/password.
   - Tap the **QR Code icon** on the top bar to generate a single-use pairing token.
2. **Agent App**:
   - Launch FamGuard Agent and tap **Scan Pairing QR Code**.
   - Point the camera at the Controller screen.
   - The child reviews the requested permissions and taps **Approve & Enroll**.
   - Grant necessary Android permissions:
     - **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`)
     - **Usage Access** (Android Settings → Usage Access)
     - **Accessibility Service** (Optional, required for screen mirroring)
3. **Done!** Both devices are now paired and securely communicating.

---

## 🔒 Security, Privacy & Compliance

- **No Covert Operation**: FamGuard runs with clear foreground notifications whenever location tracking or screen mirroring is active, adhering to Google Play Families Policy and Android Foreground Service specifications.
- **Hardware-Backed Encryption**: Device identifiers and secrets are encrypted in `EncryptedSharedPreferences` backed by the **Android Keystore** (`AES-256 GCM`).
- **Row-Level Security (RLS)**: PostgreSQL enforces strict RLS policies on all tables. Guardians can only access records for devices enrolled within their own family.
- **Automated 7-Day Data Retention**: Database includes an automated cleanup function that purges location fixes, commands, and app activity older than 7 days, preventing unnecessary data accumulation.
- **Revocation Kill-Switch**: The child or guardian can revoke permissions or unlink the device at any time, instantly deleting all local cryptographic keys and terminating background services.

---

## 📂 Repository Directory Structure

```
FamGuard/
├── apps/
│   ├── android-controller/            # Guardian Controller Android App
│   │   └── app/src/main/java/com/famguard/controller/
│   │       ├── auth/                  # Supabase Auth client & session store
│   │       ├── devices/               # QR pairing generator & device cards
│   │       ├── ui/                    # Compose UI (Dashboard, Map, Controls, Calls)
│   │       ├── webrtc/                # WebRTC audio & screen mirror receiver
│   │       └── update/                # In-app GitHub release updater
│   └── android-agent/                 # Child Agent Android App
│       └── app/src/main/java/com/famguard/agent/
│           ├── activity/              # UsageStatsManager reader & Room DB
│           ├── auth/                  # Agent API client for Edge Functions
│           ├── call/                  # Voice calling service & incoming UI
│           ├── commands/              # Remote command executor (ring, refresh)
│           ├── location/              # Foreground location tracking service
│           ├── mirror/                # WebRTC screen mirror & snapshot streamer
│           ├── security/              # Android Keystore encryption
│           ├── service/               # Accessibility helper service
│           └── ui/                    # Transparent status badges & consent UI
├── packages/
│   └── api-contracts/                 # Shared Kotlin Multiplatform serializable DTOs
├── supabase/
│   ├── migrations/                    # PostgreSQL DDL, RLS, and retention scripts
│   └── functions/                     # 11 Deno / TypeScript Edge Functions
├── docs/                              # Architecture, permissions, and threat models
└── .github/workflows/                 # CI/CD automated release build pipeline
```

---

## 🤝 Contributing

Contributions, bug reports, and feature requests are welcome!

1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'feat: Add AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
