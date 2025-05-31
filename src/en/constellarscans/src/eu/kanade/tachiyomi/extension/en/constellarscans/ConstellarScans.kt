package eu.kanade.tachiyomi.extension.en.constellarscans

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document

class ConstellarScans :
    MangaThemesia(
        "Constellar Scans",
        "https://constellarcomic.com",
        "en",
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .setRandomUserAgent(
                preferences.getPrefUAType(),
                preferences.getPrefCustomUA(),
            )
            .rateLimit(1, 1)
            .build()
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("DNT", "1")
        .add("Upgrade-Insecure-Requests", "1")

    override val seriesStatusSelector = ".status"

    override fun pageListRequest(chapter: SChapter): Request =
        super.pageListRequest(chapter).newBuilder()
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
            )
            .header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-User", "?1")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()

    override fun pageListParse(document: Document): List<Page> {
        countViews(document)

        val html = document.toString()
        if (!html.contains("ts_rea_der_._run(\"")) {
            return super.pageListParse(document)
        }

        val tsReaderRawData = html
            .substringAfter("ts_rea_der_._run(\"")
            .substringBefore("\")")
            .replace(Regex("""\D"""), "")
            .chunked(4)
            .map {
                val tenthsAndOnes = it.chunked(2).map {
                    val num = it.toInt()
                    num / 10 + num % 10
                }
                (tenthsAndOnes[0] * 10 + tenthsAndOnes[1] + 32).toChar()
            }
            .joinToString("")

        return json.parseToJsonElement(tsReaderRawData).jsonObject["sources"]!!.jsonArray[0].jsonObject["images"]!!.jsonArray.mapIndexed { idx, it ->
            Page(idx, imageUrl = it.jsonPrimitive.content)
        }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .header("Sec-Fetch-Dest", "image")
        .header("Sec-Fetch-Mode", "no-cors")
        .header("Sec-Fetch-Site", "same-origin")
        .build()
}
