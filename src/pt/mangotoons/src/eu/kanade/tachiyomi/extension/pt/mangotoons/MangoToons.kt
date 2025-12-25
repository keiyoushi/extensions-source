package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.tryParse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangoToons : HttpSource() {

    override val name = "Mango Toons"

    override val baseUrl = "https://mangotoons.com"

    private val cdnUrl = "https://cdn.mangotoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private val json: Json by injectLazy()

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

        val result = json.decodeFromString<MangoResponse<List<MangoMangaDto>>>(decrypted)

        val mangas = result.items.map { dto ->
            SManga.create().apply {
                title = dto.titulo
                url = "/obra/${dto.slug ?: dto.id ?: dto.unique_id}"
                thumbnail_url = (dto.capa ?: dto.imagem)?.let { "$cdnUrl/$it" }
            }
        }

        return MangasPage(mangas, result.pagination?.hasNextPage ?: false)
    }

    // ================= Latest ===================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/capitulos/recentes?pagina=$page&limite=24", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = json.decodeFromString<MangoResponse<List<MangoMangaDto>>>(decrypted)

        val mangas = result.items.map { dto ->
            SManga.create().apply {
                title = dto.titulo
                url = "/obra/${dto.slug ?: dto.id ?: dto.unique_id}"
                thumbnail_url = (dto.capa ?: dto.imagem)?.let { "$cdnUrl/$it" }
            }
        }

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
        val id = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/obras/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = json.decodeFromString<MangoResponse<MangoMangaDto>>(decrypted)
        val dto = result.items

        return SManga.create().apply {
            title = dto.titulo
            url = "/obra/${dto.slug ?: dto.id ?: dto.unique_id}"
            thumbnail_url = (dto.capa ?: dto.imagem)?.let { "$cdnUrl/$it" }
            description = dto.descricao
            genre = dto.tags?.joinToString { it.nome }
            status = when (dto.status_nome) {
                "Ativo", "Em Andamento" -> SManga.ONGOING
                "Concluído" -> SManga.COMPLETED
                "Hiato", "Pausado" -> SManga.ON_HIATUS
                "Cancelado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ================= Chapters ===================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = json.decodeFromString<MangoResponse<MangoMangaDto>>(decrypted)
        val mangaDto = result.items

        val chapters = mangaDto.capitulos?.map { dto ->
            SChapter.create().apply {
                name = "Capítulo ${formatChapterNumber(dto.numero)}"
                chapter_number = dto.numero
                url = "/obra/${dto.obra_id}/capitulo/${formatChapterNumber(dto.numero)}"
                date_upload = dto.data?.let { dateFormat.tryParse(it) } ?: 0L
            }
        } ?: emptyList()

        return chapters.sortedByDescending { it.chapter_number }
    }

    private fun formatChapterNumber(numero: Float): String {
        return DecimalFormat("#.###", DecimalFormatSymbols(Locale.US)).format(numero)
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

        val result = json.decodeFromString<MangoPageResponse>(decrypted)

        val pages = result.capitulo?.paginas ?: emptyList()

        return pages.mapIndexed { index, pageDto ->
            val imageUrl = if (pageDto.url.startsWith("http")) pageDto.url else "$cdnUrl/${pageDto.url}"
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

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
