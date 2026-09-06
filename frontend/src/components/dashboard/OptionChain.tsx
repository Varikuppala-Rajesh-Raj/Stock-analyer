import { useEffect, useMemo, useState } from 'react';
import { api, type OptionChainRow } from '../../services/api';

const number = new Intl.NumberFormat('en-IN', {
  maximumFractionDigits: 2,
});

const compact = new Intl.NumberFormat('en-IN', {
  notation: 'compact',
  maximumFractionDigits: 1,
});

const money = (value: number) =>
  `₹${number.format(value)}`;

type Props = {
  expiry: string;
  expiries: string[];
  onExpiryChange: (expiry: string) => void;
};

export function OptionChain({
  expiry,
  expiries,
  onExpiryChange,
}: Props) {
  const [rows, setRows] = useState<OptionChainRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setLoading(true);
      setError('');

      const response = await api.niftyOptionChain(expiry);

      setRows(response.data ?? []);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to load option chain'
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();

    const timer = window.setInterval(() => {
      void load();
    }, 15000);

    return () => window.clearInterval(timer);
  }, [expiry]);

  const spot = rows[0]?.underlying_spot_price ?? 0;

  const atmStrike = useMemo(() => {
    if (!rows.length || !spot) return null;

    return rows.reduce((closest, row) =>
      Math.abs(row.strike_price - spot) <
      Math.abs(closest.strike_price - spot)
        ? row
        : closest
    ).strike_price;
  }, [rows, spot]);

  const visibleRows = useMemo(() => {
    if (!atmStrike) return rows;

    return [...rows]
      .sort(
        (a, b) =>
          Math.abs(a.strike_price - atmStrike) -
          Math.abs(b.strike_price - atmStrike)
      )
      .slice(0, 21)
      .sort((a, b) => a.strike_price - b.strike_price);
  }, [rows, atmStrike]);

  if (loading && !rows.length) {
    return (
      <section className="panel">
        <div className="panel-title">
          <div>
            <small>NIFTY OPTIONS</small>
            <h2>Option Chain</h2>
          </div>
        </div>

        <p className="api-state">
          Loading option chain…
        </p>
      </section>
    );
  }

  if (error && !rows.length) {
    return (
      <section className="panel">
        <div className="panel-title">
          <div>
            <small>NIFTY OPTIONS</small>
            <h2>Option Chain</h2>
          </div>
        </div>

        <p className="api-state error">
          {error}
        </p>
      </section>
    );
  }

  return (
    <section className="panel option-chain-panel">

      <div className="panel-title">
        <div>
          <small>NIFTY OPTIONS · {expiry}</small>
          <h2>Option Chain</h2>
        </div>

      <div className="option-chain-summary">
  <span>
    Spot <b>{money(spot)}</b>
  </span>

  <span>
    ATM <b>{atmStrike ?? '—'}</b>
  </span>

  <label className="expiry-control">
    <span>Expiry</span>

    <select
      value={expiry}
      onChange={e => onExpiryChange(e.target.value)}
    >
      {expiries.map(item => (
        <option key={item} value={item}>
          {new Date(`${item}T00:00:00`).toLocaleDateString(
            'en-IN',
            {
              day: '2-digit',
              month: 'short',
              year: 'numeric',
            }
          )}
        </option>
      ))}
    </select>
  </label>

  <button onClick={() => void load()}>
    Refresh
  </button>
</div>
      </div>

      <div className="option-chain-wrapper">
        <table className="option-chain">

          <thead>
            <tr>
              <th colSpan={6} className="call-header">
                CALLS
              </th>

              <th className="strike-header">
                STRIKE
              </th>

              <th colSpan={6} className="put-header">
                PUTS
              </th>
            </tr>

            <tr>
              <th>LTP</th>
              <th>OI</th>
              <th>VOL</th>
              <th>IV</th>
              <th>Δ</th>
              <th>BID / ASK</th>

              <th>STRIKE</th>

              <th>BID / ASK</th>
              <th>Δ</th>
              <th>IV</th>
              <th>VOL</th>
              <th>OI</th>
              <th>LTP</th>
            </tr>
          </thead>

          <tbody>
            {visibleRows.map(row => {
              const isAtm =
                row.strike_price === atmStrike;

              return (
                <tr
                  key={row.strike_price}
                  className={isAtm ? 'atm-row' : ''}
                >

                  {/* CALL */}

                  <td>
                    {money(row.call_options.market_data.ltp)}
                  </td>

                  <td>
                    {compact.format(
                      row.call_options.market_data.oi
                    )}
                  </td>

                  <td>
                    {compact.format(
                      row.call_options.market_data.volume
                    )}
                  </td>

                  <td>
                    {row.call_options.option_greeks.iv.toFixed(2)}%
                  </td>

                  <td>
                    {row.call_options.option_greeks.delta.toFixed(3)}
                  </td>

                  <td>
                    {number.format(
                      row.call_options.market_data.bid_price
                    )}
                    {' / '}
                    {number.format(
                      row.call_options.market_data.ask_price
                    )}
                  </td>

                  {/* STRIKE */}

                  <td className="strike-cell">
                    <b>
                      {number.format(row.strike_price)}
                    </b>

                    {isAtm && (
                      <span className="atm-badge">
                        ATM
                      </span>
                    )}
                  </td>

                  {/* PUT */}

                  <td>
                    {number.format(
                      row.put_options.market_data.bid_price
                    )}
                    {' / '}
                    {number.format(
                      row.put_options.market_data.ask_price
                    )}
                  </td>

                  <td>
                    {row.put_options.option_greeks.delta.toFixed(3)}
                  </td>

                  <td>
                    {row.put_options.option_greeks.iv.toFixed(2)}%
                  </td>

                  <td>
                    {compact.format(
                      row.put_options.market_data.volume
                    )}
                  </td>

                  <td>
                    {compact.format(
                      row.put_options.market_data.oi
                    )}
                  </td>

                  <td>
                    {money(row.put_options.market_data.ltp)}
                  </td>

                </tr>
              );
            })}
          </tbody>

        </table>
      </div>

      {loading && (
        <p className="api-state">
          Updating option chain…
        </p>
      )}

    </section>
  );
}