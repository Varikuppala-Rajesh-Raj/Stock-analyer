import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useInstruments,
  useMarketWebSocket,
  useQuote,
  useWatchlistSubscription,
} from '../services/hooks';
import type { Instrument, Quote } from '../services/api';

const DEFAULT_WATCHLIST = [
  'NIFTY',
  'BANKNIFTY',
  'RELIANCE',
];

const STORAGE_KEY = 'watchlist';

export function Watchlist() {
  const navigate = useNavigate();

  // ============================================================
  // Instrument catalog
  // ============================================================

  const {
    instruments,
    loading: catalogLoading,
    error: catalogError,
  } = useInstruments();

  // ============================================================
  // Watchlist state
  // ============================================================

  const [watchlist, setWatchlist] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);

      if (!saved) {
        return DEFAULT_WATCHLIST;
      }

      const parsed = JSON.parse(saved);

      return Array.isArray(parsed)
        ? parsed
        : DEFAULT_WATCHLIST;

    } catch {
      return DEFAULT_WATCHLIST;
    }
  });

  const [query, setQuery] = useState('');
  const watchlistRef = useRef(watchlist);
  watchlistRef.current = watchlist;

  // ============================================================
  // Instrument finder
  // ============================================================

  const [isFinderOpen, setFinderOpen] = useState(false);
  const [finderQuery, setFinderQuery] = useState('');

  // ============================================================
  // WebSocket subscription hook
  // ============================================================

  const {
    subscribe,
    unsubscribe,
    loading: subscriptionLoading,
    error: subscriptionError,
  } = useWatchlistSubscription();

  const { quotesBySymbol } = useMarketWebSocket(instruments);

  // ============================================================
  // Persist watchlist
  // ============================================================

  useEffect(() => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(watchlist)
    );
  }, [watchlist]);

  // Re-establish subscriptions saved from a previous page load. Add/remove
  // actions below keep this set current for the rest of the session.
  useEffect(() => {
    void Promise.all(watchlistRef.current.map(symbol => subscribe(symbol).catch(() => undefined)));
    return () => {
      void Promise.all(watchlistRef.current.map(symbol => unsubscribe(symbol).catch(() => undefined)));
    };
  }, [subscribe, unsubscribe]);

  // ============================================================
  // Filter current watchlist
  // ============================================================

  const shown = useMemo(() => {
    const term = query.trim().toLowerCase();

    if (!term) {
      return watchlist;
    }

    return watchlist.filter(symbol =>
      symbol.toLowerCase().includes(term)
    );
  }, [watchlist, query]);

  // ============================================================
  // Search instrument catalog
  // ============================================================

  const matches = useMemo(() => {
    const term = finderQuery.trim().toLowerCase();

    return instruments
      .filter(instrument => {
        if (!term) {
          return true;
        }

        return [
          instrument.symbol,
          instrument.companyName,
          instrument.exchange,
          instrument.segment,
        ].some(value =>
          value?.toLowerCase().includes(term)
        );
      })
      .slice(0, 12);

  }, [finderQuery, instruments]);

  // ============================================================
  // Add instrument
  // ============================================================

  const addInstrument = async (
    instrument: Instrument
  ) => {

    if (
      watchlist.includes(instrument.symbol)
    ) {
      setFinderOpen(false);
      setFinderQuery('');
      return;
    }

    try {

      // Tell backend to subscribe this instrument
      // to the Upstox WebSocket.
      await subscribe(instrument.symbol);

      // Only add to UI after successful subscription.
      setWatchlist(current => [
        ...current,
        instrument.symbol,
      ]);

      setFinderOpen(false);
      setFinderQuery('');

    } catch {
      // Error is already handled by hook.
    }
  };

  // ============================================================
  // Remove instrument
  // ============================================================

  const removeInstrument = async (
    symbol: string
  ) => {

    try {

      // Tell backend to unsubscribe
      // from Upstox WebSocket.
      await unsubscribe(symbol);

      // Remove from local watchlist.
      setWatchlist(current =>
        current.filter(
          item => item !== symbol
        )
      );

    } catch {
      // Error is already handled by hook.
    }
  };

  // ============================================================
  // Open instrument
  // ============================================================

  const openInstrument = (
    symbol: string
  ) => {
    navigate(
      `/instruments/${encodeURIComponent(symbol)}`
    );
  };

  // ============================================================
  // Render
  // ============================================================

  return (
    <main>

      {/* ======================================================
          HEADER
      ====================================================== */}

      <div className="heading">

        <div>
          <small>PERSONAL MARKET LIST</small>

          <h1>Watchlist</h1>

          <p>
            Find any listed instrument, save it,
            then analyse or paper-trade it.
          </p>
        </div>

        <button
          className="primary"
          onClick={() => {
            setFinderOpen(true);
            setFinderQuery('');
          }}
          disabled={subscriptionLoading}
        >
          + Add instrument
        </button>

      </div>


      {/* ======================================================
          SUBSCRIPTION ERROR
      ====================================================== */}

      {subscriptionError && (
        <section
          className="panel"
          style={{
            marginBottom: '16px',
            borderColor: 'rgba(255, 80, 80, 0.4)',
          }}
        >
          <b>WebSocket subscription failed</b>

          <p className="bad">
            {subscriptionError}
          </p>
        </section>
      )}


      {/* ======================================================
          WATCHLIST
      ====================================================== */}

      <section className="panel watch">

        {/* Search/filter */}

        <input
          className="watch-search"
          value={query}
          onChange={event =>
            setQuery(event.target.value)
          }
          placeholder="Filter your watchlist"
        />


        {/* Table header */}

        <div className="watch-head">

          <span>Instrument</span>

          <span>Last price</span>

          <span>Day change</span>

          <span>Action</span>

        </div>


        {/* Empty state */}

        {shown.length === 0 && (

          <div className="empty-state">

            <b>⌕</b>

            <h2>
              {watchlist.length === 0
                ? 'Your watchlist is empty'
                : 'No matching instruments'}
            </h2>

            <p>
              {watchlist.length === 0
                ? 'Add an instrument from the market catalog.'
                : 'Try another search or add a new instrument.'}
            </p>

            <button
              className="primary"
              onClick={() => {
                setFinderOpen(true);
                setFinderQuery('');
              }}
            >
              + Add instrument
            </button>

          </div>

        )}


        {/* Watchlist rows */}

        {shown.map(symbol => (

          <WatchlistRow
            key={symbol}
            symbol={symbol}
            navigate={openInstrument}
            onRemove={() =>
              removeInstrument(symbol)
            }
            removing={subscriptionLoading}
            liveQuote={quotesBySymbol[symbol]}
          />

        ))}

      </section>


      {/* ======================================================
          INSTRUMENT FINDER MODAL
      ====================================================== */}

      {isFinderOpen && (

        <div
          className="instrument-dialog"
          role="dialog"
          aria-modal="true"
          aria-label="Instrument search"
        >

          <section className="panel instrument-finder">

            {/* Finder header */}

            <div className="panel-title">

              <div>

                <small>MARKET CATALOG</small>

                <h2>
                  Add an instrument
                </h2>

              </div>

              <button
                aria-label="Close"
                onClick={() =>
                  setFinderOpen(false)
                }
              >
                ×
              </button>

            </div>


            {/* Finder search */}

            <input
              autoFocus
              className="watch-search"
              value={finderQuery}
              onChange={event =>
                setFinderQuery(
                  event.target.value
                )
              }
              placeholder="Search symbol, company, exchange, or segment"
            />


            {/* Catalog loading */}

            {catalogLoading && (

              <div className="empty-state">

                <p>
                  Loading instrument catalog…
                </p>

              </div>

            )}


            {/* Catalog error */}

            {!catalogLoading &&
              catalogError && (

                <div className="empty-state">

                  <h2>
                    Catalog unavailable
                  </h2>

                  <p className="bad">
                    Could not load the instrument
                    catalog.
                  </p>

                </div>
              )}


            {/* Search results */}

            {!catalogLoading &&
              !catalogError && (

                <div className="instrument-results">

                  {matches.map(
                    instrument => {

                      const added =
                        watchlist.includes(
                          instrument.symbol
                        );

                      return (

                        <button
                          key={
                            instrument.instrumentKey
                          }
                          onClick={() =>
                            addInstrument(
                              instrument
                            )
                          }
                          disabled={
                            added ||
                            subscriptionLoading
                          }
                        >

                          <span>

                            <b>
                              {instrument.symbol}
                            </b>

                            <small>
                              {instrument.companyName ||
                                instrument.symbol}
                              {' · '}
                              {instrument.exchange}
                              {' · '}
                              {instrument.segment}
                            </small>

                          </span>

                          <em>
                            {added
                              ? 'Added'
                              : subscriptionLoading
                                ? 'Adding…'
                                : '+ Add'}
                          </em>

                        </button>

                      );
                    }
                  )}


                  {/* No results */}

                  {matches.length === 0 && (

                    <div className="empty-state">

                      <h2>
                        No instruments found
                      </h2>

                      <p>
                        No instruments match
                        "{finderQuery}".
                      </p>

                    </div>

                  )}

                </div>

              )}

          </section>

        </div>

      )}

    </main>
  );
}


