package eu.kanade.tachiyomi.extension.es.templescanesp

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class TempleScanEsp :
    Madara(
        "Temple Scan",
        "https://aedexnox.kawi.lat",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ),
    ConfigurableSource {

    override val baseUrl get() = preferences.prefBaseUrl

    private val fetchedDomainUrl: String by lazy {
        if (!preferences.fetchDomainPref()) {
            return@lazy preferences.prefBaseUrl
        }

        try {
            val initClient = network.cloudflareClient
            val headers = super.headersBuilder()
                .add("apikey", SUPABASE_API_KEY)
                .add("Accept", "application/json")
                .build()

            val fetchedDomain = initClient.newCall(GET(SUPABASE_URL, headers)).execute().use { r ->
                if (!r.isSuccessful) return@use null

                val body = r.body.string()
                val value = try {
                    json.parseToJsonElement(body).jsonArray.first().jsonObject["value"]!!.jsonPrimitive.content
                } catch (_: Exception) {
                    null
                }

                if (value.isNullOrBlank()) return@use null

                val detected = value.trimEnd('/')
                val newDomain = if (detected.startsWith("http")) detected else "https://$detected"

                preferences.prefBaseUrl = newDomain

                newDomain
            }

            if (fetchedDomain != null) {
                return@lazy fetchedDomain
            }

            return@lazy preferences.prefBaseUrl
        } catch (_: Exception) {
            return@lazy preferences.prefBaseUrl
        }
    }

    override val versionId = 4

    override val mangaSubString = "serie"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val client by lazy {
        super.client.newBuilder()
            .rateLimitHost(fetchedDomainUrl.toHttpUrl(), 3, 1)
            .build()
    }

    private val preferences = getPreferences {
        this.getString(DEFAULT_BASE_URL_PREF, null).let { domain ->
            if (domain != super.baseUrl) {
                this.edit()
                    .putString(BASE_URL_PREF, super.baseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                    .apply()
            }
        }
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

    override fun pageListParse(document: Document): List<Page> {
        val form = document.selectFirst("form#redirect-form[method=post]")
            ?: return super.pageListParse(document)

        val url = form.attr("action")

        val headers = headersBuilder()
            .set("Referer", document.location())
            .build()

        val body = FormBody.Builder().apply {
            form.select("input[name]").forEach {
                add(it.attr("name"), it.attr("value"))
            }
        }.build()

        val redirectedDoc = client.newCall(POST(url, headers, body)).execute().asJsoup()

        return super.pageListParse(redirectedDoc)
    }

    private fun Element.imageFromStyle(): String = this.attr("style").substringAfter("url(").substringBefore(")")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Editar URL de la fuente"
            summary = "Para uso temporal, si la extensión se actualiza se perderá el cambio."
            dialogTitle = "Editar URL de la fuente"
            dialogMessage = "URL por defecto:\n${super.baseUrl}"
            setDefaultValue(super.baseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Reinicie la aplicación para aplicar los cambios", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }

        SwitchPreferenceCompat(screen.context).apply {
            key = FETCH_DOMAIN_PREF
            title = "Buscar dominio automáticamente"
            summary = "Intenta buscar el dominio automáticamente al abrir la fuente."
            setDefaultValue(true)
        }.also { screen.addPreference(it) }
    }

    private fun SharedPreferences.fetchDomainPref() = getBoolean(FETCH_DOMAIN_PREF, true)

    private var cachedBaseUrl: String? = null
    private var SharedPreferences.prefBaseUrl: String
        get() {
            if (cachedBaseUrl == null) {
                cachedBaseUrl = getString(BASE_URL_PREF, super.baseUrl)!!
            }
            return cachedBaseUrl!!
        }
        set(value) {
            cachedBaseUrl = value
            edit().putString(BASE_URL_PREF, value).apply()
        }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val FETCH_DOMAIN_PREF = "fetchDomain"

        // Obtained from an API call on https://templescanesp.net
        private const val SUPABASE_URL = "https://ysilhsqbtixygcgscvbb.supabase.co/rest/v1/parameters?select=value&name=eq.redirect_url_templescan"
        private const val SUPABASE_API_KEY = "sb_publishable_y5ZlqOnxowq6W7JTSZHSBQ_AQfHg77U"
    }
}
