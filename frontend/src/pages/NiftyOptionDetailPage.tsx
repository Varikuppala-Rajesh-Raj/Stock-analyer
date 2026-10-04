import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { OptionPriceChart } from '../components/charts/OptionPriceChart';
import {
  api,
  type ManualTrade,
  type OptionChainRow,
  type OptionPricePoint,
} from '../services/api';
import './NiftyOptionDetailPage.css';

const number = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const money = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value) ? '—' : `₹${number.format(value)}`;

export function NiftyOptionDetailPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const expiry = params.get('expiry') ?? '';
  const optionType = params.get('type') === 'PE' ? 'PE' : 'CE';
  const instrumentKey = params.get('key') ?? '';
  const strike = Number(params.get('strike'));
  const [option, setOption] = useState<OptionChainRow | null>(null);
  const [points, setPoints] = useState<OptionPricePoint[]>([]);
  const [trades, setTrades] = useState<ManualTrade[]>([]);
  const [loading, setLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);
  const [action, setAction] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const sideData = optionType === 'CE' ? option?.call_options : option?.put_options;
  const price = sideData?.market_data.ltp ?? null;
  const change = sideData && sideData.market_data.close_price > 0
    ? ((sideData.market_data.ltp - sideData.market_data.close_price) / sideData.market_data.close_price) * 100
    : null;
  const openTrades = useMemo(
    () => trades.filter((trade) => trade.status === 'OPEN'),
    [trades],
  );

  const refresh = async () => {
    if (!expiry || !instrumentKey || !Number.isFinite(strike)) {
      setError('This option link is incomplete. Return to the NIFTY option chain and select a contract.');
      setLoading(false);
      return;
    }
    try {
      const [chain, history, portfolio] = await Promise.all([
        api.niftyOptionChain(expiry),
        api.niftyOptionHistory(instrumentKey, expiry),
        api.manualPortfolio(),
      ]);
      const selectedRow = (chain.data ?? []).find((row) => {
        const side = optionType === 'CE' ? row.call_options : row.put_options;
        return side.instrument_key === instrumentKey && row.strike_price === strike;
      });
      if (!selectedRow) throw new Error('This contract is no longer present in the selected expiry option chain.');
      setOption(selectedRow);
      setPoints(history);
      setTrades(portfolio.trades.filter((trade) => trade.instrumentKey === instrumentKey && trade.expiry === expiry));
      setError('');
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unable to load option contract details.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => void refresh(), 15000);
    return () => window.clearInterval(timer);
  }, [expiry, instrumentKey, optionType, strike]);

  const buy = async () => {
    setAction('buy');
    setError('');
    setNotice('');
    try {
      const trade = await api.buyManualTrade({
        instrumentType: 'OPTION',
        instrumentKey,
        expiry,
        optionType,
        quantity,
      });
      setNotice(`Bought ${trade.quantity} units at ${money(trade.entryPrice)}. Manual paper trade recorded.`);
      await refresh();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unable to buy this option.');
    } finally {
      setAction('');
    }
  };

  const sell = async (trade: ManualTrade) => {
    setAction(trade.id);
    setError('');
    setNotice('');
    try {
      const closed = await api.sellManualTrade(trade.id);
      setNotice(`Sold ${closed.quantity} units. Realized P&L: ${money(closed.realizedPnl)}.`);
      await refresh();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unable to sell this option holding.');
    } finally {
      setAction('');
    }
  };

  return (
    <main className="nifty-option-detail">
      <div className="heading">
        <div>
          <small>NIFTY OPTIONS · CONTRACT ANALYSIS</small>
          <h1>NIFTY {number.format(strike)} {optionType}</h1>
          <p>Expiry {expiry} · {instrumentKey || 'Contract key unavailable'} · simulated manual trades only</p>
        </div>
        <button className="outline" onClick={() => navigate('/nifty-options')}>← Option chain</button>
      </div>

      {error && <p className="api-state error" role="alert">{error}</p>}
      {notice && <p className="api-state option-detail-success" role="status">{notice}</p>}

      <section className="stats option-detail-stats">
        <Metric label="Option LTP" value={money(price)} detail={loading ? 'Refreshing contract quote…' : `NIFTY spot ${money(option?.underlying_spot_price)}`} tone={optionType === 'CE' ? 'call' : 'put'} />
        <Metric label="Day change" value={change == null ? '—' : `${change >= 0 ? '+' : ''}${change.toFixed(2)}%`} detail={`Previous close ${money(sideData?.market_data.close_price)}`} tone={change != null ? change >= 0 ? 'good' : 'bad' : ''} />
        <Metric label="Bid / Ask" value={sideData ? `${money(sideData.market_data.bid_price)} / ${money(sideData.market_data.ask_price)}` : '—'} detail="Current market" />
        <Metric label="Volume / OI" value={sideData ? `${number.format(sideData.market_data.volume)} / ${number.format(sideData.market_data.oi)}` : '—'} detail="Units / contracts" />
      </section>

      <div className="two">
        <section className="panel option-detail-chart">
          <div className="panel-title">
            <div><small>OPTION PREMIUM · 5-MINUTE SAVED SNAPSHOTS</small><h2>{number.format(strike)} {optionType} price history</h2></div>
            <button className="outline" onClick={() => void refresh()}>Refresh</button>
          </div>
          {points.length > 1
            ? <OptionPriceChart points={points} color={optionType === 'CE' ? '#38d7a2' : '#ff718f'} />
            : <p className="option-history-empty">{points.length === 1
              ? `One saved premium snapshot is available at ${money(points[0].price)}. More chart history will appear as snapshots are collected.`
              : 'No saved premium history is available yet. Snapshots are collected prospectively while option-chain collection is enabled.'}</p>}
        </section>

        <section className="panel option-trade-panel">
          <div className="panel-title"><div><small>MANUAL PAPER TRADE</small><h2>Buy, hold, or sell</h2></div><span className={`pill ${optionType === 'CE' ? 'call-pill' : 'put-pill'}`}>{optionType}</span></div>
          <p>Trade this exact contract. Manual option P&amp;L stays separate from auto-trading P&amp;L.</p>
          <label className="option-quantity">Quantity (units)
            <input min="1" step="1" type="number" value={quantity} onChange={(event) => setQuantity(Math.max(1, Number(event.target.value)))} />
          </label>
          <button className="primary" disabled={loading || action !== '' || price == null || !Number.isInteger(quantity) || quantity < 1} onClick={() => void buy()}>
            {action === 'buy' ? 'Buying…' : `Buy ${optionType}`}
          </button>
          <p className="option-trade-price">Estimated notional <b>{price == null ? '—' : money(price * quantity)}</b></p>
          <div className="option-open-holdings">
            <h3>Open holdings · {openTrades.length}</h3>
            {openTrades.length === 0 ? <p>No open holding for this contract.</p> : openTrades.map((trade) => (
              <div className="option-holding" key={trade.id}>
                <span>{trade.quantity} units · entry {money(trade.entryPrice)}</span>
                <b className={trade.unrealizedPnl >= 0 ? 'good' : 'bad'}>{money(trade.unrealizedPnl)}</b>
                <button className="option-sell" disabled={action !== '' || price == null} onClick={() => void sell(trade)}>{action === trade.id ? 'Selling…' : 'Sell holding'}</button>
              </div>
            ))}
          </div>
        </section>
      </div>

      <section className="panel option-greeks">
        <div className="panel-title"><div><small>CONTRACT METRICS</small><h2>Greeks and liquidity</h2></div></div>
        <div className="option-greeks-grid">
          <Metric label="Delta" value={sideData?.option_greeks.delta.toFixed(3) ?? '—'} detail="Price sensitivity" />
          <Metric label="Gamma" value={sideData?.option_greeks.gamma.toFixed(4) ?? '—'} detail="Delta sensitivity" />
          <Metric label="Theta" value={sideData?.option_greeks.theta.toFixed(3) ?? '—'} detail="Daily time decay" />
          <Metric label="Vega" value={sideData?.option_greeks.vega.toFixed(3) ?? '—'} detail="Volatility sensitivity" />
          <Metric label="Implied volatility" value={sideData ? `${sideData.option_greeks.iv.toFixed(2)}%` : '—'} detail="Market implied" />
        </div>
      </section>
    </main>
  );
}

function Metric({ label, value, detail, tone = '' }: { label: string; value: string; detail: string; tone?: string }) {
  return <div className="card"><span>{label}</span><b className={tone}>{value}</b><small>{detail}</small></div>;
}
