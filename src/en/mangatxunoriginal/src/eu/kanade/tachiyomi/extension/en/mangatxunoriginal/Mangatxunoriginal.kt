package eu.kanade.tachiyomi.extension.en.mangatxunoriginal

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Mangatxunoriginal : Madara(
    "MangaEmpress",
    "https://mangaempress.com",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
)
