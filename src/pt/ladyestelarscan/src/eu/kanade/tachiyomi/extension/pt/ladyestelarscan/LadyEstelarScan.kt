package eu.kanade.tachiyomi.extension.pt.ladyestelarscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LadyEstelarScan : Madara(
    "Lady Estelar Scan",
    "https://ladyestelarscan.com.br",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
