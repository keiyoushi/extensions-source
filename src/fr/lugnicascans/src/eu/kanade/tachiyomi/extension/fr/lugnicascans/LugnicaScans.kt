package eu.kanade.tachiyomi.extension.fr.lugnicascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response

class LugnicaScans : HttpSource() {
    override val name = "Lugnica Scans"

    override val baseUrl = "https://lugnica-scans.com"

    override val lang = "fr"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.fragment != "manga") return@addInterceptor chain.proceed(request)

            val slug = request.url.pathSegments.last()
            val newUrl = "$baseUrl/api/get/card/$slug"

            val newRequest = request.newBuilder().url(newUrl).build()

            return@addInterceptor chain.proceed(newRequest)
        }.addInterceptor { chain ->
            val request = chain.request()
            if (request.url.fragment != "chapter") return@addInterceptor chain.proceed(request)

            val slug = request.url.pathSegments.takeLast(2).joinToString("/")
            val newUrl = "$baseUrl/api/get/chapter/$slug"

            val newRequest = request.newBuilder().url(newUrl).build()

            return@addInterceptor chain.proceed(newRequest)
        }.build()

    // Popular
    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/get/homegrid/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val rawMangas = response.parseAs<List<HomePageManga>>()

        val mangas = rawMangas.map { manga ->
            SManga.create().apply {
                title = manga.manga_title
                setUrlWithoutDomain("/manga/${manga.manga_slug}#manga")

                thumbnail_url = "$baseUrl/upload/min_cover/${manga.manga_image}"
            }
        }

        return MangasPage(mangas, mangas.size == 10)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDetails = response.parseAs<MangaDetailsResponse>().manga

        val slug = response.request.url.pathSegments.last()

        return SManga.create().apply {
            url = "/manga/$slug#manga"
            title = mangaDetails.title
            thumbnail_url = "$baseUrl/upload/min_cover/${mangaDetails.image}"
            status = when (mangaDetails.status) {
                "0" -> SManga.ONGOING
                "1" -> SManga.COMPLETED
                "2" -> SManga.LICENSED
                "3" -> SManga.CANCELLED
                "4" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            description = mangaDetails.description

            val genres = mangaDetails.theme + mangaDetails.genre
            if (genres.isNotEmpty()) {
                genre = genres.filter { it.toInt() in GENRES }.joinToString { GENRES[it.toInt()]!! }
            }
            author = mangaDetails.author
            artist = mangaDetails.artist
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val details = response.parseAs<MangaDetailsResponse>()
        val slug = response.request.url.pathSegments.last()

        return details.chapters.values.flatten().map { chapterDetail ->
            SChapter.create().apply {
                url = "/manga/$slug/${chapterDetail.chapter}#chapter"
                name = "Chapitre ${chapterDetail.chapter}"
                date_upload = chapterDetail.date.trim('\r', '\n').toLong() * 1000
                chapter_number = chapterDetail.chapter
            }
        }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val details = response.parseAs<PageList>()
        return details.chapter.files.mapIndexed { i, filename -> Page(i, imageUrl = "https://lugnica-scans.com/upload/chapters/${details.manga.id}/${details.chapter.chapter}/$filename") }
    }

    // Page
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
