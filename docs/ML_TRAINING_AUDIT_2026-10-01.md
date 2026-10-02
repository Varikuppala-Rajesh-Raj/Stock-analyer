# NIFTY ML Training Audit — 2026-10-01

## Outcome

Both direction and magnitude models were trained from real Upstox NIFTY M5 candles using the existing model architectures and chronological 80/20 holdout with a three-row forward-label purge. Neither model passed the existing validation criteria. Both remain `TRAINED_UNVALIDATED`; automation eligibility is false. Paper automation was not enabled or bypassed. The option-return model was not trained because there are no historical option snapshots.

The live-aligned feature calculation uses the most recent 500 candles at each prediction timestamp, matching the existing intraday analysis window. No future candles are included in features. Upstox V3 minute history was fetched in non-overlapping 30-calendar-day chunks, ending on the last completed India-local day.

## Pipeline and Target

- Training range: requested last 365 completed calendar days, 2025-10-01 through 2026-09-30.
- Input bars: real Upstox NIFTY 50 index M5 OHLCV, resolved by `NSE_INDEX|Nifty 50`.
- Features: 28 named fields from `technical-features-v1`; generated from candles at or before the prediction timestamp, capped to 500 candles.
- Return target: `100 * (futureClose - currentClose) / currentClose`.
- Direction labels: UP at return >= +0.20%, DOWN at return <= -0.20%, otherwise NEUTRAL.
- Target alignment: a row is retained only when the target candle timestamp is exactly current timestamp + 15 minutes. Longer gaps are skipped, never relabeled as 15-minute targets.
- Split: chronological 80/20, with 3 rows purged from the training boundary for the 15-minute forward label.
- Direction architecture: StandardScaler + LogisticRegression (`max_iter=1000`, existing `class_weight="balanced"`).
- Magnitude architecture: StandardScaler + Ridge (`alpha=1.0`).
- Existing direction validation: accuracy >= 0.52, macro F1 >= 0.35, and accuracy greater than the holdout majority-class baseline.
- Existing magnitude validation: MAE <= 0.55%, RMSE <= 1.0%, and both errors better than the zero-return baseline.

## Thirty-Day Failure Audit

Date range was 2026-09-01 through 2026-09-30. Upstox returned 1,575 candles across 21 full observed sessions (75 candles each). There were no duplicate timestamp groups and no invalid OHLC rows.

There were 1,513 eligible anchors after warmup and forward-range checks. Exactly 1,453 had a target candle 15 minutes later; 60 were skipped at session transitions: 48 overnight and 12 weekend crossings. There were no same-session missing targets, weekday gaps, or accepted longer targets.

| Partition | UP | DOWN | NEUTRAL | Total |
|---|---:|---:|---:|---:|
| Training | 22 (1.90%) | 19 (1.64%) | 1,118 (96.46%) | 1,159 |
| Purged | 0 | 0 | 3 (100%) | 3 |
| Holdout | 9 (3.09%) | 16 (5.50%) | 266 (91.41%) | 291 |
| All examples | 31 (2.13%) | 35 (2.41%) | 1,387 (95.46%) | 1,453 |

The 91.41% baseline is exactly the holdout NEUTRAL share, 266/291. This confirms the baseline is caused by the measured label distribution, not an assumed distribution. With the unchanged ±0.20% threshold, 30-day return percentiles were: min -0.395291%, max 0.468227%, mean -0.005286%, median -0.004502%, standard deviation 0.090055%, p1 -0.254300%, p5 -0.147639%, p10 -0.104365%, p25 -0.053499%, p50 -0.004502%, p75 0.042106%, p90 0.090311%, p95 0.125571%, p99 0.264842%.

Thirty-day holdout results were direction accuracy 0.1134, macro F1 0.0793, majority baseline 0.9141; magnitude MAE 0.084519% versus baseline 0.081797%, and RMSE 0.115619% versus baseline 0.111700%. The target definition and threshold were not changed.

## One-Year Coverage and Alignment

| Measure | Result |
|---|---:|
| Requested range | 2025-10-01 to 2026-09-30 |
| Actual candle timestamps | 2025-10-01 09:15 IST to 2026-09-30 15:25 IST |
| Fetched / unique candles | 18,462 / 18,462 |
| Duplicate candles removed | 0 |
| Invalid OHLC candles accepted | 0 |
| Observed dates with candles | 247 |
| Expected bars at 75 per observed date | 18,525 |
| Difference from regular-session template | 63 bars |
| Usable exact 15-minute examples | 17,662 |
| Eligible anchors | 18,400 |
| Warmup candles excluded | 59 |
| Insufficient forward candles | 3 |
| Exact-horizon targets skipped | 738 |

