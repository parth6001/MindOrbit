# Testing

## Local results

The unit suite contains 54 tests: recurrence/date validation (22), Room/repository/scheduling/backup workflows (17), backup codec (9), AndroidScheduler (3), and ViewModel behavior (3). All 54 passed on the local JDK/Android SDK toolchain.

`lintDebug` completed without errors. Warnings are advisory: dependency newer-version notices, API-level XML attributes, a Compose deprecation, and normal KTX/style suggestions. No warning indicates a network capability or sensitive logging.

## Android 13 emulator

The project includes 14 Compose/UI workflow tests and a separate SAF backup workflow. The Android 13 API 33 emulator booted with WHPX acceleration, the debug APK installed successfully, and MainActivity launched with no crash-buffer entries. The final connected run passed 14/14 tests. The first connected run exposed two test harness assumptions (notification PendingIntent activity state and a list-navigation semantic selector); both were corrected before the final run. Run the connected suite with:

```powershell
.\tools\Build.ps1 -Tasks @('connectedDebugAndroidTest')
```

The separate `SafWorkflowTest` uses Android DocumentsUI and may be run after the standard suite. `RebootSeedTest` and `RebootVerifyTest` are intentionally excluded from the ordinary instrumentation run because they require a real emulator reboot between tests:

```powershell
.\tools\Build.ps1 -Tasks @('assembleDebug','assembleDebugAndroidTest')
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class app.mindorbit.RebootSeedTest app.mindorbit.test/androidx.test.runner.AndroidJUnitRunner
adb reboot
adb wait-for-device
adb shell am instrument -w -e class app.mindorbit.RebootVerifyTest app.mindorbit.test/androidx.test.runner.AndroidJUnitRunner
```

The suite covers creation, repeat edit, subscription persistence, FD and IPO typed fields, offline email category, notification actions, denied notifications, rescheduling, backup codec restore, dark mode, 1,001 records, a real three-second scheduled notification, and alarm activity presentation. Android system policies determine exact timing, channel presentation, full-screen access, and battery behavior; those paths also have repository and scheduler tests. The SAF test is excluded from the ordinary runner because Android 13 DocumentsUI did not expose the newly-created Downloads file deterministically in this emulator; JSON codec and transactional repository restore tests passed (9 codec tests plus backup repository restore), and the SAF test source remains available for manual device runs.

## Manual smoke evidence

`build-evidence/home-first.png` is a captured Android 13 launch screen at normal font scale. `build-evidence/home-large-font.png` is a captured 160% font-scale screen. `tools/Verify-Apk.ps1` writes APK badging, manifest, signature, and SHA-256 evidence and fails if INTERNET or ACCESS_NETWORK_STATE appears.

## Test limitations

No physical OEM devices were available. Exact alarms, Doze, force-stop, reboot, timezone changes, and full-screen notification policy can vary by Android build and manufacturer. The emulator supports WHPX and completed the standard instrumentation workflow; a reboot-specific run requires the explicit two-command sequence above. No release signing credentials were provided, so only the debug APK is installable from this workspace.
