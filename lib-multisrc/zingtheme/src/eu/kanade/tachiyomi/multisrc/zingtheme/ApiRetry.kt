package eu.kanade.tachiyomi.multisrc.zingtheme

import kotlinx.coroutines.CancellationException

internal suspend fun <C, T> retryZing(
    action: String,
    config: suspend (Boolean) -> C,
    invalidate: suspend () -> Unit,
    request: suspend (C) -> T,
): T {
    var failure: Throwable? = null
    repeat(2) { attempt ->
        val current = config(attempt > 0)
        try {
            return request(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
            invalidate()
        }
    }
    throw Exception("Request to the site's API failed ($action)", failure)
}
