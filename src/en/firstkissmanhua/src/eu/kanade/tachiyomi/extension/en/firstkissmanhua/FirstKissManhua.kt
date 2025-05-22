package eu.kanade.tachiyomi.extension.en.firstkissmanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit

class FirstKissManhua : Madara(
    "First Kiss Manhua",
    "https://1stkissmanhua.net",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
