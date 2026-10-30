from fastapi import APIRouter

router = APIRouter()


@router.get("/health")
def health() -> dict[str, str]:
    """Liveness du processus. Public (réseau interne uniquement), sans information sensible."""
    return {"status": "UP"}
