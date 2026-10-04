import { useEffect, useMemo, useState } from 'react';
import {
  api,
  type Account,
  type PaperAutomationAudit,
  type PaperAutomationStatus,
  type PaperOrder,
  type Position,
} from '../services/api';
import { getNseMarketSessionLabel } from '../utils/marketSession';

const money = (value?: number) =>
  value == null
    ? '—'
    : new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
      }).format(value);

const dateTime = (value?: string) =>
  value ? new Date(value).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' }) : '—';

const pct = (value: number) => `${value.toFixed(2)}%`;
const indiaDateKey = (date: Date) => new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Kolkata',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(date);

export function PaperTradingPage() {
  const [account, setAccount] = useState<Account>();
  const [positions, setPositions] = useState<Position[]>([]);
  const [orders, setOrders] = useState<PaperOrder[]>([]);
  const [automationStatus, setAutomationStatus] = useState<PaperAutomationStatus>();
  const [cycles, setCycles] = useState<PaperAutomationAudit[]>([]);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [runningCycle, setRunningCycle] = useState(false);
  const [automationAction, setAutomationAction] = useState('');
  const [closingPosition, setClosingPosition] = useState('');
  const [now, setNow] = useState(() => new Date());

  const load = async () => {
    try {
      const [nextAccount, nextPositions, nextOrders, status, recentCycles] =
        await Promise.all([
          api.account(),
          api.positions(),
          api.orders(),
          api.paperAutomationStatus(),
          api.paperAutomationCycles(),
        ]);
      setAccount(nextAccount);
      setPositions(nextPositions);
      setOrders(nextOrders);
      setAutomationStatus(status);
      setCycles(recentCycles);
      setError('');
    } catch {
      setError('Paper-trading data is unavailable. Check the backend connection and try refreshing.');
    }
  };

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 10000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 15000);
    return () => window.clearInterval(timer);
  }, []);

  const invested = useMemo(
    () => positions.reduce((total, position) => total + position.averageEntryPrice * position.quantity, 0),
    [positions],
  );
  const currentExposure = useMemo(
    () => positions.reduce((total, position) => total + position.currentPrice * position.quantity, 0),
    [positions],
  );
  const stopRisk = useMemo(
    () => positions.reduce((total, position) => (
      total + (position.stopLoss == null
        ? 0
        : Math.max(position.averageEntryPrice - position.stopLoss, 0) * position.quantity)
    ), 0),
    [positions],
  );
  const exposurePercent = account?.initialBalance
    ? (currentExposure / account.initialBalance) * 100
    : 0;
  const stopRiskPercent = account?.initialBalance
    ? (stopRisk / account.initialBalance) * 100
    : 0;
  const today = indiaDateKey(new Date());
  const todayFilledOrders = orders.filter(
    (order) => order.status === 'FILLED' && indiaDateKey(new Date(order.timestamp)) === today,
  ).length;
  const orderById = useMemo(
    () => new Map(orders.map((order) => [order.id, order])),
    [orders],
  );
  const automation = automationStatus?.automation;
  const lastCycle = automationStatus?.cycle.lastCycle;

  const runCycle = async () => {
    setRunningCycle(true);
    setActionError('');
    try {
      await api.runNiftyPaperCycle();
      await load();
    } catch {
      setActionError('Unable to run the NIFTY paper cycle. Check backend status and latest market-data availability.');
    } finally {
      setRunningCycle(false);
    }
  };

  const controlAutomation = async (action: 'stop' | 'resume') => {
    if (action === 'resume' && !window.confirm('Resume automated paper trading? The backend may submit paper orders while the market is open.')) {
      return;
    }
    setAutomationAction(action);
    setActionError('');
    try {
      if (action === 'stop') await api.stopPaperAutomation();
      else await api.resumePaperAutomation();
      await load();
    } catch {
      setActionError(action === 'stop'
        ? 'Unable to persist the automation stop. Verify backend and database connectivity.'
        : 'Unable to resume automation. Verify it is enabled in backend configuration.');
      await load();
    } finally {
      setAutomationAction('');
    }
  };

  const closePosition = async (id: string) => {
    setActionError('');
    setClosingPosition(id);
    try {
      await api.closePosition(id);
      await load();
    } catch {
      setActionError('Unable to close the paper position.');
    } finally {
      setClosingPosition('');
    }
  };

  return (
    <main className="paper-dashboard">
      <div className="heading paper-heading">
        <div>
          <small>SIMULATED EXECUTION ONLY</small>
          <h1>Paper trading portfolio</h1>
          <p>Virtual ledger, open NIFTY options, execution history, and automation decisions.</p>
        </div>
        <button className="outline" onClick={() => void load()}>Refresh data</button>
      </div>

      {error && <p className="api-state error" role="alert">{error}</p>}
      {actionError && <p className="api-state error" role="alert">{actionError}</p>}

      <section className="paper-automation panel" aria-label="Automation status">
        <div className="paper-automation-copy">
          <small>NIFTY CE/PE AUTOMATION</small>
          <h2>Paper mode <span className="pill neutral">NO BROKER ORDERS</span></h2>
          <p>
            {automation?.configuredEnabled
              ? automation.enabled
                ? 'Automation is enabled by backend configuration and is not stopped.'
                : automation.stopped
                  ? 'Automation is stopped. The stop state persists across backend restarts.'
                  : 'Automation is disabled in backend configuration.'
              : 'Automation is disabled in backend configuration; paper cycle actions are unavailable.'}
          </p>
        </div>
        <div className="paper-automation-actions">
          {automation?.configuredEnabled && automation.enabled && (
            <button
              className="danger-button"
              onClick={() => void controlAutomation('stop')}
              disabled={automationAction !== ''}
            >
              {automationAction === 'stop' ? 'Stopping…' : 'STOP AUTOMATION'}
            </button>
          )}
          {automation?.configuredEnabled && automation.stopped && (
            <button
              className="outline"
              onClick={() => void controlAutomation('resume')}
              disabled={automationAction !== ''}
            >
              {automationAction === 'resume' ? 'Resuming…' : 'Resume automation'}
            </button>
          )}
          <button
            className="outline"
            onClick={() => void runCycle()}
            disabled={!automation?.enabled || runningCycle || automationAction !== ''}
          >
            {runningCycle ? 'Running cycle…' : 'Run one cycle'}
          </button>
        </div>
        <div className="paper-automation-facts">
          <Metric label="State" value={automation ? automation.enabled ? 'ENABLED' : 'DISABLED' : 'LOADING'} hint={automation?.stopped ? 'Emergency stop active' : 'Paper execution only'} />
          <Metric label="Latest cycle" value={lastCycle?.status ?? '—'} hint={lastCycle?.timestamp && lastCycle.timestamp !== '1970-01-01T00:00:00Z' ? dateTime(lastCycle.timestamp) : 'No cycle recorded'} />
          <Metric label="Today’s filled orders" value={String(todayFilledOrders)} hint="Includes entries and exits" />
          <Metric label="Today’s P&L" value={money(account?.dailyPnl)} hint="Paper account, charges applied" tone={account?.dailyPnl} />
        </div>
        <div className="paper-risk-line">
          <span>
            Exposure <b>{pct(exposurePercent)}</b> / {automation?.maxExposurePercent ?? 20}%
          </span>
          <span>
            Stop risk <b>{pct(stopRiskPercent)}</b> / {automation?.maxLossPercent ?? 1}%
          </span>
          <span>Max lots per trade <b>{automation?.maxLotsPerTrade ?? 1}</b></span>
          <span>Market state <b>{getNseMarketSessionLabel(now)}</b></span>
        </div>
      </section>

      <section className="paper-metrics" aria-label="Paper account summary">
        <Metric label="Virtual capital" value={money(account?.initialBalance)} hint="Starting account balance" />
        <Metric label="Available cash" value={money(account?.availableCash)} hint="After open positions and charges" />
        <Metric label="Invested" value={money(invested)} hint={`${money(account?.usedMargin)} ledger margin`} />
        <Metric label="Unrealized P&L" value={money(account?.unrealizedPnl)} hint="Current open positions" tone={account?.unrealizedPnl} />
        <Metric label="Realized P&L" value={money(account?.realizedPnl)} hint="Closed paper trades" tone={account?.realizedPnl} />
        <Metric label="Portfolio equity" value={account ? money(account.initialBalance + account.totalPnl) : 'Loading…'} hint={`${money(account?.totalPnl)} total P&L`} tone={account?.totalPnl} />
        <Metric label="Open positions" value={String(positions.length)} hint={positions.length ? 'Currently holding' : 'Flat'} />
      </section>

      <section className="panel paper-section">
        <div className="panel-title">
          <div><small>LIVE PAPER LEDGER</small><h2>Open positions</h2></div>
          <span className="pill neutral">{positions.length} OPEN</span>
        </div>
        {positions.length === 0 ? (
          <div className="paper-empty">No open paper positions. New holdings will appear here after a paper fill.</div>
        ) : (
          <div className="paper-position-grid">
            {positions.map((position) => {
              const cycle = linkedCycle(position.symbol, cycles, orders);
              const hasEntryFill = orders.some((order) =>
                order.symbol === position.symbol && order.side === 'BUY' && order.status === 'FILLED');
              return <article className="paper-position" key={position.id}>
                <div className="paper-position-head">
                  <div>
                    <small>{position.exchange || 'PAPER'} · {position.status}</small>
                    <h3>{position.symbol}</h3>
                  </div>
                  <span className={`pill ${position.unrealizedPnl >= 0 ? 'good' : 'sell'}`}>
                    {position.side}
                  </span>
                </div>
                <div className="paper-position-pnl">
                  <span>Unrealized P&amp;L</span>
                  <b className={position.unrealizedPnl >= 0 ? 'good' : 'bad'}>{money(position.unrealizedPnl)}</b>
                </div>
                <div className="paper-position-facts">
                  <Fact label="Opened" value={dateTime(position.openedAt)} />
                  <Fact label="Entry price" value={money(position.averageEntryPrice)} />
                  <Fact label="Current price" value={money(position.currentPrice)} />
                  <Fact label="Quantity" value={`${position.quantity} units`} />
                  <Fact label="Investment" value={money(position.averageEntryPrice * position.quantity)} />
                  <Fact label="Stop loss" value={money(position.stopLoss)} />
                  <Fact label="Target" value={money(position.target)} />
                  <Fact label="Expiry / strike" value={cycle ? `${cycle.optionType} ${cycle.strike} · ${cycle.expiry ?? 'expiry unknown'}` : 'Not recorded for this position'} />
                </div>
                <div className="paper-lifecycle" aria-label="Position lifecycle">
                  <LifecycleStep label="Signal" active={Boolean(cycle?.direction)} />
                  <LifecycleStep label="Option selected" active={Boolean(cycle?.optionType)} />
                  <LifecycleStep label="Paper order filled" active={hasEntryFill} />
                  <LifecycleStep label="Holding" active />
                  <LifecycleStep label="Exit pending" />
                </div>
                <button
                  className="outline paper-close"
                  onClick={() => void closePosition(position.id)}
                  disabled={closingPosition !== ''}
                >
                  {closingPosition === position.id ? 'Closing…' : 'Close position'}
                </button>
              </article>;
            })}
          </div>
        )}
      </section>

      <section className="panel paper-section">
        <div className="panel-title">
          <div><small>BUY / SELL PAPER ORDER EVENTS</small><h2>Trade and order history</h2></div>
          <span className="pill neutral">{orders.length} EVENTS</span>
        </div>
        {orders.length === 0 ? (
          <div className="paper-empty">No paper orders recorded yet.</div>
        ) : (
          <div className="paper-table-wrap">
            <table className="paper-table">
              <thead><tr>
                <th>Time</th><th>Instrument</th><th>Action</th><th>Qty</th>
                <th>Fill price</th><th>Charges</th><th>Slippage</th><th>Status / reason</th><th>Lifecycle</th>
              </tr></thead>
              <tbody>
                {orders.map((order) => (
                  <tr key={order.id}>
                    <td>{dateTime(order.timestamp)}</td>
                    <td><b>{order.symbol}</b></td>
                    <td className={order.side === 'BUY' ? 'good' : 'bad'}>{order.side}</td>
                    <td>{order.quantity}</td>
                    <td>{money(order.executionPrice ?? order.requestedPrice)}</td>
                    <td>{money(order.charges)}</td>
                    <td>{order.executionPrice == null || order.requestedPrice == null
                      ? '—'
                      : money(Math.abs(order.executionPrice - order.requestedPrice) * order.quantity)}</td>
                    <td>
                      <span className={`pill ${order.status === 'FILLED' ? 'good' : order.status === 'REJECTED' ? 'sell' : 'neutral'}`}>
                        {order.status}
                      </span>
                      {order.rejectionReason && <small className="paper-reason">{order.rejectionReason}</small>}
                    </td>
                    <td>{order.side === 'BUY' ? 'Entry / add' : 'Exit / reduce'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="paper-disclaimer">
          Orders are the persisted execution events. The current ledger does not yet store a per-order exit reason,
          peak holding price, or realized P&amp;L allocation for partial closes.
        </p>
      </section>

      <section className="panel paper-section">
        <div className="panel-title">
          <div><small>WHY IT TRADED — OR DID NOT</small><h2>Automation decision log</h2></div>
          <span className="pill neutral">{cycles.length} RECENT CYCLES</span>
        </div>
        {cycles.length === 0 ? (
          <div className="paper-empty">No automation cycles recorded yet.</div>
        ) : (
          <div className="paper-cycle-list">
            {cycles.map((cycle) => {
              const order = cycle.paperOrderId ? orderById.get(cycle.paperOrderId) : undefined;
              return (
                <details className="paper-cycle" key={cycle.id}>
                  <summary>
                    <time>{dateTime(cycle.timestamp)}</time>
                    <b>{cycle.direction ?? 'NO CLEAR EDGE'}</b>
                    <span className={`pill ${cycle.status === 'TRADE_SUBMITTED' ? 'good' : cycle.status === 'ERROR' ? 'sell' : 'neutral'}`}>
                      {cycle.status}
                    </span>
                    <span>{cycle.optionType ? `${cycle.optionType} ${cycle.strike ?? ''} · ${cycle.expiry ?? 'expiry unknown'}` : 'No contract selected'}</span>
                    <span className="paper-cycle-reason">{cycle.reason}</span>
                  </summary>
                  <div className="paper-cycle-detail">
                    <Fact label="Signal" value={cycle.direction ?? 'NO TRADE'} />
                    <Fact label="Signal strength" value={cycle.signalStrength == null ? 'Not recorded' : `${cycle.signalStrength}%`} />
                    <Fact label="Selected contract" value={cycle.optionType ? `${cycle.optionType} ${cycle.strike ?? ''} · ${cycle.expiry ?? 'expiry not recorded'}` : 'None'} />
                    <Fact label="Decision reason" value={cycle.reason || 'Not recorded'} />
                    <Fact label="Paper order" value={order ? `${order.side} ${order.quantity} · ${order.status}` : cycle.paperOrderId ?? 'No order created'} />
                    {order && <Fact label="Fill / charges" value={`${money(order.executionPrice)} / ${money(order.charges)}`} />}
                  </div>
                  <p className="paper-disclaimer">
                    Persisted cycle audit contains the signal summary, chosen contract, outcome, reason, and order link.
                    Raw spot/futures snapshots and individual liquidity-filter pass/fail details are not currently stored.
                  </p>
                </details>
              );
            })}
          </div>
        )}
      </section>
    </main>
  );
}

function Metric({
  label,
  value,
  hint,
  tone,
}: {
  label: string;
  value: string;
  hint: string;
  tone?: number;
}) {
  const toneClass = tone == null ? '' : tone >= 0 ? 'good' : 'bad';
  return <div className="card"><span>{label}</span><b className={toneClass}>{value}</b><small>{hint}</small></div>;
}

function Fact({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><b>{value}</b></div>;
}

function LifecycleStep({ label, active = false }: { label: string; active?: boolean }) {
  return <span className={active ? 'active' : ''}><i>{active ? '✓' : '·'}</i>{label}</span>;
}

function linkedCycle(symbol: string, cycles: PaperAutomationAudit[], orders: PaperOrder[]) {
  return cycles.find((item) => {
    if (!item.paperOrderId || !item.optionType || item.strike == null) return false;
    return orders.some((order) => order.id === item.paperOrderId && order.symbol === symbol);
  });
}
