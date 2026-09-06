import { useEffect, useRef } from 'react';
import {
  createChart,
  LineSeries,
  ColorType,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';

export type PnlPoint = {
  timestamp: string;
  pnl: number;
};

type Props = {
  points: PnlPoint[];
  height?: number;
};

export function PnlChart({
  points,
  height = 300,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  const chartRef = useRef<IChartApi | null>(null);

  const seriesRef =
    useRef<ISeriesApi<'Line'> | null>(null);

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

      rightPriceScale: {
        borderColor: '#374151',
      },

      timeScale: {
        borderColor: '#374151',
        timeVisible: true,
        secondsVisible: false,
      },

      crosshair: {
        mode: 1,
      },

      localization: {
        priceFormatter: (value: number) =>
          `₹${value.toFixed(2)}`,
      },
    });

    const series = chart.addSeries(LineSeries, {
      lineWidth: 2,
    });

    seriesRef.current = series;
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
      seriesRef.current = null;
    };
  }, [height]);

  useEffect(() => {
    const series = seriesRef.current;
    const chart = chartRef.current;

    if (!series || !chart) return;

    const data = points
      .map(point => ({
        time: Math.floor(
          new Date(point.timestamp).getTime() / 1000
        ) as UTCTimestamp,
        value: Number(point.pnl),
      }))
      .filter(
        point =>
          Number.isFinite(point.time) &&
          Number.isFinite(point.value)
      )
      .sort(
        (a, b) =>
          Number(a.time) - Number(b.time)
      );

    if (!data.length) {
      series.setData([]);
      return;
    }

    const unique = data.filter(
      (point, index, array) =>
        index === 0 ||
        point.time !== array[index - 1].time
    );

    series.setData(unique);

    chart.timeScale().fitContent();
  }, [points]);

  if (!points.length) {
    return (
      <div
        className="api-state"
        style={{
          height,
          display: 'grid',
          placeItems: 'center',
        }}
      >
        No P&L history available yet.
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