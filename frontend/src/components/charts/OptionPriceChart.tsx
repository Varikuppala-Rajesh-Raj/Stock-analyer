import { useEffect, useRef } from 'react';
import {
  ColorType,
  createChart,
  LineSeries,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import type { OptionPricePoint } from '../../services/api';

type Props = {
  points: OptionPricePoint[];
  color: string;
  height?: number;
};

export function OptionPriceChart({ points, color, height = 360 }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const chart = useRef<IChartApi | null>(null);
  const series = useRef<ISeriesApi<'Line'> | null>(null);

  useEffect(() => {
    if (!container.current) return;
    const instance = createChart(container.current, {
      width: container.current.clientWidth,
      height,
      layout: { background: { type: ColorType.Solid, color: '#0b0b0b' }, textColor: '#9ca3af' },
      grid: { vertLines: { color: '#1f2937' }, horzLines: { color: '#1f2937' } },
      rightPriceScale: { borderColor: '#374151' },
      timeScale: { borderColor: '#374151', timeVisible: true, secondsVisible: false },
      localization: { priceFormatter: (price: number) => `₹${price.toFixed(2)}` },
    });
    const line = instance.addSeries(LineSeries, {
      color,
      lineWidth: 2,
      priceLineVisible: true,
      lastValueVisible: true,
    });
    chart.current = instance;
    series.current = line;

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) instance.applyOptions({ width: entry.contentRect.width });
    });
    observer.observe(container.current);
    return () => {
      observer.disconnect();
      instance.remove();
      chart.current = null;
      series.current = null;
    };
  }, [color, height]);

  useEffect(() => {
    series.current?.setData(points.map((point) => ({
      time: Math.floor(new Date(point.timestamp).getTime() / 1000) as UTCTimestamp,
      value: Number(point.price),
    })));
    chart.current?.timeScale().fitContent();
  }, [points]);

  return <div ref={container} aria-label="NIFTY option premium price chart" />;
}
