package eu.kanade.tachiyomi.extension.en.kenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response

class KenScans : Iken(
    "Ken Scans",
    "en",
    "https://kenscans.com",
    "https://api.kenscans.com",
) {
    override fun chapterListRequest(manga: SManga): Request {
        // Migration from old web theme to the new one(Keyoapp -> Iken)
        if (manga.url.startsWith("/series/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }
        return super.chapterListRequest(manga)
    }

    override fun pageListParse(response: Response): List<Page> {
        // Migration from old web theme to the new one(Keyoapp -> Iken)
        val url = response.request.url.toString().substringAfter(baseUrl)
        if (url.startsWith("/chapter/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }

        return super.pageListParse(response)
    }
}
