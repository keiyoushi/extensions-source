package eu.kanade.tachiyomi.extension.pt.argoscomicscombr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ArgosComicsComBr : Madara(
    "Argos Comics.com.br",
    "https://argoscomics.com.br",
    "pt-BR",
    dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
