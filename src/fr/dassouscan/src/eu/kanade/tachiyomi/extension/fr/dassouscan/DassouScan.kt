package eu.kanade.tachiyomi.extension.fr.dassouscan

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DassouScan :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val hidePremium: Boolean
        get() = preferences.getBoolean(PREF_HIDE_PREMIUM, true)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM
            title = "Masquer les chapitres premium"
            summary = "Masquer les chapitres verrouillés en accès anticipé payant"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private val dateFormat = SimpleDateFormat("d MMMM yyyy 'à' HH:mm", Locale.FRENCH)
    private val shortDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder().apply {
            addQueryParameter("tri", "popular")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("article.dsc-cat-card").map { element ->
            SManga.create().apply {
                title = element.attr("data-title")
                    .ifEmpty { element.selectFirst(".dsc-cat-card__title a")?.text().orEmpty() }
                    .ifEmpty { throw Exception("Title is empty") }
                setUrlWithoutDomain(element.selectFirst("a.dsc-cat-card__cover-link, .dsc-cat-card__title a, a.dsc-cat-card__open")!!.absUrl("href"))

                thumbnail_url = element.selectFirst(".dsc-cat-card__cover img")?.attr("abs:src")
            }
        }

        val hasNextPage = document.selectFirst("a.dsc-cat__pager-btn[href]:contains(Suivant)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder().apply {
            addQueryParameter("tri", "latest")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")?.text()?.takeIf { it.isNotEmpty() } ?: throw Exception("Manga title is missing")
            description = document.selectFirst(".dsc-mf__synopsis-text")?.text()
            genre = document.select(".dsc-mf__tags a.dsc-mf__tag").joinToString { it.text() }

            thumbnail_url = document.selectFirst(".dsc-mf__cover img")?.attr("abs:src")
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.dsc-manga-chapter-block:not(:has(a[href*=/inscription/]))").mapNotNull { element ->
            val isPremium = element.hasClass("chapter--locked") ||
                element.selectFirst(".dsc-ch-hl__access--premium, a.is-locked") != null
            if (isPremium && hidePremium) {
                return@mapNotNull null
            }
            SChapter.create().apply {
                val link = element.selectFirst("a.dsc-manga-chapter-block__title-link")!!

                setUrlWithoutDomain(link.absUrl("href"))
                name = buildString {
                    if (isPremium) {
                        append("🔒 ")
                    }
                    append(element.selectFirst(".chapter-title")?.ownText() ?: link.text())
                }

                val dateStr = element.selectFirst(".chapter-info")?.text() ?: ""
                date_upload = if (dateStr.contains("à")) {
                    dateFormat.tryParse(dateStr)
                } else {
                    shortDateFormat.tryParse(dateStr)
                }
            }
        }.reversed()
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#dsc-chapter-reader-content .dsc-chapter-strip-img").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val PREF_HIDE_PREMIUM = "pref_hide_premium"
    }
}
