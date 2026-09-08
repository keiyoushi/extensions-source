package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class ManhwaScan : KeiSource() {

    override fun Headers.Builder.configureHeaders() = apply {
        add(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
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
        val mangas = document.select("div.s-card")
            .mapNotNull { element ->
                val url = element.selectFirst("a.s-card-imglink")
                    ?.attr("href")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val title = element.selectFirst("a.s-card-title")
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(url)
                    this.title = title
                    thumbnail_url = element.selectFirst("img")
                        ?.absUrl("src")
                        ?.ifBlank {
                            element.selectFirst("img")?.attr("src")
                        }
                }
            }
            .distinctBy { it.url }

        return MangasPage(
            mangas = mangas,
            hasNextPage = document.selectFirst(
                "a[href*=\"/explorar/page/${page + 1}/\"]",
            ) != null,
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()

        thumbnail_url = document.selectFirst(".series-cover img")
            ?.absUrl("src")
            ?.ifBlank {
                document.selectFirst(".series-cover img")?.attr("src")
            }

        genre = document.select("a.genre-tag")
            .map { it.text() }
            .filter { it.isNotBlank() }
            .joinToString(", ")

        description = document.selectFirst(".series-desc")
            ?.text()
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select("a.ch-row")
            .mapNotNull { element ->
                val url = element.attr("href")
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val number = Regex("""capitulo-([0-9]+(?:\.[0-9]+)?)""")
                    .find(url)
                    ?.groupValues
                    ?.get(1)
                    ?: return@mapNotNull null

                SChapter.create().apply {
                    setUrlWithoutDomain(url)
                    name = "Capítulo $number"
                    chapter_number = number.toFloatOrNull() ?: -1f
                }
            }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
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
