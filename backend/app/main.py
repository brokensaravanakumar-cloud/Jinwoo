import hmac
import json
import logging
from typing import AsyncIterator

from fastapi import Depends, FastAPI, Request
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response, StreamingResponse

from .config import Settings, load_settings
from .errors import ApiError
from .providers import build_registry, pick
from .ratelimit import RateLimiter
from .schemas import ChatRequest, ChatResponse, TestRequest, TtsRequest

log = logging.getLogger("jinwoo")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def create_app(settings: Settings | None = None, registry: dict | None = None) -> FastAPI:
    s = settings or load_settings()
    providers = registry if registry is not None else build_registry(s)
    limiter = RateLimiter(s.rate_limit_per_minute, s.trust_proxy)
    app = FastAPI(title="JIN WOO AI backend", version="1.0.0")

    if s.cors_origins:
        app.add_middleware(CORSMiddleware, allow_origins=s.cors_origins, allow_methods=["GET", "POST"], allow_headers=["Authorization", "Content-Type"])
    if not s.access_token:
        log.warning("APP_ACCESS_TOKEN is empty: anyone who can reach this server can use your AI keys. Set it before exposing the server.")

    # ---- dependencies
    async def guard(request: Request) -> None:
        if s.access_token:
            supplied = request.headers.get("authorization", "")
            if not supplied.startswith("Bearer ") or not hmac.compare_digest(supplied[7:].encode(), s.access_token.encode()):
                raise ApiError("unauthorized", "Missing or invalid access token.", 401)
        limiter.check(request)

    # ---- error handling (uniform format, no secrets)
    @app.exception_handler(ApiError)
    async def api_error_handler(_: Request, e: ApiError):
        return JSONResponse(status_code=e.status, content={"error": {"code": e.code, "message": e.message}})

    @app.exception_handler(RequestValidationError)
    async def validation_handler(_: Request, e: RequestValidationError):
        fields = ", ".join(".".join(str(p) for p in err["loc"][1:]) for err in e.errors())[:200]
        return JSONResponse(status_code=422, content={"error": {"code": "invalid_request", "message": f"Invalid request fields: {fields}"}})

    @app.exception_handler(Exception)
    async def unhandled(_: Request, e: Exception):
        log.exception("unhandled error")  # stack trace only; request bodies and keys are never logged
        return JSONResponse(status_code=500, content={"error": {"code": "internal_error", "message": "Internal server error."}})

    # ---- routes
    @app.get("/health")
    async def health():
        return {"status": "ok"}

    @app.get("/v1/status", dependencies=[Depends(guard)])
    async def status():
        return {
            "providers": {name: {"configured": p.configured(), "default_model": p.default_model or None} for name, p in providers.items()},
            "tts": bool(s.openai_tts_model and s.openai_tts_voice and providers.get("openai") and providers["openai"].configured()),
        }

    @app.get("/v1/models", dependencies=[Depends(guard)])
    async def models(provider: str):
        p = pick(providers, provider)
        return {"provider": p.name, "models": await p.list_models()}

    @app.post("/v1/chat", response_model=ChatResponse, dependencies=[Depends(guard)])
    async def chat(req: ChatRequest):
        p = pick(providers, req.provider)
        model, reply = await p.chat(req)
        log.info("chat provider=%s model=%s messages=%d", p.name, model, len(req.messages))
        return ChatResponse(provider=p.name, model=model, reply=reply.text, usage=reply.usage or None)

    @app.post("/v1/chat/stream", dependencies=[Depends(guard)])
    async def chat_stream(req: ChatRequest):
        p = pick(providers, req.provider)
        agen = p.stream(req)
        try:
            first = await anext(agen)       # surface auth/quota/model errors as proper HTTP errors before streaming starts
        except StopAsyncIteration:
            first = None
        log.info("stream provider=%s messages=%d", p.name, len(req.messages))

        async def events() -> AsyncIterator[str]:
            try:
                if first is not None:
                    yield f"data: {json.dumps({'delta': first})}\n\n"
                async for chunk in agen:
                    yield f"data: {json.dumps({'delta': chunk})}\n\n"
                yield f"data: {json.dumps({'done': True})}\n\n"
            except ApiError as e:
                yield f"data: {json.dumps({'error': {'code': e.code, 'message': e.message}})}\n\n"
            except Exception:
                log.exception("stream failed")
                yield f"data: {json.dumps({'error': {'code': 'internal_error', 'message': 'Stream failed.'}})}\n\n"

        return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

    @app.post("/v1/test", dependencies=[Depends(guard)])
    async def test(req: TestRequest):
        p = pick(providers, req.provider)
        probe = ChatRequest(provider=req.provider, model=req.model, messages=[{"role": "user", "content": "Reply with the single word: online"}])
        model, reply = await p.chat(probe)
        return {"ok": True, "provider": p.name, "model": model, "reply": reply.text.strip()[:200]}

    @app.post("/v1/tts", dependencies=[Depends(guard)])
    async def tts(req: TtsRequest):
        openai = providers.get("openai")
        if not (s.openai_tts_model and s.openai_tts_voice and openai):
            raise ApiError("tts_not_configured", "External TTS is not configured (OPENAI_TTS_MODEL / OPENAI_TTS_VOICE).", 501)
        audio = await openai.speech(req.text, s.openai_tts_model, s.openai_tts_voice)
        return Response(content=audio, media_type="audio/mpeg")

    return app


app = create_app()
