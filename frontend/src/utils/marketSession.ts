export type NseMarketSession = 'WEEKEND' | 'OUTSIDE_HOURS' | 'TRADING_WINDOW';

export function getNseMarketSession(date: Date = new Date()): NseMarketSession {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Kolkata',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  const weekday = values.weekday;
  if (weekday === 'Sat' || weekday === 'Sun') return 'WEEKEND';

  const minutes = Number(values.hour) * 60 + Number(values.minute);
  return minutes >= 9 * 60 + 15 && minutes < 15 * 60 + 30
    ? 'TRADING_WINDOW'
    : 'OUTSIDE_HOURS';
}

export function getNseMarketSessionLabel(date: Date = new Date()): string {
  const session = getNseMarketSession(date);
  if (session === 'WEEKEND') return 'Market closed · weekend';
  if (session === 'OUTSIDE_HOURS') return 'Market closed · outside NSE hours';
  return 'Trading window · holiday status not verified';
}
