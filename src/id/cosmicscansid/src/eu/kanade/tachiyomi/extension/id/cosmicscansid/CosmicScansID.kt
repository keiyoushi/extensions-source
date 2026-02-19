package eu.kanade.tachiyomi.extension.id.cosmicscansid

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class CosmicScansID :
    MangaThemesia(
        "CosmicScans.id",
        "https://lc4.cosmicscans.asia",
        "id",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
    ),
    ConfigurableSource {

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

    override val client: OkHttpClient = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(20, 4, TimeUnit.SECONDS)
        .build()

    override val hasProjectPage = true

    private var cachedBaseUrl: String? = null
    private var SharedPreferences.prefBaseUrl: String
        get() {
            if (cachedBaseUrl == null) {
                cachedBaseUrl = getString(BASE_URL_PREF, defaultBaseUrl)!!
            }
            return cachedBaseUrl!!
        }
        set(value) {
            cachedBaseUrl = value
            edit().putString(BASE_URL_PREF, value).apply()
        }

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("page/$page/")
            .addQueryParameter("s", query)

        return GET(url.build())
    }

    override fun searchMangaSelector() = ".bixbox:not(.hothome):has(.hpage) .utao .uta .imgu, .bixbox:not(.hothome) .listupd .bs .bsx"

    // manga details
    override val seriesDescriptionSelector = ".entry-content[itemprop=description] :not(a,p:has(a))"
    override fun Elements.imgAttr(): String = this.first()?.imgAttr() ?: ""

    // pages
    override val pageSelector = "div#readerarea img:not(noscript img):not([alt=''])"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)

        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Edit source URL"
            summary = "For temporary use, if the extension is updated the change will be lost."
            dialogTitle = title
            dialogMessage = "Default URL:\n$defaultBaseUrl"
            setDefaultValue(defaultBaseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart the application to apply the changes", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
    }
}
