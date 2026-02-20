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
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
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
        .addInterceptor { chain ->
            val request = chain.request()
            // For covers
            if (chain.request().url.host.contains("images")) {
                val newRequest = request.newBuilder().apply {
                    header("Sec-Fetch-Dest", "image")
                    header("Sec-Fetch-Mode", "no-cors")
                    header("Sec-Fetch-Site", "same-site")
                }.build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
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
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-User", "?1")
        .set("Upgrade-Insecure-Requests", "1")

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

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", "same-site")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().filterNot { it is AuthorFilter || it is YearFilter }
        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }
}