/* ============================================================
   WATCHLIST ROW
============================================================ */

function WatchlistRow({
  symbol,
  onRemove,
  navigate,
  removing,
  liveQuote,
}: {
  symbol: string;
  onRemove: () => void;
  navigate: (to: string) => void;
  removing: boolean;
  liveQuote?: Partial<Quote>;
}) {

  const {
    quote,
    loading,
    error,
  } = useQuote(symbol, 30000);


  // ==========================================================
  // Safe price calculation
  // ==========================================================

  const displayedQuote = liveQuote ?? quote;

  const last = Number(
    displayedQuote?.lastPrice ??
    displayedQuote?.price
  );

  const open = Number(
    displayedQuote?.open ??
    last
  );

  const validLast =
    Number.isFinite(last);

  const validOpen =
    Number.isFinite(open) &&
    open > 0;

  const change =
    validLast && validOpen
      ? ((last - open) / open) * 100
      : 0;

  const isUp = change >= 0;


  // ==========================================================
  // Render
  // ==========================================================

  return (

    <div
      className="watch-row clickable"
      onClick={() =>
        navigate(symbol)
      }
    >

      {/* Instrument */}

      <div>

        <b>
          {symbol}
        </b>

        <small>
          {quote?.exchange || 'NSE'}
        </small>

      </div>


      {/* Last price */}

      <b>

        {loading
          ? 'Loading…'
          : error
            ? 'Unavailable'
            : validLast
              ? `₹${last.toFixed(2)}`
              : 'N/A'}

      </b>


      {/* Day change */}

      <span
        className={
          isUp
            ? 'good'
            : 'bad'
        }
      >

        {isUp
          ? '↗ +'
          : '↘ '}

        {validLast && validOpen
          ? `${change.toFixed(2)}%`
          : 'N/A'}

      </span>


      {/* Remove */}

      <button
        className="remove"
        aria-label={`Remove ${symbol}`}
        disabled={removing}
        onClick={event => {

          event.stopPropagation();

          onRemove();

        }}
      >

        ×

      </button>

    </div>

  );
}
