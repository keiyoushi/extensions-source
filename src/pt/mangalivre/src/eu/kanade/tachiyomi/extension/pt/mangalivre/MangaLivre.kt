package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.os.LocaleList
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaLivre :
    KeiSource(),
    ConfigurableSource {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    private val apiUrl get() = "$baseUrl/api".toHttpUrl()

    private val preferences by getPreferencesLazy()
    private val turnstileMutex = Mutex()

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("Accept-Language", acceptLanguage())

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter()))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(Filter.Sort.Selection(2, false))))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>()
        val url = apiUrl.newBuilder()
            .addPathSegments("mangas/search")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .apply { if (query.isNotBlank()) addQueryParameter("q", query) }
            .addQueryParameter("sortBy", sort.sortBy)
            .addQueryParameter("sortOrder", if (sort.ascending) "asc" else "desc")
            .apply { filters.filterIsInstance<CheckBoxGroup>().forEach { it.addTo(this) } }
            .build()
        val dto = client.get(url, Fetch.XHR).parseAs<WrapperDto>()
        return MangasPage(dto.mangas.map { it.toSManga(useAlternativeTitle) }, dto.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val slug = when (segments.firstOrNull()) {
            "read" -> segments.getOrNull(1)
            "manga" -> segments.lastOrNull()
            else -> segments.firstOrNull()
        } ?: return null
        client.get(mangaBySlugUrl(slug), Fetch.XHR, ensureSuccess = false).use { response ->
            if (response.code == 404) return null
            return response.parseAs<MangaDto>().toSManga(useAlternativeTitle).apply { initialized = true }
        }
    }

    private fun mangaBySlugUrl(slug: String) = apiUrl.newBuilder().addPathSegment("manga-by-slug").addPathSegment(slug).build()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get(mangaBySlugUrl(manga.url), Fetch.XHR).parseAs<MangaDto>()
        return SMangaUpdate(
            manga = dto.toSManga(useAlternativeTitle),
            chapters = dto.toSChapterList(),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reference = chapter.memo.parseAs<ChapterReferenceDto>()
        val chapterUrl = getChapterUrl(chapter)
        val access = fetchReaderAccess(reference, chapterUrl, null)
            ?: turnstileMutex.withLock {
                fetchReaderAccess(reference, chapterUrl, null)
                    ?: fetchReaderAccess(reference, chapterUrl, getTurnstileToken(chapterUrl, TURNSTILE_SITE_KEY, action = TURNSTILE_ACTION))
            }
            ?: throw IOException("Verificação de segurança falhou")
        return access.chapter.pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private suspend fun fetchReaderAccess(reference: ChapterReferenceDto, chapterUrl: String, turnstileToken: String?): ReaderAccessResponseDto? {
        val body = ReaderAccessRequestDto(reference.mangaId, reference.chapterId, turnstileToken).toJsonRequestBody()
        val url = apiUrl.newBuilder().addPathSegments("reader/chapter/access").build()
        client.post(url, Fetch.XHR.from(chapterUrl), body, ensureSuccess = false).use { response ->
            if (response.isSuccessful) return response.parseAs()
            if (response.code == 403) return null
            if (response.body.contentType()?.subtype != "json") throw HttpException(response.code)
            throw IOException(response.parseAs<ReaderAccessErrorDto>().error)
        }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(
        apiUrl.newBuilder()
            .addPathSegment("mangas")
            .addQueryParameter("lite", "1")
            .addQueryParameter("includeRecentChapters", "0")
            .build(),
        Fetch.XHR,
    )
        .parseAs<List<GenresDto>>()
        .flatMap { it.genres.orEmpty() }
        .distinct()
        .sorted()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            YearFilter(),
            data?.parseAs<List<String>>()?.let(::GenreFilter),
        ),
    )

    private val useAlternativeTitle get() = preferences.getBoolean(ALTERNATIVE_TITLE_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context)
            .apply {
                key = ALTERNATIVE_TITLE_PREF
                title = "Título alternativo"
                summary = "Usa o título alternativo como principal quando disponível. Não altera obras já na sua biblioteca."
                setDefaultValue(false)
            }.also(screen::addPreference)
    }

    private fun acceptLanguage(): String {
        val locales = LocaleList.getDefault().let { list -> List(list.size()) { list[it] } }
            .let { locales -> if (locales.any { it.language == "pt" }) locales else locales + PORTUGUESE }
        val tags = buildList {
            locales.forEachIndexed { index, locale ->
                val language = Locale.Builder().setLanguage(locale.language).build().toLanguageTag()
                val tag = if (locale.country.isEmpty()) language else "$language-${locale.country}"
                if (tag !in this) add(tag)
                if (locales.getOrNull(index + 1)?.language != locale.language && language !in this) add(language)
            }
        }
        return tags.mapIndexed { index, tag -> if (index == 0) tag else "$tag;q=0.${(10 - index).coerceAtLeast(1)}" }.joinToString(",")
    }

    companion object {
        private const val ALTERNATIVE_TITLE_PREF = "alternativeTitlePref"
        private val PORTUGUESE = Locale.forLanguageTag("pt-BR")
        private const val TURNSTILE_SITE_KEY = "0x4AAAAAAC_2UGhBWthpfExC"
        private const val TURNSTILE_ACTION = "reader-chapter"
    }
}
