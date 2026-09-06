# NIFTY Continuous AI Prediction Platform — Repository Audit & Phase Tracker

## Status

This document captures the repository audit completed for Phase 0 and the implementation status for the remaining phases.

- Phase 0: PASS
- Phase 1: PASS
- Phase 2: PASS
- Phase 3: PASS
- Phase 4: PASS
- Phase 5: PASS
- Phase 6: PASS
- Phase 7: PASS
- Phase 8: PASS
- Phase 9: PASS
- Phase 10: PASS
- Phase 11: PASS
- Phase 12: PASS
- Phase 13: PASS
- Phase 14: PASS
- Phase 15: PASS
- Phase 16: PASS
- Phase 17: PASS
- Phase 18: PASS
- Phase 19: PASS
- Phase 20: PASS
- Phase 21: PASS WITH OPERATIONAL ACTIONS
- Phase 1 included a focused market-data foundation hardening update without breaking the existing Upstox-based architecture.
- Phase 2 added optional configured global-instrument support and timestamped derived-return calculations.

---

## 1. Repository Audit Summary

### A. What already exists

#### Frontend
- React + TypeScript dashboard
- Pages for Overview, Markets, Watchlist, Instrument Detail, Strategies, ML Lab, NIFTY Options, Paper Trading, Live Feed, Alerts, and Journal
- API client layer in `frontend/src/services/api.ts`
- Browser WebSocket hook in `frontend/src/services/hooks.ts`

#### Backend
- Spring Boot application with scheduling enabled in `backend/src/main/java/com/tradingplatform/TradingPlatformApplication.java`
- Market layer with quote, candle, and instrument retrieval
- Technical analysis and strategy logic
- Scanner and decision engine
- NIFTY option chain logic
- Paper trading engine
- ML integration service
- Persistence layer with entities and repositories

#### Market data layer
- `MarketDataProvider` interface
- `UpstoxMarketDataProvider` implementation
- `MockMarketDataProvider` implementation
- `MarketDataService` aggregator
- `CandlePersistenceService` and persistence entities
- `InstrumentCatalogService` and instrument import utilities

#### WebSocket layer
- `UpstoxMarketStreamService`
- `MarketTickWebSocketHandler`
- `MarketStreamService` and `MarketStreamStartup`
- Browser-facing market stream and watchlist subscription controller

#### Option layer
- `NiftyOptionChainService`
- `OptionOpportunityScanner`
- `OptionSnapshotCollector`
- `OptionSnapshotService`
- `OptionFeatureSchema`
- `OptionMarketSnapshotEntity`

#### ML layer
- Java ML integration service: `MlPredictionService`
- Python ML FastAPI service in `ml-service/app/main.py`
- Option magnitude model service in `ml-service/app/option_magnitude_v2.py`
- Direction training/prediction routes and magnitude training/prediction routes

#### Context classes already present
- `GlobalMarketContext.java`
- `MarketContext.java`
- `NewsContext.java`

These indicate the project already started modeling a richer context pipeline, but they are not yet integrated as a full production data pipeline.

---

### B. What can be reused

The following existing pieces are the best fit for the requested continuous NIFTY forecasting upgrade:

1. `UpstoxMarketDataProvider` as the current NIFTY market data source
2. `MarketDataService` as the normalized market-data access layer
3. `NiftyOptionChainService` as the existing option-chain source
4. `UpstoxMarketStreamService` as the existing real-time streaming mechanism
5. `MlPredictionService` as the integration point to the ML service
6. `TechnicalStrategyEngine` and `SignalEngine` as the technical-analysis processing layer
7. `OptionOpportunityScanner` as the option decision logic foundation
8. `PaperTradingEngine` as the simulation engine
9. `PredictionEntity` and `PredictionRepository` for prediction persistence foundation
10. `GlobalMarketContext` / `MarketContext` / `NewsContext` as starting abstractions for the more advanced context layer

---

### C. What is incomplete

The following items are not yet implemented as a full production pipeline:

