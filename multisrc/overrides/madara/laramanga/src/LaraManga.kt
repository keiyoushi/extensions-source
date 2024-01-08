package eu.kanade.tachiyomi.extension.en.laramanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LaraManga : Madara(
    "Lara Manga",
    "https://laramanga.love",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
)
