package eu.kanade.tachiyomi.extension.id.luvyaa

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Luvyaa :
    MangaThemesia(
        "Luvyaa",
        "https://luvyaa.id",
        "id",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
    )
