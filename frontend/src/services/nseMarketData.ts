import { NIFTY50_SECTORS, NIFTY50_WEIGHTS } from '../data/nifty50';

export const DEFAULT_POLL_INTERVAL = 30_000;
export const DEFAULT_STALE_MS = 180_000;

export type ProviderStatus = 'live' | 'stale' | 'error' | 'loading' | 'no-data';

export type ProviderState<T> = {
  status: ProviderStatus;
  lastUpdated: number | null;
  latency: number | null;
  error: string | null;
  data: T | null;
};

export type NiftyMarketData = {
  symbol: string;
  ltp: number;
  open: number | null;
  high: number | null;
  low: number | null;
  previousClose: number | null;
  change: number | null;
  changePercent: number | null;
  volume: number | null;
  timestamp: string;
  source: 'NSE';
};

export type ConstituentItem = {
  symbol: string;
  ltp: number | null;
  open?: number | null;
  previousClose: number | null;
  change: number | null;
  changePercent: number | null;
  volume: number | null;
  weight?: number | null;
  sector?: string | null;
};

export type SectorWeightedReturn = {
  sector: string;
  weightPercent: number;
  coveredWeightPercent: number;
  coveredMembers: number;
  memberCount: number;
  weightedReturnPercent: number | null;
};

export type WeightedConstituentAttribution = {
  available: boolean;
  coveredMembers: number;
  totalWeightPercent: number | null;
  weightedReturnPercent: number | null;
  positiveWeightedContribution: number | null;
  negativeWeightedContribution: number | null;
  advanceWeightPercent: number;
  declineWeightPercent: number;
  weightedBreadthPercent: number;
  sectors: SectorWeightedReturn[];
  items: Array<ConstituentItem & { contributionPercent: number | null }>;
};

export type NiftyPressureSample = {
  timestamp: number;
  niftyPrice: number | null;
  constituents: Array<Pick<ConstituentItem, 'symbol' | 'ltp' | 'open' | 'weight'>>;
};

export type NiftyPressureInterval = {
  upContributionPercentPoints: number;
  downContributionPercentPoints: number;
  netPressurePercentPoints: number;
  coveredWeightPercent: number;
};

export type NiftyPressureResult = {
  session: NiftyPressureInterval;
  horizons: Record<1 | 5 | 10 | 15 | 30, NiftyPressureInterval | null>;
  upSharePercent: number | null;
  downSharePercent: number | null;
  direction: 'UP' | 'DOWN' | 'NEUTRAL' | 'COLLECTING';
  trend: 'BUILDING' | 'ACCELERATING' | 'CONTINUING' | 'WEAKENING' | 'REVERSAL_UP' | 'REVERSAL_DOWN' | 'NO_CLEAR_EDGE' | 'COLLECTING';
  persistenceHorizonCount: number | null;
  accelerationPercentPoints: number | null;
  expected15mRange: { low: number; high: number; sigmaPercent: number } | null;
};

export type MarketDirectionAnalysis = {
  direction: 'UP' | 'DOWN' | 'NEUTRAL' | 'COLLECTING';
  score: number | null;
  strength: 'WEAK' | 'MODERATE' | 'STRONG' | 'COLLECTING';
  state: string;
  spotConfirmation: 'CONFIRMS' | 'CONTRADICTS' | 'NEUTRAL' | 'STALE' | 'UNAVAILABLE' | 'COLLECTING';
  futuresConfirmation: 'CONFIRMS' | 'CONTRADICTS' | 'NEUTRAL' | 'STALE' | 'UNAVAILABLE' | 'COLLECTING';
  spotReturn5m: number | null;
  futuresReturn5m: number | null;
  supportLevel: number | null;
  resistanceLevel: number | null;
  distanceFromSupport: number | null;
  distanceFromResistance: number | null;
  nearestStructure: 'SUPPORT' | 'RESISTANCE' | 'UNAVAILABLE';
  pcr: number | null;
  changeOIPcr: number | null;
};

export type BreadthMetrics = {
  advances: number;
  declines: number;
  unchanged: number;
  advanceDeclineRatio: number;
  breadth: number;
  averageConstituentReturn: number;
  weightedConstituentReturn: number;
};

export type FuturesSnapshot = {
  contract: string;
  identifier: string;
  instrumentType: string;
  expiry: string;
  ltp: number;
  open: number | null;
  high: number | null;
  low: number | null;
  previousClose: number | null;
  change: number | null;
  changePercent: number | null;
  volume: number | null;
  turnover: number | null;
  openInterest: number | null;
  changeInOpenInterest: number | null;
  percentChangeInOpenInterest: number | null;
  underlying: string;
  underlyingValue: number;
  timestamp: string;
};

export type OptionsSnapshot = {
  symbol: string;
  expiry: string;
  strike: number;
  ceLtp: number;
  peLtp: number;
  ceOi: number;
  peOi: number;
  ceChangeOi: number;
  peChangeOi: number;
  ceVolume: number;
  peVolume: number;
  ceIv?: number;
  peIv?: number;
};

export type MomentumClassification = 'STRONG_UP' | 'UP' | 'NEUTRAL' | 'DOWN' | 'STRONG_DOWN';
export type MomentumAvailability = 'COLLECTING' | 'AVAILABLE' | 'UNAVAILABLE';
export type FuturesPositioning = 'LONG_BUILDUP' | 'SHORT_BUILDUP' | 'SHORT_COVERING' | 'LONG_UNWINDING' | 'NEUTRAL';

export type FeatureThresholds = {
  momentumDirectionPct: number;
  momentumStrongPct: number;
  futuresPriceMovePct: number;
  futuresOiChangePct: number;
};

const envNumber = (name: string, fallback: number) => {
  const configured = Number(import.meta.env[name]);
  return Number.isFinite(configured) && configured > 0 ? configured : fallback;
};

export const DEFAULT_FEATURE_THRESHOLDS: FeatureThresholds = {
  momentumDirectionPct: envNumber('VITE_FEATURE_MOMENTUM_DIRECTION_PCT', 0.05),
  momentumStrongPct: envNumber('VITE_FEATURE_MOMENTUM_STRONG_PCT', 0.25),
  futuresPriceMovePct: envNumber('VITE_FEATURE_FUTURES_PRICE_MOVE_PCT', 0.02),
  futuresOiChangePct: envNumber('VITE_FEATURE_FUTURES_OI_CHANGE_PCT', 0.1),
};

export type IndiaVixSnapshot = {
  symbol: string;
  ltp: number;
  change: number;
  changePercent: number;
  open: number;
  high: number;
  low: number;
  previousClose: number;
  timestamp: string;
};

export type MarketStatistics = {
  advances: number;
  declines: number;
  unchanged: number;
  volume: number;
  turnover: number;
  timestamp: string;
};

export type SectorReturn = {
  symbol: string;
  return1m: number;
  return5m: number;
  return15m: number;
  relativeToNifty: number;
};

