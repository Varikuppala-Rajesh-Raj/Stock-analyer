import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { getNseMarketSession, getNseMarketSessionLabel } from '../../utils/marketSession';
import './AppLayout.css';

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const [searchQuery, setSearchQuery] = useState('');
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 15000);
    return () => window.clearInterval(timer);
  }, []);
  const marketSession = getNseMarketSession(now);

  type NavItem = {
    path: string;
    label: string;
    icon: string;
    isNew?: boolean;
  };

  const navItems: NavItem[] = [
    { path: '/nifty', label: 'NIFTY', icon: '⌂' },
    { path: '/nifty-50', label: 'NIFTY 50', icon: '▦' },
    { path: '/nifty-premarket', label: 'NIFTY pre-market', icon: '◷', isNew: true },
    { path: '/markets', label: 'Markets', icon: '⌁' },
    { path: '/watchlist', label: 'Watchlist', icon: '☆' },
    { path: '/strategies', label: 'Strategies', icon: '◈' },
{ path: '/nifty-options', label: 'NIFTY Options', icon: '⌁', isNew: true },
{ path: '/ml-lab', label: 'ML Lab', icon: '✦', isNew: true },
    { path: '/paper-trading', label: 'Paper trading', icon: '▣' },
    { path: '/manual-trading', label: 'Manual trading', icon: '⇄', isNew: true },
    { path: '/paper-dry-run', label: 'Historical dry run', icon: '↻', isNew: true },
    { path: '/live-feed', label: 'Live feed', icon: '⌁' },
    { path: '/alerts', label: 'Alerts', icon: '♧' },
    { path: '/journal', label: 'Journal', icon: '▤' },
  ];

  return (
    <div className="app">
      <aside className="app-sidebar">
        <div className="brand">
          <i>⌁</i>
          <b>nexus</b>
          <small>TRADE INTELLIGENCE</small>
        </div>
        <nav className="sidebar-nav" aria-label="Main navigation">
          {navItems.map((item) => (
            <button
              key={item.path}
              className={location.pathname === item.path ? 'active' : ''}
              onClick={() => navigate(item.path)}
            >
              <span>{item.icon}</span>
              {item.label}
              {item.isNew && <em>NEW</em>}
            </button>
          ))}
        </nav>
        <div className="side-bottom">
          <div className="paper">
            <b>◉ Paper mode</b>
            <small>Virtual ledger · paper only</small>
          </div>
          <button>⚙ Settings</button>
          <button>? Support</button>
        </div>
      </aside>

      <div className="content">
        <header>
          <div className="mobile-logo">⌁ nexus</div>
          <div className="search">
            ⌕
            <input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search NSE stocks, indices, or instruments"
            />
            <kbd>⌘ K</kbd>
          </div>
          <div className="clock market-clock">
            <i style={{
              background: marketSession === 'TRADING_WINDOW' ? '#e9b85f' : '#ff768f',
              boxShadow: 'none',
            }} />
            {getNseMarketSessionLabel(now)} • {now.toLocaleTimeString('en-IN', { timeZone: 'Asia/Kolkata' })} IST
          </div>
          <button className="bell">♧</button>
          <button className="avatar">RK</button>
        </header>

        <Outlet />
      </div>
    </div>
  );
}
