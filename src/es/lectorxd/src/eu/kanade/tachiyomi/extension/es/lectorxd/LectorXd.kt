package eu.kanade.tachiyomi.extension.es.lectorxd

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.math.RoundingMode
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LectorXd : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(
        permits = 1,
        period = 2.seconds,
    ) { request ->
        request.host == baseUrl.toHttpUrl().host
    }

    // ========================================================================
    // FILTROS
    // ========================================================================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("catalogo")
            .addQueryParameter("filters", "true")
            .addQueryParameter("adult", "all")
            .build()

        return client.get(url).use { response ->
            val document = response.asJsoup()
            val genres = document
                .select("#categoryOptions label[data-category-name]")
                .mapNotNull { label ->
                    val name = label.attr("data-category-name").trim()
                    val id = label.selectFirst("input[name=tags]")
                        ?.attr("value")
                        ?.trim()

                    if (name.isBlank() || id.isNullOrBlank()) {
                        null
                    } else {
                        name to id
                    }
                }
                .distinct()

            check(genres.isNotEmpty()) {
                "No se encontraron las categorías de LectorXD"
            }

            genres.toJsonElement()
        }
    }

    override fun getFilterList(
        data: JsonElement?,
    ): FilterList = Filters.getFilterList(data)

    // ========================================================================
    // URL DEL CATÁLOGO
    // ========================================================================

    private fun catalogUrl(
        page: Int,
        query: String = "",
        filters: FilterList? = null,
        defaultOrder: String = "recent",
    ): HttpUrl {
        val builder = baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("catalogo")

        val adultContent = filters
            ?.firstInstanceOrNull<Filters.AdultContent>()
            ?.let { filter ->
                Filters.adultContentValues.getOrNull(filter.state)
            }
            ?: "safe"

        builder.addQueryParameter(
            "adult",
            adultContent,
        )

        // El sitio utiliza filtros en el catálogo.
        builder.addQueryParameter(
            "filters",
            "true",
        )

        // Orden por defecto.
        builder.addQueryParameter(
            "orderBy",
            defaultOrder,
        )

        if (page > 1) {
            builder.addQueryParameter(
                "page",
                page.toString(),
            )
        }

        query
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let {
                builder.addQueryParameter(
                    "search",
                    it,
                )
            }

        filters
            ?.firstInstanceOrNull<Filters.Type>()
            ?.takeIf { it.state > 0 }
            ?.let { filter ->
                Filters.typeValues
                    .getOrNull(filter.state)
                    ?.let { value ->
                        builder.addQueryParameter(
                            "types",
                            value,
                        )
                    }
            }

        filters
            ?.firstInstanceOrNull<Filters.Status>()
            ?.takeIf { it.state > 0 }
            ?.let { filter ->
                Filters.statusValues
                    .getOrNull(filter.state)
                    ?.let { value ->
                        builder.addQueryParameter(
                            "status",
                            value,
                        )
                    }
            }

        filters
            ?.firstInstanceOrNull<Filters.Demographic>()
            ?.takeIf { it.state > 0 }
            ?.let { filter ->
                Filters.demographicValues
                    .getOrNull(filter.state)
                    ?.let { value ->
                        builder.addQueryParameter(
                            "demographics",
                            value,
                        )
                    }
            }

        val selectedTags = filters
            ?.firstInstanceOrNull<Filters.GenresFilter>()
            ?.state
            ?.flatMap { group ->
                group.state
                    .filter { tag -> tag.state }
                    .flatMap { tag -> tag.ids }
            }
            ?.distinct()
            .orEmpty()

        if (selectedTags.isNotEmpty()) {
            builder.addQueryParameter(
                "tags",
                selectedTags.joinToString(","),
            )
        }

        filters
            ?.firstInstanceOrNull<Filters.OrderBy>()
            ?.let { filter ->
                Filters.orderByValues
                    .getOrNull(filter.state)
                    ?.let { value ->
                        builder.setQueryParameter(
                            "orderBy",
                            value,
                        )
                    }
            }

        return builder.build()
    }

    // ========================================================================
    // POPULARES
    // ========================================================================

    override suspend fun getPopularManga(
        page: Int,
    ): MangasPage {
        val url = catalogUrl(
            page = page,
            defaultOrder = "rating",
        )

        return client.get(url).use { response ->
            val document = response.asJsoup()

            MangasPage(
                mangas = parseMangaCards(document),
                hasNextPage = document.hasNextPage(),
            )
        }
    }

    // ========================================================================
    // ÚLTIMAS ACTUALIZACIONES
    // ========================================================================

    override suspend fun getLatestUpdates(
        page: Int,
    ): MangasPage {
        val url = catalogUrl(
            page = page,
            defaultOrder = "recent",
        )

        return client.get(url).use { response ->
            val document = response.asJsoup()

            MangasPage(
                mangas = parseMangaCards(document),
                hasNextPage = document.hasNextPage(),
            )
        }
    }

    // ========================================================================
    // BÚSQUEDA
    // ========================================================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = catalogUrl(
            page = page,
            query = query,
            filters = filters,
            defaultOrder = "recent",
        )

        return client.get(url).use { response ->
            val document = response.asJsoup()

            MangasPage(
                mangas = parseMangaCards(document),
                hasNextPage = document.hasNextPage(),
            )
        }
    }

    // ========================================================================
    // PARSER DEL CATÁLOGO
    // ========================================================================

    private fun parseMangaCards(
        document: Document,
    ): List<SManga> = document
        .select(".manga-grid a[href]")
        .filterNot { card ->
            card.attr("href").startsWith("/novela/")
        }
        .mapNotNull { card ->
            val url = card.attr("href").trim()

            val title = card.selectFirst("h4")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            title
                ?.takeIf { url.isNotBlank() }
                ?.let {
                    SManga.create().apply {
                        this.title = it
                        this.url = url
                        thumbnail_url = card
                            .selectFirst(
                                "img[src], img[data-src], img[data-lazy-src]",
                            )
                            ?.imageUrl()
                    }
                }
        }
        .distinctBy { it.url }

    private fun Document.hasNextPage(): Boolean = selectFirst(
        """a[aria-label="Next page"][href*="page="]""",
    ) != null

    private fun Element.imageUrl(): String? {
        val value = sequenceOf(
            attr("data-src"),
            attr("data-lazy-src"),
            attr("src"),
        )
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
            ?: return null

        if (value.startsWith("data:")) {
            return null
        }

        return baseUrl
            .toHttpUrl()
            .resolve(value)
            ?.toString()
    }

    private fun absoluteUrl(
        url: String,
    ): String = baseUrl
        .toHttpUrl()
        .resolve(url)
        ?.toString()
        ?: url

    // ========================================================================
    // DETALLES Y CAPÍTULOS
    // ========================================================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = absoluteUrl(manga.url)

        return client.get(
            mangaUrl.toHttpUrl(),
        ).use { response ->
            val document = response.asJsoup()

            SMangaUpdate(
                manga = parseMangaDetails(
                    document = document,
                    original = manga,
                    communityRating = fetchCommunityRating(document),
                ),
                chapters = chapterListParse(document),
            )
        }
    }

    private fun parseMangaDetails(
        document: Document,
        original: SManga,
        communityRating: String,
    ): SManga {
        val title = document
            .selectFirst("h1")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: original.title

        val cover = document.selectFirst(
            "img.object-contain[src*='/manga/covers/']",
        )

        val synopsis = document
            .selectFirst("p.leading-relaxed")
            ?.text()
            ?.trim()
            .orEmpty()

        val alternativeTitlesLabel = document
            .select("span")
            .firstOrNull { element ->
                element.text()
                    .trim()
                    .equals(
                        "Títulos alternativos",
                        ignoreCase = true,
                    )
            }

        val alternativeTitles = alternativeTitlesLabel
            ?.nextElementSibling()
            ?.text()
            ?.trim()
            .orEmpty()

        val description = buildString {
            if (communityRating.isNotBlank()) {
                append(formatCommunityRating(communityRating))
            }

            if (synopsis.isNotBlank()) {
                if (isNotEmpty()) {
                    append("\n\n")
                }

                append(synopsis)
            }

            if (alternativeTitles.isNotBlank()) {
                if (isNotEmpty()) {
                    append("\n\n")
                }

                append("Títulos alternativos: ")
                append(alternativeTitles)
            }
        }

        return SManga.create().apply {
            this.title = title
            this.url = original.url
            thumbnail_url = cover?.imageUrl() ?: original.thumbnail_url
            this.description = description.ifBlank { original.description }
            genre = parseGenres(document).ifBlank { original.genre }
            status = parseStatus(document)
        }
    }

    private fun chapterListParse(
        document: Document,
    ): List<SChapter> {
        val mangaType = document
            .select("script")
            .firstNotNullOfOrNull { script ->
                MANGA_TYPE_REGEX
                    .find(script.html())
                    ?.groupValues
                    ?.getOrNull(1)
            }

        val mangaSlug = document
            .select("script")
            .firstNotNullOfOrNull { script ->
                MANGA_SLUG_REGEX
                    .find(script.html())
                    ?.groupValues
                    ?.getOrNull(1)
            }

        val chaptersJson = document
            .select("script")
            .firstNotNullOfOrNull { script ->
                CHAPTERS_LIST_REGEX
                    .find(script.html())
                    ?.groupValues
                    ?.getOrNull(1)
            }

        if (
            mangaType == null ||
            mangaSlug == null ||
            chaptersJson == null
        ) {
            return parseVisibleChapters(document)
        }

        return chaptersJson.parseAs<JsonElement>()
            .jsonArray
            .map { element ->
                val chapter = element.jsonObject
                val chapterNumber = chapter.getValue("chapter")
                    .jsonPrimitive.content
                val groupId = chapter["groupId"]
                    ?.jsonPrimitive
                    ?.contentOrNull

                SChapter.create().apply {
                    name = "Capítulo $chapterNumber"
                    url = buildString {
                        append("/")
                        append(mangaType)
                        append("/")
                        append(mangaSlug)
                        append("/leer/")
                        append(chapterNumber)

                        groupId?.let {
                            append("?g=")
                            append(it)
                        }
                    }
                    date_upload = 0L
                }
            }
            .distinctBy { it.url }
            .reversed()
    }

    private fun parseVisibleChapters(
        document: Document,
    ): List<SChapter> = document
        .select(
            """a[href*="/leer/"][title^="Leer Capítulo "]""",
        )
        .mapNotNull { element ->
            val url = element.attr("href").trim()

            val chapterNumber = element
                .attr("title")
                .substringAfter(
                    "Leer Capítulo ",
                    missingDelimiterValue = "",
                )
                .substringBefore(
                    " de ",
                    missingDelimiterValue = "",
                )
                .trim()
                .takeIf { it.isNotBlank() }

            if (url.isBlank() || chapterNumber == null) {
                null
            } else {
                SChapter.create().apply {
                    name = "Capítulo $chapterNumber"
                    this.url = url
                    date_upload = 0L
                }
            }
        }
        .distinctBy { it.url }

    override suspend fun getPageList(
        chapter: SChapter,
    ): List<Page> {
        val chapterUrl = absoluteUrl(chapter.url)

        return client.get(
            chapterUrl.toHttpUrl(),
        ).use { response ->
            val document = response.asJsoup()

            document
                .select("img.page-image")
                .mapNotNull { image ->
                    image.imageUrl()
                }
                .distinct()
                .mapIndexed { index, imageUrl ->
                    Page(
                        index = index,
                        imageUrl = imageUrl,
                    )
                }
        }
    }

    private fun parseGenres(
        document: Document,
    ): String = document
        .select(
            """a[href^="/catalogo?tags="]""",
        )
        .mapNotNull { element ->
            val href = element.attr("href")
            val name = element.text().trim()

            if (
                name.isNotBlank() &&
                TAG_QUERY_REGEX.containsMatchIn(href)
            ) {
                name
            } else {
                null
            }
        }
        .distinct()
        .joinToString()

    private suspend fun fetchCommunityRating(
        document: Document,
    ): String {
        val mangaId = document
            .selectFirst("footer[id^=rating-help-]")
            ?.id()
            ?.removePrefix("rating-help-")
            ?: return ""

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/mangas/rating-summary")
            .addQueryParameter("id", mangaId)
            .build()

        return client.get(url).use { response ->
            val data = response.body.string()
                .parseAs<JsonElement>()
                .jsonObject

            data.getValue("average")
                .jsonPrimitive.contentOrNull
                .orEmpty()
        }
    }

    private fun formatCommunityRating(
        communityRating: String,
    ): String {
        val rating = communityRating
            .toBigDecimalOrNull()
            ?: return ""

        if (rating.signum() < 0 || rating > 5.toBigDecimal()) {
            return ""
        }

        val ratingText = rating
            .setScale(1, RoundingMode.DOWN)
            .toPlainString()

        val filledStars = rating.toInt()

        return buildString {
            append("★".repeat(filledStars))
            append("☆".repeat(5 - filledStars))
            append(" ")
            append(ratingText)
            append("/5.0")
        }
    }

    private fun parseStatus(
        document: Document,
    ): Int {
        val text = document
            .selectFirst("h1")
            ?.parent()
            ?.parent()
            ?.text()
            ?.lowercase()
            .orEmpty()

        return when {
            "completado" in text -> {
                SManga.COMPLETED
            }

            "en emisión" in text ||
                "en emision" in text -> {
                SManga.ONGOING
            }

            else -> {
                SManga.UNKNOWN
            }
        }
    }

    companion object {
        private val TAG_QUERY_REGEX = Regex(
            """(?:[?&])tags=\d+(?:&|$)""",
        )

        private val MANGA_TYPE_REGEX = Regex(
            """const\s+mangaType\s*=\s*"([^"]+)";""",
        )

        private val MANGA_SLUG_REGEX = Regex(
            """const\s+mangaSlug\s*=\s*"([^"]+)";""",
        )

        private val CHAPTERS_LIST_REGEX = Regex(
            """const\s+chaptersList\s*=\s*(\[.*?]);""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
