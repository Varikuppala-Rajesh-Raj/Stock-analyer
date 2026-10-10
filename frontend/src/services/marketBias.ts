import type { OvernightRetrieveResult } from './overnightMarketData';
import type {
  FiiDiiFlow,
  MarketDataBundle,
  MarketFeatureSnapshot,
  ProviderStatus,
} from './nseMarketData';

export type BiasDirection = 'BULLISH' | 'BEARISH' | 'NEUTRAL' | 'UNAVAILABLE';
export type BiasGroupKey = 'GLOBAL' | 'DOMESTIC' | 'TECHNICAL' | 'OPTIONS' | 'RISK/VOLATILITY';
export type MarketBiasLabel =
  | 'STRONG BULLISH'
  | 'BULLISH'
  | 'SLIGHTLY BULLISH'
  | 'MIXED'
  | 'SLIGHTLY BEARISH'
  | 'BEARISH'
  | 'STRONG BEARISH';
export type MarketOutlook =
  | 'STRONG UP'
  | 'UP'
  | 'LEANING UP'
  | 'SIDEWAYS / MIXED'
  | 'LEANING DOWN'
  | 'DOWN'
  | 'STRONG DOWN'
  | 'INSUFFICIENT DATA';

export type MarketBiasConfig = {
  gapThresholdPct: number;
  flowNeutralThresholdCr: number;
  futuresOiThresholdPct: number;
  breadthNeutralThreshold: number;
  returnNeutralThresholdPct: number;
  pcrSupportiveThreshold: number;
  pcrBearishThreshold: number;
  minimumDataQualityPct: number;
  minimumAlignedGroups: number;
  maximumOpposingGroups: number;
  groupWeights: Record<BiasGroupKey, number>;
  finalScoreThresholds: {
    strong: number;
    directional: number;
    slight: number;
    mixed: number;
  };
};

const envThreshold = (name: string, fallback: number): number => {
  const value = Number(import.meta.env[name]);
  return Number.isFinite(value) && value >= 0 ? value : fallback;
};

export const DEFAULT_MARKET_BIAS_CONFIG: MarketBiasConfig = {
  gapThresholdPct: envThreshold('VITE_BIAS_GAP_THRESHOLD_PCT', 0.15),
  flowNeutralThresholdCr: envThreshold('VITE_BIAS_FLOW_NEUTRAL_THRESHOLD_CR', 500),
  futuresOiThresholdPct: envThreshold('VITE_BIAS_FUTURES_OI_THRESHOLD_PCT', 0.1),
  breadthNeutralThreshold: envThreshold('VITE_BIAS_BREADTH_NEUTRAL_THRESHOLD', 3),
  returnNeutralThresholdPct: envThreshold('VITE_BIAS_RETURN_NEUTRAL_THRESHOLD_PCT', 0.05),
  pcrSupportiveThreshold: envThreshold('VITE_BIAS_PCR_SUPPORTIVE_THRESHOLD', 1.1),
  pcrBearishThreshold: envThreshold('VITE_BIAS_PCR_BEARISH_THRESHOLD', 0.9),
  minimumDataQualityPct: envThreshold('VITE_BIAS_MIN_DATA_QUALITY_PCT', 70),
  minimumAlignedGroups: envThreshold('VITE_BIAS_MIN_ALIGNED_GROUPS', 3),
  maximumOpposingGroups: envThreshold('VITE_BIAS_MAX_OPPOSING_GROUPS', 1),
  groupWeights: {
    GLOBAL: envThreshold('VITE_BIAS_WEIGHT_GLOBAL', 1),
    DOMESTIC: envThreshold('VITE_BIAS_WEIGHT_DOMESTIC', 1),
    TECHNICAL: envThreshold('VITE_BIAS_WEIGHT_TECHNICAL', 1),
    OPTIONS: envThreshold('VITE_BIAS_WEIGHT_OPTIONS', 1),
    'RISK/VOLATILITY': envThreshold('VITE_BIAS_WEIGHT_RISK', 1),
  },
  finalScoreThresholds: {
    strong: envThreshold('VITE_BIAS_SCORE_STRONG', 2),
    directional: envThreshold('VITE_BIAS_SCORE_DIRECTIONAL', 1),
    slight: envThreshold('VITE_BIAS_SCORE_SLIGHT', 0.3),
    mixed: envThreshold('VITE_BIAS_SCORE_MIXED', 0.3),
  },
};

export type MarketBiasFactor = {
  name: string;
  direction: BiasDirection;
  score: number | null;
  detail: string;
};

