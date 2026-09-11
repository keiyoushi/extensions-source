package eu.kanade.tachiyomi.extension.ja.corocoroonline

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.put
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAsProto
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.ZoneId

@Source
abstract class CorocoroOnline :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/api/csr"
    private val preferences by getPreferencesLazy()
    private val jst = ZoneId.of("Asia/Tokyo")
    private val currentDayInJapan: String
        get() = WEEKDAYS[LocalDate.now(jst).dayOfWeek.value - 1]

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = getRanking(category = null)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getUpdateDay(currentDayInJapan)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstance<CategoryFilter>().value
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return getSeriesList(url)
        }

        return when (category) {
            in WEEKDAYS -> getUpdateDay(category)
            "completed", "one-shot" -> getSeriesList("$baseUrl/rensai/$category".toHttpUrl())
            else -> getRanking(category)
        }
    }

    private suspend fun getRanking(category: String?): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/list/ranking")
            .build()

        val rankings = client.get(url).parseAsProto<PopularResponse>().rankingLists
        val ranking = if (category == null) {
            rankings.firstOrNull()
        } else {
            val (id, type) = category.split(":")
            rankings.firstOrNull { it.tag.id == id && it.tag.type == type }
        }

        val mangas = ranking?.toSMangaList().orEmpty()
        return MangasPage(mangas, false)
    }

    private suspend fun getUpdateDay(day: String): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/list/update_day")
            .addQueryParameter("day", day)
            .build()

        val titles = client.get(url).parseAsProto<TitleListView>().list?.titles.orEmpty()
        val mangas = titles.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private suspend fun getSeriesList(listUrl: HttpUrl): MangasPage {
        val document = client.get(listUrl).asJsoup()
        val mangas = document.select("div.grid > a[href^='/title/']").map {
            SManga.create().apply {
                url = it.absUrl("href").toHttpUrl().pathSegments.last()
                title = it.selectFirst("p.text-black, p")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/detail")
            .addQueryParameter("title_id", manga.url)
            .build()

        val details = client.get(url).parseAsProto<TitleDetailView>()
        val chapterList = details.chapters
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }

        val first = chapterList.firstOrNull()
        val last = chapterList.lastOrNull()
        val isAscending = when {
            first == null || last == null -> false
            first.date_upload != last.date_upload -> first.date_upload < last.date_upload
            first.chapter_number > -1 && last.chapter_number > -1 -> first.chapter_number < last.chapter_number
            else -> first.url.toLong() < last.url.toLong()
        }

        return SMangaUpdate(
            details.toSManga(),
            if (isAscending) chapterList.reversed() else chapterList,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "chapter/viewer")
            .addQueryParameter("chapter_id", chapter.url)
            .build()

        val response = client.put(url, EMPTY_BODY)
        val result = try {
            response.parseAsProto<ViewerView>()
        } catch (_: Exception) {
            throw Exception("Log in via WebView and purchase this chapter to read.")
        }

        return result.pages.mapIndexed { i, img ->
            val imageUrl = img.url.toHttpUrl().newBuilder()
                .fragment("${result.aesKey}:${result.aesIv}")
                .build()
                .toString()
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val WEEKDAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
    }
}
