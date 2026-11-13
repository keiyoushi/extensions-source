package eu.kanade.tachiyomi.extension.ja.pdftoshokan

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
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLDecoder
import java.text.Normalizer
import kotlin.time.Duration.Companion.seconds

@Source
abstract class PdfToshokan : KeiSource() {

    private val preferences by getPreferencesLazy()

    private val store by lazy { VolumeStore(applicationContext.cacheDir.resolve("source_$id/volumes")) }

    private val baseHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(PageInterceptor(store))
        .rateLimit(permits = 1, period = 3.seconds) { it.encodedPath == "/decode.php" }
        .rateLimit(permits = 2, period = 1.seconds) { it.host == baseHost }

    private val noRedirectClient by lazy { client.newBuilder().followRedirects(false).build() }

    override suspend fun getPopularManga(page: Int): MangasPage = getListing("popular", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getListing("", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return searchIndex(page, query)

        val category = filters.firstInstance<CategoryFilter>().toUriPart()
        return if (category.isNotEmpty()) {
            getListing("category/$category", page)
        } else {
            getListing(filters.firstInstance<SortFilter>().toUriPart(), page)
        }
    }

    private suspend fun getListing(path: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (path.isNotEmpty()) addPathSegments(path)
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        }.build()
        val document = client.get(url).asJsoup()
        val hasNextPage = document.select("a[href*=/page/${page + 1}]")
            .any { it.attr("href").substringAfterLast("/page/").trimEnd('/') == "${page + 1}" }
        return MangasPage(parseListing(document), hasNextPage)
    }

    private fun parseListing(document: Document): List<SManga> {
        val scripts = document.select("script:not([src])").joinToString("\n") { it.data() }
        val (linkKey, linkAttr) = HES_CONFIG_REGEX.find(scripts)!!.destructured
        val covers = COVER_STYLE_REGEX.find(scripts)?.let { m ->
            val css = xorHex(m.groupValues[2], m.groupValues[1])
            CSS_RULE_REGEX.findAll(css).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        }.orEmpty()

        // Honeypots: a hidden article whose link leads to an URL that gets the client IP banned
        val styles = document.select("style").joinToString("\n") { it.data() }
        val hiddenClasses = HIDDEN_CLASS_REGEX.findAll(styles).map { it.groupValues[1] }.toSet()
        val trapIds = TRAP_REGEX.findAll(scripts).map { it.groupValues[1] }.toSet() + TRAP_ID

        return document.select("article").mapNotNull { article ->
            if (article.classNames().any { it in hiddenClasses }) return@mapNotNull null
            val link = article.selectFirst(".entry-title a[$linkAttr]") ?: return@mapNotNull null
            val id = MANGA_ID_REGEX.find(xorHex(link.attr(linkAttr), linkKey))?.groupValues?.get(1)
                ?: return@mapNotNull null
            if (id in trapIds) return@mapNotNull null

            SManga.create().apply {
                url = id
                title = link.text()
                thumbnail_url = article.selectFirst(".entry-content p > *")?.let { covers[it.tagName().lowercase()] }
            }
        }
    }

