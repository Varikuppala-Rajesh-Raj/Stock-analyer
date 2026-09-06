from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app import main


def features(value: float) -> dict[str, float]:
    return {name: value for name in main.FEATURE_NAMES}


def test_opening_model_returns_gap_and_probabilities_from_chronological_training(tmp_path, monkeypatch):
    model_file = tmp_path / "nifty_opening_model.joblib"
    monkeypatch.setattr(main, "OPENING_MODEL_FILE", model_file)
    main.opening_model = None
    main.opening_metadata = {"status": "MODEL_NOT_TRAINED", "modelVersion": None}

    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    directions = ["UP", "DOWN", "FLAT"]
    rows = [
        {
            "timestamp": (start + timedelta(days=index)).isoformat(),
            "features": features(float(index)),
            "target_open_return_percent": (index - 10) * 0.03,
            "target_direction": directions[index % 3],
        }
        for index in range(30)
    ]
    client = TestClient(main.app)

    response = client.post(
        "/api/v1/nifty/opening/train",
        json={
            "symbol": "NIFTY",
            "feature_schema_version": main.FEATURE_SCHEMA_VERSION,
            "training_rows": rows,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "READY"
    assert body["validationMethod"] == "chronological timestamp 80/20 holdout"
    assert body["validationStartTimestamp"] > body["trainingEndTimestamp"]
    assert "directionMacroF1" in body["validationMetrics"]

    prediction = client.post(
        "/api/v1/nifty/opening/predict",
        json={
            "symbol": "NIFTY",
            "feature_schema_version": main.FEATURE_SCHEMA_VERSION,
            "technical_features": features(31.0),
        },
    )

    assert prediction.status_code == 200
    result = prediction.json()
    assert result["direction"] in directions
    assert set(result["probabilities"]) == set(directions)
    assert result["confidence"] >= 0
    assert "expectedGapPercent" in result