from datetime import datetime, timezone
from pathlib import Path
import os

import joblib
import numpy as np

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler


# ============================================================
# V2 CONFIGURATION
# ============================================================

OPTION_FEATURE_SCHEMA_VERSION = "nifty-option-features-v1"

OPTION_FEATURE_NAMES = [
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
    "strike",
    "option_type_encoded",
    "distance_from_atm_percent",
    "moneyness",
    "option_ltp",
    "bid",
    "ask",
    "spread",
    "spread_percent",
    "option_volume",
    "option_oi",
    "iv",
    "delta",
    "gamma",
    "theta",
    "vega",
    "time_to_expiry_minutes",
    "nifty_direction_down_probability",
    "nifty_direction_neutral_probability",
    "nifty_direction_up_probability",
    "nifty_predicted_return_percent",
    "nifty_expected_move_points",
]


MODEL_DIR = Path(
    os.getenv("ML_MODEL_DIR", "/app/models")
)

MODEL_FILE = (
    MODEL_DIR / "nifty_option_magnitude_v2_model.joblib"
)


# ============================================================
# API ROUTER
# ============================================================

router = APIRouter(
    prefix="/api/v2/nifty/options/magnitude",
    tags=["Option Magnitude V2"],
)


# ============================================================
# REQUEST MODELS
# ============================================================

class OptionMagnitudeTrainingRowV2(BaseModel):

    timestamp: datetime

    features: dict[str, float]

    target: float


class OptionMagnitudeTrainingRequestV2(BaseModel):

    symbol: str

    horizon_minutes: int = Field(
        ge=5,
        le=390,
    )

    feature_schema_version: str

    training_rows: list[
        OptionMagnitudeTrainingRowV2
    ] = Field(min_length=1)


class OptionMagnitudePredictionRequestV2(BaseModel):

    symbol: str

    horizon_minutes: int = Field(
        ge=5,
        le=390,
    )

    feature_schema_version: str

    option_features: dict[str, float]


# ============================================================
# MODEL STATE
# ============================================================

model = None

metadata = {
    "status": "MODEL_NOT_TRAINED",
    "modelVersion": None,
}


# ============================================================
# FEATURE VALIDATION
# ============================================================

def validate_option_schema(
    version: str,
    features: dict[str, float],
) -> np.ndarray:

    if version != OPTION_FEATURE_SCHEMA_VERSION:

        raise HTTPException(
            status_code=422,
            detail=(
                f"Unsupported option feature schema: "
                f"{version}"
            ),
        )

    missing = sorted(
        set(OPTION_FEATURE_NAMES)
        - set(features)
    )

    unexpected = sorted(
        set(features)
        - set(OPTION_FEATURE_NAMES)
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
            for name in OPTION_FEATURE_NAMES
        ],
        dtype=float,
    )

    if not np.all(
        np.isfinite(values)
    ):

        raise HTTPException(
            status_code=422,
            detail=(
                "Option features must be "
                "finite numbers."
            ),
        )

    return values


# ============================================================
# LOAD MODEL
# ============================================================

def load_model() -> None:

    global model
    global metadata

    if not MODEL_FILE.exists():

        return

    try:

        saved = joblib.load(
            MODEL_FILE
        )

        if (
            saved.get(
                "featureSchemaVersion"
            )
            == OPTION_FEATURE_SCHEMA_VERSION
        ):

            model = saved["model"]

            metadata = saved["metadata"]

            metadata["status"] = "READY"

    except Exception as error:

        model = None

        metadata = {
            "status": "ERROR",
            "modelVersion": None,
            "message": (
                "Unable to load option "
                f"magnitude V2 model: {error}"
            ),
        }


# ============================================================
# TRAIN
# ============================================================

