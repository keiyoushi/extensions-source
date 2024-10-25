package eu.kanade.tachiyomi.extension.pt.plumacomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class PlumaComics : Madara(
    "Pluma Comics",
    "https://plumacomics.cloud",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val useNewChapterEndpoint = true

    override fun chapterListParse(response: Response) =
        super.chapterListParse(response).reversed()
}
