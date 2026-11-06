# Grant the permission, phone by phone

Debugger Kit changes debug settings through `WRITE_SECURE_SETTINGS`. Android only grants it from a computer (ADB),
from Shizuku, or from root. You do this once. It stays until you uninstall the app.

## Before you start (every phone)

1. Turn on Developer options: Settings, About phone, tap **Build number** seven times.
   (Some brands put it under Software information or Version.)
2. In Developer options, turn on **USB debugging**.
3. Install Android platform tools on your computer, plug the phone in, and accept the
   "Allow USB debugging" prompt on the phone.
4. Check the connection: `adb devices` should list your phone as `device`.

## Method A: grant command (most phones)

```bash
adb shell pm grant io.github.cuongdev.debuggerkit android.permission.WRITE_SECURE_SETTINGS
```

Open Debugger Kit. Home shows a green line "WRITE_SECURE_SETTINGS granted".

## Method B: reinstall with permissions (ColorOS, and any phone where Method A fails)

Some brands block `pm grant` with `SecurityException: ... GRANT_RUNTIME_PERMISSIONS`.
They still let Android grant permissions while installing, so reinstall the app you already have
with the grant flag. Your data and settings are kept.

Run this in an **empty folder** on your computer. It pulls the installed app (also works for Play installs,
which come as several APK files) and reinstalls it:

**macOS and Linux**

```bash
adb shell pm path io.github.cuongdev.debuggerkit | tr -d '\r' | sed 's/package://' | xargs -n1 adb pull && adb install-multiple -r -g *.apk
```

**Windows PowerShell** (not tested yet, please report results)

```powershell
adb shell pm path io.github.cuongdev.debuggerkit | ForEach-Object { adb pull ($_ -replace 'package:','').Trim() }
adb install-multiple -r -g (Get-ChildItem *.apk).Name
```

On ColorOS 17 this also granted "Display over other apps" and "Usage access", so the bubble and
"Debug off per app" work without visiting system settings.

## Notes per brand

**Google Pixel, Samsung (One UI), Motorola, Nothing, Sony and most others**
Method A should work as is.

**Xiaomi, Redmi, POCO (HyperOS, MIUI)**
Before Method A, turn on **USB debugging (Security settings)** in Developer options.
Xiaomi may ask you to insert a SIM card and sign in to a Mi account to enable it. Without it,
`pm grant` fails with a SecurityException.

**OPPO, OnePlus, realme (ColorOS, OxygenOS, realme UI)**
`pm grant` from ADB is blocked. Older versions have **Disable permission monitoring** in Developer options:
turn it on and Method A works. ColorOS 17 no longer shows that option, use Method B.
Tested on OPPO CPH2841, ColorOS 17, Android 17.

**vivo, iQOO, Huawei, Honor, Tecno, Infinix**
Not tested yet. Try Method A, then Method B. Please open an issue with your phone model and result.

**Work or school phones (MDM)**
If your organization disabled debugging (`DISALLOW_DEBUGGING_FEATURES`), Debugger Kit shows
"Managed by your organization" and does nothing. That is intentional. Ask your IT team.

## Without a computer

* **Shizuku** (Android 11 and later): start Shizuku with Wireless debugging, then in Debugger Kit open Setup,
  Shizuku, Connect. The app grants itself the permission. On ColorOS the same block applies to Shizuku.
* **Root**: Setup, Root, Grant with root.

Either one also unlocks the dev panel shortcuts marked **S** (show taps, layout bounds, GPU overdraw and more).

## Troubleshooting

* `Unknown package`: install Debugger Kit first.
* `INSTALL_FAILED_UPDATE_INCOMPATIBLE` during Method B: the APK in the folder is signed differently from the
  installed app. Use only the files pulled by the command above.
* The green line does not appear: tap the refresh button on Home, or force stop and reopen the app.
* Debugger Kit lost the permission: it only goes away if the app is uninstalled. Run the command again.
