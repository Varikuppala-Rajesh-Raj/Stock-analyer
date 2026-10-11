import { API_BASE_URL } from '../config/api';

export type OvernightRetrieveResult = {
  instrument: string;
  instrumentKey: string | null;
  provider: string;
  source: 'DATABASE' | 'UPSTOX' | 'NSE' | 'NONE';
  status: 'FRESH' | 'STALE' | 'DATA_NOT_AVAILABLE' | 'PROVIDER_ERROR';
  lifecycleStatus?: 'LIVE' | 'COLLECTING' | 'COLLECTED' | 'UNAVAILABLE' | 'ERROR' | 'NOT_CONFIGURED' | 'STALE';
  sessionId?: string | null;
  requiredSessionId?: string | null;
  dataTimestamp: string | null;
  retrievedAt: string | null;
  lastPrice: number | null;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number | null;
  netChange: number | null;
  volume: number | null;
  message: string | null;
};

export type OvernightRetrieveResponse = {
  results: OvernightRetrieveResult[];
};

type SavedOvernightQuote = {
  provider?: string;
  instrumentKey: string | null;
  value: number | null;
  previousClose: number | null;
  change: number | null;
  changePct: number | null;
  timestamp: string | null;
  retrievedAt: string | null;
  status: 'FRESH' | 'STALE' | 'UNAVAILABLE';
  lifecycleStatus?: OvernightRetrieveResult['lifecycleStatus'];
  sessionId?: string | null;
  requiredSessionId?: string | null;
  error: string | null;
};

type SavedOvernightSnapshot = {
  giftNifty: SavedOvernightQuote;
  sp500: SavedOvernightQuote;
  usTech100: SavedOvernightQuote;
  dow: SavedOvernightQuote;
  nikkei: SavedOvernightQuote;
  hangSeng: SavedOvernightQuote;
  usdInr: SavedOvernightQuote;
  crudeOil: SavedOvernightQuote;
  gold: SavedOvernightQuote;
  ftse100: SavedOvernightQuote;
  dax: SavedOvernightQuote;
  brent: SavedOvernightQuote;
};

const API_BASE = API_BASE_URL;

export async function loadSavedOvernightQuotes(): Promise<Record<string, OvernightRetrieveResult>> {
  const response = await fetch(`${API_BASE}/overnight/global`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Loading saved overnight market data failed: ${response.status} ${response.statusText}`);
  }

  const payload = await response.json() as SavedOvernightSnapshot;
  const instruments = [
    { snapshotKey: 'giftNifty', instrument: 'GIFT_NIFTY' },
    { snapshotKey: 'sp500', instrument: 'SP500' },
    { snapshotKey: 'usTech100', instrument: 'US_TECH_100' },
    { snapshotKey: 'dow', instrument: 'DOW' },
    { snapshotKey: 'nikkei', instrument: 'NIKKEI' },
    { snapshotKey: 'hangSeng', instrument: 'HANG_SENG' },
    { snapshotKey: 'usdInr', instrument: 'USD_INR' },
    { snapshotKey: 'crudeOil', instrument: 'CRUDE_OIL' },
    { snapshotKey: 'gold', instrument: 'GOLD' },
    { snapshotKey: 'ftse100', instrument: 'FTSE_100' },
    { snapshotKey: 'dax', instrument: 'DAX' },
    { snapshotKey: 'brent', instrument: 'BRENT' },
  ] as const;
  const entries = instruments.map(({ snapshotKey, instrument }) => {
    const quote = payload[snapshotKey];
    if (!quote || !('value' in quote)) {
      throw new Error(`Saved overnight market data response is missing ${snapshotKey}.`);
    }
    const unavailable = quote.status === 'UNAVAILABLE';
    const lifecycleStatus = quote.lifecycleStatus;
    const result: OvernightRetrieveResult = {
      instrument,
      instrumentKey: quote.instrumentKey,
      provider: quote.provider ?? 'UPSTOX',
      source: unavailable ? 'NONE' : 'DATABASE',
      status: lifecycleStatus === 'ERROR' ? 'PROVIDER_ERROR'
        : unavailable || lifecycleStatus === 'UNAVAILABLE' || lifecycleStatus === 'NOT_CONFIGURED'
          ? 'DATA_NOT_AVAILABLE'
          : lifecycleStatus === 'LIVE' || lifecycleStatus === 'COLLECTED'
            || (!lifecycleStatus && quote.status === 'FRESH')
              ? 'FRESH' : 'STALE',
      lifecycleStatus,
      sessionId: quote.sessionId,
      requiredSessionId: quote.requiredSessionId,
      dataTimestamp: quote.timestamp,
      retrievedAt: quote.retrievedAt,
      lastPrice: quote.value,
      open: null,
      high: null,
      low: null,
      close: quote.previousClose,
      netChange: quote.change,
      volume: null,
      message: quote.error,
    };
    return [instrument, result];
  });
  return Object.fromEntries(entries);
}

export async function retrieveOvernightInstrument(instrument: string): Promise<OvernightRetrieveResult> {
  const response = await fetch(`${API_BASE}/overnight/retrieve`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ instrument }),
  });
  if (!response.ok) {
    throw new Error(`Overnight market retrieval failed: ${response.status} ${response.statusText}`);
  }

  const payload = await response.json() as OvernightRetrieveResponse;
  const result = payload.results?.[0];
  if (!result) {
    throw new Error('Overnight market retrieval returned no instrument result.');
  }
  return result;
}

export async function refreshOvernightQuotes(force = false): Promise<OvernightRetrieveResult[]> {
  const endpoint = force ? 'refresh' : 'retrieve';
  const response = await fetch(`${API_BASE}/overnight/${endpoint}`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: '{}',
  });
  if (!response.ok) {
    throw new Error(`Refreshing overnight market data failed: ${response.status} ${response.statusText}`);
  }

  const payload = await response.json() as OvernightRetrieveResponse;
  if (!Array.isArray(payload.results) || payload.results.length === 0) {
    throw new Error('Overnight market refresh returned no instrument results.');
  }
  return payload.results;
}
