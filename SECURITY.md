# Security Review

## Design Controls

- No INTERNET/ACCESS_NETWORK_STATE permission, networking client, Firebase, telemetry, ads, remote WebView, or remote service.
- No secrets, API keys, production keystore, or credentials in application code.
- MainActivity is the only application-owned exported component, for launcher use. AlarmActivity and alarm/action/restore receivers are non-exported. AndroidX may merge a permission-protected profile-installation receiver; inspect it in the built artifact.
- Explicit immutable PendingIntents carry internal component names, validated IDs, allowlisted actions, and occurrence timestamps. Notification actions cannot execute arbitrary imported data.
- Room uses bound parameters and generated APIs. No SQL is assembled from user input.
- Backups are versioned, size/count/depth-limited, validated before restore, and restored transactionally. Duplicate JSON keys and reminder IDs are rejected. CSV formula prefixes are neutralized.
- Android's document picker provides scoped file access. No broad storage permission, FileProvider, unsafe URI sharing, or arbitrary path import is used.
- Private app storage and disabled cloud/device-transfer backup protect local data. Notification content is private on the lock screen by default.
- Background exceptions become user-visible status, and reminder content is not written to production logs.

## Threat Boundaries

Device root access, an unlocked device, privileged debugging access, compromised operating systems, and exported plaintext files are outside the app sandbox's protection. Debug APKs are debuggable and should not be distributed as production releases. Android document providers and system notification policy are external to MindOrbit.

Build tools necessarily download dependencies. Test-only libraries may contain network code used by Robolectric to fetch Android test artifacts. They are not included in the application runtime. XML schema URLs and build repository URLs are not runtime connections.

Final artifact findings and verification commands are recorded in TESTING.md after builds complete.
