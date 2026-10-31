package eu.kanade.tachiyomi.extension.en.mangacloud

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

const val DOMAIN = "mangacloud.org"
const val API_URL = "https://api.$DOMAIN"
const val CDN_URL = "https://pika.$DOMAIN"

@Source
abstract class MangaCloud : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1)

    private val handshakeMutex = Mutex()

    @Volatile
    private var lastHandshake = 0L

    // every api call returns 409 until a Turnstile token is posted to /auth/handshake, which the site does on load
    private suspend fun handshake(attemptedAt: Long) = handshakeMutex.withLock {
        // another request may have already refreshed the session while we waited for the lock
        if (lastHandshake > attemptedAt) return@withLock

        val token = getTurnstileToken(
            url = baseUrl,
            siteKey = TURNSTILE_SITE_KEY,
            action = "guard",
        )
        client.post("$API_URL/auth/handshake", body = HandshakePayload(token).toJsonRequestBody())
        lastHandshake = System.currentTimeMillis()
    }

    private suspend fun <T> withHandshake(block: suspend () -> T): T {
        if (lastHandshake == 0L) handshake(0L)

        return try {
            block()
        } catch (e: HttpException) {
            if (e.code != 409) throw e
            handshake(System.currentTimeMillis())
            block()
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 3) {
            return getSearchMangaList(page - 3, "", FilterList())
        }

        val time = when (page) {
            1 -> "today"
            2 -> "week"
            else -> "month"
        }

        val data = withHandshake {
            client.get("$API_URL/comic-popular-view/$time").parseAs<Data<DataList<BrowseManga>>>()
        }

        val mangas = data.data.list.map(BrowseManga::toSManga)

        return MangasPage(mangas, true)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val payload = PagePayload(page).toJsonRequestBody()

        val data = withHandshake {
            client.post("$API_URL/comic-updates", body = payload).parseAs<Data<DataList<BrowseManga>>>()
        }

        val mangas = data.data.list.map(BrowseManga::toSManga)
        val hasNextPage = data.data.list.size == 60

        return MangasPage(mangas, hasNextPage)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = withHandshake {
        client.get("$API_URL/tag/list").parseAs<Data<List<Tag>>>().data.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            TypeFilter(),
            StatusFilter(),
            SortFilter(),
        )

        val tags = data?.parseAs<List<Tag>>()

        if (tags != null) {
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        ).toJsonRequestBody()

        val data = withHandshake {
            client.post("$API_URL/comic/library", body = payload).parseAs<Data<List<BrowseManga>>>()
        }

        val mangas = data.data.map(BrowseManga::toSManga)
        val hasNextPage = data.data.size == 10

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.pathSegments

        if (url.host != DOMAIN || path[0] != "comic" || path.size <= 1) {
            return null
        }

        return withHandshake {
            client.get("$API_URL/comic/${path[1]}").parseAs<Data<Manga>>().data.toSManga()
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = withHandshake {
            client.get("$API_URL/comic/${manga.url}").parseAs<Data<Manga>>().data
        }

        val updatedChapters = data.chapters.map { chapter ->
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

        return SMangaUpdate(data.toSManga(), updatedChapters)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = chapter.url.parseAs<ChapterUrl>()

        return "$baseUrl/comic/${chapterUrl.comicId}/chapter/${chapterUrl.chapterId}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.parseAs<ChapterUrl>().chapterId

        val data = withHandshake {
            client.get("$API_URL/chapters/$chapterId").parseAs<Data<ChapterContent>>().data
        }

        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = "$CDN_URL/${data.comicId}/${data.id}/${img.id}.${img.format}")
        }
    }
}

private const val TURNSTILE_SITE_KEY = "0x4AAAAAADLVIGf7KBRQctG1"
