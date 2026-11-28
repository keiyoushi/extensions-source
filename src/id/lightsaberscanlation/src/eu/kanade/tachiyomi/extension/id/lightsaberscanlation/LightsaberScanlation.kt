package eu.kanade.tachiyomi.extension.id.lightsaberscanlation

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.toMarkdown
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class LightsaberScanlation : ZeistManga() {
    // Some posts close the reader label early with stray </div>s; every page image carries this attribute
    override val pageListSelector = "img[data-original-height]"

    // The synopsis can run over several paragraphs
    override val mangaDetailsSelectorDescription = "h3:contains(Sinopsis) ~ p"

    override fun parsePopularManga(response: Response) = super.parsePopularManga(response).apply {
        mangas.forEach { it.thumbnail_url = it.thumbnail_url?.replace(COVER_SIZE_REGEX, "$1w600") }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !POST_PATH_REGEX.matches(url.encodedPath)) return null
        val post = feedEntry(apiUrl().addQueryParameter("path", url.encodedPath)) ?: return null
        val labels = post.category.orEmpty().map { it.term }
        if (chapterCategory !in labels) return post.toSManga(baseUrl)
        // A chapter post's other labels include its series
        val seriesLabels = labels.filter { it != chapterCategory }.joinToString(" ") { "label:\"$it\"" }
        return feedEntry(apiUrl().addQueryParameter("q", "{$seriesLabels} -label:\"$chapterCategory\""))?.toSManga(baseUrl)
    }

    private suspend fun feedEntry(url: HttpUrl.Builder) = client.get(url.build(), Fetch.XHR).parseAs<ZeistMangaDto>().feed?.entry?.singleOrNull()

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        val types = document.select(".y6x11p:contains(Type) a").map { it.text() }
        genre = (types + genre.orEmpty().split(", ")).filter { it.isNotEmpty() }.distinct().joinToString()

        val header = document.selectFirst("header > p")?.textNodes().orEmpty()
            .flatMap { it.wholeText.lines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        author = AUTHOR_REGEX.find(header)?.groupValues?.get(1)
        val altTitles = header.replace(AUTHOR_REGEX, "").lines()
            .filter { it.isNotEmpty() }
            .flatMap { it.substringAfter(":").split(";") }
            .map { it.trim() }

        description = MarkdownDescription.Builder()
            .rating(document.selectFirst("span[data-icon*=star] ~ span:not([data-icon])")?.text()?.toDoubleOrNull()?.takeIf { it > 0 })
            .markdown(document.select(mangaDetailsSelectorDescription).joinToString("\n\n") { it.toMarkdown() })
            .altTitles(altTitles, "Judul Alternatif")
            .build()
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
        Type(intl["type_manhua"], "Manhua"),
        Type(intl["type_manhwa"], "Manhwa"),
        Type(intl["type_doujinshi"], "Doujinshi"),
    )

    override fun getGenreList() = listOf(
        "Action", "Adult", "Adventure", "BDSM",
        "Comedy", "Drama", "Fantasy", "Full Color",
        "Hentai", "historical", "Martial Arts", "Mature",
        "Mystery", "One Shot", "Psychological", "Romance",
        "School Life", "Shounen Ai", "Slice of Life", "Smut",
        "Supernatural", "Vampires", "Violence", "Webtoon",
        "Yaoi",
    ).map { Genre(it, it) }

    companion object {
        private val POST_PATH_REGEX = Regex("""/\d{4}/\d{2}/[^/]+\.html""")

        // Author line under the title: "Pengarang : X", "Authors: X", "Author(s)" with X on the next line
        private val AUTHOR_REGEX = Regex("""^(?:Pengarang|Authors?(?:\(s\))?)\s*:?\s*(.+)$""", RegexOption.MULTILINE)

        // Blogger size segment ("/s450/" or "=s320") of the popular widget's covers
        private val COVER_SIZE_REGEX = Regex("""([/=])s\d+(?=/[^/]+$|$)""")
    }
}
