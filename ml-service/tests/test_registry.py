from fastapi.testclient import TestClient

from app import main
from app.registry import ModelRegistry


def test_registry_promotes_validated_model_and_retires_previous(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "registry", ModelRegistry(tmp_path / "registry.json"))
    client = TestClient(main.app)
    base = {
        "modelName": "nifty-direction",
        "featureSchemaVersion": "technical-features-v1",
        "trainingTimestamp": "2024-01-01T00:00:00Z",
        "validationMetrics": {"accuracy": 0.75},
    }

    first = client.post("/api/v1/models/registry", json={**base, "modelVersion": "v1", "status": "PRODUCTION"})
    candidate = client.post("/api/v1/models/registry", json={**base, "modelVersion": "v2", "status": "VALIDATED"})
    promoted = client.post("/api/v1/models/registry/promote", json={"modelName": "nifty-direction", "modelVersion": "v2"})

    assert first.status_code == 200
    assert candidate.status_code == 200
    assert promoted.status_code == 200
    records = client.get("/api/v1/models/registry").json()
    assert {record["modelVersion"]: record["status"] for record in records} == {"v1": "RETIRED", "v2": "PRODUCTION"}