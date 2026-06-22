package eu.kanade.tachiyomi.extension.ar.mangaxcore

import eu.kanade.tachiyomi.multisrc.madara.Madara
import kotlin.time.Duration.Companion.minutes

class MangaxCore :
    Madara(
        "Mangax Core",
        "https://mangaxcore.xyz",
        "ar",
    ) {
    override val client = super.client.newBuilder()
        .connectTimeout(1.minutes)
        .readTimeout(1.minutes)
        .callTimeout(2.minutes)
        .build()

    override val mangaSubString = "works"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
