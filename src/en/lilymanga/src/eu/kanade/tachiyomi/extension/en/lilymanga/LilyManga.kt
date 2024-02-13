package eu.kanade.tachiyomi.extension.en.lilymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LilyManga : Madara("Lily Manga", "https://lilymanga.net", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US)) {
    override val mangaSubString = "ys"
}
