# SnapMind — Technical Specification & Implementation Plan

> **Status:** v3. Task 0 is complete. Every decision below that was previously "verify on
> hardware" is now settled by measurement.
>
> **Audience:** AI coding agent (Claude Code). Read this file fully before starting Task 1.

### Task 0 outcome (Honor 90 Lite, MagicOS 9.0, Android 15 / API 35)

| Finding | Consequence |
|---|---|
| Overlay from a background service renders nothing. `addView` returns without throwing; the OEM "background pop-up" permission does not help. | **`OverlayQuickCapture` is cut from v1.** Notification capture is the only path. |
| Foreground service survives overnight and is never killed, with a battery exemption granted. | Ship, but onboarding must walk the user through the exemption. |
| `delay()` inside the service is deferred by Doze for up to ~3 hours. `ContentObserver` delivery is **not** — a screenshot after 7h idle was detected in 357 ms. | Detection is reliable. |
| **v1.1 correction.** WorkManager is deferred too. A one-shot job set for 11:00 ran at 12:45; the original "WorkManager was unaffected" claim came from ContentObserver delivery and boot, never from measured timed work. `AlarmManager.setAndAllowWhileIdle` fires within ~5 minutes. | All time-based behaviour uses AlarmManager. See §7.4. |
| `specialUse` FGS starts from `BOOT_COMPLETED` on API 35 — **but only if MagicOS autostart is enabled for the app.** Without it the receiver never fires. | `BootReceiver` ships, and autostart becomes a required onboarding step, not an optional one. |
| **Task 3 field finding:** reinstalling the app silently reset every runtime permission to denied, and nothing in the UI indicated it. | Confirms §5.3: permission state must be re-checked on every `onResume`, with a persistent banner when anything is missing. |
| One screenshot produces 3 `ContentObserver` fires. | Debounce plus id-dedup is mandatory, as specified. |
| MediaStore write lag reached 99 s while the process was frozen. | Staleness threshold raised to 120 s, plus a catch-up scan (§6.2). |
| Screenshots live in `Pictures/Screenshots/` on this ROM. | `LIKE '%Screenshots%'` confirmed correct; Samsung's `DCIM/Screenshots` still to verify. |
| With Do Not Disturb on, the notification does not appear as heads-up — it lands silently in the shade. | Real capture-path gap; see §6.3. |

---

## 1. Overview

SnapMind is an Android app for users with ADHD. It captures visual context the moment it
appears — a screenshot taken anywhere on the device, or content shared into the app via the
system Share Sheet (Instagram, browser, etc.) — and gives the user a **sub-3-second path to
attach a note** before the thought is lost. It then runs OCR on the image, persists everything
locally, and surfaces items later through a deliberately low-pressure reminder engine.

**Core design constraint:** the capture path must be fast and must never fail silently. Every
other feature is secondary to that.

### 1.1 What is explicitly out of scope for v1

- Cloud sync, accounts, backup
- Sharing items out of the app
- Search beyond simple text matching
- Any form of streak, badge, counter, or gamification (see §7.3)
- Floating overlay capture — cut after Task 0, see §11

---

## 2. Architecture & Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin (JVM target 17) |
| UI | Jetpack Compose, single-Activity |
| Architecture | Clean Architecture + MVVM |
| Async | Coroutines + Flow |
| DI | Dagger Hilt |
| Storage | Room |
| Background | Foreground Service, MediaStore ContentObserver, WorkManager |
| OCR | Google ML Kit Text Recognition (bundled model) |
| Min SDK | 26 |
| Target SDK | 35 |

### 2.1 Build configuration requirements

- Use a **Gradle version catalog** (`gradle/libs.versions.toml`). No hardcoded versions in
  `build.gradle.kts`.
- Use **KSP**, not kapt, for both Room and Hilt. Kapt roughly doubles incremental build time
  on this project shape.
