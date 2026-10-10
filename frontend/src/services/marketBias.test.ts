import { describe, expect, it } from 'vitest';
import type { MarketDataBundle } from './nseMarketData';
import { calculateMarketBias, DEFAULT_MARKET_BIAS_CONFIG } from './marketBias';
import type { OvernightRetrieveResult } from './overnightMarketData';

function freshQuote(instrument: string, netChange = 100): OvernightRetrieveResult {
  return {
    instrument,
    instrumentKey: `${instrument}|TEST`,
    provider: 'UPSTOX',
    source: 'UPSTOX',
    status: 'FRESH',
    dataTimestamp: new Date().toISOString(),
    retrievedAt: new Date().toISOString(),
    lastPrice: 10100,
    open: 10000,
    high: 10100,
    low: 10000,
    close: 10000,
    netChange,
    volume: 100,
    message: null,
  };
}

function completeBundle(): MarketDataBundle {
  const timestamp = new Date().toISOString();
  return {
    nifty: {
      status: 'live', lastUpdated: Date.now(), latency: 1, error: null,
      data: {
        symbol: 'NIFTY', ltp: 22100, open: 22000, high: 22100, low: 22000,
        previousClose: 22000, change: 100, changePercent: 0.45, volume: 0,
        timestamp, source: 'NSE',
      },
    },
    constituents: { status: 'live', lastUpdated: Date.now(), latency: 1, error: null, data: [] },
    futures: { status: 'live', lastUpdated: Date.now(), latency: 1, error: null, data: null },
    options: { status: 'live', lastUpdated: Date.now(), latency: 1, error: null, data: [] },
    indiaVix: { status: 'live', lastUpdated: Date.now(), latency: 1, error: null, data: null },
    marketStatistics: {
      status: 'live', lastUpdated: Date.now(), latency: 1, error: null,
      data: { advances: 30, declines: 10, unchanged: 10, volume: 0, turnover: 0, timestamp },
    },
    indices: { status: 'live', lastUpdated: Date.now(), latency: 1, error: null, data: {} },
    optionExpiries: { status: 'live', lastUpdated: Date.now(), latency: 1, error: null, data: [] },
    selectedOptionExpiry: '2026-10-13',
    features: {
      timestamp,
      niftyPrice: 22100,
      niftyReturn1m: 0.1, niftyReturn5m: 0.2, niftyReturn10m: 0.2, niftyReturn15m: 0.3,
      niftyOvernightReturn: 0.2, niftyOpeningGap: 0.2, niftyMomentum: 'UP',
      niftyMomentumStatus: 'AVAILABLE', niftyReturn1mStatus: 'AVAILABLE',
      niftyReturn5mStatus: 'AVAILABLE', niftyReturn10mStatus: 'AVAILABLE',
      niftyReturn15mStatus: 'AVAILABLE',
      futuresSymbol: 'NIFTY-FUT', futuresExpiry: '2026-10-27', futuresPrice: 22120,
      futuresOvernightReturn: 0.2, futuresOpeningGap: 0.2, futuresReturn1m: 0.1,
      futuresReturn5m: 0.2, futuresReturn10m: 0.2, futuresReturn15m: 0.3,
      futuresMomentum: 'UP', futuresMomentumStatus: 'AVAILABLE',
      futuresReturn5mStatus: 'AVAILABLE', futuresReturn10mStatus: 'AVAILABLE',
      futuresReturn15mStatus: 'AVAILABLE', futuresChange: 20, futuresChangePercent: 0.2,
      futuresChangeInOpenInterest: 1000, futuresPercentChangeInOpenInterest: 1,
      futuresVolume: 1000, futuresUnderlyingValue: 22100, futuresBasis: 20, futuresBasisPct: 0.09,
      futuresBasisChange15m: 0.1, spotReturn15m: 0.3, futuresLead: 0.1,
      futuresLeadStatus: 'AVAILABLE', futuresOI: 10000, futuresOIChangePct: 1,
      futuresPositioning: 'LONG_BUILDUP', optionExpiry: '2026-10-13',
      totalCallOI: 1000, totalPutOI: 1500, totalCallChangeOI: -100, totalPutChangeOI: 200,
      pcr: 1.5, changeOIPcr: 2, highestPutOIStrike: 22000, highestCallOIStrike: 22300,
      highestPutChangeOIStrike: 22000, highestCallChangeOIStrike: 22300,
      supportLevel: 22000, resistanceLevel: 22300, distanceFromSupport: 100,
      distanceFromResistance: 200, supportLabel: 'OPTION_OI_SUPPORT',
      resistanceLabel: 'OPTION_OI_RESISTANCE', callOiConcentration: 0.3,
      putOiConcentration: 0.5, dominantCallOiStrike: 22300, dominantPutOiStrike: 22000,
    },
    premarket: {
      status: 'live', lastUpdated: Date.now(), latency: 1, error: null,
      data: {
        retrievedAt: Date.now(),
        indiaVix: {
          symbol: 'INDIA VIX', ltp: 13, change: -0.2, changePercent: -1, open: 13.2,
          high: 13.3, low: 12.9, previousClose: 13.2, timestamp,
        },
        indiaVixStatus: 'live', indiaVixError: null,
        fiiDii: [
          { category: 'FII/FPI', date: '07-Oct-2026', buyValue: 2000, sellValue: 1000, netValue: 1000 },
          { category: 'DII', date: '07-Oct-2026', buyValue: 2000, sellValue: 1000, netValue: 1000 },
        ],
        fiiDiiStatus: 'live', fiiDiiError: null,
      },
    },
  };
}

