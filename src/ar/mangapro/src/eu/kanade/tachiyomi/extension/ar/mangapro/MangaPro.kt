package eu.kanade.tachiyomi.extension.ar.mangapro

import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.preference.PreferencesHelper
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

class MangaPro : ParsedHttpSource(), ConfigurableSource {

    override val name = "Manga Pro"
    override val baseUrl = "https://promanga.net"
    override val lang = "ar"
    override val supportsLatest = true
    
    override val versionId = 4
    
    private val preferences: PreferencesHelper by lazy { Injekt.get() }

    private val buildId by lazy { safeGetBuildId() }

    // ===================== BuildId =====================

    private fun safeGetBuildId(): String {
        return try {
            val response = client.newCall(GET(baseUrl, headers)).execute().body.string()
            val regex = "\"buildId\":\"([^\"]+)\"".toRegex()
            regex.find(response)?.groupValues?.get(1) ?: throw Exception("BuildId not found")
        } catch (e: Exception) {
            // في حال فشل نعيد قيمة فارغة
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
        return GET("$baseUrl/_next/data/$id/series.json?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return parseMangaList(response)
    }

    // ===================== Latest =====================

    override fun latestUpdatesRequest(page: Int): Request {
        val id = ensureBuildId()
        return GET("$baseUrl/_next/data/$id/series.json?sort=latest&page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return parseMangaList(response)
    }

    // ===================== Search =====================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val id = ensureBuildId()
        return GET("$baseUrl/_next/data/$id/series.json?searchTerm=$query&page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return parseMangaList(response)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val body = response.body.string()
        if (!body.trim().startsWith("{")) {
            throw Exception("الاستجابة غير صحيحة من الموقع")
        }

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
        return GET("$baseUrl/_next/data/$id${manga.url}.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val json = JSONObject(body)
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
        val id = ensureBuildId()
        return GET("$baseUrl/_next/data/$id${manga.url}.json", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val json = JSONObject(body)
        val arr = json.getJSONObject("pageProps").getJSONArray("chapters")

        val showLocked = preferences.sharedPreferences
            .getBoolean(PREF_SHOW_LOCKED, PREF_SHOW_LOCKED_DEFAULT)

        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)

            val locked = obj.optBoolean("locked", false)
            if (!showLocked && locked) {
                null
            } else {
                SChapter.create().apply {
                    name = obj.getString("title") + if (locked) " 🔒" else ""
                    url = "/read/${obj.getString("id")}"
                }
            }
        }
    }

    // ===================== Pages =====================

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val imgs = document.select("div.reader-area img")
        return imgs.mapIndexed { i, el ->
            Page(i, "", el.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = ""

    // ===================== Preferences =====================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
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
