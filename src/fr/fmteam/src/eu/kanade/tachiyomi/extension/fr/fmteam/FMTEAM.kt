package eu.kanade.tachiyomi.extension.fr.fmteam

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source

@Source
abstract class FMTEAM : PizzaReader() {
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url.substringAfterLast('/')}"
}
