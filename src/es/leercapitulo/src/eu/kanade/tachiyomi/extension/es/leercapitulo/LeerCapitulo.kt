package eu.kanade.tachiyomi.extension.es.leercapitulo

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeerCapitulo : KeiSource() {

    private val baseUrlHost by lazy {
        baseUrl.toHttpUrl().host
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(
        permits = 1,
        period = 3.seconds,
    ) { it.host == baseUrlHost }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get(baseUrl)

        return response.use {
            val document = it.asJsoup()

            val mangas = document.select(
                "div.lc-slider-track div.lc-slide",
            ).mapNotNull { element ->
                element.toSManga(
                    "a.lc-slide-name",
                    "a.lc-slide-cover",
                )
            }.distinctBy { it.url }

            MangasPage(
                mangas = mangas,
                hasNextPage = false,
            )
        }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl()
            .newBuilder()
            .apply {
                if (page > 1) {
                    addQueryParameter("page", page.toString())
                }
            }
            .build()

        val response = client.get(url)

        return response.use {
            val document = it.asJsoup()

            val mangas = document.select(
                "article.lc-release",
            ).mapNotNull { element ->
                element.toSManga(
                    "a.lc-release-title",
                    "a.lc-release-cover",
                )
            }.distinctBy { it.url }

            MangasPage(
                mangas = mangas,
                hasNextPage = document.hasNextPage(),
            )
        }
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val urlBuilder = baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("manga")
            .addPathSegment("")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter(
                "q",
                query.trim(),
            )
        } else {
            filters.firstInstanceOrNull<GenreFilter>()
                ?.takeIf { it.state > 0 }
                ?.let {
                    urlBuilder.addQueryParameter(
                        "genre",
                        it.toUriPart(),
                    )
                }

            filters.firstInstanceOrNull<ThemeFilter>()
                ?.takeIf { it.state > 0 }
                ?.let {
                    urlBuilder.addQueryParameter(
                        "theme",
                        it.toUriPart(),
                    )
                }

            filters.firstInstanceOrNull<TypeFilter>()
                ?.takeIf { it.state > 0 }
                ?.let {
                    urlBuilder.addQueryParameter(
                        "type",
                        it.toUriPart(),
                    )
                }

            filters.firstInstanceOrNull<StatusFilter>()
                ?.takeIf { it.state > 0 }
                ?.let {
                    urlBuilder.addQueryParameter(
                        "status",
                        it.toUriPart(),
                    )
                }

            filters.firstInstanceOrNull<SortFilter>()
                ?.takeIf { it.state > 0 }
                ?.let {
                    urlBuilder.addQueryParameter(
                        "sort",
                        it.toUriPart(),
                    )
                }
        }

        urlBuilder.addQueryParameter(
            "page",
            page.toString(),
        )

        val response = client.get(
            urlBuilder.build(),
        )

        return response.use {
            val document = it.asJsoup()

            MangasPage(
                mangas = parseMangaCards(document),
                hasNextPage = document.hasNextPage(),
            )
        }
    }

    override fun getFilterList(data: kotlinx.serialization.json.JsonElement?): FilterList = FilterList(
        Filter.Header(
            "Los filtros se ignoran al realizar una búsqueda por texto.",
        ),
        Filter.Header(
            "Los filtros se pueden combinar.",
        ),
        GenreFilter(),
        ThemeFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost) {
            return null
        }

        if (!url.encodedPath.startsWith("/manga/")) {
            return null
        }

        val response = client.get(url)

        return response.use {
            mangaDetailsParse(it.asJsoup())
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga).toHttpUrl()

        val response = client.get(mangaUrl)

        return response.use {
            val document = it.asJsoup()

            val updatedManga = if (fetchDetails) {
                mangaDetailsParse(document)
            } else {
                manga
            }

            val updatedChapters = if (fetchChapters) {
                chapterListParse(document)
            } else {
                chapters
            }

            SMangaUpdate(
                manga = updatedManga,
                chapters = updatedChapters,
            )
        }
    }

    override suspend fun getPageList(
        chapter: SChapter,
    ): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val response = client.get(chapterUrl)

        return response.use {
            val document = it.asJsoup()

            document.select(
                "main img, " +
                    "#reader img, " +
                    ".reader img, " +
                    ".reading-content img",
            ).mapNotNull { image ->
                image.imageUrl()
            }.mapIndexed { index, imageUrl ->
                Page(
                    index = index,
                    imageUrl = imageUrl,
                )
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = if (manga.url.startsWith("http")) {
        manga.url
    } else {
        "$baseUrl${manga.url.ensureLeadingSlash()}"
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("http")) {
        chapter.url
    } else {
        "$baseUrl${chapter.url.ensureLeadingSlash()}"
    }

    private fun parseMangaCards(
        document: Document,
    ): List<SManga> = document.select(
        "article.lc-card, " +
            "article.lc-release, " +
            "div.lc-card",
    ).mapNotNull { element ->
        element.toSManga(
            "a.lc-card-name, " +
                "a.lc-release-title",
            "a.lc-card-cover, " +
                "a.lc-release-cover",
        )
    }.distinctBy { it.url }

    private fun Element.toSManga(
        titleSelector: String,
        coverSelector: String,
    ): SManga? {
        val titleElement = selectFirst(titleSelector)
            ?: return null

        val href = titleElement.attr("abs:href")
            .takeIf { it.isNotBlank() }
            ?: titleElement.attr("href")

        if (href.isBlank()) {
            return null
        }

        val title = titleElement.text()
            .trim()

        if (title.isBlank()) {
            return null
        }

        return SManga.create().apply {
            this.title = title
            thumbnail_url = selectFirst(coverSelector)
                ?.coverUrl()

            setUrlWithoutDomain(href)
        }
    }

    private fun mangaDetailsParse(
        document: Document,
    ): SManga {
        val details = document.selectFirst(
            "article.lc-panel",
        )

        val facts = details
            ?.select("ul.lc-facts > li")
            .orEmpty()

        fun fact(name: String): String = facts.firstOrNull { item ->
            item.selectFirst("span.k")
                ?.text()
                ?.trim()
                ?.equals(name, ignoreCase = true) == true
        }?.select("a, span")
            ?.lastOrNull()
            ?.text()
            ?.trim()
            .orEmpty()

        val synopsis = document
            .selectFirst("#sinopsis p")
            ?.text()
            ?.trim()
            .orEmpty()
            .takeUnless {
                it.equals(
                    "Esta serie todavia no tiene sinopsis.",
                    ignoreCase = true,
                )
            }
            .orEmpty()

        val altNames = details
            ?.selectFirst("p.small.lc-muted")
            ?.text()
            ?.trim()
            .orEmpty()
            .removePrefix("Títulos Alternativos:")
            .removePrefix("Títulos alternativos:")
            .trim()

        val genres = details
            ?.select(
                "a[href*='?genre='], " +
                    "a[href*='?theme=']",
            )
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.joinToString()
            .orEmpty()

        return SManga.create().apply {
            title = details
                ?.selectFirst("h1")
                ?.text()
                ?.trim()
                .orEmpty()
                .ifBlank {
                    document.selectFirst("h1")
                        ?.text()
                        ?.trim()
                        .orEmpty()
                }

            author = fact("Autor")
            artist = fact("Dibujo")
            genre = genres
            status = fact("Estado").toStatus()

            description = buildString {
                if (synopsis.isNotBlank()) {
                    append(synopsis)
                }

                if (altNames.isNotBlank()) {
                    if (isNotEmpty()) {
                        append("\n\n")
                    }

                    append("Títulos alternativos: ")
                    append(altNames)
                }
            }

            thumbnail_url = details
                ?.selectFirst(".lc-cover-lg")
                ?.coverUrl()
        }
    }

    private fun chapterListParse(
        document: Document,
    ): List<SChapter> {
        return document.select(
            "#chapterList a.lc-chapter-row",
        ).mapNotNull { element ->
            val url = element.attr("abs:href")
                .takeIf { it.isNotBlank() }
                ?: element.attr("href")

            val name = element.selectFirst(".n")
                ?.text()
                ?.trim()
                .orEmpty()

            if (url.isBlank() || name.isBlank()) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                setUrlWithoutDomain(url)
                this.name = name

                date_upload = dateFormatter.tryParseDate(
                    element.selectFirst(".d")
                        ?.text()
                        ?.trim(),
                )
            }
        }.distinctBy { it.url }
    }

    private fun Element.coverUrl(): String? {
        val image = selectFirst("img")
            ?: return null

        return image.imageUrl()
    }

    private fun Element.imageUrl(): String? = listOf(
        attr("abs:data-lazy-src"),
        attr("abs:data-src"),
        attr("abs:src"),
        attr("abs:data-original"),
        attr("abs:data-url"),
    ).firstOrNull { url ->
        url.isNotBlank() &&
            !url.startsWith("data:", ignoreCase = true) &&
            !url.startsWith("blob:", ignoreCase = true)
    }

    private fun Document.hasNextPage(): Boolean = selectFirst(
        "a[rel=next], " +
            "a.next, " +
            "a.page-numbers.next",
    ) != null

    private fun String.ensureLeadingSlash(): String = if (startsWith("/")) {
        this
    } else {
        "/$this"
    }

    private fun String.toStatus(): Int = when (trim().lowercase()) {
        "ongoing",
        "en emisión",
        "en emision",
        -> SManga.ONGOING

        "paused",
        "en pausa",
        -> SManga.ON_HIATUS

        "completed",
        "finalizado",
        "finalizada",
        -> SManga.COMPLETED

        "cancelled",
        "canceled",
        "cancelado",
        "cancelada",
        -> SManga.CANCELLED

        else -> SManga.UNKNOWN
    }

    private val dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