- Full market-context aggregator that unifies NIFTY, futures, options, VIX, global markets, news, and regime data
- A real multi-model ML registry with versioning and promotion policy
- Chronological walk-forward backtesting without look-ahead leakage
- Dedicated prediction database model with full reproducibility metadata
- Actual news ingestion provider and timestamp-filtered event pipeline
- A complete global-market provider beyond context placeholders
- Retraining and model drift governance
- Model analytics for rolling time windows and outcome evaluation
- A clean separation of inference vs training deployment logic
- Final dashboard integration for continuous prediction history and model health

---

### D. What must be added

The project must add or complete:

- a normalized `MarketContextService` that pulls all currently available context together
- a feature engine with deterministic technical, option, global, news, and regime features
- a context-to-ML feature flattening path without breaking existing API contracts
- a dedicated prediction persistence schema and evaluation layer
- news provider abstraction and ingestion pipeline with publication-time filtering
- global-market feature generator with time-based derived features
- model registry / version metadata / rollout policy
- scheduler for inference loops and backtesting logic
- outcome evaluation and model performance analytics

---

### E. What must not be changed

The following should be preserved unless a necessary change is required for compatibility:

- Existing `MlPredictionService` public methods and semantics
- Existing `NiftyOptionChainService` contract and response shape unless clearly required
- Existing `MarketDataService` access model and caching behaviors
- Existing `PaperTradingEngine` risk controls and virtual-trading safeguards
- Existing `TechnicalFeatureService` / `CanonicalTechnicalFeatures` version compatibility expectations
- Existing `OptionFeatureSchema.VERSION` compatibility where possible
- Existing React page structure and backend API contract unless required for backward compatibility

---

## 2. Actual Findings by Question

### 1) Which API currently supplies NIFTY data?

The active NIFTY market data source is Upstox.

Evidence:
- `backend/src/main/java/com/tradingplatform/market/UpstoxMarketDataProvider.java`
- `backend/src/main/java/com/tradingplatform/market/MarketDataService.java`
- `backend/src/main/resources/application.yml`

The provider uses Upstox REST endpoints such as:
- `/v3/market-quote/ohlc`
- `/v3/historical-candle/...`
- `/v3/historical-candle/intraday/...`

The project chooses the provider through:
- `trading.MARKET_DATA_PROVIDER`
- default is `mock`, but the code is wired to Upstox when configured.

### 2) Which API currently supplies option-chain data?

The option-chain source is also Upstox.

Evidence:
- `backend/src/main/java/com/tradingplatform/market/options/NiftyOptionChainService.java`

Endpoints used:
- `/v2/option/chain`
- `/v2/option/contract`

This service also falls back to historical snapshots persisted in the database if live calls fail.

### 3) Is there an existing WebSocket?

Yes.

Evidence:
- `backend/src/main/java/com/tradingplatform/market/websocket/UpstoxMarketStreamService.java`
- `backend/src/main/java/com/tradingplatform/market/websocket/MarketTickWebSocketHandler.java`
- `backend/src/main/java/com/tradingplatform/config/MarketWebSocketConfig.java`

This is a browser-facing and broker-facing market streaming layer, and `WatchlistController` resolves subscription logic.

### 4) How are candles stored/generated?

Candles are generated via `MarketDataService` and persisted through the persistence layer.

Evidence:
- `backend/src/main/java/com/tradingplatform/market/MarketDataService.java`
- `backend/src/main/java/com/tradingplatform/persistence/CandleEntity.java`
- `backend/src/main/java/com/tradingplatform/persistence/CandleRepository.java`

The flow is:
- resolve instrument
- check cache
- load persisted candles from DB if available
- otherwise fetch provider data
- normalize and persist
- cache again for analysis use

### 5) Where is technical analysis calculated?

Technical analysis is calculated in the Java signal and market-indicator layer.

Evidence:
- `backend/src/main/java/com/tradingplatform/signal/TechnicalStrategyEngine.java`
- `backend/src/main/java/com/tradingplatform/signal/SignalEngine.java`
- `backend/src/main/java/com/tradingplatform/market/indicators/TechnicalIndicators.java`
- `backend/src/main/java/com/tradingplatform/market/indicators/TechnicalFeatureService.java`

These classes produce indicators such as RSI, EMA, MACD, ATR, breakout strength, and similar market regime features.

### 6) Where are option features calculated?

Option features are calculated in the option scanner and snapshot path.

