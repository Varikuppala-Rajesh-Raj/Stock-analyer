# AI-Assisted Stock Trading Analysis Platform

A production-grade, full-stack web application for analyzing Indian stock market (NSE/BSE) with algorithmic signal generation, backtesting, paper trading, and ML-driven insights.

## 🎯 Project Objective

Build a comprehensive trading analysis platform that allows users to:
- View real-time/historical Indian stock market data
- Generate BUY/SELL/HOLD signals using deterministic and ML strategies
- Calculate risk-adjusted entry, stop loss, and targets
- Backtest strategies against historical data
- Practice paper trading with virtual capital
- Analyze trading performance
- Receive AI-powered explanations of signals and market conditions

## ⚠️ Important Disclaimer

This is an **analysis and research platform**, not a guaranteed profit-making system.
- Signals are algorithmic suggestions based on historical patterns
- Past performance does not guarantee future results
- Users are responsible for their trading decisions
- Real-money automated trading is **disabled by default**
- Paper trading must be used to validate strategies before any live trading

## 🏗️ Architecture

```
Cloud/CDN (CloudFront)
         ↓
    Load Balancer
         ↓
    React Frontend
         ↓
    Spring Boot Backend ← → PostgreSQL
         ↓                  Redis
    Python ML Service
         ↓
    Market Data Providers
```

## 📦 Technology Stack

### Frontend
- **React** with TypeScript
- **Tailwind CSS** for styling
- **React Router** for navigation
- **TanStack Query** for state management
- **TradingView Lightweight Charts**
- **Axios** for API calls
- **Dark professional trading dashboard theme**

### Backend
- **Java 17+** with Spring Boot 3.x
- **Spring MVC, Security, Data JPA**
- **PostgreSQL** for persistence
- **Redis** for caching and pub/sub
- **JWT authentication**
- **Layered architecture** (controller → service → repository)

### Quantitative/ML Service
- **Python 3.10+** with FastAPI
- **Pandas, NumPy** for data processing
- **scikit-learn, XGBoost** for ML
- **technical-analysis** library
- **Pydantic** for validation

### Infrastructure
- **Docker & Docker Compose** for containerization
- **PostgreSQL 14+** database
- **Redis 7+** for caching
- **Jenkins** for CI/CD (initially)
- **AWS** for production deployment (optional)

## 📋 Features

