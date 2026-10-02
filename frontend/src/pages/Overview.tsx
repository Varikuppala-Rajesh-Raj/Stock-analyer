import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import './Overview.css';
import { useCandles } from '../services/hooks';
import { PriceChart } from '../components/charts/PriceChart';
import {
  nseMarketDataProvider,
  type MarketDataBundle,
  type MarketFeatureSnapshot,
} from '../services/nseMarketData';

const money = (value?: number | null) => 
  value == null ? '—' : new Intl.NumberFormat('en-IN', { 
    style: 'currency', 
    currency: 'INR', 
    maximumFractionDigits: 2 
  }).format(value);

const overviewChartFrames = ['1m', '5m', '15m', '1h'];

export function NiftyPage() {
  const [chartTimeframe, setChartTimeframe] = useState('5m');
  const { snapshot, loading: nseLoading, refresh: refreshNse } = useNseMarketSnapshot();
  const quote = snapshot?.nifty.data;
  const { candles, loading: chartLoading, error: chartError } = useCandles('NIFTY', chartTimeframe, true);

  return (
    <main>
      <div className="heading">
        <div>
          <small>NSE MARKET</small>
          <h1>NIFTY</h1>
          <p>NIFTY price action, technical context, futures, and options activity.</p>
        </div>
      </div>

      <section className="stats">
        <Card 
          label="NIFTY 50" 
          value={nseLoading && !quote ? 'Loading…' : (quote?.ltp.toFixed(2) || '—')}
          hint={quote?.changePercent == null ? 'NSE change unavailable' : `${quote.changePercent >= 0 ? '+' : ''}${quote.changePercent.toFixed(2)}% today`}
          good={quote?.changePercent == null ? undefined : quote.changePercent >= 0}
        />
        <Card 
          label="Live volume" 
          value={quote?.volume == null ? '—' : new Intl.NumberFormat('en-IN', { notation: 'compact' }).format(quote.volume)}
          hint="NSE reported volume"
        />
      </section>

      <section className="panel overview-chart-panel">
        <div className="panel-title">
          <div>
            <small>PRICE ACTION · NSE</small>
            <h2>NIFTY 50 chart</h2>
          </div>
          <div className="tabs" role="group" aria-label="Chart timeframe">
            {overviewChartFrames.map((frame) => (
              <button
                key={frame}
                className={chartTimeframe === frame ? 'chosen' : ''}
                aria-pressed={chartTimeframe === frame}
                onClick={() => setChartTimeframe(frame)}
              >
                {frame}
              </button>
            ))}
          </div>
        </div>
        <div className="price">
          <b>{money(quote?.ltp)}</b>
          {quote && <span className={quote.change == null || quote.changePercent == null ? '' : quote.changePercent >= 0 ? 'good' : 'bad'}>{quote.change == null || quote.changePercent == null ? 'Change unavailable' : `${quote.change >= 0 ? '+' : ''}${quote.change.toFixed(2)} (${quote.changePercent.toFixed(2)}%)`}</span>}
          <small>{candles.length ? `${candles.length} ${chartTimeframe} candles` : `NIFTY ${chartTimeframe}`}</small>
        </div>
        {chartLoading ? (
          <p className="api-state">Loading NIFTY {chartTimeframe} chart…</p>
        ) : chartError ? (
          <p className="api-state error">Chart data is unavailable: {chartError}</p>
        ) : candles.length ? (
          <PriceChart candles={candles} height={340} />
        ) : (
          <p className="api-state">No NIFTY candle data is available for this timeframe.</p>
        )}
      </section>

      <NseMarketSnapshot snapshot={snapshot} loading={nseLoading} onRefresh={refreshNse} />
    </main>
  );
}

