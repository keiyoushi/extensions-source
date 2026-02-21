package eu.kanade.tachiyomi.extension.en.mistminthaven

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.time.Instant
import java.util.Base64

class MistmintHaven :
    HttpSource(),
    ConfigurableSource {

    override val baseUrl: String = "https://www.mistminthaven.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true
    override val name: String = "Mistmint Haven"
    val apiUrl: String = "https://api.mistminthaven.com/api"
    private val gson: Gson = Gson()

    private val defaultBackendUrl = "https://test-api.kayt.cloud"
    private val backendUrlKey = "backend_api_url"
    private val prefs: SharedPreferences = getPreferences()
    private val backendUrlPref: String
        get() = prefs.getString(backendUrlKey, defaultBackendUrl)!!.trim().removeSuffix("/")
    private val backendUrl: String
        get() = backendUrlPref

    @RequiresApi(Build.VERSION_CODES.O)
    override fun chapterListParse(response: Response): List<SChapter> {
        val res = gson.fromJson(response.body.string(), ChapterListRes::class.java).data
        val list: ArrayList<SChapter> = arrayListOf()
        val slug = response.request.url.toString().replace("$apiUrl/novels/slug/", "").replace("/chapters", "")
        res.forEach { v ->
            v.chapters.forEach { c ->
                if (c.isFree) {
                    val num = v.volumeIndex + (c.order / 10f)
                    val chapter = SChapter.create()
                    val url = "/novels/$slug/${c.slug}"
                    chapter.setUrlWithoutDomain(url)
                    chapter.name = "${c.chapterNumber} - ${c.title.orEmpty()}"
                    chapter.chapter_number = v.volumeIndex + (c.order / 10f)
                    val timestamp: Long = Instant.parse(c.createdAt).toEpochMilli()
                    chapter.date_upload = timestamp
                    list.add(chapter)
                }
            }
        }
        list.reverse()
        return list
    }

    override fun chapterListRequest(manga: SManga): Request = GET("${apiUrl}${manga.url}/chapters".replace("novel", "novels"))

    override fun imageUrlParse(response: Response): String {
        TODO()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun pageListRequest(chapter: SChapter): Request {
        val list: ArrayList<String> = arrayListOf()
        val r = Html2CanvasReq(
            "#chapter-content",
            "$baseUrl/${chapter.url}",
            null,
            "https://www.mistminthaven.com/_next/image?url=%2Fimages%2Fchibi-shirone-sleeping-zzz.gif&w=640&q=100",
            getStyle(),
        )
        val json = gson.toJson(r)
        val base64 = Base64.getUrlEncoder()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
        list.add("$backendUrl/html2canvas/$base64.png")
        return POST(
            url = "$backendUrl/echoBody",
            body = gson.toJson(list).toRequestBody("application/json".toMediaType()),
        )
    }

    fun getStyle(): Html2CanvasReqStyle {
        val hide: ArrayList<String> = arrayListOf()
        hide.add(".flex.justify-end.mb-3.gap-2.relative")
        hide.add(".reader-header")
        return Html2CanvasReqStyle(
            hide = hide,
            fontSize = "50px",
            addMarginTopTo = "#chapter-content-text p",
            marginTop = "50px",
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/novel?limit=20&skipPage=${page - 1}&genres=&sort=latest")

    override fun mangaDetailsParse(response: Response): SManga {
        val res = gson.fromJson(response.body.string(), NovelDetailRes::class.java).data
        val novel = SManga.create()
        val url = "/novel/slug/${res.slug}"
        novel.setUrlWithoutDomain(url)
        novel.title = res.title
        novel.thumbnail_url = res.avatarUrl
        novel.author = res.author
        novel.artist = res.illustrator
        novel.description = res.description
        var status = SManga.UNKNOWN
        when (res.status) {
            1 -> status = SManga.ONGOING
            0 -> status = SManga.COMPLETED
            -1 -> status = SManga.CANCELLED
        }
        novel.status = status
        return novel
    }

    override fun pageListParse(response: Response): List<Page> {
        val type = object : TypeToken<ArrayList<String>>() {}.type
        val list: ArrayList<String> = Gson().fromJson(response.body.string(), type)
        return list.mapIndexed { index, url ->
            Page(
                index = index,
                url = "",
                imageUrl = url,
            )
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl${manga.url}")

    override fun popularMangaParse(response: Response): MangasPage {
        val res = gson.fromJson(response.body.string(), SearchResponseDto::class.java)
        val novels: ArrayList<SManga> = arrayListOf()
        res.data.forEach {
            val novel = SManga.create()
            val url = "/novel/slug/${it.slug}"
            novel.setUrlWithoutDomain(url)
            novel.title = it.title
            novel.thumbnail_url = it.avatarUrl
            novels.add(novel)
        }
        return MangasPage(
            mangas = novels,
            hasNextPage = res.paging.totalPages > res.paging.currentPage,
        )
    }

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/novel?limit=20&skipPage=${page - 1}&genres=&sort=popular")

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = GET("$apiUrl/novel?keyword=$query&limit=20&skipPage=${page - 1}&genres=&sort=popular")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = backendUrlKey
            title = "Base URL For The API"
            summary = "Current: ${backendUrlPref()}"
            dialogTitle = "Set API URL"
            setDefaultValue(defaultBackendUrl)
            setOnPreferenceChangeListener { pref, newValue ->
                val raw = (newValue as? String).orEmpty().trim()
                val normalized = raw.removeSuffix("/")
                val ok = normalized.startsWith("http://") || normalized.startsWith("https://")

                if (ok) {
                    pref.summary = "Current: $normalized"
                }
                ok
            }
        }

        screen.addPreference(baseUrlPref)
    }
    private fun backendUrlPref(): String = backendUrlPref
}
