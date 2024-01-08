package eu.kanade.tachiyomi.extension.zh.picacomic

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

class Picacomic : HttpSource(), ConfigurableSource {
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "哔咔漫画"
    override val baseUrl = "https://picaapi.picacomic.com"
    private val leeway: Long = 10

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    private val blocklist = preferences.getString("BLOCK_GENRES", "")!!
        .split(',').map { it.trim() }

    private val basicHeaders = mapOf(
        "api-key" to "C69BAF41DA5ABD1FFEDC6D2FEA56B",
        "app-channel" to preferences.getString("APP_CHANNEL", "2")!!,
        "app-version" to "2.2.1.3.3.4",
        "app-uuid" to "defaultUuid",
        "app-platform" to "android",
        "app-build-version" to "44",
        "User-Agent" to "okhttp/3.8.1",
        "accept" to "application/vnd.picacomic.com.v1+json",
        "image-quality" to preferences.getString("IMAGE_QUALITY", "high")!!,
        "Content-Type" to "application/json; charset=UTF-8", // must be exactly matched!
    )

    private fun encrpt(url: String, time: Long, method: String, nonce: String): String {
        val hmacSha256Key = "~d}\$Q7\$eIni=V)9\\RK/P.RM4;9[7|@/CA}b~OW!3?EV`:<>M7pddUBL5n|0/*Cn"
        val apiKey = basicHeaders["api-key"]
        val path = url.substringAfter("$baseUrl/")
        val raw = "$path$time$nonce${method}$apiKey".lowercase(Locale.ROOT)
        return hmacSHA256(hmacSha256Key, raw).convertToString()
    }

    private val token: String by lazy {
        var t: String = preferences.getString("TOKEN", "")!!
        if (t.isEmpty() || isExpired(t)) {
            val username = preferences.getString("USERNAME", "")!!
            val password = preferences.getString("PASSWORD", "")!!
            if (username.isEmpty() || password.isEmpty()) {
                throw Exception("请在扩展设置界面输入用户名和密码")
            }

            t = getToken(username, password)
            preferences.edit().putString("TOKEN", t).apply()
        }
        t
    }

    private fun isExpired(token: String): Boolean {
        var parts: Array<String?> =
            token.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (parts.size == 2 && token.endsWith(".")) {
            parts = arrayOf(parts[0], parts[1], "")
        }
        if (parts.size != 3) {
            throw Exception(
                String.format(
                    "token should have 3 parts, but now there's %s.",
                    parts.size,
                ),
            )
        }

        val payload = parts[1]?.let { JSONObject(Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8)) }

        val exp = payload?.getLong("exp")?.let {
            Date(it * 1000)
        }
        val iat = payload?.getLong("iat")?.let {
            Date(it * 1000)
        }