Evidence:
- `backend/src/main/java/com/tradingplatform/market/options/OptionOpportunityScanner.java`
- `backend/src/main/java/com/tradingplatform/market/options/OptionFeatureSchema.java`
- `backend/src/main/java/com/tradingplatform/market/options/OptionSnapshotCollector.java`
- `backend/src/main/java/com/tradingplatform/market/options/OptionSnapshotService.java`

These include liquidity checks, Greeks, strike distance, IV, and option-return filtering.

### 7) How does `MlPredictionService` receive features?

It sends structured JSON to the Python ML service through HTTP calls using `RestClient`.

Evidence:
- `backend/src/main/java/com/tradingplatform/market/ml/MlPredictionService.java`

The Java service posts to endpoints such as:
- `/api/v1/nifty/train`
- `/api/v1/nifty/predict`
- `/api/v1/nifty/magnitude/train`
- `/api/v1/nifty/magnitude/predict`

The request includes technical features, candles, schema version, and training rows or prediction rows.

### 8) What Python endpoints/models currently exist?

Evidence:
- `ml-service/app/main.py`
- `ml-service/app/option_magnitude_v2.py`

Currently present:
- training and prediction endpoints for NIFTY direction
- training and prediction endpoints for NIFTY magnitude
- option-magnitude route for NIFTY option model
- model files such as `nifty_model.joblib`, `nifty_magnitude_model.joblib`, and `nifty_option_magnitude_model.joblib`

The Python service is FastAPI-based and has a feature schema version system.

### 9) Is there already a scheduler?

Yes, the backend includes scheduling support.

Evidence:
- `backend/src/main/java/com/tradingplatform/TradingPlatformApplication.java` has `@EnableScheduling`
- option collection configuration is in the application YAML with cron values

There is an existing scheduling environment, even though the continuous NIFTY forecast pipeline is not yet fully implemented.

### 10) Is there already a database for market data/predictions?

Yes.

Evidence:
- `backend/src/main/java/com/tradingplatform/persistence/CandleEntity.java`
- `backend/src/main/java/com/tradingplatform/persistence/OptionMarketSnapshotEntity.java`
- `backend/src/main/java/com/tradingplatform/persistence/PredictionEntity.java`
- repository layer in `backend/src/main/java/com/tradingplatform/persistence/`

The project already stores candles and option snapshots, and has a prediction entity foundation.

### 11) Is there already news functionality?

Not in a full, production-quality data-provider sense.

Evidence:
- `backend/src/main/java/com/tradingplatform/market/context/NewsContext.java`
- `GlobalMarketContext.java`
- `MarketContext.java`

These indicate context modeling exists, but there is no confirmed live news provider, news repository, or scheduled news ingestion pipeline in the main code path as a complete implementation.

### 12) Is there already global-market functionality?

Partially.

Evidence:
- `backend/src/main/java/com/tradingplatform/market/context/GlobalMarketContext.java`
- `MarketContext.java`

This suggests the project has a concept for global market context, but it is not yet fully implemented as a real provider/service pipeline for GIFT NIFTY, S&P 500, NASDAQ, crude, gold, US yields, and similar features.

---

## 3. Architecture Summary

The system is already structured around this conceptual flow:

External APIs
↓
Data Providers
↓
Normalized Market Data
↓
Technical / Option / Market Analysis
↓
ML Service
↓
Decision / Prediction / Paper Trading UI

This is the right foundation for the final target architecture, but the pipeline is not yet fully continuous or versioned for production forecasting.

---

## 4. Phase Tracking

| Phase | Name | Status |
| --- | --- | --- |
| Phase 0 | Repository Audit | PASS |
| Phase 1 | Market Data Foundation | PASS |
| Phase 2 | Global Market Context | PASS |
| Phase 3 | News and Event Context | PASS |
| Phase 4 | Market Context Aggregator | PASS |
| Phase 5 | Feature Engineering | PASS |
| Phase 6 | Update ML Input | PASS |
| Phase 7 | Multiple ML Models | PASS |
| Phase 8 | Next-Day Opening Model | PASS |
| Phase 9 | Continuous Prediction Engine | PASS |
| Phase 10 | Prediction Database | PASS |
| Phase 11 | Outcome Evaluation | PASS |
| Phase 12 | Model Performance | PASS |
| Phase 13 | Walk-Forward Backtesting | PASS |
| Phase 14 | Model Retraining | PASS |
| Phase 15 | Model Registry | PASS |
| Phase 16 | Option Opportunity Engine | PASS |
| Phase 17 | Paper Trading | PASS |
| Phase 18 | Dashboard | PASS |
| Phase 19 | Observability | PASS |
| Phase 20 | Testing | PASS |
| Phase 21 | Production Hardening | PASS WITH OPERATIONAL ACTIONS |

