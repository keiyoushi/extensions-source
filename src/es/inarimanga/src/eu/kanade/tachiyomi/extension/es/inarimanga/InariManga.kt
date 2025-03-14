package eu.kanade.tachiyomi.extension.es.inarimanga

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class InariManga :
    Madara(
        "Inari Manga",
        "https://clubinari.org",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ),
    ConfigurableSource {

    override val id = 8115430002084356109

    private val defaultBaseUrl: String = super.baseUrl

    private val preferences = getPreferences {
        getString(DEFAULT_BASE_URL_PREF, defaultBaseUrl).let { domain ->
            if (domain != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    private val isCi = System.getenv("CI") == "true"

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    private val fetchedDomainUrl: String by lazy {
        if (!preferences.fetchDomainPref()) return@lazy preferences.prefBaseUrl
        try {
            val initClient = network.cloudflareClient
            val headers = super.headersBuilder().build()
            val document = initClient.newCall(GET("https://nakamatoon.com", headers)).execute().asJsoup()
            val domain = document.selectFirst("section#list-pages a[target][href][href!=#]")?.attr("href")
                ?: return@lazy preferences.prefBaseUrl
            val host = initClient.newCall(GET(domain, headers)).execute().request.url.host
            val newDomain = "https://$host"
            preferences.prefBaseUrl = newDomain
            newDomain
        } catch (_: Exception) {
            preferences.prefBaseUrl
        }
    }

    override val mangaSubString = "series"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val client by lazy {
        super.client.newBuilder()
            .rateLimitHost(fetchedDomainUrl.toHttpUrl(), 3, 1)
            .build()
    }

    override fun popularMangaSelector() = "div.latest-poster"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("a[style].bg-cover")?.imageFromStyle()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun searchMangaSelector() = "button.group > div.grid"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("div[style].bg-cover")?.imageFromStyle()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override val mangaDetailsSelectorTitle = "div.wp-manga div.grid > h1"
    override val mangaDetailsSelectorStatus = "div.wp-manga div[alt=type]:eq(0) > span"
    override val mangaDetailsSelectorGenre = "div.wp-manga div[alt=type]:gt(0) > span"
    override val mangaDetailsSelectorDescription = "div.wp-manga div#expand_content"

    override fun chapterListSelector() = "ul#list-chapters li > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("div.grid > span")!!.text()
        date_upload = element.selectFirst("div.grid > div")?.text()?.let { parseChapterDate(it) } ?: 0
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    private fun Element.imageFromStyle(): String {
        return this.attr("style").substringAfter("url(").substringBefore(")")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = FETCH_DOMAIN_PREF
            title = "Buscar dominio automáticamente"
            summary = "Intenta buscar el dominio automáticamente al abrir la fuente."
            setDefaultValue(FETCH_DOMAIN_PREF_DEFAULT)
        }.also { screen.addPreference(it) }

        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Editar URL de la fuente"
            summary = "Para uso temporal, si la extensión se actualiza se perderá el cambio."
            dialogTitle = "Editar URL de la fuente"
            dialogMessage = "URL por defecto:\n$defaultBaseUrl"
            setDefaultValue(defaultBaseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Reinicie la aplicación para aplicar los cambios", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    private var _cachedBaseUrl: String? = null
    private var SharedPreferences.prefBaseUrl: String
        get() {
            if (_cachedBaseUrl == null) {
                _cachedBaseUrl = getString(BASE_URL_PREF, defaultBaseUrl)!!
            }
            return _cachedBaseUrl!!
        }
        set(value) {
            _cachedBaseUrl = value
            edit().putString(BASE_URL_PREF, value).apply()
        }

    private fun SharedPreferences.fetchDomainPref() = getBoolean(FETCH_DOMAIN_PREF, FETCH_DOMAIN_PREF_DEFAULT)

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val FETCH_DOMAIN_PREF = "fetchDomain"
        private const val FETCH_DOMAIN_PREF_DEFAULT = true
    }
}
