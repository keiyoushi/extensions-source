package eu.kanade.tachiyomi.extension.en.sunshinebutterflyscans

import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class SunshineButterflyScans : HttpSource() {

    override val name = "Sunshine Butterfly Scans"

    override val baseUrl = "https://wings.sbs"
    private val cdnUrl = "$baseUrl/images/projcoverjpeg/"

    override val lang = "en"

    override val supportsLatest = true

    // Madara -> custom theme
    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "*/*")
        add("Host", baseUrl.toHttpUrl().host)
    }

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    private val json: Json by injectLazy()

    private val chaptersData by lazy {
        client.newCall(
            GET("$baseUrl/json/chapters.json", apiHeaders),
        ).execute().parseAs<List<EntryDto>>().groupBy {
            it.series
        }.values.map { it.sortedByDescending { it.num } }
    }

    // ============================== Popular ===============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val mangaList = chaptersData.sortedBy {
            it.first().series
        }.map {
            it.first().toSManga(cdnUrl)
        }

        return Observable.just(MangasPage(mangaList, false))
    }

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    // =============================== Latest ===============================

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val mangaList = chaptersData.sortedByDescending {
            it.first().timestamp.toLongOrNull() ?: Long.MAX_VALUE
        }.map {
            it.first().toSManga(cdnUrl)
        }

        return Observable.just(MangasPage(mangaList, false))
    }

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val selectedStatus = filters.filterIsInstance<StatusFilter>().first().toUriPart()
        val selectedSort = filters.filterIsInstance<SortFilter>().first().getSelection()

        val sortedList = if (selectedSort.first == "Name") {
            chaptersData.sortedBy { it.first().series }
        } else {
            chaptersData.sortedByDescending {
                it.first().timestamp.toLongOrNull() ?: Long.MAX_VALUE
            }
        }

        val filteredList = sortedList
            .filter { it.first().series.contains(query, true) }
            .filter { it.first().projectStatus.contains(selectedStatus) }

        val reversedList = if (selectedSort.second) {
            filteredList.reversed()
        } else {
            filteredList
        }

        val mangaList = reversedList.map {
            it.first().toSManga(cdnUrl)
        }

        return Observable.just(MangasPage(mangaList, false))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(),
        SortFilter(),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Current", "current"),
            Pair("Complete", "complete"),
            Pair("Dropped", "dropped"),
            Pair("Licensed", "licensed"),
        ),
    )

    class SortFilter : Filter.Sort(
        "Sort by",
        VALUES,
        Selection(0, false),
    ) {
        fun getSelection() = Pair(VALUES[state!!.index], state!!.ascending)

        companion object {
            private val VALUES = arrayOf("Name", "Last Updated")
        }
    }

    // =========================== Manga Details ============================

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val mangaData = chaptersData.first {
            it.first().projectName == manga.url.substringAfter("?n=")
        }.first()

        return Observable.just(mangaData.toSManga(cdnUrl))
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request =
        throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException()

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val selectedManga = chaptersData.first {
            it.first().projectName == manga.url.substringAfter("?n=")
        }
        val chapterList = selectedManga.map { it.toSChapter() }

        return Observable.just(chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request =
        throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException()

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterDto = chaptersData.flatten().first {
            "${it.projectName}&num=${it.num}" == chapter.url.substringAfter("series=")
        }
        val decrypted = CryptoAES.decrypt(chapterDto.albumID, KEY, IV)

        val url = if (decrypted.length > 10) {
            GOOGLE_DRIVE_FIRST + decrypted + GOOGLE_DRIVE_SECOND
        } else {
            IMGUR_FIRST + decrypted + IMGUR_SECOND
        }
        val headers = headersBuilder().apply {
            set("Host", url.toHttpUrl().host)
            add("Origin", baseUrl)
            if (decrypted.length <= 10) {
                add("Authorization", "Bearer $IMGUR_BEARER")
            }
        }.build()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return if (response.request.url.host.contains("googleapis")) {
            response.parseAs<GoogleDriveResponseDto>().files.sortedBy {
                it.name
            }.mapIndexed { index, file ->
                Page(index, imageUrl = "https://lh3.googleusercontent.com/d/${file.id}=w${file.metadata.width}")
            }
        } else {
            response.parseAs<ImgurResponseDto>().data.mapIndexed { index, data ->
                Page(index, imageUrl = data.link)
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    companion object {
        private const val GOOGLE_DRIVE_FIRST = "https://www.googleapis.com/drive/v3/files?q=\""
        private const val GOOGLE_DRIVE_SECOND = "\"+in+parents&key=AIzaSyDDWjOHN1UPcafkwyJLO7fX1gmVyntIozs&orderBy=name_natural&fields=files(id,name,imageMediaMetadata)&pageSize=250"
        private const val IMGUR_FIRST = "https://api.imgur.com/3/album/"
        private const val IMGUR_SECOND = "/images"
        private val IMGUR_BEARER = "84155230e6a2d98eaea1cee48d97e6ecff0f6c12"
        private val KEY = Base64.decode("YX+1nM4KgfaYwNE3/MPcTg==", Base64.DEFAULT)
        private val IV = Base64.decode("279GjT2Xu9LZBkI4zLzIAg==", Base64.DEFAULT)
    }
}
