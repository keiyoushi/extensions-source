package eu.kanade.tachiyomi.extension.fr.readergen

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ReaderGen : Madara("ReaderGen", "https://fr.readergen.fr", "fr") {
    override val useNewChapterEndpoint = true
}
