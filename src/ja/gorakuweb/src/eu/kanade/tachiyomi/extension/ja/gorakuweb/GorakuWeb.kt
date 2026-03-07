package eu.kanade.tachiyomi.extension.ja.gorakuweb

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import kotlin.collections.flatten

class GorakuWeb :
    HttpSource(),
    ConfigurableSource {
    override val name = "Goraku Web"
    override val baseUrl = "https://gorakuweb.com"
    override val lang = "ja"
    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, rscHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.extractNextJs<List<Entries>>()
        val mangas = data.orEmpty().map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section:has(h2:contains(更新作品)) div.bdr_lg.group").map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val isSeries = filter.type == "series"
        val url = "$baseUrl/${filter.type}".toHttpUrl().newBuilder().apply {
            if (isSeries) {
                addQueryParameter("completed", filter.value)
            } else {
                addQueryParameter("id", filter.value)
            }
        }.build()
        return if (isSeries) GET(url, rscHeaders).newBuilder().tag("series").build() else GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.tag() == "series") {
            val data = response.extractNextJs<SeriesList>()
            val mangas = data?.cardList?.flatten().orEmpty().map { it.toSManga(baseUrl) }
            return MangasPage(mangas, false)
        }

        val document = response.asJsoup()
        val mangas = document.select("div.bdr_lg.group").map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val seriesId = (element.selectFirst("a")!!.absUrl("href")).toHttpUrl().pathSegments[1]
        setUrlWithoutDomain(seriesId)
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/episode/${manga.url}", rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.extractNextJs<EpisodeProps>()!!.toSManga()

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<EpisodeProps>()!!
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return data.episodeList
            .filter { !hideLocked || it.disabled != true }
            .map { it.toSChapter() }
    }
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<EpisodeProps>() ?: throw Exception("This chapter is not available.")
        val pagesData = data.metadata
        val base = data.base
        val token = data.accessKey
        val key = data.keyBytes
        val iv = data.ivBytes
        return pagesData.pages.map {
            val url = "$base/${it.filename}".toHttpUrl().newBuilder()
                .encodedQuery("__token__=$token")
                .fragment("$key:$iv")
                .build()
                .toString()
            Page(it.page, imageUrl = url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun getFilterList(): FilterList = FilterList(CategoryFilter())

    private class CategoryFilter :
        SelectFilter(
            "Category",
            arrayOf(
                Triple("すべて", "series", ""),
                Triple("連載中", "series", "false"),
                Triple("連載終了", "series", "true"),
                Triple("ちょっとH", "search", "2265225572180105560"),
                Triple("サスペンス・ホラー", "search", "4436096323774752167"),
                Triple("裏社会・アングラ", "search", "5625949039609423274"),
                Triple("ヒューマンドラマ", "search", "2948889619045390126"),
                Triple("日常・グルメ", "search", "8002052417606270687"),
                Triple("学園・青春", "search", "8275313961510206169"),
                Triple("恋愛・ファンタジー", "search", "2791600463112259269"),
                Triple("動物", "search", "7606094847790899835"),
                Triple("バトル・アクション", "search", "1671211288187869228"),
            ),
        )

    private open class SelectFilter(displayName: String, private val vals: Array<Triple<String, String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val type: String
            get() = vals[state].second

        val value: String
            get() = vals[state].third
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
