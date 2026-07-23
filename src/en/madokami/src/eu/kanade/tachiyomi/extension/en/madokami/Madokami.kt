package eu.kanade.tachiyomi.extension.en.madokami

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
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class Madokami :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val relatedCache = ConcurrentHashMap<String, List<SManga>>()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val credential = Credentials.basic(preferences.getString("username", "")!!, preferences.getString("password", "")!!)
            val authenticatedRequest = chain.request().newBuilder()
                .header("Authorization", credential)
                .build()
            val response = chain.proceed(authenticatedRequest)
            if (response.code == 401) {
                throw IOException("You are currently logged out.\nGo to Extensions > Details to input your credentials.")
            }
            response
        }
    }

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
        if (isArchiveUrl(url.toString())) return null

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
        val cached = relatedCache[manga.url]
        if (cached != null) return cached

        val detailsUrl = getMangaDetailsUrl(manga)
        val chaptersUrl = (baseUrl + "/" + manga.url.trimStart('/')).toHttpUrl()

        return client.get(detailsUrl).use { response ->
            val document = response.asJsoup()
            val related = parseRelatedManga(document, detailsUrl.toString()).toMutableList()

            if (detailsUrl != chaptersUrl) {
                mangaFromUrl(detailsUrl.toString())?.let { related.add(it) }
            }

            var current = chaptersUrl
            while (current.pathSize > detailsUrl.pathSize) {
                current = current.newBuilder().removePathSegment(current.pathSize - 1).build()
                if (current != detailsUrl) {
                    mangaFromUrl(current.toString())?.let { related.add(it) }
                }
            }

            related.distinctBy { it.url }.also { relatedCache[manga.url] = it }
        }
    }

    private fun mangaFromElement(element: Element): SManga? = mangaFromUrl(element.absUrl("href"))

    private fun mangaFromUrl(url: String): SManga? {
        if (url.isEmpty() || isArchiveUrl(url)) return null
        val httpUrl = try {
            url.toHttpUrl()
        } catch (_: Exception) {
            return null
        }
        val segments = httpUrl.pathSegments
        if (segments.isEmpty()) return null

        return SManga.create().apply {
            setUrlWithoutDomain(url)
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
                relatedCache[manga.url] = parseRelatedManga(document, detailsUrl.toString())
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
                client.get(detailsUrl).use { it ->
                    val document = it.asJsoup()
                    val related = parseRelatedManga(document, detailsUrl.toString()).toMutableList()
                    if (detailsUrl != chaptersUrl) {
                        mangaFromUrl(detailsUrl.toString())?.let { related.add(it) }
                    }
                    var current = chaptersUrl
                    while (current.pathSize > detailsUrl.pathSize) {
                        current = current.newBuilder().removePathSegment(current.pathSize - 1).build()
                        if (current != detailsUrl) {
                            mangaFromUrl(current.toString())?.let { related.add(it) }
                        }
                    }
                    relatedCache[manga.url] = related.distinctBy { it.url }
                    parseMangaDetails(document)
                }
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

    private fun parseChapterList(document: Document): List<SChapter> = document.select("table#index-table > tbody > tr > td:nth-child(6) > a").map { element ->
        val el = element.parent()!!.parent()!!
        SChapter.create().apply {
            url = "/reader" + el.select("td:nth-child(6) a").attr("abs:href")
                .substringAfter("/reader")
            name = el.select("td:nth-child(1) a").text()
            val date = el.select("td:nth-child(3)").text()
            date_upload = if (date.endsWith("ago")) {
                val splitDate = date.split(" ")
                val cal = Calendar.getInstance()
                val amount = splitDate[0].toInt()
                when {
                    splitDate[1].startsWith("min") -> cal.add(Calendar.MINUTE, -amount)
                    splitDate[1].startsWith("sec") -> cal.add(Calendar.SECOND, -amount)
                    splitDate[1].startsWith("hour") -> cal.add(Calendar.HOUR, -amount)
                }
                cal.time.time
            } else {
                dateFormat.tryParse(date)
            }
        }
    }.reversed()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
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
    }

    private fun isArchiveUrl(url: String): Boolean {
        val path = url.toHttpUrl().encodedPath.lowercase(Locale.ROOT)
        return ARCHIVE_EXTENSIONS.any { path.endsWith(it) }
    }
}
