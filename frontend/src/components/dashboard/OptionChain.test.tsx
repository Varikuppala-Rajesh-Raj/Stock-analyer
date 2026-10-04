// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../services/api';
import { OptionChain } from './OptionChain';

Object.defineProperty(globalThis, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, value: true });

vi.mock('../../services/api', () => ({
  api: { niftyOptionChain: vi.fn() },
}));

function Destination() {
  const location = useLocation();
  return <output data-testid="destination">{location.pathname}{location.search}</output>;
}

const option = (key: string, ltp: number) => ({
  instrument_key: key,
  market_data: {
    ltp, volume: 1000, oi: 2500, close_price: ltp - 10,
    bid_price: ltp - 1, bid_qty: 20, ask_price: ltp + 1, ask_qty: 25, prev_oi: 2400,
  },
  option_greeks: { vega: 1, theta: -2, gamma: 0.01, delta: 0.5, iv: 18, pop: 0.6 },
});

describe('OptionChain', () => {
  let root: Root;
  let host: HTMLDivElement;

  beforeEach(() => {
    host = document.createElement('div');
    document.body.append(host);
    root = createRoot(host);
    vi.mocked(api.niftyOptionChain).mockResolvedValue({
      status: 'success',
      data: [{
        expiry: '2026-10-08',
        pcr: 1,
        strike_price: 23000,
        underlying_key: 'NSE_INDEX|Nifty 50',
        underlying_spot_price: 23000,
        call_options: option('NSE_FO|CALL', 100),
        put_options: option('NSE_FO|PUT', 120),
      }],
    });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.clearAllMocks();
  });

  it('opens the selected PE contract detail with its identity and expiry', async () => {
    await act(async () => {
      root.render(
        <MemoryRouter initialEntries={['/nifty-options']}>
          <Routes>
            <Route path="/nifty-options" element={<OptionChain expiry="2026-10-08" expiries={['2026-10-08']} onExpiryChange={vi.fn()} />} />
            <Route path="/nifty-options/contract" element={<Destination />} />
          </Routes>
        </MemoryRouter>,
      );
      await Promise.resolve();
    });

    const put = host.querySelector<HTMLButtonElement>('button[aria-label="Open NIFTY 23000 put option"]');
    await act(async () => put?.dispatchEvent(new MouseEvent('click', { bubbles: true })));

    expect(host.querySelector('[data-testid="destination"]')?.textContent)
      .toContain('/nifty-options/contract?expiry=2026-10-08&type=PE&strike=23000&key=NSE_FO%7CPUT');
  });
});
