package eu.kanade.tachiyomi.extension.ja.mangaone

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

@Source
abstract class MangaOne :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl = "$baseUrl/api/client"
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "ranking")
            .build()

        val result = client.get(url).parseAsProto<RankingResponseList>()
        val mangas = result.categories.flatMap { it.rankingLists }.find { it.type == "すべて" }?.titles?.map { it.entry.toSManga() }.orEmpty()
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "rensai")
            .build()

        val dayIndex = LocalDate.now(JST).dayOfWeek.value - 1
        val result = client.get(url).parseAsProto<LatestResponseList>()
        val mangas = result.list[dayIndex].responseList.map { it.titles.entry.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tag = filters.firstInstanceOrNull<TagFilter>()?.tagId
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/search")
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("query", query)
                } else {
                    tag?.let {
                        addQueryParameter("tag_id", it.toString())
                    }
                }
            }.build()

        val result = client.post(url, EMPTY_BODY).parseAsProto<ResponseList>()
        val mangas = result.responseList.map { it.titles.entry.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val details = async {
            if (!fetchDetails) return@async manga
            val url = apiUrl.toHttpUrl().newBuilder()
                .addQueryParameter("rq", "viewer_v2")
                .addQueryParameter("title_id", manga.url)
                .build()
            client.post(url, EMPTY_BODY).parseAsProto<DetailResponse>().detailEntry.details.toSManga()
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val url = apiUrl.toHttpUrl().newBuilder()
                .addQueryParameter("rq", "viewer/chapter_list")
                .addQueryParameter("title_id", manga.url)
                .addQueryParameter("page", "1")
                .addQueryParameter("limit", "9999")
                .addQueryParameter("sort_type", "desc")
                .addQueryParameter("type", "chapter")
                .build()
            client.get(url).parseAsProto<ChapterResponse>().chapters.chapterList
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter(manga.url) }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "viewer_v2")
            .addQueryParameter("title_id", chapter.memo["titleId"]!!.string)
            .addQueryParameter("chapter_id", chapter.url)
            .build()

        val result = client.post(url, EMPTY_BODY).parseAsProto<ViewerResponse>()
        if (result.pages.isNullOrEmpty()) {
            throw Exception("Log in via WebView and rent or purchase this chapter to read.")
        }
        return result.pages.mapNotNull { it.page }.mapIndexed { i, page ->
            Page(i, imageUrl = "${page.url}#${result.key}:${result.iv}")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.memo["titleId"]!!.string}/chapter/${chapter.url}"

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/search")
            .build()
        return client.get(url).parseAsProto<TagResponse>().tags.orEmpty().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<Tags>>().orEmpty()
        return if (tags.isEmpty()) {
            FilterList()
        } else {
            FilterList(TagFilter(tags))
        }
    }

    private class TagFilter(private val tags: List<Tags>) : Filter.Select<String>("ジャンル", tags.map { it.name }.toTypedArray()) {
        val tagId: Int
            get() = tags[state].tagId
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
    }
}
