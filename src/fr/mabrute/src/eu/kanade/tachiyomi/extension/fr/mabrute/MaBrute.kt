package eu.kanade.tachiyomi.extension.fr.mabrute

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MaBrute : Madara() {
    override val supportsPostId = false

    override fun archiveSelector() = ".mo2-archive-card"
    override fun searchCardSelector() = ".mo2-archive-card"
    override val archiveUrlSelector = ".mo2-release-title"

    override val mangaDetailsSelectorAuthor = ".mo2-hd-chip:has(.mo2-hd-chip-label:contains(Auteur)) strong"
    override val mangaDetailsSelectorArtist = ".mo2-hd-chip:has(.mo2-hd-chip-label:contains(Artiste)) strong"
    override val mangaDetailsSelectorStatus = ".mo2-hd-status"
    override val mangaDetailsSelectorThumbnail = ".mo2-hd-cover img"
    override val mangaDetailsSelectorGenre = ".mo2-hd-genres a"
    override val seriesTypeSelector = ".mo2-hd-type"
    override val altNameSelector = ".mo2-hd-chip.mo2-hd-alt em"

    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM, yyyy", Locale.FRENCH)
}