export type MarketFeatureSnapshot = {
  timestamp: string;
  niftyPrice: number;
  niftyReturn1m: number | null;
  niftyReturn5m: number | null;
  niftyReturn10m: number | null;
  niftyReturn15m: number | null;
  niftyOvernightReturn: number | null;
  niftyOpeningGap: number | null;
  niftyMomentum: MomentumClassification | null;
  niftyMomentumStatus: MomentumAvailability | 'STALE';
  niftyReturn1mStatus: MomentumAvailability | 'STALE';
  niftyReturn5mStatus: MomentumAvailability | 'STALE';
  niftyReturn10mStatus: MomentumAvailability | 'STALE';
  niftyReturn15mStatus: MomentumAvailability | 'STALE';
  futuresSymbol: string | null;
  futuresExpiry: string | null;
  futuresPrice: number | null;
  futuresOvernightReturn: number | null;
  futuresOpeningGap: number | null;
  futuresReturn1m: number | null;
  futuresReturn5m: number | null;
  futuresReturn10m: number | null;
  futuresReturn15m: number | null;
  futuresMomentum: MomentumClassification | null;
  futuresMomentumStatus: MomentumAvailability | 'STALE';
  futuresReturn5mStatus: MomentumAvailability | 'STALE';
  futuresReturn10mStatus: MomentumAvailability | 'STALE';
  futuresReturn15mStatus: MomentumAvailability | 'STALE';
  futuresChange: number | null;
  futuresChangePercent: number | null;
  futuresChangeInOpenInterest: number | null;
  futuresPercentChangeInOpenInterest: number | null;
  futuresVolume: number | null;
  futuresUnderlyingValue: number | null;
  futuresBasis: number | null;
  futuresBasisPct: number | null;
  futuresBasisChange15m: number | null;
  spotReturn15m: number | null;
  futuresLead: number | null;
  futuresLeadStatus: MomentumAvailability | 'STALE';
  futuresOI: number | null;
  futuresOIChangePct: number | null;
  futuresPositioning: FuturesPositioning | null;
  optionExpiry: string | null;
  totalCallOI: number;
  totalPutOI: number;
  totalCallChangeOI: number;
  totalPutChangeOI: number;
  pcr: number;
  changeOIPcr: number;
  highestPutOIStrike: number | null;
  highestCallOIStrike: number | null;
  highestPutChangeOIStrike: number | null;
  highestCallChangeOIStrike: number | null;
  supportLevel: number | null;
  resistanceLevel: number | null;
  distanceFromSupport: number | null;
  distanceFromResistance: number | null;
  supportLabel: 'OPTION_OI_SUPPORT';
  resistanceLabel: 'OPTION_OI_RESISTANCE';
  callOiConcentration: number;
  putOiConcentration: number;
  dominantCallOiStrike: number | null;
  dominantPutOiStrike: number | null;
};

export type MarketDataBundle = {
  nifty: ProviderState<NiftyMarketData>;
  constituents: ProviderState<ConstituentItem[]>;
  futures: ProviderState<FuturesSnapshot>;
  options: ProviderState<OptionsSnapshot[]>;
  indiaVix: ProviderState<IndiaVixSnapshot>;
  marketStatistics: ProviderState<MarketStatistics>;
  indices: ProviderState<Record<string, number>>;
  optionExpiries: ProviderState<string[]>;
  selectedOptionExpiry: string | null;
  features: MarketFeatureSnapshot | null;
};

export type BackendMetric = {
  value: number | null;
  status: MomentumAvailability | 'STALE';
};

export type BackendNseMarketContext = {
  timestamp: string;
  nifty: null | {
    timestamp: string;
    ltp: number | null;
    open: number | null;
    high: number | null;
    low: number | null;
    previousClose: number | null;
    change: number | null;
    changePercent: number | null;
    overnightReturn: number | null;
    openingGap: number | null;
    return1m: BackendMetric;
    return5m: BackendMetric;
    return10m: BackendMetric;
    return15m: BackendMetric;
    status: MomentumAvailability | 'STALE';
  };
  futures: null | {
    timestamp: string;
    identifier: string;
    instrumentType: string;
    expiry: string;
    ltp: number | null;
    open: number | null;
    high: number | null;
    low: number | null;
    previousClose: number | null;
    change: number | null;
    changePercent: number | null;
    overnightReturn: number | null;
    openingGap: number | null;
    openInterest: number | null;
    changeInOpenInterest: number | null;
    percentChangeInOpenInterest: number | null;
    volume: number | null;
    turnover: number | null;
    underlying: string;
    underlyingValue: number | null;
    basis: number;
    return5m: BackendMetric;
    return10m: BackendMetric;
    return15m: BackendMetric;
    status: MomentumAvailability | 'STALE';
  };
  futuresLead: BackendMetric;
};

export function computeReturn(current: number, previous: number): number {
  if (!Number.isFinite(current) || !Number.isFinite(previous) || previous === 0) {
    return 0;
  }

  return ((current - previous) / previous) * 100;
}

export function computeBreadth(input: {
  advances: number;
  declines: number;
  unchanged: number;
  constituents?: Array<{ symbol: string; changePercent: number; weight?: number }>;
}): BreadthMetrics {
  const { advances, declines, unchanged } = input;
  const constituents = input.constituents ?? [];
  const averageConstituentReturn = constituents.length
    ? constituents.reduce((sum, item) => sum + (item.changePercent ?? 0), 0) / constituents.length
    : 0;
  const weightedConstituentReturn = computeWeightedConstituentReturn(constituents);

  const denominator = declines === 0 ? 1 : declines;

  return {
    advances,
    declines,
    unchanged,
    advanceDeclineRatio: advances / denominator,
    breadth: advances - declines,
    averageConstituentReturn,
    weightedConstituentReturn,
  };
}

export function computeWeightedConstituentReturn(
  constituents: Array<{ symbol: string; changePercent: number; weight?: number }>
): number {
  if (!constituents.length) {
    return 0;
  }

  const totalWeight = constituents.reduce((sum, item) => sum + (item.weight ?? 1), 0);

  if (totalWeight === 0) {
    return constituents.reduce((sum, item) => sum + item.changePercent, 0) / constituents.length;
  }

  return constituents.reduce((sum, item) => {
    const weight = item.weight ?? 1;
    return sum + (item.changePercent * weight);
  }, 0) / totalWeight;
}

export function computeWeightedAttribution(
  constituents: ConstituentItem[],
  expectedMembers = 50
): WeightedConstituentAttribution {
  const totalWeight = constituents.reduce((sum, item) => sum + (item.weight ?? 0), 0);
  const complete = constituents.length === expectedMembers
    && constituents.every(item => item.weight != null && item.changePercent != null)
    && Math.abs(totalWeight - 100) <= 0.5;

  const items = constituents.map(item => ({
    ...item,
    contributionPercent: item.weight != null && item.changePercent != null
      ? item.weight * item.changePercent / 100
      : null,
  }));
  const knownItems = items.filter(item => item.contributionPercent != null);
  const advanceWeightPercent = knownItems
    .filter(item => (item.changePercent ?? 0) > 0)
    .reduce((sum, item) => sum + (item.weight ?? 0), 0);
  const declineWeightPercent = knownItems
    .filter(item => (item.changePercent ?? 0) < 0)
    .reduce((sum, item) => sum + (item.weight ?? 0), 0);
  const grouped = new Map<string, typeof items>();
  for (const item of items) {
    if (!item.sector || item.weight == null) continue;
    grouped.set(item.sector, [...(grouped.get(item.sector) ?? []), item]);
  }
  const sectors = [...grouped.entries()].map(([sector, sectorItems]) => {
    const covered = sectorItems.filter(item => item.contributionPercent != null);
    const weightPercent = sectorItems.reduce((sum, item) => sum + (item.weight ?? 0), 0);
    const coveredWeightPercent = covered.reduce((sum, item) => sum + (item.weight ?? 0), 0);
    const contributions = covered.reduce((sum, item) => sum + (item.contributionPercent ?? 0), 0);
    return {
      sector,
      weightPercent,
      coveredWeightPercent,
      coveredMembers: covered.length,
      memberCount: sectorItems.length,
      weightedReturnPercent: coveredWeightPercent > 0 ? contributions / coveredWeightPercent * 100 : null,
    };
  }).sort((left, right) => left.sector.localeCompare(right.sector));

  return {
    available: complete,
    coveredMembers: constituents.filter(item => item.weight != null && item.changePercent != null).length,
    totalWeightPercent: totalWeight > 0 ? totalWeight : null,
    weightedReturnPercent: complete
      ? items.reduce((sum, item) => sum + (item.contributionPercent ?? 0), 0)
      : null,
    positiveWeightedContribution: knownItems.length
      ? knownItems.reduce((sum, item) => sum + Math.max(item.contributionPercent ?? 0, 0), 0)
      : null,
    negativeWeightedContribution: knownItems.length
      ? knownItems.reduce((sum, item) => sum + Math.min(item.contributionPercent ?? 0, 0), 0)
      : null,
    advanceWeightPercent,
    declineWeightPercent,
    weightedBreadthPercent: advanceWeightPercent - declineWeightPercent,
    sectors,
    items,
  };
}

const PRESSURE_WINDOWS = [1, 5, 10, 15, 30] as const;
const PRESSURE_MIN_COVERAGE_PERCENT = 80;
const PRESSURE_WINDOW_TOLERANCE_MS = 75_000;
const MIN_DIRECTIONAL_PRESSURE_PP = 0.01;

