package eu.kanade.tachiyomi.extension.all.thehentai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

@Source
abstract class TheHentai : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    // The site only lists its posts by date
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = fetchList(baseUrl.toHttpUrl().newBuilder().addPage(page).build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.slug
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            when {
                query.isNotBlank() -> addPage(page).addQueryParameter("s", query.trim())
                category != null -> addPathSegments("category/$category/").addPage(page)
                else -> addPage(page)
            }
        }.build()

        return fetchList(url)
    }

    private suspend fun fetchList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Ads in the grid link to other sites, and video posts have no pages
        val mangas = document.select("#posts_list .gridPosts:has(h3 a[href^=/]):not(:has(a[href*=/category/videos/]))").map { card ->
            val link = card.selectFirst("h3 a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst(".img_cage img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, document.selectFirst(".pagination li:has(.current_page) ~ li > a:matchesOwn(\\d)") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != host) return null
        val slug = url.pathSegments.first().takeUnless { it.isEmpty() || it in NON_POST_PATHS } ?: return null

        val document = client.get("$baseUrl/$slug/", Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".postInfo") == null) return null

        return document.toSManga().apply {
            this.url = "/$slug/"
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        // Video posts have no pages
        val chapterList = if (document.selectFirst(PAGE_SELECTOR) == null) {
            emptyList()
        } else {
            val date = document.selectFirst(".info_first:has(.ico-update) + .info_second")
                ?: document.selectFirst(".info_first:has(.ico-date) + .info_second")
            SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                date_upload = dateFormat.tryParseDate(date?.text(), ZONE)
            }.let(::listOf)
        }

        return SMangaUpdate(document.toSManga().apply { url = manga.url }, chapterList)
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst(".post_title span")!!.text()
        thumbnail_url = selectFirst("meta[property=og:image]")?.absUrl("content")
        artist = select(".tdArtist a").joinToString { it.tagText() }.ifEmpty { null }
        author = artist
        genre = select(".tdCategory a, .tdTag a").map { it.tagText() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst(".description"))
            .apply {
                // Series, parody, characters, group, translator and cosplayer, under the site's own labels
                select(".postInfo :is(.tdSerie, .tdParody, .tdCharacter, .tdGroup, .tdTranslator, .tdCosplayer)").forEach { values ->
                    field(values.previousElementSibling()!!.text(), values.select("a").map { it.tagText() })
                }
            }
            .build()
    }

    // Each image has its own page: the thumbnails don't tell a JPEG from a PNG or a video
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PAGE_SELECTOR)
        .mapIndexed { index, link -> Page(index, url = link.absUrl("href")) }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url, Fetch.DOCUMENT).asJsoup()
        val media = document.selectFirst("#img_gallery_big")!!
        // Animated pages are short videos, shown by their still
        return if (media.tagName() == "video") {
            document.selectFirst("meta[property=og:image]")!!.absUrl("content")
        } else {
            media.absUrl("src")
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private val dateFormat get() = if (lang == "en") EN_DATE_FORMAT else PT_DATE_FORMAT

    private fun HttpUrl.Builder.addPage(page: Int) = apply { if (page > 1) addPathSegments("page/$page/") }

    // Tag links read "#Tag", or "@Name" for people
    private fun Element.tagText() = text().trimStart('#', '@')

    companion object {
        private const val PAGE_SELECTOR = ".post_imgs .thumbnails a"
        private val NON_POST_PATHS = setOf("category", "page", "tag", "tags")
        private val ZONE = ZoneId.of("America/Sao_Paulo")
        private val EN_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

        // The site's Portuguese months have no dot, unlike Java's ("nov.")
        private val PT_DATE_FORMAT = DateTimeFormatterBuilder()
            .appendPattern("d ")
            .appendText(
                ChronoField.MONTH_OF_YEAR,
                listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
                    .withIndex()
                    .associate { (index, month) -> index + 1L to month },
            )
            .appendPattern(" yyyy")
            .toFormatter(Locale.ROOT)
    }
}
