package eu.kanade.tachiyomi.extension.all.ososedki

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class Ososedki : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::imageFallbackInterceptor)

    // ========================= Popular =========================

    override suspend fun getPopularManga(page: Int): MangasPage = getAlbums(
        page = page,
        type = "top",
        value = "1",
    )

    // ========================= Latest =========================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getAlbums(page = page)

    // ========================= Search =========================

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!isSupportedHost(url.host)) {
            return MangasPage(emptyList(), false)
        }

        val albumId = url.pathSegments.toAlbumIdOrNull()
        if (albumId != null) {
            val manga = fetchMangaUpdate(
                manga = SManga.create().apply { this.url = albumId },
                chapters = emptyList(),
                fetchDetails = true,
                fetchChapters = false,
            ).manga

            return MangasPage(listOf(manga), false)
        }

        val (type, value) = url.pathSegments.toTypeAndValueOrNull()
            ?: return MangasPage(emptyList(), false)

        return getAlbums(page = page, type = type, value = value)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isBlank()) {
            return getPopularManga(page)
        }

        return getAlbums(
            page = page,
            type = "search",
            value = trimmedQuery,
        )
    }

    // ========================= Details =========================

    override fun getMangaUrl(manga: SManga): String = buildPhotoUrl(manga.url).toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(buildPhotoUrl(manga.url)).asJsoup()
        val albumId = manga.url

        val modelTags = document.extractTags(AUTHOR_SELECTOR)
        val cosplayTags = document.extractTags(COSPLAY_SELECTOR)
        val fandomTags = document.extractTags(FANDOM_SELECTOR)

        val parsedTitle = listOfNotNull(
            modelTags.firstOrNull(),
            cosplayTags.firstOrNull(),
            fandomTags.firstOrNull(),
        )
            .joinToString(" - ")
            .ifBlank {
                document
                    .selectFirst(TITLE_SELECTOR)
                    ?.text()
                    ?.trim()
                    ?.replace(TITLE_SUFFIX_REGEX, "")
                    ?.trim()
                    .orEmpty()
            }

        if (parsedTitle.isBlank()) {
            throw Exception("Title is missing for album id: $albumId")
        }

        val updatedManga = SManga.create().apply {
            url = albumId
            title = parsedTitle
            thumbnail_url = getCoverFromAlbumId(albumId)
                ?: document.selectFirst(ENTRY_IMAGE_SELECTOR)?.imgSrc()
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
            author = modelTags
                .joinToString()
                .takeIf(String::isNotBlank)
            artist = cosplayTags
                .joinToString()
                .takeIf(String::isNotBlank)
            genre = (modelTags + cosplayTags + fandomTags)
                .distinct()
                .joinToString()
                .takeIf(String::isNotBlank)
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val chapter = SChapter.create().apply {
            url = albumId
            name = "Gallery"
            chapter_number = 0F
            date_upload = parseUploadDate(document)
        }

        return SMangaUpdate(updatedManga, listOf(chapter))
    }

    // ========================= Chapters =========================

    override fun getChapterUrl(chapter: SChapter): String = buildPhotoUrl(chapter.url).toString()

    // ========================= Pages =========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(buildPhotoUrl(chapter.url)).asJsoup()

        val imageUrls = document.select(PAGE_SELECTOR)
            .map { it.absUrl("href") }
            .filter(String::isNotBlank)
            .distinct()
            .sortedBy(::extractPageNumber)

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private suspend fun getAlbums(page: Int, type: String? = null, value: String? = null): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("albums")
            .addQueryParameter("page", page.toString())
            .apply {
                if (!type.isNullOrBlank() && !value.isNullOrBlank()) {
                    addQueryParameter("type", type)
                    addQueryParameter("value", value)
                }
            }
            .build()

        val data = client.get(url).parseAs<AlbumsResponseDto>()
        val document = Jsoup.parseBodyFragment(data.html, baseUrl)

        val mangas = document.select(ENTRY_SELECTOR)
            .map { it.toSManga() }

        return MangasPage(
            mangas = mangas,
            hasNextPage = data.hasMore,
        )
    }

    private fun Element.toSManga(): SManga {
        val albumUrl = selectFirst(ENTRY_LINK_SELECTOR)
            ?.absUrl("href")
            ?.toHttpUrlOrNull()
            ?: throw Exception("Unable to parse album URL from listing element")

        val albumId = albumUrl.pathSegments.toAlbumIdOrNull()
            ?: throw Exception("Unable to parse album id from URL: $albumUrl")

        val parsedTitle = selectFirst(ENTRY_TITLE_SELECTOR)
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank {
                selectFirst(ENTRY_IMAGE_SELECTOR)
                    ?.attr("alt")
                    ?.substringBefore(" nude.")
                    ?.trim()
                    .orEmpty()
            }

        if (parsedTitle.isBlank()) {
            throw Exception("Title is missing for album id: $albumId")
        }

        return SManga.create().apply {
            url = albumId
            title = parsedTitle
            thumbnail_url = selectFirst(ENTRY_IMAGE_SELECTOR)?.imgSrc()
            author = selectFirst(ENTRY_AUTHOR_SELECTOR)?.text()?.trim()?.takeIf(String::isNotBlank)
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    private fun Element.imgSrc(): String? = when {
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("src") -> absUrl("src")
        else -> null
    }

    private fun Document.extractTags(selector: String): List<String> = select(selector)
        .eachText()
        .map { it.trim() }
        .filter(String::isNotBlank)
        .distinct()

    private fun getCoverFromAlbumId(albumId: String): String? {
        val match = ALBUM_ID_PARTS_REGEX.matchEntire(albumId) ?: return null
        val ownerId = match.groupValues[1]
        val postId = match.groupValues[2]

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("images")
            .addPathSegment("albums")
            .addPathSegment(ownerId)
            .addPathSegment("$postId.webp")
            .build()
            .toString()
    }

    private fun parseUploadDate(document: Document): Long {
        val jsonLd = document.select("script[type=application/ld+json]")
            .map { it.data() }
            .firstOrNull { DATE_PUBLISHED_REGEX.containsMatchIn(it) }
            ?: return 0L

        val dateString = DATE_PUBLISHED_REGEX.find(jsonLd)
            ?.groupValues
            ?.getOrNull(1)

        return Instant.tryParse(dateString)
    }

    private fun buildPhotoUrl(albumId: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("photos")
        .addPathSegment(albumId)
        .build()

    private fun isSupportedHost(host: String): Boolean = host.equals(BASE_HOST, ignoreCase = true) ||
        host.equals("www.$BASE_HOST", ignoreCase = true)

    private fun extractPageNumber(url: String): Int = url
        .toHttpUrlOrNull()
        ?.pathSegments
        ?.lastOrNull()
        ?.substringBefore('.')
        ?.toIntOrNull()
        ?: Int.MAX_VALUE

    private fun List<String>.toAlbumIdOrNull(): String? {
        val normalized = map { it.trim() }
        val photosIndex = normalized.indexOf("photos")
        if (photosIndex == -1 || photosIndex + 1 >= normalized.size) {
            return null
        }

        val albumId = normalized[photosIndex + 1]
        return albumId.takeIf { ALBUM_ID_REGEX.matches(it) }
    }

    private fun List<String>.toTypeAndValueOrNull(): Pair<String, String>? {
        val normalized = map { it.trim() }
        if (normalized.size < 2) {
            return null
        }

        val type = normalized[0]
        if (type !in SUPPORTED_FILTER_TYPES) {
            return null
        }

        val value = normalized[1]
            .replace('+', ' ')
            .trim()

        return if (value.isBlank()) {
            null
        } else {
            type to value
        }
    }

    companion object {
        private const val BASE_HOST = "ososedki.com"

        private const val ENTRY_SELECTOR = "article.gallery-item:has(a.gallery-link)"
        private const val ENTRY_LINK_SELECTOR = "a.gallery-link"
        private const val ENTRY_TITLE_SELECTOR = "h3"
        private const val ENTRY_IMAGE_SELECTOR = "img.gallery-img"
        private const val ENTRY_AUTHOR_SELECTOR = "span.badge:not(.bg-dark)"

        private const val TITLE_SELECTOR = "h1"
        private const val AUTHOR_SELECTOR = "a[href^=/model/]"
        private const val COSPLAY_SELECTOR = "a[href^=/cosplay/]"
        private const val FANDOM_SELECTOR = "a[href^=/fandom/]"

        private const val PAGE_SELECTOR = "#photos a[href^=/images/], #photos a[href^=https://ososedki.com/images/]"

        private val ALBUM_ID_REGEX = "-?\\d+_\\d+".toRegex()
        private val ALBUM_ID_PARTS_REGEX = "(-?\\d+)_(\\d+)".toRegex()
        private val DATE_PUBLISHED_REGEX = "\"datePublished\":\"([^\"]+)\"".toRegex()
        private val TITLE_SUFFIX_REGEX =
            "\\s*\\(\\d+\\s+leaked\\s+photos\\)\\s+from\\s+Onlyfans,\\s+Patreon\\s+and\\s+Fansly\\s*$".toRegex(RegexOption.IGNORE_CASE)

        private val SUPPORTED_FILTER_TYPES = setOf(
            "model",
            "cosplay",
            "fandom",
        )
    }
}
