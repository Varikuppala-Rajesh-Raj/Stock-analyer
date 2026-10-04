import { describe, expect, it } from 'vitest';
import { getNseMarketSession } from './marketSession';

describe('getNseMarketSession', () => {
  it('identifies the regular NSE weekday trading window using India time', () => {
    expect(getNseMarketSession(new Date('2026-10-02T04:00:00Z'))).toBe('TRADING_WINDOW');
  });

  it('does not call outside-hours weekdays open', () => {
    expect(getNseMarketSession(new Date('2026-10-02T18:27:00Z'))).toBe('OUTSIDE_HOURS');
  });

  it('identifies weekends as closed', () => {
    expect(getNseMarketSession(new Date('2026-10-03T06:00:00Z'))).toBe('WEEKEND');
  });
});
