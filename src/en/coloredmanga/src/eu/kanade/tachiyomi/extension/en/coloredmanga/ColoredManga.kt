package eu.kanade.tachiyomi.extension.en.coloredmanga

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SManga.Companion.COMPLETED
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ColoredManga : HttpSource() {

    override val name = "ColoredManga"

    override val baseUrl = "https://coloredmanga.net"

    private val searchUrl = "$baseUrl/manga"

    override val lang = "en"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient
        .newBuilder()
        .addInterceptor(::Intercept)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET(searchUrl, headers)
    }

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val chapterDateFormat = SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    override fun popularMangaParse(response: Response): MangasPage = searchMangas(response, sortBy = "pop" to "desc")

    private fun searchMangas(response: Response, title: String = "", filters: FilterList? = null, sortBy: Pair<String, String> = "" to ""): MangasPage {
        var sort = sortBy
        val mangas = getMangas(response.asJsoup()).filter {
            val genreIncluded: MutableList<String> = mutableListOf()
            val genreExcluded: MutableList<String> = mutableListOf()

            val typeIncluded: MutableList<String> = mutableListOf()
            val typeExcluded: MutableList<String> = mutableListOf()

            val colorIncluded: MutableList<String> = mutableListOf()
            val colorExcluded: MutableList<String> = mutableListOf()

            val statusIncluded: MutableList<String> = mutableListOf()
            val statusExcluded: MutableList<String> = mutableListOf()

            filters?.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        sort = filter.getValue() to if (filter.state!!.ascending) "asc" else "desc"
                    }
                    is GenreFilter -> {
                        if (filter.state.isNotEmpty()) {
                            filter.state.split(",").filter(String::isNotBlank).map { tag ->
                                val trimmed = tag.trim().lowercase()
                                if (trimmed.startsWith("-")) {
                                    genreExcluded.add(trimmed)
                                } else {
                                    genreIncluded.add(trimmed)
                                }
                            }
                        }
                    }

                    is TypeFilter -> {
                        filter.state.filter { state -> state.isIncluded() }.forEach { tri ->
                            typeIncluded.add(tri.value)
                        }

                        filter.state.filter { state -> state.isExcluded() }.forEach { tri ->
                            typeExcluded.add(tri.value)
                        }
                    }
                    is ColorFilter -> {
                        filter.state.filter { state -> state.isIncluded() }.forEach { tri ->
                            colorIncluded.add(tri.value)
                        }

                        filter.state.filter { state -> state.isExcluded() }.forEach { tri ->
                            colorExcluded.add(tri.value)
                        }
                    }
                    is StatusFilter -> {
                        filter.state.filter { state -> state.isIncluded() }.forEach { tri ->
                            statusIncluded.add(tri.value)
                        }

                        filter.state.filter { state -> state.isExcluded() }.forEach { tri ->
                            statusExcluded.add(tri.value)
                        }
                    }
                    else -> {}
                }
            }

            val includeGenre = genreIncluded.isEmpty() || it.tags.map { genre -> genre.lowercase() }.containsAll(genreIncluded)
            val excludeGenre = genreExcluded.isNotEmpty() && it.tags.map { genre -> genre.lowercase() }.containsAll(genreExcluded)

            val includeType = typeIncluded.isEmpty() || typeIncluded.contains(it.type.lowercase())
            val excludeType = typeExcluded.isNotEmpty() && typeExcluded.contains(it.type)

            val includeColor = colorIncluded.isEmpty() || colorIncluded.contains(it.version)
            val excludeColor = colorExcluded.isNotEmpty() && colorExcluded.contains(it.version)

            val regularSearch = it.name.contains(title) || it.synopsis.contains(title)
            includeGenre && !excludeGenre &&
                includeType && !excludeType &&
                includeColor && !excludeColor &&
                regularSearch
        }

        val sorted = when (sort.first) {
            "pop" -> {
                if (sort.second == "desc") {
                    mangas.sortedByDescending { it.totalViews }
                } else {
                    mangas.sortedBy { it.totalViews }
                }
            }
            "tit" -> {
                if (sort.second == "desc") {
                    mangas.sortedByDescending { it.name }
                } else {
                    mangas.sortedBy { it.name }
                }
            }
            else -> {
                if (sort.second == "desc") {
                    mangas.sortedByDescending {
                        try {
                            dateFormat.parse(it.date)!!.time
                        } catch (e: Exception) {
                            0L
                        }
                    }
                } else {
                    mangas.sortedBy {
                        try {
                            dateFormat.parse(it.date)!!.time
                        } catch (e: Exception) {
                            0L
                        }
                    }
                }
            }
        }

        val final = sorted.map(::popularManga)
        return MangasPage(final, false)
    }

    private fun getMangas(doc: Document): MutableList<Mangas> {
        val mangasRaw = doc.selectFirst("script:containsData(\\\"cover)")!!.data()
        val mangasJson = mangasRaw.replace("\\\"", "\"").replace("\\\\\"", "\\\"").replace("self.__next_f.push([1,\"7:[\"\$\",\"div\",null,{\"className\":\"xl:w-[1280px] w-full min-h-[100vh] font-light flex flex-col items-start justify-start\",\"id\":\"themes_body___1nq3\",\"children\":[\"\$\",\"\$Le\",null,{\"params\":\"\$undefined\",\"data\":[", "{\"data\":[").replace("}]}]}]\\n\"])", "}]}")

        val parsedMangas = mangasJson.parseAs<MangasList>()

        val mangas: MutableList<Mangas> = mutableListOf()
        for (manga in parsedMangas.data) {
            mangas.add(manga)
        }
        return mangas
    }

    private fun getManga(doc: Document): Mangas {
        val mangaRaw = doc.selectFirst("script:containsData(\\\"cover)")!!.data()
        val mangaJson = mangaRaw.replace("\\\"", "\"").replace("\\\\\"", "\\\"").replace("self.__next_f.push([1,\"7:[\"\$\",\"\$Lf\",null,", "").replace("\"data\":{", "\"data\":[{").replace("}}]\\n\"])", "}]}")

        return mangaJson.parseAs<MangasList>().data[0]
    }

    private fun getImage(manga_name: String, volume_name: String? = "", chapter: Chapter, index: String): String {
        val chapterNumber = index.padStart(4, '0')
        var chapterName = chapter.number
        if (chapter.title.isNotEmpty()) {
            chapterName = "${chapter.number} - ${chapter.title}"
        }

        val formData = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("path", "/images/content/$manga_name/$volume_name$chapterName")
            .addFormDataPart("number", chapterNumber)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api/dynamicImages")
            .put(formData)
            .addHeader("Cache-Control", "no-store")
            .build()

        val response = client.newCall(request).execute().body.string()

        val responseJson = JSONObject(response)
        val image = responseJson.getString("image")

        return image
    }

    private fun popularManga(manga: Mangas): SManga {
        return SManga.create().apply {
            title = manga.name
            setUrlWithoutDomain("$baseUrl/manga/${manga.id}")
            thumbnail_url = "$baseUrl${manga.cover}"
        }
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangas(response, sortBy = "lat" to "desc")

    // Search

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val response = client.newCall(GET(searchUrl, headers)).execute()

        return Observable.just(
            searchMangas(response, query, filters),
        )
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = getManga(document)
        return SManga.create().apply {
            title = manga.name
            url = "/manga.${manga.id}"
            author = manga.author
            artist = manga.artist
            genre = manga.tags.joinToString()
            description = manga.synopsis
            status = when (manga.status.lowercase()) {
                "Ongoing" -> SManga.ONGOING
                "Cancelled" -> SManga.CANCELLED
                "Hiatus" -> SManga.ON_HIATUS
                else -> COMPLETED
            }
            initialized = true
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val manga = getManga(document)

        val listMangas = if (manga.chapters.isNotEmpty()) {
            manga.chapters.map { chapter ->
                SChapter.create().apply {
                    name = if (chapter.title.isNotEmpty()) {
                        "${chapter.number} - ${chapter.title}"
                    } else {
                        chapter.number
                    }
                    url = "/manga/${manga.id}/${chapter.id}"
                    date_upload = try {
                        chapterDateFormat.parse(chapter.date)!!.time
                    } catch (e: Exception) {
                        0L
                    }
                }
            }.reversed()
        } else {
            manga.volume.flatMap { volume ->
                volume.chapters.map { chapter ->
                    SChapter.create().apply {
                        name = if (chapter.title.isNotEmpty()) {
                            "${volume.number} | ${chapter.number} - ${chapter.title}"
                        } else {
                            "${volume.number} | ${chapter.number}"
                        }
                        url = "/manga/${manga.id}/${chapter.id}"
                        date_upload = try {
                            chapterDateFormat.parse(chapter.date)!!.time
                        } catch (e: Exception) {
                            0L
                        }
                    }
                }
            }
        }.reversed()

        return listMangas
    }

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val response = client.newCall(GET(baseUrl + chapter.url.substringBeforeLast("/"), headers)).execute()
        val document = response.asJsoup()
        val manga = getManga(document)
        val volumes = manga.chapters.isEmpty() || manga.volume.isNotEmpty()
        val spChapter = if (volumes) {
            manga.volume
                .flatMap { it.chapters }
                .find { it.number == chapter.name }
        } else {
            manga.chapters.find { it.number == chapter.name }
        }
        val chapterJson = spChapter!!.toJson()

        return Observable.just(
            List(spChapter.totalImage - 1) {
                val url = "https://127.0.0.1/#${it + 1}+${manga.name}"
                val volumeInfo = if (volumes) {
                    manga.volume.find { vol -> vol.chapters.any { chap -> chap.number == chapter.name } }
                        ?.let { vol ->
                            if (vol.title.isNotEmpty()) {
                                "${vol.number} - ${vol.title}/"
                            } else {
                                "${vol.number}/"
                            }
                        } ?: ""
                } else {
                    ""
                }
                Page(it, url = "$searchUrl/${manga.id}/${spChapter.id}", imageUrl = "$url&volume=$volumeInfo&chapter=$chapterJson")
            },
        )
    }

    private val mediaTypePattern = Regex("""(^[^;,]*)[;,]""")
    private fun Intercept(chain: Interceptor.Chain): Response {
        val yurl = chain.request().url
        return if (yurl.toString().startsWith("https://127.0.0.1/#")) {
            val index = yurl.fragment!!.substringBefore("+")
            val manga_name = yurl.fragment!!.substringAfter("+").substringBefore("&chapter=").substringBefore("&volume=")
            val volume_name = yurl.fragment!!.substringAfter("&volume=").substringBefore("&chapter=")
            val chapter = (yurl.fragment!!.substringAfter("chapter=")).parseAs<Chapter>()

            val image = getImage(manga_name, volume_name, chapter, index)

            val dataString = image.substringAfter(":")
            val byteArray = Base64.decode(dataString.substringAfter("base64,"), Base64.DEFAULT)
            val mediaType = mediaTypePattern.find(dataString)!!.value.toMediaTypeOrNull()
            Response.Builder().body(byteArray.toResponseBody(mediaType))
                .request(chain.request())
                .protocol(Protocol.HTTP_1_0)
                .code(200)
                .message("")
                .build()
        } else {
            chain.proceed(chain.request())
        }
    }
    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }

    private fun Chapter.toJson(): String {
        return json.encodeToString(this)
    }
    override fun getFilterList() = getFilters()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()
}