package eu.kanade.tachiyomi.extension.ar.yurimoonsub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class YuriMoonSub : ZeistManga("Yuri Moon Sub", "https://yurimoonsub.blogspot.com", "ar") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
