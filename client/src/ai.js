// Conuco's hybrid AI engine (Firebase AI Logic).
// Gemini Nano on the device first. Gemini in the cloud only when it adds something and only with consent:
//  - crop doctor: on-device diagnosis; a cloud "second opinion" when Nano is unsure, after the farmer says yes,
//    with the photo re-encoded so no GPS or phone data leaves the device,
//  - farm brief: built from rule results (numbers), explained on the device,
//  - Ask Conuco: on the device; "fresh answers" use the cloud with Google Search grounding, on request.
// Every call returns a trace: the steps the router took and why, which the app shows to the farmer.
import { getGenerativeModel, InferenceMode, InferenceSource, Schema } from 'firebase/ai';
import { ai, settings, track } from './firebase.js';
import { CATEGORIES, CROPS, PROBLEMS, cropName } from './crops.js';
import { safeText } from './rules.js';

const AI_TIMEOUT_MS = 150000; // an on-device photo read on a CPU-only laptop can take about a minute
const LANG_NAME = { en: 'English', es: 'Spanish' };
const SEVERITY = ['low', 'medium', 'high'];

// ---------- Schemas (one shape, two engines) ----------
const DIAG_DEVICE = {
  type: 'object',
  properties: {
    is_plant: { type: 'boolean' },
    crop: { type: 'string' },
    healthy: { type: 'boolean' },
    problem: { type: 'string' },
    category: { type: 'string', enum: CATEGORIES },
    confidence: { type: 'number', minimum: 0, maximum: 1 },
    severity: { type: 'string', enum: SEVERITY },
    signs: { type: 'array', maxItems: 4, items: { type: 'string' } },
    actions_now: { type: 'array', maxItems: 4, items: { type: 'string' } },
    prevent: { type: 'array', maxItems: 3, items: { type: 'string' } },
    see_expert: { type: 'boolean' },
  },
  required: ['is_plant', 'healthy', 'problem', 'category', 'confidence', 'severity', 'signs', 'actions_now'],
};
const DIAG_CLOUD = Schema.object({
  properties: {
    is_plant: Schema.boolean(),
    crop: Schema.string(),
    healthy: Schema.boolean(),
    problem: Schema.string(),
    category: Schema.enumString({ enum: CATEGORIES }),
    confidence: Schema.number(),
    severity: Schema.enumString({ enum: SEVERITY }),
    signs: Schema.array({ items: Schema.string() }),
    actions_now: Schema.array({ items: Schema.string() }),
    prevent: Schema.array({ items: Schema.string() }),
    see_expert: Schema.boolean(),
  },
  optionalProperties: ['crop', 'prevent', 'see_expert'],
});
const BRIEF_DEVICE = {
  type: 'object',
  properties: { headline: { type: 'string' }, steps: { type: 'array', maxItems: 3, items: { type: 'string' } } },
  required: ['headline', 'steps'],
};
const BRIEF_CLOUD = Schema.object({ properties: { headline: Schema.string(), steps: Schema.array({ items: Schema.string() }) } });

const SHAPES = {
  diag: { device: DIAG_DEVICE, cloud: DIAG_CLOUD, image: true, tokens: 900, temp: 0.1 },
  brief: { device: BRIEF_DEVICE, cloud: BRIEF_CLOUD, image: false, tokens: 400, temp: 0.3 },
  text: { device: null, cloud: null, image: false, tokens: 700, temp: 0.4 },
};

const models = new Map();
const hasPromptApi = () => typeof globalThis.LanguageModel !== 'undefined';
const deviceLang = (lang) => (settings.deviceLanguages.includes(lang) ? lang : 'en');
const canAnswerOnDevice = (lang) => settings.deviceLanguages.includes(lang);