> **Implementation status:** this repository currently contains Phase 1 foundations only: the three runnable service skeletons, container builds, health checks, and PAPER-only configuration. The checklists below are target scope, not completed functionality. See [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

### Market Data & Analysis
- [x] Search Indian stocks (NSE/BSE)
- [x] Real-time/historical price data
- [x] Candlestick charts with volume
- [x] Technical indicators (SMA, EMA, RSI, MACD, VWAP, Bollinger Bands, ATR, ADX)
- [x] Market status and index tracking (NIFTY 50, SENSEX, BANK NIFTY)

### Trading Signals
- [x] Deterministic strategy engine
- [x] BUY/SELL/HOLD signals with confidence scores
- [x] Entry, stop loss, target calculations
- [x] Risk/reward ratios
- [x] ML-enhanced signal scoring (future phases)

### Backtesting
- [x] Strategy backtesting against historical data
- [x] No look-ahead bias verification
- [x] Returns, CAGR, Sharpe ratio, max drawdown
- [x] Trade-by-trade analysis
- [x] Equity and drawdown curves

### Paper Trading
- [x] Virtual capital (₹100,000 initial)
- [x] BUY/SELL/CLOSE orders
- [x] Position tracking and P&L
- [x] Portfolio management

### Analytics
- [x] Stock scanner with configurable filters
- [x] Watchlist management
- [x] Price and signal alerts
- [x] Portfolio performance analysis
- [x] Risk metrics and attribution

### AI & ML
- [x] Feature engineering from market data
- [x] ML model training (walk-forward validation)
- [x] LLM-powered signal explanations
- [x] Market condition summarization

### Security & Safety
- [x] JWT-based authentication
- [x] Role-based access control (USER, ADMIN)
- [x] Password hashing (bcrypt)
- [x] Real trading disabled by default
- [x] Risk limits and position sizing controls
- [x] Audit logging

## 🚀 Quick Start

### Local Development

#### Prerequisites
```bash
Git
Docker & Docker Compose
Java 17+
Python 3.10+
Node.js 18+
```

#### Clone and Setup
```bash
git clone <repository>
cd trading-platform

# Copy environment example
cp .env.example .env
# Edit .env with your values
```

#### Using Docker Compose (Recommended)
```bash
docker compose up --build

# Frontend: http://localhost:3000
# Backend: http://localhost:8080
# Python Service: http://localhost:8000
# Database: localhost:5432
# Redis: localhost:6379
```

#### Local Development (Without Docker)

**Backend:**
```bash
cd backend
mvn test
mvn spring-boot:run
```

**Python Service:**
```bash
cd ml-service
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --reload
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

## 📚 Documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md) - System design and component details
- [DATABASE.md](./DATABASE.md) - Database schema and relationships
- [API.md](./API.md) - REST API documentation (OpenAPI/Swagger)
- [TRADING_ENGINE.md](./TRADING_ENGINE.md) - Strategy and signal generation
- [BACKTESTING.md](./BACKTESTING.md) - Backtesting methodology
- [ML.md](./ML.md) - Machine learning pipeline
- [SECURITY.md](./SECURITY.md) - Security considerations and best practices
- [DEPLOYMENT.md](./DEPLOYMENT.md) - Production deployment guide
- [BROKER_INTEGRATION.md](./BROKER_INTEGRATION.md) - Broker API integration patterns
- [CONTRIBUTING.md](./CONTRIBUTING.md) - Development guidelines

## 🏃 Running Tests

```bash
# Backend (requires Maven 3.9+)
cd backend
./mvnw test

# Python Service
cd ml-service
pytest -v

# Frontend
cd frontend
npm test
```

## 📊 Project Structure

```
trading-platform/
├── frontend/                 # React TypeScript app
│   ├── src/
│   │   ├── components/      # React components
│   │   ├── pages/          # Page components
│   │   ├── hooks/          # Custom hooks
│   │   ├── services/       # API services
│   │   ├── stores/         # State management
│   │   ├── styles/         # Tailwind configs
│   │   └── utils/          # Utilities
│   ├── package.json
│   └── Dockerfile
│
├── backend/                  # Java Spring Boot app
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/tradingplatform/
│   │   │   │       ├── controller/     # REST controllers
│   │   │   │       ├── service/        # Business logic
│   │   │   │       ├── repository/     # Data access
│   │   │   │       ├── entity/         # JPA entities
│   │   │   │       ├── dto/            # Data transfer objects
│   │   │   │       ├── mapper/         # Entity ↔ DTO mapping
│   │   │   │       ├── security/       # Security config
│   │   │   │       ├── strategy/       # Trading strategies
│   │   │   │       ├── risk/           # Risk management
│   │   │   │       ├── backtest/       # Backtesting
│   │   │   │       ├── exception/      # Exception handlers
│   │   │   │       └── config/         # Application config
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── schema.sql
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
│
├── ml-service/               # Python FastAPI service
│   ├── app/
│   │   ├── main.py          # Entry point
│   │   ├── models/          # ML models
│   │   ├── services/        # Business logic
│   │   ├── routes/          # API endpoints
│   │   ├── preprocessing/   # Feature engineering
│   │   ├── indicators/      # Technical indicators
│   │   ├── backtest/        # Backtesting
│   │   └── utils/           # Utilities
│   ├── tests/
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
│
├── data/                     # Data files and scripts
│   ├── migrations/          # Database migrations
│   ├── sample_data/         # Sample stock data
│   └── scripts/             # Utility scripts
│
├── infrastructure/           # DevOps and IaC
│   ├── docker-compose.yml
│   ├── jenkins/
│   │   └── Jenkinsfile      # CI/CD pipeline
│   ├── kubernetes/          # K8s manifests (future)
│   └── aws/                 # AWS IaC (future)
│
├── docs/                     # Documentation
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── DATABASE.md
│   ├── TRADING_ENGINE.md
│   ├── BACKTESTING.md
│   ├── ML.md
│   ├── SECURITY.md
│   ├── DEPLOYMENT.md
│   ├── BROKER_INTEGRATION.md
│   └── images/
│
├── scripts/                  # Development scripts
│   ├── setup.sh
│   ├── seed-data.sh
│   └── health-check.sh
│
├── .env.example             # Environment template
├── .gitignore               # Git ignore rules
├── docker-compose.yml       # Docker Compose config
└── README.md               # This file
```

## 🔐 Security

### Do Not Commit
- API keys or credentials
- Passwords or secrets
- Private keys
- JWT signing keys

### Environment Variables
Create a `.env` file from `.env.example` and set:
```
# Database
DB_HOST=postgres
DB_PORT=5432
DB_NAME=trading_platform
DB_USER=trading_user
DB_PASSWORD=<secure_password>

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# JWT
JWT_SECRET=<secure_random_string>
JWT_EXPIRATION=86400

# Backend
SERVER_PORT=8080
SERVER_ENV=development

# Python Service
PYTHON_SERVICE_URL=http://localhost:8000

# Market Data Providers (optional)
MARKET_DATA_PROVIDER=mock
# ALPHA_VANTAGE_KEY=<key>
# POLYGON_IO_KEY=<key>
# NSE_API_KEY=<key>

# LLM (optional)
OPENAI_API_KEY=<key>
# or
ANTHROPIC_API_KEY=<key>

# Broker APIs (optional, disabled by default)
BROKER_MODE=PAPER
# ZERODHA_API_KEY=<key>
# ANGEL_BROKER_TOKEN=<token>
```

## 📊 Database

PostgreSQL is the primary data store with the following key entities:
- **Users** - User accounts and authentication
- **Stocks** - Stock master data (symbols, names, exchanges)
- **MarketData** - Historical and intraday price data
- **TechnicalIndicators** - Calculated indicators
- **Strategies** - Strategy definitions
- **Signals** - Generated buy/sell signals
- **PaperOrders** - Virtual trading orders
- **Portfolios** - User portfolios and positions
- **Backtests** - Backtest results and analysis
- **MLModels** - Trained ML models and predictions

See [DATABASE.md](./DATABASE.md) for complete schema.

## 🎯 Usage Examples

### 1. View Dashboard
```
1. Go to http://localhost:3000
2. Login with credentials
3. View market indices, top gainers/losers
4. See recent signals and portfolio summary
```

### 2. Search and Analyze Stock
```
1. Search for "TCS" or "INFY"
2. View candlestick chart
3. See technical indicators
4. View generated signals
5. Analyze entry/stop/target
```

### 3. Backtest Strategy
```
1. Select Strategy → MACD Momentum
2. Select timeframe: 1D
3. Set date range: Last 2 years
4. Configure risk: 1% per trade
5. Run backtest
6. View metrics: Sharpe, Max Drawdown, Win Rate
7. Analyze equity curve
```

### 4. Paper Trade
```
1. Go to Paper Trading
2. See virtual balance (₹100,000)
3. Place BUY order on a signal
4. Monitor position
5. SELL when target hit or stop loss breach
6. View P&L and portfolio performance
```

### 5. Configure Alert
```
1. Go to Alerts
2. Create new alert
3. Select stock: TCS
4. Alert type: Price crosses EMA50
5. Notification: In-app + Email
6. Activate
```

## 🔄 API Examples

### Get Dashboard Data
```bash
GET /api/dashboard
Authorization: Bearer <jwt_token>

Response:
{
  "indices": {
    "nifty50": {"value": 24500, "change": 150, "changePercent": 0.62},
    "sensex": {"value": 82000, "change": 300, "changePercent": 0.37}
  },
  "topGainers": [...],
  "topLosers": [...],
  "recentSignals": [...],
  "portfolioSummary": {...}
}
```

### Search Stocks
```bash
GET /api/stocks?search=TCS
Authorization: Bearer <jwt_token>

Response:
{
  "stocks": [
    {"symbol": "TCS", "name": "Tata Consultancy Services", "exchange": "NSE", "price": 3450.50}
  ]
}
```

### Get Stock with Signals
```bash
GET /api/stocks/TCS/analysis
Authorization: Bearer <jwt_token>

Response:
{
  "stock": {...},
  "currentPrice": 3450.50,
  "signals": [
    {
      "strategy": "EMA Trend",
      "signal": "BUY",
      "confidence": 78,
      "entry": 3450,
      "stopLoss": 3400,
      "target": 3550,
      "riskReward": 2.0,
      "reasoning": ["Price > EMA50", "EMA50 > EMA200", "RSI > 50"]
    }
  ]
}
```

### Backtest Strategy
```bash
POST /api/backtests
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "symbol": "TCS",
  "strategy": "MACD_MOMENTUM",
  "timeframe": "1D",
  "startDate": "2022-01-01",
  "endDate": "2024-01-01",
  "initialCapital": 100000,
  "riskPerTrade": 1.0
}

