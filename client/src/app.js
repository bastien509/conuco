// Conuco web app: UI and flow. Views are built with DOM nodes (never innerHTML with user or AI text).
import { onAuthStateChanged, signInAnonymously, signInWithEmailAndPassword, createUserWithEmailAndPassword, EmailAuthProvider, linkWithCredential, signOut } from 'firebase/auth';
import { auth, settings, loadRemoteConfig, track, trackUser } from './firebase.js';
import { t, setLang, getLang, guessLang } from './i18n.js';
import { CROPS, CROP_KEYS, cropName, PRICE_UNITS, currencyFor, AREA_UNITS, CATEGORIES, CAT_COLOR, problemName } from './crops.js';
import { analyseWeather, weatherTasks, SYMPTOMS, symptomGuide } from './rules.js';
import { getWeather, cachedWeather, searchPlace, sky, coarse } from './weather.js';
import { preparePhoto } from './image.js';
import { deviceStatus, prepareDevice, diagnose, agree, farmBrief, askConuco, looksFresh } from './ai.js';
import * as store from './store.js';
import { canListen, listen, listenMode, installLocalSpeech, speak, stopSpeaking, canSpeak } from './voice.js';

// ---------- tiny DOM helper ----------
function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'style') Object.assign(el.style, v);
    else if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, v);
  }
  for (const kid of kids.flat(Infinity)) {
    if (kid == null || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}
const icon = (id, cls = 'ic') => {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('class', cls);
  s.setAttribute('aria-hidden', 'true');
  const u = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  u.setAttribute('href', `#i-${id}`);
  s.append(u);
  return s;
};
const $ = (s) => document.querySelector(s);
/** replaceChildren that skips null/false (optional pieces). */
function fill(el, ...kids) {
  el.replaceChildren(...kids.flat(Infinity).filter((k) => k != null && k !== false && k !== ''));
  return el;
}

// ---------- state ----------
const state = {
  user: null, profile: null, plots: [], logs: [], diags: [], tasks: [], tasksLoaded: false,
  wx: null, wxFrom: '', analysis: null, prices: null, outbreaks: null, nano: 'unsupported', unsub: [],
};
const live = new Map(); // name → refresh function of the current view

const money = (n, cur) => {
  try { return new Intl.NumberFormat(getLang(), { style: 'currency', currency: cur || 'USD', maximumFractionDigits: n >= 100 ? 0 : 2 }).format(n); } catch { return `${n} ${cur}`; }
};
const currency = () => currencyFor(state.profile?.country);
const ago = (ms) => {
  const m = Math.round((Date.now() - ms) / 60000);
  if (m < 2) return t('now');
  if (m < 60) return t('ago_min', { n: m });
  if (m < 48 * 60) return t('ago_h', { n: Math.round(m / 60) });
  return t('ago_d', { n: Math.round(m / 1440) });
};
const fmtWhen = (iso) => {
  const d = new Date(iso);
  return d.toLocaleString(getLang(), { weekday: 'short', hour: '2-digit', minute: '2-digit' });
};
const fmtDay = (iso) => new Date(`${iso}T12:00`).toLocaleDateString(getLang(), { weekday: 'short', day: 'numeric' });
const windowText = (w) => `${fmtWhen(w.start)}–${new Date(w.end).getHours() + 1}:00`;

function toast(msg) {
  const el = h('div', { class: 'toast', role: 'status', text: msg });
  document.body.append(el);
  setTimeout(() => el.classList.add('show'), 10);
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, 3200);
}

// ---------- boot ----------
let prefLang = null;
try { prefLang = localStorage.getItem('conuco.lang'); } catch { /* blocked */ }
setLang(prefLang || guessLang());

if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
window.addEventListener('online', () => { renderStatus(); refreshWeather(); });
window.addEventListener('offline', renderStatus);
window.addEventListener('hashchange', route);

loadRemoteConfig().then(() => { if (state.profile) route(); });
refreshNano();

onAuthStateChanged(auth, async (user) => {
  state.unsub.forEach((u) => u());
  state.unsub = [];
  state.user = user;
  if (!user) { renderWelcome(); return; }
  state.profile = await store.getProfile(user.uid);
  if (!state.profile) { renderOnboarding(); return; }
  startApp();
});

function startApp() {
  const p = state.profile;
  setLang(p.lang);
  document.body.classList.add('in-app');
  renderShell();
  trackUser({ crops: (p.crops || []).slice(0, 3).join(','), lang: p.lang });
  const uid = state.user.uid;
  state.unsub.push(store.watchPlots(uid, (x) => { state.plots = x; live.get('plots')?.(); }));
  state.unsub.push(store.watchLog(uid, (x) => { state.logs = x; live.get('logs')?.(); renderStatus(); }));
  state.unsub.push(store.watchDiagnoses(uid, (x) => { state.diags = x; live.get('diags')?.(); }));
  state.unsub.push(store.watchTasks(uid, (x) => { state.tasks = x; state.tasksLoaded = true; addWeatherTasks(); live.get('tasks')?.(); }));
  const cached = cachedWeather(p.lat, p.lng);
  if (cached) { state.wx = cached; state.wxFrom = navigator.onLine ? 'cache' : 'offline-cache'; state.analysis = analyseWeather(cached, p.crops); }
  refreshWeather();
  loadCommunity();
  if (!location.hash || location.hash === '#') location.hash = '#today'; else route();
}

async function refreshNano() {
  state.nano = await deviceStatus(true, getLang());
  renderStatus();
  live.get('nano')?.();
}

async function refreshWeather(force = false) {
  const p = state.profile;
  if (!p) return;
  try {
    const r = await getWeather(p.lat, p.lng, { force });
    state.wx = r.data; state.wxFrom = r.from;
    state.analysis = analyseWeather(r.data, p.crops);
    addWeatherTasks();
  } catch { /* offline with no cache */ }
  live.get('weather')?.();
}

async function loadCommunity() {
  try { state.prices = await store.loadPrices(Math.max(settings.priceWindowDays * 2, 14)); } catch { state.prices = state.prices || []; }
  try { state.outbreaks = await store.loadOutbreaks(21); } catch { state.outbreaks = state.outbreaks || []; }
  live.get('market')?.(); live.get('alerts')?.(); live.get('map')?.();
}

function addWeatherTasks() {
  if (!state.tasksLoaded || !state.analysis || !state.user) return;
  const day = store.today();
  const have = new Set(state.tasks.map((x) => x.id));
  for (const task of weatherTasks(state.analysis, day)) {
    if (have.has(task.id)) continue;
    const d = task.data;
    const text = {
      spray_window: () => t('task_spray_window', { when: windowText(d) }),
      irrigate: () => t('task_irrigate', { mm: Math.round(d.deficit) }),
      scout_fungus: () => t('task_scout_fungus'),
      drainage: () => t('task_drainage', { date: fmtDay(d.date) }),
      heat: () => t('task_heat', { t: d.maxT }),
    }[task.code]();
    store.upsertTask(state.user.uid, { id: task.id, code: task.code, text, day, from: 'weather' }).catch(() => {});
  }
}

// ---------- shell ----------
// Phone order: Hoy, Mercado, [Doctor camera], Cerca, Finca; Pregunta is a floating mic. CSS restores the list order on desktop.
const NAV = [['today', 'sun'], ['market', 'scale'], ['doctor', 'camera', 'center'], ['map', 'pin'], ['farm', 'book'], ['ask', 'mic', 'fab']];
function renderShell() {
  const nav = $('#nav');
  fill(nav, ...NAV.map(([r, ic, cls]) => h('a', { href: `#${r}`, 'data-r': r, class: cls || '', 'aria-label': t(`nav_${r}`) },
    cls === 'center' ? h('span', { class: 'cam' }, icon(ic)) : icon(ic), h('span', { class: 'lbl', text: t(`nav_${r}`) }))));
  // A second tap on the camera while already on Doctor opens the photo picker directly.
  nav.querySelector('a.center').addEventListener('click', (e) => {
    const input = document.getElementById('photo');
    if (location.hash === '#doctor' && input) { e.preventDefault(); input.click(); }
  });
  $('#to-settings').setAttribute('aria-label', t('nav_settings'));
  $('#to-settings').title = t('nav_settings');
  renderStatus();
}

