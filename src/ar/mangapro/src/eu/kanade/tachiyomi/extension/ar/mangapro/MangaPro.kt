package eu.kanade.tachiyomi.extension.ar.mangapro

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.json.JSONObject

class MangaPro : Iken(
    "Manga Pro",
    "ar",
    "https://promanga.net"
) {

    override val versionId = 4

    // ===================== Popular =====================

    override fun popularMangaRequest(page: Int): Request {
        return GET("/_next/data/${safeBuildId()}/series.json?page=$page")
    }

    override fun popularMangaParse(response: String): MangasPage {
        return parseMangaList(response)
    }

    // ===================== Latest =====================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("/_next/data/${safeBuildId()}/series.json?sort=latest&page=$page")
    }

    override fun latestUpdatesParse(response: String): MangasPage {
        return parseMangaList(response)
    }

    // ===================== Search =====================

    override fun searchMangaRequest(page: Int, query: String): Request {
        return GET("/_next/data/${safeBuildId()}/series.json?searchTerm=$query&page=$page")
    }

    override fun searchMangaParse(response: String): MangasPage {
        return parseMangaList(response)
    }

    private fun parseMangaList(response: String): MangasPage {
        val json = JSONObject(response)
        val data = json.getJSONObject("pageProps").optJSONArray("series") ?: return MangasPage(emptyList(), false)

        val mangas = (0 until data.length()).map { i ->
            val obj = data.getJSONObject(i)
            SManga.create().apply {
                title = obj.getString("title")
                url = "/series/${obj.getString("slug")}"
                thumbnail_url = obj.optString("image")
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // ===================== Manga details =====================

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("/_next/data/${safeBuildId()}${manga.url}.json")
    }

    override fun mangaDetailsParse(response: String): SManga {
        val json = JSONObject(response)
        val obj = json.getJSONObject("pageProps").getJSONObject("series")

        return SManga.create().apply {
            title = obj.getString("title")
            description = obj.optString("description", "")
            author = obj.optString("author", "")
            artist = obj.optString("artist", "")
            thumbnail_url = obj.optString("image")
            genre = obj.optJSONArray("genres")?.join(", ")
        }
    }

    // ===================== Chapters =====================

    override fun chapterListRequest(manga: SManga): Request {
        return GET("/_next/data/${safeBuildId()}${manga.url}.json")
    }

    override fun chapterListParse(response: String): List<SChapter> {
        val json = JSONObject(response)
        val arr = json.getJSONObject("pageProps").getJSONArray("chapters")

        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SChapter.create().apply {
                name = obj.getString("title") + if (obj.optBoolean("locked", false)) " 🔒" else ""
                url = "/read/${obj.getString("id")}"
            }
        }
    }

    // ===================== Helpers =====================

    private fun safeBuildId(): String {
        return try {
            val response = client.newCall(GET(baseUrl)).execute().body.string()
            val regex = "\"buildId\":\"([^\"]+)\"".toRegex()
            regex.find(response)?.groupValues?.get(1) ?: throw Exception("BuildId غير موجود")
        } catch (e: Exception) {
            throw Exception("تعذر جلب بيانات الموقع")
        }
    }
}
