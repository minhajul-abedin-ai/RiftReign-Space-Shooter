# RiftReign — First Upload Checklist

## Account
- [ ] Create the correct **Personal** developer account if publishing personally.
- [ ] Pay the Play Console registration fee shown by Google.
- [ ] Verify developer email/contact details.
- [ ] Complete identity verification.
- [ ] Complete real Android device verification if prompted.

## App creation
- [ ] Create app name: `RiftReign: Space Shooter`.
- [ ] Select Game.
- [ ] Select Free.
- [ ] Use package ID `com.minhajul.riftreign` (comes from the AAB; do not create a different package).

## Project
- [ ] Open `RiftReign_v11` in Android Studio.
- [ ] Choose Build Variant `launchRelease` for Play upload.
- [ ] Test `launchDebug` first on your Pixel emulator and a real Android phone.
- [ ] Create and safely back up the release keystore.
- [ ] Generate a signed `launchRelease` Android App Bundle (.aab).
- [ ] Never lose the keystore/passwords.

## Store listing
- [ ] Upload `STORE_ASSETS/riftreign_play_icon_512.png`.
- [ ] Upload `STORE_ASSETS/riftreign_feature_graphic_1024x500.png`.
- [ ] Capture real v11 screenshots from the actual game (menu, gameplay, boss, Hangar, upgrade/game-over).
- [ ] Copy title/short/full description from `STORE_LISTING_DRAFT.md`.
- [ ] Add support email `minhajasif667@gmail.com`.

## Privacy / app content
- [ ] Host `PRIVACY_POLICY.html` on a public HTTPS URL.
- [ ] Put that URL in Play Console.
- [ ] Optionally set the same URL as `RIFT_PRIVACY_POLICY_URL` so the in-game button opens it.
- [ ] Complete Data safety using `DATA_SAFETY_GUIDE.md` for **launchRelease**.
- [ ] Ads declaration: **No** for launchRelease.
- [ ] Complete target audience (recommended positioning: 13+).
- [ ] Complete IARC content rating truthfully.
- [ ] App access: no special access required.

## Testing today
- [ ] Upload first to Internal testing.
- [ ] Install the Play-delivered build on a real phone.
- [ ] Complete app setup and create Closed testing.
- [ ] Invite at least 12 reliable testers; 15–20 is safer.
- [ ] Make sure at least 12 stay opted in continuously for 14 full days.

## Do NOT enable yet
- [ ] Do not upload `onlineRelease` until AdMob/Billing/Play Games are actually configured.
- [ ] Do not sell paid Rift Crystals until secure purchase/inventory verification is ready.
