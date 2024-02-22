package eu.kanade.tachiyomi.extension.pt.lunarscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LunarScan : Madara(
    "Lunar Scan",
    "https://lunarscan.com.br",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {
    override val mangaSubString = "obra"
}
