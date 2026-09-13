package eu.kanade.tachiyomi.extension.id.komikindo

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class Komikindo : MangaThemesia() {
    // Some covers fail to load with no Accept header + no resize parameter.
    // Hence the workarounds:

    private val cdnHeaders = imageRequest(Page(0, "$baseUrl/", baseUrl)).headers

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(acceptHeaderInterceptor())
        rateLimit(3)
    }

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        thumbnail_url = thumbnail_url
            ?.toHttpUrlOrNull()
            ?.takeIf { it.queryParameter("resize") == null }
            ?.newBuilder()
            ?.setEncodedQueryParameter("resize", "165,225")
            ?.build()
            ?.toString()
    }

    override val hasProjectPage = true
}