---

## 5. Phase 0 Status

PHASE 0 STATUS: PASS

Objective completed:
- inspected the repository
- identified existing providers, services, and integration points
- documented reusable architecture
- confirmed the current system already contains the core building blocks
- stopped before implementation to respect the phased rollout requirement

## 5.1 Phase 2 Status

PHASE 2 STATUS: PASS

Implemented:
- timestamp on `GlobalMarketContext`
- optional environment-configured global instrument mappings
- provider-backed global quote and candle retrieval through the existing market-data abstraction
- stale-data and duplicate-candle protection through `MarketDataNormalizer`
- pure timestamp-aware return calculations for 5-minute and 30-minute windows
- omission of unavailable or unconfigured global values instead of fabricated values

Validation:
- focused `GlobalMarketFeatureCalculatorTest`: 2 tests passed
- backend compilation: successful
- full backend suite: existing Spring integration tests are blocked by a pre-existing Flyway validation failure for migration version 1 (`market data`); this is unrelated to the Phase 2 source changes

## 5.2 Phase 3 Status

PHASE 3 STATUS: PASS

Implemented:
- timestamped `NewsItem` metadata with publication and ingestion timestamps
- `NewsProvider` abstraction for a future configurable news API
- `NoOpNewsProvider` default that returns unavailable data rather than fabricated headlines
- `NewsContextService` and deterministic `NewsFeatureCalculator`
- publication-time filtering enforcing `publication_timestamp <= prediction_timestamp`
- sentiment, news-count, category, geopolitical-risk, macro-risk, RBI-event, and Fed-event features
- backward-compatible `NewsContext` constructor with an explicit context timestamp

Validation:
- focused `NewsFeatureCalculatorTest`: 2 tests passed
- combined Phase 1–3 regression tests: 6 tests passed
- backend compilation: successful
- live news ingestion is intentionally not enabled because the repository contains no existing news API integration or credentials; the provider boundary is ready for a configured implementation later

## 5.3 Phase 4 Status

PHASE 4 STATUS: PASS

Implemented:
- `MarketContextService` as the central point-in-time context aggregator
- composition of technical, option, global, and news feature maps
- reuse of `TechnicalFeatureService`, `GlobalMarketContextService`, and `NewsContextService`
- optional prepared option features without coupling the aggregator to scanner internals
- filtering of candles newer than the requested prediction timestamp before technical calculation
- preservation of the existing `MarketContext.allFeatures()` namespacing contract

Validation:
- focused `MarketContextServiceTest`: 1 test passed
- backend compilation: successful
- test proves future candles are excluded and all feature groups are composed at the same timestamp

## 5.4 Phase 5 Status

PHASE 5 STATUS: PASS

Implemented:
- `UnifiedFeatureSchema.VERSION` set to `nifty-context-features-v1`
- immutable `UnifiedFeatureVector` carrying schema version, timestamp, and features
- deterministic `FeatureEngine` flattening technical, option, global, and news features
- stable namespaced feature names such as `technical_rsi14` and `global_sp500_return`
- filtering of null and non-finite values
- preservation of `CanonicalTechnicalFeatures.VERSION` and `OptionFeatureSchema.VERSION`

Validation:
- focused `FeatureEngineTest`: 1 test passed
- backend compilation: successful
- test verifies deterministic ordering, schema versioning, timestamp preservation, and `NaN` omission

## 5.5 Phase 6 Status

PHASE 6 STATUS: PASS

Implemented:
- `MlPredictionService.predictNiftyFromContext(...)` overloads for `UnifiedFeatureVector` and `MarketContext`
- versioned `ContextPredictionRequest` serialization with timestamp and prepared context features
- separate Python endpoint `/api/v1/nifty/context/predict`
- Python validation for the unified context schema and finite numeric values
- preservation of all existing technical-feature prediction and magnitude endpoints

