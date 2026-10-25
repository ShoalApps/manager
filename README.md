# Tintloom Manager

Installs and updates the [Tintloom](https://tintloom.github.io) apps (Video, Social and Clips) from
their GitHub releases, and keeps itself up to date too.

- **One place for every app**: what's installed, what's new, install, update, open or uninstall.
- **Silent updates with Shizuku or root**: once a day it checks for new versions and installs them in
  the background. Without either, Android asks before each install, except that on Android 12 and
  later, apps the Manager installed can update without asking.
- **Only Tintloom's apps**: every download is checked for the right package name and the Tintloom
  release signature before anything installs it, so a tampered file is refused even with root.
- The other Tintloom apps' **Check for updates** opens the Manager.

## Getting it

Every push to `main` builds a signed release. Install `TintloomManager.apk` from the newest
[release](https://github.com/Tintloom/manager/releases/latest).

## Shizuku

[Shizuku](https://shizuku.rikka.app) lets apps use system features without root. Start it (wireless
debugging on Android 11+, or adb), tap **Allow Shizuku** in the Manager, and updates become silent.
Shizuku's API is used under the Apache License 2.0.

## Licence

Free software under the **GNU General Public License v3.0 or later**.
