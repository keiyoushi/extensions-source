package eu.kanade.tachiyomi.extension.pt.argoscomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ArgosComics : Madara(
    "Argos Comics",
    "https://argoscomics.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
)
