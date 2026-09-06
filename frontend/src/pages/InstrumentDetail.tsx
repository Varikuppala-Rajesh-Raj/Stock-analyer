import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useCandles, useQuote, useScanner, useSubmitOrder } from '../services/hooks';
import { PriceChart } from '../components/charts/PriceChart';

const frames = ['1m', '5m', '15m', '1h', '1D'];
const number = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const money = (value?: number) => value == null || !Number.isFinite(value) ? '—' : `₹${number.format(value)}`;

export function InstrumentDetail() {
  const { symbol = '' } = useParams<{ symbol: string }>();
  const navigate = useNavigate();
  const [timeframe, setTimeframe] = useState('15m');
  const [quantity, setQuantity] = useState('1');
  const [stopLoss, setStopLoss] = useState('');
  const [target, setTarget] = useState('');
  const [notice, setNotice] = useState('');
  const { quote, loading: quoteLoading } = useQuote(symbol, 10000);
  const { candles, loading: candlesLoading, error: candleError } = useCandles(symbol, timeframe, true);
  const { signals, loading: scannerLoading } = useScanner(timeframe, 30000);
  const { submit, loading: submitting, error: orderError } = useSubmitOrder();
  const lastPrice = Number(quote?.lastPrice ?? quote?.price);
  const openPrice = Number(quote?.open ?? lastPrice);
  const change = openPrice ? ((lastPrice - openPrice) / openPrice) * 100 : 0;
  const signal = signals?.find((item: any) => item.symbol?.toUpperCase() === symbol.toUpperCase());
  const isMlSupported = symbol.toUpperCase() === 'NIFTY';

  const placeOrder = async (side: 'BUY' | 'SELL') => {
    setNotice('');
    try {
      await submit({ symbol, side, quantity: Math.max(1, Number(quantity) || 1), stopLoss: stopLoss ? Number(stopLoss) : undefined, target: target ? Number(target) : undefined });
      setNotice(`${side} paper order filled. Review it in Paper trading.`);
    } catch { /* hook exposes the message */ }
  };
  const trainCompanyModel = () => {
    if (isMlSupported) navigate('/ml-lab');
    else setNotice(`${symbol} is ready for company-level research, but the connected model service currently supports NIFTY training only. Select NIFTY in ML Lab to run a real model.`);
  };
  if (!symbol) return <main><p>Instrument not found.</p></main>;
  return <main>
    <div className="heading"><div><small>INSTRUMENT ANALYSIS · NSE</small><h1>{symbol}</h1><p>{quote?.instrumentName || quote?.companyName || symbol} · live quote refreshes every 10 seconds</p></div><button className="outline" onClick={() => navigate('/watchlist')}>← Watchlist</button></div>
    <section className="stats">
      <Metric label="Last price" value={quoteLoading ? 'Loading…' : money(lastPrice)} detail={`${change >= 0 ? '+' : ''}${change.toFixed(2)}% today`} tone={change >= 0 ? 'good' : 'bad'} />
      <Metric label="Day high / low" value={quote ? `${money(Number(quote.high))} / ${money(Number(quote.low))}` : '—'} detail="Session range" />
      <Metric label="Volume" value={quote?.volume ? Number(quote.volume).toLocaleString('en-IN') : '—'} detail="Today" />
      <Metric label="Strategy score" value={scannerLoading ? 'Loading…' : signal ? `${signal.signalStrength ?? signal.score}/100` : 'No signal'} detail={signal?.signal || 'Technical scanner'} tone={signal?.signal === 'SELL' ? 'bad' : signal?.signal === 'BUY' ? 'good' : ''} />
    </section>
    <div className="two">
      <section className="panel market"><div className="panel-title"><div><small>LIVE + HISTORICAL PRICE</small><h2>{symbol} chart</h2></div><div className="tabs">{frames.map(frame => <button key={frame} className={timeframe === frame ? 'chosen' : ''} onClick={() => setTimeframe(frame)}>{frame}</button>)}</div></div>
        <div className="price"><b>{money(lastPrice)}</b><span className={change >= 0 ? 'good' : 'bad'}>{change >= 0 ? '+' : ''}{change.toFixed(2)}%</span><small>Volume {quote?.volume?.toLocaleString?.() || '—'}</small></div>
        {candlesLoading ? <p>Loading {timeframe} chart…</p> : candleError ? <p className="bad">Chart data is unavailable: {candleError}</p> : <PriceChart candles={candles} />}
      </section>
      <section className="panel detail-signal"><div className="panel-title"><div><small>AI + TECHNICAL DECISION</small><h2>{signal?.signal || 'HOLD'} setup</h2></div><span className={`pill ${(signal?.signal || 'hold').toLowerCase()}`}>{signal?.signalStrength ?? signal?.score ?? '—'}/100</span></div>
        <div className="levels"><span>Suggested entry<b>{money(Number(signal?.entry ?? lastPrice))}</b></span><span>Risk / target<b>{money(Number(signal?.stopLoss))} / {money(Number(signal?.target))}</b></span></div>
        <h3>Why this setup</h3><ul>{signal?.reasons?.length ? signal.reasons.slice(0, 4).map((reason: string) => <li key={reason}>{reason}</li>) : <><li>Live price and volume are being tracked.</li><li>Run the scanner for a fresh technical decision.</li></>}</ul>
        <button className="outline" onClick={trainCompanyModel}>{isMlSupported ? 'Train NIFTY AI model' : 'Company AI research'}</button>
      </section>
    </div>
    <div className="two lower">
      <section className="panel"><div className="panel-title"><div><small>PAPER TRADE</small><h2>Buy or sell {symbol}</h2></div><span className="pill ready">SIMULATED</span></div>
        <div className="order-fields"><label>Quantity<input min="1" type="number" value={quantity} onChange={e => setQuantity(e.target.value)} /></label><label>Stop loss<input type="number" placeholder="Optional" value={stopLoss} onChange={e => setStopLoss(e.target.value)} /></label><label>Target<input type="number" placeholder="Optional" value={target} onChange={e => setTarget(e.target.value)} /></label></div>
        <div className="order-actions"><button className="primary buy-order" disabled={submitting} onClick={() => placeOrder('BUY')}>{submitting ? 'Submitting…' : 'Buy'}</button><button className="primary sell-order" disabled={submitting} onClick={() => placeOrder('SELL')}>{submitting ? 'Submitting…' : 'Sell / Short'}</button></div>
        {(notice || orderError) && <p className={orderError ? 'bad' : 'good'}>{orderError || notice}</p>}</section>
      <section className="panel"><small>SMART WORKFLOW</small><h2>Trade with a plan</h2><div className="workflow"><span>1</span><p><b>Watch</b><small>Save the company and monitor live movement.</small></p><span>2</span><p><b>Validate</b><small>Use chart, signal reasons, risk level and target.</small></p><span>3</span><p><b>Practice</b><small>Execute a protected paper trade before real capital.</small></p></div></section>
    </div>
  </main>;
}

function Metric({ label, value, detail, tone = '' }: { label: string; value: string; detail: string; tone?: string }) { return <div className="card"><span>{label}</span><b>{value}</b><small className={tone}>{detail}</small></div>; }


