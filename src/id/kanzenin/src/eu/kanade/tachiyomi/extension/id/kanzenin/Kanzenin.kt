package eu.kanade.tachiyomi.extension.id.kanzenin

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Kanzenin : MangaThemesia(
    "Kanzenin",
    "https://kanzenin.info",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
)