function renderStatus() {
  const el = $('#status');
  if (!el) return;
  const pending = state.logs.filter((x) => x.pending).length;
  fill(el, 
    h('span', { class: `chip ${navigator.onLine ? 'ok' : 'warn'}` }, icon(navigator.onLine ? 'wifi' : 'nowifi'), t(navigator.onLine ? 'online' : 'offline')),
    h('a', { class: `chip ${state.nano === 'available' ? 'ok' : ''}`, href: '#settings' }, icon('chip'), t(`nano_${state.nano}`)),
    pending ? h('span', { class: 'chip warn' }, icon('sync'), t('pending_sync', { n: pending })) : null,
  );
}

function route() {
  if (!state.profile) return;
  const r = (location.hash || '#today').slice(1) || 'today';
  document.querySelectorAll('#nav a').forEach((a) => a.classList.toggle('on', a.dataset.r === r));
  document.body.dataset.r = r;
  live.clear();
  stopSpeaking();
  const view = { today: viewToday, doctor: viewDoctor, market: viewMarket, map: viewMap, farm: viewFarm, ask: viewAsk, settings: viewSettings }[r] || viewToday;
  const main = $('#view');
  fill(main, view());
  main.focus({ preventScroll: true });
  window.scrollTo(0, 0);
  track('screen_view', { firebase_screen: r });
}

// ---------- shared pieces ----------
function badge(source, kind = 'photo') {
  const map = {
    on_device: [kind === 'photo' ? 'src_on_device' : 'src_text_device', 'chip', 'dev'],
    in_cloud: [kind === 'photo' ? 'src_in_cloud' : 'src_text_cloud', 'cloud', 'cloud'],
    grounded: ['src_grounded', 'search', 'cloud'],
    rules: ['src_rules', 'rule', 'rules'],
  }[source] || ['src_rules', 'rule', 'rules'];
  return h('span', { class: `badge ${map[2]}` }, icon(map[1]), t(map[0]));
}

function traceBox(trace) {
  const issues = (x) => x.map((i) => t(`q_${i}`)).join(', ');
  return h('details', { class: 'trace' },
    h('summary', {}, icon('route'), t('why')),
    h('ol', {}, trace.map((s) => h('li', { text: t(`tr_${s.code}`, {
      kb: s.kb, s: s.ms != null ? (s.ms / 1000).toFixed(1) : '', c: s.confidence != null ? Math.round(s.confidence * 100) : '',
      f: s.floor != null ? Math.round(s.floor * 100) : '', msg: s.msg, status: s.status, n: s.n, issues: s.issues ? issues(s.issues) : '',
    }) }))));
}

function speakButton(getText) {
  if (!canSpeak()) return null;
  const b = h('button', { class: 'btn ghost sm', type: 'button' }, icon('speaker'), h('span', { text: t('t_listen') }));
  b.addEventListener('click', () => {
    if (b.classList.contains('on')) { stopSpeaking(); b.classList.remove('on'); b.lastChild.textContent = t('t_listen'); return; }
    b.classList.add('on'); b.lastChild.textContent = t('t_stop');
    speak(getText(), getLang(), () => { b.classList.remove('on'); b.lastChild.textContent = t('t_listen'); });
    track('speak', {});
  });
  return b;
}

function cropChips(selected, onPick, { multi = false, only = null } = {}) {
  const keys = only || CROP_KEYS;
  const wrap = h('div', { class: 'chips', role: multi ? 'group' : 'radiogroup' });
  const sel = new Set(multi ? selected : [selected]);
  const draw = () => fill(wrap, ...keys.map((k) => h('button', {
    type: 'button', class: `pick ${sel.has(k) ? 'on' : ''}`, 'aria-pressed': sel.has(k) ? 'true' : 'false',
    onclick: () => {
      if (multi) { if (sel.has(k)) sel.delete(k); else sel.add(k); } else { sel.clear(); sel.add(k); }
      draw(); onPick(multi ? [...sel] : k);
    },
  }, h('i', { class: 'dot', style: { background: CROPS[k].color } }), cropName(k, getLang()))));
  draw();
  return wrap;
}

async function askConsent(reason, conf) {
  const dlg = $('#consent');
  fill(dlg, 
    h('h2', { text: t('c_title') }),
    h('p', { text: reason === 'second_opinion' ? t('c_second', { conf: Math.round((conf || 0) * 100) }) : reason === 'cloud_first' ? t('c_cloud_first') : t('c_no_device') }),
    h('p', { class: 'muted', text: t('c_what') }),
    h('div', { class: 'row end' },
      h('button', { class: 'btn ghost', value: 'no', type: 'button', onclick: () => dlg.close('no') }, t('c_no')),
      h('button', { class: 'btn primary', value: 'yes', type: 'button', onclick: () => dlg.close('yes') }, icon('cloud'), t('c_yes'))),
  );
  dlg.showModal();
  return new Promise((res) => dlg.addEventListener('close', () => res(dlg.returnValue === 'yes'), { once: true }));
}

// ---------- welcome / sign-in ----------
function renderWelcome() {
  document.body.classList.remove('in-app');
  const main = $('#view');
  const form = h('form', { class: 'card stack hidden', id: 'signin' },
    h('label', {}, t('w_email'), h('input', { type: 'email', name: 'email', autocomplete: 'username', required: true })),
    h('label', {}, t('w_password'), h('input', { type: 'password', name: 'password', autocomplete: 'current-password', required: true, minlength: '6' })),
    h('p', { class: 'err', id: 'signin-err' }),
    h('div', { class: 'row' },
      h('button', { class: 'btn primary', type: 'submit' }, t('w_go')),
      h('button', { class: 'btn ghost', type: 'button', id: 'create' }, t('w_create'))));
  const go = async (fn) => {
    const fd = new FormData(form);
    try { await fn(auth, String(fd.get('email')).trim(), String(fd.get('password'))); } catch (e) {
      $('#signin-err').textContent = t('w_err', { msg: (e.code || e.message || '').replace('auth/', '') });
    }
  };
  form.addEventListener('submit', (e) => { e.preventDefault(); go(signInWithEmailAndPassword); });
  form.querySelector('#create').addEventListener('click', () => { if (form.reportValidity()) go(createUserWithEmailAndPassword); });
  fill(main, h('section', { class: 'welcome' },
    h('img', { src: 'assets/logo.svg', alt: '', width: '72', height: '72' }),
    h('h1', { text: t('w_title') }),
    h('p', { class: 'lead', text: t('w_sub') }),
    langSwitch(() => renderWelcome()),
    h('div', { class: 'stack' },
      h('button', { class: 'btn primary lg', type: 'button', onclick: async (e) => { e.currentTarget.disabled = true; try { await signInAnonymously(auth); track('sign_up', { method: 'anonymous' }); } catch (err) { toast(t('err_generic', { msg: err.code || err.message })); e.target.disabled = false; } } }, t('w_start')),
      h('button', { class: 'btn ghost lg', type: 'button', onclick: () => form.classList.toggle('hidden') }, t('w_signin'))),
    form,
    h('p', { class: 'muted small', text: t('w_privacy') })));
}

function langSwitch(after) {
  return h('div', { class: 'seg', role: 'radiogroup', 'aria-label': t('o_lang') },
    [['es', 'Español'], ['en', 'English']].map(([l, name]) => h('button', {
      type: 'button', class: getLang() === l ? 'on' : '', 'aria-pressed': getLang() === l ? 'true' : 'false',
      onclick: () => { setLang(l); try { localStorage.setItem('conuco.lang', l); } catch { /* blocked */ } after(); },
    }, name)));
}

