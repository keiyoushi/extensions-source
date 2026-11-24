package eu.kanade.tachiyomi.extension.fr.yureiscan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class YureiScan : KeiSource() {

    // The series page lists every series at once
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/manga_list.php", Fetch.DOCUMENT).asJsoup()
            .select("a.manga-card")
            .map { card ->
                SManga.create().apply {
                    setUrlWithoutDomain(card.absUrl("href"))
                    title = card.selectFirst("h2")!!.text()
                    thumbnail_url = card.selectFirst("img")?.absUrl("src")
                }
            }
        return MangasPage(mangas, false)
    }

    // The home page only shows the last few releases
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
            .select("a.sortie-item")
            .map { release ->
                SManga.create().apply {
                    url = "/manga.php?id=" + release.absUrl("href").toHttpUrl().queryParameter("id")!!
                    title = release.selectFirst(".sortie-title")!!.text()
                    thumbnail_url = release.selectFirst("img")?.absUrl("src")
                }
            }
            .distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            // Adult series only come with this flag, which the site sets once the visitor confirms their age
            .addQueryParameter("adult", "1")
            .build()
        val mangas = client.get(url, Fetch.XHR).parseAs<List<SearchResultDto>>().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val mangaPage = when (url.pathSegments.last()) {
            "manga.php", "manga" -> url
            "chapter.php", "chapter" -> {
                // A chapter link by slug only gives its series id in the og:url of its page
                val id = url.queryParameter("id")
                    ?: client.get(url, Fetch.DOCUMENT).asJsoup().pageId()
                    ?: return null
                "$baseUrl/manga.php?id=$id".toHttpUrl()
            }
            else -> return null
        }
        val document = client.get(mangaPage, Fetch.DOCUMENT).asJsoup()
        // A missing series answers 200 with a bare "Manga non trouvé."
        val id = document.pageId() ?: return null
        return document.toSManga().apply {
            this.url = "/manga.php?id=$id"
            initialized = true
        }
    }

    private fun Document.pageId() = selectFirst("meta[property=og:url]")?.attr("content")?.toHttpUrlOrNull()?.queryParameter("id")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga(), document.toSChapters())
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst("h1.manga-title")!!.text()
        thumbnail_url = selectFirst("img.manga-cover")?.absUrl("src")
        // Labels like "Statut :"
        val info = select(".manga-info-list li").associate { it.selectFirst("strong")!!.text().removeSuffix(" :") to it.ownText() }
        author = info["Auteur"]
        artist = info["Artiste"]
        genre = select(".genres-list .genre-tag").joinToString { it.text() }.ifEmpty { null }
        status = when (info["Statut"]?.lowercase()) {
            "en cours" -> SManga.ONGOING
            "terminé" -> SManga.COMPLETED
            "abandonné" -> SManga.CANCELLED
            "en pause" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        if (status == SManga.COMPLETED) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        description = MarkdownDescription.Builder()
            .rating(
                selectFirst(".rating-avg-number")?.text()?.toDoubleOrNull(),
                max = 5,
                votes = selectFirst(".rating-avg-count")?.text()?.substringBefore(" ")?.toIntOrNull(),
            )
            .info(info["Type"], info["Commencé le"]?.let { "Commencé le $it" })
            .description(selectFirst(".synopsis-section p"))
            .build()
    }

    private fun Document.toSChapters() = select("a.chapter-card").map { card ->
        SChapter.create().apply {
            setUrlWithoutDomain(card.absUrl("href"))
            name = card.selectFirst(".chapter-number")!!.text()
            date_upload = DATE_FORMAT.tryParseDate(card.selectFirst(".chapter-date")?.text(), ZONE)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#pagesContainer img.page-image")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("data-src").ifEmpty { img.absUrl("src") }) }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val ZONE = ZoneId.of("Europe/Paris")
    }
}
