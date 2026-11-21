// Conuco data in Firestore. Private farm data lives under users/{uid}; shared community data
// (market prices, outbreak reports) holds no names and only a coarse map cell (0.1° ≈ 11 km).
// All writes work offline; Firestore queues them and syncs later.
import {
  collection, doc, setDoc, addDoc, updateDoc, deleteDoc, getDoc, getDocs, onSnapshot, query, where, orderBy, limit,
  serverTimestamp, writeBatch,
} from 'firebase/firestore';
import { db } from './firebase.js';

const iso = (d) => d.toISOString().slice(0, 10);
export const today = () => iso(new Date());
export const daysAgo = (n) => iso(new Date(Date.now() - n * 86400000));

/** 0.1° cell: "18.4_-69.9". Shared data never carries a more precise position. */
export const cellOf = (lat, lng) => `${(Math.round(lat * 10) / 10).toFixed(1)}_${(Math.round(lng * 10) / 10).toFixed(1)}`;
export const cellCenter = (cell) => cell.split('_').map(Number);
export function distanceKm(a, b) {
  const R = 6371;
  const toR = (x) => (x * Math.PI) / 180;
  const dLat = toR(b[0] - a[0]);
  const dLng = toR(b[1] - a[1]);
  const s = Math.sin(dLat / 2) ** 2 + Math.cos(toR(a[0])) * Math.cos(toR(b[0])) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(s));
}

// ---------- Profile ----------
const userRef = (uid) => doc(db, 'users', uid);
export async function getProfile(uid) {
  try {
    const s = await getDoc(userRef(uid));
    return s.exists() ? s.data() : null;
  } catch {
    return null;
  }
}
export function saveProfile(uid, p) {
  const clean = {
    name: String(p.name || '').trim().slice(0, 40),
    lang: p.lang === 'es' ? 'es' : 'en',
    country: String(p.country || '').toUpperCase().slice(0, 2),
    place: String(p.place || '').slice(0, 60),
    lat: Math.round(Number(p.lat) * 100) / 100,
    lng: Math.round(Number(p.lng) * 100) / 100,
    crops: (p.crops || []).slice(0, 12),
    areaUnit: ['tarea', 'ha', 'acre', 'm2'].includes(p.areaUnit) ? p.areaUnit : 'ha',
    shareOutbreaks: p.shareOutbreaks !== false,
    updatedAt: serverTimestamp(),
  };
  return setDoc(userRef(uid), clean, { merge: true });
}

// ---------- Plots, log, diagnoses, tasks (private) ----------
const sub = (uid, name) => collection(db, 'users', uid, name);

export function watchPlots(uid, cb) {
  return onSnapshot(query(sub(uid, 'plots'), orderBy('createdAt', 'asc')), (s) => cb(s.docs.map((d) => ({ id: d.id, ...d.data() }))), () => cb([]));
}
export function addPlot(uid, p) {
  return addDoc(sub(uid, 'plots'), {
    name: String(p.name || '').trim().slice(0, 40) || 'Plot',
    crop: p.crop,
    area: Math.max(0, Math.min(1e7, Number(p.area) || 0)),
    planted: /^\d{4}-\d{2}-\d{2}$/.test(p.planted || '') ? p.planted : '',
    createdAt: serverTimestamp(),
  });
}
export const deletePlot = (uid, id) => deleteDoc(doc(db, 'users', uid, 'plots', id));

export const LOG_KINDS = ['plant', 'water', 'fertilize', 'spray', 'scout', 'weed', 'harvest', 'sell', 'other'];
export function watchLog(uid, cb, n = 60) {
  return onSnapshot(query(sub(uid, 'logs'), orderBy('date', 'desc'), limit(n)), { includeMetadataChanges: true },
    (s) => cb(s.docs.map((d) => ({ id: d.id, pending: d.metadata.hasPendingWrites, ...d.data() }))), () => cb([]));
}
export function addLog(uid, e) {
  return addDoc(sub(uid, 'logs'), {
    plotId: String(e.plotId || '').slice(0, 40),
    kind: LOG_KINDS.includes(e.kind) ? e.kind : 'other',
    note: String(e.note || '').trim().slice(0, 300),
    qty: Math.max(0, Math.min(1e7, Number(e.qty) || 0)),
    unit: String(e.unit || '').slice(0, 12),
    money: Math.max(0, Math.min(1e9, Number(e.money) || 0)),
    date: /^\d{4}-\d{2}-\d{2}$/.test(e.date || '') ? e.date : today(),
    via: ['voice', 'text', 'doctor', 'weather'].includes(e.via) ? e.via : 'text',
    createdAt: serverTimestamp(),
  });
}
export const deleteLog = (uid, id) => deleteDoc(doc(db, 'users', uid, 'logs', id));

