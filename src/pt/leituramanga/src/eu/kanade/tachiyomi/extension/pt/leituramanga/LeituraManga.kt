package eu.kanade.tachiyomi.extension.pt.leituramanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeituraManga : KeiSource() {

    private val apiUrl = "https://api.leituramanga.net"

    private val cdnUrl = "https://cdn.leituramanga.net"

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
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
        rateLimit(1, 2.seconds)
    }

    // ================= Popular ==================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$apiUrl/api/manga/?sort=view&limit=24&page=$page")

    // ================= Latest ==================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$apiUrl/api/manga/?sort=time&limit=24&page=$page")

    private suspend fun parseMangaList(url: String): MangasPage {
        val result = client.get(url).parseAs<MangaResponseDto<MangaListDto>>()
        val mangas = result.data.data.map { it.toSManga(cdnUrl) }
        val hasNext = result.data.pagination.let { it.page < it.totalPage }
        return MangasPage(mangas, hasNext)
    }

    // ================= Search ==================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return parseMangaList(url.build().toString())
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    // ================= Details ==================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        manga.apply {
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("h2:contains(Sinopse) +div p")?.text()
            author = document.selectFirst("h2:contains(Informações) +div p:contains(Autor)")?.text()?.substringAfter(":")
            genre = document.select("h2 + div > a[href*=genre]").joinToString { it.text() }

            status = when (document.selectFirst("h2:contains(Informações) +div p:contains(Status)")?.text()?.substringAfter(":")?.trim()?.lowercase()) {
                "Em breve", "Em andamento" -> SManga.ONGOING
                "Completo" -> SManga.COMPLETED
                "Cancelado" -> SManga.CANCELLED
                "Em pausa" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            setUrlWithoutDomain(document.location())
        }

        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val mangaId = document.extractNextJs<NextJsMangaIdDto> {
            (it as? JsonObject)?.containsKey("mangaId") == true
        }?.mangaId ?: document.extractNextJs<MangaPagePropsDto> {
            val manga = (it as? JsonObject)?.get("manga") as? JsonObject
            manga?.containsKey("_id") == true
        }?.manga?.id ?: throw IOException("ID do mangá não encontrado")

        val slug = document.location().toHttpUrl().pathSegments.last { it.isNotEmpty() }

        // Using the exact limit from the frontend so we get the 80/min limit instead of the default 3/min limit
        val result = client.get("$apiUrl/api/chapter/get-by-manga-id?mangaId=$mangaId&page=1&limit=9007199254740991")
            .parseAs<MangaResponseDto<ChapterListDto>>()

        return SMangaUpdate(manga, result.data.data.map { it.toSChapter(slug) })
    }

    // ================= Pages ==================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val dto = client.get(getChapterUrl(chapter)).extractNextJs<ChapterPageDto> {
            val chapter = (it as? JsonObject)?.get("chapter") as? JsonObject
            chapter?.containsKey("images") == true
        } ?: throw IOException("Páginas não encontradas")

        return dto.chapter.images.mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl(cdnUrl))
        }
    }
}
