import { useState } from 'react';

export function Alerts() {
  const [alerts, setAlerts] = useState([
    { id: 1, type: 'error', title: 'Model unavailable', message: 'ML prediction service is offline', time: new Date(Date.now() - 300000), read: false },
    { id: 2, type: 'warning', title: 'High correlation', message: 'Your positions show 85% correlation, consider rebalancing', time: new Date(Date.now() - 600000), read: false },
    { id: 3, type: 'info', title: 'Scan complete', message: '47 signals updated across 300 instruments', time: new Date(Date.now() - 900000), read: true },
    { id: 4, type: 'success', title: 'Trade closed', message: 'RELIANCE position closed at ₹2450.50, +₹250 P/L', time: new Date(Date.now() - 1200000), read: true },
  ]);
  const [filter, setFilter] = useState('all');

  const markRead = (id: number) => {
    setAlerts(a => a.map(x => x.id === id ? { ...x, read: true } : x));
  };

  const dismiss = (id: number) => {
    setAlerts(a => a.filter(x => x.id !== id));
  };

  const filtered = filter === 'all' ? alerts : alerts.filter(a => (filter === 'unread' ? !a.read : a.type === filter));

  return (
    <main>
      <div className="heading">
        <div>
          <small>NOTIFICATION CENTER</small>
          <h1>Alerts & notifications</h1>
          <p>System messages, warnings, trade confirmations, and updates.</p>
        </div>
        <button className="outline">Clear all read</button>
      </div>

      <div className="filters">
        <button className={filter === 'all' ? 'chosen' : ''} onClick={() => setFilter('all')}>All alerts</button>
        <button className={filter === 'unread' ? 'chosen' : ''} onClick={() => setFilter('unread')}>Unread</button>
        <button className={filter === 'error' ? 'chosen' : ''} onClick={() => setFilter('error')}>Errors</button>
        <button className={filter === 'warning' ? 'chosen' : ''} onClick={() => setFilter('warning')}>Warnings</button>
        <button className={filter === 'info' ? 'chosen' : ''} onClick={() => setFilter('info')}>Info</button>
      </div>

      <section className="panel alert-list">
        {filtered.map(alert => (
          <div key={alert.id} className={`alert-row ${alert.type} ${alert.read ? 'read' : 'unread'}`}>
            <div className={`dot ${alert.type}`} />
            <div className="content">
              <div>
                <b>{alert.title}</b>
                {!alert.read && <span className="badge">New</span>}
              </div>
              <p>{alert.message}</p>
              <small>{alert.time.toLocaleTimeString()}</small>
            </div>
            <div className="actions">
              {!alert.read && (
                <button className="outline" onClick={() => markRead(alert.id)}>Mark read</button>
              )}
              <button className="outline" onClick={() => dismiss(alert.id)}>Dismiss</button>
            </div>
          </div>
        ))}
      </section>

      {filtered.length === 0 && (
        <section className="panel" style={{ textAlign: 'center', padding: '40px 20px', color: '#888' }}>
          <p>No alerts to show</p>
        </section>
      )}
    </main>
  );
}
