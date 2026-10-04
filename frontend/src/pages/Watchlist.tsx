import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useInstruments,
  useMarketWebSocket,
  useQuote,
  useWatchlistSubscription,
} from '../services/hooks';
import type { Instrument, Quote } from '../services/api';
import './Watchlist.css';

const DEFAULT_WATCHLIST = [
  'NIFTY',
  'BANKNIFTY',
  'RELIANCE',
];

const STORAGE_KEY = 'watchlists';
const ACTIVE_LIST_KEY = 'activeWatchlist';
const LEGACY_STORAGE_KEY = 'watchlist';

function readWatchlists(): Record<string, string[]> {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const parsed: unknown = JSON.parse(saved);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        const valid = Object.entries(parsed).filter((entry): entry is [string, string[]] =>
          typeof entry[0] === 'string' && Array.isArray(entry[1])
          && entry[1].every((symbol) => typeof symbol === 'string'),
        );
        if (valid.length) return Object.fromEntries(valid);
      }
    }
    const legacy: unknown = JSON.parse(localStorage.getItem(LEGACY_STORAGE_KEY) ?? 'null');
    return {
      'My Watchlist': Array.isArray(legacy)
        ? legacy.filter((symbol): symbol is string => typeof symbol === 'string')
        : DEFAULT_WATCHLIST,
    };
  } catch {
    return { 'My Watchlist': DEFAULT_WATCHLIST };
  }
}

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

  const [watchlists, setWatchlists] = useState<Record<string, string[]>>(readWatchlists);
  const [activeList, setActiveList] = useState(() => {
    const saved = localStorage.getItem(ACTIVE_LIST_KEY);
    return saved && Object.hasOwn(readWatchlists(), saved) ? saved : 'My Watchlist';
  });
  const watchlist = watchlists[activeList] ?? [];
  const allSymbols = [...new Set(Object.values(watchlists).flat())];

  const [query, setQuery] = useState('');
  const [newListName, setNewListName] = useState('');
  const [creatingList, setCreatingList] = useState(false);
  const [addingSymbol, setAddingSymbol] = useState('');
  const watchlistRef = useRef(allSymbols);
  watchlistRef.current = allSymbols;

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
    error: subscriptionError,
  } = useWatchlistSubscription();

  const { quotesBySymbol } = useMarketWebSocket(instruments);

  // ============================================================
  // Persist watchlist
  // ============================================================

  useEffect(() => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(watchlists)
    );
  }, [watchlists]);

  useEffect(() => {
    if (Object.hasOwn(watchlists, activeList)) {
      localStorage.setItem(ACTIVE_LIST_KEY, activeList);
    } else {
      const firstList = Object.keys(watchlists)[0] ?? 'My Watchlist';
      setActiveList(firstList);
    }
  }, [activeList, watchlists]);

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

    if (watchlist.includes(instrument.symbol)) {
      setFinderOpen(false);
      setFinderQuery('');
      return;
    }

    setAddingSymbol(instrument.symbol);
    setWatchlists(current => ({
      ...current,
      [activeList]: [...(current[activeList] ?? []), instrument.symbol],
    }));
    setFinderOpen(false);
    setFinderQuery('');
    try {
      if (!allSymbols.includes(instrument.symbol)) await subscribe(instrument.symbol);
    } catch {
      // The local list remains available; the subscription error is shown above it.
    } finally {
      setAddingSymbol('');
    }
  };

  // ============================================================
  // Remove instrument
  // ============================================================

  const removeInstrument = async (
    symbol: string
  ) => {
    const remainsInAnotherList = Object.entries(watchlists)
      .some(([name, symbols]) => name !== activeList && symbols.includes(symbol));
    setWatchlists(current => ({
      ...current,
      [activeList]: (current[activeList] ?? []).filter(item => item !== symbol),
    }));
    try {
      if (!remainsInAnotherList) await unsubscribe(symbol);
    } catch {
      // The local removal stands; the subscription error is shown above the list.
    }
  };

  const createWatchlist = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const name = newListName.trim();
    if (!name || Object.hasOwn(watchlists, name)) return;
    setWatchlists(current => ({ ...current, [name]: [] }));
    setActiveList(name);
    setNewListName('');
    setCreatingList(false);
    setQuery('');
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
        >
          + Add instrument
        </button>

      </div>

      <section className="panel watchlist-manager" aria-label="Manage personal watchlists">
        <label>
          Your watchlists
          <select value={activeList} onChange={event => { setActiveList(event.target.value); setQuery(''); }}>
            {Object.keys(watchlists).map(name => <option key={name} value={name}>{name} ({watchlists[name].length})</option>)}
          </select>
        </label>
        {creatingList ? (
          <form onSubmit={createWatchlist}>
            <input autoFocus value={newListName} onChange={event => setNewListName(event.target.value)} maxLength={40} placeholder="Name your watchlist" aria-label="New watchlist name" />
            <button className="primary" type="submit" disabled={!newListName.trim() || Object.hasOwn(watchlists, newListName.trim())}>Create list</button>
            <button className="outline" type="button" onClick={() => { setCreatingList(false); setNewListName(''); }}>Cancel</button>
          </form>
        ) : (
          <button className="outline" onClick={() => setCreatingList(true)}>+ Create watchlist</button>
        )}
      </section>

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
                          disabled={added || addingSymbol === instrument.symbol}
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
                              : addingSymbol === instrument.symbol
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
  liveQuote,
}: {
  symbol: string;
  onRemove: () => void;
  navigate: (to: string) => void;
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

  const previousClose = Number(displayedQuote?.previousClose ?? quote?.previousClose);

  const validLast =
    Number.isFinite(last) && last > 0;

  const validPreviousClose =
    Number.isFinite(previousClose) && previousClose > 0;

  const changePercent = Number.isFinite(Number(displayedQuote?.changePercent))
    ? Number(displayedQuote?.changePercent)
    : validLast && validPreviousClose
      ? ((last - previousClose) / previousClose) * 100
      : null;
  const changeAmount = Number.isFinite(Number(displayedQuote?.change))
    ? Number(displayedQuote?.change)
    : validLast && validPreviousClose
      ? last - previousClose
      : null;

  const isUp = (changeAmount ?? changePercent ?? 0) >= 0;


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

        {loading && !validLast
          ? 'Loading…'
        : error && !validLast
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

        {changeAmount != null && changePercent != null
          ? `${isUp ? '+' : ''}₹${changeAmount.toFixed(2)} (${isUp ? '+' : ''}${changePercent.toFixed(2)}%)`
          : 'N/A'}

      </span>


      {/* Remove */}

      <button
        className="remove"
        aria-label={`Remove ${symbol}`}
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
