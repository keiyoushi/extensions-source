package eu.kanade.tachiyomi.extension.en.kaynscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response

@Source
abstract class KaynScans : Iken() {
    override val sortPagesByFilename = true

    // Migrate from Keyoapp to Iken by checking non slug-only urls
    override fun chapterListRequest(m: SManga): Request {
        if (m.url.startsWith('/')) {
            throw Exception("Migrate entry from '$name' to '$name'")
        }
        return super.chapterListRequest(m)
    }

    override fun pageListParse(r: Response): List<Page> {
        if (r.request.url.pathSegments
                .firstOrNull() != "api"
        ) {
            throw Exception("Migrate entry from '$name' to '$name'")
        }
        return super.pageListParse(r)
    }
}
