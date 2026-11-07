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
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// The site lists babes, not galleries: a babe is an entry and her galleries are its chapters
@Source
abstract class BabesRater : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/hottest/girls", Fetch.DOCUMENT).asJsoup()
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

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val letter = filters.firstInstanceOrNull<LetterFilter>()?.selected
        val url = when {
            query.isNotBlank() -> "$baseUrl/search/searchbyname".toHttpUrl().newBuilder().addQueryParameter("name", query.trim()).build()
            letter != null -> "$baseUrl/list-all-babes/$letter/$page".toHttpUrl()
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
        val hasNextPage = document.selectFirst("ul.pagination:not(.alphabet) li.active + li a:matchesOwn(^\\d+$)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 3) return null
        val personUrl = when (url.pathSegments[0]) {
            "person" -> url
            "gallery" -> client.get(url, Fetch.DOCUMENT).asJsoup()
                .selectFirst(".mainGalleryDiv a[href^=/person/]")?.absUrl("href")?.toHttpUrl() ?: return null
            else -> return null
        }
        return parseDetails(client.get(personUrl, Fetch.DOCUMENT).asJsoup()).apply { this.url = personUrl.encodedPath }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val galleries = document.select("#galleriesContainer li.galleries a").mapIndexed { index, link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                // Gallery names often end with a number that isn't a chapter number ("Forfeit 1")
                chapter_number = index + 1f
            }
        }
        // Galleries are listed oldest first. A babe without galleries gets her pictures as one chapter
        val chapterList = when {
            galleries.isNotEmpty() -> galleries.reversed()
            document.selectFirst(PICTURE_SELECTOR) != null -> listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Pictures"
                },
            )
            else -> emptyList()
        }
        return SMangaUpdate(parseDetails(document).apply { url = manga.url }, chapterList)
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1 [itemprop=name]")!!.text()
        thumbnail_url = document.selectFirst("#bio .person-bio-div img")?.absUrl("data-src")
        val votes = document.selectFirst("#bio [itemprop=ratingExplanation]")?.ownText()?.let { VOTES.find(it) }?.value?.toIntOrNull()
        val stats = document.select("#bio .person-bio-div tr").mapNotNull { row ->
            val cells = row.select("td").takeIf { it.size == 2 } ?: return@mapNotNull null
            cells[0].text() to cells[1].text()
        }
        description = MarkdownDescription.Builder()
            .rating(document.selectFirst("#rating [itemprop=ratingValue]")?.text()?.toDoubleOrNull()?.takeIf { votes != null && votes > 0 }, votes = votes)
            .info(document.selectFirst("#nudePictures .numpics")?.text())
            .apply { stats.forEach { (label, value) -> field(label, value) } }
            .build()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter).toHttpUrl()
        val images = if (url.pathSegments[0] == "gallery") {
            client.get(url, Fetch.DOCUMENT).asJsoup().select(".galleryPics img")
        } else {
            getPictures(url)
        }
        // Without the size parameters, the image server sends the original picture
        return images.mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("data-src").substringBefore('?')) }
    }

    private suspend fun getPictures(personUrl: HttpUrl): List<Element> {
        val first = client.get(personUrl, Fetch.DOCUMENT).asJsoup()
        val pageCount = first.selectFirst("#nudePictures .pagination div:matchesOwn(^Page \\d+ of \\d+$)")?.ownText()?.substringAfterLast(' ')?.toInt() ?: 1
        val others = (2..pageCount).map { page ->
            client.get(personUrl.newBuilder().addQueryParameter("page", page.toString()).build(), Fetch.DOCUMENT).asJsoup()
        }
        return (listOf(first) + others).flatMap { it.select(PICTURE_SELECTOR) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Text search ignores the list"),
        LetterFilter(),
    )

    companion object {
        // Each picture repeats in a <noscript>, outside the link
        private const val PICTURE_SELECTOR = "#nudePictures li.getMorePartialLi a > img"
        private val VOTES = Regex("""\d+""")
    }
}
