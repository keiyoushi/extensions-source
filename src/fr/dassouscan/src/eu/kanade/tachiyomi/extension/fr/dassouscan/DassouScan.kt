package eu.kanade.tachiyomi.extension.fr.dassouscan

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.tryParseDateTime
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DassouScan :
    KeiSource(),
    ConfigurableSource {

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

    private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH)
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)
    private val zoneId = ZoneId.of("Europe/Paris")

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder().apply {
            addQueryParameter("tri", "popular")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return parseCatalogue(client.get(url).asJsoup())
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder().apply {
            addQueryParameter("tri", "latest")
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return parseCatalogue(client.get(url).asJsoup())
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/catalogue".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return parseCatalogue(client.get(url).asJsoup())
    }

    private fun parseCatalogue(document: Document): MangasPage {
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

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        if (url.pathSegments.firstOrNull() != "manga" || url.pathSegments.size != 2) {
            return null
        }
        return parseMangaDetails(client.get(url).asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        title = document.selectFirst("h1")?.text()?.takeIf { it.isNotEmpty() } ?: throw Exception("Manga title is missing")
        description = document.selectFirst(".dsc-mf__synopsis-text")?.text()
        genre = document.select(".dsc-mf__tags a.dsc-mf__tag").joinToString { it.text() }

        thumbnail_url = document.selectFirst(".dsc-mf__cover img")?.attr("abs:src")
    }

    // ============================= Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> {
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
                    dateTimeFormat.tryParseDateTime(dateStr, zoneId)
                } else {
                    dateFormat.tryParseDate(dateStr, zoneId)
                }
            }
        }.reversed()
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("#dsc-chapter-reader-content .dsc-chapter-strip-img").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }

    companion object {
        private const val PREF_HIDE_PREMIUM = "pref_hide_premium"
    }
}
