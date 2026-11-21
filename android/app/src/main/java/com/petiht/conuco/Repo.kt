package com.petiht.conuco

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Profile(
    val name: String = "", val lang: String = "es", val country: String = "", val place: String = "",
    val lat: Double = 0.0, val lng: Double = 0.0, val crops: List<String> = emptyList(), val areaUnit: String = "ha", val shareOutbreaks: Boolean = true,
)
data class Plot(val id: String, val name: String, val crop: String, val area: Double, val planted: String)
data class LogEntry(val id: String, val plotId: String, val kind: String, val note: String, val qty: Double, val unit: String, val money: Double, val date: String, val pending: Boolean)
data class DiagRow(val id: String, val crop: String, val problem: String, val problemKey: String, val category: String, val confidence: Double, val source: String, val day: String)
data class TaskRow(val id: String, val text: String, val day: String, val done: Boolean, val from: String)
data class PriceRow(val crop: String, val unit: String, val price: Double, val currency: String, val market: String, val cell: String, val day: String)
data class Outbreak(val crop: String, val problemKey: String, val category: String, val lat: Double, val lng: Double, val day: String)
data class BoardRow(val crop: String, val unit: String, val median: Double, val n: Int, val min: Double, val max: Double, val trend: Int?, val bestMarket: String?, val bestPrice: Double?)
data class Alert(val crop: String, val problemKey: String, val category: String, val n: Int, val nearestKm: Int, val last: String)

/** Firestore access. Private data under users/{uid}; shared prices/outbreaks carry no names and only a 0.1° cell. Offline by default. */
class Repo(private val uid: String) {
    private val db = Firebase.firestore
    private val user = db.collection("users").document(uid)

    companion object {
        val LOG_KINDS = listOf("plant", "water", "fertilize", "spray", "scout", "weed", "harvest", "sell", "other")
        fun today(): String = LocalDate.now().toString()
        fun daysAgo(n: Long): String = LocalDate.now().minusDays(n).toString()
        fun cellOf(lat: Double, lng: Double) = "%.1f_%.1f".format(java.util.Locale.US, (lat * 10).roundToInt() / 10.0, (lng * 10).roundToInt() / 10.0)
        fun cellCenter(cell: String) = cell.split('_').map { it.toDouble() }.let { it[0] to it[1] }
        fun km(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val r = Math.toRadians(1.0)
            val dLat = (b.first - a.first) * r
            val dLng = (b.second - a.second) * r
            val s = sin(dLat / 2).pow(2) + cos(a.first * r) * cos(b.first * r) * sin(dLng / 2).pow(2)
            return 2 * 6371 * asin(sqrt(s))
        }
        private fun median(a: List<Double>): Double { val s = a.sorted(); val m = s.size / 2; return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2 }

        fun board(rows: List<PriceRow>, lat: Double, lng: Double, radiusKm: Double, currency: String, windowDays: Int): List<BoardRow> {
            val cut = daysAgo(windowDays.toLong())
            val near = rows.filter { it.currency == currency && km(lat to lng, cellCenter(it.cell)) <= radiusKm }
            return near.groupBy { it.crop to it.unit }.mapNotNull { (k, list) ->
                val now = list.filter { it.day >= cut }
                if (now.isEmpty()) return@mapNotNull null
                val before = list.filter { it.day < cut }
                val med = median(now.map { it.price })
                val prev = if (before.isNotEmpty()) median(before.map { it.price }) else null
                val best = now.filter { it.market.isNotBlank() }.groupBy { it.market }.map { (m, v) -> m to median(v.map { it.price }) }.maxByOrNull { it.second }
                BoardRow(k.first, k.second, (med * 100).roundToInt() / 100.0, now.size, now.minOf { it.price }, now.maxOf { it.price },
                    prev?.let { (((med - it) / it) * 100).roundToInt() }, best?.first, best?.second)
            }.sortedByDescending { it.n }
        }
        fun alerts(rows: List<Outbreak>, lat: Double, lng: Double, radiusKm: Double, min: Int, crops: List<String>?): List<Alert> =
            rows.map { it to km(lat to lng, it.lat to it.lng) }.filter { it.second <= radiusKm }
                .groupBy { it.first.crop to it.first.problemKey }
                .map { (k, v) -> Alert(k.first, k.second, v.first().first.category, v.size, v.minOf { it.second }.roundToInt(), v.maxOf { it.first.day }) }
                .filter { it.n >= min && (crops.isNullOrEmpty() || it.crop in crops) }.sortedByDescending { it.n }
    }

