# LunchPack

LunchPack is a lightweight, fully offline lunchbox and snack-packing organizer for Android.
Create reusable pack templates, tick items off as they go into the bag, see them appear in the
right compartment of an open lunchbox, plan packs for the coming days and review what you saved.

LunchPack organizes items **you** choose. It does not evaluate meals, calories, diets, nutrition,
allergies or food safety, and it cannot check what is physically in a bag.

- Package: `com.egrmeister.lunchpack`
- Version: 1.0.0 (versionCode 1)
- Kotlin + Jetpack Compose, one native module, no WebView, no JavaScript.

## Features

| Area | What it does |
| --- | --- |
| **Open Lunchbox** (main screen) | Four labelled compartments — Main, Sides, Snack, Extras — inside a teal frame plus a Bottle pocket beside the box. Selected date and pack name above the box; unpacked items as checklist chips below it; a full accessible checklist; “4 of 7 packed.”, **Review Pack** and **Start Fresh**. Tapping a compartment filters the checklist to it. Narrow screens stack the compartments; wide screens put the box beside the checklist. |
| Packed state | Tapping an item toggles it and persists immediately. Packed icons appear in their compartment; unchecking removes them. Icons never overlap — extra items collapse into a “+N more” tile. |
| Pack templates | Up to 30 templates × 30 items. Each item: stable ID, name, compartment, bundled icon, optional note, order. Create, edit, duplicate, delete, favorite. Explicit Save / Cancel with a discard warning (also on Back). |
| Favorites | Favorites sort first in the pack picker; it is a flag on the same record, never a copy. |
| Starter packs | Everyday Box, Snack Break and Outing Pack — editable examples, not meal recommendations. Seeded once; deleted examples are only restored via Settings → Restore starter packs (unique names, existing data kept). |
| Week | Seven dated rows for the selected calendar week (locale’s first day of week), today through the next 30 days. One plan per date with Not started / In progress / Saved (/ Partial / Needs review), **Change Pack**, **Open Pack**, **Update from template**, **Remove plan**. Past days point to History and are never marked “missed”. |
| Review & save | Packed list, remaining list, count, **Save Pack**, **Continue Packing**. “Everything on your list is packed.” when complete; **Save partial pack** with confirmation otherwise. |
| History | Latest 100 saved results: pack name, planned date, saved date/time, packed count, Complete/Partial. Details show items, compartments and packed states as saved. Delete one or clear all (with confirmation). |
| Unscheduled packing | “Pack now” from the picker creates a separate session (not a weekly assignment). One unfinished unscheduled session at a time. |
| Reminders | One optional daily reminder, off by default, approximate delivery. |
| Settings | Pack management, reminder on/off + time, reduced animation, restore starter packs, clear history, clear all local data, bundled privacy page. |

## Template snapshots, planning and revision rules

- **Snapshots.** Assigning a template to a date copies the template name and items into a dated
  plan (`PackingSessionEntity` + `SessionItemEntity`). Later template edits do **not** change that
  plan. **Update from template** (Week → ⋮) re-snapshots it explicitly, after a warning that packing
  progress restarts.
- **Replacing** a plan that has progress (anything packed or ever saved) requires confirmation.
  Removing or replacing a plan never deletes saved history, and removing a plan cancels its pending
  reminder (a posted reminder for tomorrow is dismissed and the schedule is re-evaluated).
- **Deleting a template** keeps every dated plan and history entry (they hold their own copies);
  the plan just loses its template link, so the template can no longer be chosen or used for
  “Update from template”.
- **Revisions.** Every change to a session (toggle, Start Fresh, Update from template) increments
  `revision`. Saving records `savedRevision = revision`. Status is derived:
  `savedRevision == revision` → Saved; saved earlier but changed → **Needs review** (a new revision
  must be saved before it shows as saved complete again); otherwise In progress / Not started.
- **Saving** is atomic (one Room transaction) and serialized by a mutex; the button is also
  disabled while saving, so duplicate taps cannot write two entries. An unchanged result —
  same revision, or identical content to the session’s latest saved snapshot — is not duplicated.
- **Start Fresh** clears packed states of the selected session after confirmation; history and
  template items are untouched.
- History entries are immutable snapshots (`PackingHistoryEntity` + `HistoryItemEntity`), trimmed
  to the latest 100.

## Reminders

- One configurable daily time, initially **disabled**. Delivery is approximate.
- Notifies only when **tomorrow** has a planned pack that is not saved complete.
  Title “LunchPack”, body “Your packing list for tomorrow is ready to review.” — generic text only,
  `VISIBILITY_PRIVATE` with a generic public version, so item names/notes never reach the lock
  screen. Tapping opens tomorrow’s planned pack.
- `POST_NOTIFICATIONS` is requested on Android 13+ **only when the user turns reminders on**. If
  denied, planning and packing keep working, Settings shows the blocked state and offers an optional
  link to the system notification settings.
