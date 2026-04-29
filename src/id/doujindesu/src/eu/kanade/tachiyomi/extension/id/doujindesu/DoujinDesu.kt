package eu.kanade.tachiyomi.extension.id.doujindesu

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class DoujinDesu :
    HttpSource(),
    ConfigurableSource {

    // Information : DoujinDesu use EastManga WordPress Theme
    override val name = "Doujindesu"
    override val baseUrl by lazy { preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!! }
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id"))

    private fun parseStatus(status: String) = when {
        status.lowercase(Locale.US).contains("publishing") -> SManga.ONGOING
        status.lowercase(Locale.US).contains("finished") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("a")?.let {
            title = element.selectFirst("h3.title")?.text() ?: ""
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        element.selectFirst("a > figure.thumbnail > img")?.let {
            thumbnail_url = imageFromElement(it)
        }
    }

    private fun imageFromElement(element: Element): String = when {
        element.hasAttr("data-src") -> element.attr("abs:data-src")
        element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
        element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
        else -> element.attr("abs:src")
    }

    private fun getNumberFromString(epsStr: String?): Float = epsStr?.substringBefore(" ")?.toFloatOrNull() ?: -1f

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhwa/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#archives > div.entries > article").map(::parseMangaFromElement)
        val hasNextPage = document.selectFirst("nav.pagination > ul > li.last > a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/doujin/page/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#archives > div.entries > article").map(::parseMangaFromElement)
        val hasNextPage = document.selectFirst("nav.pagination > ul > li.last > a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search & Filter

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val baseUrlWithPage = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        val finalUrl = if (query.isNotBlank()) "$baseUrlWithPage?s=${query.replace(" ", "+")}" else baseUrlWithPage

        val agsFilter = filters.firstInstanceOrNull<AuthorGroupSeriesFilter>()
        val agsValueFilter = filters.firstInstanceOrNull<AuthorGroupSeriesValueFilter>()
        val selectedOption = agsFilter?.values?.getOrNull(agsFilter.state)
        val filterValue = agsValueFilter?.state?.trim() ?: ""

        if (query.isBlank() && selectedOption != null && selectedOption.key.isNotBlank()) {
            val typePath = selectedOption.key
            val url = if (filterValue.isBlank()) {
                if (page == 1) "$baseUrl/$typePath/" else "$baseUrl/$typePath/page/$page/"
            } else {
                if (page == 1) "$baseUrl/$typePath/$filterValue/" else "$baseUrl/$typePath/$filterValue/page/$page/"
            }
            return GET(url, headers)
        }
        return GET(finalUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#archives > div.entries > article").map(::parseMangaFromElement)
        val hasNextPage = document.selectFirst("nav.pagination > ul > li.last > a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NB: Fitur Emergency, jadi maklumi aja jika ada bug!"),
        Filter.Separator(),
        Filter.Header("NB: Tidak bisa digabungkan dengan memakai pencarian teks dan filter lainnya, serta harus memasukkan nama Author, Group dan Series secara lengkap!"),
        AuthorGroupSeriesFilter(authorGroupSeriesOptions),
        AuthorGroupSeriesValueFilter(),
        Filter.Separator(),
        /* Will be used later if DoujinDesu aleardy fix their problem
        Filter.Header("NB: Untuk Character Filter akan mengambil hasil apapun jika diinput, misal 'alice', maka hasil akan memunculkan semua Karakter yang memiliki nama 'Alice', bisa digabungkan dengan filter lainnya"),
        CharacterFilter(),
        StatusList(statusList),
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreList(getGenreList()),
         */
    )

    // Detail Parse

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("section.metadata") ?: return SManga.create()

        val authorName = infoElement.select("td:contains(Author) ~ td").joinToString { it.text() }.takeIf { it.isNotEmpty() }
        val groupName = infoElement.select("td:contains(Group) ~ td").joinToString { it.text() }.takeIf { it.isNotEmpty() } ?: "Tidak Diketahui"
        val authorParser = authorName ?: groupName.takeIf { it != "Tidak Diketahui" }

        val characterName = infoElement.select("td:contains(Character) ~ td").joinToString { it.text() }.takeIf { it.isNotEmpty() } ?: "Tidak Diketahui"

        val seriesParser = infoElement.selectFirst("td:contains(Series) ~ td")?.text()?.let {
            if (it == "Manhwa") infoElement.selectFirst("td:contains(Serialization) ~ td")?.text() else it
        } ?: ""

        val alternativeTitle = infoElement.select("h1.title > span.alter").joinToString { it.text() }.takeIf { it.isNotEmpty() } ?: "Tidak Diketahui"

        val manga = SManga.create()
        val pb2Element = infoElement.selectFirst("div.pb-2")

        if (pb2Element == null || pb2Element.selectFirst("p:nth-child(1)") == null) {
            manga.description = buildString {
                append("Tidak ada deskripsi yang tersedia bosque\n\n")
                append("Judul Alternatif : ").append(alternativeTitle).append("\n")
                append("Grup             : ").append(groupName).append("\n")
                append("Karakter         : ").append(characterName).append("\n")
                append("Seri             : ").append(seriesParser)
            }
        } else {
            val showDescription = pb2Element.let { element ->
                val paragraphs = element.select("p")
                val firstText = paragraphs.firstOrNull()?.text()?.lowercase()

                val decodeHtmlEntities = { text: String ->
                    Jsoup.parseBodyFragment(text).text().replace('\u00A0', ' ')
                }

                val mergedChapterElement = element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))").firstOrNull {
                    chapterListRegex.containsMatchIn(it.html())
                }
                if (mergedChapterElement != null) {
                    val chapterList = mergedChapterElement.html()
                        .split("<br>")
                        .drop(1)
                        .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                        .filter { it.isNotEmpty() }

                    return@let "Daftar Chapter:\n" + chapterList.joinToString(" | ")
                }

                if (firstText == "sinopsis:" && paragraphs.size > 1 && chapterListRegex.containsMatchIn(paragraphs[1].html())) {
                    val chapterList = paragraphs[1].html()
                        .split("<br>")
                        .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                        .filter { it.isNotEmpty() }

                    return@let "Daftar Chapter:\n" + chapterList.joinToString(" | ")
                }

                val sinopsisStart = element.selectFirst("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))")
                if (sinopsisStart != null) {
                    val htmlSplit = sinopsisStart.html().split("<br>")
                    val startText = htmlSplit.drop(1)
                        .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                        .filter { it.isNotEmpty() && !it.lowercase().startsWith("download") && !it.lowercase().startsWith("volume") && !it.lowercase().startsWith("chapter") }

                    val sinopsisTexts = buildList {
                        addAll(startText)

                        val allP = element.select("p")
                        val startIndex = allP.indexOf(sinopsisStart)

                        for (i in startIndex + 1 until allP.size) {
                            val htmlSplitNext = allP[i].html().split("<br>")
                            val contents = htmlSplitNext
                                .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                                .filter { it.isNotEmpty() && !it.lowercase().startsWith("download") && !it.lowercase().startsWith("volume") && !it.lowercase().startsWith("chapter") }
                            addAll(contents)
                        }
                    }
                    if (sinopsisTexts.isNotEmpty()) {
                        val isChapterList = sinopsisTexts.first().matches(chapterPrefixRegex)
                        val prefix = if (isChapterList) "Daftar Chapter:" else "Sinopsis:"
                        return@let "$prefix\n" + sinopsisTexts.joinToString("\n\n")
                    }
                }

                if (paragraphs.size == 1 && element.selectFirst("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))") != null) {
                    val para = paragraphs[0]
                    val htmlSplit = para.html().split("<br>")

                    val content = htmlSplit.getOrNull(1)?.let {
                        decodeHtmlEntities(it.replace(htmlTagRegex, "").trim())
                    }.orEmpty()

                    if (content.isNotEmpty()) {
                        return@let "Sinopsis:\n$content"
                    }
                }

                if (firstText == "sinopsis:") {
                    val sinopsisLines = paragraphs.drop(1)
                        .map { decodeHtmlEntities(it.text()) }
                        .filter { it.isNotEmpty() && !it.lowercase().startsWith("download") && !it.lowercase().startsWith("volume") && !it.lowercase().startsWith("chapter") }

                    return@let "Sinopsis:\n" + sinopsisLines.joinToString("\n\n")
                }
                return@let ""
            }

            manga.description = buildString {
                if (showDescription.isNotEmpty()) {
                    append(showDescription).append("\n\n")
                }
                append("Judul Alternatif : ").append(alternativeTitle).append("\n")
                append("Seri             : ").append(seriesParser)
            }.replace(Regex(" +"), " ")
        }

        manga.author = authorParser
        manga.genre = infoElement.select("div.tags > a").joinToString { it.text() }
        manga.status = parseStatus(infoElement.selectFirst("td:contains(Status) ~ td")?.text() ?: "")
        manga.thumbnail_url = document.selectFirst("figure.thumbnail img")?.attr("abs:src")

        return manga
    }

    // Chapter Stuff

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapter_list li").map { element ->
            SChapter.create().apply {
                val eps = element.selectFirst("div.epsright chapter")?.text()
                chapter_number = getNumberFromString(eps)
                date_upload = dateFormat.tryParse(element.selectFirst("div.epsleft > span.date")?.text())
                name = "Chapter $eps"
                element.selectFirst("div.epsleft > span.lchx > a")?.attr("abs:href")?.let {
                    setUrlWithoutDomain(it)
                }
            }
        }
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")
        .setRandomUserAgent()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,*/*")
            .set("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    // More parser stuff
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val id = document.selectFirst("#reader")?.attr("data-id") ?: return emptyList()
        val body = FormBody.Builder()
            .add("id", id)
            .build()

        val postResponse = client.newCall(POST("$baseUrl/themes/ajax/ch.php", headers, body)).execute()
        return postResponse.asJsoup().select("img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    companion object {
        private val PREF_DOMAIN_KEY = "preferred_domain_name_v${AppInfo.getVersionName()}"
        private const val PREF_DOMAIN_TITLE = "Mengganti BaseUrl"
        private const val PREF_DOMAIN_DEFAULT = "https://doujindesu.tv"
        private const val PREF_DOMAIN_SUMMARY = "Mengganti domain default dengan domain yang berbeda"

        private val chapterListRegex = Regex("""\d+[-–]?\d*\..+<br>""", RegexOption.IGNORE_CASE)
        private val htmlTagRegex = Regex("<[^>]*>")
        private val chapterPrefixRegex = Regex("""^\d+(-\d+)?\.\s*.*""")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = PREF_DOMAIN_TITLE
            dialogTitle = PREF_DOMAIN_TITLE
            summary = PREF_DOMAIN_SUMMARY
            dialogMessage = "Default: $PREF_DOMAIN_DEFAULT"
            setDefaultValue(PREF_DOMAIN_DEFAULT)

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Mulai ulang aplikasi untuk menerapkan pengaturan baru.", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
        screen.addRandomUAPreference()
    }
}
