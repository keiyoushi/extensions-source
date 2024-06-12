package eu.kanade.tachiyomi.extension.en.spyfakku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpyFakku : HttpSource() {

    override val name = "SpyFakku"

    override val baseUrl = "https://spy.fakku.cc"

    override val lang = "en"

    override val supportsLatest = false

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("referer", "$baseUrl/")
        .set("origin", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("article.entry").map(::popularMangaFromElement)

        val hasNextPage = document.select(".next").firstOrNull() != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("a")!!.attr("title")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val terms = query
                .trim()
                .replace(Regex("""^\?"""), "")
                .lowercase()
                .split(Regex("\\s+"))
                .toMutableList()

            filters.forEach {
                when (it) {
                    is SortFilter -> {
                        addQueryParameter("sort", it.getValue())
                    }

                    is OrderFilter -> {
                        addQueryParameter("order", it.getValue())
                    }

                    is TextFilter -> {
                        if (it.state.isNotEmpty()) {
                            terms += it.state.split(",").map { tag ->
                                val trimmed = tag.trim().replace(" ", "_")
                                (if (trimmed.startsWith("-")) "-" else "") + it.type + "&:" + trimmed.removePrefix("-")
                            }
                        }
                    }

                    else -> {}
                }
            }
            addPathSegment("search")
            addQueryParameter("q", terms.joinToString(" "))
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url + ".json", headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url + ".json", headers)
    }

    override fun getFilterList() = getFilters()

    private fun Hentai.toSManga() = SManga.create().apply {
        title = this@toSManga.title
        url = "/archive/$id/$slug"
        author = artists?.joinToString { it.value }
        artist = artists?.joinToString { it.value }
        genre = tags?.joinToString { it.value }
        thumbnail_url = "https://cdn.fakku.cc/data/$id/1/288.webp"
        description = buildString {
            circle?.joinToString { it.value }?.let {
                append("Circles: ", it, "\n")
            }
            magazines?.joinToString { it.value }?.let {
                append("Magazines: ", it, "\n")
            }
            parodies?.joinToString { it.value }?.let {
                append("Parodies: ", it, "\n")
            }
            append(
                "Created At: ",
                SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH).format(
                    Date(createdAt * 1000),
                ),
                "\n",
            )
            append("Pages: ", pages, "\n")
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    override fun mangaDetailsParse(response: Response): SManga = runBlocking {
        response.parseAs<Hentai>().toSManga()
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun chapterListParse(response: Response): List<SChapter> {
        val hentai = response.parseAs<Hentai>()

        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = "/archive/${hentai.id}/${hentai.slug}"
                date_upload = hentai.createdAt * 1000
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListParse(response: Response): List<Page> {
        val hentai = response.parseAs<Hentai>()
        val range = 1..hentai.pages
        val baseImageUrl = "https://cdn.fakku.cc/data/${hentai.id}/"
        return range.map {
            val imageUrl = baseImageUrl + it
            Page(it - 1, imageUrl = imageUrl)
        }
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
}