function model(mode, shape, lang, { grounded = false } = {}) {
  const key = `${mode}|${shape}|${lang}|${settings.cloudModel}|${grounded}`;
  if (!models.has(key)) {
    const s = SHAPES[shape];
    const out = deviceLang(lang);
    const inputs = [{ type: 'text', languages: [...new Set(['en', out])] }];
    if (s.image) inputs.push({ type: 'image' });
    const generationConfig = { temperature: s.temp, maxOutputTokens: s.tokens };
    if (s.cloud) Object.assign(generationConfig, { responseMimeType: 'application/json', responseSchema: s.cloud });
    const params = {
      mode,
      inCloudParams: { model: settings.cloudModel, generationConfig },
      onDeviceParams: {
        createOptions: { expectedInputs: inputs, expectedOutputs: [{ type: 'text', languages: [out] }] },
        ...(s.device ? { promptOptions: { responseConstraint: s.device } } : {}),
      },
    };
    if (grounded) params.inCloudParams.tools = [{ googleSearch: {} }];
    models.set(key, getGenerativeModel(ai, params));
  }
  return models.get(key);
}

/** 'available' | 'downloadable' | 'downloading' | 'unavailable' | 'unsupported' */
export async function deviceStatus(withImage = false, lang = 'en') {
  if (!hasPromptApi()) return 'unsupported';
  try {
    const l = deviceLang(lang);
    const expectedInputs = [{ type: 'text', languages: [...new Set(['en', l])] }];
    if (withImage) expectedInputs.push({ type: 'image' });
    return await globalThis.LanguageModel.availability({ expectedInputs, expectedOutputs: [{ type: 'text', languages: [l] }] });
  } catch {
    return 'unavailable';
  }
}

/** Starts the Gemini Nano download (with image input). Must run from a click. */
export async function prepareDevice(onProgress, lang = 'en') {
  if (!hasPromptApi()) return false;
  try {
    await model(InferenceMode.PREFER_ON_DEVICE, 'diag', lang).initializeDeviceModel(onProgress);
    return true;
  } catch {
    return false;
  }
}

function withTimeout(p, ms) {
  let t;
  return Promise.race([p.finally(() => clearTimeout(t)), new Promise((_, rej) => { t = setTimeout(() => rej(new Error('timeout')), ms); })]);
}

function parseJson(text) {
  const t = String(text || '').trim().replace(/^```(?:json)?\s*|\s*```$/g, '');
  try {
    return JSON.parse(t);
  } catch {
    const a = t.indexOf('{');
    const b = t.lastIndexOf('}');
    if (a >= 0 && b > a) return JSON.parse(t.slice(a, b + 1));
    throw new Error('unreadable');
  }
}

export const modeName = (m) => ({
  [InferenceMode.PREFER_ON_DEVICE]: 'PREFER_ON_DEVICE',
  [InferenceMode.ONLY_ON_DEVICE]: 'ONLY_ON_DEVICE',
  [InferenceMode.PREFER_IN_CLOUD]: 'PREFER_IN_CLOUD',
  [InferenceMode.ONLY_IN_CLOUD]: 'ONLY_IN_CLOUD',
}[m] || String(m));

async function run(mode, shape, parts, lang, feature, opts) {
  const t0 = performance.now();
  const res = await withTimeout(model(mode, shape, lang, opts).generateContent(parts), AI_TIMEOUT_MS);
  const ms = Math.round(performance.now() - t0);
  const source = res.response.inferenceSource === InferenceSource.ON_DEVICE ? 'on_device' : 'in_cloud';
  track('ai_call', { feature, source, ms, mode: modeName(mode) });
  return { res: res.response, source, ms };
}

// ---------- Crop doctor ----------

