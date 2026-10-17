package eu.kanade.tachiyomi.extension.pt.manhastro

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Manhastro :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl = "https://api2.manhastro.net"

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        connectTimeout(30.seconds)
        readTimeout(30.seconds)
        val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
        if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
        rateLimit(2)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/rank/diario")
            .parseAs<ApiResponse<List<MangaDto>>>(transform = ::cleanJsonResponse)

        return MangasPage(result.data.map { it.toSManga() }, false)
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.get("$apiUrl/lancamentos")
            .parseAs<ApiResponse<List<MangaDto>>>(transform = ::cleanJsonResponse)

        return MangasPage(result.data.distinctBy { it.mangaId }.map { it.toSManga() }, false)
    }

    // ============================== Search ==============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/dados".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("nome", query)

            val sort = filters.firstInstance<SortFilter>()
            addQueryParameter("sort", sort.selected)
            addQueryParameter("order", sort.order)

            filters.firstInstance<TypeFilter>().state.filter { it.state }.map { it.value }
                .takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("categoria", it.joinToString(",")) }
            filters.firstInstance<GenreFilter>().state.filter { it.state }.map { it.value }
                .takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("genero", it.joinToString(",")) }
        }.build()

        val result = client.get(url).parseAs<ApiResponse<List<MangaDto>>>(transform = ::cleanJsonResponse)
        return MangasPage(result.data.map { it.toSManga() }, result.meta?.hasMore ?: false)
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url.substringAfterLast("/")

        val details = async {
            if (fetchDetails) {
                client.get("$apiUrl/dados?manga_id=$mangaId")
                    .parseAs<ApiResponse<List<MangaDto>>>(transform = ::cleanJsonResponse)
                    .data.firstOrNull()?.toSManga()
                    ?: throw Exception("Manga not found")
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) fetchChapterList(mangaId) else chapters
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    // ============================== Chapters ==============================

    private suspend fun fetchChapterList(mangaId: String): List<SChapter> {
        val result = client.get("$apiUrl/dados/$mangaId")
            .parseAs<ApiResponse<List<ChapterDto>>>(transform = ::cleanJsonResponse)

        return result.data.map { chapter ->
            SChapter.create().apply {
                url = "/capitulo/${chapter.capituloId}"
                name = chapter.capituloNome
                chapter_number = extractChapterNumber(chapter.capituloNome)
                date_upload = DATE_FORMAT.tryParseDateTime(chapter.capituloData)
            }
        }.sortedByDescending { it.chapter_number }
    }

    private fun extractChapterNumber(name: String): Float {
        val regex = Regex("""(\d+(?:\.\d+)?)""")
        val match = regex.find(name)
        return match?.value?.toFloatOrNull() ?: -1f
    }

    // ============================== Pages ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get("$apiUrl/paginas/${chapter.url.substringAfterLast("/")}")
            .parseAs<PagesResponse>(transform = ::cleanJsonResponse)
        val chapterData = result.data.chapter ?: return emptyList()

        return chapterData.data.mapIndexed { i, filename ->
            Page(i, imageUrl = "${chapterData.baseUrl}/${chapterData.hash}/$filename")
        }
    }

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

    private fun cleanJsonResponse(body: String): String = body.removePrefix("\uFEFF")
        .removePrefix(")]}'")
        .removePrefix(",")
        .removePrefix("_")
        .trim()

    private fun MangaDto.toSManga() = SManga.create().apply {
        url = "/manga/$mangaId"
        title = if (useEnglishTitle) {
            titulo.takeIf { it.isNotBlank() } ?: displayTitle
        } else {
            displayTitle
        }
        description = displayDescription
        genre = generos.joinToString()
        thumbnail_url = thumbnailUrl
        status = when (this@toSManga.status) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private val useEnglishTitle: Boolean
        get() =
            preferences.getBoolean(ENGLISH_TITLE_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = ENGLISH_TITLE_PREF
            title = "Títulos em inglês"
            summary = "Use títulos em inglês como principal quando disponível. (Requer ativar \"Atualizar os títulos dos mangás da biblioteca para corresponder à fonte\" em \"Avançado\")"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss", Locale.ROOT)
        private const val ENGLISH_TITLE_PREF = "englishTitlePref"
    }
}