function NseMarketSnapshot({
  snapshot,
  loading,
  onRefresh,
}: {
  snapshot: MarketDataBundle | null;
  loading: boolean;
  onRefresh: () => void;
}) {
  const nifty = snapshot?.nifty.data;
  const features = snapshot?.features;
  const feedStatus = snapshot?.nifty.status ?? 'loading';
  const futures = snapshot?.futures.data;
  const futuresStatus = snapshot?.futures.status ?? 'loading';
  const futuresDisplayStatus = futures
    ? futuresStatus.toUpperCase()
    : futuresStatus === 'error' || futuresStatus === 'no-data'
      ? 'UNAVAILABLE'
      : futuresStatus.toUpperCase();
  const optionStatus = snapshot?.options.status ?? 'loading';
  const optionValues = snapshot?.options.data ?? [];

  return (
    <section className="market-feature-section">
      <div className="panel-title">
        <div>
          <small>NSE MARKET CONTEXT</small>
          <h2>Rolling market features</h2>
          <p>Descriptive numerical features only. No trade recommendations.</p>
        </div>
        <div>
          <span className={`pill ${feedStatus === 'live' ? 'up' : 'sell'}`}>
            {loading && !snapshot ? 'CONNECTING' : feedStatus.toUpperCase()}
          </span>
          <button onClick={onRefresh} disabled={loading}>
            Refresh NSE data
          </button>
        </div>
      </div>

      {!snapshot && loading ? (
        <p className="api-state">Loading NIFTY and option data from the NSE proxy…</p>
      ) : !nifty ? (
        <p className="api-state error">
          {snapshot?.nifty.error ?? 'NSE data is unavailable.'}
        </p>
      ) : (
        <>
          {!features && <p className="api-state">Collecting the first NSE observation…</p>}
          <div className="market-context-grid">
          <section className="panel context-panel">
            <div className="panel-title">
              <div><small>MOMENTUM</small><h2>Price returns</h2></div>
              <span className="pill">{features?.niftyMomentumStatus ?? 'COLLECTING'}</span>
            </div>
            <ContextTable headers={['Metric', 'Return', 'State']}>
              <MomentumRow label="NIFTY 1m" value={features?.niftyReturn1m} status={features?.niftyReturn1mStatus} />
              <MomentumRow label="NIFTY 5m" value={features?.niftyReturn5m} status={features?.niftyReturn5mStatus} />
              <MomentumRow label="NIFTY 10m" value={features?.niftyReturn10m} status={features?.niftyReturn10mStatus} />
              <MomentumRow label="NIFTY 15m" value={features?.niftyReturn15m} status={features?.niftyReturn15mStatus} />
              <MomentumRow label="Futures 5m" value={features?.futuresReturn5m} status={features?.futuresReturn5mStatus} />
              <MomentumRow label="Futures 10m" value={features?.futuresReturn10m} status={features?.futuresReturn10mStatus} />
              <MomentumRow label="Futures 15m" value={features?.futuresReturn15m} status={features?.futuresReturn15mStatus} />
              <tr><th scope="row">Futures lead · 5m</th><td>{formatPercentagePoints(features?.futuresLead)}</td><td><MetricStatus status={features?.futuresLeadStatus} /></td></tr>
            </ContextTable>
          </section>

          <section className="panel context-panel">
            <div className="panel-title">
              <div><small>OPENING / OVERNIGHT</small><h2>Session gap features</h2></div>
              <span className="pill">{features?.niftyOpeningGap != null ? 'AVAILABLE' : 'COLLECTING'}</span>
            </div>
            {features ? (
              <ContextTable headers={['Metric', 'Value']}>
                <ValueRow label="NIFTY overnight" value={formatFeaturePercent(features.niftyOvernightReturn)} />
                <ValueRow label="NIFTY opening gap" value={formatFeaturePercent(features.niftyOpeningGap)} />
                <ValueRow label="Futures overnight" value={formatFeaturePercent(features.futuresOvernightReturn)} />
                <ValueRow label="Futures opening gap" value={formatFeaturePercent(features.futuresOpeningGap)} />
              </ContextTable>
            ) : (
              <p className="api-state">Waiting for the first market-open snapshot…</p>
            )}
          </section>

          <section className="panel context-panel">
            <div className="panel-title">
              <div><small>FUTURES · NSE</small><h2>{futures?.contract ?? 'NIFTY Futures'}</h2></div>
              <MetricStatus status={futuresStatus === 'live' ? 'AVAILABLE' : futuresStatus === 'stale' ? 'STALE' : futuresStatus === 'error' || futuresStatus === 'no-data' ? 'UNAVAILABLE' : 'COLLECTING'} />
            </div>
            {futures ? (
              <ContextTable headers={['Metric', 'Value']}>
                <ValueRow label="Expiry" value={futures.expiry} />
                <ValueRow label="LTP" value={money(futures.ltp)} />
                <ValueRow label="Change" value={formatSignedNumber(futures.change)} />
                <ValueRow label="Change %" value={formatFeaturePercent(futures.changePercent)} />
                <ValueRow label="Basis" value={formatPrice(features?.futuresBasis)} />
                <ValueRow label="Open interest" value={formatFeatureNumber(features?.futuresOI)} />
                <ValueRow label="Change OI" value={formatSignedNumber(futures.changeInOpenInterest)} />
                <ValueRow label="Change OI %" value={formatFeaturePercent(futures.percentChangeInOpenInterest)} />
                <ValueRow label="Volume" value={formatActualInteger(futures.volume)} />
                <ValueRow label="NIFTY underlying" value={money(futures.underlyingValue)} />
                <ValueRow label="Quote timestamp" value={futures.timestamp} />
              </ContextTable>
            ) : (
              <p className="api-state">{snapshot?.futures.error ?? 'NSE NIFTY futures data is unavailable.'}</p>
            )}
          </section>

          <section className="panel context-panel options-context-panel">
            <div className="panel-title">
              <div>
                <small>OPTIONS · {snapshot?.selectedOptionExpiry ?? 'EXPIRY UNAVAILABLE'}</small>
                <h2>Near-ATM open interest</h2>
              </div>
              <span className={`pill ${optionStatus === 'live' ? 'up' : 'sell'}`}>
                {optionStatus.toUpperCase()} · {optionValues.length} strikes
              </span>
            </div>
            {features ? (
              <ContextTable headers={['Metric', 'Value']}>
                <ValueRow label="PCR" value={features.pcr.toFixed(3)} />
                <ValueRow label="Change-OI PCR" value={features.changeOIPcr.toFixed(3)} />
                <ValueRow label="Total call OI" value={formatFeatureNumber(features.totalCallOI)} />
                <ValueRow label="Total put OI" value={formatFeatureNumber(features.totalPutOI)} />
                <ValueRow label="Highest call OI" value={features.highestCallOIStrike ?? '—'} />
                <ValueRow label="Highest put OI" value={features.highestPutOIStrike ?? '—'} />
                <ValueRow label="Highest call change OI" value={features.highestCallChangeOIStrike ?? '—'} />
                <ValueRow label="Highest put change OI" value={features.highestPutChangeOIStrike ?? '—'} />
                <ValueRow label="Support candidate" value={formatCandidate(features.supportLabel, features.supportLevel)} />
                <ValueRow label="Distance from support" value={formatPrice(features.distanceFromSupport)} />
                <ValueRow label="Resistance candidate" value={formatCandidate(features.resistanceLabel, features.resistanceLevel)} />
                <ValueRow label="Distance from resistance" value={formatPrice(features.distanceFromResistance)} />
                <ValueRow label="Call OI concentration" value={formatConcentration(features.callOiConcentration)} />
                <ValueRow label="Put OI concentration" value={formatConcentration(features.putOiConcentration)} />
              </ContextTable>
            ) : (
              <p className="api-state">Waiting for the first normalized option-chain snapshot…</p>
            )}
            {snapshot?.options.error && <p className="api-state error">{snapshot.options.error}</p>}
          </section>
          </div>
        </>
      )}
    </section>
  );
}