        val todayTime = (floor((Date().time / 1000).toDouble()) * 1000).toLong() // truncate millis
        val futureToday = Date(todayTime + leeway * 1000)
        val pastToday = Date(todayTime - leeway * 1000)
        val expValid = exp == null || !pastToday.after(exp)
        val iatValid = iat == null || !futureToday.before(iat)
        return !expValid || !iatValid
    }

    private fun picaHeaders(url: String, method: String = "GET"): Headers {
        val time = System.currentTimeMillis() / 1000
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val nonce = (1..32).map { allowedChars.random() }
            .joinToString("")
        val signature = encrpt(url, time, method, nonce)
        return basicHeaders.toMutableMap().apply {
            put("time", time.toString())
            put("nonce", nonce)
            put("signature", signature)
            if (!url.endsWith("/auth/sign-in")) {
                // avoid recursive call
                put("authorization", token)
            }
        }.toHeaders()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun getToken(username: String, password: String): String {
        val url = "$baseUrl/auth/sign-in"
        val body = PicaLoginPayload(username, password)
            .let { Json.encodeToString(it) }
            .toRequestBody("application/json; charset=UTF-8".toMediaType())

        val response = client.newCall(
            POST(url, picaHeaders(url, "POST"), body),
        ).execute()

        if (!response.isSuccessful) {
            throw Exception("登录失败")
        }
        return json.decodeFromString<PicaResponse>(response.body.string()).data.token!!
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/comics?page=$page&s=dd"
        return GET(url, picaHeaders(url))
    }

    // for /comics/random, /comics/leaderboard
    private fun singlePageParse(response: Response): MangasPage {
        val comics = json.decodeFromString<PicaResponse>(response.body.string())
            .data.comics!!.let { json.decodeFromJsonElement<List<PicaSearchComic>>(it) }

        val mangas = comics
            .filter { !hitBlocklist(it) }
            .map { comic ->
                SManga.create().apply {
                    title = comic.title
                    author = comic.author
                    thumbnail_url = comic.thumb.let {
                        it.fileServer + "/static/" + it.path
                    }
                    url = "$baseUrl/comics/${comic._id}"
                    status = if (comic.finished) SManga.COMPLETED else SManga.ONGOING
                }
            }

        return MangasPage(mangas, response.request.url.toString().contains("/comics/random"))
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/comics/random"
        return GET(url, picaHeaders(url))
    }

    override fun latestUpdatesParse(response: Response): MangasPage = singlePageParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var sort: String? = null
        var category: String? = null
        var rankPath: String? = null

        // parse filters
        for (filter in filters) {
            when (filter) {
                is SortFilter -> sort = filter.toUriPart()
                is CategoryFilter -> category = filter.toUriPart()
                is RankFilter -> rankPath = filter.toUriPart()
                else -> throw Exception("unknown filter found")
            }
        }

        // return comics from leaderboard
        if (!rankPath.isNullOrEmpty()) {
            return GET("$baseUrl$rankPath", picaHeaders("$baseUrl$rankPath"))
        }

        // return comics from some category or just sort
        if (query.isEmpty()) {
            var url = "$baseUrl/comics?page=$page&s=$sort"
            if (!category.isNullOrEmpty()) {
                url += "&c=${URLEncoder.encode(category, "utf-8")}"
            }

            return GET(url, picaHeaders(url))
        }

        // return comics from some search
        // filters may be empty
        val url = "$baseUrl/comics/advanced-search?page=$page"

        val body = PicaSearchPayload(query, emptyList(), sort ?: "dd")
            .let { Json.encodeToString(it) }
            .toRequestBody("application/json; charset=UTF-8".toMediaType())

        return POST(url, picaHeaders(url, "POST"), body)
    }

    private fun hitBlocklist(comic: PicaSearchComic): Boolean {
        return ((comic.tags ?: (emptyList<String>() + comic.categories)))
            .map(String::trim)
            .any { it in blocklist }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().contains("/comics/leaderboard".toRegex())) {
            return singlePageParse(response)
        }

        val comics = json.decodeFromString<PicaResponse>(
            response.body.string(),
        ).data.comics!!.let { json.decodeFromJsonElement<PicaSearchComics>(it) }

        val mangas = comics.docs
            .filter { !hitBlocklist(it) }
            .map { comic ->
                SManga.create().apply {
                    title = comic.title
                    author = comic.author
                    thumbnail_url = comic.thumb.let { "${it.fileServer}/static/${it.path}" }
                    url = "$baseUrl/comics/${comic._id}"
                    status = if (comic.finished) SManga.COMPLETED else SManga.ONGOING
                }
            }

        return MangasPage(mangas, comics.page < comics.pages)
    }

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(manga.url, picaHeaders(manga.url))

    override fun mangaDetailsParse(response: Response): SManga {
        val comic = json.decodeFromString<PicaResponse>(
            response.body.string(),
        ).data.comic!!

        return SManga.create().apply {
            title = comic.title
            author = comic.author
            description = comic.description
            artist = comic.artist
            genre = ((comic.tags ?: (emptyList<String>() + comic.categories)))
                .map(String::trim)
                .distinct()
                .joinToString(", ")
            status = if (comic.finished) SManga.COMPLETED else SManga.ONGOING
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = "${manga.url}/eps?page=1"
        return GET(url, picaHeaders(url))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val comicId = response.request.url.pathSegments[1]

        val eps = json.decodeFromString<PicaResponse>(
            response.body.string(),
        ).data.eps!!

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())

        val ret = eps.docs.map {
            SChapter.create().apply {
                name = it.title
                url = "$baseUrl/comics/$comicId/order/${it.order}"
                date_upload = sdf.parse(it.updated_at)!!.time
            }
        }.toMutableList()

        if (eps.page < eps.pages) {
            val nextUrl = response.request.url.newBuilder()
                .setQueryParameter(
                    "page",
                    (eps.page + 1).toString(),
                ).build().toString()

            val nextResponse = client.newCall(GET(nextUrl, picaHeaders(nextUrl))).execute()
            ret += chapterListParse(nextResponse)
        }

        return ret
    }

    override fun pageListRequest(chapter: SChapter) = GET(
        chapter.url + "/pages?page=1",
        picaHeaders(chapter.url + "/pages?page=1"),
    )

    override fun pageListParse(response: Response): List<Page> {
        val pages = json.decodeFromString<PicaResponse>(
            response.body.string(),
        ).data.pages!!

        val ret = pages.docs.mapIndexed { index, picaPage ->
            val url = picaPage.media.let { "${it.fileServer}/static/${it.path}" }
            Page(index + (pages.page - 1) * pages.limit, "", url)
        }.toMutableList()

        if (pages.page < pages.pages) {
            val nextUrl = response.request.url.newBuilder()
                .setQueryParameter("page", (pages.page + 1).toString())
                .build().toString()

            val nextResponse = client.newCall(GET(nextUrl, picaHeaders(nextUrl))).execute()
            ret += pageListParse(nextResponse)
        }
        return ret
    }

    override fun imageUrlParse(response: Response): String {
        TODO("Not yet implemented")
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        CategoryFilter(),
        RankFilter(),
    )

    private class SortFilter : UriPartFilter(
        "排序",
        arrayOf(
            "新到旧" to "dd",
            "旧到新" to "da",
            "最多爱心" to "ld",
            "最多绅士指名" to "vd",
        ),
    )

    private class CategoryFilter : UriPartFilter(
        "类型",
        arrayOf("全部" to "") + arrayOf(
            "大家都在看", "牛牛不哭", "那年今天", "官方都在看",
            "嗶咔漢化", "全彩", "長篇", "同人", "短篇", "圓神領域",
            "碧藍幻想", "CG雜圖", "純愛", "百合花園", "後宮閃光", "單行本", "姐姐系",
            "妹妹系", "SM", "人妻", "NTR", "強暴",
            "艦隊收藏", "Love Live", "SAO 刀劍神域", "Fate",
            "東方", "禁書目錄", "Cosplay",
            "英語 ENG", "生肉", "性轉換", "足の恋", "非人類",
            "耽美花園", "偽娘哲學", "扶他樂園", "重口地帶", "歐美", "WEBTOON",
        ).map { it to it }.toTypedArray(),
    )

    private class RankFilter : UriPartFilter(
        "榜单",
        arrayOf(
            Pair("无", ""),
            Pair("过去24小时最热门", "/comics/leaderboard?tt=H24&ct=VC"),
            Pair("过去7天最热门", "/comics/leaderboard?tt=D7&ct=VC"),
            Pair("过去30天最热门", "/comics/leaderboard?tt=D30&ct=VC"),
        ),
    )

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        open fun toUriPart() = vals[state].second
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "USERNAME"
            title = "用户名"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("USERNAME", newValue as String).commit()
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = "PASSWORD"
            title = "密码"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("PASSWORD", newValue as String).commit()
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = "BLOCK_GENRES"
            title = "屏蔽词列表"
            dialogTitle = "屏蔽词列表"
            dialogMessage = "根据关键词过滤漫画，关键词之间用','分离。" +
                "关键词分为分类和标签两种，在热门和最新中只能按分类过滤（即在filter的类型中出现的词），" +
                "而在搜索中两者都可以"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("BLOCK_GENRES", newValue as String).commit()
            }
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "IMAGE_QUALITY"
            title = "图片质量"
            entries = arrayOf("原图", "低", "中", "高")
            entryValues = arrayOf("original", "low", "medium", "high")
            setDefaultValue("original")

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "APP_CHANNEL"
            title = "分流"
            entries = arrayOf("1", "2", "3")
            entryValues = entries
            setDefaultValue("1")

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.let(screen::addPreference)
    }
}
