package eu.kanade.tachiyomi.extension.id.doujindesu

import android.app.Application
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
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

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
        Order("All", ""),
        Order("A-Z", "title"),
        Order("Latest Update", "update"),
        Order("Latest Added", "latest"),
        Order("Popular", "popular"),
    )

    private val statusList = arrayOf(
        Status("All", ""),
        Status("Publishing", "Publishing"),
        Status("Finished", "Finished"),
    )

    private val categoryNames = arrayOf(
        Category("All", ""),
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
        Genre("Defloartion"),
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

    private class CategoryNames(categories: Array<Category>) :
        Filter.Select<Category>("Category", categories, 0)

    private class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Order", orders, 0)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)
    private class StatusList(statuses: Array<Status>) : Filter.Select<Status>("Status", statuses, 0)

    private fun basicInformationFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").let {
            manga.title = it.attr("title")
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

    override fun latestUpdatesSelector(): String = "#archives > div.entries > article"
    override fun popularMangaSelector(): String = "#archives > div.entries > article"
    override fun searchMangaSelector(): String = "#archives > div.entries > article"

    override fun popularMangaNextPageSelector(): String = "nav.pagination > ul > li.last > a"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Search & FIlter

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga/page/$page/".toHttpUrlOrNull()?.newBuilder()!!
            .addQueryParameter("title", query)
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
                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .let { list ->
                            if (list.isNotEmpty()) {
                                list.forEach { genre -> url.addQueryParameter("genre[]", genre.id) }
                            }
                        }
                }
                is StatusList -> {
                    val status = filter.values[filter.state]
                    url.addQueryParameter("statusx", status.key)
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaFromElement(element: Element): SManga =
        basicInformationFromElement(element)

    override fun getFilterList() = FilterList(
        Filter.Header("NB: Filter diabaikan jika memakai pencarian teks!"),
        Filter.Separator(),
        StatusList(statusList),
        CategoryNames(categoryNames),
        OrderBy(orderBy),
        GenreList(genreList()),
    )

    // Detail Parse

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("section.metadata").first()!!
        val manga = SManga.create()
        manga.description = when {
            document.select("section.metadata > div.pb-2 > p:nth-child(1)")
                .isEmpty() -> "Tidak ada deskripsi yang tersedia bosque"
            else -> document.select("section.metadata > div.pb-2 > p:nth-child(1)").first()!!.text()
        }
        val genres = mutableListOf<String>()
        infoElement.select("div.tags > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.author =
            document.select("section.metadata > table:nth-child(2) > tbody > tr.pages > td:contains(Author) + td:nth-child(2) > a")
                .joinToString { it.text() }
        manga.genre = infoElement.select("div.tags > a").joinToString { it.text() }
        manga.status = parseStatus(
            document.select("section.metadata > table:nth-child(2) > tbody > tr:nth-child(1) > td:nth-child(2) > a")
                .first()!!.text(),
        )
        manga.thumbnail_url = document.selectFirst("figure.thumbnail img")?.attr("src")
        manga.artist =
            document.select("section.metadata > table:nth-child(2) > tbody > tr.pages > td:contains(Character) + td:nth-child(2) > a")
                .joinToString { it.text() }

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
        throw UnsupportedOperationException("Not Used")

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
        private const val PREF_DOMAIN_TITLE = "Override BaseUrl"
        private const val PREF_DOMAIN_DEFAULT = "https://doujindesu.tv"
        private const val PREF_DOMAIN_SUMMARY = "Override default domain with a different one"
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
                Toast.makeText(screen.context, "Restart App to apply new setting.", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
        addRandomUAPreferenceToScreen(screen)
    }
}
