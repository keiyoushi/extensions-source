package eu.kanade.tachiyomi.extension.tr.tonizutoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TonizuToon : Madara(
    "TonizuToon",
    "https://tonizutoon.com",
    "tr",
    SimpleDateFormat("MMMMM d, yyyy", Locale("tr")),
)
