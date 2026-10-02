"""ML service for the canonical Java technical-feature schema."""

from datetime import datetime, timezone
from pathlib import Path
from typing import Literal
import os
import math
import traceback
from app.option_magnitude_v2 import router as option_magnitude_v2_router
from app.registry import ModelRegistry
import joblib
import numpy as np

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from sklearn.linear_model import LogisticRegression, Ridge
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    f1_score,
    mean_absolute_error,
    mean_squared_error,
    precision_recall_fscore_support,
)
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler


# ============================================================
# APPLICATION
# ============================================================

app = FastAPI(
    title="Trading Platform Quant Service",
    version="1.0.0",
)

app.include_router(option_magnitude_v2_router)
registry = ModelRegistry()


# ============================================================
# CANONICAL FEATURE SCHEMA
# ============================================================

FEATURE_SCHEMA_VERSION = "technical-features-v1"

FEATURE_NAMES = [
    "ema9",
    "ema20",
    "ema50",
    "ema_slope_percent",
    "price_to_ema20_percent",
    "price_to_ema50_percent",
    "rsi14",
    "macd",
    "macd_signal",
    "macd_histogram",
    "roc10_percent",
    "atr",
    "atr_percent",
    "bollinger_width_percent",
    "recent_high",
    "recent_low",
    "breakout_strength",
    "relative_volume",
    "vwap_distance_percent",
    "ema20_distance_percent",
    "upper_band_distance_percent",
    "current_volume",
    "average_volume20",
    "regime_score",
    "market_regime_encoded",
    "context_available",
    "market_context_score",
    "overall_technical_score",
]


# ============================================================
# DIRECTION LABELS
# ============================================================

LABELS = {
    "DOWN": 0,
    "NEUTRAL": 1,
    "UP": 2,
}


# ============================================================
# MODEL FILES
# ============================================================

MODEL_DIR = Path(
    os.getenv("ML_MODEL_DIR", "/app/models")
)

DIRECTION_MODEL_FILE = (
    MODEL_DIR / "nifty_model.joblib"
)

MAGNITUDE_MODEL_FILE = (
    MODEL_DIR / "nifty_magnitude_model.joblib"
)
VOLATILITY_MODEL_FILE = MODEL_DIR / "nifty_volatility_model.joblib"
OPENING_MODEL_FILE = MODEL_DIR / "nifty_opening_model.joblib"
OPTION_FEATURE_SCHEMA_VERSION = "nifty-option-features-v1"
OPTION_FEATURE_NAMES = FEATURE_NAMES + [
    "strike", "option_type_encoded", "distance_from_atm_percent", "moneyness",
    "option_ltp", "bid", "ask", "spread", "spread_percent", "option_volume",
    "option_oi", "iv", "delta", "gamma", "theta", "vega", "time_to_expiry_minutes",
    "nifty_direction_down_probability", "nifty_direction_neutral_probability",
    "nifty_direction_up_probability", "nifty_predicted_return_percent", "nifty_expected_move_points",
]
OPTION_MAGNITUDE_MODEL_FILE = MODEL_DIR / "nifty_option_magnitude_model.joblib"


# ============================================================
# REQUEST MODELS
# ============================================================


class FeatureRow(BaseModel):

    timestamp: datetime

    features: dict[str, float]

    target: Literal[
        "UP",
        "DOWN",
        "NEUTRAL",
    ]


class TrainingRequest(BaseModel):

    symbol: Literal["NIFTY"]

    horizon_minutes: int = Field(
        ge=5,
        le=390,
    )

    movement_threshold_percent: float = Field(
        gt=0,
        le=5,
    )

    feature_schema_version: str

    training_rows: list[FeatureRow] = Field(
        min_length=1,
    )


class PredictionRequest(BaseModel):

    symbol: Literal["NIFTY"]

    horizon_minutes: int = Field(
        ge=5,
        le=390,
    )

    movement_threshold_percent: float = Field(
        gt=0,
        le=5,
    )

    feature_schema_version: str

    technical_features: dict[str, float]


class ContextPredictionRequest(BaseModel):

    symbol: Literal["NIFTY"]
    horizon_minutes: int = Field(ge=5, le=390)
    movement_threshold_percent: float = Field(gt=0, le=5)
    timestamp: datetime
    feature_schema_version: str
    context_features: dict[str, float]


# ============================================================
# MAGNITUDE REQUEST MODELS
# ============================================================


class MagnitudeTrainingRow(BaseModel):

    timestamp: datetime

    features: dict[str, float]

    target_return_percent: float


class MagnitudeTrainingRequest(BaseModel):

    symbol: Literal["NIFTY"]

    horizon_minutes: int = Field(
        ge=5,
        le=390,
    )

    feature_schema_version: str

    training_rows: list[MagnitudeTrainingRow] = Field(
        min_length=1,
    )


class VolatilityTrainingRow(BaseModel):
    timestamp: datetime
    features: dict[str, float]
    target_volatility: float


class VolatilityTrainingRequest(BaseModel):
    symbol: Literal["NIFTY"]
    horizon_minutes: int = Field(ge=5, le=390)
    feature_schema_version: str
    training_rows: list[VolatilityTrainingRow] = Field(min_length=1)


class VolatilityPredictionRequest(BaseModel):
    symbol: Literal["NIFTY"]
    horizon_minutes: int = Field(ge=5, le=390)
    feature_schema_version: str
    technical_features: dict[str, float]


class OpeningTrainingRow(BaseModel):
    timestamp: datetime
    features: dict[str, float]
    target_open_return_percent: float
    target_direction: Literal["UP", "DOWN", "FLAT"]


class OpeningTrainingRequest(BaseModel):
    symbol: Literal["NIFTY"]
    feature_schema_version: str
    training_rows: list[OpeningTrainingRow] = Field(min_length=1)


class OpeningPredictionRequest(BaseModel):
    symbol: Literal["NIFTY"]
    feature_schema_version: str
    technical_features: dict[str, float]


class OptionMagnitudeTrainingRow(BaseModel):
    timestamp: datetime
    features: dict[str, float]
    target: float


class OptionMagnitudeTrainingRequest(BaseModel):
    symbol: Literal["NIFTY"]
    horizon_minutes: int = Field(ge=5, le=390)
    feature_schema_version: str
    training_rows: list[OptionMagnitudeTrainingRow] = Field(min_length=1)


class OptionMagnitudePredictionRequest(BaseModel):
    symbol: Literal["NIFTY"]
    horizon_minutes: int = Field(ge=5, le=390)
    feature_schema_version: str
    option_features: dict[str, float]


class RegistryEntryRequest(BaseModel):
    modelName: str
    modelVersion: str
    featureSchemaVersion: str
    trainingTimestamp: datetime
    trainingStartTimestamp: datetime | None = None
    trainingEndTimestamp: datetime | None = None
    validationMetrics: dict[str, float] = {}
    status: str = "TRAINING"


class RegistryPromotionRequest(BaseModel):
    modelName: str
    modelVersion: str


# ============================================================
# IN-MEMORY MODELS / METADATA
# ============================================================

model = None

metadata: dict = {
    "status": "UNTRAINED",
    "modelVersion": None,
    "eligibleForAutomation": False,
    "validationStatus": "UNVALIDATED",
}


magnitude_model = None

magnitude_metadata: dict = {
    "status": "UNTRAINED",
    "modelVersion": None,
    "eligibleForAutomation": False,
    "validationStatus": "UNVALIDATED",
}
volatility_model = None
volatility_metadata: dict = {
    "status": "MODEL_NOT_TRAINED",
    "modelVersion": None,
}
opening_model = None
opening_metadata: dict = {
    "status": "MODEL_NOT_TRAINED",
    "modelVersion": None,
}
option_magnitude_model = None
option_magnitude_metadata: dict = {"status": "MODEL_NOT_TRAINED", "modelVersion": None}


# ============================================================
# FEATURE VALIDATION
# ============================================================


