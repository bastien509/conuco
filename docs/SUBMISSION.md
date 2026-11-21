# Devpost submission: Conuco

Paste each section into the matching Devpost field. Replace the parts marked **[CHECK]** before you submit.

## Project name
Conuco: the field assistant that works without signal

## Elevator pitch (200 characters max)
A crop doctor that runs on the farmer's own device. Gemini Nano reads the sick leaf offline, the weather becomes spray and water decisions, and neighbours share prices and outbreak warnings.

## Platforms
Web (Chrome) and Android

## Links
- Live app: https://petiht.com/conuco
- Android APK: https://petiht.com/conuco/download/conuco.apk
- Demo login (optional): `demo@conuco-farm.web.app` / `ConucoDemo509` (or "Start without an account")
- Code (Apache-2.0): **[CHECK: GitHub link]**
- Video: **[CHECK: YouTube link]**

---

## About the project

### Inspiration
A *conuco* is the small family plot of the Caribbean: plantain, beans, cassava, a few cacao or coffee trees. In the Dominican Republic and across the tropics, the people who farm them lose a large share of each harvest to pests and diseases, often because help arrives late. Agronomists are few, the signal in the hills is weak, and data plans are expensive. A farmer with a sick plantain leaf needs an answer **in the field**, in Spanish, without uploading their life to a server.

On-device AI changes that. Gemini Nano can look at the leaf where the farmer stands. The cloud is still there, but as a second opinion the farmer chooses.

### What it does
- **Crop doctor.** Photo of a leaf → problem, honest confidence, severity, what I see, what to do now, how to prevent it. Gemini Nano answers on the device. When it is unsure, Conuco asks before sending the photo to a larger cloud model and shows whether both agree.
- **Today.** The forecast becomes decisions: is it safe to spray now (and when is the next window), do I water (3-day crop water balance), how high is fungus pressure (hours of leaf wetness), heat and heavy-rain warnings. Gemini Nano explains the day in plain words and reads it aloud.
- **Neighbour outbreak map.** One tap warns nearby farms: crop, problem and an 11 km area, never a farm location or a name. Alerts appear when a disease spreads near you.
- **Market prices.** Farmers share what buyers pay; Conuco shows the median, range, trend and the best nearby market per crop and unit (pound, quintal, bunch…).
- **Farm log.** Plots, work, costs and sales, dictated by voice, fully offline.
- **Ask Conuco.** Voice or text questions answered on the device.
- **Symptom guide.** No model at all? Tick what you see and get the likely problems. Works on any phone.

### How we built it
**Firebase AI Logic hybrid inference** is the core, on web and Android:

```js
getGenerativeModel(ai, {
  mode: InferenceMode.ONLY_ON_DEVICE,           // the router picks per request
  inCloudParams: { model: rc.cloudModel,
    generationConfig: { responseMimeType: 'application/json', responseSchema } },
  onDeviceParams: {
    createOptions: { expectedInputs: [{ type: 'text', languages: ['en', 'es'] }, { type: 'image' }],
                     expectedOutputs: [{ type: 'text', languages: ['es'] }] },
    promptOptions: { responseConstraint: jsonSchema },
  },
});
```

Conuco's **router** decides where each request runs and shows the farmer why ("How Conuco decided where to run"):
1. The photo is redrawn on the device: GPS and phone data removed, 768 px, with a blur/brightness check.
2. Gemini Nano diagnoses it with `ONLY_ON_DEVICE`. `response.inferenceSource` drives the badge "On this device · photo never left".
3. If the confidence is below the **Remote Config** floor (0.65), or the problem is unknown, Conuco asks for consent and calls `ONLY_IN_CLOUD`; both answers are compared.
4. Privacy modes: **Private** (device only, falls back to the symptom guide), **Smart** (default), **Cloud first** (for phones without Nano; always asks).
5. The day brief uses `PREFER_ON_DEVICE` with **only rule facts** as input (numbers, no position, no names), so the AI explains but never invents.

On Android, Gemini Nano is reached through the same SDK (`firebase-ai-ondevice`). Because Nano on Android is validated for English and short outputs, the on-device prompt asks for a compact line format (`PROBLEM|name|category|confidence|severity`, `SIGN|…`, `DO|…`) that a small model fills reliably; the cloud uses a JSON schema.

