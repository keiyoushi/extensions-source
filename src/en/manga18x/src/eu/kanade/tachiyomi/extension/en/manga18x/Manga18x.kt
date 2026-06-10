package eu.kanade.tachiyomi.extension.en.manga18x

import eu.kanade.tachiyomi.multisrc.madara.Madara
import kotlin.time.Duration.Companion.minutes

class Manga18x : Madara("Manga 18x", "https://manga18x.net", "en") {
    override val client = super.client.newBuilder()
        .readTimeout(2.minutes)
        .build()

    override val useNewChapterEndpoint = true
}