    private suspend fun searchIndex(page: Int, query: String): MangasPage {
        val needle = normalize(query.trim())
        val matches = buildList {
            var file = 1
            while (true) {
                val response = client.get("$baseUrl/search$file.txt", ensureSuccess = false)
                if (!response.isSuccessful) {
                    response.close()
                    break
                }
                val lines = response.use { it.body.string() }.lines().filter { it.isNotBlank() }
                lines.forEach { line ->
                    val parts = line.split(":::")
                    if (parts.size >= 2 && parts[1] != TRAP_ID) {
                        val normalized = normalize(parts[0])
                        if (normalized.contains(needle)) add(Triple(parts[0], parts[1], normalized))
                    }
                }
                if (lines.size < SEARCH_FILE_LINES) break
                file++
            }
        }.sortedWith(
            compareBy<Triple<String, String, String>> { if (it.third.startsWith(needle)) 0 else 1 }
                .thenBy { it.third.length },
        )

        val entries = matches.drop((page - 1) * SEARCH_PAGE_SIZE).take(SEARCH_PAGE_SIZE).map { (title, id) ->
            SManga.create().apply {
                url = id
                this.title = title
            }
        }
        return MangasPage(entries, matches.size > page * SEARCH_PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHost) return null
        val id = MANGA_ID_REGEX.find(url.encodedPath)?.groupValues?.get(1) ?: return null
        if (id == TRAP_ID) return null
        return parseDetails(fetchMangaPage(id), id)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = fetchMangaPage(manga.url)
        val chapterList = volumeOptions(document).map { label ->
            SChapter.create().apply {
                url = "${manga.url}/$label"
                name = if (label.toFloatOrNull() != null) "第${label}巻" else label
                chapter_number = label.toFloatOrNull() ?: -1f
            }
        }.reversed()
        return SMangaUpdate(parseDetails(document, manga.url), chapterList)
    }

    private suspend fun fetchMangaPage(id: String): Document = client.get("$baseUrl/manga/$id", cacheControl = CacheControl.FORCE_NETWORK).asJsoup()

    private fun parseDetails(document: Document, id: String) = SManga.create().apply {
        url = id
        title = document.selectFirst("h1.entry-title")!!.text()
        thumbnail_url = document.selectFirst(".entry-content img")?.absUrl("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        description = document.select(".entry-content > p")
            .filter { it.selectFirst("img, select, input") == null }
            .joinToString("\n\n") { it.text() }
            .ifBlank { null }
        genre = document.select(".cat-links a").joinToString { it.text() }.ifBlank { null }
        // Tags hold the author names; their href is a honeypot, so only the text is used
        author = document.select(".tags-links a").joinToString { it.text() }.ifBlank { null }
        status = SManga.UNKNOWN
    }

    private fun volumeOptions(document: Document) = document.select("select.vi13 option, #selector option").map { it.text() }.distinct()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url.substringBefore("/")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringBefore("/")
        val label = chapter.url.substringAfter("/")

        val cached = preferences.getString(fileKey(id, label), null)?.toHttpUrl()
        val (fileUrl, index) = cached?.let { url ->
            runCatching { url to loadIndex(url) }.getOrNull()
        } ?: run {
            val url = resolveFile(id, label, guessFromFolder = cached == null)
            url to loadIndex(url)
        }
        return List(index.pages.size) { Page(it, imageUrl = "$fileUrl#$it") }
    }

    private suspend fun loadIndex(url: HttpUrl): VolumeIndex = withContext(Dispatchers.IO) {
        store.index(url) { client.newCall(Request.Builder().url(it).headers(headers).build()).execute() }.second
    }

    /**
     * decode.php is heavily rate limited, so resolved files are remembered. All volumes of a title
     * live in the same folder as `<volume>.pdf`, so once one is known the others can usually be
     * guessed without asking decode.php again.
     */
    private suspend fun resolveFile(id: String, label: String, guessFromFolder: Boolean): HttpUrl {
        if (guessFromFolder) {
            preferences.getString(folderKey(id), null)?.let { folder ->
                val guess = folder.toHttpUrl().newBuilder().addPathSegment("$label.pdf").build()
                if (exists(guess)) return guess.also { remember(id, label, it) }
            }
        }

        val document = fetchMangaPage(id)
        val decodeUrl = document.select("select.vi13 option, #selector option")
            .firstOrNull { it.text() == label }
            ?.absUrl("value")
            ?: throw IOException("Volume not found")
        return decode(decodeUrl, "$baseUrl/manga/$id").also { remember(id, label, it) }
    }

