package eu.kanade.tachiyomi.extension.ja.yomonga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.SpeedBinbReader
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Yomonga : HttpSource() {
    override val name = "Yomonga"
    override val baseUrl = "https://www.yomonga.com"
    override val lang = "ja"
    override val supportsLatest = false

    private val json = Injekt.get<Json>()
    private val reader by lazy { SpeedBinbReader(client, headers, json, true) }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(json))
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/titles/?page_num=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/titles/".toHttpUrl().newBuilder()
            .addQueryParameter("page_num", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search_word", query)
        } else {
            val group = filters.firstInstance<FilterGroup>()
            if (group.state != 0) {
                val selected = group.values[group.state]
                if (selected.queryParam.isNotEmpty()) {
                    url.addQueryParameter(selected.queryParam, selected.value)
                }
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.book-box4").map {
            SManga.create().apply {
                title = it.selectFirst("div.book-box4-title")!!.text()
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = it.selectFirst("img.book-box4-thumbnail")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".paging-next.paging-click") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".intr-title")!!.text()
            author = document.select(".intr-writer").joinToString {
                it.text().replace(Regex("^(漫画|原作|キャラクター原案)："), "").trim()
            }
            description = document.selectFirst(".intr-text > .intr-desc")?.text()
            genre = document.select(".tag-wrapper .tag").joinToString { it.text() }
            status = when {
                genre?.contains("連載中") == true -> SManga.ONGOING
                genre?.contains("連載終了") == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst(".intr-thumbnail")?.absUrl("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".episode-list").mapNotNull {
            val link = it.selectFirst("a.button-type1") ?: return@mapNotNull null
            SChapter.create().apply {
                name = it.selectFirst("span")!!.text()
                setUrlWithoutDomain(link.absUrl("href"))
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> = reader.pageListParse(response)

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        FilterGroup(),
    )

    private class FilterGroup :
        Filter.Select<FilterOption>(
            "カテゴリ・キーワード",
            arrayOf(
                FilterOption("指定なし", "", ""),
                FilterOption("試し読み", "category_id", "3"),
                FilterOption("連載中", "category_id", "1"),
                FilterOption("リバイバル連載", "category_id", "2"),
                FilterOption("連載終了", "category_id", "4"),
                FilterOption("今だけ無料", "tag_id", "147"),
                FilterOption("オリジナル作品", "tag_id", "1"),
                FilterOption("ドラマ化", "tag_id", "8"),
                FilterOption("女性向け", "tag_id", "2"),
                FilterOption("グルメ", "tag_id", "49"),
                FilterOption("男性向け", "tag_id", "3"),
                FilterOption("エッセイ", "tag_id", "4"),
                FilterOption("TL", "tag_id", "5"),
                FilterOption("BL", "tag_id", "6"),
                FilterOption("コミカライズ", "tag_id", "152"),
                FilterOption("美少女", "tag_id", "12"),
                FilterOption("異世界", "tag_id", "20"),
                FilterOption("#DOELO", "tag_id", "26"),
                FilterOption("転生", "tag_id", "29"),
                FilterOption("闘病", "tag_id", "35"),
                FilterOption("オフィスラブ", "tag_id", "36"),
                FilterOption("H", "tag_id", "38"),
                FilterOption("水商売", "tag_id", "45"),
                FilterOption("感動", "tag_id", "51"),
                FilterOption("ドS", "tag_id", "59"),
                FilterOption("夫婦問題", "tag_id", "63"),
                FilterOption("結婚", "tag_id", "65"),
                FilterOption("コメディ", "tag_id", "72"),
                FilterOption("実録", "tag_id", "75"),
                FilterOption("家族", "tag_id", "80"),
                FilterOption("育児", "tag_id", "85"),
                FilterOption("サスペンス", "tag_id", "88"),
                FilterOption("心霊", "tag_id", "90"),
                FilterOption("ホラー", "tag_id", "151"),
                FilterOption("虐待", "tag_id", "93"),
                FilterOption("復讐", "tag_id", "102"),
                FilterOption("恋愛", "tag_id", "110"),
                FilterOption("ファンタジー", "tag_id", "114"),
                FilterOption("調教", "tag_id", "117"),
                FilterOption("OL", "tag_id", "122"),
                FilterOption("イケメン", "tag_id", "127"),
                FilterOption("ラブコメ", "tag_id", "131"),
                FilterOption("学園", "tag_id", "132"),
                FilterOption("BKコミックス", "tag_id", "138"),
                FilterOption("読み切り", "tag_id", "143"),
                FilterOption("スカッと", "tag_id", "148"),
                FilterOption("ボイスコミックあり", "tag_id", "149"),
                FilterOption("広告掲載中", "tag_id", "150"),
            ),
        )

    private class FilterOption(private val name: String, val queryParam: String, val value: String) {
        override fun toString() = name
    }
}
