package eu.kanade.tachiyomi.extension.tr.orimanga

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

@Source
abstract class OriManga : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "manga-siralamasi"

    override val latestUrlSlug = "yakin-zamanda-guncellendi"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst("h2 a")!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        description = document.select("div#manga-description p")
            .map { it.text() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        document.selectFirst("span#comic-othername")?.text()?.let {
            description += "\n\nAlternatif Başlık: $it"
        }

        genre = document.select("div#genre-tags a[href*=/tur/]").joinToString { it.text() }

        author = document.infoValue("Yazar")
        artist = document.infoValue("Çizer")
    }

    // Info block is "Label: <a|span>value</a|span><br>" repeated, labels are bare text nodes
    private fun Document.infoValue(label: String): String? {
        val nodes = selectFirst("div.manga-info-details")?.childNodes() ?: return null
        val index = nodes.indexOfFirst { it is TextNode && it.text().trim() == "$label:" }
        if (index < 0) return null
        return nodes.drop(index + 1).firstOrNull { it is Element }?.let { (it as Element).text() }
    }
}
