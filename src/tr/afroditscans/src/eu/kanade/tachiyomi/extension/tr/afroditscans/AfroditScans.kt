package eu.kanade.tachiyomi.extension.tr.afroditscans

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response

class AfroditScans :
    UzayManga(
        "Afrodit Scans",
        "https://afroditscans.com",
        lang = "tr",
        versionId = 1,
        cdnUrl = "https://afroditcdn1.efsaneler.can.re",
    ) {
    override fun chapterListRequest(manga: SManga): Request {
        // Migration from Madara to UzayManga
        if (manga.url.startsWith("/manga/") && !manga.url.substringAfter("/manga/").substringBefore("/").all { it.isDigit() }) {
            throw Exception("Migrate from $name to $name (same extension)")
        }
        return super.chapterListRequest(manga)
    }

    override fun pageListParse(response: Response): List<Page> {
        // Migration from Madara to UzayManga
        val url = response.request.url.toString().substringAfter(baseUrl)
        if (!url.startsWith("/manga/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }

        return super.pageListParse(response)
    }
}
