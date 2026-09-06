import { useEffect, useState, useCallback, useRef } from 'react';
import { api, Instrument, OptionOpportunityResponse, type ApiError, type Quote } from './api';

// ============================================================
// Generic data fetching hook
// ============================================================

export function useAsync<T>(
  asyncFunction: () => Promise<T>,
  immediate = true,
  dependencies: unknown[] = []
) {
  const [status, setStatus] = useState<'idle' | 'pending' | 'success' | 'error'>('idle');
  const [value, setValue] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  const execute = useCallback(async () => {
    setStatus('pending');
    setValue(null);
    setError(null);
    try {
      const response = await asyncFunction();
      setValue(response);
      setStatus('success');
      return response;
    } catch (err) {
      const apiError: ApiError = err instanceof Error
        ? { message: err.message, status: 0 }
        : (err as ApiError);
      setError(apiError);
      setStatus('error');
      throw apiError;
    }
  }, [asyncFunction]);

  useEffect(() => {
    if (immediate) {
      void execute();
    }
  }, dependencies);

  return { execute, status, value, error };
}

// ============================================================
// Market data hooks
// ============================================================

export function useQuote(symbol: string, refreshInterval = 0) {
  const [quote, setQuote] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  const loadQuote = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await api.quote(symbol);
      setQuote(data);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to load quote';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [symbol]);

  useEffect(() => {
    void loadQuote();
    
    if (refreshInterval > 0) {
      const timer = setInterval(() => void loadQuote(), refreshInterval);
      return () => clearInterval(timer);
    }
  }, [symbol, refreshInterval, loadQuote]);

  return { quote, loading, error, refetch: loadQuote };
}

export type LiveMarketTick = {
  instrumentKey: string;
  timestamp: string;
  lastPrice: number;
  lastQuantity?: number | null;
  volume?: number | null;
  open?: number | null;
  high?: number | null;
  low?: number | null;
  close?: number | null;
};

function marketWebSocketUrl() {
  const configured = import.meta.env.VITE_MARKET_WS_URL;
  if (configured) return configured;

  const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api';
  const url = new URL(apiUrl, window.location.href);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = '/ws/market';
  url.search = '';
  return url.toString();
}

/**
 * Receives browser-safe ticks from Spring Boot. The browser never connects to
 * Upstox; REST subscriptions decide which instruments Spring subscribes to.
 */
export function useMarketWebSocket(instruments: Instrument[]) {
  const [ticks, setTicks] = useState<Record<string, LiveMarketTick>>({});
  const [connected, setConnected] = useState(false);
  const retryRef = useRef(0);
  const timerRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    let disposed = false;
    let socket: WebSocket | undefined;

    const connect = () => {
      socket = new WebSocket(marketWebSocketUrl());
      socket.onopen = () => {
        retryRef.current = 0;
        setConnected(true);
      };
      socket.onmessage = event => {
        try {
          const tick = JSON.parse(event.data) as LiveMarketTick;
          if (tick.instrumentKey && Number.isFinite(Number(tick.lastPrice))) {
            setTicks(current => ({ ...current, [tick.instrumentKey]: tick }));
          }
        } catch {
          // Ignore malformed frames; a later valid market tick will replace it.
        }
      };
      socket.onclose = () => {
        setConnected(false);
        if (!disposed) {
          const delay = Math.min(1_000 * 2 ** retryRef.current++, 10_000);
          timerRef.current = window.setTimeout(connect, delay);
        }
      };
      socket.onerror = () => socket?.close();
    };

    connect();
    return () => {
      disposed = true;
      if (timerRef.current !== undefined) window.clearTimeout(timerRef.current);
      socket?.close();
    };
  }, []);

  const quotesBySymbol = instruments.reduce<Record<string, Partial<Quote>>>((quotes, instrument) => {
    const tick = ticks[instrument.instrumentKey];
    if (tick) {
      quotes[instrument.symbol] = {
        symbol: instrument.symbol,
        price: Number(tick.lastPrice),
        open: tick.open == null ? undefined : Number(tick.open),
        high: tick.high == null ? undefined : Number(tick.high),
        low: tick.low == null ? undefined : Number(tick.low),
        previousClose: tick.close == null ? undefined : Number(tick.close),
        volume: tick.volume == null ? undefined : Number(tick.volume),
        timestamp: tick.timestamp,
      };
    }
    return quotes;
  }, {});

  return { quotesBySymbol, connected };
}

export function useCandles(
  symbol: string,
  timeframe: string,
  isIntraday = true,
  from?: string,
  to?: string
) {
  const [candles, setCandles] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    let mounted = true;

    async function load() {
      try {
        setLoading(true);
        setError('');
        const data = isIntraday
          ? await api.intraday(symbol, timeframe)
          : await api.history(symbol, timeframe, from!, to!);
        if (mounted) {
          setCandles(data);
        }
      } catch (err) {
        if (mounted) {
          const msg = err instanceof Error ? err.message : 'Failed to load candles';
          setError(msg);
        }
      } finally {
        if (mounted) setLoading(false);
      }
    }

    void load();
    return () => { mounted = false; };
  }, [symbol, timeframe, isIntraday, from, to]);

  return { candles, loading, error };
}


