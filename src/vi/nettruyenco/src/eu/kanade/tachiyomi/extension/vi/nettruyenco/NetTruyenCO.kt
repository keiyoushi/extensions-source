package eu.kanade.tachiyomi.extension.vi.nettruyenco

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class NetTruyenCO : WPComics(
    "NetTruyenCO (unoriginal)",
    "https://nettruyener.com",
    "vi",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault()),
    gmtOffset = null,
) {
    override val popularPath = "truyen-tranh-hot"

    // Override chapters

    // Fetch all chapters via the JSON endpoint (avoids the “View more” flow)
    override fun chapterListRequest(manga: SManga): Request {
        val slugAndId = manga.url.substringAfterLast("/") // e.g. "ta-co-the-don-ngo-vo-han-13396"
        val comicId = slugAndId.substringAfterLast("-").toInt() // 13396
        val slug = slugAndId.substringBeforeLast("-") // "ta-co-the-don-ngo-vo-han"
        val jsonUrl = "$baseUrl/Comic/Services/ComicService.asmx/ChapterList" +
            "?slug=$slug&comicId=$comicId"
        return GET(jsonUrl, headers)
    }

    // Parse the JSON response into a List<SChapter>
    override fun chapterListParse(response: Response): List<SChapter> {
        val bodyString = response.body?.string() ?: return emptyList()
        val root = JSONObject(bodyString)
        val dataArray = when {
            root.has("d") -> {
                val d = root.get("d")
                if (d is String) {
                    JSONObject(d).getJSONArray("data")
                } else {
                    (d as JSONObject).getJSONArray("data")
                }
            }
            root.has("data") -> root.getJSONArray("data")
            else -> return emptyList()
        }

        val slug = response.request.url.queryParameter("slug") ?: ""
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        return List(dataArray.length()) { i ->
            val obj = dataArray.getJSONObject(i)
            SChapter.create().apply {
                name = obj.optString("chapter_name", "Chapter")
                setUrlWithoutDomain(
                    "/truyen-tranh/$slug/" +
                        "${obj.optString("chapter_slug")}/" +
                        "${obj.optInt("chapter_id")}",
                )
                date_upload = dateFmt.parse(obj.optString("updated_at"))?.time ?: 0L
                chapter_number = obj.optDouble("chapter_num", 0.0).toFloat()
            }
        }
    }

    // Disable the old HTML‐based selector
    override fun chapterListSelector(): String = throw UnsupportedOperationException()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = info.select("li.status p.col-xs-8").text().toStatus()
                genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
                val otherName = info.select("h2.other-name").text()
                description = info.select("div.detail-content div.shortened").text() +
                    if (otherName.isNotBlank()) "\n\n ${intl["OTHER_NAME"]}: $otherName" else ""
                thumbnail_url = imageOrNull(info.select("div.col-image img").first()!!)
            }
        }
    }
}
