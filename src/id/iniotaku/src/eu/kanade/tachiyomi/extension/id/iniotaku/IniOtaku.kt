package eu.kanade.tachiyomi.extension.id.iniotaku

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class IniOtaku : ZeistManga() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrl.toHttpUrl().host }

    override val popularMangaSelector = "div.PopularPosts div.grid > article"
    override val popularMangaSelectorTitle = "h3 > a"
    override val popularMangaSelectorUrl = "h3 > a"

    override fun parsePopularManga(response: Response) = super.parsePopularManga(response).withCovers()

    override fun parseSearchManga(response: Response) = super.parseSearchManga(response).withCovers()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val title = document.selectFirst("main h1[itemprop=name]")?.textOrNull()
        if (title != null) {
            return mangaDetailsParse(document).apply {
                this.url = url.encodedPath
                this.title = title
            }
        }
        // Chapter posts link to their series label.
        val label = document.selectFirst("header h1 > a[href*=/search/label/]")
            ?.absUrl("href")?.toHttpUrl()?.pathSegments?.last() ?: return null
        val feedUrl = apiUrl()
            .addQueryParameter("q", "label:\"$label\" -label:$chapterCategory")
            .build()
        return client.get(feedUrl, Fetch.XHR).parseAs<ZeistMangaDto>()
            .feed?.entry?.singleOrNull()?.toSManga(baseUrl)?.apply { thumbnail_url = thumbnail_url?.coverSize() }
    }

    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorGenres = "aside dl a[rel=tag]"
    override val mangaDetailsSelectorInfo = "#extra-info dl"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        thumbnail_url = thumbnail_url?.coverSize()
        author = author?.removeSuffix(" (Story)")
        artist = artist?.removeSuffix(" (Art)")
        description = MarkdownDescription.Builder()
            .rating(document.selectFirst("[itemprop=ratingValue]")?.text()?.removePrefix("Score ")?.toDoubleOrNull())
            .info(document.extraInfo("Country"), document.extraInfo("Published"))
            .separator()
            .description(document.selectFirst(mangaDetailsSelectorDescription))
            .build()
    }

    override val pageListSelector = "#reader"

    override fun pageListParse(document: Document) = super.pageListParse(document).map {
        Page(it.index, imageUrl = it.imageUrl!!.fullSize())
    }

    override val hasFilters = true
    override val hasLanguageFilter = false

    override fun getStatusList() = listOf(
        Status(intl["all"], ""),
        Status(intl["status_ongoing"], "Ongoing"),
        Status(intl["status_completed"], "Completed"),
    )

    override fun getTypeList() = listOf(
        Type(intl["all"], ""),
        Type(intl["type_manga"], "Manga"),
        Type(intl["type_manhwa"], "Manhwa"),
    )

    override fun getGenreList() = listOf(
        "Action", "Adaptation", "Adventure", "Bloody", "Comedy", "Crime", "Demons", "Drama",
        "Ecchi", "Fantasy", "Harem", "Historical", "Isekai", "Josei", "Magic", "Mature",
        "Monster Girl", "Monsters", "Music", "Mystery", "NSFW", "One-Shot", "Psychological", "Romance",
        "Royal family", "School Life", "Sci-Fi", "Seinen", "Shounen", "Slice of Life", "Smut",
        "Supernatural", "Tragedy", "Web Comic",
    ).map { Genre(it, it) }

    private fun MangasPage.withCovers() = apply {
        mangas.forEach { it.thumbnail_url = it.thumbnail_url?.coverSize() }
    }

    // Blogger image size segment: s0 is the original upload, w600 the theme's listing cover.
    private fun String.fullSize() = replace(IMAGE_SIZE_REGEX, "/s0/$1")

    private fun String.coverSize() = replace(IMAGE_SIZE_REGEX, "/w600/$1")

    private fun Document.extraInfo(name: String) = selectFirst("#extra-info dl:has(dt:contains($name)) dd")?.text()

    companion object {
        private val IMAGE_SIZE_REGEX = Regex("""/[swh]\d[\w-]*/([^/]+)$""")
    }
}
