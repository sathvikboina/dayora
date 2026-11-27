# Sonder

**The living memory vault.** A calm, zero-guilt, 100% offline Android diary.

Sonder is built from the Dayora app bible: you collect the days that felt good in a living
aura counter, heavy days rest quietly in a hidden Rain Room, and everything clever —
tone routing, memory threads, semantic rescue — happens on your phone's silicon.

## What works in this build

- **4 honest Day States** — Bright, Warm, Ordinary, Heavy, each with a colour aura *and* a
  non-colour geometric marker (sun spark, filled dot, hollow ring, crescent).
- **Zero-guilt counter** — only counts up, computed live from SQLite, never stored separately.
  No streaks, no red badges, no sad-day scoreboard.
- **10-second capture** — day state, reason, 8 Spark chips, ML-suggested tags, good-day override,
  offline voice input, and Smart Midnight Grace (12–4 AM).
- **Calendar mosaic** — month swipe, colour + shape markers, backdating, future dates disabled,
  heavy-day visibility toggle, pull-down (or the droplet icon) to enter the Rain Room.
- **The Rain Room** — hidden dusk sanctuary, no counter, timeline of heavy days, edit/delete,
  and a *Let it go* action that clears the words but keeps the quiet date.
- **Memory Threads** — automatic people / places / activity tags, searchable, with honest
  pattern cards (minimum 4 matching memories, baseline lift guardrail, “early pattern” label).
- **Semantic Heavy-Day Rescue** — 384-dim on-device embeddings and cosine similarity surface a
  real past bright memory, never a random entry.
- **Memory Whisper** — after 15 good days, Home resurfaces one relevant past memory with a
  30-day cooldown and seasonal preference.
- **Lock** — biometrics or a PBKDF2-hashed PIN, with an eraser fallback if the PIN is lost.
- **Reminders** — one inexact evening nudge via `setAndAllowWhileIdle` (no exact-alarm
  permission), rescheduled after reboot/time/timezone changes; skipped if today is logged.
- **Data ownership** — plain CSV export with DD-MM-YYYY dates, AES-256-GCM encrypted JSON
  backup/restore (PBKDF2 120k iterations), and a delete-everything action.
- **No cloud** — `allowBackup=false`, cloud backup and device-transfer rules block the database,
  DataStore and preferences. No ads, no trackers, no accounts.

All ML is local. Phase 1 uses a fast lexical affect scorer plus a deterministic 384-dim
hashing embedder, stored in Room exactly like the future MiniLM vectors. Swapping in a
quantized LiteRT/ONNX model later requires no schema or UI changes.

## Install the ready-to-use APK

Download **[`apk/Sonder-1.0.apk`](apk/Sonder-1.0.apk)** and open it on your phone
(allow “install unknown apps” for your browser/file manager). It is a minified release
build signed with the standard Android debug key, so it installs directly but is not
Play-Store-publishable. A larger, unminified debug build is also included as
[`apk/Sonder-1.0-debug.apk`](apk/Sonder-1.0-debug.apk).

Requires Android 8.0+ (API 26), targets Android 16 (API 36).

## Build locally

Requirements: JDK 21, Android SDK platform 36, build-tools 36.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

To install on a connected phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Signed releases

1. Generate a keystore once and back it up offline:

   ```bash
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias sonder
   ```

2. Base64-encode it: `base64 -w 0 release.jks` (Linux) or `base64 release.jks` (macOS).
3. Add four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
4. Push a `v*` tag. GitHub Actions runs the tests, builds a signed APK + AAB, and publishes a release.

Debug builds and pull requests never need signing secrets.

## Privacy

- The vault never leaves the phone automatically.
- Embeddings are omitted from backups and recomputed locally on restore.
- A lost backup password cannot be recovered — that is the point.

## Project workbook

[`Sonder_Work_Log.xlsx`](Sonder_Work_Log.xlsx) is the living project record: an overview,
a feature-by-feature sheet with verification status, a chronological work log, exact
toolchain/build commands, test results, every issue and fix, APK hashes/permissions, and
emulator screenshots. It is updated alongside the code.

## Project layout

```
app/src/main/java/com/sonder/app
├── data/local        Room entities, DAO, database, converters
├── data/prefs        DataStore settings (theme, lock, reminder)
├── ml                Tone router, embedding engine, entity extractor, vector math
├── reminder          Inexact alarm scheduler + receivers
├── security          PIN hashing
├── ui                Root navigation, Home, Capture, Calendar, Rain Room,
│                     Threads, Settings, Lock, theme + aura components
└── util              Date formatting, encrypted backup, CSV
```
