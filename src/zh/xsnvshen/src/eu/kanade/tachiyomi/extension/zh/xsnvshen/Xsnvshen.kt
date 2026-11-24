package eu.kanade.tachiyomi.extension.zh.xsnvshen

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.int
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Xsnvshen : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getAlbumList("album/", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getAlbumList("album/new/", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getAlbumList(filters.firstInstance<TagFilter>().value, page)

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("w", query.trim())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Results come on one page (100 albums at most), with the models whose name matches
        val mangas = document.select("ul.list_3c li:has(a[href~=^/album/\\d+$])").map { item ->
            SManga.create().apply {
                val link = item.selectFirst(".titlebox a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = item.selectFirst(".picbox img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    private suspend fun getAlbumList(path: String, page: Int): MangasPage {
        val document = client.get(listUrl(path, page), Fetch.DOCUMENT).asJsoup()
        // Ads sit between the albums, and a hidden item holds empty album links, a trap for crawlers
        val mangas = document.select("ul.list > li:not([aria-hidden]) > a[href~=^/album/\\d+$]").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".txtbts3")!!.text()
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.hasNextPage())
    }

    private fun listUrl(path: String, page: Int) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(path)
        .apply { if (page > 1) addQueryParameter("p", page.toString()) }
        .build()

    // Past its last page, a list shows its first page again, and the last page's "next" link points back
    private fun Document.hasNextPage(): Boolean {
        val options = select("#touch_page option")
        return options.indexOfFirst { it.hasAttr("selected") } in 0 until options.size - 1
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.isSiteUrl() || url.pathSegments.firstOrNull() != "album") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { ALBUM_ID_REGEX.matches(it) } ?: return null
        val manga = SManga.create().apply { this.url = "/album/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val (section, id) = url.pathSegments.let { it.getOrNull(0) to it.getOrNull(1).orEmpty() }
        return when {
            !url.isSiteUrl() -> MangasPage(emptyList(), false)
            section == "girl" && ALBUM_ID_REGEX.matches(id) -> getModelAlbums(id, page)
            section == "album" && TAG_ID_REGEX.matches(id) -> getAlbumList("album/$id/", page)
            else -> super.getMangasByUrl(url, page)
        }
    }

    private suspend fun getModelAlbums(id: String, page: Int): MangasPage {
        val document = client.get(listUrl("girl/$id", page), Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".star-mod-ul li > a[href~=^/album/\\d+$]").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.attr("title")
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.hasNextPage())
    }

    private fun HttpUrl.isSiteUrl() = host.removePrefix("www.").removePrefix("m.") == baseUrl.toHttpUrl().host.removePrefix("m.")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val info = document.selectFirst("em:contains(更新)")?.text().orEmpty()
        val photos = PHOTO_COUNT_REGEX.find(info)?.groupValues?.get(1)
        manga.apply {
            title = document.selectFirst(".titlebox h1")!!.text()
            author = document.selectFirst(".position a[href^=/girl/]")?.text()
            genre = document.select(".poster-nav a[href^=/album/t] span").joinToString { it.text() }.ifEmpty { null }
            // The album's photos need the album page as Referer, which a cover can't send: the lists' cover thumbnail doesn't
            thumbnail_url = document.selectFirst("#arcbox img[datafull]")?.absUrl("datafull")?.toHttpUrlOrNull()
                ?.let { photo -> photo.resolve("/thumb_205x308" + photo.encodedPath.substringBeforeLast('/') + "/cover.jpg") }
                ?.toString()
            description = MarkdownDescription.Builder()
                .description(document.selectFirst("#arcbox > p:not(:has(img))")?.textOrNull())
                .field("照片", photos?.let { "$it 张" })
                .build()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = photos?.let { "共 $it 张" } ?: "图集"
            // Not the photo count of the name
            chapter_number = 1f
            date_upload = DATE_FORMAT.tryParseDate(UPDATE_DATE_REGEX.find(info)?.groupValues?.get(1), BEIJING)
            // The album is split into pages of ten photos
            memo = buildJsonObject { put("pages", document.select("#touch_page option").size.coerceAtLeast(1)) }
        }
        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = coroutineScope {
        (1..chapter.memo["pages"]!!.int).map { page ->
            async {
                val pageUrl = listUrl(chapter.url.removePrefix("/"), page).toString()
                client.get(pageUrl, Fetch.DOCUMENT).asJsoup().select("#arcbox img[datafull]").map { pageUrl to it.absUrl("datafull") }
            }
        }.awaitAll()
            .flatten()
            .mapIndexed { i, (pageUrl, imageUrl) -> Page(i, url = pageUrl, imageUrl = imageUrl) }
    }

    // The album page's referrer policy ("always") sends its full address, which the image host checks:
    // Fetch.from would cut it to the origin, as the default policy does
    override fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .fetch(Fetch.IMAGE)
        .header("Referer", page.url)
        .build()

    override fun getFilterList(data: JsonElement?) = FilterList(
        TagFilter(),
    )

    companion object {
        private val ALBUM_ID_REGEX = Regex("""\d+""")
        private val TAG_ID_REGEX = Regex("""t\d+""")
        private val PHOTO_COUNT_REGEX = Regex("""共\s*(\d+)\s*张""")
        private val UPDATE_DATE_REGEX = Regex("""更新：\s*(\d{4}-\d{2}-\d{2})""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val BEIJING = ZoneId.of("Asia/Shanghai")
    }
}
