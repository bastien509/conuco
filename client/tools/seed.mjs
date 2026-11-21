// Seeds Conuco's shared community data (market prices and outbreak reports) for the Dominican Republic,
// and prepares the judges' demo account. Run once: SEED_EMAIL, SEED_PASS, DEMO_EMAIL, DEMO_PASS from env.
// All rows go through the same security rules as the app (own uid, server timestamp, 0.1° cell).
import { initializeApp } from 'firebase/app';
import { getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword, signOut } from 'firebase/auth';
import { getFirestore, collection, addDoc, doc, setDoc, writeBatch, serverTimestamp, getDocs, query, where, deleteDoc } from 'firebase/firestore';

const app = initializeApp({
  apiKey: 'AIzaSyBGmRvZH6VNltdZNH5QKatbnnqOiFfBDWM', authDomain: 'conuco-farm.firebaseapp.com', projectId: 'conuco-farm',
  appId: '1:42753514920:web:1416be517d8979cded519d',
});
const auth = getAuth(app);
const db = getFirestore(app);
const env = (k) => { const v = process.env[k]; if (!v) throw new Error(`missing ${k}`); return v; };

async function login(email, pass) {
  try { return (await signInWithEmailAndPassword(auth, email, pass)).user; } catch (e) {
    if (!/user-not-found|invalid-credential|invalid-login/.test(e.code || '')) throw e;
    return (await createUserWithEmailAndPassword(auth, email, pass)).user;
  }
}
const iso = (d) => d.toISOString().slice(0, 10);
const daysAgo = (n) => iso(new Date(Date.now() - n * 86400000));
const cellOf = (lat, lng) => `${(Math.round(lat * 10) / 10).toFixed(1)}_${(Math.round(lng * 10) / 10).toFixed(1)}`;
let seed = 509;
const rnd = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; };
const pick = (a) => a[Math.floor(rnd() * a.length)];

const PLACES = {
  santiago: [19.45, -70.69, 'Mercado Hospidaje'],
  moca: [19.39, -70.52, 'Mercado de Moca'],
  lavega: [19.22, -70.53, 'Mercado de La Vega'],
  sfm: [19.30, -70.25, 'Asociación de cacaoteros'],
  mao: [19.55, -71.08, 'Mercado de Mao'],
  jarabacoa: [19.12, -70.64, 'Cooperativa cafetalera'],
  constanza: [18.91, -70.74, 'Mercado de Constanza'],
  puertoplata: [19.79, -70.69, 'Mercado de Puerto Plata'],
};
// crop, unit, base price in DOP, places, weekly drift (beans rising, plantain falling a little)
const PRICES = [
  ['plantain', 'unit', 18, ['santiago', 'moca', 'lavega', 'mao'], -0.01],
  ['banana', 'bunch', 320, ['santiago', 'mao', 'puertoplata'], 0],
  ['beans', 'lb', 92, ['santiago', 'moca', 'constanza', 'lavega'], 0.025],
  ['cacao', 'quintal', 12400, ['sfm', 'moca', 'santiago'], 0.01],
  ['coffee', 'quintal', 13800, ['jarabacoa', 'santiago'], 0],
  ['cassava', 'lb', 20, ['santiago', 'moca', 'lavega'], 0],
  ['avocado', 'unit', 45, ['santiago', 'lavega', 'puertoplata'], 0.005],
  ['tomato', 'lb', 35, ['constanza', 'santiago', 'lavega'], -0.02],
  ['onion', 'lb', 48, ['constanza', 'santiago'], 0.01],
  ['pepper', 'lb', 55, ['constanza', 'santiago'], 0],
];
// crop, problemKey, category, place, reports, max days ago
const OUTBREAKS = [
  ['plantain', 'black_sigatoka', 'fungus', 'moca', 3, 12],
  ['plantain', 'black_sigatoka', 'fungus', 'santiago', 2, 9],
  ['plantain', 'black_sigatoka', 'fungus', 'lavega', 2, 15],
  ['beans', 'golden_mosaic', 'virus', 'santiago', 2, 10],
  ['beans', 'whitefly', 'pest', 'moca', 1, 6],
  ['coffee', 'coffee_rust', 'fungus', 'jarabacoa', 3, 18],
  ['cacao', 'black_pod', 'fungus', 'sfm', 3, 14],
  ['maize', 'fall_armyworm', 'pest', 'mao', 2, 8],
  ['tomato', 'late_blight', 'fungus', 'constanza', 2, 7],
];

