package eu.kanade.tachiyomi.extension.pt.wolftoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Wolftoon : HttpSource() {

    override val name = "Wolftoon"

    override val baseUrl = "https://wolftoon.lovable.app"

    private val supabaseUrl = "https://encmakrlmutvsdzpodov.supabase.co"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2, 1)
        .rateLimitHost(supabaseUrl.toHttpUrl(), 2, 1)
        .addInterceptor(CookieInterceptor(supabaseUrl.toHttpUrl().host, emptyList()))
        .build()

    private val scriptUrl: String by lazy {
        val response = client.newCall(GET(baseUrl, headers)).execute()
        val html = response.body.string()
        if (html.isBlank()) throw Exception("HTML vazio recebido de $baseUrl")
        val match = Regex("""src=["']?(/assets/index-[^"'>]+\.js)["']?""").find(html)
        match?.groupValues?.get(1)?.let { "$baseUrl$it" }
            ?: throw Exception("URL do script não encontrada no HTML")
    }

    private val apiKey: String by lazy {
        val script = client.newCall(GET(scriptUrl, headers)).execute().body.string()
        API_KEY_REGEX.find(script)?.groupValues?.get(1) ?: throw Exception("API key não encontrada no script")
    }

    private val apiHeaders: Headers by lazy {
        headersBuilder()
            .set("apikey", apiKey)
            .set("Authorization", "Bearer $apiKey")
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    // ================================ Popular =======================================

    val popularFilter = FilterList(OrderBy("", arrayOf("Mais Popular")))

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", popularFilter)

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()

    // ================================ Latest =======================================

    val latestFilter = FilterList(OrderBy("", arrayOf("Mais Recentes")))

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", latestFilter)

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // ================================ Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$supabaseUrl/rest/v1/titles".toHttpUrl().newBuilder()
            .setQueryParameter("select", "*")
            .setQueryParameter("order", "rating.desc")
            .setQueryParameter("apikey", apiKey)
            .build()
        return GET(url, apiHeaders)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.fromCallable {
            val response = client.newCall(searchMangaRequest(page, query, filters)).execute()
            var mangas = response.parseAs<List<MangaDto>>()

            if (query.isNotBlank()) {
                return@fromCallable MangasPage(
                    mangas = mangas.filter { dto ->
                        listOf(dto.title, dto.synopsis).any { it.contains(query, ignoreCase = true) }
                    }.map { it.toSManga() },
                    hasNextPage = false,
                )
            }

            filters
                .filterIsInstance<Select<String>>()
                .filter {
                    !it.selected.equals("Todos", ignoreCase = true)
                }
                .forEach { filter ->
                    when (filter) {
                        is StatusFilter -> {
                            mangas = mangas.filter {
                                it.status.equals(filter.selected, ignoreCase = true)
                            }
                        }

                        is TypeFilter -> {
                            mangas = mangas.filter {
                                it.type.equals(filter.selected, ignoreCase = true)
                            }
                        }

                        is GenreFilter -> {
                            mangas = mangas.filter { dto ->
                                dto.genres.any { it.equals(filter.selected, ignoreCase = true) }
                            }
                        }

                        is OrderBy -> {
                            mangas = when (filter.selected.lowercase()) {
                                "mais popular" -> mangas.sortedByDescending { it.views }
                                "mais recentes" -> mangas.sortedByDescending { it.updatedAt() }
                                "melhor avaliado" -> mangas.sortedByDescending { it.rating }
                                else -> mangas
                            }
                        }

                        else -> {}
                    }
                }

            MangasPage(mangas.map { it.toSManga() }, hasNextPage = false)
        }
    }

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    // ================================ Details =======================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = getMangaUrl(manga).toHttpUrl().fragment
        val url = "$supabaseUrl/rest/v1/titles".toHttpUrl().newBuilder()
            .setQueryParameter("select", "*")
            .setQueryParameter("id", "eq.$titleId")
            .setQueryParameter("apikey", apiKey)
            .build()
        return GET(url, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<List<MangaDto>>().first().toSManga()

    // ================================ Chapters =======================================

    override fun chapterListRequest(manga: SManga): Request {
        val titleId = getMangaUrl(manga).toHttpUrl().fragment
        val url = "$supabaseUrl/rest/v1/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,title_id,chapter_number,created_at")
            .addQueryParameter("title_id", "eq.$titleId")
            .addQueryParameter("order", "chapter_number.desc")
            .addQueryParameter("apikey", apiKey)
            .fragment(titleId)
            .build()

        return GET(url, apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<List<ChapterDto>>().map { it.toSChapter() }

    // ================================ Pages =======================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val chapterId = chapterUrl.fragment
        val titleId = chapterUrl.pathSegments[1]
        val url = "$supabaseUrl/rest/v1/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,title_id,images")
            .addQueryParameter("title_id", "eq.$titleId")
            .addQueryParameter("order", "chapter_number.desc")
            .addQueryParameter("apikey", apiKey)
            .fragment(chapterId)
            .build()
        pageListParse(client.newCall(GET(url, apiHeaders)).execute())
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.fragment
        return response.parseAs<List<PageDto>>()
            .firstOrNull { it.id == chapterId }
            ?.toPageList()
            ?: emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ================================ Filters =======================================

    private var genreList: Array<String> = emptyArray()
    private fun getGenreList() {
        if (genreList.isNotEmpty()) {
            return
        }

        val script = client.newCall(GET(scriptUrl, headers)).execute().body.string()
        genreList = arrayOf("Todos") + (
            GENRE_REGEX.find(script)?.groupValues?.last()
                ?.replace("'", "\"")
                ?.parseAs<Array<String>>()
                ?: emptyArray()
            )
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun getFilterList(): FilterList {
        scope.launch { getGenreList() }
        val filters = mutableListOf<Filter<*>>(
            StatusFilter("Status", statusList),
            TypeFilter("Tipo", typeList),
            OrderBy("Ordenar", orderByList),
        )

        when {
            genreList.isNotEmpty() -> filters += GenreFilter("Gêneros", genreList)

            else -> filters += listOf(
                Filter.Separator(),
                Filter.Header("Aperte 'Redefinir' para tentar mostrar os gêneros"),
            )
        }

        return FilterList(filters)
    }

    companion object {
        private val GENRE_REGEX = """\w+\s*=\s*(\['Ação',[^]]+])""".toRegex()
        private val API_KEY_REGEX = """supabase\.co['"],\s*[a-zA-Z0-9_$]+\s*=\s*['"](eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\.[^'"]+)['"]""".toRegex()
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        }
    }
}