function diagPrompt(crop, note, lang) {
  const c = CROPS[crop];
  const watch = (c?.watch || []).map((k) => PROBLEMS[k]?.en).filter(Boolean).join(', ');
  return [
    'You are Conuco, a careful plant-health assistant for small farmers.',
    `Look at the photo. The farmer says it is: ${crop && crop !== 'other' ? cropName(crop, 'en') : 'a crop plant'}.`,
    watch ? `Common problems of this crop: ${watch}. Only name one of them if the photo really shows its signs.` : '',
    note ? `Farmer's note: "${String(note).slice(0, 300)}"` : '',
    'Rules:',
    '- If the photo does not show a plant, set is_plant to false.',
    '- Name the most likely problem in common words (problem). If the plant looks healthy, set healthy true and problem "Healthy".',
    '- confidence is your honest probability from 0 to 1. Use less than 0.5 when the photo is unclear or signs fit several problems.',
    '- signs: what you see in this photo. actions_now: free, practical steps first (remove and destroy sick leaves, improve drainage and air flow, clean tools, separate sick plants, scout neighbours).',
    '- Never write chemical doses, mixing ratios or brand names. If a pesticide may be needed, say: use a product registered for this crop in your country and follow the label.',
    '- see_expert is true when severity is high, when you are unsure, or when the problem spreads fast.',
    `Write every text field in ${LANG_NAME[lang] || 'English'}. Short sentences a farmer can act on.`,
  ].filter(Boolean).join('\n');
}

function cleanDiagnosis(raw, lang) {
  const list = (v, n) => (Array.isArray(v) ? v : []).map((x) => safeText(String(x).trim(), lang)).filter((x) => x.text).slice(0, n);
  const signs = list(raw?.signs, 4);
  const actions = list(raw?.actions_now, 4);
  const prevent = list(raw?.prevent, 3);
  const conf = Math.max(0, Math.min(1, Number(raw?.confidence) || 0));
  const healthy = Boolean(raw?.healthy);
  const category = healthy ? 'healthy' : (CATEGORIES.includes(raw?.category) ? raw.category : 'unknown');
  const problem = safeText(String(raw?.problem || '').trim().slice(0, 80), lang).text || (healthy ? 'Healthy' : '');
  return {
    isPlant: raw?.is_plant !== false,
    crop: String(raw?.crop || '').slice(0, 40),
    healthy,
    problem,
    problemKey: matchProblem(problem),
    category,
    confidence: Math.round(conf * 100) / 100,
    severity: SEVERITY.includes(raw?.severity) ? raw.severity : 'medium',
    signs: signs.map((x) => x.text),
    actions: actions.map((x) => x.text),
    prevent: prevent.map((x) => x.text),
    seeExpert: Boolean(raw?.see_expert) || raw?.severity === 'high' || conf < 0.5,
    filtered: [...signs, ...actions, ...prevent].some((x) => x.changed),
  };
}

/** Maps a free-text problem name to a known key (for the outbreak map). */
export function matchProblem(name) {
  const n = String(name || '').toLowerCase();
  if (!n) return 'other';
  let best = 'other';
  let bestLen = 0;
  for (const [k, p] of Object.entries(PROBLEMS)) {
    for (const label of [p.en, p.es, k.replace(/_/g, ' ')]) {
      const words = label.toLowerCase().split(/[\s(),/]+/).filter((w) => w.length > 3);
      const hit = words.filter((w) => n.includes(w)).length;
      if (hit && hit * 10 + label.length > bestLen && hit >= Math.min(2, words.length)) { best = k; bestLen = hit * 10 + label.length; }
    }
  }
  return best;
}

/**
 * Diagnoses a plant photo.
 * mode: 'private' (device only) | 'smart' (device first, cloud second opinion with consent) | 'cloud' (cloud first, with consent).
 * askConsent(reasonCode) → Promise<boolean> shows the consent dialog.
 */