The 738 skipped targets comprise 552 overnight, 156 weekend crossings, and 30 weekday gaps or possible holidays. No same-session missing target was observed, and no target with a longer-than-15-minute gap was accepted. The 63-bar difference from a simplistic 75-bar-per-date expectation is concentrated on 2025-10-21, which has 12 observed bars from 13:45 through 14:40 IST. This timing is consistent with a short special/Muhurat session, not a regular 75-bar day; the deployed audit intentionally labels it partial rather than treating a fixed regular-session template as authoritative. Historical candle staleness is not meaningful in the same way as live-quote staleness; session coverage and target alignment are reported instead.

## One-Year Distributions

| Partition | UP | DOWN | NEUTRAL | Total |
|---|---:|---:|---:|---:|
| Training | 556 (3.94%) | 609 (4.31%) | 12,961 (91.75%) | 14,126 |
| Purged | 0 | 0 | 3 (100%) | 3 |
| Holdout | 48 (1.36%) | 48 (1.36%) | 3,437 (97.28%) | 3,533 |
| All examples | 604 (3.42%) | 657 (3.72%) | 16,401 (92.86%) | 17,662 |

One-year 15-minute return distribution in percent: min -1.259781, max 0.859424, mean -0.001472, median -0.000599, standard deviation 0.113013, p1 -0.302107, p5 -0.177367, p10 -0.125518, p25 -0.058541, p50 -0.000599, p75 0.055006, p90 0.118382, p95 0.169520, p99 0.318178.

Chronological ranges: training 2025-10-01 08:40Z through 2026-07-22 09:00Z; purged 2026-07-22 09:05Z through 09:15Z; holdout 2026-07-22 09:20Z through 2026-09-30 09:40Z.

## Feature Completeness

All 28 features have 17,662 non-null values and zero nulls. Six features are constant zero, which means they are present syntactically but contain no historical information. They are not forward-filled or silently removed.

| Feature | Min | Max | Mean | Std. dev. | Status |
|---|---:|---:|---:|---:|---|
| ema9 | 22220.5761 | 26353.1608 | 24627.8133 | 1032.6920 | variable |
| ema20 | 22246.0432 | 26341.8450 | 24628.4016 | 1031.3842 | variable |
| ema50 | 22341.5350 | 26326.7436 | 24630.0280 | 1027.6602 | variable |
| ema_slope_percent | -1.235133 | 1.384593 | -0.002212 | 0.104227 | variable |
| price_to_ema20_percent | -2.733189 | 3.262523 | -0.004259 | 0.213818 | variable |
| price_to_ema50_percent | -3.170512 | 3.734227 | -0.011130 | 0.353139 | variable |
| rsi14 | 0.160481 | 98.315321 | 49.608318 | 17.944119 | variable |
| macd | -223.4583 | 251.3289 | -0.757983 | 27.9511 | variable |
| macd_signal | -194.4520 | 219.0501 | -0.770625 | 26.4883 | variable |
| macd_histogram | -87.1394 | 92.0339 | 0.012642 | 8.052776 | variable |
| roc10_percent | -3.165699 | 3.783773 | -0.003705 | 0.314328 | variable |
| atr | 7.657143 | 141.757143 | 23.228332 | 11.241932 | variable |
| atr_percent | 0.031479 | 0.549846 | 0.094957 | 0.047516 | variable |
| bollinger_width_percent | 0.054830 | 6.797897 | 0.506941 | 0.520870 | variable |
| recent_high | 22250.2000 | 26373.2000 | 24688.5442 | 1023.2520 | variable |
| recent_low | 22182.5500 | 26313.8500 | 24566.0881 | 1042.0501 | variable |
| breakout_strength | 0 | 20 | 2.584079 | 6.708510 | variable |
| relative_volume | 0 | 0 | 0 | 0 | constant zero |
| vwap_distance_percent | 0 | 0 | 0 | 0 | constant zero |
| ema20_distance_percent | -2.733189 | 3.262523 | -0.004259 | 0.213819 | variable |
| upper_band_distance_percent | -4.986491 | 1.789595 | -0.256239 | 0.358830 | variable |
| current_volume | 0 | 0 | 0 | 0 | constant zero |
| average_volume20 | 0 | 0 | 0 | 0 | constant zero |
| regime_score | -65 | 65 | -0.305458 | 60.522663 | variable |
| market_regime_encoded | -2 | 1 | -0.270694 | 1.151398 | variable |
| context_available | 0 | 0 | 0 | 0 | constant zero |
| market_context_score | 0 | 0 | 0 | 0 | constant zero |
| overall_technical_score | -58 | 58 | -0.579946 | 33.343403 | variable |

