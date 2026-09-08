# Create the Signed AAB for the First Play Upload

## Before signing
1. Open `RiftReign_v11` in Android Studio.
2. Let Gradle sync finish.
3. Open **Build > Select Build Variant** (or the Build Variants tool window).
4. Select **launchRelease** for the `app` module when creating the final Play bundle.
5. Test **launchDebug** on your emulator/real phone first.

## Android Studio signing wizard
1. Choose **Build > Generate Signed App Bundle or APK**.
2. Select **Android App Bundle**.
3. Select module `app`.
4. If this is your first app, click **Create new...** for the key store.
5. Save the keystore somewhere permanent and backed up.
6. Use a strong key-store password and key password.
7. Give the key an alias such as `riftreign`.
8. Use a long validity period (for example 25+ years).
9. Enter your real certificate identity details.
10. Select the **launchRelease** variant if Android Studio asks for a variant.
11. Finish the wizard.

The output is an `.aab` file. Upload that AAB to Play Console.

## Critical signing rule
Back up the keystore and passwords in at least two secure locations. Do not put the real keystore or passwords in GitHub or send them to other people.

## Optional Gradle signing file
`keystore.properties.example` is included if you later want command-line signing. Copy it to `keystore.properties`, fill in your own values, and keep the real file private. `.gitignore` is configured to exclude it.