export function useWatchlistSubscription() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const subscribe = useCallback(async (symbol: string) => {
    try {
      setLoading(true);
      setError('');

      return await api.subscribeInstrument(symbol);
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : 'Failed to subscribe instrument';

      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const unsubscribe = useCallback(async (symbol: string) => {
    try {
      setLoading(true);
      setError('');

      return await api.unsubscribeInstrument(symbol);
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : 'Failed to unsubscribe instrument';

      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    subscribe,
    unsubscribe,
    loading,
    error,
  };
}
// ============================================================
// Scanner hooks
// ============================================================

export function useScanner(timeframe = '1D', refreshInterval = 0) {
  const [signals, setSignals] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  const loadScanner = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await api.scanner(timeframe);
      setSignals(data.results || []);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to load scanner results';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [timeframe]);

  useEffect(() => {
    void loadScanner();

    if (refreshInterval > 0) {
      const timer = setInterval(() => void loadScanner(), refreshInterval);
      return () => clearInterval(timer);
    }
  }, [timeframe, refreshInterval, loadScanner]);

  return { signals, loading, error, refetch: loadScanner };
}

//  instrument hooks

export function useInstruments() {
  const [instruments, setInstruments] = useState<Instrument[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchInstruments = async () => {
    try {
      setLoading(true);
      setError('');

      const data = await api.instruments();

      setInstruments(data);

      return data;
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : 'Failed to load instruments';

      setError(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInstruments();
  }, []);

  return {
    instruments,
    loading,
    error,
    refetch: fetchInstruments,
  };
}
// ============================================================
// Prediction hooks
// ============================================================

export function useModelStatus(refreshInterval = 30000) {
  const [model, setModel] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  const loadModel = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await api.modelStatus();
      setModel(data);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to load model status';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadModel();

    if (refreshInterval > 0) {
      const timer = setInterval(() => void loadModel(), refreshInterval);
      return () => clearInterval(timer);
    }
  }, [refreshInterval, loadModel]);

  return { model, loading, error, refetch: loadModel };
}

export function useNiftyPrediction(
  horizonMinutes = 15,
  movementThresholdPercent = 0.2,
  refreshInterval = 30000
) {
  const [prediction, setPrediction] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  const loadPrediction = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await api.niftyPrediction();
      setPrediction(data);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to load prediction';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [horizonMinutes, movementThresholdPercent]);

  useEffect(() => {
    void loadPrediction();

    if (refreshInterval > 0) {
      const timer = setInterval(() => void loadPrediction(), refreshInterval);
      return () => clearInterval(timer);
    }
  }, [horizonMinutes, movementThresholdPercent, refreshInterval, loadPrediction]);

  return { prediction, loading, error, refetch: loadPrediction };
}

export function useTrainNifty() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');
  const [result, setResult] = useState<any>(null);

  const train = async (days = 30) => {
    try {
      setLoading(true);
      setError('');
      setResult(null);

      const data = await api.trainNifty(days);

      setResult(data);

      return data;
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Failed to train NIFTY model';

      setError(msg);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  return {
    train,
    result,
    loading,
    error,
  };
}

// ============================================================
// Paper trading hooks
// ============================================================

export function useAccount(refreshInterval = 5000) {
  const [account, setAccount] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  const loadAccount = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await api.account();
      setAccount(data);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to load account';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAccount();

    if (refreshInterval > 0) {
      const timer = setInterval(() => void loadAccount(), refreshInterval);
      return () => clearInterval(timer);
    }
  }, [refreshInterval, loadAccount]);

  return { account, loading, error, refetch: loadAccount };
}

export function usePositions(refreshInterval = 5000) {
  const [positions, setPositions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  const loadPositions = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await api.positions();
      setPositions(data || []);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to load positions';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadPositions();

    if (refreshInterval > 0) {
      const timer = setInterval(() => void loadPositions(), refreshInterval);
      return () => clearInterval(timer);
    }
  }, [refreshInterval, loadPositions]);

  return { positions, loading, error, refetch: loadPositions };
}

export function useOrders(refreshInterval = 5000) {
  const [orders, setOrders] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');

  const loadOrders = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const data = await api.orders();
      setOrders(data || []);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to load orders';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders();

    if (refreshInterval > 0) {
      const timer = setInterval(() => void loadOrders(), refreshInterval);
      return () => clearInterval(timer);
    }
  }, [refreshInterval, loadOrders]);

  return { orders, loading, error, refetch: loadOrders };
}

// ============================================================
// Paper trading actions
// ============================================================

export function useClosePosition() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');

  const close = useCallback(async (positionId: string) => {
    try {
      setLoading(true);
      setError('');
      await api.closePosition(positionId);
      return true;
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to close position';
      setError(msg);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return { close, loading, error };
}

export function useSubmitOrder() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>('');

  const submit = useCallback(async (order: any) => {
    try {
      setLoading(true);
      setError('');
      const result = await api.submitOrder(order);
      return result;
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to submit order';
      setError(msg);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return { submit, loading, error };
}

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


// ============================================================
// Options hooks
// ============================================================

export function useNiftyOptionOpportunity(
  expiry: string,
  refreshInterval = 30000
) {
  const [opportunity, setOpportunity] =
    useState<OptionOpportunityResponse | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadOpportunity = useCallback(async () => {
    if (!expiry) return;

    try {
      setLoading(true);
      setError('');

      const data = await api.niftyOptionOpportunity(expiry);

      setOpportunity(data);
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Failed to load option opportunity';

      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [expiry]);

  useEffect(() => {
    void loadOpportunity();

    if (refreshInterval > 0) {
      const timer = setInterval(
        () => void loadOpportunity(),
        refreshInterval
      );

      return () => clearInterval(timer);
    }
  }, [loadOpportunity, refreshInterval]);

  return {
    opportunity,
    loading,
    error,
    refetch: loadOpportunity,
  };
}