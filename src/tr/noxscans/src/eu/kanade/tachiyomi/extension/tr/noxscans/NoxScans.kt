package eu.kanade.tachiyomi.extension.tr.noxscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class NoxScans : MangaThemesia(
    "NoxScans",
    "https://noxscans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    companion object {
        private val IMAGE_EXTENSIONS = listOf(".webp", ".jpg", ".jpeg", ".png", ".gif")
        private const val VERIFICATION_ERROR =
            "Bölümü görüntülemek için WebView'de doğrulama yapmanız gerekiyor"
        private const val ROBOT_VERIFICATION_ERROR =
            "Robot doğrulaması gerekiyor. WebView'de doğrulama yapın"
    }

    private fun checkVerification(document: Document, url: String? = null) {
        when {
            document.select("form[action*=kontrol]").isNotEmpty() -> throw Exception(
                VERIFICATION_ERROR,
            )

            url?.contains("/kontrol/") == true -> throw Exception(ROBOT_VERIFICATION_ERROR)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.use { resp ->
            val document = Jsoup.parse(resp.peekBody(Long.MAX_VALUE).string())
            checkVerification(document, resp.request.url.toString())
            super.chapterListParse(resp)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        checkVerification(document, document.location())

        val scriptContent = document.selectFirst("script:containsData(ts_reader.run)")?.data()
            ?: return super.pageListParse(document)

        return try {
            parseReaderScript(scriptContent)
        } catch (e: Exception) {
            super.pageListParse(document)
        }
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

    override fun mangaDetailsParse(response: Response): SManga {
        return response.use { resp ->
            val document = Jsoup.parse(resp.body.string())
            checkVerification(document, resp.request.url.toString())
            super.mangaDetailsParse(resp)
        }
    }

    private fun isImageUrl(url: String): Boolean = IMAGE_EXTENSIONS.any { ext ->
        url.lowercase().endsWith(ext) && url.contains("/wp-content/uploads/")
    }
}
