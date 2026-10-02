from datetime import datetime, timedelta, timezone

from app import main


def test_direction_training_reports_chronological_metrics_and_preserves_gate(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DIRECTION_MODEL_FILE", tmp_path / "direction-model.joblib")
    monkeypatch.setattr(main, "model", None)
    monkeypatch.setattr(main, "metadata", {})
    targets = ["DOWN", "NEUTRAL", "UP"] * 40
    rows = []
    for index, target in enumerate(targets):
        features = {
            name: float((index + feature_index) % 31)
            for feature_index, name in enumerate(main.FEATURE_NAMES)
        }
        rows.append(
            main.FeatureRow(
                timestamp=datetime(2025, 1, 1, tzinfo=timezone.utc) + timedelta(minutes=5 * index),
                features=features,
                target=target,
            )
        )

    metadata = main.train(
        main.TrainingRequest(
            symbol="NIFTY",
            horizon_minutes=15,
            movement_threshold_percent=0.20,
            feature_schema_version=main.FEATURE_SCHEMA_VERSION,
            training_rows=rows,
        )
    )

    assert metadata["totalRows"] == 120
    assert metadata["trainingRows"] == 93
    assert metadata["purgedRows"] == 3
    assert metadata["validationRows"] == 24
    assert metadata["trainingStartTimestamp"] < metadata["trainingEndTimestamp"]
    assert metadata["validationStartTimestamp"] < metadata["validationEndTimestamp"]
    assert set(metadata["classDistributionBySplit"]) == {"training", "purged", "holdout"}
    assert set(metadata["perClassMetrics"]) == {"DOWN", "NEUTRAL", "UP"}
    assert metadata["validationClassOrder"] == ["DOWN", "NEUTRAL", "UP"]
    assert sum(map(sum, metadata["confusionMatrix"])) == metadata["validationRows"]
    assert metadata["eligibleForAutomation"] is (metadata["status"] == "VALIDATED")


def test_failed_direction_validation_remains_automation_ineligible(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "DIRECTION_MODEL_FILE", tmp_path / "failed-direction-model.joblib")
    monkeypatch.setattr(main, "model", None)
    monkeypatch.setattr(main, "metadata", {})
    targets = ["DOWN", "NEUTRAL", "UP"] * 40
    rows = [
        main.FeatureRow(
            timestamp=datetime(2025, 1, 1, tzinfo=timezone.utc) + timedelta(minutes=5 * index),
            features={name: 1.0 for name in main.FEATURE_NAMES},
            target=target,
        )
        for index, target in enumerate(targets)
    ]

    metadata = main.train(
        main.TrainingRequest(
            symbol="NIFTY",
            horizon_minutes=15,
            movement_threshold_percent=0.20,
            feature_schema_version=main.FEATURE_SCHEMA_VERSION,
            training_rows=rows,
        )
    )

    assert metadata["status"] == "TRAINED_UNVALIDATED"
    assert metadata["validationStatus"] == "UNVALIDATED"
    assert metadata["eligibleForAutomation"] is False