The six constant-zero fields show that index volume/VWAP and historical market-context values are unavailable in these rows. Source inspection confirms the immediate causes: Upstox NIFTY index candles have zero volume; `TechnicalFeatureService` derives current/average volume from those values; the VWAP denominator is the sum of candle volume and its indicator returns zero when that denominator is zero; and training constructs `MarketContext` with `indexContext=null`, which produces zero `context_available` and `market_context_score`. `MarketContextService` exists, but its global-context implementation fetches current quotes and there is no timestamp-indexed context-history table, so it cannot safely reconstruct past features as-is. This is a verified feature-quality limitation, but it does not by itself prove which features caused the holdout failure.

## Root Causes and Next Training Decision

The six constant-zero features have three distinct causes:

- `current_volume`, `average_volume20`, and `relative_volume`: the Upstox V3 adapter maps the provider's candle volume field directly, and the audited NIFTY index M5 history contains zero volume. The ratio helper returns zero when its average-volume denominator is zero. These fields are unavailable, not evidence of no market activity. Do not replace index volume with futures volume; store futures volume as a separately sourced feature if licensed historical futures bars become available.
- `vwap_distance_percent`: VWAP uses `sum(typicalPrice * volume) / sum(volume)`; `IndicatorEngine` returns zero when total volume is zero. The resulting zero distance is a fallback sentinel, not a measured VWAP signal.
- `context_available` and `market_context_score`: historical examples are generated with `MarketContext.indexContext=null`. There is no timestamp-indexed historical context join in this training path, so live/current context must not be substituted into past rows.

The current trained schema is 28 candle-derived technical fields, not the product's broader 34-factor design; six are constant zero in the audited dataset, leaving 22 that vary. Keep the current model artifacts frozen and block retraining while the audit reports constant/unavailable inputs. The final architecture remains the broader, point-in-time feature model (B), introduced incrementally only after each factor has verified historical coverage, publication/availability time, units, and freshness. The current audit endpoint exposes these source diagnoses and the training endpoint returns `409 DATA_AUDIT_REQUIRED` with the report before calling either model trainer when data quality is still under review.

The exact-horizon target alignment remains `features <= T` predicting the candle at `T + 15 minutes`; rows without that exact timestamp are skipped. The one-year return distribution is centered near zero (mean -0.001472%, median -0.000599%, standard deviation 0.113013%); p05 is -0.177367% and p95 is +0.169520%. With the existing +/-0.20% threshold, the one-year holdout contains 48 UP, 48 DOWN, and 3,437 NEUTRAL rows (96 directional rows, 2.72%). This is a rare-tail event definition for 15-minute bars, not automatically a useful trading target. Keep it unchanged until a candidate movement is defined against execution costs and evaluated on a new untouched holdout; do not tune the inspected holdout.

## Thirty-Day vs One-Year Holdout

| Model / metric | 30-day | 1-year | Existing gate |
|---|---:|---:|---|
| Direction accuracy | 0.1134 | 0.8089 | >= 0.52 and greater than majority baseline |
| Direction macro F1 | 0.0793 | 0.3508 | >= 0.35 |
| Direction majority baseline | 0.9141 | 0.9728 | Model must beat it |
| Magnitude MAE (%) | 0.084519 | 0.058930 | <= 0.55 and below zero-return baseline |
| Magnitude baseline MAE (%) | 0.081797 | 0.058684 | Model must beat it |
| Magnitude RMSE (%) | 0.115619 | 0.081925 | <= 1.0 and below zero-return baseline |
| Magnitude baseline RMSE (%) | 0.111700 | 0.081714 | Model must beat it |

One-year direction per-class metrics (class order DOWN, NEUTRAL, UP):

| Class | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| DOWN | 0.0714 | 0.1458 | 0.0959 | 48 |
| NEUTRAL | 0.9820 | 0.8243 | 0.8962 | 3,437 |
| UP | 0.0327 | 0.3750 | 0.0602 | 48 |

