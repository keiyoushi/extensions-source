package eu.kanade.tachiyomi.extension.all.webcomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Webcomics : KeiSource() {

    private val apiSubDomain = "official-website-api"
    private val apiUrl = "https://$apiSubDomain.${baseUrl.substringAfterLast("/")}/api/web/v4/book"

    override fun Headers.Builder.configureHeaders() = apply {
        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()
            val ua = runBlocking(Dispatchers.IO) {
                getDesktopUA()
            }

            val newHeaders = request.headers.newBuilder()
                .set("User-Agent", ua.desktop.random())
                .build()

            val newRequest = request.newBuilder()
                .headers(newHeaders)
                .build()

            chain.proceed(newRequest)
        }
        rateLimit(3)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private var userAgentList: UserAgentList? = null

    private suspend fun getDesktopUA(): UserAgentList = userAgentList ?: network.client.get(UA_DB_URL).parseAs<UserAgentList>().also {
        userAgentList = it
    }

    // ========================== Popular =====================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(lang)
            addPathSegment(genreTitle.toPathSegment())
            addPathSegment(genres.first().toPathSegment())
            addPathSegment(statuses.first().toPathSegment())
            addPathSegment(sorts.first().toPathSegment())
            addPathSegment(page.toString())
        }.build()

        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val nuxtData = document.selectFirst("script#__NUXT_DATA__")?.data()

        val mangaList = document.select(".grid > a").map { element ->
            val url = element.attr("abs:href")
            val thumbnail = element.selectFirst("img[src]")?.attr("abs:src")?.takeIf { it.startsWith("http") } ?: run {
                val regex = Regex(""""${url.substringAfterLast('/')}"[^"]*"[^"]*"[^"]*"(https[^"]+)"""")
                nuxtData?.let { regex.find(it) }?.groupValues?.get(1)
            }

            SManga.create().apply {
                title = element.selectFirst("p.text-ink,span[class*=text]")!!.text()
                thumbnail_url = thumbnail
                setUrlWithoutDomain(url)
            }
        }

        val hasNextPage = document.selectFirst("div > span.cursor-default.bg-primary + a") != null
        return MangasPage(mangaList, hasNextPage)
    }

    // ========================== Latest =====================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(lang)
            addPathSegment(genreTitle.toPathSegment())
            addPathSegment(genres.first().toPathSegment())
            addPathSegment(statuses.first().toPathSegment())
            addPathSegment(sorts.last().toPathSegment())
            addPathSegment(page.toString())
        }.build()

        return parseMangasPage(client.get(url))
    }

    // ========================== Search =====================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.isNotBlank()) {
        val url = "$baseUrl/$lang/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        parseMangasPage(client.get(url))
    } else {
        val genre = filters.firstInstance<GenreFilter>().selected()
        val status = filters.firstInstance<StatusFilter>().selected()
        val sort = filters.firstInstance<SortFilter>().selected()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(lang)
            addPathSegment(genreTitle.toPathSegment())
            addPathSegment(genre.toPathSegment())
            addPathSegment(status.toPathSegment())
            addPathSegment(sort.toPathSegment())
            addPathSegment(page.toString())
        }.build()
        parseMangasPage(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != lang || url.pathSegments.size < 4) {
            return null
        }

        val mangaUrl = "/$lang/${url.pathSegments[1]}/${url.pathSegments[2]}/${url.pathSegments[3]}"
        val manga = SManga.create().apply {
            this.url = mangaUrl
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = mangaUrl
            }
    }

    // ========================== Updates ====================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (manga, chapters) = coroutineScope {
            val mangaD = async { if (fetchDetails) getMangaDetails(manga) else manga }
            val chaptersD = async { if (fetchChapters) getChapterList(manga) else chapters }
            mangaD.await() to chaptersD.await()
        }

        return SMangaUpdate(manga, chapters)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val mangaId = manga.url.substringAfterLast("/")
        val body = buildJsonObject {
            put("book_id", mangaId)
        }.toJsonRequestBody()

        val dto = client.post("$apiUrl/info", body).parseAs<DataWrapper<BookDto>>().data
        return SManga.create().apply {
            title = dto.name
            thumbnail_url = dto.cover
            description = dto.description
            author = dto.author
            genre = dto.category.joinToString()
            status = if (dto.status == statuses[1]) SManga.ONGOING else SManga.COMPLETED
        }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val mangaId = manga.url.substringAfterLast("/")
        val body = buildJsonObject {
            put("book_id", mangaId)
            put("page", 1)
            put("size", 9999)
            put("sort", "desc")
        }.toJsonRequestBody()

        val dto = client.post("$apiUrl/chapter/list", body).parseAs<DataWrapper<ChapterListDto>>().data
        return dto.list.map { chapter ->
            SChapter.create().apply {
                name = if (chapter.is_pay) "🔒 ${chapter.name}" else chapter.name
                date_upload = chapter.update_time
                chapter_number = chapter.index.toFloat()

                val chapterUrl = (baseUrl + manga.url).toHttpUrl().newBuilder()
                    .setPathSegment(3, chapter.index.toString())
                    .addPathSegment(mangaId)
                    .build()
                    .toString()

                setUrlWithoutDomain(chapterUrl)

                memo = buildJsonObject {
                    put("index", chapter.index)
                    put("mangaId", mangaId)
                    put("id", chapter.chapter_id)
                }
            }
        }
    }

    // ========================== Pages ====================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = buildJsonObject {
            put("book_id", chapter.memo["mangaId"]!!.string)
            put("chapter_id", chapter.memo["id"]!!.string)
            put("index", chapter.memo["index"]!!.int)
        }.toJsonRequestBody()

        val dto = client.post("$apiUrl/chapter/detail", body).parseAs<DataWrapper<ImageListDto>>().data

        return dto.images.mapIndexed { index, img ->
            Page(index, imageUrl = dto.base_url + img.url)
        }
    }

    // ========================== Filters ==================================

    private open inner class SelectFilter(name: String, val items: Array<String>) : Filter.Select<String>(name, items) {
        fun selected() = items[state].toPathSegment()
    }

    private val genreTitle = when (lang) {
        "en" -> "Genres"
        "fr" -> "Genres"
        "pt" -> "Gêneros"
        "es" -> "Géneros"
        "id" -> "Genre"
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }
    private val genres = when (lang) {
        "en" -> arrayOf(
            "All", "Romance", "Action", "Fantasy", "BL", "Eastern Fantasy",
            "Eastern Romance", "Drama", "GL", "LGBTQ+", "Slice of Life", "Comedy",
            "Horror", "Mystery", "Sci-Fi",
        )
        "fr" -> arrayOf(
            "Tous", "Amour", "Action", "Fantaisie", "BL", "Fantaisie orientale",
            "Amour orientale", "Drame", "Comédie", "Science-fiction",
        )
        "pt" -> arrayOf(
            "Todos", "Romance", "Ação", "Fantasia", "BL", "Fantasia Oriental Antiga",
            "Romance Oriental Antigo", "Drama", "Humor", "Ficção",
        )
        "es" -> arrayOf(
            "Todos", "Romance", "Acción", "Fantasía", "BL", "Fantasía Oriental",
            "Romance Oriental", "Drama", "Comedia", "Ciencia Ficción",
        )
        "id" -> arrayOf(
            "Semua", "Romantis", "Aksi", "Fantasi", "Boys' Love", "Fantasi Asia",
            "Romantis Asia", "Drama", "Komedi", "Sci-Fi",
        )
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    private val statusTitle = when (lang) {
        "en" -> "Filter By"
        "fr" -> "Filtrer par"
        "pt" -> "Filtrar por"
        "es" -> "Filtrar por"
        "id" -> "Filter"
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }
    private val statuses = when (lang) {
        "en" -> arrayOf("All", "Ongoing", "Completed")
        "fr" -> arrayOf("Tous", "Sérialisé", "Terminé")
        "pt" -> arrayOf("Todos", "Em série", "Concluído")
        "es" -> arrayOf("Todos", "En curso", "Terminado")
        "id" -> arrayOf("Semua", "Berlangsung", "Tamat")
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    private val sortTitle = when (lang) {
        "en" -> "Sort By"
        "fr" -> "Trier par"
        "pt" -> "Ordenar por"
        "es" -> "Ordenar por"
        "id" -> "Urutkan"
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }
    private val sorts = when (lang) {
        "en" -> arrayOf("Hottest", "Best-rated", "Newest")
        "fr" -> arrayOf("Top", "Mieux notés", "Nouveautés")
        "pt" -> arrayOf("Mais populares", "Mais avaliados", "Mais recentes")
        "es" -> arrayOf("Populares", "Mejor calificados", "Más nuevos")
        "id" -> arrayOf("Terpopuler", "Rating Tertinggi", "Terbaru")
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    private inner class GenreFilter : SelectFilter(genreTitle, genres)
    private inner class StatusFilter : SelectFilter(statusTitle, statuses)
    private inner class SortFilter : SelectFilter(sortTitle, sorts)

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filtering is ignored when searching by text."),
        Filter.Separator(),
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    // =============================== Utils ====================================

    private fun String.toPathSegment(): String = this
        .replace(PUNCTUATION_REGEX, "")
        .replace(WHITE_SPACE_REGEX, "-")
        .lowercase()

    companion object {
        val WHITE_SPACE_REGEX = """[\s]+""".toRegex()
        val PUNCTUATION_REGEX = "[\\p{Punct}]".toRegex()

        private const val UA_DB_URL = "https://keiyoushi.github.io/user-agents/user-agents.json"
    }
}
