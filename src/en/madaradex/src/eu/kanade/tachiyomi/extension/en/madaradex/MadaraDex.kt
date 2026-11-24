package eu.kanade.tachiyomi.extension.en.madaradex

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class MadaraDex : MadaraNoAjax() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(MdxAuthInterceptor())

    override val mangaSubString = "all"

    override val supportsPostId = false
    override fun archiveSelector() = "article.mdxr-card"
    override val archiveUrlSelector = "h3 a"
    override fun nextPageSelector() = "a[rel=next]"

    override val mangaDetailsSelectorTitle = "#mdxr-series-title"
    override val mangaDetailsSelectorAuthor = "[data-detail-field='author'] a"
    override val mangaDetailsSelectorArtist = "[data-detail-field='artist'] a"
    override val mangaDetailsSelectorStatus = "[data-detail-field='status']"
    override val mangaDetailsSelectorDescription = "#mdxr-summary-text"
    override val mangaDetailsSelectorThumbnail = ".mdxr-series-cover img"
    override val mangaDetailsSelectorGenre = "[data-term-group='genres'] a.mdxr-detail-term"
    override val mangaDetailsSelectorTag = "[data-term-group='tags'] a.mdxr-detail-term"
    override val seriesTypeSelector = ".mdxr-type-badge"
    override val altNameSelector = "[data-alternative-preview] bdi"

    override val chapterMode = ChapterMode.MangaAjaxPaginated
    override val chapterNameSelector = ".mdxr-chapter-name"
    override val chapterDateSelector = "time.chapter-release-date"
    override val chapterAjaxPaginatedParam = "mdxr_chapter_page"

    override fun imageFromElement(element: Element) = super.imageFromElement(element)?.takeIf(String::isNotEmpty)
        ?: element.attr("data-mdx-protected-src")
}
