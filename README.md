# Sonder v2 — The Living Memory Vault

**A calm, zero-guilt, 100% offline Android diary.**

Sonder is built around real human days: a date can hold several Moments, the
capture sheet itself becomes the weather, and heavy days rest in a private Rain
Room. Everything — search, tags, embeddings, backups — happens on the phone.

- **No accounts, no cloud, no analytics, no streaks.**
- ISO dates in SQLite (`YYYY-MM-DD`), DD-MM-YYYY everywhere you see them.
- Lean release APK (≈2.4 MB unsigned / minified).

## What is in v2.0.0

- **Multiple Moments per day** with an in-place `MIGRATION_1_2` from the v1 vault.
- **Full-canvas weather swipe** (Dusk Rain ↔ Quiet Mist ↔ Golden Sun) with haptic
  detents, an offline tone nudge, and a first-run swipe hint.
- **Emergent day weather** derived from all Moments — a heavy morning never
  erases a golden evening. Colour is always paired with a geometric marker.
- **Text + ≤90 s voice notes** (AAC mono 16 kHz, app-private `audio_vault/`,
  pause/resume recording, playback with scrubbing).
- **Optional photos**: up to 3 per Moment, each with a short caption, via the
  Android Photo Picker (no storage permission). Files are copied into
  `photo_vault/` under opaque UUID names. Photos are included in encrypted
  backups.
- **Chapters**: title, accent, solid/dashed/dotted underline, planned end date,
  quiet 7-day check-in, optional cover photo, and a shareable recap card.
- **Editable people & tags**: rename globally, merge duplicates, confirm or
  dismiss “We noticed…” suggestions, and autocomplete from your own vocabulary.
- **Rain Room + Unsent drawer**, isolated from counts/search, with an optional
  separate vault PIN, biometrics, and `FLAG_SECURE` on sanctuary screens.
- **“Open When” envelopes**: seal a Moment until a future date; a notification
  and Home card appear when it unlocks; opening it files a readable Moment.
- **5-day Welcome Back** banner and **Re-read Roulette** (gated at 50 Moments).
- **Guardrailed insights**: at least 8 matching Moments and ≥15 % lift over your
  own sunlit baseline. An “Early pattern” badge shows under 30 Moments.
- **Home-screen widget** (classic RemoteViews — no Glance dependency) showing
  today’s weather label and Moment count.
- **Backups**: CSV export plus an AES-256-GCM `.sonder` archive (PBKDF2-HMAC-
  SHA256, 120k iterations) that round-trips text, audio, photos, chapters and
  envelopes. Embeddings are omitted and regenerated on restore.
- **Privacy**: `allowBackup=false`, cloud/device-transfer rules block the vault,
  permission-trimmed manifest (notifications, boot, mic, biometrics only).

## Get an APK from GitHub (no local Android setup)

1. Push this repository to GitHub (private is fine).
2. Open the repo → **Actions** → **Build Sonder APK** → **Run workflow**.
3. Wait for the green check, then open the run and download:
   - **`sonder-release`** — lean, minified, installable APK (debug-signed when no
     release keystore secrets are configured).
   - **`sonder-debug`** — larger debug APK with full logging.
4. Unzip the artifact, copy the `.apk` to your phone, and install it (allow
   “install unknown apps” for your file manager).

### Publish a real GitHub Release

```bash
git tag v2.0.0
git push origin v2.0.0
```

The same workflow runs, builds the release APK, and attaches it to a GitHub
Release. To sign with your own keystore, add these repository secrets first:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Full walkthrough: [`docs/GITHUB_APK.md`](docs/GITHUB_APK.md).

## Build locally

Requirements: JDK 21, Android SDK platform 36, build-tools 36.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease      # lean installable APK
```

Outputs:

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Install on a connected phone:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Signed Play Store builds

1. Generate and back up a keystore offline:

   ```bash
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
     -validity 10000 -alias sonder
   ```

2. Base64-encode it: `base64 -w 0 release.jks` (Linux) or `base64 release.jks`
   (macOS).
3. Add the four repository secrets listed above.
4. Push a `v*` tag. Actions builds a signed release APK and a GitHub Release.

`release.jks` and `*.jks` are git-ignored.

## Project records

[`Sonder_Work_Log.xlsx`](Sonder_Work_Log.xlsx) is the living workbook:

- **Overview** — build, version, verification summary.
- **Feature Log** — every v2 feature, phase, status, and where it lives.
- **Work Log** — chronological implementation record with test/build results.
- **Build & Verification** — exact offline/CI commands and APK outputs.
- **Deferred & Risks** — items intentionally left for a later release.

## Known gaps (deliberately deferred)

- **SQLCipher at-rest database encryption** — not available in the offline
  dependency cache; the vault stays app-private and excluded from cloud backup.
- **Quantized MiniLM / ONNX embeddings** — the deterministic 384-dim hashing
  embedder is used instead; it stores the same vector format, so a model can be
  dropped in later without a schema or UI change.
- **WorkManager embedding pipeline** — embeddings are computed synchronously on
  the IO dispatcher immediately after save (fast for this embedder).
- **Navigation Compose refactor** — navigation is still the typed `Screen` enum
  in `SonderRoot.kt`.
- **Real-device OEM / Doze / TalkBack QA** — release signing and sideload test
  still need to happen on the physical phone.

## Privacy

- The vault never leaves the phone automatically.
- A lost backup password cannot be recovered — that is the point.
- Screenshots are blocked while the Rain Room / Unsent sanctuary is open.

## Project layout

```
app/src/main/java/com/sonder/app
├── data/local        Room entities, DAO, database, v1→v2 migration
├── data/prefs        DataStore settings (theme, locks, reminder, notices)
├── ml                Tone router, embedding engine, entity extractor, vectors
├── reminder          Inexact alarm scheduler + boot/time receivers
├── security          PBKDF2 PIN hashing
├── ui                Root, Home, Capture, Calendar, Threads, Rain Room,
│                     Settings, Lock, theme, shared components
├── util              Dates, audio vault, photo vault, encrypted backups
└── widget            Home-screen widget provider + update helper
```