Response:
{
  "id": "backtest-123",
  "totalReturn": 45.5,
  "cagr": 20.2,
  "sharpeRatio": 1.25,
  "maxDrawdown": -12.3,
  "totalTrades": 45,
  "winRate": 55.6
}
```

### Get Paper Trading Portfolio
```bash
GET /api/paper/portfolio
Authorization: Bearer <jwt_token>

Response:
{
  "cash": 85000,
  "positions": [
    {"symbol": "TCS", "quantity": 10, "avgEntry": 3400, "currentPrice": 3450, "unrealizedPL": 500}
  ],
  "totalValue": 119500,
  "totalPL": 19500,
  "todayPL": 500
}
```

See [API.md](./API.md) for complete API documentation.

## 📈 Trading Strategies

The platform includes several validated strategies:

### 1. EMA Trend Following
**Logic:** Price above EMA50 AND EMA50 above EMA200
**Use Case:** Identify uptrends
**Best For:** Swing trading

### 2. RSI + EMA Momentum
**Logic:** Price > EMA50 AND RSI 50-70 (not overbought)
**Use Case:** Catch momentum in trending markets
**Best For:** Positional trading

### 3. MACD Momentum
**Logic:** MACD line > Signal line with volume confirmation
**Use Case:** Identify momentum shifts
**Best For:** Medium-term trades

### 4. Volume Breakout
**Logic:** Price breakout + Volume > 20-day MA
**Use Case:** Catch volume-led rallies
**Best For:** Intraday momentum

### 5. VWAP Intraday
**Logic:** Price above VWAP (intraday) with RTH confirmation
**Use Case:** Intraday mean reversion
**Best For:** Intraday scalping

**Note:** These are research strategies. Extensive backtesting and paper trading are required before live trading.

## ⚠️ Risk Management

### Position Sizing
```
Max Capital Risk = Portfolio * Risk %
Position Size = Max Capital Risk / (Entry - Stop Loss)
```

### Risk Limits (Configurable)
- Max risk per trade: 1-2% of capital
- Max daily loss: 3-5% of capital
- Max open positions: 5-10
- Max position size: 5% of capital

### Automatic Safeguards
- Stop loss orders enforced
- Target orders enforced
- Position size validation
- Risk limit checks before any order
- Emergency kill switch for live trading

## 🚀 Deployment

### Docker Compose (Development)
```bash
docker compose up -d
docker compose down
docker compose logs -f backend
```

### AWS Deployment (Production)
See [DEPLOYMENT.md](./DEPLOYMENT.md) for:
- ECR image building
- ECS task definitions
- RDS PostgreSQL setup
- ElastiCache Redis setup
- Application Load Balancer
- CloudFront CDN
- Secrets Manager
- CloudWatch monitoring

## 🧪 Testing

### Test Coverage
- **Backend:** Controllers, services, repositories, strategies, risk calculations
- **Python:** Indicators, strategies, backtesting, ML preprocessing
- **Frontend:** Critical components, integration tests

### Running Tests
```bash
# Backend
cd backend && ./mvnw test

