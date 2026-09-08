package eu.kanade.tachiyomi.extension.en.petrotechsociety

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter

@Source
abstract class Petrotechsociety : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}
