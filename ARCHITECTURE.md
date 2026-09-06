# AI Trading Platform — Architecture & Design Document

## 1. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     User Interface (React)                      │
│              Dark Professional Trading Dashboard                │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTPS/WebSocket
┌──────────────────────────▼──────────────────────────────────────┐
│              API Gateway & Load Balancer (Nginx)                │
│                   Rate Limiting & Auth                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ REST/GraphQL
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼────────┐  ┌──────▼──────┐  ┌────────▼──────┐
│ Spring Boot    │  │   Python    │  │   WebSocket   │
│  Backend       │  │  ML Service │  │   Handler     │
│ 8080           │  │  8000       │  │  8080         │
└───────┬────────┘  └──────┬──────┘  └────────┬──────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼────────┐  ┌──────▼──────┐  ┌────────▼──────┐
│  PostgreSQL    │  │    Redis    │  │  Market Data  │
│  Database      │  │   Cache     │  │  Provider     │
│  5432          │  │  6379       │  │  (NSE/Mock)   │
└────────────────┘  └─────────────┘  └───────────────┘
```

## 2. Layered Architecture

### Spring Boot Backend

```
Controller Layer
  │
  ├─ AuthController
  ├─ StockController
  ├─ SignalController
  ├─ StrategyController
  ├─ BacktestController
  ├─ PaperTradingController
  ├─ PortfolioController
  └─ AdminController
       │
       ▼
Service Layer (Business Logic)
  │
  ├─ AuthService
  ├─ StockService
  ├─ SignalService (Signal Generation)
  ├─ StrategyService
  ├─ RiskService (Risk Management)
  ├─ BacktestService
  ├─ PaperTradingService
  ├─ PortfolioService
  ├─ TechnicalIndicatorService
  ├─ MarketDataService
  └─ AlertService
       │
       ▼
Repository Layer (Data Access)
  │
  ├─ UserRepository
  ├─ StockRepository
  ├─ MarketDataRepository
  ├─ SignalRepository
  ├─ StrategyRepository
  ├─ BacktestRepository
  ├─ PaperOrderRepository
  ├─ PortfolioRepository
  ├─ TechnicalIndicatorRepository
  └─ AlertRepository
       │
       ▼
PostgreSQL Database
```

## 3. Component Breakdown

### 3.1 Frontend (React)

```
frontend/src/
├── components/
│   ├── common/
│   │   ├── Header
│   │   ├── Sidebar
│   │   ├── Footer
│   │   ├── LoadingSpinner
│   │   └── ErrorBoundary
│   ├── dashboard/
│   │   ├── MarketIndices
│   │   ├── TopGainers
│   │   ├── RecentSignals
│   │   ├── PortfolioSummary
│   │   └── MarketTrend
│   ├── stock/
│   │   ├── StockSearch
│   │   ├── StockDetail
│   │   ├── PriceChart
│   │   ├── IndicatorPanel
│   │   └── SignalPanel
│   ├── scanner/
│   │   ├── ScannerFilters
│   │   └── ScannerResults
│   ├── strategy/
│   │   ├── StrategyList
│   │   ├── StrategyDetail
│   │   └── StrategyBuilder
│   ├── backtest/
│   │   ├── BacktestForm
│   │   ├── BacktestResults
│   │   ├── EquityCurve
│   │   └── TradeList
│   ├── trading/
│   │   ├── PaperTradingDashboard
│   │   ├── OrderForm
│   │   ├── OrderHistory
│   │   ├── PortfolioViewer
│   │   └── RealizedPL
│   ├── alerts/
│   │   ├── AlertManager
│   │   ├── AlertForm
│   │   └── NotificationCenter
│   └── auth/
│       ├── Login
│       ├── Register
│       └── ProfileSettings
│
├── pages/
│   ├── Dashboard
│   ├── StockDetail
│   ├── Scanner
│   ├── Strategies
│   ├── Backtesting
│   ├── PaperTrading
│   ├── Portfolio
│   ├── Alerts
│   ├── AIAnalysis
│   ├── Settings
│   ├── Profile
│   ├── Admin
│   └── NotFound
│
├── hooks/
│   ├── useAuth
│   ├── useMarketData
│   ├── useSignals
│   ├── usePortfolio
│   ├── useTradingWebSocket
│   └── useLocalStorage
│
├── services/
│   ├── apiClient
│   ├── authService
│   ├── stockService
│   ├── signalService
│   ├── strategyService
│   ├── backtestService
│   ├── paperTradingService
│   ├── portfolioService
│   └── alertService
│
├── stores/
│   ├── authStore
│   ├── marketStore
│   ├── portfolioStore
│   ├── uiStore
│   └── settingsStore
│
├── types/
│   ├── auth.ts
│   ├── stock.ts
│   ├── signal.ts
│   ├── strategy.ts
│   ├── backtest.ts
│   ├── portfolio.ts
│   └── api.ts
│
└── utils/
    ├── formatters
    ├── validators
    ├── calculations
    └── constants
