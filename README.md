# 🚀 RiftReign: Space Shooter

**Survive the Rift. Evolve your ship. Beat your best.**

RiftReign is a fast-paced offline-first arcade space survival shooter for Android, featuring twin-stick controls, escalating enemy waves, boss battles, mid-run upgrades, achievements, cosmetic customization, Rift Crystals, and persistent local progression.

---

## 👀 First-time visitor guide

**Explore gameplay:** jump to the [screenshots](#-screenshots) and the [controls](#-controls) before trying the project.

**Run it yourself:** see [Build Instructions](#-build-instructions) for the Android project. **Report a problem:** open a [GitHub issue](https://github.com/minhajul-abedin-ai/RiftReign-Space-Shooter/issues) with your device model, Android version, reproduction steps and screenshots where possible.

This repository documents an offline-first Android game; it does not imply a live browser demo or a published store listing. For the author's AI engineering work, visit the [GitHub portfolio](https://github.com/minhajul-abedin-ai).

---

## 🎮 About the Game

**RiftReign: Space Shooter** is an independent Android game developed by **Minhajul Abedin**.

Players control a spacecraft and survive increasingly difficult waves of enemies while collecting rewards, improving their ship during each run, fighting bosses, unlocking cosmetic content, and attempting to beat their previous records.

The current version is designed as an **offline-first single-player experience**.

No RiftReign account is required.

---

## ✨ Features

- 🚀 Fast-paced twin-stick space combat
- 🌊 Progressive enemy wave system
- 👾 Boss encounters
- ⚡ Mid-run Core Evolution upgrades
- 🎚️ Three difficulty modes
- 💎 Earnable Rift Crystals
- 🛸 Multiple cosmetic spacecraft
- 🎯 Weapon and target cosmetics
- 🏆 Local achievements
- 📈 Career statistics
- 💾 Local progression
- ⏸️ Pause and retry system
- 📖 Built-in How to Play section
- ⚙️ Game settings
- 📡 Offline-first gameplay
- 🔐 No account required

---

## 🎚️ Difficulty Modes

RiftReign currently includes three difficulty settings:

### Casual

Designed for a more relaxed experience and easier survival.

### Standard

The balanced default experience.

### Overdrive

Faster and more challenging gameplay with tougher enemies and increased scoring potential.

---

## 🕹️ Controls

RiftReign uses a twin-stick style control system.

**Left side:** Move the spacecraft.

**Right side:** Aim and fire.

Players must move and attack simultaneously while avoiding enemies and incoming projectiles.

---

## 🌊 Survival Gameplay

During a run:

1. Enemy waves become progressively harder.
2. More threats appear on screen.
3. The player earns score and progression.
4. Core Evolution upgrades become available.
5. Boss encounters appear.
6. Rift Crystals can be earned through gameplay.
7. The player attempts to reach a higher wave and score.

---

## ⚡ Core Evolution

Core Evolution allows the player to select upgrades during a run.

Examples include:

### Phase Rounds

Adds enemy piercing capability to projectiles.

### Flux Drive

Improves spacecraft movement speed.

### Hyper Trigger

Increases weapon fire rate.

These upgrades allow individual runs to develop differently depending on the player's choices.

---

## 👾 Boss Battles

RiftReign includes larger boss encounters such as the **Rift Overseer**.

Boss battles introduce:

- Increased projectile pressure
- Larger health pools
- More difficult attack patterns
- Increased survival pressure
- More demanding movement and aiming

---

## 💎 Rift Crystals

Rift Crystals are an earnable in-game currency.

The current release allows players to earn Rift Crystals through gameplay.

They can be used to unlock cosmetic content in the Hangar.

The current launch version does **not** sell Rift Crystals for real money.

---

## 🛸 Hangar

The Hangar allows players to customize their visual loadout.

Current spacecraft include:

- Nova Core
- Cyber Falcon
- Phantom Wing
- Crimson Viper
- Golden Nova
- Void Hunter

Cosmetic selections do not increase combat power.

Every player retains the same core gameplay capabilities.

---

## 🏆 Achievements

RiftReign contains local achievement progression.

Achievements reward milestones such as:

- Beginning progression
- Reaching higher waves
- Defeating enemies
- Reaching score milestones
- Playing higher difficulty modes
- Unlocking cosmetic content

The current launch build stores achievement progress locally.

---

## 📈 Player Progression

RiftReign keeps track of local player statistics including:

- Best score
- Best wave
- Total runs
- Total kills
- Rift Crystals
- Cosmetic unlocks
- Equipped cosmetics
- Achievement progress

Progress is stored locally on the player's device.

---

## 💀 Game Over

When the player's run ends, RiftReign displays a run summary including information such as:

- Score
- Wave reached
- Kills
- Accuracy
- Best score
- Best wave
- Run reward

The player can then retry or return to the main menu.

---

## 📱 Screenshots

Gameplay screenshots have not yet been committed to this repository. To avoid broken image links, screenshots will be embedded here only once real screenshots from a tested Android build are available.

For now, inspect the Kotlin/Android sources and the build instructions below. The `STORE_ASSETS/` folder contains store artwork, not gameplay screenshots.

---

## 🛠️ Technology

RiftReign is currently developed as a native Android project.

### Core Technology

- Kotlin
- Android SDK
- Gradle Kotlin DSL
- Java 17
- Android Studio

### Android Configuration

    Application ID: com.minhajul.riftreign
    Minimum SDK: 26
    Target SDK: 36
    Compile SDK: 36

---

## 📦 Current Version

Current configured Android build (from `app/build.gradle.kts`; device/install verification outstanding):

    Version Name: 1.0.0
    Version Code: 12

Package:

    com.minhajul.riftreign

---

## 🧱 Build Variants

The project separates the offline launch version from future online functionality.

### launch

The `launch` flavor is used for the current offline-first release.

It currently has:

- No advertising SDK
- No Google Play Billing
- No paid Rift Crystal purchases
- No Google Play Games sign-in
- No cloud leaderboard dependency
- No account requirement
- Offline gameplay

The recommended release variant is:

    launchRelease

### online

The `online` flavor is reserved for future functionality.

It may eventually support:

- Google Play Games Services
- Online leaderboards
- Cloud functionality
- Google Mobile Ads
- Rewarded advertisements
- Google Play Billing
- Optional in-app purchases

These features are **not enabled in the current launch release**.

---

## 📂 Project Structure

    RiftReign-Space-Shooter/
    │
    ├── app/
    │   ├── src/
    │   ├── build.gradle.kts
    │   └── proguard-rules.pro
    │
    ├── STORE_ASSETS/
    ├── WEB_HOSTING/
    ├── PRIVACY_POLICY.html
    ├── README.md
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── gradle.properties

---

## 🚀 Build Instructions

### Requirements

You should have:

- Android Studio
- JDK 21 to execute the committed Gradle wrapper (the compiled game still targets JVM 17)
- Android SDK 36
- Git
- Gradle wrapper included with the project

### Clone the Repository

Run:

    git clone https://github.com/minhajul-abedin-ai/RiftReign-Space-Shooter.git

Then:

    cd RiftReign-Space-Shooter

Open the project using Android Studio.

Allow Android Studio to complete Gradle synchronization.

### Development Variant

For development/testing use:

    launchDebug

GitHub Actions now compiles this variant and publishes an unsigned debug APK for inspection. See [Android launch build](.github/workflows/android-build.yml). An APK build is not the same as device testing or a signed release.

### Release Variant

For the current release configuration use:

    launchRelease

Release signing credentials are intentionally excluded from the public repository.

---

## 🔐 Signing & Security

Android signing credentials must never be committed to GitHub.

The following files should remain private:

    keystore.properties
    *.jks
    *.keystore
    local.properties

Passwords, private keys, signing files, API secrets, and production credentials should never be stored in this public repository.

---

## 🔒 Privacy

The current RiftReign launch version is designed as an offline-first game.

It does not require a RiftReign user account.

Gameplay progression such as scores, statistics, Rift Crystals, cosmetic unlocks, settings, and achievements is stored locally on the user's device.

The current launch version does not transmit this local gameplay progression to the developer.

### Privacy Policy

https://minhajul-abedin-ai.github.io/RiftReign-Space-Shooter/PRIVACY_POLICY.html

---

## 💰 Monetization

The current launch version does **not** contain:

- Advertising
- Paid Rift Crystals
- Subscriptions
- Real-money cosmetic purchases
- Paid gameplay upgrades

Future versions may introduce optional monetization through approved Google Play services.

Any future advertising or payment functionality will require the relevant Google Play declarations, privacy updates, SDK configuration, and testing before release.

---

## 🗺️ Development Roadmap

Possible future improvements include:

- Additional spacecraft
- Additional cosmetic content
- More enemy types
- New bosses
- More Core Evolution upgrades
- Expanded environments
- Improved visual effects
- Additional achievements
- Google Play Games integration
- Online leaderboards
- Cloud progression
- Rewarded advertisements
- Optional in-app purchases
- Additional progression systems
- Improved sound and effects
- Possible future 3D version of RiftReign

The roadmap represents future development ideas and does not mean these features are currently available.

---

## 🐛 Feedback & Issues

RiftReign is currently maintained as an independent project.

If you discover a bug, please open a GitHub Issue.

Useful bug reports should include:

- Device model
- Android version
- Description of the problem
- Steps to reproduce it
- Screenshot or screen recording if available

Feature suggestions and constructive feedback are also welcome.

---

## 👨‍💻 Developer

**Minhajul Abedin**

Independent developer behind **Minhajul Abedin Games**.

### Contact

Email:

**minhajasif667@gmail.com**

Contact can be used for:

- Bug reports
- Game feedback
- Business enquiries
- Advertising enquiries
- Partnership opportunities
- Collaboration enquiries

---

## 🌐 Links

### GitHub Repository

https://github.com/minhajul-abedin-ai/RiftReign-Space-Shooter

### Privacy Policy

https://minhajul-abedin-ai.github.io/RiftReign-Space-Shooter/PRIVACY_POLICY.html

---

## ⚠️ License

No open-source license is granted unless a separate `LICENSE` file explicitly states otherwise.

Unless otherwise stated, the source code, artwork, branding, game assets, game design, and related project materials remain the property of the developer.

Public availability of this repository does not automatically grant permission to copy, redistribute, sell, or publish modified commercial versions of RiftReign.

---

## 🌌 RiftReign

**Survive the Rift.  
Evolve your ship.  
Beat your best.**

Developed by **Minhajul Abedin Games**.
