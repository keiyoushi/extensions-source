package eu.kanade.tachiyomi.extension.id.doujindesu

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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
        Status("Berlanjut", "Publishing"),
        Status("Selesai", "Finished"),
    )

    private val categoryNames = arrayOf(
        Category("Semua", ""),
        Category("Doujinshi", "Doujinshi"),
        Category("Manga", "Manga"),
        Category("Manhwa", "Manhwa"),
    )

    private fun genreList() = listOf(
        Genre("Age Progression"),
        Genre("Age Regression"),
        Genre("Ahegao"),
        Genre("All The Way Through"),
        Genre("Amputee"),
        Genre("Anal"),
        Genre("Anorexia"),
        Genre("Apron"),
        Genre("Artist CG"),
        Genre("Aunt"),
        Genre("Bald"),
        Genre("Bestiality"),
        Genre("Big Ass"),
        Genre("Big Breast"),
        Genre("Big Penis"),
        Genre("Bike Shorts"),
        Genre("Bikini"),
        Genre("Birth"),
        Genre("Bisexual"),
        Genre("Blackmail"),
        Genre("Blindfold"),
        Genre("Bloomers"),
        Genre("Blowjob"),
        Genre("Body Swap"),
        Genre("Bodysuit"),
        Genre("Bondage"),
        Genre("Bowjob"),
        Genre("Business Suit"),
        Genre("Cheating"),
        Genre("Collar"),
        Genre("Collor"),
        Genre("Condom"),
        Genre("Cousin"),
        Genre("Crossdressing"),
        Genre("Cunnilingus"),
        Genre("Dark Skin"),
        Genre("Daughter"),
        Genre("Defloration"),
        Genre("Demon"),
        Genre("Demon Girl"),
        Genre("Dick Growth"),
        Genre("DILF"),
        Genre("Double Penetration"),
        Genre("Drugs"),
        Genre("Drunk"),
        Genre("Elf"),
        Genre("Emotionless Sex"),
        Genre("Exhibitionism"),
        Genre("Eyepatch"),
        Genre("Females Only"),
        Genre("Femdom"),
        Genre("Filming"),
        Genre("Fingering"),
        Genre("Footjob"),
        Genre("Full Color"),
        Genre("Furry"),
        Genre("Futanari"),
        Genre("Garter Belt"),
        Genre("Gender Bender"),
        Genre("Ghost"),
        Genre("Glasses"),
        Genre("Gore"),
        Genre("Group"),
        Genre("Guro"),
        Genre("Gyaru"),
        Genre("Hairy"),
        Genre("Handjob"),
        Genre("Harem"),
        Genre("Horns"),
        Genre("Huge Breast"),
        Genre("Huge Penis"),
        Genre("Humiliation"),
        Genre("Impregnation"),
        Genre("Incest"),
        Genre("Inflation"),
        Genre("Insect"),
        Genre("Inseki"),
        Genre("Inverted Nipples"),
        Genre("Invisible"),
        Genre("Kemomimi"),
        Genre("Kimono"),
        Genre("Lactation"),
        Genre("Leotard"),
        Genre("Lingerie"),
        Genre("Loli"),
        Genre("Lolipai"),
        Genre("Maid"),
        Genre("Males"),
        Genre("Males Only"),
        Genre("Masturbation"),
        Genre("Miko"),
        Genre("MILF"),
        Genre("Mind Break"),
        Genre("Mind Control"),
        Genre("Minigirl"),
        Genre("Miniguy"),
        Genre("Monster"),
        Genre("Monster Girl"),
        Genre("Mother"),
        Genre("Multi-work Series"),
        Genre("Muscle"),
        Genre("Nakadashi"),
        Genre("Necrophilia"),
        Genre("Netorare"),
        Genre("Niece"),
        Genre("Nipple Fuck"),
        Genre("Nurse"),
        Genre("Old Man"),
        Genre("Only"),
        Genre("Oyakodon"),
        Genre("Paizuri"),
        Genre("Pantyhose"),
        Genre("Possession"),
        Genre("Pregnant"),
        Genre("Prostitution"),
        Genre("Rape"),
        Genre("Rimjob"),
        Genre("Scat"),
        Genre("School Uniform"),
        Genre("Sex Toys"),
        Genre("Shemale"),
        Genre("Shota"),
        Genre("Sister"),
        Genre("Sleeping"),
        Genre("Slime"),
        Genre("Small Breast"),
        Genre("Snuff"),
        Genre("Sole Female"),
        Genre("Sole Male"),
        Genre("Stocking"),
        Genre("Story Arc"),
        Genre("Sumata"),
        Genre("Sweating"),
        Genre("Swimsuit"),
        Genre("Tanlines"),
        Genre("Teacher"),
        Genre("Tentacles"),
        Genre("Tomboy"),
        Genre("Tomgirl"),
        Genre("Torture"),
        Genre("Twins"),
        Genre("Twintails"),
        Genre("Uncensored"),
        Genre("Unusual Pupils"),
        Genre("Virginity"),
        Genre("Webtoon"),
        Genre("Widow"),
        Genre("X-Ray"),
        Genre("Yandere"),
        Genre("Yaoi"),
        Genre("Yuri"),
    )

    private class AuthorFilter : Filter.Text("Author")
    private class GroupFilter : Filter.Text("Group")
    private class SeriesFilter : Filter.Text("Series")
    private class CharacterFilter : Filter.Text("Karakter")
    private class CategoryNames(categories: Array<Category>) : Filter.Select<Category>("Kategori", categories, 0)
    private class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Urutkan", orders, 0)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)
    private class StatusList(statuses: Array<Status>) : Filter.Select<Status>("Status", statuses, 0)

    private fun basicInformationFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").let {
            manga.title = element.selectFirst("h3.title")!!.text()
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        element.select("a > figure.thumbnail > img").first()?.let {
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
        return GET("$baseUrl/manga/page/$page/?title=&author=&character=&statusx=&typex=&order=popular", headers)
    }

    // Latest

    override fun latestUpdatesFromElement(element: Element): SManga =
        basicInformationFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page/?title=&author=&character=&statusx=&typex=&order=update", headers)
    }

    // Element Selectors

    override fun popularMangaSelector(): String = "#archives > div.entries > article"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector(): String = "nav.pagination > ul > li.last > a"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Search & FIlter

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Anything else filter handling
        val url = "$baseUrl/manga/page/$page/".toHttpUrl().newBuilder()
        url.addQueryParameter("title", query.ifBlank { "" })

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryNames -> {
                    val category = filter.values[filter.state]
                    url.addQueryParameter("typex", category.key)
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
                    url.addQueryParameter("statusx", status.key)
                }
                else -> {}
            }
        }

        val author = filters.firstInstanceOrNull<AuthorFilter>()?.state?.trim()
        val group = filters.firstInstanceOrNull<GroupFilter>()?.state?.trim()
        val series = filters.firstInstanceOrNull<SeriesFilter>()?.state?.trim()

        // Author filter handling
        if (query.isBlank()) {
            if (!author.isNullOrBlank()) {
                val slug = author.toMultiSlug()
                if (slug.isNotBlank()) {
                    val authorUrl = if (page == 1) {
                        "$baseUrl/author/$slug/"
                    } else {
                        "$baseUrl/author/$slug/page/$page/"
                    }
                    return GET(authorUrl, headers)
                }
            }

            // Group filter handling
            if (!group.isNullOrBlank()) {
                val slug = group.toMultiSlug()
                if (slug.isNotBlank()) {
                    val groupUrl = if (page == 1) {
                        "$baseUrl/group/$slug/"
                    } else {
                        "$baseUrl/group/$slug/page/$page/"
                    }
                    return GET(groupUrl, headers)
                }
            }

            // Series filter handling
            if (!series.isNullOrBlank()) {
                val slug = series.toMultiSlug()
                if (slug.isNotBlank()) {
                    val seriesUrl = if (page == 1) {
                        "$baseUrl/series/$slug/"
                    } else {
                        "$baseUrl/series/$slug/page/$page/"
                    }
                    return GET(seriesUrl, headers)
                }
            }
        }
        return GET(url.build(), headers)
    }

    private val nonAlphaNumSpaceDashRegex = Regex("[^a-z0-9\\s-]")
    private val multiSpaceRegex = Regex("\\s+")

    private fun String.toMultiSlug(): String {
        return this
            .trim()
            .lowercase()
            .replace(nonAlphaNumSpaceDashRegex, "")
            .replace(multiSpaceRegex, "-")
    }

    override fun searchMangaFromElement(element: Element): SManga =
        basicInformationFromElement(element)

    override fun getFilterList() = FilterList(
        Filter.Header("NB: Filter bisa digabungkan dengan memakai pencarian teks selain Author, Group dan Series!"),
        Filter.Separator(),
        Filter.Header("NB: Gunakan ini untuk filter per Author, Group dan Series saja, tidak bisa digabungkan dengan memakai pencarian teks dan filter lainnya!"),
        AuthorFilter(),
        GroupFilter(),
        SeriesFilter(),
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

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst("section.metadata")!!
        val authorName = if (infoElement.select("td:contains(Author) ~ td").isEmpty()) {
            null
        } else {
            infoElement.select("td:contains(Author) ~ td").joinToString { it.text() }
        }
        val groupName = if (infoElement.select("td:contains(Group) ~ td").isEmpty()) {
            "Tidak Diketahui"
        } else {
            infoElement.select("td:contains(Group) ~ td").joinToString { it.text() }
        }
        val authorParser = if (authorName.isNullOrEmpty()) {
            groupName?.takeIf { !it.isNullOrEmpty() && it != "Tidak Diketahui" }
        } else {
            authorName
        }
        val characterName = if (infoElement.select("td:contains(Character) ~ td").isEmpty()) {
            "Tidak Diketahui"
        } else {
            infoElement.select("td:contains(Character) ~ td").joinToString { it.text() }
        }
        val seriesParser = if (infoElement.select("td:contains(Series) ~ td").text() == "Manhwa") {
            infoElement.select("td:contains(Serialization) ~ td").text()
        } else {
            infoElement.select("td:contains(Series) ~ td").text()
        }
        val alternativeTitle = if (infoElement.select("h1.title > span.alter").isEmpty()) {
            "Tidak Diketahui"
        } else {
            infoElement.select("h1.title > span.alter").joinToString { it.text() }
        }
        val manga = SManga.create()
        manga.description = if (infoElement.select("div.pb-2 > p:nth-child(1)").isEmpty()) {
            """
            Tidak ada deskripsi yang tersedia bosque

            Judul Alternatif : $alternativeTitle
            Grup             : $groupName
            Karakter         : $characterName
            Seri             : $seriesParser
            """.trimIndent()
        } else {
            val pb2Element = infoElement.selectFirst("div.pb-2")

            val showDescription = pb2Element?.let { element ->
                val paragraphs = element.select("p")
                val firstText = paragraphs.firstOrNull()?.text()?.trim()?.lowercase()

                // CASE 1: Gabungan chapter dalam satu paragraf
                val mergedChapterElement = element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))").firstOrNull {
                    chapterListRegex.containsMatchIn(it.html())
                }

                if (mergedChapterElement != null) {
                    val chapterList = mergedChapterElement.html()
                        .split("<br>")
                        .drop(1)
                        .map { it.replace(htmlTagRegex, "").trim() }
                        .filter { it.isNotEmpty() }

                    return@let "Daftar Chapter:\n" + chapterList.joinToString(" | ")
                }

                // CASE 2: Dua paragraf: p[0] = "Sinopsis:", p[1] = daftar chapter
                if (
                    firstText == "sinopsis:" &&
                    paragraphs.size > 1 &&
                    chapterListRegex.containsMatchIn(paragraphs[1].html())
                ) {
                    val chapterList = paragraphs[1].html()
                        .split("<br>")
                        .map { it.replace(htmlTagRegex, "").trim() }
                        .filter { it.isNotEmpty() }

                    return@let "Daftar Chapter:\n" + chapterList.joinToString(" | ")
                }

                // CASE 3: Sinopsis biasa pakai <strong>Sinopsis:</strong> di p awal
                val sinopsisPara = element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))")
                if (sinopsisPara.isNotEmpty()) {
                    val sinopsisStart = sinopsisPara.first()!!
                    val htmlSplit = sinopsisStart.html().split("<br>")

                    val startText = htmlSplit.getOrNull(1)?.replace(htmlTagRegex, "")?.trim().orEmpty()

                    val sinopsisTexts = buildList {
                        if (startText.isNotEmpty()) add(startText)

                        val allP = element.select("p")
                        val startIndex = allP.indexOf(sinopsisStart)

                        for (i in startIndex + 1 until allP.size) {
                            val content = allP[i].text().trim()
                            if (!content.lowercase().startsWith("download")) {
                                add(content)
                            } else {
                                break
                            }
                        }
                    }
                    return@let "Sinopsis:\n" + sinopsisTexts.joinToString("\n\n")
                }

                // CASE 4: Satu paragraf saja dengan <strong> dan <br>
                if (
                    paragraphs.size == 1 &&
                    element.select("p:has(strong:matchesOwn(^\\s*Sinopsis\\s*:))").isNotEmpty()
                ) {
                    val para = paragraphs[0]
                    val htmlSplit = para.html().split("<br>")

                    val content = htmlSplit.getOrNull(1)?.replace(htmlTagRegex, "")?.trim().orEmpty()

                    return@let "Sinopsis:\n$content"
                }

                // CASE 5: Fallback
                if (firstText == "sinopsis:") {
                    val sinopsisLines = paragraphs.drop(1)
                        .map { it.text().trim() }
                        .filter { !it.lowercase().startsWith("download") }

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
        infoElement.select("div.tags > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.author = authorParser
        manga.genre = infoElement.select("div.tags > a").joinToString { it.text() }
        manga.status = parseStatus(
            infoElement.selectFirst("td:contains(Status) ~ td")!!.text(),
        )
        manga.thumbnail_url = document.selectFirst("figure.thumbnail img")?.attr("src")

        return manga
    }

    // Chapter Stuff

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val eps = element.selectFirst("div.epsright chapter")?.text()
        chapter.chapter_number = getNumberFromString(eps)
        chapter.date_upload = reconstructDate(element.select("div.epsleft > span.date").text())
        chapter.name = "Chapter $eps"
        chapter.setUrlWithoutDomain(element.select("div.epsleft > span.lchx > a").attr("href"))

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

    override fun chapterListSelector(): String = "#chapter_list li"

    // More parser stuff
    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        val id = document.select("#reader").attr("data-id")
        val body = FormBody.Builder()
            .add("id", id)
            .build()
        return client.newCall(POST("$baseUrl/themes/ajax/ch.php", headers, body)).execute()
            .asJsoup().select("img").mapIndexed { i, element ->
                Page(i, "", element.attr("src"))
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
