# Architecture

## UI And State

One activity hosts Navigation Compose with dashboard, list/filter, calendar, editor, details, categories, history, settings, and privacy destinations. A separate non-exported alarm activity supports the system full-screen notification path. Compose observes lifecycle-aware StateFlows from OrbitViewModel. The ViewModel translates operations to repository calls and exposes errors and progress.

## Persistence

Room schema version 1 stores reminders, categories, tags, reminder-tag links, typed custom fields, and history in separate tables. The reminder table indexes due time, status, and category. Fields and tag links cascade when a reminder is deleted. History intentionally retains the reminder title for deleted-item history. Category templates are catalog definitions rather than database columns, allowing new categories without schema redesign.

Room's generated schema is exported in app/schemas. There is no destructive migration fallback. Version 1 is the initial schema, so no upgrade migration is needed yet. Every future schema change must increment the database version, add an explicit tested Migration, and retain old exported schemas.

DataStore holds theme, default sound/vibration/snooze/alarm, week start, and date/time preferences. No sensitive data is logged.

## Scheduling

ReminderScheduler separates domain/repository tests from Android services. AndroidScheduler maintains an in-process queue with one next-due AlarmManager PendingIntent, avoiding per-app alarm-count limits. Room is the durable source of truth. Repository writes commit before scheduling and preserve a scheduling-status field. Scheduling failures leave data intact and can be retried on app open.

An application-scoped IO coroutine initializes catalog data and restores schedules. Boot, package-update, clock, timezone, and exact-access-grant broadcasts restore the queue with goAsync. Reminder operations are serialized with a coroutine mutex. Receivers validate actions and IDs and are not exported. Alarm broadcasts verify both reminder ID and occurrence timestamp before delivering.

Recurring delivery schedules the next future occurrence. Completion from an old notification records its occurrence without advancing the newer reminder. Snoozing that old occurrence creates a separate once-only item. Recurrence uses java.time, the stored zone, and calendar arithmetic with day/month anchors for month-end and leap-year restoration. On timezone changes, wall-clock schedules move to the new device zone.

## Backups

BackupCodec explicitly maps each supported field. Android JsonReader first bounds nesting and rejects duplicate object keys; JSONObject then parses the versioned structure. Record counts, string sizes, enums, dates, IDs, categories and numeric values are validated. Restore writes all imported rows in one Room transaction, then schedules their alerts. CSV cells are quoted and formula prefixes neutralized.

## Dependencies

Compose/Material: native UI and icons. Activity/Navigation/Lifecycle: screen hosting and state. Room/KSP: typed local database. DataStore: settings. Core: Android compatibility helpers. Coroutines: asynchronous work and flows. JUnit/Robolectric/AndroidX Test: tests only. WorkManager is intentionally absent because no periodic network or deferred maintenance work is needed.
