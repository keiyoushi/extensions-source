package eu.kanade.tachiyomi.extension.vi.yurineko

import eu.kanade.tachiyomi.extension.vi.yurineko.dto.ErrorResponseDto
import eu.kanade.tachiyomi.extension.vi.yurineko.dto.MangaDto
import eu.kanade.tachiyomi.extension.vi.yurineko.dto.MangaListDto
import eu.kanade.tachiyomi.extension.vi.yurineko.dto.ReadResponseDto
import eu.kanade.tachiyomi.extension.vi.yurineko.dto.UserDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class YuriNeko : HttpSource() {

    override val name = "YuriNeko"

    override val baseUrl = "https://yurineko.net"

    override val lang = "vi"

    override val supportsLatest = false

    private val apiUrl = "https://api.yurineko.net"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .addInterceptor(::authIntercept)
        .addInterceptor(::errorIntercept)
        .build()

    override fun headersBuilder() = Headers.Builder().add("Referer", "$baseUrl/")

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        val authCookie = cookies
            .firstOrNull { it.name == "user" }
            ?.let { URLDecoder.decode(it.value, "UTF-8") }
            ?.let { json.decodeFromString<UserDto>(it) }
            ?: return chain.proceed(request)

        val authRequest = request.newBuilder().apply {
            addHeader("Authorization", "Bearer ${authCookie.token}")
        }.build()
        return chain.proceed(authRequest)
    }
    private fun errorIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code >= 400) {
            val error = try {
                response.parseAs<ErrorResponseDto>()
            } catch (_: Throwable) {
                return response
            }
            response.close()
            throw IOException("${error.message}\nĐăng nhập qua WebView và thử lại.")
        }
        return response
    }

    override fun popularMangaRequest(page: Int): Request = GET(
        url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("lastest2")
            addQueryParameter("page", page.toString())
        }.build().toString(),
        cache = CacheControl.FORCE_NETWORK,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaListDto = response.parseAs<MangaListDto>()
        val currentPage = response.request.url.queryParameter("page")!!.toFloat()
        return mangaListDto.toMangasPage(currentPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH).trim()
                if (id.toIntOrNull() == null) {
                    throw Exception("ID tìm kiếm không hợp lệ (phải là một số).")
                }
                fetchMangaDetails(
                    SManga.create().apply {
                        url = "/manga/$id"
                    },
                )
                    .map { MangasPage(listOf(it), false) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return when {
            query.startsWith(PREFIX_TAG_SEARCH) ||
                query.startsWith(PREFIX_COUPLE_SEARCH) ||
                query.startsWith(PREFIX_DOUJIN_SEARCH) ||
                query.startsWith(PREFIX_AUTHOR_SEARCH) ||
                query.startsWith(PREFIX_TEAM_SEARCH) -> {
                val items = query.split(":")
                val searchType = items[0]
                val actualQuery = items[1].trim()
                if (actualQuery.toIntOrNull() == null) {
                    throw Exception("ID tìm kiếm không hợp lệ (phải là một số).")
                }
                GET(
                    apiUrl.toHttpUrl().newBuilder().apply {
                        addPathSegment("searchType")
                        addQueryParameter("type", searchType)
                        addQueryParameter("id", actualQuery)
                        addQueryParameter("page", page.toString())
                    }.build().toString(),
                )
            }
            query.isNotEmpty() -> {
                GET(
                    apiUrl.toHttpUrl().newBuilder().apply {
                        addPathSegment("search")
                        addQueryParameter("query", query)
                        addQueryParameter("page", page.toString())
                    }.build().toString(),
                )
            }
            else -> {
                for (filter in (if (filters.isEmpty()) getFilterList() else filters)) {
                    when (filter) {
                        is UriPartFilter -> if (filter.state != 0) {
                            when (filter.name) {
                                "Tag" -> return GET(
                                    apiUrl.toHttpUrl().newBuilder().apply {
                                        addPathSegment("searchType")
                                        addQueryParameter("type", "tag")
                                        addQueryParameter("id", filter.toUriPart())
                                        addQueryParameter("page", page.toString())
                                    }.build().toString(),
                                )
                                else -> continue
                            }
                        }
                        else -> {}
                    }
                }
                return popularMangaRequest(page)
            }
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(GET("$apiUrl${manga.url}"))
            .asObservableSuccess()
            .map { mangaDetailsParse(it) }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}")

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<MangaDto>().toSManga()

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl${manga.url}")

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaDto = response.parseAs<MangaDto>()
        val scanlator = mangaDto.team.joinToString(", ") { it.name }
        return mangaDto.chapters?.map { it.toSChapter(scanlator) } ?: emptyList()
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl${chapter.url}")

    override fun pageListParse(response: Response): List<Page> =
        response.parseAs<ReadResponseDto>().toPageList()

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Lưu ý rằng không thể vừa tìm kiếm vừa lọc bằng tag."),
        Filter.Header("Tìm kiếm sẽ được ưu tiên."),
        UriPartFilter("Tag", getGenreList()),
    )

    private fun getGenreList() = arrayOf(
        Pair("Sao cũng được", "0"),
        Pair("4-koma", "149"),
        Pair(">", "306"),
        Pair("Action", "113"),
        Pair("Adventure", "114"),
        Pair("Adult Life", "143"),
        Pair("Animal Ears", "175"),
        Pair("Age Gap", "179"),
        Pair("Anal", "209"),
        Pair("Ahegao", "211"),
        Pair("Anime", "214"),
        Pair("Amnesia", "242"),
        Pair("Autobiographical", "255"),
        Pair("Alien", "262"),
        Pair("Amputee", "277"),
        Pair("Assassin", "283"),
        Pair("Angel", "298"),
        Pair("Abuse", "300"),
        Pair("Anilingus", "308"),
        Pair("Blushing", "157"),
        Pair("Body Swap", "158"),
        Pair("Bisexual", "176"),
        Pair("Birthday", "194"),
        Pair("Big Breasts", "195"),
        Pair("Butts", "196"),
        Pair("BDSM", "199"),
        Pair("Boob Sex", "210"),
        Pair("Bath", "226"),
        Pair("Bullying", "241"),
        Pair("Biting", "270"),
        Pair("Blackmail", "280"),
        Pair("Biographical", "285"),
        Pair("Beach", "289"),
        Pair("BHTT", "304"),
        Pair("Comedy", "115"),
        Pair("College", "145"),
        Pair("Co-worker", "180"),
        Pair("Childhood Friends", "182"),
        Pair("Christmas", "189"),
        Pair("Creepy", "220"),
        Pair("Childification", "239"),
        Pair("Cheating", "267"),
        Pair("Clones", "271"),
        Pair("Cross-dressing", "288"),
        Pair("Chibi", "307"),
        Pair("Demon", "116"),
        Pair("Drama", "117"),
        Pair("Dark Skin", "208"),
        Pair("Drunk", "219"),
        Pair("Drugs", "236"),
        Pair("Disability", "252"),
        Pair("Delinquent", "258"),
        Pair("Deity", "265"),
        Pair("Depressing as fuck", "290"),
        Pair("Ecchi", "118"),
        Pair("Excuse me WTF?", "161"),
        Pair("Exhibitionism", "245"),
        Pair("Fantasy", "119"),
        Pair("Full Color", "148"),
        Pair("FBI Warning!!", "163"),
        Pair("Futanari", "201"),
        Pair("Food", "232"),
        Pair("Feet", "256"),
        Pair("Furry", "303"),
        Pair("Game", "120"),
        Pair("Gender Bender", "121"),
        Pair("Glasses", "156"),
        Pair("Guro", "206"),
        Pair("Ghost", "244"),
        Pair("Gyaru", "246"),
        Pair("Harem", "122"),
        Pair("Historical", "123"),
        Pair("Horror", "124"),
        Pair("Hints", "152"),
        Pair("Het", "160"),
        Pair("Halloween", "190"),
        Pair("Hypnosis", "254"),
        Pair("Height Gap", "281"),
        Pair("Hardcore", "292"),
        Pair("Isekai", "144"),
        Pair("Idol", "169"),
        Pair("Incest", "187"),
        Pair("Idiot Couple", "282"),
        Pair("Introspective", "286"),
        Pair("Insane Amounts of Sex", "296"),
        Pair("Kuudere", "235"),
        Pair("Lỗi: không tìm thấy trai", "153"),
        Pair("Love Triangle", "183"),
        Pair("Loli", "197"),
        Pair("Light Novel", "216"),
        Pair("Lactation", "260"),
        Pair("Lots of sex", "269"),
        Pair("Martial Arts", "125"),
        Pair("Mecha", "126"),
        Pair("Military", "127"),
        Pair("Music", "128"),
        Pair("Mystery", "129"),
        Pair("Manhua", "146"),
        Pair("Manhwa", "147"),
        Pair("Moe Paradise", "164"),
        Pair("Mahou Shoujo", "168"),
        Pair("Maid", "172"),
        Pair("Monster Girl", "173"),
        Pair("Marriage", "188"),
        Pair("Massage", "204"),
        Pair("Masturbation", "205"),
        Pair("Mangaka", "227"),
        Pair("Mermaid", "234"),
        Pair("Moderate amounts of sex", "268"),
        Pair("Miko", "301"),
        Pair("No Text", "150"),
        Pair("New Year's", "191"),
        Pair("Netorare", "198"),
        Pair("NSFW", "229"),
        Pair("Ninja", "287"),
        Pair("Non-moe art", "302"),
        Pair("Office Lady", "174"),
        Pair("Oneshot", "218"),
        Pair("Official", "222"),
        Pair("Orgy", "261"),
        Pair("Omegaverse", "276"),
        Pair("Parody", "130"),
        Pair("Psychological", "131"),
        Pair("Pay for Gay", "162"),
        Pair("Polyamory", "185"),
        Pair("Pocky Game", "212"),
        Pair("Prostitution", "240"),
        Pair("Player", "257"),
        Pair("Prequel", "272"),
        Pair("Post-Apocalyptic", "273"),
        Pair("Philosophical", "274"),
        Pair("R18", "1"),
        Pair("Romance", "132"),
        Pair("Reversal", "159"),
        Pair("Roommates", "181"),
        Pair("Rape", "203"),
        Pair("Robot", "264"),
        Pair("School Life", "133"),
        Pair("Sci-Fi", "134"),
        Pair("Slice of  Life", "137"),
        Pair("Sports", "138"),
        Pair("Supernatural", "139"),
        Pair("Science Babies", "165"),
        Pair("Student x Teacher", "166"),
        Pair("Siscon", "167"),
        Pair("School Girl", "215"),
        Pair("Spin-off", "223"),
        Pair("Subtext", "231"),
        Pair("Sleeping", "249"),
        Pair("Sequel", "251"),
        Pair("Swimsuits", "263"),
        Pair("Stalking", "266"),
        Pair("Space", "291"),
        Pair("Spanking", "299"),
        Pair("Tragedy", "142"),
        Pair("Tomboy", "170"),
        Pair("Tsundere", "177"),
        Pair("Threesome", "184"),
        Pair("Twins", "186"),
        Pair("Thất Tịch", "193"),
        Pair("Toys", "200"),
        Pair("Tentacles", "202"),
        Pair("Tailsex", "237"),
        Pair("Time Travel", "243"),
        Pair("Transgender", "284"),
        Pair("Vampire", "140"),
        Pair("Violence", "141"),
        Pair("Valentine", "192"),
        Pair("Watersports", "278"),
        Pair("Wholesome", "279"),
        Pair("Witch", "293"),
        Pair("Web Novel", "305"),
        Pair("Yuri", "151"),
        Pair("Yankee", "171"),
        Pair("Yandere", "178"),
        Pair("Yuri Crush", "228"),
        Pair("Yaoi", "230"),
        Pair("Zombies", "238"),
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(body.string())
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val PREFIX_TAG_SEARCH = "tag:"
        const val PREFIX_TEAM_SEARCH = "team:"
        const val PREFIX_AUTHOR_SEARCH = "author:"
        const val PREFIX_DOUJIN_SEARCH = "origin:"
        const val PREFIX_COUPLE_SEARCH = "couple:"
    }
}
