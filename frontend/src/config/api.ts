const configuredApiUrl = import.meta.env.VITE_API_URL?.trim();

export const API_BASE_URL = (configuredApiUrl || 'http://localhost:8080/api').replace(/\/+$/, '');

export function getMarketWebSocketUrl(): string {
  const configuredWebSocketUrl = import.meta.env.VITE_MARKET_WS_URL?.trim();
  if (configuredWebSocketUrl) return configuredWebSocketUrl;

  const url = new URL(API_BASE_URL, window.location.href);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = '/ws/market';
  url.search = '';
  return url.toString();
}