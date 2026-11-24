import time
from collections import defaultdict, deque

from fastapi import Request

from .errors import ApiError


class RateLimiter:
    """Small in-memory sliding-window limiter (per client IP). Use a shared store (e.g. Redis) if you run several workers."""

    def __init__(self, per_minute: int, trust_proxy: bool = False):
        self.per_minute = per_minute
        self.trust_proxy = trust_proxy
        self.hits: dict[str, deque] = defaultdict(deque)

    def client_id(self, request: Request) -> str:
        if self.trust_proxy:
            fwd = request.headers.get("x-forwarded-for", "")
            if fwd:
                return fwd.split(",")[0].strip()
        return request.client.host if request.client else "unknown"

    def check(self, request: Request) -> None:
        now = time.monotonic()
        q = self.hits[self.client_id(request)]
        while q and now - q[0] > 60:
            q.popleft()
        if len(q) >= self.per_minute:
            raise ApiError("rate_limited", "Too many requests. Slow down.", 429)
        q.append(now)
