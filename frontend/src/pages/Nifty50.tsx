import { useEffect, useRef, useState } from 'react';
import { NIFTY50_MEMBERS, NIFTY50_SECTORS, NIFTY50_WEIGHT_AS_OF, NIFTY50_WEIGHTS } from '../data/nifty50';
import {
  computeWeightedAttribution,
  computeNiftyPressure,
  computeMarketDirection,
  nseMarketDataProvider,
  type ConstituentItem,
  type MarketDataBundle,
  type MarketDirectionAnalysis,
  type NiftyPressureResult,
  type NiftyPressureSample,
  type NiftyMarketData,
  type ProviderState,
} from '../services/nseMarketData';
import './Nifty50.css';

type MemberView = typeof NIFTY50_MEMBERS[number] & {
  quote: ConstituentItem | null;
  contributionPercent: number | null;
  weight: number;
  sector: string;
};

const currency = (value: number | null | undefined) => value == null
  ? '—'
  : new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value);

const signedPercent = (value: number | null | undefined) => value == null
  ? '—'
  : `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;

const signedPoints = (value: number | null | undefined) => value == null
  ? '—'
  : `${value > 0 ? '+' : ''}${value.toFixed(3)} pp`;

const marketDateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit',
});

const signedPressure = (value: number | null | undefined) => value == null
  ? '—'
  : `${value > 0 ? '+' : ''}${value.toFixed(3)} pp`;

const readableState = (value: string) => value.replaceAll('_', ' ');

function moveClass(value: number | null | undefined): string {
  if (value == null || value === 0) return 'flat';
  return value > 0 ? 'up' : 'down';
}

function directionLabel(value: number | null | undefined): string {
  if (value == null) return 'Quote unavailable';
  if (value > 0) return 'Bullish · advancing';
  if (value < 0) return 'Bearish · declining';
  return 'Neutral · unchanged';
}

export function Nifty50() {
  const [quotes, setQuotes] = useState<ProviderState<ConstituentItem[]>>({
    status: 'loading', lastUpdated: null, latency: null, error: null, data: [],
  });
  const [selectedNseQuote, setSelectedNseQuote] = useState<ProviderState<ConstituentItem> | null>(null);
  const [marketBundle, setMarketBundle] = useState<MarketDataBundle | null>(null);
  const [nifty, setNifty] = useState<ProviderState<NiftyMarketData> | null>(null);
  const [selectedSymbol, setSelectedSymbol] = useState('HDFCBANK');
  const [loading, setLoading] = useState(true);
  const [refreshCount, setRefreshCount] = useState(0);
  const [pressure, setPressure] = useState<NiftyPressureResult | null>(null);
  const [pressureSampleCount, setPressureSampleCount] = useState(0);
  const pressureHistory = useRef<NiftyPressureSample[]>([]);

  useEffect(() => {
    let active = true;
    let inFlight = false;
    const load = async () => {
      if (inFlight) return;
      inFlight = true;
      setLoading(true);
      const nextBundle = await nseMarketDataProvider.refreshAll();
      const constituentResult = nextBundle.constituents;
      const niftyResult = nextBundle.nifty;
      if (active) {
        setMarketBundle(nextBundle);
        setQuotes(constituentResult);
        setNifty(niftyResult);
        if (constituentResult.status === 'live' && constituentResult.lastUpdated != null) {
          const sample: NiftyPressureSample = {
            timestamp: constituentResult.lastUpdated,
            niftyPrice: niftyResult.status === 'live' ? niftyResult.data?.ltp ?? null : null,
            constituents: (constituentResult.data ?? []).map(item => ({
              symbol: item.symbol,
              ltp: item.ltp,
              open: item.open,
              weight: NIFTY50_WEIGHTS[item.symbol] ?? item.weight,
            })),
          };
          const previous = pressureHistory.current.at(-1);
          if (!previous || marketDateFormatter.format(previous.timestamp) !== marketDateFormatter.format(sample.timestamp)) {
            pressureHistory.current = [sample];
          } else if (sample.timestamp > previous.timestamp) {
            pressureHistory.current = [...pressureHistory.current, sample].slice(-150);
          }
          setPressure(computeNiftyPressure(pressureHistory.current));
          setPressureSampleCount(pressureHistory.current.length);
        }
        setLoading(false);
      }
      inFlight = false;
    };

    void load();
    const timer = window.setInterval(() => void load(), 30_000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [refreshCount]);

  useEffect(() => {
    let active = true;
    void nseMarketDataProvider.getEquityQuote(selectedSymbol).then(result => {
      if (active) setSelectedNseQuote(result);
    });
    return () => { active = false; };
  }, [selectedSymbol]);

  const quoteBySymbol = new Map((quotes.data ?? []).map(item => [item.symbol.toUpperCase(), item]));
  const members: MemberView[] = NIFTY50_MEMBERS.map(member => ({
    ...member,
    quote: quoteBySymbol.get(member.symbol.toUpperCase()) ?? null,
    contributionPercent: null,
    weight: NIFTY50_WEIGHTS[member.symbol],
    sector: NIFTY50_SECTORS[member.symbol],
  }));
  const attribution = computeWeightedAttribution(
    members.map(member => member.quote ?? {
      symbol: member.symbol, ltp: null, previousClose: null, change: null,
      changePercent: null, volume: null, weight: member.weight, sector: member.sector,
    }).map((item, index) => ({
      ...item,
      weight: members[index].weight,
      sector: members[index].sector,
    })),
    NIFTY50_MEMBERS.length,
  );
  const attributionBySymbol = new Map(attribution.items.map(item => [item.symbol, item.contributionPercent]));
  for (const member of members) {
    member.contributionPercent = attributionBySymbol.get(member.symbol.toUpperCase()) ?? null;
  }
  const sortedMembers = [...members].sort((left, right) =>
    (right.quote?.changePercent ?? Number.NEGATIVE_INFINITY)
      - (left.quote?.changePercent ?? Number.NEGATIVE_INFINITY));
  const selected = members.find(member => member.symbol === selectedSymbol) ?? members[0];
  const selectedQuote = selectedNseQuote?.data?.symbol === selected?.symbol
    ? selectedNseQuote.data
    : selected?.quote;
  const advances = members.filter(member => (member.quote?.changePercent ?? 0) > 0).length;
  const declines = members.filter(member => (member.quote?.changePercent ?? 0) < 0).length;
  const unchanged = members.filter(member => member.quote?.changePercent === 0).length;
  const quoted = members.filter(member => member.quote?.changePercent != null).length;
  const indexMove = nifty?.data?.changePercent;
  const estimatedMove = attribution.weightedReturnPercent;
  const moveDifference = indexMove != null && estimatedMove != null ? estimatedMove - indexMove : null;
  const topPositive = [...attribution.items]
    .filter(item => item.contributionPercent != null && item.contributionPercent > 0)
    .sort((left, right) => (right.contributionPercent ?? 0) - (left.contributionPercent ?? 0))
    .slice(0, 5);
  const topNegative = [...attribution.items]
    .filter(item => item.contributionPercent != null && item.contributionPercent < 0)
    .sort((left, right) => (left.contributionPercent ?? 0) - (right.contributionPercent ?? 0))
    .slice(0, 5);

  return (
    <main className="nifty50-page">
      <div className="heading nifty50-heading">
        <div>
          <small>NIFTY 50 · NSE</small>
          <h1>Constituent map</h1>
          <p>Daily constituent moves and their free-float-weighted contribution to the index.</p>
        </div>
        <button className="outline" onClick={() => setRefreshCount(count => count + 1)} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh NSE'}
        </button>
      </div>

      {quotes.error && <p className={`api-state ${quotes.status === 'error' ? 'error' : ''}`}>{quotes.error}</p>}

      <section className="nifty50-summary" aria-label="NIFTY 50 summary">
        <article className="nifty50-summary-item index-level">
          <span>NIFTY 50</span>
          <strong>{currency(nifty?.data?.ltp)}</strong>
          <small>{nifty?.data?.timestamp ?? 'Waiting for NSE index quote'}</small>
        </article>
        <article className={`nifty50-summary-item ${moveClass(indexMove)}`}>
          <span>NSE index move · 1D</span>
          <strong>{signedPercent(indexMove)}</strong>
          <small>Reported by NSE</small>
        </article>
        <article className={`nifty50-summary-item ${moveClass(estimatedMove)}`}>
          <span>Weighted constituent move · 1D</span>
          <strong>{attribution.available ? signedPercent(estimatedMove) : '—'}</strong>
          <small>{attribution.available
            ? `Weight sum ${attribution.totalWeightPercent?.toFixed(2)}%`
            : `${attribution.coveredMembers}/50 quotes and weights complete`}</small>
        </article>
        <article className="nifty50-summary-item breadth-summary">
          <span>Market breadth</span>
          <strong><i>{advances} up</i><i>{declines} down</i></strong>
          <small>{unchanged} unchanged · {quoted}/50 quoted</small>
        </article>
      </section>

      <NiftyPressurePanel
        pressure={pressure}
        marketDirection={computeMarketDirection(pressure, marketBundle)}
        sampleCount={pressureSampleCount}
        status={quotes.status}
      />

      {attribution.available && moveDifference != null && (
        <p className="nifty50-attribution-note">
          Constituent-weight estimate differs from the reported index by {signedPoints(moveDifference)}.
          Current weights and daily returns provide an attribution estimate, not an exact index rebalance calculation.
        </p>
      )}
      {!attribution.available && (
        <p className="nifty50-attribution-note">
          Saved constituent weights are dated {NIFTY50_WEIGHT_AS_OF}. Aggregate index attribution requires daily returns
          for all 50 stocks; partial contribution and breadth figures below reflect the quoted coverage only.
        </p>
      )}

      <section className="nifty50-pressure" aria-label="Weighted constituent pressure">
        <div className="nifty50-pressure-heading">
          <div><small>WEIGHTED MARKET PRESSURE</small><h2>Constituent attribution</h2></div>
          <span>WEIGHTS AS OF {NIFTY50_WEIGHT_AS_OF} · {attribution.coveredMembers}/50 DAILY RETURNS</span>
        </div>
        <div className="nifty50-pressure-metrics">
          <div><span>Weighted return</span><strong className={moveClass(estimatedMove)}>{attribution.available ? signedPercent(estimatedMove) : '—'}</strong></div>
          <div><span>Positive contribution</span><strong className="up">{signedPoints(attribution.positiveWeightedContribution)}</strong></div>
          <div><span>Negative contribution</span><strong className="down">{signedPoints(attribution.negativeWeightedContribution)}</strong></div>
          <div><span>Advance weight</span><strong>{attribution.advanceWeightPercent.toFixed(2)}%</strong></div>
          <div><span>Decline weight</span><strong>{attribution.declineWeightPercent.toFixed(2)}%</strong></div>
          <div><span>Weighted breadth</span><strong className={moveClass(attribution.weightedBreadthPercent)}>{signedPoints(attribution.weightedBreadthPercent)}</strong></div>
        </div>
        <div className="nifty50-pressure-breakdown">
          <div className="nifty50-contributors">
            <h3>Top positive contributors</h3>
            {topPositive.map(item => <div key={item.symbol}><span>{item.symbol}</span><strong className="up">{signedPoints(item.contributionPercent)}</strong></div>)}
            {!topPositive.length && <p>No positive contributions in quoted rows.</p>}
            <h3>Top negative contributors</h3>
            {topNegative.map(item => <div key={item.symbol}><span>{item.symbol}</span><strong className="down">{signedPoints(item.contributionPercent)}</strong></div>)}
            {!topNegative.length && <p>No negative contributions in quoted rows.</p>}
          </div>
          <div className="nifty50-sector-returns">
            <h3>Sector weighted returns</h3>
            {attribution.sectors.map(sector => (
              <div key={sector.sector}>
                <span>{sector.sector}<small>{sector.coveredMembers}/{sector.memberCount} · {sector.coveredWeightPercent.toFixed(2)}% covered</small></span>
                <strong className={moveClass(sector.weightedReturnPercent)}>{signedPercent(sector.weightedReturnPercent)}</strong>
              </div>
            ))}
          </div>
        </div>
      </section>

      <div className="nifty50-workspace">
        <section className="nifty50-heatmap-section">
          <div className="nifty50-section-heading">
            <div><small>CONSTITUENTS</small><h2>Daily performance</h2></div>
            <span>{quotes.status === 'live' ? 'LIVE NSE DATA' : quotes.status.toUpperCase()}</span>
          </div>
          <div className="nifty50-heatmap">
            {sortedMembers.map(member => {
              const changePercent = member.quote?.changePercent;
              const intensity = changePercent == null ? 0 : Math.min(Math.abs(changePercent) / 5, 1);
              return (
                <button
                  type="button"
                  key={member.symbol}
                  className={`nifty50-tile ${moveClass(changePercent)} ${selectedSymbol === member.symbol ? 'selected' : ''}`}
                  style={{ '--move-intensity': intensity } as React.CSSProperties}
                  onClick={() => setSelectedSymbol(member.symbol)}
                  aria-pressed={selectedSymbol === member.symbol}
                  title={`${member.companyName} · ${member.industry}`}
                >
                  <strong>{member.symbol}</strong>
                  <span>{currency(member.quote?.ltp)}</span>
                  <b>{signedPercent(changePercent)}</b>
                  <small>Weight {member.weight.toFixed(2)}%</small>
                </button>
              );
            })}
          </div>
        </section>

        <aside className="nifty50-detail">
          {selected && (
            <>
              <div className="nifty50-detail-top">
                <small>SELECTED CONSTITUENT</small>
                <span className={`nifty50-direction ${moveClass(selected.quote?.changePercent)}`}>
                  {directionLabel(selected.quote?.changePercent)}
                </span>
              </div>
              <h2>{selected.symbol}</h2>
              <p>{selected.companyName}</p>
              <dl>
                <div><dt>Last traded price</dt><dd>{currency(selectedQuote?.ltp)}</dd></div>
                <div><dt>Daily return</dt><dd className={moveClass(selectedQuote?.changePercent)}>{signedPercent(selectedQuote?.changePercent)}</dd></div>
                <div><dt>Index weight</dt><dd>{selected.weight.toFixed(2)}%</dd></div>
                <div><dt>NIFTY contribution</dt><dd>{signedPoints(selected.contributionPercent)}</dd></div>
                <div><dt>Sector</dt><dd>{selected.sector}</dd></div>
                <div><dt>Industry</dt><dd>{selected.industry}</dd></div>
                <div><dt>ISIN</dt><dd>{selected.isin}</dd></div>
              </dl>
              <div className="nifty50-detail-foot">
                <span>Series {selected.series}</span>
                <span>{quotes.lastUpdated ? new Date(quotes.lastUpdated).toLocaleTimeString('en-IN') : 'Waiting for quote'}</span>
              </div>
            </>
          )}
        </aside>
      </div>
    </main>
  );
}

function NiftyPressurePanel({
  pressure,
  marketDirection,
  sampleCount,
  status,
}: {
  pressure: NiftyPressureResult | null;
  marketDirection: MarketDirectionAnalysis;
  sampleCount: number;
  status: string;
}) {
  const windows = [1, 5, 10, 15, 30] as const;
  const range = pressure?.expected15mRange;
  const directionTone = marketDirection.direction === 'UP' ? 'up' : marketDirection.direction === 'DOWN' ? 'down' : '';
  const confirmationTone = (confirmation: MarketDirectionAnalysis['futuresConfirmation']) =>
    confirmation === 'CONFIRMS' ? 'up' : confirmation === 'CONTRADICTS' ? 'down' : '';
  const directionScore = marketDirection.score == null
    ? 'Collecting'
    : `${marketDirection.score > 0 ? '+' : ''}${marketDirection.score.toFixed(0)} / 100`;

  return (
    <section className="nifty50-engine" aria-label="NIFTY intraday pressure engine">
      <div className="nifty50-engine-heading">
        <div><small>DETERMINISTIC · INTRADAY</small><h2>Live pressure state</h2></div>
        <span>{status === 'live' ? `${sampleCount} browser-session samples` : status.toUpperCase()}</span>
      </div>
      <div className="nifty50-engine-summary">
        <div><span>Current state</span><strong className={directionTone}>{readableState(marketDirection.state)}</strong><small>{marketDirection.direction} · rule-based evidence score, not probability</small></div>
        <div><span>Up-side pressure</span><strong className="up">{pressure ? signedPressure(pressure.session.upContributionPercentPoints) : '—'}</strong><small>{pressure ? `${pressure.session.coveredWeightPercent.toFixed(1)}% weight covered · ${pressure.upSharePercent == null ? 'share unavailable' : `${pressure.upSharePercent.toFixed(1)}% gross pressure share, not probability`}` : 'Waiting for constituent quotes'}</small></div>
        <div><span>Down-side pressure</span><strong className="down">{pressure ? signedPressure(-pressure.session.downContributionPercentPoints) : '—'}</strong><small>{pressure ? `${pressure.session.coveredWeightPercent.toFixed(1)}% weight covered · ${pressure.downSharePercent == null ? 'share unavailable' : `${pressure.downSharePercent.toFixed(1)}% gross pressure share, not probability`}` : 'Waiting for constituent quotes'}</small></div>
        <div><span>Observed 15m range · 1σ</span><strong>{range ? `${currency(range.low)} – ${currency(range.high)}` : 'Collecting'}</strong><small>{range ? `±${range.sigmaPercent.toFixed(3)}% from recent NIFTY volatility` : 'Requires at least 5 minutes of fresh NIFTY samples'}</small></div>
      </div>
      <div className="nifty50-horizons" aria-label="Weighted pressure by horizon">
        {windows.map(minutes => {
          const value = pressure?.horizons[minutes];
          return <div key={minutes}>
            <span>{minutes}m</span>
            <strong className={moveClass(value?.netPressurePercentPoints)}>{value ? signedPressure(value.netPressurePercentPoints) : 'Collecting'}</strong>
            <small>{value ? `↑ ${signedPressure(value.upContributionPercentPoints)} · ↓ ${signedPressure(-value.downContributionPercentPoints)} · ${value.coveredWeightPercent.toFixed(1)}% weight` : 'More samples needed'}</small>
          </div>;
        })}
      </div>
      <div className="nifty50-linked-context">
        <div className="nifty50-linked-heading"><small>INTERLINKED MARKET STATE</small><span>Direction first · derivatives as confirmation and structure</span></div>
        <div className="nifty50-linked-metrics">
          <div><span>Market direction score</span><strong className={directionTone}>{directionScore}</strong><small>{marketDirection.strength} · positive favors up, negative favors down</small></div>
          <div><span>Spot 5m</span><strong className={moveClass(marketDirection.spotReturn5m)}>{signedPercent(marketDirection.spotReturn5m)}</strong><small className={confirmationTone(marketDirection.spotConfirmation)}>Spot {readableState(marketDirection.spotConfirmation)}</small></div>
          <div><span>Futures 5m</span><strong className={moveClass(marketDirection.futuresReturn5m)}>{signedPercent(marketDirection.futuresReturn5m)}</strong><small className={confirmationTone(marketDirection.futuresConfirmation)}>Futures {readableState(marketDirection.futuresConfirmation)}</small></div>
          <div><span>Options structure</span><strong>{marketDirection.nearestStructure === 'UNAVAILABLE' ? 'Unavailable' : `${marketDirection.nearestStructure} · ${currency(marketDirection.nearestStructure === 'SUPPORT' ? marketDirection.supportLevel : marketDirection.resistanceLevel)}`}</strong><small>Support {currency(marketDirection.supportLevel)} · Resistance {currency(marketDirection.resistanceLevel)}</small></div>
          <div><span>PCR · change-OI PCR</span><strong>{marketDirection.pcr == null ? '—' : marketDirection.pcr.toFixed(3)} · {marketDirection.changeOIPcr == null ? '—' : marketDirection.changeOIPcr.toFixed(3)}</strong><small>Descriptive positioning context; not a direction vote</small></div>
        </div>
      </div>
      <p className="nifty50-engine-note">
        Score uses 50% constituent pressure, 25% NIFTY spot momentum, and 25% futures momentum when available; missing feeds are omitted. PCR/OI levels never set direction. Acceleration compares consecutive 5m windows ({signedPressure(pressure?.accelerationPercentPoints)}); aligned windows: {pressure?.persistenceHorizonCount ?? '—'}/5. Scores are not probabilities or contract recommendations. Samples reset on the next NSE market date and are collected while this page is open.
      </p>
    </section>
  );
}