    suspend fun loadProfile(): Profile? = try {
        val d = user.get().await()
        if (!d.exists()) null else Profile(
            d.getString("name") ?: "", d.getString("lang") ?: "es", d.getString("country") ?: "", d.getString("place") ?: "",
            d.getDouble("lat") ?: 0.0, d.getDouble("lng") ?: 0.0, (d.get("crops") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            d.getString("areaUnit") ?: "ha", d.getBoolean("shareOutbreaks") ?: true,
        )
    } catch (e: Exception) { null }

    fun saveProfile(p: Profile) = user.set(mapOf(
        "name" to p.name.take(40), "lang" to (if (p.lang == "es") "es" else "en"), "country" to p.country.take(2).uppercase(), "place" to p.place.take(60),
        "lat" to (p.lat * 100).roundToInt() / 100.0, "lng" to (p.lng * 100).roundToInt() / 100.0, "crops" to p.crops.take(12),
        "areaUnit" to p.areaUnit, "shareOutbreaks" to p.shareOutbreaks, "updatedAt" to FieldValue.serverTimestamp(),
    ))

    fun watchPlots(cb: (List<Plot>) -> Unit): ListenerRegistration = user.collection("plots").orderBy("createdAt").addSnapshotListener { s, _ ->
        cb(s?.documents?.map { Plot(it.id, it.getString("name") ?: "", it.getString("crop") ?: "other", it.getDouble("area") ?: 0.0, it.getString("planted") ?: "") } ?: emptyList())
    }
    fun addPlot(name: String, crop: String, areaM2: Double, planted: String) = user.collection("plots").add(mapOf(
        "name" to name.take(40).ifBlank { "Plot" }, "crop" to crop, "area" to areaM2.coerceIn(0.0, 1e7),
        "planted" to (if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(planted)) planted else ""), "createdAt" to FieldValue.serverTimestamp(),
    ))

    fun watchLog(cb: (List<LogEntry>) -> Unit): ListenerRegistration =
        user.collection("logs").orderBy("date", Query.Direction.DESCENDING).limit(60).addSnapshotListener(MetadataChanges.INCLUDE) { s, _ ->
            cb(s?.documents?.map {
                LogEntry(it.id, it.getString("plotId") ?: "", it.getString("kind") ?: "other", it.getString("note") ?: "", it.getDouble("qty") ?: 0.0,
                    it.getString("unit") ?: "", it.getDouble("money") ?: 0.0, it.getString("date") ?: "", it.metadata.hasPendingWrites())
            } ?: emptyList())
        }
    fun addLog(kind: String, note: String, qty: Double, unit: String, money: Double, plotId: String, via: String) = user.collection("logs").add(mapOf(
        "plotId" to plotId.take(40), "kind" to (if (kind in LOG_KINDS) kind else "other"), "note" to note.trim().take(300),
        "qty" to qty.coerceIn(0.0, 1e7), "unit" to unit.take(12), "money" to money.coerceIn(0.0, 1e9), "date" to today(),
        "via" to (if (via in listOf("voice", "text", "doctor", "weather")) via else "text"), "createdAt" to FieldValue.serverTimestamp(),
    ))
    fun deleteLog(id: String) = user.collection("logs").document(id).delete()

    fun watchDiagnoses(cb: (List<DiagRow>) -> Unit): ListenerRegistration =
        user.collection("diagnoses").orderBy("createdAt", Query.Direction.DESCENDING).limit(20).addSnapshotListener { s, _ ->
            cb(s?.documents?.map {
                DiagRow(it.id, it.getString("crop") ?: "", it.getString("problem") ?: "", it.getString("problemKey") ?: "other", it.getString("category") ?: "unknown",
                    it.getDouble("confidence") ?: 0.0, it.getString("source") ?: "", it.getString("day") ?: "")
            } ?: emptyList())
        }
    fun addDiagnosis(crop: String, d: Ai.Diagnosis, second: String) = user.collection("diagnoses").add(mapOf(
        "crop" to crop, "problem" to d.problem.take(80), "problemKey" to d.problemKey, "category" to d.category,
        "confidence" to d.confidence, "severity" to d.severity, "source" to d.source, "secondOpinion" to second,
        "actions" to d.actions.take(4).map { it.take(200) }, "plotId" to "", "day" to today(), "createdAt" to FieldValue.serverTimestamp(),
    ))

