package eu.kanade.tachiyomi.extension.tr.araznovel

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ArazNovel : Madara(
    "ArazNovel",
    "https://araznovel.com",
    "tr",
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
)
