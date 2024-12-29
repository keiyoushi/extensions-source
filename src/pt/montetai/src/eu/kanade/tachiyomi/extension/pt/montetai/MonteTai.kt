package eu.kanade.tachiyomi.extension.pt.montetai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MonteTai : Madara(
    "Monte Tai",
    "https://montetaiscanlator.xyz",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val useNewChapterEndpoint = true
}
