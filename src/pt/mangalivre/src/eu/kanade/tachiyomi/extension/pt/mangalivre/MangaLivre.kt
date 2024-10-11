package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat

class MangaLivre : HttpSource() {

    override val name = "Manga Livre"

    override val baseUrl = "https://mangalivre.one"

    override val lang = "pt-BR"

    override val supportsLatest = false

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$API_URL/manga".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "50")
            .addQueryParameter("page", "$page")
            .addQueryParameter("name", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.parseAs<MangaLivreDto>()
        val mangas = page.mangas.map {
            SManga.create().apply {
                title = it.name
                description = it.synopsis
                thumbnail_url = "$CDN_URL/${it.photo}"
                url = "/manga/slug/${it.slug}#${it.id}"
            }
        }
        return MangasPage(mangas, page.hasNextPage())
    }

    // ============================== Details ===============================

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url
            .substringAfterLast("/")
            .removeComment()
        return "$baseUrl/manga/$slug"
    }

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$API_URL${manga.url.removeComment()}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDto = response.parseAs<MangaDto>()
        return SManga.create().apply {
            title = mangaDto.name
            description = mangaDto.synopsis
            thumbnail_url = "$CDN_URL/${mangaDto.photo}"
            genre = mangaDto.genre?.joinToString { it.value }
            mangaDto.status?.let {
                status = when (it.value) {
                    "Ativo" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
            url = "/manga/slug/${mangaDto.slug}#${mangaDto.id}"
        }
    }

    // ============================== Chapters ===============================

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("#")
        return GET("$API_URL/chapter/manga/all/$id", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<List<ChapterDto>>().map {
            SChapter.create().apply {
                name = "${it.chapter} - ${it.title}"
                date_upload = it.createdAt.toDate()
                chapter_number = it.chapter.toFloat()
                url = "/chapter/${it.id}"
            }
        }
    }

    private fun String.toDate(): Long = dateFormat.parse(this)?.time ?: 0L

    // ============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter) = GET("$API_URL${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<MangaPageDto>().pages.mapIndexed { index, page ->
            Page(index, imageUrl = "$CDN_URL/${page.url}")
        }
    }

    override fun imageUrlParse(response: Response) = ""

    // ============================= Utilities ==============================

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    private fun String.removeComment() = this.substringBeforeLast("#")

    companion object {
        const val API_URL = "https://api.mangalivre.one"
        const val CDN_URL = "https://cdn.mangalivre.one"

        @SuppressLint("SimpleDateFormat")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    }
}
