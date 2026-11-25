package com.petiht.conuco

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance

/**
 * One place for Analytics events, Crashlytics (crashes + non-fatal errors with context) and Performance traces
 * (how long a diagnosis, a brief or a question takes, and whether it ran on the device or in the cloud).
 * Nothing here carries names, photos or exact places: only the pseudonymous Firebase uid and feature labels.
 */
object Telemetry {
    fun event(name: String, vararg params: Pair<String, String>) =
        runCatching { Firebase.analytics.logEvent(name) { params.forEach { (k, v) -> param(k, v) } } }

    fun user(uid: String?) = runCatching { Firebase.crashlytics.setUserId(uid ?: "") }
    fun key(k: String, v: String) = runCatching { Firebase.crashlytics.setCustomKey(k, v) }

    /** A handled error: the user saw a message, Crashlytics gets the stack trace and where it happened. */
    fun error(e: Throwable, where: String) = runCatching {
        Firebase.crashlytics.setCustomKey("where", where)
        Firebase.crashlytics.recordException(e)
    }

    class Span internal constructor(private val t: Trace?) {
        fun attr(k: String, v: String) { runCatching { t?.putAttribute(k.take(40), v.take(100)) } }
        fun metric(k: String, v: Long) { runCatching { t?.putMetric(k.take(100), v) } }
    }

    /** Times [block] as a Performance Monitoring trace. A failure is recorded and re-thrown. */
    suspend fun <R> trace(name: String, vararg attrs: Pair<String, String>, block: suspend (Span) -> R): R {
        val t = runCatching { Firebase.performance.newTrace(name).also { it.start() } }.getOrNull()
        val span = Span(t)
        attrs.forEach { (k, v) -> span.attr(k, v) }
        return try {
            block(span).also { span.attr("ok", "1") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            span.attr("ok", "cancel"); throw e
        } catch (e: Throwable) {
            span.attr("ok", "0"); error(e, name); throw e
        } finally {
            runCatching { t?.stop() }
        }
    }
}
