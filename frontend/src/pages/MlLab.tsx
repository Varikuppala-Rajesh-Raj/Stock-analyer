import { useEffect, useState } from 'react';
import { useNiftyPrediction, useTrainNifty,useModelStatus } from '../services/hooks';
import { api } from '../services/api';

export function MlLab() {
  const [train, setTrain] = useState('60');
  const [test, setTest] = useState('20');

  const {
    model,
    loading: modelLoading,
    error: modelError,
    refetch: refetchModel
  } = useModelStatus(30000);

const {
  prediction,
  loading: predictionLoading,
  error: predictionError,
  refetch: refetchPrediction
} = useNiftyPrediction(15, 0.2, 0);

  const {
    train: trainNifty,
    result: trainingResult,
    loading: training,
    error: trainError
  } = useTrainNifty();
  const [history, setHistory] = useState<Record<string, unknown>[]>([]);
  const [historyError, setHistoryError] = useState('');

  useEffect(() => {
    let active = true;
    void api.predictionHistory()
      .then(rows => { if (active) setHistory(rows.slice(0, 8)); })
      .catch(error => { if (active) setHistoryError(error instanceof Error ? error.message : 'Prediction history is unavailable.'); });
    return () => { active = false; };
  }, [prediction]);
const runTraining = async () => {
  try {
    await trainNifty(Number(train));
    await refetchModel();
  } catch {
    // Error is already stored by the hook
  }
};
const runPrediction = async () => {
  try {
    await refetchPrediction();
  } catch {
    // Error is already stored by the hook
  }
};


  return (
    <main>
      <div className="heading">
        <div>
          <small>ML RESEARCH WORKBENCH</small>
          <h1>Walk-forward validation</h1>
          <p>Train on history, test the next unseen period, then roll forward to measure real predictive quality.</p>
        </div>
        <span className="pill good">MODEL READY</span>
      </div>

      <div className="walk">
        <section className="panel">
          <div className="panel-title">
            <div>
              <small>EXPERIMENT SETUP</small>
              <h2>Rolling-window study</h2>
            </div>
            <span className="pill">NIFTY 50</span>
          </div>
          {training && (
  <section className="panel">
    <h2>Training model...</h2>
    <p>
      Fetching historical NIFTY M5 candles and training the ML model.
    </p>
  </section>
)}

{trainError && (
  <section className="panel">
    <b>Training failed</b>
    <p>{trainError}</p>
  </section>
)}

{trainingResult && (
  <section className="panel">
    <div className="panel-title">
      <div>
        <small>TRAINING RESULT</small>
        <h2>{trainingResult.modelVersion}</h2>
      </div>

      <span className="pill good">
        {trainingResult.status}
      </span>
    </div>

    <div className="stats">
      <div className="card">
        <span>Accuracy</span>
        <b>
          {(trainingResult.accuracy * 100).toFixed(2)}%
        </b>
      </div>

      <div className="card">
        <span>Macro F1</span>
        <b>
          {(trainingResult.f1Macro * 100).toFixed(2)}%
        </b>
      </div>

      <div className="card">
        <span>Training rows</span>
        <b>{trainingResult.trainingRows}</b>
      </div>

      <div className="card">
        <span>Validation rows</span>
        <b>{trainingResult.validationRows}</b>
      </div>
    </div>

    <small>
      Validation: {trainingResult.validationMethod}
      {' · '}
      Horizon: {trainingResult.horizonMinutes} minutes
    </small>
  </section>
)}
          <div className="fields">
            <label>Instrument
              <select><option>NIFTY 50</option><option>BANKNIFTY</option></select>
            </label>
            <label>Timeframe
              <select><option>5 minutes</option><option>15 minutes</option></select>
            </label>
            <label>Training window
              <input value={train} onChange={e => setTrain(e.target.value)} />
              <small>Trading days</small>
            </label>
            <label>Forward test window
              <input value={test} onChange={e => setTest(e.target.value)} />
              <small>Trading days</small>
            </label>
            <label>Step size
              <select><option>5 trading days</option><option>10 trading days</option></select>
            </label>
            <label>Prediction horizon
              <select><option>15 minutes</option><option>30 minutes</option></select>
            </label>
          </div>
          <div className="range">
            <span>Movement threshold</span>
            <input type="range" min="0.05" max="0.75" step="0.05" defaultValue="0.2" />
            <b>0.20%</b>
          </div>
          <button
  className="primary wide"
  onClick={runTraining}
  disabled={training}
>
  {training
    ? 'Training NIFTY model...'
    : 'Train NIFTY model v1.0 →'}
</button>
<button
  className="outline wide"
  onClick={runPrediction}
  disabled={predictionLoading}
>
  {predictionLoading
    ? 'Testing NIFTY prediction...'
    : 'Test NIFTY prediction →'}
</button>
        </section>

{predictionError && (
  <section className="panel">
    <b>Prediction failed</b>
    <p>{predictionError}</p>
  </section>
)}

{prediction && (
  <section className="panel">
    <div className="panel-title">
      <div>
        <small>MODEL PREDICTION</small>
        <h2>NIFTY</h2>
      </div>

      <span className="pill">
        {String(prediction.confidence ?? 'N/A')}
      </span>
    </div>

    <div className="stats">
      <div className="card">
        <span>Prediction</span>
        <b>{String(prediction.prediction ?? 'N/A')}</b>
      </div>

      <div className="card">
        <span>Probability</span>
        <b>
          {prediction.probability != null
            ? `${(Number(prediction.probability) * 100).toFixed(2)}%`
            : 'N/A'}
        </b>
      </div>

      <div className="card">
        <span>NIFTY Price</span>
        <b>
          {prediction.niftyPrice != null
            ? `₹${Number(prediction.niftyPrice).toFixed(2)}`
            : 'N/A'}
        </b>
      </div>

      <div className="card">
        <span>Decision</span>
        <b>{String(prediction.decision ?? 'N/A')}</b>
      </div>
    </div>

    <small>
      Horizon: {String(prediction.horizonMinutes ?? 15)} minutes
      {' · '}
      Model: {String(prediction.modelVersion ?? 'N/A')}
    </small>
  </section>
)}
        {prediction && (
          <section className="panel">
            <div className="panel-title">
              <div>
                <small>FORECAST SNAPSHOT</small>
                <h2>Next {String(prediction.horizonMinutes ?? 15)} minutes</h2>
              </div>
              <span className={`pill ${String(prediction.prediction ?? '').toLowerCase()}`}>
                {String(prediction.prediction ?? 'UNAVAILABLE')}
              </span>
            </div>
            <div className="stats">
              <div className="card"><span>UP probability</span><b>{prediction.probabilities?.UP != null ? `${(Number(prediction.probabilities.UP) * 100).toFixed(1)}%` : '—'}</b></div>
              <div className="card"><span>DOWN probability</span><b>{prediction.probabilities?.DOWN != null ? `${(Number(prediction.probabilities.DOWN) * 100).toFixed(1)}%` : '—'}</b></div>
              <div className="card"><span>Expected return</span><b>{prediction.predictedReturnPercent != null ? `${Number(prediction.predictedReturnPercent).toFixed(2)}%` : '—'}</b></div>
              <div className="card"><span>Model version</span><b>{String(prediction.modelVersion ?? '—')}</b></div>
            </div>
          </section>
        )}
        <section className="panel backend-table">
          <div className="panel-title">
            <div><small>PREDICTION HISTORY</small><h2>Recent forecasts</h2></div>
            <span className="pill">EVALUATION</span>
          </div>
          {historyError ? <p className="api-state error">{historyError}</p> : history.length === 0 ? <p className="api-state">No persisted predictions are available yet.</p> : history.map((item, index) => (
            <div className="api-row" key={String(item.id ?? index)}>
              <b>{String(item.prediction ?? '—')}</b>
              <span>{String(item.horizonMinutes ?? '—')} min</span>
              <span>{item.predictedReturnPercent != null ? `${Number(item.predictedReturnPercent).toFixed(2)}%` : '—'}</span>
              <small>{item.outcomeCorrect == null ? 'Awaiting outcome' : item.outcomeCorrect ? 'Correct' : 'Incorrect'}</small>
            </div>
          ))}
        </section>
        <section className="panel note">
          <small>WHY THIS MATTERS</small>
          <h2>Test only what the model could have known.</h2>
          <ol>
            <li><b>Train</b> on the first {train} days.</li>
            <li><b>Predict</b> the following {test} unseen days.</li>
            <li><b>Compare</b> prediction and actual outcome.</li>
            <li><b>Roll</b> forward and aggregate all periods.</li>
          </ol>
          <div>No future data is allowed in features, labels, calibration, or model selection.</div>
        </section>
      </div>

      <section className="stats">
        <div className="card">
          <span>Model status</span>
          <b>{modelLoading ? 'Loading...' : model?.status || 'UNKNOWN'}</b>
          <small>{model?.lastTrained ? `Last trained: ${model.lastTrained}` : 'Not trained'}</small>
        </div>
        <div className="card">
          <span>Forward periods</span>
          <b>12</b>
          <small>60D train / 20D test</small>
        </div>
        <div className="card">
          <span>Directional accuracy</span>
          <b>{model?.accuracy ? `${(model.accuracy * 100).toFixed(1)}%` : 'N/A'}</b>
          <small>Out-of-sample only</small>
        </div>
        <div className="card">
          <span>Average confidence</span>
          <b>{model?.confidence ? `${(model.confidence * 100).toFixed(1)}%` : 'N/A'}</b>
          <small>Calibration tracked</small>
        </div>
      </section>
    </main>
  );
}
