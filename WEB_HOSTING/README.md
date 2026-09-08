# RiftReign website files

Host this folder on a public HTTPS website.

Required before production:

1. Verify `index.html` and `privacy.html` are publicly accessible.
2. Put the privacy URL into Play Console and `RIFT_PRIVACY_POLICY_URL`.
3. Add the website root as the developer website in the Google Play store listing.
4. If using AdMob, replace the publisher ID in `app-ads.txt.example`, rename it to `app-ads.txt`, publish it at the website root, then verify it in AdMob.

Example layout after hosting:

- `https://example.com/`
- `https://example.com/privacy.html`
- `https://example.com/app-ads.txt`
