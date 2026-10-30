"""Configuration lue depuis l'environnement (aucun secret par défaut)."""

from __future__ import annotations

import os
from dataclasses import dataclass

_LOG_LEVELS = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}


@dataclass(frozen=True)
class Settings:
    # Jeton partagé avec l'API Java. S'il est absent, /v1/* est refermé (fail-closed).
    internal_token: str | None = None
    log_level: str = "INFO"

    @classmethod
    def from_env(cls) -> Settings:
        token = os.environ.get("SENKYC_AI_INTERNAL_TOKEN", "").strip() or None
        level = os.environ.get("SENKYC_AI_LOG_LEVEL", "INFO").strip().upper()
        return cls(internal_token=token, log_level=level if level in _LOG_LEVELS else "INFO")

    def __repr__(self) -> str:  # ne jamais journaliser le jeton
        return f"Settings(internal_token={'<set>' if self.internal_token else '<unset>'}, log_level={self.log_level!r})"