    fun watchTasks(cb: (List<TaskRow>) -> Unit): ListenerRegistration =
        user.collection("tasks").whereGreaterThanOrEqualTo("day", daysAgo(1)).limit(30).addSnapshotListener { s, _ ->
            cb(s?.documents?.map { TaskRow(it.id, it.getString("text") ?: "", it.getString("day") ?: "", it.getBoolean("done") ?: false, it.getString("from") ?: "user") } ?: emptyList())
        }
    fun upsertTask(id: String, code: String, text: String, from: String) = user.collection("tasks").document(id.take(60)).set(mapOf(
        "code" to code.take(20), "text" to text.take(160), "day" to today(), "done" to false, "from" to from, "updatedAt" to FieldValue.serverTimestamp(),
    ))
    fun setTaskDone(id: String, done: Boolean) = user.collection("tasks").document(id).update(mapOf("done" to done, "updatedAt" to FieldValue.serverTimestamp()))

    suspend fun loadPrices(days: Int): List<PriceRow> {
        val q = db.collection("prices").whereGreaterThanOrEqualTo("day", daysAgo(days.toLong())).orderBy("day", Query.Direction.DESCENDING).limit(800)
        val s = try { q.get().await() } catch (e: Exception) { q.get(Source.CACHE).await() }
        return s.documents.map { PriceRow(it.getString("crop") ?: "", it.getString("unit") ?: "", it.getDouble("price") ?: 0.0, it.getString("currency") ?: "", it.getString("market") ?: "", it.getString("cell") ?: "0.0_0.0", it.getString("day") ?: "") }
    }
    fun addPrice(crop: String, unit: String, price: Double, currency: String, market: String, country: String, cell: String) = db.collection("prices").add(mapOf(
        "uid" to uid, "crop" to crop, "unit" to unit, "price" to (price * 100).roundToInt() / 100.0, "currency" to currency, "market" to market.trim().take(40),
        "country" to country.take(2), "cell" to cell, "day" to today(), "createdAt" to FieldValue.serverTimestamp(),
    ))
    suspend fun loadOutbreaks(days: Int = 21): List<Outbreak> {
        val q = db.collection("outbreaks").whereGreaterThanOrEqualTo("day", daysAgo(days.toLong())).orderBy("day", Query.Direction.DESCENDING).limit(800)
        val s = try { q.get().await() } catch (e: Exception) { q.get(Source.CACHE).await() }
        return s.documents.map { Outbreak(it.getString("crop") ?: "", it.getString("problemKey") ?: "other", it.getString("category") ?: "unknown", it.getDouble("lat") ?: 0.0, it.getDouble("lng") ?: 0.0, it.getString("day") ?: "") }
    }
    fun addOutbreak(crop: String, problemKey: String, category: String, cell: String, country: String) = cellCenter(cell).let { (la, lo) ->
        db.collection("outbreaks").add(mapOf(
            "uid" to uid, "crop" to crop, "problemKey" to problemKey, "category" to category, "cell" to cell, "lat" to la, "lng" to lo,
            "country" to country.take(2), "day" to today(), "createdAt" to FieldValue.serverTimestamp(),
        ))
    }

    suspend fun seedSample() {
        val b = db.batch()
        listOf(
            listOf("sample-plantain", "Conuco de plátano", "plantain", 3144.0, daysAgo(160)),
            listOf("sample-beans", "Habichuela de la loma", "beans", 1258.0, daysAgo(38)),
            listOf("sample-cacao", "Cacao viejo", "cacao", 6289.0, "2019-05-10"),
        ).forEach { p -> b.set(user.collection("plots").document(p[0] as String), mapOf("name" to p[1], "crop" to p[2], "area" to p[3], "planted" to p[4], "createdAt" to FieldValue.serverTimestamp())) }
        listOf(
            listOf("sample-beans", "plant", "Sembré 6 libras de habichuela roja", 6.0, "lb", 0.0, 38L),
            listOf("sample-beans", "weed", "Limpieza a mano, dos peones", 0.0, "", 1600.0, 20L),
            listOf("sample-plantain", "fertilize", "Abono orgánico (gallinaza) alrededor de cada mata", 4.0, "sack", 1400.0, 12L),
            listOf("sample-cacao", "harvest", "Cosecha de mazorcas sanas", 180.0, "unit", 0.0, 6L),
            listOf("sample-cacao", "sell", "Vendí cacao fermentado en la asociación", 1.0, "quintal", 12500.0, 4L),
            listOf("sample-beans", "scout", "Manchas cafés en hojas de abajo, revisar", 0.0, "", 0.0, 1L),
        ).forEachIndexed { i, l ->
            b.set(user.collection("logs").document("sample-$i"), mapOf("plotId" to l[0], "kind" to l[1], "note" to l[2], "qty" to l[3], "unit" to l[4], "money" to l[5],
                "date" to daysAgo(l[6] as Long), "via" to "text", "createdAt" to FieldValue.serverTimestamp()))
        }
        b.commit().await()
    }
}
