package eu.kanade.tachiyomi.extension.en.hdporncomics

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class HDPornComics : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get(listUrl("", page), Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = listUrl("", page).toHttpUrl().newBuilder().addQueryParameter("sort", "update").build()
        return mangaListParse(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    // The site has no working text search, only listings by type and tag
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return MangasPage(emptyList(), false)
        val path = when {
            filters.firstInstanceOrNull<TypeFilter>()?.state == 1 -> "manhwa/"
            else -> filters.firstInstanceOrNull<TagFilter>()?.selectedPath()?.let { "tag/$it/" } ?: ""
        }
        return mangaListParse(client.get(listUrl(path, page), Fetch.DOCUMENT).asJsoup())
    }

    private fun listUrl(path: String, page: Int) = if (page > 1) "$baseUrl/${path}page/$page/" else "$baseUrl/$path"

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("div[id^=post-].hentry").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.selectFirst("a[href]")!!.absUrl("href"))
                title = card.selectFirst("h2")!!.text().cleanTitle()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a.next") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotBlank)
        val path = when {
            segments.size == 1 -> "/${segments[0]}/"
            segments.size >= 2 && segments[0] == MANHWA -> "/$MANHWA/${segments[1]}/"
            else -> return null
        }
        val document = client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("h1") == null) return null
        return (if (path.startsWith("/$MANHWA/")) manhwaDetailsParse(document) else comicDetailsParse(document))
            .apply { this.url = path }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return if (manga.url.startsWith("/$MANHWA/")) {
            val chapterList = document.select("#allChapters > div").map { row ->
                SChapter.create().apply {
                    val link = row.selectFirst("a")!!
                    setUrlWithoutDomain(link.absUrl("href"))
                    name = link.text()
                    date_upload = CHAPTER_DATE_FORMAT.tryParseDate(row.selectFirst("span")?.text(), ZoneOffset.UTC)
                }
            }
            SMangaUpdate(manhwaDetailsParse(document).apply { url = manga.url }, chapterList)
        } else {
            val chapter = SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                date_upload = Instant.tryParse(
                    document.selectFirst("script.yoast-schema-graph")?.data()
                        ?.let { DATE_MODIFIED_REGEX.find(it)?.groupValues?.get(1) },
                )
            }
            SMangaUpdate(comicDetailsParse(document).apply { url = manga.url }, listOf(chapter))
        }
    }

    private fun comicDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text().cleanTitle()
        author = document.select("a[href*='/artist/'][rel=tag]").eachText().distinct().joinToString()
        genre = document.select("a[href*='/tag/'][rel=tag]").eachText().joinToString()
        thumbnail_url = document.selectFirst("figure[itemprop=associatedMedia] img")?.absUrl("src")
        val ongoing = title.lowercase().let { "ongoing" in it || "updated" in it }
        status = if (ongoing) SManga.ONGOING else SManga.COMPLETED
        update_strategy = if (ongoing) UpdateStrategy.ALWAYS_UPDATE else UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun manhwaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text().cleanTitle()
        author = document.select("a[href*='/manhwa_author/']").eachText().joinToString()
        artist = document.select("a[href*='/manhwa_artist/']").eachText().joinToString()
        description = document.selectFirst("#summary")?.text()
        thumbnail_url = document.selectFirst("img[src*='/cover/']")?.absUrl("src")
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val images = if (chapter.url.startsWith("/$MANHWA/")) {
            document.select("#imageContainer img").map { it.absUrl("src") }
        } else {
            document.select("figure[itemprop=associatedMedia] a[itemprop=contentUrl]").map { it.absUrl("href") }
        }
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select("a.tag-cloud-link")
        .associate { it.ownText() to it.absUrl("href").toHttpUrl().pathSegments.filter(String::isNotBlank).last() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            Filter.Header("Text search isn't available on this site, use the filters"),
            TypeFilter(),
            data?.parseAs<Map<String, String>>()?.let { TagFilter(it) },
        ),
    )

    private fun String.cleanTitle() = replace(TITLE_SUFFIX_REGEX, "")

    companion object {
        private const val MANHWA = "manhwa"
        private val TITLE_SUFFIX_REGEX = Regex("""(?i)(\s+comic porn|\s*(NEW\s*)?\(\s*Manhwa Porn\s*\))\s*$""")
        private val DATE_MODIFIED_REGEX = Regex(""""dateModified":"([^"]+)"""")
        private val CHAPTER_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yy", Locale.ENGLISH)
    }
}