function pressureBetween(start: NiftyPressureSample, end: NiftyPressureSample): NiftyPressureInterval {
  const startingBySymbol = new Map(start.constituents.map(item => [item.symbol, item]));
  let upContributionPercentPoints = 0;
  let downContributionPercentPoints = 0;
  let coveredWeightPercent = 0;

  for (const current of end.constituents) {
    const previous = startingBySymbol.get(current.symbol);
    const weight = current.weight ?? previous?.weight;
    if (weight == null || weight <= 0 || current.ltp == null || previous?.ltp == null || previous.ltp <= 0) continue;

    const contribution = ((current.ltp - previous.ltp) / previous.ltp) * weight;
    coveredWeightPercent += weight;
    if (contribution > 0) upContributionPercentPoints += contribution;
    if (contribution < 0) downContributionPercentPoints += Math.abs(contribution);
  }

  return {
    upContributionPercentPoints,
    downContributionPercentPoints,
    netPressurePercentPoints: upContributionPercentPoints - downContributionPercentPoints,
    coveredWeightPercent,
  };
}

function findPressureSample(samples: NiftyPressureSample[], target: number): NiftyPressureSample | null {
  for (let index = samples.length - 1; index >= 0; index -= 1) {
    const sample = samples[index];
    if (sample.timestamp <= target) {
      return target - sample.timestamp <= PRESSURE_WINDOW_TOLERANCE_MS ? sample : null;
    }
  }
  return null;
}

function sessionPressure(sample: NiftyPressureSample): NiftyPressureInterval {
  let upContributionPercentPoints = 0;
  let downContributionPercentPoints = 0;
  let coveredWeightPercent = 0;

  for (const item of sample.constituents) {
    if (item.weight == null || item.weight <= 0 || item.ltp == null || item.open == null || item.open <= 0) continue;
    const contribution = ((item.ltp - item.open) / item.open) * item.weight;
    coveredWeightPercent += item.weight;
    if (contribution > 0) upContributionPercentPoints += contribution;
    if (contribution < 0) downContributionPercentPoints += Math.abs(contribution);
  }

  return {
    upContributionPercentPoints,
    downContributionPercentPoints,
    netPressurePercentPoints: upContributionPercentPoints - downContributionPercentPoints,
    coveredWeightPercent,
  };
}

function expectedRange15m(samples: NiftyPressureSample[], current: NiftyPressureSample) {
  const windowStart = current.timestamp - 15 * 60_000;
  const observations = samples.filter(sample => sample.timestamp >= windowStart
    && sample.timestamp <= current.timestamp && sample.niftyPrice != null && sample.niftyPrice > 0);
  if (observations.length < 6 || observations[0].timestamp > current.timestamp - 5 * 60_000) return null;

  let variancePerMinute = 0;
  let validIntervals = 0;
  for (let index = 1; index < observations.length; index += 1) {
    const previous = observations[index - 1];
    const next = observations[index];
    const elapsedMinutes = (next.timestamp - previous.timestamp) / 60_000;
    if (elapsedMinutes <= 0 || elapsedMinutes > 2 || previous.niftyPrice == null || next.niftyPrice == null) continue;
    const returnPercent = ((next.niftyPrice - previous.niftyPrice) / previous.niftyPrice) * 100;
    variancePerMinute += (returnPercent * returnPercent) / elapsedMinutes;
    validIntervals += 1;
  }

  if (validIntervals < 5) return null;
  const sigmaPercent = Math.sqrt((variancePerMinute / validIntervals) * 15);
  if (!Number.isFinite(sigmaPercent) || sigmaPercent <= 0 || current.niftyPrice == null) return null;
  return {
    low: current.niftyPrice * (1 - sigmaPercent / 100),
    high: current.niftyPrice * (1 + sigmaPercent / 100),
    sigmaPercent,
  };
}

export function computeNiftyPressure(samples: NiftyPressureSample[]): NiftyPressureResult | null {
  if (!samples.length) return null;
  const ordered = [...samples].sort((left, right) => left.timestamp - right.timestamp);
  const current = ordered[ordered.length - 1];
  const session = sessionPressure(current);
  const horizons = Object.fromEntries(PRESSURE_WINDOWS.map(minutes => {
    const previous = findPressureSample(ordered, current.timestamp - minutes * 60_000);
    return [minutes, previous ? pressureBetween(previous, current) : null];
  })) as NiftyPressureResult['horizons'];
  const grossPressure = session.upContributionPercentPoints + session.downContributionPercentPoints;
  const sessionHasCoverage = session.coveredWeightPercent >= PRESSURE_MIN_COVERAGE_PERCENT;
  const fiveMinute = horizons[5];
  const pressureIsAvailable = fiveMinute != null
    && fiveMinute.coveredWeightPercent >= PRESSURE_MIN_COVERAGE_PERCENT;
  const direction = !pressureIsAvailable
    ? 'COLLECTING'
    : Math.abs(fiveMinute.netPressurePercentPoints) < MIN_DIRECTIONAL_PRESSURE_PP
      ? 'NEUTRAL'
      : fiveMinute.netPressurePercentPoints > 0 ? 'UP' : 'DOWN';
  const alignedHorizons = PRESSURE_WINDOWS
    .map(minutes => horizons[minutes])
    .filter((interval): interval is NiftyPressureInterval => interval != null
      && interval.coveredWeightPercent >= PRESSURE_MIN_COVERAGE_PERCENT
      && direction !== 'COLLECTING' && direction !== 'NEUTRAL'
      && Math.sign(interval.netPressurePercentPoints) === (direction === 'UP' ? 1 : -1));
  const persistenceHorizonCount = direction === 'UP' || direction === 'DOWN'
    ? alignedHorizons.length
    : null;

  const previousFiveMinuteEnd = findPressureSample(ordered, current.timestamp - 5 * 60_000);
  const previousFiveMinuteStart = findPressureSample(ordered, current.timestamp - 10 * 60_000);
  const previousFiveMinute = previousFiveMinuteStart && previousFiveMinuteEnd
    ? pressureBetween(previousFiveMinuteStart, previousFiveMinuteEnd)
    : null;
  const accelerationPercentPoints = fiveMinute && previousFiveMinute
    ? fiveMinute.netPressurePercentPoints - previousFiveMinute.netPressurePercentPoints
    : null;

  let trend: NiftyPressureResult['trend'] = 'COLLECTING';
  if (direction === 'NEUTRAL') {
    trend = 'NO_CLEAR_EDGE';
  } else if (direction === 'UP' || direction === 'DOWN') {
    const sessionDirection = session.netPressurePercentPoints > MIN_DIRECTIONAL_PRESSURE_PP
      ? 'UP'
      : session.netPressurePercentPoints < -MIN_DIRECTIONAL_PRESSURE_PP ? 'DOWN' : 'NEUTRAL';
    if (sessionHasCoverage && sessionDirection !== 'NEUTRAL' && sessionDirection !== direction) {
      trend = direction === 'UP' ? 'REVERSAL_UP' : 'REVERSAL_DOWN';
    } else if (fiveMinute && !previousFiveMinute && (persistenceHorizonCount ?? 0) >= 2) {
      trend = 'BUILDING';
    } else if (accelerationPercentPoints != null) {
      if (Math.sign(accelerationPercentPoints) === (direction === 'UP' ? 1 : -1)
          && Math.abs(accelerationPercentPoints) >= MIN_DIRECTIONAL_PRESSURE_PP) {
        trend = 'ACCELERATING';
      } else if (Math.sign(accelerationPercentPoints) === (direction === 'UP' ? -1 : 1)
          && Math.abs(accelerationPercentPoints) >= MIN_DIRECTIONAL_PRESSURE_PP) {
        trend = 'WEAKENING';
      } else {
        trend = 'CONTINUING';
      }
    }
  }

  return {
    session,
    horizons,
    upSharePercent: grossPressure > 0 ? session.upContributionPercentPoints / grossPressure * 100 : null,
    downSharePercent: grossPressure > 0 ? session.downContributionPercentPoints / grossPressure * 100 : null,
    direction,
    trend,
    persistenceHorizonCount,
    accelerationPercentPoints,
    expected15mRange: expectedRange15m(ordered, current),
  };
}