- Enable `room.schemaLocation` and commit generated schemas — needed for migrations later.
- Use the ML Kit **bundled** text recognition artifact
  (`com.google.mlkit:text-recognition`), not the Play-Services-delivered variant. The
  unbundled variant can fail on first use while the model downloads, which breaks the very
  first capture a user ever makes.

---

## 3. Risk register — read before implementing

These are the known failure points. The implementation order in §8 exists because of them.

| # | Risk | Mitigation |
|---|---|---|
| R1 | ~~Overlay cannot be shown from background on OEM ROMs.~~ **Confirmed on MagicOS 9.0.** | **Closed by cutting the feature.** Notification capture is the only path in v1. |
| R2 | Aggressive OEM battery management. **Measured:** the service is not killed, but it *is* frozen for long stretches; only ContentObserver delivery still wakes it. | Onboarding must secure the battery exemption. No feature may depend on in-service timers. |
| R3 | Android 14+ partial photo access (`READ_MEDIA_VISUAL_USER_SELECTED`) means the ContentObserver sees nothing for screenshots the user did not explicitly grant. | Detect partial-grant state and surface a blocking explainer; do not fail silently. |
| R4 | `ContentObserver` fires multiple times per single screenshot. **Measured: consistently 3 fires.** | Deduplicate by MediaStore `_ID`; see §6.2. |
| R7 | Do Not Disturb suppresses the heads-up, so the capture prompt is invisible until the user opens the shade. **Task 5 field finding:** on MagicOS the app must additionally be added to the DND allow-list, not just granted policy access. | See §6.3. Surfaced in onboarding rather than silently failing. |
| R8 | **New, and the most dangerous of the set.** Android app hibernation ("Manage unused apps") revokes permissions and stops background work for apps the user has not opened recently. SnapMind is *designed* not to be opened — that is the whole point — so the system will eventually classify it as abandoned and switch it off. | Onboarding must offer to disable hibernation for this app (`Intent.ACTION_APPLICATION_DETAILS_SETTINGS`, or the unused-app-restrictions API where available). The §5.3 banner is the safety net, but it only fires once the user opens the app. |
| R5 | `foregroundServiceType="specialUse"` requires a manifest `<property>` justification and is scrutinised at Play review. **Verified working from `BOOT_COMPLETED` on API 35.** | Declared correctly from the start; justification string kept in the manifest. |
| R6 | ComposeView hosted in `WindowManager` crashes unless three ViewTree owners are set manually. | See §6.4. Non-negotiable checklist item. |

---

## 4. Folder structure

```
app/src/main/java/com/app/snapmind/
├── SnapMindApp.kt                          # @HiltAndroidApp, Configuration.Provider for WorkManager
├── di/
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   ├── ServiceModule.kt
│   ├── CaptureModule.kt                    # Binds the selected QuickCapturePresenter
│   └── RepositoryModule.kt
├── data/
│   ├── local/
│   │   ├── SnapMindDatabase.kt
│   │   ├── Converters.kt                   # NEW — TypeConverter for CaptureSource
│   │   ├── dao/
│   │   │   └── CapturedItemDao.kt
│   │   └── entity/
│   │       └── CapturedItemEntity.kt
│   ├── mediastore/
│   │   └── ScreenshotQuery.kt              # NEW — isolated MediaStore query + dedup logic
│   ├── ocr/
│   │   └── MlKitOcrAnalyzerImpl.kt
│   ├── prefs/
│   │   └── SettingsDataStore.kt            # NEW — reminder window, quiet hours, capture mode
│   └── repository/
│       └── CapturedItemRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── CapturedItem.kt
│   │   ├── CaptureSource.kt                # Enum: SCREENSHOT, SHARE_SHEET
│   │   ├── ReminderPolicy.kt               # NEW — see §7.3
│   │   └── PermissionState.kt              # NEW — see §5.3
│   ├── repository/
│   │   └── CapturedItemRepository.kt
│   ├── service/
│   │   ├── OcrAnalyzer.kt
│   │   └── QuickCapturePresenter.kt        # CHANGED — replaces FloatingOverlayManager
│   └── usecase/
│       ├── ProcessCapturedImageUseCase.kt
│       ├── SaveItemAndScheduleUseCase.kt
│       ├── GetPendingRemindersUseCase.kt
│       ├── BuildReminderDigestUseCase.kt   # NEW — anti-overwhelm batching
│       └── ResolvePermissionStateUseCase.kt # NEW
├── presentation/
│   ├── theme/
│   │   └── Color.kt, Theme.kt, Type.kt
│   ├── main/
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt
│   │   └── MainScreen.kt
│   ├── onboarding/                         # NEW — see §5.3
│   │   ├── OnboardingViewModel.kt
│   │   └── OnboardingScreen.kt
│   └── components/
└── service/
    ├── observer/
    │   └── MediaStoreObserver.kt
    ├── foreground/
    │   └── ScreenshotObserverService.kt
    ├── capture/
    │   ├── NotificationQuickCapture.kt     # The only presenter in v1
    │   └── QuickCaptureReplyReceiver.kt    # Handles RemoteInput result
    ├── boot/
    │   └── BootReceiver.kt                 # NEW — was missing despite the permission
    └── worker/
        └── ReminderWorker.kt
```

