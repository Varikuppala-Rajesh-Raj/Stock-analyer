// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  loadSavedOvernightQuotes,
  refreshOvernightQuotes,
  retrieveOvernightInstrument,
} from '../services/overnightMarketData';
import { nseMarketDataProvider, type MarketDataBundle, type ProviderState } from '../services/nseMarketData';
import { NiftyPremarket } from './NiftyPremarket';

Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, value: true });

vi.mock('../services/overnightMarketData', () => ({
  loadSavedOvernightQuotes: vi.fn(),
  refreshOvernightQuotes: vi.fn(),
  retrieveOvernightInstrument: vi.fn(),
}));

vi.mock('../services/nseMarketData', () => ({
  nseMarketDataProvider: {
    refreshAll: vi.fn(),
  },
}));

function marketState<T>(data: T | null, status: ProviderState<T>['status'] = 'live'): ProviderState<T> {
  return { status, lastUpdated: Date.now(), latency: 25, error: null, data };
}

function premarketBundle(): MarketDataBundle {
  const vix = {
    symbol: 'INDIA VIX',
    ltp: 13.59,
    change: -1.19,
    changePercent: -8.03,
    open: 14.78,
    high: 14.78,
    low: 13.52,
    previousClose: 14.78,
    timestamp: '07-Oct-2026 10:00:00',
  };
  return {
    nifty: marketState<NonNullable<MarketDataBundle['nifty']['data']>>(null, 'no-data'),
    constituents: marketState([], 'no-data'),
    futures: marketState<NonNullable<MarketDataBundle['futures']['data']>>(null, 'no-data'),
    options: marketState([], 'no-data'),
    indiaVix: marketState(vix),
    marketStatistics: marketState<NonNullable<MarketDataBundle['marketStatistics']['data']>>(null, 'no-data'),
    indices: marketState({}, 'no-data'),
    optionExpiries: marketState([], 'no-data'),
    selectedOptionExpiry: null,
    features: null,
    premarket: marketState({
      retrievedAt: Date.now(),
      indiaVix: vix,
      indiaVixStatus: 'live',
      indiaVixError: null,
      fiiDii: [
        { category: 'FII/FPI', date: '06-Oct-2026', buyValue: 11258.72, sellValue: 14220.02, netValue: -2961.3 },
        { category: 'DII', date: '06-Oct-2026', buyValue: 20146.4, sellValue: 15057.48, netValue: 5088.92 },
      ],
      fiiDiiStatus: 'live',
      fiiDiiError: null,
    }),
  };
}

