package eu.kanade.tachiyomi.extension.pt.limanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class LiManga : MangaThemesia(
    "Li Manga",
    "https://limanga.net",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
)
