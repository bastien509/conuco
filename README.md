# Conuco — the field assistant that works without signal

Conuco helps small farmers diagnose crop problems, plan the day from the weather, share market prices and outbreak warnings, and keep a farm log, **on their own device first**. It is built on **Firebase AI Logic hybrid inference**: Gemini Nano on the device, Gemini in the cloud only with consent.

- Live web app: https://petiht.com/conuco
- Android APK: https://petiht.com/conuco/download/conuco.apk
- Languages: Spanish and English. Built for the Dominican Republic first; works anywhere (Open-Meteo weather, any currency).

## What it does

| Feature | How it runs |
| --- | --- |
| **Crop doctor** — photo → problem, confidence, severity, signs, actions, prevention | Gemini Nano on the device (`ONLY_ON_DEVICE`). If confidence is under the Remote Config floor, a **cloud second opinion** (`ONLY_IN_CLOUD`) after the farmer says yes, with an agree/disagree check |
| **Photo privacy** | The photo is redrawn on a canvas (EXIF/GPS removed) and resized to 768 px before any AI sees it. Photos are never stored |
| **Safety** | No pesticide doses: a filter removes doses and banned chemicals from any AI text and points to the label and the extension officer |
| **Today** — spray window, watering, fungus risk, heat, heavy rain | Deterministic rules on the Open-Meteo forecast (FAO-56 water balance, leaf-wetness hours). Cached for offline use. Gemini Nano explains the day in plain words (`PREFER_ON_DEVICE`), read aloud with on-device speech |
| **Outbreak map** | One tap shares crop + problem + an 11 km cell. Leaflet map (web), offline radar (Android), alerts when a problem spreads near you |
| **Market prices** | Crowdsourced reports; median, range, trend and best nearby market per crop and unit |
| **Farm log** | Plots, work, costs and sales; voice dictation; Firestore offline persistence with "waiting to sync" markers |
| **Ask Conuco** | Voice or text, answered by Gemini Nano. Optional Google Search grounding (Remote Config switch; needs a paid Gemini API tier) |
| **Symptom guide** | Works with no AI at all, on any phone |

## Firebase

- **AI Logic** (Gemini Developer API) with hybrid inference on web (`firebase` 13.4) and Android (`firebase-ai` 18.2 + `firebase-ai-ondevice`).
- **App Check**: reCAPTCHA Enterprise (web), Play Integrity (Android); enforced on AI Logic.
- **Firestore**: offline-first, strict rules in `firestore.rules` (owner-only farm data; validated, coarse, name-free community data).
- **Authentication**: anonymous start, email upgrade.
- **Remote Config**: cloud model, Nano languages, confidence floor, cloud-photo kill switch, grounding switch, outbreak radius and threshold, price window, spray wind limit.
- **Analytics**: features and where the AI ran, never content.

## Layout

```
client/   web app source (vanilla JS + esbuild): ai.js (hybrid engine), rules.js, weather.js, store.js, app.js …
web/      static site served at petiht.com/conuco (index.html landing, app.html app, sw.js, .htaccess CSP)
android/  Kotlin + Jetpack Compose app (com.petiht.conuco)
firestore.rules, remoteconfig.template.json
```

Build the web bundle: `cd client && npm install && node build.mjs`. Build Android: `cd android && ./gradlew assembleRelease`.

Sample photos: Wikimedia Commons (CC0 and CC BY-SA 3.0), see `web/samples/CREDITS.txt`.

Conuco gives general guidance, not professional agronomic advice. License: Apache-2.0.
