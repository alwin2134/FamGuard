# FamGuard — Android Permissions & Platform Restrictions

## 1. Declared Permissions

### 1.1 Target Agent (`apps/android-agent`)
| Permission | Level | Usage & Justification |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Normal | Secure HTTPS communication with Supabase Edge Functions. |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Checks network connectivity before scheduling WorkManager jobs. |
| `android.permission.ACCESS_FINE_LOCATION` | Dangerous (Runtime) | FusedLocationProviderClient coordinates during active sessions. |
| `android.permission.ACCESS_COARSE_LOCATION` | Dangerous (Runtime) | Fallback cell/Wi-Fi location fix when fine GPS is unavailable. |
| `android.permission.FOREGROUND_SERVICE` | Normal | Runs time-limited location tracking service with notification. |
| `android.permission.FOREGROUND_SERVICE_LOCATION` | Normal (API 34+) | Declares foreground service type for Android 14+ compliance. |
| `android.permission.POST_NOTIFICATIONS` | Dangerous (API 33+) | Displays persistent notification during live location sharing. |
| `android.permission.CAMERA` | Dangerous (Runtime) | Scans guardian QR code during initial pairing. |
| `android.permission.VIBRATE` | Normal | Vibrates device during guardian-triggered audible ring alert. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Reconciles sync schedules upon device reboot. |
| `android.permission.PACKAGE_USAGE_STATS` | Special Access | Granted via Android Settings -> Usage Access for app activity. |

### 1.2 Controller (`apps/android-controller`)
| Permission | Level | Usage & Justification |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Normal | Supabase Auth, Maps tiles, and Edge Functions. |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Network state detection. |
| `android.permission.ACCESS_FINE_LOCATION` | Dangerous (Runtime) | Optional: shows guardian's own position relative to target on map. |

---

## 2. Platform Restrictions & Honest Handling
1. **No Background Service Without Live Session**: The app does NOT run a continuous background service when no session is active.
2. **WorkManager Interval**: WorkManager periodic jobs execute at minimum 15-minute intervals. Updates are delayed when Battery Saver or Doze mode is active.
3. **Usage Access**: Android blocks `UsageStatsManager` queries until the user manually grants access in Settings. If revoked, the app stops querying immediately.
4. **Push Wakeup Realities**: Android 12+ restricts background foreground-service starts from FCM data messages. When blocked, the session status transitions to `blocked` or waits for target-side user activation.