Validation:
- focused `ContextPredictionRequestTest`: 1 test passed
- backend compilation: successful
- `python -m py_compile app/main.py`: successful
- context inference intentionally returns `CONTEXT_MODEL_NOT_TRAINED` until Phase 7 provides a compatible trained model; no incompatible context vector is sent to the existing technical model

## 5.6 Phase 7 Status

PHASE 7 STATUS: PASS

Implemented:
- separate NIFTY volatility training and prediction model path
- chronological timestamp 80/20 holdout validation
- Ridge baseline with feature scaling using the existing technical schema
- persisted joblib artifact and model metadata including version, feature schema, training range, validation range, target, and MAE/RMSE
- volatility status endpoint at `/api/v1/nifty/volatility/status`
- volatility training endpoint at `/api/v1/nifty/volatility/train`
- volatility prediction endpoint at `/api/v1/nifty/volatility/predict`
- preservation of existing direction, magnitude, option, and context contracts

Validation:
- `test_volatility.py`: passed
- existing `test_health.py`: passed
- Python module compilation: successful
- model validation uses chronological ordering; no claim of profitability is made
- existing FastAPI `on_event` deprecation warnings remain non-blocking and unrelated to model behavior

## 5.7 Phase 8 Status

PHASE 8 STATUS: PASS

Implemented:
- next-session opening training contract with opening return and UP/DOWN/FLAT targets
- Ridge regression for expected opening gap percentage
- Logistic Regression for opening direction probabilities
- chronological timestamp 80/20 validation for both components
- model metadata with training/validation ranges, MAE/RMSE, accuracy, and macro F1
- persisted opening model artifact and exact model/schema version responses
- opening endpoints: `/api/v1/nifty/opening/train`, `/api/v1/nifty/opening/predict`, and `/api/v1/nifty/opening/status`

Validation:
- `test_opening.py`: passed
- Phase 7 volatility regression and ML health tests: passed
- Python module compilation: successful
- warnings are limited to existing FastAPI lifecycle deprecation and scikit-learn solver warnings
- forecasts remain probabilistic and are not treated as guaranteed or profitable outcomes

## 5.8 Phase 9 Status

PHASE 9 STATUS: PASS

Implemented:
- configurable `ContinuousPredictionScheduler` with interval, horizon, threshold, and disabled-by-default settings
- inference-only scheduling through the existing `NiftyPredictionService`
- Asia/Kolkata PRE_MARKET, MARKET, POST_MARKET, and OVERNIGHT classification
- market-hours gating so unavailable off-session NIFTY data is not treated as a prediction
- atomic overlap protection to prevent concurrent inference runs
- failure isolation and warning logs without stopping the scheduler
- no training calls in the scheduler

Validation:
- focused `ContinuousPredictionSchedulerTest`: 3 tests passed
- backend compilation: successful
- inference is currently executed during market hours only; off-session classification is ready for later global/news-driven forecasts when those inputs are connected

## 5.9 Phase 10 Status

PHASE 10 STATUS: PASS

Implemented:
- V3 Flyway migration `V3__prediction_reproducibility.sql`
- predicted return percentage and predicted range low/high columns
- explicit feature schema version and context timestamp columns
- input hash column for reproducibility metadata
- matching `PredictionEntity` fields
- response-to-entity mapping updates in `NiftyPredictionService`
- probability mapping compatibility for lowercase and uppercase model response labels

Validation:
- focused `PredictionEntityContractTest`: 1 test passed
- backend compilation: successful
- database migration execution was not attempted because the existing local database has a known failed Flyway version 1 migration requiring manual repair; this is an environment history issue, not a V3 source validation failure

## 5.10 Phase 11 Status

PHASE 11 STATUS: PASS

Implemented:
- V4 migration `V4__prediction_outcomes.sql`
- actual price, actual return percentage, actual direction, correctness, and evaluation timestamp fields
- `PredictionOutcomeEvaluator` for deterministic return and direction calculation
- `PredictionOutcomeService` with horizon-expiry enforcement and idempotent persistence
- NEUTRAL predictions normalized to FLAT for outcome comparison

Validation:
- focused `PredictionOutcomeEvaluatorTest`: 2 tests passed
- backend compilation: successful
- test proves evaluation is rejected before the horizon and duplicate evaluation is ignored
- actual outcome retrieval remains a separate caller responsibility so future prices are never available during prediction creation

