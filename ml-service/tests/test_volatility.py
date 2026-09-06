from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app import main


def features(value: float) -> dict[str, float]:
    return {name: value for name in main.FEATURE_NAMES}


def test_volatility_training_uses_chronological_holdout_and_predicts(tmp_path, monkeypatch):
    model_file = tmp_path / "nifty_volatility_model.joblib"
    monkeypatch.setattr(main, "VOLATILITY_MODEL_FILE", model_file)
    main.volatility_model = None
    main.volatility_metadata = {"status": "MODEL_NOT_TRAINED", "modelVersion": None}

    start = datetime(2024, 1, 1, tzinfo=timezone.utc)
    rows = [
        {
            "timestamp": (start + timedelta(minutes=index)).isoformat(),
            "features": features(float(index)),
            "target_volatility": 0.1 + index * 0.01,
        }
        for index in range(20)
    ]
    client = TestClient(main.app)

    response = client.post(
        "/api/v1/nifty/volatility/train",
        json={
            "symbol": "NIFTY",
            "horizon_minutes": 15,
            "feature_schema_version": main.FEATURE_SCHEMA_VERSION,
            "training_rows": rows,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "READY"
    assert body["validationMethod"] == "chronological timestamp 80/20 holdout"
    assert body["validationStartTimestamp"] > body["trainingEndTimestamp"]

    prediction = client.post(
        "/api/v1/nifty/volatility/predict",
        json={
            "symbol": "NIFTY",
            "horizon_minutes": 15,
            "feature_schema_version": main.FEATURE_SCHEMA_VERSION,
            "technical_features": features(21.0),
        },
    )

    assert prediction.status_code == 200
    assert prediction.json()["predictedVolatility"] >= 0