// ---------- onboarding ----------
function renderOnboarding() {
  document.body.classList.remove('in-app');
  const draft = { lang: getLang(), crops: [], place: '', lat: null, lng: null, country: '', name: '', sample: true };
  const main = $('#view');
  const placeOut = h('p', { class: 'found' });
  const results = h('div', { class: 'results' });
  const setPlace = (p) => {
    Object.assign(draft, p);
    fill(placeOut, icon('pin'), t('o_found', { place: draft.place }));
    fill(results);
  };
  const q = h('input', { type: 'search', placeholder: t('o_search'), 'aria-label': t('o_search') });
  let tmr;
  q.addEventListener('input', () => {
    clearTimeout(tmr);
    tmr = setTimeout(async () => {
      if (q.value.trim().length < 2) return;
      const list = await searchPlace(q.value.trim(), getLang()).catch(() => []);
      fill(results, ...list.map((p) => h('button', { type: 'button', class: 'result', onclick: () => setPlace({ place: [p.name, p.admin].filter(Boolean).join(', '), lat: p.lat, lng: p.lng, country: p.country }) },
        h('strong', { text: p.name }), ` ${p.admin} · ${p.country}`)));
    }, 350);
  });
  const gps = h('button', { class: 'btn ghost', type: 'button' }, icon('pin'), t('o_gps'));
  gps.addEventListener('click', () => {
    navigator.geolocation?.getCurrentPosition(async (pos) => {
      const lat = coarse(pos.coords.latitude);
      const lng = coarse(pos.coords.longitude);
      let place = `${lat}, ${lng}`;
      let country = '';
      try {
        const r = await fetch(`https://geocoding-api.open-meteo.com/v1/reverse?latitude=${lat}&longitude=${lng}&language=${getLang()}`).then((x) => (x.ok ? x.json() : null));
        const top = r?.results?.[0];
        if (top) { place = [top.name, top.admin1].filter(Boolean).join(', '); country = top.country_code || ''; }
      } catch { /* keep coordinates */ }
      setPlace({ place, lat, lng, country });
    }, () => toast(t('err_generic', { msg: 'location' })), { enableHighAccuracy: false, timeout: 15000, maximumAge: 600000 });
  });
  const err = h('p', { class: 'err' });
  const name = h('input', { type: 'text', maxlength: '40', autocomplete: 'given-name' });
  const sample = h('input', { type: 'checkbox', checked: true });
  const done = h('button', { class: 'btn primary lg', type: 'button' }, t('o_done'));
  done.addEventListener('click', async () => {
    if (draft.lat == null || !draft.crops.length) { err.textContent = t('o_need'); return; }
    done.disabled = true;
    draft.name = name.value;
    draft.lang = getLang();
    draft.areaUnit = draft.country === 'DO' ? 'tarea' : 'ha';
    try {
      await store.saveProfile(state.user.uid, draft);
      if (sample.checked) await store.seedSampleFarm(state.user.uid);
      track('onboarding_done', { crops: draft.crops.length, sample: sample.checked ? 1 : 0 });
      state.profile = await store.getProfile(state.user.uid) || draft;
      startApp();
    } catch (e) { err.textContent = t('err_generic', { msg: e.code || e.message }); done.disabled = false; }
  });
  fill(main, h('section', { class: 'onboard stack' },
    h('h1', { text: t('o_title') }),
    h('div', { class: 'field' }, h('span', { class: 'label', text: t('o_lang') }), langSwitch(() => renderOnboarding())),
    h('div', { class: 'field' }, h('span', { class: 'label', text: t('o_place') }), h('div', { class: 'row wrap' }, gps, q), results, placeOut, h('p', { class: 'muted small', text: t('o_gps_note') })),
    h('div', { class: 'field' }, h('span', { class: 'label', text: t('o_crops') }), cropChips(draft.crops, (v) => { draft.crops = v; }, { multi: true })),
    h('label', { class: 'field' }, h('span', { class: 'label', text: t('o_name') }), name),
    h('label', { class: 'check' }, sample, t('o_sample')),
    err, done));
}

// ---------- Today ----------
function viewToday() {
  const p = state.profile;
  const hr = new Date().getHours();
  const part = t(hr < 12 ? 'part_morning' : hr < 19 ? 'part_afternoon' : 'part_evening');
  const wx = h('div', { class: 'stack' });
  const brief = h('div', { class: 'brief' });
  const tasks = h('div', { class: 'card' });
  const alerts = h('div', {});

  const drawWeather = () => {
    const a = state.analysis;
    if (!a) { fill(wx, h('div', { class: 'card muted', text: t('t_wx_err') })); return; }
    const days = a.days.map((d) => h('div', { class: 'day' },
      h('span', { class: 'dname', text: fmtDay(d.date) }), icon(`sky-${sky(d.code)}`, 'ic sky'),
      h('span', { class: 'temps' }, h('b', { text: `${Math.round(d.tmax)}°` }), ` ${Math.round(d.tmin)}°`),
      h('span', { class: 'rain', text: `${Math.round(d.rain)} mm` })));
    const spray = a.spray;
    const sprayLine = spray.okNow ? t('t_spray_ok') : spray.windows[0] ? t('t_spray_next', { when: windowText(spray.windows[0]), h: spray.windows[0].hours }) : t('t_spray_none');
    const blockers = spray.blockers.map((b) => t(`b_${b.code}`, b)).join(' · ');
    const irr = a.irrigation;
    fill(wx, 
      h('div', { class: 'card weather' },
        h('div', { class: 'now' },
          h('div', { class: 'big', text: a.now ? `${Math.round(a.now.temp)}°C` : '–' }),
          h('div', { class: 'meta' },
            h('span', {}, icon('drop'), `${t('t_humidity')} ${a.now?.rh ?? '–'}%`),
            h('span', {}, icon('wind'), `${t('t_wind')} ${a.now?.wind ?? '–'} km/h`),
            h('span', {}, icon('cloud'), `${t('t_rain')} ${a.now?.prob ?? '–'}%`)),
          h('p', { class: 'muted small' }, icon('pin'), ` ${p.place} · `, state.wxFrom === 'offline-cache' ? t('t_cached') : t('t_updated', { ago: ago(a.updated) }))),
        h('div', { class: 'days' }, days)),
      h('div', { class: 'decisions' },
        decision('spray', t('t_spray'), sprayLine, blockers, spray.okNow ? 'good' : spray.windows[0] ? 'mid' : 'bad'),
        decision('drop', t('t_water'), t(`t_${irr.advice}`), t('t_water_detail', { use: irr.cropUse, rain: irr.rainEff }), irr.advice === 'skip' ? 'good' : irr.advice === 'light' ? 'mid' : 'bad'),
        decision('leaf', t('t_disease'), t(`lvl_${a.disease.level}`), t('t_disease_detail', { h: a.disease.wetHours }), a.disease.level === 'low' ? 'good' : a.disease.level === 'medium' ? 'mid' : 'bad')),
      a.heat ? h('div', { class: 'note warn' }, icon('alert'), t('t_heat', { t: a.heat.maxT, d: a.heat.days })) : null,
      a.storm ? h('div', { class: 'note warn' }, icon('alert'), t('t_storm', { mm: a.storm.mm, date: fmtDay(a.storm.date) })) : null,
    );
    drawBrief();
  };

  let briefResult = null;
  const drawBrief = () => {
    const a = state.analysis;
    if (!a) { fill(brief); return; }
    const btn = h('button', { class: 'btn primary', type: 'button' }, icon('spark'), t('t_brief'));
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      fill(btn, h('span', { class: 'spin' }), t('t_brief_busy'));
      try {
        briefResult = await farmBrief({ facts: briefFacts(a), lang: getLang() });
        track('brief', { source: briefResult.source });
      } catch (e) {
        briefResult = { error: e.message === 'timeout' ? 'timeout' : (e.code || e.message) };
      }
      drawBrief();
    });
    const out = [];
    if (briefResult?.headline) {
      const full = `${briefResult.headline}. ${briefResult.steps.join('. ')}`;
      out.push(h('div', { class: 'card ai' },
        h('div', { class: 'row between' }, badge(briefResult.source, 'text'), speakButton(() => full)),
        h('h3', { text: briefResult.headline }),
        h('ol', {}, briefResult.steps.map((s) => h('li', { text: s }))),
        traceBox(briefResult.trace)));
    } else if (briefResult?.error) {
      out.push(h('p', { class: 'err', text: briefResult.error === 'timeout' ? t('a_err', { msg: 'timeout' }) : t('a_need') }));
    }
    fill(brief, briefResult?.headline ? null : btn, ...out);
  };

  const drawTasks = () => {
    const list = state.tasks.filter((x) => x.day === store.today() || !x.done).sort((a, b) => Number(a.done) - Number(b.done));
    fill(tasks, h('h2', {}, icon('check'), t('t_tasks')),
      list.length ? h('ul', { class: 'tasks' }, list.map((x) => h('li', { class: x.done ? 'done' : '' },
        h('label', {}, h('input', { type: 'checkbox', checked: x.done, onchange: (e) => store.setTaskDone(state.user.uid, x.id, e.target.checked) }),
          h('span', { text: x.text }), x.from === 'weather' ? h('small', { class: 'tag', text: '☁' }) : null)))) : h('p', { class: 'muted', text: t('t_no_tasks') }));
  };
  const drawAlerts = () => {
    const rows = state.outbreaks || [];
    const list = store.outbreakAlerts(rows, { lat: p.lat, lng: p.lng, radiusKm: settings.outbreakRadiusKm, min: settings.outbreakAlertMin, crops: p.crops });
    fill(alerts, list.length ? h('a', { class: 'card alert', href: '#map' },
      h('h2', {}, icon('alert'), t('t_alerts')),
      h('ul', {}, list.slice(0, 3).map((x) => h('li', {}, h('i', { class: 'dot', style: { background: CAT_COLOR[x.category] } }),
        t('t_alert_line', { problem: problemName(x.problemKey, getLang()), crop: cropName(x.crop, getLang()), n: x.n, km: settings.outbreakRadiusKm }))))) : '');
  };
  live.set('weather', drawWeather); live.set('tasks', drawTasks); live.set('alerts', drawAlerts);
  drawWeather(); drawTasks(); drawAlerts();
  return h('section', { class: 'page' },
    h('header', { class: 'pagehead' },
      h('h1', { text: p.name ? t('t_hello', { part, name: p.name }) : t('t_hello_anon', { part }) }),
      h('button', { class: 'btn ghost sm', type: 'button', onclick: () => refreshWeather(true), 'aria-label': 'refresh' }, icon('sync'))),
    alerts, wx, brief, tasks);
}

