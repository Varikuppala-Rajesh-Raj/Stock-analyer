# Implementation plan

## Current status: Phase 1 complete

The repository now has independently buildable frontend, backend, and quant-service foundations; health endpoints; container definitions; and an explicit PAPER-only posture. No market values, credentials, broker connections, or fabricated prices are present.

## Delivery phases

1. Foundation — monorepo, local configuration, health checks, containers, CI baseline.
2. Authentication — PostgreSQL schema, user/role model, password hashing, JWT and authorization tests.
3. Market data — provider interface, legal provider configuration, cache, data quality checks.
4. Charts — stock search/detail APIs and responsive chart UI.
5. Indicators — validated calculations with known-value tests.
6. Strategies — deterministic signal engine and explainable results.
7. Risk — limits, sizing and trade rejection tests.
8. Backtesting — no-look-ahead execution and performance reporting.
9. Paper trading — virtual ledger, positions and portfolio reporting.
10. ML experiments — time-series validation only, model registry and comparisons.
11. LLM explanations — structured-fact explanations; never order execution.
12. Alerts — modular in-app/email alert delivery.
13. Broker abstraction — paper adapter first; live adapter stays disabled.
14. DevOps — CI pipeline, image scanning and deployment configuration.
15. Production hardening — observability, threat-model review, performance and recovery testing.

Each phase requires its targeted tests to pass before the next begins.