One-year confusion matrix, true rows and predicted columns in order DOWN, NEUTRAL, UP:

```text
[[7, 22, 19],
 [91, 2833, 513],
 [0, 30, 18]]
```

The one-year direction scores improved materially but still fail because 0.8089 accuracy is below the 0.9728 majority baseline. Macro F1 barely exceeds its 0.35 floor. One-year magnitude is better than the 30-day magnitude error, but both errors remain slightly worse than the zero-return baseline. R² is not emitted by the current NIFTY magnitude trainer.

## Decisions and Remaining Limits

- Direction: `TRAINED_UNVALIDATED`, not automation eligible.
- Magnitude: `TRAINED_UNVALIDATED`, not automation eligible.
- Paper automation: `modelsEligibleForAutomation=false`; existing validation gate unchanged.
- Option model: `MODEL_NOT_TRAINED`. At the time of the initial audit the table was empty; live collection has since stored 388 real rows across 194 contracts at two rounds, 2026-10-01 15:25 and 15:30 IST. There are zero same-contract 15-minute pairs, so training remains blocked. The collector is scheduled every five minutes during configured market hours. Do not train from synthetic data; continue collecting timestamped real option-chain snapshots and later observations of the same contract at the target horizon, with contemporaneous point-in-time NIFTY inputs.
- No threshold, model architecture, validation cutoff, or class-weighting change was made. The classifier's existing balanced class weights were left as-is.
- The one-year holdout remains 97.28% neutral, and six input fields are constant zero. These are observed bottlenecks; the next step should improve historical feature availability and verify the special-session/holiday calendar, then reassess on a new untouched chronological holdout. Do not tune against this already inspected holdout.
- Before reusing the broader `MarketContextService` for historical training, persist timestamped global/news/market context observations and select only observations available at or before each feature timestamp. Its current live-quote behavior is not an as-of historical source.

## Verification

- Backend: 66 tests, 0 failures, 0 errors, 0 skipped.
- ML service: 11 tests passed, 0 failures.
- Focused additions cover 30-day history chunking, year chunk boundaries, candle sorting/deduplication, exact 15-minute target alignment, overnight/weekend/weekday-closure exclusions, bounded no-lookahead feature windows, validation diagnostics, and failed-model automation ineligibility.

## NSE Source Access Assessment

Reviewed NSE's official historical reports, contract-wise report, historical-index page, paid EOD/historical product page, paid real-time product page, and FII/FPI-DII page on 2026-10-01.

