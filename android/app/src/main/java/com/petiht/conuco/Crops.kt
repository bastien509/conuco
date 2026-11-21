package com.petiht.conuco

/** Crop catalog, problems and categories. Same keys as the web app and Firestore. */
data class Crop(val key: String, val en: String, val es: String, val kc: Double, val color: Long, val watch: List<String>)
data class Problem(val key: String, val cat: String, val en: String, val es: String)

object Crops {
    val ALL = listOf(
        Crop("plantain", "Plantain", "Plátano", 1.1, 0xFF5D8A2F, listOf("black_sigatoka", "weevil", "fusarium_wilt")),
        Crop("banana", "Banana", "Guineo", 1.1, 0xFFC9A400, listOf("black_sigatoka", "fusarium_wilt", "weevil")),
        Crop("cassava", "Cassava", "Yuca", 0.8, 0xFFA0703C, listOf("mosaic_virus", "mites", "root_rot")),
        Crop("beans", "Beans", "Habichuela", 1.1, 0xFF8C2F39, listOf("rust", "golden_mosaic", "whitefly", "angular_leaf_spot")),
        Crop("rice", "Rice", "Arroz", 1.2, 0xFFC8B560, listOf("blast", "brown_spot", "stem_borer")),
        Crop("maize", "Maize", "Maíz", 1.2, 0xFFE0A800, listOf("fall_armyworm", "rust", "leaf_blight")),
        Crop("cacao", "Cacao", "Cacao", 1.0, 0xFF6B3E26, listOf("black_pod", "monilia", "witches_broom")),
        Crop("coffee", "Coffee", "Café", 0.95, 0xFF4B2E1F, listOf("coffee_rust", "berry_borer", "leaf_miner")),
        Crop("avocado", "Avocado", "Aguacate", 0.85, 0xFF3F6B2A, listOf("root_rot", "anthracnose", "scab")),
        Crop("tomato", "Tomato", "Tomate", 1.15, 0xFFD13B2F, listOf("late_blight", "early_blight", "whitefly", "leaf_curl")),
        Crop("pepper", "Pepper", "Ají", 1.05, 0xFF2F8F3A, listOf("anthracnose", "whitefly", "mites", "bacterial_spot")),
        Crop("onion", "Onion", "Cebolla", 1.05, 0xFFB46A8A, listOf("purple_blotch", "thrips", "downy_mildew")),
        Crop("potato", "Potato", "Papa", 1.15, 0xFFB08850, listOf("late_blight", "early_blight", "tuber_moth")),
        Crop("sweet_potato", "Sweet potato", "Batata", 1.15, 0xFFC0622E, listOf("weevil", "scab")),
        Crop("mango", "Mango", "Mango", 0.9, 0xFFF0A020, listOf("anthracnose", "powdery_mildew", "fruit_fly")),
        Crop("pineapple", "Pineapple", "Piña", 0.4, 0xFFD8A520, listOf("mealybug_wilt", "heart_rot")),
        Crop("pigeon_pea", "Pigeon pea", "Guandul", 1.0, 0xFF7A6A2A, listOf("pod_borer", "wilt")),
        Crop("other", "Other crop", "Otro cultivo", 1.0, 0xFF6B7A72, emptyList()),
    )
    private val byKey = ALL.associateBy { it.key }
    fun get(k: String?) = byKey[k] ?: byKey.getValue("other")
    fun name(k: String?, lang: String) = get(k).let { if (lang == "es") it.es else it.en }

