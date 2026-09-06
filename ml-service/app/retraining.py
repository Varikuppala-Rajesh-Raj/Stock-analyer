"""Candidate model evaluation and rollback-safe artifact promotion."""

from pathlib import Path
from shutil import copy2


def evaluate_candidate(current: dict | None, candidate: dict,
                       minimum_improvement: float = 0.0) -> dict:
    if candidate.get("status") != "READY":
        return {"decision": "REJECT", "reason": "candidate_not_ready"}
    if current and candidate.get("featureSchemaVersion") != current.get("featureSchemaVersion"):
        return {"decision": "REJECT", "reason": "feature_schema_mismatch"}
    if not current or current.get("status") != "READY":
        return {"decision": "PROMOTE", "reason": "no_ready_production_model"}

    current_score, candidate_score, higher_is_better = _comparable_score(current, candidate)
    if current_score is None or candidate_score is None:
        return {"decision": "REJECT", "reason": "comparable_validation_metric_missing"}
    improvement = candidate_score - current_score if higher_is_better else current_score - candidate_score
    decision = "PROMOTE" if improvement > minimum_improvement else "REJECT"
    return {"decision": decision, "reason": "candidate_improves_validation" if decision == "PROMOTE" else "no_required_improvement", "improvement": improvement}


def promote_artifact(candidate_path: Path, production_path: Path, backup_path: Path) -> None:
    if not candidate_path.exists():
        raise FileNotFoundError(f"Candidate model artifact does not exist: {candidate_path}")
    production_path.parent.mkdir(parents=True, exist_ok=True)
    if production_path.exists():
        copy2(production_path, backup_path)
    candidate_path.replace(production_path)


def _comparable_score(current: dict, candidate: dict) -> tuple[float | None, float | None, bool]:
    current_metrics = current.get("validationMetrics", {})
    candidate_metrics = candidate.get("validationMetrics", {})
    for name in ("accuracy", "directionAccuracy", "f1Macro", "directionMacroF1"):
        if name in current_metrics and name in candidate_metrics:
            return float(current_metrics[name]), float(candidate_metrics[name]), True
    for name in ("mae", "maePercent", "gapMaePercent", "rmse", "rmsePercent", "gapRmsePercent"):
        if name in current_metrics and name in candidate_metrics:
            return float(current_metrics[name]), float(candidate_metrics[name]), False
    return None, None, True