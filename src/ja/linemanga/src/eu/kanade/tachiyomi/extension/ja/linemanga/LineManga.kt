package eu.kanade.tachiyomi.extension.ja.linemanga

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.WeekFields

@Source
abstract class LineManga :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "$baseUrl/api"
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 412) {
                throw IOException("This service can only be used from Japan.")
            }
            if (response.code == 404 && request.url.encodedPath.contains("book/viewer")) {
                throw IOException("Log in via WebView and rent or purchase this chapter via web or their LINE Manga app.")
            }
            response
        }
    }

    // Requires either desktop UA or X-Requested-With.
    override fun Headers.Builder.configureHeaders() = set("X-Requested-With", "XMLHttpRequest")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/$GENDER_RANKING".toHttpUrl().newBuilder()
            .addQueryParameter("gender", "0")
            .addQueryParameter("page", page.toString())
            .build()

        return client.get(url).parseAs<EntryResponse>().result.toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val weekDay = LocalDate.now(JST).get(WeekFields.SUNDAY_START.dayOfWeek())
        val url = "$apiUrl/$DAILY_LIST".toHttpUrl().newBuilder()
            .addQueryParameter("week_day", weekDay.toString())
            .addQueryParameter("page", page.toString())
            .build()

        return client.get(url).parseAs<Result>().toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search_product/list".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .addQueryParameter("page", page.toString())
                .build()

            return client.get(url).parseAs<EntryResponse>().result.toMangasPage()
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val url = "$apiUrl/${filter.type}".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                when (filter.type) {
                    DAILY_LIST -> addQueryParameter("week_day", filter.value)
                    GENRE_LIST -> addQueryParameter("genre_id", filter.value)
                    else -> addQueryParameter("gender", filter.value)
                }
            }
            .build()

        val response = client.get(url)
        return if (filter.type == GENDER_RANKING) {
            response.parseAs<EntryResponse>().result
        } else {
            response.parseAs<Result>()
        }.toMangasPage()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/product/periodic?id=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // E -> fully bundled episodes/volumes, S or Z -> episodes in parts
        val isBundled = manga.url.startsWith("E")
        val url = "$apiUrl/book/product_list".toHttpUrl().newBuilder()
            .addQueryParameter("product_id", manga.url)
            .apply {
                if (!isBundled) {
                    addQueryParameter("need_read_info", "1")
                    addQueryParameter("rows", "1000")
                    addQueryParameter("is_periodic", "1")
                }
            }
            .build()

        val result = client.get(url).parseAs<EntryDetails>().result
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = result.rows
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }

        return SMangaUpdate(
            result.product.toSManga(),
            if (isBundled) chapterList else chapterList.asReversed(),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/book/viewer?id=${chapter.url}"

    // TODO: Check entries for maybe Publus?:  mediado_token: '', mediado_contents_url: '', mediado_contents_file: 'configuration_pack.json',
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/book/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("id", chapter.url)
            .build()

        val options = client.get(url).parseViewer()
        return if (options.isPortal) parsePortalPages(options.portalPages) else parseImgPages(options.imgs)
    }

    // A portal chapter inlines several MB of scramble data in one <script> that Jsoup would buffer whole.
    // The statements are emitted in reading order, one per line, so the body can be scanned as it downloads.
    private fun Response.parseViewer(): ViewerOptions = use { response ->
        val options = ViewerOptions()
        var inPortalPage = false

        val source = response.body.source()
        while (true) {
            val line = source.readUtf8Line()?.trimStart() ?: break

            when {
                line.startsWith("imgs[") -> inPortalPage = false
                line.startsWith("portal_pages[") -> {
                    if (line.contains(".metadata.m[")) options.portalPages.last().m += line.quotedValue() else inPortalPage = true
                }
                line.startsWith("'url'") -> {
                    val imageUrl = line.quotedValue()
                    if (inPortalPage) options.portalPages += PortalPage(imageUrl) else options.imgs += imageUrl
                }
                line.startsWith("'hc'") -> options.portalPages.last().hc = line.numberValue()
                line.startsWith("'bwd'") -> options.portalPages.last().bwd = line.numberValue()
                line.startsWith("isPortal") -> options.isPortal = line.contains("true")
            }
        }
        options
    }

    private fun parseImgPages(imgs: List<String>): List<Page> = imgs
        .filterNot { it.contains("inline_ads_banner") }
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    private fun parsePortalPages(pages: List<PortalPage>): List<Page> = pages.mapIndexed { index, page ->
        Page(index, imageUrl = "${page.url}#${page.hc}:${page.bwd}:${page.m.joinToString(":")}")
    }

    private fun String.quotedValue(): String = QUOTED_VALUE.find(this)!!.groupValues[1]

    private fun String.numberValue(): Int = NUMBER_VALUE.find(this)!!.groupValues[1].toInt()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private val QUOTED_VALUE = Regex("""'([^']*)'\s*[,;]?\s*$""")
        private val NUMBER_VALUE = Regex("""(\d+)\s*,?\s*$""")
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}

private class ViewerOptions {
    var isPortal = false
    val imgs = mutableListOf<String>()
    val portalPages = mutableListOf<PortalPage>()
}

private class PortalPage(val url: String) {
    var hc = 0 // horizontal block count
    var bwd = 0 // block width/height in px
    val m = mutableListOf<String>() // scramble map (base-35 encoded values)
}
