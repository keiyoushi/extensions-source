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
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class CosplayRule34 : KeiSource() {

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = fetchList(baseHttpUrl.newBuilder().addPathSegment("top"), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchList(baseHttpUrl.newBuilder(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getLatestUpdates(page)
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("search")
            .addPathSegment(query.trim())
        val result = fetchList(url, page)
        // Unlike the other listings, search answers with an empty page past the last one
        return MangasPage(result.mangas, result.hasNextPage && result.mangas.size == SEARCH_PAGE_SIZE)
    }

    private suspend fun fetchList(listUrl: HttpUrl.Builder, page: Int): MangasPage {
        val document = client.get(listUrl.addQueryParameter("page", page.toString()).build(), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.grid-item:has(a[href^=/post/])").map { element ->
            val link = element.selectFirst("a[href^=/post/]")!!
            SManga.create().apply {
                url = link.attr("href").substringAfterLast('/')
                title = link.text()
                thumbnail_url = COVER_REGEX.find(element.attr("style"))?.groupValues?.get(1)
            }
        }
        // Every page links to a next one, and going past the last page serves the first one again
        val hasNextPage = document.selectFirst("a.next")?.attr("href")?.endsWith("page=${page + 1}") == true
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHttpUrl.host || url.pathSegments.firstOrNull() != "post") return null
        val id = url.pathSegments.getOrNull(1) ?: return null
        val document = getPost(id)
        return document.toSManga(id, document.constants())
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
            }
        }
        return SMangaUpdate(document.toSManga(manga.url, constants), listOf(chapter))
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/post/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = PhotosBody(
            albumId = chapter.memo[ALBUM_ID]!!.string,
            ownerId = chapter.memo[OWNER_ID]!!.string,
            download = 1,
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
        title = constants.getValue("title")
        // The og:image of a post is a .jpg that doesn't exist
        thumbnail_url = "$baseUrl/images/albums/${constants.getValue(OWNER_ID)}/${constants.getValue(ALBUM_ID)}.webp"
        author = select("a.btn[href^=/model/]").eachText().distinct().joinToString()
        genre = select("a.btn[href^=/cosplay/], a.btn[href^=/fandom/]").eachText().distinct().joinToString()
        description = selectFirst(".accordion-body")?.text()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    companion object {
        private const val SEARCH_PAGE_SIZE = 10
        private const val ALBUM_ID = "albumId"
        private const val OWNER_ID = "ownerId"
        private const val TOTAL_PHOTOS = "totalPhotos"

        private val COVER_REGEX = Regex("""url\((.+?)\)""")
        private val CONSTANT_REGEX = Regex("""const (\w+) = ("(?:[^"\\]|\\.)*"|\d+);""")
    }
}
