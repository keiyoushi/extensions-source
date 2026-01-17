package eu.kanade.tachiyomi.extension.all.beauty3600000

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Beauty3600000 : ParsedHttpSource() {
    override val baseUrl = "https://3600000.xyz"
    override val lang = "all"
    override val name = "3600000 Beauty"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    // Latest
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    // Popular
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("a img.ls_lazyimg").attr("file")
        title = element.select(".entry-title").text()
        setUrlWithoutDomain(element.select(".entry-title > a").attr("abs:href"))
        status = SManga.COMPLETED
    }

    override fun popularMangaNextPageSelector() = ".next"
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/${getPageUri(page)}", headers)
    override fun popularMangaSelector() = "#blog-entries > article"

    // Search
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val categoryFilter = filterList.findInstance<CategoryFilter>()!!
        val tagFilter = filterList.findInstance<TagFilter>()!!
        val searchPath: String = when {
            categoryFilter.state != 0 -> "$baseUrl/category/${categoryFilter.toUriPart()}/${getPageUri(page)}"
            tagFilter.state != 0 -> "$baseUrl/tag/${tagFilter.toUriPart()}/${getPageUri(page)}"
            query.isNotBlank() -> {
                val tag = query.lowercase()
                    .replace(" ", "-")
                    .replace(".", "-")
                    .replace(specialCharRegex, "")
                "$baseUrl/tag/$tag/${getPageUri(page)}"
            }
            else -> throw IllegalStateException("Filter or query must be set")
        }
        return GET(searchPath, headers)
    }

    private val specialCharRegex by lazy { Regex("""[\[\]()!#]""") }

    override fun searchMangaSelector() = popularMangaSelector()

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val main = document.selectFirst("#main")!!
        title = main.select(".entry-title").text()
        description = main.select(".entry-title").text()
        genre = getGenres(document).joinToString(", ")
        thumbnail_url = main.select(".entry-content img.ls_lazyimg").attr("file")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun getGenres(element: Element): List<String> {
        return element.select(".cat-links a, .tags-links a")
            .map { it.text() }
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("link[rel=\"shortlink\"]").attr("href"))
        name = "Gallery"
        date_upload = DATE_FORMAT.tryParse(element.select("#main time").attr("datetime"))
    }

    override fun chapterListSelector() = "html"

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("noscript").remove()
        document.select(".entry-content img").forEachIndexed { i, it ->
            val itUrl = it.select("img.ls_lazyimg").attr("file")
            pages.add(Page(i, imageUrl = itUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        CategoryFilter(),
        TagFilter(),
    )

    open class UriPartFilter(
        displayName: String,
        private val valuePair: Array<Pair<String, String>>,
    ) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
        fun toUriPart() = valuePair[state].second
    }

    class CategoryFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("Any", ""),
            Pair("Aidol", "aidol"),
            Pair("China", "china"),
            Pair("Chinese", "chinese"),
            Pair("Cosplay", "cosplay"),
            Pair("Gravure", "gravure"),
            Pair("Japan", "japan"),
            Pair("Korea", "korea"),
            Pair("Magazine", "magazine"),
            Pair("Photobook", "photobook"),
            Pair("Thailand", "thailand"),
            Pair("Uncategorized", "uncategorized"),
            Pair("Western", "western"),
        ),
    )

    class TagFilter : UriPartFilter(
        "Tag",
        arrayOf(
            Pair("<Select tag>", ""),
            Pair("[XIUREN秀人网]", "xiuren%e7%a7%80%e4%ba%ba%e7%bd%91"),
            Pair("ギルドデジタル写真集", "%e3%82%ae%e3%83%ab%e3%83%89%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("[Graphis]", "graphis"),
            Pair("[Minisuka.tv]", "minisuka-tv"),
            Pair("FLASH フラッシュ", "flash-%e3%83%95%e3%83%a9%e3%83%83%e3%82%b7%e3%83%a5"),
            Pair("[The Black Alley]", "the-black-alley"),
            Pair("Weekly Playboy 週刊プレイボーイ", "weekly-playboy-%e9%80%b1%e5%88%8a%e3%83%97%e3%83%ac%e3%82%a4%e3%83%9c%e3%83%bc%e3%82%a4"),
            Pair("ＦＲＩＤＡＹデジタル写真集", "%ef%bd%86%ef%bd%92%ef%bd%89%ef%bd%84%ef%bd%81%ef%bd%99%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("FRIDAY フライデー", "friday-%e3%83%95%e3%83%a9%e3%82%a4%e3%83%87%e3%83%bc"),
            Pair("Young Magazine ヤングマガジン", "young-magazine-%e3%83%a4%e3%83%b3%e3%82%b0%e3%83%9e%e3%82%ac%e3%82%b8%e3%83%b3"),
            Pair("グラビア写真集", "%e3%82%b0%e3%83%a9%e3%83%93%e3%82%a2%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("Young Jump ヤングジャンプ", "young-jump-%e3%83%a4%e3%83%b3%e3%82%b0%e3%82%b8%e3%83%a3%e3%83%b3%e3%83%97"),
            Pair("Nogizaka46 (乃木坂46)", "nogizaka46-%e4%b9%83%e6%9c%a8%e5%9d%8246"),
            Pair("[LEEHEE EXPRESS]", "leehee-express"),
            Pair("[ArtGravia]", "artgravia"),
            Pair("[DJAWA]", "djawa"),
            Pair("[Digital Photobook]", "digital-photobook"),
            Pair("FLASHデジタル写真集", "flash%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("[JVID美模]", "jvid%e7%be%8e%e6%a8%a1"),
            Pair("ヌード写真集", "%e3%83%8c%e3%83%bc%e3%83%89%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("週プレ Photo Book", "%e9%80%b1%e3%83%97%e3%83%ac-photo-book"),
            Pair("AKB48", "akb48"),
            Pair("[Bimilstory]", "bimilstory"),
            Pair("[YOUMI尤蜜荟]", "youmi%e5%b0%a4%e8%9c%9c%e8%8d%9f"),
            Pair("Weekly SPA! 週刊SPA!", "weekly-spa-%e9%80%b1%e5%88%8aspa"),
            Pair("[Girlz-High]", "girlz-high"),
            Pair("デジタル写真集", "%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("Young Animal ヤングアニマル", "young-animal-%e3%83%a4%e3%83%b3%e3%82%b0%e3%82%a2%e3%83%8b%e3%83%9e%e3%83%ab"),
            Pair("NMB48", "nmb48"),
            Pair("Young Champion ヤングチャンピオン", "young-champion-%e3%83%a4%e3%83%b3%e3%82%b0%e3%83%81%e3%83%a3%e3%83%b3%e3%83%94%e3%82%aa%e3%83%b3"),
            Pair("週刊ポストデジタル写真集", "%e9%80%b1%e5%88%8a%e3%83%9d%e3%82%b9%e3%83%88%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("[XIAOYU画语界]", "xiaoyu%e7%94%bb%e8%af%ad%e7%95%8c"),
            Pair("[PURE MEDIA]", "pure-media"),
            Pair("アイドルワン I-One", "%e3%82%a2%e3%82%a4%e3%83%89%e3%83%ab%e3%83%af%e3%83%b3-i-one"),
            Pair("Young Gangan ヤングガンガン", "young-gangan-%e3%83%a4%e3%83%b3%e3%82%b0%e3%82%ac%e3%83%b3%e3%82%ac%e3%83%b3"),
            Pair("Hinatazaka46 (日向坂46)", "hinatazaka46-%e6%97%a5%e5%90%91%e5%9d%8246"),
            Pair("Big Comic Spirits ビッグコミックスピリッツ", "big-comic-spirits-%e3%83%93%e3%83%83%e3%82%b0%e3%82%b3%e3%83%9f%e3%83%83%e3%82%af%e3%82%b9%e3%83%94%e3%83%aa%e3%83%83%e3%83%84"),
            Pair("[LOOZY]", "loozy"),
            Pair("[BLUECAKE]", "bluecake"),
            Pair("B.L.T ビー・エル・ティー", "b-l-t-%e3%83%93%e3%83%bc%e3%83%bb%e3%82%a8%e3%83%ab%e3%83%bb%e3%83%86%e3%82%a3%e3%83%bc"),
            Pair("ENTAME 月刊エンタメ", "entame-%e6%9c%88%e5%88%8a%e3%82%a8%e3%83%b3%e3%82%bf%e3%83%a1"),
            Pair("Shukan Taishu (週刊大衆)", "shukan-taishu-%e9%80%b1%e5%88%8a%e5%a4%a7%e8%a1%86"),
            Pair("Shonen Magazine 週刊少年マガジン", "shonen-magazine-%e9%80%b1%e5%88%8a%e5%b0%91%e5%b9%b4%e3%83%9e%e3%82%ac%e3%82%b8%e3%83%b3"),
            Pair("HKT48", "hkt48"),
            Pair("Ex-Taishu EX大衆", "ex-taishu-ex%e5%a4%a7%e8%a1%86"),
            Pair("[HuaYang花漾]", "huayang%e8%8a%b1%e6%bc%be"),
            Pair("週刊現代デジタル写真集", "%e9%80%b1%e5%88%8a%e7%8f%be%e4%bb%a3%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("Shukan Jitsuwa 週刊実話", "shukan-jitsuwa-%e9%80%b1%e5%88%8a%e5%ae%9f%e8%a9%b1"),
            Pair("EX-MAX! エキサイティングマックス", "ex-max-%e3%82%a8%e3%82%ad%e3%82%b5%e3%82%a4%e3%83%86%e3%82%a3%e3%83%b3%e3%82%b0%e3%83%9e%e3%83%83%e3%82%af%e3%82%b9"),
            Pair("プレステージ出版 PRESTIGE Digital Book Series", "%e3%83%97%e3%83%ac%e3%82%b9%e3%83%86%e3%83%bc%e3%82%b8%e5%87%ba%e7%89%88-prestige-digital-book-series"),
            Pair("[WPB-net]", "wpb-net"),
            Pair("[MFStar模范学院]", "mfstar%e6%a8%a1%e8%8c%83%e5%ad%a6%e9%99%a2"),
            Pair("Shukan Post 週刊ポスト", "shukan-post-%e9%80%b1%e5%88%8a%e3%83%9d%e3%82%b9%e3%83%88"),
            Pair("Sakurazaka46 櫻坂46", "sakurazaka46-%e6%ab%bb%e5%9d%8246"),
            Pair("[Patreon]", "patreon"),
            Pair("Shukan Gendai 週刊現代", "shukan-gendai-%e9%80%b1%e5%88%8a%e7%8f%be%e4%bb%a3"),
            Pair("[IMISS爱蜜社]", "imiss%e7%88%b1%e8%9c%9c%e7%a4%be"),
            Pair("Shonen Champion 少年チャンピオン", "shonen-champion-%e5%b0%91%e5%b9%b4%e3%83%81%e3%83%a3%e3%83%b3%e3%83%94%e3%82%aa%e3%83%b3"),
            Pair("[YS-Web]", "ys-web"),
            Pair("[SAINT Photolife]", "saint-photolife"),
            Pair("Young King ヤングキング", "young-king-%e3%83%a4%e3%83%b3%e3%82%b0%e3%82%ad%e3%83%b3%e3%82%b0"),
            Pair("[UGirls尤果圈]", "ugirls%e5%b0%a4%e6%9e%9c%e5%9c%88"),
            Pair("DOLCE ドルチェ", "dolce-%e3%83%89%e3%83%ab%e3%83%81%e3%82%a7"),
            Pair("[XINGYAN星颜社]", "xingyan%e6%98%9f%e9%a2%9c%e7%a4%be"),
            Pair("SKE48", "ske48"),
            Pair("[SWEETBOX]", "sweetbox"),
            Pair("[Moon Night Snap]", "moon-night-snap"),
            Pair("BUBKA ブブカ", "bubka-%e3%83%96%e3%83%96%e3%82%ab"),
            Pair("BOMB! ボム", "bomb-%e3%83%9c%e3%83%a0"),
            Pair("[Sabra.net]", "sabra-net"),
            Pair("Shonen Sunday 少年サンデー", "shonen-sunday-%e5%b0%91%e5%b9%b4%e3%82%b5%e3%83%b3%e3%83%87%e3%83%bc"),
            Pair("[KIMLEMON]", "kimlemon"),
            Pair("[MyGirl美媛馆]", "mygirl%e7%be%8e%e5%aa%9b%e9%a6%86"),
            Pair("UTB アップトゥボーイ", "utb-%e3%82%a2%e3%83%83%e3%83%97%e3%83%88%e3%82%a5%e3%83%9c%e3%83%bc%e3%82%a4"),
            Pair("[FEILIN嗲囡囡]", "feilin%e5%97%b2%e5%9b%a1%e5%9b%a1"),
            Pair("SPA!デジタル写真集", "spa%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("アサ芸Secret！", "%e3%82%a2%e3%82%b5%e8%8a%b8secret%ef%bc%81"),
            Pair("Keyakizaka46 欅坂46", "keyakizaka46-%e6%ac%85%e5%9d%8246"),
            Pair("Gravure Photobook", "gravure-photobook"),
            Pair("aR (アール) Magazine", "ar-%e3%82%a2%e3%83%bc%e3%83%ab-magazine"),
            Pair("[DGC] (Desktop Gal Collection)", "dgc-desktop-gal-collection"),
            Pair("Morning Musume (モーニング娘。)", "morning-musume-%e3%83%a2%e3%83%bc%e3%83%8b%e3%83%b3%e3%82%b0%e5%a8%98%e3%80%82"),
            Pair("[PartyCat轟趴貓系列]", "partycat%e8%bd%9f%e8%b6%b4%e8%b2%93%e7%b3%bb%e5%88%97"),
            Pair("ヤンマガWeb", "%e3%83%a4%e3%83%b3%e3%83%9e%e3%82%acweb"),
            Pair("Dragon Age ドラゴンエイジ", "dragon-age-%e3%83%89%e3%83%a9%e3%82%b4%e3%83%b3%e3%82%a8%e3%82%a4%e3%82%b8"),
            Pair("Chinese Model", "chinese-model"),
            Pair("【デジタル限定 YJ PHOTO BOOK】", "%e3%80%90%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e9%99%90%e5%ae%9a-yj-photo-book%e3%80%91"),
            Pair("Fashion Magazine", "fashion-magazine"),
            Pair("STRiKE!", "strike"),
            Pair("グラビアザテレビジョン Gravure the Television", "%e3%82%b0%e3%83%a9%e3%83%93%e3%82%a2%e3%82%b6%e3%83%86%e3%83%ac%e3%83%93%e3%82%b8%e3%83%a7%e3%83%b3-gravure-the-television"),
            Pair("[X-City]", "x-city"),
            Pair("Manga Action 漫画アクション", "manga-action-%e6%bc%ab%e7%94%bb%e3%82%a2%e3%82%af%e3%82%b7%e3%83%a7%e3%83%b3"),
            Pair("[Moecco.ch]", "moecco-ch"),
            Pair("デジタルグラビア写真集", "%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e3%82%b0%e3%83%a9%e3%83%93%e3%82%a2%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("デジタル限定 YJ PHOTO BOOK", "%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e9%99%90%e5%ae%9a-yj-photo-book"),
            Pair("BRODYデジタル写真集", "brody%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("Wanibooks", "wanibooks"),
            Pair("[LOVEPOP]", "lovepop"),
            Pair("[Lilynah]", "lilynah"),
            Pair("[MakeModel]", "makemodel"),
            Pair("[Espasia Korea]", "espasia-korea"),
            Pair("[Espacia Korea]", "espacia-korea"),
            Pair("[PhotoChips]", "photochips"),
            Pair("Bamboo e-Book", "bamboo-e-book"),
            Pair("[Fantasy Story]", "fantasy-story"),
            Pair("[WANIMAL王動系列]", "wanimal%e7%8e%8b%e5%8b%95%e7%b3%bb%e5%88%97"),
            Pair("[iAsian4u]", "iasian4u"),
            Pair("Seigura 声優グランプリ", "seigura-%e5%a3%b0%e5%84%aa%e3%82%b0%e3%83%a9%e3%83%b3%e3%83%97%e3%83%aa"),
            Pair("漫画アクションデジタル写真集", "%e6%bc%ab%e7%94%bb%e3%82%a2%e3%82%af%e3%82%b7%e3%83%a7%e3%83%b3%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("Cyzo サイゾー", "cyzo-%e3%82%b5%e3%82%a4%e3%82%be%e3%83%bc"),
            Pair("XR Uncensored", "xr-uncensored"),
            Pair("NGT48", "ngt48"),
            Pair("Hello! Project Digital Books", "hello-project-digital-books"),
            Pair("STU48", "stu48"),
            Pair("[PUSSYLET]", "pussylet"),
            Pair("[Yo-U]", "yo-u"),
            Pair("[MISS TOUCH]", "miss-touch"),
            Pair("[BOMB.tv]", "bomb-tv"),
            Pair("[PDL潘多拉]", "pdl%e6%bd%98%e5%a4%9a%e6%8b%89"),
            Pair("SUPER☆GiRLS", "super%e2%98%86girls"),
            Pair("アサ芸SEXY女優写真集", "%e3%82%a2%e3%82%b5%e8%8a%b8sexy%e5%a5%b3%e5%84%aa%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("Haivia Photobook", "haivia-photobook"),
            Pair("Non-No ノンノ Magazine", "non-no-%e3%83%8e%e3%83%b3%e3%83%8e-magazine"),
            Pair("[PINK]", "pink"),
            Pair("[Digi-Gra]", "digi-gra"),
            Pair("#Escape", "escape"),
            Pair("[JOApictures]", "joapictures"),
            Pair("[HIGH FANTASY]", "high-fantasy"),
            Pair("CYBERJAPAN DANCERS", "cyberjapan-dancers"),
            Pair("Shukan Asahi Geino 週刊アサヒ芸能", "shukan-asahi-geino-%e9%80%b1%e5%88%8a%e3%82%a2%e3%82%b5%e3%83%92%e8%8a%b8%e8%83%bd"),
            Pair("Wunder Publishing House", "wunder-publishing-house"),
            Pair("Weekly ASCII 週刊アスキー", "weekly-ascii-%e9%80%b1%e5%88%8a%e3%82%a2%e3%82%b9%e3%82%ad%e3%83%bc"),
            Pair("[CreamSoda]", "creamsoda"),
            Pair("[Paranhosu]", "paranhosu"),
            Pair("[Ligui丽柜]", "ligui%e4%b8%bd%e6%9f%9c"),
            Pair("週刊実話デジタル写真集", "%e9%80%b1%e5%88%8a%e5%ae%9f%e8%a9%b1%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("BRODY ブロディ", "brody-%e3%83%96%e3%83%ad%e3%83%87%e3%82%a3"),
            Pair("[DCP-snaps]", "dcp-snaps"),
            Pair("PIMM'S （ピムス）", "pimms-%ef%bc%88%e3%83%94%e3%83%a0%e3%82%b9%ef%bc%89"),
            Pair("Ray レイ Magazine", "ray-%e3%83%ac%e3%82%a4-magazine"),
            Pair("USA 宇咲", "usa-%e5%ae%87%e5%92%b2"),
            Pair("Juice=Juice (ジュースジュース)", "juicejuice-%e3%82%b8%e3%83%a5%e3%83%bc%e3%82%b9%e3%82%b8%e3%83%a5%e3%83%bc%e3%82%b9"),
            Pair("ViVi ヴィヴィ", "vivi-%e3%83%b4%e3%82%a3%e3%83%b4%e3%82%a3"),
            Pair("anan (アンアン)", "anan-%e3%82%a2%e3%83%b3%e3%82%a2%e3%83%b3"),
            Pair("[BUNNY]", "bunny"),
            Pair("GIRLS-PEDIA", "girls-pedia"),
            Pair("ANGERME (アンジュルム)", "angerme-%e3%82%a2%e3%83%b3%e3%82%b8%e3%83%a5%e3%83%ab%e3%83%a0"),
            Pair("MIRU みる", "miru-%e3%81%bf%e3%82%8b"),
            Pair("DELA (Delightful Enchanting Lovely Angels)", "dela-delightful-enchanting-lovely-angels"),
            Pair("Big Comic Superior ビッグコミックスペリオール", "big-comic-superior-%e3%83%93%e3%83%83%e3%82%b0%e3%82%b3%e3%83%9f%e3%83%83%e3%82%af%e3%82%b9%e3%83%9a%e3%83%aa%e3%82%aa%e3%83%bc%e3%83%ab"),
            Pair("[PANS写真]", "pans%e5%86%99%e7%9c%9f"),
            Pair("ヤングチャンピオンデジグラ", "%e3%83%a4%e3%83%b3%e3%82%b0%e3%83%81%e3%83%a3%e3%83%b3%e3%83%94%e3%82%aa%e3%83%b3%e3%83%87%e3%82%b8%e3%82%b0%e3%83%a9"),
            Pair("[MARK]", "mark"),
            Pair("[Idol Line]", "idol-line"),
            Pair("BIS ビス Magazine", "bis-%e3%83%93%e3%82%b9-magazine"),
            Pair("Last Idol ラストアイドル", "last-idol-%e3%83%a9%e3%82%b9%e3%83%88%e3%82%a2%e3%82%a4%e3%83%89%e3%83%ab"),
            Pair("ヤンマガデジタル写真集", "%e3%83%a4%e3%83%b3%e3%83%9e%e3%82%ac%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("模特合集 Model Collection", "%e6%a8%a1%e7%89%b9%e5%90%88%e9%9b%86-model-collection"),
            Pair("[YouWu尤物馆]", "youwu%e5%b0%a4%e7%89%a9%e9%a6%86"),
            Pair("=LOVE (イコールラブ)", "love-%e3%82%a4%e3%82%b3%e3%83%bc%e3%83%ab%e3%83%a9%e3%83%96"),
            Pair("BIG ONE GIRLS", "big-one-girls"),
            Pair("SODデジタルヌード写真集", "sod%e3%83%87%e3%82%b8%e3%82%bf%e3%83%ab%e3%83%8c%e3%83%bc%e3%83%89%e5%86%99%e7%9c%9f%e9%9b%86"),
            Pair("[Lookas]", "lookas"),
            Pair("SUNNY GIRL", "sunny-girl"),
            Pair("[KiSiA]", "kisia"),
            Pair("SERA Photobook", "sera-photobook"),
            Pair("[Glamarchive]", "glamarchive"),
            Pair("READY Photobook", "ready-photobook"),
            Pair("[UMIZINE]", "umizine"),
            Pair("CrazyGiant", "crazygiant"),
            Pair("Chinese Model Private Photo", "chinese-model-private-photo"),
            Pair("TAKESHOBO", "takeshobo"),
            Pair("[SIDAM]", "sidam"),
            Pair("TOKYONIGHT", "tokyonight"),
            Pair("X-Level Photobook", "x-level-photobook"),
            Pair("[Korean Realgraphic]", "korean-realgraphic"),
            Pair("WXY ENT Photobook", "wxy-ent-photobook"),
            Pair("Pimm’s (ピムス)", "pimms-%e3%83%94%e3%83%a0%e3%82%b9"),
            Pair("Dolly Kiss", "dolly-kiss"),
            Pair("UHHUNG Magazine", "uhhung-magazine"),
            Pair("Dempagumi.inc", "dempagumi-inc"),
            Pair("SWEET Magazine", "sweet-magazine"),
            Pair("[SEESHE]", "seeshe"),
            Pair("PARADE Magazine", "parade-magazine"),
            Pair("AV Photoshoot", "av-photoshoot"),
            Pair("Peel the Apple", "peel-the-apple"),
            Pair("[Playboy Plus]", "playboy-plus"),
            Pair("[CREAM PIE]", "cream-pie"),
        ),
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun getPageUri(page: Int) = if (page == 1) {
        ""
    } else {
        "page/$page/"
    }

    // dirty hack to disable suggested mangas on Komikku
    // site doesn't support keyword search and too slow
    // https://github.com/komikku-app/komikku/blob/4323fd5841b390213aa4c4af77e07ad42eb423fc/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt#L176-L184
    @Suppress("Unused")
    @JvmName("getDisableRelatedMangasBySearch")
    fun disableRelatedMangasBySearch() = true

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
        }
    }
}
