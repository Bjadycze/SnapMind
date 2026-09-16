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
| R5 | `foregroundServiceType="specialUse"` requires a manifest `<property>` justification and is scrutinised at Play review. **Verified working from `BOOT_COMPLETED` on API 35, and confirmed end-to-end on device: the service restarts after a real reboot.** The autostart exemption of R2 is a prerequisite — without it the receiver never runs. | Declared correctly from the start; justification string kept in the manifest. |
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
│   ├── ClassifyModule.kt                   # Task 7 — binds ContentClassifier
│   └── RepositoryModule.kt
├── data/
│   ├── local/
│   │   ├── SnapMindDatabase.kt
│   │   ├── Converters.kt                   # TypeConverter for CaptureSource
│   │   ├── dao/
│   │   │   └── CapturedItemDao.kt
│   │   └── entity/
│   │       └── CapturedItemEntity.kt
│   ├── mediastore/
│   │   └── ScreenshotQuery.kt              # isolated MediaStore query + dedup logic
│   ├── ocr/
│   │   └── MlKitOcrAnalyzerImpl.kt
│   ├── prefs/
│   │   └── SettingsDataStore.kt            # reminder window, quiet hours, palette, theme
│   └── repository/
│       └── CapturedItemRepositoryImpl.kt
├── domain/
│   ├── classify/                           # Task 7 — on-device only, no network
│   │   ├── ContentClassifier.kt            # interface + DetectedCategory + Signals
│   │   ├── Tier0RegexClassifier.kt
│   │   ├── Tier1KeywordClassifier.kt
│   │   └── ChainedContentClassifier.kt
│   ├── model/
│   │   ├── CapturedItem.kt
│   │   ├── CaptureSource.kt                # Enum: SCREENSHOT, SHARE_SHEET
│   │   ├── ReminderPolicy.kt               # see §7.3
│   │   └── PermissionState.kt              # see §5.3
│   ├── repository/
│   │   └── CapturedItemRepository.kt
│   ├── service/
│   │   ├── OcrAnalyzer.kt
│   │   └── QuickCapturePresenter.kt        # replaces FloatingOverlayManager
│   └── usecase/
│       ├── ProcessCapturedImageUseCase.kt
│       ├── SaveItemAndScheduleUseCase.kt
│       ├── GetPendingRemindersUseCase.kt
│       ├── BuildReminderDigestUseCase.kt   # anti-overwhelm batching
│       └── ResolvePermissionStateUseCase.kt
├── presentation/
│   ├── theme/
│   │   └── Palette.kt, Theme.kt, Type.kt
│   ├── main/
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt
│   │   └── MainScreen.kt
│   ├── onboarding/                         # see §5.3
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
    │   └── BootReceiver.kt
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
    val mediaStoreId: Long?,            // dedup key for screenshots, null otherwise
    val extractedText: String,
    val userNote: String,
    val source: CaptureSource,
    val timestamp: Long,
    val isProcessed: Boolean,           // OCR completed
    val requiresReminder: Boolean,
    val remindersSent: Int = 0,         // drives the escalation cap in §7.3
    val resolvedAt: Long? = null,       // user acted on it; excluded from digests
    val resolution: String? = null,     // DONE / DISCARDED, §11.8, schema v2
    val detectedCategory: String? = null,   // Task 7, schema v3
    val detectedDateMillis: Long? = null    // Task 7, schema v3
)
```

`Converters.kt` must provide the `CaptureSource` ↔ `String` TypeConverter and be registered
via `@TypeConverters` on the database class. Store the enum **name**, not the ordinal —
ordinals break the moment a value is inserted into the enum. The same rule applies to
`detectedCategory`, which stores `DetectedCategory.name`.

Add a unique index on `mediaStoreId` to make dedup a database-level guarantee, not just
application logic.

**Schema versions:** v2 added `resolution` (§11.8), v3 added `detectedCategory` and
`detectedDateMillis` (§11.14). Migrations are always explicit — `fallbackToDestructiveMigration`
is prohibited, there are real captures on the test device.

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
  the reply still works, but silently.

  **Decided in v1.1: leave it that way.** Do Not Disturb is the user stating they do not want
  to be interrupted, and an app that overrides that is telling them its business outranks
  their decision — which is how apps end up muted permanently. Nothing is lost either: the
  capture is saved, the reply still works, the item appears in the list. It simply does not
  jump the screen.

  The escape hatch already exists and belongs to the user, not to us: granting policy access
  and adding SnapMind to the system allow-list makes captures break through. That is offered
  during onboarding and left switched off. Do not add an in-app "ignore Do Not Disturb"
  toggle; it would be the same override wearing a settings label.

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

Classification (§11.14) runs in the same worker, immediately after the OCR text is written —
never on the capture path.

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

### Task 1 — Project setup ✅ shipped

Version catalog, KSP, Hilt, Compose, Room, WorkManager, ML Kit. Full manifest per §5.
`SnapMindApp` with `@HiltAndroidApp` and `Configuration.Provider`. Empty `MainActivity` that
builds and launches.

**Done when:** clean build passes, app installs and shows an empty screen, `./gradlew
lint` is clean.

### Task 2 — Data layer ✅ shipped

`CapturedItemEntity` (§6.1), `CaptureSource`, `Converters`, unique index on `mediaStoreId`,
`CapturedItemDao` (insert / getAllFlow / getUnresolvedForDigest / update / delete),
`SnapMindDatabase`, repository interface in domain plus implementation in data, Hilt modules.
`SettingsDataStore` for reminder window and quiet hours.

**Done when:** instrumented Room tests cover insert, dedup-collision on `mediaStoreId`, and
the digest query ordering.

### Task 3 — Capture pipeline, notification path ✅ shipped

`OcrAnalyzer` implementation. `ScreenshotQuery` with dedup and debounce per §6.2.
`MediaStoreObserver`. `ScreenshotObserverService` as a foreground service with the correct
type and property. `NotificationQuickCapture` and `QuickCaptureReplyReceiver`.
`ProcessCapturedImageUseCase` running OCR asynchronously off the capture path. `BootReceiver`.

**Done when:** taking a screenshot anywhere on the device produces exactly one notification
within 1.5 s, replying to it persists the note, and OCR text appears in the database
afterwards. Verify after a reboot.

### Task 4 — Onboarding and main screen ✅ shipped

`PermissionState`, `ResolvePermissionStateUseCase`, `OnboardingScreen` per §5.3.
`MainActivity` handling `ACTION_SEND` for `image/*` and `text/plain`. `MainViewModel` and
`MainScreen` listing saved items with the Archive section. Permission-revoked banner.

**Done when:** a fresh install walks through onboarding cleanly, sharing an Instagram post
into the app creates an item, and revoking media permission from Settings surfaces the banner
rather than breaking anything.

### Task 5 — Reminder engine ✅ shipped

`ReminderPolicy`, `BuildReminderDigestUseCase`, `ReminderWorker` per §7 — scheduled with
`AlarmManager`, not `PeriodicWorkRequest` (§7.4). Settings UI for the reminder window and
quiet hours.

**Done when:** unit tests cover the batching rules — max 3 items, max 3 appearances per item,
silence when the pool is empty, exclusion of resolved items — and the worker is verifiable
with `WorkManagerTestInitHelper`.

### Task 6 — cut

Overlay capture was removed after Task 0. Do not implement it. See §11.

### Task 7 — Tier 0 and Tier 1 classification ✅ shipped (v1.1)

On-device only, no network, no entitlement check. `domain/classify/` with a
`ContentClassifier` interface and two implementations chained by confidence.

- **Tier 0, regex.** Dates and times, URLs, phone numbers, amounts, flight and order codes.
  Deterministic, testable, and the only tier the reminder engine is allowed to depend on.
- **Tier 1, on-device heuristics.** Coarse category from keyword and structure signals:
  recipe, event, purchase, article, contact, unknown. Wrong answers are cheap here because
  the category only orders the list; nothing acts on it.

Results go in new nullable columns (`detectedCategory`, `detectedDateMillis`), migrated at
version 3. Runs in the existing OCR worker, not on the capture path (§6.5).

**Done:** 26 unit tests pass, including Czech and English samples per pattern; migration 2→3
verified by upgrading over an existing install with real captures on the Honor; no code path
reaches the network. Implementation decisions and field findings in §11.14.

### Task 8 — `EntitlementProvider` becomes real ← next

Replace the constant `true` binding from §11.2 with Google Play Billing. No feature changes
in this task — it exists on its own so the billing path can be verified before anything
depends on it.

Non-negotiable behaviours:

- Entitlement state is cached locally and **fails open on a network error**. A user who paid
  must never be locked out because Play was unreachable on a train.
- When entitlement lapses, previously generated AI output **stays visible and searchable**.
  It was paid for. Only new generation stops.
- Capture, reminders, search over own text, and export of own data are never gated (§11.2).

**Done when:** license testers can buy, cancel, and restore; airplane mode does not revoke
access; a lapsed account still sees its existing summaries.

### Task 9 — Tier 3 cloud classification (paid)

The paid surface. Sends captured **text** — never the image — to a cloud LLM for semantic
titling, summarisation, and natural-language search.

Privacy rules, which outrank the feature:

- **Opt-in, off by default**, with a plain-language explanation of what leaves the device.
- **Text only.** Screenshots contain banking apps, medical results, and private messages;
  shipping the image raises the stakes far beyond what a nicer title is worth.
- **Per-item veto.** Any item can be excluded, and exclusion is permanent for that row.
- A visible indicator on any item whose text was sent. The user must be able to tell, later,
  what left the device.

Cost rules: one call per item maximum, result cached in the row, never re-sent on edit unless
the user asks. Batch overnight rather than on capture — nothing here is urgent, and it keeps
the capture path free of network latency.

**Done when:** the free tier is fully usable with Tier 3 disabled, opt-out is honoured
retroactively for future generation, and no code path can send an image.

---

## 9. Testing expectations

Not exhaustive coverage, but these specific areas are where this app will break:

- **Unit:** digest batching rules, dedup logic, permission state resolution, Tier 0 date
  parsing and Tier 1 category selection.
- **Instrumented:** Room DAO queries and the unique-index collision behaviour.
- **Manual checklist**, run before each release: screenshot capture on API 30 / 33 / 35;
  capture after reboot; capture with the screen having been off for several hours; partial
  photo grant on API 34+; share from Instagram, Chrome, and Gallery; overlay permission
  revoked mid-session; **upgrade over the previous install** so every migration actually runs.
- **Test doubles count as production code.** A fake repository that no longer matches its
  interface only fails when someone runs the tests, which can be several tasks later — see
  §11.14.

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

- Calendar integration

Tiered classification and reward mechanics are no longer deferred: rewards shipped in §11.8,
classification is specified as Tasks 7–9 with the paywall boundary in §11.12 and the shipped
Tier 0/1 implementation in §11.14.

### 11.4 Filtering the list (v1.1, shipped)

The flat list became unreadable once it held more than a screenful. Three filter tabs replace
it: Aktivní (`resolvedAt == null`), Vyřízené (`resolvedAt != null`), Vše. Filtering happens in
`MainScreen` over the existing `Flow<List<CapturedItem>>` — no DAO or schema change.

The visual model is a browser tab strip: the active tab and the list panel share one surface
and are joined by a moving connector block, so the current filter reads as a physical position
rather than a highlight.

Decisions worth preserving:

- **The panel colour is duplicated.** `FilterTabs.ConnectorColor` and the panel background in
  `MainScreen` must hold the same value or a seam appears between them. Change both together.
- **Square corners at the joint.** The active tab has square bottom corners, the panel square
  top corners, and the connector overlaps into the panel by `ConnectorOverlap`. Rounding
  either side produced a visible notch.
- **`topStart` is conditional.** The panel's top-left corner is square only while Aktivní is
  selected, because that tab sits directly above it.
- **`tween`, not `spring`.** Spring animation on the connector overshot enough to read as
  playful, which is noise on a screen whose job is to reduce noise.
- All tunables live in one block at the top of `FilterTabs.kt`.

Settled items grey out as a whole block — container colour and content alpha both animate, not
just the label. Partial fading read as a rendering glitch rather than a state.

### 11.5 Swipe between details (v1.1, shipped)

`ItemDetailDialog` is a `HorizontalPager` over the list currently on screen, so swiping moves
between items **within the active filter**. Swiping out of a filter would silently contradict
what the user just chose.

The dialog takes `items: List<CapturedItem>` plus an index rather than a single item, and the
callbacks carry an id. It is declared after the filters are computed in `MainScreen` so the
pager always receives exactly what the list shows. The `Surface` needs an explicit centring
`Box` inside each page — the pager fills the width, the dialog does not.

### 11.6 Capturing saved reels — investigated, not viable as asked

The ask: when the user taps Save inside Instagram, YouTube or Facebook, SnapMind captures the
post without a Share Sheet detour.

**Not achievable.** An in-app save is a network call to that platform's own servers. It emits
no broadcast, no MediaStore write, and no notification, so there is nothing on the device for
an observer to see. The only mechanism that could observe it is an `AccessibilityService`
watching for a tap on a node labelled Save — which is outside Play's accessibility policy, is
killed by MagicOS power management (R2), and would grant SnapMind read access to the entire
screen contents of every app. Not worth it for this feature.

What remains available, in order of cost:

1. **Share Sheet** (already shipped). One extra tap versus the platform's own save button.
2. **YouTube Data API.** Liked videos and playlists are readable with OAuth, so YouTube saves
   specifically *could* be polled. Instagram and Facebook expose no equivalent — the Basic
   Display API is retired.
3. **Link enrichment.** A shared reel currently arrives as text with no thumbnail and nothing
   for OCR to read, which makes the card unidentifiable a week later — the exact failure the
   thumbnail was added to fix (§11.4). Fetching the Open Graph image would close that gap and
   benefits every shared link, not just reels.

Item 3 is the one worth doing; it improves what already exists rather than chasing a capture
path the platform does not expose.

### 11.7 Link enrichment (v1.1, shipped)

Shared links are enriched after the item is saved, never before: a slow or blocked request
must not delay the capture, and a failure leaves the item exactly as it would have been.

- `LinkMetadataFetcher` tries YouTube's public oEmbed endpoint first and falls back to Open
  Graph scraping. **Verified on device: YouTube returns title and thumbnail reliably.**
  Instagram and Facebook answer a logged-out request with a login page often enough that they
  should be treated as best-effort.
- Only the first ~120 KB of a page is read. Open Graph tags sit in the head; whole pages are
  megabytes.
- A browser `User-Agent` is required — without one most sites return a stub with no OG tags.
- **No schema change.** The URL goes in `extractedText` (search material, same as OCR output),
  the title in `userNote` while the user has not written their own, the thumbnail in
  `imageUri`. `isProcessed = true` keeps ML Kit away from an `https` URL it cannot read.
- Source is shown as a coloured dot plus a word (`LinkPlatform`), not a brand logo. Logos are
  licensed assets and would need re-cutting at every rebrand.
- **Instagram works in practice**, contrary to the expectation above: a logged-out request
  returns usable OG tags for public reels. Verified on device.
- HTML entities must be decoded numerically, not from a named table. Instagram writes captions
  as `&#x11b;`-style escapes, so Czech text arrives visibly broken otherwise. `&amp;` is
  decoded last, or `&amp;#39;` becomes a live entity.

### 11.8 Settle reward (v1.1, shipped)

Two buttons in the detail dialog remove an item from Aktivní, and the difference between them
is the whole point:

- **Fajfka (`Check`) = Vyřízeno.** Rewards.
- **Koš (`Delete`) = Odloženo.** Silent — no haptic, no bounce.

Rewarding both would train deletion instead of follow-through. `resolution` (`DONE` /
`DISCARDED`, nullable) records which, migrated at version 2 with existing resolved rows read
as `DONE`.

Guiding rule, and the reason this does not violate §7.3: **reward the action, never the
state.** Every banned pattern — badges, streaks, counts — stays visible while the user does
nothing, and so punishes absence. This exists for roughly half a second and leaves no record.

Field findings, both of which cost a round trip:

- **The reward cannot live on the list card.** In the Aktivní filter a settled item leaves the
  list the instant it settles, so the animation plays on a composable already being disposed —
  behind a dialog, at that. It has to be on the button that was pressed.
- **`View.performHapticFeedback` does nothing on MagicOS.** `CONFIRM` is silently ignored, the
  same failure shape as §11.1. Use `Vibrator` / `VibrationEffect` directly. `VIBRATE` permission
  required.
- **The rare variant must differ in kind, not degree.** A 1.18 versus 1.35 scale peak on a 40dp
  button is about seven pixels over 200ms and is not perceptible. A second bounce and a
  different vibration rhythm are. Intensity is what a phone in a pocket conveys worst; rhythm
  is what it conveys best.

Variable reward (1 in `RareOdds`, currently 6) is deliberate. It is also the slot-machine
mechanism, so the terms are kept fair: the bet is on doing something genuinely useful, and the
ordinary outcome costs nothing.

### 11.9 Settle animation variants (v1.1, shipped)

The bounce was one animation. It would have stopped registering once it became familiar, which
is the known failure mode of any fixed reward. `SettleButton` now draws one of seven at random
on every settle, split into the same two tiers as the vibration pattern (§11.8):

- **Common** (~5 in 6): `BOUNCE` (the original scale bump), `POP` (a quick compress and
  rebound), `TILT` (a small `rotationZ` lean, direction randomised, and back), `NUDGE` (a small
  sideways `translationX` shift and back).
- **Rare** (~1 in 6): `FLIP` (`rotationY` through 360°), `SPIN` (`rotationZ` through 360° with a
  simultaneous scale bump), `DOUBLE_POP` (the original two-stage bounce).

Implementation notes:

- One `Modifier.graphicsLayer { }` carries `scaleX`/`scaleY`, `rotationZ`, `rotationY`, and
  `translationX` together, driven by four separate `Animatable`s. Stacking `Modifier.scale` and
  a rotate modifier instead would apply the transforms in a fixed order, which fights `SPIN`
  where scale and rotation animate at once.
- Each variant is a `private suspend fun` that leaves every `Animatable` it touched back at its
  rest value before returning; `SettleButton` awaits the whole animation and only then calls
  `onSettled()`, so nothing is ever seen mid-motion.
- Tier selection reuses the same `rare` draw already used for `settleVibration` — the pattern
  and the animation tier agree by construction, never independently rolled.
- Selection within a tier is a uniform `entries.filter { it.tier == tier }.random()`. There is
  no ordering, weighting, or rarity between variants inside a tier — a completable set is a
  streak wearing a different hat, and §7.3 already bans that. Commented in code so it stays
  that way.

Constraints carried over from §11.8, unchanged: every variant stays within its tier's time
budget (~350ms common, ~600ms rare), leaves no persistent state, and never appears on the koš
button.

### 11.10 Search and retrospect (v1.1, shipped)

Searching and looking back are the same screen (`SearchScreen` + `SearchViewModel`), reached
only from the search icon next to Nastavení on `MainScreen` — never opened by the app itself,
never surfaced by a notification.

- **Search.** `CapturedItemDao.search` matches `userNote` or `extractedText` with `LIKE`;
  `extractedText` already holds both OCR output and shared URLs, so one query covers both.
  Archived items (`isArchived`, §7.2) are deliberately not filtered out — this is what finally
  makes the quiet archive reachable, instead of only findable by scrolling. The query is
  debounced 250ms in the ViewModel (`flatMapLatest` over the debounced text) so a `LIKE` isn't
  fired per keystroke.
- **An empty query never hits the database.** Guarded in `SearchViewModel`, not in SQL — an
  unguarded `LIKE '%' || '' || '%'` would silently return the entire table.
- **Retrospect.** A "Vyřízené" switch, visible only while the query is blank, shows items with
  `resolution == DONE`, newest first — filtered client-side from the existing `observeAll()`
  flow rather than a second DAO query, the same way `MainScreen`'s filter tabs already work.
- **The card is shared, not duplicated.** `CapturedItemCard` moved from `MainScreen` to
  `presentation/components/`, unchanged, so both screens render identically. `ItemDetailDialog`
  opens the same way, swipe included, over whichever list is currently on screen.
- **No count, ever** — not on the toggle, not on the search icon, not "N results". Any of those
  turns a list back into a badge (§7.3), which is the one thing this screen exists to avoid
  while still making the archive reachable.

### 11.11 Missing image after gallery deletion (v1.1, shipped)

Deleting the screenshot from the gallery — after SnapMind already saved the row — left the
card's thumbnail permanently blank: coil kept `imageUri` pointing at a `content://` Uri the
`MediaStore` no longer served, so the image slot just rendered nothing where a thumbnail used
to be, indistinguishable from "still loading".

**The row is never deleted for this.** §7.2 is explicit that a captured item is never deleted,
only stops being reminded about — the note and the OCR text are frequently the part worth
keeping, and they routinely outlive the picture (a reminder about a recipe screenshot is still
useful after the screenshot itself is gone). Losing the row because the *image* went missing
would silently violate that guarantee for a failure that has nothing to do with the user's
notes.

- **Detection is `AsyncImage`'s `onError` callback, not a pre-check.** Stat-ing the file before
  rendering would be I/O inside composition, and would still race a user who deletes the image
  between the check and the render — `onError` is the only point that actually knows the load
  failed.
- **`MissingImagePlaceholder`** (`presentation/components/`) is shared by `CapturedItemCard`
  (56dp, matching the thumbnail it replaces) and `ItemDetailDialog` (a fixed 200dp band, since
  there is no intrinsic image size to size a `heightIn(max = …)` box against once the image
  itself is gone). Muted `surfaceVariant` background, `Icons.Default.ImageNotSupported` at
  ~0.4 alpha, no error colour, no exclamation mark: deleting your own screenshot is an
  ordinary, expected action, not a fault the app needs to flag.
- **The failure flag is keyed on `(item.id, item.imageUri)`,** not just the id. Link enrichment
  (§11.7) can fill `imageUri` in after the row already exists; keying on the uri too means a
  stale "failed" flag from a prior uri can never suppress a legitimately new image.
- **"Open in gallery" hides itself once the image has failed to load.** Showing it would open
  either an empty intent or the gallery's own error, which is a worse outcome than not offering
  the button.
- **Text-only shares are unaffected.** The placeholder only triggers on an `imageUri` that
  failed to *load* — an item with `imageUri == null` (a shared link with no thumbnail, or a
  share that never had an image) renders exactly as before.

### 11.12 Where the AI paywall sits — decided before building

§11.2 fixed the rule: capture and reminders are free permanently, paid surface belongs in
retrieval and organisation. Tiered classification is the first feature that has to be split
along that line, so the split is written down before any of it exists.

| Tier | What it does | Runs | Gated |
|---|---|---|---|
| 0 | Regex: dates, URLs, phone numbers, amounts | On device | Never |
| 1 | Coarse category from keywords and structure | On device | Never |
| 2 | *Reserved.* On-device small model, if one ever fits | On device | Undecided |
| 3 | Semantic titles, summaries, natural-language search | Cloud LLM | **Paid** |

**Tier 0 is free because the reminder engine depends on it.** A screenshot of a ticket that
knows its own date produces a better-timed reminder, and reminders are free. Gating Tier 0
would gate reminders through the back door.

**Tier 3 is paid because it costs money per call.** That is the honest reason and the only one
that holds up. It is not made deliberately worse to sell the upgrade, and the free tier has to
stay genuinely usable — the app's premise is that a thought is not lost between noticing it
and writing it down, and a paywall anywhere near that moment contradicts §7.

**Lapsed entitlement never deletes anything.** Summaries already generated stay visible and
searchable. Only new generation stops. Anything else would mean paying for something that can
be taken away, which is not what was sold.

**The image never leaves the device, at any tier.** Text only, opt-in, per-item veto, with a
visible marker on rows that were sent. Screenshots are the most private thing on a phone; that
constraint is not negotiable against product convenience later.

### 11.13 Colour palettes & light/dark mode (v1.1, shipped)

Settings gained two independent choices: a colour palette and a light/dark mode — six palettes
times three modes. The default after the update reproduces the app's pre-existing look
byte-for-byte (Violet, Dark), so this ships as a preference nobody has to notice.

- **`presentation/theme/Palette.kt` is the only source of truth for colour.** `SnapMindPalette`
  holds twelve roles (`accent`, `accentMuted`, `secondary`, `background`, `panel`,
  `surfaceRaised`, `surfaceRaisedHigh`, `settled`, `onSurface`, `onSurfaceFaded`, `onSettled`,
  `outline`); `paletteFor(choice, dark)` resolves one of `PaletteChoice` — `VIOLET` (default),
  `TERRACOTTA`, `DUSTYROSE`, `SAGE`, `COMFORTBEIGE`, `POWDERBLUE` — against `dark: Boolean`. **No
  `Color(0xFF...)` literal is permitted anywhere else under `presentation/`.** Every screen reads
  colour through `LocalSnapMindPalette.current`, or through the Material3 `colorScheme` that
  `SnapMindTheme` builds from it — never a hardcoded hex.
- **Values are generated in OKLCH at a fixed lightness per role**, not picked by eye per palette.
  Switching `PaletteChoice` changes hue, not the measured contrast of any role against the
  surface it sits on — a palette swap can never turn readable text unreadable.
- **`settled` / `onSettled` replace alpha for a done item.** `CapturedItemCard` used to fade a
  resolved item with `.alpha()`; opacity multiplies with whatever sits behind it, so the same
  alpha value read differently on every palette. `settled` and `onSettled` are separate,
  pre-verified roles that hold the palette's own tone while losing saturation — **never alpha,
  never a neutral grey**, which would read as an application error rather than "done" on a
  tonally-coloured palette. The thumbnail is exempt: it stays fully saturated, since it remains
  the only thing that still identifies a settled item at a glance.
- **`SettingsDataStore` persists the choice under the `palette` and `theme_mode` keys**
  (string-backed `PaletteChoice`/`ThemeMode`, parsed with `runCatching { enumValueOf(...) }` so
  an unknown or corrupted stored value falls back instead of crashing the app). Default is
  `PaletteChoice.VIOLET` + `ThemeMode.DARK` — the pre-existing look. `ThemeMode` is `SYSTEM` /
  `LIGHT` / `DARK`; `SYSTEM` resolves against `isSystemInDarkTheme()` where `SnapMindTheme` is
  composed.
- **`SettingsScreen`'s back action goes through `BackHandler`, not only the system back
  gesture.** On the MagicOS 9.0 test device the system back gesture is occasionally swallowed
  before it reaches the app, so an explicit in-app back arrow plus `BackHandler` are used instead
  of relying on the system back stack alone.

### 11.14 Tier 0 a Tier 1 klasifikace (v1.1, shipped)

Task 7 běží na zařízení, bez sítě, v existujícím OCR workeru. `domain/classify/` obsahuje
`ContentClassifier` (rozhraní), `Tier0RegexClassifier`, `Tier1KeywordClassifier` a
`ChainedContentClassifier`; navenek je vidět jen rozhraní, vázané v `di/ClassifyModule.kt`.
Výsledek jde do `detectedCategory` (název enumu, nikdy ordinal) a `detectedDateMillis`,
migrace `MIGRATION_2_3` přidává oba sloupce jako nullable.

Rozhodnutí, která stojí za uchování:

- **Tier 0 nesmí být pravděpodobnostní.** Kategorii vrací jen u jednoznačné kombinace signálů
  (částka + kód → PURCHASE, datum + čas → EVENT, telefon → CONTACT), jinak UNKNOWN a slovo
  přebírá Tier 1. Připomínkový engine smí záviset jen na Tier 0 (§11.12), takže tam nesmí být
  nic, co si "tipne".
- **Datum bez roku se posouvá dopředu.** "15. 3." vyfocené v prosinci míří na březen
  následujícího roku. Bez tohoto pravidla by lístek generoval připomínku v minulosti, kterou
  digest nikdy nezobrazí.
- **Datum bez času míří na poledne, ne na půlnoc.** Půlnoc spadne uživateli na předchozí večer.
- **České měsíce se testují od nejdelšího prefixu.** "července" jinak projde jako "červen" —
  `startsWith("cerven")` je pravda pro obojí. Diakritika se před porovnáním odstraňuje.
- **`\w` nematchuje diakritiku.** Kód objednávky proto nejde hledat jako
  `objedn\w*\W{0,12}(KÓD)`: v "Objednávka AB12345" se `\W` zastaví na "á" a přes "vka" se
  nedostane. Používá se `[\s\S]{0,25}?` a kód musí obsahovat číslici, aby "ORDER CONFIRMED"
  neprošlo jako kód.
- **Telefon a datum se nesmí prolnout.** Telefon je vázaný na devět číslic v českém členění,
  datum na oddělovač `.` nebo `/`, takže "+420 777 123 456" nikdy nevrátí datum.
- **Klasifikace čte OCR text i uživatelovu poznámku dohromady.** Poznámka je často jediné
  místo, kde datum je ("zítra vyzvednout").

Provozní poznámky z aplikace:

- Migrace 2→3 se ověřuje **upgradem přes existující instalaci**, ne čistou instalací — jinak se
  nikdy nespustí a rozbití se pozná až u uživatele s reálnými daty.
- Unit testy odhalily, že `FakeRepository` v `BuildReminderDigestUseCaseTest` byl rozejitý
  s `CapturedItemRepository` (chyběly `updateImageUri`, `markDiscarded`, `search`, byl tam
  neexistující `markResolved`). **Každý nový člen repozitáře patří ve stejném commitu i do
  fake** — testovací zdrojáky se kompilují jen při spuštění testů, takže drift zůstane skrytý
  klidně přes několik tasků.
- 26 unit testů prochází; klasifikace nemá žádnou závislost na Androidu, takže jde testovat
  obyčejným JUnit během sekund.

Co zbývá: kategorie zatím nikde není vidět, jen leží v databázi. Zobrazení na kartě je vědomě
odložené — jakmile se ukáže, je to informace na každém řádku a musí projít testem §7.3
(nesmí z toho být počítadlo ani odznak).

### 11.15 Štítek kategorie a swipe „Hotovo?" (v1.1, shipped)

Kategorie z Task 7 se kreslí jako svislý pruh na pravém okraji karty
(`presentation/components/CategoryEdgeTab.kt`, `CategoryTabWidth = 30.dp`, kartu obaluje
`BoxWithConstraints` a pruh leží přes ni v `matchParentSize` Boxu). Tah pruhu doleva položku
vyřídí, klepnutí je rezervované pro filtr podle kategorie.

Proč pruh a ne odznak v řádku metadat: nesoupeří s poznámkou, roste s výškou karty a nenese
žádnou vlastní barvu. Nic nepočítá (§7.3) — říká, **co** položka je, nikdy kolik jich je.

Rozhodnutí, která stojí za uchování:

- **Gesto je samo odměnou.** Barva, slovo i vibrace se dějí, dokud je prst dole. Po puštění
  nenásleduje žádná animace, a ani následovat nemůže: ve filtru Aktivní karta ze seznamu zmizí
  v okamžiku vyřízení, takže by animace hrála na composable, který se právě ruší — přesně ta
  chyba, kterou §11.8 zapsala u `SettleButton`.
- **Práh je 50 % šířky karty, ne 70 %.** Přes polovinu se palcem jedné ruky nedostaneš bez
  přehmátnutí, a gesto, které chce druhou ruku, je pomalejší než otevřít detail a klepnout na
  fajfku. Rychlý švih (přes 1000 dp/s) potvrdí už na 30 %.
- **Otazník je ukazatel prahu.** Pod prahem „Hotovo?", nad prahem „Hotovo" plným jasem. Práh
  tak nepotřebuje ani čáru, ani ukazatel postupu — obojí by bylo počítadlo.
- **Puštění pod prahem je tiché** a vrací se pružinou. Nevyřízení není chyba, takže nesmí
  vibrovat — stejná logika jako u koše (§11.8).
- **Jedno slabé cvaknutí na prahu nebylo na zařízení cítit.** Práh proto vibruje dvěma
  krátkými pulzy na plné amplitudě, potvrzení jedním delším (40 ms). Rozlišuje je rytmus, ne
  síla — §11.8 už to má napsané a tady se to potvrdilo podruhé.
- **Barva pruhu není palette role.** `surfaceRaisedHigh` **je** barva karty, ve světlých
  paletách je `surfaceRaised` skoro bílá a `panel` splynul také. Pruh proto bere barvu karty
  posunutou k `outline`: `lerp(cardColor, palette.outline, TabTint)`, odladěno na
  **`TabTint = 0.45f`**, plus samostatná dělicí čára `outline` o šířce **1.5 dp**. Žádná nová
  hodnota mimo `Palette.kt` (§11.13) a přežije to výměnu palety.
- **UNKNOWN je prázdné místo, ne slovo „neurčeno".** Výplň má barvu karty, hranici drží
  čárkovaná čára. Do budoucna je to místo pro ručně zapsanou kategorii.
- **Rotace sama nemění naměřenou velikost.** Svislý text potřebuje `Modifier.layout`
  s prohozenými constraints a teprve pak `rotationZ = 90f`; bez toho by se karta roztáhla
  do šířky.

Dvě pasti, které to stály build:

- **Balík je `com.app.snapmind.domain.classify`, i když složka na disku je `classify/`.**
  Import podle cesty ve stromu projektu je omyl.
- **Nepovinné parametry za `onOpen` rozbily volání s koncovou lambdou.** `CapturedItemCard(item)
  { … }` v `SearchScreen` se po přidání `onSettle` a `onFilterCategory` navázalo na poslední
  parametr místo na `onOpen`. Každé volání karty proto používá pojmenované argumenty.

Filtr klepnutím (shipped, stav ve `MainScreen`, ne v ViewModelu — stejně jako záložky §11.4):

- Klepnutí zapne filtr kategorie, druhé klepnutí na stejnou ho zruší.
- **Zapnutý filtr pozná jen podle štítků samotných** — ty jeho kategorie nesou akcent
  (`SelectedTint`). Značka sedí na tom, na co uživatel klepl, ne jinde na obrazovce, a nic
  nepočítá (§7.3).
- **Přepnutí záložky Aktivní/Vyřízené/Vše ruší i filtr kategorie.** Dva filtry naskládané přes
  sebe by daly krátký seznam bez viditelného důvodu.
- **Prázdný výsledek filtr sám zruší** (`LaunchedEffect` nad oběma seznamy). Bez toho vzniká
  past: vyfiltruješ kategorii, poslední položku v ní vyřídíš, seznam je prázdný a nezbude
  štítek, kterým filtr vypnout. Pravidlo o záložkách by to zachránilo, ale musel bys ho znát.
- **Neurčené položky jsou jedna filtrovatelná skupina** pod klíčem
  `UnclassifiedFilterKey = "__unclassified__"` — prázdná kategorie, `UNKNOWN` i hodnota
  uložená budoucí verzí, kterou build nezná. Pro uživatele je to jedna hromádka: to, co ještě
  čeká na zařazení, a přesně tu bude hledat, až se budou kategorie doplňovat ručně. Klíč není
  hodnota `DetectedCategory`, takže nemůže kolidovat se skutečnou kategorií.
- **Prázdný štítek dostane akcent, jen když je zapnutý filtr neurčených.** Jinak zůstává
  prázdný — ale bez té výjimky by z filtru nevedla cesta ven, protože by nebylo na co klepnout.
- **Dotyková plocha zůstala na šířce štítku, ne na 48 dp.** Rozšíření doleva by přesáhlo přes
  text poznámky a bralo by klepnutí, která mají otevřít detail. Na výšku má štítek celou kartu,
  takže cíl je i tak dost velký.

### 11.16 Ruční a vlastní kategorie (v1.1, shipped)

Klasifikátor hádá; tohle je místo, kde se odhad opraví a kde se zařadí to, co neuměl zařadit.
Řádek štítků v detailu položky (`presentation/components/CategoryPicker.kt`): pět vestavěných
kategorií, vlastní kategorie uživatele, „neurčeno" a „+ vlastní" s polem na název. Seznam
vlastních jmen žije v `SettingsDataStore`, mazání je v Nastavení.

- **Nový sloupec `userCategory`, migrace na verzi 4.** `detectedCategory` zůstává záznamem toho,
  co odhadl klasifikátor. Kdyby ho ruční volba přepsala, ztratil by se rozdíl mezi „stroj
  netrefil" a „stroj netipoval". Všude se čte `CapturedItem.effectiveCategory`
  (`userCategory ?: detectedCategory`) — jedno místo, kde se to řeší.
- **Ruční volbu klasifikátor nepřepíše a nepotřebuje k tomu příznak.** `OcrWorker` bere jen
  položky s `isProcessed = false`, takže se ke kategorii po rozpoznání textu už nikdy nevrátí.
- **Zápis jde přes `updateUserCategory`, ne `updateClassification`.** To druhé píše oba sloupce
  najednou a smazalo by rozpoznané datum.
- **Vlastní jméno je uložené jako text a je samo filtrovacím klíčem.** Hodnota, která není
  `DetectedCategory`, se na štítku zobrazí doslova a ve filtru se chová jako každá jiná
  kategorie (§11.15).
- **Smazání nic nezahazuje.** Název se přesune do seznamu „dřív používané" a nabídne se zpět
  při dalším přidávání — nemusí se přepisovat ručně a nevzniknou dvě skoro stejná jména.
  Porovnává se bez ohledu na velikost písmen, prázdný název se ignoruje.
- **Smazat lze jen kategorii, kterou nedrží žádná nevyřízená položka.** Křížek je jinak
  neaktivní a pod názvem je řádek proč. Vyřízené a archivované položky si název ponechají a
  zobrazují ho dál: mizí ze seznamu k nabízení, ne z historie (§7.2).
- **Přejmenování se nedělá.** Přidá se nový název a starý se smaže, jakmile ho nic aktivního
  nedrží — hromadný update řádků a další obrazovka v Nastavení za to nestojí.
- Sekce v Nastavení se objeví, teprve když existuje aspoň jedna vlastní kategorie. Nikde žádné
  počty (§7.3).

Nález mimo zadání: **`markDone` při zápisu poznámky žilo ještě na dvou dalších místech** —
`MainViewModel.updateNote` a `SearchViewModel.updateNote`. Editace poznámky v detailu tedy
položku rovnou vyřídila, stejně jako dřív odpověď na notifikaci (§11.8). Opraveno; §7.2 platí
na všech třech cestách.

**Sdílené odkazy se klasifikují v `EnrichSharedLinkUseCase`, ne v `OcrWorker`** (doplněno
hned po předchozím). Obohacení volá `updateOcrResult`, které nastaví `isProcessed = 1`, a
worker se dívá jen na nezpracované řádky — odkaz by tedy kategorii nedostal nikdy. Klasifikuje
se titulek, poznámka a URL dohromady, takže odkaz na recept skončí jako recept, ne jako obecný
článek. Zapisuje se jen `detectedCategory`; ruční volba v `userCategory` má přednost, takže
opakované sdílení téhož odkazu ji nepřepíše.

### 11.17 Play Billing (Task 8, kód shipped, UI skryté pro první vydání)

`data/billing/BillingManager.kt` obaluje Play Billing 7.1.1, `PlayEntitlementProvider` z něj dělá
jeden boolean a `di/BillingModule` ho váže místo `AlwaysProEntitlementProvider` (ta zůstává
v kódu nenavázaná — dá se dočasně přepnout zpět při vývoji placené funkce bez Play Console).
Zatím nic negatuje: Task 8 záměrně ověřuje platební cestu dřív, než na ní cokoli závisí.

- **Selhává otevřeně.** `BillingManager.entitled` je `null`, dokud Play neodpoví, a `null`
  nikdy neznamená „nemá zaplaceno". Do té doby platí poslední známý stav uložený
  v `SettingsDataStore` (`pro_entitled`). Kdo zaplatil, nesmí přijít o přístup ve vlaku.
- **Předplatné, ale kód přijme i jednorázový nákup.** Dotazuje se na `SUBS` i `INAPP` a za Pro
  považuje cokoli vlastněného, takže „lifetime" varianta je později otázka Play Console, ne kódu.
  Předplatné je zvolené proto, že Tier 3 stojí peníze za každé volání — opakovaný náklad si
  žádá opakovaný příjem.
- **Nepotvrzený nákup Play po třech dnech automaticky vrátí.** `acknowledgePurchase` se volá
  hned, jakmile se objeví nepotvrzený `PURCHASED`. Tohle je jediné místo v celé platební cestě,
  které tiše vezme peníze zpět, když se vynechá.
- **`USER_CANCELED` a ostatní chybové kódy stav nemění.** Zavřený platební dialog není důkaz,
  že předplatné zaniklo.
- **Stav se obnovuje v `MainActivity.onResume`**, stejně jako oprávnění: předplatné může skončit,
  být vráceno nebo obnoveno na jiném zařízení, zatímco appka běží na pozadí.
- **`PRO_PRODUCT_ID = "snapmind_pro"`** musí přesně sedět s ID produktu v Play Console.

### 11.17.1 Billing UI je v prvním vydání skryté (oprava z 14. 9. 2026)

Původní znění téhle sekce říkalo, že *sekce v Nastavení stav ukazuje a nabízí Předplatit*. To
přestalo platit před prvním vydáním v Play a tenhle odstavec je autoritativní.

Produkt `snapmind_pro` v Play Console neexistuje, takže tlačítko Předplatit by nic nekoupilo —
rozbité tlačítko ve veřejné verzi, které Play review může zachytit. Sekce se proto **neodstraňuje,
jen skrývá**:

- `BuildConfig.BILLING_UI_ENABLED` — **`false` v debug i v release.** Obě varianty čtou gradle
  property `snapmind.billingUi` s defaultem `false`; build s viditelným billingem se postaví
  lokálně přes `-Psnapmind.billingUi=true`. Property (místo natvrdo zadaného `false`) je tam
  proto, aby placená cesta šla odladit ve stejné variantě, v jaké poběží u uživatele — jinak
  by ji release prostředí vidělo poprvé až v den zapnutí, současně s R8 a ostrým Play Billingem.
- `BillingManager`, `PlayEntitlementProvider`, `EntitlementProvider` i `di/BillingModule`
  zůstávají navázané a běží dál. Mění se výhradně viditelnost UI; zapnutí je změna flagu.
- Na místě předplatného je v Nastavení sekce **O aplikaci** se dvěma řádky: **adresa GitHub
  repozitáře** (klikací, `ACTION_VIEW`) a **verze** z `BuildConfig.VERSION_NAME`. Adresa je
  sama sobě popiskem — žádný nadpis „Zdrojový kód" nad ní. Žádná zmínka o podpoře, příspěvku
  ani sponzorství.
- **Odkaz na GitHub nesmí nikdy stát pod nadpisem o platbě nebo podpoře.** Jakmile na GitHubu
  vznikne Sponsors nebo jiná cesta k penězům, odkaz z aplikace musí pryč — odkaz vedoucí
  k platbě mimo Play porušuje Google Play Payments policy.

Billing UI se zapne až po rozhodnutí **31. 3. 2027** (práh instalací v `aplikace-dalsi-kroky.md`).
Do té doby je Task 8 hotový v kódu a nedokončený v Play Console.

**Co zbývá k ověření** v Play Console: vytvořit předplatné s ID `snapmind_pro` a základní plán,
přidat licenční testery. Teprve pak dává smysl definition of done z Task 8 — koupit, zrušit,
obnovit, a ověřit, že režim letadlo přístup nevezme.

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
5. Jestli české datumy z reálného OCR (ML Kit občas vrací "15 . 3 ." s mezerami kolem tečky)
   projdou `Tier0RegexClassifier` — v testech ano, na reálných screenshotech zatím jen jeden
   vzorek.