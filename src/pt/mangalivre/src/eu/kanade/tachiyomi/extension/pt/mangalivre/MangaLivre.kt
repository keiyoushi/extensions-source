package eu.kanade.tachiyomi.extension.pt.mangalivre

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferences
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val cookieInterceptor by lazy {
        CookieInterceptor(
            baseUrl.substringAfter("://"),
            listOf(
                "manga_reading_pass" to "verified",
                "manga_reading_ml" to "verified",
            ),
        )
    }
    private val preferences = getPreferences()

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(cookieInterceptor)
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            title = title.cleanTitleIfNeeded()
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return super.latestUpdatesFromElement(element).apply {
            title = title.cleanTitleIfNeeded()
        }
    }

    override val chapterUrlSuffix = ""
    override val id: Long = 2834885536325274328

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            val cleanedTitle = title.cleanTitleIfNeeded()
            if (cleanedTitle != title.trim()) {
                description = listOfNotNull(title, description)
                    .joinToString("\n\n")
                title = cleanedTitle
            }
        }
    }

    private fun String.cleanTitleIfNeeded(): String {
        var tempTitle = this
        customRemoveTitle().takeIf { it.isNotEmpty() }?.let { customRegex ->
            runCatching {
                tempTitle = tempTitle.replace(Regex(customRegex), "")
            }
        }
        if (isRemoveTitleVersion()) {
            tempTitle = tempTitle.replace(titleRegex, "")
        }
        return tempTitle.trim()
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!
    private fun isRemoveTitleVersion(): Boolean {
        return preferences.getBoolean("${REMOVE_TITLE_VERSION_PREF}_$lang", false)
    }
    private fun customRemoveTitle(): String =
        preferences.getString("${REMOVE_TITLE_CUSTOM_PREF}_$lang", "")!!

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
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
