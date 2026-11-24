class ApiError(Exception):
    """Uniform error. Serialised as {"error": {"code": ..., "message": ...}} - never contains secrets."""

    def __init__(self, code: str, message: str, status: int = 502):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status = status


def from_provider_http(provider: str, status: int, body: str) -> ApiError:
    snippet = " ".join((body or "").split())[:300]
    if status in (401, 403):
        return ApiError("invalid_api_key", f"{provider} rejected the API key or access was denied.", 502)
    if status == 404:
        return ApiError("model_unavailable", f"{provider}: model or endpoint not found. {snippet}", 404)
    if status == 429:
        return ApiError("quota_exceeded", f"{provider}: quota or rate limit exceeded. {snippet}", 429)
    if status == 400:
        return ApiError("bad_request", f"{provider} rejected the request: {snippet}", 400)
    return ApiError("provider_error", f"{provider} returned HTTP {status}. {snippet}", 502)
