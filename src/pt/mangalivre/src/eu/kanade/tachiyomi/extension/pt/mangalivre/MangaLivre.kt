package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.FormBody
import okhttp3.Response
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLivre :
    Madara(
        "Manga Livre",
        "https://mangalivre.tv",
        "pt-BR",
        SimpleDateFormat("dd.MM.yyyy", Locale("pt")),
    ),
    ConfigurableSource {

    override val id: Long = 2834885536325274328
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("X-Requested-With", "XMLHttpRequest")

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()
        var document = Jsoup.parse(html)

        if (document.selectFirst("li.wp-manga-chapter") == null) {
            val mangaUrl = response.request.url.toString().trimEnd('/')
            val headers = headersBuilder().set("Referer", mangaUrl).build()
            val request = POST("$mangaUrl/ajax/chapters/", headers, FormBody.Builder().build())

            client.newCall(request).execute().use { xhr ->
                if (!xhr.isSuccessful) {
                    throw IOException("Failed to fetch chapters via AJAX: ${xhr.code}. Open in WebView.")
                }
                document = Jsoup.parse(xhr.body.string())
            }
        }

        val elements = document.select("li.wp-manga-chapter").ifEmpty {
            document.select("li.chapter-li")
        }

        if (elements.isEmpty()) {
            throw IOException("No chapters found. Open in WebView to solve Cloudflare/Turnstile.")
        }

        return elements.map { chapterFromElement(it) }
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(var imgs)")?.data()
            ?: throw IOException("Images not found. Open in WebView to solve Cloudflare/Turnstile.")

        val jsonString = IMAGES_REGEX.find(script)?.groupValues?.get(1)
            ?: throw IOException("Image list not found in script.")

        val jsonArray = JSONArray(jsonString)

        return buildList(jsonArray.length()) {
            for (i in 0 until jsonArray.length()) {
                val url = jsonArray.getString(i).trim()
                if (url.startsWith("http")) {
                    add(Page(size, "", url))
                }
            }
        }.ifEmpty { throw IOException("Image list is empty.") }
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
