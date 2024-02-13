package eu.kanade.tachiyomi.extension.tr.mangaefendisi

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class MangaEfendisi : MangaThemesia(
    "Manga Efendisi",
    "https://mangaefendisi.net",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override val seriesAuthorSelector = ".fmed b:contains(Yazar) + span"
    override val seriesArtistSelector = ".fmed b:contains(Çizer) + span"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Tip) a"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(Durum) i"

    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("güncel", "devam ediyor").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("tamamlandı", ignoreCase = true) -> SManga.COMPLETED
        this.contains("bırakıldı", ignoreCase = true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}
