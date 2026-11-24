# Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Gradle sync fails on plugin/dependency versions | Versions were not re-verified online. Accept Android Studio's upgrade assistant; keep Kotlin, KSP (`<kotlin>-<ksp>`) and `kotlinCompilerExtensionVersion` matched (see Compose-to-Kotlin compatibility table in the Android docs). |
| `gradlew` says wrapper jar missing | Open the project in Android Studio, or install Gradle and run `gradle wrapper` in `android/`. |
| "Cannot reach the backend" | Wrong URL; emulator must use `http://10.0.2.2:8000` (debug); real phones need HTTPS or a debug network-security entry. Check `http(s)://.../health` in the phone's browser. |
| `unauthorized` | Token in the app differs from `APP_ACCESS_TOKEN`. |
| `invalid_api_key` | Provider key missing/wrong in `backend/.env` (restart uvicorn after editing). |
| `model_unavailable` | Pick a model via "Load available models" or paste a current ID from the provider docs. |
| `quota_exceeded` | Provider billing/quota; check your account. |
| `rate_limited` | Raise `RATE_LIMIT_PER_MINUTE`. |
| "Speech recognition is not available" | Install/enable Google Speech Services (or another recogniser) and grant mic permission. |
| Voice is high/robotic | Install Google Speech Services voices, choose a male voice in Settings, lower Pitch (0.6-0.8), or enable External TTS via backend. |
| Wake word says ENGINE MISSING | Step 1 of docs/WAKE_WORD_SETUP.md (AAR in `app/libs/`), rebuild. |
| Wake word says MODEL FILES MISSING | Import the folder with encoder/decoder/joiner `.onnx`, `tokens.txt`, `keywords.txt`. |
| Wake word stops by itself | Battery optimisation: exempt the app. Check the service line on the Wake Word screen. |
| Heard wake word in background but nothing opened | By design: tap the notification (Android blocks background launches). |
| "Open X" says app not found | The app is not installed or has a very different name; try its exact label. The app list uses the launcher intent query declared in the manifest. |
| Alarm/timer did nothing | Some clock apps ignore silent requests; the system clock app must handle `SET_ALARM` / `SET_TIMER`. |
| Compile error inside `SherpaWakeWordEngine.kt` | API names differ in your AAR version; edit that one file against the official Android KWS example. |
