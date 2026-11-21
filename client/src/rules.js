// Deterministic farm rules. They run offline, instantly, and are the source of every number Conuco shows.
// The AI explains these facts in plain words; it never invents them.
import { CROPS, PROBLEMS } from './crops.js';
import { settings } from './firebase.js';

const sum = (a) => a.reduce((s, x) => s + (Number(x) || 0), 0);
const round1 = (n) => Math.round(n * 10) / 10;

/**
 * Weather → field decisions for the next 48 h.
 * wx = Open-Meteo response (hourly + daily, timezone=auto, past_days=1).
 */
export function analyseWeather(wx, crops = [], now = new Date()) {
  const h = wx?.hourly;
  if (!h?.time?.length) return null;
  const nowKey = localHourKey(now);
  let i0 = h.time.findIndex((t) => t >= nowKey);
  if (i0 < 0) i0 = 0;
  const hours = h.time.map((t, i) => ({
    t, hour: Number(t.slice(11, 13)), temp: h.temperature_2m[i], rh: h.relative_humidity_2m[i],
    rain: h.precipitation[i] ?? 0, prob: h.precipitation_probability?.[i] ?? 0, wind: h.wind_speed_10m[i] ?? 0,
    et0: h.et0_fao_evapotranspiration?.[i] ?? 0,
  }));
  const next48 = hours.slice(i0, i0 + 48);
  const past24 = hours.slice(Math.max(0, i0 - 24), i0);

  // Spray window: daylight, calm, dry for the next 6 h, not too hot, leaves not soaked.
  const maxWind = settings.sprayMaxWindKmh;
  const okHour = (x, j) => {
    const after = next48.slice(j, j + 6);
    return x.hour >= 6 && x.hour <= 18 && x.wind <= maxWind && x.temp >= 10 && x.temp <= 30 && x.rh < 92
      && sum(after.map((a) => a.rain)) < 0.3 && Math.max(...after.map((a) => a.prob)) < 40;
  };
  const windows = [];
  let cur = null;
  next48.forEach((x, j) => {
    if (okHour(x, j)) {
      if (!cur) cur = { start: x.t, end: x.t, hours: 0, maxWind: 0, maxTemp: 0 };
      cur.end = x.t; cur.hours += 1; cur.maxWind = Math.max(cur.maxWind, x.wind); cur.maxTemp = Math.max(cur.maxTemp, x.temp);
    } else if (cur) { if (cur.hours >= 2) windows.push(cur); cur = null; }
  });
  if (cur && cur.hours >= 2) windows.push(cur);
  const nowH = next48[0];
  const blockers = [];
  if (nowH) {
    if (nowH.wind > maxWind) blockers.push({ code: 'wind', value: round1(nowH.wind), limit: maxWind });
    const rain6 = sum(next48.slice(0, 6).map((a) => a.rain));
    if (rain6 >= 0.3) blockers.push({ code: 'rain', value: round1(rain6) });
    if (nowH.temp > 30) blockers.push({ code: 'heat', value: round1(nowH.temp) });
    if (nowH.hour < 6 || nowH.hour > 18) blockers.push({ code: 'dark' });
  }

  // Irrigation: crop water use (ET0 × kc) against effective rain (80 %) over the next 3 days.
  const kc = crops.length ? Math.max(...crops.map((c) => CROPS[c]?.kc || 1)) : 1;
  const d = wx.daily || {};
  const di0 = Math.max(0, (d.time || []).findIndex((t) => t >= nowKey.slice(0, 10)));
  const days = (d.time || []).map((t, i) => ({
    date: t, tmax: d.temperature_2m_max?.[i], tmin: d.temperature_2m_min?.[i], rain: d.precipitation_sum?.[i] ?? 0,
    prob: d.precipitation_probability_max?.[i] ?? 0, et0: d.et0_fao_evapotranspiration?.[i] ?? 0,
    wind: d.wind_speed_10m_max?.[i] ?? 0, code: d.weather_code?.[i] ?? 0,
  })).slice(di0, di0 + 5);
  const d3 = days.slice(0, 3);
  const cropUse = round1(sum(d3.map((x) => x.et0)) * kc);
  const rainEff = round1(sum(d3.map((x) => x.rain)) * 0.8);
  const deficit = round1(cropUse - rainEff);
  const irrigation = { cropUse, rainEff, deficit, kc, advice: deficit > 12 ? 'irrigate' : deficit > 5 ? 'light' : 'skip' };

  // Fungal disease pressure: hours with RH ≥ 90 % at 15–30 °C (a leaf-wetness proxy), last 24 h + next 48 h.
  const wet = (x) => x.rh >= 90 && x.temp >= 15 && x.temp <= 30;
  const wetHours = past24.filter(wet).length + next48.filter(wet).length;
  const disease = { wetHours, level: wetHours >= 14 ? 'high' : wetHours >= 6 ? 'medium' : 'low' };

  const hot = days.filter((x) => x.tmax >= 35);
  const storm = days.find((x) => x.rain >= 30);

  return {
    updated: wx._fetchedAt || Date.now(),
    now: nowH ? { temp: round1(nowH.temp), rh: Math.round(nowH.rh), wind: round1(nowH.wind), prob: nowH.prob } : null,
    spray: { windows: windows.slice(0, 3), blockers, okNow: blockers.length === 0, maxWind },
    irrigation,
    disease,
    heat: hot.length ? { days: hot.length, maxT: round1(Math.max(...hot.map((x) => x.tmax))) } : null,
    storm: storm ? { date: storm.date, mm: round1(storm.rain) } : null,
    days,
  };
}

