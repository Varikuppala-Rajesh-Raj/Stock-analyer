import { useEffect, useRef } from 'react';
import {
  createChart,
  CandlestickSeries,
  HistogramSeries,
  ColorType,
  type IChartApi,
  type ISeriesApi,
  TickMarkType,
  type Time,
  type UTCTimestamp,
} from 'lightweight-charts';

type Candle =
  | [string, number, number, number, number, number]
  | {
      timestamp: string;
      open: number;
      high: number;
      low: number;
      close: number;
      volume: number;
    };

type Props = {
  candles: Candle[];
  height?: number;
};

const indiaTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const indiaDateTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  dateStyle: 'medium',
  timeStyle: 'short',
});

const indiaDateFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  day: '2-digit',
  month: 'short',
});

const indiaMonthFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  month: 'short',
});

const indiaYearFormatter = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  year: 'numeric',
});

function chartTimeToDate(time: Time): Date {
  if (typeof time === 'number') return new Date(time * 1000);
  if (typeof time === 'string') return new Date(time);
  return new Date(Date.UTC(time.year, time.month - 1, time.day));
}

function normalizeCandle(candle: Candle) {
  if (Array.isArray(candle)) {
    const [timestamp, open, high, low, close, volume] = candle;

    return {
      time: Math.floor(
        new Date(timestamp).getTime() / 1000
      ) as UTCTimestamp,
      open: Number(open),
      high: Number(high),
      low: Number(low),
      close: Number(close),
      volume: Number(volume ?? 0),
    };
  }

  return {
    time: Math.floor(
      new Date(candle.timestamp).getTime() / 1000
    ) as UTCTimestamp,
    open: Number(candle.open),
    high: Number(candle.high),
    low: Number(candle.low),
    close: Number(candle.close),
    volume: Number(candle.volume ?? 0),
  };
}

export function PriceChart({
  candles,
  height = 520,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  const chartRef = useRef<IChartApi | null>(null);

  const candleSeriesRef =
    useRef<ISeriesApi<'Candlestick'> | null>(null);

  const volumeSeriesRef =
    useRef<ISeriesApi<'Histogram'> | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      width: containerRef.current.clientWidth,
      height,

      layout: {
        background: {
          type: ColorType.Solid,
          color: '#0b0b0b',
        },
        textColor: '#9ca3af',
      },

      grid: {
        vertLines: {
          color: '#1f2937',
        },
        horzLines: {
          color: '#1f2937',
        },
      },

      crosshair: {
        mode: 1,
      },

      rightPriceScale: {
        borderColor: '#374151',
      },

      timeScale: {
        borderColor: '#374151',
        timeVisible: true,
        secondsVisible: false,
        tickMarkFormatter: (time: Time, tickType: TickMarkType) => {
          const date = chartTimeToDate(time);
          if (tickType === TickMarkType.Year) return indiaYearFormatter.format(date);
          if (tickType === TickMarkType.Month) return indiaMonthFormatter.format(date);
          if (tickType === TickMarkType.DayOfMonth) return indiaDateFormatter.format(date);
          return indiaTimeFormatter.format(date);
        },
      },

      localization: {
        priceFormatter: (price: number) =>
          price.toFixed(2),
        timeFormatter: (time: Time) =>
          indiaDateTimeFormatter.format(chartTimeToDate(time)),
      },
    });

    const candleSeries =
      chart.addSeries(CandlestickSeries, {
        upColor: '#00c087',
        downColor: '#ef4444',
        borderUpColor: '#00c087',
        borderDownColor: '#ef4444',
        wickUpColor: '#00c087',
        wickDownColor: '#ef4444',
      });

    const volumeSeries =
      chart.addSeries(HistogramSeries, {
        priceFormat: {
          type: 'volume',
        },

        priceScaleId: '',
      });

    volumeSeries.priceScale().applyOptions({
      scaleMargins: {
        top: 0.82,
        bottom: 0,
      },
    });

    candleSeriesRef.current = candleSeries;
    volumeSeriesRef.current = volumeSeries;
    chartRef.current = chart;

    const resizeObserver = new ResizeObserver(
      entries => {
        const entry = entries[0];

        if (!entry) return;

        chart.applyOptions({
          width: entry.contentRect.width,
        });
      }
    );

    resizeObserver.observe(containerRef.current);

    return () => {
      resizeObserver.disconnect();
      chart.remove();

      chartRef.current = null;
      candleSeriesRef.current = null;
      volumeSeriesRef.current = null;
    };
  }, [height]);

  useEffect(() => {
    const candleSeries = candleSeriesRef.current;
    const volumeSeries = volumeSeriesRef.current;
    const chart = chartRef.current;

    if (!candleSeries || !volumeSeries || !chart) {
      return;
    }

    const normalized = candles
      .map(normalizeCandle)
      .filter(
        candle =>
          Number.isFinite(candle.time) &&
          Number.isFinite(candle.open) &&
          Number.isFinite(candle.high) &&
          Number.isFinite(candle.low) &&
          Number.isFinite(candle.close)
      )
      .sort((a, b) => Number(a.time) - Number(b.time));

    if (normalized.length === 0) {
      candleSeries.setData([]);
      volumeSeries.setData([]);
      return;
    }

    const unique = normalized.filter(
      (candle, index, array) =>
        index === 0 ||
        candle.time !== array[index - 1].time
    );

    candleSeries.setData(
      unique.map(candle => ({
        time: candle.time,
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
      }))
    );

    volumeSeries.setData(
      unique.map(candle => ({
        time: candle.time,
        value: candle.volume,
        color:
          candle.close >= candle.open
            ? 'rgba(0, 192, 135, 0.45)'
            : 'rgba(239, 68, 68, 0.45)',
      }))
    );

    chart.timeScale().fitContent();
  }, [candles]);

  if (!candles.length) {
    return (
      <div
        className="api-state"
        style={{
          height,
          display: 'grid',
          placeItems: 'center',
        }}
      >
        No candle data available for this timeframe.
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      style={{
        width: '100%',
        height,
      }}
    />
  );
}