# SnapMind

Android capture app for users with ADHD. Detects a screenshot anywhere on the device, or
receives shared content, and offers a sub-3-second path to attach a note before the thought
is lost. OCR runs afterwards; items resurface through a deliberately low-pressure reminder
engine.

- `spec.md` — full specification and the task order for implementation
- `docs/spike-results.md` — hardware measurements from the Task 0 spike
- `CLAUDE.md` — working agreement for AI-assisted development

## Status

Task 1 baseline: builds, launches, empty screen. Nothing else is implemented yet.

## Build

Requires JDK 21 and Android SDK 35.

```
./gradlew assembleDebug
```
