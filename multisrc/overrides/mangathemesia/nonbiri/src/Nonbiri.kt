package eu.kanade.tachiyomi.extension.id.nonbiri

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Nonbiri : MangaThemesia(
    "Nonbiri",
    "https://nonbiri.space",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {

    override val hasProjectPage = true
}
