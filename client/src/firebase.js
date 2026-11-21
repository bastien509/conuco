// Firebase setup for Conuco: App Check, Auth, Firestore (offline-first), Remote Config, Analytics
// and Firebase AI Logic. These web config values are public identifiers, not secrets.
import { initializeApp } from 'firebase/app';
import { initializeAppCheck, ReCaptchaEnterpriseProvider } from 'firebase/app-check';
import { getAuth } from 'firebase/auth';
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager } from 'firebase/firestore';
import { initializeAnalytics, isSupported, logEvent, setUserProperties } from 'firebase/analytics';
import { getRemoteConfig, fetchAndActivate, getValue } from 'firebase/remote-config';
import { getAI, GoogleAIBackend } from 'firebase/ai';

export const firebaseConfig = {
  apiKey: 'AIzaSyBGmRvZH6VNltdZNH5QKatbnnqOiFfBDWM',
  authDomain: 'conuco-farm.firebaseapp.com',
  projectId: 'conuco-farm',
  storageBucket: 'conuco-farm.firebasestorage.app',
  messagingSenderId: '42753514920',
  appId: '1:42753514920:web:1416be517d8979cded519d',
  measurementId: 'G-CTC8BVT4NX',
};
const RECAPTCHA_SITE_KEY = '6LeSfCsuAAAAAN6A4dfXH0k81vio4VN_6-Iq6Sqy';

export const app = initializeApp(firebaseConfig);

// App Check: every Firebase AI Logic call carries a reCAPTCHA Enterprise attestation (enforced).
try {
  initializeAppCheck(app, { provider: new ReCaptchaEnterpriseProvider(RECAPTCHA_SITE_KEY), isTokenAutoRefreshEnabled: true });
} catch { /* already initialised */ }

export const auth = getAuth(app);

// Offline-first: the farm log, plots, tasks and the last market/outbreak data stay readable and
// writable with no signal; Firestore syncs when the phone reconnects.
export const db = initializeFirestore(app, {
  localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
});

export const ai = getAI(app, { backend: new GoogleAIBackend() });

/** Settings that change without a new build (Remote Config). In-app defaults first. */
export const settings = {
  cloudModel: 'gemini-3.5-flash-lite',
  deviceLanguages: ['en', 'es'],
  confidenceFloor: 0.65,
  cloudPhotos: true,
  grounding: false, // Google Search grounding needs a paid Gemini API tier; Remote Config turns it on
  outbreakRadiusKm: 30,
  outbreakAlertMin: 2,
  priceWindowDays: 14,
  sprayMaxWindKmh: 15,
  source: 'defaults',
};
const RC_DEFAULTS = {
  conuco_cloud_model: settings.cloudModel,
  conuco_device_languages_web: settings.deviceLanguages.join(','),
  conuco_confidence_floor: String(settings.confidenceFloor),
  conuco_cloud_photos_allowed: 'true',
  conuco_grounding_enabled: 'false',
  conuco_outbreak_radius_km: String(settings.outbreakRadiusKm),
  conuco_outbreak_alert_min: String(settings.outbreakAlertMin),
  conuco_price_window_days: String(settings.priceWindowDays),
  conuco_spray_max_wind_kmh: String(settings.sprayMaxWindKmh),
};

const within = (v, lo, hi, fallback) => (Number.isFinite(v) && v >= lo && v <= hi ? v : fallback);

export async function loadRemoteConfig() {
  try {
    const rc = getRemoteConfig(app);
    rc.settings.minimumFetchIntervalMillis = 30 * 60 * 1000;
    rc.defaultConfig = RC_DEFAULTS;
    await fetchAndActivate(rc);
    const s = (k) => getValue(rc, k).asString();
    const n = (k) => getValue(rc, k).asNumber();
    settings.cloudModel = /^gemini-[\w.-]+$/.test(s('conuco_cloud_model')) ? s('conuco_cloud_model') : settings.cloudModel;
    const langs = s('conuco_device_languages_web').split(',').map((x) => x.trim()).filter((x) => /^[a-z]{2}$/.test(x));
    if (langs.length) settings.deviceLanguages = langs;
    settings.confidenceFloor = within(n('conuco_confidence_floor'), 0.3, 0.95, settings.confidenceFloor);
    settings.cloudPhotos = getValue(rc, 'conuco_cloud_photos_allowed').asBoolean();
    settings.grounding = getValue(rc, 'conuco_grounding_enabled').asBoolean();
    settings.outbreakRadiusKm = within(n('conuco_outbreak_radius_km'), 5, 150, settings.outbreakRadiusKm);
    settings.outbreakAlertMin = within(n('conuco_outbreak_alert_min'), 1, 20, settings.outbreakAlertMin);
    settings.priceWindowDays = within(n('conuco_price_window_days'), 3, 60, settings.priceWindowDays);
    settings.sprayMaxWindKmh = within(n('conuco_spray_max_wind_kmh'), 5, 30, settings.sprayMaxWindKmh);
    settings.source = 'remote';
  } catch {
    settings.source = 'defaults'; // offline or blocked: in-app defaults stay
  }
  return settings.source;
}

// Analytics: counts only (feature, where the AI ran, latency). Never photos, notes, places or prices.
let analytics = null;
isSupported().then((ok) => {
  if (!ok) return;
  analytics = initializeAnalytics(app, {
    config: { allow_google_signals: false, allow_ad_personalization_signals: false, send_page_view: true },
  });
}).catch(() => {});

export function track(name, params = {}) {
  try {
    if (analytics) logEvent(analytics, name, params);
  } catch { /* analytics never blocks the app */ }
}
export function trackUser(props) {
  try {
    if (analytics) setUserProperties(analytics, props);
  } catch { /* ignore */ }
}
