package eu.kanade.tachiyomi.extension.ja.booklive

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
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class BookLive :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val desktopHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36")
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(SpeedBinbInterceptor())
        addCookie(listOf("SAFE_SEARCH_LEVEL" to "3", "IS_AGE_CHECKED" to "1"))
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 1 }))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 2 }))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = "$baseUrl/search/keyword".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("keyword", query)
            addFilter("category_ids", filters.firstInstanceOrNull<CategoryFilter>()?.value)
            addFilter("g_ids", filters.firstInstanceOrNull<GenreFilter>()?.value)
            addFilter("c_r", filters.firstInstanceOrNull<ReviewFilter>()?.value)
            addFilter("dc", filters.firstInstanceOrNull<DiscountFilter>()?.values?.joinToString(","))
            addFilter("relstat", filters.firstInstanceOrNull<ReleaseFilter>()?.values?.joinToString(","))
            addFilter("pubstat", filters.firstInstanceOrNull<PublishFilter>()?.values?.joinToString(","))
            addFilter("vol", filters.firstInstanceOrNull<VolumeFilter>()?.values?.joinToString(","))
            filters.firstInstanceOrNull<ExcludeFilter>()?.values?.forEach { addQueryParameter(it, "1") }
            addQueryParameter("sort", filters.firstInstance<SortFilter>().value)
            addQueryParameter("page_no", page.toString())
        }.build()
        val document = client.get(searchUrl, desktopHeaders).asJsoup()
        val mangas = document.select("ul.search_item_list > li.item").map {
            SManga.create().apply {
                val link = it.selectFirst("h3.title_ellipsis a")!!
                url = link.absUrl("href").toHttpUrl().pathSegments[3]
                title = link.text()
                thumbnail_url = it.selectFirst("div.picture img")?.absUrl("src")?.toHttpUrl()?.resolve("X.jpg")?.toString()
            }
        }
        val hasNextPage = document.selectFirst(".bl-pager li.page_nav_next:not(.page_stop)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/product/index/title_id/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), desktopHeaders).asJsoup()
        val mangaTitle = document.selectFirst("#breadcrumb .book_title")!!.text()
        val details = SManga.create().apply {
            title = mangaTitle
            author = document.select("#product_detail_area dl.author a").joinToString { it.text() }
            description = document.selectFirst("#product_summary .product_description")?.textOrNull()
            genre = buildList {
                document.selectFirst("#product_field")?.select("a[href*=/g_ids/]")?.mapTo(this) { it.textOrNull() }
                document.select("#product_tag_area .tags_list a").mapTo(this) { it.textOrNull() }
            }.joinToString()
            status = if (document.select("ul.product_topic li").any { it.textOrNull() == "完結" }) SManga.COMPLETED else SManga.ONGOING
            thumbnail_url = document.selectFirst("#product_detail_area .product_image img")?.absUrl("src")?.toHttpUrl()?.resolve("X.jpg")?.toString()
        }

        val isNovel = document.selectFirst("#product_detail_area .category-label--l, #product_detail_area .category-label--b") != null ||
            document.selectFirst("#product_field")?.select("a[href*=/g_ids/]")?.any { it.text().contains("小説") } == true
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val volumes = document.select("div.series_list_area li.item").ifEmpty { document.select("#product_detail_area") }
        val chapterList = volumes.mapNotNull {
            if (it.selectFirst("a.reservation_action") != null) return@mapNotNull null
            val isLocked = it.selectFirst("a.bl-free_reading") == null && it.selectFirst("a.bl-cart") != null
            if (hideLocked && isLocked) return@mapNotNull null
            val viewer = it.selectFirst("a.bl-bviewer")
            val freeLink = it.selectFirst("a.bl-free_reading[href*=/title_id/]")
            val link = it.selectFirst("h3 a")
            val volumeTitle = (link ?: it.selectFirst("h1")!!).text()
            SChapter.create().apply {
                val segments = (link?.absUrl("href") ?: document.location()).toHttpUrl().pathSegments
                url = segments[5]
                name = when {
                    !isLocked -> ""
                    viewer != null -> "🔒 (Preview) "
                    else -> "🔒 "
                } + volumeTitle.removePrefix(mangaTitle).trim().ifEmpty { volumeTitle }
                chapter_number = segments[5].toFloat()
                memo = buildJsonObject {
                    val cid = when {
                        viewer != null -> "${viewer.attr("data-title")}_${viewer.attr("data-vol")}"
                        freeLink != null -> freeLink.absUrl("href").toHttpUrl().pathSegments.let { s -> "${s[3]}_${s[5]}" }
                        else -> "${segments[3]}_${segments[5]}"
                    }
                    put("cid", cid)
                    put("novel", isNovel)
                }
            }
        }.sortedByDescending { it.chapter_number }

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/bviewer/?cid=${chapter.memo["cid"]!!.string}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["novel"]!!.boolean) throw Exception("Novels are not supported!")
        return client.fetchPages("$baseUrl/bib-api/bibGetCntntInfo".toHttpUrl(), chapter.memo["cid"]!!.string)
            .ifEmpty { throw Exception("Log in via WebView and purchase this volume to read.") }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Novels are not supported!"),
        SortFilter(),
        CategoryFilter(),
        GenreFilter(),
        ReviewFilter(),
        DiscountFilter(),
        ReleaseFilter(),
        PublishFilter(),
        VolumeFilter(),
        ExcludeFilter(),
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
