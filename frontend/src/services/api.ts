
import { API_BASE_URL } from '../config/api';

const API_BASE = API_BASE_URL;
const ML_BASE = import.meta.env.VITE_ML_URL ?? 'http://localhost:5000';

// ============================================================
// Types
// ============================================================

export type Quote = { 
  symbol: string; 
  price: number; 
  open: number; 
  high: number; 
  low: number; 
  previousClose: number; 
  change: number; 
  changePercent: number; 
  volume: number; 
  timestamp: string; 
};

export type Instrument = {
  instrumentKey: string;
  symbol: string;
  companyName: string;
  exchange: string;
  segment: string;
  isin: string;
  active: boolean;
};

export type Candle = 
  | [string, number, number, number, number, number] 
  | { timestamp: string; open: number; high: number; low: number; close: number; volume: number };
export type ScannerResult = {
  symbol: string;
  signal: 'BUY' | 'SELL' | 'HOLD';
  signalStrength: number;
  price: number | null;
  entry: number | null;
  stopLoss: number | null;
  target: number | null;
  riskReward: number | null;
  reasons: string[];
};

export type ScannerResponse = {
  timeframe: string;
  results: ScannerResult[];
};

export type Account = {
  initialBalance: number;
  availableCash: number;
  usedMargin: number;
  realizedPnl: number;
  unrealizedPnl: number;
  totalPnl: number;
  dailyPnl: number;
};



export type Position = {
  id: string;
  symbol: string;
  exchange: string;
  side: string;
  quantity: number;
  averageEntryPrice: number;
  currentPrice: number;
  unrealizedPnl: number;
  realizedPnl: number;
  stopLoss?: number;
  target?: number;
  status: string;
  openedAt: string;
};

export type ManualTrade = {
  id: string;
  instrumentType: 'STOCK' | 'OPTION';
  instrumentKey: string;
  symbol: string;
  exchange: string;
  expiry: string | null;
  optionType: 'CE' | 'PE' | null;
  strike: number | null;
  quantity: number;
  entryPrice: number;
  currentPrice: number;
  exitPrice: number | null;
  entryCharges: number;
  exitCharges: number;
  unrealizedPnl: number;
  realizedPnl: number;
  status: 'OPEN' | 'CLOSED';
  openedAt: string;
  closedAt: string | null;
  priceUpdatedAt: string;
};

export type ManualPortfolio = {
  trades: ManualTrade[];
  realizedPnl: number;
  unrealizedPnl: number;
  totalPnl: number;
  openTrades: number;
  closedTrades: number;
  winningTrades: number;
  losingTrades: number;
};

export type ManualTradeRequest = {
  instrumentType: 'STOCK' | 'OPTION';
  symbol?: string;
  instrumentKey?: string;
  expiry?: string;
  optionType?: 'CE' | 'PE';
  quantity: number;
};

export type PaperOrder = {
  id: string;
  symbol: string;
  side: string;
  quantity: number;
  requestedPrice: number;
  executionPrice: number;
  status: string;
  charges: number;
  rejectionReason?: string;
  timestamp: string;
};

export type PaperAutomationCycle = {
  timestamp: string;
  status: string;
  reason: string;
  direction: string | null;
  optionType: string | null;
  expiry: string | null;
  strike: number | null;
  signalStrength: number | null;
  order: PaperOrder | null;
};

export type PaperAutomationAudit = Omit<PaperAutomationCycle, 'order'> & {
  id: string;
  paperOrderId: string | null;
};

export type PaperAutomationStatus = {
  automation: {
    enabled: boolean;
    configuredEnabled: boolean;
    stopped: boolean;
    idempotencyWindowMinutes: number;
    maxLotsPerTrade: number;
    maxLossPercent: number;
    maxExposurePercent: number;
    trackedSignals: number;
    modelGate: string;
  };
  cycle: {
    enabled: boolean;
    running: boolean;
    lastCycle: PaperAutomationCycle;
  };
};

export type PaperDryRunSettings = {
  defaultVirtualCapital: number;
  defaultLotSize: number;
  maxLossPercent: number;
  maxExposurePercent: number;
  maxStopLossPercent: number;
  rewardRiskMultiple: number;
  minRiskReward: number;
  maxAllocationPerTrade: number;
  maxDailyLoss: number;
  maxOpenPositions: number;
  maxTradesPerDay: number;
  maxHoldingMinutes: number;
  maxSpreadPercent: number;
  minVolume: number;
  minOpenInterest: number;
  maxAtmDistancePercent: number;
  minAbsDelta: number;
  maxAbsDelta: number;
  slippagePercent: number;
  brokerageRate: number;
};

