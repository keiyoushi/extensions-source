package eu.kanade.tachiyomi.extension.ja.mangaone

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.Calendar
import java.util.TimeZone

class MangaOne :
    HttpSource(),
    ConfigurableSource {
    override val name = "Manga One"
    override val baseUrl = "https://manga-one.com"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/client"
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val preferences: SharedPreferences by getPreferencesLazy()

    private var tagList: List<Tags> = emptyList()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "ranking")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAsProto<RankingResponseList>()
        val mangas = result.categories.flatMap { it.rankingLists }.find { it.type == "すべて" }?.titles?.map { it.entry.toSManga() }.orEmpty()
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "rensai")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val calendar = Calendar.getInstance(jst)
        val dayIndex = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val result = response.parseAsProto<LatestResponseList>()
        val mangas = result.list[dayIndex].responseList.map { it.titles.entry.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = apiUrl.toHttpUrl().newBuilder()
                .addQueryParameter("rq", "title/search")
                .addQueryParameter("query", query)
                .build()
                .toString()
            return POST(url, headers)
        }

        val tagId = filters.firstInstanceOrNull<TagFilter>()?.valueId
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/search")
            .addQueryParameter("tag_id", tagId.toString())
            .build()
            .toString()
        return POST(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAsProto<ResponseList>()
        val mangas = result.responseList.map { it.titles.entry.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "viewer_v2")
            .addQueryParameter("title_id", manga.url)
            .build()
            .toString()
        return POST(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAsProto<DetailResponse>().detailEntry.details.toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "viewer/chapter_list")
            .addQueryParameter("title_id", manga.url)
            .addQueryParameter("page", "1")
            .addQueryParameter("limit", "9999")
            .addQueryParameter("sort_type", "desc")
            .addQueryParameter("type", "chapter")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val titleId = response.request.url.queryParameter("title_id")!!
        return response.parseAsProto<ChapterResponse>().chapters.chapterList
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(titleId) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = chapterUrl.fragment
        val chapterId = chapterUrl.pathSegments.first()
        return "$baseUrl/manga/$titleId/chapter/$chapterId"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = chapterUrl.fragment
        val chapterId = chapterUrl.pathSegments.first()
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "viewer_v2")
            .addQueryParameter("title_id", titleId)
            .addQueryParameter("chapter_id", chapterId)
            .build()
            .toString()
        return POST(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAsProto<ViewerResponse>()
        val key = result.key
        val iv = result.iv
        if (result.pages.isNullOrEmpty()) {
            throw Exception("Log in via WebView and rent or purchase this chapter to read.")
        }
        return result.pages.mapIndexed { i, pages ->
            Page(i, imageUrl = "${pages.page.url}#$key:$iv")
        }
    }

    override fun getFilterList(): FilterList {
        fetchTags()
        return if (tagList.isEmpty()) {
            FilterList(Filter.Header("Press 'Reset' to load genres"))
        } else {
            FilterList(TagFilter(tagList))
        }
    }

    private class TagFilter(private val tags: List<Tags>) : Filter.Select<String>("ジャンル", tags.map { it.name }.toTypedArray()) {
        val valueId: Int
            get() = tags[state].tagId
    }

    private fun fetchTags() {
        if (tagList.isNotEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val url = apiUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("rq", "title/search")
                    .build()
                val request = GET(url, headers)
                val response = client.newCall(request).execute()
                tagList = response.parseAsProto<TagResponse>().tags.orEmpty()
            }
        }
    }

    private inline fun <reified T> Response.parseAsProto(): T = ProtoBuf.decodeFromByteArray(body.bytes())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
