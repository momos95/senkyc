"""Point d'entrée : `uvicorn senkyc_ai.main:create_app --factory`."""

from __future__ import annotations

from fastapi import Depends, FastAPI

from . import __version__
from .api import health, info
from .config import Settings
from .correlation import CorrelationIdMiddleware
from .logging_config import configure_logging
from .security import require_internal_token


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    configure_logging(settings.log_level)

    # Service interne : pas de documentation interactive exposée.
    app = FastAPI(title="SenKYC AI", version=__version__, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings = settings
    app.add_middleware(CorrelationIdMiddleware)
    app.include_router(health.router)
    app.include_router(info.router, prefix="/v1", dependencies=[Depends(require_internal_token)])
    return app
