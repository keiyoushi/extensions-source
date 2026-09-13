package eu.kanade.tachiyomi.extension.en.sunshinebutterflyscans

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class SunshineButterflyScans : KeiSource() {

    private val cdnUrl = "$baseUrl/images/projcoverjpeg/"

    // Madara -> custom theme

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    private fun apiHeaders() = headersBuilder().apply {
        add("Accept", "*/*")
    }.build()

    @Volatile
    private var chaptersData: List<List<EntryDto>>? = null

    @Volatile
    private var initialized = false

    private val mutex = Mutex()

    private suspend fun getChaptersData(): List<List<EntryDto>> {
        if (initialized) return chaptersData!!

        mutex.withLock {
            if (!initialized) {
                initialized = true
                chaptersData = client.get("$baseUrl/json/chapters.json", apiHeaders()).parseAs<List<EntryDto>>().groupBy {
                    it.series
                }.values.map { it.sortedByDescending { it.num } }
            }
        }

        return chaptersData!!
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangaList = getChaptersData().sortedBy {
            it.first().series
        }.map {
            it.first().toSManga(cdnUrl)
        }

        return MangasPage(mangaList, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangaList = getChaptersData().sortedByDescending {
            it.first().timestamp.toLongOrNull() ?: Long.MAX_VALUE
        }.map {
            it.first().toSManga(cdnUrl)
        }

        return MangasPage(mangaList, false)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val selectedStatus = filters.firstInstance<StatusFilter>().toUriPart()
        val selectedSort = filters.firstInstance<SortFilter>().getSelection()

        val sortedList = if (selectedSort.first == "Name") {
            getChaptersData().sortedBy { it.first().series }
        } else {
            getChaptersData().sortedByDescending {
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

        return MangasPage(mangaList, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "projects" || url.queryParameter("n") == null) {
            return null
        }

        val mangaUrl = "/projects?n=${url.queryParameter("n")}"
        val manga = SManga.create().apply {
            this.url = mangaUrl
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = mangaUrl
            }
    }

    // =============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        StatusFilter(),
        SortFilter(),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    class StatusFilter :
        UriPartFilter(
            "Status",
            arrayOf(
                Pair("All", ""),
                Pair("Current", "current"),
                Pair("Complete", "complete"),
                Pair("Dropped", "dropped"),
                Pair("Licensed", "licensed"),
            ),
        )

    class SortFilter :
        Filter.Sort(
            "Sort by",
            VALUES,
            Selection(0, false),
        ) {
        fun getSelection() = Pair(VALUES[state!!.index], state!!.ascending)

        companion object {
            private val VALUES = arrayOf("Name", "Last Updated")
        }
    }

    // =========================== Manga Updates ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaData = getChaptersData().first {
            it.first().projectName == manga.url.substringAfter("?n=")
        }
        val manga = mangaData.first().toSManga(cdnUrl)
        val chapters = mangaData.map { it.toSChapter() }
        return SMangaUpdate(manga, chapters)
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterDto = getChaptersData().flatten().first {
            "${it.projectName}&num=${it.num}" == chapter.url.substringAfter("series=")
        }
        val decrypted = CryptoAES.decrypt(chapterDto.albumID, KEY, IV)
        val isGoogleDrive = decrypted.length > 10

        val url = if (isGoogleDrive) {
            GOOGLE_DRIVE_FIRST + decrypted + GOOGLE_DRIVE_SECOND
        } else {
            IMGUR_FIRST + decrypted + IMGUR_SECOND
        }
        val headers = headersBuilder().apply {
            set("Host", url.toHttpUrl().host)
            if (decrypted.length <= 10) {
                add("Authorization", "Client-ID $IMGUR_CLIENT_ID")
            }
        }.build()

        val response = client.get(url, headers)
        return if (isGoogleDrive) {
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

    // ============================= Utilities ==============================

    companion object {
        private const val GOOGLE_DRIVE_FIRST = "https://www.googleapis.com/drive/v3/files?q=\""
        private const val GOOGLE_DRIVE_SECOND = "\"+in+parents&key=AIzaSyDDWjOHN1UPcafkwyJLO7fX1gmVyntIozs&orderBy=name_natural&fields=files(id,name,imageMediaMetadata)&pageSize=250"
        private const val IMGUR_FIRST = "https://api.imgur.com/3/album/"
        private const val IMGUR_SECOND = "/images"
        private val IMGUR_CLIENT_ID = "227a2add62d2c9c"
        private val KEY = Base64.decode("YX+1nM4KgfaYwNE3/MPcTg==", Base64.DEFAULT)
        private val IV = Base64.decode("279GjT2Xu9LZBkI4zLzIAg==", Base64.DEFAULT)
    }
}
