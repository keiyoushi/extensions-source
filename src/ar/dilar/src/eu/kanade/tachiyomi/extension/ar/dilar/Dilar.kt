package eu.kanade.tachiyomi.extension.ar.dilar

import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
private const val MIRROR_PREF_KEY = "MIRROR"
private const val MIRROR_PREF_TITLE = "Dilar : Mirror Urls"
private val MIRROR_PREF_ENTRIES = arrayOf("Dilar (dilar.tube)", "Golden (golden.rest)")
private val MIRROR_PREF_ENTRY_VALUES = arrayOf("https://dilar.tube", "https://golden.rest")
private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]
private const val RESTART_TACHIYOMI = ".لتطبيق الإعدادات الجديدة Tachiyomi أعد تشغيل"

class Dilar :
    HttpSource(),
    ConfigurableSource {

    override val name = "Dilar"
    override val lang = "ar"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
    private val json = Json { ignoreUnknownKeys = true }
    override val baseUrl by lazy { mirrorPref() }
    private val cdnUrl by lazy { baseUrl }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ── Popular ───────────────────────────────────────────────────────────

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/series?page=$page&sort=views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val list = json.decodeFromString<DilarSeriesListDto>(response.body.string())
        val mangas = list.series.mapNotNull { item ->
            runCatching { item.toSManga(cdnUrl) }.getOrNull()?.takeIf {
                runCatching { it.title.isNotBlank() }.getOrElse { false }
            }
        }
        return MangasPage(mangas, list.hasNextPage)
    }

    // ── Latest ────────────────────────────────────────────────────────────

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/series?page=$page&sort=latest", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ── Search ────────────────────────────────────────────────────────────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = buildJsonObject {
            put("query", query)
            put(
                "includes",
                buildJsonArray {
                    add("Manga")
                    add("Team")
                    add("Member")
                },
            )
        }.toString().toRequestBody("application/json".toMediaType())
        return POST("$baseUrl/api/search/quick_search", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val results = json.decodeFromString<List<DilarSearchGroupDto>>(response.body.string())
        val mangas = results
            .filter { it.clazz == "Manga" }
            .flatMap { it.data }
            .map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ── Manga details ─────────────────────────────────────────────────────

    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/api/series/${mangaId(manga)}", headers)

    override fun mangaDetailsParse(response: Response): SManga = json.decodeFromString<DilarSeriesDto>(response.body.string()).toSManga(cdnUrl)

    // ── Chapter list ──────────────────────────────────────────────────────

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/api/series/${mangaId(manga)}/chapters", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = json.decodeFromString<DilarChapterListDto>(response.body.string())
        return dto.chapters
            .flatMap { chapter ->
                chapter.releases
                    .map { release -> release.toSChapter(chapter, dateFormat) }
            }
            .sortedByDescending { it.chapter_number }
    }

    private fun mangaId(manga: SManga) = manga.url.split("/")[2]

    // ── Page list ─────────────────────────────────────────────────────────

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl/api/chapters/${releaseId(chapter)}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = json.decodeFromString<DilarReleaseDetailDto>(response.body.string())
        return dto.pages
            .sortedBy { it.order }
            .mapIndexed { index, page ->
                Page(index, imageUrl = "$cdnUrl/uploads/releases/${dto.storageKey}/hq/${page.url}")
            }
    }

    private fun releaseId(chapter: SChapter) = chapter.url.substringAfterLast("/")

    override fun imageUrlParse(response: Response) = ""

    // ── Settings ──────────────────────────────────────────────────────────

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = MIRROR_PREF_TITLE
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(mirrorPref)
    }

    private fun mirrorPref() = when {
        System.getenv("CI") == "true" -> MIRROR_PREF_ENTRY_VALUES.joinToString("#, ")
        else -> preferences.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE)!!
    }

    private val preferences by getPreferencesLazy()
}
