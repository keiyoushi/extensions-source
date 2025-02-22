package eu.kanade.tachiyomi.extension.pt.lerhentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class LerHentai : Madara(
    "Ler Hentai",
    "https://lerhentai.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            description = description?.removePrefix("Sinopse\n\n")
        }
    }
}