function localHourKey(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:00`;
}

/** Turns the analysis into to-dos for today (stable ids so they are not duplicated). */
export function weatherTasks(a, day) {
  if (!a) return [];
  const out = [];
  if (a.spray.windows[0]) out.push({ id: `w-spray-${day}`, code: 'spray_window', data: a.spray.windows[0] });
  if (a.irrigation.advice === 'irrigate') out.push({ id: `w-irr-${day}`, code: 'irrigate', data: a.irrigation });
  if (a.disease.level === 'high') out.push({ id: `w-scout-${day}`, code: 'scout_fungus', data: a.disease });
  if (a.storm) out.push({ id: `w-storm-${day}`, code: 'drainage', data: a.storm });
  if (a.heat) out.push({ id: `w-heat-${day}`, code: 'heat', data: a.heat });
  return out;
}

// ---------- Symptom guide (no AI, works on every phone) ----------

export const SYMPTOMS = ['yellow', 'spots', 'powder', 'streaks', 'wilt', 'holes', 'curl', 'mosaic', 'rot', 'insects'];
const SYMPTOM_HINTS = {
  yellow: ['nutrient', 'water_stress', 'mosaic_virus'],
  spots: ['early_blight', 'anthracnose', 'angular_leaf_spot', 'bacterial_spot', 'brown_spot'],
  powder: ['powdery_mildew', 'rust', 'coffee_rust'],
  streaks: ['black_sigatoka', 'blast'],
  wilt: ['fusarium_wilt', 'wilt', 'water_stress', 'root_rot'],
  holes: ['fall_armyworm', 'leaf_miner', 'pod_borer'],
  curl: ['leaf_curl', 'whitefly', 'mites', 'thrips'],
  mosaic: ['mosaic_virus', 'golden_mosaic'],
  rot: ['late_blight', 'black_pod', 'heart_rot', 'root_rot', 'monilia'],
  insects: ['whitefly', 'thrips', 'mites', 'fall_armyworm', 'weevil', 'berry_borer'],
};
/** Ranks likely problems for a crop from the ticked symptoms. */
export function symptomGuide(crop, ticked) {
  const watch = new Set(CROPS[crop]?.watch || []);
  const score = new Map();
  ticked.forEach((s) => (SYMPTOM_HINTS[s] || []).forEach((p, i) => {
    score.set(p, (score.get(p) || 0) + (3 - Math.min(i, 2)) + (watch.has(p) ? 3 : 0));
  }));
  return [...score.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3)
    .map(([key, s]) => ({ key, cat: PROBLEMS[key]?.cat || 'unknown', score: s }));
}

// ---------- Safety ----------

// Conuco never gives chemical doses: they depend on the registered product, the label and local law.
const DOSE_RE = /\b\d+(?:[.,]\d+)?\s*(?:ml|mL|cc|cm3|g|gr|gramos?|grams?|kg|l|lt|litros?|liters?|litres?|oz|onzas?|cucharad[ao]s?|tbsp|tsp|spoons?)\b\s*(?:\/|por|per|en|in|de|of|each|cada)\s*[\wáéíóú]+/giu;
const BANNED_RE = /\b(paraquat|endosulf[aá]n|metamidof[oó]s|methamidophos|carbofur[aá]n|monocrotof[oó]s|monocrotophos|aldicarb|DDT|lindan[oe])\b/giu;

/** Removes doses and banned chemicals from AI text. Returns {text, changed}. */
export function safeText(text, lang) {
  let changed = false;
  let t = String(text || '');
  t = t.replace(DOSE_RE, () => { changed = true; return lang === 'es' ? '(dosis: siga la etiqueta)' : '(dose: follow the label)'; });
  t = t.replace(BANNED_RE, () => { changed = true; return lang === 'es' ? '[producto prohibido]' : '[banned product]'; });
  return { text: t, changed };
}
