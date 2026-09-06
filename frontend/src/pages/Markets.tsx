import { useNavigate } from 'react-router-dom';
import { useInstruments } from '../services/hooks';

export function Markets() {
  const navigate = useNavigate();

  const {
    instruments,
    loading,
    error,
  } = useInstruments();

  const visibleInstruments = instruments.slice(0, 20);

  return (
    <main>
      <div className="heading">
        <div>
          <small>MARKET EXPLORER</small>
          <h1>Markets at a glance</h1>
          <p>
            Browse instruments and open an instrument for
            live quote and chart data.
          </p>
        </div>

        <button className="primary">
          + Add instrument
        </button>
      </div>

      <section className="panel table">

        <div className="table-head">
          <span>Instrument</span>
          <span>Market</span>
          <span>Exchange</span>
          <span>Action</span>
        </div>

        {loading && (
          <div className="market-row">
            <span>Loading instruments...</span>
          </div>
        )}

        {error && (
          <div className="market-row">
            <span>
              Failed to load instruments: {error}
            </span>
          </div>
        )}

        {!loading &&
          !error &&
          visibleInstruments.map(instrument => (
            <div
              key={instrument.instrumentKey}
              className="market-row clickable"
              onClick={() =>
                navigate(
                  `/instruments/${encodeURIComponent(
                    instrument.symbol
                  )}`
                )
              }
            >
              <div>
                <b>{instrument.symbol}</b>

                <small>
                  {instrument.companyName}
                </small>
              </div>

              <span>
                {instrument.segment}
              </span>

              <span>
                {instrument.exchange}
              </span>

              <button
                className="outline"
                onClick={(e) => {
                  e.stopPropagation();

                  navigate(
                    `/instruments/${encodeURIComponent(
                      instrument.symbol
                    )}`
                  );
                }}
              >
                View
              </button>
            </div>
          ))}
      </section>
    </main>
  );
}