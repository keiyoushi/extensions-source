package eu.kanade.tachiyomi.extension.pt.lichmangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LichMangas : Madara(
    "Lich Mangas",
    "https://lichmangas.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true
}