def validate_schema(
    version: str,
    features: dict[str, float],
) -> np.ndarray:

    if version != FEATURE_SCHEMA_VERSION:

        raise HTTPException(
            status_code=422,
            detail=(
                f"Unsupported feature schema: {version}"
            ),
        )

    missing = sorted(
        set(FEATURE_NAMES)
        - set(features)
    )

    unexpected = sorted(
        set(features)
        - set(FEATURE_NAMES)
    )

    if missing or unexpected:

        raise HTTPException(
            status_code=422,
            detail={
                "missingFeatures": missing,
                "unexpectedFeatures": unexpected,
            },
        )

    values = np.asarray(
        [
            features[name]
            for name in FEATURE_NAMES
        ],
        dtype=float,
    )

    if not np.all(
        np.isfinite(values)
    ):

        raise HTTPException(
            status_code=422,
            detail=(
                "Technical features must be "
                "finite numbers."
            ),
        )

    return values


def validate_option_schema(version: str, features: dict[str, float]) -> np.ndarray:
    if version != OPTION_FEATURE_SCHEMA_VERSION:
        raise HTTPException(status_code=422, detail=f"Unsupported option feature schema: {version}")
    missing = sorted(set(OPTION_FEATURE_NAMES) - set(features))
    unexpected = sorted(set(features) - set(OPTION_FEATURE_NAMES))
    if missing or unexpected:
        raise HTTPException(status_code=422, detail={"missingFeatures": missing, "unexpectedFeatures": unexpected})
    values = np.asarray([features[name] for name in OPTION_FEATURE_NAMES], dtype=float)
    if not np.all(np.isfinite(values)):
        raise HTTPException(status_code=422, detail="Option features must be finite numbers.")
    return values


def load_persisted_option_magnitude_model() -> None:
    global option_magnitude_model, option_magnitude_metadata
    if not OPTION_MAGNITUDE_MODEL_FILE.exists(): return
    try:
        saved = joblib.load(OPTION_MAGNITUDE_MODEL_FILE)
        if saved.get("featureSchemaVersion") == OPTION_FEATURE_SCHEMA_VERSION:
            option_magnitude_model = saved["model"]
            option_magnitude_metadata = saved["metadata"]
            option_magnitude_metadata["status"] = "READY"
            option_magnitude_metadata.setdefault("eligibleForAutomation", False)
            option_magnitude_metadata.setdefault("validationStatus", "UNVALIDATED")
    except Exception as error:
        option_magnitude_metadata = {"status": "ERROR", "modelVersion": None, "message": f"Unable to load option magnitude model: {error}"}


# ============================================================
# LOAD PERSISTED DIRECTION MODEL
# ============================================================


def model_status_is_validated(status: str | None) -> bool:
    return str(status or "").upper() == "VALIDATED"


def model_is_eligible_for_prediction(model_metadata: dict | None) -> bool:
    if not model_metadata:
        return False
    return model_status_is_validated(model_metadata.get("status"))


def chronological_purged_split(row_count: int, horizon_minutes: int) -> tuple[int, int]:
    split = int(row_count * 0.8)
    purge_rows = max(1, math.ceil(horizon_minutes / 5))
    train_end = split - purge_rows
    if train_end <= 0 or split >= row_count:
        raise HTTPException(status_code=422, detail="Chronological validation partition is invalid after purging forward labels.")
    return train_end, split


def load_persisted_model() -> None:

    global model
    global metadata

    if not DIRECTION_MODEL_FILE.exists():

        return

    try:

        saved = joblib.load(
            DIRECTION_MODEL_FILE
        )

        if (
            saved.get(
                "featureSchemaVersion"
            )
            == FEATURE_SCHEMA_VERSION
        ):

            model = saved["model"]

            metadata = saved["metadata"]

            metadata.setdefault("eligibleForAutomation", model_status_is_validated(metadata.get("status")))

    except Exception as error:

        metadata = {
            "status": "ERROR",
            "modelVersion": None,
            "eligibleForAutomation": False,
            "validationStatus": "UNVALIDATED",
            "message": (
                "Unable to load persisted "
                f"direction model: {error}"
            ),
        }


# ============================================================
# LOAD PERSISTED MAGNITUDE MODEL
# ============================================================


def load_persisted_magnitude_model() -> None:

    global magnitude_model
    global magnitude_metadata

    if not MAGNITUDE_MODEL_FILE.exists():

        return

    try:

        saved = joblib.load(
            MAGNITUDE_MODEL_FILE
        )

        if (
            saved.get(
                "featureSchemaVersion"
            )
            == FEATURE_SCHEMA_VERSION
        ):

            magnitude_model = saved["model"]

            magnitude_metadata = saved[
                "metadata"
            ]

            magnitude_metadata.setdefault("eligibleForAutomation", model_status_is_validated(magnitude_metadata.get("status")))

    except Exception as error:

        magnitude_metadata = {
            "status": "ERROR",
            "modelVersion": None,
            "eligibleForAutomation": False,
            "validationStatus": "UNVALIDATED",
            "message": (
                "Unable to load persisted "
                f"magnitude model: {error}"
            ),
        }


def load_persisted_volatility_model() -> None:
    global volatility_model, volatility_metadata
    if not VOLATILITY_MODEL_FILE.exists():
        return
    try:
        saved = joblib.load(VOLATILITY_MODEL_FILE)
        if saved.get("featureSchemaVersion") == FEATURE_SCHEMA_VERSION:
            volatility_model = saved["model"]
            volatility_metadata = saved["metadata"]
            volatility_metadata["status"] = "READY"
    except Exception as error:
        volatility_metadata = {
            "status": "ERROR",
            "modelVersion": None,
            "message": f"Unable to load volatility model: {error}",
        }


def load_persisted_opening_model() -> None:
    global opening_model, opening_metadata
    if not OPENING_MODEL_FILE.exists():
        return
    try:
        saved = joblib.load(OPENING_MODEL_FILE)
        if saved.get("featureSchemaVersion") == FEATURE_SCHEMA_VERSION:
            opening_model = saved["model"]
            opening_metadata = saved["metadata"]
            opening_metadata["status"] = "READY"
    except Exception as error:
        opening_metadata = {
            "status": "ERROR",
            "modelVersion": None,
            "message": f"Unable to load opening model: {error}",
        }


# ============================================================
# BASELINE TRAINING BOOTSTRAP
# ============================================================


def build_baseline_training_rows(samples: int = 220) -> list[dict]:
    rows: list[dict] = []
    for index in range(samples):
        trend = math.sin(index / 18.0)
        momentum = (index % 17) / 15.0
        signal = math.sin(index / 7.0) + 0.55 * math.sin(index / 19.0)
        feature_values = {
            "ema9": 100.0 + index * 0.06 + trend * 2.5,
            "ema20": 100.0 + index * 0.08 + trend * 2.0,
            "ema50": 99.5 + index * 0.10 + trend * 1.5,
            "ema_slope_percent": (signal * 0.65) + (momentum * 0.12),
            "price_to_ema20_percent": (signal * 0.38) + (momentum * 0.08),
            "price_to_ema50_percent": (signal * 0.32) + (momentum * 0.07),
            "rsi14": 50.0 + signal * 25.0 + momentum * 10.0,
            "macd": signal * 2.5 + momentum,
            "macd_signal": signal * 1.8 + momentum * 0.7,
            "macd_histogram": signal * 1.1 + momentum * 0.5,
            "roc10_percent": signal * 0.9 + momentum * 0.3,
            "atr": 8.0 + momentum * 6.0 + abs(signal) * 4.0,
            "atr_percent": 0.12 + abs(signal) * 0.3 + momentum * 0.08,
            "bollinger_width_percent": 0.6 + abs(signal) * 0.7 + momentum * 0.2,
            "recent_high": 101.0 + index * 0.05 + abs(signal) * 3.0,
            "recent_low": 98.0 + index * 0.04 - abs(signal) * 2.5,
            "breakout_strength": signal * 0.8 + momentum * 0.4,
            "relative_volume": 0.9 + momentum * 0.7,
            "vwap_distance_percent": signal * 0.9 + momentum * 0.25,
            "ema20_distance_percent": signal * 0.6 + momentum * 0.15,
            "upper_band_distance_percent": signal * 0.4 + momentum * 0.2,
            "current_volume": 1400000.0 + (index % 25) * 50000.0 + abs(signal) * 80000.0,
            "average_volume20": 1300000.0 + (index % 18) * 40000.0,
            "regime_score": 0.5 + signal * 0.35,
            "market_regime_encoded": 1.0 if signal >= 0 else 0.0,
            "context_available": 1.0,
            "market_context_score": 0.5 + signal * 0.35,
            "overall_technical_score": 52.0 + signal * 22.0 + momentum * 8.0,
        }

        future_return = 0.21 * signal + 0.04 * momentum
        if future_return > 0.12:
            target = "UP"
        elif future_return < -0.12:
            target = "DOWN"
        else:
            target = "NEUTRAL"

        rows.append(
            {
                "timestamp": datetime(2024, 1, 1, tzinfo=timezone.utc) + __import__("datetime").timedelta(minutes=index * 5),
                "features": feature_values,
                "target": target,
                "future_return_percent": future_return,
            }
        )

    return rows


