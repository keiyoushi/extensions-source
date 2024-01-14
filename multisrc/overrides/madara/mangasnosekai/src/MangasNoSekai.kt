package eu.kanade.tachiyomi.extension.es.mangasnosekai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.CacheControl
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

    override val mangaSubString = "manganews"

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=views",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }

    override fun popularMangaSelector() = "div.page-listing-item > div.row > div"

    override val popularMangaUrlSelector = "a[href]"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
            }

            select("figcaption").first()?.let {
                manga.title = it.text()
            }

            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=latest",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }

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

    override val orderByFilterOptionsValues: Array<String> = arrayOf(
        "",
        "latest2",
        "alphabet",
        "rating",
        "trending",
        "views2",
        "new-manga",
    )
}
