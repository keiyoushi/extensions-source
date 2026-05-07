package eu.kanade.tachiyomi.extension.en.mangacloud

import android.app.Application
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.lang.UnsupportedOperationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.hours

const val DOMAIN = "mangacloud.org"
const val API_URL = "https://api.$DOMAIN"
const val CDN_URL = "https://pika.$DOMAIN"

class MangaCloud : HttpSource() {
    override val name = "MangaCloud"
    override val lang = "en"

    override val baseUrl = "https://$DOMAIN"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Key", generateKey())

    override fun popularMangaRequest(page: Int): Request {
        return if (page > 3) {
            searchMangaRequest(page - 3, "", FilterList())
        } else {
            val time = when (page) {
                1 -> "today"
                2 -> "week"
                else -> "month"
            }

            return GET("$API_URL/comic-popular-view/$time", headersBuilder().build())
        }
    }

    override fun popularMangaParse(response: Response): MangasPage = if (response.request.url.pathSegments.last() == "browse") {
        searchMangaParse(response)
    } else {
        val data = response.parseAs<Data<DataList<BrowseManga>>>()

        val mangas = data.data.list.map(BrowseManga::toSManga)

        MangasPage(mangas, true)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$API_URL/comic-updates"
        val payload = PagePayload(page)
            .toJsonString()
            .toRequestBody(jsonMediaType)

        return POST(url, headersBuilder().build(), payload)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<Data<DataList<BrowseManga>>>()

        val mangas = data.data.list.map(BrowseManga::toSManga)
        val hasNextPage = data.data.list.size == 60

        return MangasPage(mangas, hasNextPage)
    }

    private fun fetchCachedTags(): List<Tag> {
        val tagsFile = Injekt.get<Application>()
            .cacheDir
            .resolve("${name}_${id}_tmp")
            .also { it.mkdirs() }
            .resolve("tags.json")

        if (tagsFile.exists()) {
            val tags = tagsFile.readText().parseAs<Data<List<Tag>>>().data

            // refresh if older than 12 hours
            val expiry = System.currentTimeMillis() - 12.hours.inWholeMilliseconds
            if (tagsFile.lastModified() < expiry) {
                fetchOnlineTags(tagsFile)
            }

            return tags
        } else {
            fetchOnlineTags(tagsFile)

            return emptyList()
        }
    }

    private val fetchingOnlineTags = AtomicBoolean(false)

    private fun fetchOnlineTags(tagsFile: File) {
        if (!fetchingOnlineTags.compareAndSet(false, true)) return

        val request = GET("$API_URL/tag/list", headersBuilder().build())

        client.newCall(request).enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            val tmpFile = File(tagsFile.absolutePath + ".tmp")
                            response.body.byteStream().use { input ->
                                tmpFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            tmpFile.renameTo(tagsFile)
                        }
                    } finally {
                        fetchingOnlineTags.set(false)
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e(name, "Failed to fetch tags", e)
                    fetchingOnlineTags.set(false)
                }
            },
        )
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            TypeFilter(),
            StatusFilter(),
            SortFilter(),
        )

        val tags = fetchCachedTags()

        if (tags.isEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    Filter.Header("Press 'reset' to fetch tags"),
                ),
            )
        } else {
            val genre = TriStateGroupFilter(
                name = "Genre",
                options = tags.filter { it.type == "genre" }
                    .map { it.name to it.id },
            )
            val theme = TriStateGroupFilter(
                name = "Theme",
                options = tags.filter { it.type == "theme" }
                    .map { it.name to it.id },
            )
            val format = TriStateGroupFilter(
                name = "Format",
                options = tags.filter { it.type == "format" }
                    .map { it.name to it.id },
            )

            filters.addAll(listOf(genre, theme, format))
        }

        return FilterList(filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$API_URL/comic/browse"

        if (query.isNotBlank() && query.length < 3) {
            throw Exception("Search query must be more than 3 characters!")
        }

        val payload = SearchPayload(
            title = query.takeIf(String::isNotBlank),
            type = filters.firstInstanceOrNull<TypeFilter>()?.selected,
            sort = filters.firstInstanceOrNull<SortFilter>()?.selected,
            status = filters.firstInstanceOrNull<StatusFilter>()?.selected,
            includes = filters.filterIsInstance<TriStateGroupFilter>().flatMap { it.included },
            excludes = filters.filterIsInstance<TriStateGroupFilter>().flatMap { it.excluded },
            page = page,
        )
            .toJsonString()
            .toRequestBody(jsonMediaType)

        return POST(url, headersBuilder().build(), payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Data<List<BrowseManga>>>()

        val mangas = data.data.map(BrowseManga::toSManga)
        val hasNextPage = data.data.size == 10

        return MangasPage(mangas, hasNextPage)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            val path = url.pathSegments

            if (url.host == DOMAIN && path[0] == "comic" && path.size > 1) {
                val comicId = path[1]

                return client.newCall(mangaDetailsRequest(comicId))
                    .asObservableSuccess()
                    .map(::mangaDetailsParse)
                    .map { MangasPage(listOf(it), false) }
            } else {
                throw Exception("Unsupported Url")
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun mangaDetailsRequest(manga: SManga) = mangaDetailsRequest(manga.url)

    private fun mangaDetailsRequest(comicId: String) = GET("$API_URL/comic/$comicId", headersBuilder().build())

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override fun mangaDetailsParse(response: Response) = response.parseAs<Data<Manga>>().data.toSManga()

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Data<Manga>>().data

        return data.chapters.map { chapter ->
            SChapter.create().apply {
                url = ChapterUrl(data.id, chapter.id).toJsonString()
                name = buildString {
                    append("Chapter ")
                    append(chapter.number.toString().substringBefore(".0"))
                    chapter.name?.also {
                        append(" - ")
                        append(it)
                    }
                }
                chapter_number = chapter.number
                date_upload = chapter.date
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.parseAs<ChapterUrl>().chapterId

        return GET("$API_URL/chapter/$chapterId", headersBuilder().build())
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (comicId, chapterId) = chapter.url.parseAs<ChapterUrl>()

        return "$baseUrl/comic/$comicId/chapter/$chapterId"
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<Data<ChapterContent>>().data

        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = "$CDN_URL/${data.comicId}/${data.id}/${img.id}.${img.format}")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun generateKey(): String {
        val timestamp = (System.currentTimeMillis() / 1000).toString().reversed()
        return timestamp.map { "${(0..9).random()}$it" }.joinToString("")
    }
}

private val jsonMediaType = "application/json".toMediaType()
