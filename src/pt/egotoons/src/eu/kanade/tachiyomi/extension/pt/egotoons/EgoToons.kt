package eu.kanade.tachiyomi.extension.pt.egotoons

import eu.kanade.tachiyomi.multisrc.yuyu.YuYu
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class EgoToons : YuYu(
    "Ego Toons",
    "https://egotoons.com",
    "pt-BR",
) {

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept-Encoding", "")

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