def ensure_baseline_models() -> dict:
    global model, metadata, magnitude_model, magnitude_metadata

    if model is not None and metadata.get("status") in {"VALIDATED", "READY", "SYNTHETIC_BASELINE"}:
        return metadata

    if magnitude_model is not None and magnitude_metadata.get("status") in {"VALIDATED", "READY", "SYNTHETIC_BASELINE"}:
        return magnitude_metadata

    if DIRECTION_MODEL_FILE.exists() and MAGNITUDE_MODEL_FILE.exists():
        load_persisted_model()
        load_persisted_magnitude_model()
        if model is not None and metadata.get("status") in {"VALIDATED", "READY", "SYNTHETIC_BASELINE"}:
            return metadata
        return magnitude_metadata

    rows = build_baseline_training_rows()
    direction_rows = [
        {"timestamp": row["timestamp"], "features": row["features"], "target": row["target"]}
        for row in rows
    ]
    magnitude_rows = [
        {"timestamp": row["timestamp"], "features": row["features"], "target_return_percent": row["future_return_percent"]}
        for row in rows
    ]

    direction_payload = {
        "symbol": "NIFTY",
        "horizon_minutes": 15,
        "movement_threshold_percent": 0.2,
        "feature_schema_version": FEATURE_SCHEMA_VERSION,
        "training_rows": [
            {
                "timestamp": row["timestamp"].isoformat(),
                "features": row["features"],
                "target": row["target"],
            }
            for row in direction_rows
        ],
    }
    magnitude_payload = {
        "symbol": "NIFTY",
        "horizon_minutes": 15,
        "feature_schema_version": FEATURE_SCHEMA_VERSION,
        "training_rows": [
            {
                "timestamp": row["timestamp"].isoformat(),
                "features": row["features"],
                "target_return_percent": row["target_return_percent"],
            }
            for row in magnitude_rows
        ],
    }

    DIRECTION_MODEL_FILE.parent.mkdir(parents=True, exist_ok=True)

    direction_training = train(
        TrainingRequest(**direction_payload)
    )
    magnitude_training = train_magnitude(
        MagnitudeTrainingRequest(**magnitude_payload)
    )

    direction_training["status"] = "SYNTHETIC_BASELINE"
    direction_training["validationStatus"] = "UNVALIDATED"
    direction_training["eligibleForAutomation"] = False
    direction_training["validationMetrics"] = {
        **direction_training.get("validationMetrics", {}),
        "syntheticBaseline": True,
    }

    magnitude_training["status"] = "SYNTHETIC_BASELINE"
    magnitude_training["validationStatus"] = "UNVALIDATED"
    magnitude_training["eligibleForAutomation"] = False
    magnitude_training["validationMetrics"] = {
        **magnitude_training.get("validationMetrics", {}),
        "syntheticBaseline": True,
    }

    metadata = direction_training
    magnitude_metadata = magnitude_training
    return metadata


@app.on_event("startup")
def startup() -> None:

    load_persisted_model()
    load_persisted_magnitude_model()
    load_persisted_option_magnitude_model()
    load_persisted_volatility_model()
    load_persisted_opening_model()

    if metadata.get("status") not in {"VALIDATED", "SYNTHETIC_BASELINE"} or magnitude_metadata.get("status") not in {"VALIDATED", "SYNTHETIC_BASELINE"}:
        ensure_baseline_models()


# ============================================================
# HEALTH
# ============================================================


@app.get("/health")
def health() -> dict:

    return {
        "status": "UP",
        "service": "ml-service",
        "timestamp": datetime.now(
            timezone.utc
        ),
    }


@app.get("/api/v1/models/registry")
def model_registry() -> list[dict]:
    return registry.list()


@app.post("/api/v1/models/registry")
def register_model(request: RegistryEntryRequest) -> dict:
    try:
        return registry.register(request.model_dump(mode="json"))
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


@app.post("/api/v1/models/registry/promote")
def promote_model(request: RegistryPromotionRequest) -> dict:
    try:
        return registry.promote(request.modelName, request.modelVersion)
    except KeyError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except ValueError as error:
        raise HTTPException(status_code=409, detail=str(error)) from error


# ============================================================
# DIRECTION MODEL STATUS
# ============================================================


@app.get("/api/v1/model/status")
def status() -> dict:

    return metadata


# ============================================================
# MAGNITUDE MODEL STATUS
# ============================================================


@app.get("/api/v1/nifty/magnitude/status")
def magnitude_status() -> dict:

    return magnitude_metadata


@app.get("/api/v1/nifty/volatility/status")
def volatility_status() -> dict:
    return volatility_metadata


@app.get("/api/v1/nifty/opening/status")
def opening_status() -> dict:
    return opening_metadata


@app.get("/api/v1/nifty/options/magnitude/status")
def option_magnitude_status() -> dict:
    return option_magnitude_metadata


