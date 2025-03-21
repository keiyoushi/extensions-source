package eu.kanade.tachiyomi.extension.ja.gaugaumonsterplus

import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbInterceptor
import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GaugauMonsterPlus : ParsedHttpSource() {

    override val name = "がうがうモンスター＋"

    override val baseUrl = "https://gaugau.futabanet.jp"

    override val lang = "ja"

    override val supportsLatest = false

    private val json = Injekt.get<Json>()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(json))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/works?page=$page", headers)

    override fun popularMangaSelector() = ".works__grid .list__box"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("h4 a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        author = element.select(".list__text span a").joinToString { it.text() }
        genre = element.select(".tag__item").joinToString { it.text() }
        thumbnail_url = element.selectFirst(".thumbnail .img-books")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = "ol.pagination li.next a:not([href='#'])"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.ifEmpty { getFilterList() }.filterIsInstance<TagFilter>().first()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegments("list/search-result")
                addQueryParameter("word", query)
            } else if (tagFilter.state != 0) {
                addPathSegments("list/tag")
                addPathSegment(tagFilter.values[tagFilter.state])
            } else {
                addPathSegment("list/works")
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".mbOff h1")!!.text()
        author = document.select(".list__text span a").joinToString { it.text() }
        description = document.selectFirst("p.mbOff")?.text()
        genre = document.select(".tag__item").joinToString { it.text() }
        thumbnail_url = document.selectFirst(".list__box .thumbnail .img-books")?.absUrl("src")
    }

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl${manga.url}/episodes", headers)

    override fun chapterListSelector() =
        "#episodes .episode__grid:not(:has(.episode__button-app, .episode__button-complete)) a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val episodeNum = element.selectFirst(".episode__num")!!.text()
        val episodeTitle = element.selectFirst(".episode__title")?.text()
            ?.takeIf { t -> t.isNotBlank() }

        setUrlWithoutDomain(element.attr("href"))
        name = buildString(episodeNum.length + 2 + (episodeTitle?.length ?: 0)) {
            append(episodeNum)

            if (episodeTitle != null) {
                append("「")
                append(episodeTitle)
                append("」")
            }
        }
        // There is actually a consistent chapter number format on this site, which we
        // take advantage of because the built-in chapter number parser doesn't properly
        // parse the fractions.
        chapter_number = CHAPTER_NUMBER_REGEX.matchEntire(episodeNum)?.let { m ->
            val major = m.groupValues[1].toFloat()
            val minor = m.groupValues[2].toFloat()

            major + minor / 10
        } ?: -1F
    }

    private val reader by lazy { SpeedBinbReader(client, headers, json) }

    override fun pageListParse(document: Document) = reader.pageListParse(document)

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("フリーワード検索はジャンル検索では機能しません"),
        TagFilter(),
    )
}

private val CHAPTER_NUMBER_REGEX = Regex("""^第(\d+)話\((\d+)\)$""")

// https://gaugau.futabanet.jp/list/search
// copy([...document.querySelectorAll(".tagSearch__item")].map((e) => `"${e.textContent.trim()}",`).join("\n"))
private class TagFilter : Filter.Select<String>(
    "ジャンル",
    arrayOf(
        "なし",
        "転生・召喚",
        "少年向け",
        "青年向け",
        "大人向け",
        "少女向け",
        "追放",
        "女性向け",
        "チート",
        "もふもふ",
        "小説",
        "スローライフ",
        "時代物",
        "異世界転生",
        "官能",
        "歴史",
        "ライトノベル",
        "婚約破棄",
        "暮らし",
        "料理",
        "結婚",
        "医療",
        "恋",
        "溺愛",
        "教養",
        "ミリタリー",
        "逆ハーレム",
        "戦争",
        "寝取られ",
        "おっぱい",
        "趣味",
        "巨乳",
        "ファッション",
        "グルメ",
        "TS",
        "最強",
        "天才",
        "勇者",
        "癒し",
        "魔王",
        "雑学",
        "女主人公",
        "成り上がり",
        "探偵",
        "引きこもり",
        "社畜",
        "ツンデレ",
        "子育て",
        "ヤンデレ",
        "絵本",
        "青年",
        "女性",
        "少女",
        "少年",
        "アウトドア",
        "おっさん",
        "スポーツ",
        "トレーニング",
        "幼馴染",
        "同級生",
        "ダイエット",
        "高校生",
        "令嬢",
        "美少女",
        "エッセイ",
        "美人",
        "妹",
        "貴族",
        "イケメン",
        "ヒューマンドラマ",
        "双子",
        "冒険",
        "魔法",
        "4コマ",
        "魔女",
        "ショートショート",
        "サキュバス",
        "スライム",
        "エルフ",
        "ヤンキー",
        "ギャンブル",
        "獣人",
        "サブカル",
        "ミステリー",
        "乙女ゲーム",
        "サスペンス",
        "シリアス",
        "ホラー",
        "ラブコメ",
        "恋愛",
        "胸キュン",
        "いじめ",
        "アクション",
        "サバイバル",
        "バトル",
        "絶望",
        "アニメ",
        "ギャグ",
        "動物",
        "猫",
        "非異世界",
        "百合",
        "内政",
        "BL",
        "ダンジョン",
        "幼なじみ",
        "田舎",
        "学園",
        "悪役令嬢",
        "なろう",
        "異世界",
        "メディアミックス",
        "ファンタジー",
        "お仕事もの",
        "人外",
        "オフィスラブ",
        "日常",
        "夫婦",
        "家族",
        "アラサー",
        "アラフォー",
        "中高年",
        "老後",
        "話題",
        "重版",
        "音楽",
        "幕末",
        "お金",
        "長期連載",
        "読み切り",
        "1話完結",
        "ゲーム",
        "人情",
        "ハーレム",
    ),
)
