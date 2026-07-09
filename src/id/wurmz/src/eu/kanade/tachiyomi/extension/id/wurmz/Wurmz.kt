package eu.kanade.tachiyomi.extension.id.wurmz

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.util.Locale

@Source
abstract class Wurmz : HttpSource() {
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .add("Rsc", "1")
        .build()

    // ======================== Popular ========================
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ======================== Latest ========================
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Tidak didukung")

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Tidak didukung")

    // ======================== Search ========================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host == baseUrl.toHttpUrl().host && url.pathSegments.contains("detail")) {
                val detailIdx = url.pathSegments.indexOf("detail")
                val type = url.pathSegments[detailIdx + 1]
                val slug = url.pathSegments[detailIdx + 2]
                val manga = SManga.create().apply {
                    setUrlWithoutDomain("$baseUrl/detail/$type/$slug")
                    title = slug.replace("-", " ")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
                return Observable.just(MangasPage(listOf(manga), false))
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/semua-komik".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) addQueryParameter("q", query)
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> addQueryParameter("type", filter.toUriPart())
                    is StatusFilter -> addQueryParameter("status", filter.toUriPart())
                    is GenreFilter -> addQueryParameter("genre", filter.toUriPart())
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.comic-card").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.attr("aria-label").ifEmpty { element.selectFirst("h2")!!.text() }
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("a:contains(Berikutnya)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ======================== Details ========================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val bodyString = response.body.string()
        val details = bodyString.extractNextJsRsc<MangaDetailsDto> {
            it is JsonObject && it["@type"]?.jsonPrimitive?.content == "ComicSeries"
        } ?: bodyString.extractNextJsRsc<JsonObject> {
            it is JsonObject && it.containsKey("dangerouslySetInnerHTML") &&
                it.jsonObject["dangerouslySetInnerHTML"]?.jsonObject?.get("__html")?.jsonPrimitive?.content?.contains("ComicSeries") == true
        }?.get("dangerouslySetInnerHTML")?.jsonObject?.get("__html")?.jsonPrimitive?.content?.parseAs<MangaDetailsDto>()
            ?: throw Exception("Gagal memproses detail komik")

        val statusText = bodyString.extractNextJsRsc<JsonObject> {
            it is JsonObject && it.containsKey("className") && it["className"]?.jsonPrimitive?.content == "status-badge"
        }?.get("children")?.jsonPrimitive?.content

        return details.toSManga().apply {
            status = parseStatus(statusText.orEmpty())
            initialized = true
        }
    }

    private fun parseStatus(status: String) = when (status.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "tamat", "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ONGOING
        "drop" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    // ======================== Chapters ========================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val bodyString = response.body.string()
        val chapterList = bodyString.extractNextJsRsc<ChapterListDto> {
            it is JsonObject && it.containsKey("chapters")
        } ?: throw Exception("Gagal memproses daftar chapter")

        val slug = response.request.url.pathSegments.let { segments ->
            val detailIdx = segments.indexOf("detail")
            "${segments[detailIdx + 1]}/${segments[detailIdx + 2]}"
        }

        return chapterList.chapters.map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    // ======================== Pages ========================
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val bodyString = response.body.string()
        val pageList = bodyString.extractNextJsRsc<PageListDto> {
            it is JsonObject && it.containsKey("images")
        } ?: bodyString.extractNextJsRsc<JsonObject> {
            it is JsonObject && it.containsKey("images") && it["images"] is kotlinx.serialization.json.JsonArray
        }?.get("images")?.jsonArray?.let { PageListDto(it.map { img -> img.jsonPrimitive.content }) }
            ?: throw Exception("Gagal memproses daftar gambar")

        return pageList.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Tidak didukung")

    // ======================== Filters ========================
    override fun getFilterList(): FilterList = FilterList(
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )
}