- Inexact one-shot `AlarmManager.setAndAllowWhileIdle` with an explicit, immutable
  `PendingIntent` to a non-exported receiver. No `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`, no
  foreground service, push messaging, wake-lock permission or battery-exemption request.
- Before posting, the callback re-checks: reminders enabled, callback matches the current schedule,
  delivered on the scheduled day (stale previous-day deliveries are skipped, no backlog),
  notifications allowed, tomorrow’s plan exists and is not saved complete.
- Rescheduled after every delivery, plan changes, reminder changes, reboot (`BOOT_COMPLETED`),
  manual time / time-zone changes (`TIME_SET`, `TIMEZONE_CHANGED`) and app update
  (`MY_PACKAGE_REPLACED`); the system-events receiver is exported only for those protected
  broadcasts.
- **Force-stop limitation:** Android cancels all alarms of a force-stopped app and does not deliver
  `BOOT_COMPLETED` to it until it is opened again. After a force stop, reminders resume the next
  time LunchPack is launched.

## Dates

Planned dates are stored as ISO local dates (`yyyy-MM-dd`), history timestamps as absolute
instants (epoch millis). “Today”, “Tomorrow” and reminders use the device’s current time zone;
a time-zone change never rewrites chosen plan dates. Date labels refresh at midnight, on resume and
on clock/time-zone broadcasts. Unplanned days are not treated as missed tasks.

## Architecture

```
app/src/main/java/com/egrmeister/lunchpack/
  LunchPackApp.kt        Application, manual DI (AppContainer), DateTicker
  MainActivity.kt        single activity, edge-to-edge, notification deep link
  domain/                pure Kotlin: models, rules, validation, PackingService,
                         ReminderPolicy/ReminderEngine, injected AppClock / IdGenerator
  data/                  Room entities, DAOs, AppDatabase (+ Migrations), LunchRepository,
                         SettingsStore (DataStore)
  reminders/             AlarmManager + notification gateways, broadcast receivers
  ui/                    Compose screens (home, packs, week, review, history, settings),
                         Navigation Compose root, customized Material 3 theme
```

- Kotlin, Jetpack Compose, customized Material 3, Navigation Compose, ViewModel + `StateFlow`,
  `collectAsStateWithLifecycle`, coroutines, Room (KSP), DataStore. Manual dependency injection.
- All business rules live in `PackingService`, which runs on a `PackingDataSource` interface: Room
  in the app, an in-memory implementation in unit tests — the tests exercise the real rules.
- Database work never runs on the main thread (Room suspend/Flow APIs, `Dispatchers.IO` for
  `clearAllTables`). Transactions wrap assignment, saving, replacement and reset.
- Room schema is exported to `app/schemas/`; `AppDatabase` registers explicit migrations and has no
  destructive fallback.
- Portrait, landscape, tablets and resizable windows (navigation bar on compact widths, navigation
  rail ≥ 600 dp). Edge-to-edge with safe-drawing insets, display cutouts and `imePadding` for the
  keyboard. System bars stay visible; no immersive mode, no keep-screen-on.
- Predictive Back: `android:enableOnBackInvokedCallback="true"`; the editor uses `BackHandler` only
  while there are unsaved edits. Sessions are persisted on every tap, so leaving never loses work.
- Accessibility: every compartment exposes a description (“Main compartment, 2 of 3 packed: …”),
  the checklist is a full equivalent of the illustration (checkbox rows with name, compartment and
  note), touch targets ≥ 48 dp, `sp` text that scales, AA-contrast navy/teal on cream, reduced
  animation option.

## Toolchain (pinned)

| Component | Version |
| --- | --- |
| JDK | 17 (Temurin in CI) |
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin / Compose compiler plugin | 2.2.0 |
| KSP | 2.2.0-2.0.2 |
| Compose BOM | 2025.07.00 |
| Room | 2.7.2 · DataStore 1.1.7 · Navigation 2.9.3 · Lifecycle 2.9.2 |
| compileSdk / targetSdk / minSdk | **36 / 36 / 26** |
| Build tools (CI) | 36.0.0 |

Versions live in `gradle/libs.versions.toml`. Every library the code imports is declared as a
direct dependency in `app/build.gradle.kts`.

### API 36 / Android 16 behavior changes handled

- Edge-to-edge is enforced for targetSdk 35+: the app draws edge-to-edge (`enableEdgeToEdge`) and
  pads content with safe-drawing / IME insets.
- Predictive back is on by default for targetSdk 36: `OnBackInvokedCallback` is enabled and
  `BackHandler` is used instead of overriding `onBackPressed`.
- Orientation/resizability restrictions are ignored on large screens in Android 16: the app does not
  lock orientation and adapts its layout by window width.
- Exact alarms are not used; notification permission is runtime-requested (Android 13+).
- No `JobScheduler`/foreground-service work that Android 16 quotas would affect.

## Build commands

