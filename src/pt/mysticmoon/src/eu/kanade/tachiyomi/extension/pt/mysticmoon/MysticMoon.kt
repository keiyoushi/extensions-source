package eu.kanade.tachiyomi.extension.pt.mysticmoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MysticMoon : Madara(
    "Mystic Moon",
    "https://mysticmagic.com.br",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val useNewChapterEndpoint: Boolean = true
}
