package eu.kanade.tachiyomi.extension.en.kaynscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response

class KaynScans :
    Iken(
        "Kayn Scans",
        "en",
        "https://kaynscan.org",
        "https://api.kaynscan.org",
    ) {

    // Migrate from Keyoapp to Iken by checking non slug-only urls
    override fun chapterListRequest(m: SManga): Request {
        if (m.url.startsWith('/')) {
            throw Exception("Migrate entry from '$name' to '$name'")
        }
        return super.chapterListRequest(m)
    }

    override fun pageListParse(r: Response): List<Page> {
        if (r.request.url.pathSegments.size > 1) {
            throw Exception("Migrate entry from '$name' to '$name'")
        }
        return super.pageListParse(r)
    }
}