    val PROBLEMS = listOf(
        Problem("black_sigatoka", "fungus", "Black Sigatoka", "Sigatoka negra"),
        Problem("fusarium_wilt", "fungus", "Fusarium wilt", "Mal de Panamá (fusarium)"),
        Problem("weevil", "pest", "Weevil", "Picudo / gorgojo"),
        Problem("mosaic_virus", "virus", "Mosaic virus", "Virus del mosaico"),
        Problem("golden_mosaic", "virus", "Golden mosaic", "Mosaico dorado"),
        Problem("mites", "pest", "Mites", "Ácaros"),
        Problem("root_rot", "fungus", "Root rot", "Pudrición de raíz"),
        Problem("rust", "fungus", "Rust", "Roya"),
        Problem("coffee_rust", "fungus", "Coffee leaf rust", "Roya del café"),
        Problem("whitefly", "pest", "Whitefly", "Mosca blanca"),
        Problem("angular_leaf_spot", "fungus", "Angular leaf spot", "Mancha angular"),
        Problem("blast", "fungus", "Rice blast", "Piricularia (quemazón)"),
        Problem("brown_spot", "fungus", "Brown spot", "Mancha parda"),
        Problem("stem_borer", "pest", "Stem borer", "Barrenador del tallo"),
        Problem("fall_armyworm", "pest", "Fall armyworm", "Gusano cogollero"),
        Problem("leaf_blight", "fungus", "Leaf blight", "Tizón foliar"),
        Problem("black_pod", "fungus", "Black pod", "Mazorca negra"),
        Problem("monilia", "fungus", "Frosty pod rot", "Moniliasis"),
        Problem("witches_broom", "fungus", "Witches' broom", "Escoba de bruja"),
        Problem("berry_borer", "pest", "Coffee berry borer", "Broca del café"),
        Problem("leaf_miner", "pest", "Leaf miner", "Minador de la hoja"),
        Problem("anthracnose", "fungus", "Anthracnose", "Antracnosis"),
        Problem("scab", "fungus", "Scab", "Roña"),
        Problem("late_blight", "fungus", "Late blight", "Tizón tardío"),
        Problem("early_blight", "fungus", "Early blight", "Tizón temprano"),
        Problem("leaf_curl", "virus", "Leaf curl virus", "Virus del enrollamiento"),
        Problem("bacterial_spot", "bacteria", "Bacterial spot", "Mancha bacteriana"),
        Problem("purple_blotch", "fungus", "Purple blotch", "Mancha púrpura"),
        Problem("thrips", "pest", "Thrips", "Trips"),
        Problem("downy_mildew", "fungus", "Downy mildew", "Mildiu"),
        Problem("powdery_mildew", "fungus", "Powdery mildew", "Oídio (cenicilla)"),
        Problem("tuber_moth", "pest", "Tuber moth", "Polilla de la papa"),
        Problem("fruit_fly", "pest", "Fruit fly", "Mosca de la fruta"),
        Problem("mealybug_wilt", "pest", "Mealybug wilt", "Marchitez por cochinilla"),
        Problem("heart_rot", "fungus", "Heart rot", "Pudrición del cogollo"),
        Problem("pod_borer", "pest", "Pod borer", "Barrenador de vainas"),
        Problem("wilt", "fungus", "Wilt", "Marchitez"),
        Problem("nutrient", "nutrient", "Nutrient deficiency", "Deficiencia de nutrientes"),
        Problem("water_stress", "water", "Water stress", "Estrés hídrico"),
        Problem("other", "unknown", "Other problem", "Otro problema"),
    )
    private val problemByKey = PROBLEMS.associateBy { it.key }
    fun problem(k: String?) = problemByKey[k] ?: problemByKey.getValue("other")
    fun problemName(k: String?, lang: String) = problem(k).let { if (lang == "es") it.es else it.en }

    val CATEGORIES = listOf("fungus", "bacteria", "virus", "pest", "nutrient", "water", "healthy", "unknown")
    val CAT_COLOR = mapOf(
        "fungus" to 0xFFC2410C, "bacteria" to 0xFFB91C1C, "virus" to 0xFFA16207, "pest" to 0xFF0F766E,
        "nutrient" to 0xFF4D7C0F, "water" to 0xFF1D4ED8, "healthy" to 0xFF15803D, "unknown" to 0xFF57534E,
    )
    val PRICE_UNITS = listOf("lb", "kg", "quintal", "unit", "bunch", "sack", "dozen", "box")
    val AREA_UNITS = linkedMapOf("tarea" to 628.86, "ha" to 10000.0, "acre" to 4046.86, "m2" to 1.0)
    private val CURRENCY = mapOf(
        "DO" to "DOP", "HT" to "HTG", "US" to "USD", "MX" to "MXN", "GT" to "GTQ", "HN" to "HNL", "NI" to "NIO", "CR" to "CRC",
        "CO" to "COP", "PE" to "PEN", "EC" to "USD", "BO" to "BOB", "BR" to "BRL", "AR" to "ARS", "CL" to "CLP", "CU" to "CUP",
        "PR" to "USD", "JM" to "JMD", "ES" to "EUR", "FR" to "EUR", "KE" to "KES", "NG" to "NGN", "GH" to "GHS", "ET" to "ETB",
        "UG" to "UGX", "TZ" to "TZS", "IN" to "INR", "PH" to "PHP", "ID" to "IDR", "VN" to "VND", "BD" to "BDT", "PK" to "PKR",
    )
    fun currency(country: String?) = CURRENCY[country] ?: "USD"

    /** Maps a free-text problem name to a known key (for the outbreak map). */
    fun matchProblem(name: String): String {
        val n = name.lowercase()
        if (n.isBlank()) return "other"
        var best = "other"
        var bestScore = 0
        for (p in PROBLEMS) for (label in listOf(p.en, p.es, p.key.replace('_', ' '))) {
            val words = label.lowercase().split(Regex("[\\s(),/]+")).filter { it.length > 3 }
            val hit = words.count { n.contains(it) }
            val score = hit * 10 + label.length
            if (hit > 0 && hit >= minOf(2, words.size) && score > bestScore) { best = p.key; bestScore = score }
        }
        return best
    }
}