function ContextTable({ headers, children }: { headers: string[]; children: ReactNode }) {
  return (
    <div className="context-table-scroll">
      <table className="context-table">
        <thead>
          <tr>{headers.map((header) => <th key={header} scope="col">{header}</th>)}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

function ValueRow({ label, value }: { label: string; value: ReactNode }) {
  return <tr><th scope="row">{label}</th><td>{value}</td></tr>;
}

function MomentumRow({
  label,
  value,
  status,
}: {
  label: string;
  value: number | null | undefined;
  status: MarketFeatureSnapshot['niftyReturn1mStatus'] | undefined;
}) {
  return <tr><th scope="row">{label}</th><td>{formatMomentumPercent(value, status)}</td><td><MetricStatus status={status} /></td></tr>;
}

function MetricStatus({ status }: { status: MarketFeatureSnapshot['niftyReturn1mStatus'] | undefined }) {
  const value = status ?? 'COLLECTING';
  return <span className={`context-status ${value.toLowerCase()}`}>{value}</span>;
}

function useNseMarketSnapshot() {
  const [snapshot, setSnapshot] = useState<MarketDataBundle | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let active = true;
    let inFlight = false;
    const load = async () => {
      if (inFlight) return;
      inFlight = true;
      setLoading(true);
      try {
        const raw = await nseMarketDataProvider.refreshAll();
        if (active) {
          setSnapshot(raw);
        }
      } finally {
        inFlight = false;
        if (active) setLoading(false);
      }
    };

    void load();
    const timer = window.setInterval(() => void load(), 30_000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [refreshKey]);

  return {
    snapshot,
    loading,
    refresh: () => setRefreshKey(value => value + 1),
  };
}

function formatFeaturePercent(value: number | null | undefined): string {
  return value == null ? 'Collecting' : `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function formatMomentumPercent(
  value: number | null | undefined,
  status: 'COLLECTING' | 'AVAILABLE' | 'STALE' | 'UNAVAILABLE' | undefined
): string {
  if (status === 'UNAVAILABLE') return 'Unavailable';
  if (status === 'STALE') return 'Stale';
  if (status !== 'AVAILABLE' || value == null) return 'Collecting';
  return formatFeaturePercent(value);
}

function formatPercentagePoints(value: number | null | undefined): string {
  return value == null ? '—' : `${value >= 0 ? '+' : ''}${value.toFixed(2)} pp`;
}

function formatMetricPercentagePoints(
  value: number | null | undefined,
  status: 'COLLECTING' | 'AVAILABLE' | 'STALE' | 'UNAVAILABLE' | undefined
): string {
  if (status === 'UNAVAILABLE') return 'Unavailable';
  if (status === 'STALE') return 'Stale';
  if (status !== 'AVAILABLE') return 'Collecting';
  return formatPercentagePoints(value);
}

function formatSignedNumber(value: number | null | undefined): string {
  return value == null ? '—' : `${value >= 0 ? '+' : ''}${new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(value)}`;
}

function formatPrice(value: number | null | undefined): string {
  return value == null ? '—' : money(value);
}

function formatFeatureNumber(value: number | null | undefined): string {
  return value == null ? '—' : new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 2 }).format(value);
}

function formatActualInteger(value: number | null | undefined): string {
  return value == null ? '—' : new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 }).format(value);
}

function formatConcentration(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

function formatCandidate(
  label: MarketFeatureSnapshot['supportLabel'] | MarketFeatureSnapshot['resistanceLabel'],
  value: number | null
): string {
  return value == null ? '—' : `${label} · ${value}`;
}

function Card({
  label,
  value,
  hint,
  good,
}: {
  label: string;
  value: string;
  hint: string;
  good?: boolean;
}) {
  return (
    <div className="card">
      <span>{label}</span>
      <b>{value}</b>
      <small className={good ? 'good' : ''}>{hint}</small>
      <i />
    </div>
  );
}
