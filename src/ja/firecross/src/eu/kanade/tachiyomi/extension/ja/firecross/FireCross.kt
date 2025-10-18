package eu.kanade.tachiyomi.extension.ja.firecross

import eu.kanade.tachiyomi.multisrc.clipstudioreader.ClipStudioReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class FireCross : ClipStudioReader(
    "FireCross",
    "https://firecross.jp",
    "ja",
) {
    private val apiUrl = "$baseUrl/api"
    private val dateFormat = SimpleDateFormat("yyyy/M/d", Locale.JAPAN)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ebook/comics?sort=1&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.seriesList li.seriesList_item").map { element ->
            SManga.create().apply {
                element.selectFirst("a.seriesList_itemTitle")!!.let { a ->
                    setUrlWithoutDomain(a.absUrl("href"))
                    title = a.text()
                }
                thumbnail_url = element.selectFirst("img.series-list-img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a.pagination-btn--next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.ebook-series-title")!!.text()
            author = document.select("ul.ebook-series-author li").joinToString { it.text() }
            artist = author
            description = document.selectFirst("p.ebook-series-synopsis")?.text()
            genre = document.select("div.book-genre a").joinToString { it.text() }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div[js-tab-content][js-tab-episode] ul.shop-list li.shop-item--episode").mapNotNull { element ->
            val info = element.selectFirst(".shop-item-info")!!
            val nameText = info.selectFirst("span.shop-item-info-name")?.text()!!
            val dateText = info.selectFirst("span.shop-item-info-release")?.text()?.substringAfter("公開：")
            val form = element.selectFirst("form[data-api=reader]")
            val rentalButton = element.selectFirst("button.btn-rental--both")

            SChapter.create().apply {
                name = nameText
                date_upload = dateFormat.tryParse(dateText)

                when {
                    form != null -> {
                        val token = form.selectFirst("input[name=_token]")!!.attr("value")
                        val ebookId = form.selectFirst("input[name=ebook_id]")!!.attr("value")
                        url = ChapterId(token, ebookId).toJsonString()
                    }

                    rentalButton != null -> {
                        name = "🔒 $nameText"
                        val rentalId = rentalButton.attr("data-id")
                        url = "rental/$rentalId"
                    }
                }
            }
        }.reversed()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (!chapter.url.startsWith("{")) {
            return Observable.error(Exception("This chapter is locked. Log in and purchase this chapter to read."))
        }

        val chapterId = chapter.url.parseAs<ChapterId>()

        val formBody = FormBody.Builder()
            .add("_token", chapterId.token)
            .add("ebook_id", chapterId.id)
            .build()

        val apiHeaders = headers.newBuilder()
            .add("Accept", "application/json")
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiRequest = POST("$apiUrl/reader", apiHeaders, formBody)

        return client.newCall(apiRequest).asObservable().map { response ->
            if (!response.isSuccessful) {
                throw Exception("API call failed with HTTP ${response.code}")
            }
            val redirectUrl = response.parseAs<ApiResponse>().redirect
            val viewerRequest = GET(redirectUrl, headers)
            val viewerResponse = client.newCall(viewerRequest).execute()
            super.pageListParse(viewerResponse)
        }
    }
}
