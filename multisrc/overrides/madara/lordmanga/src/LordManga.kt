package eu.kanade.tachiyomi.extension.en.lordmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LordManga : Madara(
    "Lord Manga",
    "https://lordmanga.com",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
)
