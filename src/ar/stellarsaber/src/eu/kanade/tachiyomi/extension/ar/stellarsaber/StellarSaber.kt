package eu.kanade.tachiyomi.extension.ar.stellarsaber

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import org.jsoup.nodes.Document
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Source
abstract class StellarSaber : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::imageInterceptor)

    private fun Int.pageNumber() = if (this > 1) "page/$this/" else ""

    // ------------------- Popular / Latest -------------------

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga/${page.pageNumber()}?sort=rating")

        return parseMangaListPage(response.asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga/${page.pageNumber()}?sort=latest")

        return parseMangaListPage(response.asJsoup())
    }

    private fun parseMangaListPage(document: Document): MangasPage {
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

    // ------------------- Search -------------------

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.size < 2) {
            throw Exception("Unsupported url")
        }

        val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }

        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            this.url = manga.url
            initialized = true
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val nonce = fetchNonce()

        val body = MultipartBody.Builder().apply {
            setType(MultipartBody.FORM)
            addFormDataPart("action", "flavor_ajax_filter_content")
            addFormDataPart("nonce", nonce)
            addFormDataPart("page", page.toString())
            addFormDataPart("keyword", query)
        }.build()

        val response = client.post("$baseUrl/wp-admin/admin-ajax.php", body)
        val data = response.parseAs<SearchResponse>()

        val entries = data.data.results
            .filterNot { it.type in listOf("novel", "anime") }
            .map { result ->
                SManga.create().apply {
                    setUrlWithoutDomain(result.url)
                    title = result.title
                    thumbnail_url = result.cover
                }
            }

        return MangasPage(entries, true)
    }

    private var nonce: String? = null

    private fun fetchNonce(document: Document): String {
        nonce?.let { return it }

        val script = document.selectFirst("#flavor-ajax-js-extra")?.data()
            ?: error("Nonce script not found")

        return script.extract("nonce\":\"", '"').also { nonce = it }
    }

    private suspend fun fetchNonce(): String {
        nonce?.let { return it }

        val document = client.get("$baseUrl/manga/").asJsoup()
        return fetchNonce(document)
    }

    // ------------------- details & chapters -------------------

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        val updatedManga = parseMangaDetails(document)
        val updatedChapters = parseChapterList(document)

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        val element = document.selectFirst(".detail-content")!!

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

    private fun parseChapterList(document: Document): List<SChapter> = document.select(".volume-group").flatMap { volume ->
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

    private fun parseRelativeDate(date: String?): Long {
        date ?: return 0L
        val number = NUMBER_REGEX.find(date)?.value?.toIntOrNull()
        val now = System.currentTimeMillis()

        val duration: Duration = if (number == null) {
            when {
                date.contains("دقيقتين") -> 2.minutes
                date.contains("دقيقة") -> 1.minutes
                date.contains("ساعتين") -> 2.hours
                date.contains("ساعة") -> 1.hours
                date.contains("يومين") -> 2.days
                date.contains("يوم") -> 1.days
                date.contains("أسبوعين") -> 14.days
                date.contains("أسبوع") -> 7.days
                date.contains("شهرين") -> 60.days
                date.contains("شهر") -> 30.days
                date.contains("سنتين") -> 730.days
                date.contains("سنة") -> 365.days
                else -> return 0L
            }
        } else {
            when {
                date.contains("دقيقة") || date.contains("دقائق") -> number.minutes
                date.contains("ساعة") || date.contains("ساعات") -> number.hours
                date.contains("يوم") || date.contains("أيام") -> number.days
                date.contains("أسبوع") || date.contains("أسابيع") -> (number * 7).days
                date.contains("شهر") || date.contains("أشهر") -> (number * 30).days
                date.contains("سنة") || date.contains("سنوات") -> (number * 365).days
                else -> return 0L
            }
        }

        return now - duration.inWholeMilliseconds
    }

    // ------------------- Pages -------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        val key = fetchCdnKey(document)

        return document.select("img.reader__page[data-cdn-url]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.attr("abs:data-cdn-url") + "#$key") }
    }

    private suspend fun fetchCdnKey(document: Document): String {
        val script = document.selectFirst("script:containsData(flavorReaderData)")?.data()
            ?: error("Script not found")

        val formBody = FormBody.Builder()
            .add("action", "flavor_cdn_get_key")
            .add("nonce", script.extract("cdnNonce: '", '\''))
            .add("chapter_id", script.extract("chapterId: ", ','))
            .build()

        val response = client.post("$baseUrl/wp-admin/admin-ajax.php", formBody)

        return response.parseAs<CdnKeyResponse>().data.key
    }

    private fun String.extract(startKey: String, endChar: Char): String {
        val startIndex = this.indexOf(startKey)
        require(startIndex != -1) { "$startKey not found" }

        val valueStart = startIndex + startKey.length
        val valueEnd = this.indexOf(endChar, valueStart)
        require(valueEnd != -1) { "End char not found for $startKey" }

        return this.substring(valueStart, valueEnd)
    }

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val fragment = request.url.fragment
            ?: return response

        val keyBytes = Base64.decode(fragment, Base64.DEFAULT)

        val source = response.body.source()
        val iv = source.readByteArray(12)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(128, iv),
            )
        }

        val cipherSource = source.cipherSource(cipher)

        return response.newBuilder()
            .body(cipherSource.buffer().asResponseBody(JPEG_MEDIA_TYPE, -1))
            .build()
    }

    companion object {
        private val NUMBER_REGEX = Regex("""(\d+)""")
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
