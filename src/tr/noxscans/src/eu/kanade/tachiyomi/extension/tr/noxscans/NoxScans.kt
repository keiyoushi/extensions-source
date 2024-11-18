package eu.kanade.tachiyomi.extension.tr.noxscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class NoxScans : MangaThemesia(
    "NoxScans",
    "https://www.noxscans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    companion object {
        private val IMAGE_EXTENSIONS = listOf(".webp", ".jpg", ".jpeg", ".png", ".gif")
        private const val ROBOT_VERIFICATION_ERROR =
            "Robot doğrulaması gerekiyor. WebView'de doğrulama yapın"
    }

    private var attempts = 0
    private val formSelector = "form[action*=kontrol], form:has(legend)"

    private fun checkVerification(document: Document): Document {
        attempts = 0
        return document.selectFirst(formSelector)?.let {
            bypassRobotVerification(document)
        } ?: document
    }

    private fun bypassRobotVerification(document: Document): Document {
        if (attempts == 3) {
            throw Exception(ROBOT_VERIFICATION_ERROR)
        }

        attempts++

        return document.selectFirst(formSelector)?.let { robotForm ->
            val formUrl = robotForm.absUrl("action").takeIf(String::isNotBlank) ?: document.location()
            val input = robotForm.selectFirst("input")!!.let {
                it.attr("name") to it.attr("value")
            }

            val formBody = FormBody.Builder()
                .add(input.first, input.second)
                .build()

            bypassRobotVerification(client.newCall(POST(formUrl, headers, formBody)).execute().asJsoup())
        } ?: document
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return checkVerification(response.asJsoup())
            .select(chapterListSelector())
            .map(::chapterFromElement)
    }

    override fun pageListParse(document: Document): List<Page> {
        val doc = checkVerification(document)

        val scriptContent = doc.selectFirst("script:containsData(ts_reader.run)")?.data()
            ?: return super.pageListParse(doc)

        return try {
            parseReaderScript(scriptContent)
        } catch (e: Exception) {
            super.pageListParse(doc)
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(checkVerification(document))
    }

    private fun parseReaderScript(scriptContent: String): List<Page> {
        val jsonStr = scriptContent.substringAfter("ts_reader.run(").substringBefore(");")
        val jsonData = JSONObject(jsonStr)

        val serverArrayKey =
            findServerArrayKey(jsonData) ?: throw Exception("Server array not found")
        val serverArray = jsonData.getJSONArray(serverArrayKey)
        val firstServer = serverArray.getJSONObject(0)

        val imageArrayKey =
            findImageArrayKey(firstServer) ?: throw Exception("Image array not found")
        val imageArray = firstServer.getJSONArray(imageArrayKey)

        return List(imageArray.length()) { i ->
            Page(i, "", imageArray.getString(i))
        }
    }

    private fun findServerArrayKey(jsonData: JSONObject): String? =
        jsonData.keys().asSequence().find { key ->
            try {
                val value = jsonData.getJSONArray(key)
                value.length() > 0 && isValidServerObject(value.getJSONObject(0))
            } catch (e: Exception) {
                false
            }
        }

    private fun isValidServerObject(obj: JSONObject): Boolean =
        obj.length() == 2 && obj.keys().asSequence().any { key ->
            try {
                val arrayValue = obj.getJSONArray(key)
                arrayValue.length() > 0 && isImageUrl(arrayValue.getString(0))
            } catch (e: Exception) {
                false
            }
        }

    private fun findImageArrayKey(server: JSONObject): String? =
        server.keys().asSequence().find { key ->
            try {
                val value = server.getJSONArray(key)
                value.length() > 0 && isImageUrl(value.getString(0))
            } catch (e: Exception) {
                false
            }
        }

    private fun isImageUrl(url: String): Boolean = IMAGE_EXTENSIONS.any { ext ->
        url.lowercase().endsWith(ext) && url.contains("/wp-content/uploads/")
    }
}
