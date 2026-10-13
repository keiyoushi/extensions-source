package eu.kanade.tachiyomi.extension.es.reizucomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class ReizuComics : KeiSource() {

    private val apiUrl = "https://zrydzcqveheuwviyerto.supabase.co/rest/v1"
    private val cdnUrl = "https://cdn.reizucomics.com"

    private val anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpyeWR6Y3F2ZWhldXd2aXllcnRvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM3ODEzOTUsImV4cCI6MjA3OTM1NzM5NX0.9YWTbyCsK5itpTMFnNT8q6nOYfpIRc7Aslm8OQHe7QU"

    private val apiHeaders: Headers get() = headersBuilder()
        .add("apikey", anonKey)
        .add("Authorization", "Bearer $anonKey")
        .build()

    private val pageSize = 20
    private val comicSelect = "id,slug,title,description,cover_image_url,cover_r2_key,genres,status,work_type,has_adult_content"
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val limit = pageSize * page
        val body = buildJsonObject {
            put("p_days", JsonNull)
            put("p_genre", "all")
            put("p_work_type", "all")
            put("p_reading_format", "all")
            put("p_language", "es")
            put("p_limit", limit)
        }.toJsonRequestBody()
        val rows = client.post("$apiUrl/rpc/get_comics_by_period_views", apiHeaders, body)
            .parseAs<List<RankingRow>>()
            .filter { it.work_type != "novel" }
        val entries = rows
            .drop((page - 1) * pageSize)
            .take(pageSize)
            .map { it.toSManga() }
        return MangasPage(entries, rows.size >= limit)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * pageSize
        val body = buildJsonObject {
            put("p_locale", "es")
            put("p_search", JsonNull)
            put("p_letter", JsonNull)
            put("p_statuses", JsonNull)
            put("p_work_type", JsonNull)
            put("p_orientation", JsonNull)
            put("p_format", JsonNull)
            put("p_periodicity", JsonNull)
            put("p_genres", JsonNull)
            put("p_show_exclusive", false)
            put("p_show_adult", false)
            put("p_show_reizu_og", false)
            put("p_show_roty", false)
            put("p_month", JsonNull)
            put("p_offset", offset)
            put("p_limit", pageSize)
        }.toJsonRequestBody()
        val rows = client.post("$apiUrl/rpc/get_comics_by_recent_chapter", apiHeaders, body)
            .parseAs<List<RecentRow>>()
        val total = rows.firstOrNull()?.total_count ?: 0
        val entries = rows
            .filter { it.work_type != "novel" }
            .map { it.toSManga() }
        return MangasPage(entries, offset + rows.size < total)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return MangasPage(emptyList(), false)
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("select", comicSelect)
            .addQueryParameter("approval_status", "eq.approved")
            .addQueryParameter("language", "eq.es")
            .addQueryParameter("status", "neq.draft")
            .addQueryParameter("status", "neq.pending_review")
            .addQueryParameter("status", "neq.cancelled")
            .addQueryParameter("title", "ilike.*$query*")
            .addQueryParameter("order", "title.asc")
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("offset", ((page - 1) * pageSize).toString())
            .build()
        val rows = client.get(url, apiHeaders)
            .parseAs<List<ComicRow>>()
            .filter { it.work_type != "novel" }
        return MangasPage(rows.map { it.toSManga() }, rows.size >= pageSize)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segment = url.pathSegments.lastOrNull { it.isNotBlank() } ?: return null
        val (column, value) = if (uuidRegex.matches(segment)) "id" to segment else "slug" to segment
        val requestUrl = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("select", comicSelect)
            .addQueryParameter(column, "eq.$value")
            .addQueryParameter("limit", "1")
            .build()
        return client.get(requestUrl, apiHeaders)
            .parseAs<List<ComicRow>>()
            .firstOrNull()
            ?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,chapter_number,title,is_locked,is_special,published_at,comics($comicSelect)")
            .addQueryParameter("comic_id", "eq.${manga.url}")
            .addQueryParameter("is_published", "eq.true")
            .addQueryParameter("order", "chapter_number.desc")
            .build()
        val rows = client.get(url, apiHeaders).parseAs<List<ChapterRow>>()
        val details = rows.firstOrNull()?.comics?.toSManga() ?: manga
        return SMangaUpdate(details, rows.toSChapters(manga.url))
    }

    private fun List<ChapterRow>.toSChapters(comicId: String): List<SChapter> {
        val (regulars, specials) = partition { it.is_special != true }
        return (regulars + specials).map { it.toSChapter(comicId) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfter("/")
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "content_url,content_r2_keys,content_r2_keys_paged,content_r2_keys_scroll,is_locked")
            .addQueryParameter("is_published", "eq.true")
            .addQueryParameter("limit", "1")
            .addQueryParameter("id", "eq.$chapterId")
            .build()
        val row = client.get(url, apiHeaders)
            .parseAs<List<ChapterPages>>()
            .firstOrNull()
            ?: throw Exception("Capítulo no encontrado")
        if (row.is_locked == true) throw Exception("Este capítulo está bloqueado en Reizu Comics (requiere RP)")
        val imageUrls = resolvePageUrls(row)
        if (imageUrls.isEmpty()) throw Exception("No se encontraron páginas para este capítulo")
        return imageUrls.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private fun ComicRow.toSManga(): SManga = SManga.create().apply {
        url = id
        title = this@toSManga.title?.takeIf { it.isNotBlank() } ?: slug ?: "Sin título"
        thumbnail_url = resolveMediaUrl(cover_image_url)
            .ifBlank { resolveMediaUrl(cover_r2_key) }
            .ifBlank { null }
        description = this@toSManga.description
        genre = genres?.joinToString(", ")
        status = parseStatus(this@toSManga.status)
    }

    private fun RankingRow.toSManga(): SManga = SManga.create().apply {
        url = comicId
        title = this@toSManga.title?.takeIf { it.isNotBlank() } ?: slug ?: "Sin título"
        thumbnail_url = resolveMediaUrl(cover_image_url)
            .ifBlank { resolveMediaUrl(cover_r2_key) }
            .ifBlank { null }
        genre = genres?.joinToString(", ")
    }

    private fun RecentRow.toSManga(): SManga = SManga.create().apply {
        url = id
        title = this@toSManga.title?.takeIf { it.isNotBlank() } ?: slug ?: "Sin título"
        thumbnail_url = resolveMediaUrl(cover_image_url)
            .ifBlank { resolveMediaUrl(cover_r2_key) }
            .ifBlank { null }
        genre = genres?.joinToString(", ")
        status = parseStatus(this@toSManga.status)
    }

    private fun ChapterRow.toSChapter(comicId: String): SChapter = SChapter.create().apply {
        val number = formatChapterNumber(this@toSChapter.chapter_number)
        url = "$comicId/$id"
        name = buildString {
            if (this@toSChapter.is_special == true) append("Especial ")
            append("Capítulo ").append(number)
            this@toSChapter.title?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            if (this@toSChapter.is_locked == true) append(" 🔒")
        }
        chapter_number = this@toSChapter.chapter_number?.toFloat() ?: 0f
        date_upload = this@toSChapter.published_at?.let { Instant.tryParse(it) } ?: 0L
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "completed" -> SManga.COMPLETED
        "paused", "hiatus" -> SManga.ON_HIATUS
        "ongoing" -> SManga.ONGOING
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun formatChapterNumber(number: Double?): String {
        if (number == null) return "0"
        return if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
    }

    private fun resolvePageUrls(row: ChapterPages): List<String> {
        val paged = row.content_r2_keys_paged.cleanKeys()
        val scroll = row.content_r2_keys_scroll.cleanKeys()
        if (paged.isNotEmpty() || scroll.isNotEmpty()) return paged.ifEmpty { scroll }
        val legacy = row.content_r2_keys.cleanKeys()
        if (legacy.isNotEmpty()) return legacy
        return row.content_url
            .map(::resolveMediaUrl)
            .filter(String::isNotBlank)
            .distinctConsecutive()
    }

    private fun List<String>.distinctConsecutive(): List<String> {
        val out = mutableListOf<String>()
        forEach { if (out.isEmpty() || out.last() != it) out.add(it) }
        return out
    }

    private fun List<String>?.cleanKeys(): List<String> {
        val out = mutableListOf<String>()
        this?.forEach { key ->
            val url = resolveMediaUrl(key)
            if (url.isNotBlank() && (out.isEmpty() || out.last() != url)) out.add(url)
        }
        return out
    }

    private fun resolveMediaUrl(raw: String?): String {
        val key = raw?.trim().orEmpty()
        if (key.isEmpty()) return ""
        // Normalize like the site: protocol-relative and bare host/path become https URLs.
        // (containsMatchIn, not matches: the site's JS uses a partial test().)
        val normalized = when {
            key.startsWith("http://") || key.startsWith("https://") -> key
            key.startsWith("//") -> "https:$key"
            hostPathRegex.containsMatchIn(key) -> "https://$key"
            else -> key
        }
        // Already CDN or direct R2 URLs pass through.
        if (normalized.startsWith(cdnUrl) || ".r2.dev" in normalized || "pub-" in normalized) return normalized
        if ("cdn.doki-labs.net" in normalized) return normalized.replace("cdn.doki-labs.net", "cdn.reizucomics.com")
        if ("doki-labs.net" in normalized) return normalized.replace("doki-labs.net", "reizucomics.com")
        if ("cdn.reizucomics.com" in normalized || "reizucomics.com" in normalized) return normalized
        // Supabase storage URLs are rewritten to the CDN (the bucket itself may not be public).
        if (supabaseHost in normalized &&
            ("/storage/v1/object/public/" in normalized || "/storage/v1/object/sign/" in normalized)
        ) {
            val path = normalized
                .substringAfter("/storage/v1/object/public/")
                .substringAfter("/storage/v1/object/sign/")
                .substringBefore("?")
            if (path.isNotBlank() && path != normalized) return "$cdnUrl/$path"
            return normalized
        }
        if (key.startsWith("supabase:")) {
            val path = key.removePrefix("supabase:").trimStart('/')
            return if (path.isNotBlank()) "$cdnUrl/$path" else ""
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return normalized
        return "$cdnUrl/${key.trimStart('/')}"
    }

    private val supabaseHost = "zrydzcqveheuwviyerto.supabase.co"

    private val hostPathRegex = Regex("^[a-z0-9.-]+\\.[a-z]{2,}(/|$)", RegexOption.IGNORE_CASE)
}
