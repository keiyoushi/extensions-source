package eu.kanade.tachiyomi.extension.fr.hotmanga

import eu.kanade.tachiyomi.multisrc.komiko.Komiko
import keiyoushi.annotation.Source

@Source
abstract class HotManga : Komiko() {
    override val apiUrl = "https://api.hot-manga.fr/v1"

    override val productPath = "produit"

    // Every volume has a preview, ebook or not
    override val ebookOnly = false
}
