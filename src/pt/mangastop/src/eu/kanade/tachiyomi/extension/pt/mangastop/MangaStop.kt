package eu.kanade.tachiyomi.extension.pt.mangastop

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.lib.randomua.PREF_KEY_RANDOM_UA
import eu.kanade.tachiyomi.lib.randomua.RANDOM_UA_VALUES
import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaStop :
    MangaThemesia(
        "Manga Stop",
        "https://mangastop.net",
        "pt-BR",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
    ),
    ConfigurableSource {

    private val preferences by getPreferencesLazy {
        if (getPrefUAType() != UserAgentType.OFF || getPrefCustomUA().isNullOrBlank().not()) {
            return@getPreferencesLazy
        }
        edit().putString(PREF_KEY_RANDOM_UA, RANDOM_UA_VALUES.last()).apply()
    }

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(
            CookieInterceptor(baseUrl.substringAfter("//"), "wpmanga-ada" to "1"),
        )
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .addInterceptor(ClientHintsInterceptor())
        .rateLimit(2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        .add("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("Sec-Fetch-Dest", "document")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Site", "none")
        .add("Sec-Fetch-User", "?1")
        .add("Upgrade-Insecure-Requests", "1")

    override fun pageListParse(document: Document): List<Page> {
        val pages = super.pageListParse(document)
            .filterNot { it.imageUrl?.contains("mihon", true) == true }

        if (pages.isNotEmpty()) return pages

        return MangaThemesia.JSON_IMAGE_LIST_REGEX.find(document.toString())
            ?.groupValues?.get(1)
            ?.let { json.parseToJsonElement(it).jsonArray }
            ?.mapIndexed { i, el ->
                Page(i, document.location(), el.jsonPrimitive.content)
            }
            .orEmpty()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }
}
