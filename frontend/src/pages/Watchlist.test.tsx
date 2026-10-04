// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useInstruments, useMarketWebSocket, useQuote, useWatchlistSubscription } from '../services/hooks';
import { Watchlist } from './Watchlist';

Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, value: true });

vi.mock('../services/hooks', () => ({
  useInstruments: vi.fn(),
  useMarketWebSocket: vi.fn(),
  useQuote: vi.fn(),
  useWatchlistSubscription: vi.fn(),
}));

const instrument = {
  instrumentKey: 'NSE_EQ|ABC',
  symbol: 'ABC',
  companyName: 'ABC Limited',
  exchange: 'NSE',
  segment: 'NSE_EQ',
  isin: 'ISIN',
  active: true,
};

describe('Watchlist', () => {
  let root: Root;
  let host: HTMLDivElement;

  beforeEach(() => {
    localStorage.clear();
    host = document.createElement('div');
    document.body.append(host);
    root = createRoot(host);
    vi.mocked(useInstruments).mockReturnValue({
      instruments: [instrument],
      loading: false,
      error: '',
      refetch: vi.fn().mockResolvedValue([instrument]),
    });
    vi.mocked(useMarketWebSocket).mockReturnValue({ quotesBySymbol: {}, connected: true });
    vi.mocked(useQuote).mockReturnValue({
      quote: {
        symbol: 'ABC',
        instrumentKey: instrument.instrumentKey,
        price: 105,
        open: 104,
        high: 106,
        low: 99,
        previousClose: 100,
        change: 5,
        changePercent: 5,
        volume: 1000,
        timestamp: '2026-10-04T09:00:00Z',
      },
      loading: false,
      error: '',
      refetch: vi.fn(),
    });
    vi.mocked(useWatchlistSubscription).mockReturnValue({
      subscribe: vi.fn().mockResolvedValue({ status: 'SUBSCRIBED' }),
      unsubscribe: vi.fn().mockResolvedValue({ status: 'UNSUBSCRIBED' }),
      loading: false,
      error: '',
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.clearAllMocks();
  });

  it('shows the live price and change against previous close', async () => {
    localStorage.setItem('watchlist', JSON.stringify(['ABC']));
    await act(async () => {
      root.render(<MemoryRouter><Watchlist /></MemoryRouter>);
      await Promise.resolve();
    });

    expect(host.textContent).toContain('₹105.00');
    expect(host.textContent).toContain('+₹5.00 (+5.00%)');
  });

  it('creates a named list and adds a catalog instrument to it', async () => {
    await act(async () => {
      root.render(<MemoryRouter><Watchlist /></MemoryRouter>);
      await Promise.resolve();
    });

    const create = [...host.querySelectorAll('button')].find(button => button.textContent === '+ Create watchlist');
    await act(async () => create?.dispatchEvent(new MouseEvent('click', { bubbles: true })));

    const listName = host.querySelector<HTMLInputElement>('input[aria-label="New watchlist name"]');
    await act(async () => {
      if (listName) {
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
        setter?.call(listName, 'Blue chips');
        listName.dispatchEvent(new Event('input', { bubbles: true }));
      }
    });
    const createList = [...host.querySelectorAll('button')].find(button => button.textContent === 'Create list');
    await act(async () => createList?.dispatchEvent(new MouseEvent('click', { bubbles: true })));

    const addInstrument = [...host.querySelectorAll('button')].find(button => button.textContent?.includes('+ Add instrument'));
    await act(async () => addInstrument?.dispatchEvent(new MouseEvent('click', { bubbles: true })));
    const finder = host.querySelector<HTMLInputElement>('input[placeholder="Search symbol, company, exchange, or segment"]');
    await act(async () => {
      if (finder) {
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
        setter?.call(finder, 'ABC');
        finder.dispatchEvent(new Event('input', { bubbles: true }));
      }
    });
    const addResult = [...host.querySelectorAll('.instrument-results button')].find(button => button.textContent?.includes('+ Add'));
    await act(async () => {
      addResult?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await Promise.resolve();
    });

    expect(host.querySelector('select')?.value).toBe('Blue chips');
    expect(localStorage.getItem('watchlists')).toContain('"Blue chips":["ABC"]');
  });
});
