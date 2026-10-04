// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, type ManualPortfolio, type OptionChainRow } from '../services/api';
import { NiftyOptionDetailPage } from './NiftyOptionDetailPage';

Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, value: true });

vi.mock('../services/api', () => ({
  api: {
    niftyOptionChain: vi.fn(),
    niftyOptionHistory: vi.fn(),
    manualPortfolio: vi.fn(),
    buyManualTrade: vi.fn(),
    sellManualTrade: vi.fn(),
  },
}));

const row: OptionChainRow = {
  expiry: '2026-10-08',
  pcr: 1,
  strike_price: 23000,
  underlying_key: 'NSE_INDEX|Nifty 50',
  underlying_spot_price: 23050,
  call_options: {
    instrument_key: 'NSE_FO|CALL',
    market_data: { ltp: 120, volume: 1000, oi: 2000, close_price: 110, bid_price: 119, bid_qty: 20, ask_price: 121, ask_qty: 20, prev_oi: 1950 },
    option_greeks: { vega: 2, theta: -1, gamma: 0.01, delta: 0.6, iv: 17, pop: 0.65 },
  },
  put_options: {
    instrument_key: 'NSE_FO|PUT',
    market_data: { ltp: 135, volume: 1200, oi: 2400, close_price: 125, bid_price: 134, bid_qty: 25, ask_price: 136, ask_qty: 20, prev_oi: 2300 },
    option_greeks: { vega: 2.5, theta: -1.2, gamma: 0.012, delta: -0.4, iv: 19, pop: 0.58 },
  },
};

const portfolio: ManualPortfolio = {
  trades: [],
  realizedPnl: 0,
  unrealizedPnl: 0,
  totalPnl: 0,
  openTrades: 0,
  closedTrades: 0,
  winningTrades: 0,
  losingTrades: 0,
};

describe('NiftyOptionDetailPage', () => {
  let root: Root;
  let host: HTMLDivElement;

  beforeEach(() => {
    host = document.createElement('div');
    document.body.append(host);
    root = createRoot(host);
    vi.mocked(api.niftyOptionChain).mockResolvedValue({ status: 'success', data: [row] });
    vi.mocked(api.niftyOptionHistory).mockResolvedValue([]);
    vi.mocked(api.manualPortfolio).mockResolvedValue(portfolio);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.clearAllMocks();
  });

  it('shows PE contract analysis and buys the selected contract into manual trading', async () => {
    vi.mocked(api.buyManualTrade).mockResolvedValue({
      id: 'manual-pe',
      instrumentType: 'OPTION',
      instrumentKey: 'NSE_FO|PUT',
      symbol: 'NIFTY 23000 PE',
      exchange: 'NSE_FO',
      expiry: '2026-10-08',
      optionType: 'PE',
      strike: 23000,
      quantity: 1,
      entryPrice: 135,
      currentPrice: 135,
      exitPrice: null,
      entryCharges: 0,
      exitCharges: 0,
      unrealizedPnl: 0,
      realizedPnl: 0,
      status: 'OPEN',
      openedAt: '2026-10-04T09:00:00Z',
      closedAt: null,
      priceUpdatedAt: '2026-10-04T09:00:00Z',
    });
    await act(async () => {
      root.render(
        <MemoryRouter initialEntries={['/nifty-options/contract?expiry=2026-10-08&type=PE&strike=23000&key=NSE_FO%7CPUT']}>
          <NiftyOptionDetailPage />
        </MemoryRouter>,
      );
      await Promise.resolve();
    });

    expect(host.textContent).toContain('NIFTY 23,000 PE');
    expect(host.textContent).toContain('Greeks and liquidity');
    expect(host.textContent).toContain('₹135');

    const buy = [...host.querySelectorAll('button')].find((button) => button.textContent === 'Buy PE');
    await act(async () => {
      buy?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(api.buyManualTrade).toHaveBeenCalledWith({
      instrumentType: 'OPTION',
      instrumentKey: 'NSE_FO|PUT',
      expiry: '2026-10-08',
      optionType: 'PE',
      quantity: 1,
    });
  });
});
