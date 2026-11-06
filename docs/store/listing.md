# Google Play submission kit

Everything needed to fill in Play Console for Debugger Kit 1.0.0.

## Store listing

**App name** (30 max)

Debugger Kit: USB Debug Toggle

**Short description** (80 max)

Toggle USB and wireless debugging from a bubble, tiles and per app rules.

**Full description**

Debugger Kit puts USB debugging one tap away. No more digging through Settings every time you plug in a phone.

FLOATING BUBBLE
* Tap to turn USB debugging on or off from any app.
* A timer ring shows how long until auto off, if you turn the timer on.
* Long press opens the dev panel. Double tap applies your favourite preset.
* Docks into a slim edge tab when idle, drag it to the bottom to hide it.

QUICK SETTINGS TILES
* USB debugging, wireless debugging, Developer options, keep awake and the dev panel.
* Optional unlock requirement before a tile changes anything from the lock screen.

DEV PANEL
* Animation scale, stay awake, don't keep activities, font scale and adb connect.
* With Shizuku or root: show taps, pointer location, layout bounds, GPU overdraw, GPU bars, dark mode, demo mode and force stop, clear data or restart for your app under test.
* Presets for UI tests, screenshots and performance checks, plus "Restore original" to undo everything.

DEBUG OFF PER APP
* Banking and wallet apps often refuse to run while debugging is on.
* Pick those apps once. Debugger Kit really turns debugging off while they are open, then back on when you leave.
* "Open without debugging" shortcuts work even for apps that check at startup.

SAFE BY DESIGN
* No internet permission. Nothing leaves your phone.
* Respects device management policies and never touches your lock screen.
* Optional auto off timer, and a local activity log you can export.

ONE TIME SETUP
Android only lets apps change debug settings after you grant a permission from a computer, once:
adb shell pm grant io.github.cuongdev.debuggerkit android.permission.WRITE_SECURE_SETTINGS
The app walks you through it, including Shizuku, root, ColorOS and HyperOS.

Available in English and Vietnamese.

Open source under the MIT license: https://github.com/cuongdev/debugger-kit

**Category**: Tools
**Tags**: Developer tools
**Website**: https://github.com/cuongdev/debugger-kit
**Privacy policy URL**: https://github.com/cuongdev/debugger-kit/blob/main/PRIVACY.md
**Contact email**: [YOUR SUPPORT EMAIL]

## Graphics (in this folder)

* `icon-512.png`: app icon, 512 x 512.
* `feature-graphic.png`: 1024 x 500.
* `screenshots/`: phone screenshots, 1080 x 2160 (2:1, within Play limits). They avoid third party logos on purpose.
* Add a Vietnamese store listing (Play Console, Store presence, Translations) using the strings in
  `app/src/main/res/values-vi/strings.xml` as a glossary.

## App content answers

**Privacy policy**: URL above.

**Ads**: No ads.

**App access**: All functionality is available without an account. Give reviewers this note:
"Changing debug settings needs a permission Android only grants from a computer. Install the app, then run:
adb shell pm grant io.github.cuongdev.debuggerkit android.permission.WRITE_SECURE_SETTINGS
Without it the app shows read only status and a setup guide."

**Content rating**: Utility app, no user generated content, no violence, no gambling. Expected rating: Everyone.

**Target audience**: 18 and over (developer tool). Not designed for children.

**Data safety**
* Does the app collect or share any of the required user data types? **No.**
* Is all user data encrypted in transit? Not applicable, the app makes no network requests.
* Can users request data deletion? Data is local only and is removed by uninstalling the app.

**Government app**: No. **Financial features**: None. **Health**: None. **News**: No.

## Permission declarations

**Foreground service, type specialUse**

Description to paste:
"Debugger Kit shows a floating button over other apps that toggles USB debugging, and it watches which app is in front to turn debugging off for apps the user listed (for example banking apps). These features must keep running while the user works in other apps, so they run in a foreground service with a persistent notification. The user starts and stops the service from the app."

**Usage access (PACKAGE_USAGE_STATS)**: optional, used only by "Debug off per app" to know which app is in front. The screen that asks for it explains why before sending the user to system settings.

**Display over other apps (SYSTEM_ALERT_WINDOW)**: core feature, the floating bubble.

**WRITE_SECURE_SETTINGS**: cannot be granted by Play. The user grants it with adb.

**SCHEDULE_EXACT_ALARM**: optional, user granted, for the auto off timer.

Not used: accessibility services, QUERY_ALL_PACKAGES, location, contacts, camera, microphone, internet.

## Release checklist

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a CHANGELOG entry.
2. Build with JDK 17 to 21: `./gradlew bundleRelease`.
3. Upload `app/build/outputs/bundle/release/app-release.aab` to the internal testing track first.
4. Enroll in Play App Signing. Upload key certificate SHA 256:
   `68:79:88:A2:E9:16:5B:C4:76:5A:AF:EF:1C:7C:06:53:F8:07:CE:C5:4B:81:12:F7:F8:79:1E:DF:B1:E6:88:34`
5. Back up the upload keystore and `keystore.properties` somewhere safe, such as a password manager.
   Both are git ignored on purpose.
6. Fill the store listing, app content answers and the foreground service declaration above.
7. Test the internal build on at least one Pixel and one OEM phone (ColorOS, HyperOS or One UI), then promote.
