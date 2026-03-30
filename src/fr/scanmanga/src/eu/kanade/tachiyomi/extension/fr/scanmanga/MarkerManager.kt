package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

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
        val smlParam: String,
        val smeParam: String,
        val chapterInfo: String,
    )

    @Serializable
    class ApiConfig(
        val pageListUrl: String,
        val requestBody: String,
        val headers: Map<String, String>? = null,
    )
}

@Serializable
class WhatsUp(
    val systemNotice: String? = null,
)

class MarkerManager(
    private val client: OkHttpClient,
    private val preferences: SharedPreferences,
) {
    private var cachedMarkers: RemoteMarkers? = null

    companion object {
        private const val MARKER_URL = "https://github.com/Starmania/scan-manga/releases/latest/download/marker.json"
        private const val NOTICE_URL = "https://github.com/Starmania/scan-manga/releases/latest/download/message.json"

        const val PREF_MARKERS_JSON = "external_markers_json"

        private const val PARSER_VERSION = 1
    }

    fun getMarkers(): RemoteMarkers {
        val cachedJson = preferences.getString(PREF_MARKERS_JSON, null)

        cachedMarkers?.let {
            if (PARSER_VERSION in it.validVersion) return it
        }

        if (cachedJson != null) {
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

    fun fetchWithRetry(): RemoteMarkers {
        return (1..3).firstNotNullOfOrNull {
            run {
                client.newCall(GET(MARKER_URL)).execute().use { response ->
                    if (!response.isSuccessful) return@run null

                    val basic = response.parseAs<RemoteMarkersBasic>()

                    if (PARSER_VERSION !in basic.validVersion) {
                        return@run null
                    }

                    response.parseAs<RemoteMarkers>().also { markers ->
                        cachedMarkers = markers
                        preferences.edit()
                            .putString(PREF_MARKERS_JSON, response.body.string())
                            .apply()
                    }
                }
            }
        } ?: error("Update the extension !")
    }

    fun handleFatalFailure(originalError: Throwable): Nothing {
        val messageObject = fetchMessage()

        if (messageObject?.systemNotice != null) {
            error(messageObject.systemNotice.replace("{message}", originalError.message ?: ""))
        }

        throw originalError
    }

    private fun fetchMessage(): WhatsUp? = run {
        client.newCall(GET(NOTICE_URL)).execute().use { response ->
            if (!response.isSuccessful) return@run null
            response.parseAs<WhatsUp>()
        }
    }
}
