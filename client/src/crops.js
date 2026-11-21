// Crop catalog. Global crops with names in English and Spanish. kc = FAO-56 mid-season crop coefficient,
// used for the irrigation balance. `watch` = common problems the crop doctor and the symptom guide know about.
export const CROPS = {
  plantain: { en: 'Plantain', es: 'Plátano', kc: 1.1, color: '#5d8a2f', watch: ['black_sigatoka', 'weevil', 'fusarium_wilt'] },
  banana: { en: 'Banana', es: 'Guineo', kc: 1.1, color: '#c9a400', watch: ['black_sigatoka', 'fusarium_wilt', 'weevil'] },
  cassava: { en: 'Cassava', es: 'Yuca', kc: 0.8, color: '#a0703c', watch: ['mosaic_virus', 'mites', 'root_rot'] },
  beans: { en: 'Beans', es: 'Habichuela', kc: 1.1, color: '#8c2f39', watch: ['rust', 'golden_mosaic', 'whitefly', 'angular_leaf_spot'] },
  rice: { en: 'Rice', es: 'Arroz', kc: 1.2, color: '#c8b560', watch: ['blast', 'brown_spot', 'stem_borer'] },
  maize: { en: 'Maize', es: 'Maíz', kc: 1.2, color: '#e0a800', watch: ['fall_armyworm', 'rust', 'leaf_blight'] },
  cacao: { en: 'Cacao', es: 'Cacao', kc: 1.0, color: '#6b3e26', watch: ['black_pod', 'monilia', 'witches_broom'] },
  coffee: { en: 'Coffee', es: 'Café', kc: 0.95, color: '#4b2e1f', watch: ['coffee_rust', 'berry_borer', 'leaf_miner'] },
  avocado: { en: 'Avocado', es: 'Aguacate', kc: 0.85, color: '#3f6b2a', watch: ['root_rot', 'anthracnose', 'scab'] },
  tomato: { en: 'Tomato', es: 'Tomate', kc: 1.15, color: '#d13b2f', watch: ['late_blight', 'early_blight', 'whitefly', 'leaf_curl'] },
  pepper: { en: 'Pepper', es: 'Ají', kc: 1.05, color: '#2f8f3a', watch: ['anthracnose', 'whitefly', 'mites', 'bacterial_spot'] },
  onion: { en: 'Onion', es: 'Cebolla', kc: 1.05, color: '#b46a8a', watch: ['purple_blotch', 'thrips', 'downy_mildew'] },
  potato: { en: 'Potato', es: 'Papa', kc: 1.15, color: '#b08850', watch: ['late_blight', 'early_blight', 'tuber_moth'] },
  sweet_potato: { en: 'Sweet potato', es: 'Batata', kc: 1.15, color: '#c0622e', watch: ['weevil', 'scab'] },
  mango: { en: 'Mango', es: 'Mango', kc: 0.9, color: '#f0a020', watch: ['anthracnose', 'powdery_mildew', 'fruit_fly'] },
  pineapple: { en: 'Pineapple', es: 'Piña', kc: 0.4, color: '#d8a520', watch: ['mealybug_wilt', 'heart_rot'] },
  pigeon_pea: { en: 'Pigeon pea', es: 'Guandul', kc: 1.0, color: '#7a6a2a', watch: ['pod_borer', 'wilt'] },
  other: { en: 'Other crop', es: 'Otro cultivo', kc: 1.0, color: '#6b7a72', watch: [] },
};
export const CROP_KEYS = Object.keys(CROPS);
export const cropName = (key, lang) => (CROPS[key] ? CROPS[key][lang === 'es' ? 'es' : 'en'] : key || '');

/** Units people actually sell in (quintal = 100 lb, racimo = bunch, tarea = DR land unit). */
export const PRICE_UNITS = ['lb', 'kg', 'quintal', 'unit', 'bunch', 'sack', 'dozen', 'box'];

/** Country → currency for the market board. Unknown countries fall back to USD. */
export const CURRENCY = {
  DO: 'DOP', HT: 'HTG', US: 'USD', MX: 'MXN', GT: 'GTQ', HN: 'HNL', NI: 'NIO', CR: 'CRC', PA: 'USD', SV: 'USD',
  CO: 'COP', PE: 'PEN', EC: 'USD', BO: 'BOB', BR: 'BRL', AR: 'ARS', CL: 'CLP', PY: 'PYG', VE: 'VES', CU: 'CUP',
  PR: 'USD', JM: 'JMD', ES: 'EUR', FR: 'EUR', KE: 'KES', NG: 'NGN', GH: 'GHS', ET: 'ETB', UG: 'UGX', TZ: 'TZS',
  IN: 'INR', PH: 'PHP', ID: 'IDR', VN: 'VND', BD: 'BDT', PK: 'PKR',
};
export const currencyFor = (country) => CURRENCY[country] || 'USD';

/** Area: the app stores m². Tarea is the Dominican unit (628.86 m²). */
export const AREA_UNITS = { tarea: 628.86, ha: 10000, acre: 4046.86, m2: 1 };

