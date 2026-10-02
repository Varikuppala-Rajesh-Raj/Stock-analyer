from datetime import datetime, timedelta, timezone

from app import main


def train_option_model(tmp_path, monkeypatch, targets):
    model_file = tmp_path / "option-model.joblib"
    monkeypatch.setattr(main, "OPTION_MAGNITUDE_MODEL_FILE", model_file)
    monkeypatch.setattr(main, "option_magnitude_model", None)
    monkeypatch.setattr(main, "option_magnitude_metadata", {})

    rows = []
    for index, target in enumerate(targets):
        features = {name: 0.0 for name in main.OPTION_FEATURE_NAMES}
        features[main.OPTION_FEATURE_NAMES[0]] = float(index)
        rows.append(
            main.OptionMagnitudeTrainingRow(
                timestamp=datetime(2026, 1, 1, tzinfo=timezone.utc) + timedelta(minutes=5 * index),
                features=features,
                target=target,
            )
        )

    return main.train_option_magnitude(
        main.OptionMagnitudeTrainingRequest(
            symbol="NIFTY",
            horizon_minutes=15,
            feature_schema_version=main.OPTION_FEATURE_SCHEMA_VERSION,
            training_rows=rows,
        )
    )


def test_option_model_is_eligible_only_when_holdout_beats_zero_return_baseline(tmp_path, monkeypatch):
    metadata = train_option_model(
        tmp_path,
        monkeypatch,
        [1.0 + 0.02 * index for index in range(100)],
    )

    assert metadata["status"] == "READY"
    assert metadata["validationStatus"] == "VALIDATED"
    assert metadata["eligibleForAutomation"] is True
    assert metadata["trainingRows"] > 0
    assert metadata["validationRows"] > 0
    assert "chronological" in metadata["validationMethod"]
    assert metadata["validationMetrics"]["maePercent"] < metadata["validationMetrics"]["zeroReturnBaselineMaePercent"]
    assert metadata["validationMetrics"]["rmsePercent"] < metadata["validationMetrics"]["zeroReturnBaselineRmsePercent"]


def test_option_model_that_does_not_beat_baseline_is_not_automation_eligible(tmp_path, monkeypatch):
    metadata = train_option_model(
        tmp_path,
        monkeypatch,
        [1.0 if index % 2 else -1.0 for index in range(100)],
    )

    assert metadata["status"] == "READY"
    assert metadata["validationStatus"] == "UNVALIDATED"
    assert metadata["eligibleForAutomation"] is False