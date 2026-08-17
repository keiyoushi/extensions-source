package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class ManhwaScan : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("User-Agent", "Mozilla/5.0")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/explorar/"
        } else {
            "$baseUrl/explorar/page/$page/"
        }
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/explorar/".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query.trim())
        }

        getGenreFilter(filters)?.let { genre ->
            urlBuilder.addQueryParameter("genero", genre)
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filtrar por género"),
        GenreFilter(),
    )

    private class GenreFilter :
        Filter.Select<String>(
            "Género",
            arrayOf(
                "Todos", "Romance", "Drama", "Fantasía", "Comedia", "Acción", "Aventura",
                "Harem", "Isekai", "Manhwa", "Manga", "Manhua", "Shounen", "Seinen",
                "BL", "Yaoi", "Yuri", "+18", "Sin censura",
            ),
        )

    private fun getGenreFilter(filters: FilterList): String? {
        val genre = filters.filterIsInstance<Filter.Select<String>>().firstOrNull() ?: return null
        return when (genre.state) {
            1 -> "romance"
            2 -> "drama"
            3 -> "fantasia"
            4 -> "comedia"
            5 -> "accion"
            6 -> "aventura"
            7 -> "harem"
            8 -> "isekai"
            9 -> "manhwa"
            10 -> "manga"
            11 -> "manhua"
            12 -> "shounen"
            13 -> "seinen"
            14 -> "bl"
            15 -> "yaoi"
            16 -> "yuri"
            17 -> "18"
            18 -> "sin-censura"
            else -> null
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage {
        val html = response.body.string()

        val cardRegex = Regex(
            """<div class="s-card">.*?<a class="s-card-imglink" href="([^"]+)">.*?<img src="([^"]+)".*?<a class="s-card-title" href="[^"]+">([^<]+)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )

        val mangas = cardRegex.findAll(html)
            .map { match ->
                SManga.create().apply {
                    setUrlWithoutDomain(match.groupValues[1])
                    thumbnail_url = match.groupValues[2]
                    title = match.groupValues[3].trim()
                }
            }
            .distinctBy { it.url }
            .toList()

        val currentPage = response.request.url.pathSegments
            .lastOrNull { it.toIntOrNull() != null }
            ?.toIntOrNull() ?: 1

        val hasNextPage = html.contains("/explorar/page/${currentPage + 1}/")
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val html = response.body.string()

        return SManga.create().apply {
            title = Regex("""<h1[^>]*>([^<]+)</h1>""")
                .find(html)?.groupValues?.get(1)?.trim().orEmpty()

            thumbnail_url = Regex(
                """class="series-cover".*?<img src="([^"]+)"""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).find(html)?.groupValues?.get(1)

            genre = Regex(
                """<a class="genre-tag"[^>]*>(.*?)</a>""",
            )
                .findAll(html)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")

            description = Regex(
                """class="series-desc[^"]*".*?>(.*?)</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).find(html)?.groupValues?.get(1)
                ?.replace(Regex("""<[^>]+>"""), "")
                ?.trim()
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()

        val chapterRegex = Regex(
            """<a class="ch-row"[^>]* href="(/manga/[^"]+/capitulo-([0-9]+(?:\.[0-9]+)?)/?)"""",
        )

        return chapterRegex.findAll(html)
            .map { match ->
                SChapter.create().apply {
                    setUrlWithoutDomain(match.groupValues[1])
                    name = "Capítulo ${match.groupValues[2]}"
                    chapter_number = match.groupValues[2].toFloatOrNull() ?: -1f
                }
            }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
            .toList()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        val imageRegex = Regex(
            """https://img\.mantrazscan\.co[^"\\ ]+/WP-manga/[^"\\ ]+\.(?:webp|WEBP|jpg|JPG|jpeg|JPEG|png|PNG)""",
        )

        return imageRegex.findAll(html)
            .map { it.value.replace("\\/", "/") }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
            .toList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
