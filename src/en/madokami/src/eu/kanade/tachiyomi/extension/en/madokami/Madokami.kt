package eu.kanade.tachiyomi.extension.en.madokami

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.zip.readZipEntry
import keiyoushi.zip.zipDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Madokami :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest = false

    @SuppressLint("NewApi")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val authenticatedRequest = chain.request().newBuilder()
                .header("Authorization", getAuthCredential())
                .build()
            val response = chain.proceed(authenticatedRequest)
            if (response.code == 401) {
                response.close()
                throw IOException("You are currently logged out.\nGo to Extensions > Details to input your credentials.")
            }
            response
        }
        addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val fragment = url.fragment

            if (fragment != null && (url.encodedPath.endsWith(".zip", true) || url.encodedPath.endsWith(".cbz", true))) {
                val parts = fragment.split("|")
                if (parts.size == 4) {
                    val zipUrl = url.newBuilder().fragment(null).build().toString()
                    val entry = keiyoushi.zip.Entry(
                        name = parts[0],
                        method = parts[1].toInt(),
                        compressedSize = parts[2].toLong(),
                        localHeaderOffset = parts[3].toLong(),
                    )

                    val source = network.client.readZipEntry(zipUrl, entry, request.headers)
                    val mediaType = getMediaType(entry.name)

                    return@addInterceptor Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(source.buffer().asResponseBody(mediaType))
                        .build()
                }
            }
            chain.proceed(request)
        }
    }

    private fun getAuthCredential(): String {
        val username = preferences.getString("username", "")!!
        val password = preferences.getString("password", "")!!

        if (username.isBlank() || password.isBlank()) {
            throw IOException("Username or password cannot be empty.\nGo to Extensions > Details to input your credentials.")
        }

        return Credentials.basic(username, password)
    }

    private fun getAuthHeaders() = headers.newBuilder().set("Authorization", getAuthCredential()).build()

    private fun getMediaType(name: String) = when {
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".avif", true) -> "image/avif"
        else -> "image/jpeg"
    }.toMediaType()

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/recent").use { response ->
        val document = response.asJsoup()
        val mangas = document.select("table.mobile-files-table tbody tr td:nth-child(1) a:nth-child(1)").mapNotNull { element ->
            mangaFromElement(element)
        }
        MangasPage(mangas, hasNextPage = false)
    }

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = when {
            query.startsWith("genre:", true) || query.startsWith("Genres:", true) ->
                baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("search")
                    .addPathSegment("genre")
                    .addPathSegment(query.substringAfter(":").trim())
                    .build()
            query.startsWith("category:", true) || query.startsWith("Tags:", true) || query.startsWith("Tag:", true) ->
                baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("search")
                    .addPathSegment("category")
                    .addPathSegment(query.substringAfter(":").trim())
                    .build()
            else -> "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
        }

        return client.get(url).use { response ->
            val document = response.asJsoup()
            val mangas = document.select("div.container table tbody tr td:nth-child(1) a:nth-child(1), table.mobile-files-table tbody tr td:nth-child(1) a:nth-child(1)").mapNotNull { element ->
                mangaFromElement(element)
            }
            MangasPage(mangas, hasNextPage = false)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (isArchiveUrl(url)) return null

        val manga = SManga.create().apply {
            setUrlWithoutDomain(url.toString())
        }

        return try {
            fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        } catch (_: Exception) {
            null
        }
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val detailsUrl = getMangaDetailsUrl(manga)
        val chaptersUrl = (baseUrl + "/" + manga.url.trimStart('/')).toHttpUrl()

        return client.get(detailsUrl).use { response ->
            val document = response.asJsoup()
            val related = parseRelatedManga(document, detailsUrl.toString()).toMutableList()

            if (detailsUrl != chaptersUrl) {
                mangaFromUrl(detailsUrl)?.let { related.add(it) }
            }

            var current = chaptersUrl
            while (current.pathSize > detailsUrl.pathSize) {
                current = current.newBuilder().removePathSegment(current.pathSize - 1).build()
                if (current != detailsUrl) {
                    mangaFromUrl(current)?.let { related.add(it) }
                }
            }

            related.distinctBy { it.url }
        }
    }

    private fun mangaFromElement(element: Element): SManga? = mangaFromUrl(element.absUrl("href"))

    private fun mangaFromUrl(url: String): SManga? {
        if (url.isEmpty()) return null
        val httpUrl = try {
            url.toHttpUrl()
        } catch (_: Exception) {
            return null
        }
        return mangaFromUrl(httpUrl)
    }

    private fun mangaFromUrl(url: HttpUrl): SManga? {
        if (isArchiveUrl(url)) return null
        val segments = url.pathSegments
        if (segments.isEmpty()) return null

        return SManga.create().apply {
            setUrlWithoutDomain(url.toString())
            description = segments.last()
            var i = segments.lastIndex
            while (i > 0 && segments[i].startsWith("!")) {
                i--
            }
            title = segments[i]
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsUrl = getMangaDetailsUrl(manga)
        val chaptersUrl = (baseUrl + "/" + manga.url.trimStart('/')).toHttpUrl()

        if (fetchDetails && fetchChapters && detailsUrl == chaptersUrl) {
            client.get(detailsUrl).use { response ->
                val document = response.asJsoup()
                return@coroutineScope SMangaUpdate(
                    manga = parseMangaDetails(document).apply {
                        this.url = manga.url
                        initialized = true
                    },
                    chapters = parseChapterList(document),
                )
            }
        }

        val detailsDeferred = if (fetchDetails) {
            async {
                client.get(detailsUrl).use { parseMangaDetails(it.asJsoup()) }
            }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async {
                client.get(chaptersUrl).use { parseChapterList(it.asJsoup()) }
            }
        } else {
            null
        }

        val updatedManga = detailsDeferred?.await()?.apply {
            this.url = manga.url
            initialized = true
        } ?: manga

        val updatedChapters = chaptersDeferred?.await() ?: chapters

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun getMangaDetailsUrl(manga: SManga): HttpUrl {
        val url = (baseUrl + "/" + manga.url.trimStart('/')).toHttpUrl()
        if (url.pathSize > 5 && url.pathSegments[0] == "Manga" && url.pathSegments[1].length == 1) {
            val builder = url.newBuilder()
            repeat(url.pathSize - 5) {
                builder.removePathSegment(5)
            }
            return builder.build()
        }
        if (url.pathSize > 2 && url.pathSegments[0] == "Raws") {
            val builder = url.newBuilder()
            var i = url.pathSize - 1
            while (url.pathSegments[i].startsWith("!") && i >= 2) {
                builder.removePathSegment(i)
                i--
            }
            return builder.build()
        }
        return url
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        author = document.select("a[itemprop=\"author\"]").joinToString { it.text() }
        val genres = document.select("div.genres a[itemprop=\"genre\"]").map { "Genres:${it.text()}" }
        val categories = document.select("div.genres[itemprop=\"keywords\"] a.tag-category").map { "Tags:${it.text()}" }
        genre = (genres + categories).joinToString()
        status = if (document.select("span.scanstatus").text() == "Yes") SManga.COMPLETED else SManga.UNKNOWN
        thumbnail_url = document.select("div.manga-info img[itemprop=\"image\"]").attr("abs:src")
    }

    private fun parseRelatedManga(document: Document, url: String): List<SManga> {
        val related = mutableListOf<SManga>()

        document.select("table#index-table tbody tr td:nth-child(1) a").forEach { element ->
            val href = element.absUrl("href")
            if (href.isEmpty() || href == url || isArchiveUrl(href) || element.text() == "..") {
                return@forEach
            }
            mangaFromUrl(href)?.let { related.add(it) }
        }

        document.select("div.manga-info a[href*=\"/Manga/\"], div.manga-info a[href*=\"/Raws/\"]").forEach { element ->
            val href = element.absUrl("href")
            if (href.isEmpty() || href == url || isArchiveUrl(href)) return@forEach
            mangaFromUrl(href)?.let { related.add(it) }
        }

        return related.distinctBy { it.url }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/${manga.url.trimStart('/')}"

    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select("table#index-table > tbody > tr").mapNotNull { row ->
            val fileLink = row.selectFirst("td:nth-child(1) a") ?: return@mapNotNull null
            val fileName = fileLink.text()
            val readerLink = row.selectFirst("td:nth-child(6) a")
            val isZip = fileName.endsWith(".zip", true) || fileName.endsWith(".cbz", true)

            if (!isZip && readerLink == null) return@mapNotNull null

            SChapter.create().apply {
                url = if (isZip) {
                    fileLink.absUrl("href").substringAfter(baseUrl)
                } else {
                    "/reader" + readerLink!!.absUrl("href").substringAfter("/reader")
                }
                name = normalizeName(fileName)
                date_upload = parseChapterDate(row.select("td:nth-child(3)").text())
            }
        }.reversed()
    }

    private fun normalizeName(name: String): String {
        val fileName = name.substringBeforeLast(".").replace("_", " ")
        val volMatch = VOLUME_REGEX.find(fileName)
        val chMatch = CHAPTER_REGEX.find(fileName)

        val vol = volMatch?.groupValues?.get(1)
        val ch = chMatch?.groupValues?.get(1)

        val baseName = when {
            ch != null && vol != null -> "Ch. $ch (Vol. $vol)"
            ch != null -> "Ch. $ch"
            vol != null -> "Vol. $vol"
            else -> {
                val rawMatch = RAW_NUMBER_REGEX.find(fileName)
                if (rawMatch != null) "Ch. ${rawMatch.groupValues[1]}" else name
            }
        }

        val metadata = METADATA_REGEX.findAll(fileName)
            .map { it.groupValues[1] }
            .filter { tag ->
                val lowerTag = tag.lowercase()
                if (FIX_REGEX.matches(lowerTag)) return@filter true
                val tagVol = VOLUME_REGEX.find(tag)?.groupValues?.get(1)
                val tagCh = CHAPTER_REGEX.find(tag)?.groupValues?.get(1)
                (tagVol == null || tagVol != vol) && (tagCh == null || tagCh != ch)
            }
            .joinToString(" ") { if (vol != null || FIX_REGEX.matches(it.lowercase())) "($it)" else "[$it]" }

        return if (metadata.isNotEmpty()) "$baseName $metadata" else baseName
    }

    @SuppressLint("NewApi")
    private fun parseChapterDate(dateString: String): Long {
        if (dateString.endsWith("ago")) {
            val splitDate = dateString.split(" ")
            val amount = splitDate[0].toLongOrNull() ?: return 0L
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val result = when {
                splitDate[1].startsWith("min") -> now.minusMinutes(amount)
                splitDate[1].startsWith("sec") -> now.minusSeconds(amount)
                splitDate[1].startsWith("hour") -> now.minusHours(amount)
                else -> now
            }
            return result.toInstant().toEpochMilli()
        }
        return try {
            LocalDateTime.parse(dateString, dateTimeFormatter).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.endsWith(".zip", true) || chapter.url.endsWith(".cbz", true)) {
            return getZipPageList(chapter)
        }

        require(chapter.url.startsWith("/")) { "Refresh chapter list" }
        return client.get(baseUrl + chapter.url).use { response ->
            val document = response.asJsoup()
            val element = document.select("div#reader")
            val path = element.attr("data-path")
            val files = element.attr("data-files").parseAs<JsonArray>()
            files.mapIndexed { index, file ->
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("manga.madokami.al")
                    .addPathSegments("reader/image")
                    .addQueryParameter("path", path)
                    .addQueryParameter("file", file.jsonPrimitive.content)
                    .build()
                val pageUrl = url.toString()
                Page(index, url = pageUrl, imageUrl = pageUrl)
            }
        }
    }

    private suspend fun getZipPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        val url = baseUrl + chapter.url
        val directory = try {
            network.client.zipDirectory(url, getAuthHeaders())
        } catch (e: IOException) {
            if (e.message?.contains("Content-Range") == true) {
                throw IOException("Refresh episode list and try again", e)
            }
            throw e
        }

        directory.entries
            .filter { isImage(it.name) }
            .sortedBy { it.name }
            .mapIndexed { index, entry ->
                // Store entry metadata in fragment to avoid re-reading CD per page
                val fragment = "${entry.name}|${entry.method}|${entry.compressedSize}|${entry.localHeaderOffset}"
                val pageUrl = url.toHttpUrl().newBuilder().fragment(fragment).build().toString()
                Page(index, url = pageUrl, imageUrl = pageUrl)
            }
    }

    private fun isImage(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".avif")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "username"
            title = "Username"
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = "password"
            title = "Password"

            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.let(screen::addPreference)
    }

    companion object {
        private val ARCHIVE_EXTENSIONS = listOf(".zip", ".cbz", ".rar", ".cbr", ".7z", ".cb7", ".tar", ".cbt")
        private val VOLUME_REGEX = Regex("""(?i)\b(?:v|vol)(?:\.|ume)?\s?(\d+)\b""")
        private val CHAPTER_REGEX = Regex("""(?i)\b(?:c|ch)(?:\.|apter)?\s?(\d+(?:-\d+)?)\b""")
        private val RAW_NUMBER_REGEX = Regex("""\b(\d{3,})\b""")
        private val METADATA_REGEX = Regex("""[\[(]([^])]+)[])]""")
        private val FIX_REGEX = Regex("""(?i)f\d+""")
    }

    private fun isArchiveUrl(url: String): Boolean {
        val httpUrl = try {
            url.toHttpUrl()
        } catch (_: Exception) {
            return ARCHIVE_EXTENSIONS.any { url.lowercase(Locale.ROOT).endsWith(it) }
        }
        return isArchiveUrl(httpUrl)
    }

    private fun isArchiveUrl(url: HttpUrl): Boolean {
        val path = url.encodedPath.lowercase(Locale.ROOT)
        return ARCHIVE_EXTENSIONS.any { path.endsWith(it) }
    }
}
