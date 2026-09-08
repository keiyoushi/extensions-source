package eu.kanade.tachiyomi.extension.pt.traducoesdolipe

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document

@Source
abstract class TraducoesDoLipe : ZeistManga() {
    override val supportsLatest = false

    override val mangaCategory = "Projeto"
    override val chapterCategory = "Capítulo"

    override val hasFilters = true
    override val hasStatusFilter = false
    override val hasTypeFilter = false
    override val hasLanguageFilter = false
    override val hasGenreFilter = true

    override val mangaDetailsSelector = ".main-content"
    override val mangaDetailsSelectorAuthor = "li:contains(Autor) > span"
    override val mangaDetailsSelectorArtist = "li:contains(Ilustração) > span"
    override val mangaDetailsSelectorDescription = ".synopsis"
    override val mangaDetailsSelectorGenres = ".genres a"
    override val mangaDetailsSelectorStatus = ".status"

    override fun getChapterFeedUrl(doc: Document, mangaTitle: String): String {
        val label = PROJECT_NAME_REGEX.find(
            doc.selectFirst("script:containsData(catNameProject)")!!.html(),
        )!!.groupValues[1]
        return super.getChapterFeedUrl(doc, label)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.selectFirst(".chapter script")!!.html().let {
            PAGES_REGEX.find(it)!!.groups[1]!!.value.parseAs<List<String>>()
        }
        return pages.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    companion object {
        val PROJECT_NAME_REGEX = """=\s+?\('([^']+)""".toRegex()
        val PAGES_REGEX = """=(\[[^]]+])""".toRegex()
    }
}
