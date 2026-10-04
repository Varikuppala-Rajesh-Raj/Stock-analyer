import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, type OptionChainRow } from '../../services/api';
import './OptionChain.css';

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
  const navigate = useNavigate();
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

              const call = row.call_options;
              const put = row.put_options;
              const callChange = call.market_data.close_price > 0
                ? ((call.market_data.ltp - call.market_data.close_price) / call.market_data.close_price) * 100
                : null;
              const putChange = put.market_data.close_price > 0
                ? ((put.market_data.ltp - put.market_data.close_price) / put.market_data.close_price) * 100
                : null;
              const openContract = (type: 'CE' | 'PE', key: string) => {
                if (!key) return;
                const search = new URLSearchParams({
                  expiry,
                  type,
                  strike: String(row.strike_price),
                  key,
                });
                navigate(`/nifty-options/contract?${search.toString()}`);
              };

              return (
                <tr
                  key={row.strike_price}
                  className={`${isAtm ? 'atm-row' : ''} ${row.strike_price < spot ? 'below-spot' : 'above-spot'}`}
                >

                  {/* CALL */}

                  <td className="call-cell">
                    <button className="option-contract-link call-contract" disabled={!call.instrument_key} onClick={() => openContract('CE', call.instrument_key)} aria-label={`Open NIFTY ${row.strike_price} call option`}>
                      <b>{money(call.market_data.ltp)}</b>
                      <small className={callChange == null ? '' : callChange >= 0 ? 'option-positive' : 'option-negative'}>
                        {callChange == null ? 'Select call' : `${callChange >= 0 ? '+' : ''}${callChange.toFixed(2)}%`}
                      </small>
                    </button>
                  </td>

                  <td className="call-cell">
                    {compact.format(
                      call.market_data.oi
                    )}
                  </td>

                  <td className="call-cell">
                    {compact.format(
                      call.market_data.volume
                    )}
                  </td>

                  <td className="call-cell">
                    {call.option_greeks.iv.toFixed(2)}%
                  </td>

                  <td className="call-cell">
                    {call.option_greeks.delta.toFixed(3)}
                  </td>

                  <td className="call-cell">
                    {number.format(
                      call.market_data.bid_price
                    )}
                    {' / '}
                    {number.format(
                      call.market_data.ask_price
                    )}
                  </td>

                  {/* STRIKE */}

                  <td className={`strike-cell ${isAtm ? 'atm-strike' : ''}`}>
                    <b>
                      {number.format(row.strike_price)}
                    </b>

                    <span className="strike-distance">{row.strike_price < spot ? 'ITM' : row.strike_price > spot ? 'OTM' : 'ATM'}</span>
                  </td>

                  {/* PUT */}

                  <td className="put-cell">
                    {number.format(
                      put.market_data.bid_price
                    )}
                    {' / '}
                    {number.format(
                      put.market_data.ask_price
                    )}
                  </td>

                  <td className="put-cell">
                    {put.option_greeks.delta.toFixed(3)}
                  </td>

                  <td className="put-cell">
                    {put.option_greeks.iv.toFixed(2)}%
                  </td>

                  <td className="put-cell">
                    {compact.format(
                      put.market_data.volume
                    )}
                  </td>

                  <td className="put-cell">
                    {compact.format(
                      put.market_data.oi
                    )}
                  </td>

                  <td className="put-cell">
                    <button className="option-contract-link put-contract" disabled={!put.instrument_key} onClick={() => openContract('PE', put.instrument_key)} aria-label={`Open NIFTY ${row.strike_price} put option`}>
                      <b>{money(put.market_data.ltp)}</b>
                      <small className={putChange == null ? '' : putChange >= 0 ? 'option-positive' : 'option-negative'}>
                        {putChange == null ? 'Select put' : `${putChange >= 0 ? '+' : ''}${putChange.toFixed(2)}%`}
                      </small>
                    </button>
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