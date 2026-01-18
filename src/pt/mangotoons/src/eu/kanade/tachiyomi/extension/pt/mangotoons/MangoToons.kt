package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class MangoToons : HttpSource() {

    override val name = "Mango Toons"

    override val baseUrl = "https://mangotoons.com"

    private val cdnUrl = "https://cdn.mangotoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")

    // ================= Popular ===================
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/obras/top10/views?periodo=total", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoResponse<List<MangoMangaDto>>>()

        val mangas = result.items.map { it.toSManga(cdnUrl) }

        return MangasPage(mangas, result.pagination?.hasNextPage ?: false)
    }

    // ================= Latest ===================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/capitulos/recentes?pagina=$page&limite=24", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoResponse<List<MangoMangaDto>>>()

        val mangas = result.items.map { it.toSManga(cdnUrl) }

        return MangasPage(mangas, result.pagination?.hasNextPage ?: false)
    }

    // ================= Search ===================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/obras".toHttpUrl().newBuilder()
            .addQueryParameter("busca", query)
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "20")

        filters.filterIsInstance<UrlQueryFilter>()
            .forEach { it.addQueryParameter(url) }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        StatusFilter(),
        FormatFilter(),
        TagFilter(),
    )

    // ================= Details ===================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val rawId = manga.url.substringAfterLast("/")
        val id = rawId.substringBefore("-")
        return GET("$baseUrl/api/obras/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoResponse<MangoMangaDto>>()
        val dto = result.items

        return dto.toSManga(cdnUrl)
    }

    // ================= Chapters ===================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoResponse<MangoMangaDto>>()
        val mangaDto = result.items

        return mangaDto.capitulos
            ?.map { it.toSChapter() }
            ?.sortedByDescending { it.chapter_number }
            ?: emptyList()
    }

    // ================= Pages ===================
    override fun pageListRequest(chapter: SChapter): Request {
        val apiUrl = baseUrl + "/api" + chapter.url.replace("/capitulo/", "/capitulos/")
            .replace("/obra/", "/obras/")
        return GET(apiUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoPageResponse>()

        val capitulo = result.capitulo ?: return emptyList()
        val obraId = capitulo.obraId
        val chapterNumero = capitulo.numero

        return capitulo.paginas.mapIndexed { index, pageDto ->
            val imageUrl = "$baseUrl/api/cdn/obra/$obraId/capitulo/$chapterNumero/image/${pageDto.imageRandomId}"
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used.")
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }
}
