package eu.kanade.tachiyomi.extension.pt.nekotoons

import eu.kanade.tachiyomi.multisrc.yuyu.YuYu
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class NekoToons : YuYu(
    "Neko Toons",
    "https://nekotoons.site",
    "pt-BR",
) {

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
