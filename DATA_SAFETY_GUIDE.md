# RiftReign v11 — Data Safety Guide for `launchRelease`

The first-upload flavor is intentionally offline and excludes Google Mobile Ads, UMP, Play Billing and Play Games dependencies from the build. Its manifest does not request Internet or billing permissions.

## Expected first-build posture
- RiftReign does not transmit local game progress to the developer.
- No developer-operated account.
- No advertising in the launch build.
- No real-money purchases in the launch build.
- No cloud saves/leaderboards in the launch build.
- No location, contacts, camera, microphone or storage permission.
- Local progress is not considered collected by the developer if it never leaves the device.

For the current `launchRelease`, the expected Data safety answer is generally **no app/developer data collection or sharing**, subject to the exact current Play Console wording and any platform behavior Google asks you to disclose.

## Important for later `onlineRelease`
Do NOT reuse the launch answers when you enable ads, Billing or Play Games. Before uploading an online build:
1. update the privacy policy;
2. update Data safety for every enabled SDK;
3. complete the Ads declaration;
4. configure consent/UMP where applicable;
5. configure Billing products and purchase verification;
6. configure Play Games IDs and cloud behavior.
