# Install on a phone, and run the backend in Termux

Two separate pieces:
1. **The app** (an APK) is installed like any Android app. It cannot run inside Termux.
2. **The backend** (Python/FastAPI, holds your Gemini/OpenAI keys) can run on a PC, a server, **or in Termux on the same phone**.

## A. Get the APK onto the phone (pick one)
**A1. Build on a PC (most reliable).** Android Studio > open `android/` > Build > Build APK(s). Copy `android/app/build/outputs/apk/debug/app-debug.apk` to the phone (USB, cloud drive, Telegram "saved messages", etc.).

**A2. Build in the cloud from your phone.** Create a GitHub repo, upload this project (repo root = this folder, including `.github/`), open the **Actions** tab > *Build debug APK* > *Run workflow*. When it finishes, download the `jinwoo-debug-apk` artifact (a zip), extract `app-debug.apk`. (Template not run by me; if it fails, read the log and fix the version numbers.)

**A3. Build on the phone itself.** Not recommended: Android's build tools (notably `aapt2`) are published for desktop CPUs and are unreliable on ARM Termux. Prefer A1/A2.

**Install:** open the APK > allow "Install unknown apps" for the app you opened it from (browser/file manager) > Install. Debug builds are signed with a debug key; Play Protect may ask to scan it. Grant microphone + notification permission on first run.

## B. Backend in Termux (same phone)
1. Install **Termux from F-Droid or the official GitHub releases** (the Play Store build is outdated).
2. Put the project on the phone and unzip:
   ```bash
   termux-setup-storage                       # allow storage access, accept the prompt
   cd ~ && unzip ~/storage/downloads/JinWooAssistant.zip
   cd JinWooAssistant
   ```
3. One-time setup (installs Ubuntu inside Termux; plain Termux often cannot install `pydantic-core`, which FastAPI needs):
   ```bash
   bash backend/termux/1_termux_setup.sh
   bash backend/termux/2_install_backend.sh
   nano backend/.env          # add GEMINI_API_KEY and/or OPENAI_API_KEY, APP_ACCESS_TOKEN. Save: Ctrl+O, Enter, Ctrl+X
   ```
   Make a token with: `head -c 24 /dev/urandom | base64 | tr -d '=+/'`
4. Start it (keep Termux open; swipe-lock it in recents):
   ```bash
   bash backend/termux/3_start_backend.sh
   ```
5. Check (new Termux session): `curl http://127.0.0.1:8000/health` -> `{"status":"ok"}`.

## C. Connect the app
App > gear > AI provider & model:
- Backend URL: `http://127.0.0.1:8000` (plain http is allowed only to this phone itself)
- Backend access token: the same `APP_ACCESS_TOKEN`
- Load available models > pick one > **Test AI connection**.

## D. Keeping it alive
- The Termux notification shows "wake lock"; leave it. Exclude Termux **and** JIN WOO AI from battery optimisation (Settings > Apps > Battery > Unrestricted).
- Android 12+ can kill background child processes ("phantom process killer"). If the backend dies when Termux is in the background, look in Developer options for **Disable child process restrictions** (present on some Android 14+ builds), or keep Termux in a floating/split window.
- Wi-Fi/mobile data is needed for Gemini/OpenAI calls. Only local commands (open apps, alarms, calculator) work offline.
- To let another device use this backend: `HOST=0.0.0.0 bash backend/termux/3_start_backend.sh` and use `http://<phone-ip>:8000` (needs a debug-config entry or HTTPS) - keep the token set.

## E. Updating
Re-unzip over the folder (keep your `backend/.env`), rebuild/reinstall the APK. In Termux: `Ctrl+C` then rerun step 4.

## F. Troubleshooting
- `proot-distro: command not found` > run `pkg install proot-distro`.
- `pip` errors about `pydantic-core` or Rust > you are outside Ubuntu; use the scripts above, which run pip inside Ubuntu.
- `--bind` flag error > check `proot-distro login --help` for your version.
- App says "Cannot reach the backend" > backend not running, wrong URL (must be `http://127.0.0.1:8000`), or Termux was killed.
- `unauthorized` > token mismatch.
