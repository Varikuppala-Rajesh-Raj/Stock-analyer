"""ML service for the canonical Java technical-feature schema."""

from datetime import datetime, timezone
from pathlib import Path
from typing import Literal
import os
import traceback
from app.option_magnitude_v2 import router as option_magnitude_v2_router
import joblib
import numpy as np

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from sklearn.linear_model import LogisticRegression, Ridge
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    mean_absolute_error,
    mean_squared_error,
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


# ============================================================
# IN-MEMORY MODELS / METADATA
# ============================================================

model = None

metadata: dict = {
    "status": "MODEL_NOT_TRAINED",
    "modelVersion": None,
}


magnitude_model = None

magnitude_metadata: dict = {
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
            option_magnitude_model = saved["model"]; option_magnitude_metadata = saved["metadata"]; option_magnitude_metadata["status"] = "READY"
    except Exception as error:
        option_magnitude_metadata = {"status": "ERROR", "modelVersion": None, "message": f"Unable to load option magnitude model: {error}"}


# ============================================================
# LOAD PERSISTED DIRECTION MODEL
# ============================================================


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

            metadata["status"] = "READY"

    except Exception as error:

        metadata = {
            "status": "ERROR",
            "modelVersion": None,
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

            magnitude_metadata[
                "status"
            ] = "READY"

    except Exception as error:

        magnitude_metadata = {
            "status": "ERROR",
            "modelVersion": None,
            "message": (
                "Unable to load persisted "
                f"magnitude model: {error}"
            ),
        }


# ============================================================
# STARTUP
# ============================================================


@app.on_event("startup")
def startup() -> None:

    load_persisted_model()

    load_persisted_magnitude_model()
    load_persisted_option_magnitude_model()


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

    split = int(
        len(rows) * 0.80
    )

    x_train = X[:split]

    x_validation = X[split:]

    y_train = y[:split]

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

        metadata = {

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

            "validationRows":
                len(x_validation),

            "classDistribution":
                distribution,

            "validationMethod":
                "chronological 80/20 holdout",

            "validationMetrics": {

                "accuracy":
                    round(
                        float(
                            accuracy_score(
                                y_validation,
                                predicted,
                            )
                        ),
                        4,
                    ),

                "f1Macro":
                    round(
                        float(
                            f1_score(
                                y_validation,
                                predicted,
                                average="macro",
                                zero_division=0,
                            )
                        ),
                        4,
                    ),
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

    split = int(
        len(rows) * 0.80
    )

    x_train = X[:split]

    x_validation = X[split:]

    y_train = y[:split]

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

        magnitude_metadata = {

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
                FEATURE_SCHEMA_VERSION,

            "featureNames":
                FEATURE_NAMES,

            "totalRows":
                len(rows),

            "trainingRows":
                len(x_train),

            "validationRows":
                len(x_validation),

            "validationMethod":
                "chronological 80/20 holdout",

            "target":
                "future_return_percent",

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


@app.post("/api/v1/nifty/predict")
def predict(
    request: PredictionRequest,
) -> dict:

    if (
        metadata.get("status")
        != "READY"
        or model is None
    ):

        raise HTTPException(
            status_code=409,
            detail="MODEL_NOT_TRAINED",
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
        magnitude_metadata.get(
            "status"
        )
        != "READY"
        or magnitude_model is None
    ):

        raise HTTPException(
            status_code=409,
            detail=(
                "MAGNITUDE_MODEL_NOT_TRAINED"
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