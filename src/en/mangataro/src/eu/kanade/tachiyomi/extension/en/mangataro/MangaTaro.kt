package eu.kanade.tachiyomi.extension.en.mangataro

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import rx.Observable
import java.lang.UnsupportedOperationException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MangaTaro : HttpSource() {

    override val name = "MangaTaro"

    override val baseUrl = "https://mangataro.org"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response) =
        searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) =
        searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response) =
        searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith("https://")) {
            deeplinkHandler(query)
        } else if (
            query.isNotBlank() &&
            filters.firstInstanceOrNull<SearchWithFilters>()?.state == false
        ) {
            querySearch(query)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun querySearch(query: String): Observable<MangasPage> {
        val body = SearchQueryPayload(
            query = query.trim(),
            limit = 25,
        ).toJsonString().toRequestBody("application/json".toMediaType())

        return client.newCall(POST("$baseUrl/auth/search", headers, body))
            .asObservableSuccess()
            .map { response ->
                val data = response.parseAs<SearchQueryResponse>().results

                val mangas = data.filter { it.type != "Novel" }
                    .map {
                        SManga.create().apply {
                            url = MangaUrl(it.id.toString(), it.slug).toJsonString()
                            title = it.title
                            thumbnail_url = it.thumbnail
                            description = it.description
                            status = when (it.status) {
                                "Ongoing" -> SManga.ONGOING
                                "Completed" -> SManga.COMPLETED
                                else -> SManga.UNKNOWN
                            }
                        }
                    }

                MangasPage(
                    mangas = mangas,
                    hasNextPage = false,
                )
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = SearchPayload(
            page = page,
            search = query.trim(),
            years = filters.firstInstanceOrNull<YearFilter>()
                ?.selected.let(::listOfNotNull),
            genres = filters.firstInstanceOrNull<TagFilter>()
                ?.checked.orEmpty(),
            types = filters.firstInstanceOrNull<TypeFilter>()
                ?.selected.let(::listOfNotNull),
            statuses = filters.firstInstanceOrNull<StatusFilter>()
                ?.selected.let(::listOfNotNull),
            sort = filters.firstInstance<SortFilter>().selected,
            genreMatchMode = filters.firstInstance<TagFilterMatch>().selected,
        ).toJsonString().toRequestBody("application/json".toMediaType())

        return POST("$baseUrl/wp-json/manga/v1/load", headers, body)
    }

    override fun getFilterList() = FilterList(
        SearchWithFilters(),
        Filter.Header("If unchecked, all filters will be ignored with search query"),
        Filter.Header("But will give more relevant results"),
        Filter.Separator(),
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        YearFilter(),
        TagFilter(),
        TagFilterMatch(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<List<BrowseManga>>()

        val mangas = data.filter { it.type != "Novel" }
            .map {
                SManga.create().apply {
                    url = MangaUrl(id = it.id, slug = it.url.toSlug()).toJsonString()
                    title = it.title
                    thumbnail_url = it.cover
                    description = it.description
                    status = when (it.status) {
                        "Ongoing" -> SManga.ONGOING
                        "Completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }

        return MangasPage(
            mangas = mangas,
            hasNextPage = data.size == 24,
        )
    }

    private fun deeplinkHandler(url: String): Observable<MangasPage> {
        val slug = url.toSlug()

        return client.newCall(GET("$baseUrl/manga/$slug", headers))
            .asObservableSuccess()
            .map {
                val document = it.asJsoup()

                val id = document.body().dataset()["manga-id"]!!
                val status = when (document.selectFirst(".manga-page-wrapper span.capitalize")?.text()?.lowercase()) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }

                if (document.selectFirst(".manga-page-wrapper span:contains(Novel)") != null) {
                    throw Exception("Novels are not supported")
                }

                id to status
            }
            .switchMap {
                client.newCall(mangaDetailsRequest(it.first, it.second))
                    .asObservableSuccess()
                    .map(::mangaDetailsParse)
            }
            .map { MangasPage(listOf(it), false) }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.parseAs<MangaUrl>().id

        return mangaDetailsRequest(id, manga.status)
    }

    private fun mangaDetailsRequest(id: String, status: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("wp-json/wp/v2/manga")
            addPathSegment(id)
            addQueryParameter("_embed", null)
            fragment(status.toString())
        }.build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaDetails>()

        return SManga.create().apply {
            url = MangaUrl(data.id.toString(), data.slug).toJsonString()
            title = Parser.unescapeEntities(data.title.rendered, false)
            description = Jsoup.parseBodyFragment(data.content.rendered).wholeText()
            genre = buildSet {
                addAll(data.embedded.getTerms("post_tag"))
                if (listOf("Manhwa", "Manhua", "Manga").none { it -> this.contains(it) }) {
                    add(data.type)
                }
            }.joinToString()
            author = data.embedded.getTerms("manga_author").joinToString()
            status = response.request.url.fragment!!.toInt()
            thumbnail_url = data.embedded.featuredMedia.firstOrNull()?.url
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.parseAs<MangaUrl>().slug

        return "$baseUrl/manga/$slug"
    }

    override fun chapterListRequest(manga: SManga): Request {
        val timestamp = System.currentTimeMillis() / 1000
        val token = md5(
            "${timestamp}mng_ch_${isoDateFormatter.format(Date())}",
        ).substring(0, 16)
        val mangaId = manga.url.parseAs<MangaUrl>().id

        val url = "$baseUrl/auth/manga-chapters".toHttpUrl().newBuilder().apply {
            addQueryParameter("manga_id", mangaId)
            addQueryParameter("offset", "0")
            addQueryParameter("limit", "9999")
            addQueryParameter("order", "DESC")
            addQueryParameter("_t", token)
            addQueryParameter("_ts", timestamp.toString())
        }.build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        countViews(response.request.url.queryParameter("manga_id")!!)

        val data = response.parseAs<ChapterList>()

        val placeholders = listOf(null, "", "N/A", "—")
        var hasScanlator = false

        // currently there is only English chapters on the site, at least from a quick look.
        // if they ever have multiple languages, we would need to make this a source factory
        val chapters = data.chapters.filter {
            it.language == "en"
        }.map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.url)
                name = buildString {
                    append("Chapter ")
                    append(it.chapter)
                    it.title.also { title ->
                        if (title !in placeholders) {
                            append(": ", title)
                        }
                    }
                }
                it.groupName.let { group ->
                    if (group !in placeholders) {
                        scanlator = group
                        hasScanlator = true
                    }
                }
                date_upload = it.date.parseRelativeDate()
            }
        }

        if (hasScanlator) {
            chapters.onEach { it.scanlator = it.scanlator ?: "\u200B" } // Insert zero-width space
        }

        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("img.comic-image").mapIndexed { idx, img ->
            val imageUrl = when {
                img.hasAttr("data-src") -> img.absUrl("data-src")
                else -> img.absUrl("src")
            }
            Page(idx, imageUrl = imageUrl)
        }
    }

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private val isoDateFormatter = SimpleDateFormat("yyyyMMddHH", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun String.toSlug() = toHttpUrl().let { url ->
        val path = url.pathSegments.filter(String::isNotBlank)

        if ((path.size == 2 && path[0] == "manga") || (path.size == 3 && path[0] == "read")) {
            path[1]
        } else {
            throw Exception("Expected manga or read path, got $this")
        }
    }

    private fun String.parseRelativeDate(): Long {
        val calendar = Calendar.getInstance()
        val (amount, unit) = relativeDateRegex.matchEntire(this)?.destructured
            ?: return 0L

        when (unit) {
            "second" -> calendar.add(Calendar.SECOND, -amount.toInt())
            "minute" -> calendar.add(Calendar.MINUTE, -amount.toInt())
            "hour" -> calendar.add(Calendar.HOUR, -amount.toInt())
            "day" -> calendar.add(Calendar.DAY_OF_YEAR, -amount.toInt())
            "week" -> calendar.add(Calendar.WEEK_OF_YEAR, -amount.toInt())
            "month" -> calendar.add(Calendar.MONTH, -amount.toInt())
            "year" -> calendar.add(Calendar.YEAR, -amount.toInt())
        }

        return calendar.timeInMillis
    }

    private val relativeDateRegex = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""")

    private fun countViews(postId: String) {
        val payload = """{"post_id":"$postId"}"""
            .toRequestBody("application/json".toMediaType())
        val url = "$baseUrl/wp-json/pviews/v1/increment/"
        val request = POST(url, headers, payload)

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onResponse(call: okhttp3.Call, response: Response) {
                        response.closeQuietly()
                    }
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        Log.e(name, "Failed to count views", e)
                    }
                },
            )
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}