describe('NiftyPremarket', () => {
  let root: Root;
  let host: HTMLDivElement;

  beforeEach(() => {
    vi.mocked(nseMarketDataProvider.refreshAll).mockResolvedValue(premarketBundle());
    vi.mocked(loadSavedOvernightQuotes).mockResolvedValue({});
    vi.mocked(refreshOvernightQuotes).mockResolvedValue([]);
    host = document.createElement('div');
    document.body.append(host);
    root = createRoot(host);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.restoreAllMocks();
  });

  it('retrieves a quote from the backend only when its instrument button is clicked', async () => {
    vi.mocked(retrieveOvernightInstrument).mockResolvedValue({
      instrument: 'GIFT_NIFTY',
      instrumentKey: 'GLOBAL_INDEX|SGX NIFTY',
      provider: 'UPSTOX',
      source: 'UPSTOX',
      status: 'FRESH',
      dataTimestamp: '2026-10-06T09:59:00Z',
      retrievedAt: '2026-10-06T10:00:00Z',
      lastPrice: 25123.5,
      open: 25000,
      high: 25150,
      low: 24950,
      close: 25000,
      netChange: 123.5,
      volume: 1000,
      message: null,
    });

    await act(async () => root.render(<NiftyPremarket />));

    expect(retrieveOvernightInstrument).not.toHaveBeenCalled();
    expect(loadSavedOvernightQuotes).toHaveBeenCalledOnce();
    expect(host.textContent).toContain('WEEKDAYS AT 6:00 PM IST');
    expect(host.textContent).not.toContain('/api/overnight');
    expect(host.textContent).not.toContain('SOURCE MATRIX');
    expect(host.querySelector('[data-testid="gift-nifty-quote"]')?.textContent)
      .toContain('AWAITING');

    const button = host.querySelector<HTMLButtonElement>('[aria-label="Retrieve GIFT NIFTY"]');
    expect(button).not.toBeNull();
    await act(async () => button?.click());

    expect(retrieveOvernightInstrument).toHaveBeenCalledWith('GIFT_NIFTY');
    const quote = host.querySelector('[data-testid="gift-nifty-quote"]')?.textContent ?? '';
    expect(quote).toContain('25,123.5');
    expect(quote).toContain('LIVE');
    expect(quote).toContain('Updated');
    expect(quote).not.toContain('Retrieved timestamp');
  });

  it('refreshes the latest overnight and NSE market data on demand', async () => {
    await act(async () => root.render(<NiftyPremarket />));

    const button = [...host.querySelectorAll<HTMLButtonElement>('button')]
      .find((item) => item.textContent?.includes('Refresh all market data'));
    expect(button).toBeDefined();
    await act(async () => button?.click());

    expect(refreshOvernightQuotes).toHaveBeenCalledWith(true);
    expect(nseMarketDataProvider.refreshAll).toHaveBeenCalledWith({ includePremarketFeeds: true });
  });

  it('renders database quotes automatically on page load', async () => {
    vi.mocked(loadSavedOvernightQuotes).mockResolvedValue({
      GIFT_NIFTY: {
        instrument: 'GIFT_NIFTY',
        instrumentKey: 'GLOBAL_INDEX|SGX NIFTY',
        provider: 'UPSTOX',
        source: 'DATABASE',
        status: 'STALE',
        dataTimestamp: '2026-10-06T09:59:00Z',
        retrievedAt: '2026-10-06T10:00:00Z',
        lastPrice: 25123.5,
        open: 25000,
        high: 25150,
        low: 24950,
        close: 25000,
        netChange: 123.5,
        volume: 1000,
        message: 'Persisted market data is stale.',
      },
    });

    await act(async () => root.render(<NiftyPremarket />));

    const quote = host.querySelector('[data-testid="gift-nifty-quote"]')?.textContent ?? '';
    expect(quote).toContain('25,123.5');
    expect(quote).toContain('STALE');
    expect(quote).toContain('STALE');
    expect(quote).not.toContain('DATABASE');
  });

  it('requests a fresh provider snapshot automatically when saved overnight data is stale', async () => {
    vi.mocked(loadSavedOvernightQuotes).mockResolvedValue({
      GIFT_NIFTY: {
        instrument: 'GIFT_NIFTY',
        instrumentKey: 'GLOBAL_INDEX|SGX NIFTY',
        provider: 'UPSTOX',
        source: 'DATABASE',
        status: 'STALE',
        dataTimestamp: '2026-10-08T09:00:00Z',
        retrievedAt: '2026-10-08T09:01:00Z',
        lastPrice: 25000,
        open: null,
        high: null,
        low: null,
        close: 24900,
        netChange: 100,
        volume: null,
        message: 'Persisted market data is stale.',
      },
    });
    vi.mocked(refreshOvernightQuotes).mockResolvedValue([{
      instrument: 'GIFT_NIFTY',
      instrumentKey: 'GLOBAL_INDEX|SGX NIFTY',
      provider: 'UPSTOX',
      source: 'UPSTOX',
      status: 'FRESH',
      dataTimestamp: '2026-10-08T12:30:00Z',
      retrievedAt: '2026-10-08T12:31:00Z',
      lastPrice: 25100,
      open: 25000,
      high: 25150,
      low: 24950,
      close: 25000,
      netChange: 100,
      volume: 1000,
      message: null,
    }]);

    await act(async () => root.render(<NiftyPremarket />));

    expect(refreshOvernightQuotes).toHaveBeenCalledWith();
    expect(host.querySelector('[data-testid="gift-nifty-quote"]')?.textContent).toContain('25,100');
    expect(host.querySelector('[data-testid="gift-nifty-quote"]')?.textContent).toContain('LIVE');
  });

  it('clearly renders unavailable provider data without a zero placeholder', async () => {
    vi.mocked(retrieveOvernightInstrument).mockResolvedValue({
      instrument: 'GIFT_NIFTY',
      instrumentKey: 'GLOBAL_INDEX|SGX NIFTY',
      provider: 'UPSTOX',
      source: 'NONE',
      status: 'DATA_NOT_AVAILABLE',
      dataTimestamp: null,
      retrievedAt: null,
      lastPrice: null,
      open: null,
      high: null,
      low: null,
      close: null,
      netChange: null,
      volume: null,
      message: 'No market data is currently available',
    });

    await act(async () => root.render(<NiftyPremarket />));
    const button = host.querySelector<HTMLButtonElement>('[aria-label="Retrieve GIFT NIFTY"]');
    await act(async () => button?.click());

    const quote = host.querySelector('[data-testid="gift-nifty-quote"]')?.textContent ?? '';
    expect(quote).toContain('DATA NOT AVAILABLE');
    expect(quote).toContain('NO DATA');
    expect(quote).not.toContain('DATA_NOT_AVAILABLE');
    expect(quote).not.toContain('0.00');
  });

  it('identifies crude oil as an NSE futures quote', async () => {
    vi.mocked(retrieveOvernightInstrument).mockResolvedValue({
      instrument: 'CRUDE_OIL',
      instrumentKey: 'NSE_COM|131011',
      provider: 'UPSTOX',
      source: 'UPSTOX',
      status: 'FRESH',
      dataTimestamp: '2026-10-06T09:59:00Z',
      retrievedAt: '2026-10-06T10:00:00Z',
      lastPrice: 6500,
      open: 6490,
      high: 6520,
      low: 6480,
      close: 6490,
      netChange: 10,
      volume: 1000,
      message: null,
    });

    await act(async () => root.render(<NiftyPremarket />));

    expect(host.textContent).toContain('NSE CRUDEOIL front-month futures');
    const button = host.querySelector<HTMLButtonElement>(
      '[aria-label="Retrieve NSE Crude Oil futures proxy"]',
    );
    expect(button).not.toBeNull();
    await act(async () => button?.click());

    expect(retrieveOvernightInstrument).toHaveBeenCalledWith('CRUDE_OIL');
    expect(host.textContent).toContain('NSE Crude Oil futures proxy: 6,500');
  });

  it('shows the European indices and Brent global benchmark from saved quotes', async () => {
    const savedQuote = (instrument: string, lastPrice: number) => ({
      instrument,
      instrumentKey: `GLOBAL_INDEX|${instrument}`,
      provider: 'UPSTOX',
      source: 'DATABASE' as const,
      status: 'FRESH' as const,
      dataTimestamp: '2026-10-06T09:59:00Z',
      retrievedAt: '2026-10-06T10:00:00Z',
      lastPrice,
      open: null,
      high: null,
      low: null,
      close: lastPrice - 10,
      netChange: 10,
      volume: null,
      message: null,
    });
    vi.mocked(loadSavedOvernightQuotes).mockResolvedValue({
      FTSE_100: savedQuote('FTSE_100', 8200),
      DAX: savedQuote('DAX', 19000),
      BRENT: {
        ...savedQuote('BRENT', 78.45),
        instrumentKey: 'GLOBAL_INDICATOR|BZUSD',
        status: 'STALE',
        dataTimestamp: null,
        retrievedAt: '2026-10-08T13:34:00Z',
        message: 'Upstox returned a price without a source timestamp; freshness cannot be verified.',
      },
    });

    await act(async () => root.render(<NiftyPremarket />));

    expect(host.textContent).toContain('European markets');
    expect(host.textContent).toContain('FTSE 100: 8,200');
    expect(host.textContent).toContain('DAX: 19,000');
    expect(host.textContent).toContain('Brent crude global benchmark: 78.45');
    expect(host.textContent).toContain('source timestamp unavailable, freshness cannot be verified');
  });

  it('retrieves and labels NSE gold futures as a proxy rather than spot gold', async () => {
    vi.mocked(retrieveOvernightInstrument).mockResolvedValue({
      instrument: 'GOLD',
      instrumentKey: 'NSE_COM|124394',
      provider: 'UPSTOX',
      source: 'UPSTOX',
      status: 'FRESH',
      dataTimestamp: '2026-10-08T10:00:00Z',
      retrievedAt: '2026-10-08T10:00:01Z',
      lastPrice: 156800,
      open: 156700,
      high: 156900,
      low: 156600,
      close: 156675,
      netChange: 125,
      volume: 100,
      message: null,
    });

    await act(async () => root.render(<NiftyPremarket />));
    expect(host.textContent).toContain('not spot gold');
    const button = host.querySelector<HTMLButtonElement>(
      '[aria-label="Retrieve NSE Gold futures proxy"]',
    );
    expect(button).not.toBeNull();
    await act(async () => button?.click());

    expect(retrieveOvernightInstrument).toHaveBeenCalledWith('GOLD');
    expect(host.textContent).toContain('NSE Gold futures proxy: 1,56,800');
  });

  it('loads, freshness-checks, and renders VIX and FII/DII data from the backend premarket route', async () => {
    const bundle = premarketBundle();
    bundle.premarket!.data!.indiaVixStatus = 'collected';
    bundle.premarket!.data!.fiiDiiStatus = 'report-collected';
    vi.mocked(nseMarketDataProvider.refreshAll).mockResolvedValue(bundle);

    await act(async () => root.render(<NiftyPremarket />));

    expect(nseMarketDataProvider.refreshAll).toHaveBeenCalledWith({ includePremarketFeeds: true });
    const feeds = host.querySelector('[data-testid="premarket-feeds"]')?.textContent ?? '';
    expect(feeds).toContain('India VIX');
    expect(feeds).toContain('13.59');
    expect(feeds).toContain('COLLECTED');
    expect(feeds).toContain('REPORT COLLECTED');
    expect(feeds).toContain('Updated 07-Oct-2026 10:00:00');
    expect(feeds).toContain('FII/FPI');
    expect(feeds).toContain('-2,961.30 Cr');
    expect(feeds).toContain('DII');
    expect(feeds).toContain('5,088.92 Cr');
    expect(feeds).toContain('Report date: 06-Oct-2026');
    expect(host.textContent).not.toContain('live response and freshness need runtime validation');
  });

  it('shows a multi-group market bias and keeps readiness separate from direction', async () => {
    await act(async () => root.render(<NiftyPremarket />));

    const bias = host.querySelector('[data-testid="market-bias"]');
    expect(bias?.textContent).toContain('TODAY’S NIFTY OUTLOOK:');
    expect(bias?.textContent).toContain('Evidence bias:');
    expect(bias?.textContent).toContain('Confidence:');
    expect(bias?.textContent).toContain('Data quality:');
    expect(bias?.textContent).toContain('NO CLEAR EDGE');
    const why = bias?.querySelector('details');
    expect(why?.textContent).toContain('GLOBAL');
    expect(why?.textContent).toContain('DOMESTIC');
    expect(why?.textContent).toContain('TECHNICAL');
    expect(why?.textContent).toContain('OPTIONS');
    expect(why?.textContent).toContain('RISK/VOLATILITY');
  });

  it('keeps a VIX quote visible and reports the independent FII/DII error', async () => {
    const bundle = premarketBundle();
    const premarket = bundle.premarket!;
    bundle.premarket = {
      ...premarket,
      status: 'error',
      error: 'NSE provider request failed with HTTP 502.',
      data: {
        ...premarket.data!,
        indiaVix: { ...premarket.data!.indiaVix!, timestamp: null },
        indiaVixStatus: 'stale',
        indiaVixError: 'NSE did not supply a valid source timestamp; freshness cannot be verified.',
        fiiDii: [],
        fiiDiiStatus: 'error',
        fiiDiiError: 'NSE provider request failed with HTTP 502.',
      },
    };
    vi.mocked(nseMarketDataProvider.refreshAll).mockResolvedValue(bundle);

    await act(async () => root.render(<NiftyPremarket />));

    const feeds = host.querySelector('[data-testid="premarket-feeds"]')?.textContent ?? '';
    expect(host.textContent).toContain('PARTIAL');
    expect(feeds).toContain('13.59');
    expect(feeds).toContain('STALE');
    expect(feeds).toContain('freshness cannot be verified');
    expect(feeds).toContain('NSE provider request failed with HTTP 502.');
  });

  it('shows a refresh failure instead of reporting live NSE data when the route fails', async () => {
    vi.mocked(nseMarketDataProvider.refreshAll).mockRejectedValue(
      new Error('NSE premarket service is unavailable'),
    );

    await act(async () => root.render(<NiftyPremarket />));

    expect(host.querySelector('[role="alert"]')?.textContent)
      .toContain('NSE premarket service is unavailable');
    expect(host.textContent).not.toContain('FII/FPI:');
  });
});
