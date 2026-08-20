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

Verified working: AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, JDK 21, compileSdk 35, minSdk 26.
KSP everywhere — do not introduce kapt.

If a dependency version needs bumping, bump it in `gradle/libs.versions.toml` only. No
hardcoded versions in `build.gradle.kts`.

## Anti-overwhelm rules are product requirements, not suggestions

`spec.md` §7 caps reminders at one notification per day, three items per digest, three
appearances per item. §7.3 forbids badges, streaks, and unread counts outright. If a change
would touch any of that, stop and ask.

## Test device

Honor 90 Lite, MagicOS 9.0, Android 15 (API 35). Samsung and a partial-photo-access case are
still unverified — see `spec.md` §12.
