import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


def _csv(name: str) -> list[str]:
    return [x.strip() for x in os.getenv(name, "").split(",") if x.strip()]


@dataclass(frozen=True)
class Settings:
    openai_api_key: str
    gemini_api_key: str
    openai_model: str
    gemini_model: str
    openai_tts_model: str
    openai_tts_voice: str
    access_token: str
    rate_limit_per_minute: int
    cors_origins: list[str]
    trust_proxy: bool


def load_settings() -> Settings:
    return Settings(
        openai_api_key=os.getenv("OPENAI_API_KEY", "").strip(),
        gemini_api_key=os.getenv("GEMINI_API_KEY", "").strip(),
        openai_model=os.getenv("OPENAI_MODEL", "").strip(),
        gemini_model=os.getenv("GEMINI_MODEL", "").strip(),
        openai_tts_model=os.getenv("OPENAI_TTS_MODEL", "").strip(),
        openai_tts_voice=os.getenv("OPENAI_TTS_VOICE", "").strip(),
        access_token=os.getenv("APP_ACCESS_TOKEN", "").strip(),
        rate_limit_per_minute=int(os.getenv("RATE_LIMIT_PER_MINUTE", "30") or 30),
        cors_origins=_csv("CORS_ORIGINS"),
        trust_proxy=os.getenv("TRUST_PROXY", "false").lower() == "true",
    )
