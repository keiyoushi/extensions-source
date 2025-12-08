package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.getPreferences
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLivre :
    Madara("Manga Livre", "https://mangalivre.tv", "pt-BR", SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)),
    ConfigurableSource {

    override val id: Long = 2834885536325274328
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val baseUrl by lazy { preferences.getString(BASE_URL_PREF, super.baseUrl)!! }
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true
    override val pageListParseSelector = ""

    private val preferences by lazy { getPreferences() }

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("Referer", "$baseUrl/")

    override fun chapterListSelector() = "li.wp-manga-chapter, li.chapter-li"

    override fun xhrChaptersRequest(mangaUrl: String) =
        POST("$mangaUrl/ajax/chapters/", xhrHeaders, FormBody.Builder().build())

    override fun pageListParse(document: Document): List<Page> =
        document.select("script")
            .asSequence()
            .map { BASE64_REGEX.findAll(it.data()).joinToString("") { m -> m.groupValues[1] } }
            .firstNotNullOfOrNull { base64 ->
                runCatching {
                    val decoded = String(Base64.decode(base64, Base64.DEFAULT))
                    json.decodeFromString<List<String>>(decoded)
                        .mapIndexed { idx, url -> Page(idx, imageUrl = url.trim()) }
                }.getOrNull()
            } ?: throw Exception("Failed to load images. Please open the chapter in WebView first.")

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
        }.also(screen::addPreference)
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private val BASE64_REGEX = Regex("""\+=\s*['"]([^'"]*)['"]""")
    }
}
