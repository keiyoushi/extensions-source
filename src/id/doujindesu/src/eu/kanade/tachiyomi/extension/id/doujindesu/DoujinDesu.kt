package eu.kanade.tachiyomi.extension.id.doujindesu

import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class DoujinDesu : ParsedHttpSource(), ConfigurableSource {
    // Information : DoujinDesu use EastManga WordPress Theme
    override val name = "Doujindesu"
    override val baseUrl by lazy { preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!! }
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    // Private stuff
    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("publishing", ignoreCase = true) -> SManga.ONGOING
        status.contains("finished", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private class Category(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Genre(name: String, val id: String = name) : Filter.CheckBox(name) {
        override fun toString(): String {
            return id
        }
    }

    private class Order(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Status(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private val orderBy = arrayOf(
        Order("Semua", ""),
        Order("A-Z", "title"),
        Order("Update Terbaru", "latest"),
        Order("Baru Ditambahkan", "created"),
        Order("Populer", "popular"),
    )

    private val statusList = arrayOf(
        Status("Semua", ""),
        Status("Berlanjut", "publishing"),
        Status("Selesai", "finished"),
    )

    private val categoryNames = arrayOf(
        Category("Semua", ""),
        Category("Doujinshi", "Doujinshi"),
        Category("Manga", "Manga"),
        Category("Manhwa", "Manhwa"),
    )

    private fun genreList() = listOf(
        Genre("Age Progression", "age-progression"),
        Genre("Age Regression", "age-regression"),
        Genre("Ahegao", "ahegao"),
        Genre("All The Way Through", "all-the-way-through"),
        Genre("Amputee", "amputee"),
        Genre("Anal", "anal"),
        Genre("Anorexia", "anorexia"),
        Genre("Apron", "apron"),
        Genre("Artist CG", "artist-cg"),
        Genre("Aunt", "aunt"),
        Genre("Bald", "bald"),
        Genre("Bestiality", "bestiality"),
        Genre("Big Ass", "big-ass"),
        Genre("Big Breast", "big-breast"),
        Genre("Big Penis", "big-penis"),
        Genre("Bike Shorts", "bike-shorts"),
        Genre("Bikini", "bikini"),
        Genre("Birth", "birth"),
        Genre("Bisexual", "bisexual"),
        Genre("Blackmail", "blackmail"),
        Genre("Blindfold", "blindfold"),
        Genre("Bloomers", "bloomers"),
        Genre("Blowjob", "blowjob"),
        Genre("Body Swap", "body-swap"),
        Genre("Bodysuit", "bodysuit"),
        Genre("Bondage", "bondage"),
        Genre("Bowjob", "bowjob"),
        Genre("Business Suit", "business-suit"),
        Genre("Cheating", "cheating"),
        Genre("Collar", "collar"),
        Genre("Collor", "collor"),
        Genre("Condom", "condom"),
        Genre("Cousin", "cousin"),
        Genre("Crossdressing", "crossdressing"),
        Genre("Cunnilingus", "cunnilingus"),
        Genre("Dark Skin", "dark-skin"),
        Genre("Daughter", "daughter"),
        Genre("Defloration", "defloration"),
        Genre("Demon", "demon"),
        Genre("Demon Girl", "demon-girl"),
        Genre("Dick Growth", "dick-growth"),
        Genre("DILF", "dilf"),
        Genre("Double Penetration", "double-penetration"),
        Genre("Drugs", "drugs"),
        Genre("Drunk", "drunk"),
        Genre("Elf", "elf"),
        Genre("Emotionless Sex", "emotionless-sex"),
        Genre("Exhibitionism", "exhibitionism"),
        Genre("Eyepatch", "eyepatch"),
        Genre("Females Only", "females-only"),
        Genre("Femdom", "femdom"),
        Genre("Filming", "filming"),
        Genre("Fingering", "fingering"),
        Genre("Footjob", "footjob"),
        Genre("Full Color", "full-color"),
        Genre("Furry", "furry"),
        Genre("Futanari", "futanari"),
        Genre("Garter Belt", "garter-belt"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Ghost", "ghost"),
        Genre("Glasses", "glasses"),
        Genre("Gore", "gore"),
        Genre("Group", "group"),
        Genre("Guro", "guro"),
        Genre("Gyaru", "gyaru"),
        Genre("Hairy", "hairy"),
        Genre("Handjob", "handjob"),
        Genre("Harem", "harem"),
        Genre("Horns", "horns"),
        Genre("Huge Breast", "huge-breast"),
        Genre("Huge Penis", "huge-penis"),
        Genre("Humiliation", "humiliation"),
        Genre("Impregnation", "impregnation"),
        Genre("Incest", "incest"),
        Genre("Inflation", "inflation"),
        Genre("Insect", "insect"),
        Genre("Inseki", "inseki"),
        Genre("Inverted Nipples", "inverted-nipples"),
        Genre("Invisible", "invisible"),
        Genre("Kemomimi", "kemomimi"),
        Genre("Kimono", "kimono"),
        Genre("Lactation", "lactation"),
        Genre("Leotard", "leotard"),
        Genre("Lingerie", "lingerie"),
        Genre("Loli", "loli"),
        Genre("Lolipai", "lolipai"),
        Genre("Maid", "maid"),
        Genre("Males", "males"),
        Genre("Males Only", "males-only"),
        Genre("Masturbation", "masturbation"),
        Genre("Miko", "miko"),
        Genre("MILF", "milf"),
        Genre("Mind Break", "mind-break"),
        Genre("Mind Control", "mind-control"),
        Genre("Minigirl", "minigirl"),
        Genre("Miniguy", "miniguy"),
        Genre("Monster", "monster"),
        Genre("Monster Girl", "monster-girl"),
        Genre("Mother", "mother"),
        Genre("Multi-work Series", "multi-work-series"),
        Genre("Muscle", "muscle"),
        Genre("Nakadashi", "nakadashi"),
        Genre("Necrophilia", "necrophilia"),
        Genre("Netorare", "netorare"),
        Genre("Niece", "niece"),
        Genre("Nipple Fuck", "nipple-fuck"),
        Genre("Nurse", "nurse"),
        Genre("Old Man", "old-man"),
        Genre("Only", "only"),
        Genre("Oyakodon", "oyakodon"),
        Genre("Paizuri", "paizuri"),
        Genre("Pantyhose", "pantyhose"),
        Genre("Possession", "possession"),
        Genre("Pregnant", "pregnant"),
        Genre("Prostitution", "prostitution"),
        Genre("Rape", "rape"),
        Genre("Rimjob", "rimjob"),
        Genre("Scat", "scat"),
        Genre("School Uniform", "school-uniform"),
        Genre("Sex Toys", "sex-toys"),
        Genre("Shemale", "shemale"),
        Genre("Shota", "shota"),
        Genre("Sister", "sister"),
        Genre("Sleeping", "sleeping"),
        Genre("Slime", "slime"),
        Genre("Small Breast", "small-breast"),
        Genre("Snuff", "snuff"),
        Genre("Sole Female", "sole-female"),
        Genre("Sole Male", "sole-male"),
        Genre("Stocking", "stocking"),
        Genre("Story Arc", "story-arc"),
        Genre("Sumata", "sumata"),
        Genre("Sweating", "sweating"),
        Genre("Swimsuit", "swimsuit"),
        Genre("Tanlines", "tanlines"),
        Genre("Teacher", "teacher"),
        Genre("Tentacles", "tentacles"),
        Genre("Tomboy", "tomboy"),
        Genre("Tomgirl", "tomgirl"),
        Genre("Torture", "torture"),
        Genre("Twins", "twins"),
        Genre("Twintails", "twintails"),
        Genre("Uncensored", "uncensored"),
        Genre("Unusual Pupils", "unusual-pupils"),
        Genre("Virginity", "virginity"),
        Genre("Webtoon", "webtoon"),
        Genre("Widow", "widow"),
        Genre("X-Ray", "x-ray"),
        Genre("Yandere", "yandere"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )

    private class AuthorGroupSeriesOption(val display: String, val key: String) {
        override fun toString(): String = display
    }

    private val authorGroupSeriesOptions = arrayOf(
        AuthorGroupSeriesOption("None", ""),
        AuthorGroupSeriesOption("Author", "author"),
        AuthorGroupSeriesOption("Group", "group"),
        AuthorGroupSeriesOption("Series", "series"),
    )

    private class AuthorGroupSeriesFilter(options: Array<AuthorGroupSeriesOption>) : Filter.Select<AuthorGroupSeriesOption>("Filter by Author/Group/Series", options, 0)
    private class AuthorGroupSeriesValueFilter : Filter.Text("Nama Author/Group/Series")
    private class CharacterFilter : Filter.Text("Karakter")
    private class CategoryNames(type: Array<Category>) : Filter.Select<Category>("Kategori", type, 0)
    private class OrderBy(order: Array<Order>) : Filter.Select<Order>("Urutkan", order, 0)
    private class GenreList(genre: List<Genre>) : Filter.Group<Genre>("Genre", genre)
    private class StatusList(status: Array<Status>) : Filter.Select<Status>("Status", status, 0)

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[data-state=closed]")!!.absUrl("href"))
        title = element.select("a[data-state=closed] img").attr("alt")
        element.select("a[data-state=closed] img").first()?.let {
            thumbnail_url = imageFromElement(it)
        }
    }

    private val qualityRegex = Regex("q=\\d+")
    private val widthRegex = Regex("w=(\\d+)")

    private fun imageFromElement(element: Element): String {
        val srcset = element.attr("srcset")
        val src = element.attr("src")

        var selectedUrl = if (srcset.isNotEmpty()) {
            srcset.split(",")
                .map { it.trim() }
                .maxByOrNull {
                    widthRegex.find(it)?.groupValues?.get(1)?.toInt() ?: 0
                }
                ?.substringBefore(" ") ?: src
        } else {
            src
        }

        if (selectedUrl.startsWith("/")) {
            selectedUrl = baseUrl.trimEnd('/') + selectedUrl
        }

        return selectedUrl
            .replace(qualityRegex, "q=100")
            .replace("&amp;", "&")
    }

    private val datePrefixRegex = Regex("([+-]\\d{2}):(\\d{2})$")

    private fun parseApiDate(date: String?): Long {
        if (date.isNullOrBlank()) return 0L

        return try {
            val fixedDate = date.replace(
                datePrefixRegex,
                "$1$2",
            )

            val sdf = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssZ",
                Locale("id", "ID"),
            )

            sdf.parse(fixedDate)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga?order=popular&page=$page", headers)
    }

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga?order=updated&page=$page", headers)
    }

    // Element Selectors
    override fun popularMangaSelector(): String = ".animate-fade-in-up > div a[data-state=closed]"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    // Next Page
    override fun popularMangaNextPageSelector(): String = "nav > ul > li > a[aria-label='Go to next page']"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Search & FIlter

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/manga".toHttpUrlOrNull()!!
            .newBuilder()

        // Pagination
        if (page > 1) {
            urlBuilder.addQueryParameter("page", page.toString())
        }

        // Search
        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("title", query)
        }

        // Filter handling
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryNames -> {
                    val category = filter.values[filter.state]
                    if (category.key.isNotBlank()) {
                        urlBuilder.addQueryParameter("type", category.key)
                    }
                }

                is OrderBy -> {
                    val order = filter.values[filter.state]
                    urlBuilder.addQueryParameter("order", order.key)
                }

                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .take(5) // max 5 genre
                        .forEach { genre ->
                            urlBuilder.addQueryParameter("genre[]", genre.id)
                        }
                }

                is StatusList -> {
                    val status = filter.values[filter.state]
                    if (status.key.isNotBlank()) {
                        urlBuilder.addQueryParameter("status", status.key)
                    }
                }

                else -> {}
            }
        }

        val agsFilter = filters.firstInstanceOrNull<AuthorGroupSeriesFilter>()
        val agsValueFilter = filters.firstInstanceOrNull<AuthorGroupSeriesValueFilter>()
        val selectedOption = agsFilter?.values?.getOrNull(agsFilter.state)
        val filterValue = agsValueFilter?.state?.trim() ?: ""

        // Author Group Series handling
        if (query.isBlank() && selectedOption != null && selectedOption.key.isNotBlank()) {
            val typePath = selectedOption.key
            val requestUrl =
                if (filterValue.isBlank()) {
                    "$baseUrl/$typePath?page=$page"
                } else {
                    "$baseUrl/$typePath/$filterValue?page=$page"
                }

            return GET(requestUrl, headers)
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun getFilterList() = FilterList(
        Filter.Header("NB: Tidak bisa digabungkan dengan memakai pencarian teks dan filter lainnya, serta harus memasukkan nama Author, Group dan Series secara lengkap!"),
        AuthorGroupSeriesFilter(authorGroupSeriesOptions),
        AuthorGroupSeriesValueFilter(),
        Filter.Separator(),
        Filter.Header("NB: Untuk Character Filter akan mengambil hasil apapun jika diinput, misal 'alice', maka hasil akan memunculkan semua Karakter yang memiliki nama 'Alice', bisa digabungkan dengan filter lainnya"),
        CharacterFilter(),
        StatusList(statusList),
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreList(genreList()),
    )

    // Detail Parse
    private val chapterListRegex = Regex("""\d+[-–]?\d*\..+<br>""", RegexOption.IGNORE_CASE)
    private val htmlTagRegex = Regex("<[^>]*>")
    private val chapterPrefixRegex = Regex("""^\d+(-\d+)?\.\s*.*""")
    private val widthThumbnailRegex = Regex("url=([^&]+)")

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst("section.flex div.flex-1 tbody")!!
        val groupName = infoElement.selectFirst("td:contains(Group) ~ td")?.wholeText()?.trim() ?: "Tidak Diketahui"
        val characterName = infoElement.selectFirst("td:contains(Character) ~ td")?.wholeText()?.trim() ?: "Tidak Diketahui"
        val seriesParser = infoElement.selectFirst("td:contains(Series) ~ td")?.wholeText()?.trim() ?: "Tidak Diketahui"
        val alternativeTitle = document.selectFirst("section.flex div.flex-1 > h2.text-slate-500")?.wholeText()?.trim() ?: "Tidak Diketahui"
        val authorName = infoElement.selectFirst("td:contains(Author) ~ td")?.text() ?: groupName

        val manga = SManga.create()
        manga.description = if (document.select("section.flex div.flex-1 #manga-summary > p:nth-child(1)").isEmpty()) {
            """
            Tidak ada deskripsi yang tersedia bosque

            Judul Alternatif : $alternativeTitle
            Grup             : $groupName
            Karakter         : $characterName
            Seri             : $seriesParser
            """.trimIndent()
        } else {
            val pb2Element = document.selectFirst("section.flex div.flex-1 #manga-summary")

            val showDescription = pb2Element?.let { element ->
                val paragraphs = element.select("p")
                val firstText = paragraphs.firstOrNull()?.text()?.trim()?.lowercase()

                // Fungsi untuk mendekode semua entitas HTML
                val decodeHtmlEntities = { text: String ->
                    Jsoup.parse(text).text().replace('\u00A0', ' ')
                }

                // CASE 1: Gabungan chapter dalam satu paragraf (Manga Style)
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

                // CASE 2: Dua paragraf: p[0] = "Sinopsis:", p[1] = daftar chapter (Manga Style)
                if (
                    firstText == "sinopsis:" &&
                    paragraphs.size > 1 &&
                    chapterListRegex.containsMatchIn(paragraphs[1].html())
                ) {
                    val chapterList = paragraphs[1].html()
                        .split("<br>")
                        .map { decodeHtmlEntities(it.replace(htmlTagRegex, "").trim()) }
                        .filter { it.isNotEmpty() }

                    return@let "Daftar Chapter:\n" + chapterList.joinToString(" | ")
                }

                // CASE 3 + 5 Hybrid: Tangani Sinopsis dengan <strong> + <br> + <p> campuran (Manhwa Style + Terkompresi)
                val sinopsisPara = element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))")
                if (sinopsisPara.isNotEmpty()) {
                    val sinopsisStart = sinopsisPara.first()!!
                    val htmlSplit = sinopsisStart.html().split("<br>")

                    val startText = htmlSplit
                        .drop(1)
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

                // CASE 4: Satu paragraf saja dengan <strong> dan <br> (Manhwa Style)
                if (
                    paragraphs.size == 1 &&
                    element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))").isNotEmpty()
                ) {
                    val para = paragraphs[0]
                    val htmlSplit = para.html().split("<br>")

                    val content = htmlSplit.getOrNull(1)?.let {
                        decodeHtmlEntities(it.replace(htmlTagRegex, "").trim())
                    }.orEmpty()

                    if (content.isNotBlank()) {
                        return@let "Sinopsis:\n$content"
                    }
                }

                // CASE 6: Fallback
                if (firstText == "sinopsis:") {
                    val sinopsisLines = paragraphs.drop(1)
                        .map { decodeHtmlEntities(it.text().trim()) }
                        .filter { it.isNotEmpty() && !it.lowercase().startsWith("download") && !it.lowercase().startsWith("volume") && !it.lowercase().startsWith("chapter") }

                    return@let "Sinopsis:\n" + sinopsisLines.joinToString("\n\n")
                }
                return@let ""
            } ?: ""
            """
            |$showDescription
            |
            |Judul Alternatif : $alternativeTitle
            |Seri             : $seriesParser
            """.trimMargin().replace(Regex(" +"), " ")
        }
        manga.author = authorName
        manga.genre = document.select("section.flex div.flex-1 .my-4 a button").joinToString { it.text() }
        manga.status = parseStatus(
            infoElement.selectFirst("td:contains(Status) ~ td")?.text(),
        )
        manga.thumbnail_url = document.selectFirst("section.flex > div.mx-auto img")
            ?.let { img ->
                val src = img.attr("abs:src")
                val srcset = img.attr("srcset")
                val best = srcset.split(",")
                    .maxByOrNull { it.trim().substringAfterLast(" ").removeSuffix("x").toIntOrNull() ?: 1 }
                    ?.substringBefore(" ")
                    ?.trim()

                val pick = best ?: img.attr("abs:src")
                val real = widthThumbnailRegex.find(pick)?.groupValues?.get(1)
                real?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: pick

                val hdProxy = if (real != null) {
                    "$baseUrl/_next/image?url=${java.net.URLEncoder.encode(real, "UTF-8")}&w=1080&q=100"
                } else {
                    null
                }

                real ?: hdProxy ?: src
            }

        return manga
    }

    // Chapter Stuff
    @Serializable
    data class ChapterResponse(
        val page: Int,
        val limit: Int,
        val total: Int,
        val total_pages: Int,
        val items: List<ChapterItem>,
    )

    @Serializable
    data class ChapterItem(
        val id: Int,
        val chapter_number: Double,
        val slug: String,
        val title: String,
        val created_at: String,
    )

    private val jsonDecoder = Json {
        ignoreUnknownKeys = true
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/manga/$slug/chapters?page=1", headers)
    }

    private val chapterNameRegex = Regex("(Chapter\\s*\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.toString()
            .substringAfter("/api/manga/")
            .substringBefore("/chapters")

        val chapters = mutableListOf<SChapter>()

        var page = 1
        var totalPages: Int

        do {
            val url = "$baseUrl/api/manga/$slug/chapters?page=$page"
            val res = client.newCall(GET(url, headers)).execute()

            val body = res.body!!.string()
            val parsed = jsonDecoder.decodeFromString<ChapterResponse>(body)

            totalPages = parsed.total_pages

            for (obj in parsed.items) {
                val chapterName = chapterNameRegex
                    .find(obj.title)
                    ?.value
                    ?: "Chapter ${obj.chapter_number.toInt()}"

                val chapter = SChapter.create().apply {
                    chapter_number = obj.chapter_number.toFloat()
                    name = chapterName
                    date_upload = parseApiDate(obj.created_at)
                    setUrlWithoutDomain("/read/${obj.slug}")
                }
                chapters += chapter
            }

            page++
        } while (page <= totalPages)

        return chapters.sortedByDescending { it.chapter_number }
    }

    // Page Stuff
    private val xorKey = "youdoZFFxQusvsva1iHsbccZbpUAjoqB6niUyntkn5mocg2DZ0fCw1Zoow"

    private fun decodeImage(encoded: String, index: Int = 0): String {
        return try {
            val padding = (4 - encoded.length % 4) % 4
            val padded = (encoded + "=".repeat(padding))
                .replace('-', '+')
                .replace('_', '/')

            val decoded = Base64.decode(padded, Base64.DEFAULT)
            val bytes = decoded.mapIndexed { i, b ->
                val keyChar = xorKey[i % xorKey.length].code.toByte()
                (b.toInt() xor keyChar.toInt()).toByte()
            }.toByteArray()

            var url = String(bytes).trim()

            if (!url.startsWith("http")) {
                url = tryFallback(encoded)
            }

            return url
        } catch (_: Exception) {
            ""
        }
    }

    private fun tryFallback(encoded: String): String {
        return try {
            val decoded = Base64.decode(encoded, Base64.DEFAULT)
            val shifted = decoded.map { (it + 3).toByte() }.toByteArray()
            val url = String(shifted)

            if (url.startsWith("http")) url else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun cdnHeaders(slug: String, chapterUrl: String): Headers {
        val host = baseUrl.trimEnd('/')
        val refererUrl = if (chapterUrl.startsWith("http")) chapterUrl else "$host$chapterUrl"

        return Headers.Builder()
            .set("Accept", "application/json, text/plain, */*")
            .set("Origin", host)
            .set("Referer", refererUrl)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    override fun imageRequest(page: Page): Request {
        val headers = Headers.Builder()
            .set("Accept", "image/avif,image/webp,*/*")
            .set("Origin", "https://doujindesu.tv")
            .set("Referer", "https://doujindesu.tv/")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET(page.imageUrl!!, headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val rawSlug = chapter.url.substringAfter("/read/").trim('/')
        val slug = URLEncoder.encode(rawSlug, "UTF-8")

        val apiUrl = "https://cdn.doujindesu.dev/api/ch.php?slug=$slug"

        return GET(apiUrl, cdnHeaders(rawSlug, chapter.url))
    }

    override fun pageListParse(response: Response): List<Page> {

        if (response.code == 404) {
            throw Exception("Halaman tidak ditemukan (404). Coba buka di WebView.")
        }

        val responseBody = response.body.string()
        val json = JSONObject(responseBody)
        val success = json.optBoolean("success", true)
        val items = json.optJSONArray("images") ?: throw Exception("JSON 'images' tidak ditemukan")

        if (!success) {
            throw Exception("Akses CDN ditolak. Buka WebView untuk melewati proteksi.")
        }

        val pages = List(items.length()) { index ->
            val decoded = decodeImage(items.getString(index), index)
            if (decoded.isBlank()) {
                null
            } else {
                Page(index, imageUrl = decoded)
            }
        }.filterNotNull()

        return pages
    }

    // Unsuported
    override fun chapterListSelector(): String =
        throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter =
        throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> =
        throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    // Change domain toast
    companion object {
        private val PREF_DOMAIN_KEY = "preferred_domain_name_v${AppInfo.getVersionName()}"
        private const val PREF_DOMAIN_TITLE = "Mengganti BaseUrl"
        private const val PREF_DOMAIN_DEFAULT = "https://doujindesu.tv"
        private const val PREF_DOMAIN_SUMMARY = "Mengganti domain default dengan domain yang berbeda"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = PREF_DOMAIN_TITLE
            dialogTitle = PREF_DOMAIN_TITLE
            summary = PREF_DOMAIN_SUMMARY
            dialogMessage = "Default: $PREF_DOMAIN_DEFAULT"
            setDefaultValue(PREF_DOMAIN_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                Toast.makeText(screen.context, "Mulai ulang aplikasi untuk menerapkan pengaturan baru.", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
        addRandomUAPreferenceToScreen(screen)
    }
}
