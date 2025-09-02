package eu.kanade.tachiyomi.extension.ar.mangapro

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import eu.kanade.tachiyomi.source.model.Filter

class MangaPro : ParsedHttpSource() {

    override val name = "Manga Pro"
    override val baseUrl = "https://promanga.net"
    override val lang = "ar"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2)
        .build()

    private val buildId by lazy { getBuildId() }

    private fun getBuildId(): String {
        return client.newCall(GET(baseUrl, headers)).execute().use { response ->
            if (!response.isSuccessful) throw Exception("فشل تحميل الصفحة الرئيسية")
            val body = response.body?.string().orEmpty()
            val regex = "\"buildId\":\"([^\"]+)\"".toRegex()
            regex.find(body)?.groupValues?.get(1)
                ?: throw Exception("لم يتم العثور على BuildId")
        }
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            ShowLockedChaptersFilter()
        )
    }

    class ShowLockedChaptersFilter : Filter.CheckBox("عرض الفصول المقفلة", false)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/_next/data/$buildId/series.json?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = JSONObject(response.body?.string().orEmpty())
        return parseMangaList(json)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/_next/data/$buildId/series.json?sort=latest&page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/_next/data/$buildId/series.json?searchTerm=$query&page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/_next/data/$buildId${manga.url}.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val json = JSONObject(response.body?.string().orEmpty())
        val obj = json.getJSONObject("pageProps").getJSONObject("series")

        return SManga.create().apply {
            title = obj.optString("title", "بدون عنوان")
            description = obj.optString("description", "")
            author = obj.optString("author", "")
            artist = obj.optString("artist", "")
            thumbnail_url = obj.optString("image", "")
            genre = obj.optJSONArray("genres")?.join(", ")
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/_next/data/$buildId${manga.url}.json", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = JSONObject(response.body?.string().orEmpty())
        val arr = json.getJSONObject("pageProps").getJSONArray("chapters")

        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val isLocked = obj.optBoolean("locked", false)

            val showLocked = getFilterList().filterIsInstance<ShowLockedChaptersFilter>().firstOrNull()?.state ?: false
            if (!showLocked && isLocked) return@mapNotNull null

            SChapter.create().apply {
                name = obj.optString("title", "بدون عنوان")
                url = "/read/${obj.optString("id", "")}"
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val imgs = document.select("div.reader-area img")
        return imgs.mapIndexed { i, el ->
            Page(i, "", el.attr("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = ""

    private fun parseMangaList(json: JSONObject): MangasPage {
        val data = json.getJSONObject("pageProps").getJSONArray("series")
        val mangas = (0 until data.length()).map { i ->
            val obj = data.getJSONObject(i)
            SManga.create().apply {
                title = obj.optString("title", "بدون عنوان")
                url = "/series/${obj.optString("slug", "")}"
                thumbnail_url = obj.optString("image", "")
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }
}
