# Building Sonder APKs with GitHub Actions

This project ships a single workflow: `.github/workflows/android-build.yml`.
You never need Android Studio or a local SDK to get an installable APK.

## 1. Push the repository

```bash
git add .
git commit -m "Sonder v2"
git push origin main
```

A private repository works. GitHub Actions is free for personal repositories
within the usual minutes allowance.

## 2. Run the workflow manually

1. Open the repository on GitHub.
2. Go to **Actions**.
3. Select **Build Sonder APK** in the left sidebar.
4. Click **Run workflow** → choose `main` → **Run workflow**.
5. Wait for the green check (about 5–8 minutes on a cold cache).

## 3. Download the APK

Open the completed run and scroll to **Artifacts**:

| Artifact | What it is | Use it when |
| --- | --- | --- |
| `sonder-release` | Minified release APK (≈2.4 MB). Signed with your release key if secrets exist, otherwise the debug key. | Daily use / sideloading. |
| `sonder-debug` | Unminified debug APK (≈19 MB) with full logs. | Testing and bug reports. |

Download the zip, extract `app-release.apk` (or `app-debug.apk`), send it to your
phone, and install it. Android will ask you to allow installing unknown apps for
the file manager or browser you use.

## 4. Publish a versioned GitHub Release

```bash
git tag v2.0.0
git push origin v2.0.0
```

On a `v*` tag the workflow also creates a GitHub Release and attaches the APK.

## 5. Sign with your own keystore (optional but recommended for updates)

Generate a keystore once and keep it offline:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias sonder
```

Base64-encode it:

```bash
base64 -w 0 release.jks        # Linux
base64 release.jks             # macOS (single line)
```

Add four **repository secrets** under
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the base64 text of `release.jks` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `sonder` (or your alias) |
| `KEY_PASSWORD` | the key password |

The workflow decodes the keystore only for `v*` tags. If the secret is missing,
the release falls back to the debug key so the build still succeeds.

> Keep the same keystore for every release. Android will refuse to update an
> installed app with an APK signed by a different key.

## 6. Troubleshooting

- **`sdkmanager: command not found`** — the workflow runs
  `android-actions/setup-android@v3` before installing packages; make sure you
  kept that step.
- **Wrong Java version** — the workflow pins Temurin 21. AGP 8.9 does not support
  Java 27.
- **Unsigned release APK** — only happens if you build locally without a
  `release.jks`; GitHub Actions uses the debug key fallback, so artifacts are
  always installable.
- **Artifact expired** — artifacts are kept for 30 days. Re-run the workflow.
