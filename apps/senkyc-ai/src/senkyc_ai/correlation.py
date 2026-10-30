"""Identifiant de corrélation : repris de X-Correlation-Id s'il est valide, sinon généré."""

from __future__ import annotations

import logging
import re
import uuid
from contextvars import ContextVar

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

CORRELATION_HEADER = "X-Correlation-Id"
_VALID = re.compile(r"[A-Za-z0-9._-]{8,64}")

correlation_id_var: ContextVar[str] = ContextVar("correlation_id", default="-")


class CorrelationIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        incoming = request.headers.get(CORRELATION_HEADER, "")
        correlation_id = incoming if _VALID.fullmatch(incoming) else str(uuid.uuid4())
        token = correlation_id_var.set(correlation_id)
        try:
            response = await call_next(request)
        finally:
            correlation_id_var.reset(token)
        response.headers[CORRELATION_HEADER] = correlation_id
        return response


class CorrelationIdLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.correlation_id = correlation_id_var.get()
        return True
