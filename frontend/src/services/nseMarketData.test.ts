import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  computeBreadth,
  computeConstituentMarketStatistics,
  computeChangeOiPcr,
  computeFutureBasis,
  computePcr,
  computeReturn,
  classifyMomentum,
  computeWeightedConstituentReturn,
  computeWeightedAttribution,
  computeNiftyPressure,
  computeNiftyIndexWindowReturns,
  parseNiftyIndexCandles,
  computeMarketDirection,
  estimateSupportResistance,
  classifyFuturesPositioning,
  isMarketDataStale,
  NseMarketDataProvider,
  buildBackendFeatureSnapshot,
  parseNseExpiry,
  selectAtmOptions,
  selectNearestExpiry,
  type FuturesSnapshot,
  type MarketDataBundle,
  type NiftyMarketData,
  type BackendNseMarketContext,
  type OptionsSnapshot,
} from './nseMarketData';
import { NIFTY50_MEMBERS, NIFTY50_SECTORS, NIFTY50_WEIGHTS } from '../data/nifty50';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

const testThresholds = {
  momentumDirectionPct: 0.05,
  momentumStrongPct: 0.25,
  futuresPriceMovePct: 0.02,
  futuresOiChangePct: 0.1,
};

function recentNseTimestamp(): string {
  const ist = new Date(Date.now() + 330 * 60_000);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${pad(ist.getUTCDate())}-${months[ist.getUTCMonth()]}-${ist.getUTCFullYear()} ${pad(ist.getUTCHours())}:${pad(ist.getUTCMinutes())}:${pad(ist.getUTCSeconds())}`;
}

function makeBundle(
  niftyPrice: number,
  options: OptionsSnapshot[] = [],
  futuresValues?: { price: number; oi: number }
): MarketDataBundle {
  const nifty: NiftyMarketData = {
    symbol: 'NIFTY 50', ltp: niftyPrice, open: niftyPrice, high: niftyPrice,
    low: niftyPrice, previousClose: niftyPrice, change: 0, changePercent: 0,
    volume: 1_000, timestamp: new Date().toISOString(), source: 'NSE',
  };
  const futures: FuturesSnapshot | null = futuresValues ? {
    contract: 'FUTIDXNIFTY06-10-2026XX0.00', identifier: 'FUTIDXNIFTY06-10-2026XX0.00',
    instrumentType: 'FUTIDX', expiry: '06-Oct-2026', ltp: futuresValues.price,
    open: futuresValues.price, high: futuresValues.price, low: futuresValues.price,
    previousClose: futuresValues.price, change: 0, changePercent: 0, volume: 1_000,
    turnover: 0, openInterest: futuresValues.oi, changeInOpenInterest: 0,
    percentChangeInOpenInterest: (futuresValues.oi - 1_000) / 10,
    underlying: 'NIFTY', underlyingValue: niftyPrice, timestamp: new Date().toISOString(),
  } : null;
  const state = <T,>(data: T | null) => ({
    status: data == null ? 'no-data' as const : 'live' as const,
    lastUpdated: data == null ? null : Date.now(), latency: 0, error: null, data,
  });

  return {
    nifty: state(nifty),
    constituents: state([]),
    futures: state(futures),
    options: state(options),
    indiaVix: state(null),
    marketStatistics: state(null),
    indices: state({}),
    optionExpiries: state(['06-Oct-2026']),
    selectedOptionExpiry: '06-Oct-2026',
    features: null,
  };
}

function makeBackendContext(spot = 100): BackendNseMarketContext {
  const metric = (value: number, status: 'AVAILABLE' | 'COLLECTING' | 'STALE' | 'UNAVAILABLE' = 'AVAILABLE') => ({ value, status });
  const timestamp = '30-Sep-2026 14:30:00';
  return {
    timestamp,
    nifty: {
      timestamp, ltp: spot, open: spot, high: spot, low: spot, previousClose: spot,
      change: 0, changePercent: 0, overnightReturn: 0.35, openingGap: 0.35,
      return1m: metric(0.02), return5m: metric(-0.08),
      return10m: metric(-0.11), return15m: metric(-0.15), status: 'AVAILABLE',
    },
    futures: {
      timestamp, identifier: 'FUTIDXNIFTY27-10-2026XX0.00', instrumentType: 'FUTIDX',
      expiry: '27-Oct-2026', ltp: spot + 111.15, open: spot, high: spot, low: spot,
      previousClose: spot, change: 0, changePercent: 0, overnightReturn: 0.35, openingGap: 0.35,
      openInterest: 270_137, changeInOpenInterest: 6518, percentChangeInOpenInterest: 2.47, volume: 34_542,
      turnover: 0, underlying: 'NIFTY', underlyingValue: spot, basis: 111.15,
      return5m: metric(-0.05), return10m: metric(-0.08), return15m: metric(-0.12), status: 'AVAILABLE',
    },
    futuresLead: metric(0.03),
  };
}

describe('nse market data helpers', () => {
  it('parses same-day NIFTY candles and calculates 5m, 10m, 15m, and 30m index returns', () => {
    const now = Date.parse('2026-10-05T08:30:00.000Z');
    const start = Date.parse('2026-10-05T07:30:00.000Z');
    const payload = {
      grapthData: Array.from({ length: 61 }, (_, index) => [
        start + index * 60_000,
        100,
        103,
        99,
        index <= 30 ? 100 : 100 + (index - 30) / 10,
        1_000,
      ]),
    };

    const candles = parseNiftyIndexCandles(payload, now);
    const returns = computeNiftyIndexWindowReturns(candles, null, now);

    expect(candles).toHaveLength(61);
    expect(returns[5]).toBeCloseTo((103 - 102.5) / 102.5 * 100);
    expect(returns[10]).toBeCloseTo((103 - 102) / 102 * 100);
    expect(returns[15]).toBeCloseTo((103 - 101.5) / 101.5 * 100);
    expect(returns[30]).toBeCloseTo(3);
  });

  it('does not use yesterday, stale, or poorly aligned NIFTY index candles', () => {
    const now = Date.parse('2026-10-05T08:30:00.000Z');
    const yesterday = Date.parse('2026-10-04T08:00:00.000Z');
    const candles = parseNiftyIndexCandles({
      grapthData: [
        [yesterday, 100, 100, 100, 100, 1],
        [Date.parse('2026-10-05T08:20:00.000Z'), 100, 100, 100, 100, 1],
        [Date.parse('2026-10-05T08:29:00.000Z'), 100, 100, 100, 101, 1],
      ],
    }, now);

    expect(candles).toHaveLength(2);
    expect(computeNiftyIndexWindowReturns(candles, null, now)).toEqual({
      5: null, 10: null, 15: null, 30: null,
    });
  });

  it('falls back to Upstox intraday candles when NSE index history is unavailable', async () => {
    const latestTimestamp = new Date(Date.now() - 60_000).toISOString();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 503, statusText: 'Unavailable' })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [{
          timestamp: latestTimestamp, open: 100, high: 101, low: 99, close: 100.5, volume: 1_000,
        }],
      });
    vi.stubGlobal('fetch', fetchMock);
      const provider = new NseMarketDataProvider(
      'http://localhost:8080/api/nse',
      'http://localhost:8080/api'
    );

    const result = await provider.getNiftyIndexCandles();

    expect(result.source).toBe('UPSTOX');
    expect(result.candles).toHaveLength(1);
    expect(fetchMock).toHaveBeenNthCalledWith(1, 'http://localhost:8080/api/nse/index-candles', expect.any(Object));
    expect(fetchMock).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/market/nifty-index-intraday', expect.any(Object));
  });

  it('combines constituent pressure with spot and futures confirmation', () => {
    const pressure = computeNiftyPressure([
      { timestamp: 0, niftyPrice: 100, constituents: [{ symbol: 'A', ltp: 100, open: 100, weight: 100 }] },
      { timestamp: 5 * 60_000, niftyPrice: 99.5, constituents: [{ symbol: 'A', ltp: 99.5, open: 100, weight: 100 }] },
    ]);
    const bundle = makeBundle(100);
    const features = buildBackendFeatureSnapshot(bundle, makeBackendContext());
    bundle.features = features && {
      ...features,
      supportLevel: 95,
      resistanceLevel: 105,
      distanceFromSupport: 5,
      distanceFromResistance: 5,
    };

    const analysis = computeMarketDirection(pressure, bundle);

    expect(analysis.score).toBe(-75);
    expect(analysis.direction).toBe('DOWN');
    expect(analysis.strength).toBe('STRONG');
    expect(analysis.futuresConfirmation).toBe('CONFIRMS');
    expect(analysis.spotConfirmation).toBe('CONFIRMS');
    expect(analysis.nearestStructure).toBe('SUPPORT');
    expect(analysis.supportLevel).toBe(95);
    expect(analysis.resistanceLevel).toBe(105);
  });

  it('does not let PCR or OI structure change the direction score', () => {
    const pressure = computeNiftyPressure([
      { timestamp: 0, niftyPrice: 100, constituents: [{ symbol: 'A', ltp: 100, open: 100, weight: 100 }] },
      { timestamp: 5 * 60_000, niftyPrice: 99.5, constituents: [{ symbol: 'A', ltp: 99.5, open: 100, weight: 100 }] },
    ]);
    const bundle = makeBundle(100);
    const features = buildBackendFeatureSnapshot(bundle, makeBackendContext());
    bundle.features = features;
    const before = computeMarketDirection(pressure, bundle);
    if (bundle.features) {
      bundle.features = { ...bundle.features, pcr: 99, changeOIPcr: 0, supportLevel: 1, resistanceLevel: 999 };
    }

    const after = computeMarketDirection(pressure, bundle);

    expect(after.score).toBe(before.score);
    expect(after.direction).toBe(before.direction);
  });

  it('does not publish a direction without available constituent pressure', () => {
    const analysis = computeMarketDirection(null, makeBundle(100));

    expect(analysis.direction).toBe('COLLECTING');
    expect(analysis.score).toBeNull();
    expect(analysis.strength).toBe('COLLECTING');
    expect(analysis.constituentPressureWindowMinutes).toBeNull();
  });

  it('uses fresh one-minute constituent pressure while the five-minute window collects', () => {
    const pressure = computeNiftyPressure([
      { timestamp: 0, niftyPrice: 100, constituents: [{ symbol: 'A', ltp: 100, open: 100, weight: 100 }] },
      { timestamp: 60_000, niftyPrice: 100.02, constituents: [{ symbol: 'A', ltp: 100.02, open: 100, weight: 100 }] },
    ]);
    const bundle = makeBundle(100);
    const features = buildBackendFeatureSnapshot(bundle, makeBackendContext());
    bundle.features = features && {
      ...features,
      niftyReturn5mStatus: 'COLLECTING',
      futuresReturn5mStatus: 'COLLECTING',
    };

    const analysis = computeMarketDirection(pressure, bundle);

    expect(pressure?.horizons[1]?.coveredWeightPercent).toBe(100);
    expect(pressure?.horizons[5]).toBeNull();
    expect(analysis.direction).toBe('UP');
    expect(analysis.score).toBe(100);
    expect(analysis.constituentPressureWindowMinutes).toBe(1);
    expect(analysis.spotConfirmation).toBe('COLLECTING');
    expect(analysis.futuresConfirmation).toBe('COLLECTING');
  });

  it('marks stale spot and futures as stale and excludes them from the score', () => {
    const pressure = computeNiftyPressure([
      { timestamp: 0, niftyPrice: 100, constituents: [{ symbol: 'A', ltp: 100, open: 100, weight: 100 }] },
      { timestamp: 5 * 60_000, niftyPrice: 99.5, constituents: [{ symbol: 'A', ltp: 99.5, open: 100, weight: 100 }] },
    ]);
    const bundle = makeBundle(100);
    const features = buildBackendFeatureSnapshot(bundle, makeBackendContext());
    bundle.features = features && {
      ...features,
      niftyReturn5mStatus: 'STALE',
      futuresReturn5mStatus: 'STALE',
    };

    const analysis = computeMarketDirection(pressure, bundle);

    expect(analysis.score).toBe(-100);
    expect(analysis.spotConfirmation).toBe('STALE');
    expect(analysis.futuresConfirmation).toBe('STALE');
  });

  it('splits constituent pressure and detects increasing upside momentum', () => {
    const samples = [
      { timestamp: 0, niftyPrice: 25_000, constituents: [
        { symbol: 'A', ltp: 100, open: 100, weight: 50 },
        { symbol: 'B', ltp: 100, open: 100, weight: 50 },
      ] },
      { timestamp: 5 * 60_000, niftyPrice: 25_010, constituents: [
        { symbol: 'A', ltp: 100.5, open: 100, weight: 50 },
        { symbol: 'B', ltp: 99.8, open: 100, weight: 50 },
      ] },
      { timestamp: 10 * 60_000, niftyPrice: 25_015, constituents: [
        { symbol: 'A', ltp: 100.4, open: 100, weight: 50 },
        { symbol: 'B', ltp: 100.4, open: 100, weight: 50 },
      ] },
    ];

    const pressure = computeNiftyPressure(samples);

    expect(pressure?.horizons[5]?.upContributionPercentPoints).toBeCloseTo(0.3006, 3);
    expect(pressure?.horizons[5]?.downContributionPercentPoints).toBeCloseTo(0.0498, 3);
    expect(pressure?.direction).toBe('UP');
    expect(pressure?.trend).toBe('ACCELERATING');
    expect(pressure?.accelerationPercentPoints).toBeCloseTo(0.1009, 3);
    expect(pressure?.expected15mRange).toBeNull();
  });

  it('flags an upside reversal against session pressure', () => {
    const samples = [
      { timestamp: 0, niftyPrice: 25_000, constituents: [{ symbol: 'A', ltp: 99, open: 100, weight: 100 }] },
      { timestamp: 5 * 60_000, niftyPrice: 25_005, constituents: [{ symbol: 'A', ltp: 99.2, open: 100, weight: 100 }] },
      { timestamp: 10 * 60_000, niftyPrice: 25_010, constituents: [{ symbol: 'A', ltp: 99.5, open: 100, weight: 100 }] },
    ];

    const pressure = computeNiftyPressure(samples);

    expect(pressure?.session.netPressurePercentPoints).toBeLessThan(0);
    expect(pressure?.horizons[5]?.netPressurePercentPoints).toBeGreaterThan(0);
    expect(pressure?.trend).toBe('REVERSAL_UP');
  });

  it('keeps direction collecting without enough horizon or volatility samples', () => {
    const pressure = computeNiftyPressure([{
      timestamp: 0,
      niftyPrice: 25_000,
      constituents: [{ symbol: 'A', ltp: 100, open: 100, weight: 100 }],
    }]);

    expect(pressure?.direction).toBe('COLLECTING');
    expect(pressure?.trend).toBe('COLLECTING');
    expect(pressure?.horizons[5]).toBeNull();
    expect(pressure?.expected15mRange).toBeNull();
  });

  it('estimates a fifteen-minute range only from sufficient NIFTY samples', () => {
    const samples = Array.from({ length: 16 }, (_, index) => ({
      timestamp: index * 30_000,
      niftyPrice: 25_000 + index * (index % 2 ? 8 : -5),
      constituents: [{ symbol: 'A', ltp: 100, open: 100, weight: 100 }],
    }));

    const pressure = computeNiftyPressure(samples);

    expect(pressure?.expected15mRange?.sigmaPercent).toBeGreaterThan(0);
    expect(pressure?.expected15mRange?.low).toBeLessThan(pressure?.expected15mRange?.high ?? 0);
  });

  it('calculates 1m, 5m and 15m returns', () => {
    expect(computeReturn(101, 100)).toBeCloseTo(1, 5);
    expect(computeReturn(105, 100)).toBeCloseTo(5, 5);
    expect(computeReturn(115, 100)).toBeCloseTo(15, 5);
  });

  it('calculates breadth metrics', () => {
    const breadth = computeBreadth({
      advances: 250,
      declines: 180,
      unchanged: 20,
      constituents: [
        { symbol: 'A', changePercent: 2.6 },
        { symbol: 'B', changePercent: -1.4 },
        { symbol: 'C', changePercent: 0.2 },
        { symbol: 'D', changePercent: -2.1 },
      ],
    });

    expect(breadth.advances).toBe(250);
    expect(breadth.declines).toBe(180);
    expect(breadth.unchanged).toBe(20);
    expect(breadth.advanceDeclineRatio).toBeCloseTo(1.3889, 4);
    expect(breadth.averageConstituentReturn).toBeCloseTo(-0.175, 3);
  });

  it('derives market breadth from the constituent quote endpoint fields', () => {
    const stats = computeConstituentMarketStatistics([
      { symbol: 'A', ltp: 101, previousClose: 100, change: 1, changePercent: 1, volume: 12 },
      { symbol: 'B', ltp: 99, previousClose: 100, change: -1, changePercent: -1, volume: 20 },
      { symbol: 'C', ltp: 100, previousClose: 100, change: 0, changePercent: 0, volume: 8 },
      { symbol: 'D', ltp: 100, previousClose: null, change: null, changePercent: null, volume: null },
    ], '2026-10-08T10:10:00.000Z');

    expect(stats).toEqual({
      advances: 1,
      declines: 1,
      unchanged: 1,
      volume: 40,
      turnover: null,
      timestamp: '2026-10-08T10:10:00.000Z',
    });
    expect(computeConstituentMarketStatistics([], '2026-10-08T10:10:00.000Z')).toBeNull();
  });

  it('calculates weighted constituent return', () => {
    const weighted = computeWeightedConstituentReturn([
      { symbol: 'A', changePercent: 2.5, weight: 0.4 },
      { symbol: 'B', changePercent: -1.0, weight: 0.3 },
      { symbol: 'C', changePercent: 0.75, weight: 0.3 },
    ]);

    expect(weighted).toBeCloseTo(0.925, 3);
  });

  it('calculates futures basis and pct', () => {
    const basis = computeFutureBasis(24500, 24400);
    expect(basis.price).toBe(100);
    expect(basis.percent).toBeCloseTo(0.409836, 4);
    expect(computeFutureBasis(22893.9, 22782.75).price).toBeCloseTo(111.15, 2);
    expect(computeFutureBasis(24500, 0).percent).toBe(0);
  });

  it('classifies futures positioning', () => {
    expect(classifyFuturesPositioning(1, 1)).toBe('LONG_BUILDUP');
    expect(classifyFuturesPositioning(-1, 1)).toBe('SHORT_BUILDUP');
    expect(classifyFuturesPositioning(1, -1)).toBe('SHORT_COVERING');
    expect(classifyFuturesPositioning(-1, -1)).toBe('LONG_UNWINDING');
    expect(classifyFuturesPositioning(0.01, 0.02)).toBe('NEUTRAL');
    expect(classifyFuturesPositioning(null, null)).toBe('NEUTRAL');
  });

  it('classifies momentum with configurable thresholds', () => {
    expect(classifyMomentum(0.3, testThresholds)).toBe('STRONG_UP');
    expect(classifyMomentum(0.1, testThresholds)).toBe('UP');
    expect(classifyMomentum(0.01, testThresholds)).toBe('NEUTRAL');
    expect(classifyMomentum(-0.1, testThresholds)).toBe('DOWN');
    expect(classifyMomentum(-0.3, testThresholds)).toBe('STRONG_DOWN');
    expect(classifyMomentum(null, testThresholds)).toBe('NEUTRAL');
  });

  it('calculates PCR values', () => {
    expect(computePcr(1500, 500)).toBeCloseTo(1 / 3, 5);
    expect(computeChangeOiPcr(1200, 400)).toBeCloseTo(1 / 3, 5);
    expect(computePcr(0, 500)).toBe(0);
    expect(computeChangeOiPcr(0, 400)).toBe(0);
  });

  it('maps the NSE index response fields', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [{
          indexName: 'NIFTY 50',
          open: 22732.45,
          high: 22753.25,
          low: 22569.65,
          last: 22716.2,
          previousClose: 22780.25,
          percChange: -0.28,
          timeVal: '29-Sep-2026 15:30',
        }],
      }),
    }));

    const result = await new NseMarketDataProvider('http://localhost:8080/api/nse').getNifty();

    expect(result.data?.symbol).toBe('NIFTY 50');
    expect(result.data?.ltp).toBe(22716.2);
    expect(result.data?.change).toBeCloseTo(-64.05, 2);
    expect(result.data?.changePercent).toBe(-0.28);
    expect(result.data?.timestamp).toBe('29-Sep-2026 15:30');
  });

  it('loads timestamped GIFT NIFTY data from the backend route', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-08T09:30:00Z'));
    const timestamp = recentNseTimestamp();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        timestamp,
        data: [{ symbol: 'GIFT NIFTY', lastPrice: '25,123.50', previousClose: '25,000.00' }],
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getGiftNifty();

    expect(fetchMock).toHaveBeenCalledWith('http://localhost/api/nse/gift-nifty', expect.any(Object));
    expect(result.status).toBe('live');
    expect(result.data).toMatchObject({
      symbol: 'GIFT NIFTY',
      ltp: 25123.5,
      previousClose: 25000,
      change: 123.5,
      changePercent: 0.494,
    });
  });

  it('loads and validates the combined India VIX and FII/DII premarket route', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-08T09:30:00Z'));
    const reportDate = new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Kolkata',
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }).format(Date.now() - 24 * 60 * 60_000).replaceAll(' ', '-');
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        indiaVix: {
          symbol: 'INDIA VIX',
          last: '13.59',
          change: '-1.19',
          percentChange: '-8.03',
          open: '14.78',
          high: '14.78',
          low: '13.52',
          previousClose: '14.78',
          timestamp: recentNseTimestamp(),
        },
        fiiDii: {
          value: [
            { category: 'DII', date: reportDate, buyValue: '20,146.40', sellValue: '15,057.48', netValue: '5,088.92' },
            { category: 'FII/FPI', date: reportDate, buyValue: '11,258.72', sellValue: '14,220.02', netValue: '-2,961.30' },
          ],
        },
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getPremarketFeeds();

    expect(fetchMock).toHaveBeenCalledWith('http://localhost/api/nse/premarket', expect.any(Object));
    expect(result.status).toBe('live');
    expect(result.data?.indiaVix).toMatchObject({ ltp: 13.59, change: -1.19, changePercent: -8.03 });
    expect(result.data?.indiaVixStatus).toBe('live');
    expect(result.data?.fiiDii).toEqual([
      { category: 'DII', date: reportDate, buyValue: 20146.4, sellValue: 15057.48, netValue: 5088.92 },
      { category: 'FII/FPI', date: reportDate, buyValue: 11258.72, sellValue: 14220.02, netValue: -2961.3 },
    ]);
    expect(result.data?.fiiDiiStatus).toBe('report-collected');
  });

  it('preserves valid India VIX data when the FII/DII payload is invalid', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-08T09:30:00Z'));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        indiaVix: {
          last: 13.59, change: -1.19, percentChange: -8.03, open: 14.78, high: 14.78,
          low: 13.52, previousClose: 14.78, timestamp: recentNseTimestamp(),
        },
        fiiDii: { value: [
          { category: 'DII', date: '06-Oct-2026', buyValue: 1, sellValue: 2, netValue: -1 },
        ] },
      }),
    }));

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getPremarketFeeds();

    expect(result.status).toBe('error');
    expect(result.data?.indiaVix).toMatchObject({ ltp: 13.59 });
    expect(result.data?.indiaVixStatus).toBe('live');
    expect(result.data?.fiiDiiStatus).toBe('error');
    expect(result.data?.fiiDiiError).toContain('missing valid FII/FPI or DII records');
  });

  it('renders a VIX quote without claiming freshness when NSE omits its source timestamp', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        indiaVix: {
          symbol: 'INDIA VIX',
          last: 13.69,
          variation: 0.08,
          percentChange: 0.56,
          open: 13.61,
          high: 14.32,
          low: 13.61,
          previousClose: 13.61,
        },
        fiiDiiError: 'NSE provider request failed with HTTP 502.',
      }),
    }));

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getPremarketFeeds();

    expect(result.status).toBe('error');
    expect(result.data?.indiaVix).toMatchObject({ ltp: 13.69, timestamp: null });
    expect(result.data?.indiaVixStatus).toBe('stale');
    expect(result.data?.indiaVixError).toContain('freshness cannot be verified');
    expect(result.data?.fiiDiiStatus).toBe('error');
    expect(result.data?.fiiDiiError).toContain('HTTP 502');
  });

  it('does not treat GIFT NIFTY quotes without a source timestamp as live', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ data: [{ symbol: 'GIFT NIFTY', lastPrice: 25123.5 }] }),
    }));

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getGiftNifty();

    expect(result.status).toBe('no-data');
    expect(result.data).toBeNull();
  });

  it('maps the verified NSE futures quote fields and request path', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-08T09:30:00Z'));
    const timestamp = recentNseTimestamp();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [{
          change: 28.2,
          changeinOpenInterest: 6518,
          expiryDate: '27-Oct-2026',
          highPrice: 22935,
          identifier: 'FUTIDXNIFTY27-10-2026XX0.00',
          instrumentType: 'FUTIDX',
          lastPrice: 22893.9,
          lowPrice: 22776.6,
          openInterest: 270137,
          openPrice: 22839.9,
          pchange: 0.12332882876972934,
          pchangeinOpenInterest: 2.472507672057022,
          prevClose: 22865.7,
          totalTradedVolume: 34542,
          totalTurnover: 51310869854.4,
          underlying: 'NIFTY',
          underlyingValue: 22782.75,
        }],
        timestamp,
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getFutures();

    expect(result.status).toBe('live');
    expect(result.data).toMatchObject({
      identifier: 'FUTIDXNIFTY27-10-2026XX0.00',
      instrumentType: 'FUTIDX',
      expiry: '27-Oct-2026',
      ltp: 22893.9,
      open: 22839.9,
      high: 22935,
      low: 22776.6,
      previousClose: 22865.7,
      change: 28.2,
      changePercent: 0.12332882876972934,
      openInterest: 270137,
      changeInOpenInterest: 6518,
      percentChangeInOpenInterest: 2.472507672057022,
      volume: 34542,
      turnover: 51310869854.4,
      underlying: 'NIFTY',
      underlyingValue: 22782.75,
      timestamp,
    });
    expect(fetchMock).toHaveBeenCalledWith('http://localhost/api/nse/futures', expect.any(Object));
  });

  it('gets rolling momentum from the backend Market Context endpoint', async () => {
    const context = makeBackendContext(22_782.75);
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => context });
    vi.stubGlobal('fetch', fetchMock);

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getMarketContext();

    expect(result.status).toBe('live');
    expect(result.data?.nifty?.return5m).toEqual({ value: -0.08, status: 'AVAILABLE' });
    expect(result.data?.futuresLead).toEqual({ value: 0.03, status: 'AVAILABLE' });
    expect(fetchMock).toHaveBeenCalledWith('http://localhost/api/nse/market-context', expect.any(Object));
  });

  it('returns no-data for incomplete futures quotes instead of substituting zero', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [{ identifier: 'FUTIDXNIFTY27-10-2026XX0.00', lastPrice: 22893.9 }],
        timestamp: recentNseTimestamp(),
      }),
    }));

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getFutures();

    expect(result.status).toBe('no-data');
    expect(result.data).toBeNull();
  });

  it('classifies malformed JSON and NSE provider errors as unavailable', async () => {
    const provider = new NseMarketDataProvider('http://localhost/api/nse');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => { throw new SyntaxError('Invalid JSON'); },
    }));
    expect((await provider.getFutures()).status).toBe('error');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      statusText: 'Forbidden',
    }));
    expect((await provider.getFutures()).status).toBe('error');
  });

  it('marks a quote stale using the NSE source timestamp', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [{ identifier: 'FUTIDXNIFTY27-10-2026XX0.00', instrumentType: 'FUTIDX',
          underlying: 'NIFTY', expiryDate: '27-Oct-2026', lastPrice: 22893.9,
          openInterest: 270137, underlyingValue: 22782.75 }],
        timestamp: '30-Sep-2026 13:26:34',
      }),
    }));

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getFutures();

    expect(result.status).toBe('stale');
    expect(result.data?.ltp).toBe(22893.9);
  });

  it('loads NSE index-tracker constituents with current quote and weight fields', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [
          { cmSymbol: 'HDFCBANK', lasttradedPrice: '1,721.20', change: '12.5', pchange: '1.76',
            totaltradedquantity: 373.89857, totaltradedvalue: 2679.058, weightage: 12 },
          { cmSymbol: 'INFY', lasttradedPrice: '1,035', change: '40.84', pchange: '4.11',
            totaltradedquantity: 251, totaltradedvalue: 260000, weightage: 3.35 },
          { cmSymbol: 'HINDUNILVR', lasttradedPrice: '25,12.34', change: '0.1' },
        ],
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await new NseMarketDataProvider().getConstituents();

    expect(result.status).toBe('stale');
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/nse/constituents'), expect.any(Object));
    expect(result.data).toHaveLength(3);
    expect(result.data?.[0]).toMatchObject({
      symbol: 'HDFCBANK', ltp: 1721.2, previousClose: 1708.7, change: 12.5,
      changePercent: 1.76, volume: 373.89857, weight: 10.38, sector: 'Financial Services',
    });
    expect(result.data?.[1]?.ltp).toBe(1035);
    expect(result.data?.[2]?.ltp).toBeNull();
  });

  it('loads an on-demand equity quote using the NSE priceInfo response', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-08T09:30:00Z'));
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        equityResponse: [{
          metaData: { symbol: 'INFY', open: 1010, previousClose: 994.1, change: 40.9, pChange: 4.11 },
          tradeInfo: { lastPrice: 1035, totalTradedVolume: 15166446 },
          priceInfo: { lastPrice: 1035 },
          lastUpdateTime: recentNseTimestamp(),
        }],
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getEquityQuote('INFY');

    expect(result.status).toBe('live');
    expect(result.data).toMatchObject({ symbol: 'INFY', ltp: 1035, open: 1010, previousClose: 994.1, changePercent: 4.11, volume: 15166446 });
    expect(fetchMock).toHaveBeenCalledWith('http://localhost/api/nse/quote/INFY', expect.any(Object));
  });

  it('keeps aggregate attribution unavailable while exposing covered row contributions', () => {
    const partial = Array.from({ length: 49 }, (_, index) => ({
      symbol: `S${index}`, ltp: 100, previousClose: 99, change: 1,
      changePercent: 1, volume: 100, weight: 100 / 50,
    }));

    const result = computeWeightedAttribution(partial);

    expect(result.available).toBe(false);
    expect(result.weightedReturnPercent).toBeNull();
    expect(result.items.every(item => item.contributionPercent === 0.02)).toBe(true);
    expect(result.positiveWeightedContribution).toBeCloseTo(0.98, 8);
    expect(result.advanceWeightPercent).toBe(98);
    expect(result.weightedBreadthPercent).toBe(98);
  });

  it('calculates weighted index contribution from all 50 complete rows', () => {
    const members = Array.from({ length: 50 }, (_, index) => ({
      symbol: `S${index}`, ltp: 100, previousClose: 99, change: 1,
      changePercent: index === 0 ? 2 : 1, volume: 100, weight: 2,
    }));

    const result = computeWeightedAttribution(members);

    expect(result.available).toBe(true);
    expect(result.totalWeightPercent).toBe(100);
    expect(result.weightedReturnPercent).toBeCloseTo(1.02, 8);
    expect(result.items[0].contributionPercent).toBeCloseTo(0.04, 8);
    expect(result.positiveWeightedContribution).toBeCloseTo(1.02, 8);
    expect(result.negativeWeightedContribution).toBe(0);
    expect(result.advanceWeightPercent).toBe(100);
  });

  it('stores all 50 constituent weights and computes weighted sector returns with coverage', () => {
    expect(Object.keys(NIFTY50_WEIGHTS)).toHaveLength(50);
    expect(NIFTY50_MEMBERS.every(member => NIFTY50_WEIGHTS[member.symbol] != null
      && NIFTY50_SECTORS[member.symbol] != null)).toBe(true);
    expect(Object.values(NIFTY50_WEIGHTS).reduce((sum, weight) => sum + weight, 0)).toBeCloseTo(100, 8);

    const result = computeWeightedAttribution([
      { symbol: 'HDFCBANK', ltp: 100, previousClose: 99, change: 1, changePercent: 1,
        volume: 100, weight: 10.38, sector: 'Financial Services' },
      { symbol: 'ICICIBANK', ltp: 100, previousClose: 99, change: -1, changePercent: -1,
        volume: 100, weight: 9.05, sector: 'Financial Services' },
    ]);

    expect(result.sectors).toHaveLength(1);
    expect(result.sectors[0]).toMatchObject({
      sector: 'Financial Services', weightPercent: 19.43, coveredWeightPercent: 19.43,
      coveredMembers: 2, memberCount: 2,
    });
    expect(result.sectors[0].weightedReturnPercent).toBeCloseTo((10.38 - 9.05) / 19.43, 8);
  });

  it('calculates support and resistance from option OI', () => {
    const levels = estimateSupportResistance({
      niftySpot: 24650,
      callOiByStrike: {
        24700: 120000,
        24800: 90000,
      },
      putOiByStrike: {
        24600: 140000,
        24500: 110000,
      },
      strikeStep: 50,
    });

    expect(levels.support).toBe(24600);
    expect(levels.resistance).toBe(24700);
    expect(levels.highestPutOiStrike).toBe(24600);
    expect(levels.highestCallOiStrike).toBe(24700);
  });

  it('finds change-OI concentrations, support, resistance, and concentration ratios', () => {
    const levels = estimateSupportResistance({
      niftySpot: 100,
      callOiByStrike: { 100: 100, 110: 500, 120: 100 },
      putOiByStrike: { 80: 200, 90: 600, 100: 100 },
      callChangeOiByStrike: { 100: 1, 110: 40, 120: 5 },
      putChangeOiByStrike: { 80: 6, 90: 30, 100: 2 },
    });

    expect(levels.highestCallOiStrike).toBe(110);
    expect(levels.highestPutOiStrike).toBe(90);
    expect(levels.highestCallChangeOiStrike).toBe(110);
    expect(levels.highestPutChangeOiStrike).toBe(90);
    expect(levels.support).toBe(90);
    expect(levels.resistance).toBe(110);
    expect(levels.callOiConcentration).toBeCloseTo(500 / 700);
    expect(levels.putOiConcentration).toBeCloseTo(600 / 900);
  });

  it('selects only configured strike offsets around ATM', () => {
    const options = Array.from({ length: 21 }, (_, index) => ({ strike: 23000 + index * 50 })) as OptionsSnapshot[];
    const selected = selectAtmOptions(options, 23500);
    expect(selected.map((item) => item.strike)).toEqual([23250, 23350, 23400, 23450, 23500, 23550, 23600, 23650, 23750]);
  });

  it('chooses the nearest valid option expiry', () => {
    const now = Date.UTC(2026, 8, 29, 12);
    expect(parseNseExpiry('06-Oct-2026')).toBe(Date.UTC(2026, 9, 6));
    expect(parseNseExpiry('invalid')).toBeNull();
    expect(selectNearestExpiry(['29-Sep-2026', '19-Oct-2026', '06-Oct-2026'], now)).toBe('29-Sep-2026');
    expect(selectNearestExpiry(['29-Sep-2026', '06-Oct-2026'], Date.UTC(2026, 8, 30))).toBe('06-Oct-2026');
  });

  it('normalizes mocked NSE options and keeps only near-ATM strikes', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-08T09:30:00Z'));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        records: {
          timestamp: recentNseTimestamp(),
          data: Array.from({ length: 21 }, (_, index) => {
            const strikePrice = 23000 + index * 50;
            const leg = (side: 'CE' | 'PE') => ({
              lastPrice: side === 'CE' ? 100 : 120,
              openInterest: side === 'CE' ? strikePrice : strikePrice * 2,
              changeinOpenInterest: side === 'CE' ? 10 : 20,
              totalTradedVolume: side === 'CE' ? 1_000 : 2_000,
              impliedVolatility: 18,
              underlyingValue: 23500,
            });
            return { strikePrice, expiryDates: '06-Oct-2026', CE: leg('CE'), PE: leg('PE') };
          }),
        },
      }),
    }));

    const result = await new NseMarketDataProvider('http://localhost/api/nse').getOptions('06-Oct-2026');

    expect(result.status).toBe('live');
    expect(result.data).toHaveLength(9);
    expect(result.data?.[0]).toMatchObject({
      symbol: 'NIFTY', expiry: '06-Oct-2026', strike: 23250, ceLtp: 100,
      peLtp: 120, ceOi: 23250, peOi: 46500, ceChangeOi: 10, peChangeOi: 20,
      ceVolume: 1_000, peVolume: 2_000, ceIv: 18, peIv: 18,
    });
    expect(fetch).toHaveBeenCalledWith('http://localhost/api/nse/option-chain?expiry=06-Oct-2026', expect.any(Object));
  });

  it('displays server-calculated momentum and 5m Futures lead values', () => {
    const features = buildBackendFeatureSnapshot(makeBundle(100), makeBackendContext());

    expect(features?.niftyReturn1m).toBe(0.02);
    expect(features?.niftyReturn5m).toBe(-0.08);
    expect(features?.niftyReturn10m).toBe(-0.11);
    expect(features?.niftyReturn15m).toBe(-0.15);
    expect(features?.futuresReturn5m).toBe(-0.05);
    expect(features?.futuresReturn10m).toBe(-0.08);
    expect(features?.futuresReturn15m).toBe(-0.12);
    expect(features?.futuresLead).toBe(0.03);
    expect(features?.futuresLeadStatus).toBe('AVAILABLE');
    expect(features?.niftyReturn15mStatus).toBe('AVAILABLE');
    expect(features?.futuresReturn15mStatus).toBe('AVAILABLE');
  });

  it('preserves backend stale status for Futures lead', () => {
    const context = makeBackendContext();
    context.futuresLead = { value: null, status: 'STALE' };

    const features = buildBackendFeatureSnapshot(makeBundle(100), context);

    expect(features?.futuresLead).toBeNull();
    expect(features?.futuresLeadStatus).toBe('STALE');
  });

  it('calculates option-derived level distances and handles zero OI', () => {
    const options: OptionsSnapshot[] = [
      { symbol: 'NIFTY', expiry: '06-Oct-2026', strike: 80, ceLtp: 1, peLtp: 1, ceOi: 50, peOi: 200, ceChangeOi: 1, peChangeOi: 5, ceVolume: 1, peVolume: 1 },
      { symbol: 'NIFTY', expiry: '06-Oct-2026', strike: 90, ceLtp: 1, peLtp: 1, ceOi: 100, peOi: 600, ceChangeOi: 2, peChangeOi: 30, ceVolume: 1, peVolume: 1 },
      { symbol: 'NIFTY', expiry: '06-Oct-2026', strike: 100, ceLtp: 1, peLtp: 1, ceOi: 100, peOi: 100, ceChangeOi: 1, peChangeOi: 2, ceVolume: 1, peVolume: 1 },
      { symbol: 'NIFTY', expiry: '06-Oct-2026', strike: 110, ceLtp: 1, peLtp: 1, ceOi: 500, peOi: 100, ceChangeOi: 40, peChangeOi: 1, ceVolume: 1, peVolume: 1 },
      { symbol: 'NIFTY', expiry: '06-Oct-2026', strike: 120, ceLtp: 1, peLtp: 1, ceOi: 100, peOi: 50, ceChangeOi: 5, peChangeOi: 1, ceVolume: 1, peVolume: 1 },
    ];
    const features = buildBackendFeatureSnapshot(makeBundle(100, options), makeBackendContext(100));

    expect(features?.highestPutOIStrike).toBe(90);
    expect(features?.highestCallOIStrike).toBe(110);
    expect(features?.highestPutChangeOIStrike).toBe(90);
    expect(features?.highestCallChangeOIStrike).toBe(110);
    expect(features?.supportLevel).toBe(90);
    expect(features?.resistanceLevel).toBe(110);
    expect(features?.distanceFromSupport).toBe(10);
    expect(features?.distanceFromResistance).toBe(10);
    expect(features?.supportLabel).toBe('OPTION_OI_SUPPORT');
    expect(features?.resistanceLabel).toBe('OPTION_OI_RESISTANCE');
    expect(features?.pcr).toBeCloseTo(1050 / 850);
    expect(features?.changeOIPcr).toBeCloseTo(39 / 49);

    const emptyFeatures = buildBackendFeatureSnapshot(makeBundle(100), makeBackendContext(100));
    expect(emptyFeatures?.pcr).toBe(0);
    expect(emptyFeatures?.changeOIPcr).toBe(0);
    expect(emptyFeatures?.callOiConcentration).toBe(0);
    expect(emptyFeatures?.putOiConcentration).toBe(0);
  });

  it('marks stale data when the provider has not refreshed in time', () => {
    expect(isMarketDataStale(Date.now() - 181_000)).toBe(true);
    expect(isMarketDataStale(Date.now() - 150_000)).toBe(false);
  });
});
