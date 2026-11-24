# Installation

## 1. Requirements
- Android Studio (current stable), JDK 17 (bundled with Android Studio), Android SDK Platform 34.
- An Android 8.0+ phone (recommended: real device; the emulator's microphone is unreliable) with **Google Speech Services** (speech-to-text + text-to-speech voices).
- Python 3.11+ for the backend.

## 2. Start the backend (do this first)
```bash
cd backend
python -m venv venv
venv\Scripts\activate          # Windows   (macOS/Linux: source venv/bin/activate)
pip install -r requirements.txt
copy .env.example .env         # macOS/Linux: cp .env.example .env
# edit .env: add GEMINI_API_KEY and/or OPENAI_API_KEY, models, APP_ACCESS_TOKEN
uvicorn app.main:app --host 0.0.0.0 --port 8000
```
Check: open http://localhost:8000/health -> `{"status":"ok"}`. Run `pytest tests` to run the backend tests.

## 3. Open and build the Android project
1. Android Studio > **Open** > select the `android/` folder (not the zip root).
2. Wait for Gradle sync. If it offers to upgrade AGP/Gradle/Kotlin, accept (keep Kotlin, KSP and the Compose compiler version in step; the comment in `android/build.gradle.kts` explains).
3. Run on a device (`Run > Run 'app'`) or build `Build > Build APK(s)`; the debug APK appears in `android/app/build/outputs/apk/debug/`.
4. Command line (optional): install Gradle 8.7+, run `gradle wrapper` once inside `android/` (this creates the missing `gradle-wrapper.jar`), then `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`.

## 4. Point the app at your backend
In the app: gear icon > AI provider & model.
- **Android emulator**: debug builds default to `http://10.0.2.2:8000` (cleartext allowed for emulator/localhost in debug only).
- **Backend inside Termux on the same phone**: `http://127.0.0.1:8000` (loopback http is allowed in all builds). See `docs/MOBILE_AND_TERMUX.md`.
- **Real phone + backend on your PC**: use HTTPS (see docs/API_SETUP.md, "HTTPS"), or for quick tests only, a debug build with `http://<PC-LAN-IP>:8000` requires adding that IP to `android/app/src/debug/res/xml/network_security_config.xml`.
- **Production**: always HTTPS; release builds block cleartext.
- Enter the same value as `APP_ACCESS_TOKEN` in "Backend access token".
- Press **Load available models**, choose one, then **Test AI connection**.

## 5. Permissions you will be asked for
- **Microphone** (voice commands, wake word). **Notifications** (Android 13+, needed to show the listening notification).
- Alarms/timers use the system clock app; no special permission beyond the manifest entry.

## 6. First-run checklist
1. Tap the mic: "Open YouTube" (local action, no AI needed).
2. Type or say "What is artificial intelligence?" (needs the backend).
3. Settings > Speak test; adjust pitch/speed for a deeper voice.
4. Optional: wake word (docs/WAKE_WORD_SETUP.md).
