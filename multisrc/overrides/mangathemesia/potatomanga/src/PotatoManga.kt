package eu.kanade.tachiyomi.extension.ar.potatomanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class PotatoManga : MangaThemesia(
    "PotatoManga",
    "https://potatomanga.xyz",
    "ar",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override val seriesArtistSelector =
        ".infotable tr:contains(الرسام) td:last-child, ${super.seriesArtistSelector}"
    override val seriesAuthorSelector =
        ".infotable tr:contains(المؤلف) td:last-child, ${super.seriesAuthorSelector}"
    override val seriesStatusSelector =
        ".infotable tr:contains(الحالة) td:last-child, ${super.seriesStatusSelector}"
    override val seriesTypeSelector =
        ".infotable tr:contains(النوع) td:last-child, ${super.seriesTypeSelector}"
    override val seriesAltNameSelector =
        ".infotable tr:contains(الأسماء الثانوية) td:last-child, ${super.seriesAltNameSelector}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("/manga")) {
            manga.url.replaceFirst("/manga", mangaUrlDirectory)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("/manga")) {
            manga.url.replaceFirst("/manga", mangaUrlDirectory)
        }
        return super.chapterListRequest(manga)
    }
}
