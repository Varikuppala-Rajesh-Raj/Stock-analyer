import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  loadSavedOvernightQuotes,
  refreshOvernightQuotes,
  retrieveOvernightInstrument,
} from './overnightMarketData';

afterEach(() => vi.unstubAllGlobals());

describe('overnight market data service', () => {
  it('loads saved quotes from the backend without requesting new provider data', async () => {
    const savedQuote = {
      instrumentKey: 'GLOBAL_INDEX|SGX NIFTY',
      value: 22650.5,
      previousClose: 22600,
      change: 50.5,
      changePct: 0.223,
      timestamp: '2026-10-06T09:59:00Z',
      retrievedAt: '2026-10-06T10:00:00Z',
      status: 'STALE',
      error: 'Persisted market data is stale.',
    };
    const savedGoldQuote = {
      ...savedQuote,
      instrumentKey: 'NSE_COM|124394',
      value: 156800,
      change: 125,
    };
    const savedBrentQuote = {
      ...savedQuote,
      provider: 'NSE',
      instrumentKey: 'NSE_API|BRCRUDEOIL',
      value: 78.45,
      previousClose: null,
      change: null,
      changePct: null,
      timestamp: null,
      error: 'Upstox returned a price without a source timestamp; freshness cannot be verified.',
    };
    const unavailableQuote = {
      instrumentKey: '',
      value: null,
      previousClose: null,
      change: null,
      changePct: null,
      timestamp: null,
      retrievedAt: null,
      status: 'UNAVAILABLE',
      error: 'No persisted market data is available.',
    };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        giftNifty: savedQuote,
        sp500: unavailableQuote,
        usTech100: unavailableQuote,
        dow: unavailableQuote,
        nikkei: unavailableQuote,
        hangSeng: unavailableQuote,
        usdInr: unavailableQuote,
        crudeOil: unavailableQuote,
        gold: savedGoldQuote,
        ftse100: savedQuote,
        dax: unavailableQuote,
        brent: savedBrentQuote,
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(loadSavedOvernightQuotes()).resolves.toMatchObject({
      GIFT_NIFTY: {
        source: 'DATABASE',
        status: 'STALE',
        lastPrice: 22650.5,
        dataTimestamp: '2026-10-06T09:59:00Z',
      },
      GOLD: {
        instrumentKey: 'NSE_COM|124394',
        source: 'DATABASE',
        status: 'STALE',
        lastPrice: 156800,
      },
      FTSE_100: { instrumentKey: 'GLOBAL_INDEX|SGX NIFTY', source: 'DATABASE', status: 'STALE' },
      DAX: { source: 'NONE', status: 'DATA_NOT_AVAILABLE' },
      BRENT: {
        provider: 'NSE',
        instrumentKey: 'NSE_API|BRCRUDEOIL',
        source: 'DATABASE',
        status: 'STALE',
        lastPrice: 78.45,
        dataTimestamp: null,
        retrievedAt: '2026-10-06T10:00:00Z',
      },
      SP500: { source: 'NONE', status: 'DATA_NOT_AVAILABLE' },
    });
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/overnight/global',
      { headers: { Accept: 'application/json' } },
    );
  });

  it('surfaces saved-quote loading errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      statusText: 'Service Unavailable',
    }));

    await expect(loadSavedOvernightQuotes()).rejects.toThrow(
      'Loading saved overnight market data failed: 503 Service Unavailable',
    );
  });

  it('rejects a snapshot that omits configured overnight instruments', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({}),
    }));

    await expect(loadSavedOvernightQuotes()).rejects.toThrow(
      'Saved overnight market data response is missing giftNifty.',
    );
  });

  it('retrieves a selected instrument from the backend', async () => {
    const snapshot = {
      instrument: 'GIFT_NIFTY',
      instrumentKey: 'GLOBAL_INDEX|SGX NIFTY',
      provider: 'UPSTOX',
      source: 'UPSTOX',
      status: 'FRESH',
      dataTimestamp: '2026-10-06T09:59:00Z',
      retrievedAt: '2026-10-06T10:00:00Z',
      lastPrice: 22650.5,
      open: 22600,
      high: 22700,
      low: 22590,
      close: 22600,
      netChange: 50.5,
      volume: 1000,
      message: null,
    };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ results: [snapshot] }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await retrieveOvernightInstrument('GIFT_NIFTY');

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/overnight/retrieve',
      {
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify({ instrument: 'GIFT_NIFTY' }),
      },
    );
    expect(result).toEqual(snapshot);
  });

  it('forces a latest-data refresh for every overnight instrument', async () => {
    const result = {
      instrument: 'GIFT_NIFTY',
      instrumentKey: 'GLOBAL_INDEX|SGX NIFTY',
      provider: 'UPSTOX',
      source: 'UPSTOX',
      status: 'FRESH',
      dataTimestamp: '2026-10-08T12:30:00Z',
      retrievedAt: '2026-10-08T12:31:00Z',
      lastPrice: 25123.5,
      open: 25000,
      high: 25150,
      low: 24950,
      close: 25000,
      netChange: 123.5,
      volume: 1000,
      message: null,
    };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ results: [result] }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(refreshOvernightQuotes(true)).resolves.toEqual([result]);
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/overnight/refresh',
      expect.objectContaining({ method: 'POST', body: '{}' }),
    );
  });

  it('surfaces backend failures instead of returning empty market data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      statusText: 'Service Unavailable',
    }));

    await expect(retrieveOvernightInstrument('GIFT_NIFTY')).rejects.toThrow(
      'Overnight market retrieval failed: 503 Service Unavailable',
    );
  });

  it('rejects a success response with no instrument result', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ results: [] }),
    }));

    await expect(retrieveOvernightInstrument('GIFT_NIFTY')).rejects.toThrow(
      'Overnight market retrieval returned no instrument result.',
    );
  });
});
