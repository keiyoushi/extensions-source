package eu.kanade.tachiyomi.extension.fr.kataitake

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Kataitake : Madara("Kataitake", "https://www.kataitake.fr", "fr", dateFormat = SimpleDateFormat("dd/mm/yyyy", Locale.FRANCE)) {
    override val altName: String = "Noms alternatifs :"
}