---

## 5. Manifest, permissions, and onboarding

### 5.1 Permissions

```xml
<!-- Reading screenshots -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- Background observer -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Restart after reboot -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### 5.2 Manifest components

**Service** — the `foregroundServiceType` attribute and the `<property>` child are both
required on Android 14+. Omitting the property throws at service start.

```xml
<service
    android:name=".service.foreground.ScreenshotObserverService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="screenshot_capture_assist" />
</service>
```

**MainActivity** — must handle both share intents:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="image/*" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

**BootReceiver** — restarts the observer service after reboot:

```xml
<receiver
    android:name=".service.boot.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

**QuickCaptureReplyReceiver** — `android:exported="false"`, receives the `RemoteInput` payload.

### 5.3 Permission onboarding (previously missing entirely)

The app needs up to five grants, two of which cannot be requested inline and must deep-link
into Settings. Dropping the user into a raw permission dialog storm will lose them. Build an
explicit onboarding flow.

`PermissionState` models each item as `Granted | Denied | PermanentlyDenied | NotApplicable`:

| Step | How it is requested | Blocking? |
|---|---|---|
| Notifications (`POST_NOTIFICATIONS`, API 33+) | Runtime dialog | Yes — the whole reminder engine depends on it |
| Media images | Runtime dialog. On API 34+ check for *partial* grant and explain why full access is needed | Yes for screenshot detection |
| Battery optimization exemption | `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (open the settings list, do **not** use the direct-request action — Play policy restricts it) | Effectively yes — measured as the difference between a usable and an unusable app |
| OEM autostart (Honor/Huawei "App launch", Samsung "Never sleeping apps") | Cannot be detected or requested. Show a manufacturer-specific instruction card when `Build.MANUFACTURER` matches a known-restrictive OEM | **Effectively yes on Honor/Huawei** — measured in Task 3: without it `BOOT_COMPLETED` never reaches the app and the observer stays dead after every reboot |
| Do Not Disturb exception | `NotificationManager.isNotificationPolicyAccessGranted()`, then `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`. **On MagicOS the app also has to be added to the DND allow-list manually** — policy access alone is not enough | No — but explain that captures are silent under DND without it |
| App hibernation disabled | `PackageManager.isAutoRevokeWhitelisted()` where available, otherwise route to app settings. **Critical for this app specifically:** it is meant to run unattended, so hibernation will eventually trigger and silently revoke everything | Effectively yes — see R8 |

Onboarding rules:

- One permission per screen, each with a one-sentence reason in plain language.
- The app must remain usable (Share Sheet capture works) even if screenshot detection is
  declined. Never hard-gate the whole app behind a permission the user refused.
- Re-check permission state on every `onResume` of `MainActivity`; a revoked permission must
  produce a persistent, dismissible banner on `MainScreen`, not a crash or a silent no-op.

---

## 6. Core interfaces and implementation notes

### 6.1 Domain models

```kotlin
enum class CaptureSource { SCREENSHOT, SHARE_SHEET }

