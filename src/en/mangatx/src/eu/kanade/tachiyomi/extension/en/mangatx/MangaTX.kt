package eu.kanade.tachiyomi.extension.en.mangatx

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaTX : MangaThemesia() {
    override val mangaUrlDirectory = "/manga-list"
    override val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT)
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val seriesAuthorSelector = ".imptdt:contains(Author) a"
}
