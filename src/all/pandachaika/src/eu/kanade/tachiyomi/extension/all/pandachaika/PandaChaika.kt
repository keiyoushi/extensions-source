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
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.zip.dataRange
import keiyoushi.zip.range
import keiyoushi.zip.readEntry
import keiyoushi.zip.zipDirectory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import rx.Observable
import java.lang.String.CASE_INSENSITIVE_ORDER

@Source
abstract class PandaChaika : HttpSource() {

    private val searchLang: String
        get() = when (lang) {
            "en" -> "english"
            "zh" -> "chinese"
            "ko" -> "korean"
            "es" -> "spanish"
            "ru" -> "russian"
            "pt" -> "portuguese"
            "fr" -> "french"
            "th" -> "thai"
            "vi" -> "vietnamese"
            "ja" -> "japanese"
            "id" -> "indonesian"
            "ar" -> "arabic"
            "uk" -> "ukrainian"
            "tr" -> "turkish"
            "cs" -> "czech"
            "tl" -> "tagalog"
            "fi" -> "finnish"
            "jv" -> "javanese"
            "el" -> "greek"
            else -> ""
        }

    private val baseSearchUrl = "$baseUrl/search"

    override val supportsLatest = true

    override val client = network.client
        .newBuilder()
        .addInterceptor(::intercept)
        .build()

    private val fakkuRegex = Regex("""(?:https?://)?(?:www\.)?fakku\.net/hentai/""")
    private val ehentaiRegex = Regex("""(?:https?://)?e-hentai\.org/g/""")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseSearchUrl/?tags=$searchLang&sort=rating&apply=&json=&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseSearchUrl/?tags=$searchLang&sort=public_date&apply=&json=&page=$page", headers)

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
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported url")
            }
            if (url.pathSegments.size <= 2) {
                throw Exception("Unsupported url")
            }
            val id = "${url.pathSegments[1]}/${url.pathSegments[2]}"
            return fetchSearchManga(page, "$PREFIX_ID_SEARCH$id", filters)
        }
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

        val hasNextPage = library.hasNext

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

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/api?archive=${manga.url}", headers)

    override fun getFilterList() = getFilters()

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga.apply { initialized = true })

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
        val url = "$baseUrl${chapter.url}/download/"
        val dir = client.zipDirectory(url, headers)
        val pages = dir.entries.sortedWith(compareBy(CASE_INSENSITIVE_ORDER) { it.name }).mapIndexed { index, entry ->
            val data = ImageRequest(
                url,
                entry.name,
                entry.localHeaderOffset,
                entry.compressedSize,
                entry.method,
            ).toJsonString()
            Page(index, imageUrl = "https://127.0.0.1/#$data")
        }

        return Observable.just(pages)
    }

    private fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.toString().startsWith("https://127.0.0.1/#")) {
            return chain.proceed(request)
        }

        val data = request.url.fragment?.parseAs<ImageRequest>() ?: return chain.proceed(request)
        val range = dataRange(data.offset, data.compressedSize)
        val rangeRequest = request.newBuilder()
            .url(data.url)
            .range(range)
            .build()

        val response = chain.proceed(rangeRequest)
        if (!response.isSuccessful) return response
        val image = readEntry(response.body.source(), data.compressedSize, data.method).buffer()
        var type = data.name.substringAfterLast('.').lowercase()
        type = if (type == "jpg") "jpeg" else type

        return response.newBuilder()
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .body(image.asResponseBody("image/$type".toMediaType()))
            .build()
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
