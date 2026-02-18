package eu.kanade.tachiyomi.extension.en.mangacloud

import android.app.Application
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
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

    override fun popularMangaRequest(page: Int): Request {
        val time = when (page) {
            1 -> "today"
            2 -> "week"
            3 -> "month"
            else -> throw Exception()
        }

        return GET("$API_URL/comic-popular-view/$time", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Data<DataList<BrowseManga>>>()

        val mangas = data.data.list.map(BrowseManga::toSManga)
        val hasNextPage = response.request.url.pathSegments.last() != "month"

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$API_URL/comic-updates"
        val payload = PagePayload(page)
            .toJsonString()
            .toRequestBody(jsonMediaType)

        return POST(url, headers, payload)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<Data<DataList<BrowseManga>>>()

        val mangas = data.data.list.map(BrowseManga::toSManga)
        val hasNextPage = data.data.list.size == 60

        return MangasPage(mangas, hasNextPage)
    }

    private fun fetchCachedTags(): List<Tag> {
        val cacheDir = Injekt.get<Application>()
            .cacheDir
            .resolve("${name}_${id}_tmp")
            .also { it.mkdirs() }

        val tagsFile = cacheDir.resolve("tags.json")

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
    private val fetchingOnlineTagsFailed = AtomicBoolean(false)

    private fun fetchOnlineTags(tagsFile: File) {
        if (!fetchingOnlineTags.compareAndSet(false, true)) return
        if (fetchingOnlineTagsFailed.get()) return

        val request = GET("$API_URL/tag/list", headers)

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
                        } else {
                            fetchingOnlineTagsFailed.set(true)
                        }
                    } catch (_: Exception) {
                        fetchingOnlineTagsFailed.set(true)
                    } finally {
                        fetchingOnlineTags.set(false)
                    }
                }
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(name, "Failed to fetch tags", e)
                    fetchingOnlineTags.set(false)
                    fetchingOnlineTagsFailed.set(true)
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
            val message = if (fetchingOnlineTagsFailed.get()) {
                "Failed to fetch tags"
            } else {
                "Press `refresh` to fetch tags"
            }

            filters.addAll(
                listOf(
                    Filter.Separator(),
                    Filter.Header(message),
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

        val payload = SearchPayload(
            title = query.takeIf(String::isNotBlank),
            type = filters.firstInstance<TypeFilter>().selected,
            sort = filters.firstInstance<SortFilter>().selected,
            status = filters.firstInstance<StatusFilter>().selected,
            includes = filters.firstInstanceOrNull<TriStateGroupFilter>()?.included.orEmpty(),
            excludes = filters.firstInstanceOrNull<TriStateGroupFilter>()?.excluded.orEmpty(),
            page = page,
        )
            .toJsonString()
            .toRequestBody(jsonMediaType)

        return POST(url, headers, payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Data<List<BrowseManga>>>()

        val mangas = data.data.map(BrowseManga::toSManga)
        val hasNextPage = data.data.size == 10

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$API_URL/comic/${manga.url}"

        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<Data<Manga>>().data.toSManga()

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Data<Manga>>()

        return data.data.chapters.map { chapter ->
            SChapter.create().apply {
                url = ChapterUrl(data.data.id, chapter.id).toJsonString()
                name = "Chapter ${chapter.number}".substringBefore(".0")
                date_upload = chapter.date
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.parseAs<ChapterUrl>().chapterId

        return GET("$API_URL/chapter/$chapterId", headers)
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

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private val jsonMediaType = "application/json".toMediaType()
