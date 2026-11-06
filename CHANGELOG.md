# Changelog

## Unreleased

* Equal heights for cards, buttons and segmented controls in every language; long labels wrap instead of clipping.
* Prominent disclosure before asking for usage access.
* Activity log lines follow the app language.
* Lighter background work: the per app check pauses while the screen is off, and log writes leave the main thread.

## 1.0.0 (November 6, 2026)

First public release.

* Toggle USB debugging, wireless debugging, Developer options and keep awake from the app.
* Floating bubble with a timer ring: tap to toggle USB debugging, long press for the dev panel,
  double tap for the UI test preset, drag to the bottom to hide. Docks to the screen edge when idle.
* Dev panel with 13 developer shortcuts, 3 presets and "Restore original".
* Quick Settings tiles for USB debugging, wireless debug, Developer options, keep awake and the dev panel.
* Debug off per app: turns debugging off while banking or wallet apps are open, and back on when you leave.
  Includes "Open without debugging" and home screen shortcuts.
* Optional auto off timer (15, 30 or 60 minutes) with exact timing when allowed.
* Keep the screen on while debugging, restoring your own setting afterwards.
* Local activity log with CSV export.
* Setup guide for ADB, Shizuku and root, with notes for ColorOS and HyperOS.
* Respects device policies (DISALLOW_DEBUGGING_FEATURES) and never bypasses the lock screen.
* English and Vietnamese. Follows the system language, with a per app language picker on Android 13 and later.
