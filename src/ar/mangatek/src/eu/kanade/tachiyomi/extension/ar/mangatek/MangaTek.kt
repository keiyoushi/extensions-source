package eu.kanade.tachiyomi.extension.ar.mangatek

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MangaTek :
    KeiSource(),
    ConfigurableSource {

    private var fontSize: Int
        get() = preferences.getString(FONT_SIZE_PREF, DEFAULT_FONT_SIZE)!!.toInt()
        set(value) = preferences.edit().putString(FONT_SIZE_PREF, value.toString()).apply()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(SpeechBubblePainterInterceptor(fontSize))
        rateLimit(3)
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()

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

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga-list?sort=views&page=$page")
        return response.toMangasPage()
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga-list?page=$page")
        return response.toMangasPage()
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
            addQueryParameter("page", page.toString())
        }.build()
        return client.get(url).toMangasPage()
    }

    // ========================= Details & Chapters  =========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        check(url.pathSegments.size >= 2) { "Unsupported URL" }
        val slug = url.pathSegments[1]
        val manga = SManga.create().apply {
            this.url = "/manga/$slug"
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            initialized = true
        }
    }

    private inline fun <reified T> Document.extractAstroProp(key: String): T {
        val prop = selectFirst("[props*=$key]")?.attr("props")
            ?: throw Exception("Unable to find prop with $key")
        return prop.parseAs<JsonElement>().unwrapAstro().parseAs()
    }

    private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
        is JsonArray -> when {
            size == 2 && this[0] is JsonPrimitive -> this[1].unwrapAstro()
            else -> JsonArray(map { it.unwrapAstro() })
        }
        is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
        else -> this
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$baseUrl${manga.url}".toHttpUrl()
        val data: MangaDto = client.get(url).asJsoup().extractAstroProp("manga")
        val slug = url.pathSegments[1]

        return SMangaUpdate(
            data.manga.toSManga(manga.url),
            data.manga.chapters.map { it.toSChapter(slug) },
        )
    }

    //  ============================== Page ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
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
    }
}
