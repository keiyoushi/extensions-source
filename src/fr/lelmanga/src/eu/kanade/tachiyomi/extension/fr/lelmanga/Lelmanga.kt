package eu.kanade.tachiyomi.extension.fr.lelmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Lelmanga : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    override val altNamePrefix = "Nom alternatif: "
    override val seriesAuthorSelector = ".imptdt:contains(Auteur) i"
    override val seriesArtistSelector = ".imptdt:contains(Artiste) i"
}
