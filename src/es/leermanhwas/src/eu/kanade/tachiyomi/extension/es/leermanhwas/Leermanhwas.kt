package eu.kanade.tachiyomi.extension.es.leermanhwas

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.Locale

@Source
abstract class Leermanhwas : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()

            if (request.url.host != baseUrl.toHttpUrl().host) {
                chain.proceed(
                    request.newBuilder()
                        .removeHeader("Referer")
                        .build(),
                )
            } else {
                chain.proceed(request)
            }
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page)

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val genre = filters.filterIsInstance<GenreFilter>().first().toUriPart()

        val url = when {
            query.isNotBlank() -> {
                baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("search")
                    .addQueryParameter("s", query.trim())
                    .build()
                    .toString()
            }
            genre.isNotBlank() && page == 1 -> "$baseUrl/genero/$genre/"
            genre.isNotBlank() -> "$baseUrl/genero/$genre/page/$page/"
            else -> getPageUrl(page)
        }

        val document = client.get(url).asJsoup()

        return MangasPage(
            mangas = parseMangaList(document),
            hasNextPage = hasNextPage(document, page),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/manhwa/")) {
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

        return document.select("div.reading-content img").mapIndexedNotNull { index, image ->
            val imageUrl = image.attr("data-src").ifBlank {
                image.absUrl("src")
            }

            imageUrl.takeIf { it.isNotBlank() }?.let {
                Page(index, imageUrl = it)
            }
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Escribe un título o selecciona un género."),
        GenreFilter(),
    )

    private suspend fun getMangaList(page: Int): MangasPage {
        val document = client.get(getPageUrl(page)).asJsoup()

        return MangasPage(
            mangas = parseMangaList(document),
            hasNextPage = hasNextPage(document, page),
        )
    }

    private fun getPageUrl(page: Int): String = if (page == 1) {
        baseUrl
    } else {
        "$baseUrl/page/$page/"
    }

    private fun hasNextPage(document: Document, page: Int): Boolean = document.selectFirst(
        "ul.pagination a[href*=\"/page/${page + 1}/\"], .pagination a[href*=\"/page/${page + 1}/\"]",
    ) != null

    private fun parseMangaList(document: Document): List<SManga> {
        return document.select("div.latest-item").mapNotNull { element ->
            val link = element.selectFirst("div.latest-left > a[href], div.mm-name > a[href]")
                ?: return@mapNotNull null

            val title = element.selectFirst("h3.title-smaller")?.text()?.trim().orEmpty()
            if (title.isBlank()) {
                return@mapNotNull null
            }

            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = element.selectFirst("img.img-latest")
                    ?.attr("data-src")
                    ?.ifBlank { element.selectFirst("img.img-latest")?.absUrl("src").orEmpty() }
                    ?.takeIf { it.isNotBlank() }
                    ?: element.selectFirst("img.img-latest")?.absUrl("src")
            }
        }.distinctBy { it.url }
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.main-info-title")?.text()?.trim().orEmpty()

        thumbnail_url = document.selectFirst("img.img-cover")
            ?.attr("data-src")
            ?.ifBlank { document.selectFirst("img.img-cover")?.absUrl("src").orEmpty() }
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("img.img-cover")?.absUrl("src")

        description = document.selectFirst("div.short-desc-content")
            ?.text()
            ?.trim()

        genre = document.select("li:has(h5:matchesOwn(^Géneros$)) a[rel=tag]")
            .joinToString { it.text().trim() }

        status = parseStatus(
            document.selectFirst("div.post-status li:has(h5:matchesOwn(^Estado$)) span")
                ?.text(),
        )
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select("ul.chapter-list a.leermos[href]").mapNotNull { element ->
            val name = element.selectFirst("span.chapter-name")?.text()?.trim().orEmpty()
            val url = element.absUrl("href")

            if (name.isBlank() || url.isBlank()) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                this.name = name
                setUrlWithoutDomain(url)
                chapter_number = parseChapterNumber(name)
            }
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()?.lowercase(Locale.ROOT)) {
        "ongoing", "en curso" -> SManga.ONGOING
        "completed", "completo", "finalizado" -> SManga.COMPLETED
        "hiatus", "en pausa" -> SManga.ON_HIATUS
        "cancelled", "cancelado" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun parseChapterNumber(name: String): Float = CHAPTER_NUMBER_REGEX.find(name)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
        ?: -1f

    private class GenreFilter :
        Filter.Select<String>(
            "Género",
            GENRES.map { it.first }.toTypedArray(),
        ) {
        fun toUriPart(): String = GENRES[state].second
    }

    companion object {
        private val CHAPTER_NUMBER_REGEX = """(\d+(?:\.\d+)?)""".toRegex()

        private val GENRES = arrayOf(
            "Todos" to "",
            "Acción" to "accion",
            "Adulto" to "adulto",
            "Ciencia ficción" to "ciencia-ficcion",
            "Comedia" to "comedia",
            "Drama" to "drama",
            "Familia" to "familia",
            "Fantasía" to "fantasia",
            "Harem" to "harem",
            "Josei" to "josei",
            "Maduro" to "maduro",
            "Reencarnación" to "reencarnacion",
            "Romance" to "romance",
            "Seinen" to "seinen",
            "Shonen" to "shonen",
            "Smut" to "smut",
            "Sobrenatural" to "sobrenatural",
            "Vida escolar" to "vida-escolar",
        )
    }
}
