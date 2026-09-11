# MindOrbit - My Reminder

MindOrbit is a local Android reminder and task manager built with Kotlin, Compose Material 3, Room, Coroutines, Navigation Compose, and DataStore. No account, network permission, analytics, advertising, remote API, or cloud synchronization is included.

## Features

- Today dashboard, upcoming/overdue/completed/important lists, calendar, local search, tags, categories, priority and date-range filters.
- Reminder editor, details, swipe completion, duplication, archive, snooze, and local event history.
- One-time, daily, weekday/weekend, weekly, monthly, yearly, custom-day and interval recurrence with optional end date and occurrence limit.
- Manual subscription, investment/FD, IPO, purchase and email templates; optional separate reminders for entered milestone dates.
- Local original thoughts, five original synthesized tones, standard system sound, vibration and silent delivery.
- Notifications with Open, Done and Snooze actions; optional alarm screen subject to Android permission and presentation restrictions.
- Light, dark and system themes; optional device colors; date/time/week-start preferences.
- JSON backup/restore and CSV export through Android's document picker.

## Build

Requirements: JDK 17 or 21, Android SDK platform 36, build tools 36.0.0, Gradle 8.13, and Android Studio compatible with AGP 8.13.2. Minimum Android version is 8.0 (API 26); Android 13 is the primary verification target. Target SDK is 36.

Open this directory in Android Studio and set the SDK location in local.properties. The build requires dependency downloads; the installed application does not.

```powershell
.\gradlew.bat clean test lint assembleDebug
```

For the project-local tooling prepared in this workspace:

```powershell
.\tools\Build.ps1 -Tasks @('clean', 'test', 'lint', 'assembleDebug')
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Android Studio can install it using Run. From the command line:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.mindorbit/.MainActivity
```

## Permissions And Reliability

Allow notifications in Settings inside MindOrbit on Android 13+. Without permission, reminders remain usable and stored, but alerts cannot appear. Enable precise timing using Alarms & reminders special access on Android 12+. Denial falls back to `setAndAllowWhileIdle`; Android may delay delivery.

Alarm mode uses a high-importance notification. Android may open its alarm activity over the lock screen when full-screen access is allowed. The activity plays the selected tone, optionally vibrates, and stops after one minute or when it leaves the foreground. Without full-screen permission, a normal prominent notification is the supported fallback. Users can also mute channels in Android settings.

Only the next due reminder is registered with AlarmManager. The repository retains all reminder schedules and restores the queue after reboot, package replacement, time changes, and app launch. Repeating reminders advance when delivered; a stale notification's action addresses its original occurrence. Snoozing an earlier recurring occurrence creates an independent one-time reminder.

Force-stopping an app prevents Android from delivering its alarms until the app is opened again. Alarms cannot run while a phone is powered off, before the first unlock after a restart, or reliably under every manufacturer's battery restrictions. Missed times are delivered after restoration, with recurrence advanced to the next future occurrence. See TESTING.md for evidence and remaining verification limits.

## Backup

Settings offers JSON export, JSON import with confirmation, and CSV export. Choose local device storage in the Android document picker. Other document-provider apps may offer cloud storage; those providers are outside MindOrbit. MindOrbit makes no connection to them over the network itself.

Backup includes categories, reminders, fields, tags and history. Preferences are device-local and are not included. Restore merges by reminder ID, replacing matching reminders in a transaction. Input is limited to 10 MB, 10,000 reminders and bounded fields/tags/nesting. Invalid backups are rejected before changes. CSV is for reading in spreadsheets and is not a restore format.

Exported files are plaintext; protect them yourself. Deleting app data does not erase files you exported. Cloud and device-transfer backup are disabled in the app manifest.

See ARCHITECTURE.md, PRIVACY.md, SECURITY.md, TESTING.md, and RELEASE.md.