export async function diagnose({ photo, crop, note, lang, mode = 'smart', askConsent }) {
  const trace = [];
  const parts = [diagPrompt(crop, note, lang), { inlineData: { mimeType: photo.mimeType, data: photo.base64 } }];
  trace.push({ code: 'exif_removed', kb: Math.round(photo.bytes / 1024) });
  if (photo.quality.issues.length) trace.push({ code: 'photo_quality', issues: photo.quality.issues });

  const online = navigator.onLine;
  const dev = await deviceStatus(true, lang);
  const deviceOk = dev === 'available';
  const outLang = canAnswerOnDevice(lang) ? lang : 'en';
  if (!canAnswerOnDevice(lang)) trace.push({ code: 'lang_device_en', lang });

  let first = null;
  if (mode !== 'cloud') {
    if (deviceOk) {
      trace.push({ code: 'device_ready' });
      try {
        const r = await run(InferenceMode.ONLY_ON_DEVICE, 'diag', parts, outLang, 'diagnose');
        first = { ...r, data: cleanDiagnosis(parseJson(r.res.text()), outLang) };
        trace.push({ code: 'device_done', ms: r.ms, confidence: first.data.confidence });
      } catch (e) {
        trace.push({ code: 'device_failed', msg: String(e.message || e).slice(0, 80) });
      }
    } else {
      trace.push({ code: dev === 'downloading' || dev === 'downloadable' ? 'device_not_ready' : 'device_missing', status: dev });
    }
  }

  if (mode === 'private') {
    trace.push({ code: 'private_mode' });
    return { first, second: null, trace, fallback: first ? null : 'symptoms' };
  }

  const unsure = first && (first.data.confidence < settings.confidenceFloor || first.data.category === 'unknown' || !first.data.isPlant);
  const wantCloud = mode === 'cloud' || !first || unsure;
  if (!wantCloud) {
    trace.push({ code: 'confident', floor: settings.confidenceFloor });
    return { first, second: null, trace, fallback: null };
  }
  if (unsure) trace.push({ code: 'low_confidence', confidence: first.data.confidence, floor: settings.confidenceFloor });
  if (!online) {
    trace.push({ code: 'offline' });
    return { first, second: null, trace, fallback: first ? null : 'symptoms' };
  }
  if (!settings.cloudPhotos) {
    trace.push({ code: 'cloud_disabled' });
    return { first, second: null, trace, fallback: first ? null : 'symptoms' };
  }
  const reason = first ? 'second_opinion' : mode === 'cloud' ? 'cloud_first' : 'no_device';
  const yes = askConsent ? await askConsent(reason, first?.data.confidence || 0) : false;
  track('cloud_consent', { answer: yes ? 'yes' : 'no', reason });
  if (!yes) {
    trace.push({ code: 'consent_no' });
    return { first, second: null, trace, fallback: first ? null : 'symptoms' };
  }
  trace.push({ code: 'consent_yes' });
  try {
    const r = await run(InferenceMode.ONLY_IN_CLOUD, 'diag', [diagPrompt(crop, note, lang), parts[1]], lang, 'diagnose_cloud');
    const second = { ...r, data: cleanDiagnosis(parseJson(r.res.text()), lang) };
    trace.push({ code: 'cloud_done', ms: r.ms, confidence: second.data.confidence });
    if (first) trace.push({ code: agree(first.data, second.data) ? 'agree' : 'disagree' });
    return { first, second, trace, fallback: null };
  } catch (e) {
    trace.push({ code: 'cloud_failed', msg: String(e.message || e).slice(0, 80) });
    return { first, second: null, trace, fallback: first ? null : 'symptoms' };
  }
}

export function agree(a, b) {
  if (!a || !b) return false;
  if (a.healthy && b.healthy) return true;
  if (a.problemKey !== 'other' && a.problemKey === b.problemKey) return true;
  return a.category === b.category && a.category !== 'unknown';
}

// ---------- Farm brief (rules → plain words) ----------

/** facts: short English lines built from the rules (numbers already decided). */
export async function farmBrief({ facts, lang }) {
  const trace = [];
  const outLang = canAnswerOnDevice(lang) ? lang : 'en';
  const prompt = [
    'You are Conuco, a farm assistant. Turn these facts about today into advice for a small farmer.',
    'Use ONLY these facts. Do not add numbers that are not here. No chemical doses.',
    `Facts:\n${facts.map((f) => `- ${f}`).join('\n')}`,
    `Answer in ${LANG_NAME[outLang]}: a headline of at most 12 words, then up to 3 short steps in order of importance.`,
  ].join('\n');
  const online = navigator.onLine;
  const mode = online ? InferenceMode.PREFER_ON_DEVICE : InferenceMode.ONLY_ON_DEVICE;
  trace.push({ code: online ? 'prefer_device' : 'offline' });
  trace.push({ code: 'facts_only', n: facts.length });
  const r = await run(mode, 'brief', [prompt], outLang, 'brief');
  const d = parseJson(r.res.text());
  const steps = (Array.isArray(d.steps) ? d.steps : []).slice(0, 3).map((s) => safeText(String(s), outLang).text);
  trace.push({ code: r.source === 'on_device' ? 'device_done' : 'cloud_done', ms: r.ms });
  return { headline: safeText(String(d.headline || ''), outLang).text, steps, source: r.source, ms: r.ms, trace };
}

