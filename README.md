# Shoal

Installs and updates the [Shoal](https://shoalapps.github.io) apps (Video, Social and Clips) from
their GitHub releases, and keeps itself up to date too.

- **One place for every app**: what's installed, what's new, install, update, open or uninstall.
- **Silent updates with Shizuku or root**: once a day it checks for new versions and installs them in
  the background. Without either, Android asks before each install, except that on Android 12 and
  later, apps the Manager installed can update without asking.
- **Only Shoal's apps**: every download is checked for the right package name and the Shoal
  release signature before anything installs it, so a tampered file is refused even with root.
- The other Shoal apps' **Check for updates** opens the Manager.

## Getting it

Every push to `main` builds a signed release. Install `Shoal.apk` from the newest
[release](https://github.com/ShoalApps/shoal/releases/latest).

## Shevery (or Shizuku)

[Shevery](https://github.com/HmnDev-Tech/shevery) is recommended: a Material 3 fork of Shizuku with the same API, so it works the same way. Uninstall Shizuku before installing it (they conflict).


[Shizuku](https://shizuku.rikka.app) lets apps use system features without root. Start it (wireless
debugging on Android 11+, or adb), tap **Allow Shizuku** in the Manager, and updates become silent.
Shizuku's API is used under the Apache License 2.0.

## Licence

Free software under the **GNU General Public License v3.0 or later**.

## Privacy and legal

No ads, no tracking, nothing sent to us unless you agree to anonymous error reports. See the [privacy policy](https://shoalapps.github.io/privacy.html) and [legal notices](https://shoalapps.github.io/legal.html).

## Support

Shoal is free, open source and has no ads. If you'd like to help it keep going, you can [support Shoal on Liberapay](https://liberapay.com/ShoalApps).

## Release notes

Update `app/src/main/assets/whats-new.json` with each release. Notes ship with the APK, appear once per installed version and can be reopened from Settings. Keep them specific to changes in that release.

## App language

The apps follow Android's system language by default, with an App language picker in Settings and right-to-left layout support. Translations are bundled and work offline. Coverage varies: many phrases are automatically translated, community catalogues may be partial, and missing strings fall back to British English. See `tools/README.md` and `app/src/main/assets/i18n/coverage.json` for maintenance and actual coverage.
