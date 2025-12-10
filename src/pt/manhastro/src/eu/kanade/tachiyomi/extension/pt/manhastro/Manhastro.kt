package eu.kanade.tachiyomi.extension.pt.manhastro

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Manhastro : HttpSource() {

    override val name = "Manhastro"

    override val baseUrl = "https://manhastro.net"

    private val apiUrl = "https://api2.manhastro.net"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
            if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
        }
        .build()

    private val dataClient = client.newBuilder()
        .cache(
            Cache(
                directory = File(Injekt.get<Application>().externalCacheDir, "network_cache_${name.lowercase()}"),
                maxSize = 10L * 1024 * 1024, // 10 MiB
            ),
        )
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .removeHeader("Cache-Control")
                .removeHeader("Expires")
                .removeHeader("Pragma")
                .build()
        }
        .build()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/rank/diario", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiResponse<List<RankingItemDto>>>(transform = ::cleanJsonResponse)
        val mangaIds = result.data.map { it.mangaId }
        val mangaMap = fetchMangasByIds(mangaIds)

        val mangas = result.data.mapNotNull { mangaMap[it.mangaId]?.toSManga() }

        return MangasPage(mangas, false)
    }

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/lancamentos", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<ApiResponse<List<LatestItemDto>>>(transform = ::cleanJsonResponse)
        val mangaIds = result.data.map { it.mangaId }.distinct()
        val mangaMap = fetchMangasByIds(mangaIds)

        val mangas = mangaIds.mapNotNull { mangaMap[it]?.toSManga() }

        return MangasPage(mangas, false)
    }

    // ============================== Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        rx.Observable.fromCallable {
            var mangas = fetchAllMangas()

            if (query.isNotBlank()) {
                val q = query.lowercase().normalize()
                mangas = mangas.filter { manga ->
                    val titulo = manga.titulo.lowercase().normalize()
                    val tituloBrasil = manga.tituloBrasil?.lowercase()?.normalize() ?: ""
                    titulo.contains(q) || tituloBrasil.contains(q)
                }
            }

            val typeFilter = filters.filterIsInstance<TypeFilter>().firstOrNull()
            val genreFilter = filters.filterIsInstance<GenreFilter>().firstOrNull()
            val sortFilter = filters.filterIsInstance<SortFilter>().firstOrNull()

            val selectedTypes = typeFilter?.state?.filter { it.state }?.map { it.value } ?: emptyList()
            if (selectedTypes.isNotEmpty()) {
                mangas = mangas.filter { manga ->
                    val mangaGenres = parseGenres(manga.generos)
                    selectedTypes.any { type ->
                        mangaGenres.any { it.equals(type, ignoreCase = true) }
                    }
                }
            }

            val selectedGenres = genreFilter?.state?.filter { it.state }?.map { it.value } ?: emptyList()
            if (selectedGenres.isNotEmpty()) {
                mangas = mangas.filter { manga ->
                    val mangaGenres = parseGenres(manga.generos)
                    selectedGenres.all { genre ->
                        mangaGenres.any { it.equals(genre, ignoreCase = true) }
                    }
                }
            }

            val sortOption = sortFilter?.selected ?: "popular"
            mangas = when (sortOption) {
                "popular" -> mangas.sortedByDescending { it.popularity }
                "recent" -> mangas.sortedByDescending { it.mangaId }
                "alphabetical" -> mangas.sortedBy { it.displayTitle.lowercase() }
                "chapters" -> mangas.sortedByDescending { it.qntCapitulo ?: 0 }
                else -> mangas
            }

            MangasPage(mangas.map { it.toSManga() }, false)
        }!!

    private fun parseGenres(generos: String?): List<String> {
        if (generos.isNullOrBlank()) return emptyList()

        return try {
            json.decodeFromString<List<String>>(generos)
        } catch (_: Exception) {
            if (generos.contains(",")) {
                generos.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                listOf(generos.trim()).filter { it.isNotEmpty() }
            }
        }
    }

    private fun String.normalize(): String {
        return java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
    }

    override fun getFilterList() = getFilters()

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga) =
        throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga) = rx.Observable.fromCallable {
        val mangaId = manga.url.substringAfterLast("/").toInt()
        val allMangas = fetchAllMangas()
        allMangas.find { it.mangaId == mangaId }?.toSManga()
            ?: throw Exception("Manga not found")
    }!!

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga) =
        GET("$apiUrl/dados/${manga.url.substringAfterLast("/")}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ApiResponse<List<ChapterDto>>>(transform = ::cleanJsonResponse)

        return result.data.map { chapter ->
            SChapter.create().apply {
                url = "/capitulo/${chapter.capituloId}"
                name = chapter.capituloNome
                chapter_number = extractChapterNumber(chapter.capituloNome)
                date_upload = DATE_FORMAT.tryParse(chapter.capituloData)
            }
        }.sortedByDescending { it.chapter_number }
    }

    private fun extractChapterNumber(name: String): Float {
        val regex = Regex("""(\d+(?:\.\d+)?)""")
        val match = regex.find(name)
        return match?.value?.toFloatOrNull() ?: -1f
    }

    // ============================== Pages ==============================

    override fun pageListRequest(chapter: SChapter) =
        GET("$apiUrl/paginas/${chapter.url.substringAfterLast("/")}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PagesResponse>(transform = ::cleanJsonResponse)
        val chapter = result.data.chapter ?: return emptyList()

        return chapter.data.mapIndexed { i, filename ->
            Page(i, imageUrl = "${chapter.baseUrl}/${chapter.hash}/$filename")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== URLs ==============================

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast("/")
        return "$baseUrl/manga/$mangaId"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterId = chapter.url.substringAfterLast("/")
        return "$baseUrl/capitulo/$chapterId"
    }

    // ============================== Helpers ==============================

    private fun cleanJsonResponse(body: String): String =
        body.removePrefix("\uFEFF")
            .removePrefix(")]}'")
            .removePrefix(",")
            .removePrefix("_")
            .trim()

    private fun fetchAllMangas(): List<MangaDto> {
        val request = GET(
            "$apiUrl/dados",
            headers,
            CacheControl.Builder().maxStale(30, TimeUnit.MINUTES).build(),
        )
        val response = dataClient.newCall(request).execute()
        return response.parseAs<ApiResponse<List<MangaDto>>>(transform = ::cleanJsonResponse).data
    }

    private fun fetchMangasByIds(ids: List<Int>): Map<Int, MangaDto> {
        val allMangas = fetchAllMangas()
        return allMangas.filter { it.mangaId in ids }.associateBy { it.mangaId }
    }

    private fun MangaDto.toSManga() = SManga.create().apply {
        url = "/manga/$mangaId"
        title = displayTitle
        description = displayDescription
        genre = parseGenres(generos).joinToString()
        thumbnail_url = thumbnailUrl
        status = SManga.UNKNOWN
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }
}