export function watchDiagnoses(uid, cb, n = 20) {
  return onSnapshot(query(sub(uid, 'diagnoses'), orderBy('createdAt', 'desc'), limit(n)), (s) => cb(s.docs.map((d) => ({ id: d.id, ...d.data() }))), () => cb([]));
}
/** Saves the result only. The photo never leaves the phone and is never stored. */
export function addDiagnosis(uid, d) {
  return addDoc(sub(uid, 'diagnoses'), {
    crop: String(d.crop || 'other').slice(0, 20),
    problem: String(d.problem || '').slice(0, 80),
    problemKey: String(d.problemKey || 'other').slice(0, 30),
    category: d.category,
    confidence: Math.max(0, Math.min(1, Number(d.confidence) || 0)),
    severity: d.severity,
    source: d.source,
    secondOpinion: d.secondOpinion || 'none',
    actions: (d.actions || []).slice(0, 4).map((a) => String(a).slice(0, 200)),
    plotId: String(d.plotId || '').slice(0, 40),
    day: today(),
    createdAt: serverTimestamp(),
  });
}

export function watchTasks(uid, cb) {
  return onSnapshot(query(sub(uid, 'tasks'), where('day', '>=', daysAgo(1)), limit(30)), (s) => cb(s.docs.map((d) => ({ id: d.id, ...d.data() }))), () => cb([]));
}
/** Weather and doctor to-dos use stable ids, so the same advice is never added twice. */
export function upsertTask(uid, t) {
  return setDoc(doc(db, 'users', uid, 'tasks', String(t.id).slice(0, 60)), {
    code: String(t.code || 'custom').slice(0, 20),
    text: String(t.text || '').slice(0, 160),
    day: t.day || today(),
    done: Boolean(t.done),
    from: ['weather', 'doctor', 'user'].includes(t.from) ? t.from : 'user',
    updatedAt: serverTimestamp(),
  }, { merge: true });
}
export const setTaskDone = (uid, id, done) => updateDoc(doc(db, 'users', uid, 'tasks', id), { done: Boolean(done), updatedAt: serverTimestamp() });

// ---------- Community: market prices ----------
export function addPrice(uid, p) {
  return addDoc(collection(db, 'prices'), {
    uid,
    crop: p.crop,
    unit: p.unit,
    price: Math.round(Number(p.price) * 100) / 100,
    currency: String(p.currency || 'USD').slice(0, 3),
    market: String(p.market || '').trim().slice(0, 40),
    country: String(p.country || '').slice(0, 2),
    cell: p.cell,
    day: today(),
    createdAt: serverTimestamp(),
  });
}
export async function loadPrices(sinceDays) {
  const s = await getDocs(query(collection(db, 'prices'), where('day', '>=', daysAgo(sinceDays)), orderBy('day', 'desc'), limit(800)));
  return s.docs.map((d) => ({ id: d.id, ...d.data() }));
}

const median = (a) => {
  const s = [...a].sort((x, y) => x - y);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};
/** Board: per crop+unit near me — median of the window, the window before it, count and best market. */
export function priceBoard(rows, { lat, lng, radiusKm, currency, windowDays }) {
  const here = [lat, lng];
  const cut = daysAgo(windowDays);
  const near = rows.filter((r) => r.currency === currency && distanceKm(here, cellCenter(r.cell)) <= radiusKm);
  const groups = new Map();
  for (const r of near) {
    const k = `${r.crop}|${r.unit}`;
    if (!groups.has(k)) groups.set(k, { crop: r.crop, unit: r.unit, now: [], before: [], markets: new Map() });
    const g = groups.get(k);
    (r.day >= cut ? g.now : g.before).push(r.price);
    if (r.day >= cut && r.market) {
      const m = g.markets.get(r.market) || [];
      m.push(r.price); g.markets.set(r.market, m);
    }
  }
  return [...groups.values()].filter((g) => g.now.length).map((g) => {
    const med = median(g.now);
    const prev = g.before.length ? median(g.before) : null;
    const best = [...g.markets.entries()].filter(([, v]) => v.length >= 1).map(([m, v]) => ({ market: m, median: median(v), n: v.length }))
      .sort((a, b) => b.median - a.median)[0] || null;
    return {
      crop: g.crop, unit: g.unit, median: Math.round(med * 100) / 100, n: g.now.length,
      min: Math.min(...g.now), max: Math.max(...g.now),
      trend: prev ? Math.round(((med - prev) / prev) * 100) : null, best,
    };
  }).sort((a, b) => b.n - a.n);
}

