*[Česká verze](README.cs.md)*

# SnapMind

An Android capture app for people with ADHD.

Take a screenshot anywhere on your phone, or share something into the app, and SnapMind gives
you a sub-three-second path to attach a note before the thought is gone. It reads the text
from the image afterwards, keeps everything on the device, and brings things back later
through a reminder engine designed not to nag.

Everything runs locally. There are no accounts, no cloud sync, and no network calls except
fetching a title and thumbnail for links you share.

## Why it is built the way it is

Most capture apps fail the same way: they turn into a pile of unfinished items that makes you
feel worse every time you open them. SnapMind treats that as the primary design constraint,
not a polish item.

- **One notification a day, at most.** Not one per item.
- **Three items per digest, maximum.** Oldest unresolved first.
- **Nothing to show means nothing is sent.** No "you're all caught up" message.
- **An item appears in at most three digests**, then moves to a quiet archive. It is never
  deleted and stays searchable.
- **No badges, no streaks, no unread counts, no red dots.** These are prohibited outright,
  not merely absent — see `spec.md` §7.3.

The one reward in the app fires when you tick something off, lasts about half a second, and
leaves no record. It rewards the action, never the state, because every banned pattern above
punishes absence.

## What it does

- **Screenshot capture.** A foreground service watches MediaStore and posts a notification
  with a direct-reply field, so the note can be written from the lock screen.
- **Share Sheet capture.** Images and text shared from any app.
- **Link enrichment.** A shared link gets its title and thumbnail, so the card is still
  identifiable a week later.
- **On-device OCR.** ML Kit, run off the capture path — you never wait on it.
- **On-device classification.** Regex for dates, amounts, phone numbers and order codes;
  keyword heuristics for a coarse category. No network, no account.
- **Manual and custom categories.** The classifier guesses; you correct it, and your own
  category names become filters.
- **Swipe to settle.** A vertical tab on the card edge; drag it left to mark an item done.
- **Search and retrospect.** Over your notes and the OCR text, including the archive.
- **Six colour palettes**, each in light and dark.

## Documentation

| File | What is in it |
|---|---|
| `spec.md` | Full specification, implementation order, and every decision with its reasoning |
| `CLAUDE.md` | Working agreement for AI-assisted development, and the locked dependency chain |
| `docs/spike-results.md` | Hardware measurements from the Task 0 risk spike |

`spec.md` is authoritative. It records not only what the app does but why each alternative was
rejected, usually because it was measured and failed.

## Status

Version 1.1, database schema v4. Tasks 1 through 8 are implemented. Currently being prepared
for its first Google Play release.

Paid functionality is not in this build: the Play Billing code is wired and running, but its
UI is hidden behind a build flag and there is no product to buy. The decision on whether a
paid tier ever ships is scheduled for 31 March 2027.

## Build

Requires JDK 21 and Android SDK 36.

The repository currently has no Gradle wrapper, so build from Android Studio:
**Build → Assemble Project**.

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.9.3 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.20 |
| KSP | 2.1.20-1.0.32 |
| Hilt | 2.58 |
| Room | 2.7.2 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

**These versions are one locked chain.** Moving any of them alone breaks the build — the
reasoning for each is in `CLAUDE.md` under *Build environment*. In particular, Hilt cannot go
to 2.59 or higher without first migrating to AGP 9.

To build with the billing UI visible: `-Psnapmind.billingUi=true`.

## Device notes

Developed and tested on an Honor 90 Lite running MagicOS 9.0 (Android 15).

Aggressive OEM power management is the app's main adversary, and it fails quietly: calls
return normally and nothing happens. Three consequences shaped the architecture.

- **No overlay window.** `WindowManager.addView` from a background service renders nothing on
  MagicOS and throws no exception, so the feature was cut rather than shipped as a setting
  that silently does nothing.
- **No timers in services.** Doze deferred `delay()` by up to three hours and WorkManager by
  nearly two. All scheduling goes through `AlarmManager`.
- **Onboarding has to walk the user into system settings** for the battery exemption,
  autostart, and disabling hibernation. Without those, detection stops after the first reboot.

## Architecture

Kotlin, Jetpack Compose, single Activity. Clean Architecture with MVVM and use cases, Hilt for
DI, Room for storage, Coroutines and Flow throughout. KSP everywhere; kapt is not used.