export type MarketBiasGroup = {
  key: BiasGroupKey;
  score: number | null;
  direction: BiasDirection;
  weight: number;
  factors: MarketBiasFactor[];
};

export type MarketBiasAssessment = {
  label: MarketBiasLabel | 'UNAVAILABLE';
  outlook: MarketOutlook;
  score: number | null;
  confidence: 'LOW' | 'LOW–MODERATE' | 'MODERATE' | 'HIGH';
  dataQualityPct: number;
  bullishFactors: number;
  bearishFactors: number;
  neutralFactors: number;
  tradeReady: boolean;
  tradeReadiness: string;
  groups: MarketBiasGroup[];
};

type OvernightQuotes = Record<string, OvernightRetrieveResult>;

const percentDirection = (value: number | null | undefined, threshold: number): BiasDirection => {
  if (value == null || !Number.isFinite(value)) return 'UNAVAILABLE';
  if (value >= threshold) return 'BULLISH';
  if (value <= -threshold) return 'BEARISH';
  return 'NEUTRAL';
};

const signedDirection = (value: number | null | undefined, threshold: number): BiasDirection => {
  if (value == null || !Number.isFinite(value)) return 'UNAVAILABLE';
  if (value >= threshold) return 'BULLISH';
  if (value <= -threshold) return 'BEARISH';
  return 'NEUTRAL';
};

const available = (status: ProviderStatus | undefined): boolean => status === 'live'
  || status === 'collected' || status === 'report-collected';

function factor(
  name: string,
  direction: BiasDirection,
  detail: string,
  neutralWeight = 0,
): MarketBiasFactor {
  if (direction === 'BULLISH') return { name, direction, score: 1, detail };
  if (direction === 'BEARISH') return { name, direction, score: -1, detail };
  if (direction === 'NEUTRAL') return { name, direction, score: neutralWeight, detail };
  return { name, direction, score: null, detail };
}

function quoteFactor(
  name: string,
  quote: OvernightRetrieveResult | undefined,
  threshold: number,
  inverse = false,
): MarketBiasFactor {
  const sessionUsable = quote?.lifecycleStatus === 'LIVE' || quote?.lifecycleStatus === 'COLLECTED'
    || (quote?.lifecycleStatus == null && quote?.status === 'FRESH');
  if (!quote || !sessionUsable || quote.netChange == null || quote.close == null || quote.close === 0) {
    return factor(name, 'UNAVAILABLE', quote?.message ?? 'Valid data from the required market session is unavailable.');
  }
  const pct = quote.netChange / quote.close * 100 * (inverse ? -1 : 1);
  return factor(name, percentDirection(pct, threshold), `${pct.toFixed(2)}% versus prior close.`);
}

function flowFactors(
  flows: FiiDiiFlow[],
  status: ProviderStatus | undefined,
  threshold: number,
): MarketBiasFactor[] {
  const categories: FiiDiiFlow['category'][] = ['FII/FPI', 'DII'];
  return categories.map((category) => {
    const flow = flows.find((item) => item.category === category);
    if (!flow || !available(status)) {
      return factor(category, 'UNAVAILABLE', status === 'stale' ? 'Flow report is stale.' : 'Fresh flow report unavailable.');
    }
    const direction = signedDirection(flow.netValue, threshold);
    return factor(category, direction,
      `Net ₹${flow.netValue.toLocaleString('en-IN', { maximumFractionDigits: 2 })} Cr · report ${flow.date ?? 'date unavailable'}.`);
  });
}

function futurePositionFactor(
  features: MarketFeatureSnapshot | null,
  threshold: number,
): MarketBiasFactor {
  if (!features || (features.futuresMomentumStatus !== 'AVAILABLE'
      && features.futuresMomentumStatus !== 'COLLECTED')
      || features.futuresPercentChangeInOpenInterest == null
      || Math.abs(features.futuresPercentChangeInOpenInterest) < threshold) {
    return factor('Futures price + OI', 'UNAVAILABLE', 'Fresh futures price and OI change are required.');
  }
  const direction = features.futuresMomentum;
  if (direction == null || direction === 'NEUTRAL') {
    return factor('Futures price + OI', 'NEUTRAL', 'Futures price is not directional.');
  }
  const priceUp = direction === 'UP' || direction === 'STRONG_UP';
  const oiUp = features.futuresPercentChangeInOpenInterest > 0;
  if (priceUp && oiUp) return factor('Futures price + OI', 'BULLISH', 'Price and open interest rising: long buildup.');
  if (!priceUp && oiUp) return factor('Futures price + OI', 'BEARISH', 'Price falling while open interest rises: short buildup.');
  if (priceUp) return factor('Futures price + OI', 'BULLISH', 'Price rising while open interest falls: short covering.');
  return factor('Futures price + OI', 'BEARISH', 'Price and open interest falling: long unwinding.');
}

