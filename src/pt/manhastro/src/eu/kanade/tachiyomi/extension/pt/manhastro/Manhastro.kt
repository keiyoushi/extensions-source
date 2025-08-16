package eu.kanade.tachiyomi.extension.pt.manhastro

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.text.Normalizer

class Manhastro : HttpSource() {

    override val name: String = "Manhastro"

    override val baseUrl: String = "https://manhastro.net"

    override val lang: String = "pt-BR"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private val apiUrl: String = "https://api2.manhastro.net"

    private val mangaSubString = "manga"

    private val oldMangaSubString = "lermanga"

    private val database: Map<Int, MangaDto> by lazy {
        client.newCall(GET("$apiUrl/dados"))
            .execute()
            .parseAs<ResponseWrapper<List<MangaDto>>>()
            .data
            .associateBy { it.id }
    }

    override val supportsLatest: Boolean = true

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/rank/mensal", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<ResponseWrapper<List<PopularMangaDto>>>().data
            .distinctBy(PopularMangaDto::id)
            .mapNotNull { dto -> database[dto.id]?.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/lancamentos", headers)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.fromCallable {
            val mangas = database.values.filter { dto ->
                dto.title.contains(query) || dto.titleLocalized?.contains(query) ?: false
            }.map { it.toSManga() }
            MangasPage(mangas, hasNextPage = false)
        }
    }

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String {
        if (manga.url.contains(oldMangaSubString, ignoreCase = true)) {
            throw IOException("Migrate a obra para a $name")
        }
        return "$baseUrl/$mangaSubString/${manga.url}"
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.fromCallable {
            if (manga.url.contains(oldMangaSubString)) {
                return@fromCallable tryCompatibility(manga)
            }
            database[manga.url.toInt()]?.toSManga() ?: throw IOException("Não encontrado")
        }
    }

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun chapterListRequest(manga: SManga): Request {
        val url = if (manga.url.contains(oldMangaSubString)) tryCompatibility(manga).url else manga.url
        return GET("$apiUrl/dados/$url", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val ids = chapter.url.split(":")
        return "$baseUrl/$mangaSubString/${ids.first()}/chapter/${ids.last()}"
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.pathSegments.last().toInt()
        return response.parseAs<ResponseWrapper<List<ChapterDto>>>().data
            .map { it.toSChapter(mangaId) }
            .sortedByDescending(SChapter::chapter_number)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast(":")
        return GET("$apiUrl/paginas/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<ResponseWrapper<PageDto>>().data.toPageList()
    }

    override fun imageUrlParse(response: Response): String = ""

    // ============================== Utilities =================================

    private fun tryCompatibility(manga: SManga): SManga {
        val slug = manga.url.substringAfterLast("$oldMangaSubString/")
        return database.values.firstOrNull { it.slug == slug }
            ?.toSManga()
            ?: throw IOException("Migrate a obra para a $name")
    }

    private fun String.normalize(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(ACCENT_MARKS_REGEX, "")
    }

    private fun String.contains(other: String): Boolean {
        return normalize().contains(other.normalize(), ignoreCase = true)
    }

    companion object {
        val ACCENT_MARKS_REGEX = "\\p{M}+".toRegex()
    }
}
