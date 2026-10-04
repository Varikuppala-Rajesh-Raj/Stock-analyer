// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  api,
  type PaperDryRunCoverage,
  type PaperDryRunResult,
  type PaperDryRunSettings,
} from '../services/api';
import { PaperDryRunPage } from './PaperDryRunPage';

Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, value: true });

vi.mock('../services/api', () => ({
  api: {
    paperDryRunSettings: vi.fn(),
    paperDryRunCoverage: vi.fn(),
    runPaperDryRun: vi.fn(),
  },
}));

const settings: PaperDryRunSettings = {
  defaultVirtualCapital: 100000,
  defaultLotSize: 65,
  maxLossPercent: 1,
  maxExposurePercent: 20,
  maxStopLossPercent: 30,
  rewardRiskMultiple: 2,
  minRiskReward: 1.5,
  maxAllocationPerTrade: 5000,
  maxDailyLoss: 300,
  maxOpenPositions: 10,
  maxTradesPerDay: 50,
  maxHoldingMinutes: 240,
  maxSpreadPercent: 3,
  minVolume: 100,
  minOpenInterest: 1000,
  maxAtmDistancePercent: 1,
  minAbsDelta: 0.25,
  maxAbsDelta: 0.75,
  slippagePercent: 0,
  brokerageRate: 0.0003,
};

const coverageFor = (date: string, ready: boolean): PaperDryRunCoverage => ({
  date,
  timezone: 'Asia/Kolkata',
  status: ready ? 'DATA_SUFFICIENT' : 'DATA_INSUFFICIENT',
  expectedIntervals: 75,
  requiredIntervals: 72,
  minimumCoveragePercent: 95,
  optionSnapshotRows: ready ? 194 * 75 : 0,
  optionSnapshotIntervals: ready ? 75 : 0,
  optionIntervals: ready ? 75 : 0,
  spotIntervals: ready ? 75 : 0,
  spotConfirmationIntervals: ready ? 74 : 0,
  futuresIntervals: ready ? 75 : 0,
  firstSnapshot: ready ? '2026-10-02T03:50:00Z' : null,
  lastSnapshot: ready ? '2026-10-02T10:00:00Z' : null,
  firstSpotCandle: ready ? '2026-10-02T03:45:00Z' : null,
  lastSpotCandle: ready ? '2026-10-02T09:55:00Z' : null,
  firstFuturesObservation: ready ? '2026-10-02T03:45:00Z' : null,
  lastFuturesObservation: ready ? '2026-10-02T10:00:00Z' : null,
  futuresObservations: ready ? 76 : 0,
  spotSource: 'PERSISTED_M5_CANDLES',
  coveragePercent: ready ? 98.7 : 0,
  missingOptionIntervals: ready ? [] : ['09:20'],
  missingSpotIntervals: ready ? [] : ['09:15'],
  missingSpotConfirmationIntervals: ready ? ['09:20'] : ['09:20', '09:25'],
  missingFuturesIntervals: ready ? [] : ['09:20'],
  ready,
  note: ready ? 'Historical data was recorded.' : 'No saved historical data.',
});

const result: PaperDryRunResult = {
  date: '2026-10-02',
  generatedAt: '2026-10-03T00:00:00Z',
  virtualCapital: 100000,
  lotSize: 65,
  settings,
  data: {
    optionSnapshotRows: 194 * 75,
    optionSnapshotIntervals: 75,
    futuresObservations: 76,
    niftyCandles: 300,
    maxCandlesAvailableAtDecision: 250,
    replayResolution: 'Recorded option snapshots',
  },
  summary: {
    cycles: 1,
    tradeEntries: 0,
    closedTrades: 0,
    openTrades: 0,
    noTradeCycles: 1,
    winningClosedTrades: 0,
    losingClosedTrades: 0,
    realizedGrossPnl: 0,
    charges: 0,
    realizedNetPnl: 0,
    unrealizedPnl: 0,
    totalPnl: 0,
    returnPercent: 0,
    endingEquity: 100000,
  },
  trades: [],
  cycles: [{
    timestamp: '2026-10-02T04:00:00Z',
    status: 'NO_TRADE',
    direction: null,
    optionType: null,
    strike: null,
    expiry: null,
    signalStrength: null,
    reason: 'NO_CLEAR_EDGE_OR_FUTURES_DIVERGENCE',
    selectedContract: null,
  }],
  isHistoricalSimulation: true,
  note: 'No account or broker was accessed.',
};

describe('PaperDryRunPage', () => {
  let root: Root;
  let host: HTMLDivElement;

  beforeEach(() => {
    host = document.createElement('div');
    document.body.append(host);
    root = createRoot(host);
    vi.mocked(api.paperDryRunSettings).mockResolvedValue(settings);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.clearAllMocks();
  });

  it('blocks replay when historical option or futures data is missing', async () => {
    vi.mocked(api.paperDryRunCoverage).mockImplementation(async (date) => ({
      ...coverageFor(date, false),
    }));

    await act(async () => {
      root.render(<PaperDryRunPage />);
      await Promise.resolve();
    });

    await act(async () => {
      [...host.querySelectorAll('button')]
        .find((button) => button.textContent === 'Check date coverage')
        ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await Promise.resolve();
    });

    expect(host.textContent).toContain('DATA INSUFFICIENT');
    expect(host.textContent).toContain('No saved historical data.');
    expect(api.runPaperDryRun).not.toHaveBeenCalled();
  });

  it('replays only after coverage is ready and renders the decision and P&L results', async () => {
    vi.mocked(api.paperDryRunCoverage).mockImplementation(async (date) => ({
      ...coverageFor(date, true),
    }));
    vi.mocked(api.runPaperDryRun).mockResolvedValue(result);

    await act(async () => {
      root.render(<PaperDryRunPage />);
      await Promise.resolve();
    });

    await act(async () => {
      [...host.querySelectorAll('button')]
        .find((button) => button.textContent === 'Check date coverage')
        ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await Promise.resolve();
    });
    await act(async () => {
      [...host.querySelectorAll('button')]
        .find((button) => button.textContent === 'Run dry run')
        ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(api.runPaperDryRun).toHaveBeenCalledWith(
      host.querySelector<HTMLInputElement>('input[type="date"]')?.value,
      100000,
      65,
    );
    expect(host.textContent).toContain('Automated trading dry run');
    expect(host.textContent).toContain('₹0.00');
    expect(host.textContent).toContain('NO_CLEAR_EDGE_OR_FUTURES_DIVERGENCE');
    expect(host.textContent).toContain('No account or broker was accessed.');
  });
});