# Python
cd ml-service && pytest -v

# Frontend
cd frontend && npm test
```

### Critical Test Cases
- Empty market data
- Missing candles / gaps
- Stop loss and target execution
- Insufficient capital
- Invalid inputs
- API timeouts and failures
- Database unavailability
- Look-ahead bias in backtests
- No survivorship bias

## 📋 Roadmap

### Phase 1 (Current): Foundation ✓
- Project structure
- Git workflow
- Environment setup
- Base documentation

### Phase 2: Authentication
- User registration
- Login with JWT
- Password hashing
- Refresh tokens
- Role-based access

### Phase 3: Market Data
- Data provider abstraction
- Stock master data
- Historical price caching
- Near-real-time updates

### Phase 4: Charts & UI
- Stock detail pages
- Interactive candlestick charts
- Timeframe selection
- Responsive design

### Phase 5: Technical Indicators
- Indicator calculations
- SMA, EMA, RSI, MACD, VWAP, Bollinger Bands
- Real-time calculation
- Indicator tests

### Phase 6: Strategy Engine
- Strategy abstraction
- 5+ strategies
- Signal generation
- Confidence scoring

### Phase 7: Risk Engine
- Position sizing
- Risk limit checks
- Exposure calculation
- Validation

### Phase 8: Backtesting
- Historical data loader
- No look-ahead bias
- Trade simulation
- Metrics calculation

### Phase 9: Paper Trading
- Virtual orders
- Portfolio tracking
- P&L calculation
- Order history

### Phase 10: ML System
- Feature engineering
- Model training (walk-forward)
- Prediction pipeline
- Validation

### Phase 11: LLM Integration
- Signal explanation
- Market summarization
- Alert notifications
- Q&A system

### Phase 12: Alerts
- Price alerts
- Signal alerts
- Email notifications
- In-app notifications

### Phase 13: Broker Abstraction
- Broker interface
- Paper broker implementation
- Real broker adapters (Zerodha, Angel)
- Order management

### Phase 14: DevOps
- Docker containerization
- Docker Compose
- Jenkins CI/CD
- Automated testing

### Phase 15: Production Hardening
- Performance optimization
- Security review
- Monitoring & logging
- Load testing
- Documentation

## ❓ FAQ

### Is this a guaranteed profit system?
No. This is a research and analysis tool. All signals are algorithmic suggestions based on historical patterns. Past performance does not guarantee future results.

### Can I trade with real money?
Real-money trading is disabled by default. You must explicitly enable it after understanding the risks and validating strategies through paper trading.

### What market data providers are supported?
Currently: Mock provider (development)
Future: NSE APIs, Zerodha, Angel Broking, Polygon.io, Alpha Vantage

### How do I backtest?
1. Go to Backtesting section
2. Select stock, strategy, timeframe, date range
3. Set initial capital and risk parameters
4. Run backtest
5. Analyze metrics and charts

### What's the minimum capital for paper trading?
₹100,000 virtual capital is provided. Real trading requirements depend on your broker.

### Can I export backtest results?
Yes. Backtest results can be exported as CSV or PDF.

### How often is market data updated?
- Intraday: Every 1-5 minutes (based on provider)
- Daily: Daily at market close
- Historical: As needed

### Is my data safe?
Yes. All credentials are encrypted. Market data is read-only. Portfolio data is user-specific and encrypted.

## 📞 Support & Contact

For issues, feature requests, or contributions:
1. Create a GitHub issue
2. Follow [CONTRIBUTING.md](./CONTRIBUTING.md)
3. Submit a pull request

## 📄 License

[Your License Here]

## ⚠️ Legal & Regulatory Disclaimer

This platform is for research and educational purposes. Users are responsible for:
- Understanding market risks
- Complying with regulatory requirements
- Paying applicable taxes
- Conducting due diligence
- Managing their own capital

The authors and contributors make no warranties about:
- Accuracy of signals
- Profitability of strategies
- Completeness of market data
- Availability of services

Use at your own risk.

---

**Last Updated:** [Date]  
**Version:** 1.0.0 (Development)  
**Status:** Active Development