@app.post("/api/v1/nifty/options/magnitude/train")
def train_option_magnitude(
    request: OptionMagnitudeTrainingRequest
) -> dict:

    global option_magnitude_model
    global option_magnitude_metadata

    # ========================================================
    # 1. SORT ALL ROWS CHRONOLOGICALLY
    # ========================================================

    rows = sorted(
        request.training_rows,
        key=lambda row: row.timestamp
    )

    if len(rows) < 80:
        raise HTTPException(
            status_code=422,
            detail=(
                "At least 80 option training rows are required; "
                f"received {len(rows)}."
            )
        )

    # ========================================================
    # 2. SPLIT BY TIMESTAMP
    #
    # IMPORTANT:
    # All option contracts belonging to the same market
    # timestamp stay in the same partition.
    # ========================================================

    timestamps = sorted({
        row.timestamp
        for row in rows
    })

    if len(timestamps) < 2:
        raise HTTPException(
            status_code=422,
            detail=(
                "At least two distinct timestamps are "
                "required for chronological validation."
            )
        )

    timestamp_split = int(
        len(timestamps) * 0.80
    )

    if (
        timestamp_split <= 0
        or timestamp_split >= len(timestamps)
    ):
        raise HTTPException(
            status_code=422,
            detail=(
                "Chronological timestamp validation "
                "partition is invalid."
            )
        )

    train_timestamps = set(
        timestamps[:timestamp_split]
    )

    validation_timestamps = set(
        timestamps[timestamp_split:]
    )

    train_rows = [
        row
        for row in rows
        if row.timestamp in train_timestamps
    ]

    validation_rows = [
        row
        for row in rows
        if row.timestamp in validation_timestamps
    ]

    if not train_rows or not validation_rows:
        raise HTTPException(
            status_code=422,
            detail=(
                "Chronological timestamp split produced "
                "an empty training or validation partition."
            )
        )

    # ========================================================
    # 3. FEATURE MATRIX
    # ========================================================

    X_train = np.vstack([
        validate_option_schema(
            request.feature_schema_version,
            row.features
        )
        for row in train_rows
    ])

    X_validation = np.vstack([
        validate_option_schema(
            request.feature_schema_version,
            row.features
        )
        for row in validation_rows
    ])

    # ========================================================
    # 4. TARGET
    # ========================================================

    y_train = np.asarray(
        [
            row.target
            for row in train_rows
        ],
        dtype=float
    )

    y_validation = np.asarray(
        [
            row.target
            for row in validation_rows
        ],
        dtype=float
    )

    if not np.all(np.isfinite(y_train)):
        raise HTTPException(
            status_code=422,
            detail=(
                "Training option return targets "
                "must be finite numbers."
            )
        )

    if not np.all(np.isfinite(y_validation)):
        raise HTTPException(
            status_code=422,
            detail=(
                "Validation option return targets "
                "must be finite numbers."
            )
        )

    # ========================================================
    # 5. MODEL STATE
    # ========================================================

    option_magnitude_metadata = {
        "status": "TRAINING",
        "modelVersion": None
    }

    try:

        # ====================================================
        # 6. RIDGE MODEL
        # ====================================================

        trained = make_pipeline(
            StandardScaler(),
            Ridge(alpha=1.0)
        )

        trained.fit(
            X_train,
            y_train
        )

        # ====================================================
        # 7. VALIDATION
        # ====================================================

        predicted = trained.predict(
            X_validation
        )

        # ====================================================
        # 8. METRICS
        # ====================================================

        mae = mean_absolute_error(
            y_validation,
            predicted
        )

        rmse = np.sqrt(
            mean_squared_error(
                y_validation,
                predicted
            )
        )

        denominator = np.sum(
            (
                y_validation
                - np.mean(y_validation)
            ) ** 2
        )

        r2 = (
            1
            - (
                np.sum(
                    (
                        y_validation
                        - predicted
                    ) ** 2
                )
                / denominator
            )
            if (
                len(y_validation) > 1
                and denominator > 0
            )
            else None
        )

        zero_return_baseline_mae = float(np.mean(np.abs(y_validation)))
        zero_return_baseline_rmse = float(np.sqrt(np.mean(np.square(y_validation))))
        validation_status = (
            "VALIDATED"
            if mae < zero_return_baseline_mae and rmse < zero_return_baseline_rmse
            else "UNVALIDATED"
        )

        # ====================================================
        # 9. MODEL VERSION
        # ====================================================

        version = (
            "nifty-option-magnitude-ridge-"
            + datetime.now(
                timezone.utc
            ).strftime(
                "%Y%m%dT%H%M%SZ"
            )
        )

        # ====================================================
        # 10. METADATA
        # ====================================================

        option_magnitude_metadata = {

            "status": "READY",

            "modelVersion": version,

            "trainingTimestamp":
                datetime.now(
                    timezone.utc
                ).isoformat(),

            "symbol":
                request.symbol,

            "timeframe":
                "M5",

            "horizonMinutes":
                request.horizon_minutes,

            "featureSchemaVersion":
                OPTION_FEATURE_SCHEMA_VERSION,

            "featureNames":
                OPTION_FEATURE_NAMES,

            "totalRows":
                len(rows),

            "trainingRows":
                len(train_rows),

            "validationRows":
                len(validation_rows),

            "trainingTimestamps":
                len(train_timestamps),

            "validationTimestamps":
                len(validation_timestamps),

            "trainingStartTimestamp":
                min(train_timestamps).isoformat(),

            "trainingEndTimestamp":
                max(train_timestamps).isoformat(),

            "validationStartTimestamp":
                min(validation_timestamps).isoformat(),

            "validationEndTimestamp":
                max(validation_timestamps).isoformat(),

            "validationMethod":
                "chronological timestamp 80/20 holdout",

            "validationStatus": validation_status,

            "eligibleForAutomation": validation_status == "VALIDATED",

            "target":
                "future_option_return_percent",

            "validationMetrics": {

                "maePercent":
                    round(
                        float(mae),
                        6
                    ),

                "rmsePercent":
                    round(
                        float(rmse),
                        6
                    ),

                "zeroReturnBaselineMaePercent":
                    round(zero_return_baseline_mae, 6),

                "zeroReturnBaselineRmsePercent":
                    round(zero_return_baseline_rmse, 6),

                "r2":
                    None
                    if r2 is None
                    else round(
                        float(r2),
                        6
                    )
            }
        }

        # ====================================================
        # 11. SAVE MODEL
        # ====================================================

        OPTION_MAGNITUDE_MODEL_FILE.parent.mkdir(
            parents=True,
            exist_ok=True
        )

        joblib.dump(
            {
                "model": trained,

                "metadata":
                    option_magnitude_metadata,

                "featureSchemaVersion":
                    OPTION_FEATURE_SCHEMA_VERSION
            },
            OPTION_MAGNITUDE_MODEL_FILE
        )

        option_magnitude_model = trained

        return option_magnitude_metadata

    except Exception as error:

        option_magnitude_model = None

        option_magnitude_metadata = {
            "status": "ERROR",
            "modelVersion": None,
            "message": str(error)
        }

        raise HTTPException(
            status_code=500,
            detail=str(error)
        ) from error
# ============================================================
# VOLATILITY MODEL TRAINING
# ============================================================


@app.post("/api/v1/nifty/volatility/train")
def train_volatility(request: VolatilityTrainingRequest) -> dict:
    global volatility_model, volatility_metadata
    rows = sorted(request.training_rows, key=lambda row: row.timestamp)
    if len(rows) < 20:
        raise HTTPException(status_code=422, detail="At least 20 volatility training rows are required.")
    validated = [validate_schema(request.feature_schema_version, row.features) for row in rows]
    targets = np.asarray([row.target_volatility for row in rows], dtype=float)
    if not np.all(np.isfinite(targets)) or np.any(targets < 0):
        raise HTTPException(status_code=422, detail="Volatility targets must be finite and non-negative.")
    timestamps = sorted({row.timestamp for row in rows})
    split = int(len(timestamps) * 0.8)
    if split <= 0 or split >= len(timestamps):
        raise HTTPException(status_code=422, detail="Chronological validation partition is invalid.")
    train_times = set(timestamps[:split])
    validation_times = set(timestamps[split:])
    train_indices = [index for index, row in enumerate(rows) if row.timestamp in train_times]
    validation_indices = [index for index, row in enumerate(rows) if row.timestamp in validation_times]
    if not train_indices or not validation_indices:
        raise HTTPException(status_code=422, detail="Chronological split produced an empty partition.")

    volatility_metadata = {"status": "TRAINING", "modelVersion": None}
    trained = make_pipeline(StandardScaler(), Ridge(alpha=1.0))
    trained.fit(np.vstack(validated)[train_indices], targets[train_indices])
    predicted = trained.predict(np.vstack(validated)[validation_indices])
    mae = mean_absolute_error(targets[validation_indices], predicted)
    rmse = mean_squared_error(targets[validation_indices], predicted) ** 0.5
    model_version = f"nifty-volatility-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"
    volatility_model = trained
    volatility_metadata = {
        "status": "READY",
        "modelVersion": model_version,
        "featureSchemaVersion": FEATURE_SCHEMA_VERSION,
        "horizonMinutes": request.horizon_minutes,
        "trainingRows": len(rows),
        "trainingStartTimestamp": min(train_times).isoformat(),
        "trainingEndTimestamp": max(train_times).isoformat(),
        "validationStartTimestamp": min(validation_times).isoformat(),
        "validationEndTimestamp": max(validation_times).isoformat(),
        "validationMethod": "chronological timestamp 80/20 holdout",
        "target": "future_volatility",
        "validationMetrics": {"mae": round(float(mae), 6), "rmse": round(float(rmse), 6)},
    }
    VOLATILITY_MODEL_FILE.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": trained, "metadata": volatility_metadata,
                 "featureSchemaVersion": FEATURE_SCHEMA_VERSION}, VOLATILITY_MODEL_FILE)
    return volatility_metadata


