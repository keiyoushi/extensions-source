package eu.kanade.tachiyomi.extension.fr.inovascanmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class InovaScanManga : Madara(
    "Inova Scans Manga",
    "https://inovascanmanga.com",
    "fr",
    SimpleDateFormat("dd MMMMMM yyyy", Locale.FRENCH),
)
