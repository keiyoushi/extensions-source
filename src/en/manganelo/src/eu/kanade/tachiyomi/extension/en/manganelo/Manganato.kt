package eu.kanade.tachiyomi.extension.en.manganelo

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Manganato : MangaBox(
    "Manganato",
    arrayOf(
        "www.natomanga.com",
        "www.nelomanga.com",
        "www.manganato.gg",
    ),
    "en",
) {

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.trimEnd('/').substringAfterLast("/")
        return GET("$baseUrl/api/manga/$slug/chapters?limit=500&offset=0", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonString = response.body.string()
        val json = JSONObject(jsonString)

        if (!json.has("data")) return emptyList()

        val data = json.getJSONObject("data")
        val chaptersArray = data.getJSONArray("chapters")
        val chapters = mutableListOf<SChapter>()

        val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
        apiDateFormat.timeZone = TimeZone.getTimeZone("UTC")

        val mangaSlug = response.request.url.pathSegments.let { segments ->
            val index = segments.indexOf("manga")
            if (index != -1 && index + 1 < segments.size) segments[index + 1] else ""
        }

        for (i in 0 until chaptersArray.length()) {
            val item = chaptersArray.getJSONObject(i)
            val chapter = SChapter.create()

            chapter.name = item.getString("chapter_name")

            val chapterSlug = item.getString("chapter_slug")
            chapter.url = "/manga/$mangaSlug/$chapterSlug"

            chapter.chapter_number = item.optDouble("chapter_num", -1.0).toFloat()

            val dateStr = item.optString("updated_at")
            if (dateStr.isNotEmpty()) {
                try {
                    chapter.date_upload = apiDateFormat.parse(dateStr)?.time ?: 0L
                } catch (e: Exception) {
                    chapter.date_upload = 0L
                }
            }

            chapters.add(chapter)
        }

        return chapters
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (LEGACY_DOMAINS.any { manga.url.startsWith(it) }) {
            throw Exception(MIGRATE_MESSAGE)
        }
        return super.mangaDetailsRequest(manga)
    }

    companion object {
        private val LEGACY_DOMAINS = arrayOf(
            "https://chapmanganato.to/",
            "https://manganato.com/",
            "https://readmanganato.com/",
        )
        private const val MIGRATE_MESSAGE = "Migrate this entry from \"Manganato\" to \"Manganato\" to continue reading"
    }
}