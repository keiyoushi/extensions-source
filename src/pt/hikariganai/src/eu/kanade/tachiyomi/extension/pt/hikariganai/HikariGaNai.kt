package eu.kanade.tachiyomi.extension.pt.hikariganai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HikariGaNai : Madara(
    "Hikari Ga Nai",
    "https://hikariganai.xyz",
    "pt-BR",
    dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
