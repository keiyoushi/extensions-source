package eu.kanade.tachiyomi.extension.en.madaradex

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MadaraDex : Madara(
    "MadaraDex",
    "https://madaradex.org",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
) {
    override val mangaSubString = "title"
}
