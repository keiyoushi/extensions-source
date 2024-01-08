package eu.kanade.tachiyomi.extension.all.comico

import android.webkit.CookieManager
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

open class Comico(
    final override val baseUrl: String,
    final override val name: String,
    private val langCode: String,
) : HttpSource() {
    final override val supportsLatest = true

    override val lang = langCode.substring(0, 2)

    protected open val apiUrl = baseUrl.replace("www", "api")

    private val json by injectLazy<Json>()

    private val cookieManager by lazy { CookieManager.getInstance() }

    private val imgHeaders by lazy {
        headersBuilder().set("Accept", ACCEPT_IMAGE).build()
    }

    private val apiHeaders: Headers
        get() = headersBuilder().apply {
            val time = System.currentTimeMillis() / 1000L
            this["X-comico-request-time"] = time.toString()
            this["X-comico-check-sum"] = sha256(time)
            this["X-comico-client-immutable-uid"] = ANON_IP
            this["X-comico-client-accept-mature"] = "Y"
            this["X-comico-client-platform"] = "web"
            this["X-comico-client-store"] = "other"
            this["X-comico-client-os"] = "aos"
            this["Origin"] = baseUrl
        }.build()

    override val client = network.client.newBuilder()
        .cookieJar(
            object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) =
                    cookies.filter { it.matches(url) }.forEach {
                        cookieManager.setCookie(url.toString(), it.toString())
                    }

                override fun loadForRequest(url: HttpUrl) =
                    cookieManager.getCookie(url.toString())?.split("; ")
                        ?.mapNotNull { Cookie.parse(url, it) } ?: emptyList()
            },
        ).build()

    override fun headersBuilder() = Headers.Builder()
        .set("Accept-Language", langCode)
        .set("User-Agent", userAgent)
        .set("Referer", "$baseUrl/")

    override fun latestUpdatesRequest(page: Int) =
        paginate("all_comic/daily/$day", page)

    override fun popularMangaRequest(page: Int) =
        paginate("all_comic/ranking/trending", page)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isEmpty()) {
            paginate("all_comic/read_for_free", page)
        } else {
            POST("$apiUrl/search", apiHeaders, search(query, page))
        }

    override fun chapterListRequest(manga: SManga) =
        GET(apiUrl + manga.url + "/episode", apiHeaders)

    override fun pageListRequest(chapter: SChapter) =
        GET(apiUrl + chapter.url, apiHeaders)

    override fun imageRequest(page: Page) =
        GET(page.imageUrl!!, imgHeaders)

    override fun latestUpdatesParse(response: Response) =
        popularMangaParse(response)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.data
        val hasNext = data["page"]["hasNext"]
        val mangas = data.map<ContentInfo, SManga>("contents") {
            SManga.create().apply {
                title = it.name
                url = "/comic/${it.id}"
                thumbnail_url = it.cover
                description = it.description
                status = when (it.status) {
                    "completed" -> SManga.COMPLETED
                    else -> SManga.ONGOING
                }
                author = it.authors?.filter { it.isAuthor }?.joinToString()
                artist = it.authors?.filter { it.isArtist }?.joinToString()
                genre = buildString {
                    it.genres?.joinTo(this)
                    if (it.mature) append(", Mature")
                    if (it.original) append(", Original")
                    if (it.exclusive) append(", Exclusive")
                }
            }
        }
        return MangasPage(mangas, hasNext.jsonPrimitive.boolean)
    }

    override fun searchMangaParse(response: Response) =
        popularMangaParse(response)

    override fun chapterListParse(response: Response): List<SChapter> {
        val content = response.data["episode"]["content"]
        val id = content["id"].jsonPrimitive.int
        return content.map<Chapter, SChapter>("chapters") {
            SChapter.create().apply {
                chapter_number = it.id.toFloat()
                url = "/comic/$id/chapter/${it.id}/product"
                name = it.name + if (it.isAvailable) "" else LOCK
                date_upload = dateFormat.parse(it.publishedAt)?.time ?: 0L
            }
        }.reversed()
    }

    override fun pageListParse(response: Response) =
        response.data["chapter"].map<ChapterImage, Page>("images") {
            Page(it.sort, "", it.url.decrypt() + "?" + it.parameter)
        }

    override fun fetchMangaDetails(manga: SManga) =
        rx.Observable.just(manga.apply { initialized = true })!!

    override fun fetchPageList(chapter: SChapter) =
        if (!chapter.name.endsWith(LOCK)) {
            super.fetchPageList(chapter)
        } else {
            throw Error("You are not authorized to view this!")
        }

    private fun search(query: String, page: Int) =
        FormBody.Builder().add("query", query)
            .add("pageNo", (page - 1).toString())
            .add("pageSize", "25").build()

    private fun paginate(route: String, page: Int) =
        GET("$apiUrl/$route?pageNo=${page - 1}&pageSize=25", apiHeaders)

    private fun String.decrypt() =
        CryptoAES.decrypt(this, keyBytes, ivBytes)

    private val Response.data: JsonElement?
        get() = json.parseToJsonElement(body.string()).jsonObject.also {
            val code = it["result"]["code"].jsonPrimitive.int
            if (code != 200) throw Error(status(code))
        }["data"]

    private operator fun JsonElement?.get(key: String) =
        this!!.jsonObject[key]!!

    private inline fun <reified T, R> JsonElement?.map(
        key: String,
        transform: (T) -> R,
    ) = json.decodeFromJsonElement<List<T>>(this[key]).map(transform)

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    companion object {
        private const val ANON_IP = "0.0.0.0"

        private const val LOCK = " \uD83D\uDD12"

        private const val ISO_DATE = "yyyy-MM-dd'T'HH:mm:ss'Z'"

        private const val WEB_KEY = "9241d2f090d01716feac20ae08ba791a"

        private const val AES_KEY = "a7fc9dc89f2c873d79397f8a0028a4cd"

        private val keyBytes = AES_KEY.toByteArray(Charsets.UTF_8)

        private val ivBytes = ByteArray(16) // Zero filled array as IV

        private const val ACCEPT_IMAGE =
            "image/avif,image/jxl,image/webp,image/*,*/*"

        private val userAgent = System.getProperty("http.agent")!!

        private val dateFormat = SimpleDateFormat(ISO_DATE, Locale.ROOT)

        private val SHA256 = MessageDigest.getInstance("SHA-256")

        private val day by lazy {
            when (Calendar.getInstance()[Calendar.DAY_OF_WEEK]) {
                Calendar.SUNDAY -> "sunday"
                Calendar.MONDAY -> "monday"
                Calendar.TUESDAY -> "tuesday"
                Calendar.WEDNESDAY -> "wednesday"
                Calendar.THURSDAY -> "thursday"
                Calendar.FRIDAY -> "friday"
                Calendar.SATURDAY -> "saturday"
                else -> "completed"
            }
        }

        fun sha256(timestamp: Long) = buildString(64) {
            SHA256.digest((WEB_KEY + ANON_IP + timestamp).toByteArray())
                .joinTo(this, "") { "%02x".format(it) }
            SHA256.reset()
        }

        private fun status(code: Int) = when (code) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            402 -> "Payment Required"
            403 -> "Forbidden"
            404 -> "Not Found"
            408 -> "Request Timeout"
            409 -> "Conflict"
            410 -> "DormantAccount"
            417 -> "Expectation Failed"
            426 -> "Upgrade Required"
            428 -> "성인 on/off 권한"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            451 -> "성인 인증"
            else -> "Error $code"
        }
    }
}
