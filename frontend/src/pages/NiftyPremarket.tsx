import { useEffect, useState } from 'react';
import {
  loadSavedOvernightQuotes,
  refreshOvernightQuotes,
  retrieveOvernightInstrument,
  type OvernightRetrieveResult,
} from '../services/overnightMarketData';
import {
  nseMarketDataProvider,
  type MarketDataBundle,
  type ProviderStatus,
} from '../services/nseMarketData';
import { calculateMarketBias } from '../services/marketBias';
import './NiftyPremarket.css';

type MarketInput = {
  name: string;
  symbol: string;
  purpose: string;
  update: string;
  priority: 'Core' | 'Useful';
  source: string;
  endpoint: string;
  sourceStatus: 'In app' | 'Candidate' | 'Not connected';
};

const inputGroups: { title: string; description: string; inputs: MarketInput[] }[] = [
  {
    title: 'Overnight global cues',
    description: 'Set the opening backdrop; compare moves rather than treating any single market as a prediction.',
    inputs: [
      { name: 'GIFT NIFTY', symbol: 'NIFTY futures indication', purpose: 'Implied gap versus the previous NIFTY close.', update: 'Overnight / pre-open', priority: 'Core', source: 'Spring Boot persists the configured Upstox quote before returning it', endpoint: 'POST /api/overnight/retrieve · GIFT_NIFTY', sourceStatus: 'In app' },
      { name: 'US markets', symbol: 'S&P 500 · Nasdaq · Dow', purpose: 'Risk appetite and the previous US-session direction.', update: 'Previous close + futures', priority: 'Core', source: 'Spring Boot retrieves and persists each configured Upstox quote independently', endpoint: 'POST /api/overnight/retrieve · SP500 / US_TECH_100 / DOW', sourceStatus: 'In app' },
      { name: 'Asian markets', symbol: 'Nikkei · Hang Seng · relevant peers', purpose: 'Regional sentiment during the Indian pre-open.', update: 'Latest completed session', priority: 'Useful', source: 'Spring Boot retrieves and persists each configured Upstox quote independently', endpoint: 'POST /api/overnight/retrieve · NIKKEI / HANG_SENG', sourceStatus: 'In app' },
      { name: 'European markets', symbol: 'FTSE 100 · DAX', purpose: 'Latest European session risk sentiment; complementary context, not a standalone signal.', update: 'Latest available session', priority: 'Useful', source: 'Quotes resolved from the local Upstox global instrument master and persisted with overnight data.', endpoint: 'POST /api/overnight/retrieve · FTSE_100 / DAX', sourceStatus: 'In app' },
      { name: 'USD / INR', symbol: 'NSE/BSE USDINR futures (front-month)', purpose: 'Currency pressure proxy for foreign flows and import costs; this is a futures quote, not spot FX.', update: 'Latest available session', priority: 'Core', source: 'Spring Boot resolves active NSE contracts first, then BSE alternatives, from Upstox’s instrument master and persists a usable quote', endpoint: 'POST /api/overnight/retrieve · USD_INR', sourceStatus: 'In app' },
      { name: 'Brent crude', symbol: 'Global Brent oil benchmark', purpose: 'Global energy-cost context relevant to India’s import bill and inflation.', update: 'Latest available global quote', priority: 'Core', source: 'Quote resolved from the local Upstox global instrument master and persisted with overnight data.', endpoint: 'POST /api/overnight/retrieve · BRENT', sourceStatus: 'In app' },
      { name: 'Crude oil', symbol: 'NSE CRUDEOIL front-month futures', purpose: 'Domestic energy-cost and inflation sensitivity proxy.', update: 'Overnight / latest quote', priority: 'Useful', source: 'Spring Boot retrieves and persists the active NSE CRUDEOIL futures quote.', endpoint: 'POST /api/overnight/retrieve · CRUDE_OIL', sourceStatus: 'In app' },
      { name: 'Gold', symbol: 'NSE GOLD front-month futures', purpose: 'Indian gold futures proxy for defensive positioning and risk context; not spot gold.', update: 'Latest available session', priority: 'Useful', source: 'Active NSE GOLD futures contract resolved from the Upstox instrument master and persisted with overnight quotes.', endpoint: 'POST /api/overnight/retrieve · GOLD', sourceStatus: 'In app' },
      { name: 'US volatility and yields', symbol: 'VIX · US 10-year yield', purpose: 'Risk aversion and global rate expectations.', update: 'Previous close + latest', priority: 'Useful', source: 'Not included in the configured Upstox overnight instrument set yet', endpoint: 'No VIX / US 10-year instruments in POST /api/overnight/retrieve', sourceStatus: 'Not connected' },
    ],
  },
  {
    title: 'Domestic positioning and breadth',
    description: 'Check whether positioning and participation support or contradict the overnight cues.',
    inputs: [
      { name: 'FII / DII activity', symbol: 'Cash-market net flow', purpose: 'Recent institutional buying or selling; show reporting date.', update: 'Previous reported session', priority: 'Core', source: 'Spring Boot proxies, validates, and persists the NSE institutional-flow report.', endpoint: 'GET /api/nse/premarket → /api/fiidiiTradeReact', sourceStatus: 'In app' },
      { name: 'Index futures positioning', symbol: 'NIFTY futures OI and change', purpose: 'Price-plus-open-interest context; avoid reading OI change alone.', update: 'Previous session / latest quote', priority: 'Core', source: 'Existing NSE futures proxy; overnight Upstox snapshots provide global quotes, not domestic futures OI.', endpoint: 'GET /api/nse/futures · global snapshots: POST /api/overnight/retrieve', sourceStatus: 'In app' },
      { name: 'India VIX', symbol: 'NSE India VIX', purpose: 'Expected volatility context for sizing and gap risk.', update: 'Latest NSE index timestamp', priority: 'Core', source: 'Spring Boot proxies, validates, and persists the NSE India VIX quote.', endpoint: 'GET /api/nse/premarket → /api/allIndices', sourceStatus: 'In app' },
      { name: 'Market breadth', symbol: 'Advances / declines · NIFTY constituents', purpose: 'Participation behind the index move and sector leadership.', update: 'Timestamp returned with constituents', priority: 'Core', source: 'Existing NSE constituent proxy; breadth can be calculated from member quotes', endpoint: 'GET /api/nse/constituents', sourceStatus: 'In app' },
      { name: 'NIFTY futures basis', symbol: 'Futures minus spot', purpose: 'Read futures premium or discount alongside price direction.', update: 'Matched futures and spot timestamps', priority: 'Useful', source: 'Existing NSE futures route plus NSE market context; compare timestamps before deriving basis', endpoint: 'GET /api/nse/futures + GET /api/nse/market-context', sourceStatus: 'In app' },
    ],
  },
  {
    title: 'Previous NIFTY session and today’s calendar',
    description: 'Anchor the opening plan to actual levels, volatility, and scheduled event risk.',
    inputs: [
      { name: 'Prior-session levels', symbol: 'Open · high · low · close', purpose: 'Reference levels for gap, range, and opening location.', update: 'Previous NSE session', priority: 'Core', source: 'Existing NSE market-context and index-candles proxies', endpoint: 'GET /api/nse/market-context · GET /api/nse/index-candles', sourceStatus: 'In app' },
      { name: 'Trend and volatility', symbol: '5m / 15m candles · ATR · key averages', purpose: 'Recent structure, realized range, and nearby levels.', update: 'Fresh session candles', priority: 'Core', source: 'With MARKET_DATA_PROVIDER=upstox and an imported instrument, the backend proxies Upstox intraday candles.', endpoint: 'GET /api/market/intraday/{symbol}?timeframe=M5', sourceStatus: 'In app' },
      { name: 'Sector / constituent leaders', symbol: 'NIFTY sector contribution and breadth', purpose: 'Identify which large-weight groups are driving the index.', update: 'Timestamp returned with constituents', priority: 'Useful', source: 'Existing NSE constituent proxy plus locally maintained NIFTY weights', endpoint: 'GET /api/nse/constituents', sourceStatus: 'In app' },
      { name: 'Events and expiry', symbol: 'India / global events · expiry calendar', purpose: 'Flag scheduled volatility, holidays, and contract expiry effects.', update: 'Today and next session', priority: 'Core', source: 'Expiry route exists; no event-calendar integration is present', endpoint: 'GET /api/nse/option-expiries · event provider not connected', sourceStatus: 'In app' },
    ],
  },
  {
    title: 'NIFTY options positioning',
    description: 'Use the option chain as positioning context, not as a standalone direction call.',
    inputs: [
      { name: 'Put-call ratio', symbol: 'Total OI and change-OI PCR', purpose: 'Compare aggregate put/call positioning for the selected expiry.', update: 'Same option-chain snapshot', priority: 'Core', source: 'Existing NSE option-chain proxy; PCR must be derived from its returned strikes', endpoint: 'GET /api/nse/option-expiries → GET /api/nse/option-chain?expiry={DD-MMM-YYYY}', sourceStatus: 'In app' },
      { name: 'Open interest and levels', symbol: 'Strike-wise OI · change OI · support/resistance', purpose: 'Locate OI concentrations and changes around spot.', update: 'Same option-chain snapshot', priority: 'Core', source: 'Existing NSE option-chain proxy; levels must be derived from returned strike data', endpoint: 'GET /api/nse/option-chain?expiry={DD-MMM-YYYY}', sourceStatus: 'In app' },
      { name: 'Option IV and liquidity', symbol: 'IV · volume · bid/ask', purpose: 'Check volatility pricing and whether candidate contracts are tradable.', update: 'Live chain quote; verify timestamps', priority: 'Useful', source: 'Existing NSE option-chain proxy; validate schema coverage and freshness before relying on fields', endpoint: 'GET /api/nse/option-chain?expiry={DD-MMM-YYYY}', sourceStatus: 'In app' },
    ],
  },
];

