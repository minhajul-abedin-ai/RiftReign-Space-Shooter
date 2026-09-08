# Monetization — Planned for a Later Update

Do not enable monetization in the first `launchRelease` uploaded from a brand-new Play developer account.

The `online` flavor keeps the monetization implementation for a later version after the game has completed closed testing and the external services are properly configured.

Recommended order after launch readiness:
1. AdMob rewarded ads;
2. limited interstitial ads;
3. one-time Remove Ads purchase;
4. paid Rift Crystal packs only after server-side purchase/inventory verification.

When ready, configure the `RIFT_*` properties in `gradle.properties`, update privacy/Data safety/Ads declarations, and test `onlineRelease` through a Play test track before Production.
