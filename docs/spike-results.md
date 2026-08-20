# Spike results

Fill one block per device. Record the result **before** and **after** granting battery
exemptions — the difference is the real finding.

---

## Device: Honor 90 Lite

- MagicOS version: 9.0
- Android version / API: 15 / 35
- Test date:

> API 35 means this device also validates the Android 14/15 tightenings: enforced
> `foregroundServiceType`, partial photo access, and the BOOT_COMPLETED FGS restrictions.

### Q1 — service survival

| Scenario | Before exemptions | After exemptions |
|---|---|---|
| 1 hour, screen off | | |
| Overnight (8h) | | |
| Survives reboot (BootReceiver) | | |
| `specialUse` FGS allowed from BOOT_COMPLETED on API 35 | | |
| Time of death, if killed | | |

### Q2 — screenshot detection

| Metric | Result |
|---|---|
| Screenshots taken | 10 |
| Detected (`DETECT` lines) | |
| `FIRE` lines per screenshot (min/typical/max) | |
| Detection latency (ms) | |
| `relativePath` reported | |
| False positives (`STALE` that should have been detected, or vice versa) | |

### Q2b — partial photo access (API 34+, testable on this device)

| Check | Result |
|---|---|
| Granting "Select photos" instead of "Allow all" — does the observer still fire? | |
| Does `resolveLatest()` return `MISS`, `Failed`, or a row? | |
| Is the partial state distinguishable from a full denial in `PERM state`? | |

### Q3 — overlay from background

| Check | Result |
|---|---|
| `addView` threw an exception | |
| Overlay actually visible on screen | |
| Visible when triggered from another app in the foreground | |
| Extra OEM permission required, and where it lives | |

### Q4 — direct-reply notification

| Check | Result |
|---|---|
| Appears as heads-up | |
| Image preview renders | |
| Reply delivered (`REPLY OK`) | |
| Still works after 8h idle | |

---

## Device: (Samsung)

<!-- copy the blocks above -->

---

## Decision

- Ships `OverlayQuickCapture` in v1? **yes / no / opt-in only**
- Reasoning:
- Changes required to `spec.md`:
