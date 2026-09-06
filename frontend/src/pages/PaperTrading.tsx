import { useState } from 'react';
import { useAccount, usePositions, useOrders, useSubmitOrder, useClosePosition } from '../services/hooks';

const n = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const money = (v: number) => `₹${n.format(v)}`;

export function PaperTrading() {
  const [orderSymbol, setOrderSymbol] = useState('NIFTY');
  const [orderSide, setOrderSide] = useState<'BUY' | 'SELL'>('BUY');
  const [orderQty, setOrderQty] = useState('1');
  const [orderPrice, setOrderPrice] = useState('');

  const { account, loading: acctLoading } = useAccount(10000);
  const { positions, loading: posLoading } = usePositions(10000);
  const { orders, loading: ordersLoading } = useOrders(10000);
  const { submit: submitOrder, loading: submitLoading } = useSubmitOrder();
  const { close: closePosition, loading: closeLoading } = useClosePosition();

  const handleSubmitOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await submitOrder({
        symbol: orderSymbol,
        side: orderSide,
        quantity: parseInt(orderQty),
        price: parseFloat(orderPrice),
      });
      setOrderPrice('');
      setOrderQty('1');
    } catch (err) {
      console.error('Order failed:', err);
    }
  };

  return (
    <main>
      <div className="heading">
        <div>
          <small>SIMULATED TRADING ENGINE</small>
          <h1>Paper trading account</h1>
          <p>Risk-free practice environment with real market data and execution rules.</p>
        </div>
        <button className="primary">View reports</button>
      </div>

      <section className="stats">
        <div className="card">
          <span>Cash available</span>
          <b>{acctLoading ? 'Loading...' : money(account?.cash || 0)}</b>
          <small>{acctLoading ? '' : `Used: ${money((account?.capital || 0) - (account?.cash || 0))}`}</small>
        </div>
        <div className="card">
          <span>Portfolio value</span>
          <b>{acctLoading ? 'Loading...' : money(account?.portfolio || 0)}</b>
          <small>{account && account.portfolio > 0 ? 'Net exposure' : 'No open positions'}</small>
        </div>
        <div className="card">
          <span>Unrealised P/L</span>
          <b className={account && account.pnl >= 0 ? 'good' : 'bad'}>
            {acctLoading ? 'Loading...' : money(account?.pnl || 0)}
          </b>
          <small>{account ? ((account.pnl / account.capital) * 100).toFixed(2) + '%' : 'N/A'}</small>
        </div>
        <div className="card">
          <span>Open positions</span>
          <b>{posLoading ? 'Loading...' : positions?.length || 0}</b>
          <small>{positions?.length ? 'Active' : 'Flat'}</small>
        </div>
      </section>

      <div className="two">
        <section className="panel">
          <div className="panel-title">
            <small>PLACE NEW ORDER</small>
            <h2>Order entry</h2>
          </div>
          <form onSubmit={handleSubmitOrder}>
            <div className="form-row">
              <label>Instrument
                <input value={orderSymbol} onChange={e => setOrderSymbol(e.target.value)} />
              </label>
              <label>Side
                <select value={orderSide} onChange={e => setOrderSide(e.target.value as 'BUY' | 'SELL')}>
                  <option value="BUY">BUY</option>
                  <option value="SELL">SELL</option>
                </select>
              </label>
            </div>
            <div className="form-row">
              <label>Quantity
                <input type="number" value={orderQty} onChange={e => setOrderQty(e.target.value)} />
              </label>
              <label>Limit price
                <input type="number" step="0.01" value={orderPrice} onChange={e => setOrderPrice(e.target.value)} placeholder="At current" />
              </label>
            </div>
            <button type="submit" className="primary wide" disabled={submitLoading}>
              {submitLoading ? 'Submitting...' : 'Submit order'}
            </button>
          </form>
        </section>

        <section className="panel">
          <div className="panel-title">
            <small>OPEN POSITIONS</small>
            <h2>Current holdings</h2>
          </div>
          {posLoading ? (
            <p>Loading positions...</p>
          ) : (positions && positions.length > 0) ? (
            <div className="position-list">
              {positions.map(pos => (
                <div key={pos.id} className="position">
                  <div>
                    <b>{pos.symbol}</b>
                    <small>{pos.quantity} shares @ {money(pos.entryPrice)}</small>
                  </div>
                  <div>
                    <b className={pos.unrealisedPnl >= 0 ? 'good' : 'bad'}>
                      {money(pos.unrealisedPnl)}
                    </b>
                    <small>{((pos.unrealisedPnl / (pos.quantity * pos.entryPrice)) * 100).toFixed(2)}%</small>
                  </div>
                  <button 
                    className="outline bad" 
                    onClick={() => closePosition(pos.id)}
                    disabled={closeLoading}
                  >
                    Close
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <p style={{ color: '#888' }}>No open positions</p>
          )}
        </section>
      </div>

      <section className="panel table">
        <div className="panel-title">
          <small>ORDER HISTORY</small>
          <h2>Recent fills</h2>
        </div>
        {ordersLoading ? (
          <p>Loading orders...</p>
        ) : (orders && orders.length > 0) ? (
          <div>
            <div className="table-head">
              <span>Time</span>
              <span>Symbol</span>
              <span>Side</span>
              <span>Qty</span>
              <span>Price</span>
              <span>Status</span>
            </div>
            {orders.map(order => (
              <div key={order.id} className="table-row">
                <small>{new Date(order.timestamp).toLocaleTimeString()}</small>
                <b>{order.symbol}</b>
                <span className={order.side === 'BUY' ? 'good' : 'bad'}>{order.side}</span>
                <span>{order.quantity}</span>
                <b>{money(order.price)}</b>
                <span className="pill">{order.status}</span>
              </div>
            ))}
          </div>
        ) : (
          <p style={{ color: '#888' }}>No orders yet</p>
        )}
      </section>
    </main>
  );
}
