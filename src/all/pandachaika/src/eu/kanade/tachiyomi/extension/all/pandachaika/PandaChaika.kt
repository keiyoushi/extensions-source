package eu.kanade.tachiyomi.extension.all.pandachaika

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PandaChaika(
    override val lang: String = "all",
    private val searchLang: String = "",
) : HttpSource() {

    override val name = "PandaChaika"

    override val baseUrl = "https://panda.chaika.moe"

    private val baseApiUrl = "$baseUrl/api"

    override val supportsLatest = true

    override val client = network.cloudflareClient
        .newBuilder()
        .addInterceptor(::Intercept)
        .build()

    private val json: Json by injectLazy()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/search/?tags=$searchLang&sort=rating&apply=&json=&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val library = response.parseAs<ArchiveResponse>()

        val mangas = library.archives.map(::popularManga)

        val hasNextPage = library.has_next

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularManga(hentai: LongArchive) = SManga.create().apply {
        setUrlWithoutDomain(hentai.url)
        title = hentai.title
        thumbnail_url = hentai.thumbnail
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/search/?tags=$searchLang&sort=public_date&apply=&json=&page=$page", headers)
    }

    private fun parsePageRange(query: String, minPages: Int = 1, maxPages: Int = 9999): Pair<Int, Int> {
        val num = query.filter(Char::isDigit).toIntOrNull() ?: -1
        fun limitedNum(number: Int = num): Int = number.coerceIn(minPages, maxPages)

        if (num < 0) return minPages to maxPages
        return when (query.firstOrNull()) {
            '<' -> 1 to if (query[1] == '=') limitedNum() else limitedNum(num + 1)
            '>' -> limitedNum(if (query[1] == '=') num else num + 1) to maxPages
            '=' -> when (query[1]) {
                '>' -> limitedNum() to maxPages
                '<' -> 1 to limitedNum(maxPages)
                else -> limitedNum() to limitedNum()
            }
            else -> limitedNum() to limitedNum()
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val tags = mutableListOf<String>()
            var reason = ""
            var uploader = ""
            var pagesMin = 1
            var pagesMax = 9999

            tags.add(searchLang)

            filters.forEach {
                when (it) {
                    is SortFilter -> {
                        addQueryParameter("sort", it.getValue())
                        addQueryParameter("asc_desc", if (it.state!!.ascending) "asc" else "desc")
                    }

                    is SelectFilter -> {
                        addQueryParameter("category", it.vals[it.state].replace("All", ""))
                    }

                    is PageFilter -> {
                        if (it.state.isNotBlank()) {
                            val (min, max) = parsePageRange(it.state)
                            pagesMin = min
                            pagesMax = max
                        }
                    }

                    is TextFilter -> {
                        if (it.state.isNotEmpty()) {
                            when (it.type) {
                                "reason" -> reason = it.state
                                "uploader" -> uploader = it.state
                                else -> {
                                    it.state.split(",").filter(String::isNotBlank).map { tag ->
                                        val trimmed = tag.trim()
                                        tags.add(
                                            buildString {
                                                if (trimmed.startsWith('-')) append("-")
                                                append(it.type)
                                                if (it.type.isNotBlank()) append(":")
                                                append(trimmed.lowercase().removePrefix("-"))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            addPathSegment("search")
            addQueryParameter("title", query)
            addQueryParameter("tags", tags.joinToString())
            addQueryParameter("filecount_from", pagesMin.toString())
            addQueryParameter("filecount_to", pagesMax.toString())
            addQueryParameter("reason", reason)
            addQueryParameter("uploader", uploader)
            addQueryParameter("page", page.toString())
            addQueryParameter("apply", "")
            addQueryParameter("json", "")
        }.build()

        return GET(url, headers)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseApiUrl?archive=${manga.url.substringAfter("/archive/").substringBeforeLast("/")}", headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseApiUrl?archive=${manga.url.substringAfter("/archive/").substringBeforeLast("/")}", headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseApiUrl?archive=${chapter.url.substringAfter("/archive/").substringBeforeLast("/")}", headers)
    }

    override fun getFilterList() = getFilters()

    private val dateReformat = SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
    private fun <T> Collection<T>.emptyToNull(): Collection<T>? {
        return this.ifEmpty { null }
    }

    // Details
    private fun Archive.toSManga() = SManga.create().apply {
        fun filterTags(include: String = "", exclude: List<String> = emptyList()): String? {
            return tags.filter { it.startsWith("$include:") && exclude.none { substring -> it.startsWith("$substring:") } }
                .emptyToNull()
                ?.joinToString { it.substringAfter(":").replace("_", " ") }
        }
        fun getReadableSize(bytes: Double): String {
            return when {
                bytes >= 300 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
                bytes >= 100 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
                bytes >= 1024 -> "${"%.2f".format(bytes / (1024.0))} KB"
                else -> "$bytes B"
            }
        }
        val groups = filterTags("group")
        val artists = filterTags("artist")
        val publishers = filterTags("publisher")
        val male = filterTags("male")
        val female = filterTags("female")
        val others = filterTags(exclude = listOf("female", "male", "artist", "publisher", "group", "parody"))
        val parodies = filterTags("parody")
        title = this@toSManga.title
        url = download.substringBefore("/download/")
        author = (groups ?: artists)
        artist = artists
        genre = listOfNotNull(male, female, others).joinToString().takeIf { it.isNotEmpty() }
        description = buildString {
            append("Uploader: ", uploader, "\n")
            publishers?.let {
                append("Publishers: ", it, "\n")
            }
            parodies?.let {
                append("Parodies: ", it, "\n")
            }
            male?.let {
                append("Male Tags: ", it, "\n")
            }
            female?.let {
                append("Female Tags: ", it, "\n")
            }
            others?.let {
                append("Other Tags: ", it, "\n\n")
            }

            title_jpn?.let { append("Japanese Title: ", it, "\n") }
            append("Pages: ", filecount, "\n")
            append("File Size: ", getReadableSize(filesize), "\n")

            try {
                append("Posted: ", dateReformat.format(Date(posted * 1000)), "\n")
            } catch (_: Exception) {}
        }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<Archive>().toSManga()
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val archive = response.parseAs<Archive>()

        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = archive.download.substringBefore("/download/")
                date_upload = archive.posted * 1000
            },
        )
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url
    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        fun List<String>.sort() = this.sortedWith(compareBy(CASE_INSENSITIVE_ORDER) { it })
        val archive = response.parseAs<Archive>()
        val url = "$baseUrl${archive.download}".toHttpUrl()

        val fileType = getZipType(url)

        val remoteZip = ZipHandler(url, client, headers, fileType).populate()
        val fileListing = remoteZip.files().sort()

        val files = remoteZip.toJson()
        return fileListing.mapIndexed { index, filename ->
            Page(index, imageUrl = "https://127.0.0.1/#$filename&$files")
        }
    }

    private fun getZipType(url: HttpUrl): String {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .method("HEAD", null)
            .build()

        val contentLength = client.newCall(request).execute().header("content-length")!!.toBigInteger()

        return if (contentLength > Int.MAX_VALUE.toBigInteger()) "zip64" else "zip"
    }
    private fun Intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url.toString()
        return if (url.startsWith("https://127.0.0.1/#")) {
            val fragment = url.toHttpUrl().fragment!!
            val remoteZip = fragment.substringAfter("&").parseAs<RemoteZip>()
            val filename = fragment.substringBefore("&")

            val byteArray = remoteZip.fetch(filename, client)
            var type = filename.substringAfterLast('.').lowercase()
            type = if (type == "jpg") "jpeg" else type

            Response.Builder().body(byteArray.toResponseBody("image/$type".toMediaType()))
                .request(chain.request())
                .protocol(Protocol.HTTP_1_0)
                .code(200)
                .message("")
                .build()
        } else {
            chain.proceed(chain.request())
        }
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }

    private fun RemoteZip.toJson(): String {
        return json.encodeToString(this)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
