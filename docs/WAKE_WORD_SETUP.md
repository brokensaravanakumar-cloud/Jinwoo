# Wake word ("Jin Woo") setup

The app does **not** pretend wake-word detection works: until the engine and model files are installed, the Wake Word screen shows "ENGINE MISSING" or "MODEL FILES MISSING" and the switch stays off. The mic button works regardless.

Engine: **sherpa-onnx** open-vocabulary keyword spotting (k2-fsa project, runs fully on-device). Its docs describe it as a small streaming recogniser that can only output the keywords you provide, so *you can choose any phrase without retraining*. Official docs: https://k2-fsa.github.io/sherpa/onnx/kws/index.html (check the Android build page and "Pretrained models" there for current downloads). The project is believed to be Apache-2.0 licensed - confirm on its repository, and check the licence of whichever model you download.

> I could not verify download file names from the build sandbox. Follow the official pages above for exact URLs/names.

## Step 1 - add the engine (once)
1. From the sherpa-onnx project (GitHub releases or its Android build instructions), obtain the **Android AAR** (it contains the native libraries and Kotlin API, `com.k2fsa.sherpa.onnx`).
2. Copy it to `android/app/libs/sherpa-onnx.aar` (exact name).
3. Sync Gradle and rebuild. `app/build.gradle.kts` detects the file and compiles `app/src/sherpa/kotlin/.../SherpaWakeWordEngine.kt` in. If the compiler complains about a class/parameter name, compare that single file with the official Android KWS example for your AAR version and adjust it.

## Step 2 - get a keyword-spotting model
Download one **streaming transducer KWS model** from the sherpa-onnx "Keyword spotting > Pretrained models" list (pick one for English). Unpack it; you need `encoder*.onnx`, `decoder*.onnx`, `joiner*.onnx` and `tokens.txt` (plus `bpe.model` or similar if the model uses BPE tokens). Use a matching set (all int8 or all float).

## Step 3 - create `keywords.txt` for "JIN WOO"
Create `keywords_raw.txt` with one line:
```
JIN WOO :1.5 #0.3 @JIN_WOO
```
(`:` = boosting score, `#` = trigger threshold, `@` = display name; no spaces between the symbol and the number.) Convert it to the token format your model needs:
```bash
pip install sherpa-onnx
sherpa-onnx-cli text2token --help
sherpa-onnx-cli text2token --tokens tokens.txt --tokens-type bpe --bpe-model bpe.model keywords_raw.txt keywords.txt
```
Choose `--tokens-type` according to your model (see the help text and the KWS docs). Open `keywords.txt`: it should contain token pieces followed by your `:` `#` `@` items. Put `keywords.txt` in the same folder as the model files.

## Step 4 - import into the app
Wake Word screen > **Import model folder** > choose that folder. The files are copied into the app's private storage. Status should become **READY**.

## Step 5 - enable
Turn on "Listen for Jin Woo". Allow microphone + notification permission. A persistent notification (with **Stop**) appears while listening. Say "Jin Woo": in the foreground the System activates immediately; in the background a "JIN WOO heard you - tap to open" notification appears (Android does not let apps pop up screens from the background).

## Tuning
- Too many false triggers: lower Sensitivity, or raise `#` in `keywords_raw.txt` and regenerate.
- Not triggering: raise Sensitivity, raise the boost `:` (e.g. 2.0), speak clearly, test in a quiet room. A two-syllable name is short, so expect some false alarms; a longer phrase ("hey jin woo") is more reliable.
- Accuracy depends on the model and accent; test with your own voice.

## Limits
- One app can hold the microphone at a time on many devices. While the assistant listens for a command, the wake service pauses itself and resumes afterwards.
- Phones with aggressive battery savers may kill the service: use the "battery optimisation" button on the screen.
- The microphone service can only be started from the visible app (user toggles the switch). It does not auto-start at boot.
