package eu.kanade.tachiyomi.extension.es.lectormonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangoLibreria : HttpSource() {

    override val name = "MangoLibreria"
    override val baseUrl = "https://mangolibreria.com"
    override val lang = "es"
    override val supportsLatest = true
    override val versionId = 2

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            // The image CDN rejects requests with the main site's Referer header.
            if (request.url.host != baseUrl.toHttpUrl().host) {
                val newRequest = request.newBuilder()
                    .removeHeader("Referer")
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
        .build()

    private val dateFormat1 by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val dateFormat2 by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val dateFormat3 by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }

    private fun parseDate(dateStr: String?): Long = dateFormat1.tryParse(dateStr)
        .takeIf { it != 0L }
        ?: dateFormat2.tryParse(dateStr)
            .takeIf { it != 0L }
        ?: dateFormat3.tryParse(dateStr)

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/comics?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val props = response.extractNextJs<ComicsDataProps> {
            it is JsonObject && "comicsData" in it
        }
        val comics = props!!.comicsData.comics
        return MangasPage(
            comics.map { it.toSManga() },
            props.comicsData.page < props.comicsData.totalPages,
        )
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comics?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/comics".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            } else {
                addQueryParameter("sort", "views")
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        val props = response.extractNextJs<ComicDataProps> {
            it is JsonObject && "comicData" in it
        }
        return props!!.comicData.toSManga()
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val props = response.extractNextJs<ComicDataProps> {
            it is JsonObject && "comicData" in it
        }

        val chapters = props!!.comicData.scanGroups?.flatMap { group ->
            val groupName = group.name
            group.chapters.map { ch ->
                ch.toSChapter(groupName).apply {
                    date_upload = parseDate(ch.dateString)
                }
            }
        } ?: emptyList()

        return chapters.sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val props = response.extractNextJs<ComicDataProps> {
            it is JsonObject && "comicData" in it
        }
        return props!!.comicData.urlPages?.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        } ?: emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
