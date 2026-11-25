package com.petiht.conuco

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.auth
import com.petiht.conuco.ui.windowText
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Process start: App Check for every Firebase call (also from background work), notification channels, the daily job. */
class ConucoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // App Check: Play Integrity attests every Firebase AI Logic and Firestore call.
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        Bg.channels(this)
        Bg.schedule(this)
    }
}

/**
 * Background side of Conuco. The activity saves a small snapshot of the profile (place, crops, language, live thresholds);
 * a WorkManager job reads it every 6 hours, runs the same offline Rules as the app, checks neighbours' outbreak reports,
 * posts at most one notification per topic per day, and refreshes the home-screen widget.
 */
object Bg {
    const val CH_ALERTS = "outbreaks"
    const val CH_DAILY = "daily"
    private const val SNAP = "conuco_bg"
    const val WIDGET = "conuco_widget"
    private const val WORK = "conuco_daily"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, tr("n_ch_alerts"), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, tr("n_ch_daily"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<DailyWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Runs the job once now (used right after onboarding, and by the "test alerts" button in Settings). */
    fun runNow(ctx: Context) = WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<DailyWorker>().build())

    fun saveSnapshot(ctx: Context, p: Profile, maxWind: Double, radiusKm: Double, alertMin: Int) {
        ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE).edit()
            .putFloat("lat", p.lat.toFloat()).putFloat("lng", p.lng.toFloat()).putString("crops", p.crops.joinToString(","))
            .putString("lang", p.lang).putString("place", p.place.substringBefore(',').trim())
            .putFloat("wind", maxWind.toFloat()).putFloat("radius", radiusKm.toFloat()).putInt("min", alertMin)
            .putBoolean("share", p.shareOutbreaks).apply()
    }

    fun alertsOn(ctx: Context) = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE).getBoolean("alerts", true)
    fun setAlertsOn(ctx: Context, on: Boolean) = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE).edit().putBoolean("alerts", on).apply()

    /** The widget shows exactly what the Today screen decides: next spray window, water, fungus risk, temperature. */
    suspend fun updateWidget(ctx: Context, an: Rules.Analysis, place: String, lang: String) {
        val head = an.windows.firstOrNull()?.let { Strings.t("wg_spray", "w" to windowText(it, lang)) } ?: Strings.t("wg_nospray")
        val sub = Strings.t("wg_sub", "w" to Strings.t(an.irrigation), "f" to Strings.t(an.disease))
        ctx.getSharedPreferences(WIDGET, Context.MODE_PRIVATE).edit()
            .putString("head", head).putString("sub", sub).putString("place", place).putString("lang", lang)
            .putString("temp", an.now?.let { "${it.temp.toInt()}°" } ?: "").putBoolean("ok", an.windows.isNotEmpty())
            .putLong("at", System.currentTimeMillis()).apply()
        runCatching { TodayWidget().updateAll(ctx) }
    }

    fun tabIntent(ctx: Context, tab: String): PendingIntent = PendingIntent.getActivity(
        ctx, tab.hashCode(),
        Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("conuco://tab/$tab")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canNotify(ctx: Context) = alertsOn(ctx) && NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    @android.annotation.SuppressLint("MissingPermission")
    fun notify(ctx: Context, id: Int, channel: String, title: String, body: String, tab: String) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_conuco).setColor(0xFF1F6F3F.toInt())
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tabIntent(ctx, tab)).setAutoCancel(true)
            .setPriority(if (channel == CH_ALERTS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    /** One pass of the background job. Returns false only on a hard failure worth a retry. */
    suspend fun check(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
        if (!sp.contains("lat")) return true // no profile yet
        val lat = sp.getFloat("lat", 0f).toDouble(); val lng = sp.getFloat("lng", 0f).toDouble()
        val crops = sp.getString("crops", "")!!.split(',').filter { it.isNotBlank() }
        val lang = sp.getString("lang", "es")!!; Strings.lang = lang
        val place = sp.getString("place", "")!!
        val day = LocalDateTime.now().toLocalDate().toString()

        // 1. Weather → field decisions (offline rules; cached forecast if there is no signal).
        val wx = Weather(ctx).get(lat, lng, online = true)
        val an = wx?.let { Rules.analyse(it.json, crops, sp.getFloat("wind", 15f).toDouble()) }
        if (an != null) {
            updateWidget(ctx, an, place, lang)
            an.windows.firstOrNull()?.let { w ->
                val startsSoon = LocalDateTime.parse(w.start).isBefore(LocalDateTime.now().plusHours(24))
                if (startsSoon && sp.getString("n_spray", "") != w.start) {
                    notify(ctx, 2, CH_DAILY, tr("n_spray_title"), tr("n_spray_body", "w" to windowText(w, lang)), "today")
                    sp.edit().putString("n_spray", w.start).apply()
                }
            }
            if (an.disease == "high" && sp.getString("n_fungus", "") != day) {
                notify(ctx, 3, CH_DAILY, tr("n_fungus_title"), tr("n_fungus_body", "h" to an.wetHours), "doctor")
                sp.edit().putString("n_fungus", day).apply()
            }
        }

        // 2. Neighbours' outbreak reports (shared, anonymous, 0.1° cells) for the farmer's own crops.
        val uid = Firebase.auth.currentUser?.uid ?: return true
        val rows = runCatching { Repo(uid).loadOutbreaks(21) }.getOrNull() ?: return true
        val alerts = Repo.alerts(rows, lat, lng, sp.getFloat("radius", 30f).toDouble(), sp.getInt("min", 2), crops)
        val seen = sp.getStringSet("n_seen", emptySet())!!.associate { it.substringBeforeLast('=') to (it.substringAfterLast('=').toIntOrNull() ?: 0) }
        val fresh = alerts.filter { (seen["${it.crop}|${it.problemKey}"] ?: 0) < it.n }
        fresh.firstOrNull()?.let { a ->
            val more = if (fresh.size > 1) " " + tr("n_more", "n" to (fresh.size - 1)) else ""
            notify(ctx, 1, CH_ALERTS, tr("n_out_title"),
                tr("n_out_body", "p" to Crops.problemName(a.problemKey, lang), "c" to Crops.name(a.crop, lang), "n" to a.n, "km" to a.nearestKm) + more, "map")
        }
        sp.edit().putStringSet("n_seen", alerts.map { "${it.crop}|${it.problemKey}=${it.n}" }.toSet()).apply()
        return true
    }
}

class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = if (runCatching { Bg.check(applicationContext) }.getOrDefault(false)) Result.success() else Result.retry()
}
