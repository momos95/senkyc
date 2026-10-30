from fastapi import APIRouter
from pydantic import BaseModel

from .. import __version__

router = APIRouter()


class InfoResponse(BaseModel):
    service: str
    version: str
    # Nom du modèle -> état de chargement. Vide tant qu'aucun modèle n'est embarqué (semaine 4+).
    models: dict[str, str]


@router.get("/info", response_model=InfoResponse)
def info() -> InfoResponse:
    return InfoResponse(service="senkyc-ai", version=__version__, models={})
