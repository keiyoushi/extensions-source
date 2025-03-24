package eu.kanade.tachiyomi.extension.fr.phenixscans

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.*

class PhenixScans : HttpSource() {
    override val baseUrl = "https://phenix-scans.com"
    val apiBaseUrl = baseUrl.replace("https://", "https://api.")
    override val lang = "fr"
    override val name = "Phenix Scans"
    override val supportsLatest = true

    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.FRENCH)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiBaseUrl/front/homepage?section=top"

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<TopResponse>()

        val mangas = data.top.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$apiBaseUrl/${it.coverImage}" // Possibility of using ?width=75
                url = "$baseUrl/manga/${it.slug}"
            }
        }

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("front").addPathSegment("homepage")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("section", "latest")
            .addQueryParameter("limit", "12")
            .build()

        Log.e("PhenixScans", url.toString())

        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = Json.decodeFromString(LatestMangaResponse.serializer(), response.body.string())

        val mangas = data.latest.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$apiBaseUrl/${it.coverImage}" // Possibility of using ?width=75
                url = "$baseUrl/manga/${it.slug}"
            }
        }

        val hasNextPage = data.pagination.currentPage < data.pagination.totalPages

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw UnsupportedOperationException()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        TODO("Not yet implemented")
    }

    // =============================== Manga ==================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("front").addPathSegment("manga")
            .addPathSegment(manga.url.substringAfterLast("manga/"))
            .build()

        Log.e("PhenixScans", url.toString())

        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaDetailResponse>()

        return SManga.create().apply {
            title = data.manga.title
            thumbnail_url = "$apiBaseUrl/${data.manga.coverImage}"
            url = "/manga/${data.manga.slug}"
            description = data.manga.synopsis
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaDetailResponse>()

        return data.chapters.map {
            SChapter.create().apply {
                chapter_number = it.number.float
                date_upload = simpleDateFormat.tryParse(it.createdAt)
                name = "Chapter ${it.number}"
                url = "/manga/${data.manga.slug}/chapitre/${it.number}"
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    // =============================== Pages ================================

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val endpoint = chapter.url.substringAfterLast("manga/")
            .replace("/chapitre/", "/chapter/")
        val url = "$apiBaseUrl/front/manga/$endpoint"

        return GET(url, headers)
    }

    override fun getFilterList(): FilterList = FilterList()

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterReadingResponse>()

        return data.chapter.images.mapIndexed { index, url ->
            Page(index, imageUrl = "$apiBaseUrl/$url")
        }
    }
}