# ============================================================
# NEXT-SESSION OPENING MODEL TRAINING
# ============================================================


@app.post("/api/v1/nifty/opening/train")
def train_opening(request: OpeningTrainingRequest) -> dict:
    global opening_model, opening_metadata
    rows = sorted(request.training_rows, key=lambda row: row.timestamp)
    if len(rows) < 30:
        raise HTTPException(status_code=422, detail="At least 30 opening training rows are required.")
    validated = [validate_schema(request.feature_schema_version, row.features) for row in rows]
    targets = np.asarray([row.target_open_return_percent for row in rows], dtype=float)
    directions = np.asarray([row.target_direction for row in rows])
    if not np.all(np.isfinite(targets)) or not np.all(targets > -100):
        raise HTTPException(status_code=422, detail="Opening return targets must be finite and greater than -100%.")
    if len(set(directions)) < 2:
        raise HTTPException(status_code=422, detail="At least two opening direction classes are required.")
    timestamps = sorted({row.timestamp for row in rows})
    split = int(len(timestamps) * 0.8)
    if split <= 0 or split >= len(timestamps):
        raise HTTPException(status_code=422, detail="Chronological validation partition is invalid.")
    train_times = set(timestamps[:split])
    validation_times = set(timestamps[split:])
    train_indices = [index for index, row in enumerate(rows) if row.timestamp in train_times]
    validation_indices = [index for index, row in enumerate(rows) if row.timestamp in validation_times]
    if not train_indices or not validation_indices:
        raise HTTPException(status_code=422, detail="Chronological split produced an empty partition.")
    matrix = np.vstack(validated)
    gap_model = make_pipeline(StandardScaler(), Ridge(alpha=1.0))
    direction_model = make_pipeline(StandardScaler(), LogisticRegression(max_iter=1000))
    gap_model.fit(matrix[train_indices], targets[train_indices])
    direction_model.fit(matrix[train_indices], directions[train_indices])
    predicted_gap = gap_model.predict(matrix[validation_indices])
    predicted_direction = direction_model.predict(matrix[validation_indices])
    gap_mae = mean_absolute_error(targets[validation_indices], predicted_gap)
    gap_rmse = mean_squared_error(targets[validation_indices], predicted_gap) ** 0.5
    direction_accuracy = accuracy_score(directions[validation_indices], predicted_direction)
    direction_f1 = f1_score(directions[validation_indices], predicted_direction, average="macro", zero_division=0)
    model_version = f"nifty-opening-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"
    opening_model = {"gap": gap_model, "direction": direction_model}
    opening_metadata = {
        "status": "READY",
        "modelVersion": model_version,
        "featureSchemaVersion": FEATURE_SCHEMA_VERSION,
        "trainingRows": len(rows),
        "trainingStartTimestamp": min(train_times).isoformat(),
        "trainingEndTimestamp": max(train_times).isoformat(),
        "validationStartTimestamp": min(validation_times).isoformat(),
        "validationEndTimestamp": max(validation_times).isoformat(),
        "validationMethod": "chronological timestamp 80/20 holdout",
        "targets": ["tomorrow_open_return_percent", "tomorrow_open_direction"],
        "validationMetrics": {
            "gapMaePercent": round(float(gap_mae), 6),
            "gapRmsePercent": round(float(gap_rmse), 6),
            "directionAccuracy": round(float(direction_accuracy), 6),
            "directionMacroF1": round(float(direction_f1), 6),
        },
    }
    OPENING_MODEL_FILE.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": opening_model, "metadata": opening_metadata,
                 "featureSchemaVersion": FEATURE_SCHEMA_VERSION}, OPENING_MODEL_FILE)
    return opening_metadata


# ============================================================
# DIRECTION MODEL TRAINING
# ============================================================


@app.post("/api/v1/nifty/train")
def train(
    request: TrainingRequest,
) -> dict:

    global model
    global metadata

    rows = sorted(
        request.training_rows,
        key=lambda row: row.timestamp,
    )

    # --------------------------------------------------------
    # Duplicate timestamps
    # --------------------------------------------------------

    if len(
        {
            row.timestamp
            for row in rows
        }
    ) != len(rows):

        raise HTTPException(
            status_code=422,
            detail=(
                "Training rows contain "
                "duplicate timestamps."
            ),
        )

    # --------------------------------------------------------
    # Feature matrix
    # --------------------------------------------------------

    X = np.vstack(
        [
            validate_schema(
                request.feature_schema_version,
                row.features,
            )
            for row in rows
        ]
    )

    # --------------------------------------------------------
    # Direction targets
    # --------------------------------------------------------

    y = np.asarray(
        [
            LABELS[row.target]
            for row in rows
        ],
        dtype=int,
    )

    if len(rows) < 80:

        raise HTTPException(
            status_code=422,
            detail=(
                "At least 80 usable feature "
                f"rows are required; received {len(rows)}."
            ),
        )

    if len(
        np.unique(y)
    ) < 2:

        raise HTTPException(
            status_code=422,
            detail=(
                "Training data must contain "
                "at least two target classes."
            ),
        )

    # --------------------------------------------------------
    # Chronological 80 / 20 split
    # --------------------------------------------------------

    train_end, split = chronological_purged_split(len(rows), request.horizon_minutes)

    x_train = X[:train_end]

    x_validation = X[split:]

    y_train = y[:train_end]

    y_validation = y[split:]

    if (
        len(x_validation) == 0
        or len(np.unique(y_train)) < 2
    ):

        raise HTTPException(
            status_code=422,
            detail=(
                "Chronological training "
                "partition is insufficient "
                "for classification."
            ),
        )

    metadata = {
        "status": "TRAINING",
        "modelVersion": None,
    }

    try:

        # ----------------------------------------------------
        # Direction model
        # ----------------------------------------------------

        trained = make_pipeline(
            StandardScaler(),

            LogisticRegression(
                max_iter=1000,
                class_weight="balanced",
                random_state=42,
            ),
        )

        trained.fit(
            x_train,
            y_train,
        )

        predicted = trained.predict(
            x_validation
        )

        # ----------------------------------------------------
        # Class distribution
        # ----------------------------------------------------

        distribution = {
            name: int(
                np.sum(
                    y == value
                )
            )
            for name, value
            in LABELS.items()
        }

        def split_distribution(values):
            total = len(values)
            return {
                name: {
                    "count": int(np.sum(values == label)),
                    "percentage": round(float(np.mean(values == label) * 100), 4) if total else 0.0,
                }
                for name, label in LABELS.items()
            }

        precision, recall, class_f1, support = precision_recall_fscore_support(
            y_validation,
            predicted,
            labels=[LABELS["DOWN"], LABELS["NEUTRAL"], LABELS["UP"]],
            zero_division=0,
        )

        # ----------------------------------------------------
        # Model version
        # ----------------------------------------------------

        version = (
            "nifty-logistic-"
            + datetime.now(
                timezone.utc
            ).strftime(
                "%Y%m%dT%H%M%SZ"
            )
        )

        # ----------------------------------------------------
        # Metadata
        # ----------------------------------------------------

        validation_accuracy = float(accuracy_score(y_validation, predicted))
        validation_f1 = float(f1_score(y_validation, predicted, average="macro", zero_division=0))
        majority_baseline_accuracy = float(np.mean(y_validation == np.bincount(y_validation).argmax()))
        status = "VALIDATED" if validation_accuracy >= 0.52 and validation_f1 >= 0.35 and validation_accuracy > majority_baseline_accuracy else "TRAINED_UNVALIDATED"

        metadata = {

            "status": status,

            "modelVersion": version,

            "trainingTimestamp":
                datetime.now(
                    timezone.utc
                ).isoformat(),

            "symbol":
                request.symbol,

            "timeframe":
                "M5",

            "horizonMinutes":
                request.horizon_minutes,

            "movementThresholdPercent":
                request.movement_threshold_percent,

            "featureSchemaVersion":
                FEATURE_SCHEMA_VERSION,

            "featureNames":
                FEATURE_NAMES,

            "totalRows":
                len(rows),

            "trainingRows":
                len(x_train),

            "trainingStartTimestamp":
                rows[0].timestamp.isoformat(),

            "purgedRows":
                split - train_end,

            "validationRows":
                len(x_validation),

            "trainingEndTimestamp":
                rows[train_end - 1].timestamp.isoformat(),

            "validationStartTimestamp":
                rows[split].timestamp.isoformat(),

            "validationEndTimestamp":
                rows[-1].timestamp.isoformat(),

            "classDistribution":
                distribution,

            "classDistributionBySplit": {
                "training": split_distribution(y_train),
                "purged": split_distribution(y[train_end:split]),
                "holdout": split_distribution(y_validation),
            },

            "validationClassOrder": ["DOWN", "NEUTRAL", "UP"],

            "confusionMatrix": confusion_matrix(
                y_validation,
                predicted,
                labels=[LABELS["DOWN"], LABELS["NEUTRAL"], LABELS["UP"]],
            ).tolist(),

            "perClassMetrics": {
                name: {
                    "precision": round(float(precision[index]), 4),
                    "recall": round(float(recall[index]), 4),
                    "f1": round(float(class_f1[index]), 4),
                    "support": int(support[index]),
                }
                for index, name in enumerate(["DOWN", "NEUTRAL", "UP"])
            },

            "validationMethod":
                "chronological 80/20 holdout with forward-label purge",

            "validationStatus":
                "VALIDATED" if status == "VALIDATED" else "UNVALIDATED",

            "eligibleForAutomation": status == "VALIDATED",

            "validationMetrics": {

                "accuracy":
                    round(
                        validation_accuracy,
                        4,
                    ),

                "f1Macro":
                    round(
                        validation_f1,
                        4,
                    ),

                "majorityClassAccuracy":
                    round(majority_baseline_accuracy, 4),
            },
        }

        # ----------------------------------------------------
        # Persist direction model
        # ----------------------------------------------------

        DIRECTION_MODEL_FILE.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        joblib.dump(
            {
                "model": trained,

                "metadata": metadata,

                "featureSchemaVersion":
                    FEATURE_SCHEMA_VERSION,
            },
            DIRECTION_MODEL_FILE,
        )

        model = trained

        return metadata

    except Exception as error:

        traceback.print_exc()

        model = None

        metadata = {

            "status": "ERROR",

            "modelVersion": None,

            "message": str(error),
        }

        raise HTTPException(
            status_code=500,
            detail=str(error),
        ) from error