```

### 3.2 Backend (Spring Boot)

```
backend/src/main/java/com/tradingplatform/
│
├── controller/
│   ├── auth/
│   │   ├── AuthController
│   │   ├── TokenRefreshController
│   │   └── UserProfileController
│   ├── market/
│   │   ├── StockController
│   │   ├── MarketDataController
│   │   ├── IndexController
│   │   └── ScannerController
│   ├── signal/
│   │   ├── SignalController
│   │   └── AnalysisController
│   ├── strategy/
│   │   ├── StrategyController
│   │   └── StrategyParameterController
│   ├── trading/
│   │   ├── PaperOrderController
│   │   ├── PortfolioController
│   │   ├── PaperPositionController
│   │   └── RealizedPLController
│   ├── backtest/
│   │   ├── BacktestController
│   │   ├── BacktestMetricsController
│   │   └── BacktestTradeController
│   ├── alert/
│   │   ├── AlertController
│   │   └── NotificationController
│   ├── admin/
│   │   ├── UserManagementController
│   │   ├── SystemController
│   │   └── DataManagementController
│   └── health/
│       └── HealthCheckController
│
├── service/
│   ├── auth/
│   │   ├── AuthService
│   │   ├── JwtTokenProvider
│   │   ├── PasswordEncoder
│   │   └── UserDetailsService
│   ├── market/
│   │   ├── StockService
│   │   ├── MarketDataService
│   │   ├── MarketDataProvider (interface)
│   │   ├── MockMarketDataProvider
│   │   ├── NSEApiProvider (future)
│   │   ├── IndexService
│   │   └── ScannerService
│   ├── signal/
│   │   ├── SignalService
│   │   ├── SignalEngine
│   │   ├── AnalysisService
│   │   └── ExplanationService
│   ├── strategy/
│   │   ├── StrategyService
│   │   ├── TradingStrategy (interface)
│   │   ├── EMAStrategy
│   │   ├── RSIStrategy
│   │   ├── MACDStrategy
│   │   ├── VolumeBreakoutStrategy
│   │   ├── VWAPStrategy
│   │   └── StrategyParameterService
│   ├── indicator/
│   │   ├── TechnicalIndicatorService
│   │   ├── SMACalculator
│   │   ├── EMACalculator
│   │   ├── RSICalculator
│   │   ├── MACDCalculator
│   │   ├── VWAPCalculator
│   │   ├── BollingerBandsCalculator
│   │   ├── ATRCalculator
│   │   ├── ADXCalculator
│   │   ├── VolumeMaCalculator
│   │   └── PriceRocCalculator
│   ├── trading/
│   │   ├── PaperTradingService
│   │   ├── PortfolioService
│   │   ├── PaperOrderService
│   │   ├── PaperPositionService
│   │   ├── RiskCalculationService
│   │   ├── PositionSizingService
│   │   └── P&LService
│   ├── backtest/
│   │   ├── BacktestService
│   │   ├── BacktestExecutor
│   │   ├── BacktestHistoricalDataLoader
│   │   ├── BacktestTradeSimulator
│   │   ├── BacktestMetricsCalculator
│   │   ├── LookAheadBiasValidator
│   │   └── DrawdownCalculator
│   ├── risk/
│   │   ├── RiskManager
│   │   ├── RiskValidator
│   │   ├── ExposureCalculator
│   │   ├── CapitalAllocator
│   │   └── RiskLimitChecker
│   ├── alert/
│   │   ├── AlertService
│   │   ├── NotificationService
│   │   ├── EmailNotificationService
│   │   └── WebSocketNotificationService
│   ├── external/
│   │   ├── PyMLService (Python service client)
│   │   ├── LLMService (OpenAI/Claude)
│   │   └── MarketDataCache
│   ├── broker/
│   │   ├── BrokerService (interface)
│   │   ├── PaperBroker
│   │   ├── ZerodhaAdapter (future)
│   │   └── AngelAdapter (future)
│   └── admin/
│       ├── UserManagementService
│       ├── AuditService
│       └── SystemHealthService
│
├── repository/
│   ├── UserRepository
│   ├── RoleRepository
│   ├── StockRepository
│   ├── MarketDataRepository
│   ├── TechnicalIndicatorRepository
│   ├── StrategyRepository
│   ├── StrategyParameterRepository
│   ├── SignalRepository
│   ├── PaperOrderRepository
│   ├── PaperPositionRepository
│   ├── PortfolioRepository
│   ├── PortfolioTransactionRepository
│   ├── BacktestRepository
│   ├── BacktestTradeRepository
│   ├── BacktestMetricsRepository
│   ├── AlertRepository
│   ├── AuditLogRepository
│   └── CacheRepository
│
├── entity/
│   ├── User
│   ├── Role
│   ├── Stock
│   ├── MarketData
│   ├── TechnicalIndicator
│   ├── Strategy
│   ├── StrategyParameter
│   ├── Signal
│   ├── PaperOrder
│   ├── PaperPosition
│   ├── Portfolio
│   ├── PortfolioTransaction
│   ├── Backtest
│   ├── BacktestTrade
│   ├── BacktestMetrics
│   ├── Alert
│   ├── AuditLog
│   └── MLPrediction
│
├── dto/
│   ├── request/
│   │   ├── LoginRequest
│   │   ├── RegisterRequest
│   │   ├── StockSearchRequest
│   │   ├── SignalAnalysisRequest
│   │   ├── StrategyParameterRequest
│   │   ├── BacktestRequest
│   │   ├── PaperOrderRequest
│   │   ├── AlertRequest
│   │   └── StrategyConfigRequest
│   └── response/
│       ├── LoginResponse
│       ├── UserResponse
│       ├── StockResponse
│       ├── SignalResponse
│       ├── StrategyResponse
│       ├── BacktestResultsResponse
│       ├── PortfolioResponse
│       ├── PaperOrderResponse
│       ├── AlertResponse
│       ├── AnalysisResponse
│       ├── DashboardResponse
│       └── ErrorResponse
│
├── mapper/
│   ├── UserMapper
│   ├── StockMapper
│   ├── SignalMapper
│   ├── StrategyMapper
│   ├── BacktestMapper
│   ├── PortfolioMapper
│   ├── PaperOrderMapper
│   ├── AlertMapper
│   └── MarketDataMapper
│
├── security/
│   ├── JwtAuthenticationFilter
│   ├── JwtTokenProvider
│   ├── UserDetailsService
│   ├── SecurityConfig
│   ├── AuthenticationProvider
│   ├── CustomUserDetails
│   └── RoleBasedAccessControl
│
├── config/
│   ├── AppConfig
│   ├── DatabaseConfig
│   ├── RedisConfig
│   ├── SecurityConfig
│   ├── WebSocketConfig
│   ├── RestTemplateConfig
│   ├── SchedulingConfig
│   ├── CachingConfig
│   └── DocumentationConfig
│
├── exception/
│   ├── GlobalExceptionHandler
│   ├── ApiException
│   ├── AuthenticationException
│   ├── ValidationException
│   ├── ResourceNotFoundException
│   ├── InsufficientCapitalException
│   ├── RiskLimitExceededException
│   ├── InvalidOrderException
│   ├── MarketDataUnavailableException
│   ├── BacktestException
│   └── ExternalServiceException
│
├── model/
│   ├── request/
│   ├── response/
│   ├── enums/
│   │   ├── SignalType
│   │   ├── OrderType
│   │   ├── OrderStatus
│   │   ├── StrategyType
│   │   ├── TimeFrame
│   │   ├── TradingMode
│   │   └── RoleEnum
│   └── view/
│       └── Various view models
│
├── util/
│   ├── DateTimeUtil
│   ├── MathUtil
│   ├── PercentageCalculator
│   ├── VolatilityCalculator
│   ├── PerformanceMetricsCalculator
│   ├── JsonUtil
│   ├── LoggingUtil
│   └── ValidationUtil
│
├── event/
│   ├── SignalGeneratedEvent
│   ├── OrderPlacedEvent
│   ├── PositionClosedEvent
│   └── AlertTriggeredEvent
│
├── listener/
│   ├── SignalEventListener
│   ├── OrderEventListener
│   └── AlertEventListener
│
└── TradingPlatformApplication
```

### 3.3 Python ML Service

```
ml-service/app/
│
├── main.py                  # FastAPI entry point
│
├── models/
│   ├── indicator_models.py
│   ├── ml_models.py
│   ├── ml_pipeline.py
│   └── __init__.py
│
├── services/
│   ├── indicator_service.py
│   ├── backtest_service.py
│   ├── ml_service.py
│   ├── preprocessing_service.py
│   └── __init__.py
│
├── routes/
│   ├── indicators.py
│   ├── backtest.py
│   ├── ml.py
│   ├── health.py
│   └── __init__.py
│
├── schemas/
│   ├── request_schemas.py
│   ├── response_schemas.py
│   └── __init__.py
│
├── utils/
│   ├── calculations.py
│   ├── validators.py
│   ├── formatters.py
│   ├── data_loader.py
│   └── __init__.py
│
├── preprocessing/
│   ├── feature_engineering.py
│   ├── data_normalization.py
│   ├── feature_selection.py
│   └── __init__.py
│
├── indicators/
│   ├── sma.py
│   ├── ema.py
│   ├── rsi.py
│   ├── macd.py
│   ├── vwap.py
│   ├── bollinger_bands.py
│   ├── atr.py
│   ├── adx.py
│   ├── volume_ma.py
│   ├── price_roc.py
│   └── __init__.py
│
├── backtest/
│   ├── backtest_engine.py
│   ├── trade_simulator.py
│   ├── metrics_calculator.py
│   ├── look_ahead_bias_check.py
│   └── __init__.py
│
├── ml/
│   ├── feature_engineering.py
│   ├── model_training.py
│   ├── model_evaluation.py
│   ├── walk_forward_validation.py
│   ├── prediction.py
│   └── __init__.py
│
├── config/
│   ├── settings.py
│   ├── logging.py
│   └── constants.py
│
├── cache/
│   ├── cache_manager.py
│   └── redis_cache.py
│
└── tests/
    ├── test_indicators.py
    ├── test_backtest.py
    ├── test_ml.py
    └── __init__.py