async function seedCommunity(uid) {
  // Clear rows this seed account made before, so a re-run does not double the data.
  for (const name of ['prices', 'outbreaks']) {
    const old = await getDocs(query(collection(db, name), where('uid', '==', uid)));
    await Promise.all(old.docs.map((d) => deleteDoc(d.ref)));
    console.log(`removed ${old.size} old ${name}`);
  }
  let n = 0;
  let b = writeBatch(db);
  const flush = async () => { await b.commit(); b = writeBatch(db); };
  for (const [crop, unit, base, places, drift] of PRICES) {
    for (let day = 27; day >= 0; day -= 1 + Math.floor(rnd() * 3)) {
      const place = pick(places);
      const [lat, lng, market] = PLACES[place];
      const weeksAgo = day / 7;
      const price = base * (1 - drift * weeksAgo) * (0.9 + rnd() * 0.2) * (place === 'santiago' ? 1.04 : 1);
      b.set(doc(collection(db, 'prices')), {
        uid, crop, unit, price: Math.round(price * (base > 1000 ? 1 : 100)) / (base > 1000 ? 1 : 100),
        currency: 'DOP', market, country: 'DO', cell: cellOf(lat, lng), day: daysAgo(day), createdAt: serverTimestamp(),
      });
      if (++n % 400 === 0) await flush();
    }
  }
  await flush();
  console.log(`prices: ${n}`);
  let m = 0;
  for (const [crop, problemKey, category, place, count, maxAgo] of OUTBREAKS) {
    const [lat, lng] = PLACES[place];
    for (let i = 0; i < count; i++) {
      const jitterLat = lat + (rnd() - 0.5) * 0.25;
      const jitterLng = lng + (rnd() - 0.5) * 0.25;
      const cell = cellOf(jitterLat, jitterLng);
      const [cLat, cLng] = cell.split('_').map(Number);
      b.set(doc(collection(db, 'outbreaks')), {
        uid, crop, problemKey, category, cell, lat: cLat, lng: cLng, country: 'DO',
        day: daysAgo(Math.floor(rnd() * maxAgo)), createdAt: serverTimestamp(),
      });
      m++;
    }
  }
  await flush();
  console.log(`outbreaks: ${m}`);
}

async function seedDemo(uid) {
  await setDoc(doc(db, 'users', uid), {
    name: 'Rosa', lang: 'es', country: 'DO', place: 'Santiago de los Caballeros, Santiago', lat: 19.45, lng: -70.69,
    crops: ['plantain', 'beans', 'cacao', 'coffee'], areaUnit: 'tarea', shareOutbreaks: true, updatedAt: serverTimestamp(),
  });
  const b = writeBatch(db);
  const plots = [
    ['sample-plantain', 'Conuco de plátano', 'plantain', 3144, daysAgo(160)],
    ['sample-beans', 'Habichuela de la loma', 'beans', 1258, daysAgo(38)],
    ['sample-cacao', 'Cacao viejo', 'cacao', 6289, '2019-05-10'],
    ['sample-coffee', 'Café bajo sombra', 'coffee', 2515, '2021-06-01'],
  ];
  plots.forEach(([id, name, crop, area, planted]) => b.set(doc(db, 'users', uid, 'plots', id), { name, crop, area, planted, createdAt: serverTimestamp() }));
  const logs = [
    ['sample-beans', 'plant', 'Sembré 6 libras de habichuela roja', 6, 'lb', 0, 38],
    ['sample-beans', 'weed', 'Limpieza a mano, dos peones', 0, '', 1600, 20],
    ['sample-plantain', 'fertilize', 'Abono orgánico (gallinaza) alrededor de cada mata', 4, 'sack', 1400, 12],
    ['sample-coffee', 'scout', 'Revisé la roya en el café: pocas hojas con polvo naranja', 0, '', 0, 9],
    ['sample-cacao', 'harvest', 'Cosecha de mazorcas sanas', 180, 'unit', 0, 6],
    ['sample-cacao', 'sell', 'Vendí cacao fermentado en la asociación', 1, 'quintal', 12500, 4],
    ['sample-beans', 'scout', 'Manchas cafés en hojas de abajo, revisar', 0, '', 0, 1],
  ];
  logs.forEach(([plotId, kind, note, qty, unit, money, ago], i) => b.set(doc(db, 'users', uid, 'logs', `sample-${i}`), {
    plotId, kind, note, qty, unit, money, date: daysAgo(ago), via: 'text', createdAt: serverTimestamp(),
  }));
  await b.commit();
  console.log('demo profile + farm ready');
}

const s = await login(env('SEED_EMAIL'), env('SEED_PASS'));
await seedCommunity(s.uid);
await signOut(auth);
const d = await login(env('DEMO_EMAIL'), env('DEMO_PASS'));
await seedDemo(d.uid);
await signOut(auth);
process.exit(0);
