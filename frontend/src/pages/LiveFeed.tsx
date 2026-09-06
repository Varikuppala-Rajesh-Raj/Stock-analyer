import { useState, useEffect } from 'react';

export function LiveFeed() {
  const [events, setEvents] = useState<any[]>([]);
  const [filter, setFilter] = useState('all');

  // Mock events - in production would connect to WebSocket
  useEffect(() => {
    const mockEvents = [
      { id: 1, type: 'price', symbol: 'NIFTY', time: new Date(Date.now() - 30000), message: 'NIFTY 50 reached 22500, breaking resistance' },
      { id: 2, type: 'signal', symbol: 'BANKNIFTY', time: new Date(Date.now() - 60000), message: 'BUY signal generated on 15m timeframe, score 78' },
      { id: 3, type: 'execution', symbol: 'RELIANCE', time: new Date(Date.now() - 120000), message: 'Limit order filled - RELIANCE 100 @ ₹2450.50' },
      { id: 4, type: 'alert', symbol: 'TCS', time: new Date(Date.now() - 180000), message: 'TCS exceeded daily stop loss, position closed' },
      { id: 5, type: 'model', symbol: 'NIFTY', time: new Date(Date.now() - 300000), message: 'ML model confidence 82% for up move in next 15m' },
    ];
    setEvents(mockEvents);
  }, []);

  const filtered = filter === 'all' ? events : events.filter(e => e.type === filter);

  return (
    <main>
      <div className="heading">
        <div>
          <small>REAL-TIME DATA STREAM</small>
          <h1>Live event feed</h1>
          <p>Price updates, signals, executions, and system alerts as they happen.</p>
        </div>
        <button className="primary">Settings</button>
      </div>

      <div className="filters">
        <button className={filter === 'all' ? 'chosen' : ''} onClick={() => setFilter('all')}>All events</button>
        <button className={filter === 'price' ? 'chosen' : ''} onClick={() => setFilter('price')}>Price updates</button>
        <button className={filter === 'signal' ? 'chosen' : ''} onClick={() => setFilter('signal')}>Signals</button>
        <button className={filter === 'execution' ? 'chosen' : ''} onClick={() => setFilter('execution')}>Executions</button>
        <button className={filter === 'alert' ? 'chosen' : ''} onClick={() => setFilter('alert')}>Alerts</button>
      </div>

      <section className="panel feed">
        {filtered.map(event => (
          <div key={event.id} className={`feed-row ${event.type}`}>
            <div className={`icon ${event.type}`}>
              {event.type === 'price' && '📈'}
              {event.type === 'signal' && '⚡'}
              {event.type === 'execution' && '✓'}
              {event.type === 'alert' && '⚠'}
              {event.type === 'model' && '🤖'}
            </div>
            <div>
              <b>{event.symbol}</b>
              <p>{event.message}</p>
              <small>{event.time.toLocaleTimeString()}</small>
            </div>
            <button className="outline">View</button>
          </div>
        ))}
      </section>
    </main>
  );
}