```bash
./gradlew testDebugUnitTest          # focused unit tests
./gradlew lintRelease                # release lint (aborts on errors)
./gradlew assembleDebug              # debug APK (debug key, applicationId suffix .debug)
./gradlew assembleRelease bundleRelease   # signed release APK + AAB (needs signing credentials)
```

Release artifacts:

- APK: `app/build/outputs/apk/release/app-release.apk` (local install / testing only)
- AAB: `app/build/outputs/bundle/release/app-release.aab` (**the only file uploaded to Google Play**)
- R8 mapping (when minified): `app/build/outputs/mapping/release/mapping.txt`

### Signing

The release build type explicitly uses `signingConfigs.getByName("release")` (PKCS12). Credentials
come from environment variables (CI) or a git-ignored `keystore.properties` (local):

```
storeFile=/absolute/path/to/lunchpack-release.p12
storePassword=…
keyAlias=…
keyPassword=…
```

If any credential is missing, every release packaging/signing task fails. There is no fallback to
the debug key. The keystore, passwords, Base64 and any signing notes are excluded by `.gitignore`
and are never printed by CI.

GitHub repository secrets (Settings → Secrets and variables → Actions):

| Secret | Content |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the PKCS12 keystore (one line) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password (equal to the store password for PKCS12) |

### CI (`.github/workflows/android.yml`)

Committed wrapper + JDK 17 + SDK 36 → wrapper validation → unit tests → release lint → decode
keystore to `$RUNNER_TEMP` → signed `assembleRelease bundleRelease` → `apksigner verify
--print-certs` (fails on errors, `CN=Android Debug`, or an unexpected signer) → `jarsigner -verify`
on the AAB + same signer as the APK → packaged and merged manifest permission allowlist → native
library inventory + `zipalign -c -P 16` (and ELF `LOAD` alignment if any `.so` appears) → artifact
sizes → upload verified APK/AAB/report → upload schemas, lint reports and mapping → remove signing
material. A valid self-signed upload certificate is accepted. No emulator test is required in CI.

## Privacy, offline storage and backup

- No `INTERNET` or `ACCESS_NETWORK_STATE`; no networking library, account, backend, Firebase, ads,
  analytics, payments or cloud services. The only permissions are `POST_NOTIFICATIONS` (optional
  reminders) and `RECEIVE_BOOT_COMPLETED` (reminder recovery); the manifest also removes a list of
  others defensively, and CI checks the merged and packaged manifests against this allowlist.
- Data lives in app-private storage: Room database `lunchpack.db` and DataStore
  `lunchpack_settings`.
- `android:allowBackup="false"`, `fullBackupContent` (≤ Android 11) and `dataExtractionRules`
  (Android 12+) exclude everything from cloud backup and device-to-device transfer.
- Production logs contain only reminder decisions — never item names or notes.
- A bundled Privacy page is in Settings.

## 16 KB page-size findings

LunchPack’s dependencies (Compose, Room with the framework SQLite driver, DataStore Preferences,
Navigation, Lifecycle, coroutines) ship no native code. CI lists every `.so` in the release APK and
AAB (including transitive dependencies) and records the result in `release-report.md`; the expected
result is “No native .so libraries”, so ELF 16 KB alignment does not apply. CI still verifies APK
zip alignment with `zipalign -c -P 16`. Targeting API 36 alone is not treated as proof — see the
pending runtime check below.

## Verification log

| Check | Result |
| --- | --- |
| Domain unit tests (37) — packed toggling & progress, compartments, snapshots, favorites, replacement & template deletion, partial save & revisions, duplicate-save prevention, week boundaries, reminder eligibility & stale callbacks, permission denial | Pass locally on the JVM; CI runs them on every push |
| Signed non-minified release (APK + AAB), apksigner / jarsigner, no `CN=Android Debug` | Pending — first CI run |
| Permission allowlist (merged + packaged) | Pending — first CI run |
| Native libraries / 16 KB | Pending — first CI run |
| R8 + resource shrinking, repeated checks, mapping preserved | Pending — enabled after the non-minified release is green |
| Release artifact size | Pending — see `release-report.md` in the CI artifact |
| `adb install` of the signed release APK + `adb logcat` | **Pending** — no Android device/emulator was reachable from the build environment |
| Offline launch, templates, compartments, favorites, week, history, reminders, reboot recovery, process recreation, accessibility (TalkBack, font scale), rotation, reset | **Pending** — on-device |
| Runtime check on a 16 KB page-size environment | **Pending** — e.g. Android 15+ 16 KB emulator image |

### Local device verification (to run on a Mac with a device or emulator)

```bash
adb install -r app-release.apk
adb shell am start -n com.egrmeister.lunchpack/.MainActivity
adb logcat --pid=$(adb shell pidof com.egrmeister.lunchpack) | grep -E "LunchPack|AndroidRuntime"
adb shell getconf PAGE_SIZE          # 16384 on a 16 KB image
adb reboot                           # then confirm the reminder is rescheduled
```

Record device model, Android version, artifact size and results in the table above.
