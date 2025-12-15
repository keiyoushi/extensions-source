package eu.kanade.tachiyomi.extension.id.doujindesu

import android.content.SharedPreferences
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
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

    private val DATE_FORMAT by lazy {
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id"))
    }

    private fun parseStatus(status: String) = when {
        status.lowercase(Locale.US).contains("publishing") -> SManga.ONGOING
        status.lowercase(Locale.US).contains("finished") -> SManga.COMPLETED
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
        Order("Update Terbaru", "update"),
        Order("Baru Ditambahkan", "latest"),
        Order("Populer", "popular"),
    )

    private val statusList = arrayOf(
        Status("Semua", ""),
        Status("Berlanjut", "publishing"),
        Status("Selesai", "finished"),
    )

    private val categoryNames = arrayOf(
        Category("Semua", ""),
        Category("Doujinshi", "doujinshi"),
        Category("Manga", "manga"),
        Category("Manhwa", "manhwa"),
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

    private val typePrefixRegex = Regex("^(Doujinshi|Manga|Manhwa)\\s+", RegexOption.IGNORE_CASE)
    private val chapterInfoRegex = Regex("\\s*Ch\\s*\\d+.*$")

    private fun basicInformationFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("div.p-6.py-1.px-1 > a").let {
            val fullTitle = element.selectFirst("div.p-6.py-1.px-1 > a > div.font-semibold")?.text()?.trim() ?: ""
            var cleanTitle = fullTitle.replace(chapterInfoRegex, "").trim()
            cleanTitle = cleanTitle.replace(typePrefixRegex, "")
            manga.title = cleanTitle
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        element.select("div > div > a > div > div:nth-child(1) > div > img").first()?.let {
            manga.thumbnail_url = imageFromElement(it)
        }

        return manga
    }

    private fun imageFromElement(element: Element): String {
        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
            else -> element.attr("abs:src")
        }
    }

    private fun getNumberFromString(epsStr: String?): Float {
        return epsStr?.substringBefore(" ")?.toFloatOrNull() ?: -1f
    }

    private fun reconstructDate(dateStr: String): Long {
        return runCatching { DATE_FORMAT.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }

    // Popular

    override fun popularMangaFromElement(element: Element): SManga =
        basicInformationFromElement(element)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga?order=popular&page=$page", headers)
    }

    // Latest

    override fun latestUpdatesFromElement(element: Element): SManga =
        basicInformationFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga?order=updated&page=$page", headers)
    }

    // Element Selectors

    override fun popularMangaSelector(): String = "div.border.bg-card"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector(): String = "nav > ul > li > a[aria-label='Go to next page']"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Search & FIlter

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Anything else filter handling
        val baseUrlWithPage = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"

        val finalUrl = if (query.isNotBlank()) "$baseUrlWithPage?s=${query.replace(" ", "+")}" else baseUrlWithPage

        /* Will be used later if DoujinDesu aleardy fix their problem
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryNames -> {
                    val category = filter.values[filter.state]
                    url.addQueryParameter("type", category.key)
                }
                is OrderBy -> {
                    val order = filter.values[filter.state]
                    url.addQueryParameter("order", order.key)
                }
                is CharacterFilter -> {
                    url.addQueryParameter("character", filter.state)
                }
                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .forEach { genre ->
                            url.addQueryParameter("genre[]", genre.id)
                        }
                }
                is StatusList -> {
                    val status = filter.values[filter.state]
                    url.addQueryParameter("status", status.key)
                }
                else -> {}
            }
        }
         */

        val agsFilter = filters.firstInstanceOrNull<AuthorGroupSeriesFilter>()
        val agsValueFilter = filters.firstInstanceOrNull<AuthorGroupSeriesValueFilter>()
        val selectedOption = agsFilter?.values?.getOrNull(agsFilter.state)
        val filterValue = agsValueFilter?.state?.trim() ?: ""

        // Author/Group/Series filter handling
        if (query.isBlank() && selectedOption != null && selectedOption.key.isNotBlank()) {
            val typePath = selectedOption.key
            val request = if (filterValue.isBlank()) {
                val url = if (page == 1) {
                    "$baseUrl/$typePath/"
                } else {
                    "$baseUrl/$typePath/page/$page/"
                }
                GET(url, headers)
            } else {
                val url = if (page == 1) {
                    "$baseUrl/$typePath/$filterValue/"
                } else {
                    "$baseUrl/$typePath/$filterValue/page/$page/"
                }
                GET(url, headers)
            }
            return request
        }
        return GET(finalUrl, headers)
    }

    override fun searchMangaFromElement(element: Element): SManga =
        basicInformationFromElement(element)

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
        GenreList(genreList()),
         */
    )

    // Detail Parse

    private val chapterListRegex = Regex("""\d+[-–]?\d*\..+<br>""", RegexOption.IGNORE_CASE)
    private val htmlTagRegex = Regex("<[^>]*>")
    private val chapterPrefixRegex = Regex("""^\d+(-\d+)?\.\s*.*""")

    override fun mangaDetailsParse(document: Document): SManga {
        val titleElement = document.selectFirst("section.flex div.flex-1")!!
        val infoElement = document.selectFirst("section.flex div.flex-1 tbody")!!
        val authorName = if (infoElement.select("td:contains(Author) ~ td a").isEmpty()) {
            null
        } else {
            infoElement
                .select("td:contains(Author) ~ td a")
                .joinToString(", ") { it.text().trim() }
        }

        val groupName = if (infoElement.select("td:contains(Group) ~ td a").isEmpty()) {
            "Tidak Diketahui"
        } else {
            val text = infoElement
                .select("td:contains(Group) ~ td a")
                .joinToString(", ") { it.text().trim() }
            if (text == "N/A") "Tidak Diketahui" else text
        }

        val authorParser = if (authorName.isNullOrEmpty()) {
            groupName.takeIf { it.isNotEmpty() && it != "Tidak Diketahui" }
        } else {
            authorName
        }

        val characterName = if (infoElement.select("td:contains(Character) ~ td a").isEmpty()) {
            "Tidak Diketahui"
        } else {
            infoElement
                .select("td:contains(Character) ~ td a")
                .joinToString(", ") { it.text().trim() }
        }

        val seriesParser = infoElement
            .select("td:contains(Series) ~ td a")
            .text()
            .trim()
            .ifEmpty { "Tidak Diketahui" }

        val alternativeTitle = document
            .selectFirst("section.flex div.flex-1 h2")
            ?.text()
            ?.trim()
            .takeIf { it?.isNotEmpty() == true }
            ?: "Tidak Diketahui"

        val manga = SManga.create()
        manga.description = if (titleElement.select("div.leading-6 > p:nth-child(1)").isEmpty()) {
            """
            Tidak ada deskripsi yang tersedia bosque

            Judul Alternatif : $alternativeTitle
            Grup             : $groupName
            Karakter         : $characterName
            Seri             : $seriesParser
            """.trimIndent()
        } else {
            val pb2Element = titleElement.selectFirst("div.leading-6")

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
        val genres = mutableListOf<String>()
        titleElement.select("div.my-4 a button").forEach { element ->
            val genre = element.text().trim()
            if (genre.isNotEmpty()) {
                genres.add(genre)
            }
        }
        manga.author = authorParser
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(
            infoElement.selectFirst("td:contains(Status) ~ td")!!.text(),
        )
        manga.thumbnail_url = document.selectFirst("section.flex > div.mx-auto img")?.attr("src")

        return manga
    }

    // Chapter Stuff

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        val numberText = element
            .selectFirst("td:first-child div")
            ?.text()
            ?.trim()
            ?: "0"

        chapter.chapter_number = getNumberFromString(numberText)

        chapter.name = element
            .selectFirst("td:nth-child(2) a span")
            ?.text()
            ?.trim()
            ?: "Chapter $numberText"

        val dateText = element
            .selectFirst("td:nth-child(2) p")
            ?.text()
            ?.trim()
            ?: ""

        chapter.date_upload = reconstructDate(dateText)

        chapter.setUrlWithoutDomain(
            element.selectFirst("td:nth-child(2) a")!!.attr("href"),
        )

        return chapter
    }

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder()
            .add("Referer", "$baseUrl/")
            .add("X-Requested-With", "XMLHttpRequest")

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,*/*")
            .set("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun chapterListSelector(): String = "#chapter-list table tbody tr"

    // More parser stuff
    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img[alt='Manga page']")
            .mapIndexed { index, img ->
                val url = img.attr("abs:src")
                Page(index, imageUrl = url)
            }
    }

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
