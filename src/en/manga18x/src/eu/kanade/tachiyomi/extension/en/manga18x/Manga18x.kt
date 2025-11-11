package eu.kanade.tachiyomi.extension.en.manga18x

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.util.concurrent.TimeUnit

class Manga18x : Madara("Manga 18x", "https://manga18x.net", "en") {
    override val client = super.client.newBuilder()
        .readTimeout(2, TimeUnit.MINUTES)
        .build()

    override val useNewChapterEndpoint = true
}