// ---------- Community: outbreak reports ----------
export function addOutbreak(uid, o) {
  const [la, lo] = cellCenter(o.cell);
  return addDoc(collection(db, 'outbreaks'), {
    uid,
    crop: o.crop,
    problemKey: o.problemKey,
    category: o.category,
    cell: o.cell,
    lat: la,
    lng: lo,
    country: String(o.country || '').slice(0, 2),
    day: today(),
    createdAt: serverTimestamp(),
  });
}
export async function loadOutbreaks(sinceDays = 21) {
  const s = await getDocs(query(collection(db, 'outbreaks'), where('day', '>=', daysAgo(sinceDays)), orderBy('day', 'desc'), limit(800)));
  return s.docs.map((d) => ({ id: d.id, ...d.data() }));
}
/** Alerts: the same problem reported at least `min` times within the radius, for crops I grow. */
export function outbreakAlerts(rows, { lat, lng, radiusKm, min, crops }) {
  const here = [lat, lng];
  const near = rows.filter((r) => distanceKm(here, [r.lat, r.lng]) <= radiusKm);
  const g = new Map();
  for (const r of near) {
    const k = `${r.crop}|${r.problemKey}`;
    const x = g.get(k) || { crop: r.crop, problemKey: r.problemKey, category: r.category, n: 0, last: r.day, nearest: Infinity };
    x.n += 1; x.last = r.day > x.last ? r.day : x.last;
    x.nearest = Math.min(x.nearest, Math.round(distanceKm(here, [r.lat, r.lng])));
    g.set(k, x);
  }
  return [...g.values()].filter((x) => x.n >= min && (!crops?.length || crops.includes(x.crop)))
    .sort((a, b) => b.n - a.n);
}

// ---------- Sample farm (for judges and first-time users) ----------
export async function seedSampleFarm(uid) {
  const b = writeBatch(db);
  const plots = [
    { id: 'sample-plantain', name: 'Conuco de plátano', crop: 'plantain', area: 3144, planted: daysAgo(160) },
    { id: 'sample-beans', name: 'Habichuela de la loma', crop: 'beans', area: 1258, planted: daysAgo(38) },
    { id: 'sample-cacao', name: 'Cacao viejo', crop: 'cacao', area: 6289, planted: '2019-05-10' },
  ];
  plots.forEach((p) => b.set(doc(db, 'users', uid, 'plots', p.id), { name: p.name, crop: p.crop, area: p.area, planted: p.planted, createdAt: serverTimestamp() }));
  const logs = [
    ['sample-beans', 'plant', 'Sembré 6 libras de habichuela roja', 6, 'lb', 0, 38],
    ['sample-beans', 'weed', 'Limpieza a mano, dos peones', 0, '', 1600, 20],
    ['sample-plantain', 'fertilize', 'Abono orgánico (gallinaza) alrededor de cada mata', 4, 'sack', 1400, 12],
    ['sample-cacao', 'harvest', 'Cosecha de mazorcas sanas', 180, 'unit', 0, 6],
    ['sample-cacao', 'sell', 'Vendí cacao fermentado en la asociación', 1, 'quintal', 9800, 4],
    ['sample-beans', 'scout', 'Manchas cafés en hojas de abajo, revisar', 0, '', 0, 1],
  ];
  logs.forEach(([plotId, kind, note, qty, unit, money, ago], i) => b.set(doc(db, 'users', uid, 'logs', `sample-${i}`), {
    plotId, kind, note, qty, unit, money, date: daysAgo(ago), via: 'text', createdAt: serverTimestamp(),
  }));
  await b.commit();
}
