package eu.kanade.tachiyomi.extension.en.templescan

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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import kotlin.time.Instant

@Source
abstract class TempleScan :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy {
        edit().remove("pref_rsc_keys").remove("pref_rsc_key_table").apply()
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1)
    }

    private val sitePages by lazy { SitePages(baseUrl) { headers["User-Agent"]!! } }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", OrderFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", OrderFilter.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val order = filters.firstInstanceOrNull<OrderFilter>()
        // The site search ignores the filters; the cards of every status give its results their covers
        val args = CatalogArgs(
            statuses = if (query.isNotBlank()) STATUSES else filters.firstInstanceOrNull<StatusFilter>()?.selected ?: STATUSES.take(1),
            order = order?.selected,
            direction = order?.direction,
            query = query.trim().ifEmpty { null },
        )
        val catalog = sitePages.read("$baseUrl/comics", "catalog.js", args.toJsonString()).parseAs<CatalogPage>()

        val cards = catalog.cards.flatMap { it.asJsoup(baseUrl).select("a[href^=/comic/]:has(h2)") }
            .map(::cardToManga)
            .distinctBy { it.url }
        val mangas = catalog.results?.let { results ->
            val covers = cards.associate { it.url to it.thumbnail_url }
            results.asJsoup(baseUrl).select("[role=option][href^=/comic/]").map { option ->
                // Title, alternative names, then the status and type tags
                val info = option.selectFirst("> span:last-child")!!
                val tags = info.select("> span:last-child > span")
                SManga.create().apply {
                    url = "/comic/${option.absUrl("href").toHttpUrl().pathSegments[1]}"
                    title = info.selectFirst("> span")!!.textOrNull()!!
                    thumbnail_url = covers[url]
                    status = parseStatus(tags.getOrNull(0)?.text())
                    genre = tags.getOrNull(1)?.text()?.withoutRating()
                }
            }
        } ?: cards

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun cardToManga(link: Element) = SManga.create().apply {
        val card = link.parent()!!
        url = "/comic/${link.absUrl("href").toHttpUrl().pathSegments[1]}"
        title = link.textOrNull()!!
        thumbnail_url = card.selectFirst("img")?.absUrl("src")
        genre = card.selectFirst("> span")?.text()?.withoutRating()
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "comic") {
            return null
        }

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            this.url = "/comic/$slug"
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = sitePages.read(getMangaUrl(manga), "series.js")
            .parseAs<HtmlPage>().html
            .asJsoup(baseUrl)
        val details = if (fetchDetails) parseDetails(document, manga.url) else manga
        return SMangaUpdate(details, parseChapters(document))
    }

    private fun parseDetails(document: Document, url: String): SManga {
        val series = document.jsonLd<ComicSeriesLd> { it.isSeries }
        val genres = series?.genre.orEmpty()
        val stats = document.select("ul[aria-label=\"Series stats\"] li")

        return SManga.create().apply {
            this.url = url
            title = document.selectFirst("#series-title")!!.text()
            thumbnail_url = series?.image
            author = (document.info("Author") ?: series?.author?.name)
                ?.split('|')?.map(String::trim)?.filter(String::isNotEmpty)?.joinToString()?.ifEmpty { null }
            status = stats.map { parseStatus(it.text()) }.firstOrNull { it != SManga.UNKNOWN } ?: SManga.UNKNOWN
            genre = buildList {
                if (genres.any { it.equals("+18", ignoreCase = true) }) add("Adult")
                addAll(genres.filterNot { it.equals("+18", ignoreCase = true) })
            }.joinToString().ifEmpty { null }
            description = MarkdownDescription.Builder().apply {
                description(document.selectFirst("#series-synopsis-text"))
                field("Views", stats.selectFirst("span[title$= views]")?.attrOrNull("title")?.removeSuffix(" views"))
                field("Studio", document.info("Studio"))
                field("Released", document.info("Released"))
                // Commas also appear inside names; only `|` always separates them, in author names too
                altTitles((series?.alternateName ?: document.info("Also known as"))?.split('|').orEmpty())
            }.build()
        }
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED_CHAPTERS, true)
        return document.select("a[id^=chapter-link-]").mapNotNull { row ->
            val premium = row.selectFirst("span:matchesOwn(^Premium$)") != null
            if (hideLocked && premium) return@mapNotNull null

            // The chapter name, then its title if it has one
            val spans = row.select("div:has(> span > time) > span:not(:has(time))")
            SChapter.create().apply {
                url = row.absUrl("href").toHttpUrl().encodedPath
                name = buildString {
                    if (premium) append(LOCK)
                    append(spans.first()!!.textOrNull()!!)
                    spans.getOrNull(1)?.textOrNull()?.let { append(": ", it) }
                }
                date_upload = Instant.tryParse(row.selectFirst("time")?.attr("datetime"))
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val imageHost = ".${baseUrl.toHttpUrl().host}"
        return sitePages.read(getChapterUrl(chapter), "pages.js").parseAs<ReaderPage>().pages.mapIndexed { index, url ->
            if (!url.toHttpUrl().host.endsWith(imageHost)) throw IOException("Unexpected page url from the site's reader")
            Page(index, imageUrl = url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED_CHAPTERS
            title = "Hide locked chapters"
            summary = "Hide early access chapters that require a subscription. If disabled, they are shown with a \uD83D\uDD12 prefix but stay unreadable without a subscription."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private fun Document.info(label: String) = select("dl dt").firstOrNull { it.text() == label }
        ?.nextElementSibling()?.textOrNull()

    private inline fun <reified T> Document.jsonLd(predicate: (T) -> Boolean): T? = select("script[type=application/ld+json]")
        .mapNotNull { runCatching { it.data().parseAs<T>() }.getOrNull() }
        .firstOrNull(predicate)

    companion object {
        private const val PREF_HIDE_LOCKED_CHAPTERS = "pref_hide_locked_chapters"
        private const val LOCK = "\uD83D\uDD12 "
    }
}
