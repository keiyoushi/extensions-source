package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.getPreferences
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLivre :
    Madara("Manga Livre", "https://mangalivre.tv", "pt-BR", SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)),
    ConfigurableSource {

    override val id: Long = 2834885536325274328
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val baseUrl by lazy { preferences.getString(BASE_URL_PREF, super.baseUrl)!! }

    private val preferences by lazy { getPreferences() }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        .set("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .set("Cache-Control", "no-cache")
        .set("Pragma", "no-cache")
        .set("Upgrade-Insecure-Requests", "1")

    override fun chapterListSelector() = "li.wp-manga-chapter, li.chapter-li"

    override fun xhrChaptersRequest(mangaUrl: String) = POST("$mangaUrl/ajax/chapters/", xhrHeaders, FormBody.Builder().build())

    override fun pageListRequest(chapter: SChapter): Request {
        val fullUrl = if (chapter.url.startsWith("http")) {
            chapter.url
        } else {
            "$baseUrl${chapter.url}"
        }
        return GET(fullUrl, headers)
    }

    override fun pageListParse(document: Document): List<Page> = document.select(".reading-content .page-break img.wp-manga-chapter-img[src]")
        .mapIndexed { idx, img ->
            Page(idx, document.location(), img.attr("abs:src").trim())
        }

    override fun imageRequest(page: Page): Request {
        val referer = if (page.url.isNotEmpty()) page.url else "$baseUrl/"
        val requestHeaders = headers.newBuilder()
            .set("Referer", referer)
            .build()
        return GET(page.imageUrl!!, requestHeaders)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)

        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Override Base URL"
            summary = "Default: ${super.baseUrl}"
            setDefaultValue(super.baseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart app to apply.", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
    }
}
