# FamGuard — Verification & Testing Guide

## 1. Automated Test Execution
Run the unit test suite across all modules:
```bash
./gradlew test
```
The test suite in `:packages:api-contracts` validates:
- `testPairingTokenExpiry()`: Expired tokens reject pairing.
- `testLocationCoordinateValidation()`: Rejection of out-of-bounds coordinates and negative accuracy.
- `testLocationSessionUploadRejectionAfterExpiry()`: Server-side rejection of coordinate uploads after session ends.
- `testActivityEventDeduplication()`: Business-key deduplication based on `(package_name, event_type, occurred_at)`.
- `testCommandStatusTransitionValidation()`: Strict validation of command lifecycle transitions.
- `testConsentEnforcement()`: Capability enforcement when consent toggles are disabled.
- `testSevenDayRetentionWindowCalculation()`: Verification of 7-day retention cutoff.

## 2. Compilation and Build Verification
Build debug APKs for both applications:
```bash
./gradlew assembleDebug
```
Output artifacts:
- Controller APK: `apps/android-controller/app/build/outputs/apk/debug/app-debug.apk`
- Agent APK: `apps/android-agent/app/build/outputs/apk/debug/app-debug.apk`

## 3. Physical Device Testing Checklist
- [ ] **Screen Off / Doze Mode**: Verify `LocationTrackingService` maintains updates for 5 minutes with screen off.
- [ ] **Battery Saver**: Verify `ActivitySyncWorker` tolerates delayed execution without CPU wakelock abuse.
- [ ] **Network Reconnection**: Verify pending Room events queue locally and upload upon reconnection.
- [ ] **Usage Access Revocation**: Verify app gracefully reports "Usage Access Missing" without crashing or repeatedly launching settings.
- [ ] **Audible Ring Alert**: Verify ring alert sounds with `AudioAttributes.USAGE_ALARM` and stops on dismiss.
