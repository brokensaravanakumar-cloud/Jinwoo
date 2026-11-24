# JIN WOO AI backend (FastAPI)

Keeps the OpenAI and Gemini API keys on the server and gives the Android app one consistent API.

```bash
python -m venv venv && venv\Scripts\activate      # macOS/Linux: source venv/bin/activate
pip install -r requirements.txt
cp .env.example .env                                # then edit it
uvicorn app.main:app --host 0.0.0.0 --port 8000
pytest tests
```

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /health` | none | liveness |
| `GET /v1/status` | token | which providers/TTS are configured |
| `GET /v1/models?provider=` | token | models your key can use |
| `POST /v1/chat` | token | non-streaming chat -> `{provider, model, reply, usage}` |
| `POST /v1/chat/stream` | token | SSE: `data: {"delta"}` ... `data: {"done":true}` |
| `POST /v1/test` | token | one tiny round-trip to verify key + model |
| `POST /v1/tts` | token | optional external voice (mp3) |

Layout: `app/main.py` (routes, errors, auth), `providers/` (one file per provider; subclass `Provider`, register in `providers/__init__.py`), `ratelimit.py`, `config.py`. Errors are always `{"error":{"code","message"}}`. Logs never contain keys or message text.
Full setup, HTTPS and security notes: `../docs/API_SETUP.md`.
