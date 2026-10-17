package eu.kanade.tachiyomi.extension.es.lectormonline

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
import keiyoushi.utils.getArrayOrNull
import keiyoushi.utils.getIntOrNull
import keiyoushi.utils.getObjectOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

@Source
abstract class MangoLibreria : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            // The image CDN blocks requests without a browser Sec-Fetch header and
            // serves a placeholder when a Referer is present, so both are fixed here.
            val newRequest = if (request.url.host != baseUrl.toHttpUrl().host) {
                request.newBuilder()
                    .removeHeader("Referer")
                    .header("Sec-Fetch-Dest", "image")
                    .build()
            } else {
                request
            }
            // Retry transient failures (e.g. stalled connections) before giving up.
            var lastException: IOException? = null
            repeat(MAX_RETRIES) { attempt ->
                try {
                    return@addInterceptor chain.proceed(newRequest)
                } catch (e: IOException) {
                    lastException = e
                    if (attempt == MAX_RETRIES - 1) throw e
                }
            }
            throw lastException!!
        }

    // ============================== Popular ==============================
    // The list pages render card <img> tags without src (covers are set by JS
    // during hydration), so covers are read from SvelteKit's data endpoint instead.
    override suspend fun getPopularManga(page: Int): MangasPage = client.get(dataJsonUrl(page, sort = "views")).parseAsDataJson()

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(dataJsonUrl(page)).parseAsDataJson()

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.isBlank()) {
        client.get(dataJsonUrl(page, sort = "views")).parseAsDataJson()
    } else {
        client.get(dataJsonUrl(page, query = query)).parseAsDataJson()
    }

    private fun dataJsonUrl(page: Int, sort: String? = null, query: String? = null) = "$baseUrl/comics/__data.json".toHttpUrl().newBuilder().apply {
        addQueryParameter("x-sveltekit-invalidated", "01")
        addQueryParameter("page", page.toString())
        sort?.let { addQueryParameter("sort", it) }
        query?.takeIf { it.isNotBlank() }?.let { addQueryParameter("q", it.trim()) }
    }.build()

    private fun Response.parseAsDataJson(): MangasPage {
        val json = parseAs<JsonObject>()
        val nodes = json.getArrayOrNull("nodes") ?: return MangasPage(emptyList(), false)
        for (node in nodes) {
            val data = (node as? JsonObject)?.getArrayOrNull("data") ?: continue
            // data[0] is the request-params echo; the page payload is the object holding "comics".
            val rootIndex = data.indexOfFirst { it is JsonObject && "comics" in it }
            if (rootIndex == -1) continue
            val root = resolveRef(data, rootIndex) as? JsonObject ?: continue
            val comics = root.getArrayOrNull("comics") ?: continue
            val page = root.getIntOrNull("page") ?: 1
            val totalPages = root.getIntOrNull("totalPages") ?: page
            val list = comics.mapNotNull { (it as? JsonObject)?.toSManga() }
            return MangasPage(list, page < totalPages)
        }
        return MangasPage(emptyList(), false)
    }

    // SvelteKit serializes page data with devalue: a flat array where object
    // values and array items are integer indexes pointing back into the array.
    // The references are resolved manually here since the index graph can't be
    // expressed as plain @Serializable field mappings. Resolved indexes are
    // cached so shared references aren't rebuilt repeatedly.
    private fun resolveRef(data: JsonArray, index: Int, depth: Int = 0, cache: MutableMap<Int, JsonElement> = mutableMapOf()): JsonElement {
        if (depth > 50) return JsonNull
        cache[index]?.let { return it }
        val slot = data.getOrNull(index) ?: return JsonNull
        val resolved = when (slot) {
            is JsonObject -> buildJsonObject {
                slot.forEach { (k, v) ->
                    val ref = (v as? JsonPrimitive)?.intOrNull
                    put(k, if (ref != null) resolveRef(data, ref, depth + 1, cache) else v)
                }
            }
            is JsonArray -> buildJsonArray {
                slot.forEach { v ->
                    val ref = (v as? JsonPrimitive)?.intOrNull
                    add(if (ref != null) resolveRef(data, ref, depth + 1, cache) else v)
                }
            }
            else -> slot
        }
        cache[index] = resolved
        return resolved
    }

    // Devalue encodes some values (e.g. dates) as ["Date", "..."] arrays
    // instead of plain primitives; this reads either form as text.
    private fun JsonElement?.asText(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonArray -> getOrNull(1)?.stringOrNull
        else -> null
    }

    private fun JsonObject.toSManga(): SManga? {
        val name = getStringOrNull("name")
        val urlPath = getStringOrNull("urlPath")
        if (name.isNullOrBlank() || urlPath.isNullOrBlank()) return null
        return SManga.create().apply {
            title = name
            url = urlPath
            // The data holds direct CDN URLs; the site's own image proxy is gone.
            thumbnail_url = getStringOrNull("urlCover") ?: getStringOrNull("coverImage")
        }
    }

    // ====================== Details & Chapters ======================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val doc = client.get(baseUrl + manga.url).asJsoup()
        return SManga.create().apply {
            url = manga.url
            title = doc.selectFirst("h1")?.text().orEmpty()
            thumbnail_url = doc.selectFirst("div.relative.mx-auto img")?.attr("abs:src")?.let { url ->
                // Rendered HTML still wraps covers in the site's (now dead) image proxy.
                val proxy = "https://mango-proxy-image.zincbaq.workers.dev/?url="
                if (url.startsWith(proxy)) URLDecoder.decode(url.removePrefix(proxy), "UTF-8") else url
            }
            description = doc.selectFirst("div.mt-6 > p")?.text()
            genre = doc.select("a[href*=\"genres=\"]").joinToString { it.text() }
            status = parseStatus(doc.selectFirst("p.text-xs.uppercase")?.text())
        }
    }

    // The detail HTML only renders the 10 most recent chapters per scan group;
    // the complete per-group lists live in the detail page's data payload.
    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.substringAfter("/comics/", "").substringBefore("?").substringBefore("/")
        val dataUrl = if (slug.isBlank()) {
            baseUrl + manga.url
        } else {
            "$baseUrl/comics/$slug/__data.json".toHttpUrl().newBuilder()
                .addQueryParameter("x-sveltekit-invalidated", "01")
                .build()
                .toString()
        }
        val json = client.get(dataUrl).parseAs<JsonObject>()
        val nodes = json.getArrayOrNull("nodes") ?: return emptyList()
        for (node in nodes) {
            val data = (node as? JsonObject)?.getArrayOrNull("data") ?: continue
            val comicIdx = (data.getOrNull(0) as? JsonObject)?.getIntOrNull("comic")
                ?: data.indexOfFirst { it is JsonObject && "comicScans" in it }
            if (comicIdx == -1) continue
            val comic = resolveRef(data, comicIdx) as? JsonObject ?: continue
            val scans = comic.getArrayOrNull("comicScans") ?: continue
            val chapters = mutableListOf<SChapter>()
            scans.forEach { scan ->
                val scanObj = scan as? JsonObject ?: return@forEach
                val groupName = scanObj.getObjectOrNull("scanGroup")?.getStringOrNull("name")
                val chapterArr = scanObj.getArrayOrNull("chapters") ?: return@forEach
                chapterArr.forEach { ch ->
                    val c = ch as? JsonObject ?: return@forEach
                    val path = c.getStringOrNull("chapterPath") ?: return@forEach
                    val number = c.getStringOrNull("chapterNumber")
                    chapters += SChapter.create().apply {
                        url = path
                        name = "Capítulo ${number ?: "?"}"
                        chapter_number = number?.toFloatOrNull() ?: -1f
                        date_upload = Instant.tryParse(c["releaseDate"].asText()).takeIf { it != 0L }
                            ?: Instant.tryParse(c["createdAt"].asText())
                        scanlator = groupName
                    }
                }
            }
            val sorted = chapters.sortedByDescending { it.chapter_number }
            return sorted
        }
        return emptyList()
    }

    // =============================== Pages ===============================
    // The reader HTML only inlines real URLs for the first ~14 images; the
    // rest are rendered without src and filled in client-side from the
    // chapter's data payload, so the complete page list is read from there.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = chapter.url.substringAfter(baseUrl)
        val dataUrl = "$baseUrl$path/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("x-sveltekit-invalidated", "01")
            .build()
        val json = client.get(dataUrl).parseAs<JsonObject>()
        val nodes = json.getArrayOrNull("nodes") ?: return emptyList()
        for (node in nodes) {
            val data = (node as? JsonObject)?.getArrayOrNull("data") ?: continue
            val chapterIdx = (data.getOrNull(0) as? JsonObject)?.getIntOrNull("chapter")
                ?: data.indexOfFirst { it is JsonObject && ("url_pages" in it || "urlPages" in it) }
            if (chapterIdx == -1) continue
            val chapterObj = resolveRef(data, chapterIdx) as? JsonObject ?: continue
            val pages = (chapterObj.getArrayOrNull("url_pages") ?: chapterObj.getArrayOrNull("urlPages"))
                ?.mapNotNull { it.stringOrNull }
                ?.filterNot { it.contains("banner", ignoreCase = true) }
                ?: continue
            if (pages.isEmpty()) continue
            return pages.mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
        }
        return emptyList()
    }

    private fun parseStatus(text: String?): Int = when {
        text == null -> SManga.UNKNOWN
        "ONGOING" in text.uppercase() -> SManga.ONGOING
        "COMPLETED" in text.uppercase() || "FINALIZADO" in text.uppercase() -> SManga.COMPLETED
        "HIATUS" in text.uppercase() || "PAUSA" in text.uppercase() -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val MAX_RETRIES = 3
    }
}
