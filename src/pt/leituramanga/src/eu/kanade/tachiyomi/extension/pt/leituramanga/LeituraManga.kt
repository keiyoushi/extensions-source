package eu.kanade.tachiyomi.extension.pt.leituramanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class LeituraManga : HttpSource() {

    override val name = "Leitura Mangá"

    override val baseUrl = "https://leituramanga.net"

    private val apiUrl = "https://api.leituramanga.net"

    private val cdnUrl = "https://cdn.leituramanga.net"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            when (response.code) {
                403 -> {
                    response.close()
                    throw IOException("Abra primeiramente algum mangá ou qualquer capítulo na WebiView")
                }
                429 -> {
                    val retryAfter = response.header("Retry-After") ?: "alguns"
                    response.close()
                    throw IOException("Limite do site atingido. Aguarde $retryAfter segundos.")
                }
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // ================= Popular ==================

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/api/manga/?sort=view&limit=24&page=$page", headers)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    // ================= Latest ==================

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/api/manga/?sort=time&limit=24&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<MangaResponseDto<MangaListDto>>()
        val mangas = result.data.data.map { it.toSManga(cdnUrl) }
        val hasNext = result.data.pagination.let { it.page < it.totalPage }
        return MangasPage(mangas, hasNext)
    }

    // ================= Search ==================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "24")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.selectedValue.isNotEmpty()) {
                        url.addQueryParameter("genre", filter.selectedValue)
                    }
                }

                is StatusFilter -> {
                    if (filter.selectedValue.isNotEmpty()) {
                        url.addQueryParameter("status", filter.selectedValue)
                    }
                }

                is SortFilter -> {
                    url.addQueryParameter("sort", filter.selectedValue)
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun getFilterList() = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    // ================= Details ==================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("h2:contains(Sinopse) +div p")?.text()
        author = document.selectFirst("h2:contains(Informações) +div p:contains(Autor)")?.text()?.substringAfter(":")
        genre = document.select("h2 + div > a[href*=genre]").joinToString { it.text() }

        status = when (document.selectFirst("h2:contains(Informações) +div p:contains(Status)")?.text()?.substringAfter(":")?.trim()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        setUrlWithoutDomain(document.location())
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    // ================= Chapters ==================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val mangaId = document.extractNextJs<NextJsMangaIdDto> {
            (it as? JsonObject)?.containsKey("mangaId") == true
        }?.mangaId ?: document.extractNextJs<MangaPagePropsDto> {
            val manga = (it as? JsonObject)?.get("manga") as? JsonObject
            manga?.containsKey("_id") == true
        }?.manga?.id ?: throw IOException("ID do mangá não encontrado")

        val slug = response.request.url.pathSegments.last { it.isNotEmpty() }

        // Using the exact limit from the frontend so we get the 80/min limit instead of the default 3/min limit
        val request = GET("$apiUrl/api/chapter/get-by-manga-id?mangaId=$mangaId&page=1&limit=9007199254740991", headers)
        val result = client.newCall(request).execute().parseAs<MangaResponseDto<ChapterListDto>>()

        return result.data.data.map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    // ================= Pages ==================

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.extractNextJs<ChapterPageDto> {
            val chapter = (it as? JsonObject)?.get("chapter") as? JsonObject
            chapter?.containsKey("images") == true
        } ?: throw IOException("Páginas não encontradas")

        return dto.chapter.images.mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl(cdnUrl))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
