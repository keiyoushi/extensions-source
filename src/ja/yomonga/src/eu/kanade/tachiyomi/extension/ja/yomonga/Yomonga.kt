package eu.kanade.tachiyomi.extension.ja.yomonga

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
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class Yomonga : KeiSource() {
    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(SpeedBinbInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/titles/?page_num=$page").toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return client.get(url.build()).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst(".intr-title")!!.text()
            author = document.select(".intr-writer").joinToString {
                it.text().replace(AUTHOR_ROLE_REGEX, "").trim()
            }
            description = document.selectFirst(".intr-text > .intr-desc")?.textOrNull()
            genre = document.select(".tag-wrapper .tag").joinToString { it.text() }
            status = when {
                genre?.contains("連載中") == true -> SManga.ONGOING
                genre?.contains("連載終了") == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst(".intr-thumbnail")?.absUrl("src")
        }

        val chapterList = document.select(".episode-list[data-episode_no]").map {
            SChapter.create().apply {
                val link = it.selectFirst("a.button-type1")!!.absUrl("href").toHttpUrl()
                url = link.queryParameter("cid")!!
                name = it.selectFirst(".episode-name")!!.text()
                memo = buildJsonObject {
                    put("title", link.pathSegments[1])
                }
            }
        }

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/titles/${chapter.memo["title"]!!.string}/?episode=${chapter.url}&cid=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.fetchPages("$baseUrl/binb/sws/apis/bibGetCntntInfo.php".toHttpUrl(), chapter.url)

    override fun getFilterList(data: JsonElement?) = FilterList(
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

    companion object {
        private val AUTHOR_ROLE_REGEX = Regex("^(漫画|原作|キャラクター原案)：")
    }
}
