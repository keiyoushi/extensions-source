package eu.kanade.tachiyomi.extension.es.tecnoscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class TecnoScan : MangaThemesia(
    "Tecno Scan",
    "https://tecnoscann.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    // Site moved from Madara to MangaThemesia
    override val versionId = 3

    override val seriesStatusSelector = ".tsinfo .imptdt:contains(Estado) i"
}
