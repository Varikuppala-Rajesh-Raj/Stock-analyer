import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  api,
  type ManualPortfolio,
  type ManualTrade,
  type OptionChainRow,
  type Quote,
} from '../services/api';
import './ManualTradingPage.css';

type TradeMode = 'STOCK' | 'OPTION';
type SelectedOption = {
  key: string;
  type: 'CE' | 'PE';
  strike: number;
  price: number;
  bid: number;
  ask: number;
  volume: number;
  oi: number;
  delta: number;
  iv: number;
};

const money = (value: number | null | undefined) =>
  value == null
    ? '—'
    : new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
      }).format(value);

const dateTime = (value: string) =>
  new Date(value).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' });

function errorText(error: unknown, fallback: string) {
  if (error && typeof error === 'object' && 'message' in error) {
    return String(error.message);
  }
  return fallback;
}

function optionsInChain(rows: OptionChainRow[]): SelectedOption[] {
  return rows.flatMap((row) => {
    const sides = [
      { type: 'CE' as const, data: row.call_options },
      { type: 'PE' as const, data: row.put_options },
    ];
    return sides
      .filter(({ data }) => data.instrument_key && data.market_data.ltp > 0)
      .map(({ type, data }) => ({
        key: data.instrument_key,
        type,
        strike: row.strike_price,
        price: data.market_data.ltp,
        bid: data.market_data.bid_price,
        ask: data.market_data.ask_price,
        volume: data.market_data.volume,
        oi: data.market_data.oi,
        delta: data.option_greeks.delta,
        iv: data.option_greeks.iv,
      }));
  });
}

