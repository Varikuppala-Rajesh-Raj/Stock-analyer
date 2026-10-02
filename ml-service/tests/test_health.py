from fastapi.testclient import TestClient
from app import main


def test_health_is_available():
    response = TestClient(main.app).get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_bootstrap_trains_baseline_models_when_missing(tmp_path, monkeypatch):
    model_dir = tmp_path / "models"
    model_dir.mkdir()

    monkeypatch.setattr(main, "MODEL_DIR", model_dir)
    monkeypatch.setattr(main, "DIRECTION_MODEL_FILE", model_dir / "nifty_model.joblib")
    monkeypatch.setattr(main, "MAGNITUDE_MODEL_FILE", model_dir / "nifty_magnitude_model.joblib")

    main.model = None
    main.metadata = {"status": "UNTRAINED", "modelVersion": None, "eligibleForAutomation": False}
    main.magnitude_model = None
    main.magnitude_metadata = {"status": "UNTRAINED", "modelVersion": None, "eligibleForAutomation": False}

    main.ensure_baseline_models()

    assert main.metadata["status"] in {"SYNTHETIC_BASELINE", "TRAINED_UNVALIDATED", "VALIDATED"}
    assert main.magnitude_metadata["status"] in {"SYNTHETIC_BASELINE", "TRAINED_UNVALIDATED", "VALIDATED"}
    assert main.metadata.get("eligibleForAutomation", False) is False
    assert main.magnitude_metadata.get("eligibleForAutomation", False) is False
    assert main.metadata["purgedRows"] == 3
    assert main.magnitude_metadata["purgedRows"] == 3
    assert main.metadata["trainingEndTimestamp"] < main.metadata["validationStartTimestamp"]
    assert main.magnitude_metadata["trainingEndTimestamp"] < main.magnitude_metadata["validationStartTimestamp"]
    assert "forward-label purge" in main.metadata["validationMethod"]
    assert "zeroReturnBaselineMaePercent" in main.magnitude_metadata["validationMetrics"]
    assert main.DIRECTION_MODEL_FILE.exists()
    assert main.MAGNITUDE_MODEL_FILE.exists()
