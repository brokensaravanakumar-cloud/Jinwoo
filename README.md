# JIN WOO AI - Shadow Monarch personal assistant for Android

A native Android assistant (Kotlin + Jetpack Compose) with a Solo Leveling-inspired "System" interface, an original activation
sound, a deep calm voice, on-device wake-word support ("Jin Woo"), and a self-hosted FastAPI backend that talks to **Google Gemini**
and **OpenAI** with *your* API keys. All artwork and audio are original; no official logos, screenshots or soundtrack are used.

## What you get

| Folder | Content |
|---|---|
| `android/` | Android Studio project (package `com.jinwoo.ai`, minSdk 26) |
| `backend/` | FastAPI backend (OpenAI + Gemini, streaming, rate limit, token auth, tests) |
| `assets/` | Original activation sound + generator script, emblem SVG, notes |
| `docs/` | INSTALLATION, API_SETUP, WAKE_WORD_SETUP, TROUBLESHOOTING |

## Features
- **Wake word "Jin Woo"** via a local sherpa-onnx keyword spotter in a visible microphone foreground service (Stop action in the notification). Background detection posts a tap-to-open notification, because Android restricts background activity launches.
- **One-shot command recognition** with Android `SpeechRecognizer` after wake / mic button (never as an endless listener). Manual mic button and typed commands always work.
- **AI brain**: Gemini or OpenAI through your backend; streaming replies; model picker + custom model ID; connection test; conversation memory (Room) you can switch off and clear.
- **Voice**: Android TextToSpeech tuned deep/slow (pitch, speed, volume, voice choice, interrupt button) or an optional external TTS voice via the backend.
- **Phone actions** (modular, `AssistantAction`): open ANY installed app by name, YouTube / Chrome / WhatsApp, YouTube search, web search, open website, alarms, timers, time/date, calculator, open Settings pages, prepare calls / SMS / WhatsApp messages (**always ask for confirmation**), end conversation. Commands the app does not recognise go to the AI, which may answer with a structured action that runs through the same safe registry.
- **UI**: animated System-eye emblem with Sleeping / Listening / Processing / Speaking / Error / Offline states, shadow particles, activation shock-wave and "JIN WOO SYSTEM ACTIVATED" overlay, accent themes.

## Quick start
1. Backend: see `backend/README.md` and `docs/API_SETUP.md` (copy `.env.example` to `.env`, add keys, run uvicorn).
2. Android: open `android/` in Android Studio, let Gradle sync, run on a device. See `docs/INSTALLATION.md`.
3. In the app: Settings > AI provider > set Backend URL (+ token), load models, **Test AI connection**.
4. Optional wake word: `docs/WAKE_WORD_SETUP.md`.
5. Phone-only route (install the APK, run the backend in Termux): `docs/MOBILE_AND_TERMUX.md`.

## Honest status of this delivery
This package was generated in a sandbox **without internet, Gradle, Android SDK, Kotlin compiler or FastAPI**. Therefore:

| Item | Status |
|---|---|
| All source files, resources, docs present | Checked by script (see below) |
| Resource XML well-formed, manifest well-formed, R references resolve, Kotlin imports resolve to project symbols | Checked by script |
| Activation sound generated and verified as a valid WAV | Done |
| Python backend syntax | `py_compile` passed |
| Action/regex parsing logic | Mirrored in Python and spot-tested |
| Gradle build, Android unit tests, backend pytest, running on a device | **NOT run - you must run them** |
| Gradle wrapper JAR | **Not included** (binary; could not be generated). Android Studio does not need it; see docs/INSTALLATION.md |
| Wake-word engine + models | **Not bundled** (large, separate download). The app says so honestly and stays disabled until installed |
| Library versions (AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00 ...) | Chosen as a known-compatible set; **not re-verified online**. Accept Android Studio's upgrade suggestions if offered |
| sherpa-onnx Kotlin calls (`src/sherpa/...`) | Written from memory of the official API; may need small name tweaks |

Expect to fix a few compile errors on first build; nothing here was compiled. Please report them with the Gradle output and they can be fixed quickly.

## Privacy & safety
- Wake-word audio never leaves the phone. After the wake word, your command is transcribed by Android's speech service (device/Google, per your phone), and the *text* goes to your own backend and chosen AI provider.
- No provider API key exists in the app. Only an optional backend access token is stored, AES-GCM encrypted with an Android Keystore key.
- No Accessibility Service, no hidden recording, no overlay. Calls and messages are only *prepared* (dialer / composer opens) after your confirmation.
- "Jin Woo" and the Shadow Monarch theme are inspired by *Solo Leveling*, which belongs to its creators; if you publish the app, review naming/branding rules for your store and region.
