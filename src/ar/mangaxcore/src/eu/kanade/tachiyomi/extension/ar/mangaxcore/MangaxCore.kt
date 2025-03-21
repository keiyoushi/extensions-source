package eu.kanade.tachiyomi.extension.ar.mangaxcore

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.util.concurrent.TimeUnit

class MangaxCore : Madara(
    "Mangax Core",
    "https://mangaxcore.xyz",
    "ar",
) {
    override val client = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    override val mangaSubString = "works"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
