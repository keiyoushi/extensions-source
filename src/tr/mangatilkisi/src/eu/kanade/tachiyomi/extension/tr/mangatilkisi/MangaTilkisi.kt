package eu.kanade.tachiyomi.extension.tr.mangatilkisi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTilkisi : Madara(
    "MangaTilkisi",
    "https://www.mangatilkisi.com",
    "tr",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("tr")),
)
