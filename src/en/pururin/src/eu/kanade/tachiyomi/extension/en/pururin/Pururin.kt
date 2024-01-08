package eu.kanade.tachiyomi.extension.en.pururin

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Pururin : HttpSource() {
    override val name = "Pururin"

    override val baseUrl = "https://pururin.to"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val searchUrl = "$baseUrl/api/search/advance"

    private val galleryUrl = "$baseUrl/api/contribute/gallery/info"

    private val json by injectLazy<Json>()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl).set("X-Requested-With", "XMLHttpRequest")

    override fun latestUpdatesRequest(page: Int) =
        POST(searchUrl, headers, Search(Search.Sort.NEWEST, page))

    override fun latestUpdatesParse(response: Response) =
        searchMangaParse(response)

    override fun popularMangaRequest(page: Int) =
        POST(searchUrl, headers, Search(Search.Sort.POPULAR, page))

    override fun popularMangaParse(response: Response) =
        searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        filters.ifEmpty(::getFilterList).run {
            val whitelist = mutableListOf<Int>()
            val blacklist = mutableListOf<Int>()
            filterIsInstance<TagGroup<*>>().forEach { group ->
                group.state.forEach {
                    when {
                        it.isIncluded() -> whitelist += it.id
                        it.isExcluded() -> blacklist += it.id
                    }
                }
            }
            val body = Search(
                find<SortFilter>().sort,
                page,
                query,
                whitelist,
                blacklist,
                find<TagModeFilter>().mode,
                find<PagesGroup>().range,
            )
            POST(searchUrl, headers, body)
        }

    override fun searchMangaParse(response: Response): MangasPage {
        val results = json.decodeFromString<Results>(
            response.jsonObject["results"]!!.jsonPrimitive.content,
        )
        val mp = results.map {
            SManga.create().apply {
                url = it.path
                title = it.title
                thumbnail_url = CDN_URL + it.cover
            }
        }
        return MangasPage(mp, results.hasNext)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val gallery = json.decodeFromJsonElement<Gallery>(
            response.jsonObject["gallery"]!!,
        )
        return SManga.create().apply {
            description = gallery.description
            artist = gallery.artists.joinToString()
            author = gallery.authors.joinToString()
            genre = gallery.genres.joinToString()
        }
    }

    override fun fetchMangaDetails(manga: SManga) =
        client.newCall(chapterListRequest(manga))
            .asObservableSuccess().map(::mangaDetailsParse)!!

    override fun chapterListRequest(manga: SManga) =
        POST(galleryUrl, headers, Search.info(manga.id))

    override fun chapterListParse(response: Response): List<SChapter> {
        val gallery = json.decodeFromJsonElement<Gallery>(
            response.jsonObject["gallery"]!!,
        )
        val chapter = SChapter.create().apply {
            name = "Chapter"
            url = gallery.id.toString()
            scanlator = gallery.scanlators.joinToString()
        }
        return listOf(chapter)
    }

    override fun pageListRequest(chapter: SChapter) =
        POST(galleryUrl, headers, Search.info(chapter.url))

    override fun pageListParse(response: Response): List<Page> {
        val pages = json.decodeFromJsonElement<Gallery>(
            response.jsonObject["gallery"]!!,
        ).pages
        return pages.mapIndexed { idx, img ->
            Page(idx + 1, CDN_URL + img)
        }
    }

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    override fun fetchImageUrl(page: Page) =
        Request.Builder().url(page.url).head().build()
            .run(client::newCall).asObservable().map {
                when (it.code) {
                    200 -> page.url
                    // try to fix images that are broken even on the site
                    404 -> page.url.replaceAfterLast('.', "png")
                    else -> throw Error("HTTP error ${it.code}")
                }
            }!!

    override fun getFilterList() = FilterList(
        SortFilter(),
        CategoryGroup(),
        TagModeFilter(),
        PagesGroup(),
    )

    private inline val Response.jsonObject
        get() = json.parseToJsonElement(body.string()).jsonObject

    private inline val SManga.id get() = url.split('/')[2]

    companion object {
        private const val CDN_URL = "https://cdn.pururin.to/assets/images/data"
    }
}
