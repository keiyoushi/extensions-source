package eu.kanade.tachiyomi.extension.id.ngamenkomik

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class NgamenKomik : ZeistManga("NgamenKomik", "https://ngamenkomik05.blogspot.com", "id") {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
