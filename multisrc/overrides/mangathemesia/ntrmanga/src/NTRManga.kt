package eu.kanade.tachiyomi.extension.th.ntrmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class NTRManga : MangaThemesia(
    "NTR-Manga",
    "https://www.ntr-manga.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
)
