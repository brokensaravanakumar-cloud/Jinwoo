# AI provider & backend setup

## Keys (stay on the backend only)
- **Gemini**: create a key in Google AI Studio (Google's Gemini API docs: ai.google.dev).
- **OpenAI**: create a key in the OpenAI platform dashboard (platform.openai.com).
Put them in `backend/.env` (`GEMINI_API_KEY`, `OPENAI_API_KEY`). `.env` is git-ignored and excluded from the zip. Never put keys in the Android app.

## Models
Model IDs change often. This project deliberately ships **no hard-coded model names**:
1. Leave `OPENAI_MODEL` / `GEMINI_MODEL` empty at first.
2. In the app press **Load available models** (calls `GET /v1/models`, which lists what your key can use) and pick one, or paste any model ID from the provider's current documentation.
3. Optionally copy your favourite into `.env` as the server default.

## Endpoints used (official REST APIs)
- OpenAI: `POST https://api.openai.com/v1/chat/completions` (SSE with `stream: true`), `GET /v1/models`, optional `POST /v1/audio/speech`.
- Gemini: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, `...:streamGenerateContent?alt=sse`, `GET /v1beta/models`; key sent in the `x-goog-api-key` header.
Providers evolve; if a call starts failing, compare `backend/app/providers/*.py` with the current official docs.

## Access token
```bash
python -c "import secrets;print(secrets.token_urlsafe(32))"
```
Put it in `.env` as `APP_ACCESS_TOKEN` and in the app's "Backend access token". Without it, anyone who can reach your server can spend your API credit.

## Test with curl
```bash
curl http://localhost:8000/health
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8000/v1/status
curl -H "Authorization: Bearer YOUR_TOKEN" "http://localhost:8000/v1/models?provider=gemini"
curl -X POST http://localhost:8000/v1/test -H "Authorization: Bearer YOUR_TOKEN" -H "Content-Type: application/json" -d "{\"provider\":\"gemini\"}"
```

## Response/error format
Success: `{"provider","model","reply","usage"}`. Stream: SSE lines `data: {"delta":"..."}` ... `data: {"done":true}`.
Errors (any provider): `{"error":{"code","message"}}` with codes `invalid_api_key`, `quota_exceeded`, `model_unavailable`, `network_error`, `bad_request`, `provider_not_configured`, `unauthorized`, `rate_limited`, `invalid_request`, `internal_error`.

## Optional external TTS voice
Set `OPENAI_TTS_MODEL` and `OPENAI_TTS_VOICE` (see OpenAI's text-to-speech docs for current options; pick a deep male voice), then enable "External TTS voice" in the app. OpenAI requires telling users the voice is AI-generated. Do not use a cloned voice of a real actor without permission.

## HTTPS deployment
The Android release build only talks HTTPS. Easiest options:
1. **Caddy** (automatic certificates) on a VPS with a domain pointing at it:
   ```
   jinwoo.example.com {
       reverse_proxy 127.0.0.1:8000
   }
   ```
   Run uvicorn on `127.0.0.1:8000` (e.g. under systemd) and set `TRUST_PROXY=true` in `.env`.
2. **Cloudflare Tunnel** / similar tunnel from your home PC to a public HTTPS hostname.
3. nginx + Let's Encrypt (disable proxy buffering for `/v1/chat/stream`: `proxy_buffering off;`).
Then set the app's Backend URL to `https://your-domain`.

## Security checklist
- `.env` never committed; rotate keys if leaked. Keep `APP_ACCESS_TOKEN` set. Keep `RATE_LIMIT_PER_MINUTE` low. Logs contain provider/model/counts only - never keys or message text.