// ---------- Ask Conuco ----------

const FRESH_RE = /\b(precio|precios|price|prices|hoy|today|news|noticia|noticias|this week|esta semana|alerta|alert|outbreak|brote|subsid|programa|program|ley|law|registrad|registered|clima de|forecast|mercado|market)\b/i;
export const looksFresh = (q) => FRESH_RE.test(q);

/**
 * Answers a farming question. fresh=true → cloud with Google Search grounding (the farmer chose it).
 * context: {crops, place, weather} — region-level only; no names, no exact position.
 */
export async function askConuco({ question, context, lang, fresh = false }) {
  const trace = [];
  const q = String(question || '').trim().slice(0, 600);
  const ctx = [
    context?.crops?.length ? `Crops: ${context.crops.map((c) => cropName(c, 'en')).join(', ')}.` : '',
    context?.place ? `Region: ${context.place}.` : '',
    context?.weather ? `Weather now: ${context.weather}.` : '',
    context?.recent ? `Recent diagnosis: ${context.recent}.` : '',
  ].filter(Boolean).join(' ');
  const online = navigator.onLine;
  const groundedOk = fresh && online && settings.grounding;
  const outLang = groundedOk || canAnswerOnDevice(lang) ? lang : 'en';
  const prompt = [
    'You are Conuco, a practical assistant for small farmers. Answer in plain words, at most 120 words.',
    'Prefer low-cost, safe practices. Never give pesticide doses or mixing ratios; say to follow the product label and local extension advice.',
    'If you are not sure, say so and suggest asking a local agricultural extension officer.',
    ctx ? `Farmer context: ${ctx}` : '',
    `Answer in ${LANG_NAME[outLang]}.`,
    `Question: ${q}`,
  ].filter(Boolean).join('\n');

  let r;
  let grounded = groundedOk;
  if (groundedOk) {
    trace.push({ code: 'fresh_cloud' });
    try {
      r = await run(InferenceMode.ONLY_IN_CLOUD, 'text', [prompt], outLang, 'ask_grounded', { grounded: true });
    } catch (e) {
      // Search grounding needs a paid Gemini API tier; answer without it instead of failing.
      trace.push({ code: 'grounding_unavailable' });
      grounded = false;
    }
  }
  if (!r) {
    if (fresh && !online) trace.push({ code: 'offline' });
    else if (fresh && !settings.grounding) trace.push({ code: 'cloud_disabled' });
    trace.push({ code: online ? 'prefer_device' : 'offline_device' });
    r = await run(online ? InferenceMode.PREFER_ON_DEVICE : InferenceMode.ONLY_ON_DEVICE, 'text', [prompt], outLang, 'ask');
  }
  trace.push({ code: r.source === 'on_device' ? 'device_done' : 'cloud_done', ms: r.ms });
  const gm = r.res.candidates?.[0]?.groundingMetadata;
  const sources = (gm?.groundingChunks || []).map((c) => c.web).filter((w) => w?.uri)
    .slice(0, 5).map((w) => ({ title: String(w.title || w.uri).slice(0, 80), uri: w.uri }));
  const answer = safeText(r.res.text(), outLang);
  return {
    text: answer.text.trim(), filtered: answer.changed, source: r.source, ms: r.ms, trace, sources,
    searchHtml: gm?.searchEntryPoint?.renderedContent || '', grounded,
  };
}
