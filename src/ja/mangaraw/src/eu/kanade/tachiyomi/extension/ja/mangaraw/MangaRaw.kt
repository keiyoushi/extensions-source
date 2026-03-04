package eu.kanade.tachiyomi.extension.ja.mangaraw

import android.graphics.BitmapFactory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import rx.Observable
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MangaRaw :
    HttpSource(),
    ConfigurableSource {

    override val name = "Manga Raw Page"
    override val baseUrl = "https://mangaraw.page"
    override val lang = "ja"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override val client = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .rateLimit(2)
        .addNetworkInterceptor(ImageUnscramblerInterceptor())
        .build()

    private val nextBuildId = "MangaRaw02"

    private fun getMangasJsonUrl(page: Int): String = "$baseUrl/mangas_$page.json"

    private fun getMangaDetailsApiUrl(slug: String): String = "$baseUrl/_next/data/$nextBuildId/manga/$slug.json"

    private fun getChapterApiUrl(chapterPath: String): String = "$baseUrl/_next/data/$nextBuildId/manga/$chapterPath.json"

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept", "application/json")
        .set("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
        .set("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.7632.122 Mobile Safari/537.36")
        .set("sec-ch-ua", "\"Chromium\";v=\"145\", \"Not_A Brand\";v=\"99\"")
        .set("sec-ch-ua-mobile", "?1")
        .set("sec-ch-ua-platform", "\"Android\"")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(getMangasJsonUrl(page), headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListResponse>()
        val hideNsfw = hideNsfwPreference()

        val mangas = data.list
            .asSequence()
            .filterNot { it.isAdult() && hideNsfw }
            .filter { it.hasNavigableMangaSlug() }
            .distinctBy { it.getSlugKey() }
            .distinctBy { it.getTitleKey() }
            .map { it.toSMangaWithInfo() }
            .toList()

        if (mangas.isEmpty() && hideNsfw) {
            throw Exception("All results filtered out due to nsfw filter")
        }

        val hasNextPage = try {
            val currentUrl = response.request.url.toString()
            val currentPage = currentUrl.substringAfter("mangas_").substringBefore(".json").toIntOrNull() ?: 1
            val nextPageUrl = getMangasJsonUrl(currentPage + 1)
            client.newCall(GET(nextPageUrl, headers)).execute().use { nextResponse ->
                nextResponse.isSuccessful
            }
        } catch (e: Exception) {
            false
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.isNotEmpty()) {
        fetchAllMangasAndFilter(query, page)
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    private fun fetchAllMangasAndFilter(query: String, requestedPage: Int): Observable<MangasPage> = Observable.create { subscriber ->
        try {
            val allMangas = mutableListOf<MangaListItem>()
            var currentPage = 1
            var hasMorePages = true
            val hideNsfw = hideNsfwPreference()
            var foundEnough = false

            while (hasMorePages && currentPage <= 50 && !foundEnough) {
                val url = getMangasJsonUrl(currentPage)
                val response = client.newCall(GET(url, headers)).execute()

                if (!response.isSuccessful) {
                    hasMorePages = false
                } else {
                    val pageData = response.parseAs<MangaListResponse>()
                    if (pageData.list.isEmpty()) {
                        hasMorePages = false
                    } else {
                        allMangas.addAll(pageData.list)
                        if (allMangas.size > requestedPage * 40) {
                            foundEnough = true
                        }
                        currentPage++
                    }
                }
            }

            val matchedMangas = allMangas
                .asSequence()
                .filter { manga ->
                    val matchesQuery = manga.matchesQuery(query)
                    val passesNsfwFilter = !hideNsfw || !manga.isAdult()
                    matchesQuery && passesNsfwFilter
                }
                .filter { it.hasNavigableMangaSlug() }
                .distinctBy { it.getSlugKey() }
                .groupBy { it.getTitleKey() }
                .values
                .mapNotNull { itemsWithSameTitle ->
                    itemsWithSameTitle.maxByOrNull { it.getViews() }
                }
                .sortedByDescending { it.getViews() }
                .toList()

            val filteredMangas = matchedMangas
                .drop((requestedPage - 1) * 20)
                .take(20)
                .map { it.toSManga() }

            val hasNextPage = matchedMangas.size > requestedPage * 20
            subscriber.onNext(MangasPage(filteredMangas, hasNextPage))
            subscriber.onCompleted()
        } catch (e: Exception) {
            subscriber.onError(e)
        }
    }

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())
    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList())
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaDetailsApiUrl(manga.url), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaDetailsResponse>()
        return data.pageProps.data.manga.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaDetailsApiUrl(manga.url), headers)

    override fun chapterListParse(response: Response) = response.parseAs<MangaDetailsResponse>()
        .pageProps.data.manga.chapters
        .map { it.toSChapter() }
        .sortedByDescending { it.chapter_number }

    override fun pageListRequest(chapter: eu.kanade.tachiyomi.source.model.SChapter): Request = GET(getChapterApiUrl(chapter.url), headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterResponse>()
        val chapterDetails = data.pageProps.data.chapter
        val contentChapterId = chapterDetails.getChapterId()
        val seedChapterId = contentChapterId.toString()
        val chapterUuid = chapterDetails.getChapterUuid()

        val images = chapterDetails.getPages()
        if (images.isEmpty()) return emptyList()

        ChapterContextStore.putContext(
            contentChapterId = contentChapterId,
            seedChapterId = seedChapterId,
            isScrambled = chapterDetails.isScrambled(),
            chapterUuid = chapterUuid,
        )

        return images.sortedBy { it.order }.map { image ->
            val orderPadded = image.order.toString().padStart(3, '0')
            val imageUrl = "https://lh2.rawcontent.top/c$contentChapterId/${orderPadded}_${image.id}.webp"
            Page(image.order - 1, imageUrl = imageUrl)
        }
    }
    private fun hideNsfwPreference(): Boolean = preferences.getBoolean("hide_nsfw", true)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val hideNsfwPref = SwitchPreferenceCompat(screen.context).apply {
            key = "hide_nsfw"
            title = "Hide adult content"
            summary = "Filter out manga marked as adult"
            setDefaultValue(true)
        }
        screen.addPreference(hideNsfwPref)
    }
}

