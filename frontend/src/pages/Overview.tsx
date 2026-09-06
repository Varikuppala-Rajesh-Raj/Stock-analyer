import { useState } from 'react';
import { useQuote, useScanner, useModelStatus } from '../services/hooks';
import { useNavigate } from 'react-router-dom';

const money = (value?: number | null) => 
  value == null ? '—' : new Intl.NumberFormat('en-IN', { 
    style: 'currency', 
    currency: 'INR', 
    maximumFractionDigits: 2 
  }).format(value);

const pill = (value: string) => 
  <span className={`pill ${value.toLowerCase()}`}>{value}</span>;

export function Overview() {
  const navigate = useNavigate();
  const { quote, loading: quoteLoading, error: quoteError } = useQuote('NIFTY', 15000);
  const { signals, loading: scannerLoading, error: scannerError } = useScanner('15m', 30000);
  const { model, loading: modelLoading, error: modelError } = useModelStatus(30000);

  const hasError = quoteError || scannerError || modelError;

  return (
    <main>
      <div className="heading">
        <div>
          <small>LIVE BACKEND DATA</small>
          <h1>Market dashboard</h1>
          <p>Quotes, scanner signals, and model status refresh from the trading backend.</p>
        </div>
        <button className="primary" onClick={() => navigate('/strategies')}>
          + Run market scan
        </button>
      </div>

      {hasError && (
        <p className="api-state error">
          {quoteError || scannerError || modelError}
        </p>
      )}

      <section className="stats">
        <Card 
          label="NIFTY 50" 
          value={quoteLoading ? 'Loading…' : (quote?.price?.toFixed(2) || '—')}
          hint={!quoteLoading && quote ? `${quote.changePercent >= 0 ? '+' : ''}${quote.changePercent.toFixed(2)}% today` : 'Waiting for quote'}
          good={quote && quote.changePercent >= 0}
        />
        <Card 
          label="Live volume" 
          value={!quoteLoading && quote ? new Intl.NumberFormat('en-IN', { notation: 'compact' }).format(quote.volume) : '—'}
          hint="Latest quote volume"
        />
        <Card 
          label="Scanner results" 
          value={String(signals.length)}
          hint="Current backend scan"
        />
        <Card 
          label="ML model" 
          value={modelLoading ? 'Loading…' : (model?.status ?? 'UNTRAINED')}
          hint={model?.modelVersion ?? 'Train a model to enable predictions'}
          good={model?.status === 'READY'}
        />
      </section>

      <div className="two">
        <section className="panel market">
          <div className="panel-title">
            <div>
              <small>LIVE QUOTE</small>
              <h2>NIFTY 50</h2>
            </div>
            <span className={`pill ${quote && quote.changePercent >= 0 ? 'up' : 'sell'}`}>
              {!quoteLoading && quote ? quote.timestamp : 'CONNECTING'}
            </span>
          </div>

          {quoteLoading ? (
            <p className="api-state">Loading NIFTY quote…</p>
          ) : quote ? (
            <div className="quote-grid">
              <b>{money(quote.price)}</b>
              <span className={quote.changePercent >= 0 ? 'good' : 'bad'}>
                {quote.change >= 0 ? '+' : ''}{quote.change.toFixed(2)} ({quote.changePercent.toFixed(2)}%)
              </span>
              <small>
                Open {money(quote.open)} · H {money(quote.high)} · L {money(quote.low)}
              </small>
            </div>
          ) : (
            <p className="api-state error">Failed to load NIFTY quote</p>
          )}
        </section>

        <section className="panel">
          <div className="panel-title">
            <div>
              <small>MODEL STATUS</small>
              <h2>Prediction service</h2>
            </div>
            <button onClick={() => navigate('/ml-lab')}>ML analytics →</button>
          </div>

          {modelLoading ? (
            <p className="api-state">Loading model status…</p>
          ) : model && model.status !== 'UNTRAINED' ? (
            <div className="model-facts">
              <span>Version <b>{model.modelVersion ?? '—'}</b></span>
              <span>Accuracy <b>{model.accuracy ? (model.accuracy * 100).toFixed(1) + '%' : '—'}</b></span>
              <span>F1 Score <b>{model.f1Macro ? model.f1Macro.toFixed(3) : '—'}</b></span>
              <span>Training rows <b>{model.trainingRows ?? '—'}</b></span>
            </div>
          ) : (
            <div className="api-state">Model not yet trained. Visit ML Lab to train.</div>
          )}
        </section>
      </div>

      <div className="two lower">
        <section className="panel">
          <div className="panel-title">
            <div>
              <small>ACTIONABLE SETUPS</small>
              <h2>Strategy signals</h2>
            </div>
            <button onClick={() => navigate('/strategies')}>View scanner →</button>
          </div>

          {scannerLoading ? (
            <p className="api-state">Loading signals…</p>
          ) : scannerError ? (
            <p className="api-state error">{scannerError}</p>
          ) : signals.length > 0 ? (
            signals.slice(0, 3).map((signal) => (
              <div 
                className="signal clickable" 
                key={signal.symbol}
                onClick={() => navigate(`/instruments/${signal.symbol}`)}
              >
                <i>{signal.symbol[0]}</i>
                <div>
                  <b>{signal.symbol}</b>
                  <small>{signal.reasons?.[0] ?? 'Signal found'}</small>
                </div>
                {pill(signal.signal)}
                <div className="score">
                  <b>{signal.signalStrength}</b>
                  <span>
                    <i style={{ width: `${signal.signalStrength}%` }} />
                  </span>
                </div>
                <button>•••</button>
              </div>
            ))
          ) : (
            <p className="api-state">No signals available</p>
          )}
        </section>

        <section className="panel model">
          <div className="panel-title">
            <div>
              <small>NIFTY ML MODEL</small>
              <h2>Next 15 min outlook</h2>
            </div>
            {model?.status === 'READY' ? pill('TRAINED') : pill('UNTRAINED')}
          </div>

          {modelLoading ? (
            <p className="api-state">Loading prediction…</p>
          ) : model?.status === 'READY' ? (
            <div className="predict">
              <div className="ring">
                <b>{model.accuracy ? (model.accuracy * 100).toFixed(0) : '—'}</b>
                <span>%</span>
              </div>
              <div>
                {pill('MODEL READY')}
                <h3>Prediction ready</h3>
                <p>ML model has completed training. Predictions are available in the ML Lab.</p>
              </div>
            </div>
          ) : (
            <div className="api-state">Model requires training before predictions.</div>
          )}
        </section>
      </div>
    </main>
  );
}

function Card({
  label,
  value,
  hint,
  good,
}: {
  label: string;
  value: string;
  hint: string;
  good?: boolean;
}) {
  return (
    <div className="card">
      <span>{label}</span>
      <b>{value}</b>
      <small className={good ? 'good' : ''}>{hint}</small>
      <i />
    </div>
  );
}
