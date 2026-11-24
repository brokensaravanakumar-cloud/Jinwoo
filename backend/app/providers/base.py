from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import AsyncIterator, Optional

import httpx

from ..errors import ApiError
from ..schemas import ChatRequest


@dataclass
class ProviderReply:
    text: str
    usage: dict = field(default_factory=dict)


class Provider(ABC):
    """Add a new AI provider by subclassing this and registering it in providers/__init__.py."""

    name: str = ""

    def __init__(self, api_key: str, default_model: str, client: Optional[httpx.AsyncClient] = None):
        self.api_key = api_key
        self.default_model = default_model
        self.client = client or httpx.AsyncClient(timeout=httpx.Timeout(60.0, connect=10.0))

    def configured(self) -> bool:
        return bool(self.api_key)

    def resolve_model(self, requested: Optional[str]) -> str:
        if not self.configured():
            raise ApiError("provider_not_configured", f"{self.name} is not configured on the backend (missing API key).", 503)
        model = (requested or self.default_model or "").strip()
        if not model:
            raise ApiError("model_unavailable", f"No model set for {self.name}. Set it in the app or in the backend .env.", 400)
        return model

    def wrap_network(self, e: Exception) -> ApiError:
        if isinstance(e, httpx.TimeoutException):
            return ApiError("network_error", f"{self.name} request timed out.", 504)
        return ApiError("network_error", f"Could not reach {self.name}.", 502)

    @abstractmethod
    async def chat(self, req: ChatRequest) -> tuple[str, ProviderReply]: ...

    @abstractmethod
    def stream(self, req: ChatRequest) -> AsyncIterator[str]: ...

    @abstractmethod
    async def list_models(self) -> list[str]: ...