const GIFT_NIFTY_POLL_MS = 60_000;

function formatMarketNumber(value: number | null | undefined, digits = 2): string {
  return value == null || !Number.isFinite(value)
    ? 'Not available'
    : value.toLocaleString('en-IN', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

function statusLabel(status: ProviderStatus | undefined): string {
  switch (status) {
    case 'live': return 'LIVE';
    case 'collected': return 'COLLECTED';
    case 'report-collected': return 'REPORT COLLECTED';
    case 'stale': return 'STALE';
    case 'error': return 'ERROR';
    case 'no-data': return 'NO DATA';
    case 'loading': return 'CHECKING';
    default: return 'NOT CHECKED';
  }
}

function inputProviderStatus(name: string, bundle: MarketDataBundle | null): ProviderStatus | undefined {
  if (!bundle) return undefined;
  switch (name) {
    case 'FII / DII activity': return bundle.premarket?.data?.fiiDiiStatus ?? bundle.premarket?.status;
    case 'Index futures positioning': return bundle.futures.status;
    case 'India VIX': return bundle.indiaVix.status;
    case 'Market breadth': return bundle.marketStatistics.status;
    case 'NIFTY futures basis':
      return bundle.futures.status === 'live' && bundle.nifty.status === 'live'
        ? 'live'
        : bundle.futures.status === 'error' || bundle.nifty.status === 'error'
          ? 'error'
          : bundle.futures.status === 'stale' || bundle.nifty.status === 'stale'
            ? 'stale' : 'no-data';
    case 'Prior-session levels':
    case 'Trend and volatility': return bundle.nifty.status;
    case 'Sector / constituent leaders': return bundle.constituents.status;
    case 'Events and expiry': return bundle.optionExpiries.status;
    case 'Put-call ratio':
    case 'Open interest and levels':
    case 'Option IV and liquidity': return bundle.options.status;
    default: return undefined;
  }
}

function renderInputData(name: string, bundle: MarketDataBundle | null) {
  if (!bundle) return null;
  if (name === 'Index futures positioning' && bundle.futures.data) {
    const futures = bundle.futures.data;
    return <small className="premarket-live-detail">
      {futures.contract} · LTP {formatMarketNumber(futures.ltp)} · OI {formatMarketNumber(futures.openInterest, 0)}
      {' · '}Source time {futures.timestamp}
    </small>;
  }
  if (name === 'Market breadth' && bundle.marketStatistics.data) {
    const breadth = bundle.marketStatistics.data;
    const constituentCount = bundle.constituents.data?.length ?? 0;
    return <small className="premarket-live-detail">
      Advances {formatMarketNumber(breadth.advances, 0)} · Declines {formatMarketNumber(breadth.declines, 0)}
      {' · '}Unchanged {formatMarketNumber(breadth.unchanged, 0)} · {constituentCount} constituents
    </small>;
  }
  if (name === 'NIFTY futures basis' && bundle.futures.data && bundle.nifty.data) {
    return <small className="premarket-live-detail">
      Basis {formatMarketNumber(bundle.futures.data.ltp - bundle.nifty.data.ltp)} points
      {' · '}Spot {formatMarketNumber(bundle.nifty.data.ltp)} · Futures {formatMarketNumber(bundle.futures.data.ltp)}
    </small>;
  }
  if (name === 'Prior-session levels' && bundle.nifty.data) {
    const nifty = bundle.nifty.data;
    return <small className="premarket-live-detail">
      NIFTY {formatMarketNumber(nifty.ltp)} · Open {formatMarketNumber(nifty.open)}
      {' · '}High {formatMarketNumber(nifty.high)} · Low {formatMarketNumber(nifty.low)}
      {' · '}Previous close {formatMarketNumber(nifty.previousClose)}
    </small>;
  }
  if (name === 'Trend and volatility' && bundle.features) {
    return <small className="premarket-live-detail">
      NIFTY returns: 5m {formatMarketNumber(bundle.features.niftyReturn5m)}% ·
      {' '}15m {formatMarketNumber(bundle.features.niftyReturn15m)}%
    </small>;
  }
  if (name === 'Sector / constituent leaders' && bundle.constituents.data) {
    return <small className="premarket-live-detail">
      {bundle.constituents.data.length} constituents returned
    </small>;
  }
  if (name === 'Events and expiry' && bundle.selectedOptionExpiry) {
    return <small className="premarket-live-detail">Nearest expiry {bundle.selectedOptionExpiry}</small>;
  }
  if (['Put-call ratio', 'Open interest and levels', 'Option IV and liquidity'].includes(name)
      && bundle.options.data?.length) {
    const calls = bundle.options.data.reduce((sum, row) => sum + row.ceOi, 0);
    const puts = bundle.options.data.reduce((sum, row) => sum + row.peOi, 0);
    const pcr = calls > 0 ? puts / calls : null;
    return <small className="premarket-live-detail">
      Expiry {bundle.selectedOptionExpiry ?? 'Not available'} · {bundle.options.data.length} strikes
      {' · '}OI PCR {formatMarketNumber(pcr)}
    </small>;
  }
  return null;
}

const globalQuoteFields: Record<string, Array<{ label: string; instrument: string }>> = {
  'GIFT NIFTY': [{ label: 'GIFT NIFTY', instrument: 'GIFT_NIFTY' }],
  'US markets': [
    { label: 'S&P 500', instrument: 'SP500' },
    { label: 'US Tech 100', instrument: 'US_TECH_100' },
    { label: 'Dow', instrument: 'DOW' },
  ],
  'Asian markets': [
    { label: 'Nikkei 225', instrument: 'NIKKEI' },
    { label: 'Hang Seng', instrument: 'HANG_SENG' },
  ],
  'European markets': [
    { label: 'FTSE 100', instrument: 'FTSE_100' },
    { label: 'DAX', instrument: 'DAX' },
  ],
  'USD / INR': [{ label: 'USD / INR', instrument: 'USD_INR' }],
  'Crude oil': [{ label: 'NSE Crude Oil futures proxy', instrument: 'CRUDE_OIL' }],
  'Brent crude': [{ label: 'Brent crude global benchmark', instrument: 'BRENT' }],
  Gold: [{ label: 'NSE Gold futures proxy', instrument: 'GOLD' }],
};

function formatOvernightTimestamp(timestamp: string | null): string {
  if (timestamp == null) return 'Timestamp unavailable';
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: 'Asia/Kolkata',
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(timestamp));
}

function overnightStatusLabel(status: string | undefined): string {
  if (!status) return 'AWAITING';
  if (status === 'LIVE') return '● LIVE';
  if (status === 'COLLECTED') return '✓ COLLECTED';
  if (status === 'COLLECTING') return '↻ COLLECTING';
  if (status === 'UNAVAILABLE') return '⚠ UNAVAILABLE';
  if (status === 'ERROR') return '✕ ERROR';
  if (status === 'NOT_CONFIGURED') return '— NOT CONFIGURED';
  if (status === 'STALE') return 'STALE / OLD';
  if (status === 'FRESH') return 'LIVE';
  if (status === 'DATA_NOT_AVAILABLE') return '⚠ UNAVAILABLE · NO DATA';
  if (status === 'PROVIDER_ERROR') return '✕ ERROR';
  return status;
}

function OvernightQuoteCard({
  label,
  instrument,
  quote,
  loading,
  onRetrieve,
}: {
  label: string;
  instrument: string;
  quote: OvernightRetrieveResult | undefined;
  loading: boolean;
  onRetrieve: (instrument: string) => void;
}) {
  const lifecycleStatus = quote?.lifecycleStatus;
  const unavailable = lifecycleStatus === 'UNAVAILABLE' || lifecycleStatus === 'ERROR'
    || lifecycleStatus === 'NOT_CONFIGURED'
    || quote?.status === 'DATA_NOT_AVAILABLE' || quote?.status === 'PROVIDER_ERROR';
  return (
    <div className="premarket-gift-quote">
      <div className="premarket-gift-quote-heading">
        <b>{quote?.lastPrice != null
          ? `${label}: ${quote.lastPrice?.toLocaleString('en-IN', { maximumFractionDigits: 2 }) ?? 'DATA NOT AVAILABLE'}`
          : unavailable ? `${label}: DATA NOT AVAILABLE` : label}</b>
        <span className={`premarket-quote-status ${(lifecycleStatus ?? quote?.status ?? 'unavailable').toLowerCase()}`}>
          {overnightStatusLabel(lifecycleStatus ?? quote?.status)}
        </span>
      </div>
      {quote?.netChange != null && (
        <span className={quote.netChange >= 0 ? 'positive' : 'negative'}>
          {quote.netChange >= 0 ? '+' : ''}{quote.netChange.toFixed(2)}
        </span>
      )}
      {quote && <>
        <small>Source: {quote.provider}</small>
        <small>{quote.lifecycleStatus === 'COLLECTED' && quote.sessionId
          ? `Previous completed session · ${quote.sessionId}`
          : quote.dataTimestamp
          ? `Updated ${formatOvernightTimestamp(quote.dataTimestamp)}`
          : quote.retrievedAt
            ? `Fetched ${formatOvernightTimestamp(quote.retrievedAt)}; source timestamp unavailable, freshness cannot be verified.`
            : 'Source timestamp unavailable; freshness cannot be verified.'}</small>
        {(quote.message || unavailable) && <small>{quote.message ?? 'DATA NOT AVAILABLE'}</small>}
      </>}
      <button
        className="premarket-retrieve-button"
        type="button"
        onClick={() => onRetrieve(instrument)}
        disabled={loading}
        aria-label={`Retrieve ${label}`}
      >
        {loading ? 'Retrieving…' : 'Retrieve'}
      </button>
    </div>
  );
}

export function NiftyPremarket() {
  const [retrievedQuotes, setRetrievedQuotes] = useState<Record<string, OvernightRetrieveResult>>({});
  const [savedQuotesError, setSavedQuotesError] = useState<string | null>(null);
  const [loadingInstruments, setLoadingInstruments] = useState<Record<string, boolean>>({});
  const [retrieveErrors, setRetrieveErrors] = useState<Record<string, string>>({});
  const [marketBundle, setMarketBundle] = useState<MarketDataBundle | null>(null);
  const [loadingMarketBundle, setLoadingMarketBundle] = useState(false);
  const [refreshingOvernight, setRefreshingOvernight] = useState(false);
  const [marketBundleError, setMarketBundleError] = useState<string | null>(null);
  const marketBias = calculateMarketBias(marketBundle, retrievedQuotes);
  const refreshMarketBundle = async () => {
    setLoadingMarketBundle(true);
    setMarketBundleError(null);
    try {
      setMarketBundle(await nseMarketDataProvider.refreshAll({ includePremarketFeeds: true }));
    } catch (error) {
      setMarketBundleError(error instanceof Error ? error.message : 'NSE premarket refresh failed.');
    } finally {
      setLoadingMarketBundle(false);
    }
  };

  const applyOvernightResults = (results: OvernightRetrieveResult[]) => {
    setRetrievedQuotes((current) => ({
      ...current,
      ...Object.fromEntries(results.map((result) => [result.instrument, result])),
    }));
  };

  useEffect(() => {
    void refreshMarketBundle();
    void loadSavedOvernightQuotes()
      .then(async (quotes) => {
        setRetrievedQuotes(quotes);
        if (!Object.values(quotes).some((quote) =>
          quote.status === 'STALE' || quote.status === 'DATA_NOT_AVAILABLE' || quote.status === 'PROVIDER_ERROR')) {
          return;
        }
        setRefreshingOvernight(true);
        try {
          applyOvernightResults(await refreshOvernightQuotes());
        } catch (error) {
          setSavedQuotesError(error instanceof Error
            ? error.message : 'Refreshing stale overnight market data failed.');
        } finally {
          setRefreshingOvernight(false);
        }
      })
      .catch((error: unknown) => {
        setSavedQuotesError(error instanceof Error
          ? error.message : 'Loading saved overnight market data failed.');
      });
  }, []);

  const refreshAllMarketData = async () => {
    setRefreshingOvernight(true);
    setSavedQuotesError(null);
    const [overnightResult, nseResult] = await Promise.allSettled([
      refreshOvernightQuotes(true),
      nseMarketDataProvider.refreshAll({ includePremarketFeeds: true }),
    ]);
    if (overnightResult.status === 'fulfilled') {
      applyOvernightResults(overnightResult.value);
    } else {
      setSavedQuotesError(overnightResult.reason instanceof Error
        ? overnightResult.reason.message : 'Refreshing overnight market data failed.');
    }
    if (nseResult.status === 'fulfilled') {
      setMarketBundle(nseResult.value);
      setMarketBundleError(null);
    } else {
      setMarketBundleError(nseResult.reason instanceof Error
        ? nseResult.reason.message : 'NSE premarket refresh failed.');
    }
    setRefreshingOvernight(false);
  };

  const handleRetrieve = async (instrument: string) => {
    setLoadingInstruments((current) => ({ ...current, [instrument]: true }));
    setRetrieveErrors((current) => ({ ...current, [instrument]: '' }));
    try {
      const result = await retrieveOvernightInstrument(instrument);
      setRetrievedQuotes((current) => ({ ...current, [instrument]: result }));
    } catch (error) {
      setRetrieveErrors((current) => ({
        ...current,
        [instrument]: error instanceof Error ? error.message : 'Market data retrieval failed.',
      }));
    } finally {
      setLoadingInstruments((current) => ({ ...current, [instrument]: false }));
    }
  };

  return (
    <main className="premarket-page">
      <div className="heading premarket-heading">
        <div>
          <small>NIFTY SESSION PREPARATION</small>
          <h1>Overnight &amp; pre-market</h1>
          <p>Market signals, latest available quotes, and a balanced view of today’s NIFTY setup.</p>
        </div>
        <div className="premarket-refresh-controls">
          <span className="premarket-status">
            {refreshingOvernight ? 'FETCHING LATEST AVAILABLE QUOTES…' : 'AUTO-REFRESH · WEEKDAYS AT 6:00 PM IST'}
          </span>
          <button
            className="premarket-retrieve-button"
            type="button"
            onClick={() => void refreshAllMarketData()}
            disabled={refreshingOvernight || loadingMarketBundle}
          >
            {refreshingOvernight || loadingMarketBundle ? 'Refreshing…' : 'Refresh all market data'}
          </button>
        </div>
      </div>
      {savedQuotesError && <p className="premarket-feed-error" role="alert">{savedQuotesError}</p>}

      <section className={`market-bias panel ${marketBias.label.toLowerCase().replaceAll(' ', '-')}`}
        aria-labelledby="market-bias-title" data-testid="market-bias">
        <div className="market-bias-heading">
          <div>
            <small>MARKET OUTLOOK · BASED ON AVAILABLE EVIDENCE</small>
            <h2 id="market-bias-title">TODAY’S NIFTY OUTLOOK: {marketBias.outlook}</h2>
            <p>Evidence bias: <b>{marketBias.label}</b>
              {' · '}Confidence: <b>{marketBias.confidence}</b>
              {' · '}Data quality: <b>{marketBias.dataQualityPct}%</b>
            </p>
            <p className="market-bias-guidance">
              This is a market context summary, not a standalone buy or sell instruction.
            </p>
          </div>
          <span className="market-bias-readiness" data-ready={marketBias.tradeReady}>
            {marketBias.tradeReadiness}
          </span>
        </div>
        <div className="market-bias-factor-counts" aria-label="Factor direction counts">
          <span className="bullish">Bullish <b>{marketBias.bullishFactors}</b></span>
          <span className="bearish">Bearish <b>{marketBias.bearishFactors}</b></span>
          <span>Neutral <b>{marketBias.neutralFactors}</b></span>
        </div>
        <details className="market-bias-why">
          <summary>Why?</summary>
          <div className="market-bias-groups">
            {marketBias.groups.map((group) => (
              <section className="market-bias-group" key={group.key}>
                <header>
                  <b>{group.key}</b>
                  <span>{group.direction}{group.score == null ? '' : ` · ${group.score.toFixed(2)}`}</span>
                </header>
                <ul>
                  {group.factors.map((item) => (
                    <li className={`bias-factor ${item.direction.toLowerCase()}`} key={item.name}>
                      <b>{item.direction}</b>
                      <span>{item.name} · {item.detail}</span>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
          <p className="market-bias-caveat">
            A paper-trade eligible bias is only one gate. The automated strategy’s live-data,
            entry, liquidity, sizing, and risk checks remain mandatory.
          </p>
        </details>
      </section>

      <section className="premarket-output panel" aria-labelledby="premarket-output-title">
        <div className="premarket-section-title">
          <div>
            <small>INDIA MARKET SNAPSHOT</small>
            <h2 id="premarket-output-title">India VIX and institutional flows</h2>
          </div>
          <div className="premarket-refresh-controls">
            <span aria-live="polite">
              {loadingMarketBundle ? 'Refreshing NSE feeds…'
                : marketBundle?.premarket?.status
                  ? `${marketBundle.premarket.data
                    && [marketBundle.premarket.data.indiaVixStatus, marketBundle.premarket.data.fiiDiiStatus]
                      .some((status) => status === 'error')
                    && [marketBundle.premarket.data.indiaVixStatus, marketBundle.premarket.data.fiiDiiStatus]
                      .some((status) => status === 'live' || status === 'collected'
                        || status === 'report-collected' || status === 'stale')
                    ? 'PARTIAL'
                    : statusLabel(marketBundle.premarket.status)}`
                  : 'NSE feeds not checked'}
            </span>
            <button
              className="premarket-retrieve-button"
              type="button"
              onClick={() => void refreshMarketBundle()}
              disabled={loadingMarketBundle}
            >
              {loadingMarketBundle ? 'Refreshing…' : 'Refresh live feeds'}
            </button>
          </div>
        </div>
        {marketBundleError && <p className="premarket-feed-error" role="alert">{marketBundleError}</p>}
        <div className="premarket-feed-grid" data-testid="premarket-feeds">
          <article className="premarket-feed-card">
            <div className="premarket-input-head">
              <b>India VIX</b>
              <span className={`premarket-quote-status ${(marketBundle?.premarket?.data?.indiaVixStatus ?? 'no-data').toLowerCase()}`}>
                {statusLabel(marketBundle?.premarket?.data?.indiaVixStatus)}
              </span>
            </div>
            {marketBundle?.premarket?.data?.indiaVix ? <>
              <strong>{formatMarketNumber(marketBundle.premarket.data.indiaVix.ltp)}</strong>
              <span className={marketBundle.premarket.data.indiaVix.change >= 0 ? 'positive' : 'negative'}>
                {marketBundle.premarket.data.indiaVix.change >= 0 ? '+' : ''}
                {formatMarketNumber(marketBundle.premarket.data.indiaVix.change)}
                {' '}({formatMarketNumber(marketBundle.premarket.data.indiaVix.changePercent)}%)
              </span>
              {marketBundle.premarket.data.indiaVix.timestamp
                ? <small>Updated {marketBundle.premarket.data.indiaVix.timestamp}</small>
                : <small>Source timestamp unavailable; freshness cannot be verified.</small>}
              {marketBundle.premarket.data.indiaVixError
                && <small className="premarket-feed-error">{marketBundle.premarket.data.indiaVixError}</small>}
            </> : <small>{marketBundle?.premarket?.data?.indiaVixError
              ?? marketBundle?.premarket?.error
              ?? 'Waiting for a validated NSE response.'}</small>}
          </article>
          <article className="premarket-feed-card">
            <div className="premarket-input-head">
              <b>FII/FPI + DII cash flows</b>
              <span className={`premarket-quote-status ${(marketBundle?.premarket?.data?.fiiDiiStatus ?? marketBundle?.premarket?.status ?? 'no-data').toLowerCase()}`}>
                {statusLabel(marketBundle?.premarket?.data?.fiiDiiStatus ?? marketBundle?.premarket?.status)}
              </span>
            </div>
            {marketBundle?.premarket?.data?.fiiDii.length ? marketBundle.premarket.data.fiiDii.map((flow) => (
              <div className="premarket-flow-row" key={flow.category}>
                <b>{flow.category}</b>
                <span>Net {formatMarketNumber(flow.netValue)} Cr</span>
                <small>Buy {formatMarketNumber(flow.buyValue)} Cr · Sell {formatMarketNumber(flow.sellValue)} Cr</small>
                <small>Report date: {flow.date ?? 'Not supplied'}</small>
              </div>
            )) : <small>{marketBundle?.premarket?.data?.fiiDiiError
              ?? marketBundle?.premarket?.error
              ?? 'Waiting for a validated NSE response.'}</small>}
          </article>
        </div>
      </section>

      {inputGroups.map((group, index) => (
        <details className="premarket-group" key={group.title} open={index === 0}>
          <summary className="premarket-group-summary">
            <div>
              <small>GROUP {String(index + 1).padStart(2, '0')}</small>
              <h2 id={`premarket-group-${index}`}>{group.title}</h2>
              <p>{group.description}</p>
            </div>
            <span>{group.inputs.length} signals</span>
          </summary>
          <div className="premarket-input-grid">
            {group.inputs.map((input) => (
              <article className="premarket-input panel" key={input.name}>
                <div className="premarket-input-head">
                  <span className="premarket-symbol">{input.symbol}</span>
                  <span className={`premarket-priority ${input.priority.toLowerCase()}`}>{input.priority}</span>
                </div>
                <h3>{input.name}</h3>
                <p>{input.purpose}</p>
                {globalQuoteFields[input.name]?.map(({ label, instrument }, quoteIndex) => (
                  <div key={instrument} aria-live={quoteIndex === 0 ? 'polite' : undefined}
                    data-testid={instrument === 'GIFT_NIFTY' ? 'gift-nifty-quote' : undefined}>
                    <OvernightQuoteCard
                      label={label}
                      instrument={instrument}
                      quote={retrievedQuotes[instrument]}
                      loading={Boolean(loadingInstruments[instrument])}
                      onRetrieve={handleRetrieve}
                    />
                    {retrieveErrors[instrument] && <small role="alert">{retrieveErrors[instrument]}</small>}
                  </div>
                ))}
                {renderInputData(input.name, marketBundle)}
                {!globalQuoteFields[input.name] && input.sourceStatus === 'In app' ? (
                  <div className={`premarket-source-state ${(inputProviderStatus(input.name, marketBundle) ?? (loadingMarketBundle ? 'loading' : 'no-data')).replace('no-data', 'unavailable')}`}>
                    {statusLabel(inputProviderStatus(input.name, marketBundle) ?? (loadingMarketBundle ? 'loading' : 'no-data'))}
                  </div>
                ) : !globalQuoteFields[input.name] ? (
                  <div className="premarket-source-state not-connected">Data not configured yet</div>
                ) : null}
              </article>
            ))}
          </div>
        </details>
      ))}
    </main>
  );
}