function decision(ic, title, main, detail, tone) {
  return h('div', { class: `decision ${tone}` }, h('div', { class: 'dhead' }, icon(ic), h('span', { text: title })),
    h('strong', { text: main }), detail ? h('small', { text: detail }) : null);
}

/** Plain facts from the rules, for the AI brief. No place names, no coordinates. */
function briefFacts(a) {
  const f = [];
  const L = getLang();
  const crops = (state.profile.crops || []).map((c) => cropName(c, L)).join(', ');
  if (crops) f.push(`Crops: ${crops}.`);
  if (a.now) f.push(`Now: ${a.now.temp} °C, humidity ${a.now.rh} %, wind ${a.now.wind} km/h, rain chance ${a.now.prob} %.`);
  f.push(a.spray.okNow ? 'Spraying conditions are good now.' : a.spray.windows[0] ? `Do not spray now (${a.spray.blockers.map((b) => b.code).join(', ')}). Next safe spray window: ${windowText(a.spray.windows[0])}.` : 'No safe spray window in the next 48 hours.');
  f.push(`Irrigation: ${a.irrigation.advice} (crops need ${a.irrigation.cropUse} mm, rain gives ${a.irrigation.rainEff} mm over 3 days).`);
  f.push(`Fungal disease risk: ${a.disease.level} (${a.disease.wetHours} very humid hours).`);
  if (a.heat) f.push(`Heat up to ${a.heat.maxT} °C.`);
  if (a.storm) f.push(`Heavy rain of ${a.storm.mm} mm expected on ${a.storm.date}.`);
  const al = store.outbreakAlerts(state.outbreaks || [], { lat: state.profile.lat, lng: state.profile.lng, radiusKm: settings.outbreakRadiusKm, min: settings.outbreakAlertMin, crops: state.profile.crops });
  if (al[0]) f.push(`Neighbours reported ${problemName(al[0].problemKey, L)} on ${cropName(al[0].crop, L)} (${al[0].n} reports nearby).`);
  const open = state.tasks.filter((x) => !x.done && x.from === 'user').length;
  if (open) f.push(`${open} open to-dos from the farmer.`);
  return f;
}

// ---------- Doctor ----------
function viewDoctor() {
  const p = state.profile;
  const draft = { crop: p.crops?.[0] || 'other', mode: 'smart', note: '', photo: null };
  const preview = h('div', { class: 'preview' });
  const out = h('div', { class: 'stack' });
  const hist = h('div', { class: 'card' });
  const file = h('input', { type: 'file', accept: 'image/*', capture: 'environment', class: 'visually-hidden', id: 'photo' });
  const pickLabel = h('label', { for: 'photo', class: 'btn primary lg photo-btn' }, icon('camera'), h('span', { text: t('d_photo') }));
  const go = h('button', { class: 'btn primary lg', type: 'button', disabled: true }, icon('spark'), t('d_go'));
  const note = h('textarea', { rows: '2', maxlength: '300', placeholder: t('d_note_ph') });
  note.addEventListener('input', () => { draft.note = note.value; });

  const usePhoto = async (blob) => {
    try {
      draft.photo = await preparePhoto(blob);
      fill(preview, h('img', { src: draft.photo.url, alt: '' }),
        draft.photo.quality.issues.length ? h('p', { class: 'note warn', text: t('d_quality', { issues: draft.photo.quality.issues.map((i) => t(`q_${i}`)).join(', ') }) }) : null);
      pickLabel.lastChild.textContent = t('d_change');
      go.disabled = false;
      fill(out);
    } catch (e) { toast(t('err_generic', { msg: e.message })); }
  };
  file.addEventListener('change', () => { if (file.files[0]) usePhoto(file.files[0]); });

  const samples = h('div', { class: 'samples' });
  fetch('samples/samples.json').then((r) => (r.ok ? r.json() : [])).then((list) => {
    if (!list.length) return;
    fill(samples, h('span', { class: 'muted small', text: getLang() === 'es' ? 'Fotos de ejemplo:' : 'Sample photos:' }),
      ...list.map((s) => h('button', { type: 'button', class: 'sample', onclick: async () => {
        const b = await fetch(`samples/${s.file}`).then((r) => r.blob());
        if (CROPS[s.crop]) {
          if (!cropList.includes(s.crop)) cropList.unshift(s.crop);
          draft.crop = s.crop;
          fill(cropBox, cropChips(draft.crop, (v) => { draft.crop = v; }, { only: cropList }));
        }
        usePhoto(b);
      }, title: s.credit || '' }, h('img', { src: `samples/${s.thumb || s.file}`, alt: '' }), h('span', { text: s[getLang()] || s.en }))),
      h('a', { class: 'muted small', href: 'samples/CREDITS.txt', target: '_blank', rel: 'noopener', text: getLang() === 'es' ? 'Créditos de fotos' : 'Photo credits' }));
  }).catch(() => {});

  const cropList = [...new Set([...(p.crops || []), 'other'])];
  const cropBox = h('div', {}, cropChips(draft.crop, (v) => { draft.crop = v; }, { only: cropList }));
  const modes = h('div', { class: 'modes', role: 'radiogroup', 'aria-label': t('d_mode') }, ['private', 'smart', 'cloud'].map((m) => h('label', { class: 'mode' },
    h('input', { type: 'radio', name: 'mode', value: m, checked: m === 'smart', onchange: () => { draft.mode = m; } }),
    h('span', {}, h('strong', { text: t(`m_${m}`) }), h('small', { text: t(`m_${m}_d`) })))));

  go.addEventListener('click', async () => {
    if (!draft.photo) return;
    go.disabled = true;
    fill(out, h('div', { class: 'card busy' }, h('span', { class: 'spin' }), h('span', { text: t(draft.mode === 'cloud' ? 'd_busy_cloud' : 'd_busy') })));
    try {
      const r = await diagnose({
        photo: draft.photo, crop: draft.crop, note: draft.note, lang: getLang(), mode: draft.mode,
        askConsent: async (reason, conf) => {
          const ok = await askConsent(reason, conf);
          if (ok) fill(out, h('div', { class: 'card busy' }, h('span', { class: 'spin' }), h('span', { text: t('d_busy_cloud') })));
          return ok;
        },
      });
      track('diagnose', { source: r.second ? 'both' : r.first?.source || 'none', mode: draft.mode, crop: draft.crop });
      fill(out, ...diagResult(r, draft));
    } catch (e) {
      fill(out, h('p', { class: 'err', text: t('err_generic', { msg: e.message }) }));
    }
    go.disabled = false;
  });

  const drawHist = () => {
    fill(hist, h('h2', {}, icon('clock'), t('d_history')),
      state.diags.length ? h('ul', { class: 'hist' }, state.diags.map((d) => h('li', {},
        h('i', { class: 'dot', style: { background: CAT_COLOR[d.category] || CAT_COLOR.unknown } }),
        h('span', {}, h('strong', { text: d.problem || problemName(d.problemKey, getLang()) }), ` · ${cropName(d.crop, getLang())}`),
        h('small', { text: `${Math.round((d.confidence || 0) * 100)}% · ${d.source === 'on_device' ? '⚙ ' : '☁ '}${d.day}` })))) : h('p', { class: 'muted', text: t('d_none') }));
  };
  live.set('diags', drawHist);
  drawHist();

  return h('section', { class: 'page' },
    h('header', { class: 'pagehead' }, h('h1', { text: t('d_title') })),
    h('p', { class: 'lead', text: t('d_sub') }),
    h('div', { class: 'card stack' },
      h('div', { class: 'field' }, h('span', { class: 'label', text: t('d_crop') }), cropBox),
      file, pickLabel, samples, preview,
      h('div', { class: 'field' }, h('span', { class: 'label', text: t('d_note') }), h('div', { class: 'row' }, note, dictateButton(note, (v) => { draft.note = v; }))),
      h('div', { class: 'field' }, h('span', { class: 'label', text: t('d_mode') }), modes),
      go),
    out, hist);
}

