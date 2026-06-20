package eu.kanade.tachiyomi.extension.id.otsugami

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Otsugami :
    MangaThemesia(
        "Otsugami ID",
        "https://otsugami.id",
        "id",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
    )
