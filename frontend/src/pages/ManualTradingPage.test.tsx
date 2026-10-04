// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, type ManualPortfolio } from '../services/api';
import { ManualTradingPage } from './ManualTradingPage';

Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, value: true });

vi.mock('../services/api', () => ({
  api: {
    manualPortfolio: vi.fn(),
    buyManualTrade: vi.fn(),
    sellManualTrade: vi.fn(),
    niftyExpiries: vi.fn(),
    niftyOptionChain: vi.fn(),
    quote: vi.fn(),
  },
}));

const portfolio: ManualPortfolio = {
  trades: [{
    id: 'manual-1',
    instrumentType: 'STOCK',
    instrumentKey: 'NSE_EQ|ABC',
    symbol: 'ABC',
    exchange: 'NSE',
    expiry: null,
    optionType: null,
    strike: null,
    quantity: 2,
    entryPrice: 100,
    currentPrice: 105,
    exitPrice: null,
    entryCharges: 2,
    exitCharges: 0,
    unrealizedPnl: 8,
    realizedPnl: 0,
    status: 'OPEN',
    openedAt: '2026-10-04T04:00:00Z',
    closedAt: null,
    priceUpdatedAt: '2026-10-04T04:00:00Z',
  }],
  realizedPnl: 25,
  unrealizedPnl: 8,
  totalPnl: 33,
  openTrades: 1,
  closedTrades: 1,
  winningTrades: 1,
  losingTrades: 0,
};

describe('ManualTradingPage', () => {
  let root: Root;
  let host: HTMLDivElement;

  beforeEach(() => {
    host = document.createElement('div');
    document.body.append(host);
    root = createRoot(host);
    vi.mocked(api.manualPortfolio).mockResolvedValue(portfolio);
    vi.mocked(api.niftyExpiries).mockResolvedValue({ status: 'success', expiries: [] });
    vi.mocked(api.niftyOptionChain).mockResolvedValue({ status: 'success', data: [] });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.clearAllMocks();
  });

  it('shows manual P&L and holdings independently of the auto paper portfolio', async () => {
    await act(async () => {
      root.render(<MemoryRouter><ManualTradingPage /></MemoryRouter>);
      await Promise.resolve();
    });

    expect(host.textContent).toContain('Manual realized P&L');
    expect(host.textContent).toContain('Manual unrealized P&L');
    expect(host.textContent).toContain('AUTO-TRADE P&L IS SEPARATE');
    expect(host.textContent).toContain('ABC');
    expect(host.textContent).toContain('Buy and hold');
    expect(api.manualPortfolio).toHaveBeenCalled();
  });

  it('sells an open manual holding and refreshes the manual ledger', async () => {
    vi.mocked(api.sellManualTrade).mockResolvedValue({
      ...portfolio.trades[0],
      status: 'CLOSED',
      exitPrice: 110,
      realizedPnl: 17.8,
      unrealizedPnl: 0,
    });
    await act(async () => {
      root.render(<MemoryRouter><ManualTradingPage /></MemoryRouter>);
      await Promise.resolve();
    });

    const sell = [...host.querySelectorAll('button')].find((button) => button.textContent === 'Sell');
    await act(async () => {
      sell?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(api.sellManualTrade).toHaveBeenCalledWith('manual-1');
    expect(api.manualPortfolio).toHaveBeenCalledTimes(2);
  });
});
