package eu.kanade.tachiyomi.extension.en.coffeemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class CoffeeManga : Madara() {

    // The site was rebuilt with a custom child theme. The standard Madara
    // markup only survives in the reader; listings, details and chapters
    // use custom markup ("acard" grid, "htitle" hero, embedded chapter JSON).

    override val fetchGenres = false

    override fun popularMangaSelector() = "a.acard"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.attr("title").ifEmpty { element.selectFirst(".ac-t")!!.text() }
        element.selectFirst(".ac-img img")?.let { thumbnail_url = imageFromElement(it) }
    }

    override fun searchMangaSelector() = "a.acard"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun getFilterList() = FilterList(
        Filter.Header("Only text search and sorting are supported by the site"),
        Filter.Separator(),
        OrderByFilter("Order by", orderByFilterOptions.toList()),
    )

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.htitle")!!.text()
        document.selectFirst(".hposter__card img")?.let { thumbnail_url = imageFromElement(it) }
        description = buildString {
            document.selectFirst("#syn")?.text()?.takeIf { it.isNotEmpty() }?.let(::append)
            document.selectFirst(".sir--wide .v")?.ownText()?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative names: ").append(it)
            }
        }
        status = when (document.selectFirst(".htag--status")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "on hold" -> SManga.ON_HIATUS
            "canceled", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = (
            document.select(".hchips--genres a.chip") +
                document.select(".hchips--tags a.chip")
            )
            .mapTo(LinkedHashSet()) { it.text().replaceFirstChar(Char::uppercase) }
            .joinToString()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.asJsoup()
            .selectFirst("script#mk-chapters-data")?.data()
            ?: throw Exception("Chapter list not found")
        return data.parseAs<ChaptersData>().items.map { item ->
            SChapter.create().apply {
                setUrlWithoutDomain(item.url)
                name = item.name
                date_upload = parseChapterDate(item.ago)
            }
        }
    }
}

@Serializable
private class ChaptersData(val items: List<ChapterItem> = emptyList())

@Serializable
private class ChapterItem(
    val name: String,
    val url: String,
    val ago: String? = null,
)
