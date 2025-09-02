package eu.kanade.tachiyomi.extension.ar.mangapro

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MangaPro : ParsedHttpSource(), ConfigurableSource {

    override val name = "Manga Pro"
    override val baseUrl = "https://promanga.net"
    override val lang = "ar"
    override val supportsLatest = true
    override val versionId = 4

    // Preferences
    private lateinit var preferences: SharedPreferences

    // ===================== BuildId =====================
    private val buildId by lazy { safeGetBuildId() }

    private fun safeGetBuildId(): String {
        return try {
            val resp = client.newCall(Request.Builder().url(baseUrl).get().build()).execute()
            val regex = "\"buildId\":\"([^\"]+)\"".toRegex()
            regex.find(resp.body?.string() ?: "")?.groupValues?.get(1) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun ensureBuildId(): String {
        if (buildId.isBlank()) throw Exception("تعذر جلب بيانات الموقع (BuildId غير موجود)")
        return buildId
    }

    // ===================== Popular =====================
    override fun popularMangaRequest(page: Int): Request {
        val id = ensureBuildId()
        return Request.Builder().url("$baseUrl/_next/data/$id/series.json?page=$page").get().build()
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ===================== Latest =====================
    override fun latestUpdatesRequest(page: Int): Request {
        val id = ensureBuildId()
        return Request.Builder().url("$baseUrl/_next/data/$id/series.json?sort=latest&page=$page").get().build()
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // ===================== Search =====================
    override fun searchMangaRequest(page: Int, query: String, filters: List<Any>): Request {
        val id = ensureBuildId()
        return Request.Builder().url("$baseUrl/_next/data/$id/series.json?searchTerm=$query&page=$page").get().build()
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val body = response.body?.string() ?: throw Exception("لم يتم استلام بيانات")
        if (!body.trim().startsWith("{")) throw Exception("الاستجابة غير صحيحة من الموقع")

        val json = JSONObject(body)
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
        val id = ensureBuildId()
        return Request.Builder().url("$baseUrl/_next/data/$id${manga.url}.json").get().build()
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val jsonText = document.selectFirst("pre")?.text() ?: "{}"
        val obj = JSONObject(jsonText).getJSONObject("pageProps").getJSONObject("series")
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
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body?.string() ?: "{}"
        val arr = JSONObject(body).getJSONObject("pageProps").getJSONArray("chapters")

        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, PREF_SHOW_LOCKED_DEFAULT)

        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val locked = obj.optBoolean("locked", false)
            if (!showLocked && locked) null
            else SChapter.create().apply {
                name = obj.getString("title") + if (locked) " 🔒" else ""
                url = "/read/${obj.getString("id")}"
            }
        }
    }

    // ===================== Pages =====================
    override fun pageListRequest(chapter: SChapter): Request =
        Request.Builder().url("$baseUrl${chapter.url}").get().build()

    override fun pageListParse(document: Document): List<Page> {
        val imgs = document.select("div.reader-area img")
        return imgs.mapIndexed { i, el -> Page(i, "", el.absUrl("src")) }
    }

    override fun imageUrlParse(document: Document): String = ""

    // ===================== Preferences =====================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferences = PreferenceManager.getDefaultSharedPreferences(screen.context)
        val showLockedPref = SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "عرض الفصول المقفلة"
            summary = "إظهار الفصول المقفلة (قد لا تعمل إذا كان المحتوى محمي)"
            setDefaultValue(PREF_SHOW_LOCKED_DEFAULT)
        }
        screen.addPreference(showLockedPref)
    }

    companion object {
        private const val PREF_SHOW_LOCKED = "showLocked"
        private const val PREF_SHOW_LOCKED_DEFAULT = false
    }
}
