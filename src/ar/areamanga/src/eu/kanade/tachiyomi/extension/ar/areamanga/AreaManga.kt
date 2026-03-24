package eu.kanade.tachiyomi.extension.ar.areamanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class AreaManga :
    MangaThemesia(
        "أريا مانجا",
        "https://ar.kenmanga.com",
        "ar",
        dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("ar")),
    ) {

    override fun searchMangaSelector() = ".listupd .manga-card-v"
    protected val searchMangaTitleSelector = ".bigor .tt, h3 a"

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).apply {
        title = element.selectFirst(searchMangaTitleSelector)?.text()?.takeIf(String::isNotEmpty) ?: element.selectFirst("a")!!.attr("title")
    }

    override val seriesDetailsSelector = "div.legendary-single-page"
    override val seriesTitleSelector = ".manga-title-large"
    override val seriesThumbnailSelector = ".manga-poster img"
    override val seriesDescriptionSelector = "div.story-text"
    override val seriesGenreSelector = "div.filter-tags a"
    override val seriesStatusSelector = ".info-label:contains(الحالة) + span"
    override val seriesAuthorSelector = ".info-label:contains(المؤلف) + span"
    override val seriesArtistSelector = ".info-label:contains(الرسام) + span"

    override fun chapterListSelector() = "#chapters-list-container .ch-item"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        name = element.selectFirst(".chap-num")!!.text().ifEmpty { name }
        date_upload = element.selectFirst(".chap-date")?.text()?.parseChapterDate() ?: date_upload
    }

    protected fun pageListParseApiRequest(document: Document): Request {
        val chapterId = document.selectFirst("#comment_post_ID")?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("Chapter ID not found.")

        val formBody = FormBody.Builder()
            .add("action", "get_secure_chapter_images")
            .add("chapter_id", chapterId)
            .build()
        val newHeaders = headersBuilder()
            .set("Referer", document.location())
            .set("Content-Type", "application/x-www-form-urlencoded")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", newHeaders, formBody)
    }

    override fun pageListParse(document: Document): List<Page> {
        val request = pageListParseApiRequest(document)
        val response = client.newCall(request).execute()
        val apiResponse = response.parseAs<SecureChapterResponse>()

        if (!apiResponse.success) throw Exception("Failed to load chapter.")

        return when (apiResponse.data.status) {
            "unlocked" -> {
                val html = apiResponse.data.content ?: throw Exception("Chapter content missing.")
                val doc = Jsoup.parse(html)

                doc.select("img").mapIndexed { index, img ->
                    Page(index, "", img.imgAttr())
                }
            }

            "locked" -> {
                throw Exception("Chapter locked. Open in WebView to unlock.")
            }

            else -> emptyList()
        }
    }

    @Serializable
    class SecureChapterResponse(
        val success: Boolean,
        val data: SecureChapterData,
    )

    @Serializable
    class SecureChapterData(
        val status: String,
        val content: String?,
    )
}
