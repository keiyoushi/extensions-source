package eu.kanade.tachiyomi.extension.all.pandachaika

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.math.BigInteger

class PandaChaika(
    override val lang: String = "all",
    private val searchLang: String = "",
) : HttpSource() {

    override val name = "PandaChaika"

    override val baseUrl = "https://panda.chaika.moe"

    private val baseSearchUrl = "$baseUrl/search"

    override val supportsLatest = true

    override val client = network.cloudflareClient
        .newBuilder()
        .addInterceptor(::Intercept)
        .build()

    private val json: Json by injectLazy()

    private val fakkuRegex = Regex("""(?:https?://)?(?:www\.)?fakku\.net/hentai/""")
    private val ehentaiRegex = Regex("""(?:https?://)?e-hentai\.org/g/""")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseSearchUrl/?tags=$searchLang&sort=rating&apply=&json=&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseSearchUrl/?tags=$searchLang&sort=public_date&apply=&json=&page=$page", headers)
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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH).toInt()
                client.newCall(GET("$baseUrl/api?archive=$id", headers))
                    .asObservable()
                    .map { response ->
                        searchMangaByIdParse(response, id)
                    }
            }
            query.startsWith(PREFIX_EHEN_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_EHEN_ID_SEARCH).replace(ehentaiRegex, "")
                val baseLink = "https://e-hentai.org/g/"
                val fullLink = baseSearchUrl.toHttpUrl().newBuilder().apply {
                    addQueryParameter("qsearch", baseLink + id)
                    addQueryParameter("json", "")
                }.build()
                client.newCall(GET(fullLink, headers))
                    .asObservableSuccess()
                    .map {
                        val archive = it.parseAs<ArchiveResponse>().archives.getOrNull(0)?.toSManga() ?: throw Exception("Not Found")
                        MangasPage(listOf(archive), false)
                    }
            }
            query.startsWith(PREFIX_FAK_ID_SEARCH) -> {
                val slug = query.removePrefix(PREFIX_FAK_ID_SEARCH).replace(fakkuRegex, "")
                val baseLink = "https://www.fakku.net/hentai/"
                val fullLink = baseSearchUrl.toHttpUrl().newBuilder().apply {
                    addQueryParameter("qsearch", baseLink + slug)
                    addQueryParameter("json", "")
                }.build()
                client.newCall(GET(fullLink, headers))
                    .asObservableSuccess()
                    .map {
                        val archive = it.parseAs<ArchiveResponse>().archives.getOrNull(0)?.toSManga() ?: throw Exception("Not Found")
                        MangasPage(listOf(archive), false)
                    }
            }
            query.startsWith(PREFIX_SOURCE_SEARCH) -> {
                val url = query.removePrefix(PREFIX_SOURCE_SEARCH)
                client.newCall(GET("$baseSearchUrl/?qsearch=$url&json=", headers))
                    .asObservableSuccess()
                    .map {
                        val archive = it.parseAs<ArchiveResponse>().archives.getOrNull(0)?.toSManga() ?: throw Exception("Not Found")
                        MangasPage(listOf(archive), false)
                    }
            }

            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response, id: Int = 0): MangasPage {
        val title = response.parseAs<Archive>().title
        val fullLink = baseSearchUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("qsearch", title)
            addQueryParameter("json", "")
        }.build()
        val archive = client.newCall(GET(fullLink, headers))
            .execute()
            .parseAs<ArchiveResponse>().archives
            .find {
                it.id == id
            }
            ?.toSManga()
            ?: throw Exception("Invalid ID")

        return MangasPage(listOf(archive), false)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val library = response.parseAs<ArchiveResponse>()

        val mangas = library.archives.map(LongArchive::toSManga)

        val hasNextPage = library.has_next

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseSearchUrl.toHttpUrl().newBuilder().apply {
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

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/api?archive=${manga.url}", headers)
    }

    override fun getFilterList() = getFilters()

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(manga.apply { initialized = true })
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

    override fun getMangaUrl(manga: SManga) = "$baseUrl/archive/${manga.url}"
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    // Pages
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        fun List<String>.sort() = this.sortedWith(compareBy(CASE_INSENSITIVE_ORDER) { it })
        val url = "$baseUrl${chapter.url}/download/"
        val (fileType, contentLength) = getZipType(url)

        val remoteZip = ZipHandler(url, client, headers, fileType, contentLength).populate()
        val fileListing = remoteZip.files().sort()

        val files = remoteZip.toJson()
        return Observable.just(
            fileListing.mapIndexed { index, filename ->
                Page(index, imageUrl = "https://127.0.0.1/#$filename&$files")
            },
        )
    }

    private fun getZipType(url: String): Pair<String, BigInteger> {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .method("HEAD", null)
            .build()

        val contentLength = (
            client.newCall(request).execute().header("content-length")
                ?: throw Exception("Could not get Content-Length of URL")
            )
            .toBigInteger()

        return (if (contentLength > Int.MAX_VALUE.toBigInteger()) "zip64" else "zip") to contentLength
    }

    private fun Intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url.toString()
        return if (url.startsWith("https://127.0.0.1/#")) {
            val fragment = url.toHttpUrl().fragment!!
            val remoteZip = fragment.substringAfter("&").parseAs<Zip>()
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

    private fun Zip.toJson(): String {
        return json.encodeToString(this)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val PREFIX_FAK_ID_SEARCH = "fakku:"
        const val PREFIX_EHEN_ID_SEARCH = "ehentai:"
        const val PREFIX_SOURCE_SEARCH = "source:"
    }
}
