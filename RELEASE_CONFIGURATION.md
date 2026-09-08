# Release Configuration

RiftReign v11 uses two service flavors with the same final application ID/signing identity.

## `launch` flavor — USE NOW
Purpose: first Google Play upload and required closed testing for a new personal Play developer account.

- application ID: `com.minhajul.riftreign`
- versionName: `1.0.0`
- versionCode: `11`
- target/compile SDK: 36
- no Internet permission
- no Billing permission
- no AdMob/UMP/Billing/Play Games dependencies
- no ads, real-money purchases or cloud services

Build task: `bundleLaunchRelease`

## `online` flavor — LATER UPDATE
Purpose: monetization + Play Games after external services are configured.

- same application ID: `com.minhajul.riftreign`
- versionName: `1.1.0`
- versionCode: `12`
- can include AdMob, UMP, Billing and Play Games
- controlled by the `RIFT_*` values in `gradle.properties`

Build task: `bundleOnlineRelease`

Never upload `onlineRelease` with demo AdMob IDs or placeholder Play Games IDs. The build script fails fast if those services are explicitly enabled without real IDs.
