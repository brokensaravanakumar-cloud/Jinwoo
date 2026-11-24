from ..config import Settings
from ..errors import ApiError
from .base import Provider
from .gemini_provider import GeminiProvider
from .openai_provider import OpenAIProvider


def build_registry(s: Settings) -> dict[str, Provider]:
    """Register new providers here (one line each)."""
    return {
        "openai": OpenAIProvider(s.openai_api_key, s.openai_model),
        "gemini": GeminiProvider(s.gemini_api_key, s.gemini_model),
    }


def pick(registry: dict[str, Provider], name: str | None) -> Provider:
    key = name or next((k for k, p in registry.items() if p.configured()), "")
    if key not in registry:
        raise ApiError("provider_not_configured", "No AI provider is configured on the backend.", 503)
    return registry[key]
