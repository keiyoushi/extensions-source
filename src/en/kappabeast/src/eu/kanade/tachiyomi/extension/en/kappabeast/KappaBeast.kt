package eu.kanade.tachiyomi.extension.en.kappabeast

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class KappaBeast : HttpSource() {

    override val name = "Kappa Beast"
    override val baseUrl = "https://kappabeast.com"
    private val apiUrl = "https://strapi.kappabeast.com/api"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId = 2

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("populate[media][populate]", "*")
            .addQueryParameter("populate[category][fields][0]", "name")
            .addQueryParameter("pagination[page]", page.toString())
            .addQueryParameter("pagination[pageSize]", "24")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNext = result.meta?.pagination?.let { it.page < it.pageCount } == true
        return MangasPage(mangas, hasNext)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("populate[media][populate]", "*")
            .addQueryParameter("populate[category][fields][0]", "name")
            .addQueryParameter("sort[0]", "updatedAt:desc")
            .addQueryParameter("pagination[page]", page.toString())
            .addQueryParameter("pagination[pageSize]", "24")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("populate[media][populate]", "*")
            .addQueryParameter("populate[category][fields][0]", "name")
            .addQueryParameter("pagination[page]", page.toString())
            .addQueryParameter("pagination[pageSize]", "24")

        if (query.isNotBlank()) {
            url.addQueryParameter($$"filters[title][$containsi]", query)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        if (sortFilter != null) {
            val sortOption = sortFilter.selected
            if (sortOption.isNotBlank()) {
                url.addQueryParameter("sort[0]", sortOption)
            }
        } else if (query.isBlank()) {
            url.addQueryParameter("sort[0]", "updatedAt:desc")
        }

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        if (genreFilter != null) {
            val genre = genreFilter.selected
            if (genre.isNotBlank()) {
                url.addQueryParameter($$"filters[category][name][$eq]", genre)
            }
        }

        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        if (statusFilter != null) {
            val status = statusFilter.selected
            if (status.isNotBlank()) {
                url.addQueryParameter($$"filters[manga_status][$eq]", status)
            }
        }

        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        if (typeFilter != null) {
            val type = typeFilter.selected
            if (type.isNotBlank()) {
                url.addQueryParameter($$"filters[type][$eq]", type)
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ===============================

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter($$"filters[slug][$eq]", slug)
            .addQueryParameter("populate[media][populate]", "*")
            .addQueryParameter("populate[category][fields][0]", "name")
            .addQueryParameter("pagination[pageSize]", "1")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaResponse>()
        return result.data.firstOrNull()?.toSManga()?.apply {
            initialized = true
        } ?: throw Exception("Manga not found")
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter($$"filters[manga][slug][$eq]", slug)
            .addQueryParameter("sort[0]", "number:desc")
            .addQueryParameter("pagination[pageSize]", "1000")
            .addQueryParameter("fields[0]", "title")
            .addQueryParameter("fields[1]", "number")
            .addQueryParameter("fields[2]", "publishedAt")
            .addQueryParameter("fields[3]", "createdAt")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterResponse>()
        return result.data.map { dto ->
            dto.toSChapter().apply {
                date_upload = dateFormat.tryParse(dto.getDateString())
            }
        }
    }

    // ============================== Pages =================================

    override fun getChapterUrl(chapter: SChapter) = baseUrl

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/chapters/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<SingleChapterResponse>()
        val html = result.data.htmlContent ?: ""
        val document = Jsoup.parseBodyFragment(html, baseUrl)

        return document.select("img").mapIndexed { i, img ->
            val raw = img.parent()
                ?.takeIf { it.tagName() == "a" }
                ?.attr("abs:href")
                ?.ifEmpty { null }
                ?: img.attr("abs:src").ifEmpty { img.attr("abs:data-original") }
            val src = BLOGGER_SIZE_REGEX.replace(raw, "/s0/")
            Page(i, imageUrl = src)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
    )

    companion object {
        private val BLOGGER_SIZE_REGEX = Regex("/s\\d+/")
    }
}
