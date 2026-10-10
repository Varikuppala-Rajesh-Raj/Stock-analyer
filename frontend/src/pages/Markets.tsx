import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useInstruments } from '../services/hooks';
import './Markets.css';

export function Markets() {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const { instruments, loading, error } = useInstruments();

  const normalizedQuery = searchQuery.trim().toLowerCase();
  const filteredInstruments = instruments.filter((instrument) => {
    const searchableText = [
      instrument.symbol,
      instrument.companyName,
      instrument.segment,
      instrument.exchange,
    ].join(' ').toLowerCase();

    return searchableText.includes(normalizedQuery);
  });
  const visibleInstruments = normalizedQuery
    ? filteredInstruments
    : filteredInstruments.slice(0, 20);
  const recommendedSymbols = ['RELIANCE', 'TCS', 'HDFCBANK', 'INFY', 'ICICIBANK', 'SBIN'];
  const rankedRecommendations = recommendedSymbols
    .map((symbol) => instruments.find(
      (instrument) => instrument.active && instrument.symbol.toUpperCase() === symbol
    ))
    .filter((instrument) => instrument !== undefined);
  const recommendedInstruments = rankedRecommendations.length > 0
    ? rankedRecommendations
    : instruments.filter((instrument) => instrument.active).slice(0, 6);

  const openInstrument = (symbol: string) => {
    navigate(`/instruments/${encodeURIComponent(symbol)}`);
  };

  return (
    <main className="markets-page">
      <div className="heading markets-heading">
        <div>
          <small>MARKET EXPLORER</small>
          <h1>Markets at a glance</h1>
          <p>Find listed instruments and open one for live quotes and chart data.</p>
        </div>

        <button className="primary" type="button">
          + Add instrument
        </button>
      </div>

      <section className="panel market-discovery" aria-labelledby="market-discovery-title">
        <div className="market-discovery-copy">
          <div>
            <small>DISCOVER</small>
            <h2 id="market-discovery-title">Search the market</h2>
            <p>Search by company, ticker, market segment, or exchange.</p>
          </div>
        </div>
        <label className="market-search">
          <span aria-hidden="true">⌕</span>
          <input
            type="search"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
            placeholder="Try RELIANCE or banking"
            aria-label="Search market instruments"
          />
          {searchQuery && (
            <button type="button" onClick={() => setSearchQuery('')} aria-label="Clear search">
              Clear
            </button>
          )}
        </label>
      </section>

      {!loading && !error && recommendedInstruments.length > 0 && (
        <section className="market-recommendations" aria-labelledby="recommendations-title">
          <div className="market-section-heading">
            <div>
              <small>QUICK PICKS</small>
              <h2 id="recommendations-title">Recommended instruments</h2>
            </div>
            <span>{rankedRecommendations.length > 0 ? 'Popular NSE stocks' : 'Available listed picks'}</span>
          </div>
          <div className="recommendation-grid">
            {recommendedInstruments.map((instrument) => (
              <button
                key={instrument.instrumentKey}
                className="recommendation-card"
                type="button"
                onClick={() => openInstrument(instrument.symbol)}
              >
                <span className="recommendation-icon" aria-hidden="true">↗</span>
                <span className="recommendation-info">
                  <b>{instrument.symbol}</b>
                  <small>{instrument.companyName}</small>
                </span>
                <span className="recommendation-arrow" aria-hidden="true">→</span>
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="panel market-table" aria-labelledby="instrument-list-title">
        <div className="market-list-heading">
          <div>
            <small>INSTRUMENT DIRECTORY</small>
            <h2 id="instrument-list-title">
              {normalizedQuery ? 'Search results' : 'All instruments'}
            </h2>
          </div>
          {!loading && !error && (
            <span className="market-result-count">
              {normalizedQuery ? filteredInstruments.length : visibleInstruments.length}
              {normalizedQuery ? ' results' : ' shown'}
            </span>
          )}
        </div>

        <div className="market-table-scroll">
          <div className="table-head market-table-head" aria-hidden="true">
            <span>Instrument</span>
            <span>Market</span>
            <span>Exchange</span>
            <span>Action</span>
          </div>

          {loading && (
            <div className="market-empty-state">
              <span className="market-empty-icon" aria-hidden="true">⌕</span>
              <b>Loading instruments</b>
              <span>Market data is on its way.</span>
            </div>
          )}

          {error && (
            <div className="market-empty-state market-error-state" role="alert">
              <b>Couldn’t load instruments</b>
              <span>{error}</span>
            </div>
          )}

          {!loading && !error && visibleInstruments.length === 0 && (
            <div className="market-empty-state">
              <span className="market-empty-icon" aria-hidden="true">⌕</span>
              <b>No matching instruments</b>
              <span>Try another ticker, company, segment, or exchange.</span>
            </div>
          )}

          {!loading && !error && visibleInstruments.map((instrument) => (
            <div key={instrument.instrumentKey} className="market-row">
              <div className="market-instrument">
                <span className="market-symbol-icon" aria-hidden="true">
                  {instrument.symbol.slice(0, 1)}
                </span>
                <span>
                  <b>{instrument.symbol}</b>
                  <small>{instrument.companyName}</small>
                </span>
              </div>
              <span className="market-tag">{instrument.segment}</span>
              <span className="market-exchange">{instrument.exchange}</span>
              <button
                className="outline"
                type="button"
                onClick={() => openInstrument(instrument.symbol)}
              >
                View <span aria-hidden="true">→</span>
              </button>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
