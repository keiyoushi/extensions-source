package eu.kanade.tachiyomi.extension.en.webtoonxyz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class WebtoonXYZ : Madara("WebtoonXYZ", "https://www.webtoon.xyz", "en", SimpleDateFormat("dd MMMM yyyy", Locale.US)) {
    override val mangaSubString = "read"
    override val sendViewCount = false

    private val thumbnailOriginalUrlRegex = Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$")

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = super.popularMangaFromElement(element)
        manga.thumbnail_url = manga.thumbnail_url?.replace(thumbnailOriginalUrlRegex, "$1")
        return manga
    }
}
