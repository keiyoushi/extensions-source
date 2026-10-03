package eu.kanade.tachiyomi.extension.es.lectormonline

import android.content.SharedPreferences
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

@Source
abstract class MangoLibreria :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            // The image proxy rejects requests with the main site's Referer header.
            val newRequest = if (request.url.host != baseUrl.toHttpUrl().host) {
                request.newBuilder()
                    .removeHeader("Referer")
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
        val raw = body.string()
        val json = Json.parseToJsonElement(raw) as? JsonObject
            ?: return MangasPage(emptyList(), false)
        val nodes = json["nodes"] as? JsonArray ?: return MangasPage(emptyList(), false)
        for (node in nodes) {
            val data = (node as? JsonObject)?.get("data") as? JsonArray ?: continue
            // data[0] is the request-params echo; the page payload is the object holding "comics".
            val rootIndex = data.indexOfFirst { it is JsonObject && "comics" in it }
            if (rootIndex == -1) continue
            val root = resolveRef(data, rootIndex) as? JsonObject ?: continue
            val comics = root["comics"] as? JsonArray ?: continue
            val page = (root["page"] as? JsonPrimitive)?.intOrNull ?: 1
            val totalPages = (root["totalPages"] as? JsonPrimitive)?.intOrNull ?: page
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
        is JsonArray -> (getOrNull(1) as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    private fun JsonObject.toSManga(): SManga? {
        val name = this["name"].asText()
        val urlPath = this["urlPath"].asText()
        if (name.isNullOrBlank() || urlPath.isNullOrBlank()) return null
        return SManga.create().apply {
            title = name
            url = urlPath
            // The data holds direct origin URLs (blocked without the proxy),
            // the site builds the proxied form client-side, so do the same.
            thumbnail_url = (this@toSManga["urlCover"].asText() ?: this@toSManga["coverImage"].asText())?.let(::proxyImageUrl)
        }
    }

    // ====================== Details & Chapters ======================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { fetchMangaDetails(manga) }
        val chapterList = async { fetchChapterList(manga) }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val doc = client.get(baseUrl + manga.url).asJsoup()
        return SManga.create().apply {
            url = manga.url
            title = doc.selectFirst("h1")?.text().orEmpty()
            thumbnail_url = doc.selectFirst("div.relative.mx-auto img")?.attr("abs:src")
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
        val raw = client.get(dataUrl).body.string()
        val json = Json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        val nodes = json["nodes"] as? JsonArray ?: return emptyList()
        for (node in nodes) {
            val data = (node as? JsonObject)?.get("data") as? JsonArray ?: continue
            val comicIdx = ((data.getOrNull(0) as? JsonObject)?.get("comic") as? JsonPrimitive)?.intOrNull
                ?: data.indexOfFirst { it is JsonObject && "comicScans" in it }
            if (comicIdx == -1) continue
            val comic = resolveRef(data, comicIdx) as? JsonObject ?: continue
            val scans = comic["comicScans"] as? JsonArray ?: continue
            val chapters = mutableListOf<SChapter>()
            scans.forEach { scan ->
                val scanObj = scan as? JsonObject ?: return@forEach
                val groupName = (scanObj["scanGroup"] as? JsonObject)?.get("name").asText()
                val chapterArr = scanObj["chapters"] as? JsonArray ?: return@forEach
                chapterArr.forEach { ch ->
                    val c = ch as? JsonObject ?: return@forEach
                    val path = c["chapterPath"].asText() ?: return@forEach
                    val number = c["chapterNumber"].asText()
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
            preferences.rememberScanlators(sorted.mapNotNull { it.scanlator })
            return sorted.filterBlacklistedScanlators(preferences.scanlatorBlacklist())
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
        val raw = client.get(dataUrl).body.string()
        val json = Json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        val nodes = json["nodes"] as? JsonArray ?: return emptyList()
        for (node in nodes) {
            val data = (node as? JsonObject)?.get("data") as? JsonArray ?: continue
            val chapterIdx = ((data.getOrNull(0) as? JsonObject)?.get("chapter") as? JsonPrimitive)?.intOrNull
                ?: data.indexOfFirst { it is JsonObject && ("url_pages" in it || "urlPages" in it) }
            if (chapterIdx == -1) continue
            val chapterObj = resolveRef(data, chapterIdx) as? JsonObject ?: continue
            val pages = (chapterObj["url_pages"] as? JsonArray ?: chapterObj["urlPages"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.filterNot { it.contains("banner", ignoreCase = true) }
                ?: continue
            if (pages.isEmpty()) continue
            return pages.mapIndexed { index, imageUrl ->
                Page(index, imageUrl = proxyImageUrl(imageUrl))
            }
        }
        return emptyList()
    }

    private fun proxyImageUrl(url: String): String = if (url.startsWith("https://mango-proxy-image.zincbaq.workers.dev/?url=")) {
        url
    } else {
        "https://mango-proxy-image.zincbaq.workers.dev/?url=${URLEncoder.encode(url, "UTF-8")}"
    }

    private fun parseStatus(text: String?): Int = when {
        text == null -> SManga.UNKNOWN
        "ONGOING" in text.uppercase() -> SManga.ONGOING
        "COMPLETED" in text.uppercase() || "FINALIZADO" in text.uppercase() -> SManga.COMPLETED
        "HIATUS" in text.uppercase() || "PAUSA" in text.uppercase() -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================ Preferences ============================
    // Scan groups are per-title and the site exposes no global group list,
    // so known names are collected from chapter lists as the user browses.
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = SCANLATOR_BLACKLIST_PREF
            title = "Scanlator blacklist"
            summary = "Hide chapters from the selected scanlators. The list fills up automatically as you browse titles."
            val scanlators = preferences.knownScanlatorNames().toTypedArray()
            entries = scanlators
            entryValues = scanlators
            setDefaultValue(emptySet<String>())
        }.also(screen::addPreference)
    }

    private fun SharedPreferences.scanlatorBlacklist(): Set<String> = getStringSet(SCANLATOR_BLACKLIST_PREF, emptySet()).orEmpty()
        .mapTo(mutableSetOf()) { it.trim().lowercase() }

    private fun SharedPreferences.knownScanlatorNames(): List<String> = getStringSet(KNOWN_SCANLATORS_PREF, emptySet()).orEmpty()
        .sortedBy { it.lowercase() }

    private fun SharedPreferences.rememberScanlators(names: List<String>) {
        val known = getStringSet(KNOWN_SCANLATORS_PREF, emptySet()).orEmpty()
        val new = names.filter { it.isNotBlank() }.toSet() - known
        if (new.isEmpty()) return
        edit().putStringSet(KNOWN_SCANLATORS_PREF, known + new).apply()
    }

    private fun List<SChapter>.filterBlacklistedScanlators(blacklist: Set<String>): List<SChapter> = filterNot { it.scanlator?.trim()?.lowercase()?.let(blacklist::contains) == true }

    companion object {
        private const val MAX_RETRIES = 3
        private const val SCANLATOR_BLACKLIST_PREF = "scanlator_blacklist_pref"
        private const val KNOWN_SCANLATORS_PREF = "known_scanlators_pref"
    }
}
