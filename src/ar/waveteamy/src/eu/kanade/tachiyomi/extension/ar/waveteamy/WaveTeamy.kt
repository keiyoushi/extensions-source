package eu.kanade.tachiyomi.extension.ar.waveteamy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64

class WaveTeamy : HttpSource() {
    override val name = "WaveTeamy"
    override val baseUrl = "https://waveteamy.com"
    override val lang = "ar"

    private val cloudUrl = "https://wcloud.site"

    private val pageLimit = 40
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    private val oldDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)

    override val client =
        network.cloudflareClient
            .newBuilder()
            .rateLimit(10, 1, TimeUnit.SECONDS)
            .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    // Popular
    override fun popularMangaRequest(page: Int) = POST(
        "$baseUrl/wapi/hanout/v1/series/series-list",
        headers,
        FormBody
            .Builder()
            .add("page", page.toString())
            .add("limit", pageLimit.toString())
            .build(),
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<List<WManga>>()
        val mangas = dto.map { it.toSManga() }
        return MangasPage(mangas, mangas.size == pageLimit)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = POST(
        "$baseUrl/wapi/hanout/v1/series/releases-web",
        headers,
        FormBody
            .Builder()
            .add("page", page.toString())
            .add("limit", pageLimit.toString())
            .build(),
    )

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<WLatestManga>()
        val mangas = dto.chapters.map { it.toSManga() }
        return MangasPage(mangas, !dto.isLastPage)
    }

    // Search
    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ) = POST(
        "$baseUrl/wapi/hanout/v1/series/series-list",
        headers,
        FormBody
            .Builder()
            .add("page", page.toString())
            .add("keyUpValue", query)
            .add("limit", pageLimit.toString())
            .build(),
    )

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Manga Details
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val mangaData = response.extractNextJs<WMangaDetails>()!!
        title = mangaData.name
        thumbnail_url = mangaData.cover.toImage()
        description = mangaData.story?.replace("\\n", "\n")
        genre = (mangaData.genre + mangaData.type)
            .filterNot { it.isNullOrBlank() }
            .joinToString(", ")
        status = mangaData.status.toStatus()
        artist = mangaData.artist.takeIf { it != "Updating" }
        author = mangaData.author.takeIf { it != "Updating" }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.extractNextJs<List<WChapterList>>()
            ?.toMutableList()
            ?: return emptyList()

        val workId = response.request.url.pathSegments[1]
        var page = 2

        while (true) {
            val request = POST(
                "$baseUrl/wapi/hanout/v1/series/chapters/get",
                headers,
                FormBody.Builder()
                    .add("workId", workId)
                    .add("limit", pageLimit.toString())
                    .add("page", (page++).toString())
                    .build(),
            )
            val nextChapters = client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                res.parseAs<WChapters>()
            }

            chapters.addAll(nextChapters.chapters)

            if (nextChapters.chapters.size < pageLimit || !nextChapters.success) break
        }

        return chapters.map { chapter ->
            SChapter.create().apply {
                url = "/series/$workId/${chapter.chapter}"
                name = buildString {
                    append("الفصل ${chapter.chapter.toString().removeSuffix(".0")}")
                    chapter.title?.let {
                        append(" - $it")
                    }
                }
                date_upload = dateFormat.tryParse(chapter.postTime)
                    .takeIf { it != 0L }
                    ?: oldDateFormat.tryParse(chapter.postTime)
            }
        }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val basePages = response.extractNextJs<WPage>()!!

        val pages: List<WImagePayload> = basePages.images.map { encoded ->
            val decodedJson = Base64.decode(encoded.substringBefore(".")).decodeToString()
            decodedJson.parseAs<WImagePayload>()
        }

        return pages.mapIndexed { index, image ->
            Page(index, "", image.url.toImage())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Int?.toStatus() = when (this) {
        0 -> SManga.ONGOING
        1 -> SManga.COMPLETED
        2 -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    fun String.toImage(): String {
        val t = this.replace(" ", "%20")
        return when {
            this.startsWith("http") -> t
            this.startsWith("projects") ||
                this.startsWith("series") ||
                this.startsWith("users") -> "$cloudUrl/$t"
            else ->
                "$baseUrl/$t"
        }
    }

    private fun WManga.toSManga(): SManga = SManga.create().apply {
        url = "/series/$postId"
        title = this@toSManga.title
        thumbnail_url = imageUrl.toImage()
    }

    @Serializable
    class WManga(
        val postId: Long,
        val title: String,
        val imageUrl: String,
    )

    @Serializable
    class WLatestManga(
        val chapters: List<WManga>,
        val isLastPage: Boolean,
    )

    @Serializable
    class WMangaDetails(
        val name: String,
        val cover: String,
        val story: String?,
        val status: Int?,
        val type: String?,
        val genre: List<String>,
        val artist: String?,
        val author: String?,
    )

    @Serializable
    class WChapters(
        val chapters: List<WChapterList>,
        val success: Boolean,
    )

    @Serializable
    class WChapterList(
        val title: String?,
        val chapter: Double,
        val postTime: String?,
    )

    @Serializable
    class WPage(
        val images: List<String>,
    )

    @Serializable
    class WImagePayload(
        @SerialName("p")
        val url: String,
    )
}