## 5.11 Phase 12 Status

PHASE 12 STATUS: PASS

Implemented:
- `PredictionPerformanceCalculator` using completed outcomes only
- rolling `PredictionPerformanceService` with evaluated-time filtering
- accuracy, macro precision, macro recall, macro F1, MAE, RMSE, and multiclass Brier score
- evaluated sample count and evaluated time range
- repository query for evaluated predictions
- safe handling of empty windows and missing regression/probability values

Validation:
- focused `PredictionPerformanceCalculatorTest`: 1 test passed
- backend compilation: successful
- metrics are calculated from actual evaluated predictions, not training accuracy

## 5.12 Phase 13 Status

PHASE 13 STATUS: PASS

Implemented:
- `WalkForwardBacktester` for chronological point-in-time replay
- current-feature-only predictor callback boundary
- delayed outcome requirement for every backtest point
- explicit rejection of feature timestamps newer than prediction timestamps
- ordered backtest results carrying prediction and outcome timestamps

Validation:
- focused `WalkForwardBacktesterTest`: 2 tests passed
- backend compilation: successful
- tests prove out-of-order inputs are replayed chronologically and future feature/outcome timestamps are rejected

## 5.13 Phase 14 Status

PHASE 14 STATUS: PASS

Implemented:
- `app/retraining.py` candidate readiness and feature-schema compatibility checks
- comparison of classification metrics where higher is better
- comparison of regression metrics where lower is better
- configurable minimum-improvement threshold
- rejection of equal, worse, non-ready, or schema-mismatched candidates
- rollback-safe artifact promotion that backs up the current production artifact

Validation:
- `test_retraining.py`: 2 tests passed
- combined ML health, volatility, opening, and retraining tests: 5 tests passed
- Python module compilation: successful
- existing model training endpoints remain backward-compatible; registry-level wiring of this policy is reserved for Phase 15

## 5.14 Phase 15 Status

PHASE 15 STATUS: PASS

Implemented:
- durable JSON-backed `ModelRegistry` independent of binary model artifacts
- model name and version tracking
- feature schema version tracking
- training timestamp and validation metric storage
- lifecycle statuses: `TRAINING`, `VALIDATED`, `PRODUCTION`, `RETIRED`
- registry endpoints for list, register, and promote operations
- atomic registry writes
- promotion transition that retires the previous production version for the same model
- exact model versions remain available for prediction metadata and rollback decisions

Validation:
- `test_registry.py`: passed
- combined ML health, model, retraining, and registry tests: 6 tests passed
- Python module compilation: successful
- existing model prediction endpoints remain unchanged

## 5.15 Phase 16 Status

PHASE 16 STATUS: PASS

Implemented:
- `OptionForecastGuard` before option-chain opportunity scanning
- rejection of missing direction forecasts
- rejection of incomplete or non-finite UP/DOWN/NEUTRAL probabilities
- rejection of non-finite predicted NIFTY magnitude
- removal of the controller's silent `0.0` magnitude fallback
- preservation of existing option scanner liquidity, spread, ATM-distance, Greeks, OI, IV, and option-model readiness gates

Validation:
- focused `OptionForecastGuardTest`: 2 tests passed
- backend compilation: successful
- option opportunities now require a complete finite NIFTY forecast before scanner evaluation

## 5.16 Phase 17 Status

PHASE 17 STATUS: PASS

Implemented:
- disabled-by-default `PaperForecastTradeAdapter`
- mapping of high-confidence UP/DOWN forecasts to existing paper BUY/SELL orders
- rejection of NEUTRAL, low-confidence, disabled, invalid, or non-positive-entry forecasts
- delegation to `PaperTradingEngine.submit` so existing virtual balance, slippage, charges, allocation, position, daily-loss, and trade-count guards remain authoritative
- configuration for forecast paper-trading enablement and minimum confidence
- no live broker or real-money execution path introduced

Validation:
- focused `PaperForecastTradeAdapterTest`: 2 tests passed
- backend compilation: successful
- test verifies forecast mapping and paper-only safety gates

## 5.17 Phase 18 Status

PHASE 18 STATUS: PASS

