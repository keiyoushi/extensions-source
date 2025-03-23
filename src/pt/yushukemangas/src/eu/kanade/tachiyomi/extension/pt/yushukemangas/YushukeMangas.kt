package eu.kanade.tachiyomi.extension.pt.yushukemangas

import eu.kanade.tachiyomi.multisrc.yuyu.YuYu
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class YushukeMangas : YuYu(
    "Yushuke Mangas",
    "https://new.yushukemangas.com",
    "pt-BR",
) {

    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .build()

    override val versionId = 2
}
