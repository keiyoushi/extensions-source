package eu.kanade.tachiyomi.extension.en.teenmanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TeenManhua : Madara(
    "TeenManhua",
    "https://teenmanhua.com",
    "en",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US),
) {
    override val filterNonMangaItems = false
}
