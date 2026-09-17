package eu.kanade.tachiyomi.extension.ja.flowercomics

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.ZoneId

@Source
abstract class FlowerComics :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val jst = ZoneId.of("Asia/Tokyo")
    private val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val data = client.get("$baseUrl/ranking", rscHeaders).extractNextJs<List<RankingBlock>>()
        val mangas = data.orEmpty().firstOrNull { it.rankingTypeName == "総合" }?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getUpdateDay(LocalDate.now(jst).dayOfWeek.name.take(3).lowercase())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstance<CategoryFilter>()
        val url = if (query.isNotBlank()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
                .toString()
        } else {
            when (category.type) {
                "day" -> return getUpdateDay(category.value)
                "rensai" -> "$baseUrl/rensai/${category.value}"
                else -> "$baseUrl/tag/${category.value}/${category.type}"
            }
        }

        val mangas = client.get(url).asJsoup().select("div.grid > a[href^=/title/]").map {
            SManga.create().apply {
                this.url = it.absUrl("href").toHttpUrl().pathSegments.last()
                title = it.selectFirst("p.text-black")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    private suspend fun getUpdateDay(day: String): MangasPage {
        val data = client.get("$baseUrl/rensai", rscHeaders).extractNextJs<LatestData>()
        val mangas = data?.weekdays?.get(day).orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val details = SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.select("a[href^=/author/] p").joinToString { it.text() }
            description = document.selectFirst("div.whitespace-pre-wrap p")?.text()
            genre = document.select("ul[aria-label=ジャンルタグ一覧] p").joinToString { it.text() }
            thumbnail_url = document.selectFirst("section img.object-cover")?.absUrl("src")
            val statusText = document.select("div.bg-main-blue p").text()
            status = when {
                statusText.contains("完結") -> SManga.COMPLETED
                statusText.contains("更新予定") || statusText.contains("連載") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }

        val chapterList = document.extractNextJs<EntryChapters>()?.chapters.orEmpty()
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(getChapterUrl(chapter), rscHeaders).extractNextJs<List<PageEntry>>()
            ?: throw Exception("Log in via WebView and rent or purchase this chapter to read.")

        return data.filter { it.crypto != null }.mapIndexed { i, page ->
            val url = page.src.toHttpUrl().newBuilder()
                .fragment("${page.crypto?.key}:${page.crypto?.iv}")
                .build()
                .toString()
            Page(i, imageUrl = url)
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}/viewer"

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

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
}
