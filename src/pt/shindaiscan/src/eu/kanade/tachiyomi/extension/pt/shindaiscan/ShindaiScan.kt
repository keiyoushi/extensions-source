package eu.kanade.tachiyomi.extension.pt.shindaiscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ShindaiScan : Madara(
    "Shindai Scan",
    "https://shindai.com.br",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response)
            .sortedBy(SChapter::name)
            .reversed()
    }
}
