package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class ManhwaScan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Referer", "$baseUrl/")
                    .header("User-Agent", "Mozilla/5.0")
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    )
                    .build(),
            )
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page)

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val urlBuilder = "$baseUrl/explorar/".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query.trim())
        }

        getGenreFilter(filters)?.let { genre ->
            urlBuilder.addQueryParameter("genero", genre)
        }

        val document = client.get(urlBuilder.build()).asJsoup()

        return parseMangaPage(document, page)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Filtrar por género"),
        GenreFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/manga/")) {
            throw Exception("URL no soportada")
        }

        val document = client.get(url).asJsoup()
        return parseMangaDetails(document).apply {
            setUrlWithoutDomain(url.toString())
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        val imageRegex = Regex(
            """https://img\.mantrazscan\.co[^"\\ ]+/WP-manga/[^"\\ ]+\.(?:webp|WEBP|jpg|JPG|jpeg|JPEG|png|PNG)""",
        )

        return imageRegex.findAll(document.html())
            .map { it.value.replace("\\/", "/") }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
            .toList()
    }

    private suspend fun getMangaList(page: Int): MangasPage {
        val url = if (page == 1) {
            "$baseUrl/explorar/"
        } else {
            "$baseUrl/explorar/page/$page/"
        }

        return parseMangaPage(client.get(url).asJsoup(), page)
    }

    private fun parseMangaPage(document: Document, page: Int): MangasPage {
        val html = document.html()

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

        return MangasPage(
            mangas = mangas,
            hasNextPage = html.contains("/explorar/page/${page + 1}/"),
        )
    }

    private fun parseMangaDetails(document: Document): SManga {
        val html = document.html()

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

    private fun parseChapterList(document: Document): List<SChapter> {
        val html = document.html()

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
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull() ?: return null

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
}