# ============================================================
# OPTION MAGNITUDE V1 DIAGNOSTICS
# ============================================================

@app.post("/api/v1/nifty/options/magnitude/diagnostics")
def diagnose_option_magnitude(
    request: OptionMagnitudeTrainingRequest
) -> dict:

    # --------------------------------------------------------
    # Validate model
    # --------------------------------------------------------

    if (
        option_magnitude_metadata.get("status") != "READY"
        or option_magnitude_model is None
    ):
        raise HTTPException(
            status_code=409,
            detail="OPTION_MAGNITUDE_MODEL_NOT_TRAINED"
        )

    # --------------------------------------------------------
    # Sort rows chronologically
    # --------------------------------------------------------

    rows = sorted(
        request.training_rows,
        key=lambda row: row.timestamp
    )

    # --------------------------------------------------------
    # Same timestamp split as training
    # --------------------------------------------------------

    train_rows, validation_rows = chronological_timestamp_split(rows)

    if not validation_rows:
        raise HTTPException(
            status_code=422,
            detail="Validation rows are empty."
        )

    # --------------------------------------------------------
    # Validate features
    # --------------------------------------------------------

    X_validation = np.vstack([
        validate_option_schema(
            request.feature_schema_version,
            row.features
        )
        for row in validation_rows
    ])

    y_validation = np.asarray(
        [
            row.target
            for row in validation_rows
        ],
        dtype=float
    )

    if not np.all(np.isfinite(y_validation)):
        raise HTTPException(
            status_code=422,
            detail="Validation targets must be finite."
        )

    # --------------------------------------------------------
    # Predict using CURRENT V1 model
    # --------------------------------------------------------

    predicted = option_magnitude_model.predict(
        X_validation
    )

    # --------------------------------------------------------
    # Generic metric helper
    # --------------------------------------------------------

    def metrics(
        actual,
        prediction
    ):

        actual = np.asarray(
            actual,
            dtype=float
        )

        prediction = np.asarray(
            prediction,
            dtype=float
        )

        if len(actual) == 0:
            return {
                "samples": 0,
                "maePercent": None,
                "rmsePercent": None,
                "r2": None
            }

        mae = mean_absolute_error(
            actual,
            prediction
        )

        rmse = np.sqrt(
            mean_squared_error(
                actual,
                prediction
            )
        )

        denominator = np.sum(
            (
                actual
                - np.mean(actual)
            ) ** 2
        )

        r2 = (
            1
            - (
                np.sum(
                    (
                        actual
                        - prediction
                    ) ** 2
                )
                / denominator
            )
            if denominator > 0
            else None
        )

        return {
            "samples": int(len(actual)),
            "maePercent": round(
                float(mae),
                6
            ),
            "rmsePercent": round(
                float(rmse),
                6
            ),
            "r2": (
                None
                if r2 is None
                else round(float(r2), 6)
            )
        }

    # --------------------------------------------------------
    # Overall
    # --------------------------------------------------------

    overall = metrics(
        y_validation,
        predicted
    )

    # --------------------------------------------------------
    # Feature indexes
    # --------------------------------------------------------

    ltp_index = OPTION_FEATURE_NAMES.index(
        "option_ltp"
    )

    option_type_index = OPTION_FEATURE_NAMES.index(
        "option_type_encoded"
    )

    distance_atm_index = OPTION_FEATURE_NAMES.index(
        "distance_from_atm_percent"
    )

    # --------------------------------------------------------
    # LTP bucket
    # --------------------------------------------------------

    def ltp_bucket(value):

        if value < 5:
            return "ltp_<5"

        if value < 20:
            return "ltp_5_20"

        if value < 100:
            return "ltp_20_100"

        if value < 500:
            return "ltp_100_500"

        return "ltp_500_plus"

    ltp_groups = {}

    for index, row in enumerate(validation_rows):

        bucket = ltp_bucket(
            row.features["option_ltp"]
        )

        ltp_groups.setdefault(
            bucket,
            {
                "actual": [],
                "predicted": []
            }
        )

        ltp_groups[bucket]["actual"].append(
            row.target
        )

        ltp_groups[bucket]["predicted"].append(
            predicted[index]
        )

    ltp_diagnostics = {
        bucket: metrics(
            values["actual"],
            values["predicted"]
        )
        for bucket, values
        in ltp_groups.items()
    }

    # --------------------------------------------------------
    # CE / PE
    #
    # option_type_encoded:
    #   Java schema determines the actual encoding.
    #
    # We identify groups from the feature value and expose
    # them as encoded values rather than guessing CE=0/PE=1.
    # --------------------------------------------------------

    option_groups = {}

    for index, row in enumerate(validation_rows):

        encoded = row.features[
            "option_type_encoded"
        ]

        key = str(encoded)

        option_groups.setdefault(
            key,
            {
                "actual": [],
                "predicted": []
            }
        )

        option_groups[key]["actual"].append(
            row.target
        )

        option_groups[key]["predicted"].append(
            predicted[index]
        )

    option_type_diagnostics = {
        key: metrics(
            values["actual"],
            values["predicted"]
        )
        for key, values
        in option_groups.items()
    }

    # --------------------------------------------------------
    # Moneyness / distance from ATM
    # --------------------------------------------------------

    def atm_bucket(value):

        value = abs(value)

        if value <= 1:
            return "near_atm_0_1"

        if value <= 3:
            return "atm_1_3"

        if value <= 5:
            return "atm_3_5"

        return "far_atm_5_plus"

    atm_groups = {}

    for index, row in enumerate(validation_rows):

        bucket = atm_bucket(
            row.features[
                "distance_from_atm_percent"
            ]
        )

        atm_groups.setdefault(
            bucket,
            {
                "actual": [],
                "predicted": []
            }
        )

        atm_groups[bucket]["actual"].append(
            row.target
        )

        atm_groups[bucket]["predicted"].append(
            predicted[index]
        )

    moneyness_diagnostics = {
        bucket: metrics(
            values["actual"],
            values["predicted"]
        )
        for bucket, values
        in atm_groups.items()
    }

    # --------------------------------------------------------
    # Return diagnostic report
    # --------------------------------------------------------

    return {

        "status": "READY",

        "modelVersion":
            option_magnitude_metadata[
                "modelVersion"
            ],

        "featureSchemaVersion":
            OPTION_FEATURE_SCHEMA_VERSION,

        "horizonMinutes":
            request.horizon_minutes,

        "totalRows":
            len(rows),

        "trainingRows":
            len(train_rows),

        "validationRows":
            len(validation_rows),

        "trainingTimestamps":
            len({
                row.timestamp
                for row in train_rows
            }),

        "validationTimestamps":
            len({
                row.timestamp
                for row in validation_rows
            }),

        "validationStartTimestamp":
            min(
                row.timestamp
                for row in validation_rows
            ).isoformat(),

        "validationEndTimestamp":
            max(
                row.timestamp
                for row in validation_rows
            ).isoformat(),

        "overall":
            overall,

        "ltpBuckets":
            ltp_diagnostics,

        "optionTypeEncoded":
            option_type_diagnostics,

        "moneynessBuckets":
            moneyness_diagnostics
    }
