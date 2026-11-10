package eu.kanade.tachiyomi.extension.all.ahrigallery

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class AhriGallery : KeiSource() {

    // The site keeps its language filter in a cookie
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addCookie("lang_filter" to siteLanguage)

    private val siteLanguage
        get() = when (lang) {
            "ja" -> "Japanese"
            "en" -> "English"
            "zh" -> "Chinese"
            "ko" -> "Korean"
            "es" -> "Spanish"
            "ru" -> "Russian"
            "pt-BR" -> "Portuguese"
            "fr" -> "French"
            "it" -> "Italian"
            "th" -> "Thai"
            "vi" -> "Vietnamese"
            "de" -> "German"
            "pl" -> "Polish"
            "hu" -> "Hungarian"
            "nl" -> "Dutch"
            else -> "All"
        }

    override suspend fun getPopularManga(page: Int): MangasPage = getGalleryList(listUrl(page, "fav" to "DESC").build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getGalleryList(listUrl(page, "default" to "DESC").build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = listUrl(page, filters.firstInstance<SortFilter>().selected)
            .addQueryParameter("category_id", filters.firstInstance<CategoryFilter>().selected)
            .apply { if (query.isNotBlank()) addQueryParameter("search_key", query.trim()) }
            .build()
        return getGalleryList(url)
    }

    private fun listUrl(page: Int, sort: Pair<String, String>) = routeUrl("comic/list")
        .addQueryParameter("sort", sort.first)
        .addQueryParameter("order", sort.second)
        .addQueryParameter("page", page.toString())

    private suspend fun getGalleryList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".gallery-group").map { item ->
            val link = item.selectFirst("h5.title a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.attr("title")
                thumbnail_url = item.selectFirst(".image-inner img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst(".pagination a:matchesOwn(^>$)") != null)
    }

    // Tag, category and search links open their lists
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host == baseUrl.toHttpUrl().host && url.queryParameter("route") == "comic/list") {
            return getGalleryList(url.newBuilder().setQueryParameter("page", page.toString()).build())
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val hash = url.queryParameter("c_id") ?: return null
        val id = when (url.queryParameter("route")) {
            "comic/article" -> url.queryParameter("comic_id") ?: return null
            // Reader links only have the gallery's hash, its id is in the page
            "comic/reader" -> client.get(url, Fetch.DOCUMENT).asJsoup().readerScript().scriptValue("postID = ")
            else -> return null
        }
        val galleryUrl = routeUrl("comic/article").addQueryParameter("comic_id", id).addQueryParameter("c_id", hash).build()
        val manga = SManga.create().apply { setUrlWithoutDomain(galleryUrl.toString()) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val comic = document.selectFirst("script:containsData(var comic_data)")!!.data().scriptValue("var comic_data = ").parseAs<ComicData>()
        comic.toSManga(manga, document.selectFirst(".gallery_card img")?.absUrl("src"))
        return SMangaUpdate(manga, listOf(comic.toSChapter(manga.url)))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // The reader link carries a token made for each visit of the gallery page
        val readerUrl = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .selectFirst("a[href*=route=comic/reader]")!!.absUrl("href")
        val script = client.get(readerUrl, Fetch.DOCUMENT).asJsoup().readerScript()
        val images = script.scriptValue("Image_List = ").parseAs<List<ReaderImage>>()
        val servers = script.scriptValue("var IMAGE_SERVER = ").parseAs<Map<String, List<String>>>()[script.scriptValue("var image_server_id = ")]!!
        val folder = script.scriptValue("var IMAGE_FOLDER = ").removeSurrounding("\"")
        // Like the reader, take the image servers in turn
        return images.mapIndexed { index, image ->
            val imageUrl = baseUrl.toHttpUrl().resolve("${servers[index % servers.size]}$folder${image.sort}.${image.fileExtension}")!!
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
        SortFilter(),
    )

    private fun routeUrl(route: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("index.php")
        .addEncodedQueryParameter("route", route)

    private fun Document.readerScript() = selectFirst("script:containsData(Image_List)")!!.data()

    // The page scripts set one variable per line
    private fun String.scriptValue(prefix: String) = lineSequence().map { it.trim() }.first { it.startsWith(prefix) }
        .removePrefix(prefix).removeSuffix(";")
}