    private suspend fun decode(url: String, referer: String): HttpUrl {
        val navigationHeaders = navigationHeaders(referer)
        repeat(DECODE_ATTEMPTS) { attempt ->
            val response = noRedirectClient.get(
                url,
                navigationHeaders,
                cacheControl = CacheControl.FORCE_NETWORK,
                ensureSuccess = false,
            )
            response.use {
                when {
                    it.isRedirect -> {
                        val viewer = it.request.url.resolve(it.header("Location")!!)!!
                        viewer.queryParameter("file")?.let { file -> return file.toHttpUrl() }
                    }

                    it.isSuccessful -> {
                        val viewer = it.request.url.queryParameter("file")
                            ?: VIEWER_FILE_REGEX.find(it.body.string())?.groupValues?.get(1)
                                ?.let { file -> URLDecoder.decode(file, "UTF-8") }
                        if (viewer != null) return viewer.toHttpUrl()
                    }

                    it.code != 429 -> throw IOException("HTTP ${it.code}")
                }
            }
            if (attempt < DECODE_ATTEMPTS - 1) delay(5.seconds * (attempt + 1))
        }
        throw IOException("Site is rate limiting volume requests, try again in a minute")
    }

    private fun navigationHeaders(referer: String): Headers = headers.newBuilder()
        .removeAll("Origin")
        .set("Referer", referer)
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Sec-Fetch-User", "?1")
        .set("Upgrade-Insecure-Requests", "1")
        .build()

    private suspend fun exists(url: HttpUrl): Boolean {
        val rangeHeaders = headers.newBuilder().set("Range", "bytes=0-0").build()
        return runCatching {
            client.get(url, rangeHeaders, CacheControl.FORCE_NETWORK, ensureSuccess = false).use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun remember(id: String, label: String, file: HttpUrl) {
        val folder = file.newBuilder().removePathSegment(file.pathSize - 1).addPathSegment("").build()
        preferences.edit()
            .putString(fileKey(id, label), file.toString())
            .putString(folderKey(id), folder.toString())
            .apply()
    }

    private fun fileKey(id: String, label: String) = "file_$id/$label"

    private fun folderKey(id: String) = "folder_$id"

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("キーワード検索時はフィルターは無視されます"),
        Filter.Header("カテゴリー選択時は並び替えは無視されます"),
        SortFilter(),
        CategoryFilter(),
    )

    private fun xorHex(hex: String, key: String): String = buildString {
        for (i in 0 until hex.length / 2) {
            append((hex.substring(i * 2, i * 2 + 2).toInt(16) xor key[i % key.length].code).toChar())
        }
    }

    // Same normalization as the site's own search: NFKC, katakana -> hiragana, lowercase
    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase()
        .map { if (it in 'ァ'..'ヶ') it - 0x60 else it }
        .joinToString("")

    companion object {
        private const val TRAP_ID = "4973674"
        private const val SEARCH_FILE_LINES = 10000
        private const val SEARCH_PAGE_SIZE = 40
        private const val DECODE_ATTEMPTS = 4

        private val HES_CONFIG_REGEX = Regex("""key\s*:\s*"([^"]+)"\s*,\s*randAttr\s*:\s*"([^"]+)"""")
        private val COVER_STYLE_REGEX = Regex("""let\s+k\s*=\s*"([^"]+)"\s*,\s*x\s*=\s*"([0-9a-f]+)"""")
        private val CSS_RULE_REGEX = Regex("""([a-zA-Z][\w-]*)\s*\{[^}]*url\(\s*['"]?([^'")]+)""")
        private val HIDDEN_CLASS_REGEX = Regex("""\.(category-\d+)\s*\{\s*display\s*:\s*none""")
        private val TRAP_REGEX = Regex("""location\.href\s*=\s*['"]/manga/(\d+)""")
        private val MANGA_ID_REGEX = Regex("""/manga/(\d+)""")
        private val VIEWER_FILE_REGEX = Regex("""[?&]file=([^"'&\s<>]+)""")
    }
}
