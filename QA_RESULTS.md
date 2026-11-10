# RiftReign — QA / Release Engineering Notes

## Completed here
- Final package ID verified: `com.minhajul.riftreign`.
- First launch version: `1.0.0` / versionCode `12`.
- Target/compile SDK set to API 36.
- Split service architecture reviewed:
  - `launch` flavor contains no Google Ads, UMP, Billing or Play Games dependency;
  - `launch` manifest requests no Internet/network/billing permission;
  - `online` flavor retains future monetization/social implementations.
- All 12 Android XML resource/manifest files parsed successfully.
- Store icon verified at exactly 512x512.
- Feature graphic verified at exactly 1024x500.
- Pure Kotlin `GameCore.kt` recompiled with JDK/Kotlin compiler.
- Randomized simulation completed successfully: **77,043 frames**.
- No Google SDK imports were found in `app/src/main` or `app/src/launch` Kotlin source.
- Gradle script delimiter sanity check passed.

## Automated Android build evidence (10 November 2026)
- GitHub Actions [launchDebug run #42737094520](https://github.com/minhajul-abedin-ai/RiftReign-Space-Shooter/actions/runs/42737094520): **passed** on Linux with SDK 36 and JDK 21.
- Uploaded an **unsigned debug APK**, artifact `riftreign-launch-debug-apk` (artifact ID 13728180630, 962,210 bytes).
- Artifact ZIP digest reported by GitHub: `sha256:a5e5e4f8e5969a37d843b9d674730458566d6f68b70f80cc54756c4c50d2affd`.
- Initial CI attempts exposed incompatible SDK setup and JDK 17; the workflow was corrected to use the runner SDK and JDK 21.
- This verifies compilation only. It does not verify Android device/emulator functionality, signing, Play Store acceptance, store release compliance or online services.

## Must still be tested on the developer PC / Google Play
The Android launchDebug variant compiles in CI, but no signed release bundle or device test has been verified in this audit.

Before upload:
- Gradle sync in Android Studio.
- `launchDebug` emulator run.
- `launchDebug` real-phone run.
- create release keystore.
- generate signed `launchRelease` AAB.
- install/test the Play-delivered Internal Testing build.
- review Play pre-launch report.
- complete the required Closed Test for a new personal account.

## First-release risk reduction
The first-upload build intentionally does not ship external monetization/social SDKs. This keeps first-release Data safety and permissions minimal and prevents demo AdMob IDs, unconfigured Billing products or placeholder Play Games IDs from becoming submission blockers.
