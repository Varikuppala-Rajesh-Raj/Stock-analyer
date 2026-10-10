import { lazy, StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import './styles.css';

import { AppLayout } from './components/layout/AppLayout';

const NiftyPage = lazy(() => import('./pages/Overview').then((module) => ({ default: module.NiftyPage })));
const Markets = lazy(() => import('./pages/Markets').then((module) => ({ default: module.Markets })));
const Watchlist = lazy(() => import('./pages/Watchlist').then((module) => ({ default: module.Watchlist })));
const InstrumentDetail = lazy(() => import('./pages/InstrumentDetail').then((module) => ({ default: module.InstrumentDetail })));
const Strategies = lazy(() => import('./pages/Strategies').then((module) => ({ default: module.Strategies })));
const MlLab = lazy(() => import('./pages/MlLab').then((module) => ({ default: module.MlLab })));
const PaperTradingPage = lazy(() => import('./pages/PaperTradingPage').then((module) => ({ default: module.PaperTradingPage })));
const PaperDryRunPage = lazy(() => import('./pages/PaperDryRunPage').then((module) => ({ default: module.PaperDryRunPage })));
const LiveFeed = lazy(() => import('./pages/LiveFeed').then((module) => ({ default: module.LiveFeed })));
const Alerts = lazy(() => import('./pages/Alerts').then((module) => ({ default: module.Alerts })));
const Journal = lazy(() => import('./pages/Journal').then((module) => ({ default: module.Journal })));
const NiftyOptions = lazy(() => import('./pages/NiftyOptions').then((module) => ({ default: module.NiftyOptions })));
const Nifty50 = lazy(() => import('./pages/Nifty50').then((module) => ({ default: module.Nifty50 })));
const ManualTradingPage = lazy(() => import('./pages/ManualTradingPage').then((module) => ({ default: module.ManualTradingPage })));
const NiftyOptionDetailPage = lazy(() => import('./pages/NiftyOptionDetailPage').then((module) => ({ default: module.NiftyOptionDetailPage })));
const NiftyPremarket = lazy(() => import('./pages/NiftyPremarket').then((module) => ({ default: module.NiftyPremarket })));

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<div role="status">Loading page…</div>}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/" element={<Navigate to="/nifty" replace />} />
            <Route path="/nifty" element={<NiftyPage />} />
            <Route path="/overview" element={<Navigate to="/nifty" replace />} />
            <Route path="/nifty-50" element={<Nifty50 />} />
            <Route path="/nifty-premarket" element={<NiftyPremarket />} />
            <Route path="/markets" element={<Markets />} />
            <Route path="/watchlist" element={<Watchlist />} />
            <Route path="/instruments/:symbol" element={<InstrumentDetail />} />
            <Route path="/strategies" element={<Strategies />} />
            <Route path="/ml-lab" element={<MlLab />} />
            <Route path="/nifty-options" element={<NiftyOptions />} />
            <Route path="/nifty-options/contract" element={<NiftyOptionDetailPage />} />
            <Route path="/paper-trading" element={<PaperTradingPage />} />
            <Route path="/manual-trading" element={<ManualTradingPage />} />
            <Route path="/paper-dry-run" element={<PaperDryRunPage />} />
            <Route path="/live-feed" element={<LiveFeed />} />
            <Route path="/alerts" element={<Alerts />} />
            <Route path="/journal" element={<Journal />} />
            <Route path="*" element={<Navigate to="/" />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
