package eu.kanade.tachiyomi.extension.all.webcomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getInt
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response

@Source
abstract class Webcomics : KeiSource() {

    private val apiUrl get() = "https://official-website-api.${baseUrl.toHttpUrl().host}/api/web/v4/book"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { !it.host.endsWith(IMAGE_DOMAIN) }

    // Language ids from the site's API client
    private val apiLang = when (lang) {
        "en" -> "0"
        "fr" -> "5"
        "pt" -> "6"
        "es" -> "7"
        "id" -> "1"
        else -> throw IllegalArgumentException("Invalid lang: $lang")
    }

    private val apiHeaders: Headers get() = headers.newBuilder().set("lang", apiLang).build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(lang)
            addPathSegment(genreTitle.toPathSegment())
            addPathSegment(genres.first().toPathSegment())
            addPathSegment(statuses.first().toPathSegment())
            addPathSegment(sorts.first().toPathSegment())
            addPathSegment(page.toString())
        }.build()

        return parseMangasPage(client.get(url, Fetch.DOCUMENT))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val nuxtData = response.asJsoup().selectFirst("script#__NUXT_DATA__")!!.data().parseAs<JsonArray>()
        val bookList = nuxtData.indexOfFirst { it is JsonObject && "list" in it && "pagination" in it }
        return nuxtData.unflatten(bookList).parseAs<BookListDto>().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(lang)
            addPathSegment(genreTitle.toPathSegment())
            addPathSegment(genres.first().toPathSegment())
            addPathSegment(statuses.first().toPathSegment())
            addPathSegment(sorts.last().toPathSegment())
            addPathSegment(page.toString())
        }.build()

        return parseMangasPage(client.get(url, Fetch.DOCUMENT))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.isNotBlank()) {
        val body = SearchBody(query, page, 20).toJsonRequestBody()
        client.post("$apiUrl/search", apiHeaders, Fetch.XHR, body).parseAs<DataWrapper<BookListDto>>().data.toMangasPage()
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
        parseMangasPage(client.get(url, Fetch.DOCUMENT))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // Series: /lang/slug/name/id, chapter: /lang/slug/name/index/id
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val isSeries = segments.size == 4
        val isChapter = segments.size == 5 && segments[3].toIntOrNull() != null
        if (url.host != baseUrl.toHttpUrl().host || segments.firstOrNull() != lang || !(isSeries || isChapter) || !ID_REGEX.matches(segments.last())) {
            return null
        }

        val manga = SManga.create().apply {
            this.url = segments.last()
            memo = buildJsonObject {
                put("slug", segments[1])
                put("name", segments[2])
            }
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment(lang)
        addPathSegment(manga.memo.getString("slug"))
        addPathSegment(manga.memo.getString("name"))
        addPathSegment(manga.url)
    }.build().toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment(lang)
        addPathSegment(chapter.memo.getString("slug"))
        addPathSegment(chapter.memo.getString("name"))
        addPathSegment(chapter.memo.getInt("index").toString())
        addPathSegment(chapter.memo.getString("mangaId"))
    }.build().toString()

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
        val body = BookBody(manga.url).toJsonRequestBody()

        val dto = client.post("$apiUrl/info", apiHeaders, Fetch.XHR, body).parseAs<DataWrapper<BookDto>>().data
        return SManga.create().apply {
            url = manga.url
            title = dto.name
            thumbnail_url = dto.cover
            description = dto.description
            author = dto.author
            genre = dto.category.joinToString()
            status = if (dto.status == statuses[1]) SManga.ONGOING else SManga.COMPLETED
            memo = manga.memo
        }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val mangaId = manga.url
        val body = ChapterListBody(mangaId, 1, 9999, "desc").toJsonRequestBody()

        val dto = client.post("$apiUrl/chapter/list", apiHeaders, Fetch.XHR, body).parseAs<DataWrapper<ChapterListDto>>().data
        return dto.list.map { chapter ->
            SChapter.create().apply {
                name = if (chapter.isPay) "🔒 ${chapter.name}" else chapter.name
                date_upload = chapter.updateTime
                chapter_number = chapter.index.toFloat()
                url = chapter.chapterId
                memo = buildJsonObject {
                    put("index", chapter.index)
                    put("mangaId", mangaId)
                    put("slug", manga.memo.getString("slug"))
                    put("name", manga.memo.getString("name"))
                }
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = ChapterDetailBody(chapter.memo.getString("mangaId"), chapter.url, chapter.memo.getInt("index")).toJsonRequestBody()

        var result = chapterDetail(body, apiHeaders)
        if (result.code == CODE_LOCKED) {
            // Login token from the site's localStorage
            val token = getLocalStorage(baseUrl, "tk")
            if (token != null) result = chapterDetail(body, apiHeaders.newBuilder().set("Token", token).build())
            if (result.code != CODE_OK) throw Exception("Log in and unlock this chapter in WebView")
        }
        val dto = result.data

        return dto.images.mapIndexed { index, img ->
            Page(index, imageUrl = dto.baseUrl + img.url)
        }
    }

    private suspend fun chapterDetail(body: RequestBody, headers: Headers) = client.post("$apiUrl/chapter/detail", headers, Fetch.XHR, body).parseAs<DataWrapper<ImageListDto>>()

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

    // Nuxt payload: every value is an index into the root array, negative ones are undefined
    private fun JsonArray.unflatten(index: Int): JsonElement = when (val value = getOrNull(index) ?: JsonNull) {
        is JsonObject -> JsonObject(value.mapValues { unflatten(it.value.jsonPrimitive.int) })
        is JsonArray -> JsonArray(value.map { unflatten(it.jsonPrimitive.int) })
        else -> value
    }

    private fun String.toPathSegment(): String = this
        .replace(PUNCTUATION_REGEX, "")
        .replace(WHITE_SPACE_REGEX, "-")
        .lowercase()

    companion object {
        private val WHITE_SPACE_REGEX = """\s+""".toRegex()
        private val PUNCTUATION_REGEX = """\p{Punct}""".toRegex()
        private val ID_REGEX = "[0-9a-f]{24}".toRegex()
        private const val IMAGE_DOMAIN = ".mangaina.com"

        private const val CODE_OK = 1000
        private const val CODE_LOCKED = 1208
    }
}
