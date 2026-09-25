package eu.kanade.tachiyomi.extension.ja.mangakingdom

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
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.head
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.io.IOException

@Source
abstract class MangaKingdom :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val desktopHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36")
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addCookie("is_verified_age_over_18" to "1")
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (!response.isSuccessful && request.url.pathSegments.first() == "viewer-launcher") {
                throw IOException("Log in via WebView and purchase this product to read.")
            }
            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/rank/", desktopHeaders).asJsoup()
        val mangas = document.select(".book-list-ranking .book-list--item").map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/search/new/".toHttpUrl().newBuilder()
            .addQueryParameter("search_option[category]", "0")
            .addQueryParameter("search_option[new]", "0")
            .addQueryParameter("search_option[pvfv_flag]", "0")
            .addQueryParameter("search_option[finished_flag]", "0")
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(url, desktopHeaders).asJsoup()
        val mangas = document.select(".book-list__new .book-list--item").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".paging--next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        fun Builder.addFilter(param: String, filter: Filter.Text) = filter.state.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
        fun Builder.addFilter(param: String, filter: SelectFilter) = filter.value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }
        fun Builder.addFilter(param: String, filter: Filter.CheckBox) = filter.state.takeIf { it }?.let { addQueryParameter(param, "1") }
        fun Builder.addFilter(param: String, filter: Filter.Group<CheckBoxItem>) = filter.state.filter { it.state }.forEach { addQueryParameter(param, it.id) }

        val url = "$baseUrl/search/detail".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("search_option[search_word]", query)
            addFilter("search_option[sort]", filters.firstInstance<SortFilter>())
            addFilter("search_option[categories][]", filters.firstInstance<CategoryFilter>())
            addFilter("search_option[genres][]", filters.firstInstance<GenreFilter>())
            addFilter("search_option[keywords][]", filters.firstInstance<KeywordFilter>())
            addFilter("search_option[magazines][]", filters.firstInstance<MagazineFilter>())
            addFilter("search_option[finished_flag]", filters.firstInstance<FinishedFlagFilter>())
            addFilter("search_option[free_campaign_type]", filters.firstInstance<FreeCampaignFilter>())
            addFilter("search_option[discount_chapter][]", filters.firstInstance<DiscountFilter>())
            addFilter("search_option[without_sexy_title][]", filters.firstInstance<WithoutSexyTitleFilter>())
            addFilter("search_option[mangarepo_num]", filters.firstInstance<MangaRepoNumFilter>())
            addFilter("search_option[pvfv_flag]", filters.firstInstance<DistributionFilter>())
            addFilter("search_option[point_fv_max]", filters.firstInstance<PointFvMaxFilter>())
            addFilter("search_option[point_pv_max]", filters.firstInstance<PointPvMaxFilter>())
            addFilter("search_option[volume_fv_min]", filters.firstInstance<VolumeFvMinFilter>())
            addFilter("search_option[volume_fv_max]", filters.firstInstance<VolumeFvMaxFilter>())
            addFilter("search_option[volume_pv_min]", filters.firstInstance<VolumePvMinFilter>())
            addFilter("search_option[volume_pv_max]", filters.firstInstance<VolumePvMaxFilter>())
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url, desktopHeaders).asJsoup()
        val mangas = document.select(".book-list-detail--box a.book-list--item").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".paging--next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        SortFilter(),
        CategoryFilter(),
        GenreFilter(),
        KeywordFilter(),
        MagazineFilter(),
        FinishedFlagFilter(),
        FreeCampaignFilter(),
        DiscountFilter(),
        WithoutSexyTitleFilter(),
        MangaRepoNumFilter(),
        DistributionFilter(),
        Filter.Separator(),
        Filter.Header("価格 (空欄で無指定 / pt)"),
        PointFvMaxFilter(),
        PointPvMaxFilter(),
        Filter.Separator(),
        Filter.Header("配信巻数・話数 (空欄で無指定)"),
        VolumeFvMinFilter(),
        VolumeFvMaxFilter(),
        VolumePvMinFilter(),
        VolumePvMaxFilter(),
    )

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.absUrl("href").toHttpUrl().pathSegments[1]
        title = element.selectFirst(".book-list--title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}/pv"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val document = client.get(getMangaUrl(manga), desktopHeaders).asJsoup()
        val bookTitle = document.selectFirst("h1.book-info--title span[itemprop=name]")!!.text()
        val details = SManga.create().apply {
            title = bookTitle
            author = document.selectFirst(".book-info--detail dt:contains(著者・作者) + dd")?.select("a")?.joinToString { it.ownText() }
            description = document.selectFirst(".book-info--desc p[itemprop=description]")?.textOrNull()
            genre = document.selectFirst(".book-info--detail dt:contains(ジャンル) + dd")?.select("a")?.joinToString { it.text() }
            val completed = document.selectFirst(".book-info--detail dt:contains(配信) + dd")?.textOrNull()?.contains("完結") == true
            status = if (completed) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("img.book-info--img")?.absUrl("src")
        }

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val lastPage = document.select(".paging__title .paging--item").maxOfOrNull { it.text().toInt() } ?: 1
        val otherPages = (2..lastPage).map { page ->
            async { client.get("$baseUrl/title/${manga.url}/pv/$page", desktopHeaders).asJsoup() }
        }

        val chapterList = (listOf(document) + otherPages.awaitAll()).flatMap {
            it.select("ul.book-chapter li.book-chapter--target").mapNotNull {
                val btn = it.selectFirst(".x-invoke-viewer--btn__selector")
                val isSample = btn?.hasClass("book-chapter--btn__sample") == true
                val isLocked = btn == null || isSample

                if (hideLocked && isLocked) return@mapNotNull null
                val prefix = when {
                    isSample -> "🔒 (Preview) "
                    isLocked -> "🔒 "
                    else -> ""
                }

                SChapter.create().apply {
                    url = it.selectFirst(".book-chapter--item")!!.attr("chapter-exid")
                    name = prefix + it.selectFirst(".book-chapter--title a")!!.text().replace(bookTitle, "").trim()
                    memo = buildJsonObject {
                        put("book", manga.url)
                        put("readType", btn?.attr("data-chapter-readtype") ?: "0")
                    }
                }
            }
        }

        SMangaUpdate(
            details,
            chapterList.sortedByDescending { it.url.toInt() },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/viewer-launcher/3/1/${chapter.memo["book"]!!.string}/pv/${chapter.url}/1/${chapter.memo["readType"]!!.string}/0/0"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val viewer = client.head(getChapterUrl(chapter), desktopHeaders).use { it.request.url }
        val ticket = viewer.queryParameter("p0")!!
        val obfuid = viewer.queryParameter("p1")!!
        val header = client.get(buildViewerUrl(ticket, "64kb_QVGA_h", obfuid, "header"), desktopHeaders).parseAs<HeaderResponse>()
        return header.contentInfos.flatMap { content ->
            val contentUrl = buildViewerUrl(ticket, content.name, obfuid, "content", header.dk)
            (content.startSceneNo..content.endSceneNo).map {
                Page(it - 1, imageUrl = "$contentUrl#$it")
            }
        }
    }

    private fun buildViewerUrl(t: String, fn: String, o: String, type: String, dk: String? = null): HttpUrl = "https://bv.k-manga.jp/public/app/action/bd00.php".toHttpUrl().newBuilder()
        .addQueryParameter("t", t)
        .addQueryParameter("fn", fn)
        .addQueryParameter("o", o)
        .addQueryParameter("type", type)
        .apply { if (dk != null) addQueryParameter("dk", dk) }
        .build()

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
