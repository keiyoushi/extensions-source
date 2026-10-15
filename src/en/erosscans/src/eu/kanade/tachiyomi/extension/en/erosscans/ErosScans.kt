package eu.kanade.tachiyomi.extension.en.erosscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Headers
import okhttp3.OkHttpClient

@Source
abstract class ErosScans : MangaThemesia() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // Cloudflare keeps a stale cache variant of the manga pages keyed on the presence
    // of an Origin header, so requests carrying one get a page frozen at an older
    // chapter list. Browsers only send Origin on cross-origin requests, never on a
    // top-level navigation, so drop it to match a normal page load.
    override fun Headers.Builder.configureHeaders(): Headers.Builder = removeAll("Origin")
}