export function computeMarketDirection(
  pressure: NiftyPressureResult | null,
  bundle: MarketDataBundle | null
): MarketDirectionAnalysis {
  const features = bundle?.features;
  const fiveMinutePressure = pressure?.horizons[5];
  const pressureAvailable = fiveMinutePressure != null
    && fiveMinutePressure.coveredWeightPercent >= PRESSURE_MIN_COVERAGE_PERCENT;
  const components: Array<{ weight: number; signal: number }> = [];

  if (pressureAvailable && fiveMinutePressure) {
    const gross = fiveMinutePressure.upContributionPercentPoints + fiveMinutePressure.downContributionPercentPoints;
    components.push({
      weight: 50,
      signal: gross > 0 ? fiveMinutePressure.netPressurePercentPoints / gross : 0,
    });
  }

  const momentumSignal = (value: number | null | undefined, status: string | undefined): number | null => {
    if (value == null || status !== 'AVAILABLE') return null;
    const classification = classifyMomentum(value);
    if (classification === 'STRONG_UP') return 1;
    if (classification === 'UP') return 0.5;
    if (classification === 'DOWN') return -0.5;
    if (classification === 'STRONG_DOWN') return -1;
    return 0;
  };

  const spotSignal = momentumSignal(features?.niftyReturn5m, features?.niftyReturn5mStatus);
  const futuresSignal = momentumSignal(features?.futuresReturn5m, features?.futuresReturn5mStatus);
  if (spotSignal != null) components.push({ weight: 25, signal: spotSignal });
  if (futuresSignal != null) components.push({ weight: 25, signal: futuresSignal });

  const totalWeight = components.reduce((sum, component) => sum + component.weight, 0);
  const score = pressureAvailable && totalWeight > 0
    ? components.reduce((sum, component) => sum + component.weight * component.signal, 0) / totalWeight * 100
    : null;
  const direction = score == null
    ? 'COLLECTING'
    : score >= 15 ? 'UP' : score <= -15 ? 'DOWN' : 'NEUTRAL';
  const strength = score == null
    ? 'COLLECTING'
    : Math.abs(score) >= 60 ? 'STRONG' : Math.abs(score) >= 35 ? 'MODERATE' : 'WEAK';

  const spotStatus = features?.niftyReturn5mStatus ?? 'COLLECTING';
  const futuresStatus = features?.futuresReturn5mStatus ?? 'COLLECTING';
  const confirmation = (
    signal: number | null,
    status: typeof spotStatus
  ): MarketDirectionAnalysis['spotConfirmation'] => {
    if (direction === 'COLLECTING') return 'COLLECTING';
    if (signal == null) return status === 'STALE' || status === 'UNAVAILABLE' ? status : 'COLLECTING';
    if (direction === 'NEUTRAL' || signal === 0) return 'NEUTRAL';
    return Math.sign(signal) === (direction === 'UP' ? 1 : -1) ? 'CONFIRMS' : 'CONTRADICTS';
  };

  let state = 'COLLECTING';
  if (direction === 'NEUTRAL') {
    state = 'NO_CLEAR_EDGE';
  } else if (direction === 'UP' || direction === 'DOWN') {
    const hasOpposingSignal = components.some(component =>
      component.signal !== 0 && Math.sign(component.signal) !== (direction === 'UP' ? 1 : -1));
    if (hasOpposingSignal) {
      state = 'MIXED';
    } else if (pressure?.trend === 'REVERSAL_UP' || pressure?.trend === 'REVERSAL_DOWN') {
      state = pressure.trend;
    } else if (pressure?.trend === 'ACCELERATING' || pressure?.trend === 'BUILDING'
        || pressure?.trend === 'WEAKENING' || pressure?.trend === 'CONTINUING') {
      state = `${direction}_${pressure.trend}`;
    } else {
      state = direction;
    }
  }

  const supportLevel = features?.supportLevel ?? null;
  const resistanceLevel = features?.resistanceLevel ?? null;
  const distanceFromSupport = features?.distanceFromSupport ?? null;
  const distanceFromResistance = features?.distanceFromResistance ?? null;
  const nearestStructure = supportLevel == null
    ? resistanceLevel == null ? 'UNAVAILABLE' : 'RESISTANCE'
    : resistanceLevel == null ? 'SUPPORT'
      : Math.abs(distanceFromSupport ?? Number.POSITIVE_INFINITY)
        <= Math.abs(distanceFromResistance ?? Number.POSITIVE_INFINITY) ? 'SUPPORT' : 'RESISTANCE';

  return {
    direction,
    score,
    strength,
    state,
    spotConfirmation: confirmation(spotSignal, spotStatus),
    futuresConfirmation: confirmation(futuresSignal, futuresStatus),
    spotReturn5m: features?.niftyReturn5m ?? null,
    futuresReturn5m: features?.futuresReturn5m ?? null,
    supportLevel,
    resistanceLevel,
    distanceFromSupport,
    distanceFromResistance,
    nearestStructure,
    pcr: features?.pcr ?? null,
    changeOIPcr: features?.changeOIPcr ?? null,
  };
}

export function computeFutureBasis(futuresPrice: number, spotPrice: number): { price: number; percent: number } {
  const price = futuresPrice - spotPrice;
  const percent = spotPrice === 0 ? 0 : (price / spotPrice) * 100;

  return { price, percent };
}

export function classifyMomentum(
  value: number | null,
  thresholds: FeatureThresholds = DEFAULT_FEATURE_THRESHOLDS
): MomentumClassification {
  if (value == null || Math.abs(value) < thresholds.momentumDirectionPct) return 'NEUTRAL';
  if (value >= thresholds.momentumStrongPct) return 'STRONG_UP';
  if (value > 0) return 'UP';
  if (value <= -thresholds.momentumStrongPct) return 'STRONG_DOWN';
  return 'DOWN';
}

export function classifyFuturesPositioning(
  priceMovePct: number | null,
  oiMovePct: number | null,
  thresholds: FeatureThresholds = DEFAULT_FEATURE_THRESHOLDS
): FuturesPositioning {
  if (priceMovePct == null || oiMovePct == null) return 'NEUTRAL';

  const priceUp = priceMovePct > thresholds.futuresPriceMovePct;
  const priceDown = priceMovePct < -thresholds.futuresPriceMovePct;
  const oiUp = oiMovePct > thresholds.futuresOiChangePct;
  const oiDown = oiMovePct < -thresholds.futuresOiChangePct;

  if (priceUp && oiUp) return 'LONG_BUILDUP';
  if (priceDown && oiUp) return 'SHORT_BUILDUP';
  if (priceUp && oiDown) return 'SHORT_COVERING';
  if (priceDown && oiDown) return 'LONG_UNWINDING';
  return 'NEUTRAL';
}

export function computePcr(callOi: number, putOi: number): number {
  return callOi === 0 ? 0 : putOi / callOi;
}

export function computeChangeOiPcr(callChangeOi: number, putChangeOi: number): number {
  return callChangeOi === 0 ? 0 : putChangeOi / callChangeOi;
}

export function estimateSupportResistance(input: {
  niftySpot: number;
  callOiByStrike: Record<string, number>;
  putOiByStrike: Record<string, number>;
  callChangeOiByStrike?: Record<string, number>;
  putChangeOiByStrike?: Record<string, number>;
  strikeStep?: number;
}): {
  support: number | null;
  resistance: number | null;
  highestCallOiStrike: number | null;
  highestPutOiStrike: number | null;
  highestCallChangeOiStrike: number | null;
  highestPutChangeOiStrike: number | null;
  callOiConcentration: number;
  putOiConcentration: number;
} {
  const maximumStrike = (values: Record<string, number>, aboveSpot: boolean): number | null => {
    const candidates = Object.entries(values)
      .map(([strike, value]) => [Number(strike), Number(value)] as const)
      .filter(([strike, value]) => Number.isFinite(strike) && Number.isFinite(value)
        && (aboveSpot ? strike >= input.niftySpot : strike <= input.niftySpot));
    if (!candidates.length) return null;
    return candidates.reduce((best, candidate) => candidate[1] > best[1] ? candidate : best)[0];
  };

  const highestCallOiStrike = maximumStrike(input.callOiByStrike, true);
  const highestPutOiStrike = maximumStrike(input.putOiByStrike, false);
  const highestCallChangeOiStrike = maximumStrike(input.callChangeOiByStrike ?? {}, true);
  const highestPutChangeOiStrike = maximumStrike(input.putChangeOiByStrike ?? {}, false);
  const totalCallOi = Object.values(input.callOiByStrike).reduce((sum, value) => sum + value, 0);
  const totalPutOi = Object.values(input.putOiByStrike).reduce((sum, value) => sum + value, 0);
  const maxCallOi = highestCallOiStrike == null ? 0 : input.callOiByStrike[String(highestCallOiStrike)] ?? 0;
  const maxPutOi = highestPutOiStrike == null ? 0 : input.putOiByStrike[String(highestPutOiStrike)] ?? 0;

  return {
    support: highestPutOiStrike,
    resistance: highestCallOiStrike,
    highestCallOiStrike,
    highestPutOiStrike,
    highestCallChangeOiStrike,
    highestPutChangeOiStrike,
    callOiConcentration: totalCallOi > 0 ? maxCallOi / totalCallOi : 0,
    putOiConcentration: totalPutOi > 0 ? maxPutOi / totalPutOi : 0,
  };
}

