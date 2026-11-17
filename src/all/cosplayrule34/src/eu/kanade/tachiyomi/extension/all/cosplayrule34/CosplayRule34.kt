package eu.kanade.tachiyomi.extension.all.cosplayrule34

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
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CosplayRule34 : KeiSource() {

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    // Pages and the photo API answer 503 to bursts; images aren't limited
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 3, period = 1.seconds) { !it.encodedPath.startsWith("/images/") }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchList(listOf("top"), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchList(emptyList(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
        val path = when {
            query.isNotBlank() -> listOf("search", query.trim())
            category != null -> listOf("category", category)
            else -> emptyList()
        }
        return fetchList(path, page)
    }

    private suspend fun fetchList(path: List<String>, page: Int): MangasPage {
        val url = baseHttpUrl.newBuilder().apply {
            path.forEach(::addPathSegment)
            addQueryParameter("page", page.toString())
            // The site caches pages by URL for every device, but phones get 5 entries on most lists and computers 10:
            // a page cached for the other kind would skip or repeat entries, so each kind gets its own URLs
            addQueryParameter("device", if ("Mobile" in headers["User-Agent"]!!) "mobile" else "desktop")
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.grid-item:has(a[href^=/post/])").map { element ->
            val link = element.selectFirst("a[href^=/post/]")!!
            SManga.create().apply {
                this.url = link.attr("href").substringAfterLast('/')
                title = link.text()
                thumbnail_url = COVER_REGEX.find(element.attr("style"))?.groupValues?.get(1)
            }
        }
        // Searches and model, cosplay and fandom lists keep their next link on their last page, then serve empty pages,
        // which Mihon shows as an error: a page shorter than the list's first one is the last
        val list = url.newBuilder().removeAllQueryParameters("page").build()
        val next = document.selectFirst("a.next")?.attr("href")?.endsWith("page=${page + 1}") == true
        if (page == 1 && next) pageSizes[list] = mangas.size
        return MangasPage(mangas, next && mangas.isNotEmpty() && mangas.size >= (pageSizes[list] ?: 0))
    }

    private val pageSizes = ConcurrentHashMap<HttpUrl, Int>()

    // A gallery, or the list of a model, cosplay, fandom, category or search; the site's alternate links add a language (/us/)
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseHttpUrl.host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter(String::isNotEmpty)
            .let { if (it.size > 1 && LANGUAGE.matches(it[0]) && it[1] in LINK_TYPES) it.drop(1) else it }
        return when (segments.firstOrNull()) {
            "post" -> {
                val id = segments.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return MangasPage(emptyList(), false)
                val manga = SManga.create().apply { this.url = id }
                MangasPage(listOf(getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga), false)
            }
            "top" -> fetchList(listOf("top"), page)
            in LIST_TYPES -> if (segments.size > 1) fetchList(segments.take(2), page) else MangasPage(emptyList(), false)
            else -> MangasPage(emptyList(), false)
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/post/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = getPost(manga.url)
        val constants = document.constants()
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            memo = buildJsonObject {
                put(ALBUM_ID, constants.getValue(ALBUM_ID))
                put(OWNER_ID, constants.getValue(OWNER_ID))
                put(TOTAL_PHOTOS, constants.getValue(TOTAL_PHOTOS).toInt())
                put(DOWNLOAD, constants.getValue(DOWNLOAD).toInt())
                put(DOWNLOAD_ID, constants.getValue(DOWNLOAD_ID).toInt())
            }
        }
        return SMangaUpdate(document.toSManga(manga.url, constants), listOf(chapter))
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/post/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = PhotosBody(
            albumId = chapter.memo[ALBUM_ID]!!.string,
            ownerId = chapter.memo[OWNER_ID]!!.string,
            // Chapters saved by the first version don't have these two values
            download = chapter.memo[DOWNLOAD]?.int ?: 1,
            downloadId = chapter.memo[DOWNLOAD_ID]?.int ?: 0,
            offset = 0,
            limit = chapter.memo[TOTAL_PHOTOS]!!.int,
        ).toJsonRequestBody()
        return client.post("$baseUrl/cms/load-more-photos.php", Fetch.XHR.from(getChapterUrl(chapter)), body)
            .parseAs<PhotosDto>().photos
            .mapNotNull { Jsoup.parseBodyFragment(it.html, baseUrl).selectFirst("a[data-fancybox]")?.absUrl("href") }
            // The API sorts the file names as text ("10" before "6"), the site shows them in numeric order
            .sortedWith(compareBy({ it.length }, { it }))
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private suspend fun getPost(id: String): Document = client.get("$baseUrl/post/$id", Fetch.DOCUMENT).asJsoup()

    private fun Document.constants(): Map<String, String> {
        val script = selectFirst("script:containsData(const $ALBUM_ID)")!!.data()
        return CONSTANT_REGEX.findAll(script).associate {
            it.groupValues[1] to it.groupValues[2].parseAs<JsonPrimitive>().content
        }
    }

    private fun Document.toSManga(id: String, constants: Map<String, String>) = SManga.create().apply {
        url = id
        title = constants.getValue("title").replace(SPACES, " ")
        // The og:image of a post is a .jpg that doesn't exist
        thumbnail_url = "$baseUrl/images/albums/${constants.getValue(OWNER_ID)}/${constants.getValue(ALBUM_ID)}.webp"
        author = select("a.btn[href^=/model/]").eachText().distinct().joinToString().ifEmpty { null }
        genre = select("a.btn[href^=/cosplay/], a.btn[href^=/fandom/]").eachText().distinct().joinToString().ifEmpty { null }
        description = selectFirst(".accordion-body")?.text()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private const val ALBUM_ID = "albumId"
        private const val OWNER_ID = "ownerId"
        private const val TOTAL_PHOTOS = "totalPhotos"
        private const val DOWNLOAD = "download"
        private const val DOWNLOAD_ID = "downloadId"

        private val LIST_TYPES = setOf("model", "cosplay", "fandom", "category", "search")
        private val LINK_TYPES = LIST_TYPES + setOf("post", "top")
        private val LANGUAGE = Regex("""[a-z]{2}""")
        private val COVER_REGEX = Regex("""url\((.+?)\)""")
        private val CONSTANT_REGEX = Regex("""const (\w+) = ("(?:[^"\\]|\\.)*"|\d+);""")
        private val SPACES = Regex("""\s{2,}""")
    }
}