function diagCard(res, kind) {
  const d = res.data;
  const sev = h('span', { class: `sev ${d.severity}`, text: t(`sev_${d.severity}`) });
  if (!d.isPlant) return h('div', { class: 'card' }, badge(res.source), h('p', { text: t('d_not_plant') }));
  return h('div', { class: `card diag ${kind}` },
    h('div', { class: 'row between' }, badge(res.source), h('small', { class: 'muted', text: t('ms', { s: (res.ms / 1000).toFixed(1) }) })),
    kind === 'second' ? h('h4', { class: 'kicker', text: t('d_second') }) : null,
    h('h2', {}, h('i', { class: 'dot', style: { background: CAT_COLOR[d.category] } }), d.problemKey && d.problemKey !== 'other' ? problemName(d.problemKey, getLang()) : (d.problem || t(`cat_${d.category}`))),
    h('div', { class: 'row wrap' }, h('span', { class: 'pill', text: t(`cat_${d.category}`) }), d.healthy ? null : sev),
    h('div', { class: 'meter', role: 'meter', 'aria-valuenow': String(Math.round(d.confidence * 100)), 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-label': t('d_conf') },
      h('span', { class: 'label', text: `${t('d_conf')} ${Math.round(d.confidence * 100)}%` }), h('i', { style: { width: `${Math.round(d.confidence * 100)}%` } })),
    d.signs.length ? h('div', {}, h('h4', { text: t('d_signs') }), h('ul', {}, d.signs.map((s) => h('li', { text: s })))) : null,
    d.actions.length ? h('div', {}, h('h4', { text: t('d_actions') }), h('ol', { class: 'actions' }, d.actions.map((s) => h('li', { text: s })))) : null,
    d.prevent.length ? h('div', {}, h('h4', { text: t('d_prevent') }), h('ul', {}, d.prevent.map((s) => h('li', { text: s })))) : null,
    d.seeExpert ? h('p', { class: 'note warn' }, icon('people'), t('d_expert')) : null,
    d.filtered ? h('p', { class: 'note' }, icon('shield'), t('d_filtered')) : null,
    speakButton(() => [d.problem, ...d.actions].join('. ')));
}

function diagResult(r, draft) {
  const els = [];
  if (r.first) els.push(diagCard(r.first, 'first'));
  if (r.second) {
    els.push(diagCard(r.second, r.first ? 'second' : 'cloud'));
    if (r.first) els.push(h('p', { class: `note ${agree(r.first.data, r.second.data) ? 'good' : 'warn'}`, text: agree(r.first.data, r.second.data) ? t('d_agree') : t('d_disagree') }));
  }
  const best = r.second || r.first;
  if (best && best.data.isPlant) els.push(diagActions(best, draft, r));
  if (r.fallback === 'symptoms') els.push(symptomCard(draft.crop));
  els.push(traceBox(r.trace));
  return els;
}

function diagActions(best, draft, r) {
  const d = best.data;
  const p = state.profile;
  const uid = state.user.uid;
  const saved = { log: false, warn: false };
  const save = h('button', { class: 'btn ghost', type: 'button' }, icon('book'), t('d_save'));
  save.addEventListener('click', async () => {
    if (saved.log) return;
    saved.log = true; save.disabled = true;
    await Promise.all([
      store.addDiagnosis(uid, { crop: draft.crop, problem: d.problem, problemKey: d.problemKey, category: d.category, confidence: d.confidence, severity: d.severity, source: best.source, secondOpinion: r.second ? (r.first ? (agree(r.first.data, r.second.data) ? 'agree' : 'disagree') : 'cloud_only') : 'none', actions: d.actions }),
      store.addLog(uid, { kind: 'scout', note: `${d.problem} (${Math.round(d.confidence * 100)}%) — ${cropName(draft.crop, getLang())}`, via: 'doctor' }),
    ]).catch(() => {});
    fill(save, icon('check'), t('d_saved'));
  });
  const task = h('button', { class: 'btn ghost', type: 'button' }, icon('check'), t('d_task'));
  task.addEventListener('click', () => {
    store.upsertTask(uid, { id: `doc-${Date.now()}`, code: 'doctor', text: d.actions[0] || d.problem, from: 'doctor' });
    task.disabled = true; fill(task, icon('check'), t('d_saved'));
  });
  const warn = h('button', { class: 'btn danger', type: 'button' }, icon('alert'), t('d_warn'));
  warn.addEventListener('click', async () => {
    if (saved.warn) return;
    saved.warn = true; warn.disabled = true;
    try {
      await store.addOutbreak(uid, { crop: draft.crop, problemKey: d.problemKey, category: d.category, cell: store.cellOf(p.lat, p.lng), country: p.country });
      track('outbreak_report', { category: d.category });
      fill(warn, icon('check'), t('d_warned'));
      loadCommunity();
    } catch (e) { toast(t('err_generic', { msg: e.code || e.message })); saved.warn = false; warn.disabled = false; }
  });
  const canWarn = !d.healthy && d.category !== 'unknown' && p.shareOutbreaks !== false;
  return h('div', { class: 'card stack' }, h('div', { class: 'row wrap' }, save, task, canWarn ? warn : null),
    canWarn ? h('p', { class: 'muted small', text: t('d_warn_note') }) : null);
}

function symptomCard(crop) {
  const ticked = new Set();
  const out = h('div', {});
  return h('div', { class: 'card stack' },
    h('p', { class: 'row' }, badge('rules'), ` ${t('d_symptoms')}`),
    h('div', { class: 'chips' }, SYMPTOMS.map((s) => h('button', { type: 'button', class: 'pick', 'aria-pressed': 'false', onclick: (e) => {
      const b = e.currentTarget;
      if (ticked.has(s)) ticked.delete(s); else ticked.add(s);
      b.classList.toggle('on', ticked.has(s)); b.setAttribute('aria-pressed', ticked.has(s) ? 'true' : 'false');
    } }, t(`sym_${s}`)))),
    h('button', { class: 'btn primary', type: 'button', onclick: () => {
      if (!ticked.size) { fill(out, h('p', { class: 'err', text: t('d_sym_none') })); return; }
      const list = symptomGuide(crop, [...ticked]);
      track('symptom_guide', { n: ticked.size });
      fill(out, h('ol', { class: 'guess' }, list.map((x) => h('li', {}, h('i', { class: 'dot', style: { background: CAT_COLOR[x.cat] } }),
        h('strong', { text: problemName(x.key, getLang()) }), ` · ${t(`cat_${x.cat}`)}`))), h('p', { class: 'note warn' }, icon('people'), t('d_expert')));
    } }, t('d_sym_go')),
    out);
}

// ---------- Dictation (voice → text field) ----------
function dictateButton(field, onText) {
  if (!canListen()) return null;
  const b = h('button', { class: 'btn ghost mic', type: 'button', 'aria-label': t('f_dictate'), title: t('f_dictate') }, icon('mic'));
  let session = null;
  b.addEventListener('click', async () => {
    if (session) { session.stop(); return; }
    const mode = await listenMode(getLang());
    if (mode === 'none') { toast(t('a_voice_none')); return; }
    b.classList.add('on');
    const before = field.value;
    session = listen(getLang(), { local: mode === 'local', allowCloud: true, onPartial: (txt) => { field.value = `${before} ${txt}`.trim(); } });
    try {
      const r = await session.done;
      if (r.text) { field.value = `${before} ${r.text}`.trim(); onText?.(field.value); }
      toast(r.local ? t('a_voice_local') : t('a_voice_cloud'));
      track('voice_input', { local: r.local ? 1 : 0 });
    } catch (e) { if (e.message !== 'aborted' && e.message !== 'no-speech') toast(t('err_generic', { msg: e.message })); }
    session = null;
    b.classList.remove('on');
  });
  return b;
}

// ---------- Market ----------
function viewMarket() {
  const p = state.profile;
  const cur = currency();
  const board = h('div', { class: 'stack' });
  const draw = () => {
    if (!state.prices) { fill(board, h('div', { class: 'card busy' }, h('span', { class: 'spin' }))); return; }
    const rows = store.priceBoard(state.prices, { lat: p.lat, lng: p.lng, radiusKm: Math.max(settings.outbreakRadiusKm, 60), currency: cur, windowDays: settings.priceWindowDays });
    const mine = new Set(p.crops || []);
    rows.sort((a, b) => Number(mine.has(b.crop)) - Number(mine.has(a.crop)));
    fill(board, 
      navigator.onLine ? null : h('p', { class: 'note', text: t('mk_offline') }),
      rows.length ? h('div', { class: 'prices' }, rows.map((r) => h('div', { class: `price ${mine.has(r.crop) ? 'mine' : ''}` },
        h('div', { class: 'row between' }, h('strong', {}, h('i', { class: 'dot', style: { background: CROPS[r.crop]?.color } }), cropName(r.crop, getLang())), h('small', { class: 'muted', text: `/ ${t(`unit_${r.unit}`)}` })),
        h('div', { class: 'pbig', text: money(r.median, cur) }),
        h('small', {}, `${t('mk_reports', { n: r.n })} · ${t('mk_range', { min: money(r.min, cur), max: money(r.max, cur) })}`),
        r.trend == null ? null : h('small', { class: r.trend > 2 ? 'up' : r.trend < -2 ? 'down' : '', text: r.trend > 2 ? t('mk_trend_up', { p: r.trend }) : r.trend < -2 ? t('mk_trend_down', { p: -r.trend }) : t('mk_trend_flat') }),
        r.best && r.n > 1 ? h('small', { class: 'best' }, icon('pin'), t('mk_best', { market: r.best.market, price: money(r.best.median, cur) })) : null))) : h('p', { class: 'card muted', text: t('mk_none') }));
  };
  live.set('market', draw);
  draw();

  const f = { crop: p.crops?.[0] || 'plantain', unit: 'lb' };
  const price = h('input', { type: 'number', min: '0', step: '0.01', inputmode: 'decimal', required: true });
  const unit = h('select', {}, PRICE_UNITS.map((u) => h('option', { value: u, text: t(`unit_${u}`) })));
  const market = h('input', { type: 'text', maxlength: '40' });
  const msg = h('p', { class: 'muted small' });
  const send = h('button', { class: 'btn primary', type: 'button' }, icon('send'), t('mk_send'));
  send.addEventListener('click', async () => {
    const v = Number(price.value);
    if (!(v > 0) || v > 1e7) { msg.textContent = t('mk_bad'); return; }
    send.disabled = true;
    const row = { crop: f.crop, unit: unit.value, price: v, currency: cur, market: market.value, country: p.country, cell: store.cellOf(p.lat, p.lng) };
    try {
      const pending = store.addPrice(state.user.uid, row);
      state.prices = [{ ...row, day: store.today(), id: `local-${Date.now()}` }, ...(state.prices || [])];
      draw();
      msg.textContent = t('mk_sent');
      price.value = ''; market.value = '';
      track('price_report', { crop: f.crop, unit: row.unit });
      await pending;
    } catch (e) { msg.textContent = t('err_generic', { msg: e.code || e.message }); }
    send.disabled = false;
  });
  return h('section', { class: 'page' },
    h('header', { class: 'pagehead' }, h('h1', { text: t('mk_title') })),
    h('p', { class: 'lead', text: t('mk_sub', { d: settings.priceWindowDays, km: Math.max(settings.outbreakRadiusKm, 60) }) }),
    board,
    h('div', { class: 'card stack' },
      h('h2', {}, icon('scale'), t('mk_report')),
      cropChips(f.crop, (v) => { f.crop = v; }, { only: [...new Set([...(p.crops || []), ...CROP_KEYS.filter((k) => k !== 'other')])].slice(0, 12) }),
      h('div', { class: 'row wrap' },
        h('label', { class: 'grow' }, `${t('mk_price')} (${cur})`, price),
        h('label', {}, t('mk_unit'), unit)),
      h('label', {}, t('mk_market'), market),
      send, msg));
}

// ---------- Map ----------
function viewMap() {
  const p = state.profile;
  const mapEl = h('div', { class: 'map', id: 'map' });
  const list = h('div', { class: 'card' });
  const draw = () => {
    const rows = state.outbreaks || [];
    const near = rows.filter((r) => store.distanceKm([p.lat, p.lng], [r.lat, r.lng]) <= settings.outbreakRadiusKm);
    if (navigator.onLine) {
      import('./map.js').then((m) => m.showMap(mapEl, { lat: p.lat, lng: p.lng, rows, lang: getLang(), radiusKm: settings.outbreakRadiusKm })).catch(() => {});
    } else fill(mapEl, h('p', { class: 'muted', text: t('mp_offline') }));
    const groups = store.outbreakAlerts(rows, { lat: p.lat, lng: p.lng, radiusKm: settings.outbreakRadiusKm, min: 1, crops: null });
    fill(list, h('h2', {}, icon('pin'), t('mp_list', { km: settings.outbreakRadiusKm })),
      near.length ? h('ul', { class: 'hist' }, groups.map((g) => h('li', { class: p.crops?.includes(g.crop) ? 'mine' : '' },
        h('i', { class: 'dot', style: { background: CAT_COLOR[g.category] } }),
        h('span', {}, h('strong', { text: problemName(g.problemKey, getLang()) }), ` · ${cropName(g.crop, getLang())}`),
        h('small', { text: `${g.n}× · ${g.nearest} km · ${g.last}` })))) : h('p', { class: 'muted', text: t('mp_none') }));
  };
  live.set('map', draw);
  setTimeout(draw, 0);
  return h('section', { class: 'page' },
    h('header', { class: 'pagehead' }, h('h1', { text: t('mp_title') })),
    h('p', { class: 'lead', text: t('mp_sub') }),
    mapEl,
    h('div', { class: 'legend' }, CATEGORIES.filter((c) => c !== 'healthy').map((c) => h('span', {}, h('i', { class: 'dot', style: { background: CAT_COLOR[c] } }), t(`cat_${c}`)))),
    list);
}

// ---------- Farm ----------
function viewFarm() {
  const p = state.profile;
  const uid = state.user.uid;
  const cur = currency();
  const plots = h('div', { class: 'card' });
  const logs = h('div', { class: 'card' });
  const totals = h('div', { class: 'totals' });

  const drawPlots = () => {
    const unit = p.areaUnit || 'ha';
    fill(plots, h('div', { class: 'row between' }, h('h2', {}, icon('leaf'), t('f_plots')), h('button', { class: 'btn ghost sm', type: 'button', onclick: () => plotForm() }, icon('plus'), t('f_add_plot'))),
      h('div', { id: 'plotform' }),
      state.plots.length ? h('ul', { class: 'plots' }, state.plots.map((x) => {
        const age = x.planted ? Math.max(0, Math.round((Date.now() - new Date(`${x.planted}T12:00`)) / 86400000)) : null;
        return h('li', {}, h('i', { class: 'dot', style: { background: CROPS[x.crop]?.color } }),
          h('span', {}, h('strong', { text: x.name }), ` · ${cropName(x.crop, getLang())}`),
          h('small', { text: [x.area ? `${Math.round((x.area / AREA_UNITS[unit]) * 10) / 10} ${t(`area_${unit}`)}` : '', age != null ? (age >= 365 ? `${Math.floor(age / 365)} ${getLang() === 'es' ? 'años' : 'yr'}` : t('f_age', { d: age })) : ''].filter(Boolean).join(' · ') }));
      })) : h('p', { class: 'muted', text: t('f_no_plots') }));
  };
  const plotForm = () => {
    const box = plots.querySelector('#plotform');
    const d = { crop: p.crops?.[0] || 'other' };
    const name = h('input', { type: 'text', maxlength: '40', required: true });
    const area = h('input', { type: 'number', min: '0', step: '0.1', inputmode: 'decimal' });
    const planted = h('input', { type: 'date', max: store.today() });
    const unit = p.areaUnit || 'ha';
    fill(box, h('div', { class: 'stack sub' },
      h('label', {}, t('f_plot_name'), name),
      cropChips(d.crop, (v) => { d.crop = v; }, { only: [...new Set([...(p.crops || []), 'other'])] }),
      h('div', { class: 'row wrap' }, h('label', { class: 'grow' }, `${t('f_area')} (${t(`area_${unit}`)})`, area), h('label', { class: 'grow' }, t('f_planted'), planted)),
      h('div', { class: 'row' },
        h('button', { class: 'btn primary', type: 'button', onclick: async () => {
          if (!name.value.trim()) { name.focus(); return; }
          store.addPlot(uid, { name: name.value, crop: d.crop, area: Number(area.value || 0) * AREA_UNITS[unit], planted: planted.value }).catch(() => {});
          track('plot_add', { crop: d.crop });
          fill(box);
        } }, t('f_save')),
        h('button', { class: 'btn ghost', type: 'button', onclick: () => fill(box) }, t('f_cancel')))));
    name.focus();
  };

  const drawLogs = () => {
    const month = store.today().slice(0, 7);
    const inMonth = state.logs.filter((x) => (x.date || '').startsWith(month));
    const earned = inMonth.filter((x) => x.kind === 'sell').reduce((s, x) => s + (x.money || 0), 0);
    const spent = inMonth.filter((x) => x.kind !== 'sell').reduce((s, x) => s + (x.money || 0), 0);
    fill(totals, h('div', {}, h('small', { text: `${t('f_month')} · ${t('f_spent')}` }), h('strong', { text: money(spent, cur) })),
      h('div', {}, h('small', { text: `${t('f_month')} · ${t('f_earned')}` }), h('strong', { class: 'up', text: money(earned, cur) })));
    const plotName = (id) => state.plots.find((x) => x.id === id)?.name || '';
    fill(logs, h('h2', {}, icon('book'), t('f_log')),
      logForm(),
      state.logs.length ? h('ul', { class: 'log' }, state.logs.map((x) => {
        const dAgo = Math.round((Date.now() - new Date(`${x.date}T12:00`)) / 86400000);
        return h('li', {},
          h('span', { class: `kind k-${x.kind}`, text: t(`k_${x.kind}`) }),
          h('div', {}, h('p', { text: x.note || '' }),
            h('small', { class: 'muted', text: [plotName(x.plotId), x.qty ? `${x.qty} ${x.unit || ''}` : '', x.money ? money(x.money, cur) : '', dAgo <= 0 ? t('f_today') : t('f_days', { d: dAgo })].filter(Boolean).join(' · ') }),
            x.pending ? h('small', { class: 'pending' }, icon('sync'), t('f_pending')) : null),
          h('button', { class: 'btn ghost sm del', type: 'button', 'aria-label': t('f_delete'), onclick: () => store.deleteLog(uid, x.id) }, icon('x')));
      })) : h('p', { class: 'muted', text: t('f_no_log') }));
  };
  let logDraft = { kind: 'scout', plotId: '' };
  const logForm = () => {
    const note = h('textarea', { rows: '2', maxlength: '300' });
    const qty = h('input', { type: 'number', min: '0', step: '0.1', inputmode: 'decimal' });
    const money2 = h('input', { type: 'number', min: '0', step: '1', inputmode: 'decimal' });
    const plot = h('select', {}, h('option', { value: '', text: t('f_any_plot') }), state.plots.map((x) => h('option', { value: x.id, text: x.name, selected: logDraft.plotId === x.id })));
    plot.addEventListener('change', () => { logDraft.plotId = plot.value; });
    const kinds = h('div', { class: 'chips' }, store.LOG_KINDS.map((k) => h('button', { type: 'button', class: `pick ${logDraft.kind === k ? 'on' : ''}`, onclick: (e) => {
      logDraft.kind = k; kinds.querySelectorAll('.pick').forEach((b) => b.classList.remove('on')); e.currentTarget.classList.add('on');
    } }, t(`k_${k}`))));
    let via = 'text';
    return h('details', { class: 'sub' }, h('summary', { class: 'btn ghost sm' }, icon('plus'), t('f_add_log')),
      h('div', { class: 'stack' },
        h('span', { class: 'label', text: t('f_kind') }), kinds,
        h('div', { class: 'field' }, h('span', { class: 'label', text: t('f_note') }), h('div', { class: 'row' }, note, dictateButton(note, () => { via = 'voice'; }))),
        h('div', { class: 'row wrap' }, h('label', { class: 'grow' }, t('f_qty'), qty), h('label', { class: 'grow' }, `${t('f_money')} (${cur})`, money2), h('label', { class: 'grow' }, t('f_plot'), plot)),
        h('button', { class: 'btn primary', type: 'button', onclick: () => {
          if (!note.value.trim() && !qty.value && !money2.value) { note.focus(); return; }
          store.addLog(uid, { kind: logDraft.kind, note: note.value, qty: qty.value, money: money2.value, plotId: logDraft.plotId, via }).catch(() => {});
          track('log_add', { kind: logDraft.kind, via });
        } }, t('f_save'))));
  };
  live.set('plots', () => { drawPlots(); drawLogs(); });
  live.set('logs', drawLogs);
  drawPlots(); drawLogs();
  return h('section', { class: 'page' }, h('header', { class: 'pagehead' }, h('h1', { text: t('f_title') })), totals, plots, logs);
}

// ---------- Ask ----------
const chat = [];
function viewAsk() {
  const p = state.profile;
  const list = h('div', { class: 'chat', 'aria-live': 'polite' });
  const q = h('textarea', { rows: '2', maxlength: '600', placeholder: t('a_ph') });
  const fresh = h('input', { type: 'checkbox' });
  const hint = h('p', { class: 'note hidden', text: t('a_fresh_hint') });
  const voiceInfo = h('p', { class: 'muted small' });
  q.addEventListener('input', () => hint.classList.toggle('hidden', !(looksFresh(q.value) && !fresh.checked && settings.grounding)));
  fresh.addEventListener('change', () => hint.classList.toggle('hidden', true));

  const draw = () => fill(list, ...chat.map((m) => {
    if (m.role === 'user') return h('div', { class: 'msg me', text: m.text });
    if (m.busy) return h('div', { class: 'msg bot' }, h('span', { class: 'spin' }), t('a_busy'));
    if (m.error) return h('div', { class: 'msg bot err', text: m.error });
    const frame = m.searchHtml ? h('iframe', { class: 'search', sandbox: 'allow-popups allow-popups-to-escape-sandbox', title: 'Google Search', referrerpolicy: 'no-referrer' }) : null;
    if (frame) frame.srcdoc = m.searchHtml;
    return h('div', { class: 'msg bot' },
      h('div', { class: 'row between' }, badge(m.grounded ? 'grounded' : m.source, 'text'), speakButton(() => m.text)),
      h('p', { class: 'answer', text: m.text }),
      m.filtered ? h('p', { class: 'note' }, icon('shield'), t('d_filtered')) : null,
      m.sources?.length ? h('div', { class: 'sources' }, h('small', { text: t('a_sources') }),
        h('ul', {}, m.sources.map((s) => h('li', {}, h('a', { href: s.uri, target: '_blank', rel: 'noopener noreferrer', text: s.title }))))) : null,
      frame,
      traceBox(m.trace));
  }));
  const send = async (text) => {
    const question = String(text || q.value).trim();
    if (!question) return;
    q.value = '';
    hint.classList.add('hidden');
    chat.push({ role: 'user', text: question });
    const slot = { role: 'bot', busy: true };
    chat.push(slot);
    draw();
    const a = state.analysis;
    const recent = state.diags[0] ? `${state.diags[0].problem} on ${cropName(state.diags[0].crop, 'en')}` : '';
    try {
      const r = await askConuco({
        question, lang: getLang(), fresh: fresh.checked,
        context: { crops: p.crops, place: [p.place.split(',').slice(-1)[0]?.trim(), p.country].filter(Boolean).join(', '), weather: a?.now ? `${a.now.temp} °C, humidity ${a.now.rh} %, rain chance ${a.now.prob} %` : '', recent },
      });
      Object.assign(slot, { busy: false, ...r });
      track('ask', { source: r.source, grounded: r.grounded ? 1 : 0 });
    } catch (e) {
      Object.assign(slot, { busy: false, error: e.message === 'timeout' ? t('a_err', { msg: 'timeout' }) : (navigator.onLine ? t('a_err', { msg: e.code || e.message }) : t('a_need')) });
    }
    draw();
    list.lastChild?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const mic = h('button', { class: 'btn primary mic big', type: 'button', 'aria-label': t('a_mic') }, icon('mic'), h('span', { text: t('a_mic') }));
  let session = null;
  mic.addEventListener('click', async () => {
    if (session) { session.stop(); return; }
    const mode = await listenMode(getLang());
    if (mode === 'none') { voiceInfo.textContent = t('a_voice_none'); return; }
    mic.classList.add('on');
    session = listen(getLang(), { local: mode === 'local', onPartial: (txt) => { q.value = txt; } });
    try {
      const r = await session.done;
      voiceInfo.textContent = r.local ? t('a_voice_local') : t('a_voice_cloud');
      track('voice_input', { local: r.local ? 1 : 0 });
      if (r.text) send(r.text);
    } catch (e) { if (e.message !== 'no-speech' && e.message !== 'aborted') voiceInfo.textContent = t('err_generic', { msg: e.message }); }
    session = null;
    mic.classList.remove('on');
  });
  listenMode(getLang()).then((m) => {
    if (m === 'local-downloadable') {
      fill(voiceInfo, h('button', { class: 'btn ghost sm', type: 'button', onclick: async () => { await installLocalSpeech(getLang()); voiceInfo.textContent = t('a_voice_local'); } }, icon('download'), t('a_voice_install')));
    } else if (m === 'local') voiceInfo.textContent = t('a_voice_local');
  });
  draw();
  return h('section', { class: 'page ask' },
    h('header', { class: 'pagehead' }, h('h1', { text: t('a_title') })),
    h('p', { class: 'lead', text: t('a_sub') }),
    chat.length ? null : h('div', { class: 'examples' }, h('small', { text: t('a_examples') }), ['ex1', 'ex2', 'ex3'].map((k) => h('button', { type: 'button', class: 'pick', onclick: () => { if (k === 'ex3') fresh.checked = true; send(t(k)); } }, t(k)))),
    list,
    h('div', { class: 'composer card' },
      h('div', { class: 'row' }, q, h('button', { class: 'btn primary', type: 'button', onclick: () => send() }, icon('send'), h('span', { class: 'hide-sm', text: t('a_send') }))),
      hint,
      settings.grounding ? h('label', { class: 'check small' }, fresh, icon('search'), t('a_fresh')) : null,
      h('div', { class: 'row center' }, canListen() ? mic : null), voiceInfo));
}

// ---------- Settings ----------
function viewSettings() {
  const p = { ...state.profile };
  const uid = state.user.uid;
  const nano = h('div', { class: 'stack' });
  const drawNano = () => {
    const st = state.nano;
    if (st === 'unsupported' || st === 'unavailable') { fill(nano, h('p', { class: 'muted', text: t('s_no_api') })); return; }
    if (st === 'available') { fill(nano, h('p', { class: 'good' }, icon('check'), t('s_ready'))); return; }
    const b = h('button', { class: 'btn primary', type: 'button' }, icon('download'), t('s_download'));
    b.addEventListener('click', async () => {
      b.disabled = true;
      const ok = await prepareDevice((e) => { fill(b, h('span', { class: 'spin' }), t('s_downloading', { p: Math.round((e.loaded || 0) * 100) })); }, getLang());
      track('nano_download', { ok: ok ? 1 : 0 });
      refreshNano();
    });
    fill(nano, b);
  };
  live.set('nano', drawNano);
  drawNano();

  const placeOut = h('span', { text: p.place });
  const q = h('input', { type: 'search', placeholder: t('o_search') });
  const results = h('div', { class: 'results' });
  let tmr;
  q.addEventListener('input', () => {
    clearTimeout(tmr);
    tmr = setTimeout(async () => {
      if (q.value.trim().length < 2) return;
      const list = await searchPlace(q.value.trim(), getLang()).catch(() => []);
      fill(results, ...list.map((x) => h('button', { type: 'button', class: 'result', onclick: () => {
        Object.assign(p, { place: [x.name, x.admin].filter(Boolean).join(', '), lat: x.lat, lng: x.lng, country: x.country });
        placeOut.textContent = p.place; fill(results); q.value = '';
      } }, h('strong', { text: x.name }), ` ${x.admin} · ${x.country}`)));
    }, 350);
  });
  const share = h('input', { type: 'checkbox', checked: p.shareOutbreaks !== false });
  const areaSel = h('select', {}, Object.keys(AREA_UNITS).map((u) => h('option', { value: u, text: t(`area_${u}`), selected: p.areaUnit === u })));
  const saveMsg = h('span', { class: 'muted small' });
  const save = h('button', { class: 'btn primary', type: 'button' }, t('s_save'));
  save.addEventListener('click', async () => {
    p.lang = getLang(); p.shareOutbreaks = share.checked; p.areaUnit = areaSel.value;
    if (!p.crops?.length) return;
    await store.saveProfile(uid, p).catch(() => {});
    state.profile = { ...state.profile, ...p };
    try { localStorage.setItem('conuco.lang', p.lang); } catch { /* blocked */ }
    saveMsg.textContent = t('s_saved');
    state.analysis = state.wx ? analyseWeather(state.wx, p.crops) : null;
    renderShell(); refreshWeather(); refreshNano();
  });

  const user = state.user;
  const acct = h('div', { class: 'stack' });
  if (user.isAnonymous) {
    const email = h('input', { type: 'email', autocomplete: 'username', placeholder: t('w_email') });
    const pass = h('input', { type: 'password', autocomplete: 'new-password', minlength: '6', placeholder: t('w_password') });
    const err = h('p', { class: 'err' });
    fill(acct, h('p', { class: 'muted', text: t('s_anon') }), email, pass, err,
      h('button', { class: 'btn ghost', type: 'button', onclick: async () => {
        try {
          await linkWithCredential(user, EmailAuthProvider.credential(email.value.trim(), pass.value));
          track('sign_up', { method: 'email_link' });
          route();
        } catch (e) { err.textContent = t('w_err', { msg: (e.code || e.message).replace('auth/', '') }); }
      } }, t('s_link')));
  } else {
    fill(acct, h('p', { text: t('s_linked', { email: user.email || '' }) }));
  }
  acct.append(h('button', { class: 'btn ghost', type: 'button', onclick: () => signOut(auth) }, t('s_signout')));

  const rc = Object.entries({
    conuco_cloud_model: settings.cloudModel, conuco_device_languages_web: settings.deviceLanguages.join(','),
    conuco_confidence_floor: settings.confidenceFloor, conuco_cloud_photos_allowed: settings.cloudPhotos,
    conuco_grounding_enabled: settings.grounding, conuco_outbreak_radius_km: settings.outbreakRadiusKm,
    conuco_outbreak_alert_min: settings.outbreakAlertMin, conuco_price_window_days: settings.priceWindowDays,
    conuco_spray_max_wind_kmh: settings.sprayMaxWindKmh,
  });
  return h('section', { class: 'page' },
    h('header', { class: 'pagehead' }, h('h1', { text: t('s_title') })),
    h('div', { class: 'card stack' }, h('h2', {}, icon('home'), t('s_profile')),
      h('div', { class: 'field' }, h('span', { class: 'label', text: t('s_lang') }), langSwitch(() => { p.lang = getLang(); route(); })),
      h('div', { class: 'field' }, h('span', { class: 'label', text: t('s_place') }), h('p', {}, icon('pin'), placeOut), q, results),
      h('div', { class: 'field' }, h('span', { class: 'label', text: t('s_crops') }), cropChips(p.crops || [], (v) => { p.crops = v; }, { multi: true })),
      h('label', { class: 'field' }, h('span', { class: 'label', text: t('s_area_unit') }), areaSel),
      h('label', { class: 'check' }, share, t('s_share')),
      h('div', { class: 'row' }, save, saveMsg)),
    h('div', { class: 'card stack' }, h('h2', {}, icon('chip'), t('s_ai')), nano),
    h('div', { class: 'card stack' }, h('h2', {}, icon('people'), t('s_account')), acct,
      h('button', { class: 'btn ghost', type: 'button', onclick: async (e) => { e.currentTarget.disabled = true; await store.seedSampleFarm(uid); toast(t('s_sample_done')); } }, t('s_sample'))),
    h('details', { class: 'card' }, h('summary', {}, icon('gear'), t('s_rc', { src: settings.source })),
      h('table', { class: 'rc' }, rc.map(([k, v]) => h('tr', {}, h('td', { text: k }), h('td', { text: String(v) }))))),
    h('div', { class: 'card' }, h('h2', {}, icon('shield'), t('s_privacy')), h('p', { text: t('s_privacy_text') })));
}
