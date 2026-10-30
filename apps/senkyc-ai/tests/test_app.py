import pytest
from fastapi.testclient import TestClient

from senkyc_ai import __version__
from senkyc_ai.config import Settings
from senkyc_ai.main import create_app

TOKEN = "test-token-0123456789"


@pytest.fixture
def client() -> TestClient:
    return TestClient(create_app(Settings(internal_token=TOKEN)))


def test_health_is_public(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_info_requires_a_token(client: TestClient) -> None:
    assert client.get("/v1/info").status_code == 401


def test_info_rejects_a_wrong_token(client: TestClient) -> None:
    response = client.get("/v1/info", headers={"Authorization": "Bearer nope"})

    assert response.status_code == 401
    assert response.headers["WWW-Authenticate"] == "Bearer"


def test_info_rejects_a_non_bearer_scheme(client: TestClient) -> None:
    assert client.get("/v1/info", headers={"Authorization": f"Basic {TOKEN}"}).status_code == 401


def test_info_with_valid_token(client: TestClient) -> None:
    response = client.get("/v1/info", headers={"Authorization": f"Bearer {TOKEN}"})

    assert response.status_code == 200
    assert response.json() == {"service": "senkyc-ai", "version": __version__, "models": {}}


def test_v1_is_closed_when_no_token_is_configured() -> None:
    unconfigured = TestClient(create_app(Settings(internal_token=None)))

    assert unconfigured.get("/v1/info").status_code == 503
    assert unconfigured.get("/v1/info", headers={"Authorization": "Bearer anything"}).status_code == 503
    assert unconfigured.get("/health").status_code == 200


def test_correlation_id_is_echoed_when_valid(client: TestClient) -> None:
    response = client.get("/health", headers={"X-Correlation-Id": "corr-12345678"})

    assert response.headers["X-Correlation-Id"] == "corr-12345678"


def test_correlation_id_is_generated_when_missing_or_malformed(client: TestClient) -> None:
    missing = client.get("/health").headers["X-Correlation-Id"]
    malformed = client.get("/health", headers={"X-Correlation-Id": "bad id!"}).headers["X-Correlation-Id"]

    assert len(missing) >= 8
    assert malformed != "bad id!"


def test_openapi_and_docs_are_not_exposed(client: TestClient) -> None:
    assert client.get("/openapi.json").status_code == 404
    assert client.get("/docs").status_code == 404


def test_settings_repr_never_leaks_the_token() -> None:
    assert TOKEN not in repr(Settings(internal_token=TOKEN))