# ============================================================
# MAGNITUDE MODEL TRAINING
# ============================================================


@app.post("/api/v1/nifty/magnitude/train")
def train_magnitude(
    request: MagnitudeTrainingRequest,
) -> dict:

    global magnitude_model
    global magnitude_metadata

    print(
        "========== MAGNITUDE TRAINING START =========="
    )

    print(
        f"Training rows: "
        f"{len(request.training_rows)}"
    )

    # --------------------------------------------------------
    # Sort chronologically
    # --------------------------------------------------------

    rows = sorted(
        request.training_rows,
        key=lambda row: row.timestamp,
    )

    # --------------------------------------------------------
    # Duplicate timestamps
    # --------------------------------------------------------

    if len(
        {
            row.timestamp
            for row in rows
        }
    ) != len(rows):

        raise HTTPException(
            status_code=422,
            detail=(
                "Magnitude training rows "
                "contain duplicate timestamps."
            ),
        )

    # --------------------------------------------------------
    # Feature matrix
    # --------------------------------------------------------

    X = np.vstack(
        [
            validate_schema(
                request.feature_schema_version,
                row.features,
            )
            for row in rows
        ]
    )

    # --------------------------------------------------------
    # Continuous target
    #
    # Example:
    #
    # +0.32
    # -0.18
    # +0.71
    #
    # These are future percentage returns.
    # --------------------------------------------------------

    y = np.asarray(
        [
            row.target_return_percent
            for row in rows
        ],
        dtype=float,
    )

    # --------------------------------------------------------
    # Basic validation
    # --------------------------------------------------------

    if len(rows) < 80:

        raise HTTPException(
            status_code=422,
            detail=(
                "At least 80 magnitude "
                "training rows are required; "
                f"received {len(rows)}."
            ),
        )

    if not np.all(
        np.isfinite(y)
    ):

        raise HTTPException(
            status_code=422,
            detail=(
                "Magnitude targets must "
                "contain finite numbers."
            ),
        )

    # --------------------------------------------------------
    # Chronological 80 / 20 split
    # --------------------------------------------------------

    train_end, split = chronological_purged_split(len(rows), request.horizon_minutes)

    x_train = X[:train_end]

    x_validation = X[split:]

    y_train = y[:train_end]

    y_validation = y[split:]

    if len(x_validation) == 0:

        raise HTTPException(
            status_code=422,
            detail=(
                "Magnitude validation "
                "partition is empty."
            ),
        )

    magnitude_metadata = {

        "status": "TRAINING",

        "modelVersion": None,
    }

    try:

        # ----------------------------------------------------
        # Ridge regression
        #
        # Predicts continuous future return %
        # ----------------------------------------------------

        trained = make_pipeline(

            StandardScaler(),

            Ridge(
                alpha=1.0,
            ),
        )

        trained.fit(
            x_train,
            y_train,
        )

        # ----------------------------------------------------
        # Validation prediction
        # ----------------------------------------------------

        predicted = trained.predict(
            x_validation
        )

        # ----------------------------------------------------
        # Metrics
        # ----------------------------------------------------

        mae = mean_absolute_error(
            y_validation,
            predicted,
        )

        rmse = np.sqrt(
            mean_squared_error(
                y_validation,
                predicted,
            )
        )
        zero_return_baseline_mae = float(np.mean(np.abs(y_validation)))
        zero_return_baseline_rmse = float(np.sqrt(np.mean(np.square(y_validation))))

        # ----------------------------------------------------
        # Model version
        # ----------------------------------------------------

        version = (
            "nifty-magnitude-ridge-"
            + datetime.now(
                timezone.utc
            ).strftime(
                "%Y%m%dT%H%M%SZ"
            )
        )

        # ----------------------------------------------------
        # Metadata
        # ----------------------------------------------------

        status = "VALIDATED" if mae <= 0.55 and rmse <= 1.0 and mae < zero_return_baseline_mae and rmse < zero_return_baseline_rmse else "TRAINED_UNVALIDATED"

        magnitude_metadata = {

            "status": status,

            "modelVersion": version,

            "trainingTimestamp":
                datetime.now(
                    timezone.utc
                ).isoformat(),

            "symbol":
                request.symbol,

            "timeframe":
                "M5",

            "horizonMinutes":
                request.horizon_minutes,

            "featureSchemaVersion":
                FEATURE_SCHEMA_VERSION,

            "featureNames":
                FEATURE_NAMES,

            "totalRows":
                len(rows),

            "trainingRows":
                len(x_train),

            "purgedRows":
                split - train_end,

            "validationRows":
                len(x_validation),

            "trainingEndTimestamp":
                rows[train_end - 1].timestamp.isoformat(),

            "validationStartTimestamp":
                rows[split].timestamp.isoformat(),

            "validationMethod":
                "chronological 80/20 holdout with forward-label purge",

            "target":
                "future_return_percent",

            "validationStatus":
                "VALIDATED" if status == "VALIDATED" else "UNVALIDATED",

            "eligibleForAutomation": status == "VALIDATED",

            "validationMetrics": {

                "maePercent":
                    round(
                        float(mae),
                        6,
                    ),

                "rmsePercent":
                    round(
                        float(rmse),
                        6,
                    ),

                "zeroReturnBaselineMaePercent":
                    round(zero_return_baseline_mae, 6),

                "zeroReturnBaselineRmsePercent":
                    round(zero_return_baseline_rmse, 6),
            },
        }

        # ----------------------------------------------------
        # Persist magnitude model
        # ----------------------------------------------------

        MAGNITUDE_MODEL_FILE.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        joblib.dump(
            {
                "model": trained,

                "metadata":
                    magnitude_metadata,

                "featureSchemaVersion":
                    FEATURE_SCHEMA_VERSION,
            },
            MAGNITUDE_MODEL_FILE,
        )

        magnitude_model = trained

        print(
            "Magnitude model training completed."
        )

        print(
            "========== MAGNITUDE TRAINING END =========="
        )

        return magnitude_metadata

    except Exception as error:

        traceback.print_exc()

        magnitude_model = None

        magnitude_metadata = {

            "status": "ERROR",

            "modelVersion": None,

            "message": str(error),
        }

        raise HTTPException(
            status_code=500,
            detail=str(error),
        ) from error