export function ManualTradingPage() {
  const [mode, setMode] = useState<TradeMode>('STOCK');
  const [symbol, setSymbol] = useState('RELIANCE');
  const [quote, setQuote] = useState<Quote>();
  const [expiries, setExpiries] = useState<string[]>([]);
  const [expiry, setExpiry] = useState('');
  const [optionRows, setOptionRows] = useState<OptionChainRow[]>([]);
  const [selectedOptionKey, setSelectedOptionKey] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [portfolio, setPortfolio] = useState<ManualPortfolio>();
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const options = useMemo(() => optionsInChain(optionRows), [optionRows]);
  const selectedOption = options.find((option) => option.key === selectedOptionKey);

  const loadPortfolio = async () => {
    try {
      const result = await api.manualPortfolio();
      setPortfolio(result);
      setError('');
    } catch (loadError) {
      setError(errorText(loadError, 'Unable to load the manual-trading ledger.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadPortfolio();
    const timer = window.setInterval(() => void loadPortfolio(), 15000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    let active = true;
    void api.niftyExpiries(5).then((result) => {
      if (!active) return;
      setExpiries(result.expiries);
      setExpiry((current) => current || result.expiries[0] || '');
    }).catch((expiryError: unknown) => {
      if (active) setError(errorText(expiryError, 'Unable to load NIFTY option expiries.'));
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!expiry) return;
    let active = true;
    void api.niftyOptionChain(expiry).then((response) => {
      if (!active) return;
      const rows = response.data ?? [];
      setOptionRows(rows);
      const available = optionsInChain(rows);
      setSelectedOptionKey((current) =>
        available.some((option) => option.key === current) ? current : available[0]?.key ?? '',
      );
    }).catch((chainError: unknown) => {
      if (active) setError(errorText(chainError, 'Unable to load the NIFTY option chain.'));
    });
    return () => { active = false; };
  }, [expiry]);

  const analyzeStock = async () => {
    setError('');
    setSuccess('');
    try {
      const result = await api.quote(symbol.trim());
      setQuote(result);
    } catch (quoteError) {
      setError(errorText(quoteError, 'Unable to load a fresh stock quote.'));
      setQuote(undefined);
    }
  };

  const buy = async () => {
    setAction('buy');
    setError('');
    setSuccess('');
    try {
      const trade = mode === 'STOCK'
        ? await api.buyManualTrade({ instrumentType: 'STOCK', symbol: symbol.trim(), quantity })
        : await api.buyManualTrade({
            instrumentType: 'OPTION',
            expiry,
            optionType: selectedOption?.type,
            instrumentKey: selectedOption?.key,
            quantity,
          });
      setSuccess(`Bought ${trade.quantity} ${trade.symbol} at ${money(trade.entryPrice)}.`);
      setQuote(undefined);
      await loadPortfolio();
    } catch (buyError) {
      setError(errorText(buyError, 'Unable to open the manual paper trade.'));
    } finally {
      setAction('');
    }
  };

  const sell = async (trade: ManualTrade) => {
    setAction(trade.id);
    setError('');
    setSuccess('');
    try {
      const closed = await api.sellManualTrade(trade.id);
      setSuccess(`Sold ${closed.symbol}. Realized P&L: ${money(closed.realizedPnl)}.`);
      await loadPortfolio();
    } catch (sellError) {
      setError(errorText(sellError, `Unable to sell ${trade.symbol}.`));
    } finally {
      setAction('');
    }
  };

  return (
    <main className="manual-trading">
      <div className="heading">
        <div>
          <small>SEPARATE VIRTUAL LEDGER · NO BROKER ORDERS</small>
          <h1>Manual trading</h1>
          <p>Analyze a stock or NIFTY option, buy to hold, and sell when you choose.</p>
        </div>
        <button className="outline" onClick={() => void loadPortfolio()}>Refresh P&amp;L</button>
      </div>

      {error && <p className="api-state error" role="alert">{error}</p>}
      {success && <p className="api-state manual-success" role="status">{success}</p>}

      <section className="manual-metrics" aria-label="Manual trade profit and loss">
        <Metric label="Manual realized P&L" value={money(portfolio?.realizedPnl)} tone={portfolio?.realizedPnl} hint="Closed manual trades, net of charges" />
        <Metric label="Manual unrealized P&L" value={money(portfolio?.unrealizedPnl)} tone={portfolio?.unrealizedPnl} hint="Open manual holdings, net of entry charges" />
        <Metric label="Manual total P&L" value={money(portfolio?.totalPnl)} tone={portfolio?.totalPnl} hint="Manual ledger only" />
        <Metric label="Open holdings" value={String(portfolio?.openTrades ?? 0)} hint={`${portfolio?.closedTrades ?? 0} closed trades`} />
      </section>

      <section className="panel manual-entry">
        <div className="panel-title">
          <div><small>ANALYZE · BUY · HOLD · SELL</small><h2>Open a manual paper trade</h2></div>
          <span className="pill neutral">AUTO-TRADE P&amp;L IS SEPARATE</span>
        </div>
        <div className="manual-tabs" role="tablist" aria-label="Instrument type">
          <button className={mode === 'STOCK' ? 'selected' : ''} onClick={() => setMode('STOCK')} role="tab" aria-selected={mode === 'STOCK'}>Stocks</button>
          <button className={mode === 'OPTION' ? 'selected' : ''} onClick={() => setMode('OPTION')} role="tab" aria-selected={mode === 'OPTION'}>NIFTY options</button>
        </div>

        {mode === 'STOCK' ? (
          <div className="manual-entry-grid">
            <label>Stock symbol
              <input value={symbol} onChange={(event) => setSymbol(event.target.value.toUpperCase())} onKeyDown={(event) => { if (event.key === 'Enter') void analyzeStock(); }} placeholder="e.g. RELIANCE" />
            </label>
            <button className="outline" onClick={() => void analyzeStock()} disabled={!symbol.trim()}>Analyze stock</button>
            <label>Quantity (shares)
              <input type="number" min="1" step="1" value={quantity} onChange={(event) => setQuantity(Math.max(1, Number(event.target.value)))} />
            </label>
            <button className="primary" onClick={() => void buy()} disabled={action !== '' || !symbol.trim()}>{action === 'buy' ? 'Buying…' : 'Buy and hold'}</button>
          </div>
        ) : (
          <div className="manual-option-controls">
            <label>Expiry
              <select value={expiry} onChange={(event) => setExpiry(event.target.value)} disabled={!expiries.length}>
                {expiries.map((item) => <option key={item} value={item}>{item}</option>)}
              </select>
            </label>
            <label>Option contract
              <select value={selectedOptionKey} onChange={(event) => setSelectedOptionKey(event.target.value)}>
                {options.map((option) => <option key={option.key} value={option.key}>{option.strike} {option.type} · {money(option.price)}</option>)}
              </select>
            </label>
            <label>Quantity (units)
              <input type="number" min="1" step="1" value={quantity} onChange={(event) => setQuantity(Math.max(1, Number(event.target.value)))} />
            </label>
            <button className="primary" onClick={() => void buy()} disabled={action !== '' || !selectedOption}>{action === 'buy' ? 'Buying…' : 'Buy and hold'}</button>
          </div>
        )}

        {mode === 'STOCK' && quote && (
          <div className="manual-analysis" aria-live="polite">
            <div><small>STOCK QUOTE · {quote.symbol}</small><b>{money(quote.price)}</b><span className={quote.change >= 0 ? 'good' : 'bad'}>{money(quote.change)} ({quote.changePercent.toFixed(2)}%)</span></div>
            <div><small>DAY RANGE</small><b>{money(quote.low)} – {money(quote.high)}</b><span>Volume {new Intl.NumberFormat('en-IN').format(quote.volume)}</span></div>
            <Link className="outline" to={`/instruments/${encodeURIComponent(quote.symbol)}`}>Open full stock analysis</Link>
          </div>
        )}

        {mode === 'OPTION' && (
          <div className="manual-analysis" aria-live="polite">
            {selectedOption ? <>
              <div><small>SELECTED CONTRACT</small><b>NIFTY {selectedOption.strike} {selectedOption.type}</b><span>Expiry {expiry}</span></div>
              <div><small>LIVE MARKET</small><b>{money(selectedOption.price)}</b><span>Bid {money(selectedOption.bid)} · Ask {money(selectedOption.ask)}</span></div>
              <div><small>OPTION METRICS</small><b>Delta {selectedOption.delta.toFixed(3)} · IV {selectedOption.iv.toFixed(2)}%</b><span>Volume {new Intl.NumberFormat('en-IN').format(selectedOption.volume)} · OI {new Intl.NumberFormat('en-IN').format(selectedOption.oi)}</span></div>
            </> : <p className="api-state">Loading available NIFTY option contracts…</p>}
            <Link className="outline" to="/nifty-options">Open full options analysis</Link>
          </div>
        )}
        <p className="manual-note">Manual trades are simulated. Entry and exit use backend market prices; estimated brokerage is deducted from P&amp;L. Option quantity is entered in units.</p>
      </section>

      <section className="panel manual-history">
        <div className="panel-title">
          <div><small>MANUAL LEDGER ONLY</small><h2>Holdings and trade history</h2></div>
          <span className="pill neutral">{portfolio?.trades.length ?? 0} TRADES</span>
        </div>
        {loading && !portfolio ? <p className="api-state">Loading manual trades…</p>
          : !portfolio?.trades.length ? <div className="paper-empty">No manual trades yet. Analyze a stock or option above to start a virtual holding.</div>
            : <div className="paper-table-wrap">
              <table className="paper-table manual-table">
                <thead><tr><th>Opened</th><th>Instrument</th><th>Quantity</th><th>Entry</th><th>Market / Exit</th><th>Status</th><th>Net P&amp;L</th><th>Action</th></tr></thead>
                <tbody>{portfolio.trades.map((trade) => {
                  const pnl = trade.status === 'OPEN' ? trade.unrealizedPnl : trade.realizedPnl;
                  return <tr key={trade.id}>
                    <td>{dateTime(trade.openedAt)}</td>
                    <td><b>{trade.symbol}</b><small>{trade.instrumentType === 'OPTION' ? `${trade.expiry} · ${trade.exchange}` : trade.exchange}</small></td>
                    <td>{trade.quantity}</td>
                    <td>{money(trade.entryPrice)}</td>
                    <td>{money(trade.status === 'OPEN' ? trade.currentPrice : trade.exitPrice)}</td>
                    <td><span className={`pill ${trade.status === 'OPEN' ? 'good' : 'neutral'}`}>{trade.status}</span></td>
                    <td className={pnl >= 0 ? 'good' : 'bad'}>{money(pnl)}</td>
                    <td>{trade.status === 'OPEN' ? <button className="manual-sell" onClick={() => void sell(trade)} disabled={action !== ''}>{action === trade.id ? 'Selling…' : 'Sell'}</button> : <span>Sold {trade.closedAt ? dateTime(trade.closedAt) : ''}</span>}</td>
                  </tr>;
                })}</tbody>
              </table>
            </div>}
      </section>
    </main>
  );
}

function Metric({ label, value, tone, hint }: { label: string; value: string; tone?: number; hint: string }) {
  return <div className="card"><span>{label}</span><b className={tone == null ? '' : tone >= 0 ? 'good' : 'bad'}>{value}</b><small>{hint}</small></div>;
}
