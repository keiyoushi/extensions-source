package eu.kanade.tachiyomi.extension.pt.hikariscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class HikariScan : MangaThemesia(
    "Hikari Scan",
    "https://hikariscan.org",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {
    // =========================== Manga Details ============================
    override val altNamePrefix = "Títulos alternativos: "
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(autor) i"
}
