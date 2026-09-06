import { useState } from 'react';

export function Journal() {
  const [trades, setTrades] = useState([
    {
      id: 1,
      date: new Date('2024-01-15'),
      symbol: 'RELIANCE',
      side: 'BUY',
      entry: 2450.50,
      exit: 2480.25,
      quantity: 10,
      pnl: 297.50,
      duration: '2h 30m',
      thesis: 'Breakout above 2450 support on volume',
      quality: 8,
      notes: 'Perfect entry, early exit - missed 50 more points',
    },
    {
      id: 2,
      date: new Date('2024-01-14'),
      symbol: 'NIFTY',
      side: 'SELL',
      entry: 22500.00,
      exit: 22380.50,
      quantity: 1,
      pnl: 119.50,
      duration: '4h 15m',
      thesis: 'Retest of 22500 resistance',
      quality: 7,
      notes: 'Good trade, stopped at breakeven',
    },
    {
      id: 3,
      date: new Date('2024-01-13'),
      symbol: 'TCS',
      side: 'BUY',
      entry: 3980.00,
      exit: 3920.50,
      quantity: 5,
      pnl: -297.50,
      duration: '1h 45m',
      thesis: 'Morning gap fill',
      quality: 4,
      notes: 'Bad thesis - no follow-through',
    },
  ]);

  const [filter, setFilter] = useState('all');

  const filtered = filter === 'all' ? trades : filter === 'wins' ? trades.filter(t => t.pnl > 0) : trades.filter(t => t.pnl <= 0);
  const winRate = trades.length > 0 ? ((trades.filter(t => t.pnl > 0).length / trades.length) * 100).toFixed(1) : 0;
  const totalPnl = trades.reduce((sum, t) => sum + t.pnl, 0);
  const avgTrade = trades.length > 0 ? (totalPnl / trades.length).toFixed(2) : 0;

  return (
    <main>
      <div className="heading">
        <div>
          <small>TRADE REVIEW WORKBENCH</small>
          <h1>Journal</h1>
          <p>Review your trade quality, decision-making, and edge consistency over time.</p>
        </div>
        <button className="primary">+ Log trade</button>
      </div>

      <section className="stats">
        <div className="card">
          <span>Total trades</span>
          <b>{trades.length}</b>
          <small>All time</small>
        </div>
        <div className="card">
          <span>Win rate</span>
          <b className="good">{winRate}%</b>
          <small>{trades.filter(t => t.pnl > 0).length} wins</small>
        </div>
        <div className="card">
          <span>Total P/L</span>
          <b className={totalPnl >= 0 ? 'good' : 'bad'}>₹{totalPnl.toFixed(2)}</b>
          <small>Realised</small>
        </div>
        <div className="card">
          <span>Avg per trade</span>
          <b>₹{avgTrade}</b>
          <small>Mean P/L</small>
        </div>
      </section>

      <div className="filters">
        <button className={filter === 'all' ? 'chosen' : ''} onClick={() => setFilter('all')}>All trades</button>
        <button className={filter === 'wins' ? 'chosen' : ''} onClick={() => setFilter('wins')}>Wins</button>
        <button className={filter === 'losses' ? 'chosen' : ''} onClick={() => setFilter('losses')}>Losses</button>
      </div>

      <section className="panel table">
        <div className="table-head">
          <span>Date</span>
          <span>Symbol</span>
          <span>Side</span>
          <span>Entry / Exit</span>
          <span>Qty</span>
          <span>P/L</span>
          <span>Duration</span>
          <span>Quality</span>
          <span>Notes</span>
        </div>
        {filtered.map(trade => (
          <div key={trade.id} className="journal-row">
            <small>{trade.date.toLocaleDateString()}</small>
            <b>{trade.symbol}</b>
            <span className={trade.side === 'BUY' ? 'good' : 'bad'}>{trade.side}</span>
            <div>
              <small>Entry ₹{trade.entry.toFixed(2)}</small>
              <small>Exit ₹{trade.exit.toFixed(2)}</small>
            </div>
            <b>{trade.quantity}</b>
            <div className={trade.pnl >= 0 ? 'good' : 'bad'}>
              <b>₹{trade.pnl.toFixed(2)}</b>
              <small>{((trade.pnl / (trade.quantity * trade.entry)) * 100).toFixed(2)}%</small>
            </div>
            <small>{trade.duration}</small>
            <div className="quality">
              <span style={{ width: `${(trade.quality / 10) * 100}%` }} />
              <small>{trade.quality}/10</small>
            </div>
            <small>{trade.notes}</small>
          </div>
        ))}
      </section>

      <section className="panel">
        <div className="panel-title">
          <small>TRADE THESIS ANALYSIS</small>
          <h2>Decision quality distribution</h2>
        </div>
        <div className="quality-dist">
          {[
            { range: '9-10', count: trades.filter(t => t.quality >= 9).length },
            { range: '7-8', count: trades.filter(t => t.quality >= 7 && t.quality < 9).length },
            { range: '5-6', count: trades.filter(t => t.quality >= 5 && t.quality < 7).length },
            { range: '1-4', count: trades.filter(t => t.quality < 5).length },
          ].map(({ range, count }) => (
            <div key={range} className="bar">
              <span>{range}</span>
              <div><i style={{ width: `${(count / Math.max(trades.length, 1)) * 100}%` }} /></div>
              <b>{count}</b>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
