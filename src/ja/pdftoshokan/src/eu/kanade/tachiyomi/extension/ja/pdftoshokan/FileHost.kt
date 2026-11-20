package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import android.content.SharedPreferences
import keiyoushi.network.Fetch
import keiyoushi.network.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * The site's file host. A volume is fetched in one request the way the site's PDF viewer does, as its cross-site call
 * from the site, and written out as it arrives so its first pages can be read before the rest is in. None go out while
 * the host is rate limiting: every request then makes the limit last longer.
 */
internal class FileHost(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val siteUrl: String,
    private val preferences: SharedPreferences,
) {
    /** Throws while the host is rate limiting, so nothing is fetched (or decoded for it) in vain. */
    fun checkNotPaused() {
        val pausedUntil = preferences.getLong(PAUSED_UNTIL_KEY, 0)
        if (System.currentTimeMillis() < pausedUntil) throw rateLimited(pausedUntil)
    }

    suspend fun download(url: HttpUrl, out: OutputStream) {
        checkNotPaused()
        // A volume is far too big for the HTTP cache, and is stored by the caller anyway
        client.get(url, headers, Fetch.XHR.from(siteUrl), NO_STORE, ensureSuccess = false).use { response ->
            when (response.code) {
                200 -> withContext(Dispatchers.IO) { response.body.byteStream().use { it.copyTo(out) } }
                // The host also answers a file that isn't there with 403, only without Cloudflare's block page
                403 -> throw if (response.body.string().contains("cloudflare", ignoreCase = true)) FileHostRefused() else VolumeMissing()
                429 -> {
                    val pause = response.header("Retry-After")?.toLongOrNull()?.seconds ?: DEFAULT_PAUSE
                    val until = System.currentTimeMillis() + pause.inWholeMilliseconds
                    preferences.edit().putLong(PAUSED_UNTIL_KEY, until).apply()
                    throw rateLimited(until)
                }
                else -> throw IOException("Volume download failed (HTTP ${response.code})")
            }
        }
    }

    private fun rateLimited(until: Long): IOException {
        val minutes = ceil((until - System.currentTimeMillis()) / 60_000.0).toLong().coerceAtLeast(1)
        return IOException("The file host is limiting downloads after too many in a short time, try again in $minutes min")
    }

    private companion object {
        const val PAUSED_UNTIL_KEY = "rate_limited_until"
        val DEFAULT_PAUSE: Duration = 1.hours
        val NO_STORE: CacheControl = CacheControl.Builder().noStore().build()
    }
}

internal class VolumeMissing : IOException("Volume not found")

/** The host only lets a volume's file through with the pass decode.php hands out along with it. */
internal class FileHostRefused : IOException("The file host turned the volume away, try again in a few minutes")
