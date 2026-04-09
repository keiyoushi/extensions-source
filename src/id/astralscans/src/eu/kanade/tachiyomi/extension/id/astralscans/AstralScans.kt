package eu.kanade.tachiyomi.extension.id.astralscans

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import org.jsoup.nodes.Element

class AstralScans : MangaThemesia("Astral Scans", "https://astralscans.top", "id") {

    override val hasProjectPage = true

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        // Find the script containing the anti-scraper Base64 JSON payload
        val script = document.select("script").find { it.data().contains("rawPayload") }?.data()

        if (script != null) {
            val payloadRegex = """rawPayload\s*=\s*['"]([^'"]+)['"]""".toRegex()
            val match = payloadRegex.find(script)

            if (match != null) {
                try {
                    val rawPayload = match.groupValues[1]
                    val jsonString = String(Base64.decode(rawPayload, Base64.DEFAULT), Charsets.UTF_8)
                    val jsonArray = Json.parseToJsonElement(jsonString).jsonArray

                    val chapters = jsonArray.map { element ->
                        val obj = element.jsonObject
                        SChapter.create().apply {
                            // The URL is Base64-encoded and the resulting string is reversed
                            val uBase64 = obj["u"]?.jsonPrimitive?.content ?: ""
                            val decodedUrl = String(Base64.decode(uBase64, Base64.DEFAULT), Charsets.UTF_8).reversed()
                            setUrlWithoutDomain(decodedUrl)

                            val chapNum = obj["n"]?.jsonPrimitive?.content ?: ""
                            val title = obj["t"]?.jsonPrimitive?.content ?: ""
                            name = "Chapter $chapNum" + if (title.isNotBlank()) " - $title" else ""

                            val dateStr = obj["d"]?.jsonPrimitive?.content
                            date_upload = dateStr.parseChapterDate()
                        }
                    }

                    if (chapters.isNotEmpty()) {
                        return chapters
                    }
                } catch (e: Exception) {
                    // Fallback to old DOM parsing if JSON extraction or parsing fails
                }
            }
        }

        // Old DOM fallback
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    override fun chapterListSelector() = "div#kumpulan-bab-area .astral-item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val dataU = element.selectFirst(".js-link")?.attr("data-u") ?: ""
        val decoded = if (dataU.isNotEmpty()) String(Base64.decode(dataU, Base64.DEFAULT), Charsets.UTF_8) else ""
        setUrlWithoutDomain(decoded)
        name = element.selectFirst(".ch-title")?.text() ?: ""
        date_upload = element.selectFirst(".ch-date")?.text().parseChapterDate()
    }
}
