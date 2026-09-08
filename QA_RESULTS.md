# RiftReign v11 — QA / Release Engineering Notes

## Completed here
- Final package ID verified: `com.minhajul.riftreign`.
- First launch version: `1.0.0` / versionCode `11`.
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

## Must still be tested on the developer PC / Google Play
This environment does not contain a complete Android SDK/Gradle dependency cache, so the final Android variant cannot be compiled or signed here.

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
