package eu.kanade.tachiyomi.extension.ja.cmoa

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
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.fetchPages
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.textOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Cmoa :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(SpeedBinbInterceptor()) // 1.7070.1001 SBC
        addCookie(listOf("safesearch" to "0", "R18user" to "1"))
    }

    // load desktop selectors
    override fun Headers.Builder.configureHeaders() = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36")

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/search/purpose/ranking/all?period=daily&daily=all").toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/newrelease/?page=$page").asJsoup()
        val mangas = document.select("ul.title_list li.title_wrap").map {
            SManga.create().apply {
                title = it.selectFirst("div.text_box p.title_name")!!.text()
                thumbnail_url = it.selectFirst("div.thum_box a img")?.absUrl("src")
                url = it.selectFirst("div.thum_box a")!!.absUrl("href").toHttpUrl().pathSegments[1]
            }
        }
        val hasNextPage = document.selectFirst("div.pageSlider div.swiper-slide.selected + div.swiper-slide") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        fun Builder.addFilter(param: String, filter: Filter.Text) = filter.state.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
        fun Builder.addFilter(param: String, filter: SelectFilter) = filter.value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
        fun Builder.addFilter(param: String, filter: Filter.CheckBox) = filter.state.takeIf { it }?.let { addQueryParameter(param, "1") }

        val url = "$baseUrl/search/result".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("word", query)
            addFilter("title_nm", filters.firstInstance<TitleFilter>())
            addFilter("author_nm", filters.firstInstance<AuthorFilter>())
            addFilter("magazine_nm", filters.firstInstance<MagazineFilter>())
            addFilter("publisher_nm", filters.firstInstance<PublisherFilter>())
            addFilter("titletag_nm", filters.firstInstance<TitleTagFilter>())
            addFilter("genre_id", filters.firstInstance<GenreFilter>())
            addFilter("point", filters.firstInstance<PriceFilter>())
            addFilter("review", filters.firstInstance<ReviewFilter>())
            addFilter("sort", filters.firstInstance<SortFilter>())
            addFilter("free_cam_flg", filters.firstInstance<FreeFilter>())
            addFilter("sample_up_flg", filters.firstInstance<SampleFilter>())
            addFilter("campaign_flg", filters.firstInstance<CampaignFilter>())
            addFilter("newest_flg", filters.firstInstance<NewestFilter>())
            addFilter("complete_flg", filters.firstInstance<CompleteFilter>())
            addQueryParameter("page", page.toString())
        }.build()
        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select("li.search_result_box").map {
            SManga.create().apply {
                title = it.selectFirst("div.search_result_box_right_sec1 a.title")!!.text()
                val img = it.selectFirst("div.search_result_box_left img")
                thumbnail_url = img?.absUrl("data-src")?.ifEmpty { img.absUrl("src") }
                url = it.selectFirst("div.search_result_box_left a.title")!!.absUrl("href").toHttpUrl().pathSegments[1]
            }
        }
        val hasNextPage = document.selectFirst("li.next:not(.nopage)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val document = client.get("$baseUrl/title/${manga.url}?page=1&order=down").asJsoup()
        val mangaTitle = document.selectFirst("section.brCramb a[href^=/title/]")!!.text()
        val details = SManga.create().apply {
            title = mangaTitle
            author = document.select("div.title_details_author_name a").joinToString { it.text() }
            description = document.selectFirst("div#comic_description > p")?.textOrNull()
            genre = buildList {
                document.selectFirst("a.comic_mark_thum")?.textOrNull()?.let { add(it) }
                document.select("div.category_line_f_r_l.genre_detail a").mapTo(this) { it.text() }
            }.joinToString()
            val completed = document.selectFirst("div.volume")?.textOrNull()?.contains("完結")
            status = if (completed == true) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("div.thumBox img")?.absUrl("src")
        }

        if (!fetchChapters) return@coroutineScope SMangaUpdate(details, chapters)

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val lastPage = document.select("div.pagination li a").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1
        val otherPages = (2..lastPage).map { page ->
            async { client.get("$baseUrl/title/${manga.url}?page=$page&order=down").asJsoup() }
        }

        val seriesTitle = mangaTitle.replace('\u3000', ' ')
        val chapterList = (listOf(document) + otherPages.awaitAll()).flatMap {
            it.select("ul.title_vol_vox_vols li").mapNotNull { element ->
                SChapter.create().apply {
                    url = element.selectFirst("div.thum_box img")!!.absUrl("src").substringAfterLast("/").substringBefore(".")

                    val volumeTitle = element.selectFirst("h3.title_details_title_name_h2 a")!!.ownText().replace('\u3000', ' ')
                    val rawName = volumeTitle.removePrefix(seriesTitle).trim(' ', ':', '：').ifEmpty { volumeTitle }

                    val isFree = element.selectFirst("div.GA_free.free, div.title_vol_btn_box_w a[href*=browserviewer]") != null
                    val hasPreview = element.selectFirst("div.GA_free") != null

                    if (hideLocked && !isFree) return@mapNotNull null

                    name = when {
                        isFree -> rawName
                        hasPreview -> "🔒 (Preview) $rawName"
                        else -> "🔒 $rawName"
                    }
                }
            }
        }

        SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/browserviewer/content_id/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        if (document.location().toHttpUrl().encodedPath.contains("/bib/reader")) {
            throw Exception("Novels are not supported.")
        }

        return client.fetchPages(document).ifEmpty { throw Exception("Log in via WebView and purchase this product to read.") }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Novels are not supported!"),
        TitleFilter(),
        AuthorFilter(),
        MagazineFilter(),
        PublisherFilter(),
        TitleTagFilter(),
        GenreFilter(),
        PriceFilter(),
        ReviewFilter(),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("お得（複数選択可）"),
        FreeFilter(),
        SampleFilter(),
        CampaignFilter(),
        Filter.Separator(),
        Filter.Header("作品の条件（複数選択可）"),
        NewestFilter(),
        CompleteFilter(),
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
