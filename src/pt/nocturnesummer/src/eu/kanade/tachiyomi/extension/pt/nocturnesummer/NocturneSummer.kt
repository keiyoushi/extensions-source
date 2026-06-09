package eu.kanade.tachiyomi.extension.pt.nocturnesummer

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.OkHttpClient
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class NocturneSummer :
    Madara(
        "Nocturne Summer",
        "https://nocfsb.com",
        "pt-BR",
        SimpleDateFormat("dd 'de' MMMMM 'de' yyyy", Locale("pt", "BR")),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Estado) > div.summary-content"

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response)
        .sortedBy(SChapter::name)
        .reversed()
}
