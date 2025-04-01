package eu.kanade.tachiyomi.extension.all.pornpics

import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PornPics : SimpleParsedHttpSource(), ConfigurableSource {

    override val baseUrl = "https://www.pornpics.com"
    override val lang = "all"
    override val name = "PornPics"
    override val supportsLatest = true

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val categories = listOf(
            "默认: default" to null,
            "亚洲: asian" to "/asian",
            "中国: chinese" to "/chinese",
            "韩国: korean" to "/korean",
            "日本: japanese" to "/japanese",
            "俄罗斯: russian" to "/russian",
            "乌克兰: ukrainian" to "/ukrainian",

            "巨乳: big-tits" to "/big-tits",
            "巨乳: natural-tits" to "/natural-tits",
            "角色扮演: cosplay" to "/cosplay",
            "可以: cute" to "/cute",
            "眼镜: glasses" to "/glasses",
            "女仆: maid" to "/maid",
            "护士: nurse" to "/nurse",
            "修女: nun" to "/nun",
            "丝袜: stockings" to "/stockings",
            "双胞胎: twins" to "/twins",
        )
        ListPreference(screen.context).apply {
            key = "CATEGORIES_KEY"
            title = "Categories"
            summary = "Categories"
            entries = categories.map { it.first }.toTypedArray()
            entryValues = categories.map { it.second }.toTypedArray()
        }.also(screen::addPreference)
    }

    override fun simpleMangaSelector() = "#main li.thumbwook > a.rel-link"

    override fun simpleMangaFromElement(element: Element) = throw UnsupportedOperationException()

    @Serializable
    class MangaDto(
        val desc: String,
        @SerialName("g_url")
        val url: String,
        @SerialName("t_url")
        val thumbnailUrl: String,
    )

    override fun simpleMangaParse(response: Response): MangasPage {
        val httpUrl = response.request.url
        val responseAsJson = httpUrl.queryParameter("category_id") == null &&
            httpUrl.queryParameter("offset")!!.toInt() > 0

        val mangas = if (responseAsJson) {
            val data = response.parseAs<List<MangaDto>>()
            data.map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.url)
                    title = it.desc
                    thumbnail_url = it.thumbnailUrl
                }
            }
        } else {
            val doc = response.asJsoup()
            doc.select(simpleMangaSelector()).map {
                SManga.create().apply {
                    val imgEl = it.selectFirst("img")!!
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = imgEl.attr("alt")
                    thumbnail_url = imgEl.absUrl("data-src")
                }
            }
        }
        return MangasPage(mangas, mangas.size == PAGE_SIZE)
    }

    override fun simpleNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int) = buildMangasPageRequest(page, 1)

    override fun latestUpdatesRequest(page: Int) = buildMangasPageRequest(page, 2)

    /**
     * period:
     *  popular  : 1
     *  recent   : 2
     *  rating   : 3
     *  likes    : 4
     *  views    : 5
     *  comments : 6
     *
     * category_id
     *  2585 + period
     */
    private fun buildMangasPageRequest(page: Int, period: Int): Request {
        val categories = getPreferences().getString("CATEGORIES_KEY", "")!!
        Log.d(this.name, "buildMangasPageRequest: $categories")
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE)
            .addQueryParameter("offset", (page - 1) * PAGE_SIZE)

        when {
            categories.isBlank() -> {
                Log.d(this.name, "categories.isNotBlank(): $categories")
                builder.addPathSegment("/popular/api/galleries/list")
                    .addQueryParameter("category_id", 2585 + period)
                    .addQueryParameter("period", period)
                    .addEncodedQueryParameter("lang", "en")
            }

            period == 1 -> builder.addPathSegment("/$categories")
            period == 2 -> builder.addPathSegment("/$categories/recent")
        }

        return GET(builder.build(), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val thumbEl = document.selectFirst(simpleMangaSelector())!!
        val imgEl = thumbEl.selectFirst("img")!!
        return SManga.create().apply {
            title = imgEl.attr("alt")
            genre = document.select(".gallery-info__item a").joinToString { it.text() }
        }
    }

    override fun chapterListSelector() = "li.mobile a.alt-lang-item[data-lang=en]"
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        chapter_number = 0F
        setUrlWithoutDomain(element.absUrl("href"))
        name = "Gallery"
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(simpleMangaSelector())
            .mapIndexed { index, element ->
                Page(index, imageUrl = element.absUrl("href"))
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = baseUrl.toHttpUrl().newBuilder()
            .addEncodedQueryParameter("q", query)

        filters.firstInstance<UriPartFilter>().toUriPart()?.let {
            searchUrl.addEncodedQueryParameter("date", it)
        }
        return GET(searchUrl.build(), headers)
    }

    override fun getFilterList() = FilterList(
        UriPartFilter(
            "sort",
            arrayOf(
                Pair("Most Popular", null),
                Pair("Most Recent", "latest"),
            ),
        ),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun HttpUrl.Builder.addQueryParameter(encodedName: String, encodedValue: Int) =
        addQueryParameter(encodedName, encodedValue.toString())

    companion object {
        val PAGE_SIZE = 20
    }
}
