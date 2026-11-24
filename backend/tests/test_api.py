"""Backend tests using fake providers (no network, no API keys). Run: pytest backend/tests"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from fastapi.testclient import TestClient  # noqa: E402

from app.config import Settings  # noqa: E402
from app.errors import ApiError  # noqa: E402
from app.main import create_app  # noqa: E402
from app.providers.base import Provider, ProviderReply  # noqa: E402


class Fake(Provider):
    name = "openai"

    def __init__(self, key="k", fail: ApiError | None = None):
        super().__init__(key, "fake-model")
        self.fail = fail

    async def chat(self, req):
        if self.fail:
            raise self.fail
        return self.resolve_model(req.model), ProviderReply(text="hello master", usage={"t": 1})

    async def stream(self, req):
        if self.fail:
            raise self.fail
        for part in ("hel", "lo"):
            yield part

    async def list_models(self):
        return ["fake-model"]


def settings(token="secret", rate=100) -> Settings:
    return Settings("", "", "", "", "", "", token, rate, [], False)


def client(provider=None, **kw):
    return TestClient(create_app(settings(**kw), {"openai": provider or Fake()}))


AUTH = {"Authorization": "Bearer secret"}
BODY = {"provider": "openai", "messages": [{"role": "user", "content": "hi"}]}


def test_health_is_open():
    assert client().get("/health").json() == {"status": "ok"}


def test_requires_token():
    r = client().post("/v1/chat", json=BODY)
    assert r.status_code == 401 and r.json()["error"]["code"] == "unauthorized"


def test_chat_ok():
    r = client().post("/v1/chat", json=BODY, headers=AUTH)
    assert r.status_code == 200 and r.json()["reply"] == "hello master"


def test_validation_error_format():
    r = client().post("/v1/chat", json={"messages": []}, headers=AUTH)
    assert r.status_code == 422 and r.json()["error"]["code"] == "invalid_request"


def test_bad_model_id_rejected():
    r = client().post("/v1/chat", json={**BODY, "model": "../../etc"}, headers=AUTH)
    assert r.status_code == 422


def test_provider_error_mapped():
    r = client(Fake(fail=ApiError("quota_exceeded", "quota", 429))).post("/v1/chat", json=BODY, headers=AUTH)
    assert r.status_code == 429 and r.json()["error"]["code"] == "quota_exceeded"


def test_stream_events():
    r = client().post("/v1/chat/stream", json=BODY, headers=AUTH)
    assert r.status_code == 200
    assert '"delta": "hel"' in r.text and '"done": true' in r.text


def test_stream_error_is_http_error():
    r = client(Fake(fail=ApiError("invalid_api_key", "bad key", 502))).post("/v1/chat/stream", json=BODY, headers=AUTH)
    assert r.status_code == 502 and r.json()["error"]["code"] == "invalid_api_key"


def test_rate_limit():
    c = client(rate=2)
    codes = [c.post("/v1/chat", json=BODY, headers=AUTH).status_code for _ in range(3)]
    assert codes == [200, 200, 429]


def test_unconfigured_provider():
    r = client(Fake(key="")).post("/v1/chat", json=BODY, headers=AUTH)
    assert r.status_code == 503 and r.json()["error"]["code"] == "provider_not_configured"


def test_models_and_status():
    c = client()
    assert c.get("/v1/models", params={"provider": "openai"}, headers=AUTH).json()["models"] == ["fake-model"]
    assert c.get("/v1/status", headers=AUTH).json()["providers"]["openai"]["configured"] is True
