package eu.kanade.tachiyomi.extension.pt.yushukemangas

import eu.kanade.tachiyomi.multisrc.yuyu.YuYu
import keiyoushi.network.rateLimit
import kotlin.time.Duration.Companion.seconds

class YushukeMangas : YuYu(
    "Yushuke Mangas",
    "https://new.yushukemangas.com",
    "pt-BR",
) {

    override val client = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val versionId = 2
}
