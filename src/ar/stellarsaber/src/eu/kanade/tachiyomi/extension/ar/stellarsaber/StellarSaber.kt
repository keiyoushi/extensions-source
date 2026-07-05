package eu.kanade.tachiyomi.extension.ar.stellarsaber

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import rx.Observable
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class StellarSaber : HttpSource() {

    override val client = super.client.newBuilder()
        .addInterceptor(::imageInterceptor)
        .build()

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private fun Int.pageNumber() = if (this > 1) "page/$this/" else ""

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/${page.pageNumber()}?sort=rating", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(".card-grid .card").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".card__title")!!.text()
                setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }

        fetchNonce(document)

        val hasNextPage = document.selectFirst(".nav-links .next") != null

        return MangasPage(entries, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/${page.pageNumber()}?sort=latest", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host == baseUrl.toHttpUrl().host && url.pathSegments.size >= 2) {
                val manga = SManga.create().apply {
                    setUrlWithoutDomain(query)
                }
                return fetchMangaDetails(manga).map {
                    it.url = manga.url
                    it.initialized = true

                    MangasPage(listOf(it), false)
                }
            }

            throw Exception("Unsupported url")
        }

        fetchNonce()

        return super.fetchSearchManga(page, query, filters)
    }

    private var nonce: String? = null

    @Synchronized
    private fun fetchNonce(document: Document? = null): String {
        if (nonce != null) return nonce!!

        val doc = document ?: client.newCall(
            GET("$baseUrl/manga/", headers),
        ).execute().use { it.asJsoup() }

        val script = doc.selectFirst("#flavor-ajax-js-extra")?.data()
            ?: error("Nonce script not found")

        nonce = script.extract("nonce\":\"", '"')

        return nonce ?: error("Unable to extract nonce")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("action", "flavor_ajax_filter_content")
            .addFormDataPart("nonce", fetchNonce())
            .addFormDataPart("page", page.toString())
            .addFormDataPart("keyword", query)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResponse>()

        val entries = data.data.results
            .filterNot { it.type in listOf("novel", "anime") }
            .map { it.toSManga() }

        return MangasPage(entries, true)
    }

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        setUrlWithoutDomain(this@toSManga.url)
        title = this@toSManga.title
        thumbnail_url = this@toSManga.cover
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val element = response.asJsoup().selectFirst(".detail-content")!!

        title = element.select(".detail-info__title").text()
        thumbnail_url = element.selectFirst(".detail-poster img")?.absUrl("src")
        genre = element.select(".detail-genres a").joinToString { it.text() }

        val otherName = element.select(".detail-info__alt-title").text()
        description = element.select("#detail-desc p").joinToString { it.wholeText().trim() } +
            if (otherName.isNotBlank()) "\n\n$otherName" else ""

        element.select(".detail-meta__label").forEach { labelEl ->
            val label = labelEl.text()
            val value = labelEl.nextElementSibling()?.text() ?: return@forEach

            when {
                label.contains("المؤلف") -> author = value
                label.contains("الرسّام") -> artist = value
                label.contains("الحالة") -> status = when {
                    "مستمر" in value -> SManga.ONGOING
                    "مكتمل" in value -> SManga.COMPLETED
                    "ملغا" in value -> SManga.CANCELLED
                    "متوقف" in value -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    private fun parseRelativeDate(date: String?): Long {
        date ?: return 0L
        val number = numberRegex.find(date)?.value?.toIntOrNull()
        val cal = Calendar.getInstance()

        if (number == null) {
            return when {
                date.contains("دقيقة") -> cal.apply { add(Calendar.MINUTE, -1) }.timeInMillis
                date.contains("دقيقتين") -> cal.apply { add(Calendar.MINUTE, -2) }.timeInMillis
                date.contains("ساعة") -> cal.apply { add(Calendar.HOUR, -1) }.timeInMillis
                date.contains("ساعتين") -> cal.apply { add(Calendar.HOUR, -2) }.timeInMillis
                date.contains("يوم") && !date.contains("يومين") -> cal.timeInMillis
                date.contains("يومين") -> cal.apply { add(Calendar.DAY_OF_MONTH, -2) }.timeInMillis
                date.contains("أسبوع") && !date.contains("أسبوعين") -> cal.apply { add(Calendar.DAY_OF_MONTH, -7) }.timeInMillis
                date.contains("أسبوعين") -> cal.apply { add(Calendar.DAY_OF_MONTH, -14) }.timeInMillis
                date.contains("شهر") && !date.contains("شهرين") -> cal.apply { add(Calendar.MONTH, -1) }.timeInMillis
                date.contains("شهرين") -> cal.apply { add(Calendar.MONTH, -2) }.timeInMillis
                date.contains("سنة") -> cal.apply { add(Calendar.YEAR, -1) }.timeInMillis
                date.contains("سنتين") -> cal.apply { add(Calendar.YEAR, -2) }.timeInMillis
                else -> 0L
            }
        }

        return when {
            date.contains("دقيقة") || date.contains("دقائق") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("ساعة") || date.contains("ساعات") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("يوم") || date.contains("أيام") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("أسبوع") || date.contains("أسابيع") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            date.contains("شهر") || date.contains("أشهر") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            else -> 0L
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".volume-group").flatMap { volume ->
            val volumeName = volume.selectFirst(".volume-group__label")?.text()

            volume.select(".chapter-item").map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))

                    val title = element.selectFirst(".chapter-item__title")?.text()!!
                    val number = element.selectFirst(".chapter-item__number")?.text().orEmpty()

                    name = buildString {
                        if (!volumeName.isNullOrEmpty()) {
                            append("$volumeName - ")
                        }

                        if (number.isNullOrEmpty() && title.contains(number).not()) {
                            append("$number: ")
                        }

                        append(title)
                    }

                    date_upload = parseRelativeDate(element.selectFirst(".chapter-item__date")?.text())
                    scanlator = element.selectFirst(".chapter-item__team")?.text()
                }
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val key = fetchCdnKey(document)

        return document.select("img.reader__page[data-cdn-url]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.attr("abs:data-cdn-url") + "#$key") }
    }

    private fun String.extract(startKey: String, endChar: Char): String {
        val startIndex = this.indexOf(startKey)
        require(startIndex != -1) { "$startKey not found" }

        val valueStart = startIndex + startKey.length
        val valueEnd = this.indexOf(endChar, valueStart)
        require(valueEnd != -1) { "End char not found for $startKey" }

        return this.substring(valueStart, valueEnd)
    }

    private fun fetchCdnKey(document: Document): String {
        val script = document.selectFirst("script:containsData(flavorReaderData)")?.data()
            ?: error("Script not found")

        val formBody = FormBody.Builder()
            .add("action", "flavor_cdn_get_key")
            .add("nonce", script.extract("cdnNonce: '", '\''))
            .add("chapter_id", script.extract("chapterId: ", ','))
            .build()

        return client.newCall(
            POST("$baseUrl/wp-admin/admin-ajax.php", headers, formBody),
        ).execute().use { response ->
            response.parseAs<CdnKeyResponse>().data.key
        }
    }

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val fragment = request.url.fragment
            ?: return response

        val keyBytes = Base64.decode(fragment, Base64.DEFAULT)
        val encryptedBytes = response.body.bytes()

        val iv = encryptedBytes.copyOfRange(0, 12)
        val encrypted = encryptedBytes.copyOfRange(12, encryptedBytes.size)

        val decrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(128, iv),
            )
            doFinal(encrypted)
        }

        return response.newBuilder()
            .body(decrypted.toResponseBody(JPEG_MEDIA_TYPE))
            .build()
    }

    companion object {
        private val numberRegex = Regex("""(\d+)""")
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
