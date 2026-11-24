# Assets
- `sounds/activation.wav` - original cinematic activation sound (synthesised; also copied to `android/app/src/main/res/raw/`). Regenerate or tweak with `python sounds/generate_activation.py out.wav` (needs numpy).
- `icons/emblem.svg` - original "System eye" emblem (the Android launcher icon is the vector equivalent in `res/drawable`).
- `animations/` - the activation and emblem animations are drawn in code (`ui/components/SystemEmblem.kt`); no external animation files are needed.
- `models/` - put wake-word model files here while preparing them (see docs/WAKE_WORD_SETUP.md). Not shipped, and git-ignored.
You may replace the activation sound in the app with your own legally obtained audio via Settings > Import my own activation sound. Do not redistribute copyrighted soundtrack audio.