export type PaperDryRunCoverage = {
  date: string;
  timezone: string;
  status: 'DATA_SUFFICIENT' | 'DATA_INSUFFICIENT';
  expectedIntervals: number;
  requiredIntervals: number;
  minimumCoveragePercent: number;
  optionSnapshotRows: number;
  optionSnapshotIntervals: number;
  optionIntervals: number;
  spotIntervals: number;
  spotConfirmationIntervals: number;
  futuresIntervals: number;
  firstSnapshot: string | null;
  lastSnapshot: string | null;
  firstSpotCandle: string | null;
  lastSpotCandle: string | null;
  firstFuturesObservation: string | null;
  lastFuturesObservation: string | null;
  futuresObservations: number;
  spotSource: string;
  coveragePercent: number;
  missingOptionIntervals: string[];
  missingSpotIntervals: string[];
  missingSpotConfirmationIntervals: string[];
  missingFuturesIntervals: string[];
  ready: boolean;
  note: string;
};

export type PaperDryRunSelection = {
  instrumentKey: string;
  optionType: 'CE' | 'PE';
  strike: number;
  spot: number;
  ltp: number;
  bid: number;
  ask: number;
  volume: number;
  openInterest: number;
  delta: number;
  spreadPercent: number;
  selectionScore: number;
  selectionReason: string;
};

export type PaperDryRunCycle = {
  timestamp: string;
  status: string;
  direction: 'UP' | 'DOWN' | null;
  optionType: 'CE' | 'PE' | null;
  strike: number | null;
  expiry: string | null;
  signalStrength: number | null;
  reason: string;
  selectedContract: PaperDryRunSelection | null;
};

export type PaperDryRunTrade = {
  openedAt: string;
  closedAt: string | null;
  direction: 'UP' | 'DOWN';
  optionType: 'CE' | 'PE';
  expiry: string;
  strike: number;
  instrumentKey: string;
  quantity: number;
  entryPrice: number;
  stopLoss: number;
  target: number;
  exitPrice: number | null;
  lastPrice: number;
  lastPriceAt: string;
  exitReason: string;
  grossPnl: number;
  charges: number;
  netPnl: number;
  unrealizedPnl: number;
  peakUnrealizedPnl: number;
  signalStrength: number;
  riskReward: number;
  stopRiskPercent: number;
  exposurePercent: number;
  entryReason: string;
  selectedContract: PaperDryRunSelection;
};

export type PaperDryRunResult = {
  date: string;
  generatedAt: string;
  virtualCapital: number;
  lotSize: number;
  settings: PaperDryRunSettings;
  data: {
    optionSnapshotRows: number;
    optionSnapshotIntervals: number;
    futuresObservations: number;
    niftyCandles: number;
    maxCandlesAvailableAtDecision: number;
    replayResolution: string;
  };
  summary: {
    cycles: number;
    tradeEntries: number;
    closedTrades: number;
    openTrades: number;
    noTradeCycles: number;
    winningClosedTrades: number;
    losingClosedTrades: number;
    realizedGrossPnl: number;
    charges: number;
    realizedNetPnl: number;
    unrealizedPnl: number;
    totalPnl: number;
    returnPercent: number;
    endingEquity: number;
  };
  trades: PaperDryRunTrade[];
  cycles: PaperDryRunCycle[];
  isHistoricalSimulation: boolean;
  note: string;
};

export type OrderRequest = {
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  stopLoss?: number;
  target?: number;
};

export type ModelStatus = {
  modelVersion?: string;
  status?: string;
  accuracy?: number;
  f1Macro?: number;
  totalRows?: number;
  trainingRows?: number;
  validationRows?: number;
  horizonMinutes?: number;
};

export type ApiError = {
  message: string;
  status: number;
  timestamp?: string;
};

export type OptionCandidate = {
  type: 'CE' | 'PE';
  strike: number;
  instrumentKey: string;
  expiry: string;
  ltp: number;
  bid: number;
  ask: number;
  spreadPercent: number;
  volume: number;
  oi: number;
  iv: number;
  delta: number;
  gamma: number;
  theta: number;
  vega: number;
  predictedOptionReturnPercent: number;
  optionScore: number;
  reason: string;
};

