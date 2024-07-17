package eu.kanade.tachiyomi.extension.en.blazescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class FuryManga : MangaThemesia(
    "Fury Manga",
    "https://furymanga.com",
    "en",
    "/comics",
) {
    override val id = 3912200442923601567

    override val client = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()
}
