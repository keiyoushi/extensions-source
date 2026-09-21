package eu.kanade.tachiyomi.extension.pt.cerisescans

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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.time.Instant
import kotlin.math.max

@Source
abstract class CeriseScan : KeiSource() {

    // Keep request bursts comfortably below the site's published API limit.
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override fun getHomeUrl(): String = "$baseUrl/#/library"

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val root = client.get("$baseUrl/api/comics?limit=50&sort=views")
            .parseAs<JsonElement>()

        return MangasPage(parseComicList(root), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val root = client.get("$baseUrl/api/comics?limit=120&sort=recent")
            .parseAs<JsonElement>()

        return MangasPage(parseComicList(root), false)
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val root = client.get("$baseUrl/api/comics")
            .parseAs<JsonElement>()

        val q = query.trim()
        val mangas = parseComicList(root).filter { manga ->
            q.isBlank() ||
                manga.title.contains(q, ignoreCase = true) ||
                manga.author.orEmpty().contains(q, ignoreCase = true) ||
                manga.genre.orEmpty().contains(q, ignoreCase = true)
        }

        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.fragment
            ?.substringAfter("/comic/", "")
            ?.substringBefore("/")
            ?.takeIf { it.isNotBlank() }
            ?: url.pathSegments
                .lastOrNull()
                ?.takeIf { it.isNotBlank() }

        if (slug.isNullOrBlank()) return null

        val root = client.get("$baseUrl/api/comics/$slug").parseAs<JsonElement>()
        return findComicObject(root)?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = normalizeMangaSlug(manga.url)

        val root = client.get("$baseUrl/api/comics/$slug").parseAs<JsonElement>()

        val updatedManga = if (fetchDetails) {
            findComicObject(root)?.toSManga()?.apply {
                url = slug
            } ?: manga
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            parseChapterList(root)
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = normalizeChapterId(chapter.url)

        val root = client.get(
            "$baseUrl/api/chapter-images?chapterId=${
                URLEncoder.encode(chapterId, Charsets.UTF_8.name())
            }",
        ).parseAs<JsonElement>()

        val imageArray = when (root) {
            is JsonArray -> root
            else -> findNamedArray(
                root,
                setOf("images", "pages", "chapterImages", "chapter_images", "data"),
            )
        }

        if (imageArray != null && imageArray.isNotEmpty()) {
            val pages = imageArray.mapIndexedNotNull { index, element ->
                imageElementToUrl(element, chapterId, index)?.let { url ->
                    Page(index, imageUrl = url)
                }
            }

            if (pages.isNotEmpty()) return pages

            // If only metadata is returned, the array index still defines page order.
            return imageArray.indices.map { index ->
                Page(index, imageUrl = readImageUrl(chapterId, index, null))
            }
        }

        // Fallback for nested image metadata.
        val explicitUrls = collectImageStrings(root)
            .map(::absoluteUrl)
            .distinct()

        if (explicitUrls.isNotEmpty()) {
            return explicitUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
        }

        throw Exception(
            "Unknown /api/chapter-images response: " +
                root.toString().take(500),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/#/comic/${normalizeMangaSlug(manga.url)}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/#/read/${normalizeChapterId(chapter.url)}"

    private fun parseComicList(root: JsonElement): List<SManga> {
        val preferred = findNamedArray(
            root,
            setOf("comics", "items", "results", "data"),
        )

        val objects = if (preferred != null) {
            preferred.mapNotNull { it as? JsonObject }
        } else {
            collectObjects(root)
        }

        return objects
            .filter(::looksLikeComic)
            .distinctBy { it.deepString("slug", "id", "_id") ?: it.deepString("title", "name") }
            .map { it.toSManga() }
    }

    private fun findComicObject(root: JsonElement): JsonObject? {
        val direct = root as? JsonObject
        if (direct != null && looksLikeComic(direct)) return direct

        val named = findNamedObject(root, setOf("comic", "manga", "data"))
        if (named != null && looksLikeComic(named)) return named

        return collectObjects(root).firstOrNull(::looksLikeComic)
    }

    private fun looksLikeComic(obj: JsonObject): Boolean {
        val title = obj.deepString("title", "name", "nome")
        val slug = obj.deepString("slug")
        val id = obj.deepString("id", "_id")
        return !title.isNullOrBlank() && (!slug.isNullOrBlank() || !id.isNullOrBlank())
    }

    private fun JsonObject.toSManga(): SManga {
        val title = deepString("title", "name", "nome")
            ?: throw Exception("Missing manga title in API response")

        val slug = deepString("slug")
            ?: deepString("id", "_id")
            ?: throw Exception("Missing manga slug/id in API response")

        return SManga.create().apply {
            url = slug
            this.title = title
            thumbnail_url = deepString(
                "coverUrl",
                "cover_url",
                "coverImage",
                "cover_image",
                "coverPath",
                "cover_path",
                "cover",
                "thumbnailUrl",
                "thumbnail_url",
                "thumbnail",
                "imageUrl",
                "image_url",
                "image",
                "poster",
            )?.let(::absoluteUrl)

            author = deepListText("author", "authors", "writer", "writers", "roteirista")
                ?: deepString("author", "authors", "writer", "roteirista")
            artist = deepListText("artist", "artists", "illustrator", "illustrators", "desenhista")
                ?: deepString("artist", "artists", "illustrator", "desenhista")
            description = deepString(
                "description",
                "synopsis",
                "summary",
                "descricao",
            )
            genre = deepListText("genres", "genre", "tags", "categories")
            status = parseStatus(deepString("status"))
        }
    }

    private fun parseChapterList(root: JsonElement): List<SChapter> {
        // Chapter IDs can be numeric or use the "<comicId>_ch_<number>" format.
        val rootObject = root as? JsonObject

        val chapterArray = (rootObject?.get("lastChapters") as? JsonArray)
            ?: findNamedArray(
                root,
                setOf("lastChapters", "chapters", "chapterList", "chapter_list", "episodes"),
            )

        val objects = chapterArray
            ?.mapNotNull { it as? JsonObject }
            ?: collectObjects(root).filter { obj ->
                obj.deepString("number", "chapter", "chapterNumber", "chapter_number", "num") != null &&
                    obj.deepString("id", "_id", "chapterId", "chapter_id") != null
            }

        return objects
            .mapNotNull(::chapterToSChapter)
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<SChapter> {
                    if (it.chapter_number >= 0f) it.chapter_number else Float.NEGATIVE_INFINITY
                }.thenByDescending { it.date_upload },
            )
    }

    private fun chapterToSChapter(obj: JsonObject): SChapter? {
        val id = obj.deepString("id", "_id", "chapterId", "chapter_id")
            ?: return null

        val rawNumber = obj.deepString(
            "number",
            "chapter",
            "chapterNumber",
            "chapter_number",
            "num",
        )

        val name = obj.deepString("title", "name", "chapterName", "chapter_name")
            ?: rawNumber?.let { "Capítulo $it" }
            ?: "Capítulo"

        val number = rawNumber?.toFloatOrNull()
            ?: NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: -1f

        return SChapter.create().apply {
            url = id
            this.name = name
            chapter_number = number
            date_upload = parseDate(
                obj.firstElement(
                    "createdAt",
                    "created_at",
                    "publishedAt",
                    "published_at",
                    "date",
                    "updatedAt",
                    "updated_at",
                ),
            )
            scanlator = obj.deepString("scanlator", "scan", "group", "team")
        }
    }

    private fun imageElementToUrl(
        element: JsonElement,
        chapterId: String,
        index: Int,
    ): String? {
        if (element is JsonPrimitive) {
            val value = element.contentOrNull?.trim().orEmpty()
            if (value.isBlank()) return null

            return if (
                value.startsWith("http://") ||
                value.startsWith("https://") ||
                value.startsWith("/")
            ) {
                absoluteUrl(value)
            } else {
                // Quando o array traz somente um token de versão/hash.
                readImageUrl(chapterId, index, value)
            }
        }

        val obj = element as? JsonObject ?: return null

        val explicit = obj.deepString(
            "url",
            "src",
            "path",
            "imageUrl",
            "image_url",
            "image",
            "file",
        )

        if (!explicit.isNullOrBlank()) return absoluteUrl(explicit)

        val version = obj.deepString(
            "version",
            "v",
            "hash",
            "cacheKey",
            "cache_key",
        )

        return readImageUrl(chapterId, index, version)
    }

    private fun readImageUrl(
        chapterId: String,
        index: Int,
        version: String?,
    ): String = buildString {
        append(baseUrl)
        append("/api/read-image/")
        append(chapterId)
        append("/")
        append(index)

        if (!version.isNullOrBlank()) {
            append("?v=")
            append(URLEncoder.encode(version, Charsets.UTF_8.name()))
        }
    }

    private fun collectImageStrings(root: JsonElement): List<String> {
        val result = mutableListOf<String>()

        fun walk(element: JsonElement, key: String? = null) {
            when (element) {
                is JsonObject -> element.forEach { (k, v) -> walk(v, k) }
                is JsonArray -> element.forEach { walk(it, key) }
                is JsonPrimitive -> {
                    val value = element.contentOrNull ?: return
                    val keyLooksImage = key?.lowercase()?.let {
                        it.contains("image") ||
                            it.contains("url") ||
                            it.contains("src") ||
                            it.contains("path") ||
                            it.contains("file")
                    } == true

                    val valueLooksImage =
                        value.startsWith("/api/read-image/") ||
                            value.startsWith("/api/image-proxy") ||
                            value.startsWith("/uploads/") ||
                            value.startsWith("http://") ||
                            value.startsWith("https://")

                    if (keyLooksImage && valueLooksImage) result += value
                }
            }
        }

        walk(root)
        return result
    }

    private fun normalizeMangaSlug(value: String): String = value.substringAfter("#/comic/", value)
        .substringBefore("?")
        .substringBefore("#")
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { value }

    private fun normalizeChapterId(value: String): String = value.substringAfter("#/read/", value)
        .substringBefore("?")
        .substringBefore("#")
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { value }

    private fun absoluteUrl(value: String): String = when {
        value.startsWith("https://") || value.startsWith("http://") -> value
        value.startsWith("/") -> baseUrl + value
        else -> "$baseUrl/$value"
    }

    private fun parseStatus(value: String?): Int = when {
        value == null -> SManga.UNKNOWN
        value.contains("complete", ignoreCase = true) ||
            value.contains("conclu", ignoreCase = true) ||
            value.contains("finaliz", ignoreCase = true) -> SManga.COMPLETED
        value.contains("ongoing", ignoreCase = true) ||
            value.contains("andamento", ignoreCase = true) ||
            value.contains("publica", ignoreCase = true) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    private fun parseDate(element: JsonElement?): Long {
        val primitive = element as? JsonPrimitive ?: return 0L

        primitive.longOrNull?.let { raw ->
            return if (raw in 1..9_999_999_999L) raw * 1000 else max(raw, 0L)
        }

        val text = primitive.contentOrNull ?: return 0L
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
    }

    private fun JsonObject.firstElement(vararg keys: String): JsonElement? = keys.firstNotNullOfOrNull { this[it] }

    private fun JsonObject.deepString(vararg keys: String): String? {
        keys.forEach { key ->
            val direct = this[key]
            if (direct is JsonPrimitive) {
                direct.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        for ((_, value) in this) {
            if (value is JsonObject) {
                value.deepString(*keys)?.let { return it }
            }
        }

        return null
    }

    private fun JsonObject.deepListText(vararg keys: String): String? {
        keys.forEach { key ->
            when (val value = this[key]) {
                is JsonPrimitive -> {
                    value.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                }
                is JsonArray -> {
                    val values = value.mapNotNull { item ->
                        when (item) {
                            is JsonPrimitive -> item.contentOrNull
                            is JsonObject -> item.deepString("name", "title", "label", "slug")
                            else -> null
                        }
                    }.filter { it.isNotBlank() }

                    if (values.isNotEmpty()) return values.joinToString(", ")
                }
                is JsonObject -> {
                    value.deepString("name", "title", "label", "slug")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { return it }
                }
                else -> Unit
            }
        }

        return null
    }

    private fun findNamedArray(
        root: JsonElement,
        names: Set<String>,
    ): JsonArray? {
        when (root) {
            is JsonObject -> {
                root.forEach { (key, value) ->
                    if (key in names && value is JsonArray) return value
                }
                root.values.forEach { value ->
                    findNamedArray(value, names)?.let { return it }
                }
            }
            is JsonArray -> root.forEach { value ->
                findNamedArray(value, names)?.let { return it }
            }
            else -> Unit
        }

        return null
    }

    private fun findNamedObject(
        root: JsonElement,
        names: Set<String>,
    ): JsonObject? {
        when (root) {
            is JsonObject -> {
                root.forEach { (key, value) ->
                    if (key in names && value is JsonObject) return value
                }
                root.values.forEach { value ->
                    findNamedObject(value, names)?.let { return it }
                }
            }
            is JsonArray -> root.forEach { value ->
                findNamedObject(value, names)?.let { return it }
            }
            else -> Unit
        }

        return null
    }

    private fun collectObjects(root: JsonElement): List<JsonObject> {
        val result = mutableListOf<JsonObject>()

        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    result += element
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }

        walk(root)
        return result
    }

    companion object {
        private val NUMBER_REGEX = """(\d+(?:\.\d+)?)""".toRegex()
    }
}