export function selectAtmOptions(options: OptionsSnapshot[], spot: number): OptionsSnapshot[] {
  if (!options.length || !Number.isFinite(spot)) return [];
  const strikes = [...new Set(options.map((item) => item.strike))].sort((a, b) => a - b);
  const atmIndex = strikes.reduce((best, strike, index) =>
    Math.abs(strike - spot) < Math.abs(strikes[best] - spot) ? index : best, 0);
  const offsets = new Set([0, 1, 2, 3, 5]);
  const selectedStrikes = new Set(strikes.filter((_, index) => offsets.has(Math.abs(index - atmIndex))));
  return options.filter((item) => selectedStrikes.has(item.strike));
}

export function isMarketDataStale(lastUpdated: number | null, staleThresholdMs = DEFAULT_STALE_MS): boolean {
  if (lastUpdated == null || !Number.isFinite(lastUpdated)) {
    return true;
  }

  return Date.now() - lastUpdated > staleThresholdMs;
}

function providerStatus(status: string | undefined): ProviderStatus {
  if (status === 'AVAILABLE') return 'live';
  if (status === 'STALE') return 'stale';
  if (status === 'UNAVAILABLE') return 'error';
  return 'loading';
}

function mapNiftyContext(context: BackendNseMarketContext['nifty'], fallbackError: string | null): ProviderState<NiftyMarketData> {
  if (!context || context.ltp == null) {
    return { status: context ? providerStatus(context.status) : 'error', lastUpdated: null,
      latency: null, error: fallbackError, data: null };
  }
  const updatedAt = parseNseTimestamp(context.timestamp);
  return {
    status: providerStatus(context.status), lastUpdated: updatedAt, latency: null,
    error: context.status === 'UNAVAILABLE' ? fallbackError : null,
    data: {
      symbol: 'NIFTY 50', ltp: context.ltp, open: context.open, high: context.high,
      low: context.low, previousClose: context.previousClose,
      change: context.change, changePercent: context.changePercent,
      volume: null, timestamp: context.timestamp, source: 'NSE',
    },
  };
}

function mapFuturesContext(context: BackendNseMarketContext['futures'], fallbackError: string | null): ProviderState<FuturesSnapshot> {
  if (!context || context.ltp == null || context.underlyingValue == null) {
    return { status: context ? providerStatus(context.status) : 'error', lastUpdated: null,
      latency: null, error: fallbackError, data: null };
  }
  const updatedAt = parseNseTimestamp(context.timestamp);
  return {
    status: providerStatus(context.status), lastUpdated: updatedAt, latency: null,
    error: context.status === 'UNAVAILABLE' ? fallbackError : null,
    data: {
      contract: context.identifier, identifier: context.identifier, instrumentType: context.instrumentType,
      expiry: context.expiry, ltp: context.ltp, open: context.open, high: context.high, low: context.low,
      previousClose: context.previousClose, change: context.change, changePercent: context.changePercent,
      volume: context.volume, turnover: context.turnover, openInterest: context.openInterest,
      changeInOpenInterest: context.changeInOpenInterest,
      percentChangeInOpenInterest: context.percentChangeInOpenInterest,
      underlying: context.underlying, underlyingValue: context.underlyingValue, timestamp: context.timestamp,
    },
  };
}

export function buildBackendFeatureSnapshot(input: MarketDataBundle, context: BackendNseMarketContext): MarketFeatureSnapshot | null {
  const nifty = context.nifty;
  if (!nifty || nifty.ltp == null) return null;
  const futures = context.futures;
  const options = selectAtmOptions(input.options.data ?? [], nifty.ltp);
  const totalCallOI = options.reduce((sum, item) => sum + item.ceOi, 0);
  const totalPutOI = options.reduce((sum, item) => sum + item.peOi, 0);
  const totalCallChangeOI = options.reduce((sum, item) => sum + item.ceChangeOi, 0);
  const totalPutChangeOI = options.reduce((sum, item) => sum + item.peChangeOi, 0);
  const levels = estimateSupportResistance({
    niftySpot: nifty.ltp,
    callOiByStrike: Object.fromEntries(options.map((item) => [String(item.strike), item.ceOi])),
    putOiByStrike: Object.fromEntries(options.map((item) => [String(item.strike), item.peOi])),
    callChangeOiByStrike: Object.fromEntries(options.map((item) => [String(item.strike), item.ceChangeOi])),
    putChangeOiByStrike: Object.fromEntries(options.map((item) => [String(item.strike), item.peChangeOi])),
  });
  const spot1m = nifty.return1m;
  const spot5m = nifty.return5m;
  const spot10m = nifty.return10m;
  const spot15m = nifty.return15m;
  const future5m = futures?.return5m;
  const future10m = futures?.return10m;
  const future15m = futures?.return15m;

  return {
    timestamp: context.timestamp,
    niftyPrice: nifty.ltp,
    niftyReturn1m: spot1m.value,
    niftyReturn5m: spot5m.value,
    niftyReturn10m: spot10m.value,
    niftyReturn15m: spot15m.value,
    niftyOvernightReturn: nifty.overnightReturn ?? null,
    niftyOpeningGap: nifty.openingGap ?? null,
    niftyMomentum: spot15m.status === 'AVAILABLE' && spot15m.value != null
      ? classifyMomentum(spot15m.value) : null,
    niftyMomentumStatus: spot15m.status,
    niftyReturn1mStatus: spot1m.status,
    niftyReturn5mStatus: spot5m.status,
    niftyReturn10mStatus: spot10m.status,
    niftyReturn15mStatus: spot15m.status,
    futuresSymbol: futures?.identifier ?? null,
    futuresExpiry: futures?.expiry ?? null,
    futuresPrice: futures?.ltp ?? null,
    futuresOvernightReturn: futures?.overnightReturn ?? null,
    futuresOpeningGap: futures?.openingGap ?? null,
    futuresReturn1m: null,
    futuresReturn5m: future5m?.value ?? null,
    futuresReturn10m: future10m?.value ?? null,
    futuresReturn15m: future15m?.value ?? null,
    futuresMomentum: future15m?.status === 'AVAILABLE' && future15m.value != null
      ? classifyMomentum(future15m.value) : null,
    futuresMomentumStatus: future15m?.status ?? 'UNAVAILABLE',
    futuresReturn5mStatus: future5m?.status ?? 'UNAVAILABLE',
    futuresReturn10mStatus: future10m?.status ?? 'UNAVAILABLE',
    futuresReturn15mStatus: future15m?.status ?? 'UNAVAILABLE',
    futuresChange: futures?.change ?? null,
    futuresChangePercent: futures?.changePercent ?? null,
    futuresChangeInOpenInterest: futures?.changeInOpenInterest ?? null,
    futuresPercentChangeInOpenInterest: futures?.percentChangeInOpenInterest ?? null,
    futuresVolume: futures?.volume ?? null,
    futuresUnderlyingValue: futures?.underlyingValue ?? null,
    futuresBasis: futures?.basis ?? null,
    futuresBasisPct: futures?.underlyingValue ? (futures.basis / futures.underlyingValue) * 100 : null,
    futuresBasisChange15m: null,
    spotReturn15m: spot15m.value,
    futuresLead: context.futuresLead.status === 'AVAILABLE' ? context.futuresLead.value : null,
    futuresLeadStatus: context.futuresLead.status,
    futuresOI: futures?.openInterest ?? null,
    futuresOIChangePct: futures?.percentChangeInOpenInterest ?? null,
    futuresPositioning: null,
    optionExpiry: input.selectedOptionExpiry,
    totalCallOI,
    totalPutOI,
    totalCallChangeOI,
    totalPutChangeOI,
    pcr: computePcr(totalCallOI, totalPutOI),
    changeOIPcr: computeChangeOiPcr(totalCallChangeOI, totalPutChangeOI),
    highestPutOIStrike: levels.highestPutOiStrike,
    highestCallOIStrike: levels.highestCallOiStrike,
    highestPutChangeOIStrike: levels.highestPutChangeOiStrike,
    highestCallChangeOIStrike: levels.highestCallChangeOiStrike,
    supportLevel: levels.support,
    resistanceLevel: levels.resistance,
    distanceFromSupport: levels.support == null ? null : nifty.ltp - levels.support,
    distanceFromResistance: levels.resistance == null ? null : levels.resistance - nifty.ltp,
    supportLabel: 'OPTION_OI_SUPPORT',
    resistanceLabel: 'OPTION_OI_RESISTANCE',
    callOiConcentration: levels.callOiConcentration,
    putOiConcentration: levels.putOiConcentration,
    dominantCallOiStrike: levels.highestCallOiStrike,
    dominantPutOiStrike: levels.highestPutOiStrike,
  };
}

