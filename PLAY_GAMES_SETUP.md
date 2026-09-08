# RiftReign Play Games Services Setup

Play Games is optional for the first store release. The game remains fully playable offline when it is disabled.

## Client version

The project uses Google Play Games Services v2 dependency `play-services-games-v2:22.0.0`.

## Required console setup before enabling

1. Create/link a Play Games Services project in Play Console.
2. Link Android package `com.minhajul.riftreign`.
3. Configure the correct release/upload signing certificate SHA-1 as required by Play Games setup.
4. Create a high-score leaderboard.
5. Create all eight achievements.
6. Enable Saved Games.
7. Publish the Play Games resources/configuration.
8. Copy the real IDs into `gradle.properties`.
9. Set `RIFT_PLAY_GAMES_CONFIGURED=true`.
10. Test from an Internal Testing Play build with a tester account.

## Features when enabled

- platform authentication,
- high-score leaderboard,
- eight achievements,
- cloud save/load,
- in-app RiftReign cloud-save deletion.

The game does not create a separate Minhajul Abedin Games username/password account.