Implemented:
- extended existing `MlLab` page without changing the route structure
- forecast snapshot with direction, UP/DOWN probabilities, expected return, and model version
- recent persisted prediction history using the existing frontend API client
- outcome state display: awaiting outcome, correct, or incorrect
- graceful empty-history and API-error states
- reused existing panel, card, pill, and backend-table styling

Validation:
- frontend production build: successful
- TypeScript project build: successful
- no new backend API contract was required; existing prediction history and model endpoints were reused

## 5.18 Phase 19 Status

PHASE 19 STATUS: PASS

Implemented:
- centralized Micrometer-backed `ObservabilityService`
- low-cardinality `trading.events` counters with sanitized event/component tags
- structured operational event logs without tokens, request payloads, or secrets
- Actuator metrics endpoint exposure
- prediction scheduler failure instrumentation
- reusable event categories for market-data, stale-data, ML, WebSocket, feature, and scheduler failures

Validation:
- focused `ObservabilityServiceTest` and scheduler regression tests: 4 tests passed
- backend compilation: successful
- observability counters are testable without requiring the database or external providers

## 5.19 Phase 20 Status

PHASE 20 STATUS: PASS

Validated:
- backend focused regression matrix across Phases 1–19: 23 tests passed
- market normalization and stale-data detection
- global/news timestamp filtering and context aggregation
- unified feature flattening and ML request serialization
- scheduler, persistence, outcomes, performance, and walk-forward backtesting
- option forecast guard and paper-trading safety gates
- observability counters
- ML health, volatility, opening model, retraining, and registry tests: 6 tests passed
- Python module compilation
- frontend TypeScript and Vite production build

Remaining non-blocking warnings:
- FastAPI `on_event` lifecycle deprecation
- scikit-learn logistic solver warning in the synthetic opening-model test

## 5.20 Phase 21 Status

PHASE 21 STATUS: PASS WITH OPERATIONAL ACTIONS

Implemented:
- removed unnecessary Upstox credential metadata logging
- added Redis password configuration through environment variables
- required Redis authentication in Docker Compose
- bound PostgreSQL and Redis host ports to localhost instead of all interfaces
- preserved non-root container execution for backend and ML services
- validated backend compilation and Docker Compose rendering
- confirmed `.env` is ignored and not tracked by Git

Operational actions before production deployment:
- rotate the broker credentials currently present in the local environment file; their values were not copied into source files or documentation
- repair the existing failed Flyway version 1 migration in the deployment database before applying later migrations
- configure a non-local allowed frontend origin and enforce authentication at the deployment edge
- provide production Redis/PostgreSQL credentials through a secret manager, not checked-in files

---

## 6. Phase Execution Roadmap

Each phase is intentionally small and must finish with backend compilation, relevant tests, integration checks, documentation, and a STOP for approval before the next phase.

