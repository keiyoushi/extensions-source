package eu.kanade.tachiyomi.extension.vi.hentaivn

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiVN : ParsedHttpSource(), ConfigurableSource {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val defaultBaseUrl = "https://hentaivn.tv"
    override val baseUrl by lazy { preferences.getString(PREF_KEY_BASE_URL, defaultBaseUrl)!! }

    override val name = "HentaiVN"
    override val lang = "vi"
    override val supportsLatest = true

    private val searchUrl by lazy { "$baseUrl/forum/search-plus.php" }
    private val searchByAuthorUrl by lazy { "$baseUrl/tim-kiem-tac-gia.html" }
    private val searchAllURL by lazy { "$baseUrl/tim-kiem-truyen.html" }

    override val client: OkHttpClient by lazy {
        val baseClient = if (preferences.getBoolean(PREF_KEY_ENABLE_CLOUDFLARE_BYPASS, true)) {
            network.cloudflareClient
        } else {
            network.client
        }

        val domain = baseUrl.toHttpUrl().host
        baseClient.newBuilder()
            .addNetworkInterceptor(CookieInterceptor(domain, "view1", "1"))
            .addNetworkInterceptor(CookieInterceptor(domain, "view4", "1"))
            .setRandomUserAgent(
                preferences.getPrefUAType(),
                preferences.getPrefCustomUA(),
            )
            .rateLimit(1)
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // latestUpdates
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/chap-moi.html?page=$page", headers)
    }

    override fun latestUpdatesSelector() = ".block-item ul li.item"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select(".box-description a, .box-description-2 a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
        }
        manga.thumbnail_url = imageFromElement(element.selectFirst(".box-cover a img, .box-cover-2 a img"))
        return manga
    }

    override fun latestUpdatesNextPageSelector() = ".pagination *:contains(Next)"

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/danh-sach.html?page=$page", headers)
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val authorFilter =
            (if (filters.isEmpty()) getFilterList() else filters).find { it is Author } as Author
        val searchAllFilter =
            (if (filters.isEmpty()) getFilterList() else filters).find { it is Alls } as Alls
        return when {
            authorFilter.state.isNotEmpty() -> client.newCall(
                GET(
                    searchByAuthorUrl.toHttpUrl().newBuilder()
                        .addQueryParameter("key", authorFilter.state)
                        .addQueryParameter("page", page.toString())
                        .build().toString(),
                    headers,
                ),
            )
                .asObservableSuccess()
                .map { response -> latestUpdatesParse(response) }

            // Some manga that are not searchable in advanced search create this filter to fix
            searchAllFilter.state.isNotEmpty() -> client.newCall(
                GET(
                    searchAllURL.toHttpUrl().newBuilder()
                        .addQueryParameter("key", searchAllFilter.state)
                        .addQueryParameter("page", page.toString())
                        .build().toString(),
                    headers,
                ),
            )
                .asObservableSuccess()
                .map { response -> latestUpdatesParse(response) }

            query.startsWith(PREFIX_ID_SEARCH) -> {
                val ids = query.removePrefix(PREFIX_ID_SEARCH)
                client.newCall(searchMangaByIdRequest(ids))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, ids) }
            }
            query.toIntOrNull() != null -> {
                client.newCall(searchMangaByIdRequest(query))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, query) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query)
            addQueryParameter("dou", "")
            addQueryParameter("char", "")
            addQueryParameter("search", "")

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is TextField -> setQueryParameter(filter.key, filter.state)
                    is GenreList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("tag[]", it) }
                    is GroupList -> {
                        val group = getGroupList()[filter.state]
                        addQueryParameter("group", group.id)
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (document.select("p").toString()
            .contains("Bạn chỉ có thể sử dụng chức năng này khi đã đăng ký thành viên")
        ) {
            throw Exception("Đăng nhập qua WebView để kích hoạt tìm kiếm")
        }

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() =
        ".search-ul .search-li, ${latestUpdatesSelector()}"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select(".search-des a, .box-description a, .box-description-2 a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
        }
        manga.thumbnail_url = imageFromElement(element.selectFirst("div.search-img img, .box-cover a img, .box-cover-2 a img"))
        return manga
    }

    override fun searchMangaNextPageSelector() = ".pagination *:contains(Cuối), .pagination *:contains(Next)"

    private fun searchMangaByIdRequest(id: String) = GET("$searchAllURL?key=$id", headers)
    private fun searchMangaByIdParse(response: Response, ids: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/$ids-doc-truyen-id.html"
        return MangasPage(listOf(details), false)
    }

    // Detail
    private val genreUrlRegex = Regex("""\"(list-info-theloai-mobile\.php?.+)\"""")

    override fun mangaDetailsParse(document: Document): SManga {
        if (document.toString().contains("document.cookie = \"mobile=1")) { // Desktop version
            val infoElement = document.select(".main > .page-left > .left-info > .page-info")
            return SManga.create().apply {
                title = document.selectFirst(".breadcrumb2 li:last-child span")!!.text()
                author = infoElement.select("p:contains(Tác giả:) a").text()
                description = infoElement.select(":root > p:contains(Nội dung:) + p").text()
                genre = infoElement.select("p:contains(Thể loại:) a").joinToString { it.text() }
                thumbnail_url =
                    imageFromElement(document.selectFirst(".main > .page-right > .right-info > .page-ava > img"))
                status =
                    parseStatus(infoElement.select("p:contains(Tình Trạng:) a").firstOrNull()?.text())
            }
        } else { // Mobile version
            val id = document.location().substringAfterLast("/").substringBefore("-")
            val documentText = document.toString()

            return SManga.create().apply {
                val thumbnailElem = document.selectFirst(".content-images-1 img.cover-1")
                thumbnail_url = imageFromElement(thumbnailElem)

                title = thumbnailElem?.attr("alt")?.substringBeforeLast(" Cover")?.trim() ?: client
                    .newCall(GET("$baseUrl/list-info-ten-mobile.php?id_anime=$id"))
                    .execute()
                    .asJsoup()
                    .select("h3")
                    .text()

                val genreUrl = genreUrlRegex.find(documentText)?.groupValues?.get(1)
                genre = client
                    .newCall(GET("$baseUrl/$genreUrl"))
                    .execute()
                    .asJsoup()
                    .select("a.tag")
                    .joinToString { it.text() }

                val infoElement = client
                    .newCall(GET("$baseUrl/list-info-all-mobile.php?id_anime=$id"))
                    .execute()
                    .asJsoup()
                author = infoElement.select("p:contains(Tác giả:) a").text()
                status = parseStatus(infoElement.select("p:contains(Tình Trạng:) a").firstOrNull()?.text())
                description = infoElement.select("p:contains(Nội dung:) + p").text()
            }
        }
    }

    // Chapter
    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/").substringBefore('-')
        return GET("$baseUrl/list-showchapter.php?idchapshow=$mangaId", headers)
    }

    override fun chapterListSelector() = "table.listing > tbody > tr"

    override fun chapterFromElement(element: Element): SChapter {
        if (element.select("a").isEmpty()) throw Exception(element.select("h2").html())
        val chapter = SChapter.create()
        element.select("a").first()!!.let {
            chapter.name = it.select("h2").text()
            chapter.setUrlWithoutDomain(it.attr("href"))
        }
        chapter.date_upload = parseDate(element.select("td:nth-child(2)").text().trim())
        return chapter
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#image > img").mapIndexed { i, e ->
            Page(i, imageUrl = imageFromElement(e))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

    private fun parseDate(dateString: String): Long {
        return kotlin.runCatching {
            dateFormat.parse(dateString)?.time
        }.getOrNull() ?: 0L
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Đã hoàn thành") -> SManga.COMPLETED
        status.contains("Tạm ngưng") -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun imageFromElement(element: Element?): String? {
        if (element == null) return null

        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
            else -> element.attr("abs:src")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_KEY_ENABLE_CLOUDFLARE_BYPASS
            title = TITLE_ENABLE_CLOUDFLARE_BYPASS
            summary = SUMMARY_ENABLE_CLOUDFLARE_BYPASS

            setDefaultValue(true)

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_KEY_BASE_URL
            title = TITLE_BASE_URL
            summary = SUMMARY_BASE_URL

            setDefaultValue(defaultBaseUrl)
            dialogTitle = TITLE_BASE_URL

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_KEY_RANDOM_UA
            title = TITLE_RANDOM_UA
            entries = ENTRIES_RANDOM_UA
            entryValues = VALUES_RANDOM_UA
            summary = "%s"
            setDefaultValue("off")

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_KEY_CUSTOM_UA
            title = TITLE_CUSTOM_UA
            summary = SUMMARY_CUSTOM_UA
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    Headers.Builder().add("User-Agent", newValue as String).build()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    true
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(screen.context, "Chuỗi đại diện người dùng không hợp lệ: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }.also(screen::addPreference)
    }

    private class Alls : Filter.Text("Tìm tất cả")
    private class Author : Filter.Text("Tác giả")
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)
    private class TransGroup(name: String, val id: String) : Filter.CheckBox(name) {
        override fun toString(): String {
            return name
        }
    }

    private class GroupList(groups: Array<TransGroup>) :
        Filter.Select<TransGroup>("Nhóm dịch", groups)

    override fun getFilterList() = FilterList(
        Filter.Header("Bộ lọc tìm tất cả không dùng được với bộ lọc khác!"),
        Filter.Header("Bộ lọc tác giả không dùng được với các bộ lọc khác!"),
        Alls(),
        Author(),
        TextField("Doujinshi", "dou"),
        TextField("Nhân vật", "char"),
        GroupList(getGroupList()),
        GenreList(getGenreList()),
    )

    // console.log(jQuery.makeArray($('ul.ul-search > li').map((i, e) => `Genre("${e.textContent}", "${e.children[0].value}")`)).join(',\n'))
    // https://hentaivn.autos/forum/search-plus.php
    private fun getGenreList() = listOf(
        Genre("3D Hentai", "3"),
        Genre("Action", "5"),
        Genre("Adult", "116"),
        Genre("Adventure", "203"),
        Genre("Ahegao", "20"),
        Genre("Anal", "21"),
        Genre("Angel", "249"),
        Genre("Ảnh động", "131"),
        Genre("Animal", "127"),
        Genre("Animal girl", "22"),
        Genre("Áo Dài", "279"),
        Genre("Apron", "277"),
        Genre("Artist CG", "115"),
        Genre("Based Game", "130"),
        Genre("BBM", "257"),
        Genre("BBW", "251"),
        Genre("BDSM", "24"),
        Genre("Bestiality", "25"),
        Genre("Big Ass", "133"),
        Genre("Big Boobs", "23"),
        Genre("Big Penis", "32"),
        Genre("Blackmail", "267"),
        Genre("Bloomers", "27"),
        Genre("BlowJobs", "28"),
        Genre("Body Swap", "29"),
        Genre("Bodysuit", "30"),
        Genre("Bondage", "254"),
        Genre("Breast Sucking", "33"),
        Genre("BreastJobs", "248"),
        Genre("Brocon", "31"),
        Genre("Brother", "242"),
        Genre("Business Suit", "241"),
        Genre("Catgirls", "39"),
        Genre("Che ít", "101"),
        Genre("Che nhiều", "129"),
        Genre("Cheating", "34"),
        Genre("Chikan", "35"),
        Genre("Chinese Dress", "271"),
        Genre("Có che", "100"),
        Genre("Comedy", "36"),
        Genre("Comic", "120"),
        Genre("Condom", "210"),
        Genre("Cosplay", "38"),
        Genre("Cousin", "2"),
        Genre("Crotch Tattoo", "275"),
        Genre("Cunnilingus", "269"),
        Genre("Dark Skin", "40"),
        Genre("Daughter", "262"),
        Genre("Deepthroat", "268"),
        Genre("Demon", "132"),
        Genre("DemonGirl", "212"),
        Genre("Devil", "104"),
        Genre("DevilGirl", "105"),
        Genre("Dirty", "253"),
        Genre("Dirty Old Man", "41"),
        Genre("DogGirl", "260"),
        Genre("Double Penetration", "42"),
        Genre("Doujinshi", "44"),
        Genre("Drama", "4"),
        Genre("Drug", "43"),
        Genre("Ecchi", "45"),
        Genre("Elder Sister", "245"),
        Genre("Elf", "125"),
        Genre("Exhibitionism", "46"),
        Genre("Fantasy", "123"),
        Genre("Father", "243"),
        Genre("Femdom", "47"),
        Genre("Fingering", "48"),
        Genre("Footjob", "108"),
        Genre("Foxgirls", "259"),
        Genre("Full Color", "37"),
        Genre("Furry", "202"),
        Genre("Futanari", "50"),
        Genre("GangBang", "51"),
        Genre("Garter Belts", "206"),
        Genre("Gender Bender", "52"),
        Genre("Ghost", "106"),
        Genre("Glasses", "56"),
        Genre("Gothic Lolita", "264"),
        Genre("Group", "53"),
        Genre("Guro", "55"),
        Genre("Hairy", "247"),
        Genre("Handjob", "57"),
        Genre("Harem", "58"),
        Genre("HentaiVN", "102"),
        Genre("Historical", "80"),
        Genre("Horror", "122"),
        Genre("Housewife", "59"),
        Genre("Humiliation", "60"),
        Genre("Idol", "61"),
        Genre("Imouto", "244"),
        Genre("Incest", "62"),
        Genre("Insect (Côn Trùng)", "26"),
        Genre("Isekai", "280"),
        Genre("Không che", "99"),
        Genre("Kimono", "110"),
        Genre("Kuudere", "265"),
        Genre("Lolicon", "63"),
        Genre("Maids", "64"),
        Genre("Manhua", "273"),
        Genre("Manhwa", "114"),
        Genre("Masturbation", "65"),
        Genre("Mature", "119"),
        Genre("Miko", "124"),
        Genre("Milf", "126"),
        Genre("Mind Break", "121"),
        Genre("Mind Control", "113"),
        Genre("Mizugi", "263"),
        Genre("Monster", "66"),
        Genre("Monstergirl", "67"),
        Genre("Mother", "103"),
        Genre("Nakadashi", "205"),
        Genre("Netori", "1"),
        Genre("Non-hen", "201"),
        Genre("NTR", "68"),
        Genre("Nun", "272"),
        Genre("Nurse", "69"),
        Genre("Old Man", "211"),
        Genre("Oneshot", "71"),
        Genre("Oral", "70"),
        Genre("Osananajimi", "209"),
        Genre("Paizuri", "72"),
        Genre("Pantyhose", "204"),
        Genre("Ponytail", "276"),
        Genre("Pregnant", "73"),
        Genre("Rape", "98"),
        Genre("Rimjob", "258"),
        Genre("Romance", "117"),
        Genre("Ryona", "207"),
        Genre("Scat", "134"),
        Genre("School Uniform", "74"),
        Genre("SchoolGirl", "75"),
        Genre("Series", "87"),
        Genre("Sex Toys", "88"),
        Genre("Shimapan", "246"),
        Genre("Short Hentai", "118"),
        Genre("Shota", "77"),
        Genre("Shoujo", "76"),
        Genre("Siscon", "79"),
        Genre("Sister", "78"),
        Genre("Slave", "82"),
        Genre("Sleeping", "213"),
        Genre("Small Boobs", "84"),
        Genre("Son", "278"),
        Genre("Sports", "83"),
        Genre("Stockings", "81"),
        Genre("Supernatural", "85"),
        Genre("Sweating", "250"),
        Genre("Swimsuit", "86"),
        Genre("Tall Girl", "266"),
        Genre("Teacher", "91"),
        Genre("Tentacles", "89"),
        Genre("Time Stop", "109"),
        Genre("Tomboy", "90"),
        Genre("Tracksuit", "252"),
        Genre("Transformation", "256"),
        Genre("Trap", "92"),
        Genre("Truyện Việt", "274"),
        Genre("Tsundere", "111"),
        Genre("Twins", "93"),
        Genre("Twintails", "261"),
        Genre("Vampire", "107"),
        Genre("Vanilla", "208"),
        Genre("Virgin", "95"),
        Genre("Webtoon", "270"),
        Genre("X-ray", "94"),
        Genre("Yandere", "112"),
        Genre("Yaoi", "96"),
        Genre("Yuri", "97"),
        Genre("Zombie", "128"),
    )

    // jQuery.makeArray($('#container > div > div > div.box-box.textbox > form > ul:nth-child(8) > li').map((i, e) => `TransGroup("${e.textContent}", "${e.children[0].value}")`)).join(',\n')
    // https://hentaivn.autos/forum/search-plus.php
    private fun getGroupList() = arrayOf(
        TransGroup("Tất cả", "0"),
        TransGroup("Đang cập nhật", "1"),
        TransGroup("Góc Hentai", "3"),
        TransGroup("Hakihome", "4"),
        TransGroup("LXERS", "5"),
        TransGroup("Hentai-Homies", "6"),
        TransGroup("BUZPLANET", "7"),
        TransGroup("Trang Sally", "8"),
        TransGroup("Loli Rules The World", "9"),
        TransGroup("XXX Inc", "10"),
        TransGroup("Kobato9x", "11"),
        TransGroup("Blazing Soul", "12"),
        TransGroup("TAYXUONG", "13"),
        TransGroup("[S]ky [G]arden [G]roup", "14"),
        TransGroup("Bloomer-kun", "15"),
        TransGroup("DHT", "16"),
        TransGroup("TruyenHen18", "17"),
        TransGroup("iHentaiManga", "18"),
        TransGroup("Quân cảng Kancolle X", "19"),
        TransGroup("LHMANGA", "20"),
        TransGroup("Ship of The Dream", "21"),
        TransGroup("Fallen Angels", "22"),
        TransGroup("TruyenHentai2H", "23"),
        TransGroup("Lạc Thiên", "24"),
        TransGroup("69HENTAIXXX", "25"),
        TransGroup("DHL", "26"),
        TransGroup("Hentai-AdutsManga", "27"),
        TransGroup("Hatsu Kaze Desu Translator Team", "28"),
        TransGroup("IHentai69", "29"),
        TransGroup("Zest", "30"),
        TransGroup("Demon Victory Team", "31"),
        TransGroup("NTR Victory Team", "32"),
        TransGroup("Rori Saikou", "33"),
        TransGroup("Bullet Burn Team", "34"),
        TransGroup("RE Team", "35"),
        TransGroup("Rebelliones", "36"),
        TransGroup("Shinto", "37"),
        TransGroup("Sexual Paradise", "38"),
        TransGroup("FA Dislike Team", "39"),
        TransGroup("Triggered Team", "41"),
        TransGroup("T.K Translation Team", "42"),
        TransGroup("Mabu MG", "43"),
        TransGroup("Team Zentsu", "44"),
        TransGroup("Sweeter Than Salt", "46"),
        TransGroup("Cà rà cà rà Cặt", "47"),
        TransGroup("Paradise Of The Happiness", "48"),
        TransGroup("Furry Break the 4th Wall", "49"),
        TransGroup("The Ignite Team", "50"),
        TransGroup("Cuồng Loli", "51"),
        TransGroup("Depressed Lolicons Squad - DLS", "52"),
        TransGroup("Heaven Of The Fuck", "53"),
    )

    companion object {
        const val PREFIX_ID_SEARCH = "id:"

        const val RESTART_TACHIYOMI = "Khởi động lại Tachiyomi để áp dụng thay đổi."

        const val PREF_KEY_ENABLE_CLOUDFLARE_BYPASS = "enable_cloudflare"
        const val TITLE_ENABLE_CLOUDFLARE_BYPASS = "Kích hoạt bỏ qua Cloudflare"
        const val SUMMARY_ENABLE_CLOUDFLARE_BYPASS = "Nếu bật khi không cần thiết, có thể gây lỗi \"Bỏ qua Cloudflare thất bại\" giả."

        const val PREF_KEY_BASE_URL = "override_base_url_${BuildConfig.VERSION_CODE}"
        const val TITLE_BASE_URL = "Thay đổi tên miền"
        const val SUMMARY_BASE_URL = "Thay đổi này là tạm thời và sẽ bị xoá khi cập nhật tiện ích mở rộng."

        const val PREF_KEY_RANDOM_UA = "pref_key_random_ua_"
        const val TITLE_RANDOM_UA = "Chuỗi đại diện người dùng ngẫu nhiên"
        val ENTRIES_RANDOM_UA = arrayOf("Tắt", "Máy tính", "Di động")
        val VALUES_RANDOM_UA = arrayOf("off", "desktop", "mobile")

        const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua_"
        const val TITLE_CUSTOM_UA = "Chuỗi đại diện người dùng tuỳ chỉnh"
        const val SUMMARY_CUSTOM_UA = "Để trống để dùng chuỗi đại diện người dùng mặc định của ứng dụng. Cài đặt này bị vô hiệu nếu chuỗi đại diện người dùng ngẫu nhiên được bật."
    }
}
