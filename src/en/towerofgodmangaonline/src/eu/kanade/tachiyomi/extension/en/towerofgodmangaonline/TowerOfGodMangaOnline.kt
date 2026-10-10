package eu.kanade.tachiyomi.extension.en.towerofgodmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class TowerOfGodMangaOnline : MangaCatalog() {

    override val sourceList = listOf(
        Pair("Tower of God", baseUrl),
    )

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        url = baseUrl
        title = "Tower of God"
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        description = document.selectFirst("meta[property='og:description']")?.attr("content")
    }

    override fun chapterListSelector(): String = "a[href*='/manga/tower-of-god-chapter-']"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val href = element.attr("abs:href")
        val chapterNum = Regex("chapter-(\\d+)").find(href)?.groupValues?.get(1) ?: "0"
        name = "Chapter $chapterNum"
        chapter_number = chapterNum.toFloatOrNull() ?: 0f
        url = href
    }

    override fun pageListParse(document: Document): List<Page> = document.select("img[src*='blogger.googleusercontent.com'], img[src*='bp.blogspot.com']")
        .map { it.attr("abs:src") }
        .filter { it.isNotBlank() }
        .distinct()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }
}
