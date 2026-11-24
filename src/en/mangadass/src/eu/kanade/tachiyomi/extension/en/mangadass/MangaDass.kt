package eu.kanade.tachiyomi.extension.en.mangadass

import eu.kanade.tachiyomi.multisrc.yiimadara.YiiMadara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class MangaDass : YiiMadara() {
    override val rawPath = "raw-manhwa"

    // English-only source that has always listed raws
    override val rawEntries = RawEntries.SHOW

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)
}