**Other Firebase products**
- **App Check**: reCAPTCHA Enterprise on the web, Play Integrity on Android, enforced on AI Logic.
- **Firestore** with offline persistence: the farm log, plots, tasks and the last prices and outbreak reports work with no signal; pending writes show "waiting to sync". Security rules keep farm data owner-only and validate every shared row (own uid, server timestamp, 0.1° cell, allowed values).
- **Authentication**: start without an account (anonymous), upgrade to email later without losing data.
- **Remote Config**: cloud model, Nano languages per platform, confidence floor, cloud-photo kill switch, Google Search grounding switch, outbreak radius and threshold, price window, spray wind limit.
- **Analytics**: which feature and where the AI ran (on device or cloud), never photos, notes, places or prices.

**Stack:** vanilla JS + esbuild, Leaflet/OpenStreetMap, Open-Meteo weather, Web Speech (on-device recognition where Chrome supports it) and speech synthesis; Kotlin + Jetpack Compose on Android with RecognizerIntent (offline preferred) and TextToSpeech.

### Challenges we ran into
- **Honest confidence from a small model.** We ask for a calibrated probability, clamp it, and route on it; low confidence triggers the second-opinion flow instead of a confident wrong answer.
- **Safety.** Pesticide doses are dangerous and country-specific. Prompts forbid them and a filter removes doses and banned chemicals from any AI text, showing a notice when it does.
- **Two schemas, two engines.** Chrome's Prompt API takes a JSON Schema `responseConstraint`; the cloud takes a Firebase `Schema`; Android Nano needs a line format. One cleaner normalises all three.
- **Offline everything.** The forecast, prices and outbreak reports are cached; the rules run locally; Firestore queues writes.
- **Google Search grounding** works in the code but needs a paid Gemini API tier, so it is behind a Remote Config switch and falls back to a normal answer.

### Accomplishments that we're proud of
- **Verified on-device:** in Chrome on a CPU-only laptop (no graphics card), Gemini Nano diagnosed a Black Sigatoka photo correctly (fungus, 70 %) in 33 s, with "photo never left"; it wrote the Spanish day brief in 44 s and answered a farming question in 20 s.
- The cloud second opinion read a coffee leaf rust photo (95 %) in 4 s through App Check.
- Everything a farmer needs in one offline app, in Spanish, with privacy by design.

### What we learned
Hybrid AI is a product decision, not a fallback flag. The farmer should see where the AI ran, decide when a photo leaves the phone, and get the facts from rules while the AI explains them.

### What's next for Conuco
- More crops and Haitian Kreyòl, and on-device Spanish on Android when Gemini Nano supports it (a Remote Config change).
- Work with extension services (in the Dominican Republic, the agriculture ministry and producer associations) so outbreak alerts reach technicians.
- SMS summaries for farmers without smartphones.

---

## Built with
firebase-ai-logic, gemini-nano, gemini, chrome-prompt-api, firebase-app-check, recaptcha-enterprise, play-integrity, cloud-firestore, firebase-authentication, firebase-remote-config, google-analytics-for-firebase, android, kotlin, jetpack-compose, javascript, esbuild, leaflet, openstreetmap, open-meteo, web-speech-api

## Testing instructions for judges
1. Open https://petiht.com/conuco/app.html and choose **Start without an account** (or sign in with the demo account above).
2. Pick a town (for example **Santiago de los Caballeros**) and crops such as plantain, banana, beans, cacao, coffee. Keep **Add a sample farm** ticked.
3. **Today:** read the spray / water / fungus decisions, then tap **Explain my day**.
4. **Doctor:** tap the **Banana leaf** sample, mode **Smart**, then **Diagnose**. Open **How Conuco decided where to run**. Try **Cloud first** with the coffee sample to see the consent dialog and the cloud answer.
5. **Market** and **Map** show shared Dominican data (prices in DOP, outbreak reports near Santiago).
6. Turn off Wi-Fi: Today (saved forecast), the farm log, prices, and the on-device doctor keep working.
7. **On-device AI** needs Chrome on a desktop with Gemini Nano (about 22 GB free disk and a GPU with more than 4 GB VRAM, or 16 GB RAM). On a CPU-only laptop a diagnosis takes about 30–60 s.
8. **Android:** install the APK. On phones with Gemini Nano (AICore) the doctor runs on the device; elsewhere it asks before using the cloud.
