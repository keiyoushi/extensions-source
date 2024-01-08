package eu.kanade.tachiyomi.extension.en.firstkissmangaclub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class FirstKissMangaClub : Madara(
    "1stKissManga.Club",
    "https://1stkissmanga.club",
    "en",
) {

    override val client = super.client.newBuilder()
        .rateLimit(1, 3, TimeUnit.SECONDS)
        .build()
}