@Entity(tableName = "captured_items")
data class CapturedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String?,              // null for text-only shares
    val mediaStoreId: Long?,            // NEW — dedup key for screenshots, null otherwise
    val extractedText: String,
    val userNote: String,
    val source: CaptureSource,
    val timestamp: Long,
    val isProcessed: Boolean,           // OCR completed
    val requiresReminder: Boolean,
    val remindersSent: Int = 0,         // NEW — drives the escalation cap in §7.3
    val resolvedAt: Long? = null        // NEW — user acted on it; excluded from digests
)
```

`Converters.kt` must provide the `CaptureSource` ↔ `String` TypeConverter and be registered
via `@TypeConverters` on the database class. Store the enum **name**, not the ordinal —
ordinals break the moment a value is inserted into the enum.

Add a unique index on `mediaStoreId` to make dedup a database-level guarantee, not just
application logic.

### 6.2 Screenshot detection

Register a `ContentObserver` on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with
`notifyForDescendants = true`.

`ScreenshotQuery` handles the actual resolution. Requirements:

- Query with selection on `RELATIVE_PATH LIKE '%Screenshots%'` (API 29+); fall back to
  `DATA LIKE '%Screenshots%'` below 29.
- Sort by `DATE_ADDED DESC`, limit 1.
- Ignore any result whose `DATE_ADDED` is more than **120 seconds** old. The original 10 s was
  measured as far too tight: while the process was frozen, MediaStore write lag reached 99 s
  and a real screenshot was discarded as stale.
- Deduplicate against the last handled `_ID`, held both in memory and in the database index.
  The observer will fire two to four times for one screenshot; only the first must produce UI.
- Debounce with a ~300 ms window before querying, so the file write has settled.
- **Catch-up scan.** Doze can freeze the process for hours. On every service start, and on
  every `ACTION_SCREEN_ON`, query for screenshots newer than the highest `mediaStoreId`
  already stored and process any that were missed. Cap the catch-up at the 3 most recent so
  a long freeze cannot produce a burst of prompts — that would violate §7.
- On API 34+, if `READ_MEDIA_VISUAL_USER_SELECTED` is granted but `READ_MEDIA_IMAGES` is not,
  the observer will report changes it cannot resolve. Detect this and route the user to the
  permission explainer rather than looping on empty queries.

Note: `Activity.ScreenCaptureCallback` (API 34) only fires while your own activity is
visible. It is useless for this app. Do not attempt to use it.

### 6.3 Quick capture abstraction

Task 0 settled this: notification only. The interface is kept anyway, as a seam for a future
overlay or Quick Settings tile, but v1 binds exactly one implementation.

```kotlin
interface QuickCapturePresenter {
    /** Presents the note-taking affordance for a freshly captured item. */
    suspend fun present(itemId: Long, imageUri: String?, source: CaptureSource)
    fun dismiss(itemId: Long)
    fun isPresenting(): Boolean
}
```

**`NotificationQuickCapture` (default).** A high-priority notification with an image preview
(`BigPictureStyle`), a `RemoteInput` direct-reply action for the note, and a second action to
open the item. No special permission beyond `POST_NOTIFICATIONS`, no OEM restrictions, works
on every device. The reply arrives at `QuickCaptureReplyReceiver`, which writes the note and
cancels the notification. Use a dedicated notification channel with `IMPORTANCE_HIGH` so it
heads-up.

Two measured constraints on this path:

- **The reply action is hidden until the notification is expanded.** On the collapsed
  heads-up the user sees the preview but no input field, which costs the sub-3-second target.
  Try `setStyle(NotificationCompat.BigTextStyle())` or posting pre-expanded, and measure
  again. If neither works, the tap target must open a lightweight capture Activity instead.
- **Do Not Disturb suppresses the heads-up entirely** (R7). The notification still posts and
  the reply still works, but silently. Detect DND via
  `NotificationManager.getCurrentInterruptionFilter()` and offer the policy-access exception
  during onboarding.

### 6.4 Hosting Compose in WindowManager — deferred, not deleted

Not built in v1. Kept here so the knowledge is not lost if an overlay is attempted for a
device class where it works. The `ComposeView` requires all three ViewTree owners to be set
before `addView`, or Compose throws immediately:

```kotlin
composeView.setViewTreeLifecycleOwner(owner)
composeView.setViewTreeViewModelStoreOwner(owner)
composeView.setViewTreeSavedStateRegistryOwner(owner)
```

Implement a single small class that satisfies `LifecycleOwner`, `ViewModelStoreOwner`, and
`SavedStateRegistryOwner`, drive its lifecycle to `RESUMED` on show and `DESTROYED` on hide,
and clear the `ViewModelStore` on destroy or you leak a ViewModel per capture.

`hiltViewModel()` will not work in this context — there is no `NavBackStackEntry`. Obtain the
ViewModel through an `EntryPointAccessors` entry point or construct it manually via an
assisted factory.

### 6.5 OCR

```kotlin
interface OcrAnalyzer {
    /** Extracts text from the given image URI. Returns empty string on failure. */
    suspend fun analyzeText(uri: Uri): String
}
```

OCR must run **off** the capture path. Save the item immediately with `isProcessed = false`,
show the capture UI, and run recognition in a `CoroutineWorker` afterwards. Never make the
user wait on ML Kit to write a note.

Downscale images wider than ~2000 px before recognition; full-resolution screenshots from
modern phones are slow to process and offer no accuracy benefit.

---

## 7. Reminder engine ("anti-overwhelm")

This was undefined in v1. The following is the concrete v1 policy — it is a product decision,
not an implementation detail, so it is specified here rather than left to the implementer.

### 7.1 Batching

- **At most one reminder notification per day.** Never one per item.
- Delivered in a user-configured window, default **18:00**, with a ±30 min flex.
- The digest contains **at most 3 items**, chosen oldest-unresolved-first.
- If there is nothing to show, send nothing. No "you're all caught up" notification.

### 7.2 Escalation cap

- An item may appear in at most **3 digests total** (`remindersSent <= 3`).
- After the third, it is moved to a quiet "Archive" section in the app — still searchable,
  never notified again. It is never deleted.
- Any user interaction with an item (opening it, editing the note) sets `resolvedAt` and
  removes it from the digest pool.

### 7.3 Prohibited patterns

Do not implement, and reject any later request to add without an explicit product decision:
unread badge counts, streaks, "you have 47 unprocessed items" copy, red dots, or escalating
notification frequency. These are actively harmful for the target user and are the reason
this engine exists.

### 7.4 Scheduling

**`AlarmManager.setAndAllowWhileIdle`, re-armed after each firing.** Never `delay()`, never a
`Handler`, and — corrected in v1.1 — never WorkManager either.

Three mechanisms were tried on the Honor:

| Mechanism | Result |
|---|---|
| `delay()` in the service | Deferred up to 3 hours by Doze (Task 0) |
| `PeriodicWorkRequest` | Interval measured from last enqueue, not wall clock. Opening the app at 15:00 moved an 18:00 reminder to 15:00 |
| One-shot `WorkManager` | Correct time, wrong delivery: an 11:00 reminder arrived at 12:45 |
| `AlarmManager.setAndAllowWhileIdle` | 20:00 reminder arrived at 20:05. **Shipped.** |

Inexact by design, so expect minutes of drift. `setExactAndAllowWhileIdle` would be precise
but needs `SCHEDULE_EXACT_ALARM`, which Play restricts to alarm clocks and calendars — not a
trade worth making for a notification whose own copy says "whenever you have a moment".

Alarms do not survive a reboot, so `BootReceiver` re-arms them.

One alarm at a time, never one per item: the number of scheduled wake-ups must not scale with
the number of captures. Use `@HiltWorker`,
`HiltWorkerFactory`, and implement `Configuration.Provider` in `SnapMindApp` with
on-demand WorkManager initialization (remove the default initializer in the manifest).

---

## 8. Implementation tasks

Complete each task fully, including its definition of done, before starting the next.

### Task 0 — Risk spike (throwaway)

Create a scratch branch or separate module. Single Activity, no architecture, no tests. The
only goal is to answer four questions on real target hardware:

1. Does a foreground service survive 8 hours with the screen off, without a battery exemption?
   With one?
2. Does the `ContentObserver` reliably fire for a screenshot within 1 second? How many times
   per screenshot?
3. Can a `SYSTEM_ALERT_WINDOW` overlay be shown from that background service? On which
   devices does it silently fail?
4. Does a direct-reply notification appear as a heads-up and deliver the reply reliably?

**Definition of done:** a short written result for each question recorded in
`docs/spike-results.md`, and a decision on whether `OverlayQuickCapture` ships in v1.
**Delete the spike code afterwards.** Do not evolve it into the app.

### Task 1 — Project setup

Version catalog, KSP, Hilt, Compose, Room, WorkManager, ML Kit. Full manifest per §5.
`SnapMindApp` with `@HiltAndroidApp` and `Configuration.Provider`. Empty `MainActivity` that
builds and launches.

**Done when:** clean build passes, app installs and shows an empty screen, `./gradlew
lint` is clean.

### Task 2 — Data layer

`CapturedItemEntity` (§6.1), `CaptureSource`, `Converters`, unique index on `mediaStoreId`,
`CapturedItemDao` (insert / getAllFlow / getUnresolvedForDigest / update / delete),
`SnapMindDatabase`, repository interface in domain plus implementation in data, Hilt modules.
`SettingsDataStore` for reminder window and quiet hours.

**Done when:** instrumented Room tests cover insert, dedup-collision on `mediaStoreId`, and
the digest query ordering.

### Task 3 — Capture pipeline, notification path

`OcrAnalyzer` implementation. `ScreenshotQuery` with dedup and debounce per §6.2.
`MediaStoreObserver`. `ScreenshotObserverService` as a foreground service with the correct
type and property. `NotificationQuickCapture` and `QuickCaptureReplyReceiver`.
`ProcessCapturedImageUseCase` running OCR asynchronously off the capture path. `BootReceiver`.

**Done when:** taking a screenshot anywhere on the device produces exactly one notification
within 1.5 s, replying to it persists the note, and OCR text appears in the database
afterwards. Verify after a reboot.

### Task 4 — Onboarding and main screen

`PermissionState`, `ResolvePermissionStateUseCase`, `OnboardingScreen` per §5.3.
`MainActivity` handling `ACTION_SEND` for `image/*` and `text/plain`. `MainViewModel` and
`MainScreen` listing saved items with the Archive section. Permission-revoked banner.

**Done when:** a fresh install walks through onboarding cleanly, sharing an Instagram post
into the app creates an item, and revoking media permission from Settings surfaces the banner
rather than breaking anything.

### Task 5 — Reminder engine

`ReminderPolicy`, `BuildReminderDigestUseCase`, `ReminderWorker` as a daily
`PeriodicWorkRequest` per §7. Settings UI for the reminder window and quiet hours.

**Done when:** unit tests cover the batching rules — max 3 items, max 3 appearances per item,
silence when the pool is empty, exclusion of resolved items — and the worker is verifiable
with `WorkManagerTestInitHelper`.

### Task 6 — cut

Overlay capture was removed after Task 0. Do not implement it. See §11.

---

## 9. Testing expectations

Not exhaustive coverage, but these specific areas are where this app will break:

- **Unit:** digest batching rules, dedup logic, permission state resolution.
- **Instrumented:** Room DAO queries and the unique-index collision behaviour.
- **Manual checklist**, run before each release: screenshot capture on API 30 / 33 / 35;
  capture after reboot; capture with the screen having been off for several hours; partial
  photo grant on API 34+; share from Instagram, Chrome, and Gallery; overlay permission
  revoked mid-session.

## 10. Conventions

- All user-visible strings in `strings.xml`. No hardcoded text in composables.
- Domain layer has zero Android framework imports except `android.net.Uri` in `OcrAnalyzer`
  — prefer a `String` uri in domain and convert at the boundary if it can be done cleanly.
- Every `catch` either recovers or logs with enough context to diagnose from a user report.
  Silent `catch {}` is prohibited on the capture path.
- Timestamps are epoch millis, UTC. Format only at the presentation layer.


---

## 11. Cut features and why

### 11.1 Overlay capture (`OverlayQuickCapture`)

**Cut in v1.** Task 0 established that on MagicOS 9.0 a `SYSTEM_ALERT_WINDOW` added from a
background service renders nothing: `WindowManager.addView` returns without throwing, the
window never appears, and granting the OEM's separate background pop-up permission does not
change it. The same window appears correctly when the app is in the foreground, which is
useless for this app's purpose.

Because the failure is silent, no runtime fallback could reliably detect it. The feature is
removed rather than shipped as a setting that quietly does nothing on an unknown fraction of
devices.

The `QuickCapturePresenter` interface, the ViewTree-owner notes in §6.4, and this section
remain so the work is recoverable if a future device class is verified to support it.

### 11.2 Paid add-ons — architectural decision, not a v1 feature

No paid functionality ships in v1. This section exists because the decision below is
expensive to reverse later and cheap to honour now.

**One app, gated by entitlement. Never a separate "Pro" APK.** Two listings would mean two
installs, two rating pools, and a data migration when a user upgrades. Add
`domain/billing/EntitlementProvider.kt` in Task 4:

```kotlin
interface EntitlementProvider {
    /** Emits the current entitlement state. Hardcoded to true for all of v1. */
    fun isPro(): Flow<Boolean>
}
```

Bind a constant `true` implementation for now. Any future paid feature is wrapped in that
condition rather than branching the codebase.

**Hard product boundary.** Capture and reminders stay free, permanently. The app's entire
premise is that a thought is not lost between noticing it and writing it down; a paywall at
that moment contradicts §7 and the anti-overwhelm design the app exists for. Paid surface
area, if any, belongs in retrieval and organisation — bulk export, richer search, longer
history — never in capture.

**Platform constraints**, for reference when the time comes: digital goods must use Google
Play Billing; a Play Console account and linked payments profile are required; the choice
between one-time purchase and subscription is made in Play Console, not in code, so it does
not affect the architecture above.

### 11.3 Deferred to v1.1 or later

- Tiered on-device classification of captured text
- Calendar integration
- Reward mechanics — must be designed against §7.3, not around it

## 12. Open questions for the next hardware pass

1. Samsung screenshot path — expected `DCIM/Screenshots`, not yet confirmed on a real device.
2. Whether a pre-expanded or `BigTextStyle` notification exposes the reply field without a
   user tap (§6.3).
3. Partial photo access (`READ_MEDIA_VISUAL_USER_SELECTED`) behaviour — the spike device
   reported `partial=true` alongside full access, so the two states were never cleanly
   separated. Re-test with "Select photos" only.
4. How long hibernation takes to trigger in practice, and whether the whitelist survives an
   app update. This determines whether the §5.3 banner is sufficient or whether the app needs
   to actively re-prompt (R8).