```

## 4. Data Flow

### 4.1 Signal Generation Flow

```
Market Data (PostgreSQL/Cache)
         │
         ▼
Technical Indicators (Python Service)
  ├─ SMA/EMA
  ├─ RSI/MACD
  ├─ VWAP/Bollinger Bands
  └─ ATR/ADX
         │
         ▼
Strategy Engine (Spring Boot)
  ├─ EMA Trend
  ├─ RSI + EMA
  ├─ MACD Momentum
  ├─ Volume Breakout
  └─ VWAP Intraday
         │
         ▼
Risk Manager (Spring Boot)
  ├─ Position Sizing
  ├─ Entry/Stop/Target
  ├─ Risk/Reward Calc
  └─ Risk Limit Check
         │
         ▼
Signal Generated
  ├─ Store in DB
  ├─ Cache in Redis
  ├─ Publish to WebSocket
  └─ Trigger Alerts
         │
         ▼
Frontend (React)
  └─ Display Signal
```

### 4.2 Backtesting Flow

```
User Input (Backtest Request)
         │
         ▼
Data Loading (Python)
  ├─ Fetch Historical Data
  ├─ Validate Data Integrity
  └─ Check for Gaps
         │
         ▼
Strategy Simulation (Python)
  ├─ Apply Strategy Rules
  ├─ Generate Entry Signals
  ├─ Calculate Position Sizing
  ├─ Simulate Stop/Target
  └─ Track Trades
         │
         ▼
