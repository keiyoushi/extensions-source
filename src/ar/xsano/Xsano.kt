package eu.kanade.tachiyomi.extension.ar.xsano

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Xsano : Madara(
    "Xsano Manga",
    "https://www.xsano-manga.com",
    "ar",  // اللغة العربية
    SimpleDateFormat("MMMM d, yyyy", Locale("ar", "SA")) // إضافة رمز البلد
)
