package eu.kanade.tachiyomi.extension.pt.leitordemangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class LeitorDeMangas :
    Madara(
        "Leitor de Mangas",
        "https://leitordemangas.com",
        "pt-BR",
        dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun searchMangaSelector() = "#loop-content > .page-listing-item"

    // Chapter protection does not affect the paged view
    override val chapterUrlSuffix = "?style=paged"
    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        val scriptData = document.selectFirst("#chapter_preloaded_images")!!.data()
        val imageUrls = scriptData
            .substringAfter("var chapter_preloaded_images = [")
            .substringBefore("]")
            .let { "[$it]" }
            .parseAs<List<String>>()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }
}