const asNumber = (value: unknown, fallback = 0): number => {
  const num = Number(value);
  return Number.isFinite(num) ? num : fallback;
};

const asOptionalNumber = (value: unknown): number | null => {
  if (value == null || value === '') return null;
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
};

const parseTimestamp = (value: unknown): string => {
  if (typeof value === 'string' && value.trim()) return value;
  return new Date().toISOString();
};

const NSE_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function parseNseTimestamp(value: unknown): number | null {
  if (typeof value !== 'string') return null;
  const match = /^(\d{2})-([A-Za-z]{3})-(\d{4}) (\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value.trim());
  if (!match) return null;
  const month = NSE_MONTHS.findIndex((name) => name.toLowerCase() === match[2].toLowerCase());
  if (month < 0) return null;
  return Date.UTC(Number(match[3]), month, Number(match[1]), Number(match[4]), Number(match[5]), Number(match[6] ?? 0)) - 330 * 60_000;
}

export function parseNseExpiry(value: string): number | null {
  const match = /^(\d{2})-([A-Za-z]{3})-(\d{4})$/.exec(value);
  if (!match) return null;
  const month = NSE_MONTHS.findIndex((name) => name.toLowerCase() === match[2].toLowerCase());
  if (month < 0) return null;
  return Date.UTC(Number(match[3]), month, Number(match[1]));
}

export function selectNearestExpiry(expiries: string[], now = Date.now()): string | null {
  const today = new Date(now);
  today.setUTCHours(0, 0, 0, 0);
  return expiries
    .filter((expiry) => {
      const timestamp = parseNseExpiry(expiry);
      return timestamp != null && timestamp >= today.getTime();
    })
    .sort((a, b) => (parseNseExpiry(a) ?? 0) - (parseNseExpiry(b) ?? 0))[0] ?? null;
}

const getNestedValue = <T>(source: unknown, path: string[]): T | undefined => {
  let current: unknown = source;

  for (const segment of path) {
    if (!current || typeof current !== 'object' || !(segment in current)) {
      return undefined;
    }
    current = (current as Record<string, unknown>)[segment];
  }

  return current as T;
};

type NseConstituentRow = Record<string, unknown>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}

function findConstituentRows(source: unknown, depth = 0): NseConstituentRow[] | null {
  if (depth > 6 || source == null || typeof source !== 'object') return null;
  if (Array.isArray(source)) {
    const rows = source.filter(isRecord);
    if (rows.length && rows.some(row => typeof (row.cmSymbol ?? row.symbol ?? row.Symbol ?? row.tradingSymbol) === 'string')) {
      return rows;
    }
    for (const value of source) {
      const nested = findConstituentRows(value, depth + 1);
      if (nested) return nested;
    }
    return null;
  }

  const record = source as Record<string, unknown>;
  const preferredKeys = ['indexConstituents', 'constituents', 'records', 'data', 'result'];
  for (const key of preferredKeys) {
    if (key in record) {
      const nested = findConstituentRows(record[key], depth + 1);
      if (nested) return nested;
    }
  }
  for (const [key, value] of Object.entries(record)) {
    if (preferredKeys.includes(key)) continue;
    const nested = findConstituentRows(value, depth + 1);
    if (nested) return nested;
  }
  return null;
}

function firstNumber(row: NseConstituentRow, keys: string[]): number | null {
  for (const key of keys) {
    const value = asOptionalNumber(row[key]);
    if (value != null) return value;
  }
  return null;
}

function mapConstituent(row: NseConstituentRow): ConstituentItem | null {
  const rawSymbol = row.cmSymbol ?? row.symbol ?? row.Symbol ?? row.tradingSymbol ?? row.tradingsymbol;
  if (typeof rawSymbol !== 'string' || !rawSymbol.trim()) return null;

  const ltp = firstNumber(row, ['lasttradedPrice', 'lastTradedPrice', 'lastPrice', 'lastprice', 'ltp', 'price', 'last', 'closePrice']);
  let previousClose = firstNumber(row, ['previousClose', 'prevClose', 'previousclose', 'prevDayClose']);
  const change = firstNumber(row, ['change', 'netChange'])
    ?? (ltp != null && previousClose != null ? ltp - previousClose : null);
  if (previousClose == null && ltp != null && change != null) previousClose = ltp - change;
  const changePercent = firstNumber(row, ['pChange', 'perChange', 'percentChange', 'changePercent', 'pchange'])
    ?? (change != null && previousClose != null && previousClose !== 0 ? change / previousClose * 100 : null);
  const symbol = rawSymbol.trim().toUpperCase();

  return {
    symbol,
    ltp,
    open: firstNumber(row, ['openPrice', 'open', 'openprice']),
    previousClose,
    change,
    changePercent,
    volume: firstNumber(row, ['totaltradedquantity', 'totalTradedQuantity', 'totalTradedVolume', 'volume', 'quantityTraded']),
    weight: NIFTY50_WEIGHTS[symbol] ?? firstNumber(row, [
      'weightPercent', 'weightagePercent', 'weightPercentage', 'indexWeightPercent', 'weightage', 'weight', 'indexWeight',
    ]),
    sector: NIFTY50_SECTORS[symbol] ?? null,
  };
}

