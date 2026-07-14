package eu.kanade.tachiyomi.extension.ru.wamanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class WaManga : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "*/*")

    // ─────────────────────────────────────────────────────────────────────────
    // Catalog
    // ─────────────────────────────────────────────────────────────────────────

    // ── catalog URL builder ──────────────────────────────────────────────────

    private fun catalogBuilder(page: Int) = "$baseUrl/catalog/__data.json".toHttpUrl().newBuilder()
        .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
        .addQueryParameter("limit", PAGE_SIZE.toString())
        .addQueryParameter("x-sveltekit-invalidated", "001")

    private fun HttpUrl.Builder.withDefaultFilters(): HttpUrl.Builder {
        ALL_TYPES.forEach { addQueryParameter("types", it) }
        ALL_STATUSES.forEach { addQueryParameter("statuses", it) }
        ALL_TRANSLATION_STATUSES.forEach { addQueryParameter("translationStatuses", it) }
        ALL_PEGI.forEach { addQueryParameter("pegiRatings", it) }
        return this
    }

    private fun catalogUrl(page: Int, sortKey: String, sortDescending: Boolean = true) = catalogBuilder(page)
        .addQueryParameter("sortKey", sortKey)
        .addQueryParameter("sortDescending", sortDescending.toString())
        .withDefaultFilters()
        .build()

    override fun popularMangaRequest(page: Int): Request = GET(catalogUrl(page, "likes"), headers)

    override fun popularMangaParse(response: Response): MangasPage = catalogParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET(catalogUrl(page, "updatedAt"), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = catalogParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = catalogBuilder(page)

        if (query.isNotBlank()) {
            builder.addQueryParameter("query", query.trim())
        }

        filters.firstInstanceOrNull<OrderByFilter>()?.let { sort ->
            builder.addQueryParameter("sortKey", sort.toKey())
            builder.addQueryParameter("sortDescending", sort.toDescending().toString())
        } ?: run {
            builder.addQueryParameter("sortKey", "updatedAt")
            builder.addQueryParameter("sortDescending", "true")
        }

        (
            filters.firstInstanceOrNull<TypeGroup>()?.let { g ->
                g.state.filterIsInstance<Check>().filter { it.state }.map { it.id }.ifEmpty { ALL_TYPES }
            } ?: ALL_TYPES
            ).forEach { builder.addQueryParameter("types", it) }

        (
            filters.firstInstanceOrNull<StatusGroup>()?.let { g ->
                g.state.filterIsInstance<Check>().filter { it.state }.map { it.id }.ifEmpty { ALL_STATUSES }
            } ?: ALL_STATUSES
            ).forEach { builder.addQueryParameter("statuses", it) }

        (
            filters.firstInstanceOrNull<TranslationStatusGroup>()?.let { g ->
                g.state.filterIsInstance<Check>().filter { it.state }.map { it.id }.ifEmpty { ALL_TRANSLATION_STATUSES }
            } ?: ALL_TRANSLATION_STATUSES
            ).forEach { builder.addQueryParameter("translationStatuses", it) }

        (
            filters.firstInstanceOrNull<PegiGroup>()?.let { g ->
                g.state.filterIsInstance<Check>().filter { it.state }.map { it.id }.ifEmpty { ALL_PEGI }
            } ?: ALL_PEGI
            ).forEach { builder.addQueryParameter("pegiRatings", it) }

        filters.firstInstanceOrNull<YearGroup>()?.let { group ->
            group.state.firstInstanceOrNull<YearFromFilter>()?.let {
                val v = it.state.trim()
                if (v.isNotBlank()) builder.addQueryParameter("releaseYearFrom", v)
            }
            group.state.firstInstanceOrNull<YearToFilter>()?.let {
                val v = it.state.trim()
                if (v.isNotBlank()) builder.addQueryParameter("releaseYearTo", v)
            }
        }

        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = catalogParse(response)

    private fun catalogParse(response: Response): MangasPage {
        val parsed = parseSvelteKit(response) ?: return MangasPage(emptyList(), false)
        val (nodes, data) = parsed
        val pageData = data[0].jsonObject

        val mangasDataIdx = pageData["initialMangas"]?.toRef()
            ?: return MangasPage(emptyList(), false)
        val mangaRefs = data[mangasDataIdx].jsonArray.mapNotNull { it.toRef() }
        if (mangaRefs.isEmpty()) return MangasPage(emptyList(), false)

        val mangas = mangaRefs.mapNotNull { ref ->
            if (ref < 0 || ref >= data.size) return@mapNotNull null
            resolveCatalogManga(data[ref].jsonObject, nodes, data)
        }

        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    private fun resolveCatalogManga(obj: JsonObject, nodes: JsonArray, data: JsonArray): SManga? {
        val slug = resolveString(data, nodes, obj["slug"].toRef()) ?: return null
        val type = resolveString(data, nodes, obj["type"].toRef()) ?: "manga"
        val title = resolveString(data, nodes, obj["title"].toRef()) ?: return null
        val coverPath = resolveString(data, nodes, obj["coverUrl"].toRef())
        val genres = resolveStringList(data, nodes, obj["genres"].toRef())
        val likes = resolveInt(data, nodes, obj["likes"].toRef())
        val views = resolveInt(data, nodes, obj["views"].toRef())

        return SManga.create().apply {
            url = "$type/$slug"
            this.title = title
            thumbnail_url = coverPath?.let { "$baseUrl/$it" }
            genre = genres.joinToString()
            description = buildString {
                if (views != null) appendLine("Просмотров: ${formatCount(views)}")
                if (likes != null) appendLine("Лайков: ${formatCount(likes)}")
            }.trimEnd()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manga details
    // ─────────────────────────────────────────────────────────────────────────

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/${manga.url}/$SVELTE_DATA_SUFFIX", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val parsed = parseSvelteKit(response) ?: throw IllegalStateException("Failed to parse manga details")
        val (nodes, data) = parsed
        val pageData = data[0].jsonObject
        val mangaDataIdx = pageData["manga"]?.toRef()
            ?: throw IllegalStateException("Key 'manga' not found in details pageData")
        val obj = data[mangaDataIdx].jsonObject

        val slug = resolveString(data, nodes, obj["slug"].toRef()) ?: ""
        val type = resolveString(data, nodes, obj["type"].toRef()) ?: "manga"
        val title = resolveString(data, nodes, obj["title"].toRef())
            ?: throw IllegalStateException("Title not found in manga details")
        val altTitles = resolveStringList(data, nodes, obj["alternateTitles"].toRef())
        val description = resolveString(data, nodes, obj["description"].toRef()) ?: ""
        val coverPath = resolveString(data, nodes, obj["coverUrl"].toRef())
        val genres = resolveStringList(data, nodes, obj["genres"].toRef())
        val author = resolveStringList(data, nodes, obj["authors"].toRef())
            .firstOrNull { it != "N/A" }
        val artist = resolveStringList(data, nodes, obj["artists"].toRef())
            .firstOrNull { it != "N/A" }
        val statusStr = resolveString(data, nodes, obj["statusTitle"].toRef())
        val likes = resolveInt(data, nodes, obj["likes"].toRef())
        val views = resolveInt(data, nodes, obj["views"].toRef())

        val descText = buildString {
            if (description.isNotBlank()) append(description.trim())

            if (likes != null || views != null) {
                if (isNotEmpty()) append("\n\n")
                if (views != null) appendLine("Просмотров: ${formatCount(views)}")
                if (likes != null) append("Лайков: ${formatCount(likes)}")
            }

            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                appendLine("Альтернативные названия:")
                altTitles.forEachIndexed { i, it ->
                    if (i > 0) append('\n')
                    append("• $it")
                }
            }
        }.trim()

        return SManga.create().apply {
            url = "$type/$slug"
            this.title = title
            thumbnail_url = coverPath?.let { "$baseUrl/$it" }
            this.description = descText
            genre = genres.joinToString()
            this.author = author ?: artist
            status = parseStatus(statusStr)
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    // ─────────────────────────────────────────────────────────────────────────
    // Chapters
    // ─────────────────────────────────────────────────────────────────────────

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/${manga.url}/$SVELTE_DATA_SUFFIX", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val parsed = parseSvelteKit(response) ?: throw IllegalStateException("Failed to parse chapters")
        val (nodes, data) = parsed
        val pageData = data[0].jsonObject
        val mangaDataIdx = pageData["manga"]?.toRef()
            ?: throw IllegalStateException("Key 'manga' not found in chapter pageData")
        val mangaObj = data[mangaDataIdx].jsonObject

        val slug = resolveString(data, nodes, mangaObj["slug"].toRef()) ?: ""
        val type = resolveString(data, nodes, mangaObj["type"].toRef()) ?: "manga"

        val chaptersRef = mangaObj["chapters"].toRef() ?: return emptyList()
        val chaptersArray = resolveValue(data, nodes, chaptersRef) as? JsonArray ?: return emptyList()
        val chapterRefs = chaptersArray.mapNotNull { it.toRef() }

        return chapterRefs.mapNotNull { ref ->
            val chObj = data.getOrNull(ref) as? JsonObject ?: return@mapNotNull null
            val positionRef = chObj["position"].toRef() ?: return@mapNotNull null
            val positionValue = resolveValue(data, nodes, positionRef)
            val positionStr = (positionValue as? JsonPrimitive)?.content ?: return@mapNotNull null
            val positionNum = positionStr.toFloatOrNull() ?: return@mapNotNull null
            val dateStr = resolveString(data, nodes, chObj["createdAt"].toRef())

            SChapter.create().apply {
                url = "$type/$slug/$positionStr"
                name = "Глава $positionStr"
                chapter_number = positionNum
                date_upload = DATE_FORMAT.get()!!.tryParse(dateStr)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("/", limit = 3)
        if (parts.size < 3) throw IllegalStateException("Invalid chapter URL format: ${chapter.url}")
        val (type, slug, position) = parts
        return "$baseUrl/$type/$slug/glava-$position"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pages
    // ─────────────────────────────────────────────────────────────────────────

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.split("/", limit = 3)
        if (parts.size < 3) throw IllegalStateException("Invalid chapter URL format: ${chapter.url}")
        val (type, slug, position) = parts
        return GET(
            "$baseUrl/$type/$slug/glava-$position/$SVELTE_DATA_SUFFIX",
            headers,
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val parsed = parseSvelteKit(response) ?: throw IllegalStateException("Failed to parse pages")
        val (nodes, data) = parsed
        val pageData = data[0].jsonObject
        val chapterDataIdx = pageData["chapter"]?.toRef()
            ?: throw IllegalStateException("Key 'chapter' not found in pages pageData")
        val chapterObj = data[chapterDataIdx].jsonObject

        val filesRef = chapterObj["files"].toRef() ?: return emptyList()
        val filesArray = resolveValue(data, nodes, filesRef) as? JsonArray ?: return emptyList()

        return filesArray.mapNotNull { elem ->
            val fileRef = elem.toRef() ?: return@mapNotNull null
            val fileObj = data.getOrNull(fileRef) as? JsonObject ?: return@mapNotNull null
            val imagePath = resolveString(data, nodes, fileObj["diskFile"].toRef()) ?: return@mapNotNull null
            val positionRef = fileObj["position"].toRef() ?: return@mapNotNull null
            val positionValue = resolveValue(data, nodes, positionRef)
            val positionStr = (positionValue as? JsonPrimitive)?.content ?: return@mapNotNull null
            val positionNum = positionStr.toDoubleOrNull()?.toInt() ?: return@mapNotNull null

            Page(positionNum, imageUrl = "$baseUrl/$imagePath")
        }.sortedBy { it.index }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // ─────────────────────────────────────────────────────────────────────────
    // Filters
    // ─────────────────────────────────────────────────────────────────────────

    override fun getFilterList(): FilterList = defaultFilters()

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseSvelteKit(response: Response): Pair<JsonArray, JsonArray>? {
        return try {
            val json = response.parseAs<JsonObject>()
            val nodes = json["nodes"]?.jsonArray ?: return null
            val dataNode = nodes.firstOrNull { node ->
                node is JsonObject && (node["type"] as? JsonPrimitive)?.content == "data"
            }?.jsonObject ?: return null
            val data = dataNode["data"]?.jsonArray ?: return null
            Pair(nodes, data)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveInt(data: JsonArray, nodes: JsonArray, ref: Int?): Int? {
        val value = resolveValue(data, nodes, ref) ?: return null
        return (value as? JsonPrimitive)?.content?.toIntOrNull()
    }

    private fun formatCount(count: Int): String = when {
        count >= 1_000_000 -> {
            val m = count / 1_000_000
            val d = (count % 1_000_000) / 100_000
            if (d == 0) "${m}M" else "$m.${d}M"
        }
        count >= 1_000 -> {
            val k = count / 1_000
            val d = (count % 1_000) / 100
            if (d == 0) "${k}K" else "$k.${d}K"
        }
        else -> count.toString()
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "paused", "on hiatus", "hiatus" -> SManga.ON_HIATUS
        "discontinued", "cancelled", "abandoned" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val SVELTE_DATA_SUFFIX = "__data.json?x-sveltekit-invalidated=001"
        private val DATE_FORMAT = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}
