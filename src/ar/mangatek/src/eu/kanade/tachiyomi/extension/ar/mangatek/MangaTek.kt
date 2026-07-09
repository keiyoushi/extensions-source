package eu.kanade.tachiyomi.extension.ar.mangatek

import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@Source
abstract class MangaTek :
    HttpSource(),
    ConfigurableSource {

    private var fontSize: Int
        get() = preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE)!!.toInt()
        set(value) = preferences.edit().putString(FONT_SIZE_PREF, value.toString()).apply()

    override val client by lazy {
        network.client.newBuilder()
            .addInterceptor(SpeechBubblePainterInterceptor(fontSize))
            .rateLimit(3)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".flex-grow .grid a").map { element ->
            SManga.create().apply {
                title = element.select("h3").attr("title")
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("nav a[aria-disabled=false] .fa-chevron-left") != null

        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("p.text-base")?.text()
            genre = document
                .selectFirst("p > span:contains(التصنيفات:) + span")
                ?.text()?.replace("،", ",")
            status = document.selectFirst(".flex span.border.rounded")?.text().toStatus()
            thumbnail_url = document.selectFirst("img#mangaCover")?.imgAttr()
            author = document
                .selectFirst("p > span:contains(المؤلف:) + span")
                ?.ownText()
                ?.takeIf { it != "Unknown" }
        }
    }

    private fun String?.toStatus() = when (this) {
        "مستمر" -> SManga.ONGOING
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesSlug = response.request.url.toString().substringAfterLast("/")

        val props = response.asJsoup()
            .selectFirst("astro-island[component-url*=MangaChaptersLoader]")
            ?.attr("props") ?: return emptyList()

        val data = props.parseAs<MangaWrapper>()
        val chapters = data.manga.value.mangaChapters.value.map { it.value }

        return chapters.map { ch ->
            SChapter.create().apply {
                name = ch.title.value?.takeIf { it.isNotBlank() } ?: "Chapter ${ch.chapterNumber.value}"
                url = "/reader/$seriesSlug/${ch.chapterNumber.value}"
                date_upload = dateFormat.tryParse(ch.createdAt.value)
            }
        }
    }

    // Page
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = getPages(document)

        return pages.mapIndexed { index, page ->
            val imageUrl = when {
                page.hasSpeechBubbles() -> "${page.imageUrl}${page.bubbles.toJsonString().toFragment()}"
                else -> page.imageUrl
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun getPages(document: Document): List<PageDTO> = document.select(".manga-page").map { element ->
        val imageUrl = element.selectFirst("img")!!.imgAttr()
        val overlays = element.select(".text-overlay").takeIf(List<Element>::isNotEmpty) ?: return@map PageDTO(imageUrl)

        val bubbles = overlays.map { overlay ->
            val style = overlay.attr("style")
            Bubble(
                text = overlay.text(),
                left = style.substringAfterLast("left:").substringBefore("%").trim().toFloat(),
                top = style.substringAfterLast("top:").substringBefore("%").trim().toFloat(),
                width = style.substringAfterLast("width:").substringBefore("%").trim().toFloat(),
                height = style.substringAfterLast("height:").substringBefore("%").trim().toFloat(),
            )
        }

        PageDTO(imageUrl, bubbles)
    }

    fun String.toFragment(): String = "#${this.replace("#", "*")}"

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val sizes = arrayOf(
            "12", "13", "14",
            "15", "16", "18",
            "20", "21", "22",
            "24", "26", "28",
            "32", "36", "40",
            "42", "44", "48",
            "54", "60", "72",
            "80", "88", "96",
        )

        ListPreference(screen.context).apply {
            key = FONT_SIZE_PREF
            title = "Font size"
            entries = sizes.map {
                "${it}pt" + if (it == DEFAULT_FONT_SIZE) " - Default" else ""
            }.toTypedArray()
            entryValues = sizes

            summary = buildString {
                appendLine("Font changes will not be applied to downloaded or cached chapters. ")
                append("\t* %s")
            }

            setDefaultValue(fontSize.toString())

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entries[index] as String

                Toast.makeText(
                    screen.context,
                    "Font size changed to '$entry'. Restart app to apply new setting.",
                    Toast.LENGTH_LONG,
                ).show()

                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        val PAGE_REGEX = Regex(""".*?\.(webp|png|jpg|jpeg)(?:\?v=\d+)?#\[.*?]""", RegexOption.IGNORE_CASE)
        private const val FONT_SIZE_PREF = "fontSizePref"
        private const val DEFAULT_FONT_SIZE = "28"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale("ar"))
    }
}
