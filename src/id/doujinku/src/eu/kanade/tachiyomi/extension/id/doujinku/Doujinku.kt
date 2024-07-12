package eu.kanade.tachiyomi.extension.id.doujinku

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Doujinku : MangaThemesia(
    "Doujinku",
    "https://doujinku.org",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
)
