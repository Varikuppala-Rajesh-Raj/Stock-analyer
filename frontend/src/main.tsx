import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import './styles.css';

// Layout
import { AppLayout } from './components/layout/AppLayout';

// Pages
import { NiftyPage } from './pages/Overview';
import { Markets } from './pages/Markets';
import { Watchlist } from './pages/Watchlist';
import { InstrumentDetail } from './pages/InstrumentDetail';
import { Strategies } from './pages/Strategies';
import { MlLab } from './pages/MlLab'   ;
import { PaperTrading } from './pages/PaperTrading';
import { LiveFeed } from './pages/LiveFeed';
import { Alerts } from './pages/Alerts';
import { Journal } from './pages/Journal';
import { NiftyOptions } from './pages/NiftyOptions';
import { Nifty50 } from './pages/Nifty50';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/nifty" replace />} />
          <Route path="/nifty" element={<NiftyPage />} />
          <Route path="/overview" element={<Navigate to="/nifty" replace />} />
          <Route path="/nifty-50" element={<Nifty50 />} />
          <Route path="/markets" element={<Markets />} />
          <Route path="/watchlist" element={<Watchlist />} />
          <Route path="/instruments/:symbol" element={<InstrumentDetail />} />
          <Route path="/strategies" element={<Strategies />} />
          <Route path="/ml-lab" element={<MlLab />} />
          <Route path="/nifty-options" element={<NiftyOptions />} />
          <Route path="/paper-trading" element={<PaperTrading />} />
          <Route path="/live-feed" element={<LiveFeed />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route path="/journal" element={<Journal />} />
          <Route path="*" element={<Navigate to="/" />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
