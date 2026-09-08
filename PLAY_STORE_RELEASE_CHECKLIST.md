# RiftReign v11 — Google Play First-Release Checklist

## 1. New developer account
- [ ] Choose the correct account type. Use Personal if publishing personally; do not claim Organization without a real organization and required verification details.
- [ ] Complete legal identity verification.
- [ ] Verify developer/contact email and phone where Play requests it.
- [ ] Complete physical Android device verification for a new personal account.
- [ ] Developer name: `Minhajul Abedin Games` if desired.
- [ ] Public support/developer email: `minhajasif667@gmail.com`.

## 2. Project/build
- [x] Final app ID: `com.minhajul.riftreign`.
- [x] `compileSdk=36`, `targetSdk=36`.
- [x] First release versionName `1.0.0`, versionCode `11`.
- [x] Offline `launch` flavor excludes online monetization/social SDKs.
- [ ] Test `launchDebug` on emulator.
- [ ] Test `launchDebug` on a real Android phone.
- [ ] Create release keystore and back it up securely.
- [ ] Generate signed `launchRelease` AAB.
- [ ] Upload AAB to Internal testing first.
- [ ] Install the Play-delivered build and smoke test it.

## 3. Store listing
- [x] Title prepared: `RiftReign: Space Shooter`.
- [x] Short/full descriptions prepared.
- [x] 512x512 icon included.
- [x] 1024x500 feature graphic included.
- [ ] Capture real gameplay screenshots from v11.
- [ ] Add support email.
- [ ] Add developer website if you publish one.

## 4. Privacy / app content — launchRelease
- [x] Offline launch privacy-policy HTML prepared.
- [ ] Host privacy policy at a public HTTPS URL.
- [ ] Add that URL in Play Console.
- [ ] Data safety completed for the exact `launchRelease` build.
- [ ] Ads declaration: No.
- [ ] App access: no special access.
- [ ] Target audience completed (recommended positioning: 13+; not child-directed).
- [ ] IARC content rating completed truthfully.
- [ ] Other required App content declarations completed.

## 5. New personal-account testing
- [ ] Complete app setup so Closed testing becomes available.
- [ ] Create Closed test track.
- [ ] Invite 15–20 reliable testers so at least 12 remain eligible.
- [ ] Ensure at least 12 testers opt in and remain continuously opted in for 14 days.
- [ ] Collect real feedback using the in-game Support / Feedback email action.
- [ ] Review pre-launch report, crashes and ANRs.
- [ ] Keep a record of feedback and fixes for the Production-access application.
- [ ] After 14 days, apply for Production access.

## 6. Later monetization/social update — NOT first upload
- [ ] Create AdMob app and real ad unit IDs.
- [ ] Publish app-ads.txt after AdMob setup.
- [ ] Update privacy policy and Data safety before enabling ads.
- [ ] Create Play Billing products and test them through Play.
- [ ] Add server-side purchase verification before broad paid-currency sales.
- [ ] Configure Play Games project, leaderboard, achievements and Saved Games.
- [ ] Test online services in an update/closed test before Production rollout.
