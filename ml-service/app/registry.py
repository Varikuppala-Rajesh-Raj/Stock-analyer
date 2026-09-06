"""Durable model lifecycle registry independent of model binary artifacts."""

from __future__ import annotations

import json
import os
from pathlib import Path
from tempfile import NamedTemporaryFile


STATUSES = {"TRAINING", "VALIDATED", "PRODUCTION", "RETIRED"}


class ModelRegistry:
    def __init__(self, path: Path | None = None):
        self.path = path or Path(os.getenv("ML_MODEL_REGISTRY", "/app/models/model-registry.json"))

    def list(self) -> list[dict]:
        if not self.path.exists():
            return []
        return json.loads(self.path.read_text(encoding="utf-8"))

    def register(self, entry: dict) -> dict:
        required = ("modelName", "modelVersion", "featureSchemaVersion", "trainingTimestamp")
        if any(not entry.get(field) for field in required):
            raise ValueError("modelName, modelVersion, featureSchemaVersion, and trainingTimestamp are required")
        status = entry.get("status", "TRAINING")
        if status not in STATUSES:
            raise ValueError(f"Unsupported model status: {status}")
        records = [item for item in self.list()
                   if not (item.get("modelName") == entry["modelName"]
                           and item.get("modelVersion") == entry["modelVersion"])]
        record = {**entry, "status": status}
        records.append(record)
        self._write(records)
        return record

    def promote(self, model_name: str, model_version: str) -> dict:
        records = self.list()
        target = next((item for item in records
                       if item.get("modelName") == model_name
                       and item.get("modelVersion") == model_version), None)
        if target is None:
            raise KeyError("Model version is not registered")
        if target.get("status") not in {"VALIDATED", "PRODUCTION"}:
            raise ValueError("Only validated models can be promoted")
        for item in records:
            if item.get("modelName") == model_name and item.get("status") == "PRODUCTION":
                item["status"] = "RETIRED"
        target["status"] = "PRODUCTION"
        self._write(records)
        return target

    def _write(self, records: list[dict]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with NamedTemporaryFile("w", encoding="utf-8", dir=self.path.parent, delete=False) as temporary:
            json.dump(records, temporary, indent=2, sort_keys=True)
            temporary.write("\n")
            temporary_path = Path(temporary.name)
        temporary_path.replace(self.path)