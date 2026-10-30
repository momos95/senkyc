from __future__ import annotations

import logging

from .correlation import CorrelationIdLogFilter

_FORMAT = "%(asctime)s %(levelname)s [%(correlation_id)s] %(name)s: %(message)s"


def configure_logging(level: str) -> None:
    """Installe (de façon idempotente) un handler racine incluant l'identifiant de corrélation."""
    root = logging.getLogger()
    for handler in list(root.handlers):
        if getattr(handler, "_senkyc", False):
            root.removeHandler(handler)
    handler = logging.StreamHandler()
    handler._senkyc = True  # type: ignore[attr-defined]
    handler.addFilter(CorrelationIdLogFilter())
    handler.setFormatter(logging.Formatter(_FORMAT))
    root.addHandler(handler)
    root.setLevel(level)
