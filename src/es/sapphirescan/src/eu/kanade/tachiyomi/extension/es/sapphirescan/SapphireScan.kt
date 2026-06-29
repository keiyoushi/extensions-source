package eu.kanade.tachiyomi.extension.es.sapphirescan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class SapphireScan :
    ZeistManga(
        "SapphireScan",
        "https://www.sapphirescan.com",
        "es",
    ) {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = super.client.newBuilder()
        .rateLimit(3) { it.host == baseUrlHost }
        .build()

    // Madara -> ZeistManga migration
    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("/manga/")) {
            throw Exception("Migrar de $name a $name (misma extensión)")
        }
        return super.chapterListRequest(manga)
    }

    // Madara -> ZeistManga migration
    override fun pageListParse(response: Response): List<Page> {
        if (response.request.url.encodedPath.startsWith("/manga/")) {
            throw Exception("Migrar de $name a $name (misma extensión)")
        }
        return super.pageListParse(response)
    }

    override val pageListSelector = "div.check-box"
}
