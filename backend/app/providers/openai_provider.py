"""OpenAI via the documented Chat Completions REST endpoint (POST /v1/chat/completions, SSE streaming with stream=true)."""
import json
from typing import AsyncIterator

import httpx

from ..errors import ApiError, from_provider_http
from ..schemas import ChatRequest
from .base import Provider, ProviderReply

BASE = "https://api.openai.com/v1"
_NON_CHAT = ("embedding", "whisper", "tts", "dall-e", "moderation", "davinci", "babbage", "audio", "realtime", "transcribe", "image")


class OpenAIProvider(Provider):
    name = "openai"

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}

    def _body(self, req: ChatRequest, model: str, stream: bool) -> dict:
        msgs = []
        if req.system:
            msgs.append({"role": "system", "content": req.system})
        msgs += [{"role": m.role, "content": m.content} for m in req.messages]
        body: dict = {"model": model, "messages": msgs}
        if req.temperature is not None:
            body["temperature"] = req.temperature
        if stream:
            body["stream"] = True
        return body

    async def chat(self, req: ChatRequest) -> tuple[str, ProviderReply]:
        model = self.resolve_model(req.model)
        try:
            r = await self.client.post(f"{BASE}/chat/completions", headers=self._headers(), json=self._body(req, model, False))
        except httpx.HTTPError as e:
            raise self.wrap_network(e)
        if r.status_code >= 400:
            raise from_provider_http("OpenAI", r.status_code, r.text)
        data = r.json()
        try:
            text = data["choices"][0]["message"]["content"] or ""
        except (KeyError, IndexError, TypeError):
            raise ApiError("provider_error", "OpenAI returned an unexpected response.", 502)
        return model, ProviderReply(text=text, usage=data.get("usage") or {})

    async def stream(self, req: ChatRequest) -> AsyncIterator[str]:
        model = self.resolve_model(req.model)
        try:
            async with self.client.stream("POST", f"{BASE}/chat/completions", headers=self._headers(), json=self._body(req, model, True)) as r:
                if r.status_code >= 400:
                    body = (await r.aread()).decode("utf-8", "replace")
                    raise from_provider_http("OpenAI", r.status_code, body)
                async for line in r.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    payload = line[5:].strip()
                    if payload == "[DONE]":
                        break
                    try:
                        delta = json.loads(payload)["choices"][0].get("delta", {}).get("content")
                    except (ValueError, KeyError, IndexError):
                        continue
                    if delta:
                        yield delta
        except httpx.HTTPError as e:
            raise self.wrap_network(e)

    async def list_models(self) -> list[str]:
        self.resolve_model("x")  # only checks that the key is configured
        try:
            r = await self.client.get(f"{BASE}/models", headers=self._headers())
        except httpx.HTTPError as e:
            raise self.wrap_network(e)
        if r.status_code >= 400:
            raise from_provider_http("OpenAI", r.status_code, r.text)
        ids = [m.get("id", "") for m in r.json().get("data", [])]
        return sorted(i for i in ids if i and not any(x in i for x in _NON_CHAT))

    async def speech(self, text: str, model: str, voice: str) -> bytes:
        """Optional external TTS (POST /v1/audio/speech)."""
        self.resolve_model("x")
        try:
            r = await self.client.post(f"{BASE}/audio/speech", headers=self._headers(),
                                       json={"model": model, "voice": voice, "input": text, "response_format": "mp3"})
        except httpx.HTTPError as e:
            raise self.wrap_network(e)
        if r.status_code >= 400:
            raise from_provider_http("OpenAI", r.status_code, r.text)
        return r.content
