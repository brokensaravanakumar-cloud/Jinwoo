from typing import Literal, Optional

from pydantic import BaseModel, Field

MODEL_PATTERN = r"^[A-Za-z0-9._:/\-]{1,100}$"


class Message(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)


class ChatRequest(BaseModel):
    provider: Optional[Literal["openai", "gemini"]] = None
    model: Optional[str] = Field(default=None, pattern=MODEL_PATTERN)
    messages: list[Message] = Field(min_length=1, max_length=40)
    system: Optional[str] = Field(default=None, max_length=6000)
    temperature: Optional[float] = Field(default=None, ge=0, le=2)


class ChatResponse(BaseModel):
    provider: str
    model: str
    reply: str
    usage: Optional[dict] = None


class TestRequest(BaseModel):
    provider: Optional[Literal["openai", "gemini"]] = None
    model: Optional[str] = Field(default=None, pattern=MODEL_PATTERN)


class TtsRequest(BaseModel):
    text: str = Field(min_length=1, max_length=1500)
