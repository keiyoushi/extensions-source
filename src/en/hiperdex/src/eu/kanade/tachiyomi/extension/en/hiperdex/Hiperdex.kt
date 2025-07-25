package eu.kanade.tachiyomi.extension.en.hiperdex

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import rx.Observable

class Hiperdex :
    Madara(
        "Hiperdex",
        "https://hiperdex.tv",
        "en",
    ),
    ConfigurableSource {

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    private val preferences = getPreferences()

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val client = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default URL:\n\t${super.baseUrl}"
            setDefaultValue(super.baseUrl)
            setOnPreferenceChangeListener { _, newValue ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }

        addRandomUAPreferenceToScreen(screen)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val mangaUrl = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")
        }.build()
        return client.newCall(GET(mangaUrl, headers))
            .asObservableSuccess().map { response ->
                val document = response.asJsoup()
                val anchor = document.getElementById("loop-content")
                if (anchor == null) {
                    MangasPage(emptyList(), false)
                } else {
                    val mangaList = mutableListOf<SManga>()
                    val elementsList = anchor.select(".page-listing-item")
                    for (element in elementsList) {
                        mangaList.add(
                            SManga.create().apply {
                                val imgSrc = element.selectFirst("img")!!.attr("src")
                                val linkElement = element.selectFirst("a")!!

                                setUrlWithoutDomain(linkElement.attr("href"))
                                title = linkElement.attr("title")
                                thumbnail_url = imgSrc
                            },
                        )
                    }

                    MangasPage(mangaList, false)
                }
            }
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { defaultBaseUrl ->
            if (defaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, super.baseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                    .apply()
            }
        }
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Edit source URL (requires restart)"
        private const val BASE_URL_PREF_SUMMARY = "The default settings will be applied when the extension is next updated"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Restart app to apply new setting."
    }
}