export type OptionOpportunity = {
  status?: 'AVAILABLE' | 'DATA_INSUFFICIENT';
  symbol: string;
  spot: number | null;
  atmStrike: number | null;

  recommendation: 'CE' | 'PE' | 'NO_TRADE';

  recommendedOption: OptionCandidate | null;

  ceCandidates: OptionCandidate[];
  peCandidates: OptionCandidate[];

  decisionReason: string;
  dataQualityIssues?: string[];
  scenario?: {
    direction?: 'UP' | 'DOWN' | 'NEUTRAL' | null;
    expectedReturnPercent?: number | null;
    expectedMovePoints?: number | null;
    horizonMinutes?: number | null;
    status: 'AVAILABLE' | 'DATA_INSUFFICIENT';
  };

  diagnostics?: {
    totalRows: number;
    rowsWithinAtmDistance: number;
    ceChecked: number;
    peChecked: number;
    ceAccepted: number;
    peAccepted: number;
    rejections: {
      missingMarketData: number;
      invalidLtp: number;
      liquidity: number;
      invalidGreeks: number;
      spread: number;
      mlPrediction: number;
      other: number;
    };
  };

  niftyContext?: {
    direction?: 'UP' | 'DOWN' | 'NEUTRAL' | null;
    expectedReturnPercent?: number | null;
    expectedMovePoints?: number | null;
    technicalSignal?: 'BUY' | 'SELL' | 'HOLD';
    technicalScore?: number;

    directionProbabilities?: {
      DOWN: number;
      NEUTRAL: number;
      UP: number;
    };
  };

  expiry: string;

  optionChainDataSource?: string;
  optionChainSnapshotTimestamp?: string;
};

export type OptionOpportunityResponse = {
  status: string;
  data: OptionOpportunity;
};

export type NiftyExpiryResponse = {
  status: string;
  expiry: string;
};

export type OptionMarketData = {
  ltp: number;
  volume: number;
  oi: number;
  close_price: number;
  bid_price: number;
  bid_qty: number;
  ask_price: number;
  ask_qty: number;
  prev_oi: number;
};

export type OptionGreeks = {
  vega: number;
  theta: number;
  gamma: number;
  delta: number;
  iv: number;
  pop: number;
};

export type OptionSide = {
  instrument_key: string;
  market_data: OptionMarketData;
  option_greeks: OptionGreeks;
};

export type OptionChainRow = {
  expiry: string;
  pcr: number;
  strike_price: number;
  underlying_key: string;
  underlying_spot_price: number;
  call_options: OptionSide;
  put_options: OptionSide;
};

export type OptionChainResponse = {
  status: string;
  data: OptionChainRow[];
};

export type OptionPricePoint = {
  timestamp: string;
  price: number;
  volume: number;
};

export type NiftyExpiriesResponse = {
  status: string;
  expiries: string[];
};

export type NiftyTradeRecommendation = {
  timestamp: string;
  status: 'READY' | 'NO_TRADE' | string;
  reason: string;
  direction: 'UP' | 'DOWN' | null;
  optionType: 'CE' | 'PE' | null;
  expiry: string | null;
  strike: number | null;
  instrumentKey: string | null;
  spotPrice: number | null;
  optionPrice: number | null;
  bidPrice: number | null;
  askPrice: number | null;
  entryPrice: number | null;
  stopLoss: number | null;
  targetPrice: number | null;
  riskReward: number | null;
  signalStrength: number | null;
  technicalSignal: 'BUY' | 'SELL' | 'HOLD' | null;
  expectedNiftyMovePoints: number | null;
  quantity: number | null;
  evidence: string[];
};
// ============================================================
// Request utilities
// ============================================================

async function request<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
      ...init,
    });
    
    if (!response.ok) {
      const text = await response.text();
      const error: ApiError = {
        message: text || `API request failed`,
        status: response.status,
      };
      throw error;
    }
    
    return response.json() as Promise<T>;
  } catch (err) {
    if (err instanceof Error && 'status' in err) {
      throw err;
    }
    throw {
      message: err instanceof Error ? err.message : 'Network or parsing error',
      status: 0,
    } as ApiError;
  }
}