describe('calculateMarketBias', () => {
  it('requires complete, aligned evidence before declaring a paper trade ready', () => {
    const quotes = Object.fromEntries(
      ['GIFT_NIFTY', 'SP500', 'US_TECH_100', 'DOW', 'NIKKEI', 'HANG_SENG', 'FTSE_100', 'DAX',
        'USD_INR', 'BRENT', 'CRUDE_OIL']
        .map((instrument) => [instrument, freshQuote(
          instrument,
          instrument === 'USD_INR' || instrument === 'BRENT' || instrument === 'CRUDE_OIL' ? -100 : 100,
        )]),
    ) as Record<string, OvernightRetrieveResult>;
    quotes.GIFT_NIFTY.lastPrice = 22100;

    const result = calculateMarketBias(completeBundle(), quotes);

    expect(result.label).toBe('STRONG BULLISH');
    expect(result.outlook).toBe('STRONG UP');
    expect(result.score).toBeCloseTo(2.74, 1);
    expect(result.dataQualityPct).toBe(100);
    expect(result.bullishFactors).toBe(22);
    expect(result.bearishFactors).toBe(1);
    expect(result.neutralFactors).toBe(1);
    expect(result.tradeReady).toBe(true);
    expect(result.groups).toHaveLength(5);
  });

  it('fails closed when global and source-quality inputs are missing', () => {
    const result = calculateMarketBias(null, {});

    expect(result.label).toBe('UNAVAILABLE');
    expect(result.outlook).toBe('INSUFFICIENT DATA');
    expect(result.dataQualityPct).toBe(0);
    expect(result.tradeReady).toBe(false);
    expect(result.tradeReadiness).toContain('NO CLEAR EDGE');
    expect(result.groups.every((group) => group.direction === 'UNAVAILABLE')).toBe(true);
  });

  it('uses configurable thresholds and rejects a strong opposing-group conflict', () => {
    const bundle = completeBundle();
    bundle.features!.niftyReturn5m = -0.3;
    bundle.features!.niftyReturn15m = -0.3;
    bundle.features!.niftyOpeningGap = -0.3;
    bundle.features!.niftyReturn5mStatus = 'AVAILABLE';
    bundle.features!.niftyReturn15mStatus = 'AVAILABLE';
    bundle.nifty.data!.changePercent = -0.3;
    bundle.premarket!.data!.indiaVix!.change = 0.2;
    bundle.premarket!.data!.indiaVix!.changePercent = 1;
    const quotes = Object.fromEntries(
      ['GIFT_NIFTY', 'SP500', 'US_TECH_100', 'DOW', 'NIKKEI', 'HANG_SENG', 'FTSE_100', 'DAX',
        'USD_INR', 'BRENT', 'CRUDE_OIL']
        .map((instrument) => [instrument, freshQuote(
          instrument,
          instrument === 'USD_INR' || instrument === 'BRENT' || instrument === 'CRUDE_OIL' ? -100 : 100,
        )]),
    ) as Record<string, OvernightRetrieveResult>;
    quotes.GIFT_NIFTY.lastPrice = 22100;
    const result = calculateMarketBias(bundle, quotes, {
      ...DEFAULT_MARKET_BIAS_CONFIG,
      maximumOpposingGroups: 0,
    });

    expect(result.groups.find((group) => group.key === 'TECHNICAL')?.direction).toBe('BEARISH');
    expect(result.tradeReady).toBe(false);
    expect(result.label).toBe('MIXED');
    expect(result.outlook).toBe('SIDEWAYS / MIXED');
  });

  it('does not call the outlook strong unless data quality and group alignment pass their gates', () => {
    const result = calculateMarketBias(completeBundle(), {}, {
      ...DEFAULT_MARKET_BIAS_CONFIG,
      minimumDataQualityPct: 101,
    });

    expect(result.label).toBe('BULLISH');
    expect(result.outlook).toBe('UP');
  });
});
