package eu.kanade.tachiyomi.extension.en.opscans

import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

class OpScans : HttpSource() {

    override val name = "OPSCANS"

    override val lang = "en"

    override val baseUrl = "https://opchapters.com"

    private val apiUrl = "https://opscanlations.com"

    override val supportsLatest = false

    private val json: Json by injectLazy()

    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageInterceptor)
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/api/mangaData", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaData = response.parseAs<List<MangaData>>()

        return MangasPage(
            mangaData.map { it.toSManga() },
            false,
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$apiUrl/api/mangaData#${query.trim()}", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangaData = response.parseAs<List<MangaData>>()
        val query = response.request.url.fragment!!

        return MangasPage(
            mangaData.filter {
                it.name.contains(query, true) ||
                    it.author.contains(query, true) ||
                    it.info.contains(query, true)
            }.map { it.toSManga() },
            false,
        )
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl/api/mangaData#${manga.url}", headers)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaData = response.parseAs<List<MangaData>>()
        val mangaId = response.request.url.fragment!!

        return mangaData.firstOrNull { it.id == mangaId }!!.toSManga()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaData = response.parseAs<List<MangaData>>()
        val mangaId = response.request.url.fragment!!

        return mangaData.firstOrNull { it.id == mangaId }
            ?.chapters.orEmpty().map {
                SChapter.create().apply {
                    url = "/$mangaId/${it.id}"
                    name = it.number + if (it.title.isNullOrEmpty()) "" else ": ${it.title}"
                    date_upload = runCatching {
                        dateFormat.parse(it.date!!)!!.time
                    }.getOrDefault(0L)
                }
            }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")

        return GET("$apiUrl/api/mangaData#$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val mangaData = response.parseAs<List<MangaData>>()
        val chapterId = response.request.url.fragment!!

        return mangaData.flatMap { it.chapters }.firstOrNull { it.id == chapterId }
            ?.images.orEmpty().mapIndexed { idx, img ->
                Page(idx, "", "https://127.0.0.1/image#${img.source}")
            }
    }

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.pathSegments.lastOrNull() != "image" || url.fragment.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val image = url.fragment!!

        val boundary = buildString {
            append((1..9).random())
            repeat(28) {
                append((0..9).random())
            }
        }

        val form = MultipartBody.Builder("-----------------------------$boundary").apply {
            setType(MultipartBody.FORM)
            addPart(
                Headers.headersOf("Content-Disposition", "form-data; name=\"image\""),
                image.toRequestBody(null),
            )
        }.build()

        val response = client.newCall(
            POST("$apiUrl/api/loadImages", headers, form),
        ).execute().parseAs<ImageResponse>()

        val newUrl = "https://127.0.0.1/?${response.image.substringAfter(":")}"

        return chain.proceed(
            request.newBuilder()
                .url(newUrl)
                .build(),
        )
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    companion object {
        private val dateFormat by lazy {
            SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
        }
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
