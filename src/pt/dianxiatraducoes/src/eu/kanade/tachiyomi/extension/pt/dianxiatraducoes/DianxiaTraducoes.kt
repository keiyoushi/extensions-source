package eu.kanade.tachiyomi.extension.pt.dianxiatraducoes

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class DianxiaTraducoes : Madara(
    "Dianxia Traduções",
    "https://dianxiatrads.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
