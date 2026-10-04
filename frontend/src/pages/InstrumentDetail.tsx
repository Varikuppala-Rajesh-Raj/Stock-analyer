import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, type ManualTrade } from '../services/api';
import { useCandles, useQuote, useScanner } from '../services/hooks';
import { PriceChart } from '../components/charts/PriceChart';

const frames = ['1m', '5m', '15m', '1h', '1D'];
const number = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const money = (value?: number) => value == null || !Number.isFinite(value) ? '—' : `₹${number.format(value)}`;

export function InstrumentDetail() {
  const { symbol = '' } = useParams<{ symbol: string }>();
  const navigate = useNavigate();
  const [timeframe, setTimeframe] = useState('15m');
  const [quantity, setQuantity] = useState('1');
  const [notice, setNotice] = useState('');
  const [manualTrades, setManualTrades] = useState<ManualTrade[]>([]);
  const [manualLoading, setManualLoading] = useState(false);
  const [manualAction, setManualAction] = useState('');
  const [manualError, setManualError] = useState('');
  const { quote, loading: quoteLoading } = useQuote(symbol, 10000);
  const { candles, loading: candlesLoading, error: candleError } = useCandles(symbol, timeframe, true);
  const { signals, loading: scannerLoading } = useScanner(timeframe, 30000);
  const lastPrice = Number(quote?.lastPrice ?? quote?.price);
  const previousClose = Number(quote?.previousClose ?? lastPrice);
  const change = previousClose ? ((lastPrice - previousClose) / previousClose) * 100 : 0;
  const signal = signals?.find((item: any) => item.symbol?.toUpperCase() === symbol.toUpperCase());
  const isMlSupported = symbol.toUpperCase() === 'NIFTY';

  const loadManualTrades = async () => {
    setManualLoading(true);
    setManualError('');
    try {
      const portfolio = await api.manualPortfolio();
      setManualTrades(portfolio.trades.filter((trade) =>
        trade.instrumentType === 'STOCK' && trade.symbol.toUpperCase() === symbol.toUpperCase(),
      ));
    } catch (error) {
      setManualError(error instanceof Error ? error.message : 'Unable to load manual holdings.');
    } finally {
      setManualLoading(false);
    }
  };

  const buyManual = async () => {
    setManualAction('BUY');
    setNotice('');
    setManualError('');
    try {
      const trade = await api.buyManualTrade({
        instrumentType: 'STOCK',
        symbol,
        quantity: Math.max(1, Number(quantity) || 1),
      });
      setNotice(`Bought ${trade.quantity} ${trade.symbol} at ${money(trade.entryPrice)}. This is a manual paper trade.`);
      await loadManualTrades();
    } catch (error) {
      setManualError(error instanceof Error ? error.message : 'Unable to open a manual paper trade.');
    } finally {
      setManualAction('');
    }
  };

  const sellManual = async (trade: ManualTrade) => {
    setManualAction(trade.id);
    setNotice('');
    setManualError('');
    try {
      const closed = await api.sellManualTrade(trade.id);
      setNotice(`Sold ${closed.quantity} ${closed.symbol}. Realized P&L: ${money(closed.realizedPnl)}.`);
      await loadManualTrades();
    } catch (error) {
      setManualError(error instanceof Error ? error.message : 'Unable to sell this manual holding.');
    } finally {
      setManualAction('');
    }
  };

  useEffect(() => {
    void loadManualTrades();
  }, [symbol]);
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
      <section className="panel">
        <div className="panel-title"><div><small>MANUAL PAPER TRADE</small><h2>Buy or sell {symbol}</h2></div><span className="pill ready">NO BROKER ORDERS</span></div>
        <p>Manual holdings and P&amp;L are recorded separately from automated paper trading.</p>
        <div className="order-fields"><label>Quantity<input min="1" step="1" type="number" value={quantity} onChange={e => setQuantity(e.target.value)} /></label></div>
        <div className="order-actions"><button className="primary buy-order" disabled={manualLoading || manualAction !== '' || !Number.isInteger(Number(quantity)) || Number(quantity) < 1} onClick={() => void buyManual()}>{manualAction === 'BUY' ? 'Buying…' : 'Buy and hold'}</button><button className="outline" disabled={manualLoading || manualAction !== ''} onClick={() => void loadManualTrades()}>{manualLoading ? 'Refreshing…' : 'Refresh holdings'}</button></div>
        {manualError && <p className="bad" role="alert">{manualError}</p>}
        {notice && <p className="good" role="status">{notice}</p>}
        <div className="manual-instrument-holdings">
          <h3>Open manual holdings · {symbol}</h3>
          {manualLoading && manualTrades.length === 0 ? <p>Loading holdings…</p>
            : manualTrades.filter(trade => trade.status === 'OPEN').length === 0 ? <p>No open manual holding for this stock.</p>
              : manualTrades.filter(trade => trade.status === 'OPEN').map(trade => (
                <div className="manual-instrument-holding" key={trade.id}>
                  <span>{trade.quantity} shares · entry {money(trade.entryPrice)} · now {money(trade.currentPrice)}</span>
                  <b className={trade.unrealizedPnl >= 0 ? 'good' : 'bad'}>{money(trade.unrealizedPnl)} P&amp;L</b>
                  <button className="outline" disabled={manualAction !== ''} onClick={() => void sellManual(trade)}>{manualAction === trade.id ? 'Selling…' : 'Sell holding'}</button>
                </div>
              ))}
        </div>
      </section>
      <section className="panel"><small>SMART WORKFLOW</small><h2>Trade with a plan</h2><div className="workflow"><span>1</span><p><b>Watch</b><small>Save the company and monitor live movement.</small></p><span>2</span><p><b>Validate</b><small>Use chart, signal reasons, risk level and target.</small></p><span>3</span><p><b>Practice</b><small>Execute a protected paper trade before real capital.</small></p></div></section>
    </div>
  </main>;
}

function Metric({ label, value, detail, tone = '' }: { label: string; value: string; detail: string; tone?: string }) { return <div className="card"><span>{label}</span><b>{value}</b><small className={tone}>{detail}</small></div>; }
