package eu.kanade.tachiyomi.extension.pt.infinyxscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class InfinyxScan : Madara(
    "InfinyxScan",
    "https://infinyxscan.cloud",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val useNewChapterEndpoint: Boolean = true
}
