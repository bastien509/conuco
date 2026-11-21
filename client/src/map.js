// Community outbreak map (Leaflet + OpenStreetMap). Each dot is a 0.1° cell (≈ 11 km), never a farm.
import L from 'leaflet';
import { CAT_COLOR, cropName, problemName } from './crops.js';

let map = null;
let layer = null;
let me = null;

export function showMap(el, { lat, lng, rows, lang, radiusKm }) {
  if (map && map.getContainer() !== el) { map.remove(); map = null; }
  if (!map) {
    map = L.map(el, { zoomControl: true, attributionControl: true, preferCanvas: true }).setView([lat, lng], 9);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 14, minZoom: 3, attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);
    layer = L.layerGroup().addTo(map);
  }
  layer.clearLayers();
  if (me) me.remove();
  me = L.circle([lat, lng], { radius: radiusKm * 1000, color: '#1f6f3f', weight: 1.5, fillOpacity: 0.04, dashArray: '6 6' }).addTo(map);
  L.circleMarker([lat, lng], { radius: 7, color: '#fff', weight: 2, fillColor: '#1f6f3f', fillOpacity: 1 })
    .bindTooltip(lang === 'es' ? 'Tu zona' : 'Your area').addTo(layer);

  const cells = new Map();
  for (const r of rows) {
    const k = `${r.cell}|${r.problemKey}|${r.crop}`;
    const c = cells.get(k) || { ...r, n: 0 };
    c.n += 1;
    cells.set(k, c);
  }
  for (const c of cells.values()) {
    const color = CAT_COLOR[c.category] || CAT_COLOR.unknown;
    L.circleMarker([c.lat, c.lng], { radius: 6 + Math.min(14, c.n * 2), color, weight: 2, fillColor: color, fillOpacity: 0.35 })
      .bindPopup(`<strong>${esc(problemName(c.problemKey, lang))}</strong><br>${esc(cropName(c.crop, lang))} · ${c.n} ${lang === 'es' ? 'reportes' : 'reports'}<br><small>${esc(c.day)}</small>`)
      .addTo(layer);
  }
  setTimeout(() => map.invalidateSize(), 50);
}

const esc = (s) => String(s).replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