Metrics Calculation (Python)
  ├─ Total Return
  ├─ Sharpe Ratio
  ├─ Max Drawdown
  ├─ Win Rate
  └─ Other Metrics
         │
         ▼
Store Results (Spring Boot)
  ├─ Save to DB
  ├─ Generate Charts
  └─ Compare vs Buy & Hold
         │
         ▼
Frontend (React)
  └─ Display Results
```

### 4.3 Paper Trading Flow

```
User Places Order (Frontend)
         │
         ▼
Order Validation (Spring Boot)
  ├─ Syntax Validation
  ├─ Risk Check
  ├─ Capital Check
  └─ Position Limit Check
         │
         ▼
Order Execution (Paper Broker)
  ├─ Match Against Market Data
  ├─ Calculate Entry Price
  └─ Update Position
         │
         ▼
Portfolio Update
  ├─ Update Cash
  ├─ Add Position
  ├─ Calculate PnL
  └─ Update Metrics
         │
         ▼
Store Order & Position (Database)
  ├─ Order History
  ├─ Position Tracking
  ├─ Transaction Log
  └─ Audit Log
         │
         ▼
Frontend (React)
  ├─ Update Portfolio
  ├─ Show Order Confirmation
  ├─ Display P&L
  └─ Update Charts
```

## 5. Database Schema

### Core Tables

```sql
-- Users & Authentication
users (id, username, email, password_hash, ...)
roles (id, name, description)
user_roles (user_id, role_id)