# ============================================================
# DIRECTION MODEL PREDICTION
# ============================================================


@app.post("/api/v1/nifty/volatility/predict")
def predict_volatility(request: VolatilityPredictionRequest) -> dict:
    if volatility_model is None or volatility_metadata.get("status") != "READY":
        raise HTTPException(status_code=409, detail="VOLATILITY_MODEL_NOT_TRAINED")
    if request.horizon_minutes != volatility_metadata.get("horizonMinutes"):
        raise HTTPException(status_code=409, detail="MODEL_HORIZON_MISMATCH")
    values = validate_schema(request.feature_schema_version, request.technical_features).reshape(1, -1)
    predicted = max(0.0, float(volatility_model.predict(values)[0]))
    return {
        "symbol": request.symbol,
        "timestamp": datetime.now(timezone.utc),
        "horizonMinutes": request.horizon_minutes,
        "predictedVolatility": round(predicted, 6),
        "modelVersion": volatility_metadata["modelVersion"],
        "featureSchemaVersion": FEATURE_SCHEMA_VERSION,
    }


@app.post("/api/v1/nifty/opening/predict")
def predict_opening(request: OpeningPredictionRequest) -> dict:
    if opening_model is None or opening_metadata.get("status") != "READY":
        raise HTTPException(status_code=409, detail="OPENING_MODEL_NOT_TRAINED")
    values = validate_schema(request.feature_schema_version, request.technical_features).reshape(1, -1)
    gap = float(opening_model["gap"].predict(values)[0])
    probabilities = {label: 0.0 for label in ("DOWN", "FLAT", "UP")}
    for label, probability in zip(opening_model["direction"].classes_, opening_model["direction"].predict_proba(values)[0]):
        probabilities[str(label)] = round(float(probability), 6)
    direction = max(probabilities, key=probabilities.get)
    return {
        "symbol": request.symbol,
        "timestamp": datetime.now(timezone.utc),
        "direction": direction,
        "probabilities": probabilities,
        "expectedGapPercent": round(gap, 6),
        "confidence": round(probabilities[direction], 6),
        "modelVersion": opening_metadata["modelVersion"],
        "featureSchemaVersion": FEATURE_SCHEMA_VERSION,
    }


@app.post("/api/v1/nifty/context/predict")
def predict_context(request: ContextPredictionRequest) -> dict:
    """Validate the future context contract until a context-trained model exists."""
    if request.feature_schema_version != "nifty-context-features-v1":
        raise HTTPException(
            status_code=422,
            detail="Unsupported context feature schema.",
        )
    if not request.context_features or not all(
        math.isfinite(value) for value in request.context_features.values()
    ):
        raise HTTPException(
            status_code=422,
            detail="Context features must contain finite numbers.",
        )
    raise HTTPException(
        status_code=409,
        detail="CONTEXT_MODEL_NOT_TRAINED",
    )


@app.post("/api/v1/nifty/predict")
def predict(
    request: PredictionRequest,
) -> dict:

    if (
        not model_is_eligible_for_prediction(metadata)
        or model is None
    ):

        raise HTTPException(
            status_code=409,
            detail="MODEL_NOT_VALIDATED",
        )

    if request.horizon_minutes != metadata.get("horizonMinutes"):
        raise HTTPException(
            status_code=409,
            detail=(
                "MODEL_HORIZON_MISMATCH: train the direction model for "
                f"{request.horizon_minutes} minutes before predicting this horizon."
            ),
        )

    values = validate_schema(
        request.feature_schema_version,
        request.technical_features,
    ).reshape(
        1,
        -1,
    )

    probabilities = {
        name: 0.0
        for name in LABELS
    }

    for label, probability in zip(
        model.classes_,
        model.predict_proba(values)[0],
    ):

        name = next(
            name
            for name, value
            in LABELS.items()
            if value == int(label)
        )

        probabilities[name] = round(
            float(probability),
            6,
        )

    prediction = max(
        probabilities,
        key=probabilities.get,
    )

    return {

        "symbol":
            request.symbol,

        "timestamp":
            datetime.now(
                timezone.utc
            ),

        "timeframe":
            "M5",

        "horizonMinutes":
            request.horizon_minutes,

        "prediction":
            prediction,

        "probabilities":
            probabilities,

        "modelVersion":
            metadata["modelVersion"],

        "featureSchemaVersion":
            FEATURE_SCHEMA_VERSION,

        "keyFeatures": {
            name:
                request.technical_features[name]
            for name in FEATURE_NAMES
        },
    }


# ============================================================
# MAGNITUDE MODEL PREDICTION
# ============================================================


@app.post("/api/v1/nifty/magnitude/predict")
def predict_magnitude(
    request: PredictionRequest,
) -> dict:

    if (
        not model_is_eligible_for_prediction(magnitude_metadata)
        or magnitude_model is None
    ):

        raise HTTPException(
            status_code=409,
            detail=(
                "MAGNITUDE_MODEL_NOT_VALIDATED"
            ),
        )

    if request.horizon_minutes != magnitude_metadata.get("horizonMinutes"):
        raise HTTPException(
            status_code=409,
            detail=(
                "MODEL_HORIZON_MISMATCH: train the magnitude model for "
                f"{request.horizon_minutes} minutes before predicting this horizon."
            ),
        )

    values = validate_schema(
        request.feature_schema_version,
        request.technical_features,
    ).reshape(
        1,
        -1,
    )

    predicted_return = float(
        magnitude_model.predict(
            values
        )[0]
    )

    return {

        "symbol":
            request.symbol,

        "timestamp":
            datetime.now(
                timezone.utc
            ),

        "timeframe":
            "M5",

        "horizonMinutes":
            request.horizon_minutes,

        "predictedReturnPercent":
            round(
                predicted_return,
                6,
            ),

        "modelVersion":
            magnitude_metadata[
                "modelVersion"
            ],

        "featureSchemaVersion":
            FEATURE_SCHEMA_VERSION,
    }


@app.post("/api/v1/nifty/options/magnitude/predict")
def predict_option_magnitude(request: OptionMagnitudePredictionRequest) -> dict:
    if option_magnitude_metadata.get("status") != "READY" or option_magnitude_model is None:
        raise HTTPException(status_code=409, detail="OPTION_MAGNITUDE_MODEL_NOT_TRAINED")
    values = validate_option_schema(request.feature_schema_version, request.option_features).reshape(1, -1)
    predicted_return = float(option_magnitude_model.predict(values)[0])
    return {"symbol": request.symbol, "timestamp": datetime.now(timezone.utc), "timeframe":"M5", "horizonMinutes":request.horizon_minutes, "predictedOptionReturnPercent":round(predicted_return, 6), "modelVersion":option_magnitude_metadata["modelVersion"], "featureSchemaVersion":OPTION_FEATURE_SCHEMA_VERSION}


def chronological_timestamp_split(rows, train_ratio=0.80):
    timestamps = sorted({
        row.timestamp
        for row in rows
    })

    if len(timestamps) < 2:
        raise HTTPException(
            status_code=422,
            detail="At least two distinct timestamps are required."
        )

    split_index = int(len(timestamps) * train_ratio)

    if split_index <= 0 or split_index >= len(timestamps):
        raise HTTPException(
            status_code=422,
            detail="Chronological timestamp validation partition is invalid."
        )

    train_timestamps = set(timestamps[:split_index])
    validation_timestamps = set(timestamps[split_index:])

    train_rows = [
        row for row in rows
        if row.timestamp in train_timestamps
    ]

    validation_rows = [
        row for row in rows
        if row.timestamp in validation_timestamps
    ]

    return train_rows, validation_rows