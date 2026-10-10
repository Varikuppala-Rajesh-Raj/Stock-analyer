// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, type PaperAutomationStatus } from '../services/api';
import { PaperTradingPage } from './PaperTradingPage';

Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, value: true });

vi.mock('../services/api', () => ({
  api: {
    account: vi.fn(),
    positions: vi.fn(),
    orders: vi.fn(),
    paperAutomationStatus: vi.fn(),
    paperAutomationCycles: vi.fn(),
    runNiftyPaperCycle: vi.fn(),
    stopPaperAutomation: vi.fn(),
    resumePaperAutomation: vi.fn(),
    closePosition: vi.fn(),
  },
}));

const status = (enabled: boolean, stopped: boolean): PaperAutomationStatus => ({
  automation: {
    enabled,
    configuredEnabled: true,
    stopped,
    idempotencyWindowMinutes: 5,
    maxLotsPerTrade: 1,
    maxLossPercent: 1,
    maxExposurePercent: 20,
    trackedSignals: 1,
    modelGate: 'NOT_REQUIRED_FOR_DETERMINISTIC_PAPER_TRADING',
  },
  cycle: {
    enabled,
    running: false,
    lastCycle: {
      timestamp: '2026-10-02T10:00:00Z',
      status: 'TRADE_SUBMITTED',
      reason: 'PAPER_ORDER_FILLED',
      direction: 'BULLISH',
      optionType: 'CE',
      expiry: '2026-10-08',
      strike: 23000,
      signalStrength: 68,
      order: null,
    },
  },
});

describe('PaperTradingPage', () => {
  let root: Root;
  let host: HTMLDivElement;

  beforeEach(() => {
    host = document.createElement('div');
    document.body.append(host);
    root = createRoot(host);
    vi.mocked(api.account).mockResolvedValue({
      initialBalance: 100000,
      availableCash: 90737.5,
      usedMargin: 9262.5,
      realizedPnl: 1250,
      unrealizedPnl: 435.5,
      totalPnl: 1685.5,
      dailyPnl: 1670,
    });
    vi.mocked(api.positions).mockResolvedValue([{
      id: 'position-1',
      symbol: 'NIFTY 23000 CE',
      exchange: 'NSE_FO',
      side: 'BUY',
      quantity: 65,
      averageEntryPrice: 142.5,
      currentPrice: 149.2,
      unrealizedPnl: 435.5,
      realizedPnl: 0,
      stopLoss: 135,
      target: 160,
      status: 'OPEN',
      openedAt: '2026-10-02T04:01:12Z',
    }]);
    vi.mocked(api.orders).mockResolvedValue([{
      id: 'order-1',
      symbol: 'NIFTY 23000 CE',
      side: 'BUY',
      quantity: 65,
      requestedPrice: 142.5,
      executionPrice: 142.5,
      status: 'FILLED',
      charges: 15,
      timestamp: '2026-10-02T04:01:12Z',
    }]);
    vi.mocked(api.paperAutomationStatus).mockResolvedValue(status(true, false));
    vi.mocked(api.paperAutomationCycles).mockResolvedValue([{
      id: 'cycle-1',
      timestamp: '2026-10-02T04:00:00Z',
      status: 'TRADE_SUBMITTED',
      reason: 'PAPER_ORDER_FILLED',
      direction: 'BULLISH',
      optionType: 'CE',
      expiry: '2026-10-08',
      strike: 23000,
      signalStrength: 68,
      paperOrderId: 'order-1',
    }]);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.clearAllMocks();
  });

  it('shows account, open holding, execution event, and linked automation decision', async () => {
    await act(async () => {
      root.render(<PaperTradingPage />);
      await Promise.resolve();
    });

    expect(host.textContent).toContain('Virtual capital');
    expect(host.textContent).toContain('NIFTY 23000 CE');
    expect(host.textContent).toContain('₹149.20');
    expect(host.textContent).toContain('Stop loss');
    expect(host.textContent).toContain('BUY');
    expect(host.textContent).toContain('PAPER_ORDER_FILLED');

    const audit = host.querySelector('details.paper-cycle');
    expect(audit).not.toBeNull();
    expect(audit?.querySelector('summary')?.textContent).toContain('NIFTY 68/100');
    await act(async () => audit?.querySelector('summary')?.dispatchEvent(new MouseEvent('click', { bubbles: true })));
    expect(host.textContent).toContain('NIFTY signal strength');
    expect(host.textContent).toContain('68/100');
  });

  it('stops automation from the dashboard and reflects persisted status', async () => {
    let activeStatus = status(true, false);
    vi.mocked(api.paperAutomationStatus).mockImplementation(async () => activeStatus);
    vi.mocked(api.stopPaperAutomation).mockImplementation(async () => {
      activeStatus = status(false, true);
      return activeStatus;
    });
    await act(async () => {
      root.render(<PaperTradingPage />);
      await Promise.resolve();
    });

    const stop = [...host.querySelectorAll('button')].find((button) => button.textContent === 'STOP AUTOMATION');
    expect(stop).toBeDefined();
    await act(async () => {
      stop?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(api.stopPaperAutomation).toHaveBeenCalledOnce();
    expect(host.textContent).toContain('Emergency stop active');
    expect(host.textContent).toContain('Resume automation');
  });

  it('shows the controlled paper entry-to-hold-to-exit event flow', async () => {
    let holding = true;
    let history = [{
      id: 'entry-1',
      symbol: 'NIFTY 23000 CE',
      side: 'BUY',
      quantity: 65,
      requestedPrice: 142.5,
      executionPrice: 142.5,
      status: 'FILLED',
      charges: 15,
      timestamp: '2026-10-02T04:01:12Z',
    }];
    vi.mocked(api.positions).mockImplementation(async () => holding ? [{
      id: 'position-1',
      symbol: 'NIFTY 23000 CE',
      exchange: 'NSE_FO',
      side: 'BUY',
      quantity: 65,
      averageEntryPrice: 142.5,
      currentPrice: 149.2,
      unrealizedPnl: 435.5,
      realizedPnl: 0,
      stopLoss: 135,
      target: 160,
      status: 'OPEN',
      openedAt: '2026-10-02T04:01:12Z',
    }] : []);
    vi.mocked(api.orders).mockImplementation(async () => history);
    vi.mocked(api.closePosition).mockImplementation(async () => {
      holding = false;
      history = [{
        id: 'exit-1',
        symbol: 'NIFTY 23000 CE',
        side: 'SELL',
        quantity: 65,
        requestedPrice: 151.2,
        executionPrice: 151.2,
        status: 'FILLED',
        charges: 15,
        timestamp: '2026-10-02T04:42:00Z',
      }, ...history];
      return history[0];
    });
    await act(async () => {
      root.render(<PaperTradingPage />);
      await Promise.resolve();
    });
    expect(host.textContent).toContain('Currently holding');

    const close = [...host.querySelectorAll('button')].find((button) => button.textContent === 'Close position');
    await act(async () => {
      close?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(api.closePosition).toHaveBeenCalledWith('position-1');
    expect(host.textContent).toContain('SELL');
    expect(host.textContent).toContain('₹151.20');
    expect(host.textContent).toContain('No open paper positions');
  });
});
