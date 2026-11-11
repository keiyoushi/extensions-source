package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class MikoRoku : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder =
        rateLimit(10, 1.seconds, 300.milliseconds)
            .readTimeout(30, TimeUnit.SECONDS)

    private val chapterImages = ConcurrentHashMap<String, List<String>>()

    override suspend fun getPopularManga(page: Int): MangasPage =
        fetchCatalog().sortedByDescending { it.rating }.toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        fetchCatalog().toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.normalize()
        if (normalizedQuery.isEmpty() && query.isNotBlank()) return MangasPage(emptyList(), false)

        val sort = filters.firstInstanceOrNull<SortFilter>()?.state ?: 0
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.state?.let { GENRES[it] } ?: "Semua"

        var entries = fetchCatalog()
        if (normalizedQuery.isNotEmpty()) {
            entries = entries.filter { entry ->
                entry.title.normalize().contains(normalizedQuery) ||
                    entry.altTitle.split(";").any { it.normalize().contains(normalizedQuery) }
            }
        }
        if (genre != "Semua") {
            entries = entries.filter { entry ->
                entry.genres.any { it.equals(genre, ignoreCase = true) }
            }
        }
        entries = when (sort) {
            1 -> entries.sortedBy { it.title.lowercase() }
            2 -> entries.sortedByDescending { it.title.lowercase() }
            3 -> entries
            else -> entries.sortedByDescending { it.rating }
        }
        return entries.toMangasPage()
    }

    private fun List<CatalogEntry>.toMangasPage() = MangasPage(map { it.toSManga() }, false)

    override fun getFilterList(data: JsonElement?): FilterList =
        FilterList(SortFilter(), GenreFilter())

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val slug = when {
            url.pathSegments.getOrNull(0) == "series" -> url.pathSegments.getOrNull(1)
            else -> url.queryParameter("slug")
        } ?: return null

        return buildManga(slug).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = slugFromMangaUrl(manga.url) ?: throw Exception("Invalid manga URL: ${manga.url}")
        val details = if (fetchDetails) buildManga(slug) else manga
        val chapterList = if (fetchChapters) fetchChapterList(slug, details.title) else chapters
        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = slugFromMangaUrl(manga.url) ?: return baseUrl + manga.url
        return "$baseUrl/series/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val match = SERIES_CHAPTER_URL_REGEX.find(chapter.url)
        if (match != null) return "$baseUrl/series/${match.groupValues[1]}"
        return baseUrl + chapter.url
    }

    private fun slugFromMangaUrl(url: String): String? {
        if (url.startsWith("/series/")) {
            return url.removePrefix("/series/").substringBefore("/").takeIf { it.isNotBlank() }
        }
        if (url.startsWith("/detail.html")) {
            return "$baseUrl$url".toHttpUrlOrNull()?.queryParameter("slug")
        }
        // Bare slug (no leading slash, no extra path segments).
        if (!url.startsWith("/") && !url.contains("/")) {
            return url.takeIf { it.isNotBlank() }
        }
        return null
    }

    private suspend fun buildManga(slug: String): SManga {
        val fields = fetchFirestoreDoc(slug)?.fields.orEmpty()
        val entry = fetchCatalog().find { it.slug == slug }
        return SManga.create().apply {
            url = "/series/$slug"
            title = fields.getString("title").nonBlank()
                ?: entry?.title
                ?: throw Exception("Manga tidak ditemukan")
            author = fields.getString("author").nonBlank() ?: entry?.author.nonBlank()
            artist = fields.getString("artist").nonBlank() ?: entry?.artist.nonBlank()
            description = fields.getString("desc").nonBlank()?.let { Jsoup.parseBodyFragment(it).text() }
                ?: entry?.desc.nonBlank()
            genre = fields.getStringList("genres").takeIf { it.isNotEmpty() }?.joinToString()
                ?: entry?.genres?.takeIf { it.isNotEmpty() }?.joinToString()
            status = (fields.getString("status") ?: entry?.status).toMangaStatus()
            thumbnail_url = fields.getString("img").nonBlank() ?: entry?.img.nonBlank()
        }
    }

    private fun String?.toMangaStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(slug: String, title: String): List<SChapter> {
        val byNum = sortedMapOf<Float, SChapter>(reverseOrder())

        addFromSiteApi(slug, title, byNum)
        if (byNum.isEmpty()) {
            val short = title.substringBefore("~").substringBefore("(").trim()
            if (short.length >= 4 && short != title) addFromSiteApi(slug, short, byNum)
        }
        if (byNum.isEmpty()) addFromListen(slug, byNum)
        if (byNum.isEmpty()) {
            addFromBloggerFeed(BLOGGER_SV1, slug, title, byNum)
        }
        if (byNum.isEmpty()) {
            addFromBloggerFeed(BLOGGER_SV2, slug, title, byNum)
        }
        return byNum.values.toList()
    }

    private suspend fun addFromSiteApi(
        slug: String,
        apiTitle: String,
        byNum: MutableMap<Float, SChapter>,
    ) {
        val url = "$baseUrl/api/blogger/chapters?title=${URLEncoder.encode(apiTitle, "UTF-8")}"
        val chapters = try {
            client.get(url, Fetch.XHR).parseAs<ApiChaptersResponse>().chapters
        } catch (e: Exception) {
            return
        }
        for (c in chapters) {
            val num = c.num.toFloatOrNull()
                ?: c.title.chapterNumber().toFloat().takeIf { it >= 0 } ?: continue
            val images = c.images.map { it.replace("/s1600/", "/s0/") }
            if (images.isNotEmpty()) chapterImages["$slug|$num"] = images
            if (!byNum.containsKey(num)) {
                byNum[num] = SChapter.create().apply {
                    this.url = "/series/$slug/chapter/$num"
                    name = c.title.ifBlank { "Chapter $num" }
                    chapter_number = num
                    date_upload = Instant.tryParse(c.createdAt)
                }
            }
        }
    }

    private suspend fun addFromListen(slug: String, byNum: MutableMap<Float, SChapter>) {
        val parent = "$FIRESTORE_DB/documents/manga/$slug"
        val docs = firestoreListenDocs(parent, "chapters")
        for (doc in docs) {
            val f = doc.optJSONObject("fields") ?: continue
            if (fsBool(f, "isDraft")) continue
            val docId = doc.optString("name").substringAfterLast("/")
            val numRaw = fsStr(f, "num").ifBlank { fsStr(f, "chapter") }
                .ifBlank { docId }.ifBlank { fsStr(f, "order") }
                .ifBlank { fsStr(f, "title") }
            val num = numRaw.toFloatOrNull()
                ?: fsStr(f, "title").chapterNumber().toFloat().takeIf { it >= 0 } ?: continue
            val images = fsStrList(f, "images").map { it.replace("/s1600/", "/s0/") }
            if (images.isNotEmpty()) chapterImages["$slug|$num"] = images
            if (!byNum.containsKey(num)) {
                val dateMillis = f.optJSONObject("date")?.optString("integerValue")
                    ?.toLongOrNull() ?: 0L
                byNum[num] = SChapter.create().apply {
                    url = "/series/$slug/chapter/$num"
                    name = fsStr(f, "title").ifBlank { "Chapter $num" }
                    chapter_number = num
                    date_upload = dateMillis
                }
            }
        }
    }

    private suspend fun addFromBloggerFeed(
        feedBase: String,
        slug: String,
        title: String,
        byNum: MutableMap<Float, SChapter>,
    ) {
        searchBlogger("$feedBase/feeds/posts/default", title, 500)
            .mapNotNull { it.toSChapter(slug, title) }
            .forEach { ch ->
                val num = ch.name.chapterNumber().toFloat().takeIf { it >= 0 } ?: return@forEach
                ch.chapter_number = num
                if (!byNum.containsKey(num)) byNum[num] = ch
            }
    }

    // Firestore WebChannel Listen: the same transport the site's own Firebase
    // SDK uses. The REST API answers 429 for this project, Listen works
    // without an API key. Open POST returns the session id in the "c" message
    // and the gsessionid in the X-HTTP-Session-Id response header; the
    // backchannel GET streams length-prefixed JSON frames with the documents.
    private suspend fun firestoreListenDocs(
        parent: String,
        collectionId: String,
    ): List<JSONObject> {
        val docs = mutableListOf<JSONObject>()
        try {
            val dbEnc = URLEncoder.encode(FIRESTORE_DB, "UTF-8")
            val reqJson = JSONObject()
                .put("database", FIRESTORE_DB)
                .put(
                    "addTarget",
                    JSONObject()
                        .put(
                            "query",
                            JSONObject()
                                .put(
                                    "structuredQuery",
                                    JSONObject()
                                        .put(
                                            "from",
                                            JSONArray().put(
                                                JSONObject().put("collectionId", collectionId),
                                            ),
                                        )
                                        .put(
                                            "orderBy",
                                            JSONArray().put(
                                                JSONObject()
                                                    .put(
                                                        "field",
                                                        JSONObject().put("fieldPath", "__name__"),
                                                    )
                                                    .put("direction", "ASCENDING"),
                                            ),
                                        ),
                                )
                                .put("parent", parent),
                        )
                        .put("targetId", 2),
                )
                .toString()
            val initHeaders =
                "X-Goog-Api-Client:gl-js/ fire/11.0.2\r\n" +
                    "Content-Type:text/plain\r\n" +
                    "X-Firebase-GMPID:1:271920446600:web:974b11c707ebc11a7bf33d\r\n"
            val openBody = "headers=" + URLEncoder.encode(initHeaders, "UTF-8") +
                "&count=1&ofs=0&req0___data__=" + URLEncoder.encode(reqJson, "UTF-8")
            val rid = 10000 + (Math.random() * 90000).toInt()
            val openUrl = "$FIRESTORE_LISTEN?VER=8&database=$dbEnc&RID=$rid" +
                "&CVER=22&X-HTTP-Session-Id=gsessionid&zx=${randomBase36(12)}&t=1"
            val session = withContext(Dispatchers.IO) {
                val req = Request.Builder().url(openUrl)
                    .post(openBody.toRequestBody("text/plain".toMediaType()))
                    .header("X-Client-Protocol", "webchannel")
                    .fetch(Fetch.NONE)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext Pair(null, null)
                    val g = resp.header("X-HTTP-Session-Id")
                    val b = resp.body?.string() ?: ""
                    val m = Regex("\"c\",\"([A-Za-z0-9_\\-]+)\"").find(b)
                    Pair(m?.groupValues?.getOrNull(1), g)
                }
            }
            val sid = session.first
            val gsession = session.second
            if (sid.isNullOrBlank() || gsession.isNullOrBlank()) return docs
            val backUrl = "$FIRESTORE_LISTEN?gsessionid=$gsession&VER=8&database=$dbEnc" +
                "&RID=rpc&SID=$sid&AID=0&CI=0&TYPE=xmlhttp&zx=${randomBase36(12)}&t=1"
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(backUrl)
                    .header("X-Client-Protocol", "webchannel")
                    .fetch(Fetch.NONE)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext
                    val src = resp.body?.source() ?: return@withContext
                    var done = false
                    while (!done) {
                        val lenLine = try {
                            var line = src.readUtf8LineStrict()
                            while (line.isBlank()) line = src.readUtf8LineStrict()
                            line
                        } catch (e: Exception) {
                            break
                        }
                        val len = lenLine.trim().toLongOrNull() ?: break
                        if (len <= 0 || len > 20_000_000) break
                        val frame = try {
                            src.readUtf8(len)
                        } catch (e: Exception) {
                            break
                        }
                        try {
                            val arr = JSONArray(frame)
                            for (i in 0 until arr.length()) {
                                val msg = arr.optJSONArray(i) ?: continue
                                if (msg.length() < 2) continue
                                val payload = msg.opt(1) ?: continue
                                val items = when (payload) {
                                    is JSONArray -> payload
                                    is JSONObject -> JSONArray().put(payload)
                                    else -> continue
                                }
                                for (j in 0 until items.length()) {
                                    val item = items.optJSONObject(j) ?: continue
                                    if (item.has("documentChange")) {
                                        val doc = item.optJSONObject("documentChange")
                                            ?.optJSONObject("document")
                                        if (doc != null) docs += doc
                                    } else if (item.has("targetChange")) {
                                        val tc = item.optJSONObject("targetChange")
                                        if (tc?.optString("targetChangeType") == "CURRENT") {
                                            val ids = tc.optJSONArray("targetIds")
                                            if (ids != null) {
                                                for (k in 0 until ids.length()) {
                                                    if (ids.optInt(k) == 2) done = true
                                                }
                                            }
                                        }
                                    } else if (item.has("__sm__")) {
                                        done = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Broken frame: skip it and read the next one.
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Listen failed: the caller falls back to the Blogger feed.
        }
        return docs
    }

    private fun randomBase36(n: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder()
        repeat(n) { sb.append(chars[(Math.random() * chars.length).toInt()]) }
        return sb.toString()
    }

    private fun fsStr(f: JSONObject, name: String): String =
        f.optJSONObject(name)?.optString("stringValue") ?: ""

    private fun fsBool(f: JSONObject, name: String): Boolean =
        f.optJSONObject(name)?.optBoolean("booleanValue") ?: false

    private fun fsStrList(f: JSONObject, name: String): List<String> {
        val values = f.optJSONObject(name)?.optJSONObject("arrayValue")
            ?.optJSONArray("values") ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until values.length()) {
            val s = values.getJSONObject(i).optString("stringValue")
            if (s.isNotBlank()) out += s
        }
        return out
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        SERIES_CHAPTER_URL_REGEX.find(chapter.url)?.let { match ->
            val slug = match.groupValues[1]
            val num = match.groupValues[2].toFloatOrNull()
                ?: throw Exception("Unsupported chapter URL: ${chapter.url}")
            chapterImages["$slug|$num"]?.let { return it.toPages() }

            val entry = fetchCatalog().find { it.slug == slug }
            if (entry != null) fetchChapterList(slug, entry.title)
            chapterImages["$slug|$num"]?.let { return it.toPages() }

            val title = entry?.title ?: return emptyList()
            return fetchBloggerChapterPages(title, chapter.name)
        }

        POST_URL_REGEX.find(chapter.url)?.let { match ->
            return fetchBloggerPostPages(match.groupValues[2])
        }

        CHAPTER_URL_REGEX.find(chapter.url)?.let { match ->
            val slug = match.groupValues[1]
            val key = match.groupValues[2]
            fetchListenChapterImages(slug, key)?.let { return it.toPages() }

            val title = fetchCatalog().find { it.slug == slug }?.title ?: return emptyList()
            return fetchBloggerChapterPages(title, chapter.name)
        }

        throw Exception("Unsupported chapter URL: ${chapter.url}")
    }

    private suspend fun fetchListenChapterImages(slug: String, key: String): List<String>? {
        val parent = "$FIRESTORE_DB/documents/manga/$slug"
        val docs = firestoreListenDocs(parent, "chapters")
        for (doc in docs) {
            if (doc.optString("name").substringAfterLast("/") != key) continue
            val f = doc.optJSONObject("fields") ?: continue
            if (fsBool(f, "isDraft")) continue
            val images = fsStrList(f, "images").map { it.replace("/s1600/", "/s0/") }
            if (images.isNotEmpty()) return images
        }
        return null
    }

    private suspend fun fetchBloggerPostPages(postId: String): List<Page> {
        val url = "https://www.mikodrive.my.id/feeds/posts/default/$postId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .build()

        return client.get(url, Fetch.XHR).parseAs<BloggerEntryResponse>().entry.imageUrls().toPages()
    }

    private suspend fun fetchBloggerChapterPages(mangaTitle: String, chapterName: String): List<Page> {
        val label = chapterName.chapterLabel() ?: return emptyList()
        val number = label.chapterNumber()

        return searchBlogger("https://www.mikodrive.my.id/feeds/posts/default", "$mangaTitle $label", 5)
            .firstOrNull { it.chapterLabel(mangaTitle)?.chapterNumber() == number }
            ?.imageUrls()
            .orEmpty()
            .toPages()
    }

    private fun List<String>.toPages(): List<Page> = mapIndexed { index, url -> Page(index, imageUrl = url) }

    private suspend fun fetchCatalog(): List<CatalogEntry> {
        val site = try {
            client.get("$baseUrl/catalog/manga.json", Fetch.XHR).parseAs<List<CatalogEntry>>()
        } catch (e: Exception) {
            null
        }
        if (site != null) return site
        return client.get(
            "https://raw.githubusercontent.com/moemaomao/mymangadata/main/all-manga.json",
            Fetch.XHR,
        ).parseAs<List<CatalogEntry>>()
    }

    private suspend fun fetchFirestoreDoc(slug: String): FirestoreMap? {
        val url = "https://firestore.googleapis.com/v1/projects/mikoroku/databases/(default)/documents/manga/$slug"

        val response = client.get(url, Fetch.XHR, ensureSuccess = false)
        if (response.code != 200) {
            response.close()
            return null
        }
        return response.parseAs<FirestoreMap>()
    }

    private suspend fun searchBlogger(feedUrl: String, query: String, maxResults: Int): List<BloggerEntry> {
        searchBloggerWithQuery(feedUrl, query, maxResults).takeIf { it.isNotEmpty() }?.let { return it }

        // Blogger's q= doesn't match "2" with "II", so retry with the
        // trailing number/roman numeral stripped (e.g. "isekai furin 2" -> "isekai furin").
        val stripped = query.replace(Regex("\\s+(\\d+|[ivxl]+)$", RegexOption.IGNORE_CASE), "").trim()
        if (stripped.isNotBlank() && stripped != query) {
            searchBloggerWithQuery(feedUrl, stripped, maxResults).takeIf { it.isNotEmpty() }?.let { return it }
        }

        // For conversion mismatches (e.g. "konten" vs "tamashiten"),
        // trying progressively shorter queries. The titleWordsMatch filter in toSChapter will reject false positives.
        val words = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (n in listOf(4, 3)) {
            if (words.size > n) {
                val short = words.take(n).joinToString(" ")
                searchBloggerWithQuery(feedUrl, short, maxResults).takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return emptyList()
    }

    private suspend fun searchBloggerWithQuery(feedUrl: String, query: String, maxResults: Int): List<BloggerEntry> {
        val url = feedUrl.toHttpUrl().newBuilder()
            .addQueryParameter("alt", "json")
            .addQueryParameter("max-results", maxResults.toString())
            .addQueryParameter("q", query)
            .build()

        return client.get(url, Fetch.XHR).parseAs<BloggerFeedResponse>().feed.entry
    }

    private fun String.normalize(): String = normalized()

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

    private class SortFilter : Filter.Select<String>(
        "Order By",
        arrayOf("Populer", "A-Z", "Z-A", "Terbaru"),
    )

    private class GenreFilter : Filter.Select<String>("Genre", GENRES.toTypedArray())

    companion object {
        private val SERIES_CHAPTER_URL_REGEX = """/series/([^/]+)/chapter/([\d.]+)""".toRegex()
        private val CHAPTER_URL_REGEX = """/manga/([^/]+)/chapter/([^/]+)""".toRegex()
        private val POST_URL_REGEX = """/manga/([^/]+)/post/([^/]+)""".toRegex()

        private const val FIRESTORE_DB = "projects/mikoroku/databases/(default)"
        private const val FIRESTORE_LISTEN =
            "https://firestore.googleapis.com/google.firestore.v1.Firestore/Listen/channel"

        private const val BLOGGER_SV1 = "https://www.mikodrive.my.id"
        private const val BLOGGER_SV2 = "https://www.yomidays.my.id"

        private val GENRES = listOf(
            "Semua", "Action", "Adult", "Adventure", "Cheating", "Comedy",
            "Crossdressing", "Doujinshi", "Drama", "Ecchi", "Elf", "Erotica",
            "Fantasy", "Gore", "Harem", "Hentai", "Horror", "Incest", "Isekai",
            "Magic", "Mature", "Mecha", "Milf", "Mystery", "Psychological",
            "Reincarnation", "Romance", "School", "School Life", "Sci-Fi",
            "Seinen", "Sexual Violence", "Shota", "Shounen", "Slice of Life",
            "Smut", "Strategy Game", "Supernatural", "Survival", "Yandere",
            "Zombies",
        )
    }
}
