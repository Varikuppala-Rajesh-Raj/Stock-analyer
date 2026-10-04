# Implementation plan

## Current status: foundation complete; automated NIFTY paper trading implemented, operational validation pending

The repository has market-data, deterministic-analysis, option-chain, virtual-ledger, and quant-service foundations. The NIFTY CE/PE paper cycle connects fresh NIFTY/futures context to filtered option selection, risk-sized paper orders, exits, and a persisted cycle audit. The paper portfolio dashboard exposes account metrics, open holdings, order events, and cycle decisions; its emergency stop persists across restarts. Automation remains disabled by default. It is not operationally validated until live provider credentials, the current instrument master, PostgreSQL migrations through V12, and one-lot virtual account limits are configured and exercised.

Dashboard audit boundary: raw spot/futures snapshots, option filter-level outcomes, per-order exit reason, peak holding price, and partial-close P&L allocation are not yet persisted, so the UI explicitly marks them unavailable.

## Delivery phases

1. Foundation — monorepo, local configuration, health checks, containers, CI baseline.
2. Authentication — PostgreSQL schema, user/role model, password hashing, JWT and authorization tests.
3. Market data — provider interface, legal provider configuration, cache, data quality checks.
4. Charts — stock search/detail APIs and responsive chart UI.
5. Indicators — validated calculations with known-value tests.
6. Strategies — deterministic signal engine and explainable results.
7. Risk — limits, sizing and trade rejection tests.
8. Backtesting — no-look-ahead execution and performance reporting.
9. Paper trading — virtual ledger, positions and portfolio reporting. NIFTY CE/PE auto-paper v1 is implemented; production-grade operational validation and historical replay remain.
10. ML experiments — time-series validation only, model registry and comparisons.
11. LLM explanations — structured-fact explanations; never order execution.
12. Alerts — modular in-app/email alert delivery.
13. Broker abstraction — paper adapter first; live adapter stays disabled.
14. DevOps — CI pipeline, image scanning and deployment configuration.
15. Production hardening — observability, threat-model review, performance and recovery testing.

Each phase requires its targeted tests to pass before the next begins.