| Source | Data/frequency confirmed | Access and fit for this pipeline |
|---|---|---|
| [Historical Index Data](https://www.nseindia.com/reports-indices-historical-index-data) and [Historical Data — India VIX](https://www.nseindia.com/reports-indices-historical-vix) | Separate interactive CSV pages, each with 1D, 1W, 1M, 3M, 6M, 1Y and custom period selectors. NSE archive examples are daily index-close CSVs; neither page documents intraday bars. | Useful EOD NIFTY and India VIX verification/daily context over up to a selected year; not a demonstrated 5-minute index/VIX history API. |
| [Historical Contract-wise Price Volume Data](https://www.nseindia.com/report-detail/fo_eq_security) | Public contract selector includes instrument/symbol/year/expiry/option type/strike and CSV download. Page explicitly limits the selectable time period to 90 days. It is a contract-wise price/volume report; the page does not document a 5-minute interval. | Useful recent daily contract checks; insufficient by itself for a one-year intraday feature/option-target dataset. Confirm exact returned columns from an actual downloaded CSV before mapping OI/volume fields. |
| [Historical Reports — Derivative Market](https://www.nseindia.com/resources/historical-reports-capital-market-daily-monthly-archives-derivative-market) and [All Reports — Derivatives](https://www.nseindia.com/all-reports-derivatives) | Public dated daily/monthly reports. The dated archive lists F&O reports, contract/static files and daily artifacts. NSE's paid EOD product says EOD binary files include F&O bhavcopy plus security/trade details. | EOD contract-level records can support daily futures/options validation and contract metadata, not 5-minute sequence reconstruction. Exact OI/field names require the FAO technical spec/sample file. |
| [Paid End-of-Day / Historical Data](https://www.nseindia.com/static/market-data/eod-historical-data-subscription) | EOD files are generated at end of trading day in binary format; NSE says they contain EOD bhavcopy and security/trade details for F&O among other segments. A dedicated [FAO EOD technical specification](https://nsearchives.nseindia.com/web/mediaattachment/2025-11/EOD_FAO_20251120105405.pdf) is published. The page separately lists Historical Trade and Historical Order & Trade products. | Requires NSE Data & Analytics subscription/licensing and delivery setup. EOD is not an intraday substitute; exact OI/field mapping must be confirmed from a sample and the FAO schema. |
| [Paid Real-Time Data](https://www.nseindia.com/static/market-data/real-time-data-subscription) | NSE advertises 1-minute and 5-minute snapshot products, 15-minute delayed snapshots, index feeds, and F&O feeds. Real-time feed levels span CM/F&O; documented delivery includes multicast or authorized vendors. | Strong candidate for prospective point-in-time collection if commercial terms and FAO/index fields meet the need. This is live/future collection, not proof of historical backfill. |
| [Historical Order & Trade specification](https://nsearchives.nseindia.com/web/mediaattachment/2026-09/NSE_Hist_Order_Trade_Data_1.19_20260902144045.pdf) and [layout](https://archives.nseindia.com/content/press/Hist_Order_data_layout.pdf) | NSE publishes versioned historical event-data specifications and layouts covering market segments including FAO. Search-indexed NSE layout text describes very large compressed FAO files (tens of GB, split into multiple files). | Potential source for event-time trade/order reconstruction, but a sample, field-level timestamp definition, contract keys, retention, delivery and licence must be verified. Trade events can be aggregated to OHLCV; they do not alone establish point-in-time OI snapshots. Do not assume they solve OI or option-chain snapshots without the actual licensed files/spec. |
| [FII/FPI & DII activity](https://www.nseindia.com/reports/fii-dii) | Downloadable CSV reports for capital-market segment activity; daily aggregate flow, not per-instrument intraday observations. The current page confirms CSV availability but this review did not establish a backfillable historical date range there. | Could be used only as an appropriately lagged daily feature, with publication/availability time respected. Not an intraday 5-minute signal. |

The official public NSE pages reviewed did not expose a supported public REST endpoint for historical 5-minute NIFTY index/futures/options bars. NSE separately publishes an [India VIX CSV history](https://www.nseindia.com/reports-indices-historical-vix); the index/VIX UIs have 1D/1W/1M/3M/6M/1Y/custom date-period selectors, but do not state an intraday interval. Public dated F&O contract price/volume history is capped at 90 days. Paid real-time snapshots are for forward collection, while paid EOD/order-trade products require a commercial subscription. Consequently, retain the existing Upstox V3 chunked historical M5 path for the current one-year backfill unless NSE Data & Analytics supplies a licensed historical dataset with a sample demonstrating exact timestamps and required contract/OI fields. Do not scrape undocumented browser APIs as a production dependency.

### Proposed Data Plan

1. Keep NSE as the preferred authority for instrument masters, exchange calendars, public daily reports, and EOD validation; retain Upstox V3 as the proven one-year M5 historical index provider.
2. Request an NSE Data & Analytics sample/quote for licensed FAO historical order/trade data and 1-/5-minute snapshots. Before implementation, verify: timestamp timezone/precision, contract identifier stability, trade/order side, OHLC aggregation feasibility, volume units, OI availability/frequency, option bid/ask/depth coverage, retention start, delivery format, licence permitting model training, and complete price/tariff.
3. For futures features, store futures price, traded contracts/volume, OI and change-in-OI as distinct, timestamped fields. Never copy futures volume into an index-volume feature.
4. For options training, require same-contract, same-expiry point-in-time observations bracketing the 15-minute target, with executable bid/ask or a documented mark price. Do not synthesize missing contracts or targets.
5. Store global/context/news inputs as timestamped observations before using them in historical training. Join only observations available at or before each feature timestamp, with explicit maximum-age rules.
6. Add daily FII/FPI-DII only as a lagged daily feature after verifying release timing; it cannot be joined as if known throughout the same day.

The source pages provide products and summaries, but this investigation did not obtain a licensed NSE sample file or tariff quote. NSE publishes [domestic/international product tariffs](https://www.nseindia.com/static/market-data/products-tariff). Exact FAO EOD and historical order/trade field mappings, retention limits, timestamp timezone/precision, and model-training use rights therefore remain unverified and require confirmation from NSE Data & Analytics before building an adapter. The real-time page routes access through NSE Data & Analytics or authorized vendors; it describes multicast/leased-line delivery for feeds. Snapshot product terms and whether historical replay is included must be confirmed commercially.