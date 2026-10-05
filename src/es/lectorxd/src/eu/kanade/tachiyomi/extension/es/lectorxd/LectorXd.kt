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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LectorXd : KeiSource() {

    private val baseUrlHost by lazy {
        baseUrl.toHttpUrl().host
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(
        permits = 1,
        period = 2.seconds,
    ) { request ->
        request.host == baseUrlHost
    }

    // ========================================================================
    // FILTROS
    // ========================================================================

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
            ?.filterIsInstance<Filters.GenreGroup>()
            ?.flatMap { group ->
                group.state
                    .filter { tag ->
                        tag.state
                    }
                    .flatMap { tag ->
                        tag.ids
                    }
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
        .select(
            """.manga-grid a[href^="/manga/"],
            .manga-grid a[href^="/manhwa/"],
            .manga-grid a[href^="/manhua/"],
            .manga-grid a[href^="/novela/"],
            .manga-grid a[href^="/one_shot/"]""",
        )
        .mapNotNull { card ->
            val url = card.attr("href").trim()

            val title = card
                .selectFirst("h4[title]")
                ?.attr("title")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: card
                    .selectFirst("h4")
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: card
                    .selectFirst("img[alt]")
                    ?.attr("alt")
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

            val communityRating = if (fetchDetails) {
                fetchCommunityRating(document)
            } else {
                ""
            }

            SMangaUpdate(
                manga = if (fetchDetails) {
                    parseMangaDetails(
                        document = document,
                        original = manga,
                        communityRating = communityRating,
                    )
                } else {
                    manga
                },
                chapters = if (fetchChapters) {
                    chapterListParse(document)
                } else {
                    chapters
                },
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

        val cover = document
            .select(
                "img[src*='/manga/covers/'], " +
                    "img[data-src*='/manga/covers/'], " +
                    "img[data-lazy-src*='/manga/covers/']",
            )
            .firstOrNull { image ->
                image.attr("alt")
                    .trim()
                    .equals(
                        title,
                        ignoreCase = true,
                    )
            }
            ?: document.selectFirst(
                "img[src*='/manga/covers/'], " +
                    "img[data-src*='/manga/covers/'], " +
                    "img[data-lazy-src*='/manga/covers/']",
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

        return CHAPTER_REGEX
            .findAll(chaptersJson)
            .map { match ->
                val chapterNumber = match.groupValues[1]
                val groupId = match.groupValues[2]
                    .ifBlank { match.groupValues[3] }
                    .takeIf { it.isNotBlank() && it != "null" }

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
            .toList()
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

    private fun parseCommunityRating(
        document: Document,
    ): String {
        val community = document.selectFirst(".community")

        val labelRating = community
            ?.attr("aria-label")
            ?.let { label ->
                Regex(
                    """Media:\s*([0-9]+(?:[.,][0-9]+)?)\s*de\s*5""",
                    RegexOption.IGNORE_CASE,
                )
                    .find(label)
                    ?.groupValues
                    ?.getOrNull(1)
            }

        val rating = labelRating
            ?: document
                .selectFirst(".community-caption .out-of")
                ?.text()
                ?.substringBefore("/")
                ?.trim()
            ?: community
                ?.selectFirst("strong")
                ?.text()
                ?.trim()
            ?: return ""

        val votes = community
            ?.selectFirst(".count")
            ?.text()
            ?.removePrefix("·")
            ?.trim()
            .orEmpty()

        val normalizedRating = rating.replace(",", ".")

        return if (votes.isBlank()) {
            "$normalizedRating/5"
        } else {
            "$normalizedRating/5 ($votes)"
        }
    }

    private fun parseMangaId(
        document: Document,
    ): String? = document
        .selectFirst("[aria-describedby^=rating-help-]")
        ?.attr("aria-describedby")
        ?.removePrefix("rating-help-")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private suspend fun fetchCommunityRating(
        document: Document,
    ): String {
        val mangaId = parseMangaId(document)
            ?: return parseCommunityRating(document)

        val url = baseUrl
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("api/mangas/rating-summary")
            .addQueryParameter("id", mangaId)
            .build()

        return client.get(url).use { response ->
            val body = response.body.string()

            val average = Regex(
                """"average"\s*:\s*([0-9]+(?:\.[0-9]+)?)""",
            )
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?: return@use parseCommunityRating(document)

            val count = Regex(
                """"count"\s*:\s*(\d+)""",
            )
                .find(body)
                ?.groupValues
                ?.getOrNull(1)

            if (count == null) {
                "$average/5"
            } else {
                "$average/5 ($count valoraciones)"
            }
        }
    }

    private fun formatCommunityRating(
        communityRating: String,
    ): String {
        val rating = communityRating
            .substringBefore("/")
            .trim()
            .toDoubleOrNull()
            ?: return communityRating

        val ratingText = BigDecimal.valueOf(rating)
            .setScale(2, RoundingMode.DOWN)
            .toPlainString()

        val filledStars = rating
            .toInt()
            .coerceIn(0, 5)

        val emptyStars = 5 - filledStars

        return buildString {
            append("★".repeat(filledStars))
            append("☆".repeat(emptyStars))
            append(" ")
            append(ratingText)
            append(" / 5")
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

        private val CHAPTER_REGEX = Regex(
            """"chapter"\s*:\s*"([^"]+)"\s*,\s*"groupId"\s*:\s*(?:null|"([^"]+)"|(\d+))""",
        )
    }
}
