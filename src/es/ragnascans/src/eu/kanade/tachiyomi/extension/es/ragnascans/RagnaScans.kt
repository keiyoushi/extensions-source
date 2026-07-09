package eu.kanade.tachiyomi.extension.es.ragnascans

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class RagnaScans : HttpSource() {

    override val supportsLatest = true

    // Limit to 1 request per second to prevent HTTP 429 on parallel manga update fetches
    override val client = network.client.newBuilder()
        .rateLimit(1)
        .build()

    private val dateFormat by lazy {
        SimpleDateFormat("dd MMMM, yyyy", Locale("es")).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/directorio.php?page=$page&orden=vistas&q=", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".mod-grid .mod-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".mod-card-title")!!.text()
                thumbnail_url = element.selectFirst(".mod-card-cover")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".mod-pg-btn:contains(Sig)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/directorio.php?page=$page&orden=actualizado&q=", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("http://") || query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull() ?: return super.fetchSearchManga(page, query, filters)
            if (url.host == baseUrl.toHttpUrl().host) {
                return client.newCall(GET(query, headers)).asObservableSuccess()
                    .map { response ->
                        MangasPage(listOf(mangaDetailsParse(response)), false)
                    }
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/directorio.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("q", query)

            filters.firstInstanceOrNull<GenreFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("generos[]", it.value) }

            filters.firstInstanceOrNull<StatusFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("estado[]", it.value) }

            filters.firstInstanceOrNull<TypeFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("tipo[]", it.value) }

            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("orden", it.selectedValue)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())

            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst(".cover-wrapper img")?.absUrl("src")

            val infoWrap = document.select(".flex.flex-wrap.items-center.gap-x-3")
            author = infoWrap.selectFirst("span:contains(Autor:)")?.text()?.substringAfter("Autor:")?.trim()
            artist = infoWrap.selectFirst("span:contains(Ilustrador:)")?.text()?.substringAfter("Ilustrador:")?.trim()

            description = document.selectFirst("#sinopsisWrapper p")?.text()

            val metaRows = document.select(".meta-table .meta-row")

            genre = metaRows.find { it.selectFirst(".meta-label")?.text()?.lowercase()?.contains("género") == true }
                ?.select(".meta-value a")?.joinToString { it.text() }

            val statusText = metaRows.find { it.selectFirst(".meta-label")?.text()?.lowercase()?.contains("estado") == true }
                ?.selectFirst(".meta-value")?.text()?.lowercase()

            status = when (statusText) {
                "emision", "en emisión", "en emision" -> SManga.ONGOING
                "finalizado" -> SManga.COMPLETED
                "hiatus", "pausado" -> SManga.ON_HIATUS
                "cancelado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chaptersContainer .chapter-item").mapNotNull { element ->
            // Filter out premium/locked chapters
            if (element.hasClass("locked-neon") || element.selectFirst(".ph-lock-key") != null) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".chapter-item-title h4")!!.text().removeSuffix(".00")
                date_upload = element.selectFirst(".chapter-item-date")?.text()?.let {
                    dateFormat.tryParse(it)
                } ?: 0L
            }
        }
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#pagesContainer .page-container img").mapIndexedNotNull { index, img ->
            val dataVerify = img.attr("data-verify")

            if (dataVerify.isNotEmpty()) {
                try {
                    val decodedBytes = Base64.decode(dataVerify, Base64.DEFAULT)
                    val reversedUrl = String(decodedBytes).reversed()
                    val imageUrl = when {
                        reversedUrl.startsWith("http") -> reversedUrl
                        reversedUrl.startsWith("//") -> "https:$reversedUrl"
                        else -> baseUrl + reversedUrl
                    }
                    Page(index, imageUrl = imageUrl)
                } catch (_: Exception) {
                    null
                }
            } else {
                val src = img.absUrl("src")
                if (src.isNotEmpty() && !src.startsWith("data:image")) {
                    Page(index, imageUrl = src)
                } else {
                    null
                }
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        Filter.Separator(),
        SortFilter(),
    )
}