function classifyGroup(
  key: BiasGroupKey,
  factors: MarketBiasFactor[],
  weight: number,
  threshold = 0.25,
): MarketBiasGroup {
  const values = factors.flatMap(({ score }) => score == null ? [] : [score]);
  const score = values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null;
  const direction: BiasDirection = score == null
    ? 'UNAVAILABLE'
    : score >= threshold ? 'BULLISH'
      : score <= -threshold ? 'BEARISH' : 'NEUTRAL';
  return { key, score, direction, weight, factors };
}

export function calculateMarketBias(
  bundle: MarketDataBundle | null,
  quotes: OvernightQuotes,
  config: MarketBiasConfig = DEFAULT_MARKET_BIAS_CONFIG,
): MarketBiasAssessment {
  const features = bundle?.features ?? null;
  const nifty = bundle?.nifty.data ?? null;
  const premarket = bundle?.premarket?.data;
  const flows = premarket?.fiiDii ?? [];
  const flowStatus = premarket?.fiiDiiStatus;
  const threshold = config.returnNeutralThresholdPct;
  const groups: MarketBiasGroup[] = [];

  const niftyPriorClose = nifty?.previousClose;
  const gift = quotes.GIFT_NIFTY;
  const giftSessionUsable = gift?.lifecycleStatus === 'LIVE' || gift?.lifecycleStatus === 'COLLECTED'
    || (gift?.lifecycleStatus == null && gift?.status === 'FRESH');
  const giftGap = giftSessionUsable && gift?.lastPrice != null && niftyPriorClose != null && niftyPriorClose !== 0
    ? (gift.lastPrice - niftyPriorClose) / niftyPriorClose * 100
    : null;
  groups.push(classifyGroup('GLOBAL', [
    factor('GIFT NIFTY gap', percentDirection(giftGap, config.gapThresholdPct),
      giftGap == null ? 'Fresh GIFT NIFTY and NIFTY previous close are required.' : `${giftGap.toFixed(2)}% implied gap.`),
    quoteFactor('S&P 500', quotes.SP500, threshold),
    quoteFactor('US Tech 100', quotes.US_TECH_100, threshold),
    quoteFactor('Dow', quotes.DOW, threshold),
    quoteFactor('Nikkei', quotes.NIKKEI, threshold),
    quoteFactor('Hang Seng', quotes.HANG_SENG, threshold),
    quoteFactor('FTSE 100', quotes.FTSE_100, threshold),
    quoteFactor('DAX', quotes.DAX, threshold),
    quoteFactor('USD / INR', quotes.USD_INR, threshold, true),
    quoteFactor('Brent crude', quotes.BRENT, threshold, true),
  ], config.groupWeights.GLOBAL));

  const flowEvidence = flowFactors(flows, flowStatus, config.flowNeutralThresholdCr);
  const basisPct = features?.futuresBasisPct;
  const basisDirection = basisPct == null ? 'UNAVAILABLE'
    : basisPct > config.gapThresholdPct ? 'BULLISH'
      : basisPct < -config.gapThresholdPct ? 'BEARISH' : 'NEUTRAL';
  const breadth = bundle?.marketStatistics.data;
  const breadthDirection = !breadth || !available(bundle?.marketStatistics.status)
    ? 'UNAVAILABLE'
    : signedDirection(breadth.advances - breadth.declines, config.breadthNeutralThreshold);
  const vixDirection = available(premarket?.indiaVixStatus)
    ? signedDirection(premarket?.indiaVix?.changePercent, threshold)
    : 'UNAVAILABLE';
  groups.push(classifyGroup('DOMESTIC', [
    ...flowEvidence,
    futurePositionFactor(features, config.futuresOiThresholdPct),
    factor('NIFTY futures basis', basisDirection,
      basisPct == null ? 'Matched futures and spot basis unavailable.' : `${basisPct.toFixed(2)}% basis.`),
    factor('Market breadth', breadthDirection,
      breadth ? `${breadth.advances} advances · ${breadth.declines} declines.` : 'Live market breadth unavailable.'),
    factor('NIFTY price structure', percentDirection(nifty?.changePercent, threshold),
      nifty ? `Change ${nifty.changePercent?.toFixed(2) ?? 'unavailable'}% versus previous close.` : 'Fresh NIFTY quote unavailable.'),
    factor('India VIX move', vixDirection,
      premarket?.indiaVix
        ? `${premarket.indiaVix.changePercent.toFixed(2)}% · risk modifier, not a directional signal.`
        : 'Fresh India VIX value unavailable.', 0),
  ], config.groupWeights.DOMESTIC));

  const return5m = features && (features.niftyReturn5mStatus === 'AVAILABLE'
    || features.niftyReturn5mStatus === 'COLLECTED')
    ? percentDirection(features.niftyReturn5m, threshold) : 'UNAVAILABLE';
  const return15m = features && (features.niftyReturn15mStatus === 'AVAILABLE'
    || features.niftyReturn15mStatus === 'COLLECTED')
    ? percentDirection(features.niftyReturn15m, threshold) : 'UNAVAILABLE';
  const gapDirection = percentDirection(features?.niftyOpeningGap, config.gapThresholdPct);
  const previousCloseDirection = percentDirection(nifty?.changePercent, threshold);
  groups.push(classifyGroup('TECHNICAL', [
    factor('NIFTY 5-minute return', return5m,
      features?.niftyReturn5m == null ? 'Fresh 5-minute return unavailable.' : `${features.niftyReturn5m.toFixed(2)}%.`),
    factor('NIFTY 15-minute return', return15m,
      features?.niftyReturn15m == null ? 'Fresh 15-minute return unavailable.' : `${features.niftyReturn15m.toFixed(2)}%.`),
    factor('Opening gap', gapDirection,
      features?.niftyOpeningGap == null ? 'Opening gap unavailable.' : `${features.niftyOpeningGap.toFixed(2)}%.`),
    factor('Previous-close structure', previousCloseDirection,
      nifty?.changePercent == null ? 'NIFTY previous-close comparison unavailable.' : `${nifty.changePercent.toFixed(2)}%.`),
  ], config.groupWeights.TECHNICAL));

  const optionsAvailable = bundle?.options.status === 'live' && !!features?.optionExpiry;
  const pcr = features?.pcr;
  const pcrDirection: BiasDirection = !optionsAvailable || pcr == null ? 'UNAVAILABLE'
    : pcr >= config.pcrSupportiveThreshold ? 'BULLISH'
      : pcr <= config.pcrBearishThreshold ? 'BEARISH' : 'NEUTRAL';
  const spotPrice = features?.niftyPrice;
  const putSupport = features?.highestPutOIStrike;
  const callResistance = features?.highestCallOIStrike;
  const oiDirection: BiasDirection = !optionsAvailable || spotPrice == null || putSupport == null || callResistance == null
    ? 'UNAVAILABLE'
    : putSupport < spotPrice && callResistance > spotPrice
      ? (features?.totalPutChangeOI ?? 0) > Math.abs(features?.totalCallChangeOI ?? 0) ? 'BULLISH' : 'NEUTRAL'
      : putSupport > spotPrice && callResistance < spotPrice ? 'BEARISH' : 'NEUTRAL';
  groups.push(classifyGroup('OPTIONS', [
    factor('Put-call ratio', pcrDirection, pcr == null ? 'Live option-chain PCR unavailable.' : `PCR ${pcr.toFixed(2)} · supportive context, not a standalone signal.`, 0),
    factor('OI support / resistance', oiDirection,
      oiDirection === 'UNAVAILABLE' ? 'Fresh OI concentrations around spot unavailable.'
        : `Put support ${putSupport ?? '—'} · call resistance ${callResistance ?? '—'}.`),
  ], config.groupWeights.OPTIONS));

  const niftyDirection = signedDirection(features?.niftyReturn5m, threshold);
  const freshVix = available(premarket?.indiaVixStatus) ? premarket?.indiaVix ?? null : null;
  let riskDirection: BiasDirection = 'UNAVAILABLE';
  let riskDetail = 'Fresh India VIX and NIFTY direction are required.';
  if (freshVix && niftyDirection !== 'UNAVAILABLE') {
    if (freshVix.change < 0 && niftyDirection === 'BULLISH') {
      riskDirection = 'BULLISH';
      riskDetail = 'VIX falling with NIFTY rising: supportive conditions.';
    } else if (freshVix.change > 0 && niftyDirection === 'BEARISH') {
      riskDirection = 'BEARISH';
      riskDetail = 'VIX rising with NIFTY falling: risk-off confirmation.';
    } else if (freshVix.change > 0 && niftyDirection === 'BULLISH') {
      riskDirection = 'NEUTRAL';
      riskDetail = 'VIX rising with NIFTY rising: conflicting, use caution.';
    } else {
      riskDirection = 'NEUTRAL';
      riskDetail = 'VIX falling with NIFTY falling: mixed risk context.';
    }
  }
  groups.push(classifyGroup('RISK/VOLATILITY', [
    factor('VIX + NIFTY interaction', riskDirection, riskDetail, 0),
  ], config.groupWeights['RISK/VOLATILITY']));

  const allFactors = groups.flatMap((group) => group.factors);
  const availableFactors = allFactors.filter(({ direction }) => direction !== 'UNAVAILABLE');
  const dataQualityPct = allFactors.length
    ? Math.round(availableFactors.length / allFactors.length * 100) : 0;
  const bullishFactors = availableFactors.filter(({ direction }) => direction === 'BULLISH').length;
  const bearishFactors = availableFactors.filter(({ direction }) => direction === 'BEARISH').length;
  const neutralFactors = availableFactors.filter(({ direction }) => direction === 'NEUTRAL').length;
  const usableGroups = groups.filter(({ score, weight }) => score != null && weight > 0);
  const weightTotal = usableGroups.reduce((sum, group) => sum + Math.max(0, group.weight), 0);
  const score = weightTotal > 0
    ? usableGroups.reduce((sum, group) => sum + (group.score ?? 0) * Math.max(0, group.weight), 0)
      / weightTotal * 3
    : null;
  const alignedDirection = score == null ? null : score > 0 ? 'BULLISH' : score < 0 ? 'BEARISH' : null;
  const alignedGroups = alignedDirection
    ? groups.filter((group) => group.direction === alignedDirection).length : 0;
  const opposingGroups = alignedDirection
    ? groups.filter((group) => group.direction !== 'NEUTRAL' && group.direction !== 'UNAVAILABLE'
      && group.direction !== alignedDirection).length : 0;
  const conflict = opposingGroups > config.maximumOpposingGroups;
  const strongOutlookSupported = dataQualityPct >= config.minimumDataQualityPct
    && alignedGroups >= config.minimumAlignedGroups
    && !conflict;
  let label: MarketBiasAssessment['label'] = 'UNAVAILABLE';
  if (score != null) {
    if (conflict || Math.abs(score) < config.finalScoreThresholds.mixed) label = 'MIXED';
    else if (score >= config.finalScoreThresholds.strong) {
      label = strongOutlookSupported ? 'STRONG BULLISH' : 'BULLISH';
    }
    else if (score >= config.finalScoreThresholds.directional) label = 'BULLISH';
    else if (score >= config.finalScoreThresholds.slight) label = 'SLIGHTLY BULLISH';
    else if (score <= -config.finalScoreThresholds.strong) {
      label = strongOutlookSupported ? 'STRONG BEARISH' : 'BEARISH';
    }
    else if (score <= -config.finalScoreThresholds.directional) label = 'BEARISH';
    else if (score <= -config.finalScoreThresholds.slight) label = 'SLIGHTLY BEARISH';
    else label = 'MIXED';
  }
  const tradeReady = score != null
    && dataQualityPct >= config.minimumDataQualityPct
    && label !== 'MIXED'
    && label !== 'UNAVAILABLE'
    && alignedGroups >= config.minimumAlignedGroups
    && opposingGroups <= config.maximumOpposingGroups;
  const confidence: MarketBiasAssessment['confidence'] = dataQualityPct >= 85 && alignedGroups >= 4 && !conflict
    ? 'HIGH'
    : dataQualityPct >= config.minimumDataQualityPct && alignedGroups >= 3 && !conflict
      ? 'MODERATE'
      : dataQualityPct >= 50 ? 'LOW–MODERATE' : 'LOW';
  const tradeReadiness = tradeReady
    ? 'PAPER TRADE ELIGIBLE · existing strategy and risk checks still apply'
    : `NO CLEAR EDGE · requires ${config.minimumDataQualityPct}% data quality and ${config.minimumAlignedGroups}/5 aligned groups`;
  const outlook: MarketOutlook = label === 'UNAVAILABLE'
    ? 'INSUFFICIENT DATA'
    : label === 'MIXED'
      ? 'SIDEWAYS / MIXED'
      : label === 'STRONG BULLISH'
        ? strongOutlookSupported ? 'STRONG UP' : 'UP'
        : label === 'BULLISH'
          ? 'UP'
          : label === 'SLIGHTLY BULLISH'
            ? 'LEANING UP'
            : label === 'STRONG BEARISH'
              ? strongOutlookSupported ? 'STRONG DOWN' : 'DOWN'
              : label === 'BEARISH'
                ? 'DOWN'
                : 'LEANING DOWN';
  return {
    label,
    outlook,
    score,
    confidence,
    dataQualityPct,
    bullishFactors,
    bearishFactors,
    neutralFactors,
    tradeReady,
    tradeReadiness,
    groups,
  };
}
