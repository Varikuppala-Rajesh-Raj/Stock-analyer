import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const [searchQuery, setSearchQuery] = useState('');

  type NavItem = {
    path: string;
    label: string;
    icon: string;
    isNew?: boolean;
  };

  const navItems: NavItem[] = [
    { path: '/', label: 'Overview', icon: '⌂' },
    { path: '/markets', label: 'Markets', icon: '⌁' },
    { path: '/watchlist', label: 'Watchlist', icon: '☆' },
    { path: '/strategies', label: 'Strategies', icon: '◈' },
{ path: '/nifty-options', label: 'NIFTY Options', icon: '⌁', isNew: true },
{ path: '/ml-lab', label: 'ML Lab', icon: '✦', isNew: true },
    { path: '/paper-trading', label: 'Paper trading', icon: '▣' },
    { path: '/live-feed', label: 'Live feed', icon: '⌁' },
    { path: '/alerts', label: 'Alerts', icon: '♧' },
    { path: '/journal', label: 'Journal', icon: '▤' },
  ];

  return (
    <div className="app">
      <aside>
        <div className="brand">
          <i>⌁</i>
          <b>nexus</b>
          <small>TRADE INTELLIGENCE</small>
        </div>
        <nav>
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
            <small>₹1,00,000 virtual capital</small>
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
          <div className="clock">
            <i />
            Market open • {new Date().toLocaleTimeString('en-IN')} IST
          </div>
          <button className="bell">♧</button>
          <button className="avatar">RK</button>
        </header>

        <Outlet />
      </div>
    </div>
  );
}
