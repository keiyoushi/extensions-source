package eu.kanade.tachiyomi.extension.es.datgarscanlation

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class DatGarScanlation : ZeistManga() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override val useNewChapterFeed = true
}