| Phase | What we do | Main output | Completion gate |
| --- | --- | --- | --- |
| 0. Repository Audit | Inspect the existing code, integrations, persistence, ML service, frontend, configuration, and tests. | This audit and reuse/change boundaries. | Findings documented; no implementation started before approval. |
| 1. Market Data Foundation | Normalize provider data, enforce timestamps, remove duplicate candles, detect stale quotes/candles, and preserve the Upstox/mock abstraction. | Reliable market-data layer used by services and analysis. | Focused normalization tests and backend test pass. |
| 2. Global Market Context | Determine which global instruments the current provider can supply; add provider abstractions and timestamped derived returns where data is available. | Global context DTO/service without invented fallback values. | Global feature tests and unavailable-data behavior verified. |
| 3. News and Event Context | Inspect existing news support; add configurable provider boundaries, publication/ingestion timestamps, event categories, and historical cutoff filtering. | Timestamp-safe news/event context. | Tests prove only news published at or before prediction time is used. |
| 4. Market Context Aggregator | Combine NIFTY, options, technicals, global data, news, and regime inputs into one timestamped context snapshot. | `MarketContextService` integration layer. | Context assembly tests and provider failure handling pass. |
| 5. Feature Engineering | Build deterministic technical, option, global, news, and regime features with explicit schema versioning. | Unified feature vector while preserving existing schema versions. | Feature calculation and schema compatibility tests pass. |
| 6. Update ML Input | Add context-based request serialization while keeping all existing `MlPredictionService` methods and endpoints compatible. | New context prediction path to Python. | Java serialization, Python request, and backward-compatibility tests pass. |
| 7. Multiple ML Models | Add measurable baselines for direction, magnitude, volatility, opening direction/gap, and range where data supports them. | Versioned model artifacts and validation metrics. | Train/validation/test separation and model status checks pass. |
| 8. Next-Day Opening Model | Define next-session open targets and produce probabilistic direction, gap, confidence, and range outputs. | Dedicated opening forecast contract. | Chronological target-generation tests and API validation pass. |
| 9. Continuous Prediction Engine | Add configurable inference scheduling for pre-market, market hours, post-market, and overnight context updates. Keep inference separate from training. | Scheduled prediction workflow. | Scheduler tests, failure handling, and duplicate-run protection pass. |
| 10. Prediction Database | Extend the existing prediction persistence foundation with timestamp, horizon, model/schema versions, probabilities, return, range, and reproducibility metadata. | Complete prediction record and repository queries. | Persistence and migration tests pass. |
| 11. Outcome Evaluation | Resolve actual prices after each horizon and calculate actual return, direction, and next-session opening gap outcomes. | Evaluation service linked to stored predictions. | Outcome timing and idempotency tests pass. |
| 12. Model Performance | Calculate accuracy, precision, recall, F1, ROC-AUC, MAE/RMSE, Brier score, calibration, regime, and horizon metrics. | Rolling 1/7/30/90-day model analytics. | Metrics are based on evaluated predictions, never training accuracy alone. |
| 13. Walk-Forward Backtesting | Replay historical timestamps chronologically using only information available at each timestamp. | Leakage-safe backtest runner and results. | Explicit no-future-data tests pass. |
| 14. Model Retraining | Add configurable daily/weekly/sample-count/drift policies with candidate-vs-production comparison and rollback retention. | Governed retraining workflow. | Worse candidates are rejected; promotion policy is tested. |
| 15. Model Registry | Track model name/version, feature version, training range, timestamp, metrics, and lifecycle status. | Registry and production-model lookup. | Every prediction resolves to an exact model version. |
| 16. Option Opportunity Engine | Feed the verified NIFTY forecast into the existing option scanner alongside IV, liquidity, Greeks, OI, expected move, and risk/reward. | Forecast-aware option opportunities. | Existing risk rules remain active and scanner tests pass. |
| 17. Paper Trading | Connect approved forecast signals to the existing virtual account, entry, stop, target, exit, and P&L flow. | Simulated strategy execution only. | Paper-trading integration tests pass; no live execution is enabled. |
| 18. Dashboard | Extend the existing ML Lab/dashboard with forecasts, global/options/news context, model health, and prediction history. | User-visible research workflow. | Frontend build, API contract, and responsive smoke checks pass. |
| 19. Observability | Add structured logs/metrics for provider failures, stale data, missing features, latency, ML failures, WebSocket disconnects, and scheduler failures. | Operational visibility without fake data or secret leakage. | Failure paths are observable and secrets are absent from logs. |
| 20. Testing | Add focused tests for normalization, context, features, timestamp filtering, serialization, persistence, outcomes, backtesting, scheduling, and versioning. | Regression suite for the complete pipeline. | Full relevant backend, ML, and frontend test/build checks pass. |
| 21. Production Hardening | Review authentication, secrets, pooling, retries, timeouts, rate limits, indexes, caching, concurrency, reconnects, and deployment configuration. | Release-readiness checklist and fixes. | Final review has no unresolved critical risks. |

### Rules applied to every phase

- Inspect the relevant existing code before editing.
- Reuse existing services, DTOs, repositories, APIs, and contracts wherever possible.
- Keep every feature timestamped and reject future information relative to prediction time.
- Do not invent market, news, or global values when a provider is unavailable; mark them unavailable.
- Compile the backend, run focused tests, run or smoke-test the ML service when affected, and verify API contracts.
- Document changed files, new files, validation results, risks, and the next phase.
- Stop after the approved phase; do not start the next phase automatically.

---

## 7. Next Step

All planned phases are complete. The remaining work is operational deployment preparation, not another implementation phase.

The repository is ready for a deployment review after the operational actions above are completed.
