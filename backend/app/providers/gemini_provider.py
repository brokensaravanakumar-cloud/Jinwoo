"""Gemini via the documented REST API: models/{model}:generateContent and :streamGenerateContent?alt=sse (API key in x-goog-api-key)."""
import json
from typing import AsyncIterator

import httpx

from ..errors import ApiError, from_provider_http
from ..schemas import ChatRequest
from .base import Provider, ProviderReply

BASE = "https://generativelanguage.googleapis.com/v1beta"


def _text_of(data: dict) -> str:
    parts = (((data.get("candidates") or [{}])[0].get("content") or {}).get("parts")) or []
    return "".join(p.get("text", "") for p in parts)


class GeminiProvider(Provider):
    name = "gemini"

    def _headers(self) -> dict:
        return {"x-goog-api-key": self.api_key, "Content-Type": "application/json"}

    @staticmethod
    def _clean(model: str) -> str:
        return model[len("models/"):] if model.startswith("models/") else model

    def _body(self, req: ChatRequest) -> dict:
        body: dict = {"contents": [{"role": "user" if m.role == "user" else "model", "parts": [{"text": m.content}]} for m in req.messages]}
        if req.system:
            body["systemInstruction"] = {"parts": [{"text": req.system}]}
        if req.temperature is not None:
            body["generationConfig"] = {"temperature": req.temperature}
        return body

    async def chat(self, req: ChatRequest) -> tuple[str, ProviderReply]:
        model = self._clean(self.resolve_model(req.model))
        try:
            r = await self.client.post(f"{BASE}/models/{model}:generateContent", headers=self._headers(), json=self._body(req))
        except httpx.HTTPError as e:
            raise self.wrap_network(e)
        if r.status_code >= 400:
            raise from_provider_http("Gemini", r.status_code, r.text)
        data = r.json()
        text = _text_of(data)
        if not text and (data.get("promptFeedback") or {}).get("blockReason"):
            raise ApiError("bad_request", "Gemini blocked this prompt.", 400)
        return model, ProviderReply(text=text, usage=data.get("usageMetadata") or {})

    async def stream(self, req: ChatRequest) -> AsyncIterator[str]:
        model = self._clean(self.resolve_model(req.model))
        try:
            async with self.client.stream("POST", f"{BASE}/models/{model}:streamGenerateContent?alt=sse",
                                          headers=self._headers(), json=self._body(req)) as r:
                if r.status_code >= 400:
                    body = (await r.aread()).decode("utf-8", "replace")
                    raise from_provider_http("Gemini", r.status_code, body)
                async for line in r.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    try:
                        text = _text_of(json.loads(line[5:].strip()))
                    except ValueError:
                        continue
                    if text:
                        yield text
        except httpx.HTTPError as e:
            raise self.wrap_network(e)

    async def list_models(self) -> list[str]:
        self.resolve_model("x")
        try:
            r = await self.client.get(f"{BASE}/models", headers=self._headers(), params={"pageSize": 100})
        except httpx.HTTPError as e:
            raise self.wrap_network(e)
        if r.status_code >= 400:
            raise from_provider_http("Gemini", r.status_code, r.text)
        out = []
        for m in r.json().get("models", []):
            if "generateContent" in (m.get("supportedGenerationMethods") or []):
                out.append(self._clean(m.get("name", "")))
        return sorted(x for x in out if x)
