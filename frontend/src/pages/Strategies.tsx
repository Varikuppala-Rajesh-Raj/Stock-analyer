import { useScanner } from '../services/hooks';
import { useNavigate } from 'react-router-dom';
import type { ScannerResult } from '../services/api';

const n = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const money = (v: number | null) => v == null ? '—' : `₹${n.format(v)}`;

export function Strategies() {
  const navigate = useNavigate();
  const { signals, loading, error, refetch } = useScanner('15m', 30000);

  return (
    <main>
      <div className="heading">
        <div>
          <small>QUANTITATIVE SIGNAL ENGINE</small>
          <h1>Strategy scanner</h1>
          <p>Rank instruments by technical confirmation, quality, and risk/reward.</p>
        </div>
        <button className="primary" onClick={() => void refetch()}>↻ Refresh scan</button>
      </div>

      <div className="filters">
        <button className="chosen">All signals</button>
        <button>BUY</button>
        <button>SELL</button>
        <button>HOLD</button>
        <span />
        <label>Timeframe <select><option>15 minutes</option><option>1 hour</option></select></label>
        <label>Score <select><option>70+</option><option>60+</option></select></label>
      </div>

      {loading && <p>Loading signals...</p>}
      {error && <p style={{ color: 'red' }}>Error loading signals: {error}</p>}

      {signals && signals.length > 0 ? (
        <section className="panel table">
          <div className="table-head">
            <span>Instrument</span>
            <span>Signal</span>
            <span>Score</span>
            <span>Last price</span>
            <span>Entry / Stop / Target</span>
            <span>Thesis</span>
            <span>Action</span>
          </div>
          {signals.map((signal: ScannerResult) => (
            <div className="strategy" key={signal.symbol}>
              <div>
                <b>{signal.symbol}</b>
                <small>{signal.symbol} • NSE</small>
              </div>
              <span className={`pill ${signal.signal.toLowerCase()}`}>{signal.signal}</span>
              <div className="score">
                <b>{signal.signalStrength}</b>
                <span><i style={{ width: `${signal.signalStrength}%` }} /></span>
              </div>
              <b>{money(signal.price)}</b>
              <div>
                <b>{money(signal.entry)}</b>
                <small className="bad">SL {money(signal.stopLoss)}</small>
                <small className="good">T {money(signal.target)}</small>
              </div>
              <p>{signal.reasons?.length ? signal.reasons.join(' · ') : 'No thesis details available.'}</p>
              <button 
                className="outline" 
                onClick={() => navigate(`/instruments/${signal.symbol}`)}
              >
                Analyze
              </button>
            </div>
          ))}
        </section>
      ) : (
        <p style={{ padding: '20px' }}>No signals found</p>
      )}
    </main>
  );
}