/** Problem labels for the outbreak map and the symptom guide (category = what kind of problem it is). */
export const PROBLEMS = {
  black_sigatoka: { cat: 'fungus', en: 'Black Sigatoka', es: 'Sigatoka negra' },
  fusarium_wilt: { cat: 'fungus', en: 'Fusarium wilt', es: 'Mal de Panamá (fusarium)' },
  weevil: { cat: 'pest', en: 'Weevil', es: 'Picudo / gorgojo' },
  mosaic_virus: { cat: 'virus', en: 'Mosaic virus', es: 'Virus del mosaico' },
  golden_mosaic: { cat: 'virus', en: 'Golden mosaic', es: 'Mosaico dorado' },
  mites: { cat: 'pest', en: 'Mites', es: 'Ácaros' },
  root_rot: { cat: 'fungus', en: 'Root rot', es: 'Pudrición de raíz' },
  rust: { cat: 'fungus', en: 'Rust', es: 'Roya' },
  coffee_rust: { cat: 'fungus', en: 'Coffee leaf rust', es: 'Roya del café' },
  whitefly: { cat: 'pest', en: 'Whitefly', es: 'Mosca blanca' },
  angular_leaf_spot: { cat: 'fungus', en: 'Angular leaf spot', es: 'Mancha angular' },
  blast: { cat: 'fungus', en: 'Rice blast', es: 'Piricularia (quemazón)' },
  brown_spot: { cat: 'fungus', en: 'Brown spot', es: 'Mancha parda' },
  stem_borer: { cat: 'pest', en: 'Stem borer', es: 'Barrenador del tallo' },
  fall_armyworm: { cat: 'pest', en: 'Fall armyworm', es: 'Gusano cogollero' },
  leaf_blight: { cat: 'fungus', en: 'Leaf blight', es: 'Tizón foliar' },
  black_pod: { cat: 'fungus', en: 'Black pod', es: 'Mazorca negra' },
  monilia: { cat: 'fungus', en: 'Frosty pod rot', es: 'Moniliasis' },
  witches_broom: { cat: 'fungus', en: "Witches' broom", es: 'Escoba de bruja' },
  berry_borer: { cat: 'pest', en: 'Coffee berry borer', es: 'Broca del café' },
  leaf_miner: { cat: 'pest', en: 'Leaf miner', es: 'Minador de la hoja' },
  anthracnose: { cat: 'fungus', en: 'Anthracnose', es: 'Antracnosis' },
  scab: { cat: 'fungus', en: 'Scab', es: 'Roña' },
  late_blight: { cat: 'fungus', en: 'Late blight', es: 'Tizón tardío' },
  early_blight: { cat: 'fungus', en: 'Early blight', es: 'Tizón temprano' },
  leaf_curl: { cat: 'virus', en: 'Leaf curl virus', es: 'Virus del enrollamiento' },
  bacterial_spot: { cat: 'bacteria', en: 'Bacterial spot', es: 'Mancha bacteriana' },
  purple_blotch: { cat: 'fungus', en: 'Purple blotch', es: 'Mancha púrpura' },
  thrips: { cat: 'pest', en: 'Thrips', es: 'Trips' },
  downy_mildew: { cat: 'fungus', en: 'Downy mildew', es: 'Mildiu' },
  powdery_mildew: { cat: 'fungus', en: 'Powdery mildew', es: 'Oídio (cenicilla)' },
  tuber_moth: { cat: 'pest', en: 'Tuber moth', es: 'Polilla de la papa' },
  fruit_fly: { cat: 'pest', en: 'Fruit fly', es: 'Mosca de la fruta' },
  mealybug_wilt: { cat: 'pest', en: 'Mealybug wilt', es: 'Marchitez por cochinilla' },
  heart_rot: { cat: 'fungus', en: 'Heart rot', es: 'Pudrición del cogollo' },
  pod_borer: { cat: 'pest', en: 'Pod borer', es: 'Barrenador de vainas' },
  wilt: { cat: 'fungus', en: 'Wilt', es: 'Marchitez' },
  nutrient: { cat: 'nutrient', en: 'Nutrient deficiency', es: 'Deficiencia de nutrientes' },
  water_stress: { cat: 'water', en: 'Water stress', es: 'Estrés hídrico' },
  other: { cat: 'unknown', en: 'Other problem', es: 'Otro problema' },
};
export const CATEGORIES = ['fungus', 'bacteria', 'virus', 'pest', 'nutrient', 'water', 'healthy', 'unknown'];
export const CAT_COLOR = {
  fungus: '#c2410c', bacteria: '#b91c1c', virus: '#a16207', pest: '#0f766e', nutrient: '#4d7c0f',
  water: '#1d4ed8', healthy: '#15803d', unknown: '#57534e',
};
export const problemName = (key, lang) => (PROBLEMS[key] ? PROBLEMS[key][lang === 'es' ? 'es' : 'en'] : key || '');
