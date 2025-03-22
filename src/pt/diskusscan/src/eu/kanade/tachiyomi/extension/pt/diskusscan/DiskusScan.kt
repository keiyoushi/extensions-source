package eu.kanade.tachiyomi.extension.pt.diskusscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class DiskusScan : MangaThemesia(
    "Diskus Scan",
    "https://diskusscan.online",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {

    // Changed their theme from Madara to MangaThemesia.
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val seriesAuthorSelector = ".infotable tr:contains(Autor) td:last-child"
    override val seriesDescriptionSelector = ".entry-content[itemprop=description] > *:not([class^=disku])"

    override fun String?.parseStatus() = when (orEmpty().trim().lowercase()) {
        "ativa" -> SManga.ONGOING
        "finalizada" -> SManga.COMPLETED
        "hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
}
