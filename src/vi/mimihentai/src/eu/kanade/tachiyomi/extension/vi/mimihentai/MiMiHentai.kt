package eu.kanade.tachiyomi.extension.vi.mimihentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.Calendar
import kotlin.time.Duration.Companion.minutes

class MiMiHentai : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val name = "MiMiHentai"

    override val baseUrl = "https://mimihentai.net"

    override val lang = "vi"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (!request.url.toString().startsWith(baseUrl)) return@addInterceptor response

            val body = response.peekBody(Long.MAX_VALUE).string()
            if (!body.contains("wire:initial-data") || !body.contains("enter-secret")) {
                return@addInterceptor response
            }

            response.close()
            solvePassword(chain, body)
            chain.proceed(request)
        }
        .addNetworkInterceptor {
            val request = it.request()
            val response = it.proceed(request)

            if (request.url.toString().startsWith(baseUrl)) {
                if (response.code == 429) {
                    throw IOException("Bạn đang request quá nhanh!")
                }
            }
            response
        }
        .rateLimit(14, 1.minutes) { it.host == baseUrlHost }
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach?sort=-views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("a.group").mapNotNull { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("h1")?.text()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                thumbnail_url = element.selectFirst("img")?.let { it: Element ->
                    it.absUrl("data-src")
                        .ifEmpty { it.absUrl("src") }
                }
            }
        }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = document.selectFirst("a[href*='page=${currentPage + 1}']") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val selectedGenres = filter.state.filter { it.state }.joinToString(",") { it.id }
                        if (selectedGenres.isNotEmpty()) {
                            addQueryParameter("filter[accept_genres]", selectedGenres)
                        }
                    }

                    is StatusFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("filter[status]", filter.toUriPart())
                        }
                    }

                    is SortFilter -> {
                        addQueryParameter("sort", filter.toUriPart())
                    }

                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            document.selectFirst("div.title p")?.text()?.let { title = it }
            thumbnail_url = document.selectFirst("img.rounded.shadow-md.w-full")?.let { it: Element ->
                it.absUrl("data-src")
                    .ifEmpty { it.absUrl("src") }
            }
            author = document.selectFirst("a[href*='/tac-gia/']")?.text()
            genre = document.select("a[href*='/the-loai/']").joinToString { it.text() }

            val bodyText = document.body().text()
            status = when {
                bodyText.contains("Đã hoàn thành") -> SManga.COMPLETED
                bodyText.contains("Đang tiến hành") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }

            description = document.selectFirst("div.mt-4")?.ownText()
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div.chapter-list a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("h1")?.text()
                    ?: element.attr("title").takeIf(String::isNotBlank)
                    ?: element.text()

                val dateText = element.parent()?.selectFirst("span.timeago")?.text()
                    ?: element.parent()?.parent()?.selectFirst("span.timeago")?.text()
                date_upload = parseRelativeDate(dateText)
            }
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance()
        val number = NUMBER_REGEX.find(dateStr)?.value?.toIntOrNull() ?: return 0L

        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Password ===============================

    private fun solvePassword(chain: okhttp3.Interceptor.Chain, html: String) {
        val wireDataStr = WIRE_INITIAL_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?.let { Parser.unescapeEntities(it, true) }
            ?: throw IOException("Password: wire:initial-data not found")

        val csrfToken = LIVEWIRE_TOKEN_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("Password: CSRF token not found")

        val password = PASSWORD_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("Password: password not found")

        val wireData = wireDataStr.parseAs<JsonObject>()
        val fingerprint = wireData["fingerprint"]!!.jsonObject
        val serverMemo = wireData["serverMemo"]!!.jsonObject

        val livewireHeaders = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("X-CSRF-TOKEN", csrfToken)
            .add("X-Livewire", "true")
            .add("Accept", "text/html, application/xhtml+xml")
            .add("Referer", "$baseUrl/")
            .build()

        val syncPayload = SyncInputRequestDto(
            fingerprint = fingerprint,
            serverMemo = serverMemo,
            updates = listOf(
                SyncInputUpdateDto(
                    type = "syncInput",
                    payload = SyncInputPayloadDto(
                        id = "s1",
                        name = "password",
                        value = password,
                    ),
                ),
            ),
        )

        val syncRequest = POST(
            "$baseUrl/livewire/message/enter-secret",
            livewireHeaders,
            syncPayload.toJsonRequestBody(),
        )
        val syncResponse = chain.proceed(syncRequest)
        val syncResult = syncResponse.parseAs<JsonObject>()

        val syncMemo = syncResult["serverMemo"]?.jsonObject
        val mergedMemo = buildJsonObject {
            serverMemo.forEach { (key, value) ->
                when {
                    key == "data" && syncMemo?.containsKey("data") == true -> {
                        putJsonObject("data") {
                            serverMemo["data"]!!.jsonObject.forEach { (k, v) -> put(k, v) }
                            syncMemo["data"]!!.jsonObject.forEach { (k, v) -> put(k, v) }
                        }
                    }
                    syncMemo?.containsKey(key) == true -> put(key, syncMemo[key]!!)
                    else -> put(key, value)
                }
            }
        }

        val submitPayload = SubmitRequestDto(
            fingerprint = fingerprint,
            serverMemo = mergedMemo,
            updates = listOf(
                SubmitUpdateDto(
                    type = "callMethod",
                    payload = SubmitPayloadDto(
                        id = "c1",
                        method = "submit",
                        params = emptyList(),
                    ),
                ),
            ),
        )

        val submitRequest = POST(
            "$baseUrl/livewire/message/enter-secret",
            livewireHeaders,
            submitPayload.toJsonRequestBody(),
        )
        chain.proceed(submitRequest).close()
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("img.lazy").mapIndexed { index, element ->
            val imageUrl = element.absUrl("src").ifEmpty {
                element.absUrl("data-src")
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Related ================================
    // disable suggested mangas on Komikku due to heavy rate limit
    override val disableRelatedMangasBySearch = true

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    companion object {
        private val NUMBER_REGEX = Regex("\\d+")
        private val WIRE_INITIAL_DATA_REGEX = Regex("""wire:initial-data="([^"]+)"""")
        private val LIVEWIRE_TOKEN_REGEX = Regex("""livewire_token\s*=\s*'([^']+)'""")
        private val PASSWORD_REGEX = Regex("""input\.value\s*=\s*'([^']+)'""")
    }
}
