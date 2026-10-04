import { useEffect, useState } from 'react';
import {
  api,
  type PaperDryRunCoverage,
  type PaperDryRunResult,
  type PaperDryRunSettings,
} from '../services/api';
import './PaperDryRunPage.css';

const money = (value: number) => new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
}).format(value);

const indiaDate = (date: Date) => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Kolkata',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(date);

const yesterday = () => {
  const date = new Date(`${indiaDate(new Date())}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - 1);
  return indiaDate(date);
};

const time = (value: string | null) => value
  ? new Date(value).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })
  : '—';

export function PaperDryRunPage() {
  const [settings, setSettings] = useState<PaperDryRunSettings>();
  const [date, setDate] = useState(yesterday);
  const [capital, setCapital] = useState('');
  const [lotSize, setLotSize] = useState('');
  const [coverage, setCoverage] = useState<PaperDryRunCoverage>();
  const [result, setResult] = useState<PaperDryRunResult>();
  const [checking, setChecking] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    void api.paperDryRunSettings().then((value) => {
      if (!active) return;
      setSettings(value);
      setCapital(String(value.defaultVirtualCapital));
      setLotSize(String(value.defaultLotSize));
    }).catch((reason: unknown) => {
      if (active) setError(reason instanceof Error ? reason.message : 'Unable to load dry-run settings.');
    });
    return () => { active = false; };
  }, []);

  const checkCoverage = async () => {
    setChecking(true);
    setError('');
    setResult(undefined);
    try {
      setCoverage(await api.paperDryRunCoverage(date));
    } catch (reason) {
      setCoverage(undefined);
      setError(reason instanceof Error ? reason.message : 'Unable to check historical data coverage.');
    } finally {
      setChecking(false);
    }
  };

  const startRun = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setRunning(true);
    setError('');
    setResult(undefined);
    try {
      const checked = coverage?.date === date ? coverage : await api.paperDryRunCoverage(date);
      setCoverage(checked);
      if (!checked.ready) {
        setError(checked.note);
        return;
      }
      setResult(await api.runPaperDryRun(date, Number(capital), Number(lotSize)));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'The historical dry run could not be completed.');
    } finally {
      setRunning(false);
    }
  };

  const changeDate = (value: string) => {
    setDate(value);
    setCoverage(undefined);
    setResult(undefined);
    setError('');
  };

  const coverageGaps = coverage ? [
    { label: 'Option snapshots', missing: coverage.missingOptionIntervals },
    { label: 'Spot candles', missing: coverage.missingSpotIntervals },
    { label: 'Spot confirmation', missing: coverage.missingSpotConfirmationIntervals },
    { label: 'Futures confirmation', missing: coverage.missingFuturesIntervals },
  ].filter((gap) => gap.missing.length > 0) : [];

  return (
    <main className="dry-run-page">
      <div className="heading">
        <div>
          <small>HISTORICAL REPLAY · ISOLATED SIMULATION</small>
          <h1>Automated trading dry run</h1>
          <p>Replay the NIFTY CE/PE entry rules against saved historical market snapshots.</p>
        </div>
      </div>

      <div className="dry-run-safety" role="note">
        <b>Historical simulation only — no real or paper orders are submitted.</b>
        <span>Results do not touch your paper account. Contract decisions use the data actually saved for the selected day.</span>
      </div>

      {error && <p className="api-state error" role="alert">{error}</p>}

      <form className="panel dry-run-form" onSubmit={(event) => void startRun(event)}>
        <div className="panel-title">
          <div><small>REPLAY INPUTS</small><h2>Choose a previous trading day</h2></div>
          <span className="pill neutral">NIFTY OPTIONS</span>
        </div>
        <div className="dry-run-fields">
          <label>
            Trading date
            <input type="date" value={date} max={yesterday()} onChange={(event) => changeDate(event.target.value)} required />
          </label>
          <label>
            Simulated starting capital
            <input type="number" min="1" step="0.01" value={capital} onChange={(event) => setCapital(event.target.value)} required />
          </label>
          <label>
            Historical lot quantity
            <input type="number" min="1" step="1" value={lotSize} onChange={(event) => setLotSize(event.target.value)} required />
          </label>
        </div>
        <p className="paper-disclaimer">
          Lot size is not stored in the historical option snapshots. Verify the exchange lot quantity applicable on the selected date.
          Capital and lot quantity here only affect this isolated simulation.
        </p>
        <div className="dry-run-actions">
          <button type="button" className="outline" onClick={() => void checkCoverage()} disabled={!date || checking || running}>
            {checking ? 'Checking saved data…' : 'Check date coverage'}
          </button>
          <button type="submit" className="primary" disabled={!settings || !coverage?.ready || coverage.date !== date || running || checking}>
            {running ? 'Replaying historical session…' : 'Run dry run'}
          </button>
        </div>
      </form>

      {coverage && (
        <section className="panel dry-run-coverage" aria-live="polite">
          <div className="panel-title">
            <div><small>HISTORICAL DATA CHECK</small><h2>{coverage.date}</h2></div>
            <span className={`pill ${coverage.ready ? 'good' : 'hold'}`}>{coverage.status.replace('_', ' ')}</span>
          </div>
          <div className="dry-run-facts">
            <Fact label="Session / time zone" value={`09:15–15:30 · ${coverage.timezone}`} />
            <Fact label="Option snapshots" value={`${coverage.optionIntervals}/${coverage.expectedIntervals} intervals · ${coverage.optionSnapshotRows.toLocaleString('en-IN')} rows`} />
            <Fact label="NIFTY spot M5 bars" value={`${coverage.spotIntervals}/${coverage.expectedIntervals} · ${coverage.spotSource}`} />
            <Fact label="Spot confirmation" value={`${coverage.spotConfirmationIntervals}/${coverage.expectedIntervals}`} />
            <Fact label="Futures confirmation" value={`${coverage.futuresIntervals}/${coverage.expectedIntervals} · ${coverage.futuresObservations} raw observations`} />
            <Fact label="Required coverage" value={`${coverage.requiredIntervals}/${coverage.expectedIntervals} (${coverage.minimumCoveragePercent}%)`} />
            <Fact label="Option observation window (IST)" value={`${time(coverage.firstSnapshot)} — ${time(coverage.lastSnapshot)}`} />
            <Fact label="Spot candle window (IST)" value={`${time(coverage.firstSpotCandle)} — ${time(coverage.lastSpotCandle)}`} />
            <Fact label="Futures observation window (IST)" value={`${time(coverage.firstFuturesObservation)} — ${time(coverage.lastFuturesObservation)}`} />
          </div>
          <p className="paper-disclaimer">{coverage.note}</p>
          {coverageGaps.length > 0 && (
            <div className="dry-run-gaps" aria-label="Missing historical intervals">
              {coverageGaps.map(({ label, missing }) => (
                <p key={label}><b>Missing {label} ({missing.length}):</b> {missing.slice(0, 10).join(', ')}
                  {missing.length > 10 ? `, and ${missing.length - 10} more` : ''}
                </p>
              ))}
            </div>
          )}
        </section>
      )}

      {result && (
        <>
          <section className="dry-run-metrics" aria-label="Dry run results">
            <Metric label="Net P/L" value={money(result.summary.totalPnl)} tone={result.summary.totalPnl} hint={`${result.summary.returnPercent.toFixed(2)}% on simulated capital`} />
            <Metric label="Realized P/L" value={money(result.summary.realizedNetPnl)} tone={result.summary.realizedNetPnl} hint={`${result.summary.closedTrades} closed trades`} />
            <Metric label="Unrealized P/L" value={money(result.summary.unrealizedPnl)} tone={result.summary.unrealizedPnl} hint={`${result.summary.openTrades} position(s) open at replay end`} />
            <Metric label="Trades opened" value={String(result.summary.tradeEntries)} hint={`${result.summary.winningClosedTrades} wins · ${result.summary.losingClosedTrades} losses`} />
            <Metric label="No-trade decisions" value={String(result.summary.noTradeCycles)} hint={`Across ${result.summary.cycles} saved intervals`} />
            <Metric label="Estimated charges" value={money(result.summary.charges)} hint="Entry/exit brokerage from configured rate" />
          </section>

          <section className="panel dry-run-section">
            <div className="panel-title">
              <div><small>REPLAYED AUTOMATION SETTINGS</small><h2>Risk and execution parameters</h2></div>
              <span className="pill neutral">{result.lotSize} units per entry</span>
            </div>
            <div className="dry-run-facts">
              <Fact label="Simulated starting capital" value={money(result.virtualCapital)} />
              <Fact label="Stop-risk cap / trade" value={`${result.settings.maxLossPercent}%`} />
              <Fact label="Maximum exposure" value={`${result.settings.maxExposurePercent}%`} />
              <Fact label="Stop distance cap" value={`${result.settings.maxStopLossPercent}%`} />
              <Fact label="Target / risk multiple" value={`${result.settings.rewardRiskMultiple}×`} />
              <Fact label="Minimum risk/reward" value={`${result.settings.minRiskReward}×`} />
              <Fact label="Max allocation / trade" value={money(result.settings.maxAllocationPerTrade)} />
              <Fact label="Max daily loss" value={money(result.settings.maxDailyLoss)} />
              <Fact label="Max spread" value={`${result.settings.maxSpreadPercent}%`} />
              <Fact label="Minimum volume / OI" value={`${result.settings.minVolume} / ${result.settings.minOpenInterest}`} />
              <Fact label="ATM distance / delta" value={`≤${result.settings.maxAtmDistancePercent}% · ${result.settings.minAbsDelta}–${result.settings.maxAbsDelta}`} />
              <Fact label="Slippage / brokerage" value={`${result.settings.slippagePercent}% / ${(result.settings.brokerageRate * 100).toFixed(3)}%`} />
            </div>
            <p className="paper-disclaimer">{result.data.replayResolution} Historical option snapshots are nominally five-minute observations, so intrainterval stop/target touches cannot be inferred.</p>
          </section>

          <section className="panel dry-run-section">
            <div className="panel-title">
              <div><small>SELECTED CONTRACT, TRADE LEVELS AND OUTCOME</small><h2>Simulated trades</h2></div>
              <span className="pill neutral">{result.trades.length} TRADES</span>
            </div>
            {result.trades.length === 0 ? <div className="paper-empty">No eligible trade was found on this replay. Expand the decision log below to see why.</div> : (
              <div className="paper-table-wrap">
                <table className="paper-table dry-run-table">
                  <thead><tr><th>Entry / exit (IST)</th><th>Signal / contract</th><th>Qty</th><th>Entry</th><th>Stop / target</th><th>Last / exit</th><th>Realized P/L</th><th>Unrealized</th><th>Exit status</th></tr></thead>
                  <tbody>{result.trades.map((trade, index) => (
                    <tr key={`${trade.openedAt}-${trade.instrumentKey}-${index}`}>
                      <td>{time(trade.openedAt)}<small className="dry-run-subline">{trade.closedAt ? time(trade.closedAt) : 'Still open'}</small></td>
                      <td><b>{trade.direction} · {trade.optionType} {trade.strike}</b><small className="dry-run-subline">{trade.expiry} · signal {trade.signalStrength}%</small></td>
                      <td>{trade.quantity}</td>
                      <td>{money(trade.entryPrice)}</td>
                      <td>{money(trade.stopLoss)} / {money(trade.target)}</td>
                      <td>{money(trade.exitPrice ?? trade.lastPrice)}</td>
                      <td className={trade.closedAt ? tone(trade.netPnl) : ''}>{trade.closedAt ? money(trade.netPnl) : '—'}</td>
                      <td className={tone(trade.unrealizedPnl)}>{money(trade.unrealizedPnl)}</td>
                      <td>{trade.exitReason}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            )}
            {result.trades.map((trade, index) => (
              <details className="dry-run-trade-detail" key={`detail-${trade.openedAt}-${index}`}>
                <summary>{trade.optionType} {trade.strike} · why this contract was selected</summary>
                <div className="dry-run-facts">
                  <Fact label="Selection rule" value={trade.selectedContract.selectionReason} />
                  <Fact label="Selection score" value={trade.selectedContract.selectionScore.toFixed(2)} />
                  <Fact label="Observed option price" value={money(trade.selectedContract.ltp)} />
                  <Fact label="Bid / ask spread" value={`${trade.selectedContract.spreadPercent.toFixed(2)}%`} />
                  <Fact label="Delta" value={trade.selectedContract.delta.toFixed(3)} />
                  <Fact label="Volume / open interest" value={`${trade.selectedContract.volume.toLocaleString('en-IN')} / ${trade.selectedContract.openInterest.toLocaleString('en-IN')}`} />
                  <Fact label="Planned risk / exposure" value={`${trade.stopRiskPercent.toFixed(2)}% / ${trade.exposurePercent.toFixed(2)}%`} />
                  <Fact label="Risk/reward" value={`${trade.riskReward.toFixed(2)}×`} />
                  <Fact label="Peak observed open P/L" value={money(trade.peakUnrealizedPnl)} />
                  <Fact label="Instrument key" value={trade.instrumentKey} />
                </div>
              </details>
            ))}
          </section>

          <section className="panel dry-run-section">
            <div className="panel-title">
              <div><small>WHY IT TRADED — OR DID NOT</small><h2>Historical decision log</h2></div>
              <span className="pill neutral">{result.cycles.length} INTERVALS</span>
            </div>
            <div className="dry-run-cycle-list">
              {result.cycles.map((cycle) => (
                <details className="dry-run-cycle" key={cycle.timestamp}>
                  <summary>
                    <time>{time(cycle.timestamp)}</time>
                    <b>{cycle.direction ?? 'NO CLEAR EDGE'}</b>
                    <span className={`pill ${cycle.status === 'TRADE_OPENED' ? 'good' : 'neutral'}`}>{cycle.status}</span>
                    <span>{cycle.optionType ? `${cycle.optionType} ${cycle.strike ?? ''} · ${cycle.expiry ?? ''}` : 'No contract'}</span>
                    <span className="dry-run-cycle-reason">{cycle.reason}</span>
                  </summary>
                  {cycle.selectedContract && <div className="dry-run-facts">
                    <Fact label="Selection score" value={cycle.selectedContract.selectionScore.toFixed(2)} />
                    <Fact label="LTP / bid / ask" value={`${money(cycle.selectedContract.ltp)} / ${money(cycle.selectedContract.bid)} / ${money(cycle.selectedContract.ask)}`} />
                    <Fact label="Spread / delta" value={`${cycle.selectedContract.spreadPercent.toFixed(2)}% / ${cycle.selectedContract.delta.toFixed(3)}`} />
                    <Fact label="Volume / OI" value={`${cycle.selectedContract.volume.toLocaleString('en-IN')} / ${cycle.selectedContract.openInterest.toLocaleString('en-IN')}`} />
                    <Fact label="Why eligible" value={cycle.selectedContract.selectionReason} />
                  </div>}
                </details>
              ))}
            </div>
          </section>
          <p className="paper-disclaimer">{result.note} Results are based on recorded data, not a guarantee of live execution or performance.</p>
        </>
      )}
    </main>
  );
}

function tone(value: number) {
  return value >= 0 ? 'good' : 'bad';
}

function Fact({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><b>{value}</b></div>;
}

function Metric({ label, value, hint, tone: valueTone }: { label: string; value: string; hint: string; tone?: number }) {
  return <div className="card"><span>{label}</span><b className={valueTone == null ? '' : tone(valueTone)}>{value}</b><small>{hint}</small></div>;
}
