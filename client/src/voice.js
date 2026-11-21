// Voice in and out, on the device when the browser can.
// Input: Web Speech recognition. Chrome can run it on the device (processLocally), so the farmer's
// voice never leaves the phone; the app says which one it used. Output: speechSynthesis, preferring local voices.
const SR = globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition;
const tag = (lang) => (lang === 'es' ? 'es-ES' : 'en-US');

export const canListen = () => Boolean(SR);
export const canSpeak = () => typeof speechSynthesis !== 'undefined';

/** 'local' | 'local-downloadable' | 'cloud' | 'none' */
export async function listenMode(lang) {
  if (!SR) return 'none';
  try {
    if (typeof SR.available === 'function') {
      const a = await SR.available({ langs: [tag(lang)], processLocally: true });
      if (a === 'available') return 'local';
      if (a === 'downloadable' || a === 'downloading') return 'local-downloadable';
    }
  } catch { /* older Chrome */ }
  return navigator.onLine ? 'cloud' : 'none';
}

/** Installs the on-device speech pack (from a click). */
export async function installLocalSpeech(lang) {
  try {
    if (typeof SR?.install === 'function') return await SR.install({ langs: [tag(lang)], processLocally: true });
  } catch { /* not supported */ }
  return false;
}

/**
 * Listens once. Returns {stop, done: Promise<{text, local}>}. onPartial gets interim text.
 * allowCloud=false keeps it on the device or fails.
 */
export function listen(lang, { onPartial, allowCloud = true, local = false } = {}) {
  const rec = new SR();
  rec.lang = tag(lang);
  rec.interimResults = true;
  rec.continuous = false;
  rec.maxAlternatives = 1;
  let usedLocal = false;
  if (local && 'processLocally' in rec) { rec.processLocally = true; usedLocal = true; }
  if (!usedLocal && !allowCloud) return { stop() {}, done: Promise.reject(new Error('no-local-speech')) };
  let final = '';
  const done = new Promise((resolve, reject) => {
    rec.onresult = (e) => {
      let interim = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i];
        if (r.isFinal) final += r[0].transcript; else interim += r[0].transcript;
      }
      onPartial?.((final + ' ' + interim).trim());
    };
    rec.onerror = (e) => reject(new Error(e.error || 'speech-error'));
    rec.onend = () => resolve({ text: final.trim(), local: usedLocal });
  });
  rec.start();
  return { stop: () => { try { rec.stop(); } catch { /* ended */ } }, done };
}

let speaking = null;
export function speak(text, lang, onEnd) {
  if (!canSpeak()) return false;
  stopSpeaking();
  const u = new SpeechSynthesisUtterance(String(text).slice(0, 1200));
  u.lang = tag(lang);
  const voices = speechSynthesis.getVoices().filter((v) => v.lang?.toLowerCase().startsWith(lang));
  u.voice = voices.find((v) => v.localService) || voices[0] || null;
  u.rate = 0.98;
  u.onend = () => { speaking = null; onEnd?.(); };
  u.onerror = () => { speaking = null; onEnd?.(); };
  speaking = u;
  speechSynthesis.speak(u);
  return true;
}
export function stopSpeaking() {
  if (canSpeak()) speechSynthesis.cancel();
  speaking = null;
}
export const isSpeaking = () => Boolean(speaking);
