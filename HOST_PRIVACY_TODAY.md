# Host the Privacy Policy Today

Google Play needs a public HTTPS privacy-policy URL. The project contains `PRIVACY_POLICY.html` and `WEB_HOSTING/privacy.html`.

## Simple option: GitHub Pages
1. Create a public GitHub repository, for example `riftreign-site`.
2. Upload the files inside `WEB_HOSTING/` to the repository root.
3. Rename `privacy.html` only if you want a different URL; keeping it is fine.
4. GitHub repository > Settings > Pages.
5. Deploy from the `main` branch / root.
6. Wait for the public HTTPS site to appear.
7. Your policy URL will normally look like `https://YOUR-GITHUB-USERNAME.github.io/riftreign-site/privacy.html`.
8. Open it in an incognito/private browser to confirm it is public.
9. Paste that URL into Play Console's Privacy policy field.
10. Put the same URL in `gradle.properties` as `RIFT_PRIVACY_POLICY_URL=...`, sync Gradle and rebuild the final AAB so the in-game Privacy button can open it.

## Alternative
A simple public HTTPS page on your own domain or another reliable static host is also fine. Do not use a local file path, private Drive document, or temporary sandbox link.