async function mlRequest<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  try {
    const response = await fetch(`${ML_BASE}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
      ...init,
    });
    
    if (!response.ok) {
      throw {
        message: `ML service error: ${response.statusText}`,
        status: response.status,
      } as ApiError;
    }
    
    return response.json() as Promise<T>;
  } catch (err) {
    if (err instanceof Error && 'status' in err) {
      throw err;
    }
    throw {
      message: 'ML service unavailable',
      status: 0,
    } as ApiError;
  }
}

// ============================================================
// API client
// ============================================================

export const api = {
  // Market Data APIs
  quote: (symbol: string) => 
    request<Quote>(`/market/quote/${encodeURIComponent(symbol)}`),
  
  history: (symbol: string, timeframe: string, from: string, to: string) =>
    request<Candle[]>(
      `/market/history/${encodeURIComponent(symbol)}?timeframe=${timeframe}&from=${from}&to=${to}`
    ),
  
  intraday: (symbol: string, timeframe: string) =>
    request<Candle[]>(
      `/market/intraday/${encodeURIComponent(symbol)}?timeframe=${timeframe}`
    ),

    // instrument APIs

    instruments: () =>
  request<Instrument[]>('/instruments'),

instrument: (symbol: string) =>
  request<Instrument>(
    `/instruments/${encodeURIComponent(symbol)}`
  ),

importInstruments: () =>
  request<Record<string, number>>('/instruments/import', {
    method: 'POST',
  }),

  // Watchlist / WebSocket APIs
subscribeInstrument: (symbol: string) =>
  request<{
    symbol: string;
    instrumentKey: string;
    status: string;
  }>(`/watchlist/${encodeURIComponent(symbol)}/subscribe`, {
    method: 'POST',
  }),

unsubscribeInstrument: (symbol: string) =>
  request<{
    symbol: string;
    instrumentKey: string;
    status: string;
  }>(`/watchlist/${encodeURIComponent(symbol)}/subscribe`, {
    method: 'DELETE',
  }),

watchlistStream: () =>
  request<{
    status: string;
    instruments?: string[];
  }>('/watchlist/stream'),
  
  // Scanner APIs
  scanner: (timeframe = '1D', limit = 20) =>
    request<ScannerResponse>(
      `/scanner?timeframe=${timeframe}&limit=${limit}`
    ),

  // Prediction APIs
  modelStatus: () =>
    request<ModelStatus>('/ml/status'),
  trainNifty: (days = 30) =>
  request<ModelStatus>(`/ml/train/nifty?days=${days}`, {
    method: 'POST',
  }),
  niftyPrediction: () =>
  request<Record<string, unknown>>('/ml/predict/nifty'),
  
  predictionHistory: () =>
    request<Record<string, unknown>[]>('/ml/history'),

  // Paper Trading APIs
  account: () =>
    request<Account>('/paper/account'),
  
  positions: () =>
    request<Position[]>('/paper/positions'),
  
  orders: () =>
    request<PaperOrder[]>('/paper/orders'),
  
  submitOrder: (order: OrderRequest) =>
    request<PaperOrder>('/paper/orders', {
      method: 'POST',
      body: JSON.stringify(order),
    }),
  
  closePosition: (id: string) =>
    request<PaperOrder>(`/paper/positions/${id}/close`, {
      method: 'POST',
    }),

  manualPortfolio: () =>
    request<ManualPortfolio>('/manual-trading'),

  buyManualTrade: (trade: ManualTradeRequest) =>
    request<ManualTrade>('/manual-trading/trades', {
      method: 'POST',
      body: JSON.stringify(trade),
    }),

  sellManualTrade: (id: string) =>
    request<ManualTrade>(`/manual-trading/trades/${encodeURIComponent(id)}/sell`, {
      method: 'POST',
    }),
  
  resetAccount: () =>
    request<Account>('/paper/account/reset', {
      method: 'POST',
    }),

  paperAutomationStatus: () =>
    request<PaperAutomationStatus>('/paper/automation/status'),

  niftyTradeRecommendation: () =>
    request<NiftyTradeRecommendation>('/paper/automation/recommendation'),

  stopPaperAutomation: () =>
    request<PaperAutomationStatus>('/paper/automation/stop', { method: 'POST' }),

  resumePaperAutomation: () =>
    request<PaperAutomationStatus>('/paper/automation/resume', { method: 'POST' }),

  runNiftyPaperCycle: () =>
    request<PaperAutomationCycle>('/paper/automation/cycle', {
      method: 'POST',
    }),

  paperAutomationCycles: () =>
    request<PaperAutomationAudit[]>('/paper/automation/cycles'),

  paperDryRunSettings: () =>
    request<PaperDryRunSettings>('/paper/dry-run/settings'),

  paperDryRunCoverage: (date: string) =>
    request<PaperDryRunCoverage>(`/paper/dry-run/coverage?date=${encodeURIComponent(date)}`),

  runPaperDryRun: (date: string, virtualCapital: number, lotSize: number) =>
    request<PaperDryRunResult>('/paper/dry-run/run', {
      method: 'POST',
      body: JSON.stringify({ date, virtualCapital, lotSize }),
    }),

  // ML Service APIs (Python backend)
  mlHealth: () =>
    mlRequest<{ status: string; service: string }>('/health'),

  niftyOptionOpportunity: (expiry: string) =>
  request<OptionOpportunityResponse>(
    `/options/nifty/opportunity?expiry=${encodeURIComponent(expiry)}`
  ),
  niftyOptionChain: (expiry: string) =>
  request<OptionChainResponse>(
    `/options/nifty/chain?expiry=${encodeURIComponent(expiry)}`
  ),
  niftyOptionHistory: (instrumentKey: string, expiry: string) =>
    request<OptionPricePoint[]>(
      `/options/nifty/contract-history?instrumentKey=${encodeURIComponent(instrumentKey)}&expiry=${encodeURIComponent(expiry)}`
    ),
  niftyNearestExpiry: () =>
  request<NiftyExpiryResponse>(
    '/options/nifty/expiry'
  ),
  niftyExpiries: (limit = 5) =>
  request<NiftyExpiriesResponse>(
    `/options/nifty/expiries?limit=${limit}`
  ),
  
};
