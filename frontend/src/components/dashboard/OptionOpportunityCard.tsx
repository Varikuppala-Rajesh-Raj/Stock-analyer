import type { OptionOpportunity } from '../../services/api';

type Props = {
  opportunity: OptionOpportunity | null;
  loading: boolean;
  error: string;
};

const money = (value?: number | null) =>
  value == null
    ? '—'
    : new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
      }).format(value);

export function OptionOpportunityCard({
  opportunity,
  loading,
  error,
}: Props) {
  if (loading && !opportunity) {
    return (
      <section className="panel">
        <div className="panel-title">
          <div>
            <small>AI OPTIONS ENGINE</small>
            <h2>Option Opportunity</h2>
          </div>
        </div>

        <p className="api-state">
          Analyzing NIFTY options…
        </p>
      </section>
    );
  }

  if (error && !opportunity) {
    return (
      <section className="panel">
        <div className="panel-title">
          <div>
            <small>AI OPTIONS ENGINE</small>
            <h2>Option Opportunity</h2>
          </div>
        </div>

        <p className="api-state error">
          {error}
        </p>
      </section>
    );
  }

  if (!opportunity) {
    return null;
  }

  const option = opportunity.recommendedOption;

  return (
    <section className="panel">
      <div className="panel-title">
        <div>
          <small>AI OPTIONS ENGINE</small>
          <h2>NIFTY Option Opportunity</h2>
        </div>

        <span
          className={`pill ${
            opportunity.recommendation === 'CE'
              ? 'buy'
              : opportunity.recommendation === 'PE'
              ? 'sell'
              : ''
          }`}
        >
          {opportunity.recommendation}
        </span>
      </div>

      <div className="quote-grid">
        <small>
          NIFTY {opportunity.spot.toFixed(2)}
        </small>

        <small>
          ATM {opportunity.atmStrike}
        </small>

        <small>
          Expected move{' '}
          {opportunity.niftyContext.expectedMovePoints.toFixed(2)} pts
        </small>

        <small>
          Direction{' '}
          {opportunity.niftyContext.direction}
        </small>
      </div>

      {option ? (
        <div className="model-facts">
          <span>
            Option
            <b>
              {option.type} {option.strike}
            </b>
          </span>

          <span>
            LTP
            <b>{money(option.ltp)}</b>
          </span>

          <span>
            Predicted return
            <b>
              {option.predictedOptionReturnPercent.toFixed(2)}%
            </b>
          </span>

          <span>
            Score
            <b>{option.optionScore.toFixed(2)}</b>
          </span>

          <span>
            Delta
            <b>{option.delta.toFixed(3)}</b>
          </span>

          <span>
            IV
            <b>{option.iv.toFixed(2)}%</b>
          </span>

          <span>
            OI
            <b>
              {new Intl.NumberFormat('en-IN', {
                notation: 'compact',
              }).format(option.oi)}
            </b>
          </span>

          <span>
            Volume
            <b>
              {new Intl.NumberFormat('en-IN', {
                notation: 'compact',
              }).format(option.volume)}
            </b>
          </span>
        </div>
      ) : (
        <div>
          <strong>NO TRADE</strong>
          <p>{opportunity.decisionReason}</p>
        </div>
      )}
    </section>
  );
}