-- Stocks & Market Data
stocks (id, symbol, name, exchange, sector, ...)
market_data (id, stock_id, timestamp, open, high, low, close, volume)
daily_prices (id, stock_id, date, open, high, low, close, volume)
intraday_prices (id, stock_id, timestamp, timeframe, open, high, low, close, volume)

-- Technical Indicators
technical_indicators (id, stock_id, timestamp, indicator_type, params, values)
sma_indicators (id, market_data_id, period, value)
ema_indicators (id, market_data_id, period, value)
rsi_indicators (id, market_data_id, period, value)
macd_indicators (id, market_data_id, macd, signal, histogram)
vwap_indicators (id, market_data_id, value)

-- Strategies & Signals
strategies (id, name, description, created_by, ...)
strategy_parameters (id, strategy_id, param_name, param_value)
signals (id, symbol, strategy_id, timestamp, signal_type, confidence, ...)

-- Paper Trading
paper_orders (id, user_id, symbol, order_type, quantity, price, status, ...)
paper_positions (id, user_id, symbol, quantity, avg_entry, current_price, ...)
paper_portfolios (id, user_id, cash, total_value, created_at)
portfolio_transactions (id, portfolio_id, type, amount, timestamp)

-- Backtesting
backtests (id, user_id, symbol, strategy_id, start_date, end_date, ...)
backtest_trades (id, backtest_id, entry_time, exit_time, entry_price, ...)
backtest_metrics (id, backtest_id, metric_name, value)

-- Alerts
alerts (id, user_id, stock_symbol, alert_type, condition, ...)
alert_notifications (id, alert_id, timestamp, status)

-- ML Models
ml_models (id, model_type, version, training_date, ...)
ml_predictions (id, ml_model_id, symbol, timestamp, prediction, confidence)

-- Admin
audit_logs (id, user_id, action, entity_type, entity_id, timestamp, ...)
```

## 6. Authentication & Authorization

### JWT Token Structure
```json
{
  "sub": "user_id",
  "username": "username",
  "email": "email@example.com",
  "roles": ["USER", "ADMIN"],
  "iat": 1234567890,
  "exp": 1234571490,
  "iss": "trading-platform"
}
```

### Roles & Permissions
- **USER** - Can access: stocks, signals, backtest, paper trading, portfolio
- **ADMIN** - Full access + user management, system config, audit logs

## 7. API Contract Example

### REST Endpoints (OpenAPI 3.0)

```yaml
/api/v1/auth:
  POST /register
  POST /login
  POST /refresh
  POST /logout

/api/v1/stocks:
  GET /search
  GET /{symbol}
  GET /{symbol}/prices
  GET /{symbol}/indicators
  GET /{symbol}/signals

/api/v1/signals:
  GET /
  POST /analyze
  GET /{id}

