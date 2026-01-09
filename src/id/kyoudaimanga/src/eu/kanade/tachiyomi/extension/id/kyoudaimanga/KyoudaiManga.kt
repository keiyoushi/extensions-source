package eu.kanade.tachiyomi.extension.id.kyoudaimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KyoudaiManga : MangaThemesia(
    "KyoudaiManga",
    "https://www.kyoudaimanga.my.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")),
) {

    override val hasProjectPage = true
}
