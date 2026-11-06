# Privacy Policy

Debugger Kit (package `io.github.cuongdev.debuggerkit`)
Effective November 6, 2026

## Summary

Debugger Kit does not collect, send or share any personal or device data.
The app has no internet permission, so it cannot send anything off your phone.

## What the app stores on your phone

* **Your settings**, such as the bubble size, the auto off timer and the list of apps for "Debug off per app".
* **An activity log** of the changes the app makes (for example "USB debugging ON, tile"). It keeps the last 200 entries.
* **A snapshot of developer settings** taken before the dev panel first changes them, so you can restore them.

This data stays in the app's private storage. It is excluded from cloud backup and device transfer.
Uninstalling the app deletes all of it. The activity log only leaves your phone if you tap "Export CSV" and choose where to send it.

## Permissions and why they are used

* **Change secure system settings (WRITE_SECURE_SETTINGS)**: to turn USB debugging, wireless debugging, Developer options and stay awake on or off. You grant it yourself once, from a computer.
* **Display over other apps**: to show the floating bubble and the dev panel.
* **Usage access**: only for "Debug off per app", to see which app is in front. The app reads the name of the app in front, keeps it in memory, and never stores or shares your usage history.
* **Notifications and foreground service**: to keep the bubble, the auto off timer and "Debug off per app" running, with a visible notification.
* **Alarms and reminders (optional)**: to turn USB debugging off on the exact minute.
* **Network state**: to show your WiFi IP address for wireless debugging. No network traffic is made.

## Optional shell access

If you connect Shizuku or grant root, the app runs a few shell commands on your phone to change developer settings
that normal apps cannot change (for example layout bounds). These commands never send data anywhere.

## Children

Debugger Kit is a developer tool and is not directed at children.

## Changes

Changes to this policy are published in this file in the project repository:
https://github.com/cuongdev/debugger-kit

## Contact

Open an issue at https://github.com/cuongdev/debugger-kit/issues