/api/v1/strategies:
  GET /
  GET /{id}
  POST /
  PUT /{id}

/api/v1/backtests:
  POST /
  GET /{id}
  GET /{id}/results
  GET /{id}/trades

/api/v1/paper/orders:
  POST /
  GET /
  GET /{id}
  DELETE /{id}

/api/v1/paper/portfolio:
  GET /
  GET /positions
  GET /transactions

/api/v1/alerts:
  GET /
  POST /
  PUT /{id}
  DELETE /{id}
```

## 8. External Service Integration

### Market Data Providers
- **MockProvider** - Development/testing
- **NSE APIs** - Real market data
- **Angel/Zerodha APIs** - Real market + broker

### ML Service
- Python FastAPI service
- Indicator calculations
- Backtest simulations
- ML predictions

### LLM Service
- OpenAI API / Anthropic Claude
- Signal explanations
- Market summarization
- Q&A responses

## 9. Caching Strategy

### Redis Keys
```
market:stock:{symbol}:price       # Latest price
market:stock:{symbol}:indicators  # Cached indicators
signal:{symbol}:latest            # Latest signal
portfolio:user:{userId}           # Portfolio summary
session:user:{userId}             # User session
rate_limit:user:{userId}          # Rate limiting
```

### Cache Invalidation
- Real-time updates via WebSocket
- TTL-based expiration
- Event-driven invalidation

## 10. Error Handling

### Global Exception Handler
- Centralized error responses
- Proper HTTP status codes
- Detailed error messages (dev mode only)
- Audit logging for errors

### Error Response Format
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "INVALID_INPUT",
  "message": "Position size exceeds risk limit",
  "path": "/api/v1/paper/orders",
  "traceId": "abc123"
}
```

## 11. Logging

### Levels
- **ERROR** - Exceptions, failures
- **WARN** - Deprecated, unusual
- **INFO** - Important events
- **DEBUG** - Detailed flow (dev only)

### Events to Log
- User authentication
- Signal generation
- Paper orders
- Risk limit checks
- Strategy execution
- Backtest runs
- External API calls
- Errors and exceptions

## 12. Performance Considerations

### Database Optimization
- Indexes on: symbol, timestamp, user_id, strategy_id
- Query optimization for time-series data
- Archive old backtest results
- Partition large tables by date

### Caching
- Cache market data (5-10 min TTL)
- Cache indicators (5 min TTL)
- Cache watchlist (1 hour TTL)
- Cache user portfolios (1 min TTL)

### API Performance
- Rate limiting (100 req/min default)
- Response pagination
- Async processing for heavy operations
- WebSocket for real-time updates

## 13. Scalability

### Horizontal Scaling
- Stateless Spring Boot instances
- Load balancer (Nginx/AWS ALB)
- Shared PostgreSQL database
- Shared Redis cluster

### Vertical Scaling
- More CPU/RAM for Spring Boot
- Database connection pooling
- Redis cluster mode

## 14. Deployment Architecture

### Docker Compose (Dev/Test)
```yaml
services:
  frontend
  backend
  python-service
  postgres
  redis
```

### AWS Production
```
CloudFront
  ├─ S3 (frontend static files)
  ├─ ALB
  │   ├─ ECS Spring Boot
  │   ├─ ECS Python Service
  ├─ RDS PostgreSQL
  ├─ ElastiCache Redis
  └─ Secrets Manager
```

## 15. Security Architecture

### Layers
1. **Transport** - HTTPS/TLS
2. **Authentication** - JWT tokens
3. **Authorization** - Role-based access
4. **Data** - Encryption at rest
5. **API** - Input validation, rate limiting
6. **Secrets** - Environment variables, AWS Secrets Manager

### OWASP Top 10 Mitigation
- SQL Injection → Parameterized queries, ORM
- XSS → Input validation, output encoding
- CSRF → CSRF tokens, SameSite cookies
- Broken Auth → JWT, secure session management
- Sensitive Data → Encryption, HTTPS
- XXE → XML parsing hardening
- Broken Access → RBAC, authorization checks
- SSRF → URL validation, allowlists
- Deserialization → Safe serialization
- Logging → Avoid secrets in logs

---

**Document Version:** 1.0  
**Last Updated:** 2024-01-15  
**Status:** Active Development
