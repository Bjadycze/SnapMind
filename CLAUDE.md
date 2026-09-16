# Instructions for Claude Code — SnapMind (production)

## What this repository is

The production SnapMind app. `spec.md` is authoritative; read it before proposing anything.
The Task 0 spike lives in a separate repository (`snapmind-spike`) and is finished — its
results are recorded at the top of `spec.md` and in `docs/spike-results.md`.

## Working agreement

- **Implement one task at a time**, in the order given in `spec.md` §8. Do not start Task N+1
  until Task N meets its stated definition of done.
- **Report the actual build output.** Never claim a task is complete without having run
  `./gradlew assembleDebug` and seen it pass.
- If the spec is ambiguous or wrong, say so and stop. Do not guess and implement.

## Release status — read before touching build files or Settings

The app is being prepared for its first Google Play release. The living checklist lives in
the Claude project as `claude/release-stav.md`; this section is the part Claude Code needs.

| Decision | What it means for the code |
|---|---|
| **Billing UI is hidden in this release.** | The subscription section in Settings must not render in release builds. Gate it behind `BuildConfig.BILLING_UI_ENABLED` (default `false` for release, `true` for debug). |
| **Billing code stays wired.** | `BillingManager`, `PlayEntitlementProvider` and `di/BillingModule` are untouched and keep running. Only UI visibility changes. Re-enabling is a one-line flag change after the 31 Mar 2027 decision. |
| **Settings gets an "O aplikaci" section instead.** | Two rows: the GitHub repository URL (clickable, ACTION_VIEW) and the app version. The URL is its own label — no "Zdrojový kód" caption above it. |
| **The GitHub link must never sit under a heading about payment or support.** | If GitHub Sponsors or any donate path is ever added, the in-app link has to be removed — linking to payment outside Play violates Google Play Payments policy. |
| **Target API 36 done, edge-to-edge not verified.** | compileSdk/targetSdk are on 36 and both builds pass. Android 16 enforces edge-to-edge for targetSdk 36 with no opt-out, and that has NOT been checked on the device yet — content may sit under the status and navigation bars. |

`buildFeatures { buildConfig = true }` is currently absent from `app/build.gradle.kts` and
must be added before any `BuildConfig` flag can exist.

## Decisions that are already made — do not revisit

These came out of hardware measurement, not preference. Changing them silently undoes the
spike.

| Decision | Why |
|---|---|
| **No overlay / `SYSTEM_ALERT_WINDOW`.** | On MagicOS 9.0 `addView` from a background service renders nothing and throws no exception. Cut in v1. See `spec.md` §11.1. |
| **No `delay()` or `Handler` for anything time-based in a service.** | Doze deferred those by up to 3 hours. Use WorkManager. |
| **Staleness threshold is 120 s, not 10 s.** | MediaStore write lag hit 99 s while the process was frozen. |
| **Debounce ~300 ms plus dedup by MediaStore `_ID`.** | One screenshot produces exactly 3 ContentObserver fires. |
| **OCR runs off the capture path.** | The user must never wait on ML Kit to write a note. |
| **One app gated by `EntitlementProvider`, never a separate Pro APK.** | See `spec.md` §11.2. |
| **Enums stored by name, not ordinal.** | Ordinals break when a value is inserted. |

## Build environment

Verified working (assembleDebug and assembleRelease both green, 14 Sep 2026):
AGP 8.9.3, Gradle 8.11.1, Kotlin 2.1.20, KSP 2.1.20-1.0.32, Hilt 2.58, Room 2.7.2,
JDK 21, compileSdk 36, targetSdk 36, minSdk 26. KSP everywhere — do not introduce kapt.

**These versions are one locked chain, not independent numbers.** The API 36 migration
forced every one of them, in this order:

| Link | Why it cannot move alone |
|---|---|
| AGP 8.9.3 | Lowest AGP that supports compileSdk 36. |
| Gradle 8.11.1 | Required minimum for AGP 8.9. |
| Kotlin 2.1.20 | 2.0.21 is only documented up to Gradle 8.10; 2.1.0 stops at 8.10 too. |
| KSP 2.1.20-1.0.32 | KSP is versioned `<kotlin>-<ksp>`; both halves must match the Kotlin version. |
| Hilt 2.58 | 2.52 cannot read Kotlin 2.1.20 metadata. 2.59+ **requires AGP 9**, so 2.58 is the ceiling while we stay on AGP 8. |
| `ksp.useKSP2=true` | KSP 1.0.x still defaults to KSP1, which throws NPE in `KSDeclarationImpl.simpleName` while Dagger 2.58 validates `@Module` objects. Set in `gradle.properties`. |
| Room 2.7.2 | 2.6.1 crashes under KSP2 with "unexpected jvm signature V". |

Do not bump Hilt to 2.59 or higher without migrating to AGP 9 first — that is a separate
piece of work with its own breaking changes.

If a dependency version needs bumping, bump it in `gradle/libs.versions.toml` only. No
hardcoded versions in `build.gradle.kts`.

On Windows, a failed `lintVitalAnalyzeRelease` that reports a locked `.jar` under
`app/build/intermediates/lint-cache` is a Gradle daemon holding the file, not a code error.
Close Android Studio, delete `app/build`, reopen.

## Anti-overwhelm rules are product requirements, not suggestions

`spec.md` §7 caps reminders at one notification per day, three items per digest, three
appearances per item. §7.3 forbids badges, streaks, and unread counts outright. If a change
would touch any of that, stop and ask.

## Localisation — English is the default, Czech is a translation

`res/values/strings.xml` is **English** and is the fallback for every locale.
`res/values-cs/strings.xml` is Czech. Before 15 Sep 2026 the default file was a mix of both
languages, which is why English users saw Czech labels.

- **Every new user-visible string goes into BOTH files in the same change.** A key added only
  to `values/` shows English to a Czech user; a key added only to `values-cs/` is invisible to
  everyone else. There is no partial version of this.
- **No user-visible text is hardcoded in a composable** (`spec.md` §10). Filter tabs and
  palette names were, and it took an English build to notice.
- **The in-app language switch does not call `recreate()`.** `MainActivity` wraps the Compose
  tree in `CompositionLocalProvider(LocalContext / LocalConfiguration)` built from
  `Context.withAppLocale(...)`, so changing the language redraws in place and the user stays on
  the screen they were on. `attachBaseContext` still applies the language at cold start.
- **Code outside an Activity reads the language from the SharedPreferences mirror**
  (`AppLanguagePrefs`), not from DataStore: notification builders and `attachBaseContext` have
  nowhere to suspend. The mirror is written before the DataStore write, never after.
- **The locale wrapper wraps the real Activity**, via `ContextWrapper`, so `findActivity()` and
  `startActivity()` keep working through `LocalContext`. That is deliberate — replacing it with
  a plain application context would compile and then fail at runtime the first time a row in
  Settings tries to open a link.

## Test device

Honor 90 Lite, MagicOS 9.0, Android 15 (API 35). Samsung and a partial-photo-access case are
still unverified — see `spec.md` §12.