async function fetchNseJson<T>(url: string): Promise<T> {
  const startedAt = Date.now();
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json, text/plain, */*',
    },
  });

  if (!response.ok) {
    throw new Error(`NSE request failed: ${response.status} ${response.statusText}`);
  }

  const payload = (await response.json()) as T;

  return {
    ...(payload as object),
    __latencyMs: Date.now() - startedAt,
  } as T;
}

export class NseMarketDataProvider {
  constructor(private readonly baseUrl = `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'}/nse`) {}

  async getNifty(): Promise<ProviderState<NiftyMarketData>> {
    try {
      const startedAt = Date.now();
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/index-data?type=All`);
      const rows = Array.isArray(payload)
        ? payload
        : (getNestedValue<unknown[]>(payload, ['data']) ?? getNestedValue<unknown[]>(payload, ['result']) ?? []);
      const entry = (rows as Array<Record<string, unknown>>).find((item) => {
        const symbol = String(item.symbol ?? item.index ?? item.indexName ?? '').toUpperCase();
        return symbol.includes('NIFTY') || symbol.includes('NIFTY 50');
      }) ?? (rows as Array<Record<string, unknown>>)[0];

      if (!entry) {
        return { status: 'no-data', lastUpdated: null, latency: Date.now() - startedAt, error: 'NSE index response was empty.', data: null };
      }

      const previousClose = asNumber(entry.previousClose ?? entry.prevClose ?? entry.previousclose);
      const ltp = asNumber(entry.lastPrice ?? entry.lastprice ?? entry.price ?? entry.ltp ?? entry.last);
      const timestamp = parseTimestamp(entry.timestamp ?? entry.lastUpdateTime ?? entry.timeVal);
      const sourceUpdatedAt = parseNseTimestamp(timestamp);
      const data: NiftyMarketData = {
        symbol: String(entry.symbol ?? entry.index ?? entry.indexName ?? 'NIFTY'),
        ltp,
        open: asNumber(entry.open),
        high: asNumber(entry.high),
        low: asNumber(entry.low),
        previousClose,
        change: asNumber(entry.change, ltp - previousClose),
        changePercent: asNumber(entry.percentChange ?? entry.perChange ?? entry.changePercent ?? entry.percChange),
        volume: asNumber(entry.totalTradedVolume ?? entry.volume),
        timestamp,
        source: 'NSE',
      };

      return {
        status: isMarketDataStale(sourceUpdatedAt) ? 'stale' : 'live',
        lastUpdated: sourceUpdatedAt,
        latency: Date.now() - startedAt,
        error: null,
        data,
      };
    } catch (error) {
      return {
        status: 'error',
        lastUpdated: null,
        latency: null,
        error: error instanceof Error ? error.message : 'NSE request failed for NIFTY.',
        data: null,
      };
    }
  }

  async getEquityQuote(symbol: string): Promise<ProviderState<ConstituentItem>> {
    const startedAt = Date.now();
    try {
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/quote/${encodeURIComponent(symbol)}`);
      const envelope = isRecord(payload) ? payload : {};
      const responseRows = getNestedValue<Record<string, unknown>[]>(payload, ['equityResponse']);
      const data = responseRows?.[0] ?? (isRecord(envelope.data) ? envelope.data : envelope);
      const info = isRecord(data.metaData) ? data.metaData
        : isRecord(data.info) ? data.info : {};
      const metadata = isRecord(data.metadata) ? data.metadata : {};
      const tradeInfo = isRecord(data.tradeInfo) ? data.tradeInfo : {};
      const priceInfo = isRecord(data.priceInfo) ? data.priceInfo : {};
      const row = {
        ...data,
        ...info,
        ...tradeInfo,
        ...metadata,
        ...priceInfo,
        symbol: String(info.symbol ?? metadata.symbol ?? data.symbol ?? symbol),
      };
      const item = mapConstituent(row);
      if (!item || item.ltp == null) {
        return {
          status: 'no-data', lastUpdated: null, latency: Date.now() - startedAt,
          error: `NSE quote response was empty for ${symbol}.`, data: null,
        };
      }

      const timestamp = envelope.lastUpdateTime ?? metadata.lastUpdateTime ?? metadata.timestamp
        ?? priceInfo.lastUpdateTime ?? data.timestamp;
      const updatedAt = parseNseTimestamp(timestamp);
      return {
        status: updatedAt != null && isMarketDataStale(updatedAt) ? 'stale' : 'live',
        lastUpdated: updatedAt ?? Date.now(),
        latency: Date.now() - startedAt,
        error: null,
        data: item,
      };
    } catch (error) {
      return {
        status: 'error', lastUpdated: null, latency: Date.now() - startedAt,
        error: error instanceof Error ? error.message : `NSE request failed for ${symbol}.`, data: null,
      };
    }
  }

  async getConstituents(): Promise<ProviderState<ConstituentItem[]>> {
    const startedAt = Date.now();
    try {
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/constituents`);
      const rows = findConstituentRows(payload);
      if (!rows?.length) {
        return {
          status: 'no-data', lastUpdated: null, latency: Date.now() - startedAt,
          error: 'NSE constituent response contained no recognizable quote rows.', data: [],
        };
      }

      const constituents = rows.flatMap((row) => {
        const item = mapConstituent(row);
        return item ? [item] : [];
      });
      const timestamp = getNestedValue<unknown>(payload, ['timestamp'])
        ?? getNestedValue<unknown>(payload, ['data', 'timestamp']);
      const updatedAt = parseNseTimestamp(timestamp);
      return {
        status: updatedAt != null && isMarketDataStale(updatedAt) ? 'stale' : 'live',
        lastUpdated: updatedAt ?? Date.now(),
        latency: Date.now() - startedAt,
        error: constituents.length < 50
          ? `NSE returned ${constituents.length} of 50 recognizable constituents.`
          : null,
        data: constituents,
      };
    } catch (error) {
      return {
        status: 'error', lastUpdated: null, latency: Date.now() - startedAt,
        error: error instanceof Error ? error.message : 'NSE request failed for NIFTY 50 constituents.',
        data: [],
      };
    }
  }

  async getFutures(): Promise<ProviderState<FuturesSnapshot>> {
    const startedAt = Date.now();
    try {
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/futures`);
      const row = getNestedValue<Record<string, unknown>[]>(payload, ['data'])?.[0];
      const timestamp = (payload as Record<string, unknown>).timestamp;
      const updatedAt = parseNseTimestamp(timestamp);
      const lastPrice = asOptionalNumber(row?.lastPrice);
      const openInterest = asOptionalNumber(row?.openInterest);
      const underlyingValue = asOptionalNumber(row?.underlyingValue);
      const expiry = typeof row?.expiryDate === 'string' ? row.expiryDate : null;
      const identifier = typeof row?.identifier === 'string' ? row.identifier : null;

      if (!row || lastPrice == null || openInterest == null || underlyingValue == null
          || !expiry || !identifier || updatedAt == null
          || row.instrumentType !== 'FUTIDX' || row.underlying !== 'NIFTY') {
        return {
          status: 'no-data', lastUpdated: null, latency: Date.now() - startedAt,
          error: 'NSE futures response was empty or missing required contract fields.', data: null,
        };
      }

      const data: FuturesSnapshot = {
        contract: identifier,
        identifier,
        instrumentType: String(row.instrumentType),
        expiry,
        ltp: lastPrice,
        open: asOptionalNumber(row.openPrice),
        high: asOptionalNumber(row.highPrice),
        low: asOptionalNumber(row.lowPrice),
        previousClose: asOptionalNumber(row.prevClose),
        change: asOptionalNumber(row.change),
        changePercent: asOptionalNumber(row.pchange),
        volume: asOptionalNumber(row.totalTradedVolume),
        turnover: asOptionalNumber(row.totalTurnover),
        openInterest,
        changeInOpenInterest: asOptionalNumber(row.changeinOpenInterest),
        percentChangeInOpenInterest: asOptionalNumber(row.pchangeinOpenInterest),
        underlying: String(row.underlying),
        underlyingValue,
        timestamp: String(timestamp),
      };

      return {
        status: isMarketDataStale(updatedAt) ? 'stale' : 'live',
        lastUpdated: updatedAt,
        latency: Date.now() - startedAt,
        error: null,
        data,
      };
    } catch (error) {
      return {
        status: 'error', lastUpdated: null, latency: Date.now() - startedAt,
        error: error instanceof Error ? error.message : 'NSE request failed for NIFTY futures.', data: null,
      };
    }
  }

  async getMarketContext(): Promise<ProviderState<BackendNseMarketContext>> {
    const startedAt = Date.now();
    try {
      const data = await fetchNseJson<BackendNseMarketContext>(`${this.baseUrl}/market-context`);
      const sourceStatuses = [data.nifty?.status, data.futures?.status];
      const status: ProviderStatus = sourceStatuses.includes('AVAILABLE')
        ? 'live'
        : sourceStatuses.includes('STALE')
          ? 'stale'
          : sourceStatuses.includes('UNAVAILABLE')
            ? 'error'
            : 'loading';
      return { status, lastUpdated: Date.now(), latency: Date.now() - startedAt, error: null, data };
    } catch (error) {
      return {
        status: 'error', lastUpdated: null, latency: Date.now() - startedAt,
        error: error instanceof Error ? error.message : 'NSE Market Context request failed.', data: null,
      };
    }
  }

  async getOptionExpiries(): Promise<ProviderState<string[]>> {
    try {
      const startedAt = Date.now();
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/option-expiries`);
      const expiries = getNestedValue<string[]>(payload, ['expiryDates']) ?? [];
      const data = expiries.filter((expiry) => parseNseExpiry(expiry) != null);
      return {
        status: data.length ? 'live' : 'no-data',
        lastUpdated: Date.now(),
        latency: Date.now() - startedAt,
        error: null,
        data,
      };
    } catch (error) {
      return {
        status: 'error',
        lastUpdated: null,
        latency: null,
        error: error instanceof Error ? error.message : 'NSE request failed for option expiries.',
        data: [],
      };
    }
  }

  async getOptions(expiry: string): Promise<ProviderState<OptionsSnapshot[]>> {
    try {
      const startedAt = Date.now();
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/option-chain?expiry=${encodeURIComponent(expiry)}`);
      const records = getNestedValue<unknown[]>(payload, ['records', 'data']) ?? [];

      const data = (records as Array<Record<string, unknown>>).map((row) => {
        const call = (row.CE ?? row.callOptions ?? {}) as Record<string, unknown>;
        const put = (row.PE ?? row.putOptions ?? {}) as Record<string, unknown>;

        return {
          symbol: 'NIFTY',
          expiry: String(row.expiryDates ?? expiry),
          strike: asNumber(row.strikePrice ?? call.strikePrice ?? put.strikePrice),
          ceLtp: asNumber(call.lastPrice ?? call.ltp),
          peLtp: asNumber(put.lastPrice ?? put.ltp),
          ceOi: asNumber(call.openInterest ?? call.oi),
          peOi: asNumber(put.openInterest ?? put.oi),
          ceChangeOi: asNumber(call.changeinOpenInterest ?? call.changeInOpenInterest),
          peChangeOi: asNumber(put.changeinOpenInterest ?? put.changeInOpenInterest),
          ceVolume: asNumber(call.totalTradedVolume ?? call.volume),
          peVolume: asNumber(put.totalTradedVolume ?? put.volume),
          ceIv: Number.isFinite(Number(call.impliedVolatility)) ? Number(call.impliedVolatility) : undefined,
          peIv: Number.isFinite(Number(put.impliedVolatility)) ? Number(put.impliedVolatility) : undefined,
        };
      }).filter((item) => item.strike > 0);

      const firstRecord = records[0] as Record<string, unknown> | undefined;
      const firstCall = firstRecord?.CE as Record<string, unknown> | undefined;
      const firstPut = firstRecord?.PE as Record<string, unknown> | undefined;
      const underlying = asNumber(firstRecord?.underlyingValue ?? firstCall?.underlyingValue ?? firstPut?.underlyingValue);
      const nearAtm = underlying > 0 ? selectAtmOptions(data, underlying) : data;
      return {
        status: nearAtm.length ? 'live' : 'no-data',
        lastUpdated: Date.now(),
        latency: Date.now() - startedAt,
        error: null,
        data: nearAtm,
      };
    } catch (error) {
      return {
        status: 'error',
        lastUpdated: null,
        latency: null,
        error: error instanceof Error ? error.message : 'NSE request failed for options.',
        data: [],
      };
    }
  }

  async getIndiaVix(): Promise<ProviderState<IndiaVixSnapshot>> {
    try {
      const startedAt = Date.now();
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/index-data?type=INDIA%20VIX`);
      const row = ((Array.isArray(payload) ? payload : getNestedValue<unknown[]>(payload, ['data']) ?? [payload]) as Array<Record<string, unknown>>).find((item) => {
        const symbol = String(item.symbol ?? item.index ?? item.indexName ?? '').toUpperCase();
        return symbol.includes('VIX');
      }) ?? (payload as Record<string, unknown>);

      const data: IndiaVixSnapshot = {
        symbol: 'INDIA VIX',
        ltp: asNumber((row as Record<string, unknown>).lastPrice ?? (row as Record<string, unknown>).ltp ?? (row as Record<string, unknown>).last),
        change: asNumber((row as Record<string, unknown>).change),
        changePercent: asNumber((row as Record<string, unknown>).percentChange ?? (row as Record<string, unknown>).changePercent ?? (row as Record<string, unknown>).percChange),
        open: asNumber((row as Record<string, unknown>).open),
        high: asNumber((row as Record<string, unknown>).high),
        low: asNumber((row as Record<string, unknown>).low),
        previousClose: asNumber((row as Record<string, unknown>).previousClose ?? (row as Record<string, unknown>).prevClose),
        timestamp: parseTimestamp((row as Record<string, unknown>).timestamp ?? (row as Record<string, unknown>).timeVal),
      };

      return {
        status: 'live',
        lastUpdated: Date.now(),
        latency: Date.now() - startedAt,
        error: null,
        data,
      };
    } catch (error) {
      return {
        status: 'error',
        lastUpdated: null,
        latency: null,
        error: error instanceof Error ? error.message : 'NSE request failed for India VIX.',
        data: null,
      };
    }
  }

  async getMarketStatistics(): Promise<ProviderState<MarketStatistics>> {
    try {
      const startedAt = Date.now();
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/index-data?type=All`);
      const rows = Array.isArray(payload) ? payload : getNestedValue<unknown[]>(payload, ['data']) ?? [];
      const breadth = Array.isArray(rows)
        ? (rows as Array<Record<string, unknown>>).find((item) => String(item.symbol ?? item.index ?? '').toUpperCase().includes('NIFTY'))
        : null;

      const data: MarketStatistics = {
        advances: asNumber((breadth as Record<string, unknown>)?.advances),
        declines: asNumber((breadth as Record<string, unknown>)?.declines),
        unchanged: asNumber((breadth as Record<string, unknown>)?.unchanged),
        volume: asNumber((breadth as Record<string, unknown>)?.totalVolume ?? (breadth as Record<string, unknown>)?.volume),
        turnover: asNumber((breadth as Record<string, unknown>)?.turnover),
        timestamp: parseTimestamp((breadth as Record<string, unknown>)?.timestamp ?? new Date().toISOString()),
      };

      return {
        status: data.advances || data.declines || data.volume ? 'live' : 'no-data',
        lastUpdated: Date.now(),
        latency: Date.now() - startedAt,
        error: null,
        data,
      };
    } catch (error) {
      return {
        status: 'error',
        lastUpdated: null,
        latency: null,
        error: error instanceof Error ? error.message : 'NSE request failed for market statistics.',
        data: null,
      };
    }
  }

  async getIndices(): Promise<ProviderState<Record<string, number>>> {
    try {
      const startedAt = Date.now();
      const payload = await fetchNseJson<unknown>(`${this.baseUrl}/index-data?type=All`);
      const rows = Array.isArray(payload) ? payload : getNestedValue<unknown[]>(payload, ['data']) ?? [];
      const indices: Record<string, number> = {};

      (rows as Array<Record<string, unknown>>).forEach((row) => {
        const name = String(row.symbol ?? row.index ?? row.indexName ?? '').trim();
        if (!name) return;
        indices[name] = asNumber(row.lastPrice ?? row.ltp ?? row.last ?? row.changePercent ?? row.percChange ?? 0);
      });

      return {
        status: Object.keys(indices).length ? 'live' : 'no-data',
        lastUpdated: Date.now(),
        latency: Date.now() - startedAt,
        error: null,
        data: indices,
      };
    } catch (error) {
      return {
        status: 'error',
        lastUpdated: null,
        latency: null,
        error: error instanceof Error ? error.message : 'NSE request failed for indices.',
        data: {},
      };
    }
  }

  async refreshAll(): Promise<MarketDataBundle> {
    const [marketContext, constituents, optionExpiries, indiaVix, marketStatistics, indices] = await Promise.all([
      this.getMarketContext(),
      this.getConstituents(),
      this.getOptionExpiries(),
      this.getIndiaVix(),
      this.getMarketStatistics(),
      this.getIndices(),
    ]);
    const nifty = mapNiftyContext(marketContext.data?.nifty ?? null, marketContext.error);
    const futures = mapFuturesContext(marketContext.data?.futures ?? null, marketContext.error);
    const selectedOptionExpiry = selectNearestExpiry(optionExpiries.data ?? []);
    const options = selectedOptionExpiry
      ? await this.getOptions(selectedOptionExpiry)
      : { status: 'no-data' as const, lastUpdated: null, latency: null, error: optionExpiries.error, data: [] };

    const baseBundle: MarketDataBundle = {
      nifty,
      constituents,
      futures,
      options,
      indiaVix,
      marketStatistics,
      indices,
      optionExpiries,
      selectedOptionExpiry,
      features: null,
    };
    return {
      ...baseBundle,
      features: marketContext.data ? buildBackendFeatureSnapshot(baseBundle, marketContext.data) : null,
    };
  }
}

export const nseMarketDataProvider = new NseMarketDataProvider();