object ChapterContextStore {
    private val contextMap = ConcurrentHashMap<Long, ChapterContext>()
    private const val MAX_CONTEXT_SIZE = 100

    fun putContext(contentChapterId: Long, seedChapterId: String, isScrambled: Boolean, chapterUuid: String) {
        if (contextMap.size >= MAX_CONTEXT_SIZE && !contextMap.containsKey(contentChapterId)) {
            val oldestKey = contextMap.keys.minOrNull()
            if (oldestKey != null) {
                contextMap.remove(oldestKey)
            }
        }
        contextMap[contentChapterId] = ChapterContext(
            contentChapterId = contentChapterId,
            seedChapterId = seedChapterId,
            isScrambled = isScrambled,
            chapterUuid = chapterUuid,
        )
    }

    fun getContext(contentChapterId: Long): ChapterContext? = contextMap[contentChapterId]

    fun removeContext(contentChapterId: Long) {
        contextMap.remove(contentChapterId)
    }
}

data class ChapterContext(
    val contentChapterId: Long,
    val seedChapterId: String,
    val isScrambled: Boolean,
    val chapterUuid: String,
)

private fun extractContentChapterId(urlPath: String): Long? {
    val first = urlPath.trimStart('/').substringBefore('/')
    if (!first.startsWith("c") || first.length == 1) return null
    return first.substring(1).toLongOrNull()
}

class ImageUnscramblerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful || !request.url.host.contains("rawcontent.top")) {
            return response
        }

        val contentChapterId = extractContentChapterId(request.url.encodedPath) ?: return response
        val context = ChapterContextStore.getContext(contentChapterId) ?: return response

        if (!context.isScrambled || context.chapterUuid.isBlank()) {
            return response
        }

        try {
            val contentType = response.body?.contentType()
            val imageBytes = response.body?.bytes() ?: return response

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return response

            val unscrambled = ImageUnscrambler.unscrambleImage(
                bitmap,
                context.seedChapterId,
                context.chapterUuid,
            )

            val outputStream = ByteArrayOutputStream(imageBytes.size)
            unscrambled.compress(android.graphics.Bitmap.CompressFormat.WEBP, 95, outputStream)
            val unscrambledBytes = outputStream.toByteArray()

            return response.newBuilder()
                .body(ResponseBody.create(contentType ?: "image/webp".toMediaType(), unscrambledBytes))
                .build()
        } catch (e: Exception) {
            return response
        }
    }
}
