package eu.kanade.tachiyomi.extension.zh.roumanwu

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.max

class Roumanwu : HttpSource(), ConfigurableSource {
    override val name = "肉漫屋"
    override val lang = "zh"
    override val supportsLatest = true

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val baseUrl = MIRRORS[
        max(MIRRORS.size - 1, preferences.getString(MIRROR_PREF, MIRROR_DEFAULT)!!.toInt()),
    ]

    override val client = network.client.newBuilder().addInterceptor(ScrambledImageInterceptor).build()

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home", headers)
    override fun popularMangaParse(response: Response) = response.nextjsData<HomePage>().getPopular().toMangasPage()

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response) = response.nextjsData<HomePage>().recentUpdatedBooks.toMangasPage()

    override fun searchMangaParse(response: Response) = response.nextjsData<BookList>().toMangasPage()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isNotBlank()) {
            GET("$baseUrl/search?term=$query&page=${page - 1}", headers)
        } else {
            val parts = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }
            GET("$baseUrl/books?page=${page - 1}$parts", headers)
        }

    override fun mangaDetailsParse(response: Response) = response.nextjsData<BookDetails>().book.toSManga()

    override fun chapterListParse(response: Response) = response.nextjsData<BookDetails>().book.getChapterList().reversed()

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.nextjsData<Chapter>()
        if (chapter.statusCode != null) throw Exception("服务器错误: ${chapter.statusCode}")
        return if (chapter.images != null) {
            chapter.getPageList()
        } else {
            @Suppress("NAME_SHADOWING")
            val response = client.newCall(GET(baseUrl + chapter.chapterAPIPath!!, headers)).execute()
            if (!response.isSuccessful) throw Exception("服务器错误: ${response.code}")
            response.parseAs<ChapterWrapper>().chapter.getPageList()
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("提示：搜索时筛选无效"),
        TagFilter(),
        StatusFilter(),
        SortFilter(),
    )

    private abstract class UriPartFilter(name: String, values: Array<String>) : Filter.Select<String>(name, values) {
        abstract fun toUriPart(): String
    }

    private class TagFilter : UriPartFilter("標籤", TAGS) {
        override fun toUriPart() = if (state == 0) "" else "&tag=${values[state]}"
    }

    private class StatusFilter : UriPartFilter("狀態", arrayOf("全部", "連載中", "已完結")) {
        override fun toUriPart() =
            when (state) {
                1 -> "&continued=true"
                2 -> "&continued=false"
                else -> ""
            }
    }

    private class SortFilter : UriPartFilter("排序", arrayOf("更新日期", "評分")) {
        override fun toUriPart() = if (state == 0) "" else "&sort=rating"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = androidx.preference.ListPreference(screen.context).apply {
            key = MIRROR_PREF
            title = MIRROR_PREF_TITLE
            entries = MIRRORS_DESC
            entryValues = MIRRORS.indices.map(Int::toString).toTypedArray()
            summary = MIRROR_PREF_SUMMARY

            setDefaultValue(MIRROR_DEFAULT)
        }
        screen.addPreference(mirrorPref)
    }

    companion object {
        private const val MIRROR_PREF = "MIRROR"
        private const val MIRROR_PREF_TITLE = "使用镜像网址"
        private const val MIRROR_PREF_SUMMARY = "使用镜像网址。重启软件生效。"

        // 地址: https://rou.pub/dizhi
        private val MIRRORS get() = arrayOf("https://rouman5.com", "https://roum15.xyz")
        private val MIRRORS_DESC get() = arrayOf("主站", "镜像")
        private const val MIRROR_DEFAULT = 1.toString() // use mirror

        private val TAGS get() = arrayOf("全部", "\u6B63\u59B9", "\u604B\u7231", "\u51FA\u7248\u6F2B\u753B", "\u8089\u617E", "\u6D6A\u6F2B", "\u5927\u5C3A\u5EA6", "\u5DE8\u4E73", "\u6709\u592B\u4E4B\u5A66", "\u5973\u5927\u751F", "\u72D7\u8840\u5287", "\u540C\u5C45", "\u597D\u53CB", "\u8ABF\u6559", "\u52A8\u4F5C", "\u5F8C\u5BAE", "\u4E0D\u502B")
    }

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromStream(this.body.byteStream())

    private inline fun <reified T> Response.nextjsData() =
        json.decodeFromString<NextData<T>>(this.asJsoup().select("#__NEXT_DATA__").html()).props.pageProps
}
