package eu.kanade.tachiyomi.extension.all.mangadna

import eu.kanade.tachiyomi.multisrc.yiimadara.YiiMadara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaDNA : YiiMadara() {
    override val completedPath: String? = null
    override val rawPath = "manhwa-raw"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    // Only the catalog paginates with /page/<n>
    override fun listUrl(path: String, page: Int): HttpUrl.Builder = if (path == directoryPath) {
        baseUrl.toHttpUrl().newBuilder().addPathSegments("$path/page/$page")
    } else {
        super.listUrl(path, page)
    }
}
