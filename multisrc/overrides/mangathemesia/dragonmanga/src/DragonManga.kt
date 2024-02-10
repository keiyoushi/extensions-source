package eu.kanade.tachiyomi.extension.th.dragonmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class DragonManga : MangaThemesia(
    "DragonManga",
    "https://www.dragon-manga.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
)
