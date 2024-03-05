package eu.kanade.tachiyomi.multisrc.gmanga

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

abstract class Gmanga(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
    private val apiUrl: String = baseUrl.replace("://", "://api."),
    private val cdnUrl: String = baseUrl.replace("://", "://media."),
) : ConfigurableSource, HttpSource() {

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = network.client.newBuilder()
        .rateLimit(4)
        .build()

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", getFilterList())
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val latestUrl = when (preferences.getLatestListingPref()) {
            PREF_LASTETS_LISTING_SHOW_LASTETS_MANGA -> "$baseUrl/mangas/latest"
            PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER -> "$apiUrl/api/releases?page=$page"
            else -> "$baseUrl/mangas/latest"
        }
        return GET(latestUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val isLatest = when (preferences.getLatestListingPref()) {
            PREF_LASTETS_LISTING_SHOW_LASTETS_MANGA -> true
            PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER -> false
            else -> true
        }

        val mangas = if (!isLatest) {
            val decMga = response.decryptAs<JsonObject>()
            val selectedManga = decMga["rows"]!!.jsonArray[0].jsonObject["rows"]!!.jsonArray
            selectedManga.map {
                json.decodeFromJsonElement<BrowseManga>(it.jsonArray[17])
            }
        } else {
            response.asJsoup()
                .select(".js-react-on-rails-component").html()
                .parseAs<MangaDataAction<LatestMangaDto>>()
                .mangaDataAction.newMangas
        }

        return MangasPage(
            mangas.map { it.toSManga(cdnUrl) }.distinctBy { it.url },
            hasNextPage = (mangas.size >= 30) && !isLatest,
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return Filters.buildSearchPayload(
            page,
            query,
            if (filters.isEmpty()) getFilterList() else filters,
        ).let {
            val body = it.toString().toRequestBody(MEDIA_TYPE)
            POST("$baseUrl/api/mangas/search", headers, body)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.decryptAs<SearchMangaDto>()
        return MangasPage(
            data.mangas.map { it.toSManga(cdnUrl) },
            hasNextPage = data.mangas.size == 50,
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.asJsoup()
            .select(".js-react-on-rails-component").html()
            .parseAs<MangaDataAction<MangaDetailsDto>>()
            .mangaDataAction.mangaData
            .toSManga(cdnUrl)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/mangas/$mangaId/releases", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = response.decryptAs<TableDto>()
            .asChapterList(json)

        val releases = when (preferences.getChapterListingPref()) {
            PREF_CHAPTER_LISTING_SHOW_POPULAR ->
                chapterList.releases
                    .groupBy { release -> release.chapterizationId }
                    .mapNotNull { (_, releases) -> releases.maxByOrNull { it.views } }
            PREF_CHAPTER_LISTING_SHOW_ALL -> chapterList.releases
            else -> emptyList()
        }

        return releases.map { release ->
            SChapter.create().apply {
                val chapter = chapterList.chapters.first { it.id == release.chapterizationId }
                val team = chapterList.teams.firstOrNull { it.id == release.teamId }

                url = "/r/${release.id}"
                chapter_number = chapter.chapter
                date_upload = release.timestamp * 1000
                scanlator = team?.name

                val chapterName = chapter.title.let { if (it.trim() != "") " - $it" else "" }
                name = "${chapter_number.let { if (it % 1 > 0) it else it.toInt() }}$chapterName"
            }
        }.sortedWith(compareBy({ -it.chapter_number }, { -it.date_upload }))
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url.toString()

        val data = response.asJsoup()
            .select(".js-react-on-rails-component").html()
            .parseAs<ReaderDto>()
            .readerDataAction.readerData.release

        val hasWebP = data.webpPages.isNotEmpty()

        val (pages, directory) = when {
            hasWebP -> data.webpPages to "hq_webp"
            else -> data.pages to "hq"
        }

        return pages.sortedWith(pageSort)
            .mapIndexed { index, pageUri ->
                Page(
                    index = index,
                    url = "$url#page_$index",
                    imageUrl = "$cdnUrl/uploads/releases/${data.key}/$directory/$pageUri",
                )
            }
    }

    private val pageSort = compareBy<String>(
        { parseNumber(0, it) ?: Double.MAX_VALUE },
        { parseNumber(1, it) },
        { parseNumber(2, it) },
    )

    private fun parseNumber(index: Int, string: String): Double? =
        Regex("\\d+").findAll(string).map { it.value }.toList().getOrNull(index)?.toDoubleOrNull()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_LISTING_KEY
            title = "كيفية عرض الفصل بقائمة الفصول"
            entries = arrayOf(
                "اختيار النسخة الأكثر مشاهدة",
                "عرض جميع النسخ",
            )
            entryValues = arrayOf(
                PREF_CHAPTER_LISTING_SHOW_POPULAR,
                PREF_CHAPTER_LISTING_SHOW_ALL,
            )
            summary = "%s"
            setDefaultValue(PREF_CHAPTER_LISTING_SHOW_POPULAR)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_LATEST_LISTING_KEY
            title = "كيفية عرض بقائمة الأعمال الجديدة"
            entries = arrayOf(
                "اختيار آخر الإضافات",
                "اختيار لمانجات الجديدة",
            )
            entryValues = arrayOf(
                PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER,
                PREF_LASTETS_LISTING_SHOW_LASTETS_MANGA,
            )
            summary = "%s"
            setDefaultValue(PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER)
        }.also(screen::addPreference)
    }

    private fun SharedPreferences.getChapterListingPref() =
        getString(PREF_CHAPTER_LISTING_KEY, PREF_CHAPTER_LISTING_SHOW_POPULAR)!!

    private fun SharedPreferences.getLatestListingPref() =
        getString(PREF_LATEST_LISTING_KEY, PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER)!!

    private inline fun <reified T> Response.decryptAs(): T =
        decrypt(parseAs<EncryptedResponse>().data)
            .also { Log.d(name, it) }
            .parseAs<T>()

    private inline fun <reified T> Response.parseAs(): T = body.string().parseAs()

    private inline fun <reified T> String.parseAs(): T = json.decodeFromString(this)

    override fun getFilterList() = Filters.getFilterList()

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    companion object {
        private const val PREF_CHAPTER_LISTING_KEY = "gmanga_chapter_listing"
        private const val PREF_LATEST_LISTING_KEY = "gmanga_last_listing"

        private const val PREF_CHAPTER_LISTING_SHOW_ALL = "gmanga_gmanga_chapter_listing_show_all"
        private const val PREF_CHAPTER_LISTING_SHOW_POPULAR = "gmanga_chapter_listing_most_viewed"
        private const val PREF_LASTETS_LISTING_SHOW_LASTETS_CHAPTER = "gmanga_Last_listing_last_chapter_added"
        private const val PREF_LASTETS_LISTING_SHOW_LASTETS_MANGA = "gmanga_chapter_listing_last_manga_added"

        private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
