package eu.kanade.tachiyomi.extension.id.ulascomic

import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.get
import org.jsoup.nodes.Document

@Source
abstract class UlasComic : ZeistManga() {
    // Popular
    override val popularMangaSelector = "div.serieslist.pop.wpop ul li"
    override val popularMangaSelectorTitle = ".leftseries h2 a"
    override val popularMangaSelectorUrl = ".leftseries h2 a"

    // Latest
    override fun latestUpdatesUrl(page: Int, orderBy: String?) = super.latestUpdatesUrl(page, "updated")

    // Filters
    override val hasFilters = true
    override val hasLanguageFilter = false

    override fun getStatusList() = listOf(
        Status("All", ""),
        Status("Completed", "Completed"),
        Status("Ongoing", "Ongoing"),
    )

    override fun getTypeList() = listOf(
        Type("All", ""),
        Type("Manga", "Manga"),
        Type("Manhua", "Manhua"),
        Type("Manhwa", "Manhwa"),
    )

    // Details
    override val mangaDetailsSelector = ".animefull"
    override val mangaDetailsSelectorAuthor = "span[data-perfect-post='author']"
    override val mangaDetailsSelectorDescription = ".wd-full p"
    override val mangaDetailsSelectorGenres = ".mgen a"
    override val mangaDetailsSelectorStatus = ".imptdt:contains(Status) i"

    override val chapterCategory = "Manga Chapter"

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(config['chapterImage'])")
        if (script != null) {
            val imageUrls = IMAGE_REGEX.findAll(script.data().substringAfter("config['chapterImage']"))
                .map { it.groupValues[1] }
                .toList()

            if (imageUrls.isNotEmpty()) {
                return imageUrls.mapIndexed { i, url ->
                    Page(i, "", url)
                }
            }
        }

        return super.pageListParse(document)
    }

    companion object {
        private val IMAGE_REGEX = """"(https?://.*?)"""".toRegex()
    }
}
