package eu.kanade.tachiyomi.extension.pt.egotoons

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class EgoToons :
    HttpSource(),
    ConfigurableSource {

    override val name = "Ego Toons"

    override val baseUrl = "https://www.egotoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val versionId = 3

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addInterceptor(ImageDecryptor())
        .build()

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ============================== Popular ================================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/leaderboard".toHttpUrl().newBuilder()
            .addQueryParameter("criteria", "global")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<EgoToonsMangaDto>>()
        val mangas = result.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // ============================= Latest Updates ==========================
    override fun latestUpdatesRequest(page: Int): Request {
        val limit = 20
        val offset = (page - 1) * limit
        val withHentai = preferences.getBoolean(PREF_HENTAI_KEY, PREF_HENTAI_DEFAULT)

        val url = "$baseUrl/api/releases".toHttpUrl().newBuilder()
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("withHentai", withHentai.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<EgoToonsPaginatedDto<EgoToonsMangaDto>>()
        val mangas = result.items.map { it.toSManga() }
        return MangasPage(mangas, result.pagination.hasNextPage)
    }

    // =============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val limit = 20
        val offset = (page - 1) * limit
        val withHentai = preferences.getBoolean(PREF_HENTAI_KEY, PREF_HENTAI_DEFAULT)

        val url = "$baseUrl/api/manga/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("withHentai", withHentai.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("genres", it.name)
                    }
                }

                is TagFilter -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("tags", it.name)
                    }
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<EgoToonsPaginatedDto<EgoToonsMangaDto>>()
        val mangas = result.items.map { it.toSManga() }
        return MangasPage(mangas, result.pagination.hasNextPage)
    }

    // ============================== Filters ================================
    override fun getFilterList() = FilterList(
        GenreFilter(),
        TagFilter(),
    )

    // ============================ Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(getMangaId(manga.url))
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<EgoToonsMangaDto>().toSManga()

    // ============================== Chapters ===============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val mangaId = getMangaId(manga.url)
        val pageSize = 20

        return client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { response ->
                response.parseAs<EgoToonsPaginatedDto<EgoToonsChapterDto>>()
            }
            .flatMap { firstPageDto ->
                val firstPageChapters = firstPageDto.items.map { it.toSChapter(mangaId.toInt()) }
                val total = firstPageDto.pagination.total

                if (total <= pageSize) {
                    Observable.just(firstPageChapters)
                } else {
                    val totalPages = (total + pageSize - 1) / pageSize
                    val remainingRequests = (1 until totalPages).map { page ->
                        val url = baseUrl.toHttpUrl().newBuilder()
                            .addPathSegments("api/manga")
                            .addPathSegment(mangaId)
                            .addPathSegment("chapter")
                            .addQueryParameter("limit", pageSize.toString())
                            .addQueryParameter("offset", (page * pageSize).toString())
                            .build()

                        val request = GET(url, headers)
                        client.newCall(request).asObservableSuccess()
                            .map { chapterListParse(it) }
                    }
                    Observable.concat(
                        Observable.just(firstPageChapters),
                        Observable.concat(remainingRequests),
                    ).reduce(emptyList<SChapter>()) { acc, list -> acc + list }
                }
            }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(getMangaId(manga.url))
            .addPathSegment("chapter")
            .addQueryParameter("limit", "20")
            .addQueryParameter("offset", "0")
            .build()

        return GET(url, headers)
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.pathSegments[2].toInt()
        val result = response.parseAs<EgoToonsPaginatedDto<EgoToonsChapterDto>>()
        return result.items.map { it.toSChapter(mangaId) }
    }

    // =============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = getMangaId(chapter.url)
        val number = chapter.chapter_number.toString().removeSuffix(".0")

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(mangaId)
            .addPathSegment("chapter")
            .addPathSegment(number)
            .addPathSegment("images")
            .build()

        return GET(
            url,
            headersBuilder()
                .add("x-mymangas-csrf-secure", "true")
                .add("x-mymangas-secure-panel-domain", "true")
                .build(),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<List<String>>().filter { it.isNotBlank() }

        if (pages.isEmpty()) {
            throw Exception("Lista de páginas vazia. Tente abrir na WebView.")
        }

        return pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ===============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HENTAI_KEY
            title = "Exibir conteúdo Hentai (+18)"
            summary = "Habilita a visualização de conteúdo adulto nas listas."
            setDefaultValue(PREF_HENTAI_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                val checked = newValue as Boolean
                preferences.edit().putBoolean(PREF_HENTAI_KEY, checked).apply()
                true
            }
        }.also(screen::addPreference)
    }

    // =============================== Utils =================================

    override fun getMangaUrl(manga: SManga): String {
        val id = getMangaId(manga.url)
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("obra")
            .addPathSegment(id)
            .build()
            .toString()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = getMangaId(chapter.url)
        val chapterSlug = if (chapter.url.startsWith("http")) {
            chapter.url.toHttpUrl().pathSegments.last()
        } else {
            chapter.url.trimEnd('/').substringAfterLast('/')
        }

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("obra")
            .addPathSegment(mangaId)
            .addPathSegment("capitulo")
            .addPathSegment(chapterSlug)
            .build()
            .toString()
    }

    private fun getMangaId(url: String): String {
        val absoluteUrl = if (url.startsWith("http")) {
            url
        } else {
            "$baseUrl/${url.trimStart('/')}"
        }

        val segments = absoluteUrl.toHttpUrl().pathSegments
        val index = segments.indexOfFirst { it == "obra" || it == "manga" }

        return segments[index + 1]
    }

    companion object {
        private const val PREF_HENTAI_KEY = "pref_hentai"
        private const val PREF_HENTAI_DEFAULT = false
    }
}
