package eu.kanade.tachiyomi.extension.en.skymanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class SkyManga : MangaThemesia() {
    override val mangaUrlDirectory = "/manga-list"
    override val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
