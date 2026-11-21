// Weather from Open-Meteo (free, no key). Only a coarse position (2 decimals ≈ 1 km) is sent.
// The last forecast is kept on the device so the Today page still works with no signal.
const KEY = 'conuco.wx.v1';
const FRESH_MS = 60 * 60 * 1000;

export const coarse = (n, d = 2) => Math.round(Number(n) * 10 ** d) / 10 ** d;

export function cachedWeather(lat, lng) {
  try {
    const c = JSON.parse(localStorage.getItem(KEY) || 'null');
    if (c && c.lat === coarse(lat) && c.lng === coarse(lng)) return c.data;
  } catch { /* storage blocked */ }
  return null;
}

export async function getWeather(lat, lng, { force = false } = {}) {
  const la = coarse(lat);
  const lo = coarse(lng);
  const cached = cachedWeather(la, lo);
  if (cached && !force && Date.now() - cached._fetchedAt < FRESH_MS) return { data: cached, from: 'cache' };
  if (!navigator.onLine) {
    if (cached) return { data: cached, from: 'offline-cache' };
    throw new Error('offline');
  }
  const url = 'https://api.open-meteo.com/v1/forecast?' + new URLSearchParams({
    latitude: la, longitude: lo, timezone: 'auto', past_days: '1', forecast_days: '5', wind_speed_unit: 'kmh',
    hourly: 'temperature_2m,relative_humidity_2m,precipitation,precipitation_probability,wind_speed_10m,et0_fao_evapotranspiration',
    daily: 'temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,et0_fao_evapotranspiration,wind_speed_10m_max,weather_code',
  });
  try {
    const r = await fetch(url, { referrerPolicy: 'no-referrer' });
    if (!r.ok) throw new Error(`weather ${r.status}`);
    const data = await r.json();
    data._fetchedAt = Date.now();
    try { localStorage.setItem(KEY, JSON.stringify({ lat: la, lng: lo, data })); } catch { /* full */ }
    return { data, from: 'network' };
  } catch (e) {
    if (cached) return { data: cached, from: 'offline-cache' };
    throw e;
  }
}

/** Place search for people who don't want to share GPS. Returns [{name, admin, country, lat, lng}]. */
export async function searchPlace(q, lang) {
  const url = 'https://geocoding-api.open-meteo.com/v1/search?' + new URLSearchParams({ name: q, count: '6', language: lang === 'es' ? 'es' : 'en', format: 'json' });
  const r = await fetch(url, { referrerPolicy: 'no-referrer' });
  if (!r.ok) return [];
  const j = await r.json();
  return (j.results || []).map((p) => ({
    name: p.name, admin: p.admin1 || '', country: p.country_code || '', lat: coarse(p.latitude), lng: coarse(p.longitude),
  }));
}

/** WMO weather code → simple key for icons and words. */
export function sky(code) {
  if (code === 0) return 'clear';
  if (code <= 3) return 'cloudy';
  if (code <= 48) return 'fog';
  if (code <= 67 || (code >= 80 && code <= 82)) return 'rain';
  if (code >= 95) return 'storm';
  return 'cloudy';
}
