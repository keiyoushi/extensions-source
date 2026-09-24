package eu.kanade.tachiyomi.extension.en.hentai4free

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Hentai4Free : Madara() {
    override val mangaSubString = "hentai"

    override fun archiveSelector() = ".page-item-detail"

    override fun Element.postId() = classNames().firstOrNull {
        it.startsWith("post-")
    }?.removePrefix("post-")

    override fun chapterListSelector() = "section.h4f-oneshot-preview"
    override val chapterUrlSelector = ".h4f-preview-reader-btn"
    override val chapterNameSelector = ".h4f-preview-title small"

    override fun parsePages(document: Document): List<Page> {
        val pagesJson = document.selectFirst("#h4f-r2-data")?.data() ?: return emptyList()
        return pagesJson.parseAs<PageData>().images.mapIndexed { index, image ->
            Page(index, imageUrl = image.src)
        }
    }

    @Serializable
    private class PageData(
        val images: List<Image> = emptyList(),
    ) {
        @Serializable
        class Image(val src: String)
    }
}
