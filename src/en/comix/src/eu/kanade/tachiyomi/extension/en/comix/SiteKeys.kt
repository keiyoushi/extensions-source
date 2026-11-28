package eu.kanade.tachiyomi.extension.en.comix

import keiyoushi.network.Fetch
import keiyoushi.utils.asJsoup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class SiteKeys(
    private val baseUrl: () -> String,
    private val fetchText: suspend (url: HttpUrl, fetch: Fetch) -> String,
) {

    private val mutex = Mutex()
    private var cached: Key? = null
    private var retryAt: TimeMark? = null

    private var rereadFor: String? = null

    suspend fun current(): SiteKey = mutex.withLock {
        cached?.let { return@withLock it.siteKey }
        if (retryAt?.hasNotPassedNow() == true) throw Exception(KEY_ERROR)
        val key = extract()
        if (key == null) {
            retryAt = TimeSource.Monotonic.markNow() + READ_RETRY
            throw Exception(KEY_ERROR)
        }
        cached = key
        key.siteKey
    }

    suspend fun onKeyRetired() {
        mutex.withLock { cached = null }
    }

    suspend fun noteBuild(buildId: String?) {
        if (buildId == null) return
        mutex.withLock {
            val ours = cached?.build ?: return
            if (buildId != rereadFor && isNewerBuild(buildId, ours)) {
                rereadFor = buildId
                cached = null
            }
        }
    }

    private suspend fun extract(): Key? {
        val homeUrl = baseUrl().toHttpUrl()
        val mainUrl = fetchText(homeUrl, Fetch.DOCUMENT).asJsoup(homeUrl.toString())
            .selectFirst("script[src*='/dist/main-']")
            ?.absUrl("src")
            ?.toHttpUrl()
            ?: return null
        val build = BUILD_ID.find(mainUrl.pathSegments.last())?.groupValues?.get(1) ?: return null
        val apicoreName = APICORE_IMPORT.find(fetchText(mainUrl, Fetch.SCRIPT))?.groupValues?.get(1) ?: return null
        val apicoreUrl = mainUrl.resolve(apicoreName) ?: return null
        val siteKey = ApicoreKey.read(fetchText(apicoreUrl, Fetch.SCRIPT)) ?: return null
        return Key(siteKey, build)
    }

    private class Key(val siteKey: SiteKey, val build: String)

    companion object {
        private val APICORE_IMPORT = Regex("""\./(apicore-[\w-]+\.js)""")
        private val BUILD_ID = Regex("""^main-([a-z0-9]+)-""")

        private fun isNewerBuild(theirs: String, ours: String): Boolean {
            val theirNumber = theirs.toLongOrNull(36) ?: return false
            val ourNumber = ours.toLongOrNull(36) ?: return false
            return theirNumber > ourNumber
        }

        private const val KEY_ERROR = "Couldn't read the site's key, try again later"
        private val READ_RETRY = 1.minutes
    }
}
