"""Authentification service-à-service : jeton interne partagé avec l'API Java."""

from __future__ import annotations

import logging
import secrets

from fastapi import Header, HTTPException, Request, status

logger = logging.getLogger(__name__)


def require_internal_token(request: Request, authorization: str | None = Header(default=None)) -> None:
    expected = request.app.state.settings.internal_token
    if not expected:
        # Fail-closed : sans jeton configuré, aucun traitement n'est autorisé.
        logger.error("SENKYC_AI_INTERNAL_TOKEN is not configured; refusing /v1 request")
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not configured")

    scheme, _, supplied = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not secrets.compare_digest(supplied.encode(), expected.encode()):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Unauthorized",
            headers={"WWW-Authenticate": "Bearer"},
        )
