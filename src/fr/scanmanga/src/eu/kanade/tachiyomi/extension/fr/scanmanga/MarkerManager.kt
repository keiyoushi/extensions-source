package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.content.SharedPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
class RemoteMarkersBasic(
    val validVersion: List<Int>,
)

@Serializable
class RemoteMarkers(
    val validVersion: List<Int>,
    val selectors: Selectors,
    val regexes: Regexes,
    val apiConfig: ApiConfig,
) {
    @Serializable
    class Selectors(val packedScript: String)

    @Serializable
    class Regexes(
        val hunterObfuscation: String,
        val smlParam: String, // Split from parameters
        val smeParam: String, // Split from parameters
        val chapterInfo: String,
    )

    @Serializable
    class ApiConfig(
        val pageListUrl: String,
        val requestBody: String,
        val headers: Map<String, String>? = null,
    )
}

class MarkerManager(
    private val client: OkHttpClient,
    private val preferences: SharedPreferences,
) {
    private var cachedMarkers: RemoteMarkers? = null

    companion object {
        private const val MARKER_URL = "https://github.com/Starmania/scan-manga/releases/latest/download/marker.json"
        const val PREF_MARKERS_JSON = "external_markers_json"
        const val PREF_MARKERS_LAST_UPDATE = "external_markers_last_update"
        private const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours

        private const val PARSER_VERSION = 1

        val DEFAULT_MARKERS = RemoteMarkers(
            validVersion = listOf(1),
            selectors = RemoteMarkers.Selectors(
                packedScript = "script:containsData(eval\\(function \\()",
            ),
            regexes = RemoteMarkers.Regexes(
                hunterObfuscation = """eval\(function \(\w,\w,\w,\w,\w,\w(?:,[^)]+)?\)\{.*?\}\("([^"]+)",\d+,"([^"]+)",(\d+),(\d+),\d+\)\)""",
                smlParam = """sml\s*=\s*'([^']+)'""",
                smeParam = """sme\s*=\s*'([^']+)'""",
                chapterInfo = """const idc = (\d+)""",
            ),
            apiConfig = RemoteMarkers.ApiConfig(
                pageListUrl = "https://bqj.{topDomain}/lel/{chapterId}.json",
                requestBody = """{"a":"{sme}","b":"{sml}","c":"{fingerprint}"}""",
                headers = mapOf("Token" to "yf"),
            ),
        )
    }

    fun getMarkers(): RemoteMarkers {
        val lastUpdate = preferences.getString(PREF_MARKERS_LAST_UPDATE, "0")?.toLongOrNull() ?: 0L
        val cachedJson = preferences.getString(PREF_MARKERS_JSON, null)
        val cacheExpired = (System.currentTimeMillis() - lastUpdate) >= CACHE_TTL_MS

        cachedMarkers?.let {
            if (PARSER_VERSION in it.validVersion && cacheExpired) return it
        }

        if (cachedJson != null && !cacheExpired) {
            // Populate the class with the cached JSON
            try {
                val markers = cachedJson.parseAs<RemoteMarkersBasic>()
                if (PARSER_VERSION in markers.validVersion) {
                    cachedMarkers = cachedJson.parseAs<RemoteMarkers>()
                    return cachedMarkers!!
                }
            } catch (_: Exception) {
            }
        }

        return fetchWithRetry()
    }

    private fun fetchWithRetry(): RemoteMarkers {
        return (1..3).firstNotNullOfOrNull {
            runCatching {
                val request = Request.Builder().url(MARKER_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null

                    val bodyString = response.body.string()
                    val basic = bodyString.parseAs<RemoteMarkersBasic>()

                    if (PARSER_VERSION !in basic.validVersion) {
                        // No need to retry if the version is not compatible, 10ms won't make a difference
                        return DEFAULT_MARKERS
                    }

                    bodyString.parseAs<RemoteMarkers>().also { markers ->
                        cachedMarkers = markers
                        preferences.edit()
                            .putString(PREF_MARKERS_JSON, bodyString)
                            .putString(PREF_MARKERS_LAST_UPDATE, System.currentTimeMillis().toString())
                            .apply()
                    }
                }
            }.getOrNull()
        } ?: DEFAULT_MARKERS
    }
}
