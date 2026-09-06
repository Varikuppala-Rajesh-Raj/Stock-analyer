from app.retraining import evaluate_candidate, promote_artifact


def metadata(status, accuracy, schema="technical-features-v1"):
    return {
        "status": status,
        "featureSchemaVersion": schema,
        "validationMetrics": {"accuracy": accuracy},
    }


def test_retraining_promotes_only_a_strictly_better_candidate():
    current = metadata("READY", 0.70)
    assert evaluate_candidate(current, metadata("READY", 0.71))['decision'] == "PROMOTE"
    assert evaluate_candidate(current, metadata("READY", 0.70))['decision'] == "REJECT"
    assert evaluate_candidate(current, metadata("READY", 0.69))['decision'] == "REJECT"


def test_retraining_rejects_schema_mismatch_and_keeps_backup_on_promotion(tmp_path):
    current = metadata("READY", 0.70)
    assert evaluate_candidate(current, metadata("READY", 0.80, "other-schema"))["reason"] == "feature_schema_mismatch"

    candidate = tmp_path / "candidate.joblib"
    production = tmp_path / "production.joblib"
    backup = tmp_path / "production.previous.joblib"
    candidate.write_text("candidate")
    production.write_text("production")

    promote_artifact(candidate, production, backup)

    assert production.read_text() == "candidate"
    assert backup.read_text() == "production"