package eu.kanade.tachiyomi.extension.en.dragontea

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DragonTea : Madara() {
    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val mangaSubString = "novel"

    override val useNewChapterEndpoint = true
}
