package eu.kanade.tachiyomi.extension.en.manga18x

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Manga18x : Madara() {
    override val client = super.client.newBuilder()
        .readTimeout(2.minutes)
        .build()

    override val useNewChapterEndpoint = true
}
