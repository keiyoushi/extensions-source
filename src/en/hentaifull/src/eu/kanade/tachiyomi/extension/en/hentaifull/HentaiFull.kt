package eu.kanade.tachiyomi.extension.en.hentaifull

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiFull : KeiSource() {

    override val supportsFilterFetching = true

    // The site's Top Hentai Manhwa and Top Hentai Manga lists
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get(baseUrl, Fetch.DOCUMENT).asJsoup().select(".ep-sidebar .list-sidebar li").map { element ->
            element.toSManga(".tit a").apply {
                genre = element.select(".caption a.tag").joinToString { it.text() }.ifEmpty { null }
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList(baseUrl.toHttpUrl(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) return mangaList(url.addPathSegment("${query.trim()}.html").build(), page)
        val tag = filters.firstInstanceOrNull<TagFilter>()?.selected
        if (tag != null) {
            url.addPathSegment("tag").addPathSegment(tag)
        } else {
            filters.firstInstanceOrNull<CategoryFilter>()?.selected?.let { url.addPathSegments(it) }
        }
        return mangaList(url.build(), page)
    }

    private suspend fun mangaList(listUrl: HttpUrl, page: Int): MangasPage {
        val document = client.get(listUrl.newBuilder().addQueryParameter("page", page.toString()).build(), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".main-content .list_wrap li:has(.hentai-cover)").map { it.toSManga(".title a") }
        return MangasPage(mangas, document.selectFirst(".pagination li.active + li") != null)
    }

    private fun Element.toSManga(linkSelector: String) = SManga.create().apply {
        val link = selectFirst(linkSelector)!!
        url = link.absUrl("href").toHttpUrl().pathSegments[1]
        title = link.text()
        thumbnail_url = selectFirst("img")?.attrOrNull("abs:data-original")
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val isList = url.host == baseUrl.toHttpUrl().host &&
            url.pathSegments.firstOrNull() in listPaths &&
            url.pathSegments.getOrNull(1).orEmpty().isNotEmpty()
        if (!isList) return super.getMangasByUrl(url, page)
        return mangaList(url.newBuilder().removeAllQueryParameters("page").build(), page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "hentai") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/hentai/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetails(manga, document), chapterList(document))
    }

    private fun mangaDetails(manga: SManga, document: Document) = manga.apply {
        val info = document.selectFirst(".hentai-info")!!
        title = document.selectFirst(".main-head h1")!!.text()
        thumbnail_url = document.selectFirst(".comic-thumb-detail img")?.attrOrNull("abs:src")
        val artists = info.links("Artists")
        artist = artists.joinToString().ifEmpty { null }
        author = info.links("Groups").ifEmpty { artists }.joinToString().ifEmpty { null }
        genre = (info.links("Category") + info.links("Tags")).distinct().joinToString().ifEmpty { null }
        status = when (info.selectFirst(".line:has(.line-text:containsOwn(Status:)) .line-content")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val oneShot = document.select("#chapter-list .citem").size == 1 && status == SManga.COMPLETED
        update_strategy = if (oneShot) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".desc"), keepNewlines = true)
            .altTitles(
                document.selectFirst(".main-head .alternative")?.text()
                    ?.split(altTitleSeparator)?.filterNot { it.equals(title, ignoreCase = true) }.orEmpty(),
            )
            .build()
    }

    private fun Element.links(label: String) = select(".line:has(.line-text:containsOwn($label:)) .line-content a").map { it.text() }

    private fun chapterList(document: Document): List<SChapter> = document.select("#chapter-list .citem").map { element ->
        val link = element.selectFirst("a")!!
        SChapter.create().apply {
            url = link.absUrl("href").toHttpUrl().pathSegments.drop(1).joinToString("/")
            name = link.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".time")?.text(), siteZone)
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/hentai/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".chapter-content .item-photo img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override suspend fun fetchFilterData(): JsonElement {
        val tags = mutableListOf<Tag>()
        var page = 1
        do {
            val document = client.get("$baseUrl/tags?page=$page", Fetch.DOCUMENT).asJsoup()
            document.select("a[href^=/tag/]:has(.tag-name)").mapNotNullTo(tags) { link ->
                val slug = link.absUrl("href").toHttpUrl().pathSegments[1].takeIf { it.isNotEmpty() }
                slug?.let { Tag(link.selectFirst(".tag-name")!!.text(), it) }
            }
            page++
        } while (document.selectFirst(".pagination li.active + li") != null)
        check(tags.isNotEmpty()) { "No tags found" }
        return tags.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            Filter.Header("Ignored when searching by text"),
            CategoryFilter(),
            data?.parseAs<List<Tag>>()?.let(::TagFilter),
        ),
    )

    companion object {
        private val listPaths = setOf("tag", "artist", "group", "category")

        private val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

        // The sitemap's timestamps are in US Central time
        private val siteZone = ZoneId.of("America/Chicago")

        // Alternative titles are joined with " | ", " / " or "/ "
        private val altTitleSeparator = Regex("""\s+\|\s+|\s*/\s+""")
    }
}
