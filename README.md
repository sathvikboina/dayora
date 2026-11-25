# Dayora — Phase 0 Starter Base

Offline-first, zero-guilt Android diary built with Kotlin, Jetpack Compose, Room, and on-device ML scaffolding.

## Quick Start (Get Your First APK in 3 Minutes)
1. Unzip this folder and push it to a new GitHub repository:
   ```bash
   git init
   git add .
   git commit -m "Phase 0: Dayora starter base"
   git branch -M main
   git remote add origin https://github.com/<your-username>/dayora.git
   git push -u origin main
   ```
2. Open the **Actions** tab on GitHub. The workflow will automatically run unit tests and compile `dayora-debug-*.apk` as a downloadable artifact.
3. When you are ready for signed release APKs & Play Store AABs, generate a keystore locally and add these 4 Repository Secrets (`Settings -> Secrets and variables -> Actions`):
   - `KEYSTORE_BASE64`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
