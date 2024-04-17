package eu.kanade.tachiyomi.extension.pt.onepieceteca

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class OnePieceTeca : Madara(
    "One Piece TECA",
    "https://onepieceteca.com",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {
    override val supportsLatest = false

    override val useNewChapterEndpoint = true

    override val mangaSubString = "ler-online"
}
