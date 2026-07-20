package eu.kanade.tachiyomi.extension.en.webcomics

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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Webcomics : KeiSource() {

    private val apiUrl = "https://popeye.${baseUrl.substringAfterLast("/")}/api"

    override fun Headers.Builder.configureHeaders() = apply {
        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()
            if (request.isComicRequest()) {
                val ua = runBlocking(Dispatchers.IO) {
                    getDesktopUA()
                }

                val newHeaders = request.headers.newBuilder()
                    .set("User-Agent", ua.desktop.random())
                    .build()

                val newRequest = request.newBuilder()
                    .headers(newHeaders)
                    .build()
                return@addInterceptor chain.proceed(newRequest)
            }
            chain.proceed(request)
        }
        rateLimit(3)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private fun Request.isComicRequest(): Boolean = url.pathSegments.firstOrNull()?.let { it == "comic" || it == "view" } == true

    private var userAgentList: UserAgentList? = null

    private suspend fun getDesktopUA(): UserAgentList = userAgentList ?: network.client.get(UA_DB_URL).parseAs<UserAgentList>().also {
        userAgentList = it
    }

    // ========================== Popular =====================================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(
        client.get("$baseUrl/genres/All/All/Popular/$page"),
        page,
    )

    private fun parseMangasPage(response: Response, page: Int, key: String = "genresList"): MangasPage {
        val data = response.asJsoup().selectFirst("script:containsData(__NUXT__)")!!.data()
        val items = data.getNuxtJson(key)!!.parseAs<List<GenreListItem>>(json)

        val mangaList = items.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = it.cover
                url = "/comic/${it.name.toPathSegment()}/${it.mangaId}"
            }
        }

        val total = data.substringAfter("total:", "")
            .substringBefore(",")
            .toIntOrNull()

        val hasNextPage = if (total == null) {
            false
        } else {
            page * 10 < total
        }

        return MangasPage(mangaList, hasNextPage)
    }

    // ========================== Latest =====================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(
        client.get("$baseUrl/genres/All/All/Latest_Updated/$page"),
        page,
    )

    // ========================== Search =====================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.isNotBlank()) {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addPathSegment(query.toPathSegment())
            .build()
            .toString()
        parseMangasPage(client.get(url), page, key = "data")
    } else {
        val genre = filters.firstInstance<GenreFilter>().selected()
        val status = filters.firstInstance<StatusFilter>().selected()
        val sort = filters.firstInstance<SortFilter>().selected()
        val url = "$baseUrl/genres/$genre/$status/$sort/$page"
        parseMangasPage(client.get(url), page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "comic" || url.pathSegments.size < 3) {
            return null
        }

        val mangaUrl = "/comic/${url.pathSegments[1]}/${url.pathSegments[2]}"
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
        val document = client.get(baseUrl + manga.url).asJsoup()

        val infoElement = document.selectFirst(".card-info")!!

        return SManga.create().apply {
            title = infoElement.selectFirst("h5")!!.text()
            description = infoElement.selectFirst(".book-detail > p")?.text()
            genre = infoElement.select(".label-tag").joinToString { it.text() }
            thumbnail_url = infoElement.selectFirst("img")?.absUrl("src")
            document.selectFirst(".chapter-updateDetail")?.text()?.let {
                status = if (it.contains("IDK")) SManga.COMPLETED else SManga.ONGOING
            }
        }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val mangaId = manga.url.substringAfterLast("/")
        val dto = client.get("$apiUrl/chapter/list?manga_id=$mangaId").parseAs<ChapterWrapper>()

        val manga = dto.manga

        return dto.chapters.map { chapter ->
            SChapter.create().apply {
                name = if (chapter.is_pay) "🔒 ${chapter.name}" else chapter.name
                date_upload = chapter.update_time
                chapter_number = chapter.index.toFloat()

                val chapterUrl = "$baseUrl/view".toHttpUrl().newBuilder()
                    .addPathSegment(manga.name.replace(WHITE_SPACE_REGEX, "-"))
                    .addPathSegment(chapter.index.toString())
                    .addPathSegment("${manga.manga_id}-${chapter.name.toPathSegment()}")
                    .build()

                setUrlWithoutDomain(chapterUrl.toString())
            }
        }.sortedBy(SChapter::chapter_number).reversed()
    }

    // ========================== Pages ====================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        val data = document.selectFirst("script:containsData(__NUXT__)")
            ?.data()
            ?.getNuxtJson("pages")
            ?: throw Exception("You may need to log in")

        return data.parseAs<List<PageDto>>(json).mapIndexed { index, p ->
            Page(index, imageUrl = p.src)
        }
    }

    // ========================== Filters ==================================

    private open inner class SelectFilter(name: String, val items: Array<String>) : Filter.Select<String>(name, items) {
        fun selected() = items[state].toPathSegment()
    }

    private inner class GenreFilter :
        SelectFilter(
            "Genre",
            arrayOf(
                "All",
                "Romance",
                "Action",
                "Fantasy",
                "BL",
                "Eastern Fantasy",
                "Eastern Romance",
                "Drama",
                "GL",
                "LGBTQ+",
                "Slice of Life",
                "Comedy",
                "Horror",
                "Mystery",
                "Sci-Fi",
            ),
        )

    private inner class StatusFilter :
        SelectFilter(
            "Status",
            arrayOf(
                "All",
                "Ongoing",
                "Completed",
            ),
        )

    private inner class SortFilter :
        SelectFilter(
            "Sort By",
            arrayOf(
                "Popular",
                "Length",
                "Likes",
                "Latest Updated",
                "Newest",
            ),
        )

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filtering is ignored when searching by text."),
        Filter.Separator(),
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    // =============================== Utils ====================================

    private fun String.getNuxtJson(key: String): String? {
        val startIndex = this.indexOf("data")
        val keyIndex = this.indexOf(key, startIndex + 1)
        val start = this.indexOf('[', keyIndex)

        var depth = 1
        var i = start + 1

        while (i < this.length && depth > 0) {
            when (this[i]) {
                '[' -> depth++
                ']' -> depth--
            }
            i++
        }

        if (i == this.length) {
            return null
        }

        return this.substring(start, i)
    }

    private fun String.toPathSegment(): String = this
        .replace(PUNCTUATION_REGEX, "")
        .replace(WHITE_SPACE_REGEX, "-")

    companion object {
        val WHITE_SPACE_REGEX = """[\s]+""".toRegex()
        val PUNCTUATION_REGEX = "[\\p{Punct}]".toRegex()

        private const val UA_DB_URL = "https://keiyoushi.github.io/user-agents/user-agents.json"
    }
}
