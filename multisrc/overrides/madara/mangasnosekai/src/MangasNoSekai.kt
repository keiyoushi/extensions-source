package eu.kanade.tachiyomi.extension.es.mangasnosekai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangasNoSekai : Madara(
    "Mangas No Sekai",
    "https://mangasnosekai.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2, 1)
        .build()

    override val useNewChapterEndpoint = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/${searchPage(page)}?s=&post_type=wp-manga&m_orderby=views", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/${searchPage(page)}?s=&post_type=wp-manga&m_orderby=latest", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun searchPage(page: Int): String {
        return if (page > 1) "page/$page/" else ""
    }

    override fun searchMangaNextPageSelector() = "nav.navigation a.next"

    override val mangaDetailsSelectorTitle = "div.summary-content h1.titleManga"
    override val mangaDetailsSelectorThumbnail = "div.tab-summary img.img-responsive"
    override val mangaDetailsSelectorDescription = "div.summary-content div.artist-content"
    override val mangaDetailsSelectorStatus = "div.summary-content ul.general-List li:has(span:contains(Estado))"
    override val mangaDetailsSelectorAuthor = "div.summary-content ul.general-List li:has(span:contains(Autor))"
    override val mangaDetailsSelectorArtist = "div.summary-content ul.general-List li:has(span:contains(Dibujante))"
    override val seriesTypeSelector = "div.summary-content ul.general-List li:has(span:contains(Tipo))"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        with(document) {
            selectFirst(mangaDetailsSelectorTitle)?.let {
                manga.title = it.ownText()
            }
            selectFirst(mangaDetailsSelectorAuthor)?.ownText()?.let {
                manga.author = it
            }
            selectFirst(mangaDetailsSelectorArtist)?.ownText()?.let {
                manga.artist = it
            }
            select(mangaDetailsSelectorDescription).let {
                manga.description = it.text()
            }
            select(mangaDetailsSelectorThumbnail).first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
            selectFirst(mangaDetailsSelectorStatus)?.ownText()?.let {
                manga.status = when (it) {
                    in completedStatusList -> SManga.COMPLETED
                    in ongoingStatusList -> SManga.ONGOING
                    in hiatusStatusList -> SManga.ON_HIATUS
                    in canceledStatusList -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
            val genres = select(mangaDetailsSelectorGenre)
                .map { element -> element.text().lowercase(Locale.ROOT) }
                .toMutableSet()

            // add manga/manhwa/manhua thinggy to genre
            document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
                if (it.isEmpty().not() && it.notUpdating() && it != "-" && genres.contains(it).not()) {
                    genres.add(it.lowercase(Locale.ROOT))
                }
            }

            manga.genre = genres.toList().joinToString(", ") { genre ->
                genre.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(
                            Locale.ROOT,
                        )
                    } else {
                        it.toString()
                    }
                }
            }

            // add alternative name to manga description
            document.select(altNameSelector).firstOrNull()?.ownText()?.let {
                if (it.isBlank().not() && it.notUpdating()) {
                    manga.description = when {
                        manga.description.isNullOrBlank() -> altName + it
                        else -> manga.description + "\n\n$altName" + it
                    }
                }
            }
        }

        return manga
    }
}
