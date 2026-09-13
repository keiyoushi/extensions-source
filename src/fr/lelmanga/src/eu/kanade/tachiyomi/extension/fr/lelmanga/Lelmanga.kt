package eu.kanade.tachiyomi.extension.fr.lelmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Lelmanga : MangaThemesia() {
    override val altNamePrefix = "Nom alternatif: "
    override val seriesAuthorSelector = ".imptdt:contains(Auteur) i"
    override val seriesArtistSelector = ".imptdt:contains(Artiste) i"
}
