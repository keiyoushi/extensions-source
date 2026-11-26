package eu.kanade.tachiyomi.extension.vi.panomic

import eu.kanade.tachiyomi.multisrc.koi.Koi
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Panomic : Koi() {
    override val mangaPath = "truyen"
    override fun isExcludedMangaUrl(url: String): Boolean = "/$mangaPath/novel-" in url

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)
        .addCookie {
            listOf("a3_site_passcode_token" to "0d9678e234bc5f4234834a559f1c0157b3d933222d4b147ae2d6e1ac58a1dae0")
        }
}
