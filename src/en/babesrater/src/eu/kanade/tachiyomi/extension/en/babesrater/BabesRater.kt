package eu.kanade.tachiyomi.extension.en.babesrater

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.escapeMarkdown
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

// A babe is an entry; her galleries and the pages of her picture lists are its chapters
@Source
abstract class BabesRater : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int): MangasPage = getHottest("girls")

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val list = filters.firstInstanceOrNull<ListFilter>()
        val letter = list?.letter
        val url = when {
            query.isNotBlank() -> "$baseUrl/search/searchbyname".toHttpUrl().newBuilder().addQueryParameter("name", query.trim()).build()
            letter != null -> "$baseUrl/list-all-babes".toHttpUrl().newBuilder().addPathSegment(letter).addPathSegment(page.toString()).build()
            list?.hottestPictures == true -> return getHottest("girlpictures")
            else -> return getPopularManga(page)
        }
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("li.picListAll").map { item ->
            val link = item.selectFirst(".picListAllName a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = item.selectFirst("img")?.absUrl("data-src")
            }
        }
        // Name search shows its first 50 results only, without pages
        val hasNextPage = document.selectFirst("""ul.pagination:not(.alphabet) li.active + li a:matchesOwn(^\d+$)""") != null
        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun getHottest(path: String): MangasPage {
        val document = client.get("$baseUrl/hottest/$path", Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".hottestLeft ol > li").map { item ->
            val link = item.selectFirst("h2 a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = item.selectFirst(".hottestPic img")?.absUrl("data-src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size != 3) return null
        val personPath = when (url.pathSegments[0]) {
            "person" -> url.encodedPath
            "infinite-scroll" -> url.newBuilder().setPathSegment(0, "person").build().encodedPath
            "gallery", "picture" -> client.get(url, Fetch.DOCUMENT).asJsoup()
                .selectFirst(".allLink a[href^=/person/]")?.absUrl("href")?.toHttpUrl()?.encodedPath ?: return null
            else -> return null
        }
        val manga = SManga.create().apply { this.url = personPath }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga).toHttpUrl()
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        val galleries = document.select("#galleriesContainer li.galleries a").map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                scanlator = "Galleries"
            }
        }
        // Galleries are listed oldest first, picture lists best rated first
        val chapterList = pictureChapters(document, mangaUrl, "nudePictures", "page", "Nude pictures") +
            pictureChapters(document, mangaUrl, "hrPictures", "pageHot", "HotnessRater pictures") +
            galleries
        // Names like "Nude pictures, page 2" and "Forfeit 1" would otherwise give several chapters one number
        chapterList.forEachIndexed { index, chapter -> chapter.chapter_number = index + 1f }
        return SMangaUpdate(parseDetails(document, manga), chapterList.reversed())
    }

    private fun parseDetails(document: Document, manga: SManga) = manga.apply {
        title = document.selectFirst("h1 [itemprop=name]")!!.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
        val votes = document.selectFirst("#bio [itemprop=ratingExplanation]")?.ownText()?.let { VOTES_REGEX.find(it) }?.value?.toIntOrNull()
        val stats = document.select("#bio .person-bio-div tr").mapNotNull { row ->
            val cells = row.select("td").takeIf { it.size == 2 } ?: return@mapNotNull null
            "- **${cells[0].text().escapeMarkdown()}:** ${cells[1].text().escapeMarkdown()}"
        }
        val aliases = document.selectFirst("#nudePictures p.alias")?.text()?.substringAfter(" is also known as ")?.split(", ").orEmpty()
        description = MarkdownDescription.Builder()
            .rating(document.selectFirst("#rating [itemprop=ratingValue]")?.text()?.toDoubleOrNull()?.takeIf { (votes ?: 0) > 0 }, votes = votes)
            .info(document.selectFirst("#nudePictures .numpics")?.text())
            .markdown(stats.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "**Bio:**\n"))
            .altTitles(aliases)
            .build()
    }

    private fun pictureChapters(document: Document, mangaUrl: HttpUrl, section: String, pageParameter: String, label: String): List<SChapter> {
        val container = document.selectFirst("#$section")!!
        if (container.selectFirst("li") == null) return emptyList()
        val pageCount = container.selectFirst(""".pagination div:matchesOwn(^Page \d+ of \d+$)""")!!.ownText().substringAfterLast(' ').toInt()
        return (1..pageCount).map { page ->
            SChapter.create().apply {
                setUrlWithoutDomain(mangaUrl.newBuilder().addQueryParameter(pageParameter, page.toString()).build().toString())
                name = "$label, page $page"
                scanlator = label
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter).toHttpUrl()
        val selector = when {
            url.pathSegments[0] == "gallery" -> ".galleryPics img"
            url.queryParameter("pageHot") != null -> "#hrPictures li.getMorePartialLiHot a > img"
            else -> "#nudePictures li.getMorePartialLi a > img"
        }
        val images = client.get(url, Fetch.DOCUMENT).asJsoup().select(selector)
        // Without the size parameters, the image server sends the original picture
        return images.mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src").toHttpUrl().newBuilder().query(null).build().toString())
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Text search ignores the list"),
        ListFilter(),
    )

    companion object {
        private val VOTES_REGEX = Regex("""\d+""")
    }
}
