package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import org.jsoup.nodes.Document
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLivre :
    Madara(
        "Manga Livre",
        "https://mangalivre.tv",
        "pt-BR",
        SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
    ),
    ConfigurableSource {

    override val id: Long = 2834885536325274328
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences by lazy {
        getPreferences()
    }

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("X-Requested-With", "XMLHttpRequest")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val fullUrl = if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url
        return client.newCall(GET(fullUrl, headers))
            .asObservableSuccess()
            .flatMap { response ->
                val document = response.asJsoup()
                val elements = document.select(chapterListSelector()).ifEmpty {
                    document.select("li.chapter-li")
                }

                if (elements.isNotEmpty()) {
                    return@flatMap Observable.just(elements.map { chapterFromElement(it) })
                }

                val ajaxUrl = "${fullUrl.trimEnd('/')}/ajax/chapters/"
                val request = POST(ajaxUrl, xhrHeaders, FormBody.Builder().build())

                client.newCall(request)
                    .asObservableSuccess()
                    .map { ajaxResponse ->
                        val ajaxDocument = ajaxResponse.asJsoup()
                        val ajaxElements = ajaxDocument.select(chapterListSelector()).ifEmpty {
                            ajaxDocument.select("li.chapter-li")
                        }

                        ajaxElements.map { chapterFromElement(it) }
                    }
            }
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(var imgs)")?.data()
            ?: throw IOException("Images not found. Open in WebView to solve Cloudflare/Turnstile.")

        val jsonString = IMAGES_REGEX.find(script)?.groupValues?.get(1)
            ?: throw IOException("Image list not found in script.")

        return jsonString.parseAs<List<String>>()
            .mapIndexed { idx, url ->
                Page(idx, imageUrl = url.trim())
            }
            .filter { it.imageUrl!!.startsWith("http") }
    }

    override val pageListParseSelector = ""

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Override Base URL"
            summary = "Default: ${super.baseUrl}"
            setDefaultValue(super.baseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart app to apply.", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private val IMAGES_REGEX = Regex("""var\s+imgs\s*=\s*(\[.*?\]);""")
    }
}