@router.post("/train")
def train(
    request: OptionMagnitudeTrainingRequestV2,
) -> dict:

    global model
    global metadata

    rows = sorted(
        request.training_rows,
        key=lambda row: row.timestamp,
    )

    if len(rows) < 80:

        raise HTTPException(
            status_code=422,
            detail=(
                "At least 80 option training "
                f"rows are required; received {len(rows)}."
            ),
        )

    # --------------------------------------------------------
    # Validate all features
    # --------------------------------------------------------

    validated_features = [
        validate_option_schema(
            request.feature_schema_version,
            row.features,
        )
        for row in rows
    ]

    # --------------------------------------------------------
    # Raw target
    # --------------------------------------------------------

    raw_return_percent = np.asarray(
        [
            row.target
            for row in rows
        ],
        dtype=float,
    )

    if not np.all(
        np.isfinite(raw_return_percent)
    ):

        raise HTTPException(
            status_code=422,
            detail=(
                "Option return targets must "
                "be finite numbers."
            ),
        )

    # --------------------------------------------------------
    # Log-return transformation
    # --------------------------------------------------------

    if np.any(
        raw_return_percent <= -100.0
    ):

        raise HTTPException(
            status_code=422,
            detail=(
                "Log-return transformation requires "
                "returns greater than -100%."
            ),
        )

    y_log = np.log1p(
        raw_return_percent / 100.0
    )

    # --------------------------------------------------------
    # TIMESTAMP-BASED 80 / 20 SPLIT
    # --------------------------------------------------------

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
            ),
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
            ),
        )

    train_timestamps = set(
        timestamps[:timestamp_split]
    )

    validation_timestamps = set(
        timestamps[timestamp_split:]
    )

    train_indices = [
        index
        for index, row in enumerate(rows)
        if row.timestamp in train_timestamps
    ]

    validation_indices = [
        index
        for index, row in enumerate(rows)
        if row.timestamp in validation_timestamps
    ]

    if (
        not train_indices
        or not validation_indices
    ):

        raise HTTPException(
            status_code=422,
            detail=(
                "Chronological timestamp split produced "
                "an empty training or validation partition."
            ),
        )

    # --------------------------------------------------------
    # Feature matrices
    # --------------------------------------------------------

    X = np.vstack(
        validated_features
    )

    x_train = X[train_indices]

    x_validation = X[validation_indices]

    # --------------------------------------------------------
    # Log targets
    # --------------------------------------------------------

    y_train = y_log[train_indices]

    y_validation = y_log[validation_indices]

    # --------------------------------------------------------
    # MODEL STATE
    # --------------------------------------------------------

    metadata = {
        "status": "TRAINING",
        "modelVersion": None,
    }

    try:

        # ----------------------------------------------------
        # Same Ridge architecture as V1
        # ----------------------------------------------------

        trained = make_pipeline(
            StandardScaler(),
            Ridge(alpha=1.0),
        )

        trained.fit(
            x_train,
            y_train,
        )

        # ----------------------------------------------------
        # Predict log return
        # ----------------------------------------------------

        predicted_log = trained.predict(
            x_validation
        )

        # ----------------------------------------------------
        # Convert back to percentage
        # ----------------------------------------------------

        predicted_return_percent = (
            np.expm1(predicted_log)
            * 100.0
        )

        actual_return_percent = (
            np.expm1(y_validation)
            * 100.0
        )

        # ----------------------------------------------------
        # Metrics in original percentage space
        # ----------------------------------------------------

        mae = mean_absolute_error(
            actual_return_percent,
            predicted_return_percent,
        )

        rmse = np.sqrt(
            mean_squared_error(
                actual_return_percent,
                predicted_return_percent,
            )
        )

        denominator = np.sum(
            (
                actual_return_percent
                - np.mean(actual_return_percent)
            ) ** 2
        )

        if denominator > 0:

            r2 = (
                1
                - np.sum(
                    (
                        actual_return_percent
                        - predicted_return_percent
                    ) ** 2
                )
                / denominator
            )

        else:

            r2 = None

        # ----------------------------------------------------
        # Version
        # ----------------------------------------------------

        version = (
            "nifty-option-magnitude-log-ridge-v2-"
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

            "featureSchemaVersion":
                OPTION_FEATURE_SCHEMA_VERSION,

            "featureNames":
                OPTION_FEATURE_NAMES,

            "totalRows":
                len(rows),

            "trainingRows":
                len(train_indices),

            "validationRows":
                len(validation_indices),

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
                "log_future_option_return",

            "targetTransformation":
                "log1p(return_percent / 100)",

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

                "r2":
                    None
                    if r2 is None
                    else round(
                        float(r2),
                        6,
                    ),
            },
        }

        # ----------------------------------------------------
        # Persist V2
        # ----------------------------------------------------

        MODEL_FILE.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        joblib.dump(
            {
                "model": trained,

                "metadata": metadata,

                "featureSchemaVersion":
                    OPTION_FEATURE_SCHEMA_VERSION,

                "targetTransformation":
                    "log1p(return_percent / 100)",
            },
            MODEL_FILE,
        )

        model = trained

        return metadata

    except Exception as error:

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
# STATUS
# ============================================================

@router.get("/status")
def status() -> dict:

    return metadata


# ============================================================
# PREDICT
# ============================================================

@router.post("/predict")
def predict(
    request: OptionMagnitudePredictionRequestV2,
) -> dict:

    if (
        metadata.get("status")
        != "READY"
        or model is None
    ):

        raise HTTPException(
            status_code=409,
            detail=(
                "OPTION_MAGNITUDE_V2_MODEL_NOT_TRAINED"
            ),
        )

    if (
        request.horizon_minutes
        != metadata.get("horizonMinutes")
    ):

        raise HTTPException(
            status_code=409,
            detail=(
                "MODEL_HORIZON_MISMATCH: train the "
                "option magnitude V2 model for "
                f"{request.horizon_minutes} minutes."
            ),
        )

    values = validate_option_schema(
        request.feature_schema_version,
        request.option_features,
    ).reshape(1, -1)

    predicted_log_return = float(
        model.predict(values)[0]
    )

    predicted_return_percent = (
        np.expm1(
            predicted_log_return
        )
        * 100.0
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

        "predictedOptionReturnPercent":
            round(
                predicted_return_percent,
                6,
            ),

        "predictedLogReturn":
            round(
                predicted_log_return,
                8,
            ),

        "modelVersion":
            metadata["modelVersion"],

        "featureSchemaVersion":
            OPTION_FEATURE_SCHEMA_VERSION,